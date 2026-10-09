package day.bark.android.projects.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject
import org.json.JSONException

data class BasisFeedResponse(val status: Int, val body: String? = null, val etag: String? = null)
fun interface BasisFeedTransport { fun fetch(family: String, etag: String?): BasisFeedResponse }

/** One verified, bounded record per family. Source timestamps never change on a 304 or failed refresh. */
class BasisFeedCache(
    private val directory: File,
    private val transport: BasisFeedTransport,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Record(val body: String, val etag: String?, val state: BasisCacheState)
    private class DataUnavailable : IOException()
    private class RequestFailed(val retryable: Boolean) : IOException()
    private val records = ConcurrentHashMap<String, Record>()
    private val locks = ConcurrentHashMap<String, Any>()
    private val attempts = ConcurrentHashMap<String, Long>()

    fun cached(family: String): BasisCacheState = synchronized(lock(family)) { record(family)?.state ?: BasisCacheState() }

    fun refresh(family: String, force: Boolean = false): BasisCacheState = synchronized(lock(family)) {
        val current = record(family)
        val now = clock()
        val attempted = attempts[family] ?: current?.state?.checkedAtMillis
        val interval = if (force) 15_000L else 60_000L
        if (attempted != null && now - attempted in 0 until interval) {
            return@synchronized current?.state ?: BasisCacheState()
        }
        attempts[family] = now
        try {
            val response = try {
                transport.fetch(family, current?.etag)
            } catch (_: IOException) {
                throw RequestFailed(retryable = true)
            }
            val verifiedAt = clock()
            val next = when (response.status) {
                200 -> {
                    val incoming = requireNotNull(response.body)
                    // Validate the entire response before merging any saved values into it.
                    val parsed = BasisSnapshot.parse(incoming, family)
                    val body = retainNewerQuotes(incoming, parsed, current)
                    val snapshot = if (body == incoming) parsed else BasisSnapshot.parse(body, family)
                    if (!hasDisplayData(snapshot) && current?.state?.snapshot?.let(::hasDisplayData) == true) {
                        throw DataUnavailable()
                    }
                    Record(body, cleanEtag(response.etag), BasisCacheState(snapshot, verifiedAt, verifiedAt))
                }
                304 -> {
                    require(current != null && current.etag != null) { "No validated snapshot for 304" }
                    current.copy(state = current.state.copy(checkedAtMillis = verifiedAt, error = null,
                        failure = null, retryableError = false))
                }
                else -> throw RequestFailed(response.status in setOf(408, 425, 429) || response.status in 500..599)
            }
            write(family, next)
            records[family] = next
            next.state
        } catch (error: Exception) {
            val state = (current?.state ?: BasisCacheState()).copy(
                error = when (error) {
                    is DataUnavailable -> "数据暂不可用，保留上次快照"
                    is IllegalArgumentException, is JSONException -> "数据校验失败，保留上次快照"
                    is RequestFailed -> "连接暂不可用，显示已保存数据"
                    else -> "快照保存失败，保留上次数据"
                },
                failure = when (error) {
                    is RequestFailed -> BasisRefreshFailure.CONNECTION
                    is DataUnavailable, is IllegalArgumentException, is JSONException -> BasisRefreshFailure.DATA
                    else -> BasisRefreshFailure.STORAGE
                },
                retryableError = error is RequestFailed && error.retryable,
            )
            // Keep even an empty failure in memory so concurrent widget taps share the same backoff.
            records[family] = current?.copy(state = state) ?: Record("", null, state)
            state
        } finally {
            // Start cooldown after completion so queued callers share a slow in-flight request too.
            attempts[family] = clock()
        }
    }

    private fun lock(family: String): Any {
        require(family in BasisSnapshot.FAMILIES)
        return locks.getOrPut(family) { Any() }
    }

    private fun hasDisplayData(snapshot: BasisSnapshot): Boolean = snapshot.contracts.any { contract ->
        contract.annualizedDiscount != null || contract.points.any { it.value != null }
    }

    /** Keep a newer quote per real contract while accepting new history and other maturities. */
    private fun retainNewerQuotes(incoming: String, parsed: BasisSnapshot, current: Record?): String {
        val previous = current?.state?.snapshot ?: return incoming
        val savedRows = JSONObject(current.body).getJSONArray("contracts")
        val savedByCode = (0 until savedRows.length()).associate { index ->
            val row = savedRows.getJSONObject(index)
            row.getString("contract") to row
        }
        val root = JSONObject(incoming)
        val rows = root.getJSONArray("contracts")
        var retained = false
        parsed.contracts.forEachIndexed { index, next ->
            val old = previous.contracts.firstOrNull { it.code == next.code && it.expiry == next.expiry }
                ?: return@forEachIndexed
            if (old.annualizedDiscount == null) return@forEachIndexed
            val oldTime = sourceTime(old) ?: return@forEachIndexed
            val newTime = sourceTime(next)
            // The official close is authoritative for that day even when its fixed
            // 15:00 timestamp precedes a collector's last sample by a few seconds.
            val officialClose = next.kind == "close" && next.annualizedDiscount != null &&
                next.dataDate == old.dataDate && old.kind in setOf("live", "sampled")
            if (officialClose || (newTime != null && newTime >= oldTime)) return@forEachIndexed
            val row = rows.getJSONObject(index)
            val saved = savedByCode.getValue(next.code)
            for (key in listOf("kind", "date", "quote_time", "dte", "near_expiry", BasisSnapshot.METRIC)) {
                row.put(key, saved.opt(key) ?: JSONObject.NULL)
            }
            if (saved.has("index_quote_time")) row.put("index_quote_time", saved.get("index_quote_time"))
            else row.remove("index_quote_time")
            // New history is useful, but its rank describes the regressed quote, not the retained one.
            row.getJSONObject("percentile").put("rank", JSONObject.NULL).put("status", "needs_data")
            row.put("freshness", JSONObject().put("state", "stale").put("is_realtime", false))
            row.put("status", "needs_data").put("reason", "行情源返回较早数据，保留最后有效报价")
            if (old.kind == "live") row.put("kind", "sampled")
            retained = true
        }
        if (!retained) return incoming
        root.put("as_of", (0 until rows.length()).map { rows.getJSONObject(it).optString("date", "") }.maxOrNull().orEmpty())
        root.put("freshness", JSONObject().put("state", "stale").put("is_realtime", false))
        root.put("status", "needs_data")
        return root.toString()
    }

    private fun sourceTime(contract: BasisContract): Long? = runCatching {
        if (contract.quoteTime.isNotBlank()) OffsetDateTime.parse(contract.quoteTime).toInstant().toEpochMilli()
        else LocalDate.parse(contract.dataDate).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
    }.getOrNull()

    private fun record(family: String): Record? {
        records[family]?.let { return it }
        val file = File(directory, "$family.json")
        if (!file.isFile || file.length() > BasisSnapshot.MAX_BYTES * 2L) return null
        val restored = runCatching {
            val json = JSONObject(file.readText())
            require(json.getInt("version") == 1)
            val body = json.getString("body")
            val received = json.getLong("received_at")
            val checked = json.getLong("checked_at")
            require(clock() - received in -300_000L..RETENTION_MILLIS)
            Record(body, cleanEtag(json.optString("etag", "")),
                BasisCacheState(BasisSnapshot.parse(body, family), checked, received))
        }.getOrNull() ?: return null
        records[family] = restored
        return restored
    }

    private fun write(family: String, record: Record) {
        directory.mkdirs()
        val json = JSONObject().put("version", 1).put("body", record.body)
            .put("etag", record.etag ?: "").put("received_at", record.state.receivedAtMillis)
            .put("checked_at", record.state.checkedAtMillis).toString()
        val temporary = File(directory, "$family.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            check(temporary.renameTo(File(directory, "$family.json"))) { "Cannot save snapshot" }
        } finally { temporary.delete() }
    }

    private fun cleanEtag(value: String?): String? = value?.takeIf {
        it.isNotBlank() && it.length <= 160 && !it.any { c -> c == '\r' || c == '\n' }
    }

    companion object { private const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000 }
}

package day.bark.android.projects.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

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
            val response = transport.fetch(family, current?.etag)
            val verifiedAt = clock()
            val next = when (response.status) {
                200 -> {
                    val body = requireNotNull(response.body)
                    val snapshot = BasisSnapshot.parse(body, family)
                    if (!hasDisplayData(snapshot) && current?.state?.snapshot?.let(::hasDisplayData) == true) {
                        throw DataUnavailable()
                    }
                    Record(body, cleanEtag(response.etag), BasisCacheState(snapshot, verifiedAt, verifiedAt))
                }
                304 -> {
                    require(current != null && current.etag != null) { "No validated snapshot for 304" }
                    current.copy(state = current.state.copy(checkedAtMillis = verifiedAt, error = null))
                }
                else -> throw IOException("Summary request failed")
            }
            write(family, next)
            records[family] = next
            next.state
        } catch (error: Exception) {
            val state = (current?.state ?: BasisCacheState()).copy(
                error = when (error) {
                    is DataUnavailable -> "数据暂不可用，保留上次快照"
                    is IllegalArgumentException -> "数据校验失败，保留上次快照"
                    else -> "连接暂不可用，显示已保存数据"
                },
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

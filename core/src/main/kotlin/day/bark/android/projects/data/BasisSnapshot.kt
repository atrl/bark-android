package day.bark.android.projects.data

import java.time.LocalDate
import java.time.OffsetDateTime
import org.json.JSONObject

data class BasisPoint(val date: String, val value: Double?, val gapBefore: Boolean)

data class BasisContract(
    val code: String,
    val expiry: String,
    val tenor: String,
    val kind: String,
    val dataDate: String,
    val quoteTime: String,
    val dte: Int?,
    val nearExpiry: Boolean,
    val annualizedDiscount: Double?,
    val percentile: Double?,
    val sampleCount: Int,
    val percentileStatus: String,
    val freshness: String,
    val status: String,
    val reason: String,
    val points: List<BasisPoint>,
) {
    fun liveQuoteExpired(nowMillis: Long): Boolean {
        if (kind != "live") return false
        val time = runCatching { OffsetDateTime.parse(quoteTime).toInstant().toEpochMilli() }.getOrNull()
            ?: return true
        return nowMillis - time !in -5_000L..120_000L
    }
}

data class BasisSnapshot(
    val family: String,
    val asOf: String,
    val generatedAt: String,
    val source: String,
    val freshness: String,
    val status: String,
    val contracts: List<BasisContract>,
) {
    companion object {
        val FAMILIES = setOf("IC", "IM", "IF")
        const val METRIC = "annualized_carry_pct"
        const val MAX_BYTES = 131_072

        fun parse(text: String, expectedFamily: String): BasisSnapshot {
            require(expectedFamily in FAMILIES)
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val root = JSONObject(text)
            require(root.getInt("schema_version") == 1)
            require(root.getString("family") == expectedFamily)
            val method = root.getJSONObject("method")
            require(method.getString("metric") == METRIC && method.getString("units") == "fraction")
            require(method.getString("higher_rank") == "more_discounted")
            require(method.getString("day_count") == "ACT/365")
            val rows = root.getJSONArray("contracts")
            require(rows.length() <= 4)
            val contracts = (0 until rows.length()).map { index ->
                val item = rows.getJSONObject(index)
                val code = item.getString("contract")
                require(code.matches(Regex("${expectedFamily}[0-9]{4}\\.CFX")))
                val expiry = item.getString("expiry").also { LocalDate.parse(it) }
                val tenor = item.getString("tenor").also { require(it in setOf("front", "next", "quarter", "far")) }
                val date = item.optString("date", "").also { if (it.isNotEmpty()) LocalDate.parse(it) }
                val rank = item.getJSONObject("percentile")
                require(rank.getString("metric") == METRIC)
                val sampleCount = rank.getInt("samples").also { require(it >= 0) }
                val percentileStatus = rank.getString("status")
                val percentile = number(rank, "rank")?.also { require(it in 0.0..1.0) }
                val series = item.getJSONArray("series")
                require(series.length() <= 24)
                val points = (0 until series.length()).map { pointIndex ->
                    val point = series.getJSONObject(pointIndex)
                    require(point.getString("contract") == code && point.getString("expiry") == expiry)
                    val pointDate = point.getString("date").also { LocalDate.parse(it) }
                    require(date.isEmpty() || pointDate <= date)
                    BasisPoint(pointDate, number(point, METRIC), point.optBoolean("gap_before", false))
                }
                require(points.zipWithNext().all { (a, b) -> a.date < b.date })
                BasisContract(
                    code, expiry, tenor, item.getString("kind"), date, item.optString("quote_time", ""),
                    number(item, "dte")?.let { require(it == it.toInt().toDouble()); it.toInt() },
                    item.optBoolean("near_expiry", false), number(item, METRIC),
                    percentile.takeIf { percentileStatus == "ok" && sampleCount >= 60 }, sampleCount,
                    percentileStatus, item.getJSONObject("freshness").getString("state"),
                    item.getString("status"), item.optString("reason", ""), points,
                )
            }
            require(contracts.map { it.code }.distinct().size == contracts.size)
            require(contracts.map { it.tenor }.distinct().size == contracts.size)
            return BasisSnapshot(expectedFamily, root.optString("as_of", ""), root.getString("generated_at"),
                root.getString("source"), root.getJSONObject("freshness").getString("state"),
                root.getString("status"), contracts)
        }

        private fun number(json: JSONObject, key: String): Double? {
            if (json.isNull(key)) return null
            val value = json.get(key)
            require(value is Number)
            return value.toDouble().also { require(it.isFinite()) }
        }
    }
}

enum class BasisRefreshFailure { CONNECTION, DATA, STORAGE }

data class BasisCacheState(
    val snapshot: BasisSnapshot? = null,
    val checkedAtMillis: Long = 0,
    val receivedAtMillis: Long = 0,
    val refreshing: Boolean = false,
    val error: String? = null,
    val failure: BasisRefreshFailure? = null,
    val retryableError: Boolean = false,
)

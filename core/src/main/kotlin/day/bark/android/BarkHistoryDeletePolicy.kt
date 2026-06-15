package day.bark.android

import java.time.Instant
import java.time.ZoneId

enum class BarkHistoryDeleteRange {
    LAST_HOUR,
    TODAY,
    TODAY_AND_YESTERDAY,
    LAST_MONTH,
    ALL_TIME,
    BEFORE_ONE_HOUR,
    BEFORE_TODAY,
    BEFORE_YESTERDAY,
    BEFORE_ONE_MONTH,
}

data class BarkHistoryDeleteWindow(
    val startMillis: Long,
    val endMillis: Long,
)

object BarkHistoryDeletePolicy {
    fun window(
        range: BarkHistoryDeleteRange,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): BarkHistoryDeleteWindow? {
        if (range == BarkHistoryDeleteRange.ALL_TIME) return null

        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val startOfToday = now.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
        val startOfYesterday = now.toLocalDate().minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val lastHour = now.minusHours(1).toInstant().toEpochMilli()
        val lastMonth = now.minusMonths(1).toInstant().toEpochMilli()

        return when (range) {
            BarkHistoryDeleteRange.LAST_HOUR -> BarkHistoryDeleteWindow(lastHour, nowMillis)
            BarkHistoryDeleteRange.TODAY -> BarkHistoryDeleteWindow(startOfToday, nowMillis)
            BarkHistoryDeleteRange.TODAY_AND_YESTERDAY -> BarkHistoryDeleteWindow(startOfYesterday, nowMillis)
            BarkHistoryDeleteRange.LAST_MONTH -> BarkHistoryDeleteWindow(lastMonth, nowMillis)
            BarkHistoryDeleteRange.BEFORE_ONE_HOUR -> BarkHistoryDeleteWindow(0L, lastHour)
            BarkHistoryDeleteRange.BEFORE_TODAY -> BarkHistoryDeleteWindow(0L, startOfToday)
            BarkHistoryDeleteRange.BEFORE_YESTERDAY -> BarkHistoryDeleteWindow(0L, startOfYesterday)
            BarkHistoryDeleteRange.BEFORE_ONE_MONTH -> BarkHistoryDeleteWindow(0L, lastMonth)
            BarkHistoryDeleteRange.ALL_TIME -> null
        }
    }
}

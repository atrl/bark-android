package day.bark.android

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BarkHistoryDeletePolicyTest {
    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 6, 14, 15, 30, 0, 0, zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `last hour deletes from one hour ago through now`() {
        val window = BarkHistoryDeletePolicy.window(BarkHistoryDeleteRange.LAST_HOUR, now, zone)

        assertEquals(instant("2026-06-14T14:30:00Z"), window?.startMillis)
        assertEquals(now, window?.endMillis)
    }

    @Test
    fun `today and yesterday starts at yesterday start of day`() {
        val window = BarkHistoryDeletePolicy.window(BarkHistoryDeleteRange.TODAY_AND_YESTERDAY, now, zone)

        assertEquals(instant("2026-06-13T00:00:00Z"), window?.startMillis)
        assertEquals(now, window?.endMillis)
    }

    @Test
    fun `before today ends at current day start`() {
        val window = BarkHistoryDeletePolicy.window(BarkHistoryDeleteRange.BEFORE_TODAY, now, zone)

        assertEquals(0L, window?.startMillis)
        assertEquals(instant("2026-06-14T00:00:00Z"), window?.endMillis)
    }

    @Test
    fun `all time has no bounded window`() {
        assertNull(BarkHistoryDeletePolicy.window(BarkHistoryDeleteRange.ALL_TIME, now, zone))
    }

    private fun instant(value: String): Long =
        java.time.Instant.parse(value).toEpochMilli()
}

package day.bark.android.projects.widget

import day.bark.android.projects.data.BasisCacheState
import day.bark.android.projects.data.BasisContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasisWidgetLayoutTest {
    private val contract = BasisContract("IC2610.CFX", "2026-10-16", "front", "live", "2026-10-09",
        "2026-10-09T11:00:00+08:00", 7, true, .08, null, 0, "needs_data", "live", "ok", "", emptyList())
    private val now = java.time.Instant.parse("2026-10-09T03:01:00Z").toEpochMilli()

    @Test fun standardSizeShowsAllFourAndSmallSizesKeepFrontAndPreferred() {
        assertEquals(listOf("front", "next", "quarter", "far"), BasisWidgetLayout.forSize(220, 160, "far").tenors)
        assertEquals(listOf("front", "far"), BasisWidgetLayout.forSize(140, 120, "far").tenors)
        assertEquals(listOf("front", "next"), BasisWidgetLayout.forSize(320, 120, "front").tenors)
        assertEquals(listOf("front", "next"), BasisWidgetLayout.forSize(140, 240, "unknown").tenors)
        assertEquals(2, BasisWidgetLayout.forSize(220, 160, "next").rows)
        assertEquals(1, BasisWidgetLayout.forSize(219, 160, "next").rows)
        assertFalse(BasisWidgetLayout.forSize(220, 160, "next").detailed)
        assertTrue(BasisWidgetLayout.forSize(320, 240, "next").detailed)
    }

    @Test fun newestQuoteCannotMaskOlderOrMissingMaturities() {
        val state = BasisCacheState()
        assertEquals("采样快照", BasisWidgetLayout.status(state, listOf(contract, contract), now))
        assertEquals("部分待更新", BasisWidgetLayout.status(state,
            listOf(contract, contract.copy(quoteTime = "2026-10-09T10:00:00+08:00")), now))
        assertEquals("部分待更新", BasisWidgetLayout.status(state, listOf(contract, null), now))
        assertEquals("等待数据", BasisWidgetLayout.status(state, listOf(null, null), now))
        assertEquals("更新中", BasisWidgetLayout.status(state.copy(refreshing = true), listOf(contract, null), now))
    }

    @Test fun footerKeepsAbsoluteOldestSourceTimeAcrossZones() {
        val state = BasisCacheState()
        assertEquals("截至 2026-10-09 11:00:00", BasisWidgetLayout.timestamp(state, listOf(contract, null)))
        assertEquals("最早截至 2026-10-09 02:00:00Z", BasisWidgetLayout.timestamp(state,
            listOf(contract, contract.copy(quoteTime = "2026-10-09T02:00:00Z"))))
        assertEquals("最早截至 2026-09-30", BasisWidgetLayout.timestamp(state,
            listOf(contract, contract.copy(quoteTime = "", dataDate = "2026-09-30", kind = "close"))))
        assertEquals("等待首次数据", BasisWidgetLayout.timestamp(state, listOf(null, null)))
    }
}

package day.bark.android.projects.ui

import day.bark.android.projects.data.BasisCacheState
import day.bark.android.projects.data.BasisContract
import day.bark.android.projects.data.BasisSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BasisPresentationTest {
    private val contract = BasisContract(
        code = "IC2611.CFX", expiry = "2026-11-20", tenor = "next", kind = "close",
        dataDate = "2026-09-30", quoteTime = "2026-09-30T15:00:00+08:00", dte = 51,
        nearExpiry = false, annualizedDiscount = 0.0842, percentile = 0.734,
        sampleCount = 230, percentileStatus = "ok", freshness = "close", status = "ok",
        reason = "", points = emptyList(),
    )
    private val snapshot = BasisSnapshot("IC", "2026-09-30", "2026-10-01T09:00:00+08:00", "yanxuan", "close", "ok", listOf(contract))

    @Test fun fractionValuesHaveClearDisplayUnits() {
        assertEquals("8.42", BasisPresentation.annualized(contract))
        assertEquals("P73", BasisPresentation.percentile(contract))
        assertEquals("-2.00", BasisPresentation.annualized(contract.copy(annualizedDiscount = -.02)))
    }

    @Test fun missingRequestedTenorNeverSelectsAnotherAvailableContract() {
        val state = BasisCacheState(snapshot = snapshot)
        assertEquals(contract, BasisPresentation.selected(state, "next"))
        assertNull(BasisPresentation.selected(state, "quarter"))
        assertEquals("—", BasisPresentation.annualized(null))
    }

    @Test fun missingOrInsufficientRankNeverBecomesAPercentile() {
        assertEquals("—", BasisPresentation.percentile(contract.copy(percentileStatus = "needs_data")))
        assertEquals("—", BasisPresentation.percentile(contract.copy(percentile = null)))
        assertEquals("—", BasisPresentation.percentile(contract.copy(percentile = 1.4)))
        assertEquals("—", BasisPresentation.annualized(contract.copy(annualizedDiscount = Double.NaN)))
    }

    @Test fun displayingCachedDataNeverReplacesItsAbsoluteQuoteTime() {
        val state = BasisCacheState(snapshot = snapshot, receivedAtMillis = 9_999_999L, error = "network unavailable")
        assertEquals("2026-09-30 15:00:00", BasisPresentation.timestamp(state, contract))
        assertEquals("缓存", BasisPresentation.stateLabel(state, contract))
        assertEquals("2026-09-30", BasisPresentation.timestamp(state, null))
    }

    @Test fun expiredLiveQuotesAreIdentifiedWithoutClaimingFreshness() {
        val live = contract.copy(kind = "live", quoteTime = "2026-09-30T07:00:00Z")
        val quoteMillis = java.time.Instant.parse(live.quoteTime).toEpochMilli()
        assertEquals("待更新", BasisPresentation.stateLabel(BasisCacheState(snapshot = snapshot), live, quoteMillis + 120_001))
        assertEquals("待更新", BasisPresentation.stateLabel(BasisCacheState(snapshot = snapshot), live, quoteMillis - 5_001))
        assertEquals("采样快照", BasisPresentation.stateLabel(BasisCacheState(snapshot = snapshot), live, quoteMillis + 60_000))
    }

    @Test fun oldDailyCloseUsesSourceDateDespiteARecentCacheCheck() {
        val now = java.time.Instant.parse("2026-10-08T01:00:00Z").toEpochMilli()
        val state = BasisCacheState(snapshot = snapshot, checkedAtMillis = now, receivedAtMillis = now)
        assertEquals("待更新", BasisPresentation.stateLabel(state, contract, now))
        assertEquals("收盘", BasisPresentation.stateLabel(state, contract, now - 86_400_000))
        assertEquals("2026-09-30 15:00:00", BasisPresentation.timestamp(state, contract))
    }
}

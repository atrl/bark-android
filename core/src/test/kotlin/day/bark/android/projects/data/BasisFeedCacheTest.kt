package day.bark.android.projects.data

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BasisFeedCacheTest {
    @get:Rule val directory = TemporaryFolder()

    private fun fixture() = """
        {"schema_version":1,"family":"IC","as_of":"2026-09-30","generated_at":"2026-09-30T15:01:00+08:00",
         "source":"yanxuan","status":"ok","freshness":{"state":"close"},
         "method":{"metric":"annualized_carry_pct","units":"fraction","day_count":"ACT/365","higher_rank":"more_discounted"},
         "contracts":[{"contract":"IC2611.CFX","expiry":"2026-11-20","tenor":"next","kind":"close",
         "date":"2026-09-30","quote_time":"2026-09-30T15:00:00+08:00","dte":51,"near_expiry":false,
         "annualized_carry_pct":0.081,"status":"ok","reason":"","freshness":{"state":"close"},
         "percentile":{"metric":"annualized_carry_pct","rank":0.72,"samples":99,"status":"ok"},
         "series":[{"date":"2026-09-28","contract":"IC2611.CFX","expiry":"2026-11-20","annualized_carry_pct":0.07,"gap_before":false},
                   {"date":"2026-09-30","contract":"IC2611.CFX","expiry":"2026-11-20","annualized_carry_pct":0.081,"gap_before":true}]}]}
    """.trimIndent()

    @Test fun valuesAndGapsKeepTheExactMetricAndContractIdentity() {
        val contract = BasisSnapshot.parse(fixture(), "IC").contracts.single()
        assertEquals(0.081, contract.annualizedDiscount)
        assertEquals(0.72, contract.percentile)
        assertEquals("next", contract.tenor)
        assertTrue(contract.points.last().gapBefore)
        assertEquals("2026-09-30", contract.points.last().date)
    }

    @Test fun rejectWrongFamilyMetricContractAndUnorderedSeries() {
        listOf(
            fixture().replace("\"family\":\"IC\"", "\"family\":\"IM\""),
            fixture().replace("annualized_carry_pct", "annualized_basis_pct"),
            fixture().replace("\"date\":\"2026-09-28\",\"contract\":\"IC2611.CFX\"", "\"date\":\"2026-09-28\",\"contract\":\"IC2612.CFX\""),
            fixture().replace("2026-09-28", "2026-09-30"),
            fixture().replace("\"rank\":0.72", "\"rank\":1.1"),
        ).forEach { assertFailsWith<IllegalArgumentException> { BasisSnapshot.parse(it, "IC") } }
    }

    @Test fun insufficientSamplesCannotDisplayAnAvailableLookingPercentile() {
        val contract = BasisSnapshot.parse(fixture().replace("\"samples\":99", "\"samples\":12"), "IC").contracts.single()
        assertNull(contract.percentile)
        assertEquals(0.081, contract.annualizedDiscount)
    }

    @Test fun cacheSurvivesProcessRestartAnd304DoesNotChangeSourceOrReceiptTime() {
        var clock = 1_800_000_000_000L
        val cache = BasisFeedCache(directory.root, BasisFeedTransport { _, etag ->
            assertNull(etag); BasisFeedResponse(200, fixture(), "\"v1\"")
        }) { clock }
        val original = cache.refresh("IC")
        clock += 61_000
        val restarted = BasisFeedCache(directory.root, BasisFeedTransport { family, etag ->
            assertEquals("IC", family); assertEquals("\"v1\"", etag)
            BasisFeedResponse(304)
        }) { clock }
        assertEquals(original.snapshot, restarted.cached("IC").snapshot)
        val checked = restarted.refresh("IC")
        assertEquals(original.snapshot, checked.snapshot)
        assertEquals(original.receivedAtMillis, checked.receivedAtMillis)
        assertEquals(clock, checked.checkedAtMillis)
    }

    @Test fun failedAndMalformedResponsesKeepLastVerifiedSnapshotAndSourceTime() {
        var clock = 1_800_000_000_000L
        var response = BasisFeedResponse(200, fixture(), "\"v1\"")
        val cache = BasisFeedCache(directory.root, BasisFeedTransport { _, _ -> response }) { clock }
        val before = cache.refresh("IC")
        clock += 61_000
        response = BasisFeedResponse(503)
        val unavailable = cache.refresh("IC")
        assertEquals(before.snapshot, unavailable.snapshot)
        assertEquals(before.checkedAtMillis, unavailable.checkedAtMillis)
        assertNotNull(unavailable.error)
        clock += 61_000
        response = BasisFeedResponse(200, fixture().replace("annualized_carry_pct", "annualized_basis_pct"), "\"wrong\"")
        val invalid = cache.refresh("IC")
        assertEquals(before.snapshot, invalid.snapshot)
        assertNotNull(invalid.error)
        val restarted = BasisFeedCache(directory.root, BasisFeedTransport { _, _ -> throw IOException() }) { clock }
        assertEquals(before.snapshot, restarted.cached("IC").snapshot)
    }

    @Test fun parallelHomeAndWidgetRefreshesShareOneFetchAndManualTapsAreBounded() {
        val calls = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var clock = 1_800_000_000_000L
        val cache = BasisFeedCache(directory.root, BasisFeedTransport { _, _ ->
            calls.incrementAndGet(); started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            clock += 20_000 // A slow request must not make a queued manual tap refetch immediately.
            BasisFeedResponse(200, fixture())
        }) { clock }
        val pool = Executors.newFixedThreadPool(3)
        try {
            val first = pool.submit<BasisCacheState> { cache.refresh("IC") }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val second = pool.submit<BasisCacheState> { cache.refresh("IC", true) }
            release.countDown()
            assertNotNull(first.get(5, TimeUnit.SECONDS).snapshot)
            assertNotNull(second.get(5, TimeUnit.SECONDS).snapshot)
            assertEquals(1, calls.get())
            clock += 14_000
            cache.refresh("IC", true)
            assertEquals(1, calls.get())
            clock += 2_000
            cache.refresh("IC", true)
            assertEquals(2, calls.get())
        } finally { pool.shutdownNow() }
    }

    @Test fun corruptDiskAndUnknown304DoNotInventData() {
        directory.newFile("IC.json").writeText("not-json")
        val cache = BasisFeedCache(directory.root, BasisFeedTransport { _, _ -> BasisFeedResponse(304) })
        assertNull(cache.cached("IC").snapshot)
        assertNull(cache.refresh("IC").snapshot)
        assertFailsWith<IllegalArgumentException> { cache.cached("../IC") }
    }

    @Test fun successfulEmptyResponseCannotEraseLastGoodDataButMissingPercentileCanUpdate() {
        var clock = 1_800_000_000_000L
        var body = fixture()
        val cache = BasisFeedCache(directory.root, BasisFeedTransport { _, _ -> BasisFeedResponse(200, body, "\"data\"") }) { clock }
        val before = cache.refresh("IC")
        clock += 61_000
        body = JSONObject(fixture()).put("status", "needs_data").put("contracts", org.json.JSONArray()).toString()
        val empty = cache.refresh("IC")
        assertEquals(before.snapshot, empty.snapshot)
        assertNotNull(empty.error)
        val restored = BasisFeedCache(directory.root, BasisFeedTransport { _, _ -> throw IOException() }) { clock }
        assertEquals(before.snapshot, restored.cached("IC").snapshot)
        clock += 61_000
        body = fixture().replace("\"samples\":99", "\"samples\":12").replace("0.081", "0.09")
        val insufficientRank = cache.refresh("IC")
        assertNull(insufficientRank.error)
        assertEquals(0.09, insufficientRank.snapshot!!.contracts.single().annualizedDiscount)
        assertNull(insufficientRank.snapshot!!.contracts.single().percentile)
    }

    @Test fun checkingAgainCannotExtendALiveQuotesValidity() {
        val contract = BasisSnapshot.parse(fixture().replace("\"kind\":\"close\"", "\"kind\":\"live\""), "IC").contracts.single()
        val time = java.time.OffsetDateTime.parse(contract.quoteTime).toInstant().toEpochMilli()
        assertFalse(contract.liveQuoteExpired(time + 119_000))
        assertTrue(contract.liveQuoteExpired(time + 121_000))
        assertTrue(contract.liveQuoteExpired(time - 6_000))
    }

    @Test fun olderResponseKeepsNewerQuoteButAcceptsHistoryOtherMaturityAndStaleState() {
        var clock = 1_800_000_000_000L
        val original = JSONObject(fixture())
        original.getJSONArray("contracts").getJSONObject(0).apply {
            put("kind", "live").put("date", "2026-10-09").put("quote_time", "2026-10-09T13:30:00+08:00")
            put("annualized_carry_pct", 0.091).put("freshness", JSONObject().put("state", "live"))
        }
        var response = BasisFeedResponse(200, original.toString(), "\"v1\"")
        val cache = BasisFeedCache(directory.root, BasisFeedTransport { _, _ -> response }) { clock }
        cache.refresh("IC")
        clock += 61_000
        val regressed = JSONObject(fixture())
        regressed.getJSONArray("contracts").getJSONObject(0).getJSONArray("series").getJSONObject(0)
            .put("annualized_carry_pct", 0.071)
        val other = JSONObject(original.getJSONArray("contracts").getJSONObject(0).toString()
            .replace("IC2611.CFX", "IC2612.CFX").replace("2026-11-20", "2026-12-18"))
            .put("tenor", "quarter").put("quote_time", "2026-10-09T13:31:00+08:00")
        regressed.getJSONArray("contracts").put(other)
        response = BasisFeedResponse(200, regressed.toString(), "\"v2\"")
        val updated = cache.refresh("IC")
        assertNull(updated.error)
        val retained = updated.snapshot!!.contracts.first()
        assertEquals("2026-10-09T13:30:00+08:00", retained.quoteTime)
        assertEquals("2026-10-09", retained.dataDate)
        assertEquals(0.091, retained.annualizedDiscount)
        assertEquals("sampled", retained.kind)
        assertEquals("stale", retained.freshness)
        assertNull(retained.percentile)
        assertEquals(0.071, retained.points.first().value)
        assertEquals("2026-10-09T13:31:00+08:00", updated.snapshot!!.contracts.last().quoteTime)
        assertEquals("live", updated.snapshot!!.contracts.last().freshness)
        assertEquals("2026-10-09", updated.snapshot!!.asOf)
        val restarted = BasisFeedCache(directory.root, BasisFeedTransport { _, etag ->
            assertEquals("\"v2\"", etag)
            BasisFeedResponse(304)
        }) { clock + 61_000 }
        assertEquals(updated.snapshot, restarted.refresh("IC").snapshot)
    }

    @Test fun equalSourceTimeAcceptsSessionStateAndNewerFormalCloseReplacesSample() {
        var clock = 1_800_000_000_000L
        var body = fixture().replace("\"kind\":\"close\"", "\"kind\":\"live\"")
            .replace("2026-09-30T15:00:00", "2026-09-30T15:00:21")
        val cache = BasisFeedCache(directory.root, BasisFeedTransport { _, _ -> BasisFeedResponse(200, body) }) { clock }
        cache.refresh("IC")
        clock += 61_000
        body = body.replace("\"kind\":\"live\"", "\"kind\":\"sampled\"")
            .replace("\"state\":\"close\"", "\"state\":\"closed\"")
        val closed = cache.refresh("IC").snapshot!!.contracts.single()
        assertEquals("sampled", closed.kind)
        assertEquals("closed", closed.freshness)
        clock += 61_000
        body = fixture().replace("0.081", "0.082")
        val official = cache.refresh("IC").snapshot!!.contracts.single()
        assertEquals("close", official.kind)
        assertEquals("2026-09-30T15:00:00+08:00", official.quoteTime)
        assertEquals(0.082, official.annualizedDiscount)
        assertEquals("close", official.freshness)
    }

    @Test fun missingQuoteCannotEraseOneMaturityWhileOtherMaturitiesStillUpdate() {
        var clock = 1_800_000_000_000L
        var body = fixture().replace("\"kind\":\"close\"", "\"kind\":\"live\"")
        val cache = BasisFeedCache(directory.root, BasisFeedTransport { _, _ -> BasisFeedResponse(200, body) }) { clock }
        cache.refresh("IC")
        clock += 61_000
        val missing = JSONObject(fixture())
        missing.getJSONArray("contracts").getJSONObject(0).apply {
            put("kind", "missing").put("date", "").put("quote_time", "")
            put("annualized_carry_pct", JSONObject.NULL)
            getJSONArray("series").getJSONObject(0).put("annualized_carry_pct", 0.071)
        }
        val other = JSONObject(JSONObject(fixture()).getJSONArray("contracts").getJSONObject(0).toString()
            .replace("IC2611.CFX", "IC2612.CFX").replace("2026-11-20", "2026-12-18"))
            .put("tenor", "quarter").put("annualized_carry_pct", 0.093)
        missing.getJSONArray("contracts").put(other)
        body = missing.toString()
        val result = cache.refresh("IC")
        assertNull(result.error)
        val retained = result.snapshot!!.contracts.first()
        assertEquals(0.081, retained.annualizedDiscount)
        assertEquals("2026-09-30T15:00:00+08:00", retained.quoteTime)
        assertEquals("sampled", retained.kind)
        assertEquals("stale", retained.freshness)
        assertEquals(0.071, retained.points.first().value)
        assertEquals(0.093, result.snapshot!!.contracts.last().annualizedDiscount)
    }

    @Test fun newContractAtRollNeverInheritsAnOldContractQuote() {
        var clock = 1_800_000_000_000L
        var body = fixture()
        val cache = BasisFeedCache(directory.root, BasisFeedTransport { _, _ -> BasisFeedResponse(200, body) }) { clock }
        cache.refresh("IC")
        clock += 61_000
        body = fixture().replace("IC2611.CFX", "IC2612.CFX").replace("2026-11-20", "2026-12-18")
            .replace("2026-09-30T15:00:00", "2026-09-30T14:59:00").replace("0.081", "0.07")
        val rolled = cache.refresh("IC").snapshot!!.contracts.single()
        assertEquals("IC2612.CFX", rolled.code)
        assertEquals(0.07, rolled.annualizedDiscount)
        assertEquals("close", rolled.freshness)
    }

    @Test fun automaticRetryAfterOneMinuteFetchesAgainAnd304ClearsConnectionFailure() {
        var clock = 1_800_000_000_000L
        var response = BasisFeedResponse(200, fixture(), "\"v1\"")
        var calls = 0
        val cache = BasisFeedCache(directory.root, BasisFeedTransport { _, _ ->
            calls++
            response
        }) { clock }
        val before = cache.refresh("IC")
        clock += 60_000
        response = BasisFeedResponse(503)
        val failed = cache.refresh("IC")
        assertTrue(failed.retryableError)
        assertEquals(BasisRefreshFailure.CONNECTION, failed.failure)
        assertEquals(before.snapshot, failed.snapshot)
        clock += 59_999
        response = BasisFeedResponse(304)
        assertTrue(cache.refresh("IC").retryableError)
        assertEquals(2, calls)
        clock++
        val recovered = cache.refresh("IC")
        assertEquals(3, calls)
        assertNull(recovered.error)
        assertNull(recovered.failure)
        assertFalse(recovered.retryableError)
        assertEquals(before.snapshot, recovered.snapshot)
        assertEquals(before.receivedAtMillis, recovered.receivedAtMillis)
    }

    @Test fun retryOnlyTransientTransportFailuresAndPreserveFailureCause() {
        for (status in listOf(408, 425, 429, 500, 503)) {
            val cache = BasisFeedCache(directory.newFolder(), BasisFeedTransport { _, _ -> BasisFeedResponse(status) })
            assertTrue(cache.refresh("IC").retryableError, "HTTP $status should retry")
        }
        for (status in listOf(301, 400, 401, 403, 404)) {
            val cache = BasisFeedCache(directory.newFolder(), BasisFeedTransport { _, _ -> BasisFeedResponse(status) })
            val result = cache.refresh("IC")
            assertFalse(result.retryableError, "HTTP $status must not retry automatically")
            assertEquals(BasisRefreshFailure.CONNECTION, result.failure)
        }
        val offline = BasisFeedCache(directory.newFolder(), BasisFeedTransport { _, _ -> throw IOException("offline") })
            .refresh("IC")
        assertTrue(offline.retryableError)
        assertEquals(BasisRefreshFailure.CONNECTION, offline.failure)
        val malformed = BasisFeedCache(directory.newFolder(), BasisFeedTransport { _, _ -> BasisFeedResponse(200, "{") })
            .refresh("IC")
        assertFalse(malformed.retryableError)
        assertEquals(BasisRefreshFailure.DATA, malformed.failure)
        val staleBody = fixture().replace("\"status\":\"ok\"", "\"status\":\"needs_data\"")
        val stale = BasisFeedCache(directory.newFolder(), BasisFeedTransport { _, _ -> BasisFeedResponse(200, staleBody) })
            .refresh("IC")
        assertNull(stale.error)
        assertFalse(stale.retryableError)
    }
}

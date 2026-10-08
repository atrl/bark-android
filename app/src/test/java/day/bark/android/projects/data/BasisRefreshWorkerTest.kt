package day.bark.android.projects.data

import androidx.work.ListenableWorker
import org.junit.Assert.assertEquals
import org.junit.Test

class BasisRefreshWorkerTest {
    private val offline = BasisCacheState(error = "connection unavailable",
        failure = BasisRefreshFailure.CONNECTION, retryableError = true)

    @Test fun connectionFailureRetriesThreeTimesThenStopsThisRun() {
        var requests = 0
        for (attempt in 0..3) {
            val result = runBasisRefresh(setOf("IC"), attempt, { false }) {
                requests++
                offline
            }
            assertEquals(if (attempt < 3) ListenableWorker.Result.retry() else ListenableWorker.Result.failure(), result)
        }
        assertEquals(4, requests)
    }

    @Test fun recoveryCompletesTheRetryAndDoesNotRetryValidStaleData() {
        val stale = BasisCacheState(snapshot = BasisSnapshot("IC", "2026-09-30", "2026-10-08T09:00:00+08:00",
            "yanxuan", "stale", "needs_data", emptyList()))
        assertEquals(ListenableWorker.Result.retry(), runBasisRefresh(setOf("IC"), 0, { false }) { offline })
        assertEquals(ListenableWorker.Result.success(), runBasisRefresh(setOf("IC"), 1, { false }) { stale })
        assertEquals(ListenableWorker.Result.success(), runBasisRefresh(setOf("IC"), 3, { false }) { BasisCacheState() })
    }

    @Test fun aNewPeriodicCycleGetsItsOwnRetryBudgetAfterAnExhaustedCycle() {
        assertEquals(ListenableWorker.Result.failure(), runBasisRefresh(setOf("IC"), 3, { false }) { offline })
        // WorkManager resets runAttemptCount when periodic work returns to ENQUEUED.
        assertEquals(ListenableWorker.Result.retry(), runBasisRefresh(setOf("IC"), 0, { false }) { offline })
        assertEquals(ListenableWorker.Result.success(), runBasisRefresh(setOf("IC"), 1, { false }) { BasisCacheState() })
    }

    @Test fun oneFailedFamilyDoesNotSkipOthersAndNonTransientFailuresDoNotRetry() {
        val requested = mutableListOf<String>()
        val result = runBasisRefresh(linkedSetOf("IC", "IM", "IF", "invalid"), 0, { false }) { family ->
            requested += family
            if (family == "IC") offline else BasisCacheState()
        }
        assertEquals(listOf("IC", "IM", "IF"), requested)
        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(ListenableWorker.Result.failure(), runBasisRefresh(setOf("IC"), 0, { false }) {
            offline.copy(failure = BasisRefreshFailure.DATA, retryableError = false)
        })
        assertEquals(ListenableWorker.Result.failure(), runBasisRefresh(setOf("IC"), 0, { false }) {
            offline.copy(retryableError = false) // Permanent HTTP failure, such as 401.
        })
    }

    @Test fun stoppedWorkerDoesNotStartAnotherNetworkRequest() {
        var requests = 0
        assertEquals(ListenableWorker.Result.retry(), runBasisRefresh(setOf("IC"), 0, { true }) {
            requests++
            BasisCacheState()
        })
        assertEquals(0, requests)
    }
}

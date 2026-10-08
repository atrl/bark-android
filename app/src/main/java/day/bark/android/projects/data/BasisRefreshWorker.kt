package day.bark.android.projects.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import day.bark.android.projects.widget.BarkProjectWidgetProvider
import java.util.concurrent.TimeUnit

class BasisRefreshWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val families = inputData.getStringArray("families")?.toSet()
            ?: BarkProjectWidgetProvider.configuredFamilies(applicationContext)
        return runBasisRefresh(families, runAttemptCount, { isStopped }) { family ->
            BasisRepository.refresh(applicationContext, family, inputData.getBoolean("force", false))
        }
    }
}

/** Refresh each configured family, then choose one bounded outcome for the batch. */
internal fun runBasisRefresh(
    families: Set<String>,
    runAttemptCount: Int,
    isStopped: () -> Boolean,
    refresh: (String) -> BasisCacheState,
): ListenableWorker.Result {
    var failed = false
    var retryable = false
    for (family in families.intersect(BasisSnapshot.FAMILIES)) {
        if (isStopped()) return ListenableWorker.Result.retry()
        val state = refresh(family)
        failed = failed || state.error != null
        retryable = retryable || state.retryableError
    }
    // Initial request plus at most three retries. Valid stale snapshots do not retry.
    return when {
        retryable && runAttemptCount < 3 -> ListenableWorker.Result.retry()
        failed -> ListenableWorker.Result.failure()
        else -> ListenableWorker.Result.success()
    }
}

object BasisRefreshScheduler {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun request(context: Context, families: Set<String>, force: Boolean = false) {
        families.intersect(BasisSnapshot.FAMILIES).forEach { family ->
            WorkManager.getInstance(context).enqueueUniqueWork("basis-refresh-$family", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<BasisRefreshWorker>()
                    .setInputData(workDataOf("families" to arrayOf(family), "force" to force))
                    // A full minute also clears the shared cache's ordinary refresh cooldown.
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .setConstraints(network).build())
        }
    }

    fun configure(context: Context, families: Set<String>) {
        val manager = WorkManager.getInstance(context)
        if (families.intersect(BasisSnapshot.FAMILIES).isEmpty()) {
            manager.cancelUniqueWork("basis-widget-periodic")
        } else {
            manager.enqueueUniquePeriodicWork("basis-widget-periodic", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BasisRefreshWorker>(15, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .setConstraints(network).build())
        }
    }
}

package day.bark.android.projects.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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
        families.intersect(BasisSnapshot.FAMILIES).forEach { family ->
            if (!isStopped) BasisRepository.refresh(applicationContext, family, inputData.getBoolean("force", false))
        }
        // A failed refresh keeps the last snapshot; the next periodic/manual refresh can recover.
        return Result.success()
    }
}

object BasisRefreshScheduler {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun request(context: Context, families: Set<String>, force: Boolean = false) {
        families.intersect(BasisSnapshot.FAMILIES).forEach { family ->
            WorkManager.getInstance(context).enqueueUniqueWork("basis-refresh-$family", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<BasisRefreshWorker>()
                    .setInputData(workDataOf("families" to arrayOf(family), "force" to force))
                    .setConstraints(network).build())
        }
    }

    fun configure(context: Context, families: Set<String>) {
        val manager = WorkManager.getInstance(context)
        if (families.intersect(BasisSnapshot.FAMILIES).isEmpty()) {
            manager.cancelUniqueWork("basis-widget-periodic")
        } else {
            manager.enqueueUniquePeriodicWork("basis-widget-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BasisRefreshWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(network).build())
        }
    }
}

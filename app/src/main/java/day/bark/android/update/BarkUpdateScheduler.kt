package day.bark.android.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object BarkUpdateScheduler {
    fun onForeground(context: Context) {
        schedule(context)
        BarkUpdateInstaller.reconcile(context)
        val store = BarkUpdateStore(context)
        if (System.currentTimeMillis() - store.attemptedAt > TimeUnit.HOURS.toMillis(6)) check(context)
    }

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("bark-update-daily", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BarkUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf("operation" to "check")).build())
    }

    fun check(context: Context) {
        val store = BarkUpdateStore(context)
        store.attemptedAt = System.currentTimeMillis()
        store.status = "Update check queued"
        WorkManager.getInstance(context).enqueueUniqueWork("bark-update-check", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BarkUpdateWorker>().setInputData(workDataOf("operation" to "check"))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }

    fun download(context: Context, manual: Boolean) {
        val store = BarkUpdateStore(context)
        if (!manual && !store.automatic) return
        store.status = if (manual) "Download queued" else "Waiting for unmetered Wi-Fi to download"
        WorkManager.getInstance(context).enqueueUniqueWork(if (manual) "bark-update-manual-download" else "bark-update-auto-download",
            if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BarkUpdateWorker>().setInputData(workDataOf("operation" to "download", "manual" to manual))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (manual) NetworkType.CONNECTED else NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true).build()).build())
    }

    fun setAutomatic(context: Context, enabled: Boolean) {
        BarkUpdateStore(context).automatic = enabled
        if (enabled) check(context) else WorkManager.getInstance(context).cancelUniqueWork("bark-update-auto-download")
    }
}

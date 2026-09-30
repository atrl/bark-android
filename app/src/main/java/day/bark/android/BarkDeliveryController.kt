package day.bark.android

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.OutOfQuotaPolicy
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object BarkDeliveryController {
    /** Call only from a visible activity or the allowed boot receiver. */
    fun restore(context: Context) {
        val settings = BarkSettingsStore(context)
        if (!settings.listeningEnabled) {
            if (settings.stopPending) enqueueSync(context)
            return
        }
        enqueueSync(context)
        val periodic = PeriodicWorkRequestBuilder<BarkSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("bark-periodic-sync", ExistingPeriodicWorkPolicy.KEEP, periodic)
        resumePolling(context)
    }

    fun resumePolling(context: Context) {
        val settings = BarkSettingsStore(context)
        if (!settings.listeningEnabled || BarkPollingService.isRunning) return
        val targets = settings.serverProfiles().pollTargets()
        if (targets.any { !settings.isFcmRegistered(it) }) {
            try {
                context.startForegroundService(Intent(context, BarkPollingService::class.java))
            } catch (_: RuntimeException) {
                targets.filter { !settings.isFcmRegistered(it) }.forEach {
                    settings.setTransportStatus(it, "Background service unavailable; reopen app to resume")
                }
            }
        }
    }

    fun stop(context: Context) {
        val settings = BarkSettingsStore(context)
        settings.listeningEnabled = false
        settings.stopPending = true
        context.stopService(Intent(context, BarkPollingService::class.java))
        WorkManager.getInstance(context).cancelUniqueWork("bark-periodic-sync")
        enqueueSync(context)
    }

    fun enqueueSync(context: Context, priorityTarget: String? = null, urgent: Boolean = false) {
        val builder = OneTimeWorkRequestBuilder<BarkSyncWorker>().setConstraints(networkConstraints())
            .setInputData(workDataOf("priority_target" to priorityTarget))
        if (urgent) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        val work = builder.build()
        // APPEND preserves foreground receipt requests arriving during a running sync.
        WorkManager.getInstance(context).enqueueUniqueWork(
            priorityTarget?.let { "bark-delivery-$it" } ?: "bark-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, work,
        )
    }

    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}

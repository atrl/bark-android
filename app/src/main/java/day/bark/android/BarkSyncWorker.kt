package day.bark.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ForegroundInfo
import com.google.common.util.concurrent.ListenableFuture
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeUnit

class BarkSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val settings = BarkSettingsStore(context)

    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> = CallbackToFutureAdapter.getFuture { promise ->
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("bark_sync", "Bark message recovery", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(applicationContext, "bark_sync")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Receiving Bark message").build()
        promise.set(if (Build.VERSION.SDK_INT >= 34) ForegroundInfo(1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
            else ForegroundInfo(1002, notification))
        "Bark message recovery"
    }

    override fun doWork(): Result {
        // Message handling must not wait behind token refresh or an unrelated server.
        inputData.getString("priority_target")?.let { targetId ->
            if (!settings.listeningEnabled) return Result.success()
            val target = settings.serverProfiles().pollTargets().firstOrNull { it.id == targetId } ?: return Result.success()
            return try {
                var pages = 0
                while (pages++ < 10 && settings.listeningEnabled) {
                    if (!BarkSyncEngine(applicationContext).sync(target, allowLegacy = false)) break
                }
                Result.success()
            } catch (_: Exception) { Result.retry() }
        }
        return synchronized(transportLock) { configureAndSync() }
    }

    private fun configureAndSync(): Result {
        val targets = settings.serverProfiles().pollTargets()
        if (!settings.listeningEnabled) return stopDelivery(targets)
        val messaging = if (BuildConfig.FIREBASE_CONFIGURED &&
            GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(applicationContext) == ConnectionResult.SUCCESS &&
            FirebaseApp.getApps(applicationContext).isNotEmpty()
        ) FirebaseMessaging.getInstance() else null
        var tokenFailure = false
        val token = if (messaging != null) {
            try {
                messaging.isAutoInitEnabled = true
                @Suppress("DEPRECATION")
                Tasks.await(messaging.token, 30, TimeUnit.SECONDS).also { settings.fcmToken = it }
            } catch (_: Exception) { tokenFailure = true; null }
        } else null
        var retry = tokenFailure
        for (target in targets) {
            if (!settings.listeningEnabled) return stopDelivery(targets)
            val client = BarkServerClient(target.address, settings.installToken)
            try {
                if (token != null) {
                    try {
                        val canonical = client.setTransport(target.key, "fcm", token, settings.notificationMode())
                        settings.markFcmRegistered(target, token, canonical)
                    } catch (error: BarkHttpException) {
                        if (error.statusCode !in setOf(404, 503)) throw error
                        settings.markPolling(target, "Server FCM unavailable; confirming polling transport")
                        try { client.setTransport(target.key, "poll") } catch (fallback: BarkHttpException) {
                            if (fallback.statusCode != 404) throw fallback
                        }
                        settings.markPolling(target, if (error.statusCode == 503) "Server FCM not configured" else "Server has no FCM endpoint")
                    }
                } else {
                    settings.markPolling(target, "FCM unavailable; confirming polling transport")
                    try { client.setTransport(target.key, "poll") } catch (error: BarkHttpException) {
                        if (error.statusCode != 404) throw error
                    }
                    settings.markPolling(target, when {
                        !BuildConfig.FIREBASE_CONFIGURED -> "Firebase app config missing"
                        tokenFailure -> "FCM registration unavailable; retrying"
                        else -> "Google Play services unavailable"
                    })
                }
                var pages = 0
                while (pages++ < 10 && settings.listeningEnabled) {
                    if (!BarkSyncEngine(applicationContext).sync(target)) break
                }
            } catch (error: Exception) {
                settings.setTransportStatus(target, if (error is BarkHttpException && error.statusCode in setOf(401, 403))
                    "Server authentication failed; register this device again" else "Server sync failed; retrying")
                retry = true
            }
        }
        if (targets.isNotEmpty() && targets.all(settings::isFcmRegistered)) {
            applicationContext.stopService(Intent(applicationContext, BarkPollingService::class.java))
        }
        return if (retry) Result.retry() else Result.success()
    }

    private fun stopDelivery(targets: List<BarkPollTarget>): Result {
        if (!settings.stopPending) return Result.success()
        var failed = false
        targets.forEach { target ->
            try {
                try {
                    BarkServerClient(target.address, settings.installToken).setTransport(target.key, "poll")
                } catch (error: BarkHttpException) { if (error.statusCode != 404) throw error }
                settings.markPolling(target, "Off")
            } catch (_: Exception) { failed = true }
        }
        if (FirebaseApp.getApps(applicationContext).isNotEmpty()) {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = false
            try { Tasks.await(messaging.deleteToken(), 30, TimeUnit.SECONDS) } catch (_: Exception) { failed = true }
        }
        if (failed) return Result.retry()
        settings.fcmToken = null
        settings.stopPending = false
        return Result.success()
    }

    companion object { private val transportLock = Any() }
}

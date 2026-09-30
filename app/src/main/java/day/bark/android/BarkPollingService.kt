package day.bark.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class BarkPollingService : Service() {
    private lateinit var settings: BarkSettingsStore

    @Volatile
    private var running = false
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        settings = BarkSettingsStore(this)
        ensureServiceChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settings.listeningEnabled = false
            stopPolling()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!settings.listeningEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, serviceNotification("Listening for Bark pushes"))
        startPolling()
        return START_STICKY
    }

    override fun onDestroy() {
        stopPolling()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPolling() {
        if (running) return
        running = true
        isRunning = true
        worker = Thread(::pollLoop, "BarkPollingService").also { it.start() }
    }

    private fun stopPolling() {
        running = false
        isRunning = false
        worker?.interrupt()
        worker = null
    }

    private fun pollLoop() {
        while (running && settings.listeningEnabled) {
            try {
                val targets = settings.serverProfiles().pollTargets().filterNot(settings::isFcmRegistered)
                if (targets.isEmpty()) {
                    stopSelf()
                    return
                }
                val timeoutSeconds = pollTimeoutSeconds(targets.size)
                for (target in targets) {
                    if (!running) break
                    try {
                        BarkSyncEngine(this).sync(target, timeoutSeconds)
                    } catch (error: InterruptedException) {
                        throw error
                    } catch (error: Exception) {
                        settings.setTransportStatus(target, if (isMissingAndroidDeviceToken(error))
                            "Server does not recognize this Android registration" else "Connection failed; retrying")
                        Thread.sleep(3_000)
                    }
                }
            } catch (_: InterruptedException) {
                return
            } catch (_: Exception) {
                Thread.sleep(5_000)
            }
        }
    }

    private fun pollTimeoutSeconds(targetCount: Int): Int =
        if (targetCount <= 1) 30 else 5

    private fun isMissingAndroidDeviceToken(error: Exception): Boolean {
        val message = error.message.orEmpty()
        return message.contains("failed to get [android] device token", ignoreCase = true) ||
            message.contains("device is not registered as android", ignoreCase = true)
    }

    private fun ensureServiceChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Bark Service", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun serviceNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()

    companion object {
        @Volatile var isRunning: Boolean = false
            private set
        const val ACTION_STOP = "day.bark.android.STOP"
        private const val CHANNEL_SERVICE = "bark_service"
        private const val NOTIFICATION_ID = 1001
    }
}

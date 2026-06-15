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
    private lateinit var store: BarkMessageStore
    private lateinit var deliveredStore: BarkDeliveredNotificationStore
    private lateinit var notifier: BarkNotifier

    @Volatile
    private var running = false
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        settings = BarkSettingsStore(this)
        store = BarkMessageStore(this)
        deliveredStore = BarkDeliveredNotificationStore(this)
        notifier = BarkNotifier(this)
        ensureServiceChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settings.listeningEnabled = false
            stopPolling()
            stopSelf()
            return START_NOT_STICKY
        }
        settings.listeningEnabled = true
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
        worker = Thread(::pollLoop, "BarkPollingService").also { it.start() }
    }

    private fun stopPolling() {
        running = false
        worker?.interrupt()
        worker = null
    }

    private fun pollLoop() {
        while (running) {
            try {
                cancelExpiredNotifications()
                val targets = settings.serverProfiles().pollTargets()
                if (targets.isEmpty()) {
                    Thread.sleep(3_000)
                    continue
                }
                val timeoutSeconds = pollTimeoutSeconds(targets.size)
                for (target in targets) {
                    if (!running) break
                    try {
                        val payload = BarkServerClient(target.address).poll(target.key, timeoutSeconds = timeoutSeconds)
                        if (payload != null) {
                            handlePayload(payload)
                        }
                    } catch (error: InterruptedException) {
                        throw error
                    } catch (_: Exception) {
                        Thread.sleep(1_000)
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

    private fun handlePayload(payload: Map<String, Any?>) {
        val message = try {
            BarkPayloadProcessor.process(payload, settings.cryptoSettingsOrNull())
        } catch (_: Exception) {
            BarkMessage(
                id = java.util.UUID.randomUUID().toString(),
                title = "Bark",
                subtitle = null,
                body = "Decryption Failed",
                displayBody = "Decryption Failed",
                bodyType = null,
                url = null,
                image = null,
                icon = null,
                group = null,
                sound = null,
                badge = null,
                level = null,
                volume = null,
                call = false,
                autoCopy = false,
                copy = null,
                action = null,
                isDelete = false,
                shouldArchive = false,
                createAtMillis = System.currentTimeMillis(),
                expireAtMillis = null,
                extras = emptyMap(),
            )
        }

        if (message.isDelete) {
            val group = message.group ?: deliveredStore.groupFor(message.id) ?: store.groupFor(message.id)
            store.delete(message.id)
            deliveredStore.delete(message.id)
            notifier.cancel(message.id, group)
            return
        }
        val previousGroup = deliveredStore.groupFor(message.id) ?: store.groupFor(message.id)
        BarkNotificationGroupUpdate.staleGroupToCancel(previousGroup, message.group)?.let { staleGroup ->
            notifier.cancel(message.id, staleGroup)
            deliveredStore.delete(message.id)
        }
        if (BarkArchivePolicy.shouldStore(message, settings.archiveEnabled)) {
            store.save(message)
        }
        val notificationMessage = applyGroupMute(message)
        if (notifier.show(notificationMessage)) {
            deliveredStore.save(message.id, message.group)
        }
    }

    private fun cancelExpiredNotifications() {
        store.deleteExpired().forEach {
            deliveredStore.delete(it.id)
            notifier.cancel(it.id, it.group)
        }
    }

    private fun applyGroupMute(message: BarkMessage): BarkMessage {
        val group = BarkGroupMutePolicy.groupKey(message.group)
        return BarkGroupMutePolicy.apply(
            message = message,
            mutedUntilMillis = settings.groupMutedUntilMillis(group),
        )
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
        const val ACTION_STOP = "day.bark.android.STOP"
        private const val CHANNEL_SERVICE = "bark_service"
        private const val NOTIFICATION_ID = 1001
    }
}

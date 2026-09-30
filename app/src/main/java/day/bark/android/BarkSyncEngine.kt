package day.bark.android

import android.content.Context

class BarkSyncEngine(private val context: Context) {
    private val settings = BarkSettingsStore(context)

    fun sync(target: BarkPollTarget, timeoutSeconds: Int = 0, allowLegacy: Boolean = true): Boolean {
        val client = BarkServerClient(target.address, settings.installToken)
        val source = source(target)
        // Replay locally persisted work first, including a foreground callback that
        // arrived just after another worker acknowledged the same message.
        drain(source, client, target)
        val page = try {
            client.sync(target.key, timeoutSeconds)
        } catch (error: BarkHttpException) {
            if (error.statusCode != 404 || !allowLegacy || settings.isFcmRegistered(target)) throw error
            settings.setTransportStatus(target, "Legacy polling; server has no reliable inbox")
            client.poll(target.key, timeoutSeconds)?.let { payload ->
                synchronized(receiveLock) { BarkMessageReceiver(context).use { it.handlePayload(payload) } }
            }
            return false
        }
        synchronized(receiveLock) {
            BarkInboxStore(context).use { inbox -> BarkReliableDelivery(inbox).receive(source, page.messages) }
        }
        drain(source, client, target)
        return page.more
    }

    private fun drain(source: String, client: BarkServerClient, target: BarkPollTarget) {
        synchronized(receiveLock) {
            BarkInboxStore(context).use { inbox ->
                val reliable = BarkReliableDelivery(inbox)
                BarkMessageReceiver(context).use { receiver ->
                    receiver.cancelExpiredNotifications()
                    reliable.process(source) { delivery, display, archive ->
                        receiver.handlePayload(delivery.payload, display, archive, delivery.createdAtMillis, delivery.deliveryId, delivery.notificationTag, "${target.id}\n${target.key}")
                    }
                }
                reliable.acknowledge(source) { client.acknowledge(target.key, it) }
                inbox.prune()
            }
        }
    }

    companion object {
        private val receiveLock = Any()
        fun source(target: BarkPollTarget): String = "${target.address.trimEnd('/')}\n${target.key}"
    }
}

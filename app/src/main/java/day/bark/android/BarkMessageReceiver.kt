package day.bark.android

import android.content.Context

/** All transports use the same decrypt/archive/update/delete implementation. */
class BarkMessageReceiver(context: Context) : java.io.Closeable {
    private val settings = BarkSettingsStore(context)
    private val store = BarkMessageStore(context)
    private val deliveredStore = BarkDeliveredNotificationStore(context)
    private val notifier = BarkNotifier(context)

    fun handlePayload(payload: Map<String, Any?>, alert: BarkDeliveryAlert = BarkDeliveryAlert.NORMAL, archive: Boolean = true, createdAtMillis: Long = System.currentTimeMillis(), deliveryId: String? = null, notificationTag: String? = null, localScope: String? = null) {
        val decoded = try {
            BarkPayloadProcessor.process(
                if (payload["id"]?.toString().isNullOrBlank() && deliveryId != null) payload + ("id" to deliveryId) else payload,
                settings.cryptoSettingsOrNull(), createdAtMillis,
            )
        } catch (_: Exception) {
            BarkMessage(
                id = deliveryId ?: java.util.UUID.randomUUID().toString(),
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
                createAtMillis = createdAtMillis,
                expireAtMillis = null,
                extras = emptyMap(),
            )
        }

        // Namespace the decoded business ID, including IDs inside encrypted payloads.
        // The independent server tag still identifies the OS notification.
        val message = if (localScope != null) decoded.copy(id = BarkLocalMessageIdentity.id(localScope, decoded.id)) else decoded
        if (message.expireAtMillis?.let { it <= System.currentTimeMillis() } == true) {
            notifier.cancel(message.id, message.group, notificationTag)
            store.delete(message.id)
            deliveredStore.delete(message.id)
            return
        }
        if (message.isDelete) {
            val group = message.group ?: deliveredStore.groupFor(message.id) ?: store.groupFor(message.id)
            store.delete(message.id)
            notifier.cancel(message.id, group, notificationTag)
            deliveredStore.delete(message.id)
            return
        }
        if (notificationTag != null) {
            deliveredStore.tagsFor(message.id).filter { it != notificationTag }.forEach(notifier::cancelTag)
        }
        val previousGroup = deliveredStore.groupFor(message.id) ?: store.groupFor(message.id)
        BarkNotificationGroupUpdate.staleGroupToCancel(previousGroup, message.group)?.let { staleGroup ->
            notifier.cancel(message.id, staleGroup, notificationTag)
            deliveredStore.delete(message.id)
        }
        if (archive && BarkArchivePolicy.shouldStore(message, settings.archiveEnabled)) {
            store.save(message)
        }
        val notificationMessage = applyGroupMute(message)
        // Acceptance is not display evidence. Replace the stable system tag
        // quietly during catch-up; an older update may still occupy that tag.
        if (alert != BarkDeliveryAlert.NONE &&
            notifier.show(notificationMessage, notificationTag, quiet = alert == BarkDeliveryAlert.QUIET || settings.groupMutedUntilMillis(BarkGroupMutePolicy.groupKey(message.group)) != null)) {
            deliveredStore.save(message.id, message.group, notificationTag)
        }
    }

    fun cancelExpiredNotifications() {
        store.deleteExpired().forEach {
            notifier.cancel(it.id, it.group)
            deliveredStore.delete(it.id)
        }
    }

    private fun applyGroupMute(message: BarkMessage): BarkMessage {
        val group = BarkGroupMutePolicy.groupKey(message.group)
        return BarkGroupMutePolicy.apply(
            message = message,
            mutedUntilMillis = settings.groupMutedUntilMillis(group),
        )
    }

    override fun close() { store.close() }
}

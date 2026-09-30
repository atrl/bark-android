package day.bark.android

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class BarkFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val settings = BarkSettingsStore(this)
        settings.fcmToken = token
        if (settings.listeningEnabled) BarkDeliveryController.enqueueSync(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val settings = BarkSettingsStore(this)
        if (!settings.listeningEnabled) return
        val deliveryId = message.data["bark_delivery_id"]?.takeIf { it.isNotBlank() } ?: return
        val server = message.data["bark_server_url"]?.trimEnd('/') ?: return
        // Treat the hint as an identifier, never as a URL to fetch. Only saved
        // servers receive our authentication token.
        val targets = settings.serverProfiles().pollTargets().filter { settings.canonicalServer(it) == server || it.address.trimEnd('/') == server }
        if (targets.isEmpty()) return
        BarkInboxStore(this).use { inbox ->
            targets.forEach { inbox.requestForeground(BarkSyncEngine.source(it), deliveryId) }
        }
        targets.forEach { target ->
            BarkDeliveryController.enqueueSync(this, target.id, urgent = message.priority == RemoteMessage.PRIORITY_HIGH)
        }
    }

    override fun onDeletedMessages() {
        if (BarkSettingsStore(this).listeningEnabled) BarkDeliveryController.enqueueSync(this)
    }
}

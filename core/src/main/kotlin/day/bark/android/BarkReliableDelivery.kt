package day.bark.android

/** Delivery identity is independent of Bark's editable business message id. */
data class BarkDelivery(
    val deliveryId: String,
    val payload: Map<String, Any?>,
    val createdAtMillis: Long,
    val fcmAccepted: Boolean,
    val notificationTag: String? = null,
)

data class BarkInboxEntry(
    val delivery: BarkDelivery,
    val processed: Boolean = false,
    val notificationHandled: Boolean = false,
    val foregroundRequested: Boolean = false,
    val acknowledged: Boolean = false,
)

interface BarkDeliveryInbox {
    fun stage(source: String, delivery: BarkDelivery)
    fun requestForeground(source: String, deliveryId: String)
    fun pending(source: String): List<BarkInboxEntry>
    fun markProcessed(source: String, deliveryId: String, notificationHandled: Boolean)
    fun awaitingAck(source: String): List<String>
    fun markAcknowledged(source: String, deliveryIds: List<String>)
}

/**
 * Persist before processing; ACK only after processing succeeds. A lost ACK retries
 * without replaying UI effects, even when visible message archiving is disabled.
 */
enum class BarkDeliveryAlert { NONE, NORMAL, QUIET }

class BarkReliableDelivery(private val inbox: BarkDeliveryInbox) {
    fun receive(source: String, deliveries: List<BarkDelivery>) {
        deliveries.forEach { inbox.stage(source, it) }
    }

    fun process(source: String, handler: (BarkDelivery, BarkDeliveryAlert, Boolean) -> Unit) {
        inbox.pending(source).forEach { entry ->
            val alert = when {
                entry.notificationHandled -> BarkDeliveryAlert.NONE
                entry.foregroundRequested || !entry.delivery.fcmAccepted -> BarkDeliveryAlert.NORMAL
                else -> BarkDeliveryAlert.QUIET
            }
            handler(entry.delivery, alert, !entry.processed)
            inbox.markProcessed(source, entry.delivery.deliveryId, true)
        }
    }

    fun acknowledge(source: String, send: (List<String>) -> Unit) {
        inbox.awaitingAck(source).chunked(100).forEach { ids ->
            send(ids)
            inbox.markAcknowledged(source, ids)
        }
    }
}

/** Local identity keeps server/device and decrypted business IDs independent. */
object BarkLocalMessageIdentity {
    fun id(source: String, businessId: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ":" + businessId
}

package day.bark.android

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BarkReliableDeliveryTest {
    private val source = "https://bark.example\ndevice"
    private fun delivery(id: String = "delivery-1", accepted: Boolean = false) = BarkDelivery(
        id, mapOf("id" to "editable-business-id", "body" to id), 1000L, accepted,
    )

    @Test fun lostAckRetriesWithoutReplayingEffectsEvenWithoutHistory() {
        val inbox = MemoryInbox()
        val first = BarkReliableDelivery(inbox)
        first.receive(source, listOf(delivery()))
        var notifications = 0
        first.process(source) { _, alert, _ -> if (alert != BarkDeliveryAlert.NONE) notifications++ }
        assertFailsWith<IllegalStateException> { first.acknowledge(source) { error("network lost") } }
        val restarted = BarkReliableDelivery(inbox)
        restarted.receive(source, listOf(delivery()))
        restarted.process(source) { _, _, _ -> error("must not process twice") }
        var ackIds = emptyList<String>()
        restarted.acknowledge(source) { ackIds = it }
        assertEquals(listOf("delivery-1"), ackIds)
        assertEquals(1, notifications)
        assertEquals(emptyList(), inbox.awaitingAck(source))
    }

    @Test fun processingFailureIsNotAcknowledgedAndIsRecoverableAfterRestart() {
        val inbox = MemoryInbox()
        val first = BarkReliableDelivery(inbox)
        first.receive(source, listOf(delivery()))
        assertFailsWith<IllegalStateException> { first.process(source) { _, _, _ -> error("storage full") } }
        first.acknowledge(source) { error("must not acknowledge failed processing") }
        val restarted = BarkReliableDelivery(inbox)
        var processed = 0
        restarted.process(source) { _, _, _ -> processed++ }
        restarted.acknowledge(source) { assertEquals(listOf("delivery-1"), it) }
        assertEquals(1, processed)
    }

    @Test fun updatesWithSameBusinessIdAreDistinctDeliveries() {
        val inbox = MemoryInbox()
        val engine = BarkReliableDelivery(inbox)
        engine.receive(source, listOf(delivery("version-1"), delivery("version-2")))
        val bodies = mutableListOf<Any?>()
        engine.process(source) { message, _, _ -> bodies += message.payload["body"] }
        assertEquals<List<Any?>>(listOf("version-1", "version-2"), bodies)
    }

    @Test fun fcmAcceptanceDoesNotClaimTheDeviceDisplayedIt() {
        val inbox = MemoryInbox()
        val engine = BarkReliableDelivery(inbox)
        engine.receive(source, listOf(delivery(accepted = true)))
        engine.process(source) { _, alert, archive ->
            assertEquals(BarkDeliveryAlert.QUIET, alert)
            assertEquals(true, archive)
        }
    }

    @Test fun foregroundCallbackSurvivesWorkerRestartAndDoesNotDependOnHistory() {
        val inbox = MemoryInbox()
        inbox.requestForeground(source, "delivery-1")
        val restarted = BarkReliableDelivery(inbox)
        restarted.receive(source, listOf(delivery(accepted = true)))
        restarted.process(source) { _, alert, _ -> assertEquals(BarkDeliveryAlert.NORMAL, alert) }
        inbox.requestForeground(source, "delivery-1")
        restarted.process(source) { _, _, _ -> error("duplicate callback must not notify twice") }
    }

    @Test fun separateServersDoNotShareReceipts() {
        val inbox = MemoryInbox()
        val engine = BarkReliableDelivery(inbox)
        engine.receive(source, listOf(delivery()))
        engine.receive("other-server", listOf(delivery()))
        var count = 0
        engine.process(source) { _, _, _ -> count++ }
        engine.process("other-server") { _, _, _ -> count++ }
        assertEquals(2, count)
    }

    @Test fun encryptedUpdatesKeepDecodedBusinessIdentityAcrossDeliveryIds() {
        val crypto = CryptoSettings("AES128", "CBC", "pkcs7", "0123456789abcdef", "abcdef0123456789")
        fun decode(deliveryId: String, body: String) = BarkPayloadProcessor.process(mapOf(
            "id" to deliveryId,
            "ciphertext" to AesCipher.encrypt("""{"id":"encrypted-update","body":"$body"}""", crypto),
        ), crypto)
        val first = decode("delivery-1", "before")
        val next = decode("delivery-2", "after")
        assertEquals(BarkLocalMessageIdentity.id(source, first.id), BarkLocalMessageIdentity.id(source, next.id))
        assertEquals("after", next.body)
        org.junit.Assert.assertNotEquals(BarkLocalMessageIdentity.id(source, next.id), BarkLocalMessageIdentity.id("other-server", next.id))
    }

    private class MemoryInbox : BarkDeliveryInbox {
        private val rows = linkedMapOf<Pair<String, String>, BarkInboxEntry>()
        private val requested = mutableSetOf<Pair<String, String>>()
        override fun stage(source: String, delivery: BarkDelivery) {
            rows.putIfAbsent(source to delivery.deliveryId, BarkInboxEntry(delivery))
        }
        override fun requestForeground(source: String, deliveryId: String) { requested += source to deliveryId }
        override fun pending(source: String): List<BarkInboxEntry> = rows.filterKeys { it.first == source }
            .map { (key, row) -> row.copy(foregroundRequested = key in requested) }
            .filter { !it.processed || (!it.notificationHandled && it.foregroundRequested) }
        override fun markProcessed(source: String, deliveryId: String, notificationHandled: Boolean) {
            val key = source to deliveryId
            rows[key] = rows.getValue(key).copy(processed = true, notificationHandled = notificationHandled)
        }
        override fun awaitingAck(source: String) = rows.filter { (key, row) -> key.first == source && row.processed && !row.acknowledged }.keys.map { it.second }
        override fun markAcknowledged(source: String, deliveryIds: List<String>) {
            deliveryIds.forEach { id -> val key = source to id; rows[key] = rows.getValue(key).copy(acknowledged = true) }
        }
    }
}

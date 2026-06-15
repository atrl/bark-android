package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarkPayloadProcessorTest {
    @Test
    fun `normalizes bark fields and stringifies extension parameters`() {
        val message = BarkPayloadProcessor.process(
            raw = mapOf(
                "title" to "Title",
                "subtitle" to "Subtitle",
                "body" to "Body",
                "group" to "ops",
                "url" to "https://day.app",
                "sound" to "bell.caf",
                "badge" to 7,
                "level" to "timeSensitive",
                "volume" to "7",
                "call" to "1",
                "autoCopy" to "1",
                "copy" to "copy me",
                "unknown" to 42,
            ),
            cryptoSettings = null,
            nowMillis = 1_700_000_000_000,
        )

        assertEquals("Title", message.title)
        assertEquals("Subtitle", message.subtitle)
        assertEquals("Body", message.body)
        assertEquals("ops", message.group)
        assertEquals("https://day.app", message.url)
        assertEquals("bell.caf", message.sound)
        assertEquals(7, message.badge)
        assertEquals("timeSensitive", message.level)
        assertEquals(7.0f, message.volume)
        assertTrue(message.call)
        assertTrue(message.autoCopy)
        assertEquals("copy me", message.copy)
        assertEquals("42", message.extras["unknown"])
    }

    @Test
    fun `ttl less than or equal to zero disables archive only`() {
        val message = BarkPayloadProcessor.process(
            raw = mapOf(
                "body" to "Display this but do not archive",
                "ttl" to "0",
                "isArchive" to "1",
            ),
            cryptoSettings = null,
            nowMillis = 1_700_000_000_000,
        )

        assertEquals("Display this but do not archive", message.body)
        assertFalse(message.shouldArchive)
        assertEquals(true, message.archiveOverride)
        assertEquals(null, message.expireAtMillis)
    }

    @Test
    fun `isArchive payload is retained as explicit archive override`() {
        val forceArchive = BarkPayloadProcessor.process(
            raw = mapOf("body" to "force", "isArchive" to "1"),
            cryptoSettings = null,
            nowMillis = 1_700_000_000_000,
        )
        val skipArchive = BarkPayloadProcessor.process(
            raw = mapOf("body" to "skip", "isArchive" to "0"),
            cryptoSettings = null,
            nowMillis = 1_700_000_000_000,
        )
        val defaultArchive = BarkPayloadProcessor.process(
            raw = mapOf("body" to "default"),
            cryptoSettings = null,
            nowMillis = 1_700_000_000_000,
        )

        assertEquals(true, forceArchive.archiveOverride)
        assertTrue(forceArchive.shouldArchive)
        assertEquals(false, skipArchive.archiveOverride)
        assertFalse(skipArchive.shouldArchive)
        assertEquals(null, defaultArchive.archiveOverride)
        assertTrue(defaultArchive.shouldArchive)
    }

    @Test
    fun `decrypts AES CBC ciphertext using supplied iv`() {
        val message = BarkPayloadProcessor.process(
            raw = mapOf(
                "ciphertext" to "+aPt5cwN9GbTLLSFri60l3h1X00u/9j1FENfWiTxhNHVLGU+XoJ15JJG5W/d/yf0",
                "iv" to "1234567890123456",
            ),
            cryptoSettings = CryptoSettings(
                algorithm = "AES128",
                mode = "CBC",
                padding = "pkcs7",
                key = "1234567890123456",
                iv = "0000000000000000",
            ),
            nowMillis = 1_700_000_000_000,
        )

        assertEquals("test", message.body)
        assertEquals("birdsong.caf", message.sound)
    }

    @Test
    fun `encrypted payload keeps outer notification id when decrypted json omits it`() {
        val message = BarkPayloadProcessor.process(
            raw = mapOf(
                "id" to "stable-encrypted-id",
                "ciphertext" to "+aPt5cwN9GbTLLSFri60l3h1X00u/9j1FENfWiTxhNHVLGU+XoJ15JJG5W/d/yf0",
                "iv" to "1234567890123456",
            ),
            cryptoSettings = CryptoSettings(
                algorithm = "AES128",
                mode = "CBC",
                padding = "pkcs7",
                key = "1234567890123456",
                iv = "0000000000000000",
            ),
            nowMillis = 1_700_000_000_000,
        )

        assertEquals("stable-encrypted-id", message.id)
        assertEquals("test", message.body)
    }

    @Test
    fun `delete payload targets existing notification and is never archived`() {
        val message = BarkPayloadProcessor.process(
            raw = mapOf(
                "id" to "message-to-delete",
                "delete" to "1",
                "body" to "server filler body",
                "group" to "ops",
            ),
            cryptoSettings = null,
            nowMillis = 1_700_000_000_000,
        )

        assertTrue(message.isDelete)
        assertEquals("message-to-delete", message.id)
        assertEquals("ops", message.group)
        assertFalse(message.shouldArchive)
    }

    @Test
    fun `boolean json flags are treated as enabled`() {
        val message = BarkPayloadProcessor.process(
            raw = mapOf(
                "id" to "boolean-flags",
                "body" to "Payload sent by REST JSON",
                "call" to true,
                "autoCopy" to true,
                "delete" to true,
            ),
            cryptoSettings = null,
            nowMillis = 1_700_000_000_000,
        )

        assertTrue(message.call)
        assertTrue(message.autoCopy)
        assertTrue(message.isDelete)
        assertFalse(message.shouldArchive)
    }

    @Test
    fun `boolean json isArchive values are retained as explicit archive overrides`() {
        val forceArchive = BarkPayloadProcessor.process(
            raw = mapOf("body" to "force", "isArchive" to true),
            cryptoSettings = null,
            nowMillis = 1_700_000_000_000,
        )
        val skipArchive = BarkPayloadProcessor.process(
            raw = mapOf("body" to "skip", "isArchive" to false),
            cryptoSettings = null,
            nowMillis = 1_700_000_000_000,
        )

        assertEquals(true, forceArchive.archiveOverride)
        assertTrue(forceArchive.shouldArchive)
        assertEquals(false, skipArchive.archiveOverride)
        assertFalse(skipArchive.shouldArchive)
    }
}

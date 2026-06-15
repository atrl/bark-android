package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONArray

class BarkMessageBackupCodecTest {
    @Test
    fun `exports iOS compatible fields and preserves Android fields`() {
        val json = BarkMessageBackupCodec.encode(
            listOf(
                BarkMessage(
                    id = "message-1",
                    title = "Title",
                    subtitle = "Subtitle",
                    body = "# Body",
                    displayBody = "Body",
                    bodyType = "markdown",
                    url = "https://day.app",
                    image = "https://example.com/image.png",
                    icon = "https://example.com/icon.png",
                    group = "ops",
                    sound = "bell.caf",
                    badge = 7,
                    level = "critical",
                    volume = 8.5f,
                    call = true,
                    autoCopy = true,
                    copy = "copy me",
                    action = "copy",
                    isDelete = false,
                    shouldArchive = true,
                    archiveOverride = true,
                    createAtMillis = 1_700_000_000_123L,
                    expireAtMillis = 1_700_000_060_999L,
                    extras = mapOf("trace" to "abc123"),
                ),
            ),
        )

        val item = JSONArray(json).getJSONObject(0)
        assertEquals("message-1", item.getString("id"))
        assertEquals("Title", item.getString("title"))
        assertEquals("Subtitle", item.getString("subtitle"))
        assertEquals("# Body", item.getString("body"))
        assertEquals("markdown", item.getString("bodyType"))
        assertEquals("https://day.app", item.getString("url"))
        assertEquals("https://example.com/image.png", item.getString("image"))
        assertEquals("ops", item.getString("group"))
        assertEquals(1_700_000_000L, item.getLong("createDate"))
        assertEquals(1_700_000_060L, item.getLong("expireDate"))
        assertEquals("Body", item.getString("displayBody"))
        assertEquals("https://example.com/icon.png", item.getString("icon"))
        assertEquals("bell.caf", item.getString("sound"))
        assertEquals(7, item.getInt("badge"))
        assertEquals("critical", item.getString("level"))
        assertEquals(8.5, item.getDouble("volume"))
        assertTrue(item.getBoolean("call"))
        assertTrue(item.getBoolean("autoCopy"))
        assertEquals("copy me", item.getString("copy"))
        assertEquals("copy", item.getString("action"))
        assertTrue(item.getBoolean("shouldArchive"))
        assertTrue(item.getBoolean("archiveOverride"))
        assertEquals("abc123", item.getJSONObject("extras").getString("trace"))
    }

    @Test
    fun `imports iOS backup messages using second based dates`() {
        val messages = BarkMessageBackupCodec.decode(
            """
            [
              {
                "id": "ios-message",
                "title": "iOS Title",
                "subtitle": "iOS Subtitle",
                "body": "**markdown**",
                "bodyType": "markdown",
                "url": "https://day.app",
                "image": "https://example.com/image.png",
                "group": "ios",
                "createDate": 1700000000,
                "expireDate": 1700000060
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1, messages.size)
        val message = messages.single()
        assertEquals("ios-message", message.id)
        assertEquals("iOS Title", message.title)
        assertEquals("iOS Subtitle", message.subtitle)
        assertEquals("**markdown**", message.body)
        assertEquals("markdown", message.bodyType)
        assertEquals("markdown", message.displayBody)
        assertEquals("https://day.app", message.url)
        assertEquals("https://example.com/image.png", message.image)
        assertEquals("ios", message.group)
        assertEquals(1_700_000_000_000L, message.createAtMillis)
        assertEquals(1_700_000_060_000L, message.expireAtMillis)
        assertFalse(message.isDelete)
        assertTrue(message.shouldArchive)
        assertNull(message.archiveOverride)
    }

    @Test
    fun `skips invalid backup entries the same way iOS restore does`() {
        val messages = BarkMessageBackupCodec.decode(
            """
            [
              {"id": "missing-date", "body": "ignored"},
              {"createDate": 1700000000, "body": "ignored"},
              {"id": "valid", "body": "kept", "createDate": 1700000001}
            ]
            """.trimIndent(),
        )

        assertEquals(listOf("valid"), messages.map { it.id })
        assertEquals("kept", messages.single().body)
    }
}

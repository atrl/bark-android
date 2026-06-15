package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BarkPushRequestTest {
    @Test
    fun `shared text becomes bark push body with optional subject title`() {
        val request = BarkPushRequest.fromSharedText(
            subject = "Shared title",
            text = "  Shared body  ",
        )

        assertEquals(
            mapOf(
                "title" to "Shared title",
                "body" to "Shared body",
            ),
            request.toParameters(),
        )
    }

    @Test
    fun `blank shared text falls back to empty notification body`() {
        val request = BarkPushRequest.fromSharedText(
            subject = " ",
            text = null,
        )

        assertEquals(mapOf("body" to "Empty Notification"), request.toParameters())
    }

    @Test
    fun `push request maps advanced bark options like app intents`() {
        val request = BarkPushRequest(
            title = "Deploy",
            subtitle = "Production",
            body = "Done",
            isCall = true,
            isCritical = true,
            volume = 7,
            sound = "bell",
            icon = "https://day.app/icon.png",
            group = "ops",
        )

        assertEquals("Deploy", request.toParameters()["title"])
        assertEquals("Production", request.toParameters()["subtitle"])
        assertEquals("Done", request.toParameters()["body"])
        assertEquals(1, request.toParameters()["call"])
        assertEquals("critical", request.toParameters()["level"])
        assertEquals(7, request.toParameters()["volume"])
        assertEquals("bell", request.toParameters()["sound"])
        assertEquals("https://day.app/icon.png", request.toParameters()["icon"])
        assertEquals("ops", request.toParameters()["group"])
        assertFalse(request.toParameters().containsKey("isCritical"))
    }

    @Test
    fun `push request maps full bark server api options`() {
        val request = BarkPushRequest(
            title = "Title",
            subtitle = "Subtitle",
            body = "Body",
            id = "notification-id",
            markdown = "**Markdown**",
            level = "timeSensitive",
            volume = 5,
            badge = 9,
            isCall = true,
            autoCopy = true,
            copy = "copy value",
            sound = "minuet",
            icon = "https://day.app/icon.png",
            image = "https://day.app/image.png",
            group = "ops",
            archive = false,
            ttlSeconds = 60,
            url = "https://day.app",
            action = "none",
            ciphertext = "encrypted payload",
            iv = "1234567890123456",
            isDelete = true,
        )

        val params = request.toParameters()

        assertEquals("Title", params["title"])
        assertEquals("Subtitle", params["subtitle"])
        assertEquals("Body", params["body"])
        assertEquals("notification-id", params["id"])
        assertEquals("**Markdown**", params["markdown"])
        assertEquals("timeSensitive", params["level"])
        assertEquals(5, params["volume"])
        assertEquals(9, params["badge"])
        assertEquals(1, params["call"])
        assertEquals(1, params["autoCopy"])
        assertEquals("copy value", params["copy"])
        assertEquals("minuet", params["sound"])
        assertEquals("https://day.app/icon.png", params["icon"])
        assertEquals("https://day.app/image.png", params["image"])
        assertEquals("ops", params["group"])
        assertEquals("0", params["isArchive"])
        assertEquals(60L, params["ttl"])
        assertEquals("https://day.app", params["url"])
        assertEquals("none", params["action"])
        assertEquals("encrypted payload", params["ciphertext"])
        assertEquals("1234567890123456", params["iv"])
        assertEquals(1, params["delete"])
    }
}

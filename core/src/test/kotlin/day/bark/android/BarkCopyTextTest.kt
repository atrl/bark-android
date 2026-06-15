package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals

class BarkCopyTextTest {
    @Test
    fun `explicit copy payload wins over rendered notification content`() {
        val message = message(copy = "copy this value")

        assertEquals("copy this value", BarkCopyText.from(message))
    }

    @Test
    fun `copy text joins title subtitle body url and image with new lines`() {
        val message = message(copy = null)

        assertEquals(
            "Title\nSubtitle\nBody\nhttps://day.app\nhttps://day.app/image.png",
            BarkCopyText.from(message),
        )
    }

    private fun message(copy: String?) = BarkMessage(
        id = "message-id",
        title = "Title",
        subtitle = "Subtitle",
        body = "Raw **markdown**",
        displayBody = "Body",
        bodyType = "markdown",
        url = "https://day.app",
        image = "https://day.app/image.png",
        icon = null,
        group = "ops",
        sound = null,
        badge = null,
        level = null,
        volume = null,
        call = false,
        autoCopy = false,
        copy = copy,
        action = null,
        isDelete = false,
        shouldArchive = true,
        createAtMillis = 1_700_000_000_000,
        expireAtMillis = null,
        extras = emptyMap(),
    )
}

package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals

class BarkHistoryMessageTextTest {
    @Test
    fun `history text includes url after message body`() {
        val message = message(
            title = "Deploy",
            subtitle = "Production",
            displayBody = "Finished",
            url = "https://day.app/deploy",
            group = "ops",
        )

        assertEquals(
            "Deploy\nProduction\nFinished\nhttps://day.app/deploy\nJan 2, 2026 03:04  #ops",
            BarkHistoryMessageText.format(message, "Jan 2, 2026 03:04"),
        )
    }

    @Test
    fun `history text appends expiry text to date line`() {
        val message = message(
            title = "Deploy",
            subtitle = null,
            displayBody = "Finished",
            url = null,
            group = "ops",
        )

        assertEquals(
            "Deploy\nFinished\nJan 2, 2026 03:04 · Expires in 2h  #ops",
            BarkHistoryMessageText.format(message, "Jan 2, 2026 03:04", "Expires in 2h"),
        )
    }

    @Test
    fun `history text omits blank optional fields`() {
        val message = message(
            title = null,
            subtitle = " ",
            displayBody = "",
            url = null,
            group = null,
        )

        assertEquals(
            "Bark\nJan 2, 2026 03:04",
            BarkHistoryMessageText.format(message, "Jan 2, 2026 03:04"),
        )
    }

    private fun message(
        title: String?,
        subtitle: String?,
        displayBody: String?,
        url: String?,
        group: String?,
    ) = BarkMessage(
        id = "message-id",
        title = title,
        subtitle = subtitle,
        body = "Raw body",
        displayBody = displayBody,
        bodyType = null,
        url = url,
        image = null,
        icon = null,
        group = group,
        sound = null,
        badge = null,
        level = null,
        volume = null,
        call = false,
        autoCopy = false,
        copy = null,
        action = null,
        isDelete = false,
        shouldArchive = true,
        createAtMillis = 1_767_326_640_000,
        expireAtMillis = null,
        extras = emptyMap(),
    )
}

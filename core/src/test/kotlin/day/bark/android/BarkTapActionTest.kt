package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarkTapActionTest {
    @Test
    fun `action none disables notification tap handling`() {
        val message = message(action = "none", url = "https://day.app")

        assertFalse(BarkTapAction.shouldAttachContentIntent(message))
        assertEquals(null, BarkTapAction.urlToOpen(message))
    }

    @Test
    fun `blank action keeps default app tap handling`() {
        val message = message(action = null, url = null)

        assertTrue(BarkTapAction.shouldAttachContentIntent(message))
        assertEquals(null, BarkTapAction.urlToOpen(message))
    }

    @Test
    fun `url is only opened when tap handling is enabled`() {
        val message = message(action = null, url = "https://day.app")

        assertTrue(BarkTapAction.shouldAttachContentIntent(message))
        assertEquals("https://day.app", BarkTapAction.urlToOpen(message))
    }

    @Test
    fun `action alert opens an in-app popup when no url is present`() {
        val message = message(action = "alert", url = null)

        assertTrue(BarkTapAction.shouldAttachContentIntent(message))
        assertTrue(BarkTapAction.shouldShowAlert(message))
        assertEquals(null, BarkTapAction.urlToOpen(message))
    }

    @Test
    fun `url takes precedence over action alert`() {
        val message = message(action = "alert", url = "https://day.app")

        assertTrue(BarkTapAction.shouldAttachContentIntent(message))
        assertFalse(BarkTapAction.shouldShowAlert(message))
        assertEquals("https://day.app", BarkTapAction.urlToOpen(message))
    }

    private fun message(action: String?, url: String?): BarkMessage =
        BarkMessage(
            id = "id",
            title = "Title",
            subtitle = null,
            body = "Body",
            displayBody = "Body",
            bodyType = null,
            url = url,
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
            action = action,
            isDelete = false,
            shouldArchive = true,
            createAtMillis = 0,
            expireAtMillis = null,
            extras = emptyMap(),
        )
}

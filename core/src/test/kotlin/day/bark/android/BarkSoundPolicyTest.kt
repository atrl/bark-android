package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BarkSoundPolicyTest {
    @Test
    fun `known Bark sound maps to Android raw resource name`() {
        val message = message(sound = "birdsong.caf", call = false)

        assertEquals("birdsong", BarkSoundPolicy.resourceNameFor(message))
        assertEquals("bark_high_birdsong", BarkSoundPolicy.channelIdFor(message))
    }

    @Test
    fun `unknown Bark sound falls back to level notification channel`() {
        val message = message(sound = "custom.caf", call = false)

        assertNull(BarkSoundPolicy.resourceNameFor(message))
        assertEquals("bark_high", BarkSoundPolicy.channelIdFor(message))
    }

    @Test
    fun `call notification defaults to multiwayinvitation sound`() {
        val message = message(sound = null, call = true)

        assertEquals("multiwayinvitation", BarkSoundPolicy.resourceNameFor(message))
        assertEquals("bark_high_call_multiwayinvitation", BarkSoundPolicy.channelIdFor(message))
    }

    @Test
    fun `call notification uses high channel even without explicit level`() {
        val message = message(sound = "bell.caf", call = true, level = null)

        assertEquals("bark_high_call_bell", BarkSoundPolicy.channelIdFor(message))
    }

    @Test
    fun `volume is clamped from Bark zero to ten scale`() {
        assertEquals(0.0f, BarkSoundPolicy.playerVolume(message(volume = "-1")))
        assertEquals(0.5f, BarkSoundPolicy.playerVolume(message(volume = null)))
        assertEquals(1.0f, BarkSoundPolicy.playerVolume(message(volume = "12")))
        assertEquals(0.7f, BarkSoundPolicy.playerVolume(message(volume = "7")))
    }

    private fun message(
        sound: String? = "bell.caf",
        call: Boolean = false,
        volume: String? = null,
        level: String? = "timeSensitive",
    ) = BarkMessage(
        id = "message-id",
        title = "Title",
        subtitle = null,
        body = "Body",
        displayBody = "Body",
        bodyType = null,
        url = null,
        image = null,
        icon = null,
        group = null,
        sound = sound,
        badge = null,
        level = level,
        volume = volume?.toFloatOrNull(),
        call = call,
        autoCopy = false,
        copy = null,
        action = null,
        isDelete = false,
        shouldArchive = true,
        createAtMillis = 1_700_000_000_000,
        expireAtMillis = null,
        extras = emptyMap(),
    )
}

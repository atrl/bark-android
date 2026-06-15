package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BarkCustomSoundNameTest {
    @Test
    fun `copy name is safe lowercase filename without extension`() {
        assertEquals("my_alert", BarkCustomSoundName.copyName("My Alert.caf"))
        assertEquals("build_done", BarkCustomSoundName.copyName("build done.MP3"))
        assertEquals("ding", BarkCustomSoundName.copyName("../ding.wav"))
    }

    @Test
    fun `storage filename preserves supported audio extension`() {
        assertEquals("my_alert.caf", BarkCustomSoundName.storageFileName("My Alert.caf"))
        assertEquals("build_done.mp3", BarkCustomSoundName.storageFileName("build done.MP3"))
        assertEquals("ding.wav", BarkCustomSoundName.storageFileName("../ding.wav"))
        assertEquals("audio.ogg", BarkCustomSoundName.storageFileName(""))
    }

    @Test
    fun `unsupported extension is rejected for import`() {
        assertNull(BarkCustomSoundName.storageFileName("notes.txt"))
    }
}

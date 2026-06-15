package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BarkSoundCatalogTest {
    @Test
    fun `built in sound catalog exposes Bark sound names in display order`() {
        assertEquals("alarm", BarkSoundCatalog.builtInSounds.first().name)
        assertEquals("update", BarkSoundCatalog.builtInSounds.last().name)
        assertEquals(32, BarkSoundCatalog.builtInSounds.size)
        assertTrue(BarkSoundCatalog.builtInSounds.any { it.name == "bell" })
        assertTrue(BarkSoundCatalog.builtInSounds.any { it.name == "multiwayinvitation" })
    }

    @Test
    fun `known sound names are matched without caf extension`() {
        assertTrue(BarkSoundCatalog.contains("bell"))
        assertTrue(BarkSoundCatalog.contains("bell.caf"))
        assertTrue(BarkSoundCatalog.contains(" BELL.CAF "))
    }
}

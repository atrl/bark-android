package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals

class BarkDeviceTokenTextTest {
    @Test
    fun `masks token with iOS settings style`() {
        assertEquals("an****cdef", BarkDeviceTokenText.mask("android:1234567890abcdef"))
    }

    @Test
    fun `shows unknown when token is missing`() {
        assertEquals("unknown", BarkDeviceTokenText.mask(null))
        assertEquals("unknown", BarkDeviceTokenText.mask(""))
        assertEquals("unknown", BarkDeviceTokenText.mask("   "))
    }
}

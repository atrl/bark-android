package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BarkExpiryTextTest {
    @Test
    fun `null expiry has no expiry text`() {
        assertNull(BarkExpiryText.format(expireAtMillis = null, nowMillis = 1_700_000_000_000))
    }

    @Test
    fun `future expiry uses largest useful unit`() {
        val now = 1_700_000_000_000L

        assertEquals("Expires in 10s", BarkExpiryText.format(now + 10_000, now))
        assertEquals("Expires in 3m", BarkExpiryText.format(now + 180_000, now))
        assertEquals("Expires in 2h", BarkExpiryText.format(now + 7_200_000, now))
        assertEquals("Expires in 4d", BarkExpiryText.format(now + 4L * 86_400_000, now))
        assertEquals("Expires in 2mo", BarkExpiryText.format(now + 65L * 86_400_000, now))
        assertEquals("Expires in 1y", BarkExpiryText.format(now + 370L * 86_400_000, now))
    }

    @Test
    fun `past expiry is marked expired`() {
        val now = 1_700_000_000_000L

        assertEquals("Expired", BarkExpiryText.format(now, now))
        assertEquals("Expired", BarkExpiryText.format(now - 1, now))
    }
}

package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals

class BarkPushTargetKeysTest {
    @Test
    fun `parses comma and newline separated target keys`() {
        val keys = BarkPushTargetKeys.parse(" key-one, key-two\nkey-three ")

        assertEquals(listOf("key-one", "key-two", "key-three"), keys)
    }

    @Test
    fun `drops blank and duplicate target keys while preserving order`() {
        val keys = BarkPushTargetKeys.parse("key-one,,key-two\nkey-one\n ")

        assertEquals(listOf("key-one", "key-two"), keys)
    }
}

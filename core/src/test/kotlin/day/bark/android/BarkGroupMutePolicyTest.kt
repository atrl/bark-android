package day.bark.android

import kotlin.test.Test
import kotlin.test.assertEquals

class BarkGroupMutePolicyTest {
    @Test
    fun `future group mute downgrades notification level to passive`() {
        val message = message(group = "ops", level = "timeSensitive")

        val muted = BarkGroupMutePolicy.apply(
            message = message,
            mutedUntilMillis = 1_700_000_060_000,
            nowMillis = 1_700_000_000_000,
        )

        assertEquals("passive", muted.level)
    }

    @Test
    fun `expired group mute keeps notification level unchanged`() {
        val message = message(group = "ops", level = "critical")

        val muted = BarkGroupMutePolicy.apply(
            message = message,
            mutedUntilMillis = 1_699_999_999_999,
            nowMillis = 1_700_000_000_000,
        )

        assertEquals("critical", muted.level)
    }

    @Test
    fun `future default group mute downgrades notification level to passive`() {
        val message = message(group = null, level = "timeSensitive")

        val muted = BarkGroupMutePolicy.apply(
            message = message,
            mutedUntilMillis = 1_700_000_060_000,
            nowMillis = 1_700_000_000_000,
        )

        assertEquals("passive", muted.level)
    }

    @Test
    fun `default group key is shared by null and blank groups`() {
        assertEquals("", BarkGroupMutePolicy.groupKey(null))
        assertEquals("", BarkGroupMutePolicy.groupKey(" "))
        assertEquals("ops", BarkGroupMutePolicy.groupKey(" ops "))
    }

    @Test
    fun `default group display name matches history default group`() {
        assertEquals("Default", BarkGroupMutePolicy.displayName(""))
        assertEquals("ops", BarkGroupMutePolicy.displayName("ops"))
    }

    private fun message(group: String?, level: String?) = BarkMessage(
        id = "message-id",
        title = "Title",
        subtitle = null,
        body = "Body",
        displayBody = "Body",
        bodyType = null,
        url = null,
        image = null,
        icon = null,
        group = group,
        sound = null,
        badge = null,
        level = level,
        volume = null,
        call = false,
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

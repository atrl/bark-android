package day.bark.android

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarkArchivePolicyTest {
    @Test
    fun `explicit isArchive true stores even when app default is disabled`() {
        val message = message(archiveOverride = true, shouldArchive = true)

        assertTrue(BarkArchivePolicy.shouldStore(message, defaultArchiveEnabled = false))
    }

    @Test
    fun `explicit isArchive false skips even when app default is enabled`() {
        val message = message(archiveOverride = false, shouldArchive = false)

        assertFalse(BarkArchivePolicy.shouldStore(message, defaultArchiveEnabled = true))
    }

    @Test
    fun `unset isArchive follows app default`() {
        val message = message(archiveOverride = null, shouldArchive = true)

        assertTrue(BarkArchivePolicy.shouldStore(message, defaultArchiveEnabled = true))
        assertFalse(BarkArchivePolicy.shouldStore(message, defaultArchiveEnabled = false))
    }

    @Test
    fun `delete or expired ttl messages are never stored`() {
        assertFalse(BarkArchivePolicy.shouldStore(message(archiveOverride = true, shouldArchive = false), defaultArchiveEnabled = true))
    }

    private fun message(archiveOverride: Boolean?, shouldArchive: Boolean) = BarkMessage(
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
        sound = null,
        badge = null,
        level = null,
        volume = null,
        call = false,
        autoCopy = false,
        copy = null,
        action = null,
        isDelete = false,
        shouldArchive = shouldArchive,
        archiveOverride = archiveOverride,
        createAtMillis = 1_700_000_000_000,
        expireAtMillis = null,
        extras = emptyMap(),
    )
}

package day.bark.android

object BarkArchivePolicy {
    fun shouldStore(message: BarkMessage, defaultArchiveEnabled: Boolean): Boolean {
        if (!message.shouldArchive) return false
        return message.archiveOverride ?: defaultArchiveEnabled
    }
}

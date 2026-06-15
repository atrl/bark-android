package day.bark.android

object BarkGroupMutePolicy {
    fun groupKey(group: String?): String =
        group?.trim()?.takeIf { it.isNotBlank() }.orEmpty()

    fun displayName(groupKey: String): String =
        groupKey.takeIf { it.isNotBlank() } ?: DEFAULT_GROUP_NAME

    fun apply(
        message: BarkMessage,
        mutedUntilMillis: Long?,
        nowMillis: Long = System.currentTimeMillis(),
    ): BarkMessage {
        if (mutedUntilMillis == null || mutedUntilMillis <= nowMillis) return message
        return message.copy(level = "passive")
    }

    private const val DEFAULT_GROUP_NAME = "Default"
}

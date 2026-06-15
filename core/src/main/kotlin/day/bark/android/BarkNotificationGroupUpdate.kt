package day.bark.android

object BarkNotificationGroupUpdate {
    fun staleGroupToCancel(previousGroup: String?, nextGroup: String?): String? {
        val previous = previousGroup?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val next = nextGroup?.trim()?.takeIf { it.isNotEmpty() }
        return previous.takeIf { it != next }
    }
}

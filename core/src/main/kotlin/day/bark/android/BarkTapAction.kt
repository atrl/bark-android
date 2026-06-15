package day.bark.android

object BarkTapAction {
    fun shouldAttachContentIntent(message: BarkMessage): Boolean =
        !message.action.equals("none", ignoreCase = true)

    fun urlToOpen(message: BarkMessage): String? =
        message.url?.takeIf { shouldAttachContentIntent(message) && it.isNotBlank() }

    fun shouldShowAlert(message: BarkMessage): Boolean =
        shouldAttachContentIntent(message) &&
            message.action.equals("alert", ignoreCase = true) &&
            urlToOpen(message) == null
}

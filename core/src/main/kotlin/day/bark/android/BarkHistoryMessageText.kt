package day.bark.android

object BarkHistoryMessageText {
    fun format(message: BarkMessage, dateText: String, expiryText: String? = null): String =
        buildString {
            append(message.title?.takeIf { it.isNotBlank() } ?: "Bark")
            appendIfPresent(message.subtitle)
            appendIfPresent(message.displayBody)
            appendIfPresent(message.url)
            append("\n").append(dateText)
            if (!expiryText.isNullOrBlank()) append(" · ").append(expiryText)
            if (!message.group.isNullOrBlank()) append("  #").append(message.group)
        }

    private fun StringBuilder.appendIfPresent(value: String?) {
        if (!value.isNullOrBlank()) append("\n").append(value)
    }
}

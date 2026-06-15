package day.bark.android

object BarkCopyText {
    fun from(message: BarkMessage): String {
        message.copy?.takeIf { it.isNotBlank() }?.let { return it }
        return listOfNotNull(
            message.title?.takeIf { it.isNotBlank() },
            message.subtitle?.takeIf { it.isNotBlank() },
            (message.displayBody ?: message.body)?.takeIf { it.isNotBlank() },
            message.url?.takeIf { it.isNotBlank() },
            message.image?.takeIf { it.isNotBlank() },
        ).joinToString(separator = "\n")
    }
}

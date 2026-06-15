package day.bark.android

object BarkDeviceTokenText {
    fun mask(token: String?): String {
        val value = token?.takeIf { it.isNotBlank() } ?: return "unknown"
        return "${value.take(2)}****${value.takeLast(4)}"
    }
}

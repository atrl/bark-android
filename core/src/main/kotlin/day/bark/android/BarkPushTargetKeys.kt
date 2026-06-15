package day.bark.android

object BarkPushTargetKeys {
    fun parse(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val seen = linkedSetOf<String>()
        text.split(',', '\n', '\r', '\t', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach(seen::add)
        return seen.toList()
    }
}

package day.bark.android

object MarkdownText {
    fun plainText(markdown: String): String {
        var inCodeBlock = false
        val output = mutableListOf<String>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            val text = paragraph.toString().trim()
            if (text.isNotEmpty()) output += text
            paragraph.clear()
        }

        markdown.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flushParagraph()
                inCodeBlock = !inCodeBlock
                return@forEach
            }
            if (inCodeBlock) {
                output += line.trimEnd()
                return@forEach
            }
            if (trimmed.isEmpty()) {
                flushParagraph()
                return@forEach
            }

            val withoutMarker = trimmed
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^>\\s?"), "")
                .replace(Regex("^[-*+]\\s+\\[x]\\s+(.+)", RegexOption.IGNORE_CASE), "☑ $1")
                .replace(Regex("^[-*+]\\s+\\[ ]\\s+(.+)"), "☐ $1")
                .replace(Regex("^[-*+]\\s+(.+)"), "• $1")
            if (isBlockLine(trimmed, withoutMarker)) {
                flushParagraph()
                output += withoutMarker
            } else {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(withoutMarker)
            }
        }
        flushParagraph()

        return output.joinToString("\n")
            .replace(Regex("!\\[([^]]*)]\\([^)]*\\)")) { match ->
                "[${match.groupValues[1].ifBlank { "image" }}]"
            }
            .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("__([^_]+)__"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("_([^_]+)_"), "$1")
            .replace(Regex("~~([^~]+)~~"), "$1")
            .replace(Regex("\\n\\s*\\n+"), "\n")
            .trim()
    }

    private fun isBlockLine(trimmed: String, withoutMarker: String): Boolean =
        trimmed.startsWith("#") ||
            trimmed.startsWith(">") ||
            withoutMarker.startsWith("• ") ||
            withoutMarker.startsWith("☑ ") ||
            withoutMarker.startsWith("☐ ") ||
            trimmed.matches(Regex("\\d+[.)]\\s+.+")) ||
            trimmed.startsWith("![")
}

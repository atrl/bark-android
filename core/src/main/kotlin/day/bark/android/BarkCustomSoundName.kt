package day.bark.android

object BarkCustomSoundName {
    private val supportedExtensions = setOf("caf", "wav", "mp3", "ogg", "m4a", "aac")

    fun copyName(displayName: String): String =
        sanitizeBaseName(baseAndExtension(displayName).first)

    fun storageFileName(displayName: String): String? {
        val (base, extension) = baseAndExtension(displayName)
        val safeBase = sanitizeBaseName(base)
        val safeExtension = extension ?: DEFAULT_EXTENSION
        if (safeExtension !in supportedExtensions) return null
        return "$safeBase.$safeExtension"
    }

    private fun baseAndExtension(displayName: String): Pair<String, String?> {
        val fileName = displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .lowercase()
        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == fileName.lastIndex) {
            return fileName to null
        }
        return fileName.substring(0, dotIndex) to fileName.substring(dotIndex + 1)
    }

    private fun sanitizeBaseName(baseName: String): String =
        baseName
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "audio" }

    private const val DEFAULT_EXTENSION = "ogg"
}

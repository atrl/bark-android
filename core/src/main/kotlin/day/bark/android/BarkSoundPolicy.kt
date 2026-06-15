package day.bark.android

object BarkSoundPolicy {
    fun resourceNameFor(message: BarkMessage): String? {
        val soundName = BarkSoundCatalog.normalizedName(message.sound)
            ?: if (message.call) DEFAULT_CALL_SOUND else null
        return soundName?.takeIf(BarkSoundCatalog::contains)
    }

    fun channelIdFor(message: BarkMessage): String {
        val resourceName = resourceNameFor(message)
        val base = when {
            message.call -> "bark_high"
            message.level?.lowercase() == "passive" -> "bark_low"
            message.level?.lowercase() in setOf("timesensitive", "critical") -> "bark_high"
            else -> "bark_default"
        }
        return when {
            message.call && resourceName != null -> "${base}_call_$resourceName"
            resourceName != null -> "${base}_$resourceName"
            else -> base
        }
    }

    fun playerVolume(message: BarkMessage): Float {
        val volume = message.volume ?: 5.0f
        return (volume / 10.0f).coerceIn(0.0f, 1.0f)
    }

    private const val DEFAULT_CALL_SOUND = "multiwayinvitation"
}

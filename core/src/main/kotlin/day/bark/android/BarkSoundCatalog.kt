package day.bark.android

data class BarkSound(val name: String)

object BarkSoundCatalog {
    val builtInSounds: List<BarkSound> = listOf(
        "alarm",
        "anticipate",
        "bell",
        "birdsong",
        "bloom",
        "calypso",
        "chime",
        "choo",
        "descent",
        "electronic",
        "fanfare",
        "glass",
        "gotosleep",
        "healthnotification",
        "horn",
        "ladder",
        "mailsent",
        "minuet",
        "multiwayinvitation",
        "newmail",
        "newsflash",
        "noir",
        "paymentsuccess",
        "shake",
        "sherwoodforest",
        "silence",
        "spell",
        "suspense",
        "telegraph",
        "tiptoes",
        "typewriters",
        "update",
    ).map(::BarkSound)

    private val names = builtInSounds.map { it.name }.toSet()

    fun contains(sound: String): Boolean =
        normalizedName(sound) in names

    fun normalizedName(sound: String?): String? =
        sound
            ?.trim()
            ?.lowercase()
            ?.removeSuffix(".caf")
            ?.removeSuffix(".ogg")
            ?.takeIf { it.matches(Regex("[a-z0-9_]+")) }
}

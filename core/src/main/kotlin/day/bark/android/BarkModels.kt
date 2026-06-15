package day.bark.android

data class CryptoSettings(
    val algorithm: String,
    val mode: String,
    val padding: String,
    val key: String,
    val iv: String? = null,
)

data class BarkMessage(
    val id: String,
    val title: String?,
    val subtitle: String?,
    val body: String?,
    val displayBody: String?,
    val bodyType: String?,
    val url: String?,
    val image: String?,
    val icon: String?,
    val group: String?,
    val sound: String?,
    val badge: Int?,
    val level: String?,
    val volume: Float?,
    val call: Boolean,
    val autoCopy: Boolean,
    val copy: String?,
    val action: String?,
    val isDelete: Boolean,
    val shouldArchive: Boolean,
    val archiveOverride: Boolean? = null,
    val createAtMillis: Long,
    val expireAtMillis: Long?,
    val extras: Map<String, String>,
)

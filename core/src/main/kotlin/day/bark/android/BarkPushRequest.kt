package day.bark.android

data class BarkPushRequest(
    val title: String? = null,
    val subtitle: String? = null,
    val body: String? = null,
    val id: String? = null,
    val markdown: String? = null,
    val level: String? = null,
    val isCall: Boolean = false,
    val isCritical: Boolean = false,
    val volume: Int? = null,
    val badge: Int? = null,
    val autoCopy: Boolean = false,
    val copy: String? = null,
    val sound: String? = null,
    val icon: String? = null,
    val image: String? = null,
    val group: String? = null,
    val archive: Boolean? = null,
    val ttlSeconds: Long? = null,
    val url: String? = null,
    val action: String? = null,
    val ciphertext: String? = null,
    val iv: String? = null,
    val isDelete: Boolean = false,
) {
    fun toParameters(): Map<String, Any> {
        val normalizedTitle = title.normalized()
        val normalizedSubtitle = subtitle.normalized()
        val normalizedBody = body.normalized()
        val normalizedMarkdown = markdown.normalized()
        return buildMap {
            normalizedTitle?.let { put("title", it) }
            normalizedSubtitle?.let { put("subtitle", it) }
            id.normalized()?.let { put("id", it) }
            when {
                normalizedBody != null -> put("body", normalizedBody)
                normalizedTitle == null -> put("body", EMPTY_NOTIFICATION_BODY)
            }
            normalizedMarkdown?.let { put("markdown", it) }
            if (isCall) put("call", 1)
            level.normalized()?.let { put("level", it) }
                ?: if (isCritical) put("level", "critical") else Unit
            volume?.let { put("volume", it) }
            badge?.let { put("badge", it) }
            if (autoCopy) put("autoCopy", 1)
            copy.normalized()?.let { put("copy", it) }
            sound.normalized()?.let { put("sound", it) }
            icon.normalized()?.let { put("icon", it) }
            image.normalized()?.let { put("image", it) }
            group.normalized()?.let { put("group", it) }
            archive?.let { put("isArchive", if (it) "1" else "0") }
            ttlSeconds?.let { put("ttl", it) }
            url.normalized()?.let { put("url", it) }
            action.normalized()?.let { put("action", it) }
            ciphertext.normalized()?.let { put("ciphertext", it) }
            iv.normalized()?.let { put("iv", it) }
            if (isDelete) put("delete", 1)
        }
    }

    companion object {
        private const val EMPTY_NOTIFICATION_BODY = "Empty Notification"

        fun fromSharedText(subject: String?, text: String?): BarkPushRequest =
            BarkPushRequest(
                title = subject.normalized(),
                body = text.normalized() ?: EMPTY_NOTIFICATION_BODY,
            )

        private fun String?.normalized(): String? =
            this?.trim()?.takeIf { it.isNotBlank() }
    }
}

package day.bark.android

import java.net.URLEncoder

data class BarkPushExample(
    val title: String?,
    val body: String,
    val notice: String,
    val queryParameter: String? = null,
) {
    fun url(profile: BarkServerProfile): String =
        buildString {
            append(profile.address.trimEnd('/'))
            append("/")
            append(pathPart(profile.key.ifBlank { "Your Key" }))
            title?.takeIf { it.isNotBlank() }?.let {
                append("/")
                append(pathPart(it))
            }
            append("/")
            append(pathPart(body))
            queryParameter?.takeIf { it.isNotBlank() }?.let {
                append("?")
                append(it)
            }
        }

    private fun pathPart(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

object BarkPushExampleCatalog {
    val examples: List<BarkPushExample> = listOf(
        BarkPushExample(
            title = null,
            body = "Custom Notification Content",
            notice = "Basic content push",
        ),
        BarkPushExample(
            title = "Custom Notification Title",
            body = "Custom Notification Content",
            notice = "Title and content push",
        ),
        BarkPushExample(
            title = null,
            body = "Notification Sound",
            notice = "Set a built-in notification sound",
            queryParameter = "sound=minuet",
        ),
        BarkPushExample(
            title = null,
            body = "Ringtone",
            notice = "Repeat the notification sound",
            queryParameter = "call=1",
        ),
        BarkPushExample(
            title = null,
            body = "Archive Notification",
            notice = "Force saving this push to history",
            queryParameter = "isArchive=1",
        ),
        BarkPushExample(
            title = null,
            body = "Notification Icon",
            notice = "Use a remote notification icon",
            queryParameter = "icon=https://day.app/assets/images/avatar.jpg",
        ),
        BarkPushExample(
            title = null,
            body = "Message Group",
            notice = "Group notifications and history messages",
            queryParameter = "group=groupName",
        ),
        BarkPushExample(
            title = null,
            body = "Push Notification Encryption",
            notice = "Encrypted push payload placeholder",
            queryParameter = "ciphertext=ciphertext",
        ),
        BarkPushExample(
            title = null,
            body = "Critical Alert",
            notice = "High-priority critical alert",
            queryParameter = "level=critical&volume=5",
        ),
        BarkPushExample(
            title = null,
            body = "Interruption Level",
            notice = "Time-sensitive notification",
            queryParameter = "level=timeSensitive",
        ),
        BarkPushExample(
            title = null,
            body = "URL Test",
            notice = "Open a URL when the notification is tapped",
            queryParameter = "url=https://www.baidu.com",
        ),
        BarkPushExample(
            title = null,
            body = "Image Push Notification",
            notice = "Attach a remote image URL",
            queryParameter = "image=https://day.app/assets/images/avatar.jpg",
        ),
        BarkPushExample(
            title = null,
            body = "Copy Test",
            notice = "Override copied notification text",
            queryParameter = "copy=test",
        ),
        BarkPushExample(
            title = null,
            body = "Badge",
            notice = "Set the app badge number",
            queryParameter = "badge=1",
        ),
        BarkPushExample(
            title = null,
            body = "Automatically Copy",
            notice = "Copy the payload text automatically",
            queryParameter = "autoCopy=1&copy=optional",
        ),
    )
}

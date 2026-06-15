package day.bark.android

import org.json.JSONArray
import org.json.JSONObject

object BarkMessageBackupCodec {
    private val knownTopLevelKeys = setOf(
        "id",
        "title",
        "subtitle",
        "body",
        "bodyType",
        "displayBody",
        "url",
        "image",
        "icon",
        "group",
        "sound",
        "badge",
        "level",
        "volume",
        "call",
        "autoCopy",
        "autocopy",
        "copy",
        "action",
        "isDelete",
        "shouldArchive",
        "archiveOverride",
        "createDate",
        "expireDate",
        "extras",
    )

    fun encode(messages: List<BarkMessage>): String {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(message.toBackupObject())
        }
        return array.toString(2)
    }

    fun decode(json: String): List<BarkMessage> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                item.toMessageOrNull()?.let(::add)
            }
        }
    }

    private fun BarkMessage.toBackupObject(): JSONObject =
        JSONObject().apply {
            put("id", id)
            putOptString("title", title)
            putOptString("subtitle", subtitle)
            putOptString("body", body)
            putOptString("bodyType", bodyType)
            putOptString("url", url)
            putOptString("image", image)
            putOptString("group", group)
            put("createDate", createAtMillis / 1000L)
            expireAtMillis?.let { put("expireDate", it / 1000L) }

            putOptString("displayBody", displayBody)
            putOptString("icon", icon)
            putOptString("sound", sound)
            badge?.let { put("badge", it) }
            putOptString("level", level)
            volume?.let { put("volume", it.toDouble()) }
            put("call", call)
            put("autoCopy", autoCopy)
            putOptString("copy", copy)
            putOptString("action", action)
            put("isDelete", isDelete)
            put("shouldArchive", shouldArchive)
            archiveOverride?.let { put("archiveOverride", it) }
            if (extras.isNotEmpty()) {
                put("extras", JSONObject(extras))
            }
        }

    private fun JSONObject.toMessageOrNull(): BarkMessage? {
        val id = optStringValue("id")?.takeIf { it.isNotBlank() } ?: return null
        val createDate = optLongValue("createDate") ?: return null
        val body = optStringValue("body")
        val bodyType = optStringValue("bodyType")
        val displayBody = optStringValue("displayBody")
            ?: body?.let { if (bodyType == "markdown") MarkdownText.plainText(it) else it }
        val extras = topLevelExtras() + extrasObject()

        return BarkMessage(
            id = id,
            title = optStringValue("title"),
            subtitle = optStringValue("subtitle"),
            body = body,
            displayBody = displayBody,
            bodyType = bodyType,
            url = optStringValue("url"),
            image = optStringValue("image"),
            icon = optStringValue("icon"),
            group = optStringValue("group"),
            sound = optStringValue("sound"),
            badge = optIntValue("badge"),
            level = optStringValue("level"),
            volume = optDoubleValue("volume")?.toFloat(),
            call = optBooleanValue("call") ?: false,
            autoCopy = optBooleanValue("autoCopy", "autocopy") ?: false,
            copy = optStringValue("copy"),
            action = optStringValue("action"),
            isDelete = optBooleanValue("isDelete") ?: false,
            shouldArchive = optBooleanValue("shouldArchive") ?: true,
            archiveOverride = optBooleanValue("archiveOverride"),
            createAtMillis = secondsToMillis(createDate),
            expireAtMillis = optLongValue("expireDate")?.let(::secondsToMillis),
            extras = extras.toSortedMap(),
        )
    }

    private fun secondsToMillis(value: Long): Long =
        if (value >= 100_000_000_000L) value else value * 1000L

    private fun JSONObject.putOptString(name: String, value: String?) {
        if (value != null) put(name, value)
    }

    private fun JSONObject.optStringValue(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return get(name).toString()
    }

    private fun JSONObject.optLongValue(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return when (val value = get(name)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun JSONObject.optIntValue(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return when (val value = get(name)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject.optDoubleValue(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return when (val value = get(name)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun JSONObject.optBooleanValue(vararg names: String): Boolean? {
        for (name in names) {
            if (!has(name) || isNull(name)) continue
            when (val value = get(name)) {
                is Boolean -> return value
                is Number -> return value.toInt() != 0
                is String -> {
                    value.toBooleanStrictOrNull()?.let { return it }
                    if (value == "1") return true
                    if (value == "0") return false
                }
            }
        }
        return null
    }

    private fun JSONObject.topLevelExtras(): Map<String, String> =
        buildMap {
            val keys = keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key !in knownTopLevelKeys && !isNull(key)) {
                    put(key, get(key).toString())
                }
            }
        }

    private fun JSONObject.extrasObject(): Map<String, String> {
        val extras = optJSONObject("extras") ?: return emptyMap()
        return buildMap {
            val keys = extras.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!extras.isNull(key)) {
                    put(key, extras.get(key).toString())
                }
            }
        }
    }
}

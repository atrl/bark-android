package day.bark.android

import java.util.UUID
import org.json.JSONObject

object BarkPayloadProcessor {
    private val knownKeys = setOf(
        "id",
        "title",
        "subtitle",
        "body",
        "markdown",
        "url",
        "image",
        "icon",
        "group",
        "sound",
        "badge",
        "level",
        "volume",
        "call",
        "autocopy",
        "automaticallycopy",
        "copy",
        "action",
        "isarchive",
        "ttl",
        "delete",
        "ciphertext",
        "iv",
        "device_key",
    )

    fun process(
        raw: Map<String, Any?>,
        cryptoSettings: CryptoSettings?,
        nowMillis: Long = System.currentTimeMillis(),
    ): BarkMessage {
        val normalized = normalize(raw)
        val payload = normalized["ciphertext"]?.takeIf { it.isNotBlank() }?.let { ciphertext ->
            val settings = cryptoSettings ?: throw IllegalArgumentException("No encryption key set")
            decryptPayload(ciphertext, normalized["iv"], settings).withFallback("id", normalized)
        } ?: normalized

        val markdown = payload["markdown"]?.takeIf { it.isNotBlank() }
        val rawBody = markdown ?: payload["body"]
        val displayBody = markdown?.let { MarkdownText.plainText(it) } ?: payload["body"]

        val ttlSeconds = payload["ttl"]?.toLongOrNull()
        val archiveOverride = payload["isarchive"]?.let(::flagIsTrue)
        val archiveFlag = archiveOverride ?: true
        val isDelete = flagIsTrue(payload["delete"])
        val shouldArchive = !isDelete && archiveFlag && (ttlSeconds == null || ttlSeconds > 0)
        val expireAtMillis = ttlSeconds
            ?.takeIf { it > 0 }
            ?.let { nowMillis + it * 1000L }

        return BarkMessage(
            id = payload["id"]?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            title = payload["title"],
            subtitle = payload["subtitle"],
            body = rawBody,
            displayBody = displayBody,
            bodyType = if (markdown != null) "markdown" else null,
            url = payload["url"],
            image = payload["image"],
            icon = payload["icon"],
            group = payload["group"],
            sound = normalizeSound(payload["sound"]),
            badge = payload["badge"]?.toIntOrNull(),
            level = payload["level"],
            volume = payload["volume"]?.toFloatOrNull(),
            call = flagIsTrue(payload["call"]),
            autoCopy = flagIsTrue(payload["autocopy"]) || flagIsTrue(payload["automaticallycopy"]),
            copy = payload["copy"],
            action = payload["action"],
            isDelete = isDelete,
            shouldArchive = shouldArchive,
            archiveOverride = archiveOverride,
            createAtMillis = nowMillis,
            expireAtMillis = expireAtMillis,
            extras = payload
                .filterKeys { it !in knownKeys }
                .toSortedMap(),
        )
    }

    private fun decryptPayload(
        ciphertext: String,
        iv: String?,
        settings: CryptoSettings,
    ): Map<String, String> {
        val json = AesCipher.decrypt(ciphertext, settings.copy(iv = iv ?: settings.iv))
        val jsonObject = JSONObject(json)
        return buildMap {
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                val key = rawKey.lowercase()
                val value = jsonObject.get(rawKey)
                if (value != JSONObject.NULL) {
                    put(key, value.toString())
                }
            }
        }
    }

    private fun normalize(raw: Map<String, Any?>): Map<String, String> =
        raw.entries.associate { (key, value) ->
            key.lowercase() to when (value) {
                null -> ""
                JSONObject.NULL -> ""
                else -> value.toString()
            }
        }

    private fun Map<String, String>.withFallback(key: String, fallback: Map<String, String>): Map<String, String> =
        if (!this[key].isNullOrBlank() || fallback[key].isNullOrBlank()) {
            this
        } else {
            this + (key to fallback.getValue(key))
        }

    private fun flagIsTrue(value: String?): Boolean =
        value.equals("1") || value.equals("true", ignoreCase = true)

    private fun normalizeSound(sound: String?): String? {
        if (sound.isNullOrBlank()) return null
        return if (sound.endsWith(".caf")) sound else "$sound.caf"
    }
}

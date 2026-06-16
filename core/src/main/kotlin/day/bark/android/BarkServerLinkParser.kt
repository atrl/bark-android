package day.bark.android

import java.net.URI
import java.net.URLDecoder

data class BarkServerLink(
    val address: String,
    val key: String,
)

object BarkServerLinkParser {
    fun parse(text: String): BarkServerLink? {
        val raw = text.trim().trim('"', '\'')
        if (raw.isBlank()) return null
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            return null
        }
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> parsePushUrl(uri)
            "bark" -> parseBarkUrl(uri)
            else -> null
        }
    }

    private fun parsePushUrl(uri: URI): BarkServerLink? {
        if (uri.host.isNullOrBlank()) return null
        val segments = uri.rawPath
            ?.split("/")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (segments.isEmpty()) return null

        val keyIndex = keySegmentIndex(uri, segments)
        val key = decode(segments[keyIndex])
        if (key.isBlank()) return null
        val prefix = segments.take(keyIndex).joinToString(prefix = "/", separator = "/")
        return BarkServerLink(
            address = buildBaseAddress(uri, prefix.takeIf { it != "/" }),
            key = key,
        )
    }

    private fun keySegmentIndex(uri: URI, segments: List<String>): Int {
        if (segments.size == 1) return 0
        val generatedKeyIndex = segments.indexOfFirst { looksLikeGeneratedDeviceKey(decode(it)) }
        if (generatedKeyIndex >= 0) return generatedKeyIndex
        val barkPrefixIndex = segments.indexOfFirst { decode(it).equals("bark", ignoreCase = true) }
        if (barkPrefixIndex >= 0 && barkPrefixIndex < segments.lastIndex) return barkPrefixIndex + 1
        return 0
    }

    private fun looksLikeGeneratedDeviceKey(value: String): Boolean =
        value.length >= 16 && value.all { it.isLetterOrDigit() || it == '_' || it == '-' }

    private fun parseBarkUrl(uri: URI): BarkServerLink? {
        if (!uri.host.equals("addserver", ignoreCase = true)) return null
        val address = queryParam(uri, "address")?.let(BarkServerProfiles::normalizeAddress)
        if (address.isNullOrBlank()) return null
        return BarkServerLink(address = address, key = "")
    }

    private fun buildBaseAddress(uri: URI, pathPrefix: String?): String {
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val path = pathPrefix.orEmpty()
        return "${uri.scheme.lowercase()}://${uri.host}$port$path".trimEnd('/')
    }

    private fun queryParam(uri: URI, name: String): String? =
        uri.rawQuery
            ?.split("&")
            ?.mapNotNull { part ->
                val pieces = part.split("=", limit = 2)
                val key = decode(pieces[0])
                val value = pieces.getOrNull(1)?.let(::decode).orEmpty()
                key to value
            }
            ?.firstOrNull { it.first == name }
            ?.second

    private fun decode(value: String): String =
        URLDecoder.decode(value, Charsets.UTF_8.name())
}

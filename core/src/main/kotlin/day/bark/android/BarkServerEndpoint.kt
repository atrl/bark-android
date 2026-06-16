package day.bark.android

import java.net.URI

data class BarkServerEndpoint(
    val baseUrl: String,
) {
    companion object {
        fun from(serverUrl: String): BarkServerEndpoint {
            val trimmed = serverUrl.trim().trimEnd('/')
            val uri = try {
                URI(trimmed)
            } catch (_: Exception) {
                return BarkServerEndpoint(trimmed)
            }

            val scheme = uri.scheme ?: return BarkServerEndpoint(trimmed)
            val rawAuthority = uri.rawAuthority ?: return BarkServerEndpoint(trimmed)
            val userInfoEnd = rawAuthority.lastIndexOf('@')
            if (userInfoEnd < 0) return BarkServerEndpoint(trimmed)

            val authority = rawAuthority.substring(userInfoEnd + 1)
            val path = uri.rawPath.orEmpty().trimEnd('/')
            val baseUrl = "$scheme://$authority$path".trimEnd('/')
            return BarkServerEndpoint(baseUrl)
        }
    }
}

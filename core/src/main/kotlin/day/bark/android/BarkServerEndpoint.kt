package day.bark.android

import java.net.URI
import java.util.Base64

data class BarkServerEndpoint(
    val baseUrl: String,
    val authorizationHeader: String?,
) {
    companion object {
        fun from(serverUrl: String): BarkServerEndpoint {
            val trimmed = serverUrl.trim().trimEnd('/')
            val uri = try {
                URI(trimmed)
            } catch (_: Exception) {
                return BarkServerEndpoint(trimmed, null)
            }

            val scheme = uri.scheme ?: return BarkServerEndpoint(trimmed, null)
            val rawAuthority = uri.rawAuthority ?: return BarkServerEndpoint(trimmed, null)
            val userInfo = uri.userInfo?.takeIf { it.isNotBlank() }
                ?: return BarkServerEndpoint(trimmed, null)
            val userInfoEnd = rawAuthority.lastIndexOf('@')
            if (userInfoEnd < 0) return BarkServerEndpoint(trimmed, null)

            val authority = rawAuthority.substring(userInfoEnd + 1)
            val path = uri.rawPath.orEmpty().trimEnd('/')
            val baseUrl = "$scheme://$authority$path".trimEnd('/')
            val credentials = Base64.getEncoder()
                .encodeToString(userInfo.toByteArray(Charsets.UTF_8))
            return BarkServerEndpoint(baseUrl, "Basic $credentials")
        }
    }
}

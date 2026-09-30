package day.bark.android.projects

import java.net.URI
import java.util.Locale

data class BarkProject(
    val id: String,
    val name: String,
    val url: String,
    val description: String = "",
    val group: String? = null,
) {
    fun validated(): BarkProject {
        require(id.isNotBlank()) { "项目 ID 不能为空" }
        require(name.trim().isNotEmpty()) { "请输入项目名称" }
        return copy(
            name = name.trim(),
            url = BarkProjectUrl.normalize(url),
            description = description.trim(),
            group = group?.trim()?.takeIf(String::isNotEmpty),
        )
    }
}

/** One URL policy shared by saved projects and every WebView navigation. */
object BarkProjectUrl {
    fun normalize(value: String): String {
        val uri = runCatching { URI(value.trim()) }.getOrNull()
        require(uri != null && !uri.isOpaque && uri.scheme.equals("https", ignoreCase = true)) {
            "请输入完整的 HTTPS 网址，例如 https://basis.atrl.me/"
        }
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.port in -1..65535 && uri.port != 0) {
            "网址需要有效的域名，且不能包含用户名或密码"
        }
        val host = uri.host.lowercase(Locale.ROOT)
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return buildString {
            append("https://").append(host).append(port)
            append(uri.rawPath.takeUnless { it.isNullOrBlank() } ?: "/")
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }

    fun sameOrigin(projectUrl: String, candidateUrl: String): Boolean = runCatching {
        val project = URI(normalize(projectUrl))
        val candidate = URI(normalize(candidateUrl))
        project.host == candidate.host && project.port == candidate.port
    }.getOrDefault(false)

    fun displayOrigin(url: String): String {
        val uri = URI(normalize(url))
        return uri.host + if (uri.port == -1) "" else ":${uri.port}"
    }
}

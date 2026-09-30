package day.bark.android.update

import day.bark.android.BarkAppRelease
import day.bark.android.BarkUpdatePolicy
import day.bark.android.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL

class BarkUpdateHttpClient internal constructor(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    fun latest(): BarkAppRelease {
        val connection = connection(BarkUpdatePolicy.MANIFEST_URL, "application/json")
        try {
            requireSuccess(connection)
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(bytes.size() + count <= BarkUpdatePolicy.MAX_MANIFEST_BYTES) { "Release manifest is too large" }
                    bytes.write(buffer, 0, count)
                }
            }
            return BarkAppRelease.parse(bytes.toString(Charsets.UTF_8.name()))
        } finally { connection.disconnect() }
    }

    fun download(release: BarkAppRelease, destination: File, mayContinue: () -> Boolean = { true }) {
        BarkUpdatePolicy.validateApkUrl(release.apkUrl)
        val connection = connection(release.apkUrl, "application/vnd.android.package-archive")
        try {
            requireSuccess(connection)
            val length = connection.contentLengthLong
            require(length < 0 || length == release.sizeBytes) { "APK download size does not match release" }
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        if (!mayContinue()) throw InterruptedIOException("Download paused")
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= release.sizeBytes) { "APK download exceeds published size" }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            destination.inputStream().use { BarkUpdatePolicy.verifyIntegrity(it, release.sizeBytes, release.sha256) }
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally { connection.disconnect() }
    }

    private fun connection(url: String, accept: String): HttpURLConnection = open(URL(url)).apply {
        requestMethod = "GET"
        instanceFollowRedirects = false
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "Bark-Android/${BuildConfig.VERSION_NAME}")
        setRequestProperty("Accept", accept)
        setRequestProperty("Cache-Control", "no-cache")
    }

    private fun requireSuccess(connection: HttpURLConnection) {
        if (connection.responseCode != 200) throw IOException("Update server returned HTTP ${connection.responseCode}")
    }
}

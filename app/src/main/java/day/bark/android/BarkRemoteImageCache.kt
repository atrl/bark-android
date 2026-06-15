package day.bark.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.URL
import java.security.MessageDigest

class BarkRemoteImageCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "remote_images")

    fun bitmap(url: String?): Bitmap? {
        val normalizedUrl = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return file(normalizedUrl)?.let { cacheFile ->
            BitmapFactory.decodeFile(cacheFile.absolutePath) ?: run {
                cacheFile.delete()
                null
            }
        }
    }

    fun file(url: String?): File? {
        val normalizedUrl = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            cacheDir.mkdirs()
            val cacheFile = cacheFileFor(normalizedUrl)
            if (cacheFile.exists() && cacheFile.length() > 0L) {
                return cacheFile
            }
            cacheFile.delete()

            download(normalizedUrl, cacheFile)
            cacheFile.takeIf { it.exists() && it.length() > 0L }
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheFileFor(url: String): File =
        File(cacheDir, "${sha256(url)}.img")

    private fun download(url: String, cacheFile: File) {
        val tempFile = File(cacheDir, "${cacheFile.name}.tmp")
        tempFile.delete()
        val connection = URL(url).openConnection()
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.getInputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (tempFile.length() > 0L) {
            tempFile.copyTo(cacheFile, overwrite = true)
        }
        tempFile.delete()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

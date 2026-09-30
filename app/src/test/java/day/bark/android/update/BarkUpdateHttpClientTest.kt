package day.bark.android.update

import com.sun.net.httpserver.HttpServer
import day.bark.android.BarkAppRelease
import day.bark.android.BarkUpdatePolicy
import day.bark.android.BuildConfig
import day.bark.android.toHex
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class BarkUpdateHttpClientTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val files = mutableListOf<File>()
    private val client = BarkUpdateHttpClient { url ->
        assertEquals("https", url.protocol)
        assertEquals("bark.atrl.me", url.host)
        URL("http://127.0.0.1:${server.address.port}${url.path}").openConnection() as HttpURLConnection
    }
    private fun release(bytes: ByteArray) = BarkAppRelease(5, "0.2.2", 26,
        "https://bark.atrl.me/android/releases/bark-android-0.2.2.apk",
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex(), bytes.size.toLong(),
        BarkUpdatePolicy.RELEASE_SIGNER, "Test release", "2026-09-30T05:00:00Z")
    private fun temporary() = File.createTempFile("bark-update-test", ".part").also { files += it }
    @After fun close() { server.stop(0); files.forEach(File::delete) }

    @Test fun manifestUsesFixedAuthorityAndTruthfulUserAgent() {
        val release = release(byteArrayOf(1, 2, 3))
        server.createContext("/android/releases/stable.json") { exchange ->
            assertEquals("Bark-Android/${BuildConfig.VERSION_NAME}", exchange.requestHeaders.getFirst("User-Agent"))
            assertEquals("application/json", exchange.requestHeaders.getFirst("Accept"))
            val bytes = release.toJson().toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        assertEquals(release, client.latest())
    }

    @Test fun redirectsAreRejectedAndNeverFollowed() {
        var followed = false
        server.createContext("/android/releases/stable.json") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/capture")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/capture") { exchange -> followed = true; exchange.sendResponseHeaders(204, -1); exchange.close() }
        assertThrows(IOException::class.java) { client.latest() }
        assertFalse(followed)
    }

    @Test fun downloadedBytesMustMatchPublishedHashAndSize() {
        val bytes = ByteArray(100_000) { (it % 127).toByte() }
        val release = release(bytes)
        server.createContext("/android/releases/bark-android-0.2.2.apk") { exchange ->
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val file = temporary()
        client.download(release, file)
        assertArrayEquals(bytes, file.readBytes())
        assertThrows(IllegalArgumentException::class.java) { client.download(release.copy(sha256 = "b".repeat(64)), file) }
        assertFalse(file.exists())
    }

    @Test fun interruptedDownloadsCannotLeaveAnInstallablePartial() {
        val bytes = ByteArray(100_000) { 7 }
        server.createContext("/android/releases/bark-android-0.2.2.apk") { exchange ->
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            runCatching { exchange.responseBody.use { it.write(bytes) } }
        }
        val file = temporary()
        var chunks = 0
        assertThrows(IOException::class.java) { client.download(release(bytes), file) { chunks++ == 0 } }
        assertFalse(file.exists())
    }
}

package day.bark.android

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Test
import org.junit.Assert.*

class BarkServerClientTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val address get() = "http://127.0.0.1:${server.address.port}"
    private val client get() = BarkServerClient(address, "android:test-install")
    @After fun close() = server.stop(0)

    @Test fun syncPreservesDeliveryIdentityAndUsesAuthenticatedRoute() {
        server.createContext("/android/sync/device") { exchange ->
            assertEquals("android:test-install", exchange.requestHeaders.getFirst("X-Bark-Device-Token"))
            assertEquals("timeout=0&limit=50", exchange.requestURI.rawQuery)
            assertEquals("Bark-Android/${BuildConfig.VERSION_NAME}", exchange.requestHeaders.getFirst("User-Agent"))
            assertEquals("application/json", exchange.requestHeaders.getFirst("Accept"))
            val response = """{"code":200,"data":{"messages":[{"delivery_id":"d1","created_at_millis":1234,"payload":{"id":"business","body":"hello"},"fcm_accepted":true,"notification_tag":"bark:hash"}],"more":false}}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        val message = client.sync("device").messages.single()
        assertEquals("d1", message.deliveryId)
        assertEquals("business", message.payload["id"])
        assertEquals("bark:hash", message.notificationTag)
        assertTrue(message.fcmAccepted)
    }

    @Test fun transportIncludesLocalHandlingModeAndReturnsCanonicalHint() {
        server.createContext("/android/transport/device") { exchange ->
            assertEquals("POST", exchange.requestMethod)
            val body = org.json.JSONObject(exchange.requestBody.reader().readText())
            assertEquals("fcm", body.getString("provider"))
            assertEquals("data", body.getString("notification_mode"))
            assertEquals("test-fcm-registration", body.getString("token"))
            val response = """{"code":200,"data":{"provider":"fcm","server_url":"https://canonical.example"}}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        assertEquals("https://canonical.example", client.setTransport("device", "fcm", "test-fcm-registration", "data"))
    }

    @Test fun noDataUnregistrationResponseIsSuccess() {
        server.createContext("/register") { exchange ->
            assertEquals("android:test-install", exchange.requestHeaders.getFirst("X-Bark-Device-Token"))
            assertTrue(exchange.requestBody.reader().readText().contains("deleted"))
            val response = """{"code":200,"message":"success"}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        client.unregister("device")
    }

    @Test fun syncErrorsRemainTypedAndCannotBecomeEmptySuccess() {
        server.createContext("/android/sync/device") { exchange ->
            val response = """{"message":"not authorized"}""".toByteArray()
            exchange.sendResponseHeaders(403, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        val error = assertThrows(BarkHttpException::class.java) { client.sync("device") }
        assertEquals(403, error.statusCode)
    }

    @Test fun redirectsCannotForwardInstallTokenToAnotherHost() {
        var forwarded = false
        server.createContext("/android/sync/device") { exchange ->
            exchange.responseHeaders.add("Location", "$address/capture")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/capture") { exchange -> forwarded = true; exchange.sendResponseHeaders(204, -1); exchange.close() }
        assertThrows(BarkHttpException::class.java) { client.sync("device") }
        assertFalse(forwarded)
    }
}

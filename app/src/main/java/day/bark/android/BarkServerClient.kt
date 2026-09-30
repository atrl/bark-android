package day.bark.android

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class BarkServerClient(private val serverUrl: String, private val deviceToken: String? = null) {
    private val endpoint = BarkServerEndpoint.from(serverUrl)

    fun ping(): Boolean {
        val connection = openConnection("/ping", "GET")
        return try {
            if (connection.responseCode !in 200..299) {
                false
            } else {
                val text = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
                text.trim() == "pong" || JSONObject(text).optString("message") == "pong"
            }
        } finally {
            connection.disconnect()
        }
    }

    fun register(deviceKey: String?, deviceToken: String): RegistrationResult {
        val body = JSONObject()
            .put("device_token", deviceToken)
        if (!deviceKey.isNullOrBlank()) {
            body.put("device_key", deviceKey)
        }
        val response = request("POST", "/register", body)
        val data = response.getJSONObject("data")
        return RegistrationResult(
            deviceKey = data.getString("device_key"),
            deviceToken = data.getString("device_token"),
        )
    }

    fun unregister(deviceKey: String) {
        request("POST", "/register", JSONObject().put("device_key", deviceKey).put("device_token", "deleted"))
    }

    fun pushTest(deviceKey: String) {
        request(
            "POST",
            "/push",
            JSONObject()
                .put("device_key", deviceKey)
                .put("title", "Bark Android")
                .put("body", "Test notification from Android client")
                .put("group", "test")
                .put("sound", "bell"),
        )
    }

    fun push(deviceKey: String, request: BarkPushRequest) {
        val body = JSONObject().put("device_key", deviceKey)
        request.toParameters().forEach { (key, value) ->
            body.put(key, value)
        }
        request("POST", "/push", body)
    }

    fun push(deviceKeys: List<String>, request: BarkPushRequest) {
        require(deviceKeys.isNotEmpty()) { "Device key is required" }
        val body = JSONObject().put("device_keys", JSONArray(deviceKeys))
        request.toParameters().forEach { (key, value) ->
            body.put(key, value)
        }
        request("POST", "/push", body)
    }

    fun pushToAddress(request: BarkPushRequest) {
        val body = JSONObject()
        request.toParameters().forEach { (key, value) ->
            body.put(key, value)
        }
        request("POST", "", body)
    }

    fun poll(deviceKey: String, timeoutSeconds: Int = 30): Map<String, Any?>? {
        val connection = openConnection("/android/poll/$deviceKey?timeout=$timeoutSeconds", "GET")
        return try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NO_CONTENT) {
                null
            } else {
                val response = readJson(connection)
                val data = response.optJSONObject("data") ?: return null
                jsonObjectToMap(data)
            }
        } finally {
            connection.disconnect()
        }
    }

    fun setTransport(deviceKey: String, provider: String, token: String? = null, notificationMode: String = "notification"): String? {
        val body = JSONObject().put("provider", provider).put("notification_mode", notificationMode)
        token?.let { body.put("token", it) }
        return request("POST", "/android/transport/$deviceKey", body).optJSONObject("data")?.optString("server_url")?.takeIf { it.isNotBlank() }
    }

    fun sync(deviceKey: String, timeoutSeconds: Int = 0): BarkSyncPage {
        val connection = openConnection("/android/sync/$deviceKey?timeout=$timeoutSeconds&limit=50", "GET")
        return try {
            if (connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT) return BarkSyncPage(emptyList(), false)
            val data = readJson(connection).getJSONObject("data")
            val messages = data.getJSONArray("messages")
            BarkSyncPage((0 until messages.length()).map { index ->
                val item = messages.getJSONObject(index)
                BarkDelivery(
                    deliveryId = item.getString("delivery_id"),
                    payload = jsonObjectToMap(item.getJSONObject("payload")),
                    createdAtMillis = item.getLong("created_at_millis"),
                    fcmAccepted = item.optBoolean("fcm_accepted", false),
                    notificationTag = item.optString("notification_tag").takeIf { it.isNotBlank() },
                )
            }, data.optBoolean("more", false))
        } finally { connection.disconnect() }
    }

    fun acknowledge(deviceKey: String, deliveryIds: List<String>) {
        request("POST", "/android/ack/$deviceKey", JSONObject().put("delivery_ids", JSONArray(deliveryIds)))
    }

    private fun request(method: String, path: String, body: JSONObject): JSONObject {
        val connection = openConnection(path, method)
        return try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { stream ->
                stream.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            readJson(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String, method: String): HttpURLConnection {
        val base = endpoint.baseUrl.trimEnd('/')
        return (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("User-Agent", "Bark-Android/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 10_000
            readTimeout = 35_000
            // These credentials are only sent to the configured server, never redirected.
            instanceFollowRedirects = false
            deviceToken?.let { setRequestProperty("X-Bark-Device-Token", it) }
        }
    }

    private fun readJson(connection: HttpURLConnection): JSONObject {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() } }.orEmpty()
        if (code !in 200..299) {
            throw BarkHttpException(code, text.ifBlank { "HTTP $code" })
        }
        return JSONObject(text)
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> =
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, json.get(key))
            }
        }
}

data class RegistrationResult(
    val deviceKey: String,
    val deviceToken: String,
)

data class BarkSyncPage(val messages: List<BarkDelivery>, val more: Boolean)

class BarkHttpException(val statusCode: Int, message: String) : IllegalStateException(message)

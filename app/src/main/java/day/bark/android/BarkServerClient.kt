package day.bark.android

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class BarkServerClient(private val serverUrl: String) {
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
        register(deviceKey, "deleted")
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
            endpoint.authorizationHeader?.let {
                setRequestProperty("Authorization", it)
            }
            connectTimeout = 10_000
            readTimeout = 35_000
        }
    }

    private fun readJson(connection: HttpURLConnection): JSONObject {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        if (code !in 200..299) {
            throw IllegalStateException(text.ifBlank { "HTTP $code" })
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

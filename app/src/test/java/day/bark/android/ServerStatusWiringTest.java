package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ServerStatusWiringTest {
    @Test
    public void clientCanPingBarkServerHealthEndpoint() throws Exception {
        String client = readFile("src/main/java/day/bark/android/BarkServerClient.kt");

        assertTrue(client.contains("fun ping(): Boolean"));
        assertTrue(client.contains("private val endpoint = BarkServerEndpoint.from(serverUrl)"));
        assertTrue(client.contains("openConnection(\"/ping\", \"GET\")"));
        assertTrue(client.contains("readText()"));
        assertTrue(client.contains("text.trim() == \"pong\""));
        assertTrue(client.contains("JSONObject(text).optString(\"message\") == \"pong\""));
    }

    @Test
    public void clientSendsBasicAuthHeaderForCredentialedServerUrls() throws Exception {
        String client = readFile("src/main/java/day/bark/android/BarkServerClient.kt");

        assertTrue(client.contains("endpoint.baseUrl.trimEnd('/')"));
        assertTrue(client.contains("endpoint.authorizationHeader?.let {"));
        assertTrue(client.contains("setRequestProperty(\"Authorization\", it)"));
    }

    @Test
    public void mainActivityExposesSelectedAndListedServerChecks() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("button(\"Ping\") { pingSelectedServer() }"));
        assertTrue(activity.contains("private fun pingSelectedServer()"));
        assertTrue(activity.contains("private fun pingServer(address: String)"));
        assertTrue(activity.contains("button(\"Check\")"));
        assertTrue(activity.contains("BarkServerClient(address).ping()"));
        assertTrue(activity.contains("status(\"Online $address\")"));
        assertTrue(activity.contains("status(\"Offline $address"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

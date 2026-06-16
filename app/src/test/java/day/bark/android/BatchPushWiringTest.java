package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class BatchPushWiringTest {
    @Test
    public void clientSendsJsonDeviceKeysForBatchPush() throws Exception {
        String client = readFile("src/main/java/day/bark/android/BarkServerClient.kt");

        assertTrue(client.contains("import org.json.JSONArray"));
        assertTrue(client.contains("fun push(deviceKeys: List<String>, request: BarkPushRequest)"));
        assertTrue(client.contains("JSONObject().put(\"device_keys\", JSONArray(deviceKeys))"));
        assertTrue(client.contains("fun push(deviceKey: String, request: BarkPushRequest)"));
        assertTrue(client.contains(".put(\"device_key\", deviceKey)"));
    }

    @Test
    public void composerAcceptsOptionalBatchTargetKeys() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("private var pushDeviceKeysText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("Field(\"Device Keys\", pushDeviceKeysText)"));
        assertTrue(activity.contains("val targetKeys = BarkPushTargetKeys.parse(pushDeviceKeysText).ifEmpty { listOf(key) }"));
        assertTrue(activity.contains("BarkServerClient(settings.serverUrl).push(targetKeys, request)"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

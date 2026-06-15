package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class SharePushWiringTest {
    @Test
    public void mainActivitySendsSharedTextThroughCurrentBarkServer() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");
        String client = readFile("src/main/java/day/bark/android/BarkServerClient.kt");

        assertTrue(activity.contains("handleShareIntent(intent)"));
        assertTrue(activity.contains("intent.action == Intent.ACTION_SEND"));
        assertTrue(activity.contains("intent.type?.startsWith(\"text/\") == true"));
        assertTrue(activity.contains("Intent.EXTRA_TEXT"));
        assertTrue(activity.contains("Intent.EXTRA_SUBJECT"));
        assertTrue(activity.contains("BarkPushRequest.fromSharedText("));
        assertTrue(activity.contains("BarkServerClient(settings.serverUrl).push(key, request)"));
        assertTrue(activity.contains("status(\"Shared push sent\")"));
        assertTrue(client.contains("fun push(deviceKey: String, request: BarkPushRequest)"));
        assertTrue(client.contains(".put(\"device_key\", deviceKey)"));
        assertTrue(client.contains("request.toParameters().forEach"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

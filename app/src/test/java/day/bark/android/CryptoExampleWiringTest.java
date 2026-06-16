package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class CryptoExampleWiringTest {
    @Test
    public void mainActivityExposesCryptoExampleCopyAction() throws Exception {
        String activity = new String(
                Files.readAllBytes(Path.of("src/main/java/day/bark/android/MainActivity.kt")),
                StandardCharsets.UTF_8);

        assertTrue(activity.contains("SecondaryAction(\"Copy Example\") { copyCryptoExample() }"));
        assertTrue(activity.contains("private fun copyCryptoExample()"));
        assertTrue(activity.contains("BarkCryptoExampleScript.create("));
        assertTrue(activity.contains("settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER)"));
        assertTrue(activity.contains("CryptoSettings("));
        assertTrue(activity.contains("copyText(script)"));
        assertTrue(activity.contains("status(\"Copied crypto example\")"));
        assertTrue(activity.contains("status(error.message ?: \"Invalid crypto settings\")"));
    }
}

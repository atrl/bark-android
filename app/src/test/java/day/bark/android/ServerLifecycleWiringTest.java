package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ServerLifecycleWiringTest {
    @Test
    public void clientCanRemoteUnregisterADeviceKey() throws Exception {
        String client = readFile("src/main/java/day/bark/android/BarkServerClient.kt");

        assertTrue(client.contains("fun unregister(deviceKey: String)"));
        assertTrue(client.contains("register(deviceKey, \"deleted\")"));
    }

    @Test
    public void settingsCanRenameServersAndUpdateProfileKeys() throws Exception {
        String settings = readFile("src/main/java/day/bark/android/BarkSettingsStore.kt");

        assertTrue(settings.contains("var serverName: String?"));
        assertTrue(settings.contains("fun renameServer(id: String, name: String?)"));
        assertTrue(settings.contains("fun updateServerKey(id: String, key: String)"));
        assertTrue(settings.contains(".rename(id, name"));
        assertTrue(settings.contains(".updateKey(id, key"));
    }

    @Test
    public void mainActivityExposesProfileRenameResetAndRemoteDelete() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("serverNameInput"));
        assertTrue(activity.contains("edit(\"Name\")"));
        assertTrue(activity.contains("button(\"Rename\")"));
        assertTrue(activity.contains("button(\"Reset\")"));
        assertTrue(activity.contains("renameServer(profile.id)"));
        assertTrue(activity.contains("resetServer(profile)"));
        assertTrue(activity.contains("deleteServer(profile)"));
        assertTrue(activity.contains("BarkServerClient(profile.address).unregister(profile.key)"));
        assertTrue(activity.contains("BarkServerClient(profile.address).register(null, settings.installToken)"));
        assertTrue(activity.contains("settings.updateServerKey(profile.id, result.deviceKey)"));
        assertTrue(activity.contains("settings.renameServer(id, serverNameInput.text.toString()"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

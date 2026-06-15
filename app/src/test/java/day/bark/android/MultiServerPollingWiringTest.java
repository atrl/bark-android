package day.bark.android;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class MultiServerPollingWiringTest {
    @Test
    public void pollingServicePollsEveryRegisteredServerProfile() throws Exception {
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");

        assertTrue(service.contains("val targets = settings.serverProfiles().pollTargets()"));
        assertTrue(service.contains("for (target in targets)"));
        assertTrue(service.contains("BarkServerClient(target.address).poll(target.key"));
        assertFalse(service.contains("BarkServerClient(settings.serverUrl).poll(key"));
    }

    @Test
    public void registerAllFlowSyncsDeviceTokenToEverySavedServerProfile() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("private fun registerAllServers()"));
        assertTrue(activity.contains("val profiles = settings.serverProfiles()"));
        assertTrue(activity.contains("for (profile in profiles.profiles)"));
        assertTrue(activity.contains("BarkServerClient(profile.address)"));
        assertTrue(activity.contains("register(profile.key.takeIf { it.isNotBlank() }, settings.installToken)"));
        assertTrue(activity.contains("settings.updateServerKey(profile.id, result.deviceKey)"));
        assertFalse(activity.contains("BarkServerClient(settings.serverUrl)\n                .register(settings.deviceKey, settings.installToken)"));
    }

    @Test
    public void servicePrimaryRegisterOnlyRegistersCurrentProfile() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("button(\"Register Current Server\") { registerCurrentServer() }"));
        assertTrue(activity.contains("private fun registerCurrentServer()"));
        assertTrue(activity.contains("val profile = settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER)"));
        assertTrue(activity.contains("registerProfile(profile)"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

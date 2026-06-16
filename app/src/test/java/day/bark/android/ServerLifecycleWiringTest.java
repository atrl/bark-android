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

        assertTrue(activity.contains("serverNameText"));
        assertTrue(activity.contains("edit(\"Name\")"));
        assertTrue(activity.contains(".setItems(serverActionLabels())"));
        assertTrue(activity.contains("\"Rename\""));
        assertTrue(activity.contains("\"Register / Reset Key\""));
        assertTrue(activity.contains("renameServer(profile.id)"));
        assertTrue(activity.contains("resetServer(profile)"));
        assertTrue(activity.contains("deleteServer(profile)"));
        assertTrue(activity.contains("BarkServerClient(profile.address).unregister(profile.key)"));
        assertTrue(activity.contains("BarkServerClient(profile.address).register(null, settings.installToken)"));
        assertTrue(activity.contains("settings.updateServerKey(profile.id, result.deviceKey)"));
        assertTrue(activity.contains("settings.renameServer(id, nameInput.text.toString()"));
    }

    @Test
    public void serviceTabExposesServerDialogInsteadOfInlineServerEditor() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("private enum class MainTab(val title: String)"));
        assertTrue(activity.contains("NavigationBar"));
        assertTrue(activity.contains("MainTab.entries.forEach"));
        assertTrue(activity.contains("ServiceScreen("));
        assertTrue(activity.contains("SecondaryAction(\"Servers\") { showServerPicker() }"));
        assertTrue(activity.contains("private fun showServerPicker()"));
        assertTrue(activity.contains("private fun showAddServerDialog()"));
    }

    @Test
    public void androidDefaultsToOfficialBarkApi() throws Exception {
        String settings = readFile("src/main/java/day/bark/android/BarkSettingsStore.kt");

        assertTrue(settings.contains("const val DEFAULT_ANDROID_SERVER = BarkServerProfiles.DEFAULT_ADDRESS"));
        assertTrue(settings.contains("private const val LEGACY_EMULATOR_SERVER = \"http://10.0.2.2:8080\""));
        assertTrue(settings.contains("migrateBlankLegacyEmulatorDefault"));
    }

    @Test
    public void registerCurrentServerStartsListeningAfterSuccessfulRegistration() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");
        String successBlock = between(
            activity,
            ".onSuccess { result ->",
            "}.onFailure { error ->"
        );

        assertTrue(successBlock.contains("deviceKeyText = result.deviceKey"));
        assertTrue(successBlock.contains("startPollingService()"));
        assertTrue(successBlock.contains("status(\"Registered and listening\")"));
    }

    @Test
    public void listeningActionsRefreshServiceSummary() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");
        String startBlock = between(
            activity,
            "private fun startPollingService()",
            "private fun stopPollingService()"
        );
        String stopBlock = between(
            activity,
            "private fun stopPollingService()",
            "private fun refreshHistory()"
        );

        assertTrue(startBlock.contains("refreshServers()"));
        assertTrue(stopBlock.contains("refreshServers()"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    private static String between(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        int end = text.indexOf(endMarker, start);
        assertTrue("missing start marker: " + startMarker, start >= 0);
        assertTrue("missing end marker: " + endMarker, end > start);
        return text.substring(start, end);
    }
}

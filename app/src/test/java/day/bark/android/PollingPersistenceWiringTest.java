package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class PollingPersistenceWiringTest {
    @Test
    public void settingsPersistWhetherPollingShouldRestart() throws Exception {
        String settingsStore = readFile("src/main/java/day/bark/android/BarkSettingsStore.kt");

        assertTrue(settingsStore.contains("var listeningEnabled"));
        assertTrue(settingsStore.contains("\"listening_enabled\""));
    }

    @Test
    public void userAndServiceTransitionsUpdatePersistedListeningState() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");

        assertTrue(activity.contains("settings.listeningEnabled = true"));
        assertTrue(activity.contains("settings.listeningEnabled = false"));
        assertTrue(service.contains("settings.listeningEnabled = true"));
        assertTrue(service.contains("settings.listeningEnabled = false"));
    }

    @Test
    public void bootReceiverOnlyRestartsServiceWhenListeningWasEnabled() throws Exception {
        String manifest = readFile("src/main/AndroidManifest.xml");
        String receiver = readFile("src/main/java/day/bark/android/BarkBootReceiver.kt");

        assertTrue(manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"));
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"));
        assertTrue(manifest.contains("android:foregroundServiceType=\"remoteMessaging\""));
        assertTrue(manifest.contains("android:name=\".BarkBootReceiver\""));
        assertTrue(manifest.contains("android.intent.action.BOOT_COMPLETED"));
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"));
        assertTrue(receiver.contains("Intent.ACTION_BOOT_COMPLETED"));
        assertTrue(receiver.contains("Intent.ACTION_MY_PACKAGE_REPLACED"));
        assertTrue(receiver.contains("BarkSettingsStore(context).listeningEnabled"));
        assertTrue(receiver.contains("startForegroundService"));
    }

    @Test
    public void pollingServiceStopsRetryLoopWhenOfficialAndroidTokenIsMissing() throws Exception {
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");

        assertTrue(service.contains("isMissingAndroidDeviceToken(error)"));
        assertTrue(service.contains("settings.listeningEnabled = false"));
        assertTrue(service.contains("stopSelf()"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

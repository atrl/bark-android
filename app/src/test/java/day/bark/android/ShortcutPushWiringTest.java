package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ShortcutPushWiringTest {
    @Test
    public void manifestExposesAutomationPushReceiverForCurrentAndExplicitAddresses() throws Exception {
        String manifest = readFile("src/main/AndroidManifest.xml");

        assertTrue(manifest.contains("android:name=\".BarkShortcutPushReceiver\""));
        assertTrue(manifest.contains("android:exported=\"true\""));
        assertTrue(manifest.contains("day.bark.android.action.PUSH_CURRENT"));
        assertTrue(manifest.contains("day.bark.android.action.PUSH_ADDRESS"));
    }

    @Test
    public void shortcutReceiverSendsCurrentServerPushesWithoutOpeningTheApp() throws Exception {
        Path receiverPath = Path.of("src/main/java/day/bark/android/BarkShortcutPushReceiver.kt");

        assertTrue("BarkShortcutPushReceiver should provide Android shortcut/AppIntent parity", Files.exists(receiverPath));

        String receiver = readFile(receiverPath.toString());
        assertTrue(receiver.contains("class BarkShortcutPushReceiver : BroadcastReceiver()"));
        assertTrue(receiver.contains("val pendingResult = goAsync()"));
        assertTrue(receiver.contains("ACTION_PUSH_CURRENT -> pushToCurrentServer(context.applicationContext, intent)"));
        assertTrue(receiver.contains("val settings = BarkSettingsStore(context)"));
        assertTrue(receiver.contains("val key = settings.deviceKey?.takeIf { it.isNotBlank() } ?: error(\"Register first\")"));
        assertTrue(receiver.contains("BarkServerClient(settings.serverUrl).push(key, intent.toPushRequest())"));
        assertTrue(receiver.contains("toast(context, R.string.shortcut_push_sent)"));
        assertTrue(receiver.contains("pendingResult.finish()"));
    }

    @Test
    public void shortcutReceiverSendsExplicitBarkAddressPushesAndMapsAllPayloadFields() throws Exception {
        String receiver = readFile("src/main/java/day/bark/android/BarkShortcutPushReceiver.kt");
        String client = readFile("src/main/java/day/bark/android/BarkServerClient.kt");

        assertTrue(receiver.contains("ACTION_PUSH_ADDRESS -> pushToAddress(intent)"));
        assertTrue(receiver.contains("val address = intent.extraString(EXTRA_ADDRESS) ?: error(\"Address required\")"));
        assertTrue(receiver.contains("BarkServerClient(address).pushToAddress(intent.toPushRequest())"));
        assertTrue(receiver.contains("title = extraString(EXTRA_TITLE)"));
        assertTrue(receiver.contains("subtitle = extraString(EXTRA_SUBTITLE)"));
        assertTrue(receiver.contains("body = extraString(EXTRA_BODY)"));
        assertTrue(receiver.contains("isCall = extraBoolean(EXTRA_CALL)"));
        assertTrue(receiver.contains("isCritical = extraBoolean(EXTRA_CRITICAL)"));
        assertTrue(receiver.contains("volume = extraInt(EXTRA_VOLUME)"));
        assertTrue(receiver.contains("sound = extraString(EXTRA_SOUND)"));
        assertTrue(receiver.contains("icon = extraString(EXTRA_ICON)"));
        assertTrue(receiver.contains("group = extraString(EXTRA_GROUP)"));
        assertTrue(receiver.contains("url = extraString(EXTRA_URL)"));
        assertTrue(receiver.contains("archive = extraBooleanOrNull(EXTRA_ARCHIVE)"));
        assertTrue(receiver.contains("ACTION_PUSH_CURRENT = \"day.bark.android.action.PUSH_CURRENT\""));
        assertTrue(receiver.contains("ACTION_PUSH_ADDRESS = \"day.bark.android.action.PUSH_ADDRESS\""));

        assertTrue(client.contains("fun pushToAddress(request: BarkPushRequest)"));
        assertTrue(client.contains("request(\"POST\", \"\", body)"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

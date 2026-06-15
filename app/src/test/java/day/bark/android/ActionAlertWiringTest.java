package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ActionAlertWiringTest {
    @Test
    public void notifierRoutesActionAlertTapsBackToMainActivity() throws Exception {
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");

        assertTrue(notifier.contains("BarkTapAction.shouldShowAlert(message)"));
        assertTrue(notifier.contains("MainActivity.ACTION_SHOW_MESSAGE_ALERT"));
        assertTrue(notifier.contains("MainActivity.EXTRA_ALERT_COPY"));
        assertTrue(notifier.contains("MainActivity.EXTRA_ALERT_BODY"));
    }

    @Test
    public void mainActivityShowsCopyAndMoreActionsForAlertTap() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("ACTION_SHOW_MESSAGE_ALERT"));
        assertTrue(activity.contains("showNotificationAlert(intent)"));
        assertTrue(activity.contains("AlertDialog.Builder(this)"));
        assertTrue(activity.contains("setPositiveButton(\"Copy\")"));
        assertTrue(activity.contains("setNeutralButton(\"More\")"));
        assertTrue(activity.contains("shareNotificationAlert(intent)"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

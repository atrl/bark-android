package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class NotificationContentWiringTest {
    @Test
    public void notifierDisplaysBarkSubtitleAsNotificationSubText() throws Exception {
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");

        assertTrue(notifier.contains(".setContentTitle(message.title ?: context.getString(R.string.app_name))"));
        assertTrue(notifier.contains(".setSubText(message.subtitle?.takeIf { it.isNotBlank() })"));
        assertTrue(notifier.contains(".setContentText(message.displayBody ?: message.body.orEmpty())"));
    }

    @Test
    public void imageNotificationsKeepExpandedTitleAndText() throws Exception {
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");

        assertTrue(notifier.contains("Notification.BigPictureStyle()"));
        assertTrue(notifier.contains(".setBigContentTitle(message.title ?: context.getString(R.string.app_name))"));
        assertTrue(notifier.contains(".setSummaryText(message.displayBody ?: message.body.orEmpty())"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

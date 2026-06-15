package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class NotificationGroupWiringTest {
    @Test
    public void groupedNotificationsPublishSilentAndroidSummary() throws Exception {
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");

        assertTrue(notifier.contains("showGroupSummary(group, message)"));
        assertTrue(notifier.contains("private fun showGroupSummary(group: String, message: BarkMessage)"));
        assertTrue(notifier.contains(".setGroup(group)"));
        assertTrue(notifier.contains(".setGroupSummary(true)"));
        assertTrue(notifier.contains(".setDefaults(0)"));
        assertTrue(notifier.contains(".setSound(null)"));
        assertTrue(notifier.contains(".setVibrate(null)"));
        assertTrue(notifier.contains("notificationManager.notify(groupSummaryId(group), summary)"));
        assertTrue(notifier.contains("private fun groupSummaryId(group: String): Int"));
    }

    @Test
    public void defaultGroupNotificationsExposeMuteActionWithoutAndroidGroupSummary() throws Exception {
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");
        String receiver = readFile("src/main/java/day/bark/android/BarkNotificationActionReceiver.kt");
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");

        assertTrue(notifier.contains("val muteGroup = BarkGroupMutePolicy.groupKey(message.group)"));
        assertTrue(notifier.contains("muteGroupIntent(muteGroup)"));
        assertTrue(notifier.contains("val group = message.group?.takeIf { it.isNotBlank() }"));
        assertTrue(notifier.contains("if (group != null)"));
        assertTrue(receiver.contains("intent.getStringExtra(EXTRA_GROUP) ?: return"));
        assertTrue(receiver.contains("BarkGroupMutePolicy.displayName(group)"));
        assertTrue(service.contains("val group = BarkGroupMutePolicy.groupKey(message.group)"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

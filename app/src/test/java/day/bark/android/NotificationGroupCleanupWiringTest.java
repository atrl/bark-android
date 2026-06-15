package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class NotificationGroupCleanupWiringTest {
    @Test
    public void deletePushUsesStoredGroupContextWhenCancellingNotifications() throws Exception {
        String store = readFile("src/main/java/day/bark/android/BarkMessageStore.kt");
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");

        assertTrue(store.contains("fun groupFor(id: String): String?"));
        assertTrue(service.contains("message.group ?: deliveredStore.groupFor(message.id) ?: store.groupFor(message.id)"));
        assertTrue(service.contains("notifier.cancel(message.id, group)"));
        assertTrue(notifier.contains("fun cancel(messageId: String, group: String?)"));
        assertTrue(notifier.contains("cancelGroupSummaryIfLastChild(messageId, group)"));
    }

    @Test
    public void ttlCleanupPreservesGroupContextForSummaryCancellation() throws Exception {
        String store = readFile("src/main/java/day/bark/android/BarkMessageStore.kt");
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");

        assertTrue(store.contains("data class BarkNotificationRef("));
        assertTrue(store.contains("fun deleteExpired(nowMillis: Long = System.currentTimeMillis()): List<BarkNotificationRef>"));
        assertTrue(store.contains("arrayOf(\"id\", \"group_name\")"));
        assertTrue(service.contains("deliveredStore.delete(it.id)"));
        assertTrue(service.contains("notifier.cancel(it.id, it.group)"));
    }

    @Test
    public void sameIdGroupMoveCancelsPreviousGroupContextBeforeShowingUpdate() throws Exception {
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");

        assertTrue(service.contains("val previousGroup = deliveredStore.groupFor(message.id) ?: store.groupFor(message.id)"));
        assertTrue(service.contains("BarkNotificationGroupUpdate.staleGroupToCancel(previousGroup, message.group)"));
        assertTrue(service.contains("notifier.cancel(message.id, staleGroup)"));
    }

    @Test
    public void notifierCancelsGroupSummaryOnlyWhenNoOtherChildrenRemain() throws Exception {
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");

        assertTrue(notifier.contains("private fun cancelGroupSummaryIfLastChild(messageId: String, group: String?)"));
        assertTrue(notifier.contains("notificationManager.activeNotifications.none"));
        assertTrue(notifier.contains("record.id != messageId.hashCode()"));
        assertTrue(notifier.contains("record.id != groupSummaryId(normalizedGroup)"));
        assertTrue(notifier.contains("record.notification.group == normalizedGroup"));
        assertTrue(notifier.contains("Notification.FLAG_GROUP_SUMMARY"));
        assertTrue(notifier.contains("notificationManager.cancel(groupSummaryId(normalizedGroup))"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class TtlNotificationCleanupWiringTest {
    @Test
    public void messageStoreReturnsExpiredNotificationRefsWhenCleaningTtlRows() throws Exception {
        String store = readFile("src/main/java/day/bark/android/BarkMessageStore.kt");

        assertTrue(store.contains("data class BarkNotificationRef("));
        assertTrue(store.contains("fun deleteExpired(nowMillis: Long = System.currentTimeMillis()): List<BarkNotificationRef>"));
        assertTrue(store.contains("val expiredRefs = expiredNotificationRefs(nowMillis)"));
        assertTrue(store.contains("return expiredRefs"));
    }

    @Test
    public void pollingServiceCancelsDeliveredNotificationsForExpiredHistoryRows() throws Exception {
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");

        assertTrue(service.contains("private fun cancelExpiredNotifications()"));
        assertTrue(service.contains("store.deleteExpired().forEach {"));
        assertTrue(service.contains("deliveredStore.delete(it.id)"));
        assertTrue(service.contains("notifier.cancel(it.id, it.group)"));
        assertTrue(service.contains("cancelExpiredNotifications()"));
        assertTrue(notifier.contains("fun cancel(messageId: String)"));
        assertTrue(notifier.contains("fun cancel(messageId: String, group: String?)"));
        assertTrue(notifier.contains("notificationManager.cancel(messageId.hashCode())"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

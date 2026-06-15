package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class DeliveredNotificationRegistryWiringTest {
    @Test
    public void pollingServiceStoresDeliveredGroupContextOutsideHistoryArchive() throws Exception {
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");

        assertTrue(service.contains("private lateinit var deliveredStore: BarkDeliveredNotificationStore"));
        assertTrue(service.contains("deliveredStore = BarkDeliveredNotificationStore(this)"));
        assertTrue(service.contains("if (notifier.show(notificationMessage))"));
        assertTrue(service.contains("deliveredStore.save(message.id, message.group)"));
    }

    @Test
    public void deletePushFallsBackToDeliveredRegistryBeforeArchivedHistory() throws Exception {
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");

        assertTrue(service.contains("message.group ?: deliveredStore.groupFor(message.id) ?: store.groupFor(message.id)"));
        assertTrue(service.contains("deliveredStore.delete(message.id)"));
        assertTrue(service.contains("notifier.cancel(message.id, group)"));
    }

    @Test
    public void notificationDismissalRemovesDeliveredRegistryEntry() throws Exception {
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");
        String receiver = readFile("src/main/java/day/bark/android/BarkNotificationActionReceiver.kt");

        assertTrue(notifier.contains(".setDeleteIntent(notificationDismissedIntent(message.id))"));
        assertTrue(notifier.contains("private fun notificationDismissedIntent(messageId: String): PendingIntent"));
        assertTrue(receiver.contains("ACTION_NOTIFICATION_DISMISSED -> notificationDismissed(context, intent)"));
        assertTrue(receiver.contains("BarkDeliveredNotificationStore(context).delete(messageId)"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

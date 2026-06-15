package day.bark.android;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class NotificationLevelWiringTest {
    @Test
    public void notifierNormalizesLevelBeforePriorityImportanceAndAlarmUsageChecks() throws Exception {
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");

        assertTrue(notifier.contains("when (message.level?.lowercase())"));
        assertTrue(notifier.contains("val level = message.level?.lowercase()"));
        assertTrue(notifier.contains("level == \"passive\" -> NotificationManager.IMPORTANCE_LOW"));
        assertTrue(notifier.contains("level in setOf(\"timesensitive\", \"critical\") ->"));
        assertTrue(notifier.contains("message.level?.lowercase() == \"critical\""));
        assertFalse(notifier.contains("message.level == \"passive\""));
        assertFalse(notifier.contains("message.level == \"timeSensitive\""));
        assertFalse(notifier.contains("message.level == \"critical\""));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

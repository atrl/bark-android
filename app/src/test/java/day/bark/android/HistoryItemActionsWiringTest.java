package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class HistoryItemActionsWiringTest {
    @Test
    public void historyItemsExposeCopyOpenAndDeleteActions() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("private fun HistoryItem(message: BarkMessage, dateFormat: DateFormat)"));
        assertTrue(activity.contains("SecondaryAction(\"Copy\") { copyHistoryMessage(message) }"));
        assertTrue(activity.contains("SecondaryAction(\"Open\") { openHistoryMessage(message) }"));
        assertTrue(activity.contains("SecondaryAction(\"Delete\") { deleteHistoryMessage(message) }"));
        assertTrue(activity.contains("private fun copyHistoryMessage(message: BarkMessage)"));
        assertTrue(activity.contains("BarkCopyText.from(message)"));
        assertTrue(activity.contains("private fun openHistoryMessage(message: BarkMessage)"));
        assertTrue(activity.contains("BarkTapAction.urlToOpen(message)"));
        assertTrue(activity.contains("private fun deleteHistoryMessage(message: BarkMessage)"));
        assertTrue(activity.contains("store.delete(message.id)"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class HistoryBackupWiringTest {
    @Test
    public void messageStoreExportsAndRestoresHistoryBackupJson() throws Exception {
        String store = readFile("src/main/java/day/bark/android/BarkMessageStore.kt");

        assertTrue(store.contains("fun exportBackupJson(): String"));
        assertTrue(store.contains("BarkMessageBackupCodec.encode"));
        assertTrue(store.contains("fun restoreBackupJson(json: String): Int"));
        assertTrue(store.contains("BarkMessageBackupCodec.decode"));
        assertTrue(store.contains("saveAll(messages)"));
    }

    @Test
    public void mainActivityExposesHistoryExportAndImportControls() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("HISTORY_IMPORT_REQUEST"));
        assertTrue(activity.contains("SecondaryAction(\"Export\") { exportHistory() }"));
        assertTrue(activity.contains("SecondaryAction(\"Import\") { startHistoryImport() }"));
        assertTrue(activity.contains("Intent(Intent.ACTION_OPEN_DOCUMENT)"));
        assertTrue(activity.contains("setType(\"application/json\")"));
        assertTrue(activity.contains("store.exportBackupJson()"));
        assertTrue(activity.contains("store.restoreBackupJson(json)"));
        assertTrue(activity.contains("Intent(Intent.ACTION_SEND)"));
        assertTrue(activity.contains("bark_messages_"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

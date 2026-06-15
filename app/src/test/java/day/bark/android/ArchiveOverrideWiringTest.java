package day.bark.android;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ArchiveOverrideWiringTest {
    @Test
    public void pollingServiceUsesArchivePolicyInsteadOfAndingGlobalSetting() throws Exception {
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");

        assertTrue(service.contains("BarkArchivePolicy.shouldStore(message, settings.archiveEnabled)"));
        assertFalse(service.contains("settings.archiveEnabled && message.shouldArchive"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

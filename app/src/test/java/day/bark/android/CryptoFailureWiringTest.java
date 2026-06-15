package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class CryptoFailureWiringTest {
    @Test
    public void decryptionFailureNotificationIsNotArchived() throws Exception {
        String service = readFile("src/main/java/day/bark/android/BarkPollingService.kt");

        assertTrue(service.contains("body = \"Decryption Failed\""));
        assertTrue(service.contains("displayBody = \"Decryption Failed\""));
        assertTrue(service.contains("shouldArchive = false"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

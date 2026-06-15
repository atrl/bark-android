package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class CustomSoundCallAlertWiringTest {
    @Test
    public void callAlertPlayerResolvesBuiltInAndCustomSoundsBeforeLooping() throws Exception {
        String player = readFile("src/main/java/day/bark/android/BarkCallAlertPlayer.kt");

        assertTrue(player.contains("val uri = resolvedSoundUri(appContext, message) ?: return"));
        assertTrue(player.contains("private fun resolvedSoundUri(context: Context, message: BarkMessage): Uri?"));
        assertTrue(player.contains("BarkSoundPolicy.resourceNameFor(message)?.let"));
        assertTrue(player.contains("BarkCustomSoundStore(context).find(message.sound)"));
        assertTrue(player.contains("Uri.fromFile(custom.file)"));
        assertTrue(player.contains("MediaPlayer.create(appContext, uri)"));
        assertTrue(player.contains("player.isLooping = true"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

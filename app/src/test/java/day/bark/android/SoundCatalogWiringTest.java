package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class SoundCatalogWiringTest {
    @Test
    public void mainActivityExposesBuiltInSoundListWithPlayAndCopyActions() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("SoundsPanel("));
        assertTrue(activity.contains("SectionCard(\"Sounds\")"));
        assertTrue(activity.contains("refreshSounds()"));
        assertTrue(activity.contains("BarkSoundCatalog.builtInSounds.forEach"));
        assertTrue(activity.contains("SecondaryAction(\"Play\") { playSound(sound.name) }"));
        assertTrue(activity.contains("SecondaryAction(\"Copy\") { copySoundName(sound.name) }"));
        assertTrue(activity.contains("private fun copySoundName(soundName: String)"));
        assertTrue(activity.contains("copyText(soundName)"));
        assertTrue(activity.contains("private fun playSound(soundName: String)"));
        assertTrue(activity.contains("MediaPlayer.create(this, rawSoundResource(soundName))"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

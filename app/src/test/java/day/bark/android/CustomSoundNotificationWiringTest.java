package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class CustomSoundNotificationWiringTest {
    @Test
    public void customSoundStoreCanFindImportedSoundByPayloadName() throws Exception {
        String store = readFile("src/main/java/day/bark/android/BarkCustomSoundStore.kt");

        assertTrue(store.contains("fun find(soundName: String?): BarkCustomSound?"));
        assertTrue(store.contains("val copyName = BarkCustomSoundName.copyName(soundName)"));
        assertTrue(store.contains("return list().firstOrNull { it.name == copyName }"));
    }

    @Test
    public void notifierUsesImportedCustomSoundForNotificationChannels() throws Exception {
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");

        assertTrue(notifier.contains("private fun resolvedSound(message: BarkMessage): Uri?"));
        assertTrue(notifier.contains("BarkCustomSoundStore(context).find(message.sound)"));
        assertTrue(notifier.contains("FileProvider.getUriForFile("));
        assertTrue(notifier.contains("\"${context.packageName}.soundprovider\""));
        assertTrue(notifier.contains("val channelId = channelIdFor(message)"));
        assertTrue(notifier.contains("resolvedSound(message)?.let { soundUri ->"));
        assertTrue(notifier.contains("channel.setSound(soundUri, soundAttributes(message))"));
        assertTrue(notifier.contains("\"${base}_custom_${custom.name}\""));
    }

    @Test
    public void manifestExposesPrivateSoundFilesThroughFileProvider() throws Exception {
        String manifest = readFile("src/main/AndroidManifest.xml");
        String paths = readFile("src/main/res/xml/bark_sound_paths.xml");

        assertTrue(manifest.contains("androidx.core.content.FileProvider"));
        assertTrue(manifest.contains("android:authorities=\"${applicationId}.soundprovider\""));
        assertTrue(manifest.contains("@xml/bark_sound_paths"));
        assertTrue(paths.contains("<files-path"));
        assertTrue(paths.contains("path=\"sounds/\""));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

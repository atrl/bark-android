package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class CustomSoundImportWiringTest {
    @Test
    public void customSoundStoreImportsListsAndDeletesAudioFiles() throws Exception {
        String store = readFile("src/main/java/day/bark/android/BarkCustomSoundStore.kt");

        assertTrue(store.contains("class BarkCustomSoundStore"));
        assertTrue(store.contains("fun import(uri: Uri): BarkCustomSound"));
        assertTrue(store.contains("fun list(): List<BarkCustomSound>"));
        assertTrue(store.contains("fun delete(sound: BarkCustomSound)"));
        assertTrue(store.contains("BarkCustomSoundName.storageFileName"));
        assertTrue(store.contains("contentResolver.openInputStream(uri)"));
    }

    @Test
    public void mainActivityExposesCustomSoundImportPlayCopyAndDelete() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("PrimaryAction(\"Import Sound\") { startSoundImport() }"));
        assertTrue(activity.contains("SOUND_IMPORT_REQUEST"));
        assertTrue(activity.contains("Intent(Intent.ACTION_OPEN_DOCUMENT)"));
        assertTrue(activity.contains("setType(\"audio/*\")"));
        assertTrue(activity.contains("val uri = data?.data"));
        assertTrue(activity.contains("importCustomSound(uri)"));
        assertTrue(activity.contains("BarkCustomSoundStore(this@MainActivity).list()"));
        assertTrue(activity.contains("SecondaryAction(\"Play\") { playCustomSound(sound) }"));
        assertTrue(activity.contains("SecondaryAction(\"Copy\") { copySoundName(sound.name) }"));
        assertTrue(activity.contains("SecondaryAction(\"Delete\") { deleteCustomSound(sound) }"));
        assertTrue(activity.contains("MediaPlayer.create(this, Uri.fromFile(sound.file))"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

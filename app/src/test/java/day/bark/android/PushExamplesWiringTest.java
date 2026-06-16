package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class PushExamplesWiringTest {
    @Test
    public void mainActivityExposesPushExamplesFromCatalog() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("ExamplesPanel()"));
        assertTrue(activity.contains("SectionCard(\"Examples\")"));
        assertTrue(activity.contains("refreshExamples()"));
        assertTrue(activity.contains("BarkPushExampleCatalog.examples.forEach"));
        assertTrue(activity.contains("example.url(settings.serverProfiles().current(BarkSettingsStore.DEFAULT_ANDROID_SERVER))"));
        assertTrue(activity.contains("SecondaryAction(\"Copy\") { copyPushExample(example) }"));
        assertTrue(activity.contains("SecondaryAction(\"Open\") { openPushExample(example) }"));
        assertTrue(activity.contains("private fun copyPushExample(example: BarkPushExample)"));
        assertTrue(activity.contains("private fun openPushExample(example: BarkPushExample)"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

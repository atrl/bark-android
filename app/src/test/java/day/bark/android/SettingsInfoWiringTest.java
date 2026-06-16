package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class SettingsInfoWiringTest {
    @Test
    public void mainActivityExposesMaskedDeviceTokenAndCopyAction() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("SectionCard(\"Info\")"));
        assertTrue(activity.contains("BarkDeviceTokenText.mask(settings.installToken)"));
        assertTrue(activity.contains("SecondaryAction(\"Copy Token\") { copyDeviceToken() }"));
        assertTrue(activity.contains("private fun copyDeviceToken()"));
        assertTrue(activity.contains("copyText(settings.installToken)"));
    }

    @Test
    public void mainActivityExposesBarkSettingsLinks() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("SecondaryAction(\"FAQ\") { openExternalUrl(BARK_FAQ_URL) }"));
        assertTrue(activity.contains("SecondaryAction(\"Documentation\") { openExternalUrl(BARK_DOC_URL) }"));
        assertTrue(activity.contains("SecondaryAction(\"Source Code\") { openExternalUrl(BARK_SOURCE_URL) }"));
        assertTrue(activity.contains("SecondaryAction(\"Privacy Policy\") { openExternalUrl(BARK_PRIVACY_URL) }"));
        assertTrue(activity.contains("Intent(Intent.ACTION_VIEW, Uri.parse(url))"));
        assertTrue(activity.contains("BARK_FAQ_URL = \"https://bark.day.app/#/en-us/faq\""));
        assertTrue(activity.contains("BARK_DOC_URL = \"https://bark.day.app/#/en-us/?id=bark\""));
        assertTrue(activity.contains("BARK_SOURCE_URL = \"https://github.com/Finb/Bark\""));
        assertTrue(activity.contains("BARK_PRIVACY_URL = \"https://api.day.app/privacy\""));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class PushComposerWiringTest {
    @Test
    public void mainActivityExposesAdvancedPushComposerLikeAppIntentParameters() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("private var pushTitleText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushSubtitleText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushBodyText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushIdText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushMarkdownText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushSoundText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushLevelText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushIconText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushImageText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushUrlText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushActionText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushCiphertextText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushIvText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushGroupText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushVolumeText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushBadgeText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushCopyText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushArchiveText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushTtlText by mutableStateOf(\"\")"));
        assertTrue(activity.contains("private var pushCallChecked by mutableStateOf(false)"));
        assertTrue(activity.contains("private var pushCriticalChecked by mutableStateOf(false)"));
        assertTrue(activity.contains("private var pushAutoCopyChecked by mutableStateOf(false)"));
        assertTrue(activity.contains("private var pushDeleteChecked by mutableStateOf(false)"));
        assertTrue(activity.contains("SectionCard(\"Push Composer\")"));
        assertTrue(activity.contains("PrimaryAction(\"Send Push\") { sendCustomPush() }"));
        assertTrue(activity.contains("private fun sendCustomPush()"));
        assertTrue(activity.contains("BarkPushRequest("));
        assertTrue(activity.contains("title = pushTitleText"));
        assertTrue(activity.contains("subtitle = pushSubtitleText"));
        assertTrue(activity.contains("body = pushBodyText"));
        assertTrue(activity.contains("id = pushIdText"));
        assertTrue(activity.contains("markdown = pushMarkdownText"));
        assertTrue(activity.contains("level = pushLevelText"));
        assertTrue(activity.contains("isCall = pushCallChecked"));
        assertTrue(activity.contains("isCritical = pushCriticalChecked"));
        assertTrue(activity.contains("volume = pushVolumeText.toIntOrNull()"));
        assertTrue(activity.contains("badge = pushBadgeText.toIntOrNull()"));
        assertTrue(activity.contains("autoCopy = pushAutoCopyChecked"));
        assertTrue(activity.contains("copy = pushCopyText"));
        assertTrue(activity.contains("sound = pushSoundText"));
        assertTrue(activity.contains("icon = pushIconText"));
        assertTrue(activity.contains("image = pushImageText"));
        assertTrue(activity.contains("group = pushGroupText"));
        assertTrue(activity.contains("archive = pushArchiveText.toArchiveFlagOrNull()"));
        assertTrue(activity.contains("ttlSeconds = pushTtlText.toLongOrNull()"));
        assertTrue(activity.contains("url = pushUrlText"));
        assertTrue(activity.contains("action = pushActionText"));
        assertTrue(activity.contains("ciphertext = pushCiphertextText"));
        assertTrue(activity.contains("iv = pushIvText"));
        assertTrue(activity.contains("isDelete = pushDeleteChecked"));
        assertTrue(activity.contains("private fun String.toArchiveFlagOrNull(): Boolean?"));
        assertTrue(activity.contains("BarkServerClient(settings.serverUrl).push(targetKeys, request)"));
        assertTrue(activity.contains("status(\"Push sent\")"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

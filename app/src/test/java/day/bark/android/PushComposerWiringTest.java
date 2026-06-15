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

        assertTrue(activity.contains("private lateinit var pushTitleInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushSubtitleInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushBodyInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushIdInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushMarkdownInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushSoundInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushLevelInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushIconInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushImageInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushUrlInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushActionInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushCiphertextInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushIvInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushGroupInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushVolumeInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushBadgeInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushCopyInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushArchiveInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushTtlInput: EditText"));
        assertTrue(activity.contains("private lateinit var pushCallCheck: CheckBox"));
        assertTrue(activity.contains("private lateinit var pushCriticalCheck: CheckBox"));
        assertTrue(activity.contains("private lateinit var pushAutoCopyCheck: CheckBox"));
        assertTrue(activity.contains("private lateinit var pushDeleteCheck: CheckBox"));
        assertTrue(activity.contains("section(\"Push\")"));
        assertTrue(activity.contains("button(\"Send Push\") { sendCustomPush() }"));
        assertTrue(activity.contains("private fun sendCustomPush()"));
        assertTrue(activity.contains("BarkPushRequest("));
        assertTrue(activity.contains("title = pushTitleInput.text.toString()"));
        assertTrue(activity.contains("subtitle = pushSubtitleInput.text.toString()"));
        assertTrue(activity.contains("body = pushBodyInput.text.toString()"));
        assertTrue(activity.contains("id = pushIdInput.text.toString()"));
        assertTrue(activity.contains("markdown = pushMarkdownInput.text.toString()"));
        assertTrue(activity.contains("level = pushLevelInput.text.toString()"));
        assertTrue(activity.contains("isCall = pushCallCheck.isChecked"));
        assertTrue(activity.contains("isCritical = pushCriticalCheck.isChecked"));
        assertTrue(activity.contains("volume = pushVolumeInput.text.toString().toIntOrNull()"));
        assertTrue(activity.contains("badge = pushBadgeInput.text.toString().toIntOrNull()"));
        assertTrue(activity.contains("autoCopy = pushAutoCopyCheck.isChecked"));
        assertTrue(activity.contains("copy = pushCopyInput.text.toString()"));
        assertTrue(activity.contains("sound = pushSoundInput.text.toString()"));
        assertTrue(activity.contains("icon = pushIconInput.text.toString()"));
        assertTrue(activity.contains("image = pushImageInput.text.toString()"));
        assertTrue(activity.contains("group = pushGroupInput.text.toString()"));
        assertTrue(activity.contains("archive = pushArchiveInput.text.toString().toArchiveFlagOrNull()"));
        assertTrue(activity.contains("ttlSeconds = pushTtlInput.text.toString().toLongOrNull()"));
        assertTrue(activity.contains("url = pushUrlInput.text.toString()"));
        assertTrue(activity.contains("action = pushActionInput.text.toString()"));
        assertTrue(activity.contains("ciphertext = pushCiphertextInput.text.toString()"));
        assertTrue(activity.contains("iv = pushIvInput.text.toString()"));
        assertTrue(activity.contains("isDelete = pushDeleteCheck.isChecked"));
        assertTrue(activity.contains("private fun String.toArchiveFlagOrNull(): Boolean?"));
        assertTrue(activity.contains("BarkServerClient(settings.serverUrl).push(key, request)"));
        assertTrue(activity.contains("status(\"Push sent\")"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

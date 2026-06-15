package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class GitHubActionsReleaseWiringTest {
    @Test
    public void gradleReleaseSigningReadsOnlyEnvironmentBackedSecrets() throws Exception {
        String gradle = readFile("build.gradle.kts");

        assertTrue(gradle.contains("BARK_ANDROID_KEYSTORE_PATH"));
        assertTrue(gradle.contains("BARK_ANDROID_KEYSTORE_PASSWORD"));
        assertTrue(gradle.contains("BARK_ANDROID_KEY_ALIAS"));
        assertTrue(gradle.contains("BARK_ANDROID_KEY_PASSWORD"));
        assertTrue(gradle.contains("create(\"release\")"));
        assertTrue(gradle.contains("signingConfig = signingConfigs.getByName(\"release\")"));
    }

    @Test
    public void ciWorkflowPublishesDebugApkWithoutSigningSecrets() throws Exception {
        String workflow = readFile("../.github/workflows/ci.yml");

        assertTrue(workflow.contains("name: Android CI"));
        assertTrue(workflow.contains(":core:test"));
        assertTrue(workflow.contains(":app:testDebugUnitTest"));
        assertTrue(workflow.contains(":app:assembleDebug"));
        assertTrue(workflow.contains("actions/upload-artifact"));
        assertTrue(workflow.contains("app/build/outputs/apk/debug/*.apk"));
    }

    @Test
    public void releaseWorkflowBuildsSignedApkAndAabFromGitHubSecrets() throws Exception {
        String workflow = readFile("../.github/workflows/release.yml");

        assertTrue(workflow.contains("name: Android Release"));
        assertTrue(workflow.contains("workflow_dispatch:"));
        assertTrue(workflow.contains("BARK_ANDROID_KEYSTORE_BASE64"));
        assertTrue(workflow.contains("BARK_ANDROID_KEYSTORE_PASSWORD"));
        assertTrue(workflow.contains("BARK_ANDROID_KEY_ALIAS"));
        assertTrue(workflow.contains("BARK_ANDROID_KEY_PASSWORD"));
        assertTrue(workflow.contains(":app:bundleRelease"));
        assertTrue(workflow.contains(":app:assembleRelease"));
        assertTrue(workflow.contains("-x lintVitalAnalyzeRelease"));
        assertTrue(workflow.contains("app/build/outputs/bundle/release/*.aab"));
        assertTrue(workflow.contains("app/build/outputs/apk/release/*.apk"));
        assertTrue(workflow.contains("softprops/action-gh-release"));
        assertTrue(workflow.contains("app/build/outputs/apk/release/*.apk"));
        assertTrue(workflow.contains("app/build/outputs/bundle/release/*.aab"));
    }

    @Test
    public void readmeDocumentsStoreSigningChoicesAndRequiredSecrets() throws Exception {
        String readme = readFile("../README.md");

        assertTrue(readme.contains("BARK_ANDROID_KEYSTORE_BASE64"));
        assertTrue(readme.contains("Google Play"));
        assertTrue(readme.contains("AAB"));
        assertTrue(readme.contains("signed APK"));
        assertTrue(readme.contains("upload key"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

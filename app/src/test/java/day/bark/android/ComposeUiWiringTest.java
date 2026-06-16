package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ComposeUiWiringTest {
    @Test
    public void appModuleEnablesComposeMaterial3Ui() throws Exception {
        String build = readFile("build.gradle.kts");

        assertTrue(!build.contains("org.jetbrains.kotlin.android"));
        assertTrue(build.contains("org.jetbrains.kotlin.plugin.compose"));
        assertTrue(build.contains("buildFeatures"));
        assertTrue(build.contains("compose = true"));
        assertTrue(build.contains("androidx.compose.material3:material3"));
        assertTrue(build.contains("androidx.activity:activity-compose"));
    }

    @Test
    public void mainActivityUsesComposeScreens() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("setContent"));
        assertTrue(activity.contains("BarkApp("));
        assertTrue(activity.contains("NavigationBar"));
        assertTrue(activity.contains("ServiceScreen("));
        assertTrue(activity.contains("HistoryScreen("));
        assertTrue(activity.contains("SettingsScreen("));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

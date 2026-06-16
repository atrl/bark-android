package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class WidgetHistoryDeepLinkWiringTest {
    @Test
    public void mainActivityHandlesWidgetHistoryDeepLinksByRefreshingComposeHistory() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("handleHistoryDeepLink(intent.data)"));
        assertTrue(activity.contains("private fun handleHistoryDeepLink(uri: Uri?): Boolean"));
        assertTrue(activity.contains("uri.host?.equals(\"history\", ignoreCase = true) == true"));
        assertTrue(activity.contains("historySearchText = null"));
        assertTrue(activity.contains("historySearchInputText = \"\""));
        assertTrue(activity.contains("selectedHistoryGroups = emptySet()"));
        assertTrue(activity.contains("uri.getQueryParameter(\"group\")"));
        assertTrue(activity.contains("selectedHistoryGroups = setOf(group)"));
        assertTrue(activity.contains("refreshHistory()"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

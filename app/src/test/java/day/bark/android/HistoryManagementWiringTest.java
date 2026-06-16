package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class HistoryManagementWiringTest {
    @Test
    public void messageStoreSupportsSearchAndDeleteRanges() throws Exception {
        String store = readFile("src/main/java/day/bark/android/BarkMessageStore.kt");

        assertTrue(store.contains("fun recent(limit: Int = 100, searchText: String? = null, group: String? = null)"));
        assertTrue(store.contains("title LIKE ? OR subtitle LIKE ? OR body LIKE ? OR display_body LIKE ?"));
        assertTrue(store.contains("fun delete(range: BarkHistoryDeleteRange"));
        assertTrue(store.contains("BarkHistoryDeletePolicy.window"));
    }

    @Test
    public void messageStoreCanDeleteOneHistoryGroup() throws Exception {
        String store = readFile("src/main/java/day/bark/android/BarkMessageStore.kt");

        assertTrue(store.contains("fun deleteGroup(group: String?)"));
        assertTrue(store.contains("val selectedGroup = BarkHistoryView.normalizeGroup(group)"));
        assertTrue(store.contains("\"group_name = ?\""));
        assertTrue(store.contains("\"group_name IS NULL OR TRIM(group_name) = ''\""));
        assertTrue(store.contains("notifyHistoryChanged()"));
    }

    @Test
    public void mainActivityExposesSearchAndRangeClearControls() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("historySearchInputText"));
        assertTrue(activity.contains("Field(\"Search history\", historySearchInputText)"));
        assertTrue(activity.contains("PrimaryAction(\"Search\")"));
        assertTrue(activity.contains("SecondaryAction(\"Reset\")"));
        assertTrue(activity.contains("SecondaryAction(\"Clear 1h\")"));
        assertTrue(activity.contains("SecondaryAction(\"Clear Today\")"));
        assertTrue(activity.contains("SecondaryAction(\"Today+Yesterday\")"));
        assertTrue(activity.contains("SecondaryAction(\"Clear Month\")"));
        assertTrue(activity.contains("SecondaryAction(\"Before 1h\")"));
        assertTrue(activity.contains("SecondaryAction(\"Before Today\")"));
        assertTrue(activity.contains("SecondaryAction(\"Before Yesterday\")"));
        assertTrue(activity.contains("SecondaryAction(\"Clear Old\")"));
        assertTrue(activity.contains("SecondaryAction(\"Clear All\")"));
        assertTrue(activity.contains("SecondaryAction(\"Clear Group\") { clearHistoryGroup(selectedHistoryGroups.first()) }"));
        assertTrue(activity.contains("clearHistory(BarkHistoryDeleteRange.LAST_HOUR)"));
        assertTrue(activity.contains("clearHistory(BarkHistoryDeleteRange.TODAY)"));
        assertTrue(activity.contains("clearHistory(BarkHistoryDeleteRange.TODAY_AND_YESTERDAY)"));
        assertTrue(activity.contains("clearHistory(BarkHistoryDeleteRange.LAST_MONTH)"));
        assertTrue(activity.contains("clearHistory(BarkHistoryDeleteRange.BEFORE_ONE_HOUR)"));
        assertTrue(activity.contains("clearHistory(BarkHistoryDeleteRange.BEFORE_TODAY)"));
        assertTrue(activity.contains("clearHistory(BarkHistoryDeleteRange.BEFORE_YESTERDAY)"));
        assertTrue(activity.contains("clearHistory(BarkHistoryDeleteRange.BEFORE_ONE_MONTH)"));
        assertTrue(activity.contains("clearHistory(BarkHistoryDeleteRange.ALL_TIME)"));
        assertTrue(activity.contains("private fun clearHistoryGroup(group: String?)"));
        assertTrue(activity.contains("store.deleteGroup(group)"));
        assertTrue(activity.contains("store.recent(searchText = historySearchText)"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

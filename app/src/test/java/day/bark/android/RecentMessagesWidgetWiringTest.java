package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class RecentMessagesWidgetWiringTest {
    @Test
    public void manifestAndResourcesExposeRecentMessagesWidget() throws Exception {
        String manifest = readFile("src/main/AndroidManifest.xml");
        String info = readFile("src/main/res/xml/bark_recent_messages_widget_info.xml");
        String layout = readFile("src/main/res/layout/bark_recent_messages_widget.xml");

        assertTrue(manifest.contains("android:name=\".BarkRecentMessagesWidgetProvider\""));
        assertTrue(manifest.contains("android:name=\".BarkWidgetConfigureActivity\""));
        assertTrue(manifest.contains("android.appwidget.action.APPWIDGET_UPDATE"));
        assertTrue(manifest.contains("@xml/bark_recent_messages_widget_info"));
        assertTrue(info.contains("android:initialLayout=\"@layout/bark_recent_messages_widget\""));
        assertTrue(info.contains("android:configure=\"day.bark.android.BarkWidgetConfigureActivity\""));
        assertTrue(info.contains("android:widgetCategory=\"home_screen\""));
        assertTrue(layout.contains("@+id/widget_root"));
        assertTrue(layout.contains("@+id/widget_title"));
        assertTrue(layout.contains("@+id/widget_messages"));
    }

    @Test
    public void widgetProviderRendersLatestHistoryMessages() throws Exception {
        String provider = readFile("src/main/java/day/bark/android/BarkRecentMessagesWidgetProvider.kt");

        assertTrue(provider.contains("class BarkRecentMessagesWidgetProvider : AppWidgetProvider()"));
        assertTrue(provider.contains("val selectedGroup = BarkWidgetGroupPreferences.selectedGroup(context, appWidgetId)"));
        assertTrue(provider.contains("BarkMessageStore(context).recent(limit = 3, group = selectedGroup)"));
        assertTrue(provider.contains("BarkHistoryMessageText.format("));
        assertTrue(provider.contains("RemoteViews(context.packageName, R.layout.bark_recent_messages_widget)"));
        assertTrue(provider.contains("views.setTextViewText(R.id.widget_title, widgetTitle(context, selectedGroup))"));
        assertTrue(provider.contains("views.setTextViewText(R.id.widget_messages, widgetText(context, selectedGroup))"));
        assertTrue(provider.contains("views.setOnClickPendingIntent(R.id.widget_root, historyPendingIntent(context, selectedGroup))"));
        assertTrue(provider.contains("Intent(Intent.ACTION_VIEW, historyUri(selectedGroup))"));
        assertTrue(provider.contains("PendingIntent.getActivity("));
        assertTrue(provider.contains("PendingIntent.FLAG_IMMUTABLE"));
        assertTrue(provider.contains("AppWidgetManager.getInstance(appContext)"));
        assertTrue(provider.contains("ComponentName(appContext, BarkRecentMessagesWidgetProvider::class.java)"));
        assertTrue(provider.contains("override fun onDeleted(context: Context, appWidgetIds: IntArray)"));
        assertTrue(provider.contains("BarkWidgetGroupPreferences.delete(context, appWidgetIds)"));
    }

    @Test
    public void messageStoreRefreshesWidgetAfterHistoryMutations() throws Exception {
        String store = readFile("src/main/java/day/bark/android/BarkMessageStore.kt");

        assertTrue(store.contains("private val appContext = context.applicationContext"));
        assertTrue(store.contains("private fun notifyHistoryChanged()"));
        assertTrue(store.contains("BarkRecentMessagesWidgetProvider.updateAll(appContext)"));
        assertTrue(store.contains("notifyHistoryChanged()"));
    }

    @Test
    public void messageStoreSupportsRecentMessagesByNamedGroupForWidgetFiltering() throws Exception {
        String store = readFile("src/main/java/day/bark/android/BarkMessageStore.kt");

        assertTrue(store.contains("fun recent(limit: Int = 100, searchText: String? = null, group: String? = null)"));
        assertTrue(store.contains("group_name = ?"));
        assertTrue(store.contains("selectionParts += \"group_name = ?\""));
    }

    @Test
    public void widgetConfigureActivityPersistsPerWidgetGroupSelection() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/BarkWidgetConfigureActivity.kt");
        String prefs = readFile("src/main/java/day/bark/android/BarkWidgetGroupPreferences.kt");

        assertTrue(activity.contains("class BarkWidgetConfigureActivity : Activity()"));
        assertTrue(activity.contains("AppWidgetManager.EXTRA_APPWIDGET_ID"));
        assertTrue(activity.contains("BarkHistoryView.groups(BarkMessageStore(this).recent(limit = Int.MAX_VALUE))"));
        assertTrue(activity.contains("button(\"All\") { selectGroup(null) }"));
        assertTrue(activity.contains("BarkWidgetGroupPreferences.saveSelectedGroup(this, appWidgetId, group)"));
        assertTrue(activity.contains("BarkRecentMessagesWidgetProvider.updateOne(this, appWidgetId)"));
        assertTrue(activity.contains("setResult(RESULT_OK, result)"));
        assertTrue(prefs.contains("object BarkWidgetGroupPreferences"));
        assertTrue(prefs.contains("fun selectedGroup(context: Context, appWidgetId: Int): String?"));
        assertTrue(prefs.contains("fun saveSelectedGroup(context: Context, appWidgetId: Int, group: String?)"));
        assertTrue(prefs.contains("fun delete(context: Context, appWidgetIds: IntArray)"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}

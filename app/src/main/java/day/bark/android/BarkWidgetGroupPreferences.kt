package day.bark.android

import android.content.Context

object BarkWidgetGroupPreferences {
    private const val PREFS_NAME = "bark_widget_groups"
    private const val KEY_PREFIX = "group_"

    fun selectedGroup(context: Context, appWidgetId: Int): String? =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(appWidgetId), null)
            ?.takeIf { it.isNotBlank() }

    fun saveSelectedGroup(context: Context, appWidgetId: Int, group: String?) {
        val editor = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
        val normalizedGroup = BarkHistoryView.normalizeGroup(group)
        if (normalizedGroup == null) {
            editor.remove(key(appWidgetId))
        } else {
            editor.putString(key(appWidgetId), normalizedGroup)
        }
        editor.apply()
    }

    fun delete(context: Context, appWidgetIds: IntArray) {
        val editor = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
        appWidgetIds.forEach { editor.remove(key(it)) }
        editor.apply()
    }

    private fun key(appWidgetId: Int): String = "$KEY_PREFIX$appWidgetId"
}

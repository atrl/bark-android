package day.bark.android.projects.widget

import android.content.Context

/** Each desktop widget keeps its own stable project ID, independent of list order. */
object BarkProjectWidgetPreferences {
    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences("bark_project_widgets", Context.MODE_PRIVATE)

    fun selectedProject(context: Context, widgetId: Int): String? =
        preferences(context).getString("project_$widgetId", null)

    fun save(context: Context, widgetId: Int, projectId: String) {
        preferences(context).edit().putString("project_$widgetId", projectId).apply()
    }

    fun delete(context: Context, widgetIds: IntArray) {
        preferences(context).edit().apply {
            widgetIds.forEach { remove("project_$it") }
        }.apply()
    }
}

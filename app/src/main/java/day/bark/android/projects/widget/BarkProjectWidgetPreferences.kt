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

    fun family(context: Context, widgetId: Int): String =
        preferences(context).getString("family_$widgetId", "IC")?.takeIf { it in setOf("IC", "IM", "IF") } ?: "IC"

    fun tenor(context: Context, widgetId: Int): String =
        preferences(context).getString("tenor_$widgetId", "next")?.takeIf { it in setOf("front", "next", "quarter", "far") } ?: "next"

    fun saveSelection(context: Context, widgetId: Int, projectId: String, family: String, tenor: String) {
        require(family in setOf("IC", "IM", "IF") && tenor in setOf("front", "next", "quarter", "far"))
        preferences(context).edit().putString("project_$widgetId", projectId)
            .putString("family_$widgetId", family).putString("tenor_$widgetId", tenor).apply()
    }

    fun delete(context: Context, widgetIds: IntArray) {
        preferences(context).edit().apply {
            widgetIds.forEach { remove("project_$it"); remove("family_$it"); remove("tenor_$it") }
        }.apply()
    }
}

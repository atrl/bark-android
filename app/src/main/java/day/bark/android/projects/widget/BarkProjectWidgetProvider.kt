package day.bark.android.projects.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import day.bark.android.R
import day.bark.android.projects.BarkProjectNavigator
import day.bark.android.projects.BarkProjectStore

/** A native desktop entry point; the full project runs in the app's WebView. */
class BarkProjectWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateOne(context, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        BarkProjectWidgetPreferences.delete(context, ids)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        // Snapshot every old selection first: a new ID may also be another old ID.
        val selections = oldWidgetIds.map { BarkProjectWidgetPreferences.selectedProject(context, it) }
        BarkProjectWidgetPreferences.delete(context, newWidgetIds)
        newWidgetIds.forEachIndexed { index, newId ->
            selections.getOrNull(index)?.let { projectId ->
                BarkProjectWidgetPreferences.save(context, newId, projectId)
            }
        }
        BarkProjectWidgetPreferences.delete(context, oldWidgetIds.filterNot { it in newWidgetIds }.toIntArray())
        newWidgetIds.forEach { updateOne(context, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, BarkProjectWidgetProvider::class.java))
                .forEach { updateOne(context, it) }
        }

        fun updateOne(context: Context, widgetId: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val projectId = BarkProjectWidgetPreferences.selectedProject(context, widgetId)
            val project = projectId?.let { BarkProjectStore(context).find(it) }
            val configureIntent = Intent(context, BarkProjectWidgetConfigureActivity::class.java)
                .setAction("day.bark.android.CONFIGURE_PROJECT_WIDGET")
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            val configure = PendingIntent.getActivity(
                context, widgetId, configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val open = if (project == null) configure else PendingIntent.getActivity(
                context, widgetId,
                BarkProjectNavigator.intent(context, project.id).setAction("day.bark.android.OPEN_PROJECT_WIDGET"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val height = manager.getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val views = RemoteViews(context.packageName, R.layout.bark_project_widget).apply {
                setTextViewText(R.id.project_widget_title, project?.name ?: context.getString(R.string.project_widget_missing))
                setTextViewText(R.id.project_widget_host, project?.url?.let { Uri.parse(it).host } ?: "Bark")
                setTextViewText(R.id.project_widget_detail, when {
                    project == null -> context.getString(R.string.project_widget_missing_detail)
                    project.description.isNotBlank() -> project.description
                    else -> context.getString(R.string.project_widget_description)
                })
                setTextViewText(R.id.project_widget_open, context.getString(
                    if (project == null) R.string.project_widget_choose else R.string.project_widget_open,
                ))
                setViewVisibility(R.id.project_widget_detail, if (height >= 150) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.project_widget_host, if (height >= 110) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.project_widget_open, if (height >= 110) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(R.id.project_widget_root, open)
                setOnClickPendingIntent(R.id.project_widget_configure, configure)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}

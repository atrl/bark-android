package day.bark.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import java.text.DateFormat
import java.util.Date

class BarkRecentMessagesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        BarkWidgetGroupPreferences.delete(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, BarkRecentMessagesWidgetProvider::class.java),
            )
            if (ids.isNotEmpty()) {
                updateWidgets(appContext, manager, ids)
            }
        }

        fun updateOne(context: Context, appWidgetId: Int) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            updateWidgets(appContext, manager, intArrayOf(appWidgetId))
        }

        private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { appWidgetId ->
                val selectedGroup = BarkWidgetGroupPreferences.selectedGroup(context, appWidgetId)
                val views = RemoteViews(context.packageName, R.layout.bark_recent_messages_widget)
                views.setTextViewText(R.id.widget_title, widgetTitle(context, selectedGroup))
                views.setTextViewText(R.id.widget_messages, widgetText(context, selectedGroup))
                views.setOnClickPendingIntent(R.id.widget_root, historyPendingIntent(context, selectedGroup))
                manager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun historyPendingIntent(context: Context, selectedGroup: String?): PendingIntent =
            PendingIntent.getActivity(
                context,
                selectedGroup?.hashCode() ?: 0,
                Intent(Intent.ACTION_VIEW, historyUri(selectedGroup))
                    .setClass(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun historyUri(selectedGroup: String?): Uri =
            Uri.Builder()
                .scheme("bark")
                .authority("history")
                .apply {
                    selectedGroup?.let { appendQueryParameter("group", it) }
                }
                .build()

        private fun widgetTitle(context: Context, selectedGroup: String?): String =
            selectedGroup ?: context.getString(R.string.app_name)

        private fun widgetText(context: Context, selectedGroup: String?): String {
            val messages = BarkMessageStore(context).recent(limit = 3, group = selectedGroup)
            if (messages.isEmpty()) {
                return context.getString(R.string.widget_empty)
            }
            val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            return messages.joinToString("\n\n") { message ->
                BarkHistoryMessageText.format(
                    message,
                    dateFormat.format(Date(message.createAtMillis)),
                    BarkExpiryText.format(message.expireAtMillis),
                )
            }
        }
    }
}

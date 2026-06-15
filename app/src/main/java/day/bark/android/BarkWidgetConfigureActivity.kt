package day.bark.android

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class BarkWidgetConfigureActivity : Activity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(contentView())
    }

    private fun contentView(): ScrollView {
        val groups = BarkHistoryView.groups(BarkMessageStore(this).recent(limit = Int.MAX_VALUE))
        return ScrollView(this).apply {
            addView(
                LinearLayout(this@BarkWidgetConfigureActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 32, 32, 32)
                    addView(TextView(this@BarkWidgetConfigureActivity).apply {
                        text = getString(R.string.widget_group_title)
                        textSize = 20f
                    })
                    addView(button("All") { selectGroup(null) })
                    groups.filter { !it.group.isNullOrBlank() }
                        .forEach { group ->
                            addView(button(group.displayName) { selectGroup(group.group) })
                        }
                },
            )
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

    private fun selectGroup(group: String?) {
        BarkWidgetGroupPreferences.saveSelectedGroup(this, appWidgetId, group)
        BarkRecentMessagesWidgetProvider.updateOne(this, appWidgetId)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }
}

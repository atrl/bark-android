package day.bark.android.projects.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import day.bark.android.MainActivity
import day.bark.android.R
import day.bark.android.projects.BarkProjectStore

class BarkProjectWidgetConfigureActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            info?.provider != ComponentName(this, BarkProjectWidgetProvider::class.java)) {
            finish()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        val projects = BarkProjectStore(this).list()
        val spacing = (20 * resources.displayMetrics.density).toInt()
        val contents = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(spacing, spacing, spacing, spacing)
            addView(TextView(this@BarkProjectWidgetConfigureActivity).apply {
                text = getString(R.string.project_widget_choose)
                textSize = 24f
            })
            addView(TextView(this@BarkProjectWidgetConfigureActivity).apply {
                text = "每个组件可选择一个项目，轻点卡片打开完整页面。"
                setPadding(0, spacing / 2, 0, spacing)
            })
            projects.forEach { project ->
                addView(Button(this@BarkProjectWidgetConfigureActivity).apply {
                    text = project.name
                    setOnClickListener { selectProject(project.id) }
                })
            }
            if (projects.isEmpty()) {
                addView(Button(this@BarkProjectWidgetConfigureActivity).apply {
                    text = "先添加项目"
                    setOnClickListener {
                        startActivity(Intent(this@BarkProjectWidgetConfigureActivity, MainActivity::class.java))
                    }
                })
            }
        }
        val scroll = ScrollView(this).apply { addView(contents) }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    private fun selectProject(projectId: String) {
        if (BarkProjectStore(this).find(projectId) == null) return
        BarkProjectWidgetPreferences.save(this, widgetId, projectId)
        BarkProjectWidgetProvider.updateOne(this, widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

package day.bark.android.projects.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import day.bark.android.R
import day.bark.android.projects.BarkProject
import day.bark.android.projects.BarkProjectNavigator
import day.bark.android.projects.BarkProjectStore
import day.bark.android.projects.data.BasisCacheState
import day.bark.android.projects.data.BasisRefreshScheduler
import day.bark.android.projects.data.BasisRepository
import day.bark.android.projects.ui.BasisPresentation
import day.bark.android.projects.ui.BasisSparklineRenderer
import kotlin.math.sqrt

/** Desktop rendering only reads the shared cache; workers own all network I/O. */
class BarkProjectWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, it) }
        schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateOne(context, id)
        schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)
            if (info?.provider != ComponentName(context, BarkProjectWidgetProvider::class.java)) return
            val project = BarkProjectWidgetPreferences.selectedProject(context, id)?.let { BarkProjectStore(context).find(it) }
            if (project != null && BasisRepository.supports(project)) {
                BasisRefreshScheduler.request(context, setOf(BarkProjectWidgetPreferences.family(context, id)), force = true)
                updateOne(context, id)
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        BarkProjectWidgetPreferences.delete(context, ids)
        BasisRefreshScheduler.configure(context, configuredFamilies(context))
    }

    override fun onDisabled(context: Context) {
        BasisRefreshScheduler.configure(context, emptySet())
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val selections = oldWidgetIds.map { id -> Triple(BarkProjectWidgetPreferences.selectedProject(context, id),
            BarkProjectWidgetPreferences.family(context, id), BarkProjectWidgetPreferences.tenor(context, id)) }
        BarkProjectWidgetPreferences.delete(context, newWidgetIds)
        newWidgetIds.forEachIndexed { index, newId ->
            selections.getOrNull(index)?.let { (project, family, tenor) ->
                if (project != null) BarkProjectWidgetPreferences.saveSelection(context, newId, project, family, tenor)
            }
        }
        BarkProjectWidgetPreferences.delete(context, oldWidgetIds.filterNot { it in newWidgetIds }.toIntArray())
        newWidgetIds.forEach { updateOne(context, it) }
        schedule(context)
    }

    companion object {
        private const val ACTION_REFRESH = "day.bark.android.REFRESH_PROJECT_WIDGET"

        private fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, BarkProjectWidgetProvider::class.java))

        fun configuredFamilies(context: Context): Set<String> = ids(context).toList().mapNotNull { id ->
            val project = BarkProjectWidgetPreferences.selectedProject(context, id)?.let { BarkProjectStore(context).find(it) }
            if (project != null && BasisRepository.supports(project)) BarkProjectWidgetPreferences.family(context, id) else null
        }.toSet()

        fun schedule(context: Context) {
            val families = configuredFamilies(context)
            BasisRefreshScheduler.configure(context, families)
            if (families.isNotEmpty()) BasisRefreshScheduler.request(context, families)
        }

        // Repository/worker updates must never recursively enqueue another refresh.
        fun updateAll(context: Context) { ids(context).forEach { updateOne(context, it) } }

        fun updateOne(context: Context, widgetId: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val projectId = BarkProjectWidgetPreferences.selectedProject(context, widgetId)
            val project = projectId?.let { BarkProjectStore(context).find(it) }
            val configureIntent = Intent(context, BarkProjectWidgetConfigureActivity::class.java)
                .setAction("day.bark.android.CONFIGURE_PROJECT_WIDGET")
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            val configure = PendingIntent.getActivity(context, widgetId, configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val open = if (project == null) configure else PendingIntent.getActivity(context, widgetId,
                BarkProjectNavigator.intent(context, project.id).setAction("day.bark.android.OPEN_PROJECT_WIDGET"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val native = project != null && BasisRepository.supports(project)
            val family = BarkProjectWidgetPreferences.family(context, widgetId)
            val tenor = BarkProjectWidgetPreferences.tenor(context, widgetId)
            val state = if (native) BasisRepository.cached(context, family) else null
            val refresh = PendingIntent.getBroadcast(context, widgetId,
                Intent(context, BarkProjectWidgetProvider::class.java).setAction(ACTION_REFRESH)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val data = WidgetData(project, family, tenor, state, open, configure, refresh)
            val options = manager.getAppWidgetOptions(widgetId)
            val display = context.resources.displayMetrics
            val totalBitmapPixels = (display.widthPixels.toLong() * display.heightPixels).coerceAtLeast(1)
            val views = if (Build.VERSION.SDK_INT >= 31) {
                // Let the host choose using its actual rendered size, including after rotation.
                @Suppress("DEPRECATION")
                val hostSizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    .orEmpty().filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
                    .distinct().take(8)
                val sizes = hostSizes.ifEmpty {
                    listOf(
                        SizeF(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220).coerceAtLeast(1).toFloat(),
                            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 165).coerceAtLeast(1).toFloat()),
                        SizeF(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320).coerceAtLeast(1).toFloat(),
                            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120).coerceAtLeast(1).toFloat()),
                    ).distinct()
                }
                RemoteViews(sizes.associateWith { size ->
                    renderViews(context, data, size.width.toInt(), size.height.toInt(), totalBitmapPixels / sizes.size)
                })
            } else {
                val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val width = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                    else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)
                val height = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                    else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 165)
                renderViews(context, data, width.coerceAtLeast(140), height.coerceAtLeast(120), totalBitmapPixels)
            }
            manager.updateAppWidget(widgetId, views)
        }

        private data class WidgetData(
            val project: BarkProject?,
            val family: String,
            val tenor: String,
            val state: BasisCacheState?,
            val open: PendingIntent,
            val configure: PendingIntent,
            val refresh: PendingIntent,
        )

        private fun renderViews(context: Context, data: WidgetData, width: Int, height: Int, bitmapPixelBudget: Long): RemoteViews {
            val project = data.project
            val state = data.state
            val native = state != null
            val compact = height < 165 || width < 220
            val views = RemoteViews(context.packageName, R.layout.bark_project_widget).apply {
                val padding = ((if (compact) 10 else 14) * context.resources.displayMetrics.density).toInt()
                setViewPadding(R.id.project_widget_root, padding, padding, padding, padding)
                setTextViewText(R.id.project_widget_title, project?.name ?: context.getString(R.string.project_widget_missing))
                setTextViewText(R.id.project_widget_host, project?.url?.let { Uri.parse(it).host } ?: "Bark")
                setTextViewText(R.id.project_widget_detail, when {
                    project == null -> context.getString(R.string.project_widget_missing_detail)
                    project.description.isNotBlank() -> project.description
                    else -> context.getString(R.string.project_widget_description)
                })
                setTextViewText(R.id.project_widget_open, context.getString(if (project == null) R.string.project_widget_choose else R.string.project_widget_open))
                setViewVisibility(R.id.project_widget_host, if (!native && height >= 110) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.project_widget_detail, if (!native && height >= 145) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.project_widget_open, if (!native) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.project_widget_data, if (native) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.project_widget_refresh, if (native) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(R.id.project_widget_root, data.open)
                setOnClickPendingIntent(R.id.project_widget_configure, data.configure)
            }
            if (state != null) {
                val contract = BasisPresentation.selected(state, data.tenor)
                val points = contract?.points.orEmpty().takeLast(24)
                views.apply {
                    setTextViewText(R.id.project_widget_title, "${data.family} · ${BasisPresentation.tenors.getValue(data.tenor)}")
                    setTextViewText(R.id.project_widget_contract, contract?.code ?: "该期限等待数据")
                    setTextViewText(R.id.project_widget_value, BasisPresentation.annualized(contract) +
                        if (contract?.annualizedDiscount?.isFinite() == true) "%" else "")
                    setTextViewText(R.id.project_widget_percentile, "分位 ${BasisPresentation.percentile(contract)}")
                    val status = BasisPresentation.stateLabel(state, contract)
                    val timestamp = BasisPresentation.timestamp(state, contract)
                    setTextViewText(R.id.project_widget_asof, if (compact) "$status\n$timestamp" else "$status · $timestamp")
                    setInt(R.id.project_widget_asof, "setMaxLines", if (compact) 2 else 1)
                    setTextViewTextSize(R.id.project_widget_title, TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 14f)
                    setTextViewTextSize(R.id.project_widget_value, TypedValue.COMPLEX_UNIT_SP, if (compact) 19f else 27f)
                    setTextViewTextSize(R.id.project_widget_percentile, TypedValue.COMPLEX_UNIT_SP, if (compact) 10f else 13f)
                    setTextViewTextSize(R.id.project_widget_asof, TypedValue.COMPLEX_UNIT_SP, if (compact) 8f else 9f)
                    val pointCount = points.count { it.value?.isFinite() == true }
                    setTextViewText(R.id.project_widget_chart_label, if (pointCount > 0) "近${pointCount}个收盘 · 同一合约" else "收盘走势待更新")
                    setContentDescription(R.id.project_widget_chart, "同一合约近${pointCount}个收盘的年化贴水走势，数据缺口处断开")
                    setViewVisibility(R.id.project_widget_chart_container, if (!compact) View.VISIBLE else View.GONE)
                    setViewVisibility(R.id.project_widget_contract, if (!compact) View.VISIBLE else View.GONE)
                    if (!compact) {
                        val density = context.resources.displayMetrics.density
                        // Render near the actual chart's physical pixel size, not a tiny
                        // fixed-height image that the launcher would stretch on large widgets.
                        val chartWidth = ((width - 28).coerceAtLeast(100) * density).toInt().coerceIn(280, 960)
                        val chartHeight = ((height - 140).coerceAtLeast(24) * density).toInt().coerceIn(100, 700)
                        // All responsive variants count toward Android's RemoteViews
                        // bitmap allowance. Keep their combined pixels within one screen.
                        val scale = sqrt(bitmapPixelBudget.coerceAtLeast(1).toDouble() / (chartWidth * chartHeight)).coerceAtMost(1.0)
                        setImageViewBitmap(R.id.project_widget_chart, BasisSparklineRenderer.bitmap(points,
                            (chartWidth * scale).toInt().coerceAtLeast(1), (chartHeight * scale).toInt().coerceAtLeast(1)))
                    }
                    setOnClickPendingIntent(R.id.project_widget_refresh, data.refresh)
                }
            }
            return views
        }
    }
}

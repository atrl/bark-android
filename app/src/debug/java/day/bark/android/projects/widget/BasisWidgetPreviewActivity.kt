package day.bark.android.projects.widget

import android.app.Activity
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import day.bark.android.projects.data.BasisCacheState
import day.bark.android.projects.data.BasisSnapshot
import java.io.File

/** Debug-only preview of the actual RemoteViews using an explicitly supplied public feed fixture. */
class BasisWidgetPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val width = intent.getIntExtra("width", 220).coerceIn(140, 600)
        val height = intent.getIntExtra("height", 160).coerceIn(120, 600)
        val density = resources.displayMetrics.density
        val state = BasisCacheState(snapshot = BasisSnapshot.parse(File(filesDir, "widget-preview.json").readText(), "IC"))
        val click = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val data = BarkProjectWidgetProvider.Companion.WidgetData(null, "IC", "next", state, click, click, click)
        val remote = BarkProjectWidgetProvider.renderViews(this, data, width, height, 1_000_000)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 80, 24, 24)
            setBackgroundColor(0xFFF1F2F4.toInt())
            addView(TextView(this@BasisWidgetPreviewActivity).apply {
                text = "RemoteViews QA · ${width}×${height}dp · public feed fixture"
                setPadding(0, 0, 0, 16)
            })
        }
        val widget = remote.apply(this, root)
        root.addView(widget, LinearLayout.LayoutParams((width * density).toInt(), (height * density).toInt()))
        setContentView(root)
        widget.post {
            val bitmap = Bitmap.createBitmap(widget.width, widget.height, Bitmap.Config.ARGB_8888)
            widget.draw(Canvas(bitmap))
            File(filesDir, "widget-preview-${width}x${height}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}

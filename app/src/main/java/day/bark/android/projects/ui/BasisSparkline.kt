package day.bark.android.projects.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import day.bark.android.projects.data.BasisPoint
import kotlin.math.abs
import kotlin.math.max

/** The app and desktop widget draw the same finite daily closes and gaps. */
object BasisSparklineRenderer {
    fun bitmap(points: List<BasisPoint>, width: Int = 640, height: Int = 144): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            draw(Canvas(it), width.toFloat(), height.toFloat(), points)
        }

    fun draw(canvas: Canvas, width: Float, height: Float, source: List<BasisPoint>) {
        val points = source.takeLast(24)
        val values = points.mapNotNull { it.value?.takeIf(Double::isFinite) }
        if (values.isEmpty() || width <= 0 || height <= 0) return
        val low = values.min()
        val high = values.max()
        val span = max(high - low, max(abs(high) * 0.04, 0.002))
        val middle = (high + low) / 2
        val bottom = height * 0.88f
        val top = height * 0.12f
        val left = width * 0.012f
        val right = width * 0.988f
        fun x(index: Int) = if (points.size == 1) width / 2 else left + (right - left) * index / (points.size - 1)
        fun y(value: Double) = (bottom - ((value - (middle - span / 2)) / span) * (bottom - top)).toFloat()
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE4ECE7.toInt(); strokeWidth = 1.2f }
        canvas.drawLine(0f, height * .5f, width, height * .5f, grid)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF138876.toInt(); strokeWidth = max(width / 230, 2f)
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x16138876 }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF138876.toInt() }
        val segment = mutableListOf<Pair<Float, Float>>()
        fun finishSegment() {
            if (segment.isEmpty()) return
            val stroke = Path().apply {
                moveTo(segment.first().first, segment.first().second)
                segment.drop(1).forEach { lineTo(it.first, it.second) }
            }
            if (segment.size > 1) {
                val area = Path(stroke).apply {
                    lineTo(segment.last().first, bottom); lineTo(segment.first().first, bottom); close()
                }
                canvas.drawPath(area, fill)
                canvas.drawPath(stroke, line)
            } else canvas.drawCircle(segment[0].first, segment[0].second, line.strokeWidth, dot)
            segment.clear()
        }
        points.forEachIndexed { index, point ->
            val value = point.value?.takeIf(Double::isFinite)
            if (point.gapBefore || value == null) finishSegment()
            if (value != null) segment.add(x(index) to y(value))
        }
        finishSegment()
        points.lastOrNull()?.value?.takeIf(Double::isFinite)?.let { value ->
            canvas.drawCircle(x(points.lastIndex), y(value), line.strokeWidth * 1.4f, dot)
        }
    }
}

@Composable
fun BasisSparkline(points: List<BasisPoint>, modifier: Modifier = Modifier) {
    val count = points.takeLast(24).count { it.value?.isFinite() == true }
    Canvas(modifier.semantics { contentDescription = "同一合约最近${count}个收盘的年化贴水走势，数据缺口处断开" }) {
        drawIntoCanvas { BasisSparklineRenderer.draw(it.nativeCanvas, size.width, size.height, points) }
    }
}

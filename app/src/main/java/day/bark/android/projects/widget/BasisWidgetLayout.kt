package day.bark.android.projects.widget

import day.bark.android.projects.data.BasisCacheState
import day.bark.android.projects.data.BasisContract
import day.bark.android.projects.ui.BasisPresentation
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** Layout choices are based on the host's available dp, not launcher cell counts. */
data class BasisWidgetLayout(val tenors: List<String>, val detailed: Boolean) {
    val rows: Int get() = if (tenors.size > 2) 2 else 1

    companion object {
        fun forSize(width: Int, height: Int, preferredTenor: String): BasisWidgetLayout {
            val all = BasisPresentation.tenors.keys.toList()
            val preferred = preferredTenor.takeIf { it in all } ?: "next"
            val tenors = if (width >= 220 && height >= 160) all
                else all.filter { it == "front" || it == if (preferred == "front") "next" else preferred }
            return BasisWidgetLayout(tenors, width >= 260 && height >= 220)
        }

        fun status(state: BasisCacheState, contracts: List<BasisContract?>, nowMillis: Long): String {
            val labels = contracts.map { BasisPresentation.stateLabel(state, it, nowMillis) }.distinct()
            return when {
                labels.size == 1 -> labels.first()
                labels.any { it == "待更新" || it == "等待数据" || it == "行情延迟" } -> "部分待更新"
                else -> "混合快照"
            }
        }

        fun timestamp(state: BasisCacheState, contracts: List<BasisContract?>): String {
            val times = contracts.filterNotNull().map { BasisPresentation.timestamp(state, it) }.distinct()
            val oldest = times.minByOrNull { time ->
                runCatching { OffsetDateTime.parse(time.replace(' ', 'T') +
                    if (time.length == 19) "+08:00" else "").toInstant().toEpochMilli() }
                    .recoverCatching { LocalDate.parse(time).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() }
                    .getOrDefault(Long.MIN_VALUE)
            } ?: return BasisPresentation.widgetTimestamp(state, null)
            return "${if (times.size > 1) "最早截至" else "截至"} $oldest"
        }
    }
}

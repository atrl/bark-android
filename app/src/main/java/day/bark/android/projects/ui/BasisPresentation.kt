package day.bark.android.projects.ui

import day.bark.android.projects.data.BasisCacheState
import day.bark.android.projects.data.BasisContract
import day.bark.android.projects.data.BasisRefreshFailure
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

object BasisPresentation {
    val families = linkedMapOf("IC" to "中证500", "IM" to "中证1000", "IF" to "沪深300")
    val tenors = linkedMapOf("front" to "近月", "next" to "次月", "quarter" to "季月", "far" to "远季")

    fun selected(state: BasisCacheState, tenor: String): BasisContract? =
        state.snapshot?.contracts?.firstOrNull { it.tenor == tenor }

    fun annualized(contract: BasisContract?): String = contract?.annualizedDiscount
        ?.takeIf(Double::isFinite)?.let { String.format(Locale.ROOT, "%.2f", it * 100) } ?: "—"

    fun percentile(contract: BasisContract?): String = contract?.percentile
        ?.takeIf { contract.percentileStatus == "ok" && it.isFinite() && it in 0.0..1.0 }
        ?.let { "P${String.format(Locale.ROOT, "%.0f", it * 100)}" } ?: "—"

    fun timestamp(state: BasisCacheState, contract: BasisContract?): String {
        val value = contract?.quoteTime?.takeIf(String::isNotBlank)
            ?: contract?.dataDate?.takeIf(String::isNotBlank)
            ?: state.snapshot?.asOf?.takeIf(String::isNotBlank)
            ?: return "等待首次数据"
        // Keep the absolute source date; fetching a cache never changes quote time.
        return value.replace('T', ' ').removeSuffix("+08:00")
    }

    fun stateLabel(state: BasisCacheState, contract: BasisContract?, nowMillis: Long = System.currentTimeMillis()): String = when {
        state.refreshing -> "更新中"
        state.error != null && state.failure == BasisRefreshFailure.CONNECTION ->
            if (state.snapshot == null) "连接失败" else "离线缓存"
        state.error != null -> if (state.snapshot == null) "更新失败" else "缓存 · 更新失败"
        contract == null -> "等待数据"
        contract.liveQuoteExpired(nowMillis) -> "待更新"
        contract.kind == "close" && closeExpired(contract.dataDate, nowMillis) -> "待更新"
        contract.freshness == "stale" || state.snapshot?.freshness == "stale" -> "待更新"
        contract.kind == "close" -> "收盘"
        contract.kind == "live" -> "采样快照"
        else -> "已保存"
    }

    fun widgetTimestamp(state: BasisCacheState, contract: BasisContract?): String {
        val time = timestamp(state, contract)
        return if (time == "等待首次数据") time else "截至 $time"
    }

    private fun closeExpired(date: String, nowMillis: Long): Boolean = runCatching {
        val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
        ChronoUnit.DAYS.between(LocalDate.parse(date), today) > 7
    }.getOrDefault(true)
}

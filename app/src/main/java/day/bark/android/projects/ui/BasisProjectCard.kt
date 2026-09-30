package day.bark.android.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import day.bark.android.R
import day.bark.android.projects.BarkProject
import day.bark.android.projects.BarkProjectNavigator
import day.bark.android.projects.data.BasisRefreshScheduler
import day.bark.android.projects.data.rememberBasisState
import day.bark.android.ui.BarkPalette
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@Composable
fun BasisProjectCard(project: BarkProject, actions: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(30_000)
            }
        }
    }
    var family by rememberSaveable(project.id) { mutableStateOf("IC") }
    var tenor by rememberSaveable(project.id) { mutableStateOf("next") }
    var tenorMenu by remember { mutableStateOf(false) }
    val state = rememberBasisState(family)
    val contract = BasisPresentation.selected(state, tenor)
    val points = contract?.points.orEmpty().takeLast(24)
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp))
            .border(1.dp, BarkPalette.Border, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProjectCardHeading(project.name, R.drawable.bark_ic_chart, actions)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasisPresentation.families.keys.forEach { item ->
                    Text(item, Modifier.background(
                        if (family == item) BarkPalette.SoftTeal else Color.Transparent, RoundedCornerShape(10.dp),
                    ).clickable { family = item }.padding(horizontal = 13.dp, vertical = 8.dp),
                        color = if (family == item) BarkPalette.Teal else BarkPalette.Muted,
                        fontWeight = if (family == item) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                }
            }
            Box {
                Row(Modifier.clickable { tenorMenu = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(BasisPresentation.tenors.getValue(tenor), fontSize = 13.sp, color = BarkPalette.Muted)
                    Icon(painterResource(R.drawable.bark_ic_chevron), null, Modifier.size(14.dp), tint = BarkPalette.Muted)
                }
                DropdownMenu(expanded = tenorMenu, onDismissRequest = { tenorMenu = false }) {
                    BasisPresentation.tenors.forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { tenor = key; tenorMenu = false })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("年化贴水", color = BarkPalette.Muted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(BasisPresentation.annualized(contract), fontSize = 38.sp, fontWeight = FontWeight.SemiBold,
                        color = BarkPalette.Ink, letterSpacing = (-1.3).sp)
                    if (contract?.annualizedDiscount?.isFinite() == true) {
                        Text("%", Modifier.padding(start = 3.dp, bottom = 6.dp), fontSize = 19.sp, color = BarkPalette.Muted)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("历史分位", color = BarkPalette.Muted, fontSize = 12.sp)
                Text(BasisPresentation.percentile(contract), fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = BarkPalette.Teal)
                Text(if (contract?.percentileStatus == "ok") "${contract.sampleCount} 个匹配样本" else "等待足够样本",
                    fontSize = 10.sp, color = BarkPalette.Muted)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(contract?.code ?: "$family · ${BasisPresentation.tenors.getValue(tenor)}待更新",
                fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(contract?.dte?.let { "剩余 $it 天${if (contract.nearExpiry) " · 临近到期" else ""}" } ?: "合约数据待更新",
                fontSize = 11.sp, color = BarkPalette.Muted)
        }
        if (points.any { it.value?.isFinite() == true }) {
            Column {
                BasisSparkline(points, Modifier.fillMaxWidth().height(80.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(points.firstOrNull()?.date.orEmpty(), fontSize = 10.sp, color = BarkPalette.Muted)
                    Text("近${points.count { it.value?.isFinite() == true }}个收盘", fontSize = 10.sp, color = BarkPalette.Muted)
                    Text(points.lastOrNull()?.date.orEmpty(), fontSize = 10.sp, color = BarkPalette.Muted)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(76.dp).background(BarkPalette.Background, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Text("收盘走势待更新", fontSize = 12.sp, color = BarkPalette.Muted)
            }
        }
        if (state.error != null || contract?.status != "ok") {
            val explanation = when {
                state.error != null && state.snapshot != null -> "暂未获取新数据，展示上次保存的行情"
                state.error != null -> "暂时无法获取行情，请稍后刷新"
                contract == null -> "该期限尚无可用数据，可刷新或查看完整页面"
                contract.reason.isNotBlank() -> contract.reason
                else -> "部分数据待更新"
            }
            Text(explanation, fontSize = 11.sp, color = BarkPalette.Muted)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${BasisPresentation.stateLabel(state, contract, nowMillis)} · ${BasisPresentation.timestamp(state, contract)}",
                    fontSize = 10.sp, color = BarkPalette.Muted)
                Text("全额名义本金口径 · ACT/365", fontSize = 10.sp, color = BarkPalette.Muted)
            }
            IconButton(onClick = { BasisRefreshScheduler.request(context, setOf(family), force = true) }, enabled = !state.refreshing) {
                Icon(painterResource(R.drawable.bark_ic_refresh), "刷新行情", Modifier.size(19.dp), tint = BarkPalette.Teal)
            }
            IconButton(onClick = { BarkProjectNavigator.open(context, project.id) }) {
                Icon(painterResource(R.drawable.bark_ic_arrow), "打开完整项目", Modifier.size(21.dp), tint = BarkPalette.Teal)
            }
        }
    }
}

@Composable
fun ProjectCardHeading(name: String, icon: Int, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(BarkPalette.SoftTeal, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(21.dp), tint = BarkPalette.Teal)
        }
        Text(name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        actions()
    }
}

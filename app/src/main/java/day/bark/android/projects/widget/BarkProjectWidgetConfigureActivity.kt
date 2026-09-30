package day.bark.android.projects.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import day.bark.android.MainActivity
import day.bark.android.projects.BarkProjectStore
import day.bark.android.projects.data.BasisRepository
import day.bark.android.projects.ui.BasisPresentation
import day.bark.android.ui.BarkPalette
import day.bark.android.ui.BarkTheme

class BarkProjectWidgetConfigureActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var revision by mutableStateOf(0)

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
        setContent { BarkTheme { ConfigurationScreen() } }
    }

    override fun onResume() {
        super.onResume()
        revision++
    }

    @Composable
    private fun ConfigurationScreen() {
        val projects = remember(revision) { BarkProjectStore(this).list() }
        var projectId by rememberSaveable { mutableStateOf(
            BarkProjectWidgetPreferences.selectedProject(this, widgetId) ?: projects.firstOrNull()?.id) }
        var family by rememberSaveable { mutableStateOf(BarkProjectWidgetPreferences.family(this, widgetId)) }
        var tenor by rememberSaveable { mutableStateOf(BarkProjectWidgetPreferences.tenor(this, widgetId)) }
        LaunchedEffect(projects) {
            if (projects.none { it.id == projectId }) projectId = projects.firstOrNull()?.id
        }
        val selected = projects.firstOrNull { it.id == projectId }
        val native = selected != null && BasisRepository.supports(selected)
        val cached = remember(family) { BasisRepository.cached(this, family) }
        Surface(color = BarkPalette.Background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("桌面看板", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("选择项目，把关注的数据放到桌面。", fontSize = 13.sp, color = BarkPalette.Muted)
                Text("项目", style = MaterialTheme.typography.titleMedium)
                projects.forEach { project ->
                    Choice(project.name, project.description, projectId == project.id) { projectId = project.id }
                }
                if (projects.isEmpty()) {
                    OutlinedButton(onClick = { startActivity(Intent(this@BarkProjectWidgetConfigureActivity, MainActivity::class.java)) }) {
                        Text("先添加项目")
                    }
                }
                if (native) {
                    Text("指数品种", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasisPresentation.families.forEach { (key, name) ->
                            Column(Modifier.weight(1f).background(if (family == key) BarkPalette.SoftTeal else Color.White,
                                RoundedCornerShape(14.dp)).clickable { family = key }.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(key, color = if (family == key) BarkPalette.Teal else BarkPalette.Ink, fontWeight = FontWeight.Bold)
                                Text(name, fontSize = 10.sp, color = BarkPalette.Muted)
                            }
                        }
                    }
                    Text("合约期限", style = MaterialTheme.typography.titleMedium)
                    BasisPresentation.tenors.forEach { (key, label) ->
                        val contract = cached.snapshot?.contracts?.firstOrNull { it.tenor == key }
                        Choice(label, contract?.let { "${it.code} · ${it.expiry} 到期" } ?: "暂无缓存，保存后获取数据",
                            tenor == key) { tenor = key }
                    }
                    Text("始终展示选定期限对应的真实合约；缺少数据时显示等待，不替换成其他期限。", fontSize = 11.sp, color = BarkPalette.Muted)
                }
                Button(onClick = { selected?.let { selectProject(it.id, family, tenor) } }, enabled = selected != null,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("保存到桌面") }
                Text("行情按系统允许的时间更新，也可点组件右上角刷新。以显示的数据时间为准。", fontSize = 11.sp, color = BarkPalette.Muted)
            }
        }
    }

    @Composable
    private fun Choice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) BarkPalette.Teal else BarkPalette.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onClick)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Medium)
                if (detail.isNotBlank()) Text(detail, fontSize = 11.sp, color = BarkPalette.Muted)
            }
        }
    }

    private fun selectProject(projectId: String, family: String, tenor: String) {
        if (BarkProjectStore(this).find(projectId) == null) return
        BarkProjectWidgetPreferences.saveSelection(this, widgetId, projectId, family, tenor)
        BarkProjectWidgetProvider.updateOne(this, widgetId)
        BarkProjectWidgetProvider.schedule(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

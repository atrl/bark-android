package day.bark.android.projects

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import day.bark.android.MainActivity
import day.bark.android.R
import day.bark.android.projects.data.BasisRepository
import day.bark.android.projects.ui.BasisProjectCard
import day.bark.android.projects.ui.ProjectCardHeading
import day.bark.android.ui.BarkPalette

@Composable
fun ProjectsScreen(onProjectsChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember(context) { BarkProjectStore(context) }
    var projects by remember(store) { mutableStateOf(store.list()) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    fun refresh() { projects = store.list(); onProjectsChanged() }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("BARK", fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = BarkPalette.Teal)
                Text("我的项目", fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp)
                Text("${projects.size} 个项目，让关注的信息一目了然", fontSize = 12.sp, color = BarkPalette.Muted)
            }
            IconButton(onClick = { editingId = null; showEditor = true },
                modifier = Modifier.background(BarkPalette.Teal, RoundedCornerShape(16.dp))) {
                Icon(painterResource(R.drawable.bark_ic_add), "添加项目", tint = Color.White)
            }
        }
        if (projects.isEmpty()) {
            Text("添加你的第一个项目", style = MaterialTheme.typography.titleMedium)
            Text("点右上角 +，保存项目名称与 HTTPS 网址。", color = BarkPalette.Muted)
        }
        projects.forEachIndexed { index, project ->
            key(project.id) {
                val actions: @Composable () -> Unit = {
                    ProjectActionsMenu(project,
                        onEdit = { editingId = project.id; showEditor = true },
                        onDelete = { deletingId = project.id },
                        onMoveUp = if (index > 0) { { store.move(project.id, -1); refresh() } } else null,
                        onMoveDown = if (index < projects.lastIndex) { { store.move(project.id, 1); refresh() } } else null,
                    )
                }
                if (BasisRepository.supports(project)) BasisProjectCard(project, actions)
                else Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
                    .border(1.dp, BarkPalette.Border, RoundedCornerShape(22.dp))
                    .clickable { BarkProjectNavigator.open(context, project.id) }.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProjectCardHeading(project.name, R.drawable.bark_ic_web, actions)
                    if (project.description.isNotBlank()) Text(project.description, fontSize = 13.sp, color = BarkPalette.Muted)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(BarkProjectUrl.displayOrigin(project.url), Modifier.weight(1f), fontSize = 11.sp, color = BarkPalette.Muted)
                        Icon(painterResource(R.drawable.bark_ic_arrow), "打开项目", Modifier.size(19.dp), tint = BarkPalette.Teal)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.bark_ic_widget), null, Modifier.size(20.dp), tint = BarkPalette.Muted)
            Text("添加到桌面\n长按桌面 → 小组件 → Bark 项目", fontSize = 11.sp, lineHeight = 18.sp, color = BarkPalette.Muted)
        }
    }
    if (showEditor) key(editingId) {
        ProjectEditor(projects.firstOrNull { it.id == editingId }, { showEditor = false }) { name, url, description, group ->
            store.save(editingId, name, url, description, group); refresh(); showEditor = false
        }
    }
    projects.firstOrNull { it.id == deletingId }?.let { project ->
        AlertDialog(onDismissRequest = { deletingId = null }, title = { Text("删除 ${project.name}？") },
            text = { Text("项目入口将移除，对应桌面组件需要重新选择项目。") },
            confirmButton = { TextButton(onClick = { store.remove(project.id); refresh(); deletingId = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("取消") } })
    }
}

@Composable
private fun ProjectActionsMenu(project: BarkProject, onEdit: () -> Unit, onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.bark_ic_more), "${project.name}的更多操作", Modifier.size(20.dp), tint = BarkPalette.Muted)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("打开完整项目") }, onClick = { expanded = false; BarkProjectNavigator.open(context, project.id) })
            project.group?.let { group ->
                DropdownMenuItem(text = { Text("项目消息") }, onClick = {
                    expanded = false
                    val uri = Uri.Builder().scheme("bark").authority("history").appendQueryParameter("group", group).build()
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).setClass(context, MainActivity::class.java))
                })
            }
            DropdownMenuItem(text = { Text("编辑项目") }, onClick = { expanded = false; onEdit() })
            if (onMoveUp != null) DropdownMenuItem(text = { Text("上移") }, onClick = { expanded = false; onMoveUp() })
            if (onMoveDown != null) DropdownMenuItem(text = { Text("下移") }, onClick = { expanded = false; onMoveDown() })
            DropdownMenuItem(text = { Text("删除项目", color = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; onDelete() })
        }
    }
}

@Composable
private fun ProjectEditor(
    project: BarkProject?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(project?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(project?.url.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(project?.description.orEmpty()) }
    var group by rememberSaveable { mutableStateOf(project?.group.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (project == null) "添加项目" else "编辑项目") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("项目名称") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("HTTPS 网址") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("简介（可选）") }, maxLines = 3)
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("通知分组（可选）") }, singleLine = true)
                Text("通知分组与推送消息中的 group 对应。", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try { onSave(name, url, description, group) }
                catch (exception: IllegalArgumentException) { error = exception.message ?: "请检查项目设置" }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

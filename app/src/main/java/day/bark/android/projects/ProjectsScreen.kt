@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package day.bark.android.projects

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import day.bark.android.MainActivity

@Composable
fun ProjectsScreen(onProjectsChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember(context) { BarkProjectStore(context) }
    var projects by remember(store) { mutableStateOf(store.list()) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    fun refresh() {
        projects = store.list()
        onProjectsChanged()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("我的项目", style = MaterialTheme.typography.headlineSmall)
        Text("把自己的 Web 项目放在这里，在应用内打开，也可从桌面组件进入。")
        Button(onClick = { editingId = null; showEditor = true }) { Text("添加项目") }
        if (projects.isEmpty()) Text("还没有项目。添加一个 HTTPS 网址即可开始。")
        projects.forEachIndexed { index, project ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(project.name, style = MaterialTheme.typography.titleLarge)
                    if (project.description.isNotBlank()) Text(project.description)
                    Text(project.url, style = MaterialTheme.typography.bodySmall)
                    project.group?.let { Text("通知分组：$it", style = MaterialTheme.typography.bodySmall) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { BarkProjectNavigator.open(context, project.id) }) { Text("打开项目") }
                        project.group?.let { group ->
                            OutlinedButton(onClick = {
                                val uri = Uri.Builder().scheme("bark").authority("history")
                                    .appendQueryParameter("group", group).build()
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri).setClass(context, MainActivity::class.java))
                            }) { Text("项目消息") }
                        }
                        OutlinedButton(onClick = { editingId = project.id; showEditor = true }) { Text("编辑") }
                        TextButton(onClick = { deletingId = project.id }) { Text("删除") }
                        TextButton(enabled = index > 0, onClick = { store.move(project.id, -1); refresh() }) { Text("上移") }
                        TextButton(enabled = index < projects.lastIndex, onClick = { store.move(project.id, 1); refresh() }) { Text("下移") }
                    }
                }
            }
        }
        Text("添加桌面入口：长按桌面 → 小组件 → Bark 项目 → 选择项目。", style = MaterialTheme.typography.bodySmall)
    }

    if (showEditor) {
        key(editingId) {
            ProjectEditor(
                project = projects.firstOrNull { it.id == editingId },
                onDismiss = { showEditor = false },
                onSave = { name, url, description, group ->
                    store.save(editingId, name, url, description, group)
                    refresh()
                    showEditor = false
                },
            )
        }
    }
    projects.firstOrNull { it.id == deletingId }?.let { project ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("删除 ${project.name}？") },
            text = { Text("这会移除项目入口，已放置的对应桌面组件需要重新选择项目。") },
            confirmButton = {
                TextButton(onClick = { store.remove(project.id); refresh(); deletingId = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("取消") } },
        )
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

package com.minijarvis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.data.TaskEntity
import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.launch

@Composable
fun TasksScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val tasks by container.taskRepository.observeAll().collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reminderMinutes by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Reminders fire as local notifications via WorkManager — nothing is sent off-device.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = reminderMinutes,
            onValueChange = { reminderMinutes = it },
            label = { Text("Remind me in N minutes (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            if (title.isNotBlank()) {
                val minutes = reminderMinutes.toLongOrNull()
                val reminderAt = minutes?.let { TimeUtils.nowMillis() + it * 60_000 }
                scope.launch {
                    val taskId = container.taskRepository.add(
                        title = title.trim(),
                        description = description.trim(),
                        dueAtMillis = reminderAt,
                        reminderAtMillis = reminderAt
                    )
                    if (reminderAt != null) {
                        container.reminderScheduler.scheduleTaskReminder(taskId, title.trim(), reminderAt)
                    }
                    title = ""; description = ""; reminderMinutes = ""
                }
            }
        }) { Text("Add task") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { scope.launch { container.taskRepository.setCompleted(task, !task.completed) } },
                    onDelete = {
                        scope.launch {
                            container.reminderScheduler.cancelTaskReminder(task.id)
                            container.taskRepository.delete(task)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
                Column {
                    Text(task.title)
                    if (task.description.isNotBlank()) Text(task.description, style = MaterialTheme.typography.bodySmall)
                    task.dueAtMillis?.let { Text("Due: ${TimeUtils.formatDateTime(it)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Button(onClick = onDelete) { Text("Delete") }
        }
    }
}

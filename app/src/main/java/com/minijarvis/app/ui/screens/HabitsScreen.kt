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
import com.minijarvis.app.data.HabitEntity
import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.launch

@Composable
fun HabitsScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val habits by container.habitRepository.observeActive().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("7") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Habit streaks are tracked entirely offline.", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Habit name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target per week") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            val perWeek = target.toIntOrNull() ?: 7
            if (name.isNotBlank()) {
                scope.launch {
                    container.habitRepository.addHabit(name.trim(), perWeek)
                    name = ""
                }
            }
        }) { Text("Add habit") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(habits, key = { it.id }) { habit -> HabitRow(container, habit) }
        }
    }
}

@Composable
private fun HabitRow(container: AppContainer, habit: HabitEntity) {
    val scope = rememberCoroutineScope()
    val logs by container.habitRepository.observeLogsForHabit(habit.id).collectAsState(initial = emptyList())
    val today = TimeUtils.toEpochDay()
    val completedToday = logs.any { it.epochDay == today && it.completed }
    val weekAgo = today - 7
    val weeklyCount = logs.count { it.epochDay > weekAgo && it.completed }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(habit.name)
                Text("$weeklyCount/${habit.targetPerWeek} this week", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { scope.launch { container.habitRepository.toggleForDay(habit.id) } }) {
                Text(if (completedToday) "Done today ✓" else "Mark done")
            }
        }
    }
}

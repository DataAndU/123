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
import com.minijarvis.app.data.FoodEntity
import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.launch

private val MEAL_TYPES = listOf("breakfast", "lunch", "dinner", "snack")

@Composable
fun FoodScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val entries by container.foodRepository.observeAll().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(MEAL_TYPES.first()) }
    var calories by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Meals are logged locally only.", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Food name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MEAL_TYPES.forEach { type ->
                Button(onClick = { mealType = type }) { Text(type) }
            }
        }
        Text("Selected: $mealType")
        OutlinedTextField(
            value = calories,
            onValueChange = { calories = it },
            label = { Text("Calories (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            if (name.isNotBlank()) {
                scope.launch {
                    container.foodRepository.add(
                        name = name.trim(),
                        mealType = mealType,
                        calories = calories.toIntOrNull(),
                        note = ""
                    )
                    name = ""; calories = ""
                }
            }
        }) { Text("Add food entry") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(entries, key = { it.id }) { entry -> FoodRow(entry) { scope.launch { container.foodRepository.delete(entry) } } }
        }
    }
}

@Composable
private fun FoodRow(entry: FoodEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${entry.name} (${entry.mealType})")
                entry.calories?.let { Text("$it kcal", style = MaterialTheme.typography.bodySmall) }
                Text(TimeUtils.formatDateTime(entry.timestampMillis), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onDelete) { Text("Delete") }
        }
    }
}

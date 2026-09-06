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
import com.minijarvis.app.data.WeightEntity
import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.launch

@Composable
fun WeightScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val entries by container.weightRepository.observeAll().collectAsState(initial = emptyList())
    var weight by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var metricType by remember { mutableStateOf("") }
    var metricValue by remember { mutableStateOf("") }
    var metricUnit by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Weight & health readings are stored only on this device.", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val kg = weight.toDoubleOrNull()
            if (kg != null) {
                scope.launch {
                    container.weightRepository.add(kg, note.trim())
                    weight = ""; note = ""
                }
            }
        }) { Text("Log weight") }

        Text("Other health metrics (blood pressure, heart rate, etc.)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = metricType, onValueChange = { metricType = it }, label = { Text("Type") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = metricValue, onValueChange = { metricValue = it }, label = { Text("Value") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = metricUnit, onValueChange = { metricUnit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f))
        }
        Button(onClick = {
            val value = metricValue.toDoubleOrNull()
            if (metricType.isNotBlank() && value != null) {
                scope.launch {
                    container.weightRepository.addHealthMetric(metricType.trim(), value, metricUnit.trim())
                    metricType = ""; metricValue = ""; metricUnit = ""
                }
            }
        }) { Text("Log health metric") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(entries, key = { it.id }) { entry -> WeightRow(entry) { scope.launch { container.weightRepository.delete(entry) } } }
        }
    }
}

@Composable
private fun WeightRow(entry: WeightEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${entry.weightKg} kg")
                if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall)
                Text(TimeUtils.formatDateTime(entry.timestampMillis), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onDelete) { Text("Delete") }
        }
    }
}

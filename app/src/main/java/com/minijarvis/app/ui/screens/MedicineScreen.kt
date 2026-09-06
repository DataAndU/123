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
import com.minijarvis.app.data.MedicineEntity
import kotlinx.coroutines.launch

@Composable
fun MedicineScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val medicines by container.medicineRepository.observeActive().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var scheduleNote by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Medicine names and dose logs never leave this device.", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Medicine name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = dosage, onValueChange = { dosage = it }, label = { Text("Dosage") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = scheduleNote,
            onValueChange = { scheduleNote = it },
            label = { Text("Schedule note (e.g. twice daily)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            if (name.isNotBlank()) {
                scope.launch {
                    container.medicineRepository.addMedicine(name.trim(), dosage.trim(), scheduleNote.trim())
                    name = ""; dosage = ""; scheduleNote = ""
                }
            }
        }) { Text("Add medicine") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(medicines, key = { it.id }) { medicine ->
                MedicineRow(
                    medicine = medicine,
                    onTaken = { scope.launch { container.medicineRepository.logDose(medicine, taken = true) } },
                    onMissed = { scope.launch { container.medicineRepository.logDose(medicine, taken = false) } },
                    onDelete = { scope.launch { container.medicineRepository.delete(medicine) } }
                )
            }
        }
    }
}

@Composable
private fun MedicineRow(
    medicine: MedicineEntity,
    onTaken: () -> Unit,
    onMissed: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("${medicine.name} — ${medicine.dosage}")
            if (medicine.scheduleNote.isNotBlank()) Text(medicine.scheduleNote, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onTaken) { Text("Taken") }
                Button(onClick = onMissed) { Text("Missed") }
                Button(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

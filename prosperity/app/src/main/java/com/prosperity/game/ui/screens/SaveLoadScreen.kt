package com.prosperity.game.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.save.SaveSlotInfo
import com.prosperity.game.ui.GameViewModel
import com.prosperity.game.ui.components.formatMoneyShort
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SaveLoadScreen(viewModel: GameViewModel, onLoaded: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshSlots() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportCurrentGame(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importGame(it); onLoaded() }
    }

    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("prosperity_save.json") }, enabled = state.game != null) { Text("Export Save") }
                Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import Save") }
            }
        }
        items(state.saveSlots) { slot -> SlotCard(slot, viewModel, onLoaded) }
    }
}

@Composable
private fun SlotCard(slot: SaveSlotInfo, viewModel: GameViewModel, onLoaded: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Slot ${slot.slot}", style = MaterialTheme.typography.titleSmall)
            if (slot.exists) {
                Text("${slot.playerName} — ${slot.gameMode?.displayName} (${slot.difficulty?.displayName})")
                Text("Month ${slot.month} • Net worth ${formatMoneyShort(slot.netWorth ?: 0.0)}")
                slot.savedAtEpochMillis?.let { Text(SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(it)), style = MaterialTheme.typography.labelSmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.loadFromSlot(slot.slot); onLoaded() }) { Text("Load") }
                    Button(onClick = { viewModel.saveToSlot(slot.slot) }) { Text("Overwrite") }
                    Button(onClick = { viewModel.deleteSlot(slot.slot) }) { Text("Delete") }
                }
            } else {
                Text("Empty")
                Button(onClick = { viewModel.saveToSlot(slot.slot) }) { Text("Save Here") }
            }
        }
    }
}

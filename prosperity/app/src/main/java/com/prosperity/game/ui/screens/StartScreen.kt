package com.prosperity.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.engine.game.DifficultyLevel
import com.prosperity.game.engine.game.GameMode
import com.prosperity.game.ui.GameViewModel
import com.prosperity.game.ui.components.Chip

@Composable
fun StartScreen(viewModel: GameViewModel, onGameStarted: () -> Unit) {
    var playerName by remember { mutableStateOf("Player") }
    var selectedMode by remember { mutableStateOf(GameMode.CAREER) }
    var selectedDifficulty by remember { mutableStateOf(DifficultyLevel.NORMAL) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Prosperity", style = MaterialTheme.typography.headlineMedium)
            Text("A life & economy simulator — build your financial empire, entirely offline.", style = MaterialTheme.typography.bodyMedium)
        }

        if (viewModel.hasAutosave()) {
            item {
                Button(onClick = { viewModel.continueAutosave(); onGameStarted() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue Previous Game")
                }
            }
        }

        item {
            OutlinedTextField(value = playerName, onValueChange = { playerName = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
        }

        item { Text("Game Mode", style = MaterialTheme.typography.titleMedium) }
        items(GameMode.entries) { mode ->
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Chip(label = mode.displayName, selected = mode == selectedMode) { selectedMode = mode }
                Text(mode.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
            }
        }

        item { Text("Difficulty", style = MaterialTheme.typography.titleMedium) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DifficultyLevel.entries.forEach { level ->
                    Chip(label = level.displayName, selected = level == selectedDifficulty) { selectedDifficulty = level }
                }
            }
        }

        item {
            Button(
                onClick = {
                    viewModel.startNewGame(selectedMode, selectedDifficulty, playerName)
                    onGameStarted()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start New Game") }
        }
    }
}

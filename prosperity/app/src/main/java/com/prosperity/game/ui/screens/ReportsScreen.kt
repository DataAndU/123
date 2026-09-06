package com.prosperity.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.engine.game.AchievementCatalog
import com.prosperity.game.ui.GameViewModel
import com.prosperity.game.ui.components.ComparisonBars
import com.prosperity.game.ui.components.LineChart
import com.prosperity.game.ui.components.SectionHeader
import com.prosperity.game.ui.components.formatMoneyShort

@Composable
fun ReportsScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game ?: return

    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Net Worth Over Time", infoTerm = "Net Worth")
                    LineChart(game.player.netWorthHistory)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Income vs. Expenses (last month)")
                    ComparisonBars(
                        "Income", game.player.incomeHistory.lastOrNull() ?: 0.0,
                        "Expenses", game.player.expenseHistory.lastOrNull() ?: 0.0
                    )
                }
            }
        }

        item { SectionHeader("Achievements") }
        items(AchievementCatalog.all) { achievement ->
            val unlocked = achievement.id in game.player.achievementsUnlocked
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(achievement.title, style = MaterialTheme.typography.titleSmall, color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(achievement.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { SectionHeader("Recent Events") }
        items(game.eventLog.takeLast(15).reversed()) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("Month ${entry.month}: ${entry.title}", style = MaterialTheme.typography.titleSmall)
                    Text("${entry.chosenOptionLabel} — ${entry.outcomeSummary}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { SectionHeader("Local Leaderboard (your past runs)") }
        items(state.leaderboard.entries) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("${entry.playerName} — ${formatMoneyShort(entry.finalNetWorth)}", style = MaterialTheme.typography.titleSmall)
                    Text("${entry.gameMode.displayName} (${entry.difficulty.displayName}) • ${entry.monthsPlayed} months • ${entry.tierReached.displayName}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.leaderboard.entries.isEmpty()) {
            item { Text("No completed runs yet.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

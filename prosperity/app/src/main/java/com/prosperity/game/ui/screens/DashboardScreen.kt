package com.prosperity.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.engine.economy.BusinessCyclePhase
import com.prosperity.game.engine.game.GameState
import com.prosperity.game.engine.player.FinanceEngine
import com.prosperity.game.ui.GameViewModel
import com.prosperity.game.ui.components.LineChart
import com.prosperity.game.ui.components.SectionHeader
import com.prosperity.game.ui.components.StatCard
import com.prosperity.game.ui.components.changeColor
import com.prosperity.game.ui.components.formatMoney
import com.prosperity.game.ui.components.formatMoneyShort

@Composable
fun DashboardScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game ?: return
    val netWorth = FinanceEngine.netWorth(game.player, game.markets)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Column {
                Text("Month ${game.economy.month} — ${game.gameMode.displayName}", style = MaterialTheme.typography.titleMedium)
                Text("${game.progressionTier.displayName} tier", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { StatCard("Cash", formatMoney(game.player.cash)) }
        item { StatCard("Bank Savings", formatMoney(game.player.bankSavings)) }
        item { StatCard("Net Worth", formatMoneyShort(netWorth), valueColor = changeColor(netWorth)) }
        item { StatCard("Debt", formatMoney(totalDebt(game))) }
        item { StatCard("Happiness", "${game.player.happiness.toInt()}/100") }
        item { StatCard("Health", "${game.player.health.toInt()}/100") }

        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Economy: ${phaseLabel(game.economy.phase)}", infoTerm = "Recession")
                    Text("GDP Growth: ${"%.1f".format(game.economy.gdpGrowthRate)}% • Inflation: ${"%.1f".format(game.economy.inflationRate)}%")
                    Text("Unemployment: ${"%.1f".format(game.economy.unemploymentRate)}% • Interest Rate: ${"%.1f".format(game.economy.interestRate)}%")
                }
            }
        }

        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Net Worth History", infoTerm = "Net Worth")
                    LineChart(game.player.netWorthHistory, lineColor = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (game.eventLog.isNotEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Latest News", style = MaterialTheme.typography.titleMedium)
                        val last = game.eventLog.last()
                        Text("${last.title}: ${last.outcomeSummary}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            if (game.isGameOver) {
                Text(game.gameOverReason ?: "Game over.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            } else {
                Button(onClick = { viewModel.advanceMonth() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Advance to Next Month")
                }
            }
        }
    }
}

private fun totalDebt(game: GameState): Double =
    game.player.loans.sumOf { it.principalRemaining } +
        game.player.properties.sumOf { it.mortgageBalance } +
        game.player.businesses.sumOf { it.loanBalance }

private fun phaseLabel(phase: BusinessCyclePhase): String = when (phase) {
    BusinessCyclePhase.EXPANSION -> "Expansion"
    BusinessCyclePhase.PEAK -> "Peak"
    BusinessCyclePhase.RECESSION -> "Recession"
    BusinessCyclePhase.TROUGH -> "Trough"
}

package com.prosperity.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.ui.OnlineViewModel
import com.prosperity.game.ui.components.Chip
import com.prosperity.game.ui.components.LineChart
import com.prosperity.game.ui.components.SectionHeader
import com.prosperity.game.ui.components.StatCard
import com.prosperity.game.ui.components.formatMoney
import com.prosperity.game.ui.components.formatMoneyShort
import com.prosperity.game.network.dto.LIFESTYLE_TIERS

@Composable
fun DashboardScreen(viewModel: OnlineViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val player = uiState.player
    val economy = uiState.economy

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Welcome, ${player?.displayName ?: "..."}", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (uiState.isConnected) "Connected — ${uiState.onlineCount} players online" else "Connecting...",
                style = MaterialTheme.typography.labelMedium,
                color = if (uiState.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Net Worth", formatMoneyShort(player?.netWorth ?: 0.0), Modifier.weight(1f))
                StatCard("Cash", formatMoneyShort(player?.cash ?: 0.0), Modifier.weight(1f))
                StatCard("Wallet Coins", "${(player?.walletCoins ?: 0.0).toInt()}", Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Happiness", "${(player?.happiness ?: 0.0).toInt()}/100", Modifier.weight(1f))
                StatCard("Health", "${(player?.health ?: 0.0).toInt()}/100", Modifier.weight(1f))
                StatCard("Reputation", "${(player?.reputation ?: 0.0).toInt()}/100", Modifier.weight(1f))
            }
        }

        item {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("The Economy", infoTerm = "GDP Growth")
                    if (economy != null) {
                        Text("Phase: ${economy.phase} (month ${economy.month}, ${economy.phaseMonthsElapsed} months in)")
                        Text("GDP growth: ${"%.2f".format(economy.gdpGrowthRate)}%  •  Unemployment: ${"%.1f".format(economy.unemploymentRate)}%")
                        Text("Inflation: ${"%.2f".format(economy.inflationRate)}%  •  Interest rate: ${"%.2f".format(economy.interestRate)}%")
                        Text("Data source: ${economy.dataSource}", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text("Loading economy data...")
                    }
                }
            }
        }

        if (uiState.economyHistory.size >= 2) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeader("GDP Growth History")
                        LineChart(uiState.economyHistory.map { it.gdpGrowthRate })
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Lifestyle")
                    Text("Choosing a higher lifestyle tier raises your monthly cost of living but boosts happiness.")
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LIFESTYLE_TIERS.forEach { tier ->
                            Chip(tier, selected = player?.lifestyleTier == tier, onClick = { viewModel.setLifestyle(tier) })
                        }
                    }
                }
            }
        }

        if (!player?.loans.isNullOrEmpty()) {
            item { Text("Personal Loans", style = MaterialTheme.typography.titleMedium) }
            items(player!!.loans) { loan ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${loan.type} loan — ${formatMoney(loan.principalRemaining)} remaining")
                        Text("Payment ${formatMoney(loan.monthlyPayment)}/mo at ${loan.annualRate}% APR, ${loan.termMonthsRemaining} months left", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (!player?.properties.isNullOrEmpty()) {
            item { Text("Properties", style = MaterialTheme.typography.titleMedium) }
            items(player!!.properties) { property ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Purchased at ${formatMoney(property.purchasePrice)}")
                        Text("Mortgage balance: ${formatMoney(property.mortgageBalance)} • Rent income: ${formatMoney(property.monthlyRentIncome)}/mo", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item { Divider(Modifier.padding(vertical = 8.dp)) }
        item {
            Text(
                "Net worth change (savings, dividends, coupons, rent, and expenses) is applied automatically every in-game month by the server — you don't need to be online for your finances to move.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

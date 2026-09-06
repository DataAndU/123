package com.prosperity.game.ui.screens

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.engine.player.LifestyleTier
import com.prosperity.game.engine.player.PlayerActions
import com.prosperity.game.ui.GameViewModel
import com.prosperity.game.ui.components.Chip
import com.prosperity.game.ui.components.SectionHeader
import com.prosperity.game.ui.components.formatMoney

@Composable
fun BankScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game ?: return
    var savingsAmount by remember { mutableStateOf("") }
    var loanAmount by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row { SectionHeader("Savings Account", infoTerm = "Compound Interest") }
                    Text("Balance: ${formatMoney(game.player.bankSavings)}")
                    Text("Current savings APR: ~${"%.1f".format(game.economy.interestRate * 0.65)}%", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = savingsAmount, onValueChange = { savingsAmount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { savingsAmount.toDoubleOrNull()?.let { viewModel.applyResult(PlayerActions.depositSavings(game.player, it)) } }) { Text("Deposit") }
                        Button(onClick = { savingsAmount.toDoubleOrNull()?.let { viewModel.applyResult(PlayerActions.withdrawSavings(game.player, it)) } }) { Text("Withdraw") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Personal Loans", infoTerm = "Interest Rate")
                    Text("Market rate right now: ~${"%.1f".format(game.economy.interestRate + 4.0)}% APR for a personal loan")
                    OutlinedTextField(value = loanAmount, onValueChange = { loanAmount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        loanAmount.toDoubleOrNull()?.let {
                            viewModel.applyResult(PlayerActions.takePersonalLoan(game.player, it, game.economy.interestRate + 4.0, 24))
                        }
                    }) { Text("Borrow (24 months)") }
                }
            }
        }

        items(game.player.loans) { loan ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("${loan.type} loan: ${formatMoney(loan.principalRemaining)} remaining")
                    Text("APR ${"%.1f".format(loan.annualRate)}% • payment ${formatMoney(loan.monthlyPayment)}/mo • ${loan.termMonthsRemaining} months left")
                    var payoff by remember { mutableStateOf("") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = payoff, onValueChange = { payoff = it }, label = { Text("Extra payment") }, modifier = Modifier.weight(1f))
                        Button(onClick = { payoff.toDoubleOrNull()?.let { viewModel.applyResult(PlayerActions.repayLoanEarly(game.player, loan.id, it)) } }) { Text("Pay down") }
                    }
                }
            }
        }

        item { SectionHeader("Lifestyle", infoTerm = "Inflation") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LifestyleTier.entries.forEach { tier ->
                    Row {
                        Chip(label = "${tier.displayName} — ${formatMoney(tier.monthlyCost)}/mo", selected = tier == game.player.lifestyleTier) {
                            viewModel.applyResult(PlayerActions.setLifestyle(game.player, tier))
                        }
                    }
                }
            }
        }
    }
}

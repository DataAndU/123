package com.prosperity.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.ui.OnlineViewModel
import com.prosperity.game.ui.components.SectionHeader
import com.prosperity.game.ui.components.formatMoney

@Composable
fun BankScreen(viewModel: OnlineViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val player = uiState.player
    val markets = uiState.markets

    var savingsAmount by remember { mutableStateOf("500") }
    var loanAmount by remember { mutableStateOf("5000") }
    var currencyAmount by remember { mutableStateOf("500") }

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Bank", style = MaterialTheme.typography.headlineSmall) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Savings", infoTerm = "Compound Interest")
                    Text("Balance: ${formatMoney(player?.bankSavings ?: 0.0)}")
                    OutlinedTextField(value = savingsAmount, onValueChange = { savingsAmount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Row(Modifier.padding(top = 8.dp)) {
                        Button(onClick = { savingsAmount.toDoubleOrNull()?.let { viewModel.depositSavings(it) } }) { Text("Deposit") }
                        OutlinedButton(onClick = { savingsAmount.toDoubleOrNull()?.let { viewModel.withdrawSavings(it) } }, modifier = Modifier.padding(start = 8.dp)) { Text("Withdraw") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Personal Loan")
                    OutlinedTextField(value = loanAmount, onValueChange = { loanAmount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Row(Modifier.padding(top = 8.dp)) {
                        Button(onClick = { loanAmount.toDoubleOrNull()?.let { viewModel.takePersonalLoan(it, 9.0, 24) } }) { Text("Take loan (9% APR, 24mo)") }
                    }
                }
            }
        }

        if (!player?.loans.isNullOrEmpty()) {
            items(player!!.loans) { loan ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${loan.type} — ${formatMoney(loan.principalRemaining)} left")
                            Text("${formatMoney(loan.monthlyPayment)}/mo", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { viewModel.repayPersonalLoan(loan.id, loan.monthlyPayment * 3) }) { Text("Pay extra") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Real Estate", infoTerm = "Diversification")
                    val price = (markets?.housingPriceIndex ?: 100.0) / 100.0 * 220_000
                    Text("Standard unit: ~${formatMoney(price)} (20% down = ${formatMoney(price * 0.2)})")
                    Button(onClick = { viewModel.buyProperty(0.2, 6.5) }, modifier = Modifier.padding(top = 8.dp)) { Text("Buy with 20% down") }
                }
            }
        }

        if (!player?.properties.isNullOrEmpty()) {
            items(player!!.properties) { property ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Bought at ${formatMoney(property.purchasePrice)}")
                            Text("Mortgage ${formatMoney(property.mortgageBalance)} • Rent ${formatMoney(property.monthlyRentIncome)}/mo", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { viewModel.sellProperty(property.id) }) { Text("Sell") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Foreign Currency")
                    Text("Exchange rate: ${"%.3f".format(markets?.exchangeRate ?: 1.0)} • Holdings: ${"%.2f".format(player?.foreignCurrencyHoldings ?: 0.0)}")
                    OutlinedTextField(value = currencyAmount, onValueChange = { currencyAmount = it }, label = { Text("Amount (local $)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Row(Modifier.padding(top = 8.dp)) {
                        Button(onClick = { currencyAmount.toDoubleOrNull()?.let { viewModel.buyForeignCurrency(it) } }) { Text("Buy FX") }
                        OutlinedButton(onClick = { currencyAmount.toDoubleOrNull()?.let { viewModel.sellForeignCurrency(it) } }, modifier = Modifier.padding(start = 8.dp)) { Text("Sell FX") }
                    }
                }
            }
        }
    }
}

package com.prosperity.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.network.dto.BUSINESS_TYPE_CATALOG
import com.prosperity.game.network.dto.BusinessRecord
import com.prosperity.game.ui.OnlineViewModel
import com.prosperity.game.ui.components.SectionHeader
import com.prosperity.game.ui.components.formatMoney

@Composable
fun BusinessesScreen(viewModel: OnlineViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val businesses = uiState.player?.businesses ?: emptyList()
    var showStartDialog by remember { mutableStateOf(false) }
    var expandedBusiness by remember { mutableStateOf<BusinessRecord?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showStartDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "Start business") }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxWidth().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("My Businesses", style = MaterialTheme.typography.headlineSmall) }
            if (businesses.isEmpty()) {
                item { Text("You don't own any businesses yet. Tap + to start one.") }
            }
            items(businesses) { business ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${business.name} (${business.type})", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Level ${business.level} • ${business.employees} employees • ${formatMoney(business.businessCash)} cash • loan ${formatMoney(business.loanBalance)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Row(Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = { expandedBusiness = business }) { Text("Manage") }
                        }
                    }
                }
            }
        }
    }

    if (showStartDialog) {
        StartBusinessDialog(
            cash = uiState.player?.cash ?: 0.0,
            onDismiss = { showStartDialog = false },
            onStart = { type, name -> viewModel.startBusiness(type, name); showStartDialog = false }
        )
    }

    expandedBusiness?.let { business ->
        ManageBusinessDialog(business = business, viewModel = viewModel, onDismiss = { expandedBusiness = null })
    }
}

@Composable
private fun StartBusinessDialog(cash: Double, onDismiss: () -> Unit, onStart: (type: String, name: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(BUSINESS_TYPE_CATALOG.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a Business") },
        text = {
            Column {
                Text("Your cash: ${formatMoney(cash)}", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Business name") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                BUSINESS_TYPE_CATALOG.forEach { spec ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { selectedType = spec }) {
                            Text((if (selectedType.id == spec.id) "> " else "") + "${spec.displayName} — ${formatMoney(spec.startupCost)}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(selectedType.id, name.ifBlank { selectedType.displayName }) }, enabled = cash >= selectedType.startupCost) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ManageBusinessDialog(business: BusinessRecord, viewModel: OnlineViewModel, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf("100") }
    val amount = amountText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(business.name) },
        text = {
            Column {
                Text("Employees: ${business.employees} • Level ${business.level} • Reputation ${"%.0f".format(business.reputation)}")
                Text("Price multiplier: ${"%.2f".format(business.pricePointMultiplier)}x • Ad budget: ${formatMoney(business.advertisingBudgetMonthly)}/mo")
                Row(Modifier.padding(vertical = 8.dp)) {
                    OutlinedButton(onClick = { viewModel.hireEmployee(business.id) }) { Text("Hire") }
                    TextButton(onClick = { viewModel.fireEmployee(business.id) }) { Text("Fire") }
                }
                Row {
                    OutlinedButton(onClick = { viewModel.setBusinessPrice(business.id, (business.pricePointMultiplier + 0.1).coerceAtMost(2.0)) }) { Text("Price +") }
                    TextButton(onClick = { viewModel.setBusinessPrice(business.id, (business.pricePointMultiplier - 0.1).coerceAtLeast(0.5)) }) { Text("Price -") }
                }
                Row {
                    OutlinedButton(onClick = { viewModel.upgradeBusiness(business.id) }, enabled = business.level < 5) { Text("Upgrade") }
                    TextButton(onClick = { viewModel.setBusinessAdvertising(business.id, business.advertisingBudgetMonthly + 200) }) { Text("Ad budget +$200") }
                }
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { viewModel.withdrawFromBusiness(business.id, amount) }) { Text("Withdraw") }
                    TextButton(onClick = { viewModel.injectIntoBusiness(business.id, amount) }) { Text("Inject") }
                    TextButton(onClick = { viewModel.takeBusinessLoan(business.id, amount, 8.0) }) { Text("Loan") }
                    TextButton(onClick = { viewModel.repayBusinessLoan(business.id, amount) }) { Text("Repay") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.closeBusiness(business.id); onDismiss() }) { Text("Close Business") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

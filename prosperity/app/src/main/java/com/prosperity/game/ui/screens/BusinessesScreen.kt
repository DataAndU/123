package com.prosperity.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.prosperity.game.engine.business.Business
import com.prosperity.game.engine.business.BusinessCatalog
import com.prosperity.game.engine.business.BusinessSimulator
import com.prosperity.game.engine.business.BusinessType
import com.prosperity.game.ui.GameViewModel
import com.prosperity.game.ui.components.LineChart
import com.prosperity.game.ui.components.formatMoney

@Composable
fun BusinessesScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game ?: return
    var showStartDialog by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(onClick = { showStartDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Start a New Business") }
        }
        items(game.player.businesses, key = { it.id }) { business ->
            BusinessCard(
                business = business,
                expanded = expandedId == business.id,
                onToggleExpand = { expandedId = if (expandedId == business.id) null else business.id },
                viewModel = viewModel
            )
        }
        if (game.player.businesses.isEmpty()) {
            item { Text("You don't own any businesses yet.", style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (showStartDialog) {
        StartBusinessDialog(playerCash = game.player.cash, onDismiss = { showStartDialog = false }) { type, name ->
            viewModel.startBusiness(type, name)
            showStartDialog = false
        }
    }
}

@Composable
private fun BusinessCard(business: Business, expanded: Boolean, onToggleExpand: () -> Unit, viewModel: GameViewModel) {
    val spec = BusinessCatalog.specs.getValue(business.type)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(business.name, style = MaterialTheme.typography.titleSmall)
                    Text("${spec.displayName} • Level ${business.level} • ${business.employees} employees")
                    Text("Reputation ${business.reputation.toInt()} • Competition ${business.competitionPressure.toInt()}")
                    Text("Business cash: ${formatMoney(business.businessCash)} • Loan: ${formatMoney(business.loanBalance)}")
                }
                TextButton(onClick = onToggleExpand) { Text(if (expanded) "Hide" else "Manage") }
            }
            if (business.history.isNotEmpty()) {
                Text("Monthly profit history", style = MaterialTheme.typography.labelSmall)
                LineChart(business.history.map { it.profit }, showRangeLabels = false)
            }
            if (expanded) {
                BusinessControls(business, spec.startupCost, viewModel)
            }
        }
    }
}

@Composable
private fun BusinessControls(business: Business, startupCost: Double, viewModel: GameViewModel) {
    var priceSlider by remember(business.id) { mutableStateOf(business.pricePointMultiplier.toFloat()) }
    var adBudgetText by remember(business.id) { mutableStateOf(business.advertisingBudgetMonthly.toInt().toString()) }
    var loanAmountText by remember { mutableStateOf("") }
    var transferAmountText by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.updateBusiness(business.id) { BusinessSimulator.hire(it) } }) { Text("Hire") }
            Button(onClick = { viewModel.updateBusiness(business.id) { BusinessSimulator.fire(it) } }) { Text("Fire") }
            Button(onClick = { viewModel.updateBusiness(business.id) { BusinessSimulator.upgrade(it) } }) { Text("Upgrade (${formatMoney(business.upgradeCost)})") }
        }

        Text("Price point: ${"%.2f".format(priceSlider)}x market")
        Slider(
            value = priceSlider,
            onValueChange = { priceSlider = it },
            valueRange = 0.5f..2.0f,
            onValueChangeFinished = { viewModel.updateBusiness(business.id) { BusinessSimulator.setPrice(it, priceSlider.toDouble()) } }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = adBudgetText, onValueChange = { adBudgetText = it }, label = { Text("Ad budget/mo") }, modifier = Modifier.weight(1f))
            Button(onClick = { adBudgetText.toDoubleOrNull()?.let { viewModel.updateBusiness(business.id) { b -> BusinessSimulator.setAdvertising(b, it) } } }) { Text("Set") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = loanAmountText, onValueChange = { loanAmountText = it }, label = { Text("Loan amount") }, modifier = Modifier.weight(1f))
            Button(onClick = { loanAmountText.toDoubleOrNull()?.let { amt -> viewModel.updateBusiness(business.id) { BusinessSimulator.takeLoan(it, amt, 8.0) } } }) { Text("Borrow") }
            Button(onClick = { loanAmountText.toDoubleOrNull()?.let { amt -> viewModel.updateBusiness(business.id) { BusinessSimulator.repayLoan(it, amt) } } }) { Text("Repay") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = transferAmountText, onValueChange = { transferAmountText = it }, label = { Text("Amount") }, modifier = Modifier.weight(1f))
            Button(onClick = { transferAmountText.toDoubleOrNull()?.let { viewModel.withdrawBusinessProfit(business.id, it) } }) { Text("Withdraw") }
            Button(onClick = { transferAmountText.toDoubleOrNull()?.let { viewModel.injectBusinessCapital(business.id, it) } }) { Text("Inject") }
        }

        Button(onClick = { viewModel.closeBusiness(business.id) }) { Text("Close Business") }
    }
}

@Composable
private fun StartBusinessDialog(playerCash: Double, onDismiss: () -> Unit, onConfirm: (BusinessType, String) -> Unit) {
    var selectedType by remember { mutableStateOf(BusinessType.RESTAURANT) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a New Business") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BusinessType.entries.forEach { type ->
                    val spec = BusinessCatalog.specs.getValue(type)
                    Row(Modifier.fillMaxWidth()) {
                        androidx.compose.material3.RadioButton(selected = selectedType == type, onClick = { selectedType = type })
                        Column {
                            Text("${spec.displayName} — ${formatMoney(spec.startupCost)}")
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Business name") }, modifier = Modifier.fillMaxWidth())
                val cost = BusinessCatalog.specs.getValue(selectedType).startupCost
                Text(if (cost > playerCash) "Not enough cash (need ${formatMoney(cost)})" else "Cost: ${formatMoney(cost)}")
            }
        },
        confirmButton = { Button(onClick = { onConfirm(selectedType, name) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

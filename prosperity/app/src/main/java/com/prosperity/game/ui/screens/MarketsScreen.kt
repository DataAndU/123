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

private enum class InstrumentKind { STOCK, BOND, COMMODITY }
private data class TradeTarget(val kind: InstrumentKind, val id: String, val name: String, val price: Double, val owned: Double)

@Composable
fun MarketsScreen(viewModel: OnlineViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val markets = uiState.markets
    val player = uiState.player
    var tradeTarget by remember { mutableStateOf<TradeTarget?>(null) }

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Markets", style = MaterialTheme.typography.headlineSmall)
            markets?.let { Text("Housing index: ${"%.1f".format(it.housingPriceIndex)} • Exchange rate: ${"%.3f".format(it.exchangeRate)}", style = MaterialTheme.typography.labelSmall) }
        }

        item { SectionHeader("Stocks", infoTerm = "Diversification") }
        items(markets?.stocks ?: emptyList()) { stock ->
            val owned = player?.stockHoldings?.get(stock.id) ?: 0
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${stock.name} (${stock.sector})", style = MaterialTheme.typography.titleSmall)
                        Text("${formatMoney(stock.price)}/share • div ${stock.dividendYieldAnnual}% • owned $owned", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(onClick = { tradeTarget = TradeTarget(InstrumentKind.STOCK, stock.id, stock.name, stock.price, owned.toDouble()) }) { Text("Trade") }
                }
            }
        }

        item { SectionHeader("Bonds", infoTerm = "Bond Yield") }
        items(markets?.bonds ?: emptyList()) { bond ->
            val owned = player?.bondHoldings?.get(bond.id) ?: 0
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(bond.name, style = MaterialTheme.typography.titleSmall)
                        Text("${formatMoney(bond.price)}/unit • coupon ${bond.couponRate}% • ${bond.monthsRemaining}mo left • owned $owned", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(onClick = { tradeTarget = TradeTarget(InstrumentKind.BOND, bond.id, bond.name, bond.price, owned.toDouble()) }) { Text("Trade") }
                }
            }
        }

        item { SectionHeader("Commodities", infoTerm = "Risk vs. Return") }
        items(markets?.commodities ?: emptyList()) { commodity ->
            val owned = player?.commodityHoldings?.get(commodity.id) ?: 0.0
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(commodity.name, style = MaterialTheme.typography.titleSmall)
                        Text("${formatMoney(commodity.price)}/unit • owned ${"%.2f".format(owned)}", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(onClick = { tradeTarget = TradeTarget(InstrumentKind.COMMODITY, commodity.id, commodity.name, commodity.price, owned) }) { Text("Trade") }
                }
            }
        }
    }

    tradeTarget?.let { target ->
        TradeDialog(
            target = target,
            cash = player?.cash ?: 0.0,
            onDismiss = { tradeTarget = null },
            onTrade = { side, quantity ->
                when (target.kind) {
                    InstrumentKind.STOCK -> viewModel.tradeStock(target.id, side, quantity.toInt())
                    InstrumentKind.BOND -> viewModel.tradeBond(target.id, side, quantity.toInt())
                    InstrumentKind.COMMODITY -> viewModel.tradeCommodity(target.id, side, quantity)
                }
                tradeTarget = null
            }
        )
    }
}

@Composable
private fun TradeDialog(target: TradeTarget, cash: Double, onDismiss: () -> Unit, onTrade: (side: String, quantity: Double) -> Unit) {
    var quantityText by remember { mutableStateOf("1") }
    val quantity = quantityText.toDoubleOrNull() ?: 0.0
    val estimatedCost = quantity * target.price

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.name) },
        text = {
            Column {
                Text("Price: ${formatMoney(target.price)} • You own: ${"%.2f".format(target.owned)}")
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text("Estimated: ${formatMoney(estimatedCost)} (cash: ${formatMoney(cash)})", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = { if (quantity > 0) onTrade("buy", quantity) }, enabled = quantity > 0) { Text("Buy") }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = { if (quantity > 0) onTrade("sell", quantity) }, enabled = quantity > 0 && quantity <= target.owned) { Text("Sell") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

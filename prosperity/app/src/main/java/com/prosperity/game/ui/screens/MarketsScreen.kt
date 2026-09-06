package com.prosperity.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.prosperity.game.engine.market.Bond
import com.prosperity.game.engine.market.Commodity
import com.prosperity.game.engine.market.Stock
import com.prosperity.game.engine.player.PlayerActions
import com.prosperity.game.ui.GameViewModel
import com.prosperity.game.ui.components.Sparkline
import com.prosperity.game.ui.components.formatMoney

private enum class MarketTab(val label: String) { STOCKS("Stocks"), BONDS("Bonds"), COMMODITIES("Gold & Oil"), REAL_ESTATE("Real Estate"), CURRENCY("Currency") }

@Composable
fun MarketsScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game ?: return
    var tab by remember { mutableStateOf(MarketTab.STOCKS) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            MarketTab.entries.forEach { t ->
                Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
            }
        }
        when (tab) {
            MarketTab.STOCKS -> StocksTab(viewModel)
            MarketTab.BONDS -> BondsTab(viewModel)
            MarketTab.COMMODITIES -> CommoditiesTab(viewModel)
            MarketTab.REAL_ESTATE -> RealEstateTab(viewModel)
            MarketTab.CURRENCY -> CurrencyTab(viewModel)
        }
    }
}

@Composable
private fun StocksTab(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game ?: return
    var tradeTarget by remember { mutableStateOf<Stock?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(game.markets.stocks, key = { it.id }) { stock ->
            val owned = game.player.stockHoldings[stock.id] ?: 0
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${stock.name} (${stock.sector})", style = MaterialTheme.typography.titleSmall)
                        Text("${formatMoney(stock.price)} • div ${stock.dividendYieldAnnual}% • owned $owned", style = MaterialTheme.typography.bodySmall)
                    }
                    Sparkline(stock.priceHistory, modifier = Modifier.size(70.dp, 40.dp))
                    Button(onClick = { tradeTarget = stock }) { Text("Trade") }
                }
            }
        }
    }

    tradeTarget?.let { stock ->
        val owned = game.player.stockHoldings[stock.id] ?: 0
        TradeDialog(
            title = stock.name,
            unitPrice = stock.price,
            ownedUnits = owned.toDouble(),
            allowFractional = false,
            onDismiss = { tradeTarget = null },
            onBuy = { qty -> viewModel.applyResult(PlayerActions.buyStock(game.player, game.markets, stock.id, qty.toInt())); tradeTarget = null },
            onSell = { qty -> viewModel.applyResult(PlayerActions.sellStock(game.player, game.markets, stock.id, qty.toInt())); tradeTarget = null }
        )
    }
}

@Composable
private fun BondsTab(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game ?: return
    var tradeTarget by remember { mutableStateOf<Bond?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(game.markets.bonds, key = { it.id }) { bond ->
            val owned = game.player.bondHoldings[bond.id] ?: 0
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(bond.name, style = MaterialTheme.typography.titleSmall)
                    Text("Price ${formatMoney(bond.price)} • Coupon ${"%.1f".format(bond.couponRate)}% • ${bond.monthsRemaining}mo left • owned $owned")
                    Button(onClick = { tradeTarget = bond }) { Text("Trade") }
                }
            }
        }
    }

    tradeTarget?.let { bond ->
        val owned = game.player.bondHoldings[bond.id] ?: 0
        TradeDialog(
            title = bond.name,
            unitPrice = bond.price,
            ownedUnits = owned.toDouble(),
            allowFractional = false,
            onDismiss = { tradeTarget = null },
            onBuy = { qty -> viewModel.applyResult(PlayerActions.buyBond(game.player, game.markets, bond.id, qty.toInt())); tradeTarget = null },
            onSell = { qty -> viewModel.applyResult(PlayerActions.sellBond(game.player, game.markets, bond.id, qty.toInt())); tradeTarget = null }
        )
    }
}

@Composable
private fun CommoditiesTab(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game ?: return
    var tradeTarget by remember { mutableStateOf<Commodity?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(game.markets.commodities, key = { it.id }) { commodity ->
            val owned = game.player.commodityHoldings[commodity.id] ?: 0.0
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(commodity.name, style = MaterialTheme.typography.titleSmall)
                        Text("${formatMoney(commodity.price)} • owned ${"%.2f".format(owned)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Sparkline(commodity.priceHistory, modifier = Modifier.size(70.dp, 40.dp))
                    Button(onClick = { tradeTarget = commodity }) { Text("Trade") }
                }
            }
        }
    }

    tradeTarget?.let { commodity ->
        val owned = game.player.commodityHoldings[commodity.id] ?: 0.0
        TradeDialog(
            title = commodity.name,
            unitPrice = commodity.price,
            ownedUnits = owned,
            allowFractional = true,
            onDismiss = { tradeTarget = null },
            onBuy = { qty -> viewModel.applyResult(PlayerActions.buyCommodity(game.player, game.markets, commodity.id, qty)); tradeTarget = null },
            onSell = { qty -> viewModel.applyResult(PlayerActions.sellCommodity(game.player, game.markets, commodity.id, qty)); tradeTarget = null }
        )
    }
}

@Composable
private fun RealEstateTab(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game ?: return

    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("Housing Price Index: ${"%.1f".format(game.markets.housingPriceIndex)}", style = MaterialTheme.typography.titleSmall)
                    Text("Base property price today: ${formatMoney(com.prosperity.game.engine.player.BASE_PROPERTY_PRICE * game.markets.housingPriceIndex / 100.0)}")
                    Button(onClick = { viewModel.applyResult(PlayerActions.buyProperty(game.player, game.markets, 0.2, game.economy.interestRate + 1.5)) }) {
                        Text("Buy with 20% down")
                    }
                }
            }
        }
        items(game.player.properties, key = { it.id }) { property ->
            val currentValue = property.purchasePrice * (game.markets.housingPriceIndex / property.purchaseHousingIndex)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("Property bought at ${formatMoney(property.purchasePrice)}")
                    Text("Current value: ${formatMoney(currentValue)} • Mortgage left: ${formatMoney(property.mortgageBalance)}")
                    Text("Monthly rent income: ${formatMoney(property.monthlyRentIncome)}")
                    Button(onClick = { viewModel.applyResult(PlayerActions.sellProperty(game.player, game.markets, property.id)) }) { Text("Sell") }
                }
            }
        }
    }
}

@Composable
private fun CurrencyTab(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game ?: return
    var amount by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Exchange rate: 1 local = ${"%.3f".format(game.markets.exchangeRate)} foreign units")
        Text("You hold ${"%.2f".format(game.player.foreignCurrencyHoldings)} foreign units")
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                amount.toDoubleOrNull()?.let { viewModel.applyResult(PlayerActions.buyForeignCurrency(game.player, game.markets, it)) }
            }) { Text("Convert local → foreign") }
            Button(onClick = {
                amount.toDoubleOrNull()?.let { viewModel.applyResult(PlayerActions.sellForeignCurrency(game.player, game.markets, it)) }
            }) { Text("Convert foreign → local") }
        }
    }
}

@Composable
private fun TradeDialog(
    title: String,
    unitPrice: Double,
    ownedUnits: Double,
    allowFractional: Boolean,
    onDismiss: () -> Unit,
    onBuy: (Double) -> Unit,
    onSell: (Double) -> Unit
) {
    var qtyText by remember { mutableStateOf("") }
    val qty = qtyText.toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Price: ${formatMoney(unitPrice)} • You own ${if (allowFractional) "%.2f".format(ownedUnits) else ownedUnits.toInt()}")
                OutlinedTextField(value = qtyText, onValueChange = { qtyText = it }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth())
                Text("Estimated total: ${formatMoney(qty * unitPrice)}")
            }
        },
        confirmButton = { Button(onClick = { onBuy(qty) }) { Text("Buy") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onSell(qty) }) { Text("Sell") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

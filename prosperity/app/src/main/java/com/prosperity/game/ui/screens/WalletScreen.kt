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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.ui.OnlineViewModel
import com.prosperity.game.ui.components.SectionHeader

@Composable
fun WalletScreen(viewModel: OnlineViewModel, onBuyCoins: (productId: String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val wallet = uiState.wallet

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Wallet", style = MaterialTheme.typography.headlineSmall)
            Text("Balance: ${wallet?.balanceCoins?.toInt() ?: 0} coins", style = MaterialTheme.typography.titleMedium)
            Text(
                "Coins are purchased with real money and spent inside the game's marketplace. They never convert back to real money.",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item { SectionHeader("Buy Coins") }
        items(uiState.coinProducts.entries.toList()) { (productId, coins) ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$coins coins")
                    Button(onClick = { onBuyCoins(productId) }) { Text("Buy") }
                }
            }
        }

        item { SectionHeader("Recent Activity") }
        items(wallet?.transactions ?: emptyList()) { tx ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tx.reason.replace('_', ' '), style = MaterialTheme.typography.bodySmall)
                Text("${if (tx.amountCoins >= 0) "+" else ""}${tx.amountCoins}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

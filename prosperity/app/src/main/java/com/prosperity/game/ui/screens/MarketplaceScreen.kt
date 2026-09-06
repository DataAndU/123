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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.prosperity.game.network.dto.MarketOrder
import com.prosperity.game.ui.OnlineViewModel

private val NEXT_STATUS = mapOf("placed" to "accepted", "accepted" to "producing", "producing" to "delivered")

@Composable
fun MarketplaceScreen(viewModel: OnlineViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var tabIndex by remember { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val tabs = listOf("Browse", "My Listings", "Buying", "Selling")

    Scaffold(
        floatingActionButton = {
            if (tabIndex == 1) FloatingActionButton(onClick = { showCreateDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "New listing") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title -> Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(title) }) }
            }
            when (tabIndex) {
                0 -> LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.listings) { listing ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${listing.title} — by @${listing.sellerUsername}", style = MaterialTheme.typography.titleSmall)
                                Text(listing.description, style = MaterialTheme.typography.bodySmall)
                                Text("${listing.priceCoins} coins each • ${listing.quantityAvailable} available", style = MaterialTheme.typography.labelSmall)
                                Button(onClick = { viewModel.placeOrder(listing.id, 1) }, modifier = Modifier.padding(top = 8.dp)) { Text("Buy 1") }
                            }
                        }
                    }
                }
                1 -> LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.myListings) { listing ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(listing.title, style = MaterialTheme.typography.titleSmall)
                                Text("${listing.priceCoins} coins • ${listing.quantityAvailable} left • ${listing.status}", style = MaterialTheme.typography.labelSmall)
                                Row(Modifier.padding(top = 8.dp)) {
                                    if (listing.status == "active") {
                                        TextButton(onClick = { viewModel.updateListing(listing.id, status = "paused") }) { Text("Pause") }
                                    } else if (listing.status == "paused") {
                                        TextButton(onClick = { viewModel.updateListing(listing.id, status = "active") }) { Text("Resume") }
                                    }
                                    TextButton(onClick = { viewModel.updateListing(listing.id, status = "closed") }) { Text("Close") }
                                }
                            }
                        }
                    }
                }
                2 -> LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.ordersBuying) { order -> OrderCard(order, isSeller = false, onAdvance = {}, onCancel = { viewModel.cancelOrder(order.id) }) }
                }
                3 -> LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.ordersSelling) { order ->
                        OrderCard(
                            order, isSeller = true,
                            onAdvance = { NEXT_STATUS[order.status]?.let { next -> viewModel.advanceOrder(order.id, next) } },
                            onCancel = { viewModel.cancelOrder(order.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateListingDialog(
            businessIds = uiState.player?.businesses?.map { it.id to it.name } ?: emptyList(),
            onDismiss = { showCreateDialog = false },
            onCreate = { businessId, title, description, price, quantity ->
                viewModel.createListing(businessId, title, description, price, quantity)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun OrderCard(order: MarketOrder, isSeller: Boolean, onAdvance: () -> Unit, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(order.listingTitle, style = MaterialTheme.typography.titleSmall)
            Text(
                "${if (isSeller) "Buyer @${order.buyerUsername}" else "Seller @${order.sellerUsername}"} • qty ${order.quantity} • ${order.totalPriceCoins} coins • ${order.status}",
                style = MaterialTheme.typography.labelSmall
            )
            Row(Modifier.padding(top = 8.dp)) {
                if (isSeller && NEXT_STATUS.containsKey(order.status)) {
                    TextButton(onClick = onAdvance) { Text("Advance to ${NEXT_STATUS[order.status]}") }
                }
                if (order.status == "placed") {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun CreateListingDialog(
    businessIds: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onCreate: (businessId: String?, title: String, description: String, price: Int, quantity: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("100") }
    var quantityText by remember { mutableStateOf("10") }
    var selectedBusiness by remember { mutableStateOf<String?>(businessIds.firstOrNull()?.first) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Listing") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = priceText, onValueChange = { priceText = it }, label = { Text("Price (coins)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = quantityText, onValueChange = { quantityText = it }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (businessIds.isNotEmpty()) {
                    Text("Fulfilling business:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                    businessIds.forEach { (id, name) ->
                        TextButton(onClick = { selectedBusiness = id }) { Text((if (selectedBusiness == id) "> " else "") + name) }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onCreate(selectedBusiness, title, description, priceText.toIntOrNull() ?: 0, quantityText.toIntOrNull() ?: 0)
            }, enabled = title.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

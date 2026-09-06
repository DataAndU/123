package com.prosperity.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.network.dto.UserSearchResult
import com.prosperity.game.ui.OnlineViewModel

@Composable
fun SocialScreen(viewModel: OnlineViewModel) {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Chat", "Friends", "Find People")

    LaunchedEffect(Unit) { viewModel.joinChatChannel("global") }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(title) })
            }
        }
        when (tabIndex) {
            0 -> ChatTab(viewModel)
            1 -> FriendsTab(viewModel)
            2 -> FindPeopleTab(viewModel)
        }
    }
}

@Composable
private fun ChatTab(viewModel: OnlineViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var message by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("${uiState.onlineCount} players online", style = MaterialTheme.typography.labelSmall)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(uiState.chatMessages) { msg ->
                Text("${msg.senderUsername}: ${msg.body}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message") }, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                viewModel.sendChat("global", message)
                message = ""
            }) { Icon(Icons.Filled.Send, contentDescription = "Send") }
        }
    }
}

@Composable
private fun FriendsTab(viewModel: OnlineViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Pending requests", style = MaterialTheme.typography.titleSmall) }
        val pending = uiState.friends.filter { it.status == "pending" && it.direction == "incoming" }
        if (pending.isEmpty()) item { Text("None", style = MaterialTheme.typography.labelSmall) }
        items(pending) { friend ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(friend.displayName)
                    Row {
                        TextButton(onClick = { viewModel.respondToFriendRequest(friend.friendshipId, true) }) { Text("Accept") }
                        TextButton(onClick = { viewModel.respondToFriendRequest(friend.friendshipId, false) }) { Text("Decline") }
                    }
                }
            }
        }

        item { Divider(Modifier.padding(vertical = 8.dp)) }
        item { Text("Friends", style = MaterialTheme.typography.titleSmall) }
        val accepted = uiState.friends.filter { it.status == "accepted" }
        if (accepted.isEmpty()) item { Text("No friends yet — find people in the next tab.", style = MaterialTheme.typography.labelSmall) }
        items(accepted) { friend ->
            Card(Modifier.fillMaxWidth()) {
                Text(friend.displayName, Modifier.padding(12.dp))
            }
        }

        item { Divider(Modifier.padding(vertical = 8.dp)) }
        item { Text("Sent requests", style = MaterialTheme.typography.titleSmall) }
        val outgoing = uiState.friends.filter { it.status == "pending" && it.direction == "outgoing" }
        items(outgoing) { friend ->
            Text("${friend.displayName} (pending)", Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FindPeopleTab(viewModel: OnlineViewModel) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserSearchResult>>(emptyList()) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; if (it.length >= 2) viewModel.searchUsers(it) { r -> results = r } else results = emptyList() },
            label = { Text("Search by username") },
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { user ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${user.displayName} (@${user.username})")
                        OutlinedButton(onClick = { viewModel.sendFriendRequest(user.username) }) { Text("Add") }
                    }
                }
            }
        }
    }
}

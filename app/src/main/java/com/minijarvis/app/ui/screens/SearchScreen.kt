package com.minijarvis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.search.SearchResult
import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Search runs entirely on-device across every module — try \"lunch last week\" or \"vitamin\".",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search your data") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            scope.launch {
                results = container.searchEngine.search(query)
                searched = true
            }
        }) { Text("Search") }

        if (searched && results.isEmpty()) {
            Text("No results found.")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results) { result ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("[${result.module}] ${result.title}")
                        if (result.subtitle.isNotBlank()) Text(result.subtitle, style = MaterialTheme.typography.bodySmall)
                        Text(TimeUtils.formatDateTime(result.timestampMillis), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

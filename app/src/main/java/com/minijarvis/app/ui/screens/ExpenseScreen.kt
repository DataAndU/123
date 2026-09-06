package com.minijarvis.app.ui.screens

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.data.ExpenseEntity
import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.launch

@Composable
fun ExpenseScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val expenses by container.expenseRepository.observeAll().collectAsState(initial = emptyList())
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("All amounts are stored only in your local, encrypted database.", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())

        Button(onClick = {
            val value = amount.toDoubleOrNull()
            if (value != null && category.isNotBlank()) {
                scope.launch {
                    container.expenseRepository.add(amount = value, category = category.trim(), note = note.trim())
                    amount = ""; category = ""; note = ""
                }
            }
        }) {
            Text("Add expense")
        }

        val total = expenses.sumOf { it.amount }
        Text("Total logged: ₹${"%.2f".format(total)}", style = MaterialTheme.typography.titleMedium)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(expenses, key = { it.id }) { expense -> ExpenseRow(expense) { scope.launch { container.expenseRepository.delete(expense) } } }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${expense.category} — ₹${"%.2f".format(expense.amount)}")
                if (expense.note.isNotBlank()) Text(expense.note, style = MaterialTheme.typography.bodySmall)
                Text(TimeUtils.formatDateTime(expense.timestampMillis), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onDelete) { Text("Delete") }
        }
    }
}

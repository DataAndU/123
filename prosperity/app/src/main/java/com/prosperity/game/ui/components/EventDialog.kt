package com.prosperity.game.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.engine.event.EventDefinition

@Composable
fun EventDialog(event: EventDefinition, onChoose: (optionId: String) -> Unit) {
    AlertDialog(
        onDismissRequest = { /* must choose an option */ },
        title = { Text(event.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(event.description)
                Text(event.educationalNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                event.options.forEach { option ->
                    Button(onClick = { onChoose(option.id) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Text(option.label)
                    }
                }
            }
        }
    )
}

package com.minijarvis.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minijarvis.app.assistant.VoiceInputManager
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.data.ChatMessageEntity
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages by container.chatRepository.observeAll().collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val pendingConfirmation by container.confirmationGate.pending.collectAsState()

    val voiceInputManager = remember {
        VoiceInputManager(
            context = context,
            onResult = { text ->
                scope.launch {
                    val reply = container.assistantEngine.handle(text, source = "voice")
                    container.voiceOutputManager.speak(reply)
                }
            },
            onListeningChanged = { isListening = it },
            onError = { voiceError = it }
        )
    }

    DisposableEffect(Unit) {
        onDispose { voiceInputManager.destroy() }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceInputManager.startListening() else voiceError = "Microphone permission denied."
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "Fully offline assistant — no messages ever leave this device. Try \"call mom\", " +
                "\"open camera\", \"turn on the flashlight\", or \"what's on my screen\".",
            style = MaterialTheme.typography.bodySmall
        )
        voiceError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message -> ChatBubble(message) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…") }
            )
            IconButton(onClick = {
                if (isListening) {
                    voiceInputManager.stopListening()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice input")
            }
            IconButton(onClick = {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    input = ""
                    scope.launch {
                        val reply = container.assistantEngine.handle(text, source = "text")
                        container.voiceOutputManager.speak(reply)
                    }
                }
            }) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }

    pendingConfirmation?.let { request ->
        AlertDialog(
            onDismissRequest = { container.confirmationGate.respond(request.id, false) },
            title = { Text(request.title) },
            text = { Text(request.description) },
            confirmButton = {
                TextButton(onClick = { container.confirmationGate.respond(request.id, true) }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { container.confirmationGate.respond(request.id, false) }) { Text("Deny") }
            }
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessageEntity) {
    val isUser = message.role == "user"
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(4.dp)
        ) {
            Text(message.content, modifier = Modifier.padding(10.dp))
        }
    }
}

package com.minijarvis.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.minijarvis.app.assistant.WakeWordService
import com.minijarvis.app.control.JarvisAccessibilityService
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.system.PermissionManager
import com.minijarvis.app.system.ProactiveAgentWorker
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEraseConfirm by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    val wakeWordEnabled by container.assistantSettingsStore.isWakeWordEnabled.collectAsState(initial = false)
    val proactiveEnabled by container.assistantSettingsStore.isProactiveEnabled.collectAsState(initial = false)
    val autoApplySafeActions by container.assistantSettingsStore.isAutoApplySafeActionsEnabled.collectAsState(initial = false)

    var importedModels by remember { mutableStateOf(container.modelManager.listImportedModels()) }
    var loadedModelName by remember { mutableStateOf(container.localLlmEngine.loadedModelName()) }
    var isBusyWithModel by remember { mutableStateOf(false) }
    var modelStatusMessage by remember { mutableStateOf<String?>(null) }

    val multiPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshTick++
    }

    val importModelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = queryDisplayName(context, uri) ?: "model.task"
        isBusyWithModel = true
        scope.launch {
            container.modelManager.importModel(uri, fileName)
                .onSuccess {
                    importedModels = container.modelManager.listImportedModels()
                    modelStatusMessage = "Imported $fileName"
                }
                .onFailure { modelStatusMessage = "Import failed: ${it.message}" }
            isBusyWithModel = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Privacy & security", style = MaterialTheme.typography.titleLarge)
        Text(
            "• No INTERNET permission is declared anywhere in this app.\n" +
                "• All data is stored in a SQLCipher-encrypted database, keyed by a passphrase " +
                "generated once and sealed with the Android Keystore.\n" +
                "• Cloud/device-transfer backup of app data is explicitly disabled.\n" +
                "• Every optional module (calls, location, usage, music, camera, mic, phone control) is off " +
                "until you grant its permission, and can be revoked at any time in system Settings.",
            style = MaterialTheme.typography.bodyMedium
        )

        Divider()
        Text("Permission status", style = MaterialTheme.typography.titleMedium)
        Text("Microphone: ${status(PermissionManager.hasAll(context, PermissionManager.VOICE))}")
        Text("Camera: ${status(PermissionManager.hasAll(context, PermissionManager.CAMERA))}")
        Text("Call log: ${status(PermissionManager.hasAll(context, PermissionManager.CALL_LOG))}")
        Text("Location: ${status(PermissionManager.hasAll(context, PermissionManager.LOCATION))}")
        Text("Usage access: ${status(PermissionManager.hasUsageAccess(context))}")
        Text("Notification listener (music): ${status(PermissionManager.hasNotificationListenerAccess(context))}")

        Divider()
        Text("Phone & App Control", style = MaterialTheme.typography.titleLarge)
        Text(
            "This is the part of Mini JARVIS that can act on the rest of your phone: open apps, " +
                "call/text people, read and tap what's on screen, and flip system toggles. Every " +
                "piece is opt-in and independently revocable.",
            style = MaterialTheme.typography.bodyMedium
        )

        LaunchedEffect(refreshTick) { /* recompute status texts below on permission-result recomposition */ }

        Text("Calling: ${status(PermissionManager.hasAll(context, PermissionManager.CALLING))} (falls back to opening the dialer without it)")
        Text("Messaging (send/read SMS): ${status(PermissionManager.hasAll(context, PermissionManager.MESSAGING))}")
        Text("Contacts (call/text by name): ${status(PermissionManager.hasAll(context, PermissionManager.CONTACTS))}")
        Button(onClick = {
            multiPermissionLauncher.launch(
                PermissionManager.CALLING + PermissionManager.MESSAGING + PermissionManager.CONTACTS
            )
        }) { Text("Grant calling, messaging & contacts") }

        Text("Modify system settings (brightness): ${status(container.systemControlManager.hasWriteSettingsPermission())}")
        OutlinedButton(onClick = { context.startActivity(container.systemControlManager.requestWriteSettingsIntent()) }) {
            Text("Grant \"Modify system settings\"")
        }

        Text("Do Not Disturb access (ringer volume/DND): ${status(container.systemControlManager.hasNotificationPolicyAccess())}")
        OutlinedButton(onClick = { context.startActivity(container.systemControlManager.requestNotificationPolicyAccessIntent()) }) {
            Text("Grant Do Not Disturb access")
        }

        Text("Accessibility Service (read screen, tap, navigate, screenshot, WiFi/Bluetooth toggle): ${status(JarvisAccessibilityService.isEnabled(context))}")
        Text(
            "Android requires enabling this manually — it's the most powerful permission on the " +
                "platform, so no app is allowed to turn it on for itself. You'll see Mini JARVIS in " +
                "the Accessibility Service list; enabling it is what lets it act like a real assistant " +
                "instead of just this app's own screens.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = { JarvisAccessibilityService.openAccessibilitySettings(context) }) {
            Text("Open Accessibility settings")
        }

        Divider()
        Text("Wake word", style = MaterialTheme.typography.titleLarge)
        Text(
            "When on, Mini JARVIS listens in the background for you to say \"Jarvis\" and then your " +
                "command — no cloud wake-word engine, just Android's own on-device speech recognizer " +
                "restarted in a loop, so no new network permission is added for this. Trade-off: it's " +
                "less battery-efficient than a dedicated wake-word chip/engine, and there's a brief " +
                "gap between listening cycles. A persistent notification always shows while it's on, " +
                "with a one-tap Stop.",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Always-listening wake word", modifier = Modifier.weight(1f))
            Switch(
                checked = wakeWordEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        container.assistantSettingsStore.setWakeWordEnabled(enabled)
                        if (enabled) {
                            if (!PermissionManager.hasAll(context, PermissionManager.VOICE)) {
                                multiPermissionLauncher.launch(PermissionManager.VOICE + PermissionManager.NOTIFICATIONS)
                            }
                            WakeWordService.start(context)
                        } else {
                            WakeWordService.stop(context)
                        }
                    }
                }
            )
        }

        Divider()
        Text("Local AI model (optional)", style = MaterialTheme.typography.titleLarge)
        Text(
            "By default the assistant uses fast, zero-setup pattern matching — no model needed. " +
                "If you import a compatible local model file (a \".task\" bundle from Google's " +
                "LiteRT/MediaPipe model conversion tooling — e.g. a small quantized Gemma model), " +
                "it upgrades to genuinely understanding free-form requests and chaining multiple " +
                "actions per request, entirely on-device. Mini JARVIS never downloads a model " +
                "itself — that would need the INTERNET permission it deliberately doesn't have — " +
                "so you find/convert one yourself and import it here. Expect a file anywhere from " +
                "several hundred MB to a few GB, real RAM/storage use once loaded, and that this " +
                "only works on a real arm64 device (not the debug build's typical test emulator).",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Currently loaded: ${loadedModelName ?: "none (using pattern matching)"}")
        modelStatusMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        if (isBusyWithModel) CircularProgressIndicator(modifier = Modifier.padding(4.dp))

        Button(onClick = { importModelLauncher.launch(arrayOf("*/*")) }, enabled = !isBusyWithModel) {
            Text("Import model file")
        }

        importedModels.forEach { file ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(file.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (loadedModelName == file.name) {
                    TextButton(onClick = {
                        container.localLlmEngine.unload()
                        loadedModelName = null
                        scope.launch { container.assistantSettingsStore.setSelectedModelFileName(null) }
                    }) { Text("Unload") }
                } else {
                    TextButton(
                        onClick = {
                            isBusyWithModel = true
                            scope.launch {
                                container.localLlmEngine.load(file)
                                    .onSuccess {
                                        loadedModelName = file.name
                                        container.assistantSettingsStore.setSelectedModelFileName(file.name)
                                        modelStatusMessage = "Loaded ${file.name}"
                                    }
                                    .onFailure { modelStatusMessage = "Load failed: ${it.message}" }
                                isBusyWithModel = false
                            }
                        },
                        enabled = !isBusyWithModel
                    ) { Text("Load") }
                }
                TextButton(onClick = {
                    if (loadedModelName == file.name) {
                        container.localLlmEngine.unload()
                        loadedModelName = null
                    }
                    container.modelManager.deleteModel(file)
                    importedModels = container.modelManager.listImportedModels()
                }) { Text("Delete") }
            }
        }

        Divider()
        Text("Proactive suggestions", style = MaterialTheme.typography.titleLarge)
        Text(
            "Off by default. When on, a background check runs roughly hourly — cheap, rule-based " +
                "(never the local model, to keep battery cost sane) — for things like a habit not " +
                "logged today, a likely-missed medicine, low battery with the flashlight left on, or " +
                "spending running ahead of last month's pace. It only ever notifies or acts on things " +
                "already visible in your own local data.",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable proactive suggestions", modifier = Modifier.weight(1f))
            Switch(
                checked = proactiveEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        container.assistantSettingsStore.setProactiveEnabled(enabled)
                        if (enabled) {
                            if (PermissionManager.NOTIFICATIONS.isNotEmpty() && !PermissionManager.hasAll(context, PermissionManager.NOTIFICATIONS)) {
                                multiPermissionLauncher.launch(PermissionManager.NOTIFICATIONS)
                            }
                            ProactiveAgentWorker.schedule(context)
                        } else {
                            ProactiveAgentWorker.cancel(context)
                        }
                    }
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto-apply safe suggestions (e.g. turn off a flashlight left on)", modifier = Modifier.weight(1f))
            Switch(
                checked = autoApplySafeActions,
                enabled = proactiveEnabled,
                onCheckedChange = { enabled -> scope.launch { container.assistantSettingsStore.setAutoApplySafeActionsEnabled(enabled) } }
            )
        }

        Divider()
        Button(onClick = { showEraseConfirm = true }) {
            Text("Erase all local data")
        }
    }

    if (showEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseConfirm = false },
            title = { Text("Erase all data?") },
            text = { Text("This permanently deletes every expense, food log, medicine, weight, habit, task, chat message, and cached system record on this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    container.eraseAllLocalData()
                    exitProcess(0)
                }) { Text("Erase") }
            },
            dismissButton = {
                TextButton(onClick = { showEraseConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun status(granted: Boolean) = if (granted) "granted" else "not granted"

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

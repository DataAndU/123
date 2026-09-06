package com.gemmaassistant.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
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
import com.gemmaassistant.app.assistant.GemmaVoiceInteractionService
import com.gemmaassistant.app.assistant.WakeWordService
import com.gemmaassistant.app.control.AgentAccessibilityService
import com.gemmaassistant.app.core.AppContainer
import com.gemmaassistant.app.system.PermissionManager
import com.gemmaassistant.app.system.ProactiveAgentWorker
import com.gemmaassistant.app.util.TimeUtils
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

    var grantedFolders by remember { mutableStateOf(container.fileAccessManager.grantedRoots()) }
    val internetAccessEnabled by container.assistantSettingsStore.isInternetAccessEnabled.collectAsState(initial = false)
    var showInternetWarning by remember { mutableStateOf(false) }
    val recentActivity by container.agentActivityRepository.observeRecent().collectAsState(initial = emptyList())
    val isDefaultAssistant = remember(refreshTick) { GemmaVoiceInteractionService.isDefaultAssistant(context) }
    val isIgnoringBatteryOptimizations = remember(refreshTick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    val multiPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshTick++
    }

    val settingsReturnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
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

    val folderAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        container.fileAccessManager.persistAccess(uri)
        grantedFolders = container.fileAccessManager.grantedRoots()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Privacy & security", style = MaterialTheme.typography.titleLarge)
        Text(
            "• Every module except the AI agent's internet tool works with zero network access — " +
                "see \"Internet access\" below for exactly what that one exception is and how it's gated.\n" +
                "• All data is stored in a SQLCipher-encrypted database, keyed by a passphrase " +
                "generated once and sealed with the Android Keystore.\n" +
                "• Cloud/device-transfer backup of app data is explicitly disabled.\n" +
                "• Every optional capability (calls, mic, phone control, file access, internet access) " +
                "is off until you grant it, and revocable at any time.",
            style = MaterialTheme.typography.bodyMedium
        )

        Divider()
        Text("Phone & App Control", style = MaterialTheme.typography.titleLarge)
        Text(
            "This is the part of Gemma Assistant that can act on the rest of your phone: open apps, " +
                "call/text people, read and tap what's on screen, and flip system toggles. Every piece " +
                "is opt-in and independently revocable.",
            style = MaterialTheme.typography.bodyMedium
        )

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

        Text("Accessibility Service (read screen, tap, navigate, screenshot, WiFi/Bluetooth toggle): ${status(AgentAccessibilityService.isEnabled(context))}")
        Text(
            "Android requires enabling this manually — it's the most powerful permission on the " +
                "platform, so no app is allowed to turn it on for itself.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = { AgentAccessibilityService.openAccessibilitySettings(context) }) {
            Text("Open Accessibility settings")
        }

        Divider()
        Text("Default assistant app", style = MaterialTheme.typography.titleLarge)
        Text(
            "Android has a dedicated slot for this — Settings → Apps → Default apps → Digital " +
                "assistant app — the same one Google Assistant/Gemini occupies by default. Setting " +
                "Gemma Assistant there makes the system assist gesture (long-press the home button, " +
                "or swipe in from a bottom corner on gesture navigation) open this app's chat directly, " +
                "from anywhere, the way ChatGPT/Claude/Gemini's own apps can when picked as your " +
                "assistant. Some phone makers (Samsung, Xiaomi, and others with their own built-in " +
                "assistant) hide, rename, or restrict this system screen — if it doesn't show Gemma " +
                "Assistant as an option on your phone, that's an OEM restriction, not a bug here.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Default assistant: ${if (isDefaultAssistant) "Gemma Assistant" else "not set to Gemma Assistant"}")
        Button(onClick = {
            try {
                settingsReturnLauncher.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e: Exception) {
                settingsReturnLauncher.launch(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            }
        }) { Text("Open Default apps settings") }

        Divider()
        Text("Run in background", style = MaterialTheme.typography.titleLarge)
        Text(
            "Android aggressively kills background apps to save battery unless you exempt one from " +
                "battery optimization. Without this, wake-word listening (below) can get silently " +
                "stopped by the system after a while, especially with the screen off.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Battery optimization: ${if (isIgnoringBatteryOptimizations) "Gemma Assistant is exempt (recommended)" else "Gemma Assistant is restricted"}")
        if (!isIgnoringBatteryOptimizations) {
            Button(onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                settingsReturnLauncher.launch(intent)
            }) { Text("Allow to run unrestricted in background") }
        }

        Divider()
        Text("Wake word", style = MaterialTheme.typography.titleLarge)
        Text(
            "When on, Gemma Assistant listens in the background for you to say \"Gemma\" and then your " +
                "command — no cloud wake-word engine, just Android's own on-device speech recognizer " +
                "restarted in a loop, so no new network permission is added for this. Trade-off: it's " +
                "less battery-efficient than a dedicated wake-word chip/engine, and there's a brief " +
                "gap between listening cycles. A persistent notification always shows while it's on, " +
                "with a one-tap Stop — this is also what keeps Gemma Assistant actually running in the " +
                "background rather than just installed.",
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
        Text("Local AI model", style = MaterialTheme.typography.titleLarge)
        Text(
            "Without a model loaded, the assistant uses fast, zero-setup pattern matching for basic " +
                "phone control. Import a compatible local model file (a \".task\" bundle from Google's " +
                "LiteRT/MediaPipe model conversion tooling — Gemma 3n is what this app is built and " +
                "tested against) to upgrade to genuinely understanding free-form requests, chaining " +
                "multiple actions per request, and reaching the file/internet tools below — all " +
                "entirely on-device. Gemma Assistant has no download feature for models — you find/" +
                "convert one yourself and import it here. Expect a file anywhere from several hundred " +
                "MB to a few GB, real RAM/storage use once loaded, and that this only works on a real " +
                "arm64 device.",
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
        Text("Agent file access", style = MaterialTheme.typography.titleLarge)
        Text(
            "Only reachable through the local AI model above. Once granted, the agent can freely " +
                "list/search/read anything under the folder(s) you pick — no prompt per read, since " +
                "that's what read access means here. Writing or deleting a file always shows you " +
                "exactly what and asks Allow/Deny first, every time, with no way to turn that off. " +
                "Uses Android's own folder picker (Storage Access Framework) rather than the intrusive " +
                "\"all files\" special permission — pick the top-level storage volume in the picker " +
                "for the broadest access.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (grantedFolders.isEmpty()) {
            Text("No folder access granted yet.", style = MaterialTheme.typography.labelSmall)
        } else {
            grantedFolders.forEach { uri ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(uri.lastPathSegment ?: uri.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        container.fileAccessManager.revokeAccess(uri)
                        grantedFolders = container.fileAccessManager.grantedRoots()
                    }) { Text("Revoke") }
                }
            }
        }
        Button(onClick = { folderAccessLauncher.launch(null) }) { Text("Grant folder access") }

        Divider()
        Text("Internet access (agent)", style = MaterialTheme.typography.titleLarge)
        Text(
            "Off by default, and the ONLY thing in this entire app that ever uses the network. Also " +
                "only reachable through the local AI model. Unlike camera/mic/SMS, Android grants the " +
                "INTERNET permission silently at install with no popup of its own — this switch, and " +
                "the warning below, are the only real gate. GET requests (reading a page/API) run " +
                "immediately once this is on; anything that sends or changes data (POST/PUT/DELETE) " +
                "still asks Allow/Deny first, same as file writes. Every request is recorded in " +
                "\"Recent agent activity\" below.",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable internet access", modifier = Modifier.weight(1f))
            Switch(
                checked = internetAccessEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) showInternetWarning = true
                    else scope.launch { container.assistantSettingsStore.setInternetAccessEnabled(false) }
                }
            )
        }

        Divider()
        Text("Recent agent activity", style = MaterialTheme.typography.titleLarge)
        if (recentActivity.isEmpty()) {
            Text("Nothing yet.", style = MaterialTheme.typography.labelSmall)
        } else {
            recentActivity.take(15).forEach { entry ->
                Text(
                    "${TimeUtils.formatDateTime(entry.timestampMillis)} — ${entry.kind}: ${entry.target} (${entry.detail})",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Divider()
        Text("Proactive suggestions", style = MaterialTheme.typography.titleLarge)
        Text(
            "Off by default. When on, a background check runs roughly hourly — cheap, rule-based " +
                "(never the local model, to keep battery cost sane) — for things like low battery with " +
                "the flashlight left on, or storage running low.",
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

    if (showInternetWarning) {
        AlertDialog(
            onDismissRequest = { showInternetWarning = false },
            title = { Text("Turn on internet access?") },
            text = {
                Text(
                    "This is the only feature in Gemma Assistant that ever sends or receives data " +
                        "over the network. The local AI model will be able to fetch web pages/APIs " +
                        "freely when you ask it to — including content it might read from a file or " +
                        "another page, which could try to trick it into fetching something you didn't " +
                        "intend. Actions that change something remote (not a plain read) still ask you " +
                        "first, and every request is logged below. Turn this on only if you understand " +
                        "and accept that trade-off."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.assistantSettingsStore.setInternetAccessEnabled(true) }
                    showInternetWarning = false
                }) { Text("I understand, enable it") }
            },
            dismissButton = {
                TextButton(onClick = { showInternetWarning = false }) { Text("Cancel") }
            }
        )
    }

    if (showEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseConfirm = false },
            title = { Text("Erase all data?") },
            text = { Text("This permanently deletes every chat message and cached agent activity record on this device. This cannot be undone.") },
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

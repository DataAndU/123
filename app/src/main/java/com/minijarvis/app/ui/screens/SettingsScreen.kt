package com.minijarvis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.system.PermissionManager
import kotlin.system.exitProcess

@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    var showEraseConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Privacy & security", style = MaterialTheme.typography.titleLarge)
        Text(
            "• No INTERNET permission is declared anywhere in this app.\n" +
                "• All data is stored in a SQLCipher-encrypted database, keyed by a passphrase " +
                "generated once and sealed with the Android Keystore.\n" +
                "• Cloud/device-transfer backup of app data is explicitly disabled.\n" +
                "• Every optional module (calls, location, usage, music, camera, mic) is off " +
                "until you grant its permission, and can be revoked at any time in system Settings.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text("Permission status", style = MaterialTheme.typography.titleMedium)
        Text("Microphone: ${if (PermissionManager.hasAll(context, PermissionManager.VOICE)) "granted" else "not granted"}")
        Text("Camera: ${if (PermissionManager.hasAll(context, PermissionManager.CAMERA)) "granted" else "not granted"}")
        Text("Call log: ${if (PermissionManager.hasAll(context, PermissionManager.CALL_LOG)) "granted" else "not granted"}")
        Text("Location: ${if (PermissionManager.hasAll(context, PermissionManager.LOCATION)) "granted" else "not granted"}")
        Text("Usage access: ${if (PermissionManager.hasUsageAccess(context)) "granted" else "not granted"}")
        Text("Notification listener (music): ${if (PermissionManager.hasNotificationListenerAccess(context)) "granted" else "not granted"}")

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

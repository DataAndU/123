package com.minijarvis.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.system.PermissionManager
import com.minijarvis.app.util.TimeUtils
import kotlinx.coroutines.launch

@Composable
fun SystemDataScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCallLogPermission by remember { mutableStateOf(PermissionManager.hasAll(context, PermissionManager.CALL_LOG)) }
    var hasLocationPermission by remember { mutableStateOf(PermissionManager.hasAll(context, PermissionManager.LOCATION)) }
    var hasUsageAccess by remember { mutableStateOf(PermissionManager.hasUsageAccess(context)) }
    var hasNotificationListener by remember { mutableStateOf(PermissionManager.hasNotificationListenerAccess(context)) }

    val callLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCallLogPermission = granted
        if (granted) scope.launch { container.callLogRepository.replaceAll(container.callLogHelper.readRecent()) }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
    }

    val calls by container.callLogRepository.observeRecent().collectAsState(initial = emptyList())
    val visits by container.locationRepository.observeRecent().collectAsState(initial = emptyList())
    val usage by container.appUsageRepository.observeForDay().collectAsState(initial = emptyList())
    val musicHistory by container.musicHistoryRepository.observeRecent().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Every module below is optional, off by default, and reads only from this device.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item { SectionHeader("Call history") }
        item {
            if (!hasCallLogPermission) {
                Button(onClick = { callLogLauncher.launch(android.Manifest.permission.READ_CALL_LOG) }) {
                    Text("Grant call log access")
                }
            } else {
                Button(onClick = { scope.launch { container.callLogRepository.replaceAll(container.callLogHelper.readRecent()) } }) {
                    Text("Sync call log")
                }
            }
        }
        items(calls, key = { it.id }) { call ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text("${call.displayName ?: call.number} — ${call.type}")
                    Text(TimeUtils.formatDateTime(call.timestampMillis), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { HorizontalDivider() }
        item { SectionHeader("Location visits") }
        item {
            if (!hasLocationPermission) {
                Button(onClick = { locationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text("Grant location access")
                }
            } else {
                Button(onClick = {
                    scope.launch {
                        val location = container.locationHelper.getCurrentLocation()
                        if (location != null) {
                            container.locationRepository.logVisit(location.first, location.second, label = null)
                        }
                    }
                }) { Text("Log current location") }
            }
        }
        items(visits, key = { it.id }) { visit ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text("${visit.label ?: "Visit"}: %.5f, %.5f".format(visit.latitude, visit.longitude))
                    Text(TimeUtils.formatDateTime(visit.timestampMillis), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { HorizontalDivider() }
        item { SectionHeader("App usage today") }
        item {
            if (!hasUsageAccess) {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                    Text("Grant usage access in Settings")
                }
            } else {
                Button(onClick = {
                    scope.launch {
                        container.appUsageRepository.replaceForDay(TimeUtils.toEpochDay(), container.appUsageHelper.collectForToday())
                        hasUsageAccess = PermissionManager.hasUsageAccess(context)
                    }
                }) { Text("Refresh usage stats") }
            }
        }
        items(usage, key = { it.id }) { app ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text(app.appLabel)
                    Text("${app.totalTimeMillis / 60000} min", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { HorizontalDivider() }
        item { SectionHeader("Music history") }
        item {
            if (!hasNotificationListener) {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                    Text("Grant notification access to track now-playing")
                }
            } else {
                Text("Listening for now-playing media on this device.", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(musicHistory, key = { it.id }) { play ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text("${play.title}${play.artist?.let { " — $it" } ?: ""}")
                    Text(TimeUtils.formatDateTime(play.timestampMillis), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

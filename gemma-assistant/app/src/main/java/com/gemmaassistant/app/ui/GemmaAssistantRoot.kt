package com.gemmaassistant.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gemmaassistant.app.core.AppContainer
import com.gemmaassistant.app.ui.screens.ChatScreen
import com.gemmaassistant.app.ui.screens.SettingsScreen

private const val ROUTE_CHAT = "chat"
private const val ROUTE_SETTINGS = "settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemmaAssistantRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onSettings = backStackEntry?.destination?.route == ROUTE_SETTINGS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (onSettings) "Settings" else "Gemma Assistant") },
                navigationIcon = {
                    if (onSettings) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!onSettings) {
                        IconButton(onClick = { navController.navigate(ROUTE_SETTINGS) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = ROUTE_CHAT, modifier = Modifier.padding(padding)) {
            composable(ROUTE_CHAT) { ChatScreen(container) }
            composable(ROUTE_SETTINGS) { SettingsScreen(container) }
        }
    }
}

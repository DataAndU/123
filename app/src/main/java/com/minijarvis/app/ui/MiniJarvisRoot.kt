package com.minijarvis.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.ui.screens.CameraScreen
import com.minijarvis.app.ui.screens.ChatScreen
import com.minijarvis.app.ui.screens.DashboardScreen
import com.minijarvis.app.ui.screens.ExpenseScreen
import com.minijarvis.app.ui.screens.FoodScreen
import com.minijarvis.app.ui.screens.HabitsScreen
import com.minijarvis.app.ui.screens.MedicineScreen
import com.minijarvis.app.ui.screens.ReportsScreen
import com.minijarvis.app.ui.screens.SearchScreen
import com.minijarvis.app.ui.screens.SettingsScreen
import com.minijarvis.app.ui.screens.SystemDataScreen
import com.minijarvis.app.ui.screens.TasksScreen
import com.minijarvis.app.ui.screens.WeightScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniJarvisRoot(container: AppContainer) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentLabel = Destinations.drawerItems.firstOrNull { it.route == currentRoute }?.label ?: "Mini JARVIS"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Mini JARVIS", modifier = Modifier.padding(16.dp))
                Destinations.drawerItems.forEach { destination ->
                    NavigationDrawerItem(
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                        selected = destination.route == currentRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                                popUpTo(Destinations.DASHBOARD.route) { saveState = true }
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentLabel) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destinations.DASHBOARD.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Destinations.DASHBOARD.route) { DashboardScreen(container, navController) }
                composable(Destinations.CHAT.route) { ChatScreen(container) }
                composable(Destinations.CAMERA.route) { CameraScreen(container) }
                composable(Destinations.EXPENSES.route) { ExpenseScreen(container) }
                composable(Destinations.FOOD.route) { FoodScreen(container) }
                composable(Destinations.MEDICINE.route) { MedicineScreen(container) }
                composable(Destinations.WEIGHT.route) { WeightScreen(container) }
                composable(Destinations.HABITS.route) { HabitsScreen(container) }
                composable(Destinations.TASKS.route) { TasksScreen(container) }
                composable(Destinations.SYSTEM_DATA.route) { SystemDataScreen(container) }
                composable(Destinations.REPORTS.route) { ReportsScreen(container) }
                composable(Destinations.SEARCH.route) { SearchScreen(container) }
                composable(Destinations.SETTINGS.route) { SettingsScreen(container) }
            }
        }
    }
}

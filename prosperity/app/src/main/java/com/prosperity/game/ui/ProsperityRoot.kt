package com.prosperity.game.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prosperity.game.engine.player.FinanceEngine
import com.prosperity.game.ui.components.EventDialog
import com.prosperity.game.ui.components.formatMoneyShort
import com.prosperity.game.ui.screens.BankScreen
import com.prosperity.game.ui.screens.BusinessesScreen
import com.prosperity.game.ui.screens.CareerScreen
import com.prosperity.game.ui.screens.DashboardScreen
import com.prosperity.game.ui.screens.MarketsScreen
import com.prosperity.game.ui.screens.ReportsScreen
import com.prosperity.game.ui.screens.SaveLoadScreen
import com.prosperity.game.ui.screens.StartScreen

private const val ROUTE_START = "start"
private const val ROUTE_SAVELOAD = "saveload"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProsperityRoot(viewModel: GameViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val inGame = uiState.game != null && currentRoute != ROUTE_START
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (inGame) {
                TopAppBar(
                    title = {
                        val netWorth = uiState.game?.let { FinanceEngine.netWorth(it.player, it.markets) } ?: 0.0
                        Text("Net Worth: ${formatMoneyShort(netWorth)}")
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(ROUTE_SAVELOAD) }) {
                            Icon(Icons.Filled.Save, contentDescription = "Save & Load")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (inGame && currentRoute != ROUTE_SAVELOAD) {
                NavigationBar {
                    Destinations.bottomItems.forEach { destination ->
                        NavigationBarItem(
                            selected = destination.route == currentRoute,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_START,
            modifier = Modifier.padding(padding)
        ) {
            composable(ROUTE_START) {
                StartScreen(viewModel) {
                    navController.navigate(Destinations.DASHBOARD.route) { popUpTo(ROUTE_START) { inclusive = true } }
                }
            }
            composable(Destinations.DASHBOARD.route) { DashboardScreen(viewModel) }
            composable(Destinations.MARKETS.route) { MarketsScreen(viewModel) }
            composable(Destinations.BUSINESSES.route) { BusinessesScreen(viewModel) }
            composable(Destinations.CAREER.route) { CareerScreen(viewModel) }
            composable(Destinations.BANK.route) { BankScreen(viewModel) }
            composable(Destinations.REPORTS.route) { ReportsScreen(viewModel) }
            composable(ROUTE_SAVELOAD) {
                SaveLoadScreen(viewModel) {
                    navController.navigate(Destinations.DASHBOARD.route) { popUpTo(ROUTE_START) { inclusive = true } }
                }
            }
        }
    }

    uiState.pendingEvent?.let { event ->
        EventDialog(event = event, onChoose = { optionId -> viewModel.resolveEvent(optionId) })
    }

    uiState.lastSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSummary() },
            title = { Text("Month ${summary.month} Recap") },
            text = {
                Text(
                    "Income: ${formatMoneyShort(summary.income.total)}\n" +
                        "Expenses: ${formatMoneyShort(summary.expenses.total)}\n" +
                        "Net worth: ${formatMoneyShort(summary.netWorth)} (${if (summary.netWorthChange >= 0) "+" else ""}${formatMoneyShort(summary.netWorthChange)})" +
                        (if (summary.phaseChanged) "\n\nThe economy has entered a new phase!" else "") +
                        (if (summary.businessesClosedFromBankruptcy.isNotEmpty()) "\n\nClosed due to bankruptcy: ${summary.businessesClosedFromBankruptcy.joinToString()}" else "")
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.dismissSummary() }) { Text("Continue") } }
        )
    }
}

package com.prosperity.game.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prosperity.game.ui.components.formatMoneyShort
import com.prosperity.game.ui.screens.AuthScreen
import com.prosperity.game.ui.screens.BankScreen
import com.prosperity.game.ui.screens.BusinessesScreen
import com.prosperity.game.ui.screens.CareerScreen
import com.prosperity.game.ui.screens.DashboardScreen
import com.prosperity.game.ui.screens.MarketplaceScreen
import com.prosperity.game.ui.screens.MarketsScreen
import com.prosperity.game.ui.screens.SocialScreen
import com.prosperity.game.ui.screens.WalletScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProsperityRoot(viewModel: OnlineViewModel, onBuyCoins: (String) -> Unit) {
    val authState by viewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    when (authState) {
        is AuthScreenState.CheckingSession -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        is AuthScreenState.LoggedOut -> AuthScreen(viewModel)
        is AuthScreenState.LoggedIn -> LoggedInRoot(viewModel, onBuyCoins, snackbarHostState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoggedInRoot(viewModel: OnlineViewModel, onBuyCoins: (String) -> Unit, snackbarHostState: SnackbarHostState) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val uiState by viewModel.uiState.collectAsState()

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Prosperity Online", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                Destinations.all.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        selected = destination.route == currentRoute,
                        icon = { Icon(destination.icon, contentDescription = null) },
                        onClick = {
                            scope.launch { drawerState.close() }
                            navigateTo(destination.route)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                Divider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Log out") },
                    selected = false,
                    icon = { Icon(Icons.Filled.Logout, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.logout()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Net Worth: ${formatMoneyShort(uiState.player?.netWorth ?: 0.0)}") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    Destinations.bottomItems.forEach { destination ->
                        NavigationBarItem(
                            selected = destination.route == currentRoute,
                            onClick = { navigateTo(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(navController = navController, startDestination = Destinations.DASHBOARD.route, modifier = Modifier.padding(padding)) {
                composable(Destinations.DASHBOARD.route) { DashboardScreen(viewModel) }
                composable(Destinations.MARKETS.route) { MarketsScreen(viewModel) }
                composable(Destinations.BUSINESS.route) { BusinessesScreen(viewModel) }
                composable(Destinations.CAREER.route) { CareerScreen(viewModel) }
                composable(Destinations.BANK.route) { BankScreen(viewModel) }
                composable(Destinations.SOCIAL.route) { SocialScreen(viewModel) }
                composable(Destinations.MARKETPLACE.route) { MarketplaceScreen(viewModel) }
                composable(Destinations.WALLET.route) { WalletScreen(viewModel, onBuyCoins) }
            }
        }
    }
}

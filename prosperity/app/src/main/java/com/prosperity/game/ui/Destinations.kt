package com.prosperity.game.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.graphics.vector.ImageVector

data class Destination(val route: String, val label: String, val icon: ImageVector)

object Destinations {
    val DASHBOARD = Destination("dashboard", "Home", Icons.Filled.Dashboard)
    val MARKETS = Destination("markets", "Markets", Icons.Filled.ShowChart)
    val BUSINESSES = Destination("businesses", "Business", Icons.Filled.Store)
    val CAREER = Destination("career", "Career", Icons.Filled.School)
    val BANK = Destination("bank", "Bank", Icons.Filled.AccountBalance)
    val REPORTS = Destination("reports", "Reports", Icons.Filled.Insights)

    val bottomItems = listOf(DASHBOARD, MARKETS, BUSINESSES, CAREER, BANK, REPORTS)
}

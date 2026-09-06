package com.prosperity.game.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.graphics.vector.ImageVector

data class Destination(val route: String, val label: String, val icon: ImageVector)

object Destinations {
    val DASHBOARD = Destination("dashboard", "Dashboard", Icons.Filled.Dashboard)
    val MARKETS = Destination("markets", "Markets", Icons.Filled.ShowChart)
    val BUSINESS = Destination("business", "Business", Icons.Filled.Store)
    val CAREER = Destination("career", "Career", Icons.Filled.School)
    val BANK = Destination("bank", "Bank", Icons.Filled.AccountBalance)
    val SOCIAL = Destination("social", "Social", Icons.Filled.Groups)
    val MARKETPLACE = Destination("marketplace", "Marketplace", Icons.Filled.Storefront)
    val WALLET = Destination("wallet", "Wallet", Icons.Filled.AccountBalanceWallet)

    val all = listOf(DASHBOARD, MARKETS, BUSINESS, CAREER, BANK, SOCIAL, MARKETPLACE, WALLET)
    val bottomItems = listOf(DASHBOARD, MARKETS, BUSINESS, SOCIAL)
}

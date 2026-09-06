package com.minijarvis.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector

data class Destination(val route: String, val label: String, val icon: ImageVector)

object Destinations {
    val DASHBOARD = Destination("dashboard", "Dashboard", Icons.Filled.Dashboard)
    val CHAT = Destination("chat", "Chat", Icons.Filled.Chat)
    val CAMERA = Destination("camera", "Vision", Icons.Filled.CameraAlt)
    val EXPENSES = Destination("expenses", "Expenses", Icons.Filled.AttachMoney)
    val FOOD = Destination("food", "Food", Icons.Filled.Fastfood)
    val MEDICINE = Destination("medicine", "Medicine", Icons.Filled.LocalHospital)
    val WEIGHT = Destination("weight", "Weight & Health", Icons.Filled.MonitorHeart)
    val HABITS = Destination("habits", "Habits", Icons.Filled.TrackChanges)
    val TASKS = Destination("tasks", "Tasks & Reminders", Icons.Filled.CheckCircle)
    val SYSTEM_DATA = Destination("system_data", "Calls, Location & Usage", Icons.Filled.Phone)
    val REPORTS = Destination("reports", "Reports", Icons.Filled.Insights)
    val SEARCH = Destination("search", "Smart Search", Icons.Filled.Search)
    val SETTINGS = Destination("settings", "Settings & Privacy", Icons.Filled.Settings)

    val drawerItems = listOf(
        DASHBOARD, CHAT, CAMERA, EXPENSES, FOOD, MEDICINE, WEIGHT,
        HABITS, TASKS, SYSTEM_DATA, REPORTS, SEARCH, SETTINGS
    )
}

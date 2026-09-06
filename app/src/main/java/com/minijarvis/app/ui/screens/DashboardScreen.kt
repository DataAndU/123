package com.minijarvis.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.reports.PeriodReport
import com.minijarvis.app.reports.ReportPeriod
import com.minijarvis.app.ui.Destinations

@Composable
fun DashboardScreen(container: AppContainer, navController: NavHostController) {
    var report by remember { mutableStateOf<PeriodReport?>(null) }

    LaunchedEffect(Unit) {
        report = container.reportGenerator.generate(ReportPeriod.DAILY)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Everything below lives only on this device — no cloud, no backend.")
        }

        item {
            val today = report
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Today")
                    if (today == null) {
                        Text("Loading…")
                    } else {
                        Text("Spent: ₹${"%.2f".format(today.totalSpent)}")
                        Text("Calories logged: ${today.totalCaloriesLogged}")
                        Text("Medicine doses taken: ${today.medicineDosesTaken}")
                        Text("Habits completed: ${today.habitsCompleted}/${today.habitsTracked}")
                        Text("Tasks completed: ${today.tasksCompleted}/${today.tasksDue}")
                        today.latestWeightKg?.let { Text("Latest weight: $it kg") }
                    }
                }
            }
        }

        items(Destinations.drawerItems.filter { it != Destinations.DASHBOARD }) { destination ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clickable { navController.navigate(destination.route) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(destination.label)
                }
            }
        }
    }
}

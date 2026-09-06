package com.minijarvis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minijarvis.app.core.AppContainer
import com.minijarvis.app.reports.PeriodReport
import com.minijarvis.app.reports.ReportPeriod
import com.minijarvis.app.util.TimeUtils

@Composable
fun ReportsScreen(container: AppContainer) {
    var period by remember { mutableStateOf(ReportPeriod.DAILY) }
    var report by remember { mutableStateOf<PeriodReport?>(null) }

    LaunchedEffect(period) {
        report = container.reportGenerator.generate(period)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Every figure below is computed locally from your on-device data.", style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportPeriod.values().forEach { p ->
                Button(onClick = { period = p }) { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
        }

        val current = report
        if (current == null) {
            Text("Loading…")
        } else {
            Card(modifier = Modifier.fillMaxSize()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${TimeUtils.formatDate(current.rangeStartMillis)} – ${TimeUtils.formatDate(current.rangeEndMillis)}")
                    Text("Total spent: ₹${"%.2f".format(current.totalSpent)}")
                    Text("Calories logged: ${current.totalCaloriesLogged}")
                    Text("Medicine doses — taken: ${current.medicineDosesTaken}, missed: ${current.medicineDosesMissed}")
                    current.latestWeightKg?.let { Text("Latest weight: $it kg") }
                    Text("Habits completed: ${current.habitsCompleted}/${current.habitsTracked}")
                    Text("Tasks completed: ${current.tasksCompleted}/${current.tasksDue}")
                }
            }
        }
    }
}

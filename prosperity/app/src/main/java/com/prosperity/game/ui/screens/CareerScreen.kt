package com.prosperity.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.network.dto.EDUCATION_ORDER
import com.prosperity.game.network.dto.SKILL_TYPES
import com.prosperity.game.ui.OnlineViewModel
import com.prosperity.game.ui.components.SectionHeader
import com.prosperity.game.ui.components.formatMoney

@Composable
fun CareerScreen(viewModel: OnlineViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val player = uiState.player

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Career", style = MaterialTheme.typography.headlineSmall)
            player?.currentJobId?.let { jobId ->
                val job = uiState.jobs.firstOrNull { it.id == jobId }
                Text("Current job: ${job?.title ?: jobId} (${player.jobMonthsHeld} months)")
            } ?: Text("You're currently unemployed.")
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Education", infoTerm = "Opportunity Cost")
                    Text("Level: ${player?.educationLevel ?: "NONE"}")
                    player?.educationInProgressId?.let {
                        Text("Studying — ${player.educationMonthsRemaining} months remaining", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (player?.educationInProgressId == null) {
            items(uiState.educationPrograms) { program ->
                val alreadyQualified = EDUCATION_ORDER.indexOf(program.grantsLevel) <= EDUCATION_ORDER.indexOf(player?.educationLevel ?: "NONE")
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(program.title, style = MaterialTheme.typography.titleSmall)
                            Text("${formatMoney(program.tuitionCost)} total • ${program.durationMonths} months • ${formatMoney(program.tuitionCost * 0.2)} upfront", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(onClick = { viewModel.enrollInEducation(program.id) }, enabled = !alreadyQualified) {
                            Text(if (alreadyQualified) "Owned" else "Enroll")
                        }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Skills")
                    SKILL_TYPES.forEach { skill ->
                        val level = player?.skills?.get(skill) ?: 0
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$skill: $level/100")
                            OutlinedButton(onClick = { viewModel.trainSkill(skill, 50.0) }) {
                                Text("Train ($${"%.0f".format(50.0)})")
                            }
                        }
                    }
                }
            }
        }

        item { Text("Available Jobs", style = MaterialTheme.typography.titleMedium) }
        items(uiState.jobs) { job ->
            val isCurrent = job.id == player?.currentJobId
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(job.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Tier ${job.tier} • ${formatMoney(job.baseSalary)}/mo • Requires ${job.requiredEducation}" +
                            (job.requiredSkill?.let { " + $it ${job.requiredSkillLevel}+" } ?: ""),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(Modifier.padding(top = 8.dp)) {
                        if (isCurrent) {
                            OutlinedButton(onClick = { viewModel.quitJob() }) { Text("Quit") }
                        } else {
                            Button(onClick = { viewModel.applyForJob(job.id) }) { Text("Apply") }
                        }
                    }
                }
            }
        }
    }
}

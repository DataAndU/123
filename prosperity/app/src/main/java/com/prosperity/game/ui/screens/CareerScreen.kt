package com.prosperity.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prosperity.game.engine.player.EducationCatalog
import com.prosperity.game.engine.player.JobCatalog
import com.prosperity.game.engine.player.PlayerActions
import com.prosperity.game.engine.player.SkillType
import com.prosperity.game.ui.GameViewModel
import com.prosperity.game.ui.components.SectionHeader
import com.prosperity.game.ui.components.formatMoney

@Composable
fun CareerScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game ?: return
    val currentJob = JobCatalog.byId(game.player.currentJobId)

    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Current job: ${currentJob?.title ?: "Unemployed"}", style = MaterialTheme.typography.titleMedium)
                    currentJob?.let {
                        Text("Base salary: ${formatMoney(it.baseSalary)}/mo • ${game.player.jobMonthsHeld} months held")
                        Button(onClick = { viewModel.applyResult(PlayerActions.quitJob(game.player)) }) { Text("Quit") }
                    }
                    Text("Education: ${game.player.educationLevel.displayName}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item { SectionHeader("Available Jobs") }
        items(JobCatalog.jobs) { job ->
            val skillsByType = SkillType.entries.associateWith { game.player.skill(it) }
            val eligible = JobCatalog.isEligible(job, game.player.educationLevel, skillsByType)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(job.title, style = MaterialTheme.typography.titleSmall)
                    Text("${formatMoney(job.baseSalary)}/mo • requires ${job.requiredEducation.displayName}" + (job.requiredSkill?.let { " + $it ${job.requiredSkillLevel}" } ?: ""))
                    Button(enabled = eligible && job.id != game.player.currentJobId, onClick = { viewModel.applyResult(PlayerActions.applyForJob(game.player, job.id)) }) {
                        Text(if (job.id == game.player.currentJobId) "Current job" else if (eligible) "Apply" else "Locked")
                    }
                }
            }
        }

        item { SectionHeader("Skills", infoTerm = "Opportunity Cost") }
        items(SkillType.entries) { skill ->
            val level = game.player.skill(skill)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("$skill: $level/100")
                    LinearProgressIndicator(progress = { level / 100f }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { viewModel.applyResult(PlayerActions.trainSkill(game.player, skill, 300.0)) }) { Text("Train ($300)") }
                }
            }
        }

        item { SectionHeader("Education", infoTerm = "Compound Interest") }
        if (game.player.educationInProgressId != null) {
            item { Text("Studying: ${EducationCatalog.byId(game.player.educationInProgressId)?.title} — ${game.player.educationMonthsRemaining} months left") }
        } else {
            items(EducationCatalog.programs.filter { it.grantsLevel.ordinal > game.player.educationLevel.ordinal }) { program ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(program.title, style = MaterialTheme.typography.titleSmall)
                        Text("Tuition ${formatMoney(program.tuitionCost)} over ${program.durationMonths} months (20% due at enrollment)")
                        Button(onClick = { viewModel.applyResult(PlayerActions.enrollEducation(game.player, program.id)) }) { Text("Enroll") }
                    }
                }
            }
        }
    }
}

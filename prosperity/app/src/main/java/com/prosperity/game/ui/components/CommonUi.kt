package com.prosperity.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prosperity.game.ui.theme.NegativeRed
import com.prosperity.game.ui.theme.PositiveGreen

fun formatMoney(value: Double): String {
    val sign = if (value < 0) "-" else ""
    val abs = kotlin.math.abs(value)
    return "$sign$${"%,.2f".format(abs)}"
}

fun formatMoneyShort(value: Double): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        abs >= 1_000_000_000 -> "$sign$${"%.2f".format(abs / 1_000_000_000)}B"
        abs >= 1_000_000 -> "$sign$${"%.2f".format(abs / 1_000_000)}M"
        abs >= 1_000 -> "$sign$${"%.1f".format(abs / 1_000)}k"
        else -> "$sign$${"%.0f".format(abs)}"
    }
}

fun changeColor(value: Double): Color = if (value >= 0) PositiveGreen else NegativeRed

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, infoTerm: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        infoTerm?.let { InfoTip(it) }
    }
}

/** A small (i) button that pops open a plain-English explanation of an economics term. */
@Composable
fun InfoTip(term: String) {
    var showDialog by remember { mutableStateOf(false) }
    IconButton(onClick = { showDialog = true }) {
        Icon(Icons.Filled.Info, contentDescription = "What is $term?", tint = MaterialTheme.colorScheme.secondary)
    }
    if (showDialog) {
        val explanation = EconomicsDictionary.explanations[term] ?: "No explanation available."
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(term) },
            text = { Text(explanation) },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Got it") } }
        )
    }
}

object EconomicsDictionary {
    val explanations: Map<String, String> = mapOf(
        "Inflation" to "Inflation is a general rise in prices over time, which reduces how much your money can buy. Moderate inflation is normal; very high inflation erodes savings quickly.",
        "GDP Growth" to "Gross Domestic Product (GDP) measures the total value of goods and services produced. Its growth rate shows whether the economy is expanding or shrinking.",
        "Interest Rate" to "The interest rate is the cost of borrowing money, set largely by the central bank. Higher rates cool spending and investment; lower rates encourage them.",
        "Unemployment" to "The unemployment rate is the share of the workforce without a job but actively looking for one. It usually rises in a recession and falls in an expansion.",
        "Recession" to "A recession is a sustained period of falling economic output, usually accompanied by rising unemployment and falling confidence.",
        "Consumer Confidence" to "Consumer confidence measures how optimistic people feel about the economy. Confident consumers spend more, which itself helps drive growth.",
        "Opportunity Cost" to "Opportunity cost is the value of the next-best alternative you give up when you make a choice — every decision, including doing nothing, has one.",
        "Diversification" to "Diversification means spreading money across different assets so that no single bad outcome can sink your whole portfolio.",
        "Risk vs. Return" to "Riskier assets (like stocks) tend to offer higher potential returns than safer ones (like government bonds) to compensate investors for uncertainty — but losses can be larger too.",
        "Compound Interest" to "Compound interest is interest earned on both your original money and on interest you've already accumulated — the reason long-term investing rewards patience.",
        "Monetary Policy" to "Monetary policy is how a central bank manages interest rates and the money supply to influence inflation and growth.",
        "Fiscal Policy" to "Fiscal policy is how a government uses spending and taxation to influence the economy — for example, stimulus payments during a recession.",
        "Supply and Demand" to "Prices rise when demand for something exceeds its supply, and fall when supply exceeds demand — the most basic force driving markets.",
        "Bond Yield" to "A bond's yield reflects the interest it pays relative to its price. When interest rates rise, existing bond prices tend to fall, since new bonds pay more.",
        "Net Worth" to "Net worth is everything you own (cash, investments, property, businesses) minus everything you owe (loans, mortgages). It's the scoreboard of this game."
    )
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

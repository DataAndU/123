package com.prosperity.game.engine.economy

import com.prosperity.game.engine.economy.EconomyState.Companion.INFLATION_TARGET
import com.prosperity.game.engine.economy.EconomyState.Companion.NATURAL_GROWTH
import com.prosperity.game.engine.economy.EconomyState.Companion.NATURAL_UNEMPLOYMENT
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Advances the macro-economy by one month using a simplified but internally
 * consistent set of textbook relationships:
 *  - a business-cycle phase machine that biases growth,
 *  - Okun's law (growth surprises move unemployment),
 *  - a demand-pull + money-supply inflation model with mean reversion,
 *  - a Taylor-rule-like central bank reaction function for interest rates.
 *
 * Noise is deliberately kept smaller than the structural signal so a player
 * who reads the trend (phase, inflation direction, rate direction) has a
 * real edge — but never a certainty.
 */
object EconomySimulator {

    fun advance(state: EconomyState, rng: Random, shock: EconomyShock = EconomyShock()): EconomyState {
        val (nextPhase, nextPhaseMonths) = advancePhase(state, rng, shock)

        val phaseBaseGrowth = when (nextPhase) {
            BusinessCyclePhase.EXPANSION -> 3.2
            BusinessCyclePhase.PEAK -> 1.0
            BusinessCyclePhase.RECESSION -> -2.0
            BusinessCyclePhase.TROUGH -> -0.3
        }
        // High real rates cool investment and consumption; this is the main lever
        // the player can "read" from interest-rate trends.
        val rateDrag = (state.interestRate - INFLATION_TARGET - 1.0) * 0.18
        val growthNoise = rng.nextGaussian(0.0, 0.9)
        var gdpGrowth = phaseBaseGrowth - rateDrag + growthNoise + shock.gdpGrowthDelta
        gdpGrowth = gdpGrowth.coerceIn(-8.0, 8.0)

        // Okun's law: unemployment moves opposite to the growth surprise vs potential.
        val growthSurprise = gdpGrowth - NATURAL_GROWTH
        var unemployment = state.unemploymentRate - 0.22 * growthSurprise + rng.nextGaussian(0.0, 0.12) + shock.unemploymentDelta
        unemployment = unemployment.coerceIn(2.0, 25.0)

        // Inflation: demand-pull from the output gap, a money-supply term, and
        // mean reversion toward the central bank's target.
        val outputGap = gdpGrowth - NATURAL_GROWTH
        var inflation = state.inflationRate +
            0.10 * outputGap +
            0.05 * (state.moneySupplyGrowth - 4.0) -
            0.15 * (state.inflationRate - INFLATION_TARGET) +
            rng.nextGaussian(0.0, 0.25) +
            shock.inflationDelta
        inflation = inflation.coerceIn(-5.0, 40.0)

        // Central bank reacts to the inflation and unemployment gaps (Taylor rule),
        // but only moves gradually toward its target rate unless an event forces
        // an immediate shock.
        val targetRate = 2.0 + inflation + 1.5 * (inflation - INFLATION_TARGET) - 1.0 * (unemployment - NATURAL_UNEMPLOYMENT)
        var interestRate = state.interestRate + (targetRate - state.interestRate) * 0.25 + shock.interestRateDelta
        interestRate = interestRate.coerceIn(0.0, 30.0)

        var confidence = state.consumerConfidence +
            0.6 * growthSurprise -
            0.4 * (unemployment - NATURAL_UNEMPLOYMENT) -
            0.3 * (inflation - INFLATION_TARGET) +
            rng.nextGaussian(0.0, 1.5) +
            shock.confidenceDelta
        confidence = confidence.coerceIn(0.0, 100.0)

        val moneySupplyGrowth = (state.moneySupplyGrowth + rng.nextGaussian(0.0, 0.3)).coerceIn(-5.0, 15.0)
        val priceLevelIndex = state.priceLevelIndex * (1.0 + inflation / 100.0 / 12.0)

        return state.copy(
            month = state.month + 1,
            phase = nextPhase,
            phaseMonthsElapsed = nextPhaseMonths,
            gdpGrowthRate = gdpGrowth,
            inflationRate = inflation,
            unemploymentRate = unemployment,
            interestRate = interestRate,
            consumerConfidence = confidence,
            moneySupplyGrowth = moneySupplyGrowth,
            priceLevelIndex = priceLevelIndex,
            gdpHistory = state.gdpHistory.pushCapped(gdpGrowth),
            inflationHistory = state.inflationHistory.pushCapped(inflation),
            unemploymentHistory = state.unemploymentHistory.pushCapped(unemployment),
            interestRateHistory = state.interestRateHistory.pushCapped(interestRate),
            confidenceHistory = state.confidenceHistory.pushCapped(confidence)
        )
    }

    /** Simple duration-biased Markov chain over the four business-cycle phases. */
    private fun advancePhase(state: EconomyState, rng: Random, shock: EconomyShock): Pair<BusinessCyclePhase, Int> {
        if (shock.forceRecession) return BusinessCyclePhase.RECESSION to 0
        if (shock.forceBoom) return BusinessCyclePhase.EXPANSION to 0

        val months = state.phaseMonthsElapsed
        val transitionChance = when (state.phase) {
            BusinessCyclePhase.EXPANSION -> min(0.02 + max(0, months - 12) * 0.01, 0.35)
            BusinessCyclePhase.PEAK -> min(0.25 + months * 0.15, 0.9)
            BusinessCyclePhase.RECESSION -> min(0.05 + max(0, months - 4) * 0.03, 0.5)
            BusinessCyclePhase.TROUGH -> min(0.20 + months * 0.10, 0.85)
        }

        return if (rng.nextDouble() < transitionChance) {
            val next = when (state.phase) {
                BusinessCyclePhase.EXPANSION -> BusinessCyclePhase.PEAK
                BusinessCyclePhase.PEAK -> BusinessCyclePhase.RECESSION
                BusinessCyclePhase.RECESSION -> BusinessCyclePhase.TROUGH
                BusinessCyclePhase.TROUGH -> BusinessCyclePhase.EXPANSION
            }
            next to 0
        } else {
            state.phase to (months + 1)
        }
    }
}

/** Box-Muller transform: [kotlin.random.Random] has no built-in Gaussian sampler. */
fun Random.nextGaussian(mean: Double, stdDev: Double): Double {
    var u1: Double
    var u2: Double
    do {
        u1 = nextDouble()
        u2 = nextDouble()
    } while (u1 <= Double.MIN_VALUE)
    val z0 = kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    return mean + z0 * stdDev
}

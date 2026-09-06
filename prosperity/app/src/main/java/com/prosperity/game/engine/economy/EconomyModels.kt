package com.prosperity.game.engine.economy

import kotlinx.serialization.Serializable

/** The four phases of a classic business cycle. */
@Serializable
enum class BusinessCyclePhase { EXPANSION, PEAK, RECESSION, TROUGH }

private const val MAX_HISTORY = 120

/**
 * Macro-economic snapshot for the simulated country the player operates in.
 * Everything here is expressed as a plain number so it can drive UI, the
 * market engine, and the business engine without any of them depending on
 * each other.
 */
@Serializable
data class EconomyState(
    val month: Int = 0,
    val phase: BusinessCyclePhase = BusinessCyclePhase.EXPANSION,
    val phaseMonthsElapsed: Int = 0,
    /** Annualized GDP growth rate, e.g. 2.5 means +2.5%/year. */
    val gdpGrowthRate: Double = 2.5,
    /** Annualized inflation rate (CPI), e.g. 2.0 means +2%/year. */
    val inflationRate: Double = 2.0,
    val unemploymentRate: Double = 5.0,
    /** Central bank base interest rate, annualized %. */
    val interestRate: Double = 3.0,
    /** 0-100 consumer sentiment index. */
    val consumerConfidence: Double = 60.0,
    /** Annualized growth rate of the money supply. */
    val moneySupplyGrowth: Double = 4.0,
    /** Cost-of-living index, starts at 100 and compounds with inflation each month. */
    val priceLevelIndex: Double = 100.0,
    /** Personal income tax rate, as a percent (18.0 = 18%). Moved only by policy events. */
    val incomeTaxRate: Double = 18.0,
    /** Extra corporate tax percentage points on top of each business type's base rate. */
    val corporateTaxSurcharge: Double = 0.0,
    val gdpHistory: List<Double> = listOf(gdpGrowthRate),
    val inflationHistory: List<Double> = listOf(inflationRate),
    val unemploymentHistory: List<Double> = listOf(unemploymentRate),
    val interestRateHistory: List<Double> = listOf(interestRate),
    val confidenceHistory: List<Double> = listOf(consumerConfidence)
) {
    companion object {
        const val INFLATION_TARGET = 2.0
        const val NATURAL_UNEMPLOYMENT = 4.5
        const val NATURAL_GROWTH = 2.2
    }
}

internal fun <T> List<T>.pushCapped(value: T): List<T> = (this + value).takeLast(MAX_HISTORY)

/** One-off shocks the event engine can push into the next economic tick. */
@Serializable
data class EconomyShock(
    val gdpGrowthDelta: Double = 0.0,
    val inflationDelta: Double = 0.0,
    val unemploymentDelta: Double = 0.0,
    val interestRateDelta: Double = 0.0,
    val confidenceDelta: Double = 0.0,
    val forceRecession: Boolean = false,
    val forceBoom: Boolean = false
)

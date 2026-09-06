package com.prosperity.game.engine.market

import kotlinx.serialization.Serializable

private const val MAX_HISTORY = 120
internal fun <T> List<T>.pushCapped(value: T): List<T> = (this + value).takeLast(MAX_HISTORY)

@Serializable
enum class Sector { TECH, ENERGY, CONSUMER, FINANCE, INDUSTRIAL, HEALTHCARE }

@Serializable
data class Stock(
    val id: String,
    val name: String,
    val sector: Sector,
    val price: Double,
    val priceHistory: List<Double> = listOf(price),
    /** Sensitivity to the overall economy; >1 amplifies swings, <1 dampens them. */
    val beta: Double,
    val volatility: Double,
    val dividendYieldAnnual: Double
)

@Serializable
enum class BondIssuer { GOVERNMENT, CORPORATE }

@Serializable
data class Bond(
    val id: String,
    val name: String,
    val issuer: BondIssuer,
    val faceValue: Double,
    val couponRate: Double,
    val originalTermMonths: Int,
    val monthsRemaining: Int,
    val price: Double,
    val riskPremium: Double
)

@Serializable
data class Commodity(
    val id: String,
    val name: String,
    val price: Double,
    val priceHistory: List<Double> = listOf(price),
    /** Positive = tends to rise in a recession/high inflation (e.g. gold). */
    val safeHavenFactor: Double,
    val volatility: Double
)

@Serializable
data class MarketState(
    val stocks: List<Stock>,
    val bonds: List<Bond>,
    val commodities: List<Commodity>,
    val housingPriceIndex: Double = 100.0,
    val housingHistory: List<Double> = listOf(100.0),
    /** Units of foreign currency 1 local dollar buys. Rising = local currency strengthens. */
    val exchangeRate: Double = 1.0,
    val exchangeRateHistory: List<Double> = listOf(1.0)
) {
    fun stock(id: String) = stocks.first { it.id == id }
    fun bond(id: String) = bonds.first { it.id == id }
    fun commodity(id: String) = commodities.first { it.id == id }
}

data class MaturedBond(val bondId: String, val faceValuePerUnit: Double, val unitsRedeemedAutomatically: Int = 0)
data class MarketAdvanceResult(val markets: MarketState, val maturedBonds: List<MaturedBond>)

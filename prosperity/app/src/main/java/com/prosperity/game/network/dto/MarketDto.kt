package com.prosperity.game.network.dto

data class Stock(
    val id: String,
    val name: String,
    val sector: String,
    val realTicker: String?,
    val price: Double,
    val beta: Double,
    val volatility: Double,
    val dividendYieldAnnual: Double
)

data class Bond(
    val id: String,
    val name: String,
    val issuer: String,
    val faceValue: Double,
    val couponRate: Double,
    val originalTermMonths: Int,
    val monthsRemaining: Int,
    val price: Double,
    val riskPremium: Double
)

data class Commodity(
    val id: String,
    val name: String,
    val realSymbol: String?,
    val price: Double,
    val safeHavenFactor: Double,
    val volatility: Double
)

data class MarketSnapshot(
    val stocks: List<Stock>,
    val bonds: List<Bond>,
    val commodities: List<Commodity>,
    val housingPriceIndex: Double,
    val exchangeRate: Double
)

data class EconomyState(
    val month: Int,
    val phase: String,
    val phaseMonthsElapsed: Int,
    val gdpGrowthRate: Double,
    val inflationRate: Double,
    val unemploymentRate: Double,
    val interestRate: Double,
    val consumerConfidence: Double,
    val moneySupplyGrowth: Double,
    val priceLevelIndex: Double,
    val incomeTaxRate: Double,
    val corporateTaxSurcharge: Double,
    val dataSource: String
)

data class EconomyHistoryPoint(
    val month: Int,
    val gdpGrowthRate: Double,
    val inflationRate: Double,
    val unemploymentRate: Double,
    val interestRate: Double,
    val consumerConfidence: Double
)

data class MarketSnapshotResponse(
    val economy: EconomyState,
    val markets: MarketSnapshot
)

data class StockTradeRequest(val stockId: String, val side: String, val shares: Int)
data class BondTradeRequest(val bondId: String, val side: String, val units: Int)
data class CommodityTradeRequest(val commodityId: String, val side: String, val units: Double)

data class TradeResult(
    val instrumentType: String,
    val instrumentId: String,
    val side: String,
    val quantity: Double,
    val executedPrice: Double,
    val newPrice: Double,
    val totalCost: Double,
    val cashAfter: Double
)

data class TradeBroadcast(
    val username: String,
    val instrumentType: String,
    val instrumentId: String,
    val side: String,
    val quantity: Double,
    val executedPrice: Double,
    val newPrice: Double
)

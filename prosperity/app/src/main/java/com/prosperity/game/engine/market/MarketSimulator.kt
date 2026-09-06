package com.prosperity.game.engine.market

import com.prosperity.game.engine.economy.BusinessCyclePhase
import com.prosperity.game.engine.economy.EconomyState
import com.prosperity.game.engine.economy.nextGaussian
import kotlin.random.Random

/**
 * Advances every tradable instrument by one month. Stocks follow a
 * geometric random walk whose drift depends on the business cycle and each
 * stock's beta/sector; bonds are priced with a simplified duration model
 * that pulls to par as maturity approaches; commodities react to inflation
 * and recession as safe havens or cyclical goods; housing responds mainly
 * to interest rates; currency drifts with the interest-rate level.
 */
object MarketSimulator {

    fun advance(markets: MarketState, economy: EconomyState, previousRate: Double, rng: Random): MarketAdvanceResult {
        val phaseDrift = when (economy.phase) {
            BusinessCyclePhase.EXPANSION -> 0.012
            BusinessCyclePhase.PEAK -> 0.002
            BusinessCyclePhase.RECESSION -> -0.02
            BusinessCyclePhase.TROUGH -> -0.004
        }
        val rateChange = economy.interestRate - previousRate

        val newStocks = markets.stocks.map { stock ->
            val sectorTilt = sectorCyclicalTilt(stock.sector, economy.phase)
            val monthlyReturn = (phaseDrift * stock.beta) +
                sectorTilt -
                rateChange * 0.02 * stock.beta +
                rng.nextGaussian(0.0, stock.volatility)
            val newPrice = (stock.price * (1.0 + monthlyReturn)).coerceAtLeast(0.05)
            stock.copy(price = newPrice, priceHistory = stock.priceHistory.pushCapped(newPrice))
        }

        val newBonds = markets.bonds.mapNotNull { bond ->
            if (bond.monthsRemaining <= 1) null // matured; handled below, reissued fresh
            else {
                val monthsLeft = bond.monthsRemaining - 1
                val years = monthsLeft / 12.0
                val rateGap = (bond.couponRate - economy.interestRate) / 100.0
                val priceFactor = (1.0 + rateGap * years * 0.55).coerceIn(0.5, 1.6)
                val newPrice = bond.faceValue * priceFactor
                bond.copy(monthsRemaining = monthsLeft, price = newPrice)
            }
        }
        val maturedBonds = markets.bonds
            .filter { it.monthsRemaining <= 1 }
            .map { MaturedBond(it.id, it.faceValue) }
        val reissuedBonds = markets.bonds
            .filter { it.monthsRemaining <= 1 }
            .map { old ->
                old.copy(
                    couponRate = economy.interestRate + old.riskPremium,
                    monthsRemaining = old.originalTermMonths,
                    price = old.faceValue
                )
            }

        val newCommodities = markets.commodities.map { commodity ->
            val recessionBoost = if (economy.phase == BusinessCyclePhase.RECESSION || economy.phase == BusinessCyclePhase.TROUGH) 1.0 else -0.3
            val inflationBoost = (economy.inflationRate - EconomyState.INFLATION_TARGET) * 0.01
            val monthlyReturn = commodity.safeHavenFactor * (recessionBoost * 0.01 + inflationBoost) +
                rng.nextGaussian(0.0, commodity.volatility)
            val newPrice = (commodity.price * (1.0 + monthlyReturn)).coerceAtLeast(0.05)
            commodity.copy(price = newPrice, priceHistory = commodity.priceHistory.pushCapped(newPrice))
        }

        val housingReturn = -0.03 * rateChange + (economy.gdpGrowthRate - EconomyState.NATURAL_GROWTH) * 0.003 + rng.nextGaussian(0.0, 0.01)
        val newHousingIndex = (markets.housingPriceIndex * (1.0 + housingReturn)).coerceAtLeast(10.0)

        val fxReturn = (economy.interestRate - 3.0) * 0.004 + rng.nextGaussian(0.0, 0.012)
        val newExchangeRate = (markets.exchangeRate * (1.0 + fxReturn)).coerceIn(0.2, 5.0)

        val result = MarketState(
            stocks = newStocks,
            bonds = newBonds + reissuedBonds,
            commodities = newCommodities,
            housingPriceIndex = newHousingIndex,
            housingHistory = markets.housingHistory.pushCapped(newHousingIndex),
            exchangeRate = newExchangeRate,
            exchangeRateHistory = markets.exchangeRateHistory.pushCapped(newExchangeRate)
        )
        return MarketAdvanceResult(result, maturedBonds)
    }

    private fun sectorCyclicalTilt(sector: Sector, phase: BusinessCyclePhase): Double {
        val cyclicalityStrength = when (sector) {
            Sector.TECH -> 1.3
            Sector.INDUSTRIAL -> 1.1
            Sector.CONSUMER -> 0.6
            Sector.FINANCE -> 1.0
            Sector.ENERGY -> 0.4
            Sector.HEALTHCARE -> 0.2 // defensive sector
        }
        val phaseSign = when (phase) {
            BusinessCyclePhase.EXPANSION -> 0.006
            BusinessCyclePhase.PEAK -> 0.0
            BusinessCyclePhase.RECESSION -> -0.012
            BusinessCyclePhase.TROUGH -> -0.002
        }
        return phaseSign * cyclicalityStrength
    }
}

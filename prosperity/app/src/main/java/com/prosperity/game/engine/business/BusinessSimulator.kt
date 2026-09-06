package com.prosperity.game.engine.business

import com.prosperity.game.engine.economy.EconomyState
import com.prosperity.game.engine.economy.nextGaussian
import kotlin.random.Random

data class BusinessMonthResult(val business: Business, val profit: Double, val isBankrupt: Boolean)

/**
 * Simulates one month of a single business: demand (driven by the economy,
 * consumer confidence, reputation, price, competition, and advertising),
 * revenue, costs, taxes, reputation drift, and competitive pressure.
 */
object BusinessSimulator {

    fun advanceMonth(business: Business, economy: EconomyState, rng: Random): BusinessMonthResult {
        val spec = BusinessCatalog.specs.getValue(business.type)
        val productivity = 1.0 + (business.level - 1) * 0.25

        val economyFactor = (1.0 + ((economy.gdpGrowthRate - EconomyState.NATURAL_GROWTH) / 10.0) * spec.cyclicality)
            .coerceIn(0.35, 1.9)
        val confidenceFactor = (0.5 + economy.consumerConfidence / 100.0).coerceIn(0.5, 1.5)
        val reputationFactor = (0.5 + business.reputation / 100.0).coerceIn(0.5, 1.5)
        val priceFactor = (1.5 - 0.5 * business.pricePointMultiplier).coerceIn(0.3, 1.3)
        val competitionFactor = (1.0 - business.competitionPressure / 150.0).coerceIn(0.25, 1.0)
        val advertisingFactor = 1.0 + (business.advertisingBudgetMonthly / 5000.0).coerceIn(0.0, 0.5)
        val noise = rng.nextGaussian(1.0, 0.06)

        val baseRevenue = business.employees * spec.baseRevenuePerEmployee * productivity
        val revenue = (baseRevenue * economyFactor * confidenceFactor * reputationFactor *
            priceFactor * business.pricePointMultiplier * competitionFactor * advertisingFactor * noise)
            .coerceAtLeast(0.0)

        val inflationAdjustedSalary = spec.baseSalaryPerEmployee * (1.0 + economy.inflationRate / 100.0 * 0.5)
        val salaries = business.employees * inflationAdjustedSalary
        val rent = spec.baseRent * (1.0 + (business.level - 1) * 0.3)
        val inventoryCost = if (spec.hasInventory) revenue * 0.35 else 0.0
        val loanInterest = business.loanBalance * business.loanInterestRate / 12.0
        val expenses = salaries + rent + inventoryCost + business.advertisingBudgetMonthly + loanInterest

        val profitBeforeTax = revenue - expenses
        val effectiveTaxRate = spec.corporateTaxRate + economy.corporateTaxSurcharge / 100.0
        val tax = maxOf(0.0, profitBeforeTax) * effectiveTaxRate
        val profit = profitBeforeTax - tax

        val reputationTarget = (50.0 + (business.level - 1) * 5.0 -
            maxOf(0.0, business.pricePointMultiplier - 1.3) * 20.0 +
            (business.advertisingBudgetMonthly / 2000.0).coerceAtMost(10.0)).coerceIn(0.0, 100.0)
        val newReputation = (business.reputation + (reputationTarget - business.reputation) * 0.15 + rng.nextGaussian(0.0, 1.2))
            .coerceIn(0.0, 100.0)

        val competitionDelta = 0.5 + maxOf(0.0, profit / 2000.0) * 0.3 -
            (business.advertisingBudgetMonthly / 3000.0) - (business.level - 1) * 0.2
        val newCompetition = (business.competitionPressure + competitionDelta).coerceIn(0.0, 100.0)

        val newCash = business.businessCash + profit
        val bankrupt = newCash < -spec.startupCost * 0.4

        val updated = business.copy(
            businessCash = newCash,
            reputation = newReputation,
            competitionPressure = newCompetition,
            history = (business.history + MonthlyFinancials(economy.month, revenue, expenses, profit)).takeLast(36)
        )
        return BusinessMonthResult(updated, profit, bankrupt)
    }

    fun startNew(type: BusinessType, name: String, idSuffix: String): Business =
        Business(id = "BIZ_${type.name}_$idSuffix", type = type, name = name)

    fun hire(business: Business, count: Int = 1): Business = business.copy(employees = (business.employees + count).coerceAtLeast(0))

    fun fire(business: Business, count: Int = 1): Business = business.copy(employees = (business.employees - count).coerceAtLeast(0))

    fun setPrice(business: Business, multiplier: Double): Business = business.copy(pricePointMultiplier = multiplier.coerceIn(0.5, 2.0))

    fun setAdvertising(business: Business, monthlyBudget: Double): Business = business.copy(advertisingBudgetMonthly = monthlyBudget.coerceAtLeast(0.0))

    fun upgrade(business: Business): Business =
        if (business.level >= business.maxLevel) business else business.copy(level = business.level + 1)

    fun takeLoan(business: Business, amount: Double, annualRate: Double): Business =
        business.copy(
            businessCash = business.businessCash + amount,
            loanBalance = business.loanBalance + amount,
            loanInterestRate = annualRate
        )

    fun repayLoan(business: Business, amount: Double): Business {
        val payment = amount.coerceAtMost(business.loanBalance)
        return business.copy(businessCash = business.businessCash - payment, loanBalance = business.loanBalance - payment)
    }

    fun withdrawProfit(business: Business, amount: Double): Pair<Business, Double> {
        val take = amount.coerceIn(0.0, business.businessCash)
        return business.copy(businessCash = business.businessCash - take) to take
    }

    fun injectCapital(business: Business, amount: Double): Business = business.copy(businessCash = business.businessCash + amount)

    /** Liquidation value if the player closes the business voluntarily. */
    fun liquidationValue(business: Business): Double {
        val spec = BusinessCatalog.specs.getValue(business.type)
        val assetValue = spec.startupCost * 0.4 * business.level
        return (assetValue + business.businessCash - business.loanBalance).coerceAtLeast(0.0)
    }
}

package com.prosperity.game.engine.player

import com.prosperity.game.engine.economy.EconomyState
import com.prosperity.game.engine.market.MarketState
import kotlin.math.pow

data class IncomeBreakdown(
    val salary: Double = 0.0,
    val dividends: Double = 0.0,
    val bondCoupons: Double = 0.0,
    val rentalIncome: Double = 0.0,
    val savingsInterest: Double = 0.0
) {
    val total: Double get() = salary + dividends + bondCoupons + rentalIncome + savingsInterest
}

data class ExpenseBreakdown(
    val lifestyle: Double = 0.0,
    val loanPayments: Double = 0.0,
    val educationTuition: Double = 0.0
) {
    val total: Double get() = lifestyle + loanPayments + educationTuition
}

data class PlayerTickResult(
    val player: PlayerState,
    val income: IncomeBreakdown,
    val expenses: ExpenseBreakdown,
    val netWorth: Double,
    val wentBankruptThisMonth: Boolean
)

/** Computes a player's monthly cashflow, net worth, and slow-moving personal stats. */
object FinanceEngine {

    private const val SAVINGS_RATE_SPREAD = 0.65 // savings accounts pay ~65% of the base rate

    fun netWorth(player: PlayerState, markets: MarketState): Double {
        val stockValue = player.stockHoldings.entries.sumOf { (id, shares) -> markets.stock(id).price * shares }
        val bondValue = player.bondHoldings.entries.sumOf { (id, units) -> markets.bond(id).price * units }
        val commodityValue = player.commodityHoldings.entries.sumOf { (id, units) -> markets.commodity(id).price * units }
        val propertyValue = player.properties.sumOf { it.purchasePrice * (markets.housingPriceIndex / it.purchaseHousingIndex) }
        val propertyDebt = player.properties.sumOf { it.mortgageBalance }
        val currencyValue = player.foreignCurrencyHoldings / markets.exchangeRate
        val businessValue = player.businesses.sumOf { biz ->
            biz.businessCash - biz.loanBalance + (biz.history.takeLast(6).map { it.profit }.average().takeIf { !it.isNaN() } ?: 0.0) * 8.0
        }
        val loanDebt = player.loans.sumOf { it.principalRemaining }
        return player.cash + player.bankSavings + stockValue + bondValue + commodityValue +
            propertyValue + currencyValue + businessValue - loanDebt - propertyDebt
    }

    fun advanceMonth(player: PlayerState, economy: EconomyState, markets: MarketState): PlayerTickResult {
        val job = JobCatalog.byId(player.currentJobId)
        val tenureBonus = if (job != null) (1.0 + (player.jobMonthsHeld * 0.004).coerceAtMost(0.5)) else 1.0
        val grossSalary = (job?.baseSalary ?: 0.0) * tenureBonus
        val salary = grossSalary * (1.0 - economy.incomeTaxRate / 100.0)

        val dividends = player.stockHoldings.entries.sumOf { (id, shares) ->
            val stock = markets.stocks.firstOrNull { it.id == id } ?: return@sumOf 0.0
            stock.price * shares * stock.dividendYieldAnnual / 100.0 / 12.0
        }
        val coupons = player.bondHoldings.entries.sumOf { (id, units) ->
            val bond = markets.bonds.firstOrNull { it.id == id } ?: return@sumOf 0.0
            bond.faceValue * units * bond.couponRate / 100.0 / 12.0
        }
        val rent = player.properties.sumOf { it.monthlyRentIncome }
        val savingsInterest = player.bankSavings * (economy.interestRate * SAVINGS_RATE_SPREAD) / 100.0 / 12.0
        val income = IncomeBreakdown(salary, dividends, coupons, rent, savingsInterest)

        val lifestyleCost = player.lifestyleTier.monthlyCost * (economy.priceLevelIndex / 100.0)
        val tuition = if (player.educationInProgressId != null) {
            EducationCatalog.byId(player.educationInProgressId)?.let { it.tuitionCost / it.durationMonths } ?: 0.0
        } else 0.0

        var loanPaymentsTotal = 0.0
        val updatedLoans = player.loans.mapNotNull { loan ->
            val monthlyRate = loan.annualRate / 100.0 / 12.0
            val interestPortion = loan.principalRemaining * monthlyRate
            val principalPortion = (loan.monthlyPayment - interestPortion).coerceAtMost(loan.principalRemaining)
            loanPaymentsTotal += loan.monthlyPayment
            val remaining = (loan.principalRemaining - principalPortion).coerceAtLeast(0.0)
            if (remaining <= 0.01 || loan.termMonthsRemaining <= 1) null
            else loan.copy(principalRemaining = remaining, termMonthsRemaining = loan.termMonthsRemaining - 1)
        }
        val mortgagePaymentsTotal = player.properties.sumOf { property ->
            val monthlyRate = property.mortgageRate / 100.0 / 12.0
            property.mortgageBalance * monthlyRate + property.mortgageBalance * 0.005
        }
        val expenses = ExpenseBreakdown(lifestyleCost, loanPaymentsTotal + mortgagePaymentsTotal, tuition)

        val updatedProperties = player.properties.map { property ->
            val monthlyRate = property.mortgageRate / 100.0 / 12.0
            val interestPortion = property.mortgageBalance * monthlyRate
            val principalPortion = (property.mortgageBalance * 0.005).coerceAtMost(property.mortgageBalance - interestPortion)
            property.copy(mortgageBalance = (property.mortgageBalance - principalPortion).coerceAtLeast(0.0))
        }

        val newCash = player.cash + income.total - expenses.total
        val wentBankruptThisMonth = newCash < 0.0
        val monthsNegative = if (wentBankruptThisMonth) player.monthsSinceNegativeCash + 1 else 0

        val skillGain = job?.skillGainPerMonth.orEmpty()
        val newSkills = player.skills.toMutableMap()
        skillGain.forEach { (type, amount) -> newSkills[type.name] = ((newSkills[type.name] ?: 0) + amount).coerceAtMost(100) }

        val eduInProgress = EducationCatalog.byId(player.educationInProgressId)
        val (newEducationLevel, newEduInProgressId, newEduMonthsRemaining) = if (eduInProgress != null) {
            val remaining = player.educationMonthsRemaining - 1
            if (remaining <= 0) Triple(eduInProgress.grantsLevel, null, 0) else Triple(player.educationLevel, player.educationInProgressId, remaining)
        } else Triple(player.educationLevel, null, 0)

        val incomeToExpenseRatio = if (expenses.total > 0) income.total / expenses.total else 1.5
        val happinessTarget = (50.0 + player.lifestyleTier.happinessBonus +
            (job?.happinessImpact ?: -1.0) +
            (incomeToExpenseRatio - 1.0) * 15.0 +
            (eduInProgress?.happinessImpactPerMonth ?: 0.0)).coerceIn(0.0, 100.0)
        val newHappiness = (player.happiness + (happinessTarget - player.happiness) * 0.2).coerceIn(0.0, 100.0)
        val healthTarget = (60.0 + (newHappiness - 50.0) * 0.4 + (player.lifestyleTier.ordinal - 1) * 3.0).coerceIn(0.0, 100.0)
        val newHealth = (player.health + (healthTarget - player.health) * 0.15).coerceIn(0.0, 100.0)
        val newReputation = (player.reputation + (if (wentBankruptThisMonth) -3.0 else 0.3)).coerceIn(0.0, 100.0)

        var updatedPlayer = player.copy(
            cash = newCash,
            currentJobId = player.currentJobId,
            jobMonthsHeld = if (job != null) player.jobMonthsHeld + 1 else 0,
            educationLevel = newEducationLevel,
            educationInProgressId = newEduInProgressId,
            educationMonthsRemaining = newEduMonthsRemaining,
            skills = newSkills,
            loans = updatedLoans,
            properties = updatedProperties,
            happiness = newHappiness,
            health = newHealth,
            reputation = newReputation,
            monthsSinceNegativeCash = monthsNegative,
            cashHistory = player.cashHistory.pushCapped(newCash),
            incomeHistory = player.incomeHistory.pushCapped(income.total),
            expenseHistory = player.expenseHistory.pushCapped(expenses.total)
        )
        val netWorthNow = netWorth(updatedPlayer, markets)
        updatedPlayer = updatedPlayer.copy(netWorthHistory = updatedPlayer.netWorthHistory.pushCapped(netWorthNow))

        return PlayerTickResult(updatedPlayer, income, expenses, netWorthNow, wentBankruptThisMonth)
    }

    fun loanMonthlyPayment(principal: Double, annualRate: Double, termMonths: Int): Double {
        val r = annualRate / 100.0 / 12.0
        if (r == 0.0) return principal / termMonths
        val factor = (1 + r).pow(termMonths)
        return principal * r * factor / (factor - 1)
    }
}

private const val MAX_HISTORY = 120
private fun <T> List<T>.pushCapped(value: T): List<T> = (this + value).takeLast(MAX_HISTORY)

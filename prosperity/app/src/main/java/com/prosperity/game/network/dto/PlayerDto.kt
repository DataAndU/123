package com.prosperity.game.network.dto

data class Loan(
    val id: String,
    val type: String,
    val principalRemaining: Double,
    val annualRate: Double,
    val monthlyPayment: Double,
    val originalPrincipal: Double,
    val termMonthsRemaining: Int
)

data class PropertyHolding(
    val id: String,
    val purchasePrice: Double,
    val purchaseHousingIndex: Double,
    val mortgageBalance: Double,
    val mortgageRate: Double,
    val monthlyRentIncome: Double
)

data class BusinessRecord(
    val id: String,
    val userId: String? = null,
    val type: String,
    val name: String,
    val level: Int,
    val employees: Int,
    val pricePointMultiplier: Double,
    val reputation: Double,
    val advertisingBudgetMonthly: Double,
    val competitionPressure: Double,
    val businessCash: Double,
    val loanBalance: Double,
    val loanInterestRate: Double,
    val status: String? = null
)

data class PlayerView(
    val userId: String,
    val username: String,
    val displayName: String,
    val cash: Double,
    val bankSavings: Double,
    val walletCoins: Double,
    val currentJobId: String?,
    val jobMonthsHeld: Int,
    val educationLevel: String,
    val educationInProgressId: String?,
    val educationMonthsRemaining: Int,
    val skills: Map<String, Int>,
    val lifestyleTier: String,
    val happiness: Double,
    val health: Double,
    val reputation: Double,
    val foreignCurrencyHoldings: Double,
    val achievementsUnlocked: List<String>,
    val netWorth: Double,
    val loans: List<Loan>,
    val properties: List<PropertyHolding>,
    val businesses: List<BusinessRecord>,
    val stockHoldings: Map<String, Int>,
    val bondHoldings: Map<String, Int>,
    val commodityHoldings: Map<String, Double>
)

data class LifestyleRequest(val tier: String)

data class ApplyJobRequest(val jobId: String)
data class TrainSkillRequest(val skill: String, val cost: Double)
data class TrainSkillResponse(val ok: Boolean, val gain: Int)
data class EnrollRequest(val programId: String)

data class TakeLoanRequest(val amount: Double, val annualRate: Double, val termMonths: Int)
data class TakeLoanResponse(val ok: Boolean, val monthlyPayment: Double)
data class RepayLoanRequest(val amount: Double)

data class AmountRequest(val amount: Double)

data class BuyPropertyRequest(val downPaymentFraction: Double, val mortgageRate: Double)
data class BuyPropertyResponse(val ok: Boolean, val price: Double)
data class SellPropertyResponse(val ok: Boolean, val proceeds: Double)

data class CurrencyBuyResponse(val ok: Boolean, val foreignAmount: Double)
data class CurrencySellResponse(val ok: Boolean, val localAmount: Double)

data class OkResponse(val ok: Boolean)

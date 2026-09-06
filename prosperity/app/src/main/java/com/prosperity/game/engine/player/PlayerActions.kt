package com.prosperity.game.engine.player

import com.prosperity.game.engine.market.MarketState
import java.util.UUID

sealed class ActionResult {
    data class Success(val player: PlayerState, val message: String) : ActionResult()
    data class Failure(val reason: String) : ActionResult()
}

/** Base price of a "standard" residential unit before applying the housing index. */
const val BASE_PROPERTY_PRICE = 220_000.0

/**
 * Every player-initiated financial decision, as a pure function from
 * (player state, sometimes market state) to a new player state or a
 * rejection reason. Keeping these outside the UI means the same rules
 * apply whether triggered from a button, an event choice, or a test.
 */
object PlayerActions {

    fun buyStock(player: PlayerState, markets: MarketState, stockId: String, shares: Int): ActionResult {
        if (shares <= 0) return ActionResult.Failure("Enter a positive number of shares.")
        val stock = markets.stocks.firstOrNull { it.id == stockId } ?: return ActionResult.Failure("Unknown stock.")
        val cost = stock.price * shares
        if (cost > player.cash) return ActionResult.Failure("Not enough cash. Need $${"%,.2f".format(cost)}.")
        val holdings = player.stockHoldings.toMutableMap()
        holdings[stockId] = (holdings[stockId] ?: 0) + shares
        return ActionResult.Success(player.copy(cash = player.cash - cost, stockHoldings = holdings), "Bought $shares shares of ${stock.name}.")
    }

    fun sellStock(player: PlayerState, markets: MarketState, stockId: String, shares: Int): ActionResult {
        val owned = player.stockHoldings[stockId] ?: 0
        if (shares <= 0 || shares > owned) return ActionResult.Failure("You only own $owned shares.")
        val stock = markets.stocks.firstOrNull { it.id == stockId } ?: return ActionResult.Failure("Unknown stock.")
        val proceeds = stock.price * shares
        val holdings = player.stockHoldings.toMutableMap()
        val remaining = owned - shares
        if (remaining == 0) holdings.remove(stockId) else holdings[stockId] = remaining
        return ActionResult.Success(player.copy(cash = player.cash + proceeds, stockHoldings = holdings), "Sold $shares shares of ${stock.name} for $${"%,.2f".format(proceeds)}.")
    }

    fun buyBond(player: PlayerState, markets: MarketState, bondId: String, units: Int): ActionResult {
        if (units <= 0) return ActionResult.Failure("Enter a positive number of units.")
        val bond = markets.bonds.firstOrNull { it.id == bondId } ?: return ActionResult.Failure("Unknown bond.")
        val cost = bond.price * units
        if (cost > player.cash) return ActionResult.Failure("Not enough cash. Need $${"%,.2f".format(cost)}.")
        val holdings = player.bondHoldings.toMutableMap()
        holdings[bondId] = (holdings[bondId] ?: 0) + units
        return ActionResult.Success(player.copy(cash = player.cash - cost, bondHoldings = holdings), "Bought $units units of ${bond.name}.")
    }

    fun sellBond(player: PlayerState, markets: MarketState, bondId: String, units: Int): ActionResult {
        val owned = player.bondHoldings[bondId] ?: 0
        if (units <= 0 || units > owned) return ActionResult.Failure("You only own $owned units.")
        val bond = markets.bonds.firstOrNull { it.id == bondId } ?: return ActionResult.Failure("Unknown bond.")
        val proceeds = bond.price * units
        val holdings = player.bondHoldings.toMutableMap()
        val remaining = owned - units
        if (remaining == 0) holdings.remove(bondId) else holdings[bondId] = remaining
        return ActionResult.Success(player.copy(cash = player.cash + proceeds, bondHoldings = holdings), "Sold $units units of ${bond.name} for $${"%,.2f".format(proceeds)}.")
    }

    fun buyCommodity(player: PlayerState, markets: MarketState, commodityId: String, units: Double): ActionResult {
        if (units <= 0.0) return ActionResult.Failure("Enter a positive amount.")
        val commodity = markets.commodities.firstOrNull { it.id == commodityId } ?: return ActionResult.Failure("Unknown commodity.")
        val cost = commodity.price * units
        if (cost > player.cash) return ActionResult.Failure("Not enough cash. Need $${"%,.2f".format(cost)}.")
        val holdings = player.commodityHoldings.toMutableMap()
        holdings[commodityId] = (holdings[commodityId] ?: 0.0) + units
        return ActionResult.Success(player.copy(cash = player.cash - cost, commodityHoldings = holdings), "Bought ${"%.2f".format(units)} units of ${commodity.name}.")
    }

    fun sellCommodity(player: PlayerState, markets: MarketState, commodityId: String, units: Double): ActionResult {
        val owned = player.commodityHoldings[commodityId] ?: 0.0
        if (units <= 0.0 || units > owned + 1e-6) return ActionResult.Failure("You only own ${"%.2f".format(owned)} units.")
        val commodity = markets.commodities.firstOrNull { it.id == commodityId } ?: return ActionResult.Failure("Unknown commodity.")
        val proceeds = commodity.price * units
        val holdings = player.commodityHoldings.toMutableMap()
        val remaining = owned - units
        if (remaining <= 1e-6) holdings.remove(commodityId) else holdings[commodityId] = remaining
        return ActionResult.Success(player.copy(cash = player.cash + proceeds, commodityHoldings = holdings), "Sold ${"%.2f".format(units)} units of ${commodity.name} for $${"%,.2f".format(proceeds)}.")
    }

    fun buyProperty(player: PlayerState, markets: MarketState, downPaymentFraction: Double, mortgageRate: Double): ActionResult {
        val price = BASE_PROPERTY_PRICE * (markets.housingPriceIndex / 100.0)
        val downPayment = price * downPaymentFraction.coerceIn(0.1, 1.0)
        if (downPayment > player.cash) return ActionResult.Failure("Not enough cash for the down payment of $${"%,.2f".format(downPayment)}.")
        val mortgage = price - downPayment
        val property = PropertyHolding(
            id = "PROP_${UUID.randomUUID()}",
            purchasePrice = price,
            purchaseHousingIndex = markets.housingPriceIndex,
            mortgageBalance = mortgage,
            mortgageRate = mortgageRate,
            monthlyRentIncome = price * 0.0045
        )
        return ActionResult.Success(
            player.copy(cash = player.cash - downPayment, properties = player.properties + property),
            "Bought a property for $${"%,.2f".format(price)}."
        )
    }

    fun sellProperty(player: PlayerState, markets: MarketState, propertyId: String): ActionResult {
        val property = player.properties.firstOrNull { it.id == propertyId } ?: return ActionResult.Failure("Property not found.")
        val currentValue = property.purchasePrice * (markets.housingPriceIndex / property.purchaseHousingIndex)
        val proceeds = (currentValue - property.mortgageBalance).coerceAtLeast(0.0)
        return ActionResult.Success(
            player.copy(cash = player.cash + proceeds, properties = player.properties.filterNot { it.id == propertyId }),
            "Sold property for $${"%,.2f".format(proceeds)} net of mortgage."
        )
    }

    fun buyForeignCurrency(player: PlayerState, markets: MarketState, localAmount: Double): ActionResult {
        if (localAmount <= 0.0 || localAmount > player.cash) return ActionResult.Failure("Not enough cash.")
        val foreignAmount = localAmount * markets.exchangeRate
        return ActionResult.Success(
            player.copy(cash = player.cash - localAmount, foreignCurrencyHoldings = player.foreignCurrencyHoldings + foreignAmount),
            "Exchanged $${"%,.2f".format(localAmount)} for ${"%,.2f".format(foreignAmount)} foreign units."
        )
    }

    fun sellForeignCurrency(player: PlayerState, markets: MarketState, foreignAmount: Double): ActionResult {
        if (foreignAmount <= 0.0 || foreignAmount > player.foreignCurrencyHoldings) return ActionResult.Failure("Not enough foreign currency.")
        val localAmount = foreignAmount / markets.exchangeRate
        return ActionResult.Success(
            player.copy(cash = player.cash + localAmount, foreignCurrencyHoldings = player.foreignCurrencyHoldings - foreignAmount),
            "Converted back to $${"%,.2f".format(localAmount)}."
        )
    }

    fun applyForJob(player: PlayerState, jobId: String): ActionResult {
        val job = JobCatalog.byId(jobId) ?: return ActionResult.Failure("Unknown job.")
        val skillsByType = SkillType.entries.associateWith { player.skill(it) }
        if (!JobCatalog.isEligible(job, player.educationLevel, skillsByType)) {
            return ActionResult.Failure("You don't meet the requirements for ${job.title} yet.")
        }
        return ActionResult.Success(player.copy(currentJobId = job.id, jobMonthsHeld = 0), "You are now working as ${job.title}.")
    }

    fun quitJob(player: PlayerState): ActionResult =
        ActionResult.Success(player.copy(currentJobId = null, jobMonthsHeld = 0), "You quit your job.")

    fun enrollEducation(player: PlayerState, programId: String): ActionResult {
        if (player.educationInProgressId != null) return ActionResult.Failure("You're already studying.")
        val program = EducationCatalog.byId(programId) ?: return ActionResult.Failure("Unknown program.")
        if (program.grantsLevel.ordinal <= player.educationLevel.ordinal) return ActionResult.Failure("You already hold this level or higher.")
        val upfrontFee = program.tuitionCost * 0.2
        if (upfrontFee > player.cash) return ActionResult.Failure("Not enough cash for the enrollment fee of $${"%,.2f".format(upfrontFee)}.")
        return ActionResult.Success(
            player.copy(cash = player.cash - upfrontFee, educationInProgressId = program.id, educationMonthsRemaining = program.durationMonths),
            "Enrolled in ${program.title}."
        )
    }

    fun trainSkill(player: PlayerState, skillType: SkillType, cost: Double): ActionResult {
        if (cost > player.cash) return ActionResult.Failure("Not enough cash for training.")
        val current = player.skill(skillType)
        val gain = (8 - current / 15).coerceAtLeast(1)
        val skills = player.skills.toMutableMap()
        skills[skillType.name] = (current + gain).coerceAtMost(100)
        return ActionResult.Success(player.copy(cash = player.cash - cost, skills = skills), "Trained $skillType (+$gain).")
    }

    fun depositSavings(player: PlayerState, amount: Double): ActionResult {
        if (amount <= 0.0 || amount > player.cash) return ActionResult.Failure("Not enough cash.")
        return ActionResult.Success(player.copy(cash = player.cash - amount, bankSavings = player.bankSavings + amount), "Deposited $${"%,.2f".format(amount)}.")
    }

    fun withdrawSavings(player: PlayerState, amount: Double): ActionResult {
        if (amount <= 0.0 || amount > player.bankSavings) return ActionResult.Failure("Not enough savings.")
        return ActionResult.Success(player.copy(cash = player.cash + amount, bankSavings = player.bankSavings - amount), "Withdrew $${"%,.2f".format(amount)}.")
    }

    fun takePersonalLoan(player: PlayerState, amount: Double, annualRate: Double, termMonths: Int): ActionResult {
        if (amount <= 0.0) return ActionResult.Failure("Enter a positive amount.")
        if (player.loans.size >= 4) return ActionResult.Failure("Too many active loans already.")
        val payment = FinanceEngine.loanMonthlyPayment(amount, annualRate, termMonths)
        val loan = Loan("LOAN_${UUID.randomUUID()}", LoanType.PERSONAL, amount, annualRate, payment, amount, termMonths)
        return ActionResult.Success(player.copy(cash = player.cash + amount, loans = player.loans + loan), "Took a $${"%,.2f".format(amount)} loan at ${"%.1f".format(annualRate)}% APR.")
    }

    fun repayLoanEarly(player: PlayerState, loanId: String, amount: Double): ActionResult {
        val loan = player.loans.firstOrNull { it.id == loanId } ?: return ActionResult.Failure("Loan not found.")
        if (amount <= 0.0 || amount > player.cash) return ActionResult.Failure("Not enough cash.")
        val payoff = amount.coerceAtMost(loan.principalRemaining)
        val remaining = loan.principalRemaining - payoff
        val loans = if (remaining <= 0.01) player.loans.filterNot { it.id == loanId }
        else player.loans.map { if (it.id == loanId) it.copy(principalRemaining = remaining) else it }
        return ActionResult.Success(player.copy(cash = player.cash - payoff, loans = loans), "Paid down $${"%,.2f".format(payoff)} of the loan.")
    }

    fun setLifestyle(player: PlayerState, tier: LifestyleTier): ActionResult =
        ActionResult.Success(player.copy(lifestyleTier = tier), "Lifestyle set to ${tier.displayName}.")
}

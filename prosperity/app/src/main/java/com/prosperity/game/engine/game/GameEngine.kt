package com.prosperity.game.engine.game

import com.prosperity.game.engine.business.Business
import com.prosperity.game.engine.business.BusinessSimulator
import com.prosperity.game.engine.economy.EconomySimulator
import com.prosperity.game.engine.event.EventContext
import com.prosperity.game.engine.event.EventDefinition
import com.prosperity.game.engine.event.EventEngine
import com.prosperity.game.engine.event.EventLogEntry
import com.prosperity.game.engine.market.MarketSimulator
import com.prosperity.game.engine.player.FinanceEngine
import kotlin.random.Random

/** Orchestrates one monthly tick across every sub-engine, in a fixed, deterministic order. */
object GameEngine {

    fun newGame(mode: GameMode, difficulty: DifficultyLevel, playerName: String, seed: Long = System.currentTimeMillis()): GameState =
        Scenarios.newGame(mode, difficulty, playerName, seed)

    fun advanceMonth(state: GameState): GameAdvanceResult {
        if (state.isGameOver) return GameAdvanceResult(state, null, emptySummary(state))

        val rng = Random(state.rngSeed xor (state.economy.month.toLong() * 0x9E3779B97F4A7C15UL.toLong()))
        val previousRate = state.economy.interestRate
        val previousPhase = state.economy.phase
        val previousNetWorth = FinanceEngine.netWorth(state.player, state.markets)

        val newEconomy = EconomySimulator.advance(state.economy, rng)
        val marketResult = MarketSimulator.advance(state.markets, newEconomy, previousRate, rng)

        // Settle any bonds that matured this month.
        var playerAfterBonds = state.player
        marketResult.maturedBonds.forEach { matured ->
            val units = playerAfterBonds.bondHoldings[matured.bondId] ?: 0
            if (units > 0) {
                playerAfterBonds = playerAfterBonds.copy(
                    cash = playerAfterBonds.cash + units * matured.faceValuePerUnit,
                    bondHoldings = playerAfterBonds.bondHoldings - matured.bondId
                )
            }
        }

        // Advance each owned business; auto-close ones that go deeply bankrupt.
        val closedBusinessNames = mutableListOf<String>()
        val advancedBusinesses = mutableListOf<Business>()
        var reputationHit = 0.0
        for (biz in playerAfterBonds.businesses) {
            val result = BusinessSimulator.advanceMonth(biz, newEconomy, rng)
            if (result.isBankrupt) {
                closedBusinessNames += result.business.name
                reputationHit += 4.0
            } else {
                advancedBusinesses += result.business
            }
        }
        playerAfterBonds = playerAfterBonds.copy(
            businesses = advancedBusinesses,
            reputation = (playerAfterBonds.reputation - reputationHit).coerceAtLeast(0.0)
        )

        val tickResult = FinanceEngine.advanceMonth(playerAfterBonds, newEconomy, marketResult.markets)

        val newTier = ProgressionTier.forNetWorth(tickResult.netWorth)
        val newlyUnlocked = AchievementCatalog.evaluate(state.copy(economy = newEconomy, player = tickResult.player, progressionTier = newTier), tickResult.netWorth)
        val finalPlayer = tickResult.player.copy(achievementsUnlocked = tickResult.player.achievementsUnlocked + newlyUnlocked.map { it.id })

        val maybeEvent = EventEngine.maybeSelect(newEconomy, rng, state.recentEventIds)

        val (isGameOver, gameOverReason, challengeWon) = evaluateEndConditions(state, newEconomy, finalPlayer, tickResult.netWorth)

        val newState = state.copy(
            economy = newEconomy,
            markets = marketResult.markets,
            player = finalPlayer,
            progressionTier = newTier,
            isGameOver = isGameOver,
            gameOverReason = gameOverReason,
            challengeWon = challengeWon ?: state.challengeWon,
            newlyUnlockedAchievementIds = newlyUnlocked.map { it.id }
        )

        val summary = MonthSummary(
            month = newEconomy.month,
            income = tickResult.income,
            expenses = tickResult.expenses,
            netWorth = tickResult.netWorth,
            netWorthChange = tickResult.netWorth - previousNetWorth,
            phaseChanged = previousPhase != newEconomy.phase,
            newlyUnlockedAchievements = newlyUnlocked,
            businessesClosedFromBankruptcy = closedBusinessNames
        )

        return GameAdvanceResult(newState, maybeEvent, summary)
    }

    fun applyEventChoice(state: GameState, event: EventDefinition, optionId: String): GameState {
        val rng = Random(state.rngSeed xor (state.economy.month.toLong() * 0x2545F4914F6CDD1DUL.toLong()))
        val context = EventContext(state.economy, state.markets, state.player)
        val option = event.options.firstOrNull { it.id == optionId } ?: return state
        val result = EventEngine.applyChoice(context, event, optionId, rng)
        val logEntry = EventLogEntry(state.economy.month, event.id, event.title, option.label, option.outcomeSummary)
        return state.copy(
            economy = result.economy,
            markets = result.markets,
            player = result.player,
            eventLog = (state.eventLog + logEntry).takeLast(100),
            recentEventIds = (state.recentEventIds + event.id).takeLast(10)
        )
    }

    private fun evaluateEndConditions(
        state: GameState,
        economy: com.prosperity.game.engine.economy.EconomyState,
        player: com.prosperity.game.engine.player.PlayerState,
        netWorth: Double
    ): Triple<Boolean, String?, Boolean?> {
        if (state.gameMode != GameMode.FREE_ECONOMY) {
            val graceMonths = state.difficulty.bankruptcyGraceMonths
            if (player.monthsSinceNegativeCash >= graceMonths && netWorth < -1000.0) {
                return Triple(true, "Bankruptcy: unable to cover debts for $graceMonths consecutive months.", null)
            }
        }
        if (state.gameMode == GameMode.CHALLENGE) {
            val target = state.challengeTargetNetWorth ?: return Triple(false, null, null)
            val deadline = state.challengeDeadlineMonth ?: return Triple(false, null, null)
            if (netWorth >= target) return Triple(true, "Challenge complete! You reached the target.", true)
            if (economy.month >= deadline) return Triple(true, "Challenge failed: deadline reached without hitting the target.", false)
        }
        return Triple(false, null, null)
    }

    private fun emptySummary(state: GameState) = MonthSummary(
        month = state.economy.month,
        income = com.prosperity.game.engine.player.IncomeBreakdown(),
        expenses = com.prosperity.game.engine.player.ExpenseBreakdown(),
        netWorth = FinanceEngine.netWorth(state.player, state.markets),
        netWorthChange = 0.0,
        phaseChanged = false,
        newlyUnlockedAchievements = emptyList(),
        businessesClosedFromBankruptcy = emptyList()
    )
}

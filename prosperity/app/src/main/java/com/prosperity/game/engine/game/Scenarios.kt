package com.prosperity.game.engine.game

import com.prosperity.game.engine.economy.BusinessCyclePhase
import com.prosperity.game.engine.economy.EconomyState
import com.prosperity.game.engine.market.MarketCatalog
import com.prosperity.game.engine.player.FinanceEngine
import com.prosperity.game.engine.player.Loan
import com.prosperity.game.engine.player.LoanType
import com.prosperity.game.engine.player.PlayerState
import java.util.UUID

/** Builds the starting [GameState] for each game mode + difficulty combination. */
object Scenarios {

    fun newGame(mode: GameMode, difficulty: DifficultyLevel, playerName: String, seed: Long = System.currentTimeMillis()): GameState {
        val baseCash = when (mode) {
            GameMode.CAREER -> 2000.0
            GameMode.FREE_ECONOMY -> 20_000.0
            GameMode.BUSINESS_TYCOON -> 5000.0
            GameMode.INVESTOR -> 15_000.0
            GameMode.CRISIS_SURVIVAL -> 3000.0
            GameMode.CHALLENGE -> 5000.0
        } * difficulty.startingCashMultiplier

        var player = PlayerState(cash = baseCash)
        var economy = EconomyState()
        val markets = MarketCatalog.initialState(economy.interestRate)

        when (mode) {
            GameMode.BUSINESS_TYCOON -> {
                val loanAmount = 10_000.0
                val rate = economy.interestRate + 3.0
                val payment = FinanceEngine.loanMonthlyPayment(loanAmount, rate, 36)
                player = player.copy(
                    cash = player.cash + loanAmount,
                    loans = listOf(Loan("LOAN_STARTER", LoanType.PERSONAL, loanAmount, rate, payment, loanAmount, 36))
                )
            }
            GameMode.CRISIS_SURVIVAL -> {
                economy = economy.copy(
                    phase = BusinessCyclePhase.RECESSION,
                    phaseMonthsElapsed = 2,
                    gdpGrowthRate = -3.0,
                    unemploymentRate = 9.0,
                    consumerConfidence = 35.0,
                    interestRate = 5.5
                )
            }
            else -> Unit
        }

        return GameState(
            gameId = UUID.randomUUID().toString(),
            createdAtEpochMillis = System.currentTimeMillis(),
            rngSeed = seed,
            gameMode = mode,
            difficulty = difficulty,
            scenarioId = mode.name,
            playerName = playerName,
            economy = economy,
            markets = markets,
            player = player,
            challengeTargetNetWorth = if (mode == GameMode.CHALLENGE) 1_000_000.0 else null,
            challengeDeadlineMonth = if (mode == GameMode.CHALLENGE) 60 else null,
            challengeWon = if (mode == GameMode.CHALLENGE) false else null
        )
    }
}

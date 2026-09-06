package com.prosperity.game.engine.game

import com.prosperity.game.engine.economy.EconomyState
import com.prosperity.game.engine.event.EventLogEntry
import com.prosperity.game.engine.market.MarketState
import com.prosperity.game.engine.player.PlayerState
import kotlinx.serialization.Serializable

@Serializable
enum class GameMode(val displayName: String, val description: String) {
    CAREER("Career", "Climb from your first job to running a financial empire."),
    FREE_ECONOMY("Free Economy", "A generous sandbox with no lose condition — experiment freely."),
    BUSINESS_TYCOON("Business Tycoon", "Start with a business loan and chase an empire of companies."),
    INVESTOR("Investor", "More starting capital, weaker jobs — win or lose in the markets."),
    CRISIS_SURVIVAL("Crisis Survival", "Start mid-recession with frequent shocks. Survive and rebuild."),
    CHALLENGE("Challenge", "Hit a specific net-worth target before the deadline.")
}

@Serializable
enum class DifficultyLevel(
    val displayName: String,
    val startingCashMultiplier: Double,
    val expenseMultiplier: Double,
    val eventSeverityMultiplier: Double,
    val bankruptcyGraceMonths: Int
) {
    EASY("Easy", 1.5, 0.85, 0.7, 6),
    NORMAL("Normal", 1.0, 1.0, 1.0, 4),
    HARD("Hard", 0.7, 1.15, 1.3, 3),
    EXTREME("Extreme", 0.5, 1.3, 1.6, 2)
}

@Serializable
enum class ProgressionTier(val displayName: String, val netWorthThreshold: Double) {
    BEGINNER("Beginner", 0.0),
    SKILLED("Skilled", 15_000.0),
    ENTREPRENEUR("Entrepreneur", 100_000.0),
    SEASONED_INVESTOR("Seasoned Investor", 500_000.0),
    BUSINESS_TYCOON("Business Tycoon", 2_000_000.0),
    ECONOMIC_MASTER("Economic Master", 10_000_000.0);

    companion object {
        fun forNetWorth(netWorth: Double): ProgressionTier = entries.last { netWorth >= it.netWorthThreshold }
    }
}

@Serializable
data class GameState(
    val saveVersion: Int = 1,
    val gameId: String,
    val createdAtEpochMillis: Long,
    val rngSeed: Long,
    val gameMode: GameMode,
    val difficulty: DifficultyLevel,
    val scenarioId: String,
    val playerName: String = "Player",
    val economy: EconomyState = EconomyState(),
    val markets: MarketState,
    val player: PlayerState = PlayerState(),
    val eventLog: List<EventLogEntry> = emptyList(),
    val recentEventIds: List<String> = emptyList(),
    val progressionTier: ProgressionTier = ProgressionTier.BEGINNER,
    val isGameOver: Boolean = false,
    val gameOverReason: String? = null,
    val challengeTargetNetWorth: Double? = null,
    val challengeDeadlineMonth: Int? = null,
    val challengeWon: Boolean? = null,
    val newlyUnlockedAchievementIds: List<String> = emptyList()
)

data class MonthSummary(
    val month: Int,
    val income: com.prosperity.game.engine.player.IncomeBreakdown,
    val expenses: com.prosperity.game.engine.player.ExpenseBreakdown,
    val netWorth: Double,
    val netWorthChange: Double,
    val phaseChanged: Boolean,
    val newlyUnlockedAchievements: List<Achievement>,
    val businessesClosedFromBankruptcy: List<String>
)

data class GameAdvanceResult(
    val state: GameState,
    val triggeredEvent: com.prosperity.game.engine.event.EventDefinition?,
    val summary: MonthSummary
)

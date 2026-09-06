package com.prosperity.game.engine.game

import com.prosperity.game.engine.player.EducationLevel

data class Achievement(val id: String, val title: String, val description: String, val condition: (GameState, Double) -> Boolean)

object AchievementCatalog {
    val all: List<Achievement> = listOf(
        Achievement("first_ten_k", "First $10,000", "Reach a net worth of $10,000.") { _, nw -> nw >= 10_000.0 },
        Achievement("first_business", "Entrepreneur", "Start your first business.") { s, _ -> s.player.businesses.isNotEmpty() },
        Achievement("six_figures", "Six Figures", "Reach a net worth of $100,000.") { _, nw -> nw >= 100_000.0 },
        Achievement("half_million", "Half Millionaire", "Reach a net worth of $500,000.") { _, nw -> nw >= 500_000.0 },
        Achievement("millionaire", "Millionaire", "Reach a net worth of $1,000,000.") { _, nw -> nw >= 1_000_000.0 },
        Achievement("business_empire", "Business Empire", "Own 3 or more businesses at once.") { s, _ -> s.player.businesses.size >= 3 },
        Achievement("diversified", "Diversified Investor", "Hold stocks, bonds, commodities, and property at the same time.") { s, _ ->
            s.player.stockHoldings.isNotEmpty() && s.player.bondHoldings.isNotEmpty() &&
                s.player.commodityHoldings.isNotEmpty() && s.player.properties.isNotEmpty()
        },
        Achievement("debt_free", "Debt Free", "Reach a positive net worth with zero personal loans.") { s, nw -> nw > 0 && s.player.loans.isEmpty() && s.economy.month > 3 },
        Achievement("scholar", "Lifelong Learner", "Earn a Ph.D.") { s, _ -> s.player.educationLevel == EducationLevel.PHD },
        Achievement("economic_master", "Economic Master", "Reach the Economic Master tier.") { s, _ -> s.progressionTier == ProgressionTier.ECONOMIC_MASTER },
        Achievement("crisis_survivor", "Crisis Survivor", "Survive 24 months in Crisis Survival mode.") { s, _ -> s.gameMode == GameMode.CRISIS_SURVIVAL && s.economy.month >= 24 && !s.isGameOver },
        Achievement("property_mogul", "Property Mogul", "Own 3 or more properties.") { s, _ -> s.player.properties.size >= 3 }
    )

    fun evaluate(state: GameState, netWorth: Double): List<Achievement> =
        all.filter { it.id !in state.player.achievementsUnlocked && it.condition(state, netWorth) }
}

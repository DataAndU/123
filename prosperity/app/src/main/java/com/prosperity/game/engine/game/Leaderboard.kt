package com.prosperity.game.engine.game

import kotlinx.serialization.Serializable

/** One completed or ended run, kept locally so players can compare their own past attempts. */
@Serializable
data class LeaderboardEntry(
    val playerName: String,
    val gameMode: GameMode,
    val difficulty: DifficultyLevel,
    val finalNetWorth: Double,
    val monthsPlayed: Int,
    val tierReached: ProgressionTier,
    val endedAtEpochMillis: Long,
    val outcome: String
)

@Serializable
data class Leaderboard(val entries: List<LeaderboardEntry> = emptyList()) {
    fun withEntry(entry: LeaderboardEntry): Leaderboard =
        copy(entries = (entries + entry).sortedByDescending { it.finalNetWorth }.take(50))
}

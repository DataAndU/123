package com.prosperity.game.engine.event

import com.prosperity.game.engine.economy.EconomyState
import com.prosperity.game.engine.market.MarketState
import com.prosperity.game.engine.player.PlayerState
import kotlinx.serialization.Serializable
import kotlin.random.Random

enum class EventCategory { ECONOMY, MARKET, BUSINESS, PERSONAL, OPPORTUNITY, DISASTER }

/** Everything an event's effect function needs — deliberately narrower than the full save file. */
data class EventContext(
    val economy: EconomyState,
    val markets: MarketState,
    val player: PlayerState
)

data class EventOption(
    val id: String,
    val label: String,
    val outcomeSummary: String,
    val apply: (EventContext, Random) -> EventContext
)

data class EventDefinition(
    val id: String,
    val category: EventCategory,
    val title: String,
    val description: String,
    val educationalNote: String,
    /** Relative likelihood given the current economy; 0 = impossible right now. */
    val weight: (EconomyState) -> Double,
    val options: List<EventOption>
)

/** A resolved event kept in the save file so the player can review their history. */
@Serializable
data class EventLogEntry(
    val month: Int,
    val eventId: String,
    val title: String,
    val chosenOptionLabel: String,
    val outcomeSummary: String
)

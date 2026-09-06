package com.prosperity.game.engine.event

import com.prosperity.game.engine.economy.EconomyState
import kotlin.random.Random

/** Picks at most one weighted-random event per month, with a short cooldown to avoid repeats. */
object EventEngine {

    private const val CHANCE_OF_ANY_EVENT = 0.55
    private const val COOLDOWN_MONTHS_MEMORY = 6

    fun maybeSelect(economy: EconomyState, rng: Random, recentEventIds: List<String>): EventDefinition? {
        if (rng.nextDouble() > CHANCE_OF_ANY_EVENT) return null

        val recent = recentEventIds.takeLast(COOLDOWN_MONTHS_MEMORY)
        val candidates = EventCatalog.all
            .filter { it.id !in recent }
            .map { it to it.weight(economy) }
            .filter { it.second > 0.0 }
        if (candidates.isEmpty()) return null

        val totalWeight = candidates.sumOf { it.second }
        var roll = rng.nextDouble() * totalWeight
        for ((event, weight) in candidates) {
            roll -= weight
            if (roll <= 0.0) return event
        }
        return candidates.last().first
    }

    fun applyChoice(context: EventContext, event: EventDefinition, optionId: String, rng: Random): EventContext {
        val option = event.options.firstOrNull { it.id == optionId } ?: return context
        return option.apply(context, rng)
    }
}

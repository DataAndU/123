package com.prosperity.game.network.dto

data class BusinessBankruptedEvent(val businessIds: List<String>)
data class TickCompleteEvent(val month: Int)

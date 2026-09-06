package com.prosperity.game.network.dto

data class StartBusinessRequest(val type: String, val name: String)

data class BusinessHistoryPoint(val month: Int, val revenue: Double, val expenses: Double, val profit: Double)

data class PriceRequest(val multiplier: Double)
data class AdvertiseRequest(val monthlyBudget: Double)
data class BusinessLoanRequest(val amount: Double, val annualRate: Double)
data class BusinessAmountRequest(val amount: Double)
data class WithdrawResponse(val ok: Boolean, val withdrawn: Double)
data class CloseBusinessResponse(val ok: Boolean, val recovered: Double)

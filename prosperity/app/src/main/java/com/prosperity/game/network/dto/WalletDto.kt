package com.prosperity.game.network.dto

data class WalletTransaction(
    val id: String,
    val amountCoins: Int,
    val reason: String,
    val referenceId: String?,
    val createdAt: String
)

data class WalletResponse(
    val balanceCoins: Double,
    val transactions: List<WalletTransaction>
)

data class VerifyPurchaseRequest(val productId: String, val purchaseToken: String)
data class VerifyPurchaseResponse(val ok: Boolean, val coinsCredited: Int)

package com.prosperity.game.network.dto

data class Listing(
    val id: String,
    val sellerId: String,
    val sellerUsername: String,
    val businessId: String?,
    val title: String,
    val description: String,
    val priceCoins: Int,
    val quantityAvailable: Int,
    val status: String
)

data class OrderStatusEvent(val status: String, val at: String)

data class MarketOrder(
    val id: String,
    val listingId: String,
    val listingTitle: String,
    val buyerId: String,
    val buyerUsername: String,
    val sellerId: String,
    val sellerUsername: String,
    val quantity: Int,
    val totalPriceCoins: Int,
    val status: String,
    val statusHistory: List<OrderStatusEvent>
)

data class CreateListingRequest(
    val businessId: String?,
    val title: String,
    val description: String,
    val priceCoins: Int,
    val quantity: Int
)

data class UpdateListingRequest(val status: String? = null, val priceCoins: Int? = null)

data class PlaceOrderRequest(val listingId: String, val quantity: Int)
data class AdvanceOrderRequest(val status: String)

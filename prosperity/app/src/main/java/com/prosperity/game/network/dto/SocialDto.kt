package com.prosperity.game.network.dto

data class UserSearchResult(val userId: String, val username: String, val displayName: String)

data class FriendRequestBody(val targetUsername: String)
data class FriendRespondBody(val friendshipId: String, val accept: Boolean)

data class Friend(
    val friendshipId: String,
    val status: String,
    val direction: String,
    val username: String,
    val displayName: String
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderUsername: String,
    val body: String,
    val sentAt: String,
    val channel: String? = null
)

data class ChatSendPayload(val channel: String, val body: String)

data class PresenceUpdate(val count: Int, val users: List<PresenceUser>)
data class PresenceUser(val userId: String, val username: String)

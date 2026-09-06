package com.prosperity.game.network.dto

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val displayName: String
)

data class LoginRequest(
    val usernameOrEmail: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val player: PlayerView
)

data class ApiErrorBody(val error: String?)

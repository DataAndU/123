package com.prosperity.game.core

import android.app.Application
import com.prosperity.game.data.GameRepository
import com.prosperity.game.data.TokenStore
import com.prosperity.game.network.ApiClient
import com.prosperity.game.network.SocketManager

class ProsperityApp : Application() {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var socketManager: SocketManager
        private set
    lateinit var repository: GameRepository
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        apiClient = ApiClient(tokenStore)
        socketManager = SocketManager(tokenStore)
        repository = GameRepository(apiClient, tokenStore)
    }
}

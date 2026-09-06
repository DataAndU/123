package com.prosperity.game.core

import android.app.Application
import com.prosperity.game.save.SaveManager

class ProsperityApp : Application() {
    lateinit var saveManager: SaveManager
        private set

    override fun onCreate() {
        super.onCreate()
        saveManager = SaveManager(this)
    }
}

package com.prosperity.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.prosperity.game.core.ProsperityApp
import com.prosperity.game.ui.GameViewModel
import com.prosperity.game.ui.GameViewModelFactory
import com.prosperity.game.ui.ProsperityRoot
import com.prosperity.game.ui.theme.ProsperityTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels {
        GameViewModelFactory((application as ProsperityApp).saveManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProsperityTheme {
                ProsperityRoot(viewModel)
            }
        }
    }
}

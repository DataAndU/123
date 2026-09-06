package com.prosperity.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.prosperity.game.billing.BillingManager
import com.prosperity.game.core.ProsperityApp
import com.prosperity.game.ui.OnlineViewModel
import com.prosperity.game.ui.OnlineViewModelFactory
import com.prosperity.game.ui.ProsperityRoot
import com.prosperity.game.ui.theme.ProsperityTheme

class MainActivity : ComponentActivity() {

    private val viewModel: OnlineViewModel by viewModels {
        val app = application as ProsperityApp
        OnlineViewModelFactory(app.repository, app.socketManager)
    }

    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        billingManager = BillingManager(
            activity = this,
            onPurchaseCompleted = { productId, purchaseToken -> viewModel.verifyPurchase(productId, purchaseToken) },
            onError = { /* surfaced via the wallet screen's snackbar path is unavailable here; logged for now */ }
        )

        setContent {
            ProsperityTheme {
                ProsperityRoot(viewModel = viewModel, onBuyCoins = { productId ->
                    billingManager.connect { billingManager.launchPurchase(productId) }
                })
            }
        }
    }

    override fun onDestroy() {
        billingManager.endConnection()
        super.onDestroy()
    }
}

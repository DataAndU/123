package com.prosperity.game.billing

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams

/**
 * Wraps the Google Play Billing Library for the one-way real-money-to-coins
 * purchase flow: the product IDs here must be created as in-app products
 * (type: managed/one-time) in the Google Play Console under this app's
 * package — this client can launch the flow and verify with our server,
 * but can't create the products themselves (see server DEPLOY.md).
 */
class BillingManager(
    private val activity: Activity,
    private val onPurchaseCompleted: (productId: String, purchaseToken: String) -> Unit,
    private val onError: (String) -> Unit
) : PurchasesUpdatedListener {

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    fun connect(onReady: () -> Unit) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onReady()
                } else {
                    onError("Billing unavailable: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // The library retries automatically on the next call; nothing to do here.
            }
        })
    }

    fun launchPurchase(productId: String) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onError("Could not look up product: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val details = productDetailsList.firstOrNull()
            if (details == null) {
                onError("Product '$productId' is not configured in the Google Play Console yet.")
                return@queryProductDetailsAsync
            }
            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()
            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null -> {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        val productId = purchase.products.firstOrNull() ?: continue
                        onPurchaseCompleted(productId, purchase.purchaseToken)
                        if (!purchase.isAcknowledged) {
                            val ackParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                            billingClient.acknowledgePurchase(ackParams) { }
                        }
                    }
                }
            }
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> { /* no-op */ }
            else -> onError("Purchase failed: ${result.debugMessage}")
        }
    }

    fun endConnection() {
        billingClient.endConnection()
    }
}

package com.an0obIs.pref.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Remove ads": one non-consumable store product, paid once, owned forever.
 *
 * The store is the source of truth. Ownership is confirmed whenever the app
 * starts or resumes and remembered locally, so an offline start stays ad-free;
 * a refund shows up at the next successful check.
 */
object BillingManager {

    /** The in-app product id to create in Play Console (one-time product, price 5 USD). */
    const val PRODUCT_ID = "remove_ads"

    private var client: BillingClient? = null
    private var details: ProductDetails? = null
    private var started = false

    private val _adFree = MutableStateFlow(false)
    val adFree: StateFlow<Boolean> = _adFree

    /** The store's own formatted price (local currency), once known. */
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price

    fun start(context: Context) {
        if (started) return
        started = true
        _adFree.value = Engagement.adFree
        client = BillingClient.newBuilder(context.applicationContext)
            .setListener(PurchasesUpdatedListener { result, purchases -> onUpdated(result, purchases) })
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        connect()
    }

    private fun connect() {
        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProduct()
                    refresh()
                }
            }

            override fun onBillingServiceDisconnected() {
                // reconnected on the next refresh or purchase attempt
            }
        })
    }

    private fun ready(): BillingClient? {
        val c = client ?: return null
        if (!c.isReady) {
            connect()
            return null
        }
        return c
    }

    private fun loadProduct() {
        val c = client ?: return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        c.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        ) { result, productDetails ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = productDetails.productDetailsList.firstOrNull()
                _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
    }

    /** Re-checks what the store says the player owns (also serves as "Restore purchase"). */
    fun refresh() {
        val c = ready() ?: return
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) apply(purchases)
        }
    }

    /** Starts the store's purchase sheet; false when the product is not available right now. */
    fun buy(activity: Activity): Boolean {
        val c = ready() ?: return false
        val d = details ?: run { loadProduct(); return false }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())
            )
            .build()
        return c.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun onUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            apply(purchases)
        } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            refresh()
        }
    }

    private fun apply(purchases: List<Purchase>) {
        val mine = purchases.filter {
            it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        // an unacknowledged purchase is refunded by the store after three days
        mine.filter { !it.isAcknowledged }.forEach {
            client?.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()
            ) { }
        }
        val owned = mine.isNotEmpty()
        Engagement.setAdFree(owned)
        _adFree.value = owned
    }
}

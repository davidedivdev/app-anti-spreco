package com.davide.salvacibo.monetization

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
import com.davide.salvacibo.AppConfig
import com.davide.salvacibo.BuildConfig
import com.davide.salvacibo.SupportProduct
import com.davide.salvacibo.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Acquisti in-app con Google Play Billing (obbligatorio per i pagamenti dentro l'app).
 * Tutti i prodotti sono "una tantum" (non consumabili): basta averne comprato uno per
 * togliere le pubblicità, e restano validi anche reinstallando l'app o cambiando telefono.
 */
class BillingManager(context: Context, private val prefs: Prefs) : PurchasesUpdatedListener {

    data class Item(
        val product: SupportProduct,
        val price: String? = null,
        val owned: Boolean = false,
        val details: ProductDetails? = null,
    )

    data class State(
        /** collegato a Google Play e prodotti caricati */
        val ready: Boolean = false,
        val items: List<Item> = AppConfig.SUPPORT_PRODUCTS.map { Item(it) },
        /** ha comprato almeno un prodotto → niente pubblicità */
        val isSupporter: Boolean = false,
        val purchasing: Boolean = false,
        /** messaggio da mostrare una volta (es. "Grazie!") */
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State(isSupporter = prefs.isSupporter))
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var connecting = false

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Si collega a Google Play (se serve) e aggiorna prezzi e acquisti. Chiamalo a ogni avvio/ritorno nell'app. */
    fun start() {
        if (!BuildConfig.BILLING_ENABLED) return
        if (client.isReady) {
            refresh()
            return
        }
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                }
            }

            override fun onBillingServiceDisconnected() {
                // con enableAutoServiceReconnection la libreria si ricollega da sola
                connecting = false
                _state.update { it.copy(ready = false) }
            }
        })
    }

    /** "Ripristina acquisti": rilegge da Google Play cosa hai comprato. */
    fun refresh() {
        queryProducts()
        queryPurchases()
    }

    fun buy(activity: Activity, productId: String) {
        val details = _state.value.items.firstOrNull { it.product.id == productId }?.details
        if (details == null) {
            message("Il Play Store non è raggiungibile in questo momento, riprova tra poco.")
            start()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken?.let { productParams.setOfferToken(it) }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            _state.update { it.copy(purchasing = true) }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    // ------------------------------------------------------------------------------------------

    private fun queryProducts() {
        val products = AppConfig.SUPPORT_PRODUCTS.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it.id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val byId = result.productDetailsList.associateBy { it.productId }
            _state.update { s ->
                s.copy(
                    ready = byId.isNotEmpty(),
                    items = s.items.map { item ->
                        val d = byId[item.product.id]
                        item.copy(
                            details = d,
                            price = d?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice,
                        )
                    },
                )
            }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases, isFullList = true)
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        _state.update { it.copy(purchasing = false) }
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> handlePurchases(purchases.orEmpty(), isFullList = false)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> message("Acquisto non riuscito, riprova più tardi.")
        }
    }

    /**
     * [isFullList] = true quando arriva l'elenco completo da Google Play: in quel caso
     * un acquisto rimborsato toglie lo stato di sostenitore.
     */
    private fun handlePurchases(purchases: List<Purchase>, isFullList: Boolean) {
        val owned = mutableSetOf<String>()
        var pending = false
        for (purchase in purchases) {
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    owned += purchase.products
                    if (!purchase.isAcknowledged) acknowledge(purchase)
                }
                Purchase.PurchaseState.PENDING -> pending = true
                else -> Unit
            }
        }
        val wasSupporter = _state.value.isSupporter
        val supporter = if (isFullList) owned.isNotEmpty() else wasSupporter || owned.isNotEmpty()
        prefs.isSupporter = supporter
        _state.update { s ->
            s.copy(
                isSupporter = supporter,
                items = s.items.map { item ->
                    item.copy(owned = item.product.id in owned || (!isFullList && item.owned))
                },
                message = when {
                    !isFullList && owned.isNotEmpty() -> "Grazie di cuore! 💚 Le pubblicità sono sparite per sempre."
                    !isFullList && pending -> "Pagamento in attesa: appena viene confermato le pubblicità spariscono."
                    else -> s.message
                },
            )
        }
    }

    /** Gli acquisti vanno confermati entro 3 giorni, altrimenti Google li rimborsa da solo. */
    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { }
    }

    private fun message(text: String) = _state.update { it.copy(message = text) }
}

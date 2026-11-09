package uk.hunttrack.core.data.packs

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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.hunttrack.domain.packs.RegionPackCatalog
import uk.hunttrack.domain.packs.SeasonPack

/**
 * Play Billing for country packs.
 *
 * Store products are NOT created by the app. Before a pack can be bought, create one
 * managed (one-time, non-consumable) product per country in Play Console, priced at £1.00:
 *
 * - hunttrack_pack_gb  United Kingdom (also included with the app until you decide to charge)
 * - hunttrack_pack_ie  Ireland
 * - hunttrack_pack_fr  France
 * - hunttrack_pack_de  Germany
 * - hunttrack_pack_ee  Estonia
 * - hunttrack_pack_lv  Latvia
 * - hunttrack_pack_lt  Lithuania
 * - hunttrack_pack_pl  Poland
 * - hunttrack_pack_ro  Romania
 *
 * Do not change the product ids after release. The price shown in the store replaces the
 * intended £1.00 label. Nothing here publishes a listing or creates a product.
 */
@Singleton
class PackBilling @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val details = mutableMapOf<String, ProductDetails>()
    private val listener = PurchasesUpdatedListener { result, purchases -> onPurchasesUpdated(result, purchases) }
    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .enableAutoServiceReconnection()
        .build()

    private val _state = MutableStateFlow(readState(billingReady = false, message = null))
    val state: StateFlow<PackBillingState> = _state.asStateFlow()

    init {
        connect()
    }

    fun isUnlocked(pack: SeasonPack): Boolean =
        pack.bundledWithApp || pack.productId in _state.value.ownedProductIds

    fun priceLabel(pack: SeasonPack): String =
        _state.value.storePrices[pack.productId] ?: "${pack.intendedPriceLabel} intended"

    fun connect() {
        if (client.isReady) {
            refreshProducts()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshProducts()
                } else {
                    _state.value = readState(
                        billingReady = false,
                        message = "Play Billing is not available on this device (${result.responseCode}). " +
                            "Packs stay locked except the UK pack included with the app.",
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(billingReady = false)
            }
        })
    }

    fun purchase(activity: Activity, pack: SeasonPack) {
        if (pack.bundledWithApp || isUnlocked(pack)) {
            _state.value = _state.value.copy(message = "${pack.countryName} is already available.")
            return
        }
        val product = details[pack.productId]
        if (!client.isReady || product == null) {
            _state.value = _state.value.copy(
                message = "Store product ID ${pack.productId} is not live. " +
                    "Create a £1.00 non-consumable in Play Console. Nothing was charged.",
            )
            return
        }
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build(),
                ),
            )
            .build()
        val result = client.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = _state.value.copy(message = "Purchase did not start (${result.responseCode}).")
        }
    }

    fun restore() {
        if (!client.isReady) {
            connect()
            _state.value = _state.value.copy(message = "Play Billing is not connected, so there is nothing to restore.")
            return
        }
        queryOwned(announce = true)
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            grant(purchases)
            _state.value = readState(billingReady = true, message = "Purchase recorded on this device.")
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _state.value = _state.value.copy(message = "Purchase cancelled.")
        } else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = _state.value.copy(message = "Purchase was not completed (${result.responseCode}).")
        }
    }

    private fun refreshProducts() {
        val products = RegionPackCatalog.seasonPacks.map { pack ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(pack.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details.clear()
                queryResult.productDetailsList.forEach { details[it.productId] = it }
            }
            queryOwned(announce = false)
        }
    }

    private fun queryOwned(announce: Boolean) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                grant(purchases)
            }
            val found = details.size
            val message = if (announce) {
                if (found == 0) {
                    "No store products are live yet. Create the hunttrack_pack_* products at £1.00. UK stays included."
                } else {
                    "Restored purchases from Play."
                }
            } else {
                _state.value.message
            }
            _state.value = readState(billingReady = true, message = message)
        }
    }

    private fun grant(purchases: List<Purchase>) {
        val owned = readOwned().toMutableSet()
        purchases.forEach { purchase ->
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach
            owned += purchase.products
            if (!purchase.isAcknowledged) {
                val ack = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.acknowledgePurchase(ack) { }
            }
        }
        prefs.edit().putStringSet(KEY_OWNED, owned).apply()
    }

    private fun readOwned(): Set<String> = prefs.getStringSet(KEY_OWNED, emptySet()).orEmpty()

    private fun readState(billingReady: Boolean, message: String?): PackBillingState {
        val prices = details.mapValues { (_, product) ->
            product.oneTimePurchaseOfferDetails?.formattedPrice ?: RegionPackCatalog.INTENDED_PRICE
        }
        return PackBillingState(
            ownedProductIds = readOwned(),
            storePrices = prices,
            billingReady = billingReady,
            message = message,
        )
    }

    private companion object {
        const val PREFS = "hunttrack_pack_purchases"
        const val KEY_OWNED = "owned_product_ids"
    }
}

data class PackBillingState(
    val ownedProductIds: Set<String> = emptySet(),
    val storePrices: Map<String, String> = emptyMap(),
    val billingReady: Boolean = false,
    val message: String? = null,
)

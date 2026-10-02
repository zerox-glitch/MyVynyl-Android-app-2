package com.vynyl.record.pro

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.vynyl.record.audio.MOODS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Free vs Pro — port of src/lib/pro.ts, with real Google Play Billing. */
object Free {
    const val MAX_SECONDS = 3 * 60
    const val MAX_RECORDS = 3
    /** times a free user may go back to the Studio to re-press one record */
    const val REEDITS = 3
    val presets = setOf("clean", "warm", "dusty")
    val crackles = setOf("preset", "crisp", "ticktick", "fireside", "rain")
    val music = setOf("none", "serenade", "piano", "hearth", "musicbox")
    val styles = setOf("ruby", "sapphire", "gold")
}
const val PRO_SECONDS = 20 * 60

enum class Gate { Preset, Crackle, Music, Style, Mood }

fun isFree(kind: Gate, id: String): Boolean = when (kind) {
    Gate.Preset -> id in Free.presets
    Gate.Crackle -> id in Free.crackles
    Gate.Music -> id in Free.music
    Gate.Style -> id in Free.styles
    Gate.Mood -> MOODS.firstOrNull { it.id == id }?.let {
        isFree(Gate.Preset, it.presetId) && isFree(Gate.Crackle, it.crackleId) && isFree(Gate.Music, it.musicId)
    } ?: false
}

enum class PlanId(val productId: String, val sub: Boolean) { Yearly("yearly", true), Monthly("monthly", true), Lifetime("lifetime", false) }
data class Plan(val id: PlanId, val name: String, val price: String, val per: String, val note: String?, val badge: String? = null)

val PLANS = listOf(
    Plan(PlanId.Yearly, "Yearly", "$24.99", "/ year", "Just $2.08 a month", "Best value · save 48%"),
    Plan(PlanId.Monthly, "Monthly", "$3.99", "/ month", "Cancel anytime"),
    Plan(PlanId.Lifetime, "Lifetime", "$49.99", "once", "Pay once, keep Pro forever"),
)

data class Perk(val title: String, val body: String)
val PERKS = listOf(
    Perk("Twenty-minute recordings", "Whole stories, songs and letters — not just three minutes."),
    Perk("Every mood, character & crackle", "Gramophone, war radio, TV broadcast, shellac and more."),
    Perk("The full music library", "Romantic, vintage and dance-band beds for every occasion."),
    Perk("Unlimited records", "Keep every voice on your shelf, not just three."),
    Perk("Gold nameplate & photo studio", "Glowing names on the plinth, crop and full-label photos."),
    Perk("Clean, untagged exports", "Full-quality WAV masters without the Vynyl tag."),
    Perk("Full-length 3D videos", "1080p MP4s of your spinning record — no watermark, no time limit."),
)

data class Entitlement(val pro: Boolean, val plan: PlanId? = null)

/** Global entitlement + paywall state. Google restores purchases per account; no server needed. */
object Pro : PurchasesUpdatedListener {
    private val _ent = MutableStateFlow(Entitlement(false))
    val entitlement: StateFlow<Entitlement> = _ent
    private val _paywall = MutableStateFlow<String?>(null)
    /** Non-null while the paywall is open; value is the reason line ("" for none). */
    val paywall: StateFlow<String?> = _paywall
    private val _prices = MutableStateFlow<Map<PlanId, String>>(emptyMap())
    /** Localised prices from Play, falling back to PLANS[].price. */
    val prices: StateFlow<Map<PlanId, String>> = _prices

    private lateinit var client: BillingClient
    private val details = mutableMapOf<PlanId, ProductDetails>()
    private var pending: ((Boolean) -> Unit)? = null

    fun openPaywall(reason: String? = null) { _paywall.value = reason ?: "" }
    fun closePaywall() { _paywall.value = null }

    /** Testing helper to unlock/lock Pro during development and testing. */
    fun setProForTesting(pro: Boolean) {
        setEnt(Entitlement(pro, if (pro) PlanId.Lifetime else null))
    }

    fun init(ctx: Context) {
        val prefs = ctx.getSharedPreferences("vynyl", Context.MODE_PRIVATE)
        // Cached so the app opens instantly offline; Play is the source of truth on connect.
        prefs.getString("plan", null)?.let { p -> _ent.value = Entitlement(true, PlanId.entries.firstOrNull { it.productId == p }) }
        client = BillingClient.newBuilder(ctx).setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build()
        cache = { e -> prefs.edit().apply { if (e.pro) putString("plan", e.plan?.productId ?: "pro") else remove("plan") }.apply() }
        connect { queryProducts(); restore {} }
    }

    private var cache: (Entitlement) -> Unit = {}
    private fun setEnt(e: Entitlement) { _ent.value = e; cache(e) }

    private fun connect(then: () -> Unit) {
        if (client.isReady) return then()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) { if (r.responseCode == BillingClient.BillingResponseCode.OK) then() }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryProducts() {
        fun q(type: String, ids: List<PlanId>) {
            val params = QueryProductDetailsParams.newBuilder().setProductList(ids.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId).setProductType(type).build()
            }).build()
            client.queryProductDetailsAsync(params) { _, list ->
                list.forEach { pd ->
                    val id = PlanId.entries.first { it.productId == pd.productId }
                    details[id] = pd
                    val price = pd.oneTimePurchaseOfferDetails?.formattedPrice
                        ?: pd.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                    if (price != null) _prices.value = _prices.value + (id to price)
                }
            }
        }
        q(BillingClient.ProductType.SUBS, listOf(PlanId.Monthly, PlanId.Yearly))
        q(BillingClient.ProductType.INAPP, listOf(PlanId.Lifetime))
    }

    fun purchase(activity: Activity, plan: PlanId, done: (Boolean) -> Unit) = connect {
        val pd = details[plan] ?: return@connect activity.runOnUiThread { done(false) }
        val b = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd)
        pd.subscriptionOfferDetails?.firstOrNull()?.let { b.setOfferToken(it.offerToken) }
        pending = done
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(b.build())).build())
    }

    /** Asks Play what this Google account owns. */
    fun restore(done: (Boolean) -> Unit) = connect {
        var owned: PlanId? = null
        var left = 2
        fun collect(list: List<Purchase>) {
            list.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
                acknowledge(p)
                p.products.forEach { pid -> PlanId.entries.firstOrNull { it.productId == pid }?.let { if (owned != PlanId.Lifetime) owned = it } }
            }
            if (--left == 0) { setEnt(Entitlement(owned != null, owned)); done(owned != null) }
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { _, l -> collect(l) }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { _, l -> collect(l) }
    }

    private fun acknowledge(p: Purchase) {
        if (!p.isAcknowledged) client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {}
    }

    override fun onPurchasesUpdated(r: BillingResult, list: MutableList<Purchase>?) {
        val ok = r.responseCode == BillingClient.BillingResponseCode.OK && !list.isNullOrEmpty()
        list?.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }?.forEach { p ->
            acknowledge(p)
            val plan = PlanId.entries.firstOrNull { it.productId in p.products }
            setEnt(Entitlement(true, plan))
        }
        pending?.invoke(ok && _ent.value.pro); pending = null
    }
}

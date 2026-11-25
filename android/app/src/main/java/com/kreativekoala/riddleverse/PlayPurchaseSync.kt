package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A Play purchase the server has not given a final answer for. [userId] is the account it was made (or found) under. */
data class PendingPurchase(val token: String, val productId: String, val kind: String, val userId: String, val orderId: String?) {
    fun toJson(): String = JSONObject().put("productId", productId).put("kind", kind).put("userId", userId).put("orderId", orderId ?: "").toString()

    /** The request body of the endpoint for this kind. No amounts: the server decides what a product is worth. */
    fun requestBody(): JSONObject = JSONObject().put("userId", userId).put("purchaseToken", token).apply {
        if (kind == COINS) put("productId", productId).put("transactionId", orderId ?: "").put("platform", "android")
    }

    val url: String
        get() = if (kind == COINS) "https://puzzleverseai.com/api/coins/purchase" else "https://puzzleverseai.com/api/subscription-purchase"

    companion object {
        const val COINS = "coins"
        const val SUBSCRIPTION = "subscription"

        fun fromJson(token: String, json: String): PendingPurchase? = try {
            val o = JSONObject(json)
            PendingPurchase(token, o.getString("productId"), o.getString("kind"), o.getString("userId"), o.optString("orderId").ifEmpty { null })
        } catch (_: Exception) { null }
    }
}

enum class PurchaseReport { DONE, REJECTED, RETRY }

object PurchaseReportRules {
    /**
     * Only an explicit answer ends the retries: success, or a refusal the server marks as final. Anything else (signed out,
     * verification unavailable, payment pending, rate limit, server error) is tried again later.
     */
    fun outcome(code: Int, success: Boolean, retryable: Boolean?): PurchaseReport = when {
        code in 200..299 && success -> PurchaseReport.DONE
        (code == 400 || code == 409) && retryable == false -> PurchaseReport.REJECTED
        else -> PurchaseReport.RETRY
    }
}

/**
 * Sends purchase tokens to the server until each one is credited or refused. Purchases are saved before the first attempt,
 * and purchases Google Play still lists for this device (an active subscription, a pack not yet consumed) are picked up
 * again: at most twice a day, or at once on Restore.
 */
object PlayPurchaseSync {
    private const val TAG = "PlayPurchaseSync"
    private const val PREFS = "pending_purchases"
    private const val RESTORED_PREFS = "purchase_restore"
    private val RESTORE_EVERY_MS = TimeUnit.HOURS.toMillis(12)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val running = Mutex()
    private val http by lazy { FailureReporterInterceptor.newClient() }

    /** Told about each answer for a coin pack, so the store screen can update. */
    @Volatile var coinListener: ((PurchaseReport, JSONObject) -> Unit)? = null

    private fun store(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Saves a purchase unless its token is already waiting (it then stays with the account that had it first). */
    fun remember(context: Context, purchase: PendingPurchase) {
        if (purchase.token.isEmpty()) return
        val prefs = store(context)
        if (!prefs.contains(purchase.token)) prefs.edit().putString(purchase.token, purchase.toJson()).apply()
    }

    fun sync(context: Context, restore: Boolean = false) {
        val app = context.applicationContext
        scope.launch {
            try {
                running.withLock { run(app, restore) }
            } catch (e: Exception) {
                Log.w(TAG, "sync failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun run(context: Context, restore: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous } ?: return
        val restored = context.getSharedPreferences(RESTORED_PREFS, Context.MODE_PRIVATE)
        if (restore || System.currentTimeMillis() - restored.getLong(user.uid, 0L) >= RESTORE_EVERY_MS) {
            val owned = ownedPurchases(context, user.uid)
            if (owned != null) {
                owned.forEach { remember(context, it) }
                restored.edit().putLong(user.uid, System.currentTimeMillis()).apply()
            }
        }
        val prefs = store(context)
        val waiting = prefs.all.mapNotNull { (token, json) -> PendingPurchase.fromJson(token, json as? String ?: return@mapNotNull null) }
            .filter { it.userId == user.uid }
        if (waiting.isEmpty()) return
        val idToken = Tasks.await(user.getIdToken(false), 20, TimeUnit.SECONDS).token ?: return
        waiting.forEach { purchase ->
            var answer = JSONObject()
            val outcome = try {
                val request = Request.Builder().url(purchase.url).header("Authorization", "Bearer $idToken")
                    .post(purchase.requestBody().toString().toRequestBody("application/json".toMediaType())).build()
                http.newCall(request).execute().use { response ->
                    answer = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
                    PurchaseReportRules.outcome(
                        response.code, answer.optBoolean("success", false),
                        if (answer.has("retryable")) answer.optBoolean("retryable") else null
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not reach the server: ${e.javaClass.simpleName}")
                PurchaseReport.RETRY
            }
            if (outcome != PurchaseReport.RETRY) prefs.edit().remove(purchase.token).apply()
            if (purchase.kind == PendingPurchase.COINS) coinListener?.invoke(outcome, answer)
        }
    }

    /** What Google Play lists as owned on this device, or null if Play could not be asked. */
    private suspend fun ownedPurchases(context: Context, userId: String): List<PendingPurchase>? {
        val client = BillingClient.newBuilder(context)
            .setListener { _, _ -> } // purchases are launched through RevenueCat; this client only reads
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        return try {
            withTimeoutOrNull(15_000L) {
                val connected = CompletableDeferred<Boolean>()
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        connected.complete(result.responseCode == BillingClient.BillingResponseCode.OK)
                    }

                    override fun onBillingServiceDisconnected() { connected.complete(false) }
                })
                if (!connected.await()) return@withTimeoutOrNull null
                val found = ArrayList<PendingPurchase>()
                for ((type, kind) in listOf(BillingClient.ProductType.SUBS to PendingPurchase.SUBSCRIPTION, BillingClient.ProductType.INAPP to PendingPurchase.COINS)) {
                    val answer = CompletableDeferred<List<Purchase>?>()
                    client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, purchases ->
                        answer.complete(if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases else null)
                    }
                    val purchases = answer.await() ?: return@withTimeoutOrNull null
                    purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
                        val productId = p.products.firstOrNull() ?: return@forEach
                        if (kind == PendingPurchase.COINS && CoinManager.COIN_PACKS.none { it.productId == productId }) return@forEach
                        found.add(PendingPurchase(p.purchaseToken, productId, kind, userId, p.orderId))
                    }
                }
                found
            }
        } finally {
            try { client.endConnection() } catch (_: Exception) { }
        }
    }
}

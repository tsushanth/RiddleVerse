package com.kreativekoala.riddleverse

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.GetStoreProductsCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class CoinManager private constructor() {
    companion object {
        val shared = CoinManager()
        const val CONTINUE_COST = 10
        const val CUSTOMIZE_COST = 10
        const val REMIX_COST = 15
        const val MAX_DIFFICULTY_LEVEL = 5

        fun difficultyCost(level: Int): Int {
            return when (level) {
                2 -> 15
                3 -> 20
                4 -> 25
                5 -> 30
                else -> 10
            }
        }

        val COIN_PACKS = listOf(
            CoinPack("com.kreativekoala.riddleverse.coins.100", 100, "100 Coins"),
            CoinPack("com.kreativekoala.riddleverse.coins.500", 500, "500 Coins"),
            CoinPack("com.kreativekoala.riddleverse.coins.1200", 1200, "1,200 Coins")
        )
    }

    data class CoinPack(val productId: String, val coins: Int, val label: String)

    data class GameEarning(
        val gameId: String,
        val gameTitle: String,
        val totalCoinsEarned: Int,
        val totalPlaysMonetized: Int
    )

    data class CreatorEarningsData(
        val totalCoinsEarned: Int,
        val totalPlaysMonetized: Int,
        val gameBreakdown: List<GameEarning>
    )

    data class PayoutEligibility(
        val eligible: Boolean,
        val earnedBalance: Int,
        val minThreshold: Int,
        val conversionRate: Double,
        val stripeConnected: Boolean,
        val stripePayoutsEnabled: Boolean,
        val detailsSubmitted: Boolean,
        val usdAmount: Double
    )

    data class PayoutRequest(
        val id: String,
        val coinsAmount: Int,
        val usdAmount: Double,
        val status: String,
        val createdAt: String,
        val completedAt: String?
    )

    private val TAG = "CoinManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var storeProducts: List<StoreProduct> = emptyList()

    var balance by mutableIntStateOf(0)
    var isPurchasing by mutableStateOf(false)
    var purchaseMessage by mutableStateOf<String?>(null)
    var creatorEarnings by mutableStateOf<CreatorEarningsData?>(null)
    var isLoadingEarnings by mutableStateOf(false)
    var payoutEligibility by mutableStateOf<PayoutEligibility?>(null)
    var isLoadingPayout by mutableStateOf(false)
    var payoutHistory by mutableStateOf<List<PayoutRequest>>(emptyList())
    var payoutMessage by mutableStateOf<String?>(null)

    val canContinue: Boolean get() = balance >= CONTINUE_COST
    val canAffordRemix: Boolean get() = balance >= REMIX_COST

    fun canAffordDifficulty(level: Int): Boolean = balance >= difficultyCost(level)

    // MARK: - Balance

    fun fetchBalance() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/coins/balance?userId=$userId")
                        .get()
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val body = response.body?.string()
                    response.close()

                    if (body != null) {
                        val json = JSONObject(body)
                        val bal = json.optInt("balance", 0)
                        withContext(Dispatchers.Main) { balance = bal }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fetch balance error", e)
                }
            }
        }
    }

    // MARK: - Spend

    fun spendForContinue(
        gameId: String,
        creatorId: String?,
        onResult: (Boolean) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) { onResult(false); return }

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val json = JSONObject().apply {
                        put("userId", userId)
                        put("reason", "continue_play")
                        put("gameId", gameId)
                        put("platform", "android")
                        if (creatorId != null && creatorId.isNotEmpty()) {
                            put("creatorId", creatorId)
                        }
                    }

                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/coins/spend")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    response.close()

                    if (responseBody != null) {
                        val result = JSONObject(responseBody)
                        val success = result.optBoolean("success", false)
                        if (success) {
                            val newBalance = result.optInt("newBalance", balance)
                            withContext(Dispatchers.Main) {
                                balance = newBalance
                                onResult(true)
                            }
                        } else {
                            withContext(Dispatchers.Main) { onResult(false) }
                        }
                    } else {
                        withContext(Dispatchers.Main) { onResult(false) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Spend error", e)
                    withContext(Dispatchers.Main) { onResult(false) }
                }
            }
        }
    }

    fun spendForRemix(
        gameId: String,
        creatorId: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) { onResult(false); return }
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val json = JSONObject().apply {
                        put("userId", userId)
                        put("reason", "remix")
                        put("gameId", gameId)
                        put("platform", "android")
                        if (creatorId != null && creatorId.isNotEmpty()) put("creatorId", creatorId)
                    }
                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/coins/spend")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()
                    val response = HttpClientProvider.client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    response.close()
                    if (responseBody != null) {
                        val result = JSONObject(responseBody)
                        val success = result.optBoolean("success", false)
                        if (success) {
                            val newBalance = result.optInt("newBalance", balance)
                            withContext(Dispatchers.Main) { balance = newBalance; onResult(true) }
                        } else {
                            withContext(Dispatchers.Main) { onResult(false) }
                        }
                    } else {
                        withContext(Dispatchers.Main) { onResult(false) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Spend remix error", e)
                    withContext(Dispatchers.Main) { onResult(false) }
                }
            }
        }
    }

    fun spendForHarderChallenge(
        gameId: String,
        difficultyLevel: Int,
        creatorId: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) { onResult(false); return }

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val json = JSONObject().apply {
                        put("userId", userId)
                        put("reason", "harder_challenge")
                        put("gameId", gameId)
                        put("difficultyLevel", difficultyLevel)
                        put("platform", "android")
                        if (!creatorId.isNullOrEmpty() && creatorId != userId) {
                            put("creatorId", creatorId)
                        }
                    }

                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/coins/spend")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    response.close()

                    if (responseBody != null) {
                        val result = JSONObject(responseBody)
                        val success = result.optBoolean("success", false)
                        if (success) {
                            val newBalance = result.optInt("newBalance", balance)
                            withContext(Dispatchers.Main) {
                                balance = newBalance
                                onResult(true)
                            }
                        } else {
                            withContext(Dispatchers.Main) { onResult(false) }
                        }
                    } else {
                        withContext(Dispatchers.Main) { onResult(false) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Spend harder challenge error", e)
                    withContext(Dispatchers.Main) { onResult(false) }
                }
            }
        }
    }

    fun canAffordCustomize(): Boolean = balance >= CUSTOMIZE_COST

    fun spendForCustomize(
        gameId: String,
        creatorId: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) { onResult(false); return }

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val json = JSONObject().apply {
                        put("userId", userId)
                        put("reason", "customize")
                        put("gameId", gameId)
                        put("platform", "android")
                        if (!creatorId.isNullOrEmpty() && creatorId != userId) {
                            put("creatorId", creatorId)
                        }
                    }

                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/coins/spend")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    response.close()

                    if (responseBody != null) {
                        val result = JSONObject(responseBody)
                        val success = result.optBoolean("success", false)
                        if (success) {
                            val newBalance = result.optInt("newBalance", balance)
                            withContext(Dispatchers.Main) {
                                balance = newBalance
                                onResult(true)
                            }
                        } else {
                            withContext(Dispatchers.Main) { onResult(false) }
                        }
                    } else {
                        withContext(Dispatchers.Main) { onResult(false) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Spend customize error", e)
                    withContext(Dispatchers.Main) { onResult(false) }
                }
            }
        }
    }

    // MARK: - Has Played Check (server-side, survives reinstalls)

    fun checkHasPlayed(
        gameId: String,
        onResult: (Boolean) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) { onResult(false); return }

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/games/$gameId/has-played?userId=$userId")
                        .get()
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val body = response.body?.string()
                    response.close()

                    if (body != null) {
                        val json = JSONObject(body)
                        val hasPlayed = json.optBoolean("hasPlayed", false)
                        withContext(Dispatchers.Main) { onResult(hasPlayed) }
                    } else {
                        withContext(Dispatchers.Main) { onResult(false) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Check has-played error", e)
                    withContext(Dispatchers.Main) { onResult(false) }
                }
            }
        }
    }

    // MARK: - Creator Earnings

    fun fetchCreatorEarnings() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        isLoadingEarnings = true

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/coins/earnings?creatorId=$userId")
                        .get()
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val body = response.body?.string()
                    response.close()

                    if (body != null) {
                        val json = JSONObject(body)
                        if (json.optBoolean("success", false)) {
                            val totalEarned = json.optInt("totalCoinsEarned", 0)
                            val totalPlays = json.optInt("totalPlaysMonetized", 0)
                            val breakdownArray = json.optJSONArray("gameBreakdown")
                            val games = mutableListOf<GameEarning>()

                            if (breakdownArray != null) {
                                for (i in 0 until breakdownArray.length()) {
                                    val g = breakdownArray.getJSONObject(i)
                                    games.add(GameEarning(
                                        gameId = g.optString("game_id", ""),
                                        gameTitle = g.optString("game_title", "Untitled Game"),
                                        totalCoinsEarned = g.optInt("total_coins_earned", 0),
                                        totalPlaysMonetized = g.optInt("total_plays_monetized", 0)
                                    ))
                                }
                            }

                            withContext(Dispatchers.Main) {
                                creatorEarnings = CreatorEarningsData(totalEarned, totalPlays, games)
                                isLoadingEarnings = false
                            }
                        } else {
                            withContext(Dispatchers.Main) { isLoadingEarnings = false }
                        }
                    } else {
                        withContext(Dispatchers.Main) { isLoadingEarnings = false }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fetch creator earnings error", e)
                    withContext(Dispatchers.Main) { isLoadingEarnings = false }
                }
            }
        }
    }

    // MARK: - Payouts

    fun checkPayoutEligibility() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/payouts/eligibility?creatorId=$userId")
                        .get()
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val body = response.body?.string()
                    response.close()

                    if (body != null) {
                        val json = JSONObject(body)
                        if (json.optBoolean("success", false)) {
                            val eligibility = PayoutEligibility(
                                eligible = json.optBoolean("eligible", false),
                                earnedBalance = json.optInt("earnedBalance", 0),
                                minThreshold = json.optInt("minThreshold", 1000),
                                conversionRate = json.optDouble("conversionRate", 0.007),
                                stripeConnected = json.optBoolean("stripeConnected", false),
                                stripePayoutsEnabled = json.optBoolean("stripePayoutsEnabled", false),
                                detailsSubmitted = json.optBoolean("detailsSubmitted", false),
                                usdAmount = json.optDouble("usdAmount", 0.0)
                            )
                            withContext(Dispatchers.Main) { payoutEligibility = eligibility }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Check payout eligibility error", e)
                }
            }
        }
    }

    fun connectStripe(onUrl: (String) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid

        isLoadingPayout = true
        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            val idToken = tokenResult.token ?: return@addOnSuccessListener
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val json = JSONObject().apply {
                            put("creatorId", userId)
                        }
                        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                        val request = Request.Builder()
                            .url("https://puzzleverseai.com/api/payouts/connect-stripe")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Authorization", "Bearer $idToken")
                            .post(body)
                            .build()

                        val response = HttpClientProvider.client.newCall(request).execute()
                        val responseBody = response.body?.string()
                        response.close()

                        if (responseBody != null) {
                            val result = JSONObject(responseBody)
                            if (result.optBoolean("success", false)) {
                                val url = result.optString("url", "")
                                withContext(Dispatchers.Main) {
                                    isLoadingPayout = false
                                    if (url.isNotEmpty()) onUrl(url)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    isLoadingPayout = false
                                    payoutMessage = result.optString("error", "Failed to connect Stripe")
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) { isLoadingPayout = false }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Connect Stripe error", e)
                        withContext(Dispatchers.Main) {
                            isLoadingPayout = false
                            payoutMessage = "Connection failed"
                        }
                    }
                }
            }
        }.addOnFailureListener {
            isLoadingPayout = false
            payoutMessage = "Authentication failed"
        }
    }

    fun checkStripeStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/payouts/stripe-status?creatorId=$userId")
                        .get()
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val body = response.body?.string()
                    response.close()

                    if (body != null) {
                        val json = JSONObject(body)
                        if (json.optBoolean("success", false)) {
                            // Refresh eligibility with latest stripe status
                            checkPayoutEligibility()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Check Stripe status error", e)
                }
            }
        }
    }

    fun requestPayout(coinsAmount: Int, onResult: (Boolean, String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            onResult(false, "Not signed in")
            return
        }
        val userId = user.uid

        isLoadingPayout = true
        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            val idToken = tokenResult.token ?: return@addOnSuccessListener
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val json = JSONObject().apply {
                            put("creatorId", userId)
                            put("coinsAmount", coinsAmount)
                        }
                        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                        val request = Request.Builder()
                            .url("https://puzzleverseai.com/api/payouts/request")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Authorization", "Bearer $idToken")
                            .post(body)
                            .build()

                        val response = HttpClientProvider.client.newCall(request).execute()
                        val responseBody = response.body?.string()
                        response.close()

                        if (responseBody != null) {
                            val result = JSONObject(responseBody)
                            val success = result.optBoolean("success", false)
                            withContext(Dispatchers.Main) {
                                isLoadingPayout = false
                                if (success) {
                                    val newBalance = result.optInt("newBalance", balance)
                                    balance = newBalance
                                    payoutMessage = "Payout requested! \$${result.optDouble("usdAmount", 0.0)} is on the way."
                                    checkPayoutEligibility()
                                    fetchPayoutHistory()
                                    onResult(true, null)
                                } else {
                                    val error = result.optString("error", "Payout failed")
                                    payoutMessage = error
                                    onResult(false, error)
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                isLoadingPayout = false
                                onResult(false, "No response")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Request payout error", e)
                        withContext(Dispatchers.Main) {
                            isLoadingPayout = false
                            onResult(false, "Request failed")
                        }
                    }
                }
            }
        }.addOnFailureListener {
            isLoadingPayout = false
            onResult(false, "Authentication failed")
        }
    }

    fun fetchPayoutHistory() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/payouts/history?creatorId=$userId")
                        .get()
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val body = response.body?.string()
                    response.close()

                    if (body != null) {
                        val json = JSONObject(body)
                        if (json.optBoolean("success", false)) {
                            val payoutsArray = json.optJSONArray("payouts")
                            val payouts = mutableListOf<PayoutRequest>()

                            if (payoutsArray != null) {
                                for (i in 0 until payoutsArray.length()) {
                                    val p = payoutsArray.getJSONObject(i)
                                    payouts.add(PayoutRequest(
                                        id = p.optString("id", ""),
                                        coinsAmount = p.optInt("coins_amount", 0),
                                        usdAmount = p.optDouble("usd_amount", 0.0),
                                        status = p.optString("status", "unknown"),
                                        createdAt = p.optString("created_at", ""),
                                        completedAt = p.optString("completed_at", null)
                                    ))
                                }
                            }

                            withContext(Dispatchers.Main) { payoutHistory = payouts }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fetch payout history error", e)
                }
            }
        }
    }

    // MARK: - RevenueCat Coin Purchases

    fun loadCoinProducts() {
        val productIds = COIN_PACKS.map { it.productId }
        Purchases.sharedInstance.getProducts(
            productIds,
            ProductType.INAPP,
            object : GetStoreProductsCallback {
                override fun onReceived(products: List<StoreProduct>) {
                    storeProducts = products
                    Log.d(TAG, "Loaded ${products.size} coin products")
                }

                override fun onError(error: PurchasesError) {
                    Log.e(TAG, "Failed to load coin products: ${error.message}")
                }
            }
        )
    }

    fun getProductPrice(productId: String): String? {
        return storeProducts
            .firstOrNull { it.id == productId }
            ?.price
            ?.formatted
    }

    fun launchPurchase(activity: Activity, pack: CoinPack) {
        val product = storeProducts.firstOrNull { it.id == pack.productId }
        if (product == null) {
            purchaseMessage = "Product not available"
            return
        }

        isPurchasing = true
        Purchases.sharedInstance.purchase(
            PurchaseParams.Builder(activity, product).build(),
            object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    Log.d(TAG, "Coin purchase successful: ${pack.productId}")
                    isPurchasing = false
                    recordPurchaseOnBackend(pack, storeTransaction.orderId ?: "")
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    isPurchasing = false
                    if (!userCancelled) {
                        purchaseMessage = "Purchase failed: ${error.message}"
                        Log.e(TAG, "Coin purchase failed: ${error.message}")
                    }
                }
            }
        )
    }

    private fun recordPurchaseOnBackend(pack: CoinPack, transactionId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val json = JSONObject().apply {
                        put("userId", userId)
                        put("productId", pack.productId)
                        put("transactionId", transactionId)
                        put("platform", "android")
                    }

                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/coins/purchase")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    response.close()

                    if (responseBody != null) {
                        val result = JSONObject(responseBody)
                        val newBalance = result.optInt("newBalance", balance)
                        withContext(Dispatchers.Main) {
                            balance = newBalance
                            purchaseMessage = "Coins added!"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Record purchase error", e)
                }
            }
        }
    }
}

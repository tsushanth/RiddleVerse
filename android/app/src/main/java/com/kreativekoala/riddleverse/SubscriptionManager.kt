package com.kreativekoala.riddleverse

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.GetStoreProductsCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// MARK: - Subscription Tier Enum
// Note: PREMIUM = monthly billing, PREMIUM_YEARLY = yearly billing
// Both are the same "Premium" tier, just different billing periods
enum class SubscriptionTier(val value: String) {
    FREE("free"),
    PREMIUM("premium"),           // Monthly billing
    PREMIUM_YEARLY("premium_yearly");  // Yearly billing (better value)

    val displayName: String
        get() = when (this) {
            FREE -> "Free"
            PREMIUM -> "Premium Monthly"
            PREMIUM_YEARLY -> "Premium Yearly"
        }

    val dailyLimit: Int
        get() = when (this) {
            FREE -> 10
            PREMIUM, PREMIUM_YEARLY -> -1 // Unlimited for both Premium billing options
        }

    val monthlyLimit: Int
        get() = when (this) {
            FREE -> 100
            PREMIUM, PREMIUM_YEARLY -> -1 // Unlimited for both Premium billing options
        }

    val price: String
        get() = when (this) {
            FREE -> "Free"
            PREMIUM -> "$5.99/month"
            PREMIUM_YEARLY -> "$24.99/year"
        }

    val productId: String
        get() = when (this) {
            FREE -> ""
            PREMIUM -> "com.kreativekoala.riddleverse.premium.mo"
            PREMIUM_YEARLY -> "com.kreativekoala.riddleverse.premium.yr"
        }

    // Monthly coins — intentionally above equivalent coin-pack cost so subscribers
    // always feel they're getting more than they paid for.
    val monthlyCoins: Int
        get() = when (this) {
            FREE -> 0
            PREMIUM -> 150       // Small pack = 100 coins; subscribers get 50% more free
            PREMIUM_YEARLY -> 700 // Medium pack = 500; subscribers get 40% more free
        }

    // One-time signup bonus on first purchase
    val signupBonus: Int
        get() = when (this) {
            FREE -> 0
            PREMIUM -> 150       // First month effectively 300 coins total
            PREMIUM_YEARLY -> 500 // First month effectively 1,200 coins total
        }

    val benefits: List<String>
        get() = when (this) {
            FREE -> listOf(
                "Access to all puzzle types",
                "10 puzzle generations per day",
                "Ad-supported experience",
                "Earn coins through gameplay"
            )
            PREMIUM -> listOf(
                "Ad-free experience",
                "150 free coins every month + 150 bonus on signup",
                "Unlimited puzzle generations",
                "Priority support",
                "Premium puzzle themes"
            )
            PREMIUM_YEARLY -> listOf(
                "Ad-free experience",
                "700 free coins every month + 500 bonus on signup",
                "Unlimited puzzle generations",
                "Priority support",
                "Premium puzzle themes",
                "Best value — save 65%"
            )
        }

    companion object {
        fun fromString(value: String): SubscriptionTier {
            // Handle legacy "unlimited" value for backwards compatibility
            if (value == "unlimited") return PREMIUM_YEARLY
            return values().find { it.value == value } ?: FREE
        }
    }
}

// MARK: - Purchase State
sealed class PurchaseState {
    object Idle : PurchaseState()
    object Purchasing : PurchaseState()
    object Success : PurchaseState()
    data class Failed(val error: String) : PurchaseState()
    object Cancelled : PurchaseState()
}

// MARK: - Subscription Manager (RevenueCat)
class SubscriptionManager private constructor(
    private val context: Context
) {
    private val TAG = "SubscriptionManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val preferences: SharedPreferences =
        context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)

    // State variables for Compose
    var currentTier by mutableStateOf(SubscriptionTier.FREE)
        private set

    var purchaseState by mutableStateOf<PurchaseState>(PurchaseState.Idle)
        private set

    var availablePackages by mutableStateOf<List<Package>>(emptyList())
        private set

    private val gson = Gson()
    private val httpClient = OkHttpClient()

    companion object {
        @Volatile
        private var INSTANCE: SubscriptionManager? = null

        fun getInstance(context: Context): SubscriptionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SubscriptionManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    init {
        loadCurrentSubscriptionStatus()
        loadProducts()
        refreshCustomerInfo()
    }

    // MARK: - Public Methods

    fun loadProducts() {
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) {
                val current = offerings.current
                if (current != null) {
                    availablePackages = current.availablePackages
                    Log.d(TAG, "Loaded ${availablePackages.size} packages from RevenueCat")
                    availablePackages.forEach { pkg ->
                        Log.d(TAG, "Package: ${pkg.identifier} - ${pkg.product.price}")
                    }
                } else {
                    Log.w(TAG, "No current offering configured in RevenueCat")
                }
            }

            override fun onError(error: PurchasesError) {
                Log.e(TAG, "Failed to load offerings: ${error.message}")
            }
        })
    }

    fun purchasePackage(activity: Activity, pkg: Package) {
        if (purchaseState == PurchaseState.Purchasing) return
        purchaseState = PurchaseState.Purchasing

        Purchases.sharedInstance.purchase(
            PurchaseParams.Builder(activity, pkg).build(),
            object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    Log.d(TAG, "Purchase successful: ${storeTransaction.productIds}")
                    updateTierFromCustomerInfo(customerInfo)
                    purchaseState = PurchaseState.Success

                    // Track purchase events for ad attribution and LTV reporting
                    val productId = storeTransaction.productIds.firstOrNull() ?: ""
                    val price = pkg.product.price.amountMicros / 1_000_000.0
                    val currencyCode = pkg.product.price.currencyCode
                    AnalyticsManager.getInstance()?.track(AnalyticsEvent.subscriptionPurchase(currentTier.name, price, currencyCode))
                    TikTokHelper.trackEvent("purchase_success", mapOf("product_id" to productId))

                    notifyBackendOfPurchase(storeTransaction)
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    if (userCancelled) {
                        purchaseState = PurchaseState.Cancelled
                    } else {
                        purchaseState = PurchaseState.Failed(error.message)
                        Log.e(TAG, "Purchase failed: ${error.message}")
                    }
                }
            }
        )
    }

    fun restorePurchases() {
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                updateTierFromCustomerInfo(customerInfo)
                Log.d(TAG, "Purchases restored successfully")
            }

            override fun onError(error: PurchasesError) {
                purchaseState = PurchaseState.Failed("Failed to restore purchases: ${error.message}")
                Log.e(TAG, "Restore failed: ${error.message}")
            }
        })
    }

    fun getPackageForTier(tier: SubscriptionTier): Package? {
        // Match by RevenueCat package identifier (e.g. $rc_monthly, $rc_annual)
        val packageKey = when (tier) {
            SubscriptionTier.PREMIUM -> "\$rc_monthly"
            SubscriptionTier.PREMIUM_YEARLY -> "\$rc_annual"
            SubscriptionTier.FREE -> return null
        }
        return availablePackages.find { it.identifier == packageKey }
    }

    /** Fetch packages for a specific offering by identifier (for manual offering selection). */
    fun getOfferingPackages(identifier: String, callback: (List<Package>) -> Unit) {
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) {
                val packages = offerings.getOffering(identifier)?.availablePackages ?: emptyList()
                callback(packages)
            }

            override fun onError(error: PurchasesError) {
                Log.e(TAG, "Failed to load offering $identifier: ${error.message}")
                callback(emptyList())
            }
        })
    }

    fun hasActiveSubscription(): Boolean {
        return currentTier != SubscriptionTier.FREE || isMagicLinkPremium()
    }

    /**
     * Returns true if the current user authenticated via a magic link (Telegram/WhatsApp bot),
     * which grants premium-equivalent access.
     */
    fun isMagicLinkPremium(): Boolean {
        return MagicLinkAuthManager.isMagicLinkSession(context)
    }

    fun resetPurchaseState() {
        purchaseState = PurchaseState.Idle
    }

    fun canGeneratePuzzles(puzzleType: String): Boolean {
        val limitManager = RegenerationLimitManager.getInstance(context)
        return !limitManager.hasActiveLimits(puzzleType)
    }

    fun shouldShowAds(): Boolean {
        return currentTier == SubscriptionTier.FREE && !isMagicLinkPremium()
    }

    fun clearLimitsAfterUpgrade() {
        val limitManager = RegenerationLimitManager.getInstance(context)
        limitManager.clearAllLimits()
        Log.d(TAG, "Cleared all cached limit info after subscription upgrade")
    }

    // MARK: - Private Methods

    private fun refreshCustomerInfo() {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                updateTierFromCustomerInfo(customerInfo)
            }

            override fun onError(error: PurchasesError) {
                Log.e(TAG, "Failed to get customer info: ${error.message}")
            }
        })
    }

    internal fun updateTierFromCustomerInfo(customerInfo: CustomerInfo) {
        val premiumEntitlement = customerInfo.entitlements["premium"]
        val oldTier = currentTier

        currentTier = if (premiumEntitlement?.isActive == true) {
            // Determine monthly vs yearly from the product ID
            val productId = premiumEntitlement.productIdentifier
            getTierFromProductId(productId)
        } else {
            SubscriptionTier.FREE
        }

        preferences.edit().putString("current_subscription_tier", currentTier.value).apply()
        Log.d(TAG, "Updated subscription tier: $currentTier")

        if (currentTier != SubscriptionTier.FREE && oldTier == SubscriptionTier.FREE) {
            clearLimitsAfterUpgrade()
        }
    }

    private fun loadCurrentSubscriptionStatus() {
        val savedTier = preferences.getString("current_subscription_tier", SubscriptionTier.FREE.value)
        currentTier = SubscriptionTier.fromString(savedTier ?: SubscriptionTier.FREE.value)
    }

    private fun getTierFromProductId(productId: String): SubscriptionTier {
        return when {
            productId.contains(":yr") || productId.contains(".yr") -> SubscriptionTier.PREMIUM_YEARLY
            productId.contains(":mo") || productId.contains(".mo") || productId.contains("premium") -> SubscriptionTier.PREMIUM
            else -> SubscriptionTier.FREE
        }
    }

    internal fun notifyBackendOfPurchase(transaction: StoreTransaction) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user?.email == null) {
            Log.w(TAG, "No authenticated user for backend notification")
            return
        }
        scope.launch {
            val productId = transaction.productIds.firstOrNull() ?: return@launch
            val tier = getTierFromProductId(productId)
            val firstPurchaseKey = "first_purchase_$productId"
            val prefs = context.getSharedPreferences("subscription_prefs", android.content.Context.MODE_PRIVATE)
            val isFirstPurchase = !prefs.getBoolean(firstPurchaseKey, false)
            if (isFirstPurchase) prefs.edit().putBoolean(firstPurchaseKey, true).apply()

            val purchaseData = mapOf(
                "userId" to user.uid,
                "email" to user.email,
                "productId" to productId,
                "purchaseToken" to (transaction.orderId ?: ""),
                "purchaseTime" to transaction.purchaseTime,
                "tier" to tier.value,
                "monthlyCoins" to tier.monthlyCoins,
                "signupBonus" to if (isFirstPurchase) tier.signupBonus else 0
            )

            val json = gson.toJson(purchaseData)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://puzzleverseai.com/api/subscription-purchase")
                .post(requestBody)
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Successfully notified backend of purchase")
                    } else {
                        Log.e(TAG, "Backend notification failed: ${response.code}")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to notify backend", e)
            }
        }
    }
}

package com.kreativekoala.riddleverse

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback

import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter

// MARK: - Main Subscription Upgrade Dialog (RevenueCatUI Paywall)
@Composable
fun SubscriptionUpgradeDialog(
    targetTier: SubscriptionTier = SubscriptionTier.PREMIUM,
    limitInfo: RegenerationLimitManager.LimitInfo? = null,
    puzzleType: String? = null,
    paywallContext: String = "default",
    onDismiss: () -> Unit,
    onUpgrade: (SubscriptionTier) -> Unit,
    subscriptionManager: SubscriptionManager = SubscriptionManager.getInstance(LocalContext.current)
) {
    val analyticsManager = AnalyticsManager.getInstance()
    var subscriptionOffering by remember { mutableStateOf<Offering?>(null) }
    var offeringFetchDone by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        // Load the default offering by key so we don't rely on a potentially empty cache
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                Log.d("SubscriptionDialog", "All offering keys: ${offerings.all.keys}")
                Log.d("SubscriptionDialog", "Current offering: ${offerings.current?.identifier}")
                val offering = offerings.all["default"] ?: offerings.current
                Log.d("SubscriptionDialog", "Selected offering: ${offering?.identifier}")
                mainHandler.post {
                    subscriptionOffering = offering
                    offeringFetchDone = true
                }
            }
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e("SubscriptionDialog", "Offerings error: ${error.message}")
                mainHandler.post { offeringFetchDone = true }
            }
        })

        // Tag subscriber with paywall context for RC targeting/analytics
        com.revenuecat.purchases.Purchases.sharedInstance.setAttributes(
            mapOf(
                "last_paywall_source" to paywallContext,
                "last_paywall_date" to java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US
                ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(java.util.Date())
            )
        )

        analyticsManager?.track(
            AnalyticsEvent.subscriptionDialogOpen(
                targetTier = targetTier.value,
                triggerReason = when {
                    limitInfo != null -> "limit_reached"
                    else -> paywallContext
                },
                puzzleType = puzzleType,
                isOverLimits = limitInfo?.let { it.dailyUsed >= it.dailyLimit } ?: false
            )
        )
    }

    // Don't render PaywallDialog until offering fetch completes — rendering with null causes
    // RevenueCatUI to error and auto-dismiss before the fetch completes
    if (!offeringFetchDone) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF21BF63))
            }
        }
        return
    }

    // If fetch completed but offering is null (network error / not configured), show fallback
    if (subscriptionOffering == null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Unable to load subscription options.", color = Color.White, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21BF63))
                    ) { Text("Close") }
                }
            }
        }
        return
    }

    PaywallDialog(
        PaywallDialogOptions.Builder()
            .setOffering(subscriptionOffering)
            .setDismissRequest {
                analyticsManager?.track(
                    AnalyticsEvent.subscriptionDialogDismissed(
                        targetTier = targetTier.value,
                        dismissReason = "user_close",
                        timeOnDialog = 0L
                    )
                )
                onDismiss()
            }
            .setListener(object : PaywallListener {
                override fun onPurchaseCompleted(
                    customerInfo: CustomerInfo,
                    storeTransaction: StoreTransaction
                ) {
                    Log.d("SubscriptionDialog", "Purchase completed: ${storeTransaction.productIds}")

                    // Update tier from customer info
                    subscriptionManager.updateTierFromCustomerInfo(customerInfo)

                    // Notify backend for coin granting
                    subscriptionManager.notifyBackendOfPurchase(storeTransaction)

                    // Clear limits after upgrade
                    subscriptionManager.clearLimitsAfterUpgrade()

                    onUpgrade(subscriptionManager.currentTier)
                }

                override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                    Log.d("SubscriptionDialog", "Purchases restored")
                    subscriptionManager.updateTierFromCustomerInfo(customerInfo)
                }

                override fun onPurchaseError(error: com.revenuecat.purchases.PurchasesError) {
                    Log.e("SubscriptionDialog", "Purchase error: ${error.message}")
                }

                override fun onPurchaseCancelled() {
                    Log.d("SubscriptionDialog", "Purchase cancelled")
                }

                override fun onRestoreError(error: com.revenuecat.purchases.PurchasesError) {
                    Log.e("SubscriptionDialog", "Restore error: ${error.message}")
                }
            })
            .build()
    )
}

// MARK: - Subscription Status Bar
@Composable
fun SubscriptionStatusBar(
    subscriptionManager: SubscriptionManager = SubscriptionManager.getInstance(LocalContext.current),
    onUpgradeClick: () -> Unit
) {
    if (subscriptionManager.currentTier == SubscriptionTier.FREE) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.enjoying_app),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.upgrade_for_unlimited),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Button(
                    onClick = onUpgradeClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text(stringResource(R.string.upgrade), fontSize = 12.sp)
                }
            }
        }
    }
}

// MARK: - Enhanced RegenerationLimitCard Integration
@Composable
fun EnhancedRegenerationLimitCard(
    puzzleType: String,
    limitInfo: RegenerationLimitManager.LimitInfo,
    subscriptionManager: SubscriptionManager = SubscriptionManager.getInstance(LocalContext.current),
    onDismiss: () -> Unit,
    onUpgrade: (SubscriptionTier) -> Unit
) {
    RegenerationLimitCard(
        puzzleType = puzzleType,
        limitInfo = limitInfo,
        onDismiss = onDismiss,
        onUpgrade = {
            val suggestedTier = when {
                limitInfo.monthlyUsed > 100 -> SubscriptionTier.PREMIUM_YEARLY
                else -> SubscriptionTier.PREMIUM_YEARLY
            }
            onUpgrade(suggestedTier)
        }
    )
}

// MARK: - Utility Composable for Easy Integration
@Composable
fun ShowUpgradeDialogWhenNeeded(
    puzzleType: String,
    subscriptionManager: SubscriptionManager = SubscriptionManager.getInstance(LocalContext.current)
) {
    val limitManager = RegenerationLimitManager.getInstance(LocalContext.current)
    var showDialog by remember { mutableStateOf(false) }
    var limitInfo by remember { mutableStateOf<RegenerationLimitManager.LimitInfo?>(null) }

    LaunchedEffect(puzzleType) {
        val info = limitManager.getLimitInfo(puzzleType)
        if (info != null && subscriptionManager.currentTier == SubscriptionTier.FREE) {
            limitInfo = info
            showDialog = true
        }
    }

    if (showDialog && limitInfo != null) {
        SubscriptionUpgradeDialog(
            targetTier = SubscriptionTier.PREMIUM_YEARLY,
            limitInfo = limitInfo,
            puzzleType = puzzleType,
            onDismiss = { showDialog = false },
            onUpgrade = { showDialog = false }
        )
    }
}

// MARK: - Coin Store Dialog (uses RC "coins" offering)
@Composable
fun CoinStoreDialog(onDismiss: () -> Unit) {
    var coinsOffering by remember { mutableStateOf<Offering?>(null) }
    var offeringFetchDone by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                mainHandler.post {
                    coinsOffering = offerings.all["coins"]
                    offeringFetchDone = true
                }
            }
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e("CoinStore", "Offerings error: ${error.message}")
                mainHandler.post { offeringFetchDone = true }
            }
        })
    }

    // Show loading while fetching
    if (!offeringFetchDone) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF21BF63))
            }
        }
        return
    }

    // Show error if offering not found
    if (coinsOffering == null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Unable to load coin packages.", color = Color.White, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21BF63))
                    ) { Text("Close") }
                }
            }
        }
        return
    }

    PaywallDialog(
        PaywallDialogOptions.Builder()
            .setOffering(coinsOffering)
            .setDismissRequest { onDismiss() }
            .setListener(object : PaywallListener {
                override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) {
                    val coinManager = CoinManager.shared
                    val pack = CoinManager.COIN_PACKS.firstOrNull { storeTransaction.productIds.contains(it.productId) }
                    if (pack != null) {
                        coinManager.fetchBalance()
                    }
                    onDismiss()
                }
                override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                    CoinManager.shared.fetchBalance()
                    onDismiss()
                }
                override fun onPurchaseError(error: com.revenuecat.purchases.PurchasesError) {}
                override fun onPurchaseCancelled() {}
                override fun onRestoreError(error: com.revenuecat.purchases.PurchasesError) {}
            })
            .build()
    )
}

// MARK: - Coin Purchase Section (for Settings screen)
@Composable
fun CoinPurchaseSection() {
    val context = LocalContext.current
    val coinManager = remember { CoinManager.shared }
    var showCoinStore by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💰", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Coins", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Balance: ${coinManager.balance} coins", fontSize = 14.sp, color = Color(0xFFFFD700))
                    }
                }
                Button(
                    onClick = { showCoinStore = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21BF63)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Buy Coins", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Spend coins to create games, unlock harder challenges, and more.",
                fontSize = 12.sp, color = Color.Gray
            )
        }
    }

    if (showCoinStore) {
        CoinStoreDialog(onDismiss = { showCoinStore = false })
    }
}

// MARK: - Hard Paywall Gate (non-dismissible, shown after FREE_OPEN_LIMIT app opens)
object PaywallConstants {
    const val FREE_OPEN_LIMIT = 5
    private const val PREF_NAME = "hard_paywall_prefs"
    private const val PREF_SESSION_OPEN_COUNT = "session_open_count"
    private const val PREF_LAST_SESSION_ID = "last_session_id"

    /**
     * Increments app open count once per cold-start session.
     * Returns the new total open count.
     */
    fun incrementAppOpenCount(context: android.content.Context, sessionId: String): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        val lastSessionId = prefs.getString(PREF_LAST_SESSION_ID, null)

        // Only increment if this is a new session (cold start)
        if (lastSessionId != sessionId) {
            val currentCount = prefs.getInt(PREF_SESSION_OPEN_COUNT, 0)
            prefs.edit()
                .putInt(PREF_SESSION_OPEN_COUNT, currentCount + 1)
                .putString(PREF_LAST_SESSION_ID, sessionId)
                .apply()
            Log.d("HardPaywall", "App open count incremented to: ${currentCount + 1}")
            return currentCount + 1
        }

        return prefs.getInt(PREF_SESSION_OPEN_COUNT, 0)
    }

    fun getAppOpenCount(context: android.content.Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getInt(PREF_SESSION_OPEN_COUNT, 0)
    }
}

/**
 * Hard paywall — custom UI with RevenueCat offerings.
 * Full control over dismiss behavior and layout.
 */
@Composable
fun HardPaywallGate(
    onSubscribed: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    subscriptionManager: SubscriptionManager = SubscriptionManager.getInstance(LocalContext.current)
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var packages by remember { mutableStateOf<List<com.revenuecat.purchases.Package>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isPurchasing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPackage by remember { mutableStateOf<com.revenuecat.purchases.Package?>(null) }

    LaunchedEffect(Unit) {
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                val offering = offerings.all["default"] ?: offerings.current
                packages = offering?.availablePackages ?: emptyList()
                selectedPackage = packages.firstOrNull { it.packageType == com.revenuecat.purchases.PackageType.ANNUAL }
                    ?: packages.firstOrNull()
                isLoading = false
            }
            override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                Log.e("HardPaywall", "Offerings error: ${error.message}")
                errorMessage = "Unable to load options. Please restart."
                isLoading = false
            }
        })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D0D1A), Color(0xFF1A1A2E), Color(0xFF0D0D1A))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header
            Text("🎮", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Unlock RiddleVerse",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Create unlimited games, play without limits",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Features
            val features = listOf(
                "✨" to "Unlimited game creation",
                "🎯" to "Play all community games",
                "⚡" to "Priority generation speed",
                "💰" to "Earn from games you create"
            )
            features.forEach { (emoji, text) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text, fontSize = 16.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF21BF63))
            } else if (packages.isEmpty()) {
                Text(errorMessage ?: "No plans available", color = Color.Gray, fontSize = 14.sp)
            } else {
                // Package selection
                packages.forEach { pkg ->
                    val isSelected = pkg == selectedPackage
                    val period = when (pkg.packageType) {
                        com.revenuecat.purchases.PackageType.ANNUAL -> "Yearly"
                        com.revenuecat.purchases.PackageType.MONTHLY -> "Monthly"
                        com.revenuecat.purchases.PackageType.WEEKLY -> "Weekly"
                        else -> pkg.identifier
                    }
                    val trial = pkg.product.subscriptionOptions?.freeTrial
                    val trialText = if (trial != null) " • Free trial" else ""

                    Surface(
                        onClick = { selectedPackage = pkg },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF21BF63).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF21BF63)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(period, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                if (trialText.isNotEmpty()) {
                                    Text("Free trial included", color = Color(0xFF21BF63), fontSize = 12.sp)
                                }
                            }
                            Text(
                                pkg.product.price.formatted,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF21BF63) else Color.White,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Subscribe button
                Button(
                    onClick = {
                        val pkg = selectedPackage ?: return@Button
                        if (activity == null) return@Button
                        isPurchasing = true
                        errorMessage = null
                        Purchases.sharedInstance.purchase(
                            com.revenuecat.purchases.PurchaseParams.Builder(activity, pkg).build(),
                            object : com.revenuecat.purchases.interfaces.PurchaseCallback {
                                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                                    Log.d("HardPaywall", "Purchase completed: ${storeTransaction.productIds}")
                                    isPurchasing = false
                                    subscriptionManager.updateTierFromCustomerInfo(customerInfo)
                                    subscriptionManager.notifyBackendOfPurchase(storeTransaction)
                                    subscriptionManager.clearLimitsAfterUpgrade()
                                    onSubscribed()
                                }
                                override fun onError(error: com.revenuecat.purchases.PurchasesError, userCancelled: Boolean) {
                                    isPurchasing = false
                                    if (!userCancelled) {
                                        errorMessage = "Purchase failed: ${error.message}"
                                        Log.e("HardPaywall", "Purchase error: ${error.message}")
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21BF63)),
                    enabled = !isPurchasing && selectedPackage != null
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Subscribe", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage!!, color = Color.Red, fontSize = 13.sp)
                }

                // Restore purchases
                TextButton(onClick = {
                    isPurchasing = true
                    Purchases.sharedInstance.restorePurchases(object : com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback {
                        override fun onReceived(customerInfo: CustomerInfo) {
                            isPurchasing = false
                            subscriptionManager.updateTierFromCustomerInfo(customerInfo)
                            if (subscriptionManager.hasActiveSubscription()) {
                                onSubscribed()
                            } else {
                                errorMessage = "No active subscription found"
                            }
                        }
                        override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                            isPurchasing = false
                            errorMessage = "Restore failed: ${error.message}"
                        }
                    })
                }) {
                    Text("Restore Purchases", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                }

                // Dismiss option
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) {
                        Text("Continue with limits", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                    }
                }

                // Fine print
                Text(
                    "Payment charged to your Google Play account. Auto-renews unless cancelled 24 hours before period ends.",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.3f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// MARK: - Customer Center (Subscription Management & Win-Back)
@Composable
fun CustomerCenterDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            CustomerCenter(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                onDismiss = onDismiss
            )
        }
    )
}

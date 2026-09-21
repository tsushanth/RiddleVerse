package com.kreativekoala.riddleverse

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import com.kreativekoala.riddleverse.ui.theme.*
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
import com.kreativekoala.paywallkit.models.PaywallFeature
import com.kreativekoala.paywallkit.models.PaywallProduct
import com.kreativekoala.paywallkit.models.PaywallTheme
import com.kreativekoala.paywallkit.view.PaywallView
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter

// MARK: - Main Subscription Upgrade Dialog (PaywallKit)
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
    val context = LocalContext.current
    val activity = context as? Activity
    val packages = subscriptionManager.availablePackages
    val purchaseState = subscriptionManager.purchaseState

    LaunchedEffect(Unit) {
        // Tag subscriber with paywall context for RC targeting/analytics
        Purchases.sharedInstance.setAttributes(
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

    // Detect purchase success
    LaunchedEffect(purchaseState) {
        if (purchaseState is PurchaseState.Success) {
            subscriptionManager.clearLimitsAfterUpgrade()
            onUpgrade(subscriptionManager.currentTier)
        }
    }

    if (packages.isEmpty()) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .background(RvCanvas, RoundedCornerShape(24.dp))
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RvViolet)
            }
        }
        return
    }

    val paywallProducts = packages.map { pkg ->
        PaywallProduct(
            id = pkg.product.id,
            localizedPrice = pkg.product.price.formatted,
            price = pkg.product.price.amountMicros / 1_000_000.0,
            currencyCode = pkg.product.price.currencyCode,
            trialDays = pkg.product.subscriptionOptions?.freeTrial?.let { 3 },
            period = when (pkg.packageType) {
                com.revenuecat.purchases.PackageType.WEEKLY -> PaywallProduct.Period.WEEKLY
                com.revenuecat.purchases.PackageType.MONTHLY -> PaywallProduct.Period.MONTHLY
                com.revenuecat.purchases.PackageType.ANNUAL -> PaywallProduct.Period.YEARLY
                else -> PaywallProduct.Period.MONTHLY
            }
        )
    }

    PaywallView(
        appId = "riddleverse",
        appName = "RiddleVerse",
        features = listOf(
            PaywallFeature("\u2728", "Unlimited Games", "Create without limits"),
            PaywallFeature("\uD83C\uDFAF", "Play All Games", "Access every community game"),
            PaywallFeature("\u26A1", "Priority Speed", "Faster game generation"),
            PaywallFeature("\uD83D\uDCB0", "Earn Rewards", "Get paid for your creations"),
            PaywallFeature("\uD83C\uDFC6", "Leaderboards", "Compete globally")
        ),
        products = paywallProducts,
        theme = PaywallTheme(accent = RvViolet, accent2 = RvSky),
        showWinback = true,
        isDismissible = true,
        onPurchase = { productId ->
            val pkg = packages.firstOrNull { it.product.id == productId }
            if (pkg != null && activity != null) {
                subscriptionManager.purchasePackage(activity, pkg)
            }
        },
        onRestore = { subscriptionManager.restorePurchases() },
        onRedeemCode = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/redeem?code=promo-1month-free"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { activity?.startActivity(intent) } catch (_: Exception) {}
        },
        onDismiss = {
            analyticsManager?.track(
                AnalyticsEvent.subscriptionDialogDismissed(
                    targetTier = targetTier.value,
                    dismissReason = "user_close",
                    timeOnDialog = 0L
                )
            )
            onDismiss()
        }
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
                containerColor = RvMint.copy(alpha = 0.1f)
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
                        color = RvInk
                    )
                    Text(
                        text = stringResource(R.string.upgrade_for_unlimited),
                        fontSize = 12.sp,
                        color = RvInkSoft
                    )
                }

                Button(
                    onClick = onUpgradeClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RvMint
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
                    .background(RvCanvas, RoundedCornerShape(24.dp))
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RvViolet)
            }
        }
        return
    }

    // Show error if offering not found
    if (coinsOffering == null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .background(RvCanvas, RoundedCornerShape(24.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Unable to load coin packages.", color = RvInk, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = RvViolet)
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
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(20.dp)
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
                        Text("Coins", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = RvInk)
                        Text("Balance: ${coinManager.balance} coins", fontSize = 14.sp, color = RvSunEdge)
                    }
                }
                Button(
                    onClick = { showCoinStore = true },
                    colors = ButtonDefaults.buttonColors(containerColor = RvViolet),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Buy Coins", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Spend coins to create games, unlock harder challenges, and more.",
                fontSize = 12.sp, color = RvInkSoft
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
 * Hard paywall — PaywallKit UI.
 * isDismissible = true when onDismiss is provided, false otherwise.
 */
@Composable
fun HardPaywallGate(
    onSubscribed: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    subscriptionManager: SubscriptionManager = SubscriptionManager.getInstance(LocalContext.current)
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val packages = subscriptionManager.availablePackages
    val purchaseState = subscriptionManager.purchaseState

    LaunchedEffect(purchaseState) {
        if (purchaseState is PurchaseState.Success) {
            subscriptionManager.clearLimitsAfterUpgrade()
            onSubscribed()
        }
    }

    if (packages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(RvCanvas),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = RvViolet)
        }
        return
    }

    val paywallProducts = packages.map { pkg ->
        PaywallProduct(
            id = pkg.product.id,
            localizedPrice = pkg.product.price.formatted,
            price = pkg.product.price.amountMicros / 1_000_000.0,
            currencyCode = pkg.product.price.currencyCode,
            trialDays = pkg.product.subscriptionOptions?.freeTrial?.let { 3 },
            period = when (pkg.packageType) {
                com.revenuecat.purchases.PackageType.WEEKLY -> PaywallProduct.Period.WEEKLY
                com.revenuecat.purchases.PackageType.MONTHLY -> PaywallProduct.Period.MONTHLY
                com.revenuecat.purchases.PackageType.ANNUAL -> PaywallProduct.Period.YEARLY
                else -> PaywallProduct.Period.MONTHLY
            }
        )
    }

    PaywallView(
        appId = "riddleverse",
        appName = "RiddleVerse",
        features = listOf(
            PaywallFeature("\u2728", "Unlimited Games", "Create without limits"),
            PaywallFeature("\uD83C\uDFAF", "Play All Games", "Access every community game"),
            PaywallFeature("\u26A1", "Priority Speed", "Faster game generation"),
            PaywallFeature("\uD83D\uDCB0", "Earn Rewards", "Get paid for your creations"),
            PaywallFeature("\uD83C\uDFC6", "Leaderboards", "Compete globally")
        ),
        products = paywallProducts,
        theme = PaywallTheme(accent = RvViolet, accent2 = RvSky),
        showWinback = false,
        isDismissible = onDismiss != null,
        onPurchase = { productId ->
            val pkg = packages.firstOrNull { it.product.id == productId }
            if (pkg != null && activity != null) {
                subscriptionManager.purchasePackage(activity, pkg)
            }
        },
        onRestore = { subscriptionManager.restorePurchases() },
        onRedeemCode = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/redeem?code=promo-1month-free"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { activity?.startActivity(intent) } catch (_: Exception) {}
        },
        onDismiss = { onDismiss?.invoke() }
    )
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

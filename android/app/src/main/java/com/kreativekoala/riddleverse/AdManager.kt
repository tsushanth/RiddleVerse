package com.kreativekoala.riddleverse

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production AdManager with real AdMob integration
 */
class AdManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: AdManager? = null
        private const val TAG = "AdManager"

        fun getInstance(context: Context): AdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Ad configuration
    private val adConfig = AdConfig()
    private var isInitialized = false

    // AdMob ad instances
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var isLoadingInterstitial = false
    private var isLoadingRewarded = false
    // ADD these new lines:
    private val isShowingInterstitial = AtomicBoolean(false)
    private val isShowingRewarded = AtomicBoolean(false)
    private var sessionAdFailures = 0
    private var consecutiveAdFailures = 0
    private val adFrustrationThreshold = 3
    private val sessionFrustrationThreshold = 5
    private val subscriptionManager: SubscriptionManager by lazy {
        SubscriptionManager.getInstance(context)
    }

    init {
        initializeAdMob()
    }

    private fun initializeAdMob() {
        try {
            MobileAds.initialize(context) { initializationStatus ->
                val statusMap = initializationStatus.adapterStatusMap
                for (adapterClass in statusMap.keys) {
                    val status = statusMap[adapterClass]
                    Log.d(TAG, "Adapter name: $adapterClass, Description: ${status?.description}, Latency: ${status?.latency}")
                }
                isInitialized = true
                Log.d(TAG, "AdMob initialized successfully")

                // Preload first interstitial ad
                preloadInterstitialAd()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AdMob", e)
            isInitialized = false
        }
    }

    /**
     * Shows an interstitial ad with comprehensive callback handling
     */
    fun showInterstitialAd(
        activity: Activity,
        adPlacement: String = "default",
        onAdClosed: () -> Unit,
        onAdFailed: (String) -> Unit,
        onAdNotReady: () -> Unit = { onAdFailed("Ad not ready") }
    ) {
        if (subscriptionManager.hasActiveSubscription()) {
            Log.d(TAG, "User has active subscription, skipping interstitial ad")
            onAdClosed() // Proceed as if ad was shown and closed
            return
        }

        val sessionAdCount = getSessionAdCount()
        val hourlyAdCount = getHourlyAdCount()

        // Check if user might be frustrated with ads
        if (sessionAdCount >= sessionFrustrationThreshold || consecutiveAdFailures >= adFrustrationThreshold) {
            trackAdFrustration(sessionAdCount, hourlyAdCount, consecutiveAdFailures)
        }

        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity is finishing/destroyed, skipping ad")
            trackAdExperienceNegative(adPlacement, "activity_destroyed", sessionAdCount, hourlyAdCount)
            onAdNotReady()
            return
        }
        if (!isInitialized) {
            Log.w(TAG, "AdMob not initialized")
            sessionAdFailures++
            consecutiveAdFailures++
            trackAdExperienceNegative(adPlacement, "admob_not_initialized", sessionAdCount, hourlyAdCount)
            onAdNotReady()
            return
        }

        if (!isShowingInterstitial.compareAndSet(false, true)) {
            Log.w(TAG, "Interstitial ad already showing, skipping")
            trackAdExperienceNegative(adPlacement, "ad_already_showing", sessionAdCount, hourlyAdCount)
            onAdNotReady()
            return
        }

        // Check frequency capping
        /*if (!shouldShowAd(adPlacement)) {
            Log.d(TAG, "Ad frequency cap reached for placement: $adPlacement")
            isShowingInterstitial.set(false)  // ADD this line
            onAdNotReady()
            return
        }*/

        val currentAd = interstitialAd
        if (currentAd != null) {
            // Track ad request
            trackAdRequest(adPlacement)

            // Set up ad callbacks
            currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdClicked() {
                    Log.d(TAG, "Interstitial ad clicked")
                    // Reset frustration on engagement
                    consecutiveAdFailures = 0
                    trackAdExperiencePositive(adPlacement, "user_clicked", sessionAdCount)
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed")
                    isShowingInterstitial.set(false)
                    interstitialAd = null
                    consecutiveAdFailures = 0 // Reset on successful completion
                    trackAdClosed(adPlacement)
                    trackAdExperiencePositive(adPlacement, "watched_complete", sessionAdCount)
                    preloadInterstitialAd()

                    try {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            onAdClosed()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calling onAdClosed callback", e)
                    }
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Interstitial ad failed to show: ${adError.message}")
                    isShowingInterstitial.set(false)
                    interstitialAd = null
                    sessionAdFailures++
                    consecutiveAdFailures++

                    trackAdFailed(adPlacement, adError.message)
                    trackAdExperienceNegative(adPlacement, "failed_to_show", sessionAdCount, hourlyAdCount)

                    try {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            onAdFailed("Failed to show: ${adError.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calling onAdFailed callback", e)
                    }
                }

                override fun onAdImpression() {
                    Log.d(TAG, "Interstitial ad impression")
                    trackAdShown(adPlacement)
                }
            }

            try {
                currentAd.show(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Exception while showing interstitial ad", e)
                isShowingInterstitial.set(false)
                interstitialAd = null
                sessionAdFailures++
                consecutiveAdFailures++
                trackAdExperienceNegative(adPlacement, "exception_showing", sessionAdCount, hourlyAdCount)
                onAdFailed("Exception while showing ad: ${e.message}")
            }

        } else {
            Log.w(TAG, "No interstitial ad loaded")
            isShowingInterstitial.set(false)
            sessionAdFailures++
            consecutiveAdFailures++
            trackAdFailed(adPlacement, "No ad loaded")
            trackAdExperienceNegative(adPlacement, "no_ad_loaded", sessionAdCount, hourlyAdCount)

            if (!isLoadingInterstitial) {
                preloadInterstitialAd()
            }
            onAdNotReady()
        }
    }

    private fun trackAdExperienceNegative(
        adPlacement: String,
        reason: String,
        sessionAdCount: Int,
        hourlyAdCount: Int
    ) {
        try {
            AnalyticsManager.getInstance()?.track(
                AnalyticsEvent.adExperienceNegative(
                    adPlacement = adPlacement,
                    reason = reason,
                    sessionAdCount = sessionAdCount,
                    hourlyAdCount = hourlyAdCount
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track negative ad experience", e)
        }
    }

    private fun trackAdExperiencePositive(
        adPlacement: String,
        reason: String,
        sessionAdCount: Int
    ) {
        try {
            AnalyticsManager.getInstance()?.track(
                AnalyticsEvent.adExperiencePositive(
                    adPlacement = adPlacement,
                    reason = reason,
                    sessionAdCount = sessionAdCount
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track positive ad experience", e)
        }
    }

    private fun trackAdFrustration(sessionAds: Int, hourlyAds: Int, failures: Int) {
        try {
            AnalyticsManager.getInstance()?.track(
                AnalyticsEvent.adFrustrationThresholdReached(
                    totalAdsInSession = sessionAds,
                    totalAdsInHour = hourlyAds,
                    failureCount = failures,
                    userEngagementLevel = determineEngagementLevel(sessionAds, failures)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track ad frustration", e)
        }
    }

    private fun determineEngagementLevel(sessionAds: Int, failures: Int): String {
        return when {
            failures > sessionAds / 2 -> "frustrated"
            sessionAds > 8 -> "high_volume"
            failures > 2 -> "experiencing_issues"
            else -> "normal"
        }
    }

    // Add method to check if user should see subscription upsell
    fun shouldShowSubscriptionUpsell(): Boolean {
        return !subscriptionManager.hasActiveSubscription() &&
                (getSessionAdCount() >= sessionFrustrationThreshold ||
                        consecutiveAdFailures >= adFrustrationThreshold)
    }

    // Method to get current frustration metrics
    fun getAdFrustrationMetrics(): AdFrustrationMetrics {
        return AdFrustrationMetrics(
            sessionAdCount = getSessionAdCount(),
            hourlyAdCount = getHourlyAdCount(),
            sessionFailures = sessionAdFailures,
            consecutiveFailures = consecutiveAdFailures,
            shouldShowUpsell = shouldShowSubscriptionUpsell()
        )
    }

    /**
     * Shows a rewarded ad
     */
    fun showRewardedAd(
        activity: Activity,
        adPlacement: String = "reward",
        onAdRewarded: (String, Int) -> Unit,
        onAdClosed: () -> Unit,
        onAdFailed: (String) -> Unit
    ) {
        if (subscriptionManager.hasActiveSubscription()) {
            Log.d(TAG, "User has active subscription, skipping rewarded ad")
            // For rewarded ads, still give the reward since they're subscribers
            onAdRewarded("premium_reward", 1)
            onAdClosed()
            return
        }

        if (!isInitialized) {
            Log.w(TAG, "AdMob not initialized for rewarded ad")
            onAdFailed("Ad system not ready")
            return
        }

        val currentAd = rewardedAd
        if (currentAd != null) {
            trackAdRequest(adPlacement)

            currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdClicked() {
                    Log.d(TAG, "Rewarded ad clicked")
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad dismissed")
                    rewardedAd = null
                    trackAdClosed(adPlacement)

                    // Preload next rewarded ad
                    preloadRewardedAd()

                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Rewarded ad failed to show: ${adError.message}")
                    rewardedAd = null
                    trackAdFailed(adPlacement, adError.message)
                    onAdFailed("Failed to show: ${adError.message}")
                }

                override fun onAdImpression() {
                    Log.d(TAG, "Rewarded ad impression")
                    trackAdShown(adPlacement)
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad showed full screen content")
                }
            }

            // Show the ad with reward callback
            currentAd.show(activity) { rewardItem ->
                val rewardAmount = rewardItem.amount
                val rewardType = rewardItem.type
                Log.d(TAG, "User earned reward: $rewardAmount $rewardType")
                trackAdRewarded(adPlacement, rewardType, rewardAmount)
                onAdRewarded(rewardType, rewardAmount)
            }

        } else {
            Log.w(TAG, "No rewarded ad loaded")
            trackAdFailed(adPlacement, "No rewarded ad loaded")

            // Try to load an ad for next time
            if (!isLoadingRewarded) {
                preloadRewardedAd()
            }

            onAdFailed("No rewarded ad available")
        }
    }

    /**
     * Preloads interstitial ad
     */
    private fun preloadInterstitialAd() {
        if (!isInitialized || isLoadingInterstitial || interstitialAd != null) {
            return
        }

        isLoadingInterstitial = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            adConfig.interstitialAdUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Failed to load interstitial ad: ${adError.message}")
                    interstitialAd = null
                    isLoadingInterstitial = false
                }

                override fun onAdLoaded(loadedAd: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded successfully")
                    interstitialAd = loadedAd
                    isLoadingInterstitial = false
                }
            }
        )
    }

    /**
     * Preloads rewarded ad
     */
    private fun preloadRewardedAd() {
        if (!isInitialized || isLoadingRewarded || rewardedAd != null) {
            return
        }

        isLoadingRewarded = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            adConfig.rewardedAdUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Failed to load rewarded ad: ${adError.message}")
                    rewardedAd = null
                    isLoadingRewarded = false
                }

                override fun onAdLoaded(loadedAd: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded successfully")
                    rewardedAd = loadedAd
                    isLoadingRewarded = false
                }
            }
        )
    }

    fun cleanup() {
        Log.d(TAG, "Forcing ad cleanup")
        isShowingInterstitial.set(false)
        isShowingRewarded.set(false)

        try {
            interstitialAd?.fullScreenContentCallback = null
            rewardedAd?.fullScreenContentCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during ad cleanup", e)
        }
    }

    /**
     * Check if any ad is currently being shown
     */
    fun isShowingAnyAd(): Boolean {
        return isShowingInterstitial.get() || isShowingRewarded.get()
    }

    /**
     * Public method to preload ads
     */
    fun preloadAds() {
        CoroutineScope(Dispatchers.Main).launch {
            if (interstitialAd == null && !isLoadingInterstitial) {
                preloadInterstitialAd()
            }
            if (rewardedAd == null && !isLoadingRewarded) {
                preloadRewardedAd()
            }
        }
    }

    /**
     * Check if ad should be shown based on frequency capping
     */
    private fun shouldShowAd(placement: String): Boolean {
        val lastAdTime = getLastAdTime(placement)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastAdTime

        return when (placement) {
            "puzzle_completion" -> timeSinceLastAd >= adConfig.completionAdInterval
            "level_start" -> timeSinceLastAd >= adConfig.levelStartAdInterval
            "reward" -> timeSinceLastAd >= adConfig.rewardAdInterval
            else -> timeSinceLastAd >= adConfig.defaultAdInterval
        }
    }

    private fun getLastAdTime(placement: String): Long {
        val prefs = context.getSharedPreferences("ad_manager_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("last_ad_time_$placement", 0)
    }

    private fun saveAdTime(placement: String) {
        val prefs = context.getSharedPreferences("ad_manager_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_ad_time_$placement", System.currentTimeMillis()).apply()
    }

    // Analytics tracking methods
    private fun trackAdRequest(placement: String) {
        try {
            AnalyticsManager.getInstance()?.track(AnalyticsEvent("ad_requested", mapOf(
                "placement" to placement,
                "ad_network" to "admob",
                "timestamp" to System.currentTimeMillis()
            )))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track ad request", e)
        }
    }

    private fun trackAdShown(placement: String) {
        saveAdTime(placement)
        incrementAdCounts() // Add this line to track session/hourly limits

        try {
            AnalyticsManager.getInstance()?.track(AnalyticsEvent("ad_shown", mapOf(
                "placement" to placement,
                "ad_network" to "admob",
                "session_ad_count" to getSessionAdCount(),
                "hourly_ad_count" to getHourlyAdCount(),
                "timestamp" to System.currentTimeMillis()
            )))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track ad shown", e)
        }
    }

    /**
     * Get the number of ads shown in the current session
     */
    private fun getSessionAdCount(): Int {
        val prefs = context.getSharedPreferences("ad_manager_prefs", Context.MODE_PRIVATE)
        val sessionStartTime = getSessionStartTime()
        val currentSessionId = getCurrentSessionId()
        val savedSessionId = prefs.getString("current_session_id", "")

        // If it's a new session, reset the count
        if (savedSessionId != currentSessionId) {
            prefs.edit()
                .putString("current_session_id", currentSessionId)
                .putInt("session_ad_count", 0)
                .putLong("session_start_time", sessionStartTime)
                .apply()
            return 0
        }

        return prefs.getInt("session_ad_count", 0)
    }

    /**
     * Get the number of ads shown in the last hour
     */
    private fun getHourlyAdCount(): Int {
        val prefs = context.getSharedPreferences("ad_manager_prefs", Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        val oneHourAgo = currentTime - (60 * 60 * 1000) // 1 hour in milliseconds

        // Get all ad timestamps from the last hour
        val adTimestamps = prefs.getStringSet("ad_timestamps", mutableSetOf()) ?: mutableSetOf()
        val recentTimestamps = adTimestamps.filter { timestamp ->
            try {
                timestamp.toLong() > oneHourAgo
            } catch (e: NumberFormatException) {
                false
            }
        }.toMutableSet()

        // Clean up old timestamps and save
        prefs.edit().putStringSet("ad_timestamps", recentTimestamps).apply()

        return recentTimestamps.size
    }

    /**
     * Increment session and hourly ad counts
     */
    private fun incrementAdCounts() {
        val prefs = context.getSharedPreferences("ad_manager_prefs", Context.MODE_PRIVATE)

        // Increment session count
        val currentSessionCount = getSessionAdCount()
        prefs.edit().putInt("session_ad_count", currentSessionCount + 1).apply()

        // Add timestamp for hourly tracking
        val adTimestamps = prefs.getStringSet("ad_timestamps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        adTimestamps.add(System.currentTimeMillis().toString())
        prefs.edit().putStringSet("ad_timestamps", adTimestamps).apply()

        Log.d(TAG, "Ad counts incremented - Session: ${currentSessionCount + 1}/${adConfig.maxAdsPerSession}, Hourly: ${getHourlyAdCount()}/${adConfig.maxAdsPerHour}")
    }

    /**
     * Get session start time
     */
    private fun getSessionStartTime(): Long {
        val prefs = context.getSharedPreferences("ad_manager_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("session_start_time", System.currentTimeMillis())
    }

    /**
     * Generate a simple session ID based on app start time
     */
    private fun getCurrentSessionId(): String {
        // Simple session ID based on process start time
        return "session_${android.os.Process.myPid()}_${System.currentTimeMillis() / (30 * 60 * 1000)}" // 30-minute sessions
    }

    /**
     * Reset session data (call this when app starts or after long inactivity)
     */
    fun resetSession() {
        val prefs = context.getSharedPreferences("ad_manager_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("current_session_id", getCurrentSessionId())
            .putInt("session_ad_count", 0)
            .putLong("session_start_time", System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Session reset")
    }

    private fun trackAdClosed(placement: String) {
        try {
            AnalyticsManager.getInstance()?.track(AnalyticsEvent("ad_closed", mapOf(
                "placement" to placement,
                "ad_network" to "admob",
                "timestamp" to System.currentTimeMillis()
            )))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track ad closed", e)
        }
    }

    private fun trackAdFailed(placement: String, error: String) {
        try {
            AnalyticsManager.getInstance()?.track(AnalyticsEvent("ad_failed", mapOf(
                "placement" to placement,
                "ad_network" to "admob",
                "error" to error,
                "timestamp" to System.currentTimeMillis()
            )))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track ad failure", e)
        }
    }

    private fun trackAdRewarded(placement: String, rewardType: String, amount: Int) {
        try {
            AnalyticsManager.getInstance()?.track(AnalyticsEvent("ad_rewarded", mapOf(
                "placement" to placement,
                "reward_type" to rewardType,
                "reward_amount" to amount,
                "ad_network" to "admob",
                "timestamp" to System.currentTimeMillis()
            )))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track ad reward", e)
        }
    }
}

data class AdFrustrationMetrics(
    val sessionAdCount: Int,
    val hourlyAdCount: Int,
    val sessionFailures: Int,
    val consecutiveFailures: Int,
    val shouldShowUpsell: Boolean
)

/**
 * Production ad configuration
 */
data class AdConfig(
    // Replace these with your actual AdMob ad unit IDs
    val interstitialAdUnitId: String = "ca-app-pub-5764510017766009/3276510696", // Your interstitial ad unit ID
    val rewardedAdUnitId: String = "ca-app-pub-5764510017766009/6609251047", // Your rewarded ad unit ID

    // Frequency capping settings (adjust based on your monetization strategy)
    val completionAdInterval: Long = 45_000, // 45 seconds between completion ads
    val levelStartAdInterval: Long = 180_000, // 3 minutes between level start ads
    val rewardAdInterval: Long = 30_000, // 30 seconds between reward ads
    val defaultAdInterval: Long = 60_000, // 1 minute default

    // Session limits
    val maxAdsPerSession: Int = 8,
    val maxAdsPerHour: Int = 15,

    // Debug settings
    val enableTestMode: Boolean = false, // Set to true for testing with test ads
    val enableLogging: Boolean = true
)
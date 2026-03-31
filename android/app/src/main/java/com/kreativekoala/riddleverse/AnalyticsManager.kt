package com.kreativekoala.riddleverse

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// MARK: - Analytics Event Class
data class AnalyticsEvent(
    val name: String,
    val parameters: Map<String, Any>? = null
) {
    companion object {
        // App Lifecycle
        fun appOpen() = AnalyticsEvent("app_open")
        fun appBackground() = AnalyticsEvent("app_background_riddlverse")
        fun appCrash(error: String) = AnalyticsEvent("app_crash", mapOf(
            "error_message" to error.take(100), // Firebase has parameter limits
            "timestamp" to System.currentTimeMillis()
        ))

        fun adExperienceNegative(
            adPlacement: String,
            reason: String,
            sessionAdCount: Int,
            hourlyAdCount: Int
        ) = AnalyticsEvent("ad_experience_negative", mapOf(
            "ad_placement" to adPlacement,
            "negative_reason" to reason,
            "session_ad_count" to sessionAdCount,
            "hourly_ad_count" to hourlyAdCount,
            "user_tier" to "free"
        ))

        fun adExperiencePositive(
            adPlacement: String,
            reason: String,
            sessionAdCount: Int
        ) = AnalyticsEvent("ad_experience_positive", mapOf(
            "ad_placement" to adPlacement,
            "positive_reason" to reason,
            "session_ad_count" to sessionAdCount,
            "user_tier" to "free"
        ))

        fun adFrustrationThresholdReached(
            totalAdsInSession: Int,
            totalAdsInHour: Int,
            failureCount: Int,
            userEngagementLevel: String = "unknown"
        ) = AnalyticsEvent("ad_frustration_threshold_reached", mapOf(
            "session_ads" to totalAdsInSession,
            "hourly_ads" to totalAdsInHour,
            "ad_failure_count" to failureCount,
            "engagement_level" to userEngagementLevel,
            "upsell_opportunity" to true
        ))

        fun adBlockingBehaviorDetected(
            adPlacement: String,
            skipPattern: String,
            sessionLength: Long
        ) = AnalyticsEvent("ad_blocking_behavior_detected", mapOf(
            "ad_placement" to adPlacement,
            "skip_pattern" to skipPattern,
            "session_length_ms" to sessionLength,
            "upsell_opportunity" to true
        ))

        fun subscriptionDialogOpen(
            targetTier: String,
            triggerReason: String,
            puzzleType: String? = null,
            isOverLimits: Boolean = false
        ) = AnalyticsEvent("subscription_dialog_opened", mapOf(
            "target_tier" to targetTier,
            "trigger_reason" to triggerReason,
            "puzzle_type" to (puzzleType ?: "unknown"),
            "is_over_limits" to isOverLimits
        ))

        fun subscriptionDialogDismissed(
            targetTier: String,
            dismissReason: String,
            timeOnDialog: Long
        ) = AnalyticsEvent("subscription_dialog_dismissed", mapOf(
            "target_tier" to targetTier,
            "dismiss_reason" to dismissReason,
            "time_on_dialog_ms" to timeOnDialog
        ))

        fun subscriptionTierClicked(
            tier: String,
            isRecommended: Boolean,
            price: String,
            userOverLimits: Boolean = false
        ) = AnalyticsEvent("subscription_tier_clicked", mapOf(
            "subscription_tier" to tier,
            "is_recommended" to isRecommended,
            "price" to price,
            "user_over_limits" to userOverLimits
        ))

        // User Authentication
        fun userRegistration(method: String) = AnalyticsEvent("sign_up", mapOf(
            "method" to method
        ))

        fun userLogin(method: String) = AnalyticsEvent("login", mapOf(
            "method" to method
        ))

        fun userLogout() = AnalyticsEvent("logout")

        // Onboarding & Tutorial
        fun tutorialStart() = AnalyticsEvent("tutorial_begin")
        fun tutorialStep(step: Int, stepName: String) = AnalyticsEvent("tutorial_step", mapOf(
            "step_number" to step.toLong(),
            "step_name" to stepName
        ))
        fun tutorialComplete(duration: Double) = AnalyticsEvent("tutorial_complete", mapOf(
            "duration_seconds" to duration,
            "completion_rate" to 1.0
        ))
        fun tutorialSkip(step: Int) = AnalyticsEvent("tutorial_skip", mapOf(
            "skipped_at_step" to step.toLong()
        ))

        // Puzzle Events
        fun puzzleStart(type: String, difficulty: String, questionIndex: Int) = AnalyticsEvent("puzzle_start", mapOf(
            "puzzle_type" to type,
            "difficulty" to difficulty,
            "question_index" to questionIndex.toLong()
        ))

        fun puzzleComplete(
            type: String,
            difficulty: String,
            isCorrect: Boolean,
            timeSpent: Double,
            score: Int,
            questionIndex: Int,
            totalQuestions: Int
        ) = AnalyticsEvent("puzzle_complete", mapOf(
            "puzzle_type" to type,
            "difficulty" to difficulty,
            "is_correct" to isCorrect,
            "time_spent_seconds" to timeSpent,
            "score" to score.toLong(),
            "question_index" to questionIndex.toLong(),
            "total_questions" to totalQuestions.toLong(),
            "accuracy" to if (isCorrect) 1.0 else 0.0
        ))

        fun puzzleAbandoned(type: String, difficulty: String, timeSpent: Double, questionIndex: Int) =
            AnalyticsEvent("puzzle_abandoned", mapOf(
                "puzzle_type" to type,
                "difficulty" to difficulty,
                "time_spent_seconds" to timeSpent,
                "question_index" to questionIndex.toLong(),
                "abandon_reason" to "user_exit"
            ))

        fun puzzleHintUsed(type: String, difficulty: String, questionIndex: Int) =
            AnalyticsEvent("puzzle_hint_used", mapOf(
                "puzzle_type" to type,
                "difficulty" to difficulty,
                "question_index" to questionIndex.toLong()
            ))

        // Session Events
        fun sessionStart() = AnalyticsEvent("session_start_event")

        fun sessionEnd(duration: Double, puzzlesSolved: Int, totalScore: Int) =
            AnalyticsEvent("session_end", mapOf(
                "session_duration_seconds" to duration,
                "puzzles_solved" to puzzlesSolved.toLong(),
                "total_score" to totalScore.toLong(),
                "avg_score_per_puzzle" to if (puzzlesSolved > 0) totalScore.toDouble() / puzzlesSolved else 0.0
            ))

        // Level & Progress Events
        fun levelUp(oldLevel: Int, newLevel: Int, totalXP: Int) = AnalyticsEvent("level_up", mapOf(
            "level" to newLevel.toLong(),
            "character" to "player" // Firebase Analytics parameter
        ))

        fun achievementUnlocked(achievementId: String, achievementName: String) =
            AnalyticsEvent("unlock_achievement", mapOf(
                "achievement_id" to achievementId,
                "achievement_name" to achievementName
            ))

        fun streakUpdate(currentStreak: Int, bestStreak: Int, streakMultiplier: Float) =
            AnalyticsEvent("streak_update", mapOf(
                "current_streak" to currentStreak.toLong(),
                "best_streak" to bestStreak.toLong(),
                "streak_multiplier" to streakMultiplier.toDouble(),
                "is_new_best" to (currentStreak > bestStreak)
            ))

        // Screen Navigation
        fun screenView(screenName: String, screenClass: String = "") = AnalyticsEvent("screen_view", mapOf(
            "screen_name" to screenName,
            "screen_class" to screenClass.ifEmpty { screenName }
        ))

        // Error Events
        fun networkError(endpoint: String, errorCode: Int, errorMessage: String) =
            AnalyticsEvent("network_error", mapOf(
                "endpoint" to endpoint.take(100),
                "error_code" to errorCode.toLong(),
                "error_message" to errorMessage.take(100)
            ))

        fun puzzleLoadError(puzzleType: String, errorMessage: String) =
            AnalyticsEvent("puzzle_load_error", mapOf(
                "puzzle_type" to puzzleType,
                "error_message" to errorMessage.take(100)
            ))

        // User Engagement Events
        fun userEngagement(
            duration: Double,
            screensViewed: Int,
            actionsPerformed: Int
        ) = AnalyticsEvent("user_engagement", mapOf(
            "engagement_time_msec" to (duration * 1000).toLong()
        ))

        // Settings & Help
        fun settingsView() = AnalyticsEvent("settings_view")
        fun helpView(section: String) = AnalyticsEvent("help_view", mapOf(
            "help_section" to section
        ))


        fun subscriptionView(source: String) = AnalyticsEvent("subscription_view", mapOf(
            "source" to source
        ))

        fun subscriptionPurchase(tier: String, price: Double, currency: String) =
            AnalyticsEvent("purchase", mapOf(
                "currency" to currency,
                "value" to price,
                "items" to listOf(mapOf(
                    "item_id" to tier,
                    "item_name" to "Premium Subscription",
                    "item_category" to "subscription",
                    "price" to price,
                    "currency" to currency,
                    "quantity" to 1
                ))
            ))
    }
}

// MARK: - Analytics Manager
class AnalyticsManager private constructor(
    private val context: Context
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AnalyticsManager"

        @Volatile
        private var INSTANCE: AnalyticsManager? = null

        fun initialize(application: Application): AnalyticsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AnalyticsManager(application.applicationContext).also {
                    INSTANCE = it
                    ProcessLifecycleOwner.get().lifecycle.addObserver(it)
                }
            }
        }

        fun getInstance(): AnalyticsManager? {
            return INSTANCE ?: run {
                Log.w(TAG, "⚠️ AnalyticsManager not initialized")
                null
            }
        }

        fun isInitialized(): Boolean = INSTANCE != null
    }

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sessionStartTime: Long? = null
    private var sessionPuzzlesSolved = 0
    private var sessionTotalScore = 0
    private var currentUserId: String? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    init {
        initializeAnalytics()
    }

    private fun initializeAnalytics() {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)

            // Set user properties
            setDeviceProperties()

            // Set current user ID from Firebase Auth if available
            try {
                currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                currentUserId?.let { userId ->
                    firebaseAnalytics.setUserId(userId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Auth not available: ${e.message}")
            }

            _isInitialized.value = true
            Log.d(TAG, "📊 Firebase Analytics initialized successfully")

            track(AnalyticsEvent.appOpen())
            startSession()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Analytics: ${e.message}")
        }
    }

    // MARK: - Core Tracking Method
    fun track(event: AnalyticsEvent) {
        if (!_isInitialized.value) {
            Log.w(TAG, "⚠️ Analytics not initialized, ignoring event: ${event.name}")
            return
        }

        try {
            Log.d(TAG, "📊 Analytics Event: ${event.name}")
            event.parameters?.let { params ->
                Log.d(TAG, "📊 Parameters: $params")
            }

            val bundle = Bundle().apply {
                event.parameters?.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putLong(key, value.toLong())
                        is Long -> putLong(key, value)
                        is Float -> putDouble(key, value.toDouble())
                        is Double -> putDouble(key, value)
                        is Boolean -> putString(key, value.toString())
                        is List<*> -> {
                            // For purchase events with items array
                            if (key == "items" && value.isNotEmpty()) {
                                val itemsBundle = arrayOfNulls<Bundle>(value.size)
                                value.forEachIndexed { index, item ->
                                    if (item is Map<*, *>) {
                                        itemsBundle[index] = Bundle().apply {
                                            item.forEach { (itemKey, itemValue) ->
                                                when (itemValue) {
                                                    is String -> putString(itemKey.toString(), itemValue)
                                                    is Number -> putDouble(itemKey.toString(), itemValue.toDouble())
                                                    else -> putString(itemKey.toString(), itemValue.toString())
                                                }
                                            }
                                        }
                                    }
                                }
                                putParcelableArray(key, itemsBundle)
                            }
                        }
                        else -> putString(key, value.toString())
                    }
                }
            }

            firebaseAnalytics.logEvent(event.name, bundle)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to track event ${event.name}: ${e.message}")
        }
    }

    fun trackEvents(events: List<AnalyticsEvent>) {
        events.forEach { track(it) }
    }

    fun batchTrack(events: List<AnalyticsEvent>) {
        events.forEach { track(it) }
    }

    // MARK: - Session Management
    fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        sessionPuzzlesSolved = 0
        sessionTotalScore = 0
        track(AnalyticsEvent.sessionStart())
    }

    fun endSession() {
        val startTime = sessionStartTime ?: return
        val duration = (System.currentTimeMillis() - startTime) / 1000.0

        track(AnalyticsEvent.sessionEnd(duration, sessionPuzzlesSolved, sessionTotalScore))

        sessionStartTime = null
    }

    fun puzzleCompleted(score: Int) {
        sessionPuzzlesSolved++
        sessionTotalScore += score
    }

    // MARK: - User Identification
    fun setUserId(userId: String) {
        currentUserId = userId
        try {
            firebaseAnalytics.setUserId(userId)
            Log.d(TAG, "📊 Analytics User ID set: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set user ID: ${e.message}")
        }
    }

    fun setUserProperties(properties: Map<String, String>) {
        try {
            properties.forEach { (key, value) ->
                firebaseAnalytics.setUserProperty(key, value)
            }
            Log.d(TAG, "📊 Analytics User Properties updated: $properties")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set user properties: ${e.message}")
        }
    }

    // MARK: - Screen Tracking
    fun trackScreenView(screenName: String, screenClass: String = "") {
        track(AnalyticsEvent.screenView(screenName, screenClass))
    }

    // MARK: - Conversion Events
    fun trackConversion(event: AnalyticsEvent, value: Double? = null, currency: String = "USD") {
        val parameters = event.parameters?.toMutableMap() ?: mutableMapOf()

        value?.let { conversionValue ->
            parameters["value"] = conversionValue
            parameters["currency"] = currency
        }

        val conversionEvent = AnalyticsEvent(event.name, parameters)
        track(conversionEvent)

        Log.d(TAG, "💰 Conversion Event: ${event.name} with value: ${value ?: 0}")
    }

    // MARK: - Error Tracking
    fun trackError(error: Throwable, context: String = "") {
        track(AnalyticsEvent("app_error", mapOf(
            "error_description" to (error.message ?: "Unknown error").take(100),
            "context" to context.take(100)
        )))
    }

    fun trackNetworkError(endpoint: String, statusCode: Int, error: Throwable) {
        track(AnalyticsEvent.networkError(
            endpoint = endpoint,
            errorCode = statusCode,
            errorMessage = error.message ?: "Unknown network error"
        ))
    }

    // MARK: - Puzzle-Specific Tracking
    fun trackPuzzlePerformance(
        type: String,
        difficulty: String,
        isCorrect: Boolean,
        timeSpent: Double,
        score: Int,
        hintsUsed: Int = 0,
        additionalData: Map<String, Any> = emptyMap()
    ) {
        val parameters = mutableMapOf<String, Any>(
            "puzzle_type" to type,
            "difficulty" to difficulty,
            "is_correct" to isCorrect,
            "time_spent_seconds" to timeSpent,
            "score" to score.toLong(),
            "hints_used" to hintsUsed.toLong()
        )

        // Add additional data (limit to key Firebase parameters)
        additionalData.forEach { (key, value) ->
            if (parameters.size < 25) { // Firebase has a 25 parameter limit
                parameters[key] = value
            }
        }

        track(AnalyticsEvent("puzzle_performance", parameters))
        puzzleCompleted(score)
    }

    // MARK: - Progress Tracking
    fun updateUserProgress(
        level: Int,
        xp: Int,
        streak: Int,
        puzzlesSolved: Int,
        correctAnswers: Int
    ) {
        // Set user properties for Firebase
        setUserProperties(mapOf(
            "user_level" to level.toString(),
            "total_xp" to xp.toString(),
            "current_streak" to streak.toString(),
            "puzzles_solved" to puzzlesSolved.toString(),
            "correct_answers" to correctAnswers.toString()
        ))

        track(AnalyticsEvent("user_progress_update", mapOf(
            "current_level" to level.toLong(),
            "total_xp" to xp.toLong(),
            "current_streak" to streak.toLong(),
            "total_puzzles_solved" to puzzlesSolved.toLong(),
            "total_correct_answers" to correctAnswers.toLong()
        )))
    }

    // MARK: - Lifecycle Observer
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        track(AnalyticsEvent.appOpen())
        startSession()

        // UPDATED: Handle potential null case
        try {
            DauMauAnalyticsManager.getInstance()?.trackUserActivity()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track user activity: ${e.message}")
        }
    }


    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        track(AnalyticsEvent.appBackground())
        endSession()
    }

    // MARK: - Private Helper Methods
    private fun setDeviceProperties() {
        try {
            setUserProperties(mapOf(
                "device_model" to Build.MODEL,
                "os_version" to Build.VERSION.RELEASE,
                "app_version" to getAppVersion()
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set device properties: ${e.message}")
        }
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun trackPuzzleEventToFirebase(
        eventName: String,
        puzzleType: String,
        additionalParams: Map<String, Any> = emptyMap()
    ) {
        // Existing Firebase Analytics tracking
        val params = mutableMapOf<String, Any>(
            "puzzle_type" to puzzleType,
            "timestamp" to System.currentTimeMillis(),
            "user_id" to (FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous")
        ).apply {
            putAll(additionalParams)
        }

        track(AnalyticsEvent(eventName, params))

        // NEW: Also store in Firestore for querying (matching iOS)
        storeInFirestore(eventName, puzzleType, params)
    }

    private fun storeInFirestore(
        eventName: String,
        puzzleType: String,
        params: Map<String, Any>
    ) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val analyticsData = hashMapOf<String, Any>(
                "event_name" to eventName,
                "puzzle_type" to puzzleType,
                "user_id" to (FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"),
                "timestamp" to System.currentTimeMillis()
            )

            // Safe way to add all params
            params.forEach { (key, value) ->
                analyticsData[key] = value
            }

            // Use the same collection name as iOS
            firestore.collection("puzzle_analytics")
                .add(analyticsData)
                .addOnSuccessListener {
                    Log.d("Analytics", "📊 Stored analytics event in Firebase: $eventName for $puzzleType")
                }
                .addOnFailureListener { e ->
                    Log.w("Analytics", "⚠️ Failed to store analytics event in Firebase", e)
                }
        } catch (e: Exception) {
            Log.e("Analytics", "❌ Error storing analytics event in Firebase", e)
        }
    }
}

// MARK: - Analytics Session Manager
class AnalyticsSessionManager private constructor() {
    companion object {
        @Volatile
        private var INSTANCE: AnalyticsSessionManager? = null

        fun getInstance(): AnalyticsSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AnalyticsSessionManager().also { INSTANCE = it }
            }
        }
    }

    private val loggedEvents = ConcurrentHashMap<String, Long>()
    private var currentSessionId = UUID.randomUUID().toString()
    private var lastAppOpenTime: Long? = null
    private var hasLoggedSessionStart = false

    fun logAppOpenIfNeeded() {
        val now = System.currentTimeMillis()

        if (lastAppOpenTime == null ||
            (lastAppOpenTime != null && now - lastAppOpenTime!! > 1800000)) { // 30 minutes

            AnalyticsManager.getInstance()?.track(AnalyticsEvent.appOpen())
            lastAppOpenTime = now
            currentSessionId = UUID.randomUUID().toString()
            loggedEvents.clear()
            hasLoggedSessionStart = false
            Log.d("AnalyticsSession", "📊 App open logged - New session: $currentSessionId")
        } else {
            Log.d("AnalyticsSession", "📊 App open skipped - Recent session active")
        }
    }

    fun logSessionStartIfNeeded() {
        if (!hasLoggedSessionStart) {
            AnalyticsManager.getInstance()?.track(AnalyticsEvent.sessionStart())
            hasLoggedSessionStart = true
            Log.d("AnalyticsSession", "📊 Session start logged")
        }
    }

    fun logScreenViewIfNeeded(screenName: String) {
        val eventKey = "screen_view_$screenName"
        val now = System.currentTimeMillis()
        val timeBasedKey = "${eventKey}_${now / 60000}" // Per minute

        if (loggedEvents.containsKey(timeBasedKey)) {
            Log.d("AnalyticsSession", "📊 Screen view skipped - Recently logged for $screenName")
            return
        }

        AnalyticsManager.getInstance()?.trackScreenView(screenName)
        loggedEvents[timeBasedKey] = now
        Log.d("AnalyticsSession", "📊 Screen view logged - $screenName")
    }

    fun logUserActionIfNeeded(
        action: String,
        context: String = "",
        puzzleId: String? = null
    ) {
        val contextSuffix = if (context.isEmpty()) "" else "_$context"
        val puzzleSuffix = puzzleId?.let { "_$it" } ?: ""
        val eventKey = "user_action_$action$contextSuffix$puzzleSuffix"
        val now = System.currentTimeMillis()
        val timeBasedKey = "${eventKey}_${now / 5000}" // Per 5 seconds

        if (loggedEvents.containsKey(timeBasedKey)) {
            Log.d("AnalyticsSession", "📊 User action skipped - Recently logged: $action")
            return
        }

        val parameters = mutableMapOf<String, Any>(
            "action" to action,
            "session_id" to currentSessionId
        )

        if (context.isNotEmpty()) {
            parameters["context"] = context
        }

        puzzleId?.let {
            parameters["puzzle_id"] = it
        }

        AnalyticsManager.getInstance()?.track(AnalyticsEvent("user_action", parameters))
        loggedEvents[timeBasedKey] = now
        Log.d("AnalyticsSession", "📊 User action logged - $action")
    }

    fun logPuzzleStartIfNeeded(
        puzzleId: String,
        puzzleType: String,
        difficulty: String,
        questionIndex: Int
    ) {
        val eventKey = "puzzle_start_${puzzleId}_$questionIndex"

        if (loggedEvents.containsKey(eventKey)) {
            Log.d("AnalyticsSession", "📊 Puzzle start skipped - Already logged for $puzzleId at index $questionIndex")
            return
        }

        AnalyticsManager.getInstance()?.track(AnalyticsEvent.puzzleStart(
            type = puzzleType,
            difficulty = difficulty,
            questionIndex = questionIndex
        ))

        loggedEvents[eventKey] = System.currentTimeMillis()
        Log.d("AnalyticsSession", "📊 Puzzle start logged - Type: $puzzleType, Puzzle: $puzzleId, Index: $questionIndex")
    }

    fun logPuzzleCompleteIfNeeded(
        puzzleId: String,
        puzzleType: String,
        difficulty: String,
        isCorrect: Boolean,
        timeSpent: Double,
        score: Int,
        questionIndex: Int,
        totalQuestions: Int
    ) {
        val eventKey = "puzzle_complete_${puzzleId}_$questionIndex"

        if (loggedEvents.containsKey(eventKey)) {
            Log.d("AnalyticsSession", "📊 Puzzle complete skipped - Already logged for $puzzleId")
            return
        }

        AnalyticsManager.getInstance()?.track(AnalyticsEvent.puzzleComplete(
            type = puzzleType,
            difficulty = difficulty,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            score = score,
            questionIndex = questionIndex,
            totalQuestions = totalQuestions
        ))

        loggedEvents[eventKey] = System.currentTimeMillis()
        Log.d("AnalyticsSession", "📊 Puzzle complete logged - $puzzleId, Correct: $isCorrect")
    }

    fun getCurrentSessionId(): String = currentSessionId

    fun resetSession() {
        currentSessionId = UUID.randomUUID().toString()
        loggedEvents.clear()
        hasLoggedSessionStart = false
        lastAppOpenTime = null
        Log.d("AnalyticsSession", "📊 Analytics session reset")
    }

    fun cleanupOldEvents() {
        val cutoffTime = System.currentTimeMillis() - 3600000 // 1 hour ago
        val keysToRemove = mutableListOf<String>()

        loggedEvents.forEach { (key, timestamp) ->
            if (timestamp < cutoffTime) {
                keysToRemove.add(key)
            }
        }

        keysToRemove.forEach { loggedEvents.remove(it) }
    }
}

// MARK: - Extension Functions for Easy Integration
fun androidx.fragment.app.Fragment.trackScreenView(screenName: String) {
    AnalyticsSessionManager.getInstance().logScreenViewIfNeeded(screenName)
}

fun androidx.appcompat.app.AppCompatActivity.trackScreenView(screenName: String) {
    AnalyticsSessionManager.getInstance().logScreenViewIfNeeded(screenName)
}

fun android.view.View.trackButtonTap(buttonName: String, context: String = "") {
    this.setOnClickListener {
        AnalyticsSessionManager.getInstance().logUserActionIfNeeded(
            action = "button_tap_$buttonName",
            context = context
        )
    }
}

fun AnalyticsManager.trackUserActivity() {
    // Integrate with your existing manager
    DauMauAnalyticsManager.getInstance()?.trackUserActivity()
}


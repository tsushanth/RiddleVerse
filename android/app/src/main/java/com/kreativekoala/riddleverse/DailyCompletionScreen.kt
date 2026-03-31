package com.kreativekoala.riddleverse

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.json.JSONObject
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import android.content.Context
import androidx.compose.ui.res.stringResource
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme

// Enhanced data classes for different completion scenarios
sealed class CompletionType {
    data class Success(val streakDays: Int, val earnedPoints: Int) : CompletionType()
    data class NoPuzzlesFound(val puzzleType: String, val difficulty: String) : CompletionType()
    data class GenerationInProgress(val puzzleType: String, val difficulty: String) : CompletionType()
}

data class GenerationRequest(
    val puzzleType: String,
    val difficulty: String,
    val count: Int = 5,
    val modelName: String = "randomized_generator",
    val repairFirst: Boolean = true
)

data class GenerationResponse(
    val success: Boolean,
    val message: String,
    val summary: GenerationSummary?
)

data class GenerationSummary(
    val requested: Int,
    val successful: Int,
    val failed: Int
)

class DailyCompletionActivity : AppCompatActivity() {
    private var adManager: AdManager? = null
    private var isShowingAd = false

    private val subscriptionManager: SubscriptionManager by lazy {
        SubscriptionManager.getInstance(this)
    }

    // Store group information at class level
    private var isGroupCompletion = false
    private var sourceGroupId: String? = null
    private var sourceGroupName: String? = null
    private var groupPuzzleType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            adManager = AdManager.getInstance(this)
            Log.d("DailyCompletionActivity", "AdManager initialized successfully")
        } catch (e: Exception) {
            Log.e("DailyCompletionActivity", "Failed to initialize AdManager", e)
        }
        val streakDays = intent.getIntExtra("streakDays", 1)
        val earnedPoints = intent.getIntExtra("earnedPoints", 100)
        val isCustomPuzzle = intent.getBooleanExtra("isCustomPuzzle", false)
        val customPuzzleId = intent.getStringExtra("customPuzzleId")

        // Extract session statistics and puzzle type
        val sessionStatsJson = intent.getStringExtra("sessionStats")
        val puzzleType = intent.getStringExtra("puzzleType") ?: "unknown"

        val sessionStats = try {
            if (sessionStatsJson != null) {
                Gson().fromJson(sessionStatsJson, SessionStatistics::class.java)
            } else null
        } catch (e: Exception) {
            Log.e("DailyCompletionActivity", "Failed to parse session stats", e)
            null
        }

        // Extract group information
        sourceGroupId = intent.getStringExtra("sourceGroupId")
        sourceGroupName = intent.getStringExtra("sourceGroupName")
        groupPuzzleType = intent.getStringExtra("puzzleType")
        val completedCount = intent.getIntExtra("completedPuzzleCount", 0)

        Log.d("DailyCompletionActivity", "Group completion check:")
        Log.d("DailyCompletionActivity", "  sourceGroupId: $sourceGroupId")
        Log.d("DailyCompletionActivity", "  sourceGroupName: $sourceGroupName")
        Log.d("DailyCompletionActivity", "  puzzleType: $groupPuzzleType")
        Log.d("DailyCompletionActivity", "  completedCount: $completedCount")

        // Check if this is a group completion
        val hasGroupIndicators = sourceGroupId != null && sourceGroupName != null && groupPuzzleType != null
        val hasCompletedEnough = completedCount >= 5

        if (hasGroupIndicators && hasCompletedEnough) {
            Log.d("DailyCompletionActivity", "Group completion detected!")

            // Mark as group completion and update storage
            isGroupCompletion = true
            val userId = FirebaseAuth.getInstance().currentUser?.email ?: ""

            if (sourceGroupId != null && groupPuzzleType != null) {
                GroupCompletionManager.markTypeCompleted(
                    context = this,
                    userId = userId,
                    groupId = sourceGroupId!!,
                    puzzleType = groupPuzzleType!!
                )
            }
        }

        // Determine completion type
        val completionType = when {
            intent.getBooleanExtra("noPuzzlesFound", false) -> {
                CompletionType.NoPuzzlesFound(
                    puzzleType = intent.getStringExtra("puzzleType") ?: "unknown",
                    difficulty = intent.getStringExtra("difficulty") ?: "medium"
                )
            }
            intent.getBooleanExtra("generationInProgress", false) -> {
                CompletionType.GenerationInProgress(
                    puzzleType = intent.getStringExtra("puzzleType") ?: "unknown",
                    difficulty = intent.getStringExtra("difficulty") ?: "medium"
                )
            }
            else -> {
                CompletionType.Success(
                    streakDays = streakDays,
                    earnedPoints = earnedPoints
                )
            }
        }

        // Show the regular completion screen with modified navigation
        showEnhancedCompletionScreen(
            completionType = completionType,
            isCustomPuzzle = isCustomPuzzle,
            customPuzzleId = customPuzzleId,
            sessionStats = sessionStats,
            puzzleType = puzzleType
        )
    }

    private fun showEnhancedCompletionScreen(
        completionType: CompletionType,
        isCustomPuzzle: Boolean,
        customPuzzleId: String?,
        sessionStats: SessionStatistics?,
        puzzleType: String
    ) {
        // Handle custom puzzle leaderboard update if needed
        if (isCustomPuzzle && customPuzzleId != null) {
            val userId = intent.getStringExtra("userId")
            val timeTaken = intent.getLongExtra("timeTaken", 0L)
            val finalScore = intent.getIntExtra("finalScore", 0)

            if (userId != null && finalScore > 0) {
                updateCustomPuzzleLeaderboard(customPuzzleId, userId, timeTaken, finalScore)
            }
        }

        setContent {
            RiddleVerseTheme {
                DailyCompletionScreen(
                    completionType = completionType,
                    isCustomPuzzle = isCustomPuzzle,
                    customPuzzleId = customPuzzleId,
                    sessionStats = sessionStats,
                    puzzleType = puzzleType,
                    isGroupCompletion = isGroupCompletion,
                    sourceGroupName = sourceGroupName,
                    onReturnHome = {
                        if (isGroupCompletion) {
                            navigateBackToGroup()
                        } else {
                            navigateToHome()
                        }
                    },
                    onViewLeaderboard = { puzzleId ->
                        handleViewLeaderboard(puzzleId)
                    },
                    onStartPuzzle = { puzzleTypeParam, difficulty ->
                        Log.d("StartPuzzle", "Starting puzzle: $puzzleTypeParam with difficulty: $difficulty")
                        showAdBeforeNavigation {
                            navigateToStartPuzzle(puzzleTypeParam, difficulty)
                        }
                    },
                    showAdBeforeNavigation = ::showAdBeforeNavigation
                )
            }
        }
    }

    private fun showAdBeforeNavigation(onNavigationReady: () -> Unit) {
        if (subscriptionManager.hasActiveSubscription()) {
            Log.d("DailyCompletionActivity", "User has subscription, skipping ad")
            onNavigationReady()
            return
        }
        val adManager = this.adManager
        if (adManager?.shouldShowSubscriptionUpsell() == true) {
            Log.d("DailyCompletionActivity", "User frustrated with ads, showing subscription upsell")
            showSubscriptionUpsellInsteadOfAd(onNavigationReady)
            return
        }

        if (isShowingAd) {
            Log.d("DailyCompletionActivity", "Ad already showing, skipping")
            onNavigationReady()
            return
        }

        try {
            adManager?.let { manager ->
                isShowingAd = true
                Log.d("DailyCompletionActivity", "Attempting to show completion ad")

                manager.showInterstitialAd(
                    activity = this,
                    adPlacement = "puzzle_completion",
                    onAdClosed = {
                        Log.d("DailyCompletionActivity", "Completion ad closed, proceeding with navigation")
                        isShowingAd = false
                        onNavigationReady()
                    },
                    onAdFailed = { error ->
                        Log.w("DailyCompletionActivity", "Completion ad failed: $error")
                        isShowingAd = false
                        onNavigationReady()
                    },
                    onAdNotReady = {
                        Log.d("DailyCompletionActivity", "Ad not ready, proceeding without ad")
                        isShowingAd = false
                        onNavigationReady()
                    }
                )
            } ?: run {
                Log.w("DailyCompletionActivity", "AdManager not available, proceeding without ad")
                onNavigationReady()
            }
        } catch (e: Exception) {
            Log.e("DailyCompletionActivity", "Error showing completion ad", e)
            isShowingAd = false
            onNavigationReady()
        }
    }

    private fun showSubscriptionUpsellInsteadOfAd(onNavigationReady: () -> Unit) {
        val frustrationMetrics = adManager?.getAdFrustrationMetrics()

        // Track the upsell opportunity
        AnalyticsManager.getInstance()?.track(
            AnalyticsEvent("subscription_upsell_shown_instead_of_ad", mapOf(
                "trigger_reason" to "ad_frustration",
                "session_ads" to (frustrationMetrics?.sessionAdCount ?: 0),
                "session_failures" to (frustrationMetrics?.sessionFailures ?: 0),
                "placement" to "completion_screen"
            ))
        )

        // Show subscription dialog with frustration context
        // You can integrate this with your existing SubscriptionUpgradeDialog
        // For now, just proceed - but this is where you'd show the upsell
        onNavigationReady()
    }

    private fun navigateBackToGroup() {
        Log.d("DailyCompletionActivity", "Navigating back to group: $sourceGroupName")

        val groupIntent = Intent(this, PuzzleGroupActivity::class.java).apply {
            putExtra("groupId", sourceGroupId)
            putExtra("groupName", sourceGroupName)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(groupIntent)
        finish()
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToStartPuzzle(puzzleTypeParam: String, difficulty: String) {
        val intent = Intent(this, StartPuzzleActivity::class.java).apply {
            putExtra("puzzleType", puzzleTypeParam)
            putExtra("title", getPuzzleDisplayName(puzzleTypeParam))
        }
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showAdBeforeNavigation {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun handleViewLeaderboard(puzzleId: String) {
        Log.d("DailyCompletion", "Opening leaderboard for puzzle: $puzzleId")

        fetchPuzzleLeaderboard(
            puzzleId = puzzleId,
            onResult = { leaderboard ->
                if (leaderboard != null) {
                    Log.d("DailyCompletion", "Leaderboard fetched successfully")

                    val leaderboardIntent = Intent(this, PuzzleLeaderboardActivity::class.java).apply {
                        putExtra("puzzleId", puzzleId)
                        putExtra("puzzleName", leaderboard.puzzleName ?: "Custom Puzzle")
                        putExtra("puzzleCreator", leaderboard.puzzleCreator ?: "Unknown")
                        putExtra("totalPlayers", leaderboard.totalPlayers)
                        putExtra("userRank", leaderboard.userRank ?: 0)
                        putExtra("userScore", leaderboard.userScore ?: 0)
                        putExtra("isEmpty", leaderboard.isEmpty)
                        putExtra("userId", intent.getStringExtra("userId"))

                        if (leaderboard.statistics != null) {
                            val gson = Gson()
                            putExtra("statisticsJson", gson.toJson(leaderboard.statistics))
                        }

                        val gson = Gson()
                        putExtra("topPlayersJson", gson.toJson(leaderboard.topPlayers))
                    }
                    startActivity(leaderboardIntent)
                } else {
                    Log.e("DailyCompletion", "Failed to fetch leaderboard")
                    Toast.makeText(
                        this,
                        "Could not load leaderboard for this puzzle",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        isShowingAd = false

        // Force cleanup of any MediaCodec instances
        try {
            Log.d("DailyCompletion", "Forcing MediaCodec cleanup")
            System.gc()
            System.runFinalization()
        } catch (e: Exception) {
            Log.e("DailyCompletion", "Error during MediaCodec cleanup", e)
        }
    }

    override fun onPause() {
        super.onPause()

        // Release media resources early
        try {
            // Force immediate cleanup
            System.gc()
        } catch (e: Exception) {
            Log.e("DailyCompletion", "Error during onPause cleanup", e)
        }
    }

    private fun updateCustomPuzzleLeaderboard(
        puzzleId: String,
        userId: String,
        timeTaken: Long,
        score: Int
    ) {
        val url = "https://puzzleverseai.com/update-puzzle-leaderboard"
        val client = OkHttpClient()

        val json = JSONObject().apply {
            put("puzzleId", puzzleId)
            put("userId", userId)
            put("timeTaken", timeTaken)
            put("score", score)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        Log.d("LeaderboardUpdate", "Request body: $requestBody")

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LeaderboardUpdate", "Failed to update custom puzzle leaderboard", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val json = JSONObject(body)
                        Log.d("LeaderboardUpdate", "JSON Response: $json")

                        if (json.optBoolean("success", true)) {
                            Log.d("LeaderboardUpdate", "Custom puzzle leaderboard updated successfully")

                            // Show achievement toast on main thread
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    this@DailyCompletionActivity,
                                    "Score submitted to leaderboard!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Log.e("LeaderboardUpdate", "Leaderboard update failed: ${json.optString("error")}")
                        }
                    } catch (e: Exception) {
                        Log.e("LeaderboardUpdate", "Error parsing leaderboard response", e)
                    }
                }
            }
        })
    }

    // Missing method implementations that are called but not defined
    private fun fetchPuzzleLeaderboard(
        puzzleId: String,
        onResult: (PuzzleLeaderboard?) -> Unit
    ) {
        val url = "https://puzzleverseai.com/api/puzzle-leaderboard/$puzzleId"
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("FetchLeaderboard", "Failed to fetch leaderboard", e)
                Handler(Looper.getMainLooper()).post {
                    onResult(null)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                Handler(Looper.getMainLooper()).post {
                    if (response.isSuccessful && body != null) {
                        try {
                            val gson = Gson()
                            val leaderboard = gson.fromJson(body, PuzzleLeaderboard::class.java)
                            onResult(leaderboard)
                        } catch (e: Exception) {
                            Log.e("FetchLeaderboard", "Error parsing leaderboard response", e)
                            onResult(null)
                        }
                    } else {
                        onResult(null)
                    }
                }
            }
        })
    }

    private fun getPuzzleDisplayName(puzzleType: String): String {
        return when (puzzleType.lowercase()) {
            "riddle" -> "Riddles"
            "wordpuzzle" -> "Word Puzzles"
            "logic" -> "Logic Puzzles"
            "math" -> "Math Puzzles"
            "trivia" -> "Trivia"
            "brainteaser" -> "Brain Teasers"
            else -> puzzleType.replaceFirstChar { it.uppercase() }
        }
    }
}

// Network function to generate puzzles on demand
fun kickOffPuzzleGeneration(
    puzzleType: String,
    difficulty: String,
    onComplete: () -> Unit
) {
    val url = "https://puzzleverseai.com/api/force-generate-with-path-management"

    val requestData = GenerationRequest(
        puzzleType = puzzleType,
        difficulty = difficulty,
        count = 5,
        modelName = "randomized_generator",
        repairFirst = true
    )

    val requestBody = Gson().toJson(requestData).toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .addHeader("Content-Type", "application/json")
        .build()

    val client = OkHttpClient()

    // Track generation attempt
    try {
        AnalyticsManager.getInstance()?.track(AnalyticsEvent("puzzle_generation_kicked_off", mapOf(
            "puzzle_type" to puzzleType,
            "difficulty" to difficulty,
            "requested_count" to 5,
            "source" to "no_puzzles_screen",
            "approach" to "fire_and_forget"
        )))
    } catch (e: Exception) {
        Log.e("GenerationKickOff", "Analytics failed", e)
    }

    // Fire and forget - don't wait for response
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.w("GenerationKickOff", "Generation request sent but network failed: ${e.message}")
            // Still call onComplete - we tried our best
            Handler(Looper.getMainLooper()).post { onComplete() }
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()
            Log.d("GenerationKickOff", "Generation request sent - Response: ${response.code}")

            // Don't parse or worry about the response - just complete
            Handler(Looper.getMainLooper()).post { onComplete() }
        }
    })
}

@Composable
fun GroupCompletionDialog(
    puzzleType: String,
    sourceGroupName: String?,
    sourceGroupId: String,
    onContinueGroup: () -> Unit,
    onGoHome: () -> Unit
) {
    Log.d("GroupCompletionDialog", "Rendering group completion dialog")

    AlertDialog(
        onDismissRequest = { /* Don't allow dismiss */ },
        title = {
            Text("Puzzle Type Completed!")
        },
        text = {
            Text(
                "Excellent work! You've completed all 5 $puzzleType puzzles.\n\n" +
                        if (sourceGroupName != null) "Next puzzle type in '$sourceGroupName' is now unlocked!"
                        else "You can continue with the next puzzle type in your group."
            )
        },
        confirmButton = {
            Button(onClick = onContinueGroup) {
                Text("Continue Group")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onGoHome) {
                Text(stringResource(R.string.home))
            }
        }
    )
}
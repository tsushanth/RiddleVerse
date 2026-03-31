package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import android.app.Activity
import android.content.ContentValues.TAG
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.kreativekoala.riddleverse.GroupCompletionManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.gson.Gson
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme
import okhttp3.*
import okio.IOException
import org.json.JSONObject
import java.net.URLEncoder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StartPuzzleActivity : AppCompatActivity() {

    // Add these as class properties for new architecture
    private lateinit var userStatsManager: UserStatsManager
    private lateinit var progressionEngine: ProgressionEngine
    private lateinit var dashboardDataProvider: DashboardDataProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the new architecture
        userStatsManager = UserStatsManager.getInstance(this)
        progressionEngine = ProgressionEngine(this, userStatsManager)
        dashboardDataProvider = DashboardDataProvider(this, userStatsManager, progressionEngine)
        //val puzzleQueueManager = PuzzleQueueManager.getInstance(this)
        //puzzleQueueManager.initializeQueuesAsync()

        val isDailyPuzzle = intent.getBooleanExtra("isDailyPuzzle", false) // NEW
        val dailyTopic = intent.getStringExtra("dailyTopic") // NEW
        val userEmail = intent.getStringExtra("userEmail") // NEW
        val puzzleType = intent.getStringExtra("puzzleType") ?: "Unknown"
        val title = intent.getStringExtra("title") ?: "Unknown"
        val isCustomPuzzle = intent.getBooleanExtra("isCustomPuzzle", false)
        val customPuzzleId = intent.getStringExtra("customPuzzleId")

        val sourceGroupId = intent.getStringExtra("sourceGroupId")
        val sourceGroupName = intent.getStringExtra("sourceGroupName")

        val currentUser = Firebase.auth.currentUser

        AnalyticsSessionManager.getInstance().logScreenViewIfNeeded("start_puzzle_screen")

        AnalyticsManager.getInstance()?.track(AnalyticsEvent("puzzle_setup_screen_viewed", mapOf(
            "puzzle_type" to puzzleType,
            "is_daily_puzzle" to isDailyPuzzle,
            "is_custom_puzzle" to isCustomPuzzle,
            "custom_puzzle_id" to (customPuzzleId ?: ""),
            "daily_topic" to (dailyTopic ?: ""),
            "source" to "home_screen",
            "session_id" to AnalyticsSessionManager.getInstance().getCurrentSessionId()
        )))


        setContent {
            RiddleVerseTheme {
                StartPuzzleScreen(
                    quizTitle = title,
                    type = puzzleType,
                    userName = currentUser?.displayName ?: "Guest",
                    isCustomPuzzle = intent.getBooleanExtra("isCustomPuzzle", false),
                    isDailyPuzzle = isDailyPuzzle, // NEW
                    customPuzzleId = intent.getStringExtra("customPuzzleId"),
                    dailyTopic = dailyTopic, // NEW
                    userEmail = userEmail, // NEW
                    sourceGroupId = sourceGroupId,
                    sourceGroupName = sourceGroupName,
                    screenOptions = when (puzzleType) {
                        "daily" -> listOf("multipleChoice", "Match Screen", "Falling Game Screen", "Default")
                        "custom" -> listOf("multipleChoice", "Match Screen", "Default")
                        "connotationwords" -> listOf("Swipe Word Screen")
                        "wordAssociation" -> listOf("Default", "Match Screen")
                        "synonyms" -> listOf("Synonym Grouping Screen")
                        "subtraction" -> listOf("Subtraction Screen")
                        "conversion" -> listOf("Conversion Screen")
                        "synonyms" -> listOf("Antonym Balloon Screen")
                        "mathtipping" -> listOf("Tip Bubble Screen")
                        "anagram" -> listOf("Jumble Input Screen", "Default")
                        "mathestimation" -> listOf("Math Estimation Screen")
                        "purchasing" -> listOf("Subscription Screen")
                        "division" -> listOf("Division Screen")
                        "average" -> listOf("Averages Screen")
                        "discounts" -> listOf("Discount Price Screen")
                        "storyPuzzle" -> listOf("multipleChoice", "Match Screen")
                        "percentages" -> listOf("Percentage Screen")
                        "memorysquares" -> listOf("Memory Squares Screen")
                        "memorystory" -> listOf("Memory Story Screen")
                        "memorysequencing" -> listOf("Memory Sequencing Screen")
                        "triangledotmemory" -> listOf("Triangle Dot Memory Screen")
                        "memoryretention" -> listOf("Memory Retention Screen")
                        "pinballdeflector" -> listOf("Pinball Deflector Screen")
                        "wordprefix" -> listOf("Word Prefix Screen")
                        "memoryprevioussingle" -> listOf("Memory Previous Single Screen")
                        "memorypreviouspair" -> listOf("Memory Previous Pair Screen")
                        "crossword", "crossword (new!)" -> listOf("Crossword Screen")
                        "trainrouting" -> listOf("Train Routing Screen")
                        "wordsearch", "word search" -> listOf("Word Search Screen")
                        "uniqueobject" -> listOf("Unique Object Screen")
                        "mathcomparison" -> listOf("Math Comparison Screen")
                        "numbersequence" -> listOf("Number Sequence Screen")
                        "symbolswipe" -> listOf("Symbol Swipe Screen")
                        "numbersum" -> listOf("Number Sum Screen")
                        "imagevortex" -> listOf("Image Vortex Screen")
                        "colormatching" -> listOf("Color Shape Matching Screen")
                        "flowpuzzle" -> listOf("Flow Puzzle Screen")
                        "progressiverevelation" -> listOf("Progressive Reveal Screen")
                        "realorai" -> listOf("Real or AI")
                        "mathexpression" -> listOf("Math Expression Screen")
                        "dualcard" -> listOf("Dual Card Screen")
                        "mathcrossword" -> listOf("Math Crossword Screen")
                        "colortextmatching" -> listOf("Color Text Matching Screen")
                        "contextswitch" -> listOf("Context Switch Screen")
                        "geography_cities" -> listOf("Geography City Selection Screen")
                        "crypto" -> listOf("Crypto Word Screen")
                        "geography_countries" -> listOf("Geography Country Selection Screen")
                        "sentenceTransitions" -> listOf("Sentence Transitions Screen")
                        "symmetry" -> listOf("Symmetry Screen")
                        "wordsnake", "word snake" -> listOf("Word Snake Screen")
                        "imagequestion" -> listOf("Image Question Screen")
                        "find_differences" -> listOf("Find Differences Screen")
                        "find_object" -> listOf("Find Object Screen")
                        "imagepuzzle", "image puzzle", "\uD83D\uDDBC\uFE0F image puzzle" -> listOf("Image Puzzle Screen")
                        "musicidentification", "\uD83C\uDFB5 music puzzle", "music puzzle" -> listOf("Multi-Match Music Screen")
                        "waldopuzzle" -> listOf("Waldo Puzzle Screen")
                        "letterset" -> listOf("Letter Set Puzzle Screen")
                        "imagematch" -> listOf("Image Match Screen")
                        "multiple choice", "math", "trivia", "q&a", "Multiple Choice" -> listOf("multipleChoice", "Match Screen", "Falling Game Screen", "Default")

                        else -> listOf("Default") // Default for other puzzle types
                    },
                    onBackHome = {
                        val intent = Intent(this, HomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

// ================================
// HELPER CLASSES FOR NEW ARCHITECTURE
// ================================


/**
 * Generate difficulty insight based on puzzle type and difficulty
 */
fun generateDifficultyInsight(puzzleType: String, difficulty: String): String {
    return when (puzzleType.lowercase()) {
        "math" -> when (difficulty) {
            "Easy" -> "Basic arithmetic and simple word problems"
            "Medium" -> "Multi-step problems requiring logical thinking"
            "Hard" -> "Complex calculations and advanced concepts"
            else -> "Mathematical problem solving"
        }
        "anagram" -> when (difficulty) {
            "Easy" -> "Short words with common letter patterns"
            "Medium" -> "Medium-length words with mixed patterns"
            "Hard" -> "Long words with complex arrangements"
            else -> "Word rearrangement challenges"
        }
        "trivia" -> when (difficulty) {
            "Easy" -> "General knowledge and common facts"
            "Medium" -> "Specific knowledge across various topics"
            "Hard" -> "Specialized knowledge and obscure facts"
            else -> "Knowledge-based questions"
        }
        "wordsearch" -> when (difficulty) {
            "Easy" -> "Small grids with horizontal/vertical words"
            "Medium" -> "Medium grids with diagonal patterns"
            "Hard" -> "Large grids with complex word placement"
            else -> "Hidden word finding challenges"
        }
        "crypto" -> when (difficulty) {
            "Easy" -> "Simple substitution ciphers"
            "Medium" -> "Mixed cipher techniques"
            "Hard" -> "Complex encryption patterns"
            else -> "Code-breaking challenges"
        }
        "wordsnake" -> when (difficulty) {
            "Easy" -> "Short word chains with clear connections"
            "Medium" -> "Longer chains requiring word association"
            "Hard" -> "Complex paths with challenging connections"
            else -> "Word connection puzzles"
        }
        else -> when (difficulty) {
            "Easy" -> "Beginner-friendly challenges"
            "Medium" -> "Moderate difficulty requiring focus"
            "Hard" -> "Advanced puzzles for experienced solvers"
            else -> "Cognitive training exercises"
        }
    }
}

/**
 * Generate top scores based on high score
 */
fun generateTopScores(highScore: Int): List<Int> {
    if (highScore <= 0) return emptyList()

    val scores = mutableListOf<Int>()
    scores.add(highScore)

    // Generate 9 more scores slightly below the high score
    for (i in 1..9) {
        val reduction = (highScore * (i * 0.02f + 0.01f)).toInt()
        val score = (highScore - reduction).coerceAtLeast(0)
        scores.add(score)
    }

    return scores.sortedDescending().take(10)
}

/**
 * Convert UserPuzzleStats to PuzzleStatistics for UI display
 */
fun UserPuzzleStats.toPuzzleStatistics(difficultyRating: String = "250/400"): PuzzleStatistics {
    return PuzzleStatistics(
        highScore = this.highScore,
        difficulty = difficultyRating,
        timesTrained = this.totalTimeSpentHours,
        wins = this.wins,
        topScores = generateTopScores(this.highScore),
        totalPlays = this.totalPlays,
        averageScore = this.averageScore,
        winRate = this.winRate,
        longestStreak = 0, // This would need to be passed separately
        totalTimeSpent = this.totalTimeSpentHours
    )
}

// ADD: Function to track completion and show group-specific dialog
fun handlePuzzleCompletion(
    activity: AppCompatActivity,
    score: Int,
    puzzleType: String,
    completedCount: Int,
    sourceGroupId: String?,
    sourceGroupName: String?
) {
    // Mark type as completed if user finished all 5 and came from a group
    if (completedCount >= 5 && sourceGroupId != null) {
        val userId = FirebaseAuth.getInstance().currentUser?.email ?: ""

        // Mark the puzzle type as completed in local storage
        GroupCompletionManager.markTypeCompleted(
            context = activity,
            userId = userId,
            groupId = sourceGroupId,
            puzzleType = puzzleType
        )

        // Show completion dialog with navigation back to group
        showGroupCompletionDialog(activity, score, puzzleType, sourceGroupName, sourceGroupId)
    } else {
        // Regular completion handling for non-group puzzles
        showRegularCompletionDialog(activity, score)
    }
}

// UPDATE: Group-specific completion dialog
fun showGroupCompletionDialog(
    activity: AppCompatActivity,
    score: Int,
    puzzleType: String,
    sourceGroupName: String?,
    sourceGroupId: String
) {
    val dialog = androidx.appcompat.app.AlertDialog.Builder(activity)
        .setTitle("🎉 Puzzle Type Completed!")
        .setMessage(
            "Excellent work! You've completed all 5 $puzzleType puzzles.\n\n" +
                    "Score: $score points\n\n" +
                    if (sourceGroupName != null) "Next puzzle type in '$sourceGroupName' is now unlocked!"
                    else "You can continue with the next puzzle type in your group."
        )
        .setPositiveButton("Continue Group") { _, _ ->
            // Set result and finish to return to group
            val resultIntent = Intent().apply {
                putExtra("groupCompleted", true)
                putExtra("groupId", sourceGroupId)
            }
            activity.setResult(Activity.RESULT_OK, resultIntent) // CHANGED THIS LINE
            activity.finish()
        }
        .setNegativeButton("Home") { _, _ ->
            // Go to home
            activity.finish()
        }
        .setCancelable(false)
        .create()

    dialog.show()
}

// ADD: Regular completion dialog for non-group puzzles
fun showRegularCompletionDialog(activity: AppCompatActivity, score: Int) {
    val dialog = androidx.appcompat.app.AlertDialog.Builder(activity)
        .setTitle("🎉 Puzzle Completed!")
        .setMessage("Great job! Your score: $score points")
        .setPositiveButton("Continue") { _, _ ->
            activity.finish()
        }
        .setCancelable(false)
        .create()

    dialog.show()
}

fun fetchDailyPuzzleAndStart(
    context: android.content.Context,
    userEmail: String,
    topic: String,
    selectedScreenType: String,
    sourceGroupId: String? = null,
    sourceGroupName: String? = null,
    puzzleType: String? = null,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val TAG = "FetchDailyPuzzleEnhanced"
    val url = "https://puzzleverseai.com/get-daily-puzzle?email=${URLEncoder.encode(userEmail, "UTF-8")}&topic=${URLEncoder.encode(topic, "UTF-8")}"
    val request = Request.Builder().url(url).build()
    val client = OkHttpClient()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e(TAG, "❌ Failed to load daily puzzle", e)
            Handler(Looper.getMainLooper()).post {
                try {
                    AnalyticsManager.getInstance()?.track(AnalyticsEvent("daily_puzzle_start_failed", mapOf(
                        "topic" to topic,
                        "user_email" to userEmail,
                        "error_type" to "network_error",
                        "error_message" to (e.message ?: "unknown"),
                        "screen_type" to selectedScreenType,
                        "has_group_context" to (sourceGroupId != null)
                    )))
                } catch (analyticsError: Exception) {
                    Log.e(TAG, "Analytics tracking failed", analyticsError)
                }
                onError("Network error: ${e.message}")
            }
        }

        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string()

            try {
                if (!response.isSuccessful || body == null) {
                    throw IOException("Unexpected response: ${response.code}")
                }

                val json = JSONObject(body)
                if (json.has("error")) {
                    throw IOException(json.getString("error"))
                }

                val puzzleSetObject = json.getJSONObject("puzzleSet")
                val puzzlesArray = puzzleSetObject.getJSONArray("puzzles")
                val puzzles = mutableListOf<DailyPuzzle>()

                for (i in 0 until puzzlesArray.length()) {
                    val puzzleJson = puzzlesArray.getJSONObject(i)
                    val puzzle = DailyPuzzle(
                        puzzleId = "daily_${topic}_$i",
                        question = puzzleJson.getString("question"),
                        answer = puzzleJson.getString("answer"),
                        hint = puzzleJson.optString("hint", ""),
                        options = puzzleJson.getJSONArray("options").let { optionsArray ->
                            mutableListOf<String>().apply {
                                for (j in 0 until optionsArray.length()) {
                                    add(optionsArray.getString(j))
                                }
                            }
                        },
                        difficulty = puzzleJson.optString("difficulty", "Easy")
                    )
                    puzzles.add(puzzle)
                }

                val convertedPuzzles = puzzles.map { dailyPuzzle ->
                    Puzzle(
                        puzzleId = dailyPuzzle.puzzleId,
                        question = dailyPuzzle.question,
                        answer = dailyPuzzle.answer,
                        hint = dailyPuzzle.hint,
                        difficulty = dailyPuzzle.difficulty,
                        puzzleType = "daily",
                        options = dailyPuzzle.options
                    )
                }

                Handler(Looper.getMainLooper()).post {
                    try {
                        val firstPuzzle = convertedPuzzles.firstOrNull()
                        if (firstPuzzle != null) {
                            AnalyticsSessionManager.getInstance().logPuzzleStartIfNeeded(
                                puzzleId = firstPuzzle.puzzleId,
                                puzzleType = "daily",
                                difficulty = firstPuzzle.difficulty ?: "Easy",
                                questionIndex = 0
                            )

                            AnalyticsManager.getInstance()?.track(AnalyticsEvent.puzzleStart(
                                type = "daily",
                                difficulty = firstPuzzle.difficulty ?: "Easy",
                                questionIndex = 0
                            ))

                            AnalyticsManager.getInstance()?.track(AnalyticsEvent("daily_puzzle_started", mapOf(
                                "topic" to topic,
                                "puzzle_count" to puzzles.size,
                                "screen_type" to selectedScreenType,
                                "user_email" to userEmail,
                                "generation_date" to puzzleSetObject.optString("generationDate", ""),
                                "has_options" to (firstPuzzle.options?.isNotEmpty() == true),
                                "has_group_context" to (sourceGroupId != null),
                                "source_group_id" to (sourceGroupId ?: "none"),
                                "session_id" to AnalyticsSessionManager.getInstance().getCurrentSessionId()
                            )))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to track daily puzzle start", e)
                    }

                    val finalScreenType = when {
                        selectedScreenType != "Default" -> selectedScreenType
                        puzzles.all { it.options.isNotEmpty() } -> "multipleChoice"
                        else -> "qa"
                    }

                    val puzzleSetJson = Gson().toJson(
                        mapOf(
                            "topic" to puzzleSetObject.optString("topic", topic),
                            "format" to "Daily Quiz",
                            "source" to "PuzzleVerseAI Daily",
                            "generationDate" to puzzleSetObject.optString("generationDate", ""),
                            "puzzleCount" to puzzleSetObject.optInt("puzzleCount", puzzles.size),
                            "puzzles" to convertedPuzzles
                        )
                    )

                    // 🔧 CRITICAL: Add group context to daily puzzle intent
                    val intent = Intent(context, PuzzleActivity::class.java).apply {
                        putExtra("puzzleList", Gson().toJson(convertedPuzzles))
                        putExtra("puzzleIndex", 0)
                        putExtra("customPuzzleSet", puzzleSetJson)
                        putExtra("screenType", finalScreenType)
                        putExtra("score", 0)
                        putExtra("startTime", System.currentTimeMillis())
                        putExtra("isDailyPuzzle", true)
                        putExtra("dailyTopic", topic)
                        putExtra("puzzleSource", "daily")

                        // 🔧 NEW: Add group context if available
                        if (sourceGroupId != null && sourceGroupName != null) {
                            putExtra("sourceGroupId", sourceGroupId)
                            putExtra("sourceGroupName", sourceGroupName)
                            putExtra("puzzleType", puzzleType ?: "daily")

                            Log.d(TAG, "🎯 Adding group context to daily puzzle:")
                            Log.d(TAG, "  sourceGroupId: $sourceGroupId")
                            Log.d(TAG, "  sourceGroupName: $sourceGroupName")
                            Log.d(TAG, "  puzzleType: ${puzzleType ?: "daily"}")
                        }
                    }

                    context.startActivity(intent)
                    onSuccess()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Parsing error: ${e.message}", e)
                Handler(Looper.getMainLooper()).post {
                    try {
                        AnalyticsManager.getInstance()?.track(AnalyticsEvent("daily_puzzle_start_failed", mapOf(
                            "topic" to topic,
                            "user_email" to userEmail,
                            "error_type" to "parsing_error",
                            "error_message" to (e.message ?: "unknown"),
                            "screen_type" to selectedScreenType,
                            "has_group_context" to (sourceGroupId != null)
                        )))
                    } catch (analyticsError: Exception) {
                        Log.e(TAG, "Analytics tracking failed", analyticsError)
                    }
                    onError("Error parsing daily puzzle: ${e.message}")
                }
            }
        }
    })
}

@Composable
fun StartPuzzleScreen(
    userName: String = "Esther Howard",
    userPoints: Int = 5099,
    userStreak: Int = 5,
    quizTitle: String = "Logical Twist",
    type: String = "math",
    quizSubtitle: String = "Test your logic in 10 minutes",
    screenOptions: List<String> = listOf("Default", "Match Screen", "Word Fill Screen"),
    rewardPoints: Int = 250,
    isCustomPuzzle: Boolean = false,
    customPuzzleId: String? = null,
    isDailyPuzzle: Boolean = false,
    dailyTopic: String? = null,
    userEmail: String? = null,
    sourceGroupId: String? = null,
    sourceGroupName: String? = null,
    onPlayNow: () -> Unit = {},
    onBackHome: () -> Unit = {}
) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val gameGenVM: GameGenerationViewModel = viewModel()

    // All existing state variables
    val tutorialPrefs = remember { TutorialPreferences(context) }

    // Auto-show tutorial for first-time users if they haven't seen it and puzzle has a tutorial
    var showTutorial by remember { mutableStateOf(tutorialPrefs.shouldAutoShowTutorial(type)) }

    // Only show banner if not auto-showing and user hasn't seen tutorial
    var showTutorialBanner by remember {
        mutableStateOf(!showTutorial && tutorialPrefs.shouldShowTutorialPrompt(type))
    }

    val limitManager = remember { RegenerationLimitManager.getInstance(context) }
    var showLimitCard by remember { mutableStateOf(false) }
    var currentLimitInfo by remember { mutableStateOf<RegenerationLimitManager.LimitInfo?>(null) }

    var prefetchedPuzzle by remember { mutableStateOf<Puzzle?>(null) }
    var prefetchedCustomPuzzle by remember { mutableStateOf<List<Puzzle>?>(null) }
    var prefetchedDailyPuzzle by remember { mutableStateOf<List<Puzzle>?>(null) }
    var prefetchInProgress by remember { mutableStateOf(false) }
    var prefetchError by remember { mutableStateOf<String?>(null) }

    // 🔧 FIXED: Replace ProgressionManager with new architecture
    val difficultyManager = remember { DifficultySettingsManager(context) }
    val userStatsManager = remember { UserStatsManager.getInstance(context) }
    val progressionEngine = remember { ProgressionEngine(context, userStatsManager) }
    val dashboardDataProvider = remember { DashboardDataProvider(context, userStatsManager, progressionEngine) }

    var selectedDifficulty by remember { mutableStateOf(difficultyManager.loadDifficulty()) }
    var selectedOption by remember { mutableStateOf(screenOptions.firstOrNull() ?: "Default") }
    var selectedMinutes by remember { mutableIntStateOf(5) }
    var isLoading by remember { mutableStateOf(false) }
    val minuteOptions = listOf(5, 10, 20)
    var expanded by remember { mutableStateOf(false) }
    var showDifficultyHint by remember { mutableStateOf(false) }
    var showRemixSheet by remember { mutableStateOf(false) }

    // 🔧 FIXED: Use new architecture for difficulty insight
    val difficultyInsight = remember(type, selectedDifficulty) {
        generateDifficultyInsight(type, selectedDifficulty)
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Statistics data
    var statsData by remember { mutableStateOf<PuzzleStatistics?>(null) }
    var isLoadingStats by remember { mutableStateOf(true) }
    var showRegenerationScreen by remember { mutableStateOf(false) }
    var regenerationInfo by remember { mutableStateOf<RegenerationInfo?>(null) }

// Add this early return check right after the tutorial check:
    if (showRegenerationScreen && regenerationInfo != null) {
        RegenerationInProgressScreen(
            regenerationInfo = regenerationInfo!!,
            onRetry = {
                showRegenerationScreen = false
                regenerationInfo = null
                // Reset prefetch state to trigger retry
                prefetchedPuzzle = null
                prefetchInProgress = false
                prefetchError = null
            },
            onBack = {
                showRegenerationScreen = false
                onBackHome()
            }
        )
        return
    }

    LaunchedEffect(type) {
        val cachedLimitInfo = limitManager.getLimitInfo(type)
        if (cachedLimitInfo != null) {
            currentLimitInfo = cachedLimitInfo
            showLimitCard = true
        }
    }

    LaunchedEffect(type, isDailyPuzzle, isCustomPuzzle) {
        // Only fetch for AI puzzles (not daily or custom)
        if (!isDailyPuzzle && !isCustomPuzzle && !prefetchInProgress && prefetchedPuzzle == null) {
            // Skip local puzzle types
            if (!isOfflinePuzzleType(type)) {

                prefetchInProgress = true
                prefetchError = null

                // CHANGED: Use non-blocking prefetch
                fetchAIPuzzleProactiveNonBlocking(
                    context = context,
                    type = type,
                    difficulty = selectedDifficulty.lowercase(),
                    sourceGroupId = sourceGroupId,
                    sourceGroupName = sourceGroupName,
                    onSuccess = { puzzle ->
                        prefetchedPuzzle = puzzle
                        prefetchInProgress = false
                    },
                    onError = { error ->
                        prefetchError = error
                        prefetchInProgress = false
                        // Don't show error for prefetch failures - just log
                        Log.w("StartPuzzleScreen", "Prefetch failed, will fallback to direct API: $error")
                    },
                    onLimitReached = { limitInfo ->
                        currentLimitInfo = limitInfo
                        showLimitCard = true
                        prefetchInProgress = false
                    },
                    onRegenerationInProgress = { regenInfo ->
                        regenerationInfo = regenInfo
                        showRegenerationScreen = true
                        prefetchInProgress = false
                    }
                )
            }
        }
    }

    LaunchedEffect(selectedDifficulty) {
        if (!isDailyPuzzle && !isCustomPuzzle) {
            // Skip local puzzle types
            if (!isOfflinePuzzleType(type)) {

                prefetchedPuzzle = null
                if (!prefetchInProgress) {
                    prefetchInProgress = true
                    prefetchError = null

                    // CHANGED: Use non-blocking prefetch
                    fetchAIPuzzleProactiveNonBlocking(
                        context = context,
                        type = type,
                        difficulty = selectedDifficulty.lowercase(),
                        sourceGroupId = sourceGroupId,
                        sourceGroupName = sourceGroupName,
                        onSuccess = { puzzle ->
                            prefetchedPuzzle = puzzle
                            prefetchInProgress = false
                        },
                        onError = { error ->
                            prefetchError = error
                            prefetchInProgress = false
                            // Don't show error for prefetch failures
                            Log.w("StartPuzzleScreen", "Difficulty change prefetch failed: $error")
                        },
                        onLimitReached = { limitInfo ->
                            currentLimitInfo = limitInfo
                            showLimitCard = true
                            prefetchInProgress = false
                        },
                        onRegenerationInProgress = { regenInfo ->
                            regenerationInfo = regenInfo
                            showRegenerationScreen = true
                            prefetchInProgress = false
                        }
                    )
                }
            }
        }
    }

    // 🔧 FIXED: Load statistics using new architecture
    LaunchedEffect(type, selectedDifficulty) {
        isLoadingStats = true

        try {
            // Get user stats from new architecture
            val userStats = userStatsManager.getPuzzleStats(type)
            val globalStats = userStatsManager.getGlobalStats()

            // Calculate difficulty-specific metrics
            val difficultyMultiplier = when(selectedDifficulty) {
                "Easy" -> 1.0f
                "Medium" -> 1.25f
                "Hard" -> 1.5f
                else -> 1.25f
            }

            // Convert to UI format
            statsData = PuzzleStatistics(
                highScore = userStats.highScore,
                difficulty = "${(userStats.averageScore * difficultyMultiplier).toInt()}/400",
                timesTrained = userStats.totalTimeSpentHours,
                wins = userStats.wins,
                topScores = generateTopScores(userStats.highScore),
                totalPlays = userStats.totalPlays,
                averageScore = userStats.averageScore,
                winRate = userStats.winRate,
                longestStreak = progressionEngine.getBestStreak(),
                totalTimeSpent = userStats.totalTimeSpentHours
            )

            Log.d("PuzzleStats", "📊 Loaded real stats for $type: plays=${userStats.totalPlays}, highScore=${userStats.highScore}")
        } catch (e: Exception) {
            Log.e("PuzzleStats", "Failed to load stats", e)
            // Fallback to default stats
            statsData = PuzzleStatistics(
                highScore = 0,
                difficulty = "0/400",
                timesTrained = 0f,
                wins = 0,
                topScores = emptyList(),
                totalPlays = 0,
                averageScore = 0,
                winRate = 0f,
                longestStreak = 0,
                totalTimeSpent = 0f
            )
        }

        isLoadingStats = false
    }

    // Existing tutorial logic
    if (showTutorial) {
        when (type.lowercase()) {

            "imagepuzzle", "image puzzle", "\uD83D\uDDBC\uFE0F image puzzle" -> {
                ImagePuzzleTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Image puzzle tutorial completed! Ready to solve some picture puzzles?", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }


            "crypto" -> {
                CryptoPuzzleTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Crypto tutorial completed! Ready to crack some secret codes?", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }
            "wordsnake" -> {
                WordSnakePuzzleTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Word Snake tutorial completed! Ready to build some snake paths?", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }
            "mathestimation" -> {
                MathEstimationTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Tutorial completed! Ready to play?", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }
            "division" -> {
                DivisionTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Division tutorial completed!", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }
            "conversion" -> {
                ConversionTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Conversion tutorial completed!", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }
            "discounts" -> {
                DiscountPriceTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Discount tutorial completed!", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }
            "memorysquares" -> {
                MemorySquaresTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Memory Squares tutorial completed!", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }
            "synonyms" -> {
                SynonymGroupingTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Synonym tutorial completed!", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }
            "antonyms", "antonymballoon" -> {
                AntonymBalloonTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Antonym tutorial completed!", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }
            "crossword" -> {
                CrosswordPuzzleTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Crossword tutorial completed!", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }
            "pinballdeflector" -> {
                PinballDeflectorTutorialScreen(
                    onTutorialComplete = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                        Toast.makeText(context, "Pinball Deflector tutorial completed!", Toast.LENGTH_SHORT).show()
                    },
                    onTutorialSkipped = {
                        showTutorial = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    onBack = { showTutorial = false }
                )
            }

            // ============ SIMPLE TUTORIALS ============

            // Wave 1: Number Sum, Symbol Swipe, Word Search, Averages
            "numbersum" -> {
                NumberSumTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "symbolswipe" -> {
                SymbolSwipeTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "wordsearch" -> {
                WordSearchTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "averages" -> {
                AveragesTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }

            // Wave 2: Symmetry, Color Text Matching, Number Sequence, Percentage
            "symmetry" -> {
                SymmetryTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "colortextmatching" -> {
                ColorTextMatchingTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "numbersequence" -> {
                NumberSequenceTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "percentage" -> {
                PercentageTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }

            // Wave 3: Math puzzles
            "subtraction" -> {
                SubtractionTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "mathcomparison" -> {
                MathComparisonTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "mathexpression" -> {
                MathExpressionTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "mathcrossword" -> {
                MathCrosswordTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "tipbubble" -> {
                TipBubbleTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }

            // Wave 3: Memory puzzles
            "memorystory" -> {
                MemoryStoryTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "memorysequencing" -> {
                MemorySequencingTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "memoryretention" -> {
                MemoryRetentionTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "memoryprevioussingle" -> {
                MemoryPreviousSingleTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "memorypreviouspair" -> {
                MemoryPreviousPairTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "triangledotmemory" -> {
                TriangleDotMemoryTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }

            // Wave 3: Word/Language puzzles
            "swipeword" -> {
                SwipeWordTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "jumbleinput" -> {
                JumbleInputTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "wordprefix" -> {
                WordPrefixTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "letterset" -> {
                LetterSetTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "sentencetransitions" -> {
                SentenceTransitionsTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }

            // Wave 3: Matching/Pattern puzzles
            "colorshapematching" -> {
                ColorShapeMatchingTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "imagematch" -> {
                ImageMatchTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "match" -> {
                MatchScreenTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }

            // Wave 3: Visual/Image puzzles
            "imagevortex" -> {
                ImageVortexTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "imagequestion" -> {
                ImageQuestionTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "finddifferences" -> {
                FindDifferencesTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "findobject" -> {
                FindObjectTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "waldopuzzle" -> {
                WaldoPuzzleTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "uniqueobject" -> {
                UniqueObjectTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "progressivereveal" -> {
                ProgressiveRevealTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "realorai" -> {
                RealOrAiTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }

            // Wave 3: Logic/Other puzzles
            "flowpuzzle" -> {
                FlowPuzzleTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "contextswitch" -> {
                ContextSwitchTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "dualcard" -> {
                DualCardTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "multimatchmusic" -> {
                MultiMatchMusicTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "geographycity" -> {
                GeographyCityTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "geographycountry" -> {
                GeographyCountryTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }

            // Basic puzzle types
            "qa" -> {
                QATutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }
            "multiplechoice" -> {
                MultipleChoiceTutorialScreen(
                    onTutorialComplete = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onTutorialSkipped = { showTutorial = false; tutorialPrefs.markTutorialSeen(type) },
                    onBack = { showTutorial = false }
                )
            }

            else -> {
                // No tutorial available for this type, mark as seen to avoid showing again
                tutorialPrefs.markTutorialSeen(type)
                showTutorial = false
            }
        }
        return
    }

    // Existing analytics tracking
    LaunchedEffect(Unit) {
        try {
            AnalyticsSessionManager.getInstance().logScreenViewIfNeeded("start_puzzle_screen")
        } catch (e: Exception) {
            Log.e("StartPuzzleScreen", "Screen view tracking failed", e)
        }
    }

    LaunchedEffect(selectedDifficulty) {
        if (!isCustomPuzzle) {
            try {
                AnalyticsManager.getInstance()?.track(AnalyticsEvent("difficulty_selected", mapOf(
                    "difficulty" to selectedDifficulty,
                    "puzzle_type" to type,
                    "source" to "start_puzzle_screen"
                )))
            } catch (e: Exception) {
                Log.e("StartPuzzleScreen", "Difficulty tracking failed", e)
            }
        }
    }

    // Get user info
    val currentUser = FirebaseAuth.getInstance().currentUser
    val actualUserName = currentUser?.displayName ?: userName
    val localScore = remember { UserScoreStore.load(context) }

    // 🚀 MAIN UI WITH FLOATING BUTTONS
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Main scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A1A),
                            Color(0xFF2D2D2D)
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 140.dp) // 🚀 CRITICAL: Add bottom padding for floating buttons
        ) {
            // Header with close button and actions
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackHome) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row {
                    // Help/Tutorial button
                    if (!showTutorialBanner) {
                        IconButton(
                            onClick = {
                                try {
                                    AnalyticsManager.getInstance()?.track(AnalyticsEvent("tutorial_started", mapOf(
                                        "puzzle_type" to type,
                                        "source" to "help_button"
                                    )))
                                } catch (e: Exception) {
                                    Log.e("StartPuzzleScreen", "Tutorial start tracking failed", e)
                                }

                                when (type.lowercase()) {
                                    "\\uD83D\\uDDBC\\uFE0F image puzzle", "imagepuzzle", "crypto","wordsnake","division", "antonyms", "antonymballoon", "memorysquares", "synonyms",
                                    "mathestimation", "discounts", "conversion", "crossword", "pinballdeflector" -> {
                                        showTutorial = true
                                    }
                                    else -> {
                                        try {
                                            AnalyticsManager.getInstance()?.track(AnalyticsEvent("tutorial_not_available_clicked", mapOf(
                                                "puzzle_type" to type,
                                                "source" to "help_button"
                                            )))
                                        } catch (e: Exception) {
                                            Log.e("StartPuzzleScreen", "Failed to track tutorial demand", e)
                                        }
                                        Toast.makeText(
                                            context,
                                            "Interactive tutorial for ${getPuzzleDisplayName(type)} - Coming soon!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Help,
                                contentDescription = stringResource(R.string.hint),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    IconButton(onClick = { /* Toggle favorite */ }) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.your_favorites),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title and Category Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isDailyPuzzle) "Daily $quizTitle" else quizTitle,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isDailyPuzzle) "Your personalized daily challenge" else getPuzzleCategory(type),
                    fontSize = 18.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Group context indicator (existing functionality)
            if (sourceGroupId != null && sourceGroupName != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF444444)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "From Group",
                            tint = Color(0xFF00A8E8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "From: $sourceGroupName",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Tutorial Banner (existing functionality)
            AnimatedVisibility(
                visible = showTutorialBanner,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                TutorialBanner(
                    puzzleType = type,
                    onStartTutorial = {
                        try {
                            AnalyticsManager.getInstance()?.track(AnalyticsEvent("tutorial_started", mapOf(
                                "puzzle_type" to type,
                                "source" to "start_puzzle_banner"
                            )))
                        } catch (e: Exception) {
                            Log.e("StartPuzzleScreen", "Tutorial start tracking failed", e)
                        }

                        when (type.lowercase()) {
                            "crypto","wordsnake","division", "antonyms", "antonymballoon", "memorysquares", "synonyms",
                            "mathestimation", "discounts", "conversion", "crossword", "pinballdeflector" -> {
                                showTutorial = true
                            }
                            else -> {
                                Toast.makeText(
                                    context,
                                    "Interactive tutorial for ${getPuzzleDisplayName(type)} - Coming soon!",
                                    Toast.LENGTH_LONG
                                ).show()
                                showTutorialBanner = false
                                tutorialPrefs.markTutorialSeen(type)
                            }
                        }
                    },
                    onDismiss = {
                        showTutorialBanner = false
                        tutorialPrefs.markTutorialSeen(type)
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (isLoadingStats) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight * 0.22f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                statsData?.let { stats ->
                    // Main Statistics Cards (like your reference)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StatCard(
                            title = "HIGH SCORE",
                            value = stats.highScore.toString(),
                            modifier = Modifier.weight(1f)
                        )

                        StatCard(
                            title = "DIFFICULTY",
                            value = stats.difficulty,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Benefits Section
                    BenefitsSection(puzzleType = type)

                    Spacer(modifier = Modifier.height(24.dp))

                    // Advanced Stats
                    Text(
                        text = "ADVANCED STATS:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StatCard(
                            title = "TIME TRAINED",
                            value = "${stats.timesTrained} hrs",
                            modifier = Modifier.weight(1f)
                        )

                        StatCard(
                            title = "WINS",
                            value = stats.wins.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Top Scores Section
                    TopScoresSection(topScores = stats.topScores)

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Error Message Display (existing functionality)
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF5D4037)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Error",
                            tint = Color(0xFFFFAB91),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = when {
                                error.contains("No puzzles found") -> "No daily puzzles available for ${dailyTopic ?: "this topic"} today."
                                error.contains("Network error") -> "Connection issue. Please check your internet."
                                else -> "Unable to load puzzle. Please try again."
                            },
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = { errorMessage = null },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Dismiss",
                                fontSize = 12.sp,
                                color = Color(0xFFFFAB91)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Game Configuration Section (moved to bottom)
            GameConfigurationSection(
                isCustomPuzzle = isCustomPuzzle,
                selectedDifficulty = selectedDifficulty,
                onDifficultyChanged = {
                    selectedDifficulty = it
                    difficultyManager.saveDifficulty(it) // Save the preference
                },
                selectedOption = selectedOption,
                onOptionChanged = { selectedOption = it },
                screenOptions = screenOptions,
                difficultyInsight = difficultyInsight,
                showDifficultyHint = showDifficultyHint,
                onToggleDifficultyHint = { showDifficultyHint = !showDifficultyHint }
            )

            // Extra spacing at bottom to ensure content is scrollable above floating buttons
            Spacer(modifier = Modifier.height(40.dp))
        }

        AnimatedVisibility(
            visible = showLimitCard && currentLimitInfo != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            currentLimitInfo?.let { limitInfo ->
                RegenerationLimitCard(
                    puzzleType = type,
                    limitInfo = limitInfo,
                    onDismiss = {
                        showLimitCard = false
                        limitManager.clearLimitInfo(type)
                    },
                    onUpgrade = {
                        // Handle upgrade navigation
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://puzzleverseai.com/upgrade"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Visit puzzleverseai.com to upgrade", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        fun executeOriginalLogic() {
            if (!isLoading) {
                errorMessage = null
                isLoading = true

                // 🔧 FIX: Helper function to create intent with group context
                fun createPuzzleIntent(baseIntent: Intent): Intent {
                    return baseIntent.apply {
                        // 🔧 CRITICAL: Always add group context if available
                        if (sourceGroupId != null && sourceGroupName != null) {
                            putExtra("sourceGroupId", sourceGroupId)
                            putExtra("sourceGroupName", sourceGroupName)
                            putExtra("puzzleType", type)

                            Log.d("RedesignedStartPuzzle", "🎯 Adding group context to intent:")
                            Log.d("RedesignedStartPuzzle", "  sourceGroupId: $sourceGroupId")
                            Log.d("RedesignedStartPuzzle", "  sourceGroupName: $sourceGroupName")
                            Log.d("RedesignedStartPuzzle", "  puzzleType: $type")
                        }
                    }
                }

                if (isOfflinePuzzleType(type)) {
                    Log.d("RedesignedStartPuzzle", "🏠 Creating local puzzle for type: $type")

                    // Create a simple local puzzle instead of fetching from server
                    val localPuzzle = when (type) {
                        "pinballdeflector" -> {
                            Log.d(TAG, "🎯 Generating local Pinball Deflector puzzle")

                            val (questionJson, answerJson) = PinballDeflectorGenerator.generatePuzzle(selectedDifficulty)

                            Log.d(TAG, "📊 Generated Pinball Deflector puzzle:")
                            Log.d(TAG, "   Difficulty: $selectedDifficulty")
                            Log.d(TAG, "   Question JSON length: ${questionJson.length}")
                            Log.d(TAG, "   Answer JSON length: ${answerJson.length}")

                            // Parse to log matrix details
                            try {
                                val questionData = JSONObject(questionJson)
                                val matrixSize = questionData.getInt("matrixSize")
                                val memoryTime = questionData.getInt("memoryTime")
                                val startPosArray = questionData.getJSONArray("startPosition")
                                val startDirObj = questionData.getJSONObject("startDirection")

                                Log.d(TAG, "   Matrix size: ${matrixSize}×${matrixSize}")
                                Log.d(TAG, "   Memory time: ${memoryTime}ms")
                                Log.d(TAG, "   Start position: [${startPosArray.getInt(0)},${startPosArray.getInt(1)}]")
                                Log.d(TAG, "   Start direction: ${startDirObj.getString("name")}")
                            } catch (e: Exception) {
                                Log.d(TAG, "   (Unable to parse details: ${e.message})")
                            }

                            Puzzle(
                                puzzleId = "pinball_deflector_${System.currentTimeMillis()}",
                                puzzleType = "pinballdeflector",
                                question = questionJson,
                                answer = answerJson,
                                hint = "Memorize deflector positions and predict ball path",
                                difficulty = selectedDifficulty,
                                options = emptyList()
                            )
                        }
                        "geography_cities", "geography_countries" -> Puzzle(
                            puzzleId = "geography_drag_drop_${System.currentTimeMillis()}",
                            puzzleType = "geography",
                            question = """
                                        {
                                            "difficulty": "$selectedDifficulty",
                                            "maxQuestions": ${when(selectedDifficulty.lowercase()) {
                                "easy" -> 8
                                "medium" -> 12
                                "hard" -> 15
                                else -> 10
                            }},
                                            "gameType": "geographyDragDrop",
                                            "cityDatabase": "world_major_cities_50"
                                        }
                                        """.trimIndent(),
                            answer = "generated",
                            hint = "Drag cities to their correct locations on the world map",
                            difficulty = selectedDifficulty,
                            options = emptyList()
                        )
                        "trainrouting" -> Puzzle(
                            puzzleId = "train_routing_test",
                            puzzleType = "trainrouting",
                            question = "Route trains to their matching colored stations",
                            answer = "completed",
                            hint = "Tap junctions to switch track directions",
                            difficulty = selectedDifficulty,
                            options = emptyList()
                        )
                        "memorypreviouspair" -> {
                            Log.d(TAG, "🧠 Generating local Memory Previous Pair puzzle")

                            val (questionJson, answerJson) = MemoryPreviousPairGenerator.generatePuzzle(selectedDifficulty)

                            Log.d(TAG, "📊 Generated Memory Previous Pair puzzle:")
                            Log.d(TAG, "   Difficulty: $selectedDifficulty")
                            Log.d(TAG, "   Question JSON length: ${questionJson.length}")
                            Log.d(TAG, "   Answer JSON length: ${answerJson.length}")

                            // Parse to log sequence details
                            try {
                                val questionData = JSONObject(questionJson)
                                val sequenceArray = questionData.getJSONArray("sequence")
                                val totalScreens = questionData.getInt("totalScreens")
                                val objectsPerScreen = questionData.getInt("objectsPerScreen")

                                Log.d(TAG, "   Total screens: $totalScreens")
                                Log.d(TAG, "   Objects per screen: $objectsPerScreen")
                                Log.d(TAG, "   Sample screen 1: ${sequenceArray.getJSONObject(0).getJSONArray("numbers")}")
                                if (sequenceArray.length() > 1) {
                                    Log.d(TAG, "   Sample screen 2: ${sequenceArray.getJSONObject(1).getJSONArray("numbers")} (linking: ${sequenceArray.getJSONObject(1).getInt("linkingNumber")})")
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "   (Unable to parse details: ${e.message})")
                            }

                            Puzzle(
                                puzzleId = "memory_previous_pair_${System.currentTimeMillis()}",
                                puzzleType = "memorypreviouspair",
                                question = questionJson,
                                answer = answerJson,
                                hint = "Remember objects from the previous screen and tap the one that appeared before",
                                difficulty = selectedDifficulty,
                                options = emptyList()
                            )
                        }
                        "memorysquares" -> {
                            Log.d(TAG, "🧠 Generating local Memory Squares puzzle")

                            val (questionJson, answerJson) = MemorySquaresGenerator.generatePuzzle(selectedDifficulty)

                            Log.d(TAG, "📊 Generated Memory Squares puzzle:")
                            Log.d(TAG, "   Difficulty: $selectedDifficulty")
                            Log.d(TAG, "   Question JSON length: ${questionJson.length}")
                            Log.d(TAG, "   Answer JSON length: ${answerJson.length}")

                            // Parse to log matrix details
                            try {
                                val questionData = JSONObject(questionJson)
                                val gridSize = questionData.getInt("size")
                                val targetCount = questionData.getInt("onesCount")
                                val timeLimit = questionData.getInt("timeLimit")

                                Log.d(TAG, "   Grid size: ${gridSize}×${gridSize}")
                                Log.d(TAG, "   Target count: $targetCount")
                                Log.d(TAG, "   Time limit: ${timeLimit}s")
                                Log.d(TAG, "   Density: ${((targetCount.toDouble() / (gridSize * gridSize)) * 100).toInt()}%")
                            } catch (e: Exception) {
                                Log.d(TAG, "   (Unable to parse details: ${e.message})")
                            }

                            Puzzle(
                                puzzleId = "memory_squares_${System.currentTimeMillis()}",
                                puzzleType = "memorysquares",
                                question = questionJson,
                                answer = answerJson,
                                hint = "Memorize the positions of highlighted squares and recreate the pattern",
                                difficulty = selectedDifficulty,
                                options = emptyList()
                            )
                        }
                        "memoryprevioussingle" -> {
                            Log.d(TAG, "🧠 Generating local Memory Previous Single puzzle")

                            val (questionJson, answerJson) = MemoryPreviousSingleGenerator.generatePuzzle(selectedDifficulty)

                            Log.d(TAG, "📊 Generated Memory Previous Single puzzle:")
                            Log.d(TAG, "   Difficulty: $selectedDifficulty")
                            Log.d(TAG, "   Question JSON length: ${questionJson.length}")
                            Log.d(TAG, "   Answer JSON length: ${answerJson.length}")

                            Puzzle(
                                puzzleId = "memory_previous_single_${System.currentTimeMillis()}",
                                puzzleType = "memoryprevioussingle",
                                question = questionJson,
                                answer = answerJson,
                                hint = "Remember only the previous object and compare it with the current one",
                                difficulty = selectedDifficulty,
                                options = emptyList()
                            )
                        }
                        "numbersequence" -> Puzzle(
                            puzzleId = "number_sequence_test",
                            puzzleType = "numbersequence",
                            question = """{"numberCount": 8}""",
                            answer = "completed",
                            hint = "Tap numbers in ascending order",
                            difficulty = selectedDifficulty,
                            options = emptyList()
                        )
                        "numbersum" -> Puzzle(
                            puzzleId = "number_sum_test",
                            puzzleType = "numbersum",
                            question = "Add up all the highlighted numbers",
                            answer = "completed",
                            hint = "Calculate the sum of all selected numbers",
                            difficulty = selectedDifficulty,
                            options = emptyList()
                        )
                        "symbolswipe" -> Puzzle(
                            puzzleId = "symbol_swipe_test",
                            puzzleType = "symbolswipe",
                            question = """{"symbolCount": 20, "timePerSymbol": 2.0}""",
                            answer = "completed",
                            hint = "Swipe symbols in the direction shown by the corner arrows",
                            difficulty = selectedDifficulty,
                            options = emptyList()
                        )
                        "colormatching" -> Puzzle(
                            puzzleId = "color_matching_test",
                            puzzleType = "colormatching",
                            question = """{"challengeCount": 10, "timePerChallenge": 3.0}""",
                            answer = "completed",
                            hint = "Does the text match the shape color?",
                            difficulty = selectedDifficulty,
                            options = emptyList()
                        )
                        "imagevortex" -> Puzzle(
                            puzzleId = "image_vortex_test",
                            puzzleType = "imagevortex",
                            question = """{"targetCount": 10, "timeLimit": 150}""",
                            answer = "completed",
                            hint = "Identify new images that haven't appeared before",
                            difficulty = selectedDifficulty,
                            options = emptyList()
                        )
                        "symmetry" -> {
                            Log.d(TAG, "🔄 Generating local Symmetry puzzle")

                            val (questionJson, answerJson) = SymmetryPuzzleGenerator.generatePuzzle(selectedDifficulty, context)

                            Log.d(TAG, "📊 Generated Symmetry puzzle:")
                            Log.d(TAG, "   Difficulty: $selectedDifficulty")
                            Log.d(TAG, "   Question JSON length: ${questionJson.length}")
                            Log.d(TAG, "   Answer JSON length: ${answerJson.length}")

                            // Parse to log puzzle details
                            try {
                                val questionData = JSONObject(questionJson)
                                val gridSize = questionData.getInt("gridSize")
                                val questionNumber = questionData.getInt("questionNumber")
                                val consecutiveCorrect = questionData.getInt("consecutiveCorrect")

                                Log.d(TAG, "   Grid size: ${gridSize}x${gridSize}")
                                Log.d(TAG, "   Question number: $questionNumber")
                                Log.d(TAG, "   Consecutive correct: $consecutiveCorrect")
                            } catch (e: Exception) {
                                Log.d(TAG, "   (Unable to parse details: ${e.message})")
                            }

                            Puzzle(
                                puzzleId = "symmetry_${System.currentTimeMillis()}",
                                puzzleType = "symmetry",
                                question = questionJson,
                                answer = answerJson,
                                hint = "Copy the exact pattern from the left grid to the right grid",
                                difficulty = selectedDifficulty,
                                options = emptyList()
                            )
                        }
                        "contextswitch" -> {
                            Log.d(TAG, "🧠 Generating local Context Switch puzzle")

                            val (questionJson, answerJson) = ContextSwitchPuzzleGenerator.generatePuzzle(selectedDifficulty, context)

                            Log.d(TAG, "📊 Generated Context Switch puzzle:")
                            Log.d(TAG, "   Difficulty: $selectedDifficulty")
                            Log.d(TAG, "   Question JSON length: ${questionJson.length}")
                            Log.d(TAG, "   Answer JSON length: ${answerJson.length}")

                            // Parse to log puzzle details
                            try {
                                val questionData = JSONObject(questionJson)
                                val memoryItemsArray = questionData.getJSONArray("memoryItems")
                                val recognitionItemsArray = questionData.getJSONArray("recognitionItems")
                                val interferenceTask = questionData.getJSONObject("interferenceTask")
                                val metadata = questionData.getJSONObject("metadata")

                                Log.d(TAG, "   Category: ${metadata.getString("categoryName")}")
                                Log.d(TAG, "   Memory items: ${memoryItemsArray.length()}")
                                Log.d(TAG, "   Recognition choices: ${recognitionItemsArray.length()}")
                                Log.d(TAG, "   Interference type: ${interferenceTask.getString("type")}")
                                Log.d(TAG, "   Interference complexity: ${interferenceTask.getString("complexity")}")
                            } catch (e: Exception) {
                                Log.d(TAG, "   (Unable to parse details: ${e.message})")
                            }

                            Puzzle(
                                puzzleId = "context_switch_${System.currentTimeMillis()}",
                                puzzleType = "contextswitch",
                                question = questionJson,
                                answer = answerJson,
                                hint = "Study the items, complete the task, then identify the original items",
                                difficulty = selectedDifficulty,
                                options = emptyList()
                            )
                        }
                        "dualcard" -> Puzzle(
                            puzzleId = "dual_card_${System.currentTimeMillis()}",
                            puzzleType = "dualcard",
                            question = "Check if number is even or letter is vowel",
                            answer = "completed",
                            hint = "Alternate between checking numbers and letters",
                            difficulty = selectedDifficulty,
                            options = emptyList()
                        )
                        "mathexpression" -> Puzzle(
                            puzzleId = "math_expression_test",
                            puzzleType = "mathexpression",
                            question = "Solve mathematical expressions by dragging missing elements",
                            answer = "completed",
                            hint = "Drag numbers and operators to complete the equations",
                            difficulty = selectedDifficulty,
                            options = emptyList()
                        )
                        else -> Puzzle(
                            puzzleId = "default_test",
                            puzzleType = type,
                            question = "Default puzzle",
                            answer = "completed",
                            hint = "Complete the puzzle",
                            difficulty = selectedDifficulty,
                            options = emptyList()
                        )
                    }

                    // 🔧 FIXED: Create intent with group context
                    val intent = createPuzzleIntent(
                        Intent(context, PuzzleActivity::class.java).apply {
                            putExtra("puzzle", Gson().toJson(localPuzzle))
                            putExtra("screenType", selectedOption)
                            putExtra("difficulty", selectedDifficulty)
                            putExtra("score", 0)
                            putExtra("startTime", System.currentTimeMillis())
                        }
                    )

                    context.startActivity(intent)
                    isLoading = false
                }

                when {
                    isDailyPuzzle && dailyTopic != null && userEmail != null -> {
                        // 🔧 ENHANCED: Daily puzzle flow with group context preservation
                        Log.d("RedesignedStartPuzzle", "🌅 Starting daily puzzle flow for topic: $dailyTopic")
                        fetchDailyPuzzleAndStart(
                            context = context,
                            userEmail = userEmail,
                            topic = dailyTopic,
                            selectedScreenType = selectedOption,
                            sourceGroupId = sourceGroupId,
                            sourceGroupName = sourceGroupName,
                            puzzleType = type,
                            onSuccess = {
                                isLoading = false
                                Log.d("RedesignedStartPuzzle", "✅ Daily puzzle loaded successfully")
                            },
                            onError = { errorMsg ->
                                isLoading = false
                                errorMessage = errorMsg
                                Log.e("RedesignedStartPuzzle", "❌ Daily puzzle load failed: $errorMsg")

                                val userMessage = when {
                                    errorMsg.contains("No puzzles found") -> "No daily puzzles available for $dailyTopic today. Try again later!"
                                    errorMsg.contains("Network error") -> "Connection issue. Please check your internet and try again."
                                    errorMsg.contains("parsing") -> "Something went wrong loading your puzzle. Please try again."
                                    else -> "Failed to load daily puzzle. Please try again."
                                }

                                Toast.makeText(context, userMessage, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                    isCustomPuzzle && customPuzzleId != null -> {
                        // 🔧 ENHANCED: Custom puzzle flow with group context preservation
                        Log.d("RedesignedStartPuzzle", "🎨 Starting custom puzzle flow for ID: $customPuzzleId")
                        fetchCustomPuzzleAndStart(
                            context = context,
                            customPuzzleId = customPuzzleId,
                            selectedScreenType = selectedOption,
                            sourceGroupId = sourceGroupId,
                            sourceGroupName = sourceGroupName,
                            puzzleType = type,
                            onSuccess = { isLoading = false },
                            onError = {
                                isLoading = false
                                Toast.makeText(context, "Failed to load custom puzzle", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    else -> {
                        // Handle AI puzzle flow (existing logic with group context)
                        Log.d("RedesignedStartPuzzle", "🤖 Starting AI puzzle flow for type: $type")
                        Log.d("RedesignedStartPuzzle", "🎯 Group context available:")
                        Log.d("RedesignedStartPuzzle", "  sourceGroupId: $sourceGroupId")
                        Log.d("RedesignedStartPuzzle", "  sourceGroupName: $sourceGroupName")
                        val targetPuzzleCount = selectedMinutes
                        fetchPuzzleAndStartGame(
                            context = context,
                            type = type,
                            difficulty = selectedDifficulty.lowercase(),
                            screenType = selectedOption,
                            targetPuzzleCount = targetPuzzleCount,
                            sourceGroupId = sourceGroupId,
                            sourceGroupName = sourceGroupName,
                            onSuccess = { isLoading = false },
                            onError = {
                                isLoading = false
                                Toast.makeText(context, "Failed to load puzzle", Toast.LENGTH_SHORT).show()
                            },
                            onLimitReached = { limitInfo ->
                                isLoading = false
                                currentLimitInfo = limitInfo
                                showLimitCard = true
                            }
                        )
                    }
                }
            }
        }

        // 🚀 FLOATING BUTTONS - Always visible at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF1A1A1A).copy(alpha = 0.8f),
                            Color(0xFF1A1A1A)
                        ),
                        startY = 0f,
                        endY = 300f
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Main Play Button - Prominent
                Button(
                    onClick = {
                        if (!isLoading) {
                            errorMessage = null
                            isLoading = true

                            // Helper function to create intent with group context
                            fun createPuzzleIntent(baseIntent: Intent): Intent {
                                return baseIntent.apply {
                                    // Add group context if available
                                    if (sourceGroupId != null && sourceGroupName != null) {
                                        putExtra("sourceGroupId", sourceGroupId)
                                        putExtra("sourceGroupName", sourceGroupName)
                                        putExtra("puzzleType", type)

                                        Log.d("RedesignedStartPuzzle", "Adding group context to intent:")
                                        Log.d("RedesignedStartPuzzle", "  sourceGroupId: $sourceGroupId")
                                        Log.d("RedesignedStartPuzzle", "  sourceGroupName: $sourceGroupName")
                                        Log.d("RedesignedStartPuzzle", "  puzzleType: $type")
                                    }
                                }
                            }

                            when {
                                // Handle local puzzle types FIRST - before any network logic
                                isOfflinePuzzleType(type) -> {
                                    Log.d("RedesignedStartPuzzle", "Creating local puzzle for type: $type")

                                    // Create local puzzle immediately
                                    val localPuzzle = when (type) {
                                        "memoryprevioussingle" -> {
                                            Log.d("StartPuzzle", "Generating local Memory Previous Single puzzle")
                                            val (questionJson, answerJson) = MemoryPreviousSingleGenerator.generatePuzzle(selectedDifficulty)

                                            Puzzle(
                                                puzzleId = "memory_previous_single_${System.currentTimeMillis()}",
                                                puzzleType = "memoryprevioussingle",
                                                question = questionJson,
                                                answer = answerJson,
                                                hint = "Remember only the previous object and compare it with the current one",
                                                difficulty = selectedDifficulty,
                                                options = emptyList()
                                            )
                                        }
                                        "memorypreviouspair" -> {
                                            Log.d("StartPuzzle", "Generating local Memory Previous Pair puzzle")
                                            val (questionJson, answerJson) = MemoryPreviousPairGenerator.generatePuzzle(selectedDifficulty)

                                            Puzzle(
                                                puzzleId = "memory_previous_pair_${System.currentTimeMillis()}",
                                                puzzleType = "memorypreviouspair",
                                                question = questionJson,
                                                answer = answerJson,
                                                hint = "Remember objects from the previous screen and tap the one that appeared before",
                                                difficulty = selectedDifficulty,
                                                options = emptyList()
                                            )
                                        }
                                        "memorysquares" -> {
                                            Log.d("StartPuzzle", "Generating local Memory Squares puzzle")
                                            val (questionJson, answerJson) = MemorySquaresGenerator.generatePuzzle(selectedDifficulty)

                                            Puzzle(
                                                puzzleId = "memory_squares_${System.currentTimeMillis()}",
                                                puzzleType = "memorysquares",
                                                question = questionJson,
                                                answer = answerJson,
                                                hint = "Memorize the positions of highlighted squares and recreate the pattern",
                                                difficulty = selectedDifficulty,
                                                options = emptyList()
                                            )
                                        }
                                        "pinballdeflector" -> {
                                            Log.d("StartPuzzle", "Generating local Pinball Deflector puzzle")
                                            val (questionJson, answerJson) = PinballDeflectorGenerator.generatePuzzle(selectedDifficulty)

                                            Puzzle(
                                                puzzleId = "pinball_deflector_${System.currentTimeMillis()}",
                                                puzzleType = "pinballdeflector",
                                                question = questionJson,
                                                answer = answerJson,
                                                hint = "Memorize deflector positions and predict ball path",
                                                difficulty = selectedDifficulty,
                                                options = emptyList()
                                            )
                                        }
                                        "contextswitch" -> {
                                            Log.d("StartPuzzle", "Generating local Context Switch puzzle")
                                            val (questionJson, answerJson) = ContextSwitchPuzzleGenerator.generatePuzzle(selectedDifficulty, context)

                                            Puzzle(
                                                puzzleId = "context_switch_${System.currentTimeMillis()}",
                                                puzzleType = "contextswitch",
                                                question = questionJson,
                                                answer = answerJson,
                                                hint = "Study the items, complete the task, then identify the original items",
                                                difficulty = selectedDifficulty,
                                                options = emptyList()
                                            )
                                        }
                                        "symmetry" -> {
                                            Log.d("StartPuzzle", "Generating local Symmetry puzzle")
                                            val (questionJson, answerJson) = SymmetryPuzzleGenerator.generatePuzzle(selectedDifficulty, context)

                                            Puzzle(
                                                puzzleId = "symmetry_${System.currentTimeMillis()}",
                                                puzzleType = "symmetry",
                                                question = questionJson,
                                                answer = answerJson,
                                                hint = "Copy the exact pattern from the left grid to the right grid",
                                                difficulty = selectedDifficulty,
                                                options = emptyList()
                                            )
                                        }
                                        // Add other local puzzle types here...
                                        else -> Puzzle(
                                            puzzleId = "local_${type}_${System.currentTimeMillis()}",
                                            puzzleType = type,
                                            question = "Local puzzle for $type",
                                            answer = "completed",
                                            hint = "Complete the puzzle",
                                            difficulty = selectedDifficulty,
                                            options = emptyList()
                                        )
                                    }

                                    // Create intent and start activity immediately
                                    val intent = createPuzzleIntent(
                                        Intent(context, PuzzleActivity::class.java).apply {
                                            putExtra("puzzle", Gson().toJson(localPuzzle))
                                            putExtra("screenType", selectedOption)
                                            putExtra("difficulty", selectedDifficulty)
                                            putExtra("score", 0)
                                            putExtra("startTime", System.currentTimeMillis())
                                        }
                                    )

                                    context.startActivity(intent)
                                    isLoading = false
                                }

                                // Use prefetched AI puzzle if available
                                !isDailyPuzzle && !isCustomPuzzle && prefetchedPuzzle != null -> {
                                    startPuzzleActivity(
                                        context = context,
                                        puzzle = prefetchedPuzzle!!,
                                        screenType = selectedOption,
                                        difficulty = selectedDifficulty,
                                        targetPuzzleCount = selectedMinutes,
                                        sourceGroupId = sourceGroupId,
                                        sourceGroupName = sourceGroupName,
                                        puzzleType = type,
                                        source = "prefetch"
                                    )
                                    isLoading = false
                                }

                                // Handle daily puzzles
                                isDailyPuzzle && dailyTopic != null && userEmail != null -> {
                                    Log.d("RedesignedStartPuzzle", "Starting daily puzzle flow for topic: $dailyTopic")
                                    fetchDailyPuzzleAndStart(
                                        context = context,
                                        userEmail = userEmail,
                                        topic = dailyTopic,
                                        selectedScreenType = selectedOption,
                                        sourceGroupId = sourceGroupId,
                                        sourceGroupName = sourceGroupName,
                                        puzzleType = type,
                                        onSuccess = { isLoading = false },
                                        onError = { errorMsg ->
                                            isLoading = false
                                            errorMessage = errorMsg
                                        }
                                    )
                                }

                                // Handle custom puzzles
                                isCustomPuzzle && customPuzzleId != null -> {
                                    Log.d("RedesignedStartPuzzle", "Starting custom puzzle flow for ID: $customPuzzleId")
                                    fetchCustomPuzzleAndStart(
                                        context = context,
                                        customPuzzleId = customPuzzleId,
                                        selectedScreenType = selectedOption,
                                        sourceGroupId = sourceGroupId,
                                        sourceGroupName = sourceGroupName,
                                        puzzleType = type,
                                        onSuccess = { isLoading = false },
                                        onError = {
                                            isLoading = false
                                            Toast.makeText(context, "Failed to load custom puzzle", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                // Handle AI puzzles that need network fetching
                                else -> {
                                    Log.d("RedesignedStartPuzzle", "Starting AI puzzle flow for type: $type")
                                    val targetPuzzleCount = selectedMinutes
                                    fetchPuzzleAndStartGameNonBlocking(
                                        context = context,
                                        type = type,
                                        difficulty = selectedDifficulty.lowercase(),
                                        screenType = selectedOption,
                                        targetPuzzleCount = selectedMinutes,
                                        sourceGroupId = sourceGroupId,
                                        sourceGroupName = sourceGroupName,
                                        onSuccess = { isLoading = false },
                                        onError = {
                                            isLoading = false
                                            Toast.makeText(context, "Failed to load puzzle", Toast.LENGTH_SHORT).show()
                                        },
                                        onLimitReached = { limitInfo ->
                                            isLoading = false
                                            currentLimitInfo = limitInfo
                                            showLimitCard = true
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A8E8)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    enabled = !isLoading,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White
                            )
                            Text(
                                text = when {
                                    !isDailyPuzzle && !isCustomPuzzle && prefetchInProgress -> "Preparing..."
                                    !isDailyPuzzle && !isCustomPuzzle && prefetchedPuzzle != null -> "Starting..."
                                    else -> "Loading..."
                                }
                            )
                        }
                    } else {
                        Text(
                            text = "🎮 Start Playing",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (!isDailyPuzzle && !isCustomPuzzle && prefetchedPuzzle != null) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Ready",
                                tint = Color.Green,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Create Your Version (Remix) button
                Button(
                    onClick = { showRemixSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF9C27B0), Color(0xFF2196F3))
                                ),
                                RoundedCornerShape(28.dp)
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.create_your_version),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "Earn coins",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Secondary Back Button - Subtle
                OutlinedButton(
                    onClick = onBackHome,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    border = BorderStroke(1.dp, Color(0xFF00A8E8).copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF00A8E8).copy(alpha = 0.8f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.back_to_home),
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Game Remix Sheet overlay
        if (showRemixSheet) {
            GameRemixSheet(
                puzzleType = type,
                difficulty = selectedDifficulty,
                onDismiss = { showRemixSheet = false },
                genViewModel = gameGenVM
            )
        }
    }
}

fun fetchAIPuzzleProactiveNonBlocking(
    context: Context,
    type: String,
    difficulty: String,
    sourceGroupId: String? = null,
    sourceGroupName: String? = null,
    onSuccess: (Puzzle) -> Unit,
    onError: (String) -> Unit,
    onLimitReached: (RegenerationLimitManager.LimitInfo) -> Unit,
    onRegenerationInProgress: ((RegenerationInfo) -> Unit)? = null
) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val puzzleQueueManager = PuzzleQueueManager.getInstance(context)
            val queuedPuzzle = puzzleQueueManager.getNextPuzzleForPlay(type, difficulty)

            if (queuedPuzzle != null) {
                onSuccess(queuedPuzzle.toPuzzle())
            } else {
                onError("No puzzles available in queue")
            }
        } catch (e: Exception) {
            Log.e("FetchAIProactiveNB", "Queue check failed", e)
            onError("Queue check failed: ${e.message}")
        }
    }
}

fun fetchPuzzleAndStartGameNonBlocking(
    context: Context,
    type: String,
    difficulty: String,
    screenType: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onLimitReached: (RegenerationLimitManager.LimitInfo) -> Unit,
    targetPuzzleCount: Int,
    sourceGroupId: String? = null,
    sourceGroupName: String? = null,
    onRegenerationInProgress: ((RegenerationInfo) -> Unit)? = null
) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val puzzleQueueManager = PuzzleQueueManager.getInstance(context)
            val queuedPuzzle = puzzleQueueManager.getNextPuzzleForPlay(type, difficulty)

            if (queuedPuzzle != null) {
                val puzzle = queuedPuzzle.toPuzzle()
                startPuzzleActivity(context, puzzle, screenType, difficulty, targetPuzzleCount,
                    sourceGroupId, sourceGroupName, type, "queue")
                onSuccess()
            } else {
                onError("No puzzles available")
            }
        } catch (e: Exception) {
            Log.e("FetchPuzzleNB", "Queue check failed", e)
            onError("Queue check failed: ${e.message}")
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF333333)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun BenefitsSection(puzzleType: String) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "BENEFITS:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val benefits = getPuzzleBenefits(puzzleType)

        benefits.forEachIndexed { index, benefit ->
            BenefitItem(
                icon = Icons.Default.CheckCircle,
                text = benefit
            )
            if (index < benefits.size - 1) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun BenefitItem(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            fontSize = 14.sp,
            color = Color.Gray,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TopScoresSection(topScores: List<Int>) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "TOP 10 SCORES:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF333333)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                topScores.take(10).chunked(2).forEachIndexed { rowIndex, scores ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        scores.forEachIndexed { colIndex, score ->
                            val position = rowIndex * 2 + colIndex + 1
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$position.",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = score.toString(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }

                        if (scores.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    if (rowIndex < 4) {
                        HorizontalDivider(
                            color = Color(0xFF444444),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GameConfigurationSection(
    isCustomPuzzle: Boolean,
    selectedDifficulty: String,
    onDifficultyChanged: (String) -> Unit,
    selectedOption: String,
    onOptionChanged: (String) -> Unit,
    screenOptions: List<String>,
    difficultyInsight: String,
    showDifficultyHint: Boolean,
    onToggleDifficultyHint: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF333333)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.game_settings),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color.Gray
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                if (!isCustomPuzzle) {
                    // Difficulty Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.difficulty_level), fontSize = 14.sp, color = Color.Gray)
                        IconButton(
                            onClick = onToggleDifficultyHint,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Difficulty Info",
                                tint = Color(0xFF00A8E8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (showDifficultyHint) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1976D2).copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = "💡 $difficultyInsight",
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp,
                                color = Color(0xFF64B5F6)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Easy", "Medium", "Hard").forEach { level ->
                            FilterChip(
                                selected = selectedDifficulty == level,
                                onClick = { onDifficultyChanged(level) },
                                label = { Text(level) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00A8E8),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF555555),
                                    labelColor = Color.Gray
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (screenOptions.size > 1) {
                    Text(stringResource(R.string.game_mode), fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(screenOptions) { option ->
                            FilterChip(
                                selected = selectedOption == option,
                                onClick = { onOptionChanged(option) },
                                label = {
                                    Text(
                                        when(option) {
                                            "Default" -> "Q&A"
                                            "multipleChoice" -> "Multiple Choice"
                                            "Match Screen" -> "Word Match"
                                            "Swipe Word Screen" -> "Swipe Cards"
                                            "Jumble Input Screen" -> "Word Jumble"
                                            "Falling Game Screen" -> "Space Game"
                                            else -> option
                                        }
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00A8E8),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF555555),
                                    labelColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

data class RegenerationInfo(
    val puzzleType: String,
    val difficulty: String,
    val estimatedWaitTime: Int, // minutes
    val message: String,
    val remainingPuzzles: Int,
    val isInitialSetup: Boolean,
    val notificationEnabled: Boolean = true
)

private fun fetchFromAPIWithMediaCaching(
    context: Context,
    type: String,
    difficulty: String,
    onSuccess: (Puzzle) -> Unit,
    onError: (String) -> Unit,
    onLimitReached: (RegenerationLimitManager.LimitInfo) -> Unit,
    onRegenerationInProgress: ((RegenerationInfo) -> Unit)?,
    limitManager: RegenerationLimitManager,
    mediaCache: MediaCacheManager
) {
    val url = "https://puzzleverseai.com/fetch-next-puzzle-ios/$type?userId=${FirebaseAuth.getInstance().currentUser?.uid}&email=${FirebaseAuth.getInstance().currentUser?.email}&difficulty=$difficulty"

    val request = Request.Builder().url(url).build()
    val client = OkHttpClient()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Handler(Looper.getMainLooper()).post {
                onError("Network error: ${e.message}")
            }
        }

        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string()

            try {
                val json = JSONObject(body ?: "")
                val success = json.optBoolean("success", false)

                // NEW: Check for regeneration in progress BEFORE checking success
                val regenerationInProgress = json.optBoolean("regenerationInProgress", false)
                val regenerationNeeded = json.optBoolean("regenerationNeeded", false)

                if (!success && (regenerationInProgress || regenerationNeeded) && onRegenerationInProgress != null) {
                    // Parse regeneration info
                    val regenerationInfo = RegenerationInfo(
                        puzzleType = type,
                        difficulty = difficulty,
                        estimatedWaitTime = json.optInt("estimatedWaitMinutes", 2),
                        message = json.optString("message", "New puzzles are being generated"),
                        remainingPuzzles = json.optInt("remainingPuzzles", 0),
                        isInitialSetup = json.optBoolean("initialSetup", false),
                        notificationEnabled = json.optBoolean("notificationEnabled", true)
                    )

                    Handler(Looper.getMainLooper()).post {
                        onRegenerationInProgress(regenerationInfo)
                    }
                    return
                }

                // Check for regeneration blocked status (existing logic)
                val regenerationBlocked = json.optBoolean("regenerationBlocked", false)
                if (regenerationBlocked && json.has("limitInfo")) {
                    val limitInfoJson = json.getJSONObject("limitInfo")
                    val limitInfo = RegenerationLimitManager.LimitInfo(
                        reason = limitInfoJson.optString("reason", "limit_exceeded"),
                        dailyUsed = limitInfoJson.optJSONObject("limits")?.optInt("dailyUsed", 0) ?: 0,
                        dailyLimit = limitInfoJson.optJSONObject("limits")?.optInt("dailyLimit", 10) ?: 10,
                        monthlyUsed = limitInfoJson.optJSONObject("limits")?.optInt("monthlyUsed", 0) ?: 0,
                        monthlyLimit = limitInfoJson.optJSONObject("limits")?.optInt("monthlyLimit", 100) ?: 100,
                        resetTime = limitInfoJson.optString("resetTime", ""),
                        upgradeUrl = limitInfoJson.optString("upgradeUrl", "/upgrade"),
                        message = limitInfoJson.optString("message", "Generation limit reached")
                    )

                    limitManager.saveLimitInfo(type, limitInfo)

                    Handler(Looper.getMainLooper()).post {
                        onLimitReached(limitInfo)
                    }
                    return
                }

                if (!success) {
                    Handler(Looper.getMainLooper()).post {
                        onError("No puzzles available")
                    }
                    return
                }

                // Parse successful puzzle response
                val puzzleData = json.getJSONObject("puzzleData")
                val optionsJsonArray = puzzleData.optJSONArray("options")
                val options = mutableListOf<String>()
                if (optionsJsonArray != null) {
                    for (i in 0 until optionsJsonArray.length()) {
                        options.add(optionsJsonArray.optString(i))
                    }
                }

                val puzzle = Puzzle(
                    puzzleId = puzzleData.getString("puzzleId"),
                    puzzleType = puzzleData.getString("puzzleType"),
                    question = puzzleData.getString("question"),
                    answer = puzzleData.getString("answer"),
                    hint = puzzleData.optString("hint", ""),
                    difficulty = puzzleData.optString("difficulty", difficulty),
                    options = options
                )

                Handler(Looper.getMainLooper()).post {
                    // Extract and cache media URLs
                    val mediaUrls = mediaCache.extractMediaUrlsFromPuzzle(puzzle)
                    if (mediaUrls.isNotEmpty()) {
                        Log.d("PuzzleMediaCache", "Caching ${mediaUrls.size} media files for API puzzle ${puzzle.puzzleId}")

                        // Cache media in background
                        CoroutineScope(Dispatchers.IO).launch {
                            mediaUrls.forEach { url ->
                                try {
                                    mediaCache.getCachedMediaUrl(url)
                                    Log.d("PuzzleMediaCache", "Cached API media: $url")
                                } catch (e: Exception) {
                                    Log.w("PuzzleMediaCache", "Failed to cache API media: $url", e)
                                }
                            }
                        }
                    }

                    onSuccess(puzzle)
                }

                Handler(Looper.getMainLooper()).post {
                    onSuccess(puzzle)
                }

            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onError("Error parsing response: ${e.message}")
                }
            }
        }
    })


}

@Composable
fun RegenerationInProgressScreen(
    regenerationInfo: RegenerationInfo,
    limitInfo: RegenerationLimitManager.LimitInfo? = null,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    var timeRemaining by remember { mutableStateOf(regenerationInfo.estimatedWaitTime * 60) }
    var isRetrying by remember { mutableStateOf(false) }

    val subscriptionManager = SubscriptionManager.getInstance(LocalContext.current)
    val activity = LocalActivity.current

    // Countdown timer
    LaunchedEffect(Unit) {
        while (timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A1A),
                        Color(0xFF2D2D2D)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Header with regeneration status
            RegenerationStatusCard(regenerationInfo, timeRemaining)

            Spacer(modifier = Modifier.height(24.dp))

            // Current usage display (always show for context)
            limitInfo?.let { info ->
                CurrentUsageCard(limitInfo = info)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Subscription options (prominent for free users)
            if (subscriptionManager.currentTier == SubscriptionTier.FREE) {
                UpgradeOptionsSection(
                    timeRemaining = timeRemaining,
                    limitInfo = limitInfo,
                    subscriptionManager = subscriptionManager,
                    activity = activity as? AppCompatActivity
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Alternative actions
                AlternativeActionsSection(onBack)
            } else {
                // Premium user - show different content
                PremiumUserSection()
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            ActionButtonsSection(
                timeRemaining = timeRemaining,
                isRetrying = isRetrying,
                onRetry = {
                    isRetrying = true
                    onRetry()
                },
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun RegenerationStatusCard(
    regenerationInfo: RegenerationInfo,
    timeRemaining: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF333333)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated progress indicator
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF00A8E8),
                    strokeWidth = 6.dp
                )
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = "Generating",
                    tint = Color(0xFF00A8E8),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (regenerationInfo.isInitialSetup)
                    "Setting Up New Puzzles"
                else
                    "Generating Fresh Puzzles",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = regenerationInfo.message,
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoChip(
                    label = "Type",
                    value = getPuzzleDisplayName(regenerationInfo.puzzleType)
                )
                InfoChip(
                    label = "Difficulty",
                    value = regenerationInfo.difficulty.capitalize()
                )
            }

            if (timeRemaining > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Estimated time: ${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = Color(0xFF00A8E8),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CurrentUsageCard(limitInfo: RegenerationLimitManager.LimitInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Current Usage",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Usage Stats",
                    tint = Color(0xFF00A8E8),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Daily quota
                UsageIndicator(
                    label = "Daily",
                    used = limitInfo.dailyUsed,
                    total = limitInfo.dailyLimit,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Monthly quota
                UsageIndicator(
                    label = "Monthly",
                    used = limitInfo.monthlyUsed,
                    total = limitInfo.monthlyLimit,
                    color = Color(0xFF9C27B0),
                    modifier = Modifier.weight(1f)
                )
            }

            if (limitInfo.resetTime.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Limits reset: ${formatResetTime(limitInfo.resetTime)}",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun UsageIndicator(
    label: String,
    used: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = (used.toFloat() / total.toFloat()).coerceAtMost(1f),
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = 5.dp,
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )

            Text(
                text = "$used",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )

        Text(
            text = "of $total",
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun UpgradeOptionsSection(
    timeRemaining: Int,
    limitInfo: RegenerationLimitManager.LimitInfo?,
    subscriptionManager: SubscriptionManager,
    activity: AppCompatActivity?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1565C0).copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, Color(0xFF1565C0).copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Color(0xFFFFD700).copy(alpha = 0.2f),
                            RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Premium",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Skip the Wait!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Get instant puzzle generation",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Wait time vs upgrade comparison
            WaitTimeComparisonCard(timeRemaining)

            Spacer(modifier = Modifier.height(20.dp))

            // Subscription tiers
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SubscriptionTierCard(
                    tier = SubscriptionTier.PREMIUM,
                    isRecommended = false,  // Monthly option
                    subscriptionManager = subscriptionManager,
                    activity = activity
                )

                SubscriptionTierCard(
                    tier = SubscriptionTier.PREMIUM_YEARLY,
                    isRecommended = true,  // Yearly is better value, always recommended
                    subscriptionManager = subscriptionManager,
                    activity = activity
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Benefits summary
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "No ads • Unlimited generations • Priority processing • Cancel anytime",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun WaitTimeComparisonCard(timeRemaining: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current wait
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Wait Time",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
                Text(
                    text = "minutes",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            // VS indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "vs",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "vs",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Premium instant
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Premium",
                    fontSize = 12.sp,
                    color = Color(0xFFFFD700)
                )
                Text(
                    text = "Instant",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700)
                )
                Text(
                    text = "generation",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun SubscriptionTierCard(
    tier: SubscriptionTier,
    isRecommended: Boolean,
    subscriptionManager: SubscriptionManager,
    activity: AppCompatActivity?
) {
    val isPurchasing = subscriptionManager.purchaseState is PurchaseState.Purchasing
    val pkg = subscriptionManager.getPackageForTier(tier)
    val displayPrice = pkg?.product?.price?.formatted ?: tier.price

    // Check for intro/trial offer
    val introPrice = pkg?.product?.subscriptionOptions
        ?.defaultOffer
        ?.freePhase
    val hasFreeTrial = introPrice != null
    val trialDuration = introPrice?.billingPeriod?.let { period ->
        when {
            period.unit.name == "DAY" -> "${period.value}-day free trial"
            period.unit.name == "WEEK" -> "${period.value}-week free trial"
            period.unit.name == "MONTH" -> "${period.value}-month free trial"
            else -> "Free trial"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isPurchasing) {
                activity?.let { act ->
                    pkg?.let { p ->
                        subscriptionManager.purchasePackage(act, p)
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isRecommended)
                Color(0xFFFFD700).copy(alpha = 0.1f)
            else Color(0xFF333333)
        ),
        border = BorderStroke(
            width = if (isRecommended) 2.dp else 1.dp,
            color = if (isRecommended) Color(0xFFFFD700) else Color.Gray.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tier.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (hasFreeTrial || isRecommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasFreeTrial) Color(0xFF4CAF50) else Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (hasFreeTrial) "FREE TRIAL" else "RECOMMENDED",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (hasFreeTrial && trialDuration != null) {
                    Text(
                        text = trialDuration,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "then $displayPrice",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        text = displayPrice,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecommended) Color(0xFFFFD700) else Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Text(
                    text = if (tier.dailyLimit == -1) "Unlimited daily" else "${tier.dailyLimit} daily",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (isPurchasing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFFFD700)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Select",
                    tint = if (isRecommended) Color(0xFFFFD700) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumUserSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Premium",
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Premium Member",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Thanks for your support! New puzzles will be ready shortly.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun AlternativeActionsSection(onBack: () -> Unit) {
    Column {
        Text(
            text = "Or try these alternatives:",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AlternativeActionCard(
                icon = Icons.Default.FavoriteBorder,
                title = "Browse Existing Puzzles",
                subtitle = "Play puzzles you've created before",
                onClick = { onBack() }
            )

            AlternativeActionCard(
                icon = Icons.Default.Lightbulb,
                title = "Try Different Categories",
                subtitle = "Explore other puzzle types",
                onClick = { onBack() }
            )
        }
    }
}

@Composable
private fun AlternativeActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Go",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ActionButtonsSection(
    timeRemaining: Int,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00A8E8)
            ),
            shape = RoundedCornerShape(24.dp),
            enabled = !isRetrying
        ) {
            if (isRetrying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Checking...")
            } else {
                Text(
                    text = if (timeRemaining <= 0) "Try Now" else "Check Progress",
                    fontWeight = FontWeight.Medium
                )
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            border = BorderStroke(1.dp, Color(0xFF00A8E8).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = "Back to Home",
                color = Color(0xFF00A8E8).copy(alpha = 0.8f)
            )
        }
    }
}

// Helper function for reset time formatting
private fun formatResetTime(resetTimeIso: String): String {
    return try {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val resetTime = format.parse(resetTimeIso)

        if (resetTime != null) {
            val now = java.util.Date()
            val durationMs = resetTime.time - now.time

            when {
                durationMs <= 0 -> "now"
                java.util.concurrent.TimeUnit.MILLISECONDS.toDays(durationMs) > 0 -> {
                    val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(durationMs)
                    "in ${days} day${if (days == 1L) "" else "s"}"
                }
                java.util.concurrent.TimeUnit.MILLISECONDS.toHours(durationMs) > 0 -> {
                    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(durationMs)
                    "in ${hours} hour${if (hours == 1L) "" else "s"}"
                }
                java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durationMs) > 0 -> {
                    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durationMs)
                    "in ${minutes} minute${if (minutes == 1L) "" else "s"}"
                }
                else -> "soon"
            }
        } else {
            "later"
        }
    } catch (e: Exception) {
        "later"
    }
}

@Composable
private fun SubscriptionPromptCard(
    limitInfo: RegenerationLimitManager.LimitInfo?,
    timeRemaining: Int,
    onUpgradeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1565C0).copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, Color(0xFF1565C0).copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Color(0xFFFFD700).copy(alpha = 0.2f),
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Premium",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Skip the Wait!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Get instant puzzle generation",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wait time vs upgrade comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Current wait
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Wait Time",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                    Text(
                        text = "minutes",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                // VS indicator
                Text(
                    text = "vs",
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )

                // Premium instant
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Premium",
                        fontSize = 10.sp,
                        color = Color(0xFFFFD700)
                    )
                    Text(
                        text = "Instant",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                    Text(
                        text = "generation",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Benefits preview
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "No ads • Free coins monthly • Priority processing",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Upgrade button
            Button(
                onClick = onUpgradeClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Upgrade Now",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// Alternative minimal version if you prefer less prominent
@Composable
private fun MinimalSubscriptionPrompt(
    onUpgradeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Tired of waiting?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Get instant generation with Premium",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Button(
                onClick = onUpgradeClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Upgrade", fontSize = 12.sp)
            }
        }
    }
}

// Helper functions
fun getPuzzleCategory(puzzleType: String): String {
    return when (puzzleType.lowercase()) {
        "wordassociation", "synonyms", "anagram", "connotationwords" -> "Speaking"
        "math", "mathestimation", "division", "subtraction" -> "Mathematics"
        "memorysquares", "memorystory", "memorysequencing" -> "Memory"
        "crossword", "wordsearch" -> "Word Games"
        else -> "General"
    }
}

fun getPuzzleBenefits(puzzleType: String): List<String> {
    return when (puzzleType.lowercase()) {
        "wordassociation" -> listOf(
            "Stop mixing up commonly confused words.",
            "Eliminate distracting errors in your speaking."
        )
        "math", "mathestimation" -> listOf(
            "Improve numerical reasoning skills.",
            "Enhance problem-solving speed and accuracy."
        )
        "memorysquares", "memorystory" -> listOf(
            "Strengthen working memory capacity.",
            "Improve focus and attention span."
        )
        "synonyms" -> listOf(
            "Expand your vocabulary range.",
            "Improve word choice in communication."
        )
        "imagepuzzle" -> listOf(
            "Improve spatial reasoning and visual processing.",
            "Enhance pattern recognition and problem-solving skills."
        )
        else -> listOf(
            "Enhance cognitive flexibility.",
            "Improve pattern recognition skills."
        )
    }
}

// Data class for puzzle statistics
data class PuzzleStatistics(
    val highScore: Int,
    val difficulty: String,
    val timesTrained: Float, // in hours
    val wins: Int,
    val topScores: List<Int>,
    val totalPlays: Int,
    val averageScore: Int,
    val winRate: Float, // 0.0 to 1.0
    val longestStreak: Int,
    val totalTimeSpent: Float // in hours
)

/**
 * Enhanced fetchCustomPuzzleAndStart that preserves group context
 */
fun fetchCustomPuzzleAndStart(
    context: android.content.Context,
    customPuzzleId: String,
    selectedScreenType: String,
    sourceGroupId: String? = null,
    sourceGroupName: String? = null,
    puzzleType: String? = null,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val TAG = "FetchCustomPuzzleEnhanced"
    val url = "https://puzzleverseai.com/fetch-custom-puzzle?puzzleId=$customPuzzleId"
    val request = Request.Builder().url(url).build()
    val client = OkHttpClient()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e(TAG, "❌ Failed to load custom puzzle", e)
            Handler(Looper.getMainLooper()).post {
                try {
                    AnalyticsManager.getInstance()?.track(AnalyticsEvent("custom_puzzle_start_failed", mapOf(
                        "puzzle_id" to customPuzzleId,
                        "error_type" to "network_error",
                        "error_message" to (e.message ?: "unknown"),
                        "screen_type" to selectedScreenType,
                        "has_group_context" to (sourceGroupId != null)
                    )))
                } catch (analyticsError: Exception) {
                    Log.e(TAG, "Analytics tracking failed", analyticsError)
                }
                onError("Network error: ${e.message}")
            }
        }

        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string()

            try {
                val json = JSONObject(body ?: "")

                // Check for status responses first (failed/regenerating)
                if (json.has("status")) {
                    val status = json.getString("status")
                    val message = json.optString("message", "")
                    val puzzleSet = json.optJSONObject("puzzleSet")

                    Log.d(TAG, "🟡 Received status response: $status")

                    when (status) {
                        "failed", "regenerating" -> {
                            Handler(Looper.getMainLooper()).post {
                                showPuzzleRejectionDialog(
                                    context = context,
                                    puzzleName = puzzleSet?.optString("name") ?: "Unknown Puzzle",
                                    creator = puzzleSet?.optString("creator") ?: "Unknown Creator",
                                    format = puzzleSet?.optString("format") ?: "Unknown Format",
                                    status = status,
                                    onDismiss = {
                                        onError("Puzzle topic rejected by content review")
                                    }
                                )

                                try {
                                    AnalyticsManager.getInstance()?.track(AnalyticsEvent("custom_puzzle_rejected", mapOf(
                                        "puzzle_id" to customPuzzleId,
                                        "status" to status,
                                        "puzzle_name" to (puzzleSet?.optString("name") ?: "unknown"),
                                        "screen_type" to selectedScreenType,
                                        "has_group_context" to (sourceGroupId != null)
                                    )))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Analytics tracking failed", e)
                                }
                            }
                            return
                        }
                    }
                }

                // Check for successful puzzle response
                if (!json.has("puzzleData")) {
                    throw Exception("Invalid response format: missing puzzleData")
                }

                val outer = json.getJSONObject("puzzleData")
                val puzzleSet = outer.getJSONObject("puzzleData")
                val puzzlesArray = puzzleSet.getJSONArray("puzzles")

                val puzzles = mutableListOf<Puzzle>()
                for (i in 0 until puzzlesArray.length()) {
                    val obj = puzzlesArray.getJSONObject(i)
                    val puzzle = Puzzle(
                        puzzleId = obj.optString("puzzleId", ""),
                        question = obj.optString("question", ""),
                        answer = obj.optString("answer", ""),
                        hint = obj.optString("hint", ""),
                        difficulty = null,
                        puzzleType = obj.optString("format", null),
                        options = if (obj.has("options")) {
                            val optArray = obj.getJSONArray("options")
                            List(optArray.length()) { j -> optArray.getString(j) }
                        } else emptyList()
                    )
                    puzzles.add(puzzle)
                }

                Handler(Looper.getMainLooper()).post {
                    try {
                        val firstPuzzle = puzzles.firstOrNull()
                        if (firstPuzzle != null) {
                            AnalyticsSessionManager.getInstance().logPuzzleStartIfNeeded(
                                puzzleId = firstPuzzle.puzzleId,
                                puzzleType = "custom",
                                difficulty = "custom",
                                questionIndex = 0
                            )

                            AnalyticsManager.getInstance()?.track(AnalyticsEvent.puzzleStart(
                                type = "custom",
                                difficulty = "custom",
                                questionIndex = 0
                            ))

                            AnalyticsManager.getInstance()?.track(AnalyticsEvent("custom_puzzle_started", mapOf(
                                "puzzle_id" to customPuzzleId,
                                "puzzle_count" to puzzles.size,
                                "screen_type" to selectedScreenType,
                                "puzzle_format" to (firstPuzzle.puzzleType ?: "unknown"),
                                "has_options" to (firstPuzzle.options?.isNotEmpty() == true),
                                "has_group_context" to (sourceGroupId != null),
                                "source_group_id" to (sourceGroupId ?: "none"),
                                "session_id" to AnalyticsSessionManager.getInstance().getCurrentSessionId()
                            )))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to track custom puzzle start", e)
                    }

                    // 🔧 CRITICAL: Add group context to custom puzzle intent
                    val intent = Intent(context, PuzzleActivity::class.java).apply {
                        putExtra("puzzleList", Gson().toJson(puzzles))
                        putExtra("puzzleIndex", 0)
                        putExtra("customPuzzleSet", puzzleSet.toString())
                        putExtra("screenType", selectedScreenType)
                        putExtra("score", 0)
                        putExtra("startTime", System.currentTimeMillis())
                        putExtra("isCustomPuzzle", true)
                        putExtra("customPuzzleId", customPuzzleId)

                        // 🔧 NEW: Add group context if available
                        if (sourceGroupId != null && sourceGroupName != null) {
                            putExtra("sourceGroupId", sourceGroupId)
                            putExtra("sourceGroupName", sourceGroupName)
                            putExtra("puzzleType", puzzleType ?: "custom")

                            Log.d(TAG, "🎯 Adding group context to custom puzzle:")
                            Log.d(TAG, "  sourceGroupId: $sourceGroupId")
                            Log.d(TAG, "  sourceGroupName: $sourceGroupName")
                            Log.d(TAG, "  puzzleType: ${puzzleType ?: "custom"}")
                        }
                    }

                    context.startActivity(intent)
                    onSuccess()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Parsing error: ${e.message}", e)
                Handler(Looper.getMainLooper()).post {
                    try {
                        AnalyticsManager.getInstance()?.track(AnalyticsEvent("custom_puzzle_start_failed", mapOf(
                            "puzzle_id" to customPuzzleId,
                            "error_type" to "parsing_error",
                            "error_message" to (e.message ?: "unknown"),
                            "screen_type" to selectedScreenType,
                            "has_group_context" to (sourceGroupId != null),
                            "response_preview" to (body?.take(100) ?: "no_body")
                        )))
                    } catch (analyticsError: Exception) {
                        Log.e(TAG, "Analytics tracking failed", analyticsError)
                    }
                    onError("Error parsing custom puzzle: ${e.message}")
                }
            }
        }
    })
}

fun showPuzzleRejectionDialog(
    context: Context,
    puzzleName: String,
    creator: String,
    format: String,
    status: String,
    onDismiss: () -> Unit
) {
    val title = when (status) {
        "failed" -> "Topic Rejected"
        "regenerating" -> "Puzzle Regenerating"
        else -> "Content Review Required"
    }

    val message = "This topic has been flagged by our content review system as potentially inappropriate. This is an automated process and may occasionally flag content incorrectly."

    try {
        // 🔧 FIX: Use Material3 AlertDialog instead of AppCompat
        Handler(Looper.getMainLooper()).post {
            try {
                val builder = android.app.AlertDialog.Builder(context) // Use android.app.AlertDialog
                    .setTitle("🚫 $title")
                    .setMessage(
                        "$message\n\n" +
                                "Puzzle: \"$puzzleName\"\n" +
                                "Creator: $creator\n" +
                                "Format: $format"
                    )
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                        onDismiss()
                    }
                    .setCancelable(false)

                builder.show()
                Log.d("PuzzleRejection", "✅ Material3 dialog shown successfully for status: $status")

            } catch (e: Exception) {
                Log.e("PuzzleRejection", "❌ Failed to show Material3 dialog", e)
                // Fallback to toast
                Toast.makeText(context, "Puzzle topic rejected: $puzzleName", Toast.LENGTH_LONG).show()
                onDismiss()
            }
        }
    } catch (e: Exception) {
        Log.e("PuzzleRejection", "❌ Failed to post to main thread", e)
        // Fallback to toast
        Toast.makeText(context, "Puzzle topic rejected: $puzzleName", Toast.LENGTH_LONG).show()
        onDismiss()
    }
}

fun fetchPuzzleAndStartGame(
    context: Context,
    type: String,
    difficulty: String,
    screenType: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onLimitReached: (RegenerationLimitManager.LimitInfo) -> Unit,
    targetPuzzleCount: Int,
    sourceGroupId: String? = null,
    sourceGroupName: String? = null,
    onRegenerationInProgress: ((RegenerationInfo) -> Unit)? = null
) {
    // Redirect to queue-based approach
    fetchPuzzleAndStartGameNonBlocking(
        context, type, difficulty, screenType, onSuccess, onError,
        onLimitReached, targetPuzzleCount, sourceGroupId, sourceGroupName,
        onRegenerationInProgress
    )
}

private fun startPuzzleActivity(
    context: Context,
    puzzle: Puzzle,
    screenType: String,
    difficulty: String,
    targetPuzzleCount: Int,
    sourceGroupId: String?,
    sourceGroupName: String?,
    puzzleType: String,
    source: String
) {
    val finalScreenType = when {
        screenType != "Default" -> screenType
        puzzleType == "average" -> "Averages Screen"
        puzzleType == "antonyms" -> "Antonym Balloon Screen"
        puzzleType == "synonyms" -> "Synonym Grouping Screen"
        puzzleType == "memorysquares" -> "Memory Squares Screen"
        puzzleType == "memorystory" -> "Memory Story Screen"
        puzzleType == "crossword" -> "Crossword Screen"
        puzzle.options?.isNotEmpty() == true && screenType == "Default" -> "multipleChoice"
        else -> "qa"
    }

    val intent = Intent(context, PuzzleActivity::class.java).apply {
        putExtra("puzzle", Gson().toJson(puzzle))
        putExtra("screenType", finalScreenType)
        putExtra("difficulty", difficulty)
        putExtra("score", 0)
        putExtra("targetPuzzleCount", targetPuzzleCount)
        putExtra("startTime", System.currentTimeMillis())
        putExtra("puzzleSource", source)

        // Add group context if available
        if (sourceGroupId != null && sourceGroupName != null) {
            putExtra("sourceGroupId", sourceGroupId)
            putExtra("sourceGroupName", sourceGroupName)
            putExtra("puzzleType", puzzleType)
        }
    }

    context.startActivity(intent)
}

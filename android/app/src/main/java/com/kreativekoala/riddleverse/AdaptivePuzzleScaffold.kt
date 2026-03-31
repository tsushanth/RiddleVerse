// AdaptivePuzzleScaffold.kt - Reusable scaffold for all adaptive puzzle screens
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.google.firebase.auth.FirebaseAuth
import com.kreativekoala.riddleverse.util.DebugLogger
import kotlinx.coroutines.delay

/**
 * State holder for adaptive puzzle screens.
 * Encapsulates all common state management logic.
 */
class AdaptivePuzzleState<T>(
    val puzzleType: String,
    initialDifficulty: String,
    val generatePuzzle: (DifficultyManager.DifficultyLevel) -> T
) {
    private val TAG = "AdaptivePuzzle[$puzzleType]"

    // Difficulty management
    val difficultyManager = DifficultyManager()
    var currentDifficultyLevel by mutableStateOf(difficultyManager.getCurrentDifficulty(puzzleType))
        private set
    var adaptationInfo by mutableStateOf<DifficultyManager.AdaptiveConfig?>(null)
        private set
    var showAdaptationNotification by mutableStateOf(false)

    // Competitive ranking
    var competitiveInsight by mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null)
        private set

    // Current puzzle
    var currentPuzzle by mutableStateOf(generatePuzzle(currentDifficultyLevel))
        private set

    // Score and performance tracking
    var totalScore by mutableIntStateOf(0)
        private set
    var currentStreak by mutableIntStateOf(0)
        private set
    var currentHearts by mutableIntStateOf(currentDifficultyLevel.livesAllowed)
        private set
    var gamesPlayedThisSession by mutableIntStateOf(0)
        private set

    // Session tracking
    var sessionStartTime by mutableLongStateOf(System.currentTimeMillis())
        private set
    var correctAnswers by mutableIntStateOf(0)
        private set
    var totalAnswers by mutableIntStateOf(0)
        private set

    // Timer state
    var timeRemaining by mutableIntStateOf(currentDifficultyLevel.timeLimit)
        private set
    var gameStartTime by mutableLongStateOf(System.currentTimeMillis())
        private set

    // Game state
    var isAnswered by mutableStateOf(false)
        private set
    var gameCompleted by mutableStateOf(false)
        private set

    // Feedback manager
    val feedbackManager = UnifiedFeedbackManagerHolder()

    /**
     * Record performance and potentially adapt difficulty
     */
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = getComplexity(),
            totalScore = totalScore,
            puzzleType = puzzleType
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                val oldLevel = currentDifficultyLevel.name
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
                DebugLogger.adaptive(TAG, puzzleType, oldLevel, config.level.name, config.adjustmentReason)
            }
        }
    }

    /**
     * Calculate score for current answer
     */
    fun calculateScore(isCorrect: Boolean, timeSpent: Long): Int {
        return calculateUnifiedAdaptiveScore(
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            difficulty = currentDifficultyLevel,
            challengeComplexity = getComplexity(),
            currentStreak = currentStreak,
            challengesCompleted = gamesPlayedThisSession + 1,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = puzzleType
        )
    }

    /**
     * Handle correct answer
     */
    fun onCorrectAnswer(timeSpent: Long) {
        totalAnswers++
        correctAnswers++
        currentStreak++
        val score = calculateScore(true, timeSpent)
        totalScore += score
        gamesPlayedThisSession++
        recordPerformance(true, timeSpent)
        isAnswered = true

        DebugLogger.score(TAG, puzzleType, currentDifficultyLevel.basePoints, score,
            mapOf("streak" to currentStreak, "timeBonus" to (score - currentDifficultyLevel.basePoints)))
    }

    /**
     * Handle incorrect answer
     */
    fun onIncorrectAnswer(timeSpent: Long) {
        totalAnswers++
        currentStreak = 0
        currentHearts = maxOf(0, currentHearts - 1)
        recordPerformance(false, timeSpent)
        isAnswered = true

        DebugLogger.d(TAG, "Incorrect answer. Hearts remaining: $currentHearts")
    }

    /**
     * Move to next puzzle
     */
    fun nextPuzzle() {
        currentPuzzle = generatePuzzle(currentDifficultyLevel)
        isAnswered = false
        gameStartTime = System.currentTimeMillis()
        timeRemaining = currentDifficultyLevel.timeLimit
    }

    /**
     * Reset for difficulty change
     */
    fun onDifficultyChanged() {
        if (gamesPlayedThisSession > 0) {
            currentPuzzle = generatePuzzle(currentDifficultyLevel)
            isAnswered = false
            currentHearts = currentDifficultyLevel.livesAllowed
            gameStartTime = System.currentTimeMillis()
            timeRemaining = currentDifficultyLevel.timeLimit
        }
    }

    /**
     * Decrement timer
     */
    fun decrementTimer() {
        if (timeRemaining > 0) {
            timeRemaining--
        }
    }

    /**
     * Mark game as completed
     */
    fun completeGame() {
        gameCompleted = true
    }

    /**
     * Dismiss adaptation notification
     */
    fun dismissAdaptationNotification() {
        showAdaptationNotification = false
    }

    /**
     * Update competitive insight
     */
    fun updateCompetitiveInsight(insight: CompetitiveRankingManager.CompetitiveInsight?) {
        competitiveInsight = insight
    }

    /**
     * Get session statistics
     */
    fun getSessionStats(): SessionStatistics {
        return SessionStatistics(
            correctAnswers = correctAnswers,
            totalAnswers = totalAnswers,
            totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
            bestStreak = currentStreak,
            winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
            totalScore = totalScore,
            averageTimePerPuzzle = if (totalAnswers > 0) {
                ((System.currentTimeMillis() - sessionStartTime) / 1000 / totalAnswers).toInt()
            } else 0,
            currentStreak = currentStreak,
            individualTimes = emptyList()
        )
    }

    /**
     * Get complexity based on puzzle type
     */
    private fun getComplexity(): Int {
        return currentDifficultyLevel.gridSize.takeIf { it > 0 }
            ?: currentDifficultyLevel.targetCount.takeIf { it > 0 }
            ?: 1
    }

    /**
     * Get time spent on current puzzle in milliseconds
     */
    fun getTimeSpentMs(): Long = System.currentTimeMillis() - gameStartTime

    /**
     * Format timer for display
     */
    fun formatTimer(): String = formatTime(timeRemaining)

    /**
     * Check if game is over (no hearts left)
     */
    fun isGameOver(): Boolean = currentHearts <= 0

    /**
     * Check if time is up
     */
    fun isTimeUp(): Boolean = timeRemaining <= 0
}

/**
 * Holder class for feedback manager to work with remember
 */
class UnifiedFeedbackManagerHolder {
    private var manager: UnifiedFeedbackManager? = null

    @Composable
    fun get(): UnifiedFeedbackManager {
        if (manager == null) {
            manager = rememberUnifiedFeedbackManager()
        }
        return manager!!
    }
}

/**
 * Composable scaffold for adaptive puzzle screens.
 * Handles common setup, state management, and UI structure.
 */
@Composable
fun <T> AdaptivePuzzleScaffold(
    puzzleType: String,
    initialDifficulty: String = "Medium",
    generatePuzzle: (DifficultyManager.DifficultyLevel) -> T,
    onBack: () -> Unit,
    onGameComplete: (success: Boolean, score: Int) -> Unit,
    content: @Composable (state: AdaptivePuzzleState<T>, feedbackManager: UnifiedFeedbackManager) -> Unit
) {
    val state = remember {
        AdaptivePuzzleState(
            puzzleType = puzzleType,
            initialDifficulty = initialDifficulty,
            generatePuzzle = generatePuzzle
        )
    }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val haptics = LocalHapticFeedback.current
    val currentUser = FirebaseAuth.getInstance().currentUser

    // Load competitive insight when difficulty changes
    LaunchedEffect(state.currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                val insight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = puzzleType,
                    difficulty = state.currentDifficultyLevel.name
                )
                state.updateCompetitiveInsight(insight)
            } catch (e: Exception) {
                DebugLogger.e("AdaptivePuzzle", "Failed to load competitive insight", e)
            }
        }
    }

    // Handle difficulty changes
    LaunchedEffect(state.currentDifficultyLevel) {
        state.onDifficultyChanged()
    }

    // Timer countdown effect
    LaunchedEffect(state.timeRemaining, state.isAnswered) {
        if (state.timeRemaining > 0 && !state.isAnswered) {
            delay(1000L)
            state.decrementTimer()
        } else if (state.isTimeUp() && !state.isAnswered) {
            // Time's up - auto-submit
            state.onIncorrectAnswer(state.getTimeSpentMs())
        }
    }

    // Session completion handling
    if (state.isGameOver() || state.gameCompleted) {
        UnifiedSessionCompletionHandler(
            puzzleType = puzzleType,
            sessionScore = state.totalScore,
            sessionStats = state.getSessionStats(),
            currentDifficulty = state.currentDifficultyLevel
        ) { result ->
            onGameComplete(state.correctAnswers > 0, state.totalScore)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        content(state, feedbackManager)
        EnhancedUniversalFeedback(feedbackManager)
    }
}

/**
 * Extension function for DifficultyLevel to get formatted timer display
 */
fun DifficultyManager.DifficultyLevel.formatTimerDisplay(): String {
    val minutes = timeLimit / 60
    val seconds = timeLimit % 60
    return "$minutes:${String.format("%02d", seconds)}"
}

/**
 * Extension function to get timer text based on difficulty string
 */
fun getTimerTextForDifficulty(difficulty: String): String {
    return when (difficulty.lowercase()) {
        "easy", "beginner", "tutorial" -> "4:00"
        "medium", "medium-", "medium+" -> "3:00"
        "hard", "hard-", "hard+" -> "2:00"
        "expert", "expert-", "expert+", "master", "grandmaster" -> "1:30"
        else -> "3:00"
    }
}

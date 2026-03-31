package com.kreativekoala.riddleverse

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pure UI Feedback Controller - Handles all user feedback presentation
 * Does NOT handle: Data storage, game logic calculations, progression logic
 * Does handle: UI feedback display, sound effects, animations, timing
 */
class FeedbackManager(
    private val context: Context,
    private val progressionEngine: ProgressionEngine
) {

    // UI State management
    private val _isShowingFeedback = mutableStateOf(false)
    private val _isProcessing = mutableStateOf(false)
    private val _animateIcon = mutableStateOf(false)
    private val _showConfetti = mutableStateOf(false)
    private val _currentFeedback = mutableStateOf<FeedbackData?>(null)

    // Public UI state accessors
    val isShowingFeedback: Boolean get() = _isShowingFeedback.value
    val isProcessing: Boolean get() = _isProcessing.value
    val animateIcon: Boolean get() = _animateIcon.value
    val showConfetti: Boolean get() = _showConfetti.value
    val currentFeedback: FeedbackData? get() = _currentFeedback.value

    // Sound management
    private val soundManager = SoundManager(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // ================================
    // MAIN FEEDBACK INTERFACE
    // ================================

    /**
     * Show feedback for puzzle completion - Main entry point
     */
    fun showPuzzleCompletionFeedback(
        puzzleType: String,
        isCorrect: Boolean,
        userAnswer: String,
        correctAnswer: String,
        score: Int = 0,
        timeSpentSeconds: Int,
        timeRemaining: Int = 0,
        totalTime: Int = 60,
        difficulty: String = "Medium",
        hintsUsed: Int = 0,
        customMessage: String? = null,
        onComplete: () -> Unit = {}
    ) {

        Log.d("FeedbackManager", "🎭 Showing feedback: $puzzleType, correct=$isCorrect")

        _isProcessing.value = true

        // Process progression through engine
        val progressionResult = progressionEngine.processPuzzleCompletion(
            puzzleType = puzzleType,
            isCorrect = isCorrect,
            score = score,
            timeSpentSeconds = timeSpentSeconds,
            timeRemaining = timeRemaining,
            totalTime = totalTime,
            difficulty = difficulty,
            hintsUsed = hintsUsed
        )

        _isProcessing.value = false

        // Create feedback data
        val feedbackData = FeedbackData(
            isCorrect = isCorrect,
            userAnswer = userAnswer,
            correctAnswer = correctAnswer,
            progressionResult = progressionResult,
            puzzleType = puzzleType,
            customMessage = customMessage,
            onComplete = onComplete
        )

        // Display the feedback
        displayFeedback(feedbackData)
    }

    /**
     * Show simple feedback without progression processing
     */
    fun showSimpleFeedback(
        isCorrect: Boolean,
        userAnswer: String,
        correctAnswer: String,
        message: String? = null,
        autoHideDuration: Long = 3000L,
        onComplete: () -> Unit = {}
    ) {
        Log.d("FeedbackManager", "🎭 Showing simple feedback: correct=$isCorrect")

        val feedbackData = FeedbackData(
            isCorrect = isCorrect,
            userAnswer = userAnswer,
            correctAnswer = correctAnswer,
            progressionResult = null,
            puzzleType = "",
            customMessage = message,
            onComplete = onComplete
        )

        displayFeedback(feedbackData, autoHideDuration)
    }

    // ================================
    // PRIVATE FEEDBACK DISPLAY LOGIC
    // ================================

    /**
     * Internal method to display feedback with all animations and effects
     */
    private fun displayFeedback(
        feedbackData: FeedbackData,
        autoHideDuration: Long = 0L
    ) {
        Log.d("FeedbackManager", "🎬 Displaying feedback UI")

        // Set feedback data and show UI
        _currentFeedback.value = feedbackData
        _isShowingFeedback.value = true

        // Play appropriate sound effect
        val soundEffect = determineSoundEffect(feedbackData)
        soundManager.playSound(soundEffect)

        // Start icon animation
        _animateIcon.value = true

        // Show confetti for special achievements
        if (shouldShowConfetti(feedbackData)) {
            _showConfetti.value = true
        }

        // Determine auto-hide duration
        val duration = if (autoHideDuration > 0) {
            autoHideDuration
        } else {
            calculateAutoHideDuration(feedbackData)
        }

        // Schedule auto-hide
        coroutineScope.launch {
            delay(duration)
            hideFeedback()
        }
    }

    /**
     * Hide all feedback and reset state
     */
    fun hideFeedback() {
        Log.d("FeedbackManager", "🚪 Hiding feedback")

        val feedback = _currentFeedback.value

        // Reset UI state
        _isShowingFeedback.value = false
        _animateIcon.value = false
        _showConfetti.value = false

        // Call completion handler
        feedback?.onComplete?.invoke()

        // Clear current feedback
        _currentFeedback.value = null
    }

    // ================================
    // FEEDBACK CUSTOMIZATION LOGIC
    // ================================

    /**
     * Determine which sound effect to play based on context
     */
    private fun determineSoundEffect(feedbackData: FeedbackData): SoundEffect {
        if (!feedbackData.isCorrect) {
            return SoundEffect.INCORRECT
        }

        val progressionResult = feedbackData.progressionResult

        // Priority order for sound selection
        return when {
            progressionResult?.levelUp != null -> SoundEffect.LEVEL_UP
            progressionResult?.newAchievements?.isNotEmpty() == true -> SoundEffect.ACHIEVEMENT
            progressionResult?.streakInfo?.dailyStreak ?: 0 >= 7 -> SoundEffect.DAILY_STREAK
            progressionResult?.streakInfo?.currentStreak ?: 0 >= 10 -> SoundEffect.STREAK_LARGE
            progressionResult?.streakInfo?.currentStreak ?: 0 >= 5 -> SoundEffect.STREAK_MEDIUM
            progressionResult?.streakInfo?.currentStreak ?: 0 >= 3 -> SoundEffect.STREAK_SMALL
            progressionResult?.scoreBreakdown?.timeBonus ?: 0 > (progressionResult?.scoreBreakdown?.baseScore ?: 0) * 0.4 -> SoundEffect.PERFECT
            progressionResult?.scoreBreakdown?.timeBonus ?: 0 > 0 -> SoundEffect.TIME_BONUS
            else -> SoundEffect.CORRECT
        }
    }

    /**
     * Determine if confetti should be shown
     */
    private fun shouldShowConfetti(feedbackData: FeedbackData): Boolean {
        if (!feedbackData.isCorrect) return false

        val progressionResult = feedbackData.progressionResult ?: return false

        return progressionResult.levelUp != null ||
                progressionResult.newAchievements.isNotEmpty() ||
                progressionResult.streakInfo.currentStreak >= 5 ||
                progressionResult.streakInfo.dailyStreak >= 7
    }

    /**
     * Calculate how long to show feedback based on content
     */
    private fun calculateAutoHideDuration(feedbackData: FeedbackData): Long {
        if (!feedbackData.isCorrect) return 4000L

        val progressionResult = feedbackData.progressionResult ?: return 3000L

        // Longer display for special achievements
        return when {
            progressionResult.levelUp != null -> 5000L
            progressionResult.newAchievements.isNotEmpty() -> 4500L
            shouldShowConfetti(feedbackData) -> 4000L
            progressionResult.streakInfo.currentStreak >= 3 -> 3500L
            else -> 3000L
        }
    }

    // ================================
    // SOUND MANAGEMENT
    // ================================

    /**
     * Play a specific sound effect
     */
    fun playSound(effect: SoundEffect) {
        soundManager.playSound(effect)
    }

    // ================================
    // LIFECYCLE MANAGEMENT
    // ================================

    /**
     * Release resources when no longer needed
     */
    fun release() {
        soundManager.release()
        _currentFeedback.value = null
    }
}

// ================================
// SOUND MANAGEMENT
// ================================

enum class SoundEffect(val fileName: String) {
    CORRECT("correct"),
    INCORRECT("buzz"),
    STREAK_SMALL("streak_small"),
    STREAK_MEDIUM("streak_medium"),
    STREAK_LARGE("streak_large"),
    DAILY_STREAK("daily_streak"),
    LEVEL_UP("level_up"),
    ACHIEVEMENT("achievement"),
    PERFECT("perfect"),
    TIME_BONUS("time_bonus")
}

/**
 * Internal sound management class
 */
private class SoundManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<SoundEffect, Int>()

    init {
        initializeSoundPool()
        loadSounds()
    }

    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    private fun loadSounds() {
        SoundEffect.values().forEach { effect ->
            val resId = context.resources.getIdentifier(
                effect.fileName,
                "raw",
                context.packageName
            )

            if (resId != 0) {
                soundPool?.let { pool ->
                    val soundId = pool.load(context, resId, 1)
                    soundMap[effect] = soundId
                }
            } else {
                Log.w("SoundManager", "Sound file ${effect.fileName} not found")
            }
        }
    }

    fun playSound(effect: SoundEffect) {
        soundMap[effect]?.let { soundId ->
            soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            Log.d("SoundManager", "🔊 Playing sound: ${effect.fileName}")
        } ?: run {
            Log.w("SoundManager", "Sound ${effect.fileName} not loaded, using fallback")
            playFallbackSound(effect)
        }
    }

    private fun playFallbackSound(effect: SoundEffect) {
        try {
            val uri = when (effect) {
                SoundEffect.CORRECT, SoundEffect.PERFECT ->
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                else ->
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            }

            val mediaPlayer = MediaPlayer.create(context, uri)
            mediaPlayer?.let { player ->
                player.setOnCompletionListener { mp -> mp.release() }
                player.start()
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Failed to play fallback sound", e)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundMap.clear()
    }
}

// ================================
// FEEDBACK DATA CLASSES
// ================================

/**
 * Data class containing all feedback information
 */
data class FeedbackData(
    val isCorrect: Boolean,
    val userAnswer: String,
    val correctAnswer: String,
    val progressionResult: ProgressionResult?,
    val puzzleType: String,
    val customMessage: String? = null,
    val onComplete: () -> Unit = {}
) {
    /**
     * Generate main feedback message
     */
    val mainMessage: String
        get() = customMessage ?: if (isCorrect) {
            progressionResult?.let { result ->
                "Correct! +${result.scoreBreakdown.totalScore} points"
            } ?: "Correct!"
        } else {
            "Try again! Keep practicing! 💪"
        }

    /**
     * Check if we should show progression details
     */
    val showProgressionDetails: Boolean
        get() = isCorrect && progressionResult != null
}

// ================================
// COMPOSE INTEGRATION HELPERS
// ================================

/**
 * Remember feedback manager in Compose
 */
@Composable
fun rememberFeedbackManager(): FeedbackManager {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userStatsManager = remember { UserStatsManager.getInstance(context) }
    val progressionEngine = remember { ProgressionEngine(context, userStatsManager) }

    return remember { FeedbackManager(context, progressionEngine) }
}

/**
 * Feedback state for simple feedback scenarios
 */
class SimpleFeedbackState {
    private var _isVisible by mutableStateOf(false)
    private var _message by mutableStateOf("")
    private var _isCorrect by mutableStateOf(false)

    val isVisible: Boolean get() = _isVisible
    val message: String get() = _message
    val isCorrect: Boolean get() = _isCorrect

    fun showFeedback(message: String, isCorrect: Boolean, duration: Long = 2000L) {
        _message = message
        _isCorrect = isCorrect
        _isVisible = true

        // Auto-hide
        CoroutineScope(Dispatchers.Main).launch {
            delay(duration)
            _isVisible = false
        }
    }

    fun hideFeedback() {
        _isVisible = false
    }
}

/**
 * Remember simple feedback state
 */
@Composable
fun rememberSimpleFeedbackState(): SimpleFeedbackState {
    return remember { SimpleFeedbackState() }
}
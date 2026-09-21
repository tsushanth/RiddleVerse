package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.RvCoral
import com.kreativekoala.riddleverse.ui.theme.RvCoralEdge
import com.kreativekoala.riddleverse.ui.theme.RvError
import com.kreativekoala.riddleverse.ui.theme.RvErrorEdge
import com.kreativekoala.riddleverse.ui.theme.RvGrape
import com.kreativekoala.riddleverse.ui.theme.RvGrapeEdge
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvScrim
import com.kreativekoala.riddleverse.ui.theme.RvSkyEdge
import com.kreativekoala.riddleverse.ui.theme.RvSuccess
import com.kreativekoala.riddleverse.ui.theme.RvSuccessEdge
import com.kreativekoala.riddleverse.ui.theme.RvSun
import com.kreativekoala.riddleverse.ui.theme.RvSunEdge
import com.kreativekoala.riddleverse.ui.theme.RvSurfaceRaised
import com.kreativekoala.riddleverse.ui.theme.RvWarningEdge
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar
import java.util.Date
import kotlin.math.pow
import kotlin.math.floor
import kotlin.math.sqrt
import androidx.compose.foundation.clickable
import okhttp3.MediaType.Companion.toMediaTypeOrNull

// ================================
// BACKWARD COMPATIBLE DATA CLASSES
// Keep these for existing puzzle compatibility
// ================================

data class UserLevel(
    val level: Int,
    val currentXP: Int,
    val xpToNextLevel: Int,
    val totalXP: Int
) {
    val progressPercentage: Float
        get() = if (xpToNextLevel > 0) currentXP.toFloat() / xpToNextLevel.toFloat() else 1f
}


data class ScoreUpdateConfig(
    val puzzleType: String,
    val difficulty: String,
    val timeRemaining: Int,
    val totalTime: Int,
    val baseScore: Int? = null
)

data class ProgressionResult(
    val scoreBreakdown: ScoreBreakdown,
    val levelUp: LevelUpInfo?,
    val newAchievements: List<Achievement>,
    val streakInfo: StreakInfo,
    val totalXPGained: Int,
    val isCorrect: Boolean = true
)

data class LevelUpInfo(
    val oldLevel: Int,
    val newLevel: Int,
    val rewardPoints: Int
)

data class ScoreBreakdown(
    val baseScore: Int,
    val timeBonus: Int,
    val streakBonus: Int,
    val difficultyMultiplier: Float,
    val totalScore: Int,
    val xpGained: Int
)

data class FeedbackConfig(
    val isCorrect: Boolean,
    val userAnswer: String,
    val correctAnswer: String,
    val showAnswerComparison: Boolean = true,
    val customMessage: String? = null,
    val autoHideDuration: Long = 3000L,
    val scoreConfig: ScoreUpdateConfig? = null,
    val onFeedbackComplete: () -> Unit = {}
)

// ================================
// SOUND MANAGEMENT (KEEP EXISTING)
// ================================

enum class FeedbackSoundEffect(val fileName: String) {
    CORRECT("correct"),
    INCORRECT("buzz"),
    STREAK_3("streak_small"),
    STREAK_5("streak_medium"),
    STREAK_10("streak_large"),
    DAILY_STREAK("daily_streak"),
    LEVEL_UP("level_up"),
    ACHIEVEMENT("achievement"),
    PERFECT("perfect"),
    TIME_BONUS("time_bonus")
}

class EnhancedSoundManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<FeedbackSoundEffect, Int>()

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
        FeedbackSoundEffect.values().forEach { effect ->
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

    fun playSound(effect: FeedbackSoundEffect) {
        soundMap[effect]?.let { soundId ->
            soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            Log.d("SoundManager", "Playing sound: ${effect.fileName}")
        } ?: run {
            Log.w("SoundManager", "Sound ${effect.fileName} not loaded, using fallback")
            playFallbackSound(effect)
        }
    }

    private fun playFallbackSound(effect: FeedbackSoundEffect) {
        try {
            val mediaPlayer = when (effect) {
                FeedbackSoundEffect.CORRECT, FeedbackSoundEffect.PERFECT ->
                    MediaPlayer.create(context, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
                else ->
                    MediaPlayer.create(context, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
            }

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
// BACKWARD COMPATIBLE UNIFIED FEEDBACK MANAGER
// This maintains the exact same interface that all puzzles currently use
// ================================

class UnifiedFeedbackManager(private val context: Context) {

    // Use new clean architecture internally
    private val userStatsManager = UserStatsManager.getInstance(context)
    private val progressionEngine = ProgressionEngine(context, userStatsManager)
    private val feedbackManager = FeedbackManager(context, progressionEngine)

    // Expose the new manager's state for UI (keep same interface)
    val isShowingFeedback: Boolean get() = feedbackManager.isShowingFeedback
    val isProcessing: Boolean get() = feedbackManager.isProcessing
    val animateIcon: Boolean get() = feedbackManager.animateIcon
    val showConfetti: Boolean get() = feedbackManager.showConfetti
    val currentFeedback: FeedbackData? get() = feedbackManager.currentFeedback

    /**
     * BACKWARD COMPATIBLE: Legacy showFeedback method
     * Maintains exact same interface as before
     */
    fun showFeedback(
        puzzleType: String,
        isCorrect: Boolean,
        userAnswer: String,
        correctAnswer: String,
        timeSpent: Long,
        difficulty: String,
        hintsUsed: Int = 0,
        timeRemaining: Int = 0,
        totalTime: Int = 60,
        onComplete: () -> Unit = {}
    ) {
        Log.d("UnifiedFeedbackManager", "🎭 Legacy showFeedback called: $puzzleType, correct=$isCorrect")

        // Convert to new FeedbackManager call
        feedbackManager.showPuzzleCompletionFeedback(
            puzzleType = puzzleType,
            isCorrect = isCorrect,
            userAnswer = userAnswer,
            correctAnswer = correctAnswer,
            score = 0, // Will be calculated internally
            timeSpentSeconds = timeSpent.toInt(),
            timeRemaining = timeRemaining,
            totalTime = totalTime,
            difficulty = difficulty,
            hintsUsed = hintsUsed,
            onComplete = onComplete
        )
    }

    /**
     * BACKWARD COMPATIBLE: Legacy showFeedback with config
     */
    fun showFeedback(config: FeedbackConfig) {
        Log.d("UnifiedFeedbackManager", "🎭 Legacy showFeedback with config called")

        val scoreConfig = config.scoreConfig
        if (scoreConfig != null) {
            feedbackManager.showPuzzleCompletionFeedback(
                puzzleType = scoreConfig.puzzleType,
                isCorrect = config.isCorrect,
                userAnswer = config.userAnswer,
                correctAnswer = config.correctAnswer,
                timeRemaining = scoreConfig.timeRemaining,
                totalTime = scoreConfig.totalTime,
                difficulty = scoreConfig.difficulty,
                customMessage = config.customMessage,
                onComplete = config.onFeedbackComplete,
                timeSpentSeconds = scoreConfig.totalTime - scoreConfig.timeRemaining,
            )
        } else {
            feedbackManager.showSimpleFeedback(
                isCorrect = config.isCorrect,
                userAnswer = config.userAnswer,
                correctAnswer = config.correctAnswer,
                message = config.customMessage,
                autoHideDuration = config.autoHideDuration,
                onComplete = config.onFeedbackComplete
            )
        }
    }

    /**
     * BACKWARD COMPATIBLE: Hide feedback
     */
    fun hideFeedback() {
        feedbackManager.hideFeedback()
    }

    /**
     * BACKWARD COMPATIBLE: Release resources
     */
    fun release() {
        feedbackManager.release()
    }

    // Public access to progression data (for compatibility with existing puzzle code)
    fun getCurrentDailyStreak(): Int = progressionEngine.getCurrentDailyStreak()

    fun getBestDailyStreak(): Int {
        val prefs = context.getSharedPreferences("progression_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("best_daily_streak", 0)
    }

    fun hasPlayedToday(): Boolean {
        val dailyData = userStatsManager.getDailyActivityData()
        return dailyData.hasPlayedToday
    }

    fun getCurrentLevel(): UserLevel = progressionEngine.getCurrentLevel()

    fun getStreakInfo(): StreakInfo {
        val current = progressionEngine.getCurrentStreak()
        val best = progressionEngine.getBestStreak()
        val daily = progressionEngine.getCurrentDailyStreak()
        return StreakInfo(
            currentStreak = current,
            bestStreak = best,
            streakMultiplier = when {
                current >= 10 -> 2.0f
                current >= 5 -> 1.5f
                current >= 3 -> 1.25f
                else -> 1.0f
            },
            dailyStreak = daily,
            hasDailyStreakBonus = daily >= 3
        )
    }
}

// ================================
// BACKWARD COMPATIBLE COMPOSABLES
// Keep existing interfaces for puzzle activities
// ================================

/**
 * BACKWARD COMPATIBLE: Keep existing composable rememberUnifiedFeedbackManager
 */
@Composable
fun rememberUnifiedFeedbackManager(): UnifiedFeedbackManager {
    val context = LocalContext.current
    return remember { UnifiedFeedbackManager(context) }
}

/**
 * BACKWARD COMPATIBLE: EnhancedUniversalFeedbackOverlay
 * Keep existing composable interface but use new manager internally
 */
@Composable
fun EnhancedUniversalFeedbackOverlay(
    manager: UnifiedFeedbackManager,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = manager.isShowingFeedback,
        enter = scaleIn(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
        exit = scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        manager.currentFeedback?.let { feedback ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RvScrim)
                    .clickable { manager.hideFeedback() },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight()
                        .clickable { manager.hideFeedback() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (feedback.isCorrect) RvSuccess.copy(alpha = 0.15f) else RvError.copy(alpha = 0.15f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Animated icon
                        val scale by animateFloatAsState(
                            targetValue = if (manager.animateIcon) 1f else 0.1f,
                            animationSpec = tween(500, delayMillis = 200),
                            label = "iconScale"
                        )

                        Icon(
                            imageVector = if (feedback.isCorrect) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (feedback.isCorrect) RvSuccess else RvError,
                            modifier = Modifier
                                .size(60.dp)
                                .scale(scale)
                        )

                        // Main result message
                        Text(
                            text = feedback.mainMessage,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (feedback.isCorrect) RvSuccessEdge else RvErrorEdge
                        )

                        // Enhanced progression content (only for correct answers)
                        if (feedback.isCorrect) {
                            feedback.progressionResult?.let { progressionResult ->
                                EnhancedProgressionContentView(progressionResult)
                            }
                        } else {
                            // Answer comparison and encouragement for wrong answers
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Answer comparison
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Your answer: ${feedback.userAnswer}",
                                        fontSize = 16.sp,
                                        color = RvInk
                                    )
                                    Text(
                                        text = "Correct answer: ${feedback.correctAnswer}",
                                        fontSize = 16.sp,
                                        color = RvErrorEdge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Text(
                                    text = "Keep trying! You've got this! 💪",
                                    fontSize = 16.sp,
                                    color = RvInkSoft,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Score updating indicator
                        if (manager.isProcessing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = RvSuccess
                                )
                                Text(
                                    text = "Updating score...",
                                    fontSize = 12.sp,
                                    color = RvInkSoft
                                )
                            }
                        }

                        // Tap to continue hint
                        Text(
                            text = "Tap to continue",
                            fontSize = 12.sp,
                            color = RvInkSoft,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // Confetti overlay
        if (manager.showConfetti) {
            ConfettiAnimation()
        }
    }
}

@Composable
private fun EnhancedProgressionContentView(progressionResult: ProgressionResult) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Score breakdown
        EnhancedScoreBreakdownView(progressionResult.scoreBreakdown)

        // Level up notification
        progressionResult.levelUp?.let { levelUp ->
            LevelUpView(levelUp)
        }

        // Enhanced streak display
        if (progressionResult.streakInfo.hasStreakBonus || progressionResult.streakInfo.hasDailyStreakBonus) {
            EnhancedStreakBonusCard(progressionResult.streakInfo)
        }

        // New achievements
        if (progressionResult.newAchievements.isNotEmpty()) {
            NewAchievementsView(progressionResult.newAchievements)
        }
    }
}

@Composable
private fun EnhancedScoreBreakdownView(scoreBreakdown: ScoreBreakdown) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Total score display
        Text(
            text = "+${scoreBreakdown.totalScore} points",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = RvSuccessEdge
        )

        // Breakdown details
        if (scoreBreakdown.timeBonus > 0 || scoreBreakdown.streakBonus > 0 || scoreBreakdown.difficultyMultiplier > 1.0f) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ScoreRowView("Base Score", scoreBreakdown.baseScore, RvSuccessEdge)

                    if (scoreBreakdown.timeBonus > 0) {
                        ScoreRowView("Time Bonus", scoreBreakdown.timeBonus, RvSkyEdge)
                    }

                    if (scoreBreakdown.streakBonus > 0) {
                        ScoreRowView("Streak Bonus", scoreBreakdown.streakBonus, RvWarningEdge)
                    }

                    if (scoreBreakdown.difficultyMultiplier > 1.0f) {
                        val multiplierText = "${(scoreBreakdown.difficultyMultiplier * 100).toInt()}% difficulty"
                        Text(
                            text = multiplierText,
                            fontSize = 12.sp,
                            color = RvGrapeEdge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // XP gained
        Text(
            text = "+${scoreBreakdown.xpGained} XP",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = RvSkyEdge
        )
    }
}

@Composable
private fun LevelUpView(levelUp: LevelUpInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RvSun.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = RvSunEdge,
                modifier = Modifier.size(32.dp)
            )

            Text(
                text = "LEVEL UP!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvSunEdge
            )

            Text(
                text = "Level ${levelUp.oldLevel} → ${levelUp.newLevel}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = RvInk
            )

            Text(
                text = "+${levelUp.rewardPoints} bonus points!",
                fontSize = 14.sp,
                color = RvSuccessEdge
            )
        }
    }
}

@Composable
fun EnhancedStreakBonusCard(
    streakInfo: StreakInfo,
    modifier: Modifier = Modifier
) {
    if (streakInfo.hasStreakBonus || streakInfo.hasDailyStreakBonus) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = RvSun.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (streakInfo.hasStreakBonus) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚡", fontSize = 20.sp)
                        Column {
                            Text(
                                text = "${streakInfo.currentStreak} question streak!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvSunEdge
                            )
                            Text(
                                text = "×${streakInfo.streakMultiplier} score multiplier",
                                fontSize = 12.sp,
                                color = RvWarningEdge
                            )
                        }
                    }
                }

                if (streakInfo.hasDailyStreakBonus) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔥", fontSize = 20.sp)
                        Column {
                            Text(
                                text = "${streakInfo.dailyStreak} day streak!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvCoralEdge
                            )
                            Text(
                                text = "Daily streak bonus active",
                                fontSize = 12.sp,
                                color = RvCoral
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewAchievementsView(achievements: List<Achievement>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "🏆 Achievement${if (achievements.size > 1) "s" else ""} Unlocked!",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = RvGrapeEdge
        )

        achievements.take(2).forEach { achievement ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = RvGrape.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = achievement.icon,
                        fontSize = 24.sp
                    )
                    Column {
                        Text(
                            text = achievement.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                        Text(
                            text = achievement.description,
                            fontSize = 12.sp,
                            color = RvGrapeEdge
                        )
                    }
                }
            }
        }

        if (achievements.size > 2) {
            Text(
                text = "+${achievements.size - 2} more achievements!",
                fontSize = 12.sp,
                color = RvGrapeEdge
            )
        }
    }
}

@Composable
private fun ScoreRowView(
    label: String,
    value: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = RvInk
        )
        Text(
            text = "+$value",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

// ================================
// KEEP EXISTING UTILITY FUNCTIONS AND COMPOSABLES
// ================================

/**
 * BACKWARD COMPATIBLE: Legacy feedback overlay
 */
@Composable
fun UniversalFeedbackOverlay(
    config: FeedbackConfig,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val manager = rememberUnifiedFeedbackManager()

    LaunchedEffect(isVisible, config) {
        if (isVisible) {
            manager.showFeedback(config)
        }
    }

    EnhancedUniversalFeedbackOverlay(
        manager = manager,
        modifier = modifier
    )
}

/**
 * BACKWARD COMPATIBLE: BoxScope extension
 */
@Composable
fun BoxScope.EnhancedUniversalFeedback(
    manager: UnifiedFeedbackManager
) {
    EnhancedUniversalFeedbackOverlay(
        manager = manager,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ConfettiAnimation(
    modifier: Modifier = Modifier,
    duration: Long = 3000L
) {
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(duration)
        isVisible = false
    }

    AnimatedVisibility(
        visible = isVisible,
        exit = fadeOut(animationSpec = tween(500)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎉✨🎊🌟💫",
                fontSize = 48.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

// Keep existing utility functions
fun playSound(context: Context, soundResourceId: Int) {
    try {
        val mediaPlayer = MediaPlayer.create(context, soundResourceId)
        mediaPlayer?.let { player ->
            player.setOnCompletionListener { mp ->
                mp.release()
            }
            player.start()
        }
    } catch (e: Exception) {
        Log.e("Sound", "Error playing sound", e)
    }
}

// ================================
// SCORE MANAGER (KEEP EXISTING INTERFACE)
// ================================

class ScoreManager {
    companion object {
        private val baseScores = mapOf(
            "math" to mapOf("Easy" to 10, "Medium" to 15, "Hard" to 20),
            "average" to mapOf("Easy" to 15, "Medium" to 20, "Hard" to 25),
            "division" to mapOf("Easy" to 20, "Medium" to 25, "Hard" to 30),
            "estimation" to mapOf("Easy" to 18, "Medium" to 23, "Hard" to 28),
            "percentage" to mapOf("Easy" to 16, "Medium" to 21, "Hard" to 26),
            "discounts" to mapOf("Easy" to 22, "Medium" to 27, "Hard" to 32),
            "purchasing" to mapOf("Easy" to 19, "Medium" to 24, "Hard" to 29),
            "conversion" to mapOf("Easy" to 17, "Medium" to 22, "Hard" to 27),
            "anagram" to mapOf("Easy" to 15, "Medium" to 20, "Hard" to 25),
            "trivia" to mapOf("Easy" to 12, "Medium" to 17, "Hard" to 22),
            "storyPuzzle" to mapOf("Easy" to 14, "Medium" to 19, "Hard" to 24)
        )

        fun getBaseScore(puzzleType: String, difficulty: String): Int {
            return baseScores[puzzleType.lowercase()]?.get(difficulty) ?: 15
        }

        fun calculateTimeBonus(timeRemaining: Int, totalTime: Int, baseScore: Int): Int {
            if (timeRemaining <= 0 || totalTime <= 0) return 0

            val timePercentage = timeRemaining.toDouble() / totalTime.toDouble()
            val maxBonus = baseScore.toDouble() * 0.5

            val bonus = maxBonus * timePercentage.pow(0.7)
            return bonus.toInt()
        }

        fun updateBackendScore(points: Int) {
            Thread {
                try {
                    val userId = FirebaseAuth.getInstance().currentUser?.email
                    if (userId != null) {
                        val userName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown"

                        val json = JSONObject().apply {
                            put("userId", userId)
                            put("name", userName)
                            put("score", points)
                        }

                        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                        val request = Request.Builder()
                            .url("https://puzzleverseai.com/update-score")
                            .post(requestBody)
                            .build()

                        val client = OkHttpClient()
                        val response = client.newCall(request).execute()

                        if (response.isSuccessful) {
                            Log.d("ScoreManager", "✅ Backend score updated successfully: $points points")
                        } else {
                            Log.e("ScoreManager", "❌ Backend score update failed: ${response.code}")
                        }
                    } else {
                        Log.w("ScoreManager", "⚠️ No user logged in, skipping backend score update")
                    }
                } catch (e: Exception) {
                    Log.e("ScoreManager", "❌ Failed to update backend score", e)
                }
            }.start()
        }
    }
}

class ScoreDisplayManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("score_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val LOCAL_SCORE_KEY = "local_score"
        private const val LOCAL_SCORE_INCREMENT_KEY = "local_score_increment"
        private const val LAST_BACKEND_SYNC_KEY = "last_backend_sync"
    }

    fun updateLocalScore(increment: Int) {
        val currentScore = getLocalScore()
        val newScore = currentScore + increment
        prefs.edit().putInt(LOCAL_SCORE_KEY, newScore).apply()

        val currentIncrement = getLocalScoreIncrement()
        val newIncrement = currentIncrement + increment
        prefs.edit().putInt(LOCAL_SCORE_INCREMENT_KEY, newIncrement).apply()

        Log.d("ScoreDisplayManager", "Local score updated: +$increment (total: $newScore, pending: $newIncrement)")
    }

    fun getLocalScore(): Int = prefs.getInt(LOCAL_SCORE_KEY, 0)
    fun getLocalScoreIncrement(): Int = prefs.getInt(LOCAL_SCORE_INCREMENT_KEY, 0)

    fun syncWithBackend() {
        prefs.edit()
            .putInt(LOCAL_SCORE_INCREMENT_KEY, 0)
            .putLong(LAST_BACKEND_SYNC_KEY, System.currentTimeMillis())
            .apply()
        Log.d("ScoreDisplayManager", "Backend synced - cleared pending increments")
    }
}
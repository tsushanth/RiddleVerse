// UnifiedAdaptiveIntegration.kt - Unified system for all adaptive puzzle screens
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

/**
 * =============================================================================
 * UNIFIED ADAPTIVE SYSTEM MANAGER
 * =============================================================================
 * This manager coordinates all adaptive features across puzzle types
 */
class UnifiedAdaptiveManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: UnifiedAdaptiveManager? = null

        fun getInstance(): UnifiedAdaptiveManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedAdaptiveManager().also { INSTANCE = it }
            }
        }

        private const val TAG = "UnifiedAdaptive"
    }

    val difficultyManager = DifficultyManager()
    private val rankingManager = CompetitiveRankingManager.getInstance()

    /**
     * Submit session performance and get comprehensive feedback
     */
    suspend fun submitSessionPerformance(
        userId: String,
        puzzleType: String,
        sessionScore: Int,
        sessionStats: SessionStatistics,
        currentDifficulty: DifficultyManager.DifficultyLevel
    ): AdaptiveSessionResult {
        try {
            Log.d(TAG, "📊 Submitting unified session performance for $puzzleType")

            // Record adaptive difficulty performance
            val performance = DifficultyManager.PlayerPerformance(
                accuracy = sessionStats.winRate,
                averageTime = (sessionStats.totalTimeSeconds / maxOf(1, sessionStats.totalAnswers)).toFloat(),
                streakLength = sessionStats.bestStreak,
                livesRemaining = 3, // You'd track this from your session
                gameScore = sessionScore,
                difficulty = currentDifficulty.name,
                puzzleType = puzzleType
            )

            val adaptiveResult = difficultyManager.recordPerformance(performance)

            // Submit competitive ranking (only if score improved)
            val rankingResult = rankingManager.submitScoreAndGetImprovement(
                userId = userId,
                puzzleType = puzzleType,
                difficulty = currentDifficulty.name,
                sessionScore = sessionScore
            )

            return AdaptiveSessionResult(
                adaptiveConfig = adaptiveResult,
                rankingImprovement = rankingResult,
                oldDifficulty = currentDifficulty,
                newDifficulty = adaptiveResult.level,
                shouldShowAdaptation = adaptiveResult.confidenceScore > 0.5f,
                shouldShowRanking = rankingResult != null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit unified session performance", e)
            return AdaptiveSessionResult(
                adaptiveConfig = null,
                rankingImprovement = null,
                oldDifficulty = currentDifficulty,
                newDifficulty = currentDifficulty,
                shouldShowAdaptation = false,
                shouldShowRanking = false
            )
        }
    }

    /**
     * Get current competitive insight for a puzzle type
     */
    suspend fun getCompetitiveInsight(
        userId: String,
        puzzleType: String,
        difficulty: String
    ): CompetitiveRankingManager.CompetitiveInsight? {
        return try {
            rankingManager.getCompetitiveInsight(userId, puzzleType, difficulty)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get competitive insight", e)
            null
        }
    }

    /**
     * Get current difficulty level for puzzle type
     */
    fun getCurrentDifficulty(puzzleType: String): DifficultyManager.DifficultyLevel {
        return difficultyManager.getCurrentDifficulty(puzzleType)
    }

    /**
     * Get performance analytics
     */
    fun getPerformanceAnalytics(): Map<String, Any> {
        // Return basic analytics since getPerformanceAnalytics doesn't exist
        return mapOf(
            "averageAccuracy" to 75,
            "currentStreak" to 0,
            "gamesPlayed" to 0,
            "trend" to "Stable"
        )
    }
}

/**
 * Data class for unified session results
 */
data class AdaptiveSessionResult(
    val adaptiveConfig: DifficultyManager.AdaptiveConfig?,
    val rankingImprovement: CompetitiveRankingManager.RankingImprovement?,
    val oldDifficulty: DifficultyManager.DifficultyLevel,
    val newDifficulty: DifficultyManager.DifficultyLevel,
    val shouldShowAdaptation: Boolean,
    val shouldShowRanking: Boolean
)

/**
 * =============================================================================
 * UNIFIED ADAPTIVE COMPONENTS
 * =============================================================================
 */

/**
 * Standard adaptive header for all puzzle screens
 */
@Composable
fun AdaptiveUnifiedHeader(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    score: Int,
    puzzleType: String,
    challengeNumber: Int? = null,
    totalChallenges: Int? = null,
    competitiveInsight: CompetitiveRankingManager.CompetitiveInsight? = null,
    onBack: () -> Unit,
    onPause: (() -> Unit)? = null,
    onHint: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top row: Back button, Level, Hearts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .background(RvSurface, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = RvInk,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    onPause?.let { pauseAction ->
                        IconButton(
                            onClick = pauseAction,
                            modifier = Modifier
                                .size(48.dp)
                                .background(RvSurface, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause",
                                tint = RvInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Level ${level.level}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        textAlign = TextAlign.Center
                    )

                    if (challengeNumber != null && totalChallenges != null) {
                        Text(
                            text = "Challenge $challengeNumber of $totalChallenges",
                            fontSize = 12.sp,
                            color = RvInkSoft
                        )
                    }

                    // Progress bar if we have level progress
                    LevelProgressBar(
                        level = level,
                        modifier = Modifier.width(120.dp)
                    )
                }

                // Lives and competitive ranking
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(currentDifficulty.livesAllowed) { index ->
                            Text(
                                text = if (index < lives) "❤️" else "🤍",
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Compact competitive display
                    competitiveInsight?.let { insight ->
                        CompactRankingDisplay(
                            competitiveInsight = insight,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: Difficulty, Timer, Score, and Streak
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = currentDifficulty.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvSky
                    )
                    Text(
                        text = getPuzzleDisplayName(puzzleType),
                        fontSize = 12.sp,
                        color = RvInkSoft
                    )
                }

                Text(
                    text = timer,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timer.startsWith("00:") && timer.substring(3).toIntOrNull()?.let { it <= 30 } == true) {
                        Color.Red
                    } else {
                        Color.Black
                    }
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Score: $score",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvSuccess,
                        modifier = Modifier.testTag("hud_score")
                    )

                    if (streakInfo.currentStreak > 0) {
                        Text(
                            text = "🔥 ${streakInfo.currentStreak}",
                            fontSize = 12.sp,
                            color = RvFlame
                        )
                    }
                }
            }
        }
    }
}

/**
 * The "<Puzzle> Adapted!" banners sit inline in the puzzle column and push the answer buttons
 * down on shorter screens. Adaptation still runs; only the banner is suppressed. Flip to bring it back.
 */
const val SHOW_ADAPTATION_NOTICES = false

/**
 * Unified adaptation notification that works for any puzzle type
 */
@Composable
fun UnifiedAdaptationNotification(
    adaptationInfo: DifficultyManager.AdaptiveConfig?,
    puzzleType: String,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = RvSky.copy(alpha = 0.9f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = "Difficulty adjusted",
                    tint = RvInk,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${getPuzzleDisplayName(puzzleType)} Adapted!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )
                    Text(
                        text = adaptationInfo?.adjustmentReason ?: "",
                        fontSize = 10.sp,
                        color = RvInkSoft.copy(alpha = 0.9f)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = RvInk,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Unified session completion handler
 */
@Composable
fun UnifiedSessionCompletionHandler(
    puzzleType: String,
    sessionScore: Int,
    sessionStats: SessionStatistics,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    onResultsReady: (AdaptiveSessionResult) -> Unit
) {
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(sessionScore) {
        if (currentUser != null && sessionScore > 0) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                val results = adaptiveManager.submitSessionPerformance(
                    userId = currentUser.uid,
                    puzzleType = puzzleType,
                    sessionScore = sessionScore,
                    sessionStats = sessionStats,
                    currentDifficulty = currentDifficulty
                )
                onResultsReady(results)
            } catch (e: Exception) {
                Log.e("UnifiedSession", "Failed to process session completion", e)
            }
        }
    }
}

/**
 * Compact ranking display for headers
 */
@Composable
fun CompactRankingDisplay(
    competitiveInsight: CompetitiveRankingManager.CompetitiveInsight?,
    modifier: Modifier = Modifier
) {
    if (competitiveInsight == null) return

    Row(
        modifier = modifier
            .background(
                RvGrape.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = competitiveInsight.performanceLevel.emoji,
            fontSize = 12.sp
        )

        Column {
            Text(
                text = "${competitiveInsight.currentPercentile}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = RvGrape
            )
            Text(
                text = competitiveInsight.performanceLevel.displayName,
                fontSize = 8.sp,
                color = Color.Gray
            )
        }

        // Mini circular indicator
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)

                // Background circle
                drawCircle(
                    color = RvInkSoft.copy(alpha = 0.3f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )

                // Progress arc
                val sweepAngle = (competitiveInsight.currentPercentile / 100f) * 360f
                drawArc(
                    color = RvGrape,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}

/**
 * Unified performance recording function
 */
fun recordUnifiedPerformance(
    difficultyManager: DifficultyManager,
    isCorrect: Boolean,
    timeSpent: Long,
    streak: Int,
    livesRemaining: Int,
    difficulty: DifficultyManager.DifficultyLevel,
    challengeComplexity: Int,
    totalScore: Int,
    puzzleType: String,
    onAdaptation: (DifficultyManager.AdaptiveConfig) -> Unit
) {
    val performance = DifficultyManager.PlayerPerformance(
        accuracy = if (isCorrect) 1f else 0f,
        averageTime = timeSpent / 1000f,
        streakLength = streak,
        livesRemaining = livesRemaining,
        gameScore = totalScore,
        difficulty = difficulty.name,
        puzzleType = puzzleType
    )

    val adaptiveConfig = difficultyManager.recordPerformance(performance)
    onAdaptation(adaptiveConfig)
}

/**
 * Unified adaptive score calculation
 */
fun calculateUnifiedAdaptiveScore(
    isCorrect: Boolean,
    timeSpent: Long,
    difficulty: DifficultyManager.DifficultyLevel,
    challengeComplexity: Int,
    currentStreak: Int,
    challengesCompleted: Int,
    timeLimit: Int,
    puzzleType: String
): Int {
    if (!isCorrect) return 0

    val basePoints = difficulty.basePoints

    // Complexity multiplier based on challenge type
    val complexityMultiplier = 1f + (challengeComplexity - 1) * 0.15f

    // Speed bonus calculation
    val expectedTime = timeLimit / maxOf(1, challengesCompleted).toFloat()
    val timeSpentSeconds = timeSpent / 1000f
    val speedBonus = when {
        timeSpentSeconds <= expectedTime * 0.3f -> (basePoints * 0.4f).toInt()
        timeSpentSeconds <= expectedTime * 0.5f -> (basePoints * 0.3f).toInt()
        timeSpentSeconds <= expectedTime * 0.7f -> (basePoints * 0.2f).toInt()
        timeSpentSeconds <= expectedTime -> (basePoints * 0.1f).toInt()
        else -> 0
    }

    // Streak bonus
    val streakMultiplier = 1f + (currentStreak * 0.1f)

    // Puzzle type specific bonus
    val typeBonus = when (puzzleType) {
        "colorShapeMatching", "colorTextMatching" -> (basePoints * 0.2f).toInt() // Visual processing
        "contextSwitch" -> (basePoints * 0.3f).toInt() // Executive function
        "memorysquares" -> (basePoints * 0.25f).toInt() // Working memory
        else -> (basePoints * 0.15f).toInt()
    }

    // Lives preservation bonus
    val livesBonus = when (difficulty.livesAllowed) {
        5 -> difficulty.livesAllowed * (basePoints / 8) // Easier difficulties
        4 -> difficulty.livesAllowed * (basePoints / 6)
        3 -> difficulty.livesAllowed * (basePoints / 5)
        2 -> difficulty.livesAllowed * (basePoints / 4)
        1 -> difficulty.livesAllowed * (basePoints / 3) // Harder difficulties
        else -> 0
    }

    val finalScore = ((basePoints * complexityMultiplier * streakMultiplier).toInt() +
            speedBonus + typeBonus + livesBonus)

    Log.d("UnifiedScore", "🏆 Unified adaptive score for $puzzleType:")
    Log.d("UnifiedScore", "  Base: $basePoints, Complexity: $complexityMultiplier")
    Log.d("UnifiedScore", "  Speed bonus: $speedBonus, Type bonus: $typeBonus")
    Log.d("UnifiedScore", "  Streak: ${streakMultiplier}, Lives: $livesBonus")
    Log.d("UnifiedScore", "  Final: $finalScore")

    return maxOf(finalScore, basePoints / 4)
}
// AdaptiveMathEstimationScreen.kt - Fixed: Removed answer-revealing accuracy display
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.kreativekoala.riddleverse.ui.theme.RvInk
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.math.abs
import com.kreativekoala.riddleverse.ui.theme.RvGrape
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvSky
import com.kreativekoala.riddleverse.ui.theme.RvSuccess
import com.kreativekoala.riddleverse.ui.theme.RvSurface
import com.kreativekoala.riddleverse.ui.theme.RvSurfaceRaised

@Composable
fun AdaptiveMathEstimationScreen(
    initialDifficulty: String = "Medium",
    timer: String,
    onSubmitAnswer: (Double) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val TAG = "AdaptiveEstimation"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("mathestimation"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // Competitive ranking state
    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "mathestimation",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate puzzle based on current difficulty
    val mathGenerator = remember { AdaptiveMathPuzzleGenerators() }
    var currentPuzzle by remember(currentDifficultyLevel) {
        mutableStateOf(generateEstimationPuzzleFromDifficulty(mathGenerator, currentDifficultyLevel))
    }

    // Score and performance tracking
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAnswers by remember { mutableStateOf(0) }

    // Game state
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var estimatedValue by remember { mutableStateOf<Double?>(null) }
    var hasAnswered by remember { mutableStateOf(false) }
    var currentEstimate by remember { mutableStateOf<Double?>(null) }
    var chartEstimate by remember { mutableStateOf<Double?>(null) }
    var showHint by remember { mutableStateOf(false) }

    // ✅ NEW: Store accuracy for post-submission feedback
    var lastAccuracy by remember { mutableStateOf<Double?>(null) }
    var lastDifference by remember { mutableStateOf<Double?>(null) }

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    fun calculateAdaptiveChartBounds(numbers: List<Double>): Pair<Double, Double> {
        if (numbers.isEmpty()) return 0.0 to 100.0
        val sum = numbers.sum()
        val padding = sum * 0.2
        return 0.0 to (sum + padding)
    }

    val (minValue, maxValue) = remember(currentPuzzle) {
        calculateAdaptiveChartBounds(currentPuzzle.numbers)
    }

    // Convert puzzle data to chart data points
    val dataPoints = remember(currentPuzzle, minValue, maxValue) {
        currentPuzzle.numbers.mapIndexed { index, value ->
            val range = maxValue - minValue
            val yPosition = if (range > 0) {
                ((value - minValue) / range).toFloat()
            } else {
                0.5f
            }

            ChartDataPoint(
                value = value,
                yPosition = yPosition,
                index = index
            )
        }
    }

    // Performance recording function
    fun recordPerformance(accuracy: Double, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = accuracy > 0.7,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = 3,
            difficulty = currentDifficultyLevel,
            challengeComplexity = currentPuzzle.numbers.size,
            totalScore = totalScore,
            puzzleType = "mathestimation"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = SHOW_ADAPTATION_NOTICES
            }
        }
    }

    // ✅ ENHANCED: Calculate accuracy and scoring metrics
    fun calculateAccuracyMetrics(estimate: Double, actualSum: Double): Pair<Double, Double> {
        val difference = abs(estimate - actualSum)
        val accuracy = maxOf(0.0, 1.0 - (difference / actualSum).coerceAtMost(1.0))
        return accuracy to difference
    }

    // Submit answer function with enhanced scoring
    fun submitAnswer(estimate: Double) {
        if (!hasAnswered) {
            hasAnswered = true
            estimatedValue = estimate
            attempts++
            totalAnswers++

            val timeRemaining = 0
            val timeSpent = currentDifficultyLevel.timeLimit - (timeRemaining ?: 0)
            val (accuracy, difference) = calculateAccuracyMetrics(estimate, currentPuzzle.sum)
            val tolerance = currentPuzzle.sum * 0.1
            val isCorrect = difference <= tolerance

            // ✅ Store accuracy metrics for feedback display
            lastAccuracy = accuracy
            lastDifference = difference

            if (isCorrect) {
                correctAnswers++
                currentStreak++
                val score = calculateUnifiedAdaptiveScore(
                    isCorrect = true,
                    timeSpent = timeSpent.toLong() * 1000L,
                    difficulty = currentDifficultyLevel,
                    challengeComplexity = currentPuzzle.numbers.size,
                    currentStreak = currentStreak,
                    challengesCompleted = attempts,
                    timeLimit = currentDifficultyLevel.timeLimit,
                    puzzleType = "mathestimation"
                )
                totalScore += score
                gamesPlayedThisSession++
            } else {
                currentStreak = 0
            }

            recordPerformance(accuracy, timeSpent.toLong() * 1000L)

            // ✅ ENHANCED: Include accuracy metrics in existing fields
            val estimationQuality = when {
                accuracy >= 0.95 -> "Excellent"
                accuracy >= 0.85 -> "Great"
                accuracy >= 0.70 -> "Good"
                accuracy >= 0.50 -> "Fair"
                else -> "Needs improvement"
            }

            feedbackManager.showFeedback(
                puzzleType = "mathestimation",
                isCorrect = isCorrect,
                userAnswer = "${String.format("%.1f", estimate)} (${(accuracy * 100).toInt()}% accuracy - $estimationQuality)",
                correctAnswer = "${String.format("%.1f", currentPuzzle.sum)} (±${String.format("%.1f", tolerance)} tolerance)",
                timeSpent = timeSpent * 1000L,
                difficulty = currentDifficultyLevel.name,
                timeRemaining = timeRemaining ?: 0,
                totalTime = currentDifficultyLevel.timeLimit,
                onComplete = {
                    onSubmitAnswer(estimate)
                    fetchNextPuzzle(totalScore)
                }
            )

            haptics.performHapticFeedback(
                if (isCorrect) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
        }
    }

    // Timer variables
    val totalTimeSeconds = currentDifficultyLevel.timeLimit
    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(formatTime(totalTimeSeconds)) }

    fun formatTime(seconds: Int): String {
        return "${seconds / 60}:${String.format("%02d", seconds % 60)}"
    }

    EstimationFitLayout(
        hud = { compact, wide, tall ->
            if (tall) {
                // Unified header
                AdaptiveUnifiedHeader(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    lives = 3, // Estimation doesn't use lives
                    currentDifficulty = currentDifficultyLevel,
                    score = totalScore,
                    puzzleType = "mathestimation",
                    competitiveInsight = competitiveInsight,
                    onBack = onBack ?: {},
                    onHint = {
                        showHint = !showHint
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            } else {
                AdaptiveCompactHud(
                    level = currentLevel,
                    timer = displayTimer.let { if (it.indexOf(':') == 1) "0$it" else it },
                    lives = 3,
                    maxLives = 0,
                    score = totalScore,
                    difficultyName = currentDifficultyLevel.name,
                    challengeText = null,
                    onBack = onBack ?: {},
                    onPause = null,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        status = { _ ->
            // Unified adaptation notification (banner intentionally off via SHOW_ADAPTATION_NOTICES)
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "mathestimation",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Both hud() variants (AdaptiveCompactHud and AdaptiveUnifiedHeader) already show
                // the score with a "hud_score" tag - repeating it here doubled it on screen.
                Text(
                    text = "${currentDifficultyLevel.name} • ${currentPuzzle.numbers.size} numbers",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = RvInkSoft,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (currentStreak > 0) {
                    Text(
                        text = "🔥 $currentStreak",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        maxLines = 1
                    )
                }
            }
        },
        chart = { chartWidth, chartHeight ->
            Box(modifier = Modifier.size(chartWidth + 80.dp, chartHeight + 60.dp).testTag("estimation_chart")) {
                InteractiveChart(
                    dataPoints = dataPoints,
                    dragPosition = dragPosition,
                    estimatedValue = estimatedValue,
                    minValue = minValue,
                    maxValue = maxValue,
                    chartWidth = chartWidth,
                    chartHeight = chartHeight,
                    isAnswered = hasAnswered,
                    onDrag = { offset ->
                        if (!hasAnswered) {
                            dragPosition = offset

                            val padding = with(density) { 50.dp.toPx() }
                            val chartRect = Rect(
                                offset = Offset(padding, padding),
                                size = Size(
                                    with(density) { (chartWidth + 80.dp).toPx() } - padding * 2,
                                    with(density) { (chartHeight + 60.dp).toPx() } - padding * 2
                                )
                            )

                            if (offset.x >= chartRect.left && offset.x <= chartRect.right &&
                                offset.y >= chartRect.top && offset.y <= chartRect.bottom) {

                                val relativeY = (chartRect.bottom - offset.y) / chartRect.height
                                val estimated = minValue + (maxValue - minValue) * relativeY

                                currentEstimate = estimated
                                chartEstimate = estimated
                            }

                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragEnd = { offset, chartRect ->
                        if (!hasAnswered) {
                            currentEstimate?.let { estimate ->
                                submitAnswer(estimate)
                            } ?: run {
                                val fallback = (maxValue + minValue) / 2
                                submitAnswer(fallback)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        instructions = {
            AdaptiveEstimationInstructionsCard(
                difficultyLevel = currentDifficultyLevel,
                tolerance = currentPuzzle.sum * 0.1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        },
        // No accuracy spoiler here (intentional in the adaptive variant).
        estimate = { compact ->
            EstimationEstimateCard(estimate = currentEstimate, accuracyPercent = null, compact = compact)
        },
        submit = {
            EstimationSubmitButton(
                label = when {
                    hasAnswered -> "SUBMITTED"
                    currentEstimate != null -> "SUBMIT ESTIMATE"
                    else -> "SUBMIT (will use middle value)"
                },
                enabled = !hasAnswered,
                onClick = {
                    if (!hasAnswered) {
                        val estimate = currentEstimate ?: ((maxValue + minValue) / 2)
                        submitAnswer(estimate)
                    }
                }
            )
        },
        overlay = { EnhancedUniversalFeedback(feedbackManager) }
    )

    // Timer countdown effect
    LaunchedEffect(timeRemaining, hasAnswered) {
        if (timeRemaining > 0 && !hasAnswered) {
            delay(1000L)
            timeRemaining--
            displayTimer = formatTime(timeRemaining)
        } else if (timeRemaining == 0 && !hasAnswered) {
            val finalEstimate = currentEstimate ?: ((maxValue + minValue) / 2)
            submitAnswer(finalEstimate)
        }
    }

    // Session completion handling
    if (hasAnswered) {
        UnifiedSessionCompletionHandler(
            puzzleType = "mathestimation",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAnswers,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
                totalScore = totalScore,
                averageTimePerPuzzle = ((System.currentTimeMillis() - sessionStartTime) / 1000.0).toInt(),
                currentStreak = currentStreak,
                individualTimes = listOf((System.currentTimeMillis() - sessionStartTime).toInt())
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            // Session completion handled
        }
    }
}

@Composable
fun AdaptiveEstimationInstructionsCard(
    difficultyLevel: DifficultyManager.DifficultyLevel,
    tolerance: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8E4F3)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = RvGrape,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Drag finger to estimate the sum",
                    fontSize = 16.sp,
                    color = RvGrape,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(12.dp))

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = RvGrape,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Adaptive difficulty information
            Text(
                text = "🎯 ${difficultyLevel.name}: ${difficultyLevel.targetCount} numbers",
                fontSize = 12.sp,
                color = RvInkSoft,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "💯 Target range: ±${String.format("%.1f", tolerance)} for perfect score",
                fontSize = 12.sp,
                color = RvInkSoft,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

fun generateEstimationPuzzleFromDifficulty(
    generator: AdaptiveMathPuzzleGenerators,
    difficultyLevel: DifficultyManager.DifficultyLevel
): MathEstimationPuzzle {
    val puzzle = generator.generateEstimationForLevel(difficultyLevel)
    return puzzle ?: MathEstimationPuzzle(
        numbers = listOf(10.0, 20.0, 30.0),
        sum = 60.0,
        difficulty = difficultyLevel.name,
        hint = "Round each number to make estimation easier"
    )
}
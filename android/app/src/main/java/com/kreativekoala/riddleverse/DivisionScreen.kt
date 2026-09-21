// AdaptiveDivisionPuzzleScreen.kt - Enhanced with adaptive difficulty
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvSurface

data class ValueButton(
    val value: Int,
    var count: Int,
    val maxCount: Int
) {
    fun reset() = copy(count = 0)
}

@Composable
fun DivisionPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String,
    onSubmitAnswer: (Int) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val TAG = "AdaptiveDivision"
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember { mutableStateOf(difficultyManager.getCurrentDifficulty(
        puzzleType = "division"
    )) }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // Generate division puzzle based on current difficulty
    val divisionGenerator = remember { AdaptiveDivisionPuzzleGenerator() }
    var currentPuzzle by remember(currentDifficultyLevel) {
        mutableStateOf(generateDivisionPuzzleFromDifficulty(divisionGenerator, currentDifficultyLevel))
    }

    // Score and performance tracking
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // Game state
    var valueButtons by remember(currentDifficultyLevel) {
        mutableStateOf(generateValueButtonsForDifficulty(currentDifficultyLevel))
    }
    var currentTotal by remember { mutableStateOf(0) }
    var isAnswered by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            currentPuzzle = generateDivisionPuzzleFromDifficulty(divisionGenerator, currentDifficultyLevel)
            valueButtons = generateValueButtonsForDifficulty(currentDifficultyLevel)
            isAnswered = false
            currentTotal = 0
            currentHearts = currentDifficultyLevel.livesAllowed
            gameStartTime = System.currentTimeMillis()
        }
    }

    // Update current total when value buttons change
    LaunchedEffect(valueButtons) {
        currentTotal = valueButtons.sumOf { it.value * it.count }
    }

    // Format time helper
    fun formatTime(seconds: Int): String {
        return "${seconds / 60}:${String.format("%02d", seconds % 60)}"
    }

    // Timer calculation and countdown
    val totalTimeSeconds = currentDifficultyLevel.timeLimit
    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(formatTime(totalTimeSeconds)) }

    // Enhanced score calculation with difficulty-based scoring
    fun calculateAdaptiveDivisionScore(
        isCorrect: Boolean,
        accuracy: Double,
        timeSpent: Int,
        attemptNumber: Int,
        difficulty: DifficultyManager.DifficultyLevel,
        puzzleComplexity: Int, // dividend size
        currentStreak: Int
    ): Int {
        if (!isCorrect) return 0

        val basePoints = difficulty.basePoints

        // Division complexity multiplier based on dividend size
        val complexityMultiplier = when {
            puzzleComplexity >= 1000 -> 2.0f    // Very large numbers
            puzzleComplexity >= 500 -> 1.7f     // Large numbers
            puzzleComplexity >= 100 -> 1.4f     // Medium numbers
            puzzleComplexity >= 50 -> 1.2f      // Small numbers
            else -> 1.0f                        // Very small numbers
        }

        // Mental math bonus for efficient value combinations
        val totalButtonsUsed = valueButtons.sumOf { it.count }
        val efficiencyBonus = when {
            totalButtonsUsed <= 3 -> (basePoints * 0.3f).toInt() // Very efficient
            totalButtonsUsed <= 5 -> (basePoints * 0.2f).toInt() // Efficient
            totalButtonsUsed <= 7 -> (basePoints * 0.1f).toInt() // Moderate
            else -> 0 // Inefficient
        }

        // Streak bonus
        val streakMultiplier = 1f + (currentStreak * 0.1f)

        // Perfect accuracy bonus
        val accuracyBonus = when {
            accuracy >= 0.99 -> (basePoints * 0.4f).toInt()
            accuracy >= 0.95 -> (basePoints * 0.2f).toInt()
            else -> 0
        }

        // Speed bonus
        val speedBonus = when {
            timeSpent <= totalTimeSeconds * 0.3 -> (basePoints * 0.5f).toInt()
            timeSpent <= totalTimeSeconds * 0.5 -> (basePoints * 0.3f).toInt()
            timeSpent <= totalTimeSeconds * 0.7 -> (basePoints * 0.1f).toInt()
            else -> 0
        }

        // Lives preservation bonus
        val livesBonus = currentHearts * (basePoints / 6)

        // Attempt penalty
        val attemptPenalty = when (attemptNumber) {
            1 -> 0
            2 -> (basePoints * 0.15f).toInt()
            3 -> (basePoints * 0.3f).toInt()
            else -> (basePoints * 0.5f).toInt()
        }

        val finalScore = ((basePoints * complexityMultiplier * streakMultiplier).toInt() +
                efficiencyBonus + accuracyBonus + speedBonus + livesBonus - attemptPenalty)

        return maxOf(finalScore, basePoints / 4)
    }

    fun recordGamePerformance(
        difficultyManager: DifficultyManager,
        isCorrect: Boolean,
        timeSpent: Long,
        streak: Int,
        livesRemaining: Int,
        difficulty: DifficultyManager.DifficultyLevel,
        puzzleComplexity: Int,
        onAdaptation: (DifficultyManager.AdaptiveConfig) -> Unit
    ) {
        val timeSpentSeconds = timeSpent / 1000f
        val expectedTimeForDifficulty = difficulty.timeLimit.toFloat()
        val performance = DifficultyManager.PlayerPerformance(
            accuracy = if (isCorrect) 1f else 0f,
            averageTime = timeSpent / 1000f,
            streakLength = streak,
            livesRemaining = livesRemaining,
            gameScore = totalScore,
            difficulty = difficulty.name,
            puzzleType = "division",
            timestamp = System.currentTimeMillis(),
            timeEfficiency = if (timeSpentSeconds > 0f) { // Fixed: Calculate efficiency
                (expectedTimeForDifficulty / timeSpentSeconds).coerceAtMost(2.0f)
            } else {
                1.0f
            }
        )

        val adaptiveConfig = difficultyManager.recordPerformance(performance)
        onAdaptation(adaptiveConfig)
    }

    // Submit answer function
    fun submitAnswer() {
        if (isAnswered || feedbackManager.isShowingFeedback) return

        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        isAnswered = true
        attempts++

        val timeSpent = totalTimeSeconds - timeRemaining
        val isCorrect = currentTotal == currentPuzzle.correctAnswer

        Log.d(TAG, "🎯 Answer check:")
        Log.d(TAG, "  Problem: ${currentPuzzle.dividend} ÷ ${currentPuzzle.divisor}")
        Log.d(TAG, "  Expected: ${currentPuzzle.correctAnswer}")
        Log.d(TAG, "  User answer: $currentTotal")
        Log.d(TAG, "  Result: $isCorrect")

        // Calculate accuracy and score
        val accuracy = if (currentPuzzle.correctAnswer != 0) {
            1.0 - (abs(currentTotal - currentPuzzle.correctAnswer).toDouble() / currentPuzzle.correctAnswer)
        } else {
            if (currentTotal == 0) 1.0 else 0.0
        }

        val newStreak = if (isCorrect) currentStreak + 1 else 0

        val attemptScore = calculateAdaptiveDivisionScore(
            isCorrect = isCorrect,
            accuracy = maxOf(0.0, accuracy),
            timeSpent = timeSpent,
            attemptNumber = attempts,
            difficulty = currentDifficultyLevel,
            puzzleComplexity = currentPuzzle.dividend,
            currentStreak = newStreak
        )

        if (isCorrect) {
            totalScore += attemptScore
            currentStreak = newStreak
            gamesPlayedThisSession++
            Log.d(TAG, "✅ Correct! Score: +$attemptScore, Total: $totalScore")
        } else {
            currentHearts = maxOf(0, currentHearts - 1)
            currentStreak = 0
            Log.d(TAG, "❌ Incorrect. Hearts remaining: $currentHearts")
        }

        // Record performance for adaptation
        recordGamePerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent.toLong() * 1000L,
            streak = newStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            puzzleComplexity = currentPuzzle.dividend
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = SHOW_ADAPTATION_NOTICES
            }
        }

        // Show feedback and advance
        feedbackManager.showFeedback(
            puzzleType = "division",
            isCorrect = isCorrect,
            userAnswer = currentTotal.toString(),
            correctAnswer = currentPuzzle.correctAnswer.toString(),
            timeSpent = timeSpent * 1000L,
            difficulty = currentDifficultyLevel.name,
            timeRemaining = timeRemaining,
            totalTime = totalTimeSeconds,
            onComplete = {
                Log.d(TAG, "🎊 Feedback completed")
                if (isCorrect) {
                    onSubmitAnswer(currentTotal)
                    fetchNextPuzzle(totalScore)
                } else {
                    if (currentHearts > 0) {
                        // Reset for retry
                        isAnswered = false
                        valueButtons = valueButtons.map { it.reset() }
                        currentTotal = 0
                    } else {
                        // Game over
                        onSubmitAnswer(currentTotal)
                        fetchNextPuzzle(totalScore)
                    }
                }
            }
        )
    }

    // Fit-to-screen: HUD on top, problem + steppers in the middle, total + SUBMIT pinned at the
    // bottom. Landscape / wide: problem and submit on the left, steppers on the right.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvSurface)
    ) {
        val wide = maxWidth > maxHeight || maxWidth >= 600.dp
        val tall = !wide && maxHeight >= 700.dp
        val compact = !tall
        val pad = if (compact) 8.dp else 16.dp

        val hud: @Composable () -> Unit = {
            if (tall) {
                AdaptiveDivisionTopGameBar(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    lives = currentHearts,
                    currentDifficulty = currentDifficultyLevel,
                    totalScore = totalScore,
                    onBack = onBack,
                    modifier = Modifier
                )
            } else {
                AdaptiveCompactHud(
                    level = currentLevel,
                    timer = displayTimer.let { if (it.indexOf(':') == 1) "0$it" else it },
                    lives = currentHearts,
                    maxLives = currentDifficultyLevel.livesAllowed,
                    score = totalScore,
                    difficultyName = currentDifficultyLevel.name,
                    challengeText = null,
                    onBack = onBack,
                    onPause = null
                )
            }
        }
        val problem: @Composable () -> Unit = {
            AdaptiveDivisionProblemDisplay(
                puzzle = currentPuzzle,
                difficultyLevel = currentDifficultyLevel,
                modifier = Modifier.fillMaxWidth().testTag("division_problem")
            )
        }
        val steppers: @Composable (Modifier) -> Unit = { m ->
            // Value input section
            AdaptiveValueInputSection(
                valueButtons = valueButtons,
                onIncrement = { index ->
                    if (!isAnswered && !feedbackManager.isShowingFeedback &&
                        valueButtons[index].count < valueButtons[index].maxCount) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        valueButtons = valueButtons.mapIndexed { i, button ->
                            if (i == index) button.copy(count = button.count + 1) else button
                        }
                    }
                },
                onDecrement = { index ->
                    if (!isAnswered && !feedbackManager.isShowingFeedback && valueButtons[index].count > 0) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        valueButtons = valueButtons.mapIndexed { i, button ->
                            if (i == index) button.copy(count = button.count - 1) else button
                        }
                    }
                },
                isDisabled = feedbackManager.isShowingFeedback,
                modifier = m
            )
        }
        val actionBar: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Current total (tapping it also submits, as before)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(RvSun)
                        .testTag("division_total"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$currentTotal",
                        fontSize = aCapSp(32f, 1.3f),
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        maxLines = 1
                    )
                }
                Button(
                    onClick = {
                        if (!isAnswered && !feedbackManager.isShowingFeedback) {
                            submitAnswer()
                        }
                    },
                    enabled = !isAnswered,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RvViolet,
                        contentColor = RvOnTone,
                        disabledContainerColor = RvDisabled,
                        disabledContentColor = RvInk
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1.3f).heightIn(min = 56.dp)
                ) {
                    Text(
                        text = "SUBMIT",
                        fontSize = aCapSp(18f, 1.3f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                }
            }
        }

        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(pad),
                horizontalArrangement = Arrangement.spacedBy(pad)
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(pad)
                ) {
                    hud()
                    Spacer(Modifier.weight(1f))
                    problem()
                    Spacer(Modifier.weight(1f))
                    actionBar()
                }
                steppers(Modifier.weight(1f).fillMaxHeight().widthIn(max = 480.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .widthIn(max = 640.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)
            ) {
                hud()
                problem()
                steppers(Modifier.fillMaxWidth().weight(1f))
                actionBar()
            }
        }

        EnhancedUniversalFeedback(feedbackManager)
    }


    // Timer countdown effect
    LaunchedEffect(timeRemaining, isAnswered) {
        if (timeRemaining > 0 && !isAnswered) {
            delay(1000L)
            timeRemaining--
            displayTimer = formatTime(timeRemaining)
        } else if (timeRemaining == 0 && !isAnswered) {
            // Time's up - auto-submit with current total
            Log.d(TAG, "⏰ Time's up! Auto-submitting with total: $currentTotal")
            submitAnswer()
        }
    }
}

@Composable
fun AdaptiveDivisionProblemDisplay(
    puzzle: DivisionPuzzle,
    difficultyLevel: DifficultyManager.DifficultyLevel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = puzzle.dividend.toString(),
            fontSize = aCapSp(56f, 1.2f),
            fontWeight = FontWeight.Bold,
            color = RvInk,
            maxLines = 1
        )

        Text(
            text = "DIVIDED BY ${puzzle.divisor}",
            fontSize = aCapSp(18f, 1.3f),
            fontWeight = FontWeight.Medium,
            color = RvInkSoft,
            letterSpacing = 1.sp,
            maxLines = 1
        )

        // Difficulty indicator
        Text(
            text = difficultyLevel.name.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = RvInkSoft,
            maxLines = 1
        )
    }
}

@Composable
fun AdaptiveValueInputSection(
    valueButtons: List<ValueButton>,
    onIncrement: (Int) -> Unit,
    onDecrement: (Int) -> Unit,
    isDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Each denomination gets an equal column (label, +, count, -); the parent decides the height.
    Row(
        modifier = modifier.testTag("division_steppers"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        valueButtons.forEachIndexed { index, button ->
            Box(modifier = Modifier.weight(1f).widthIn(max = 96.dp).fillMaxHeight()) {
                AdaptiveValueInputButton(
                    value = button.value,
                    count = button.count,
                    maxCount = button.maxCount,
                    onIncrement = { onIncrement(index) },
                    onDecrement = { onDecrement(index) },
                    isDisabled = isDisabled
                )
            }
        }
    }
}

@Composable
fun AdaptiveValueInputButton(
    value: Int,
    count: Int,
    maxCount: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    isDisabled: Boolean
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Height available for the three controls (label takes ~28dp, gaps 3 x 6dp).
        val avail = if (constraints.hasBoundedHeight) maxHeight else 180.dp
        val ctlH = ((avail - 28.dp - 18.dp) / 3).coerceIn(48.dp, 72.dp)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = value.toString(),
                fontSize = aCapSp(18f, 1.3f),
                fontWeight = FontWeight.Bold,
                color = RvInk,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            // Increment button (top)
            Button(
                onClick = onIncrement,
                enabled = count < maxCount && !isDisabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RvSun,
                    contentColor = RvInk,
                    disabledContainerColor = RvDisabled,
                    disabledContentColor = RvInkSoft
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(ctlH),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(text = "+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            // Count display
            Card(
                modifier = Modifier.fillMaxWidth().height(ctlH),
                colors = CardDefaults.cardColors(
                    containerColor = if (count > 0) RvSun.copy(alpha = 0.25f) else RvSurfaceRaised
                ),
                border = BorderStroke(if (count > 0) 3.dp else 2.dp, if (count > 0) RvSunEdge else RvInkSoft),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = aCapSp(20f, 1.3f),
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        maxLines = 1
                    )
                }
            }

            // Decrement button (bottom)
            Button(
                onClick = onDecrement,
                enabled = count > 0 && !isDisabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RvSun,
                    contentColor = RvInk,
                    disabledContainerColor = RvDisabled,
                    disabledContentColor = RvInkSoft
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(ctlH),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(text = "−", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AdaptiveDivisionTopGameBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    totalScore: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Back button and level
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = RvInk,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Level ${level.level}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )

                    // Adaptive difficulty indicator
                    Text(
                        text = currentDifficulty.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = RvInkSoft
                    )

                    // Level progress bar
                    LevelProgressBar(
                        level = level,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }

            // Center: Lives display with difficulty-based count
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(currentDifficulty.livesAllowed) { index ->
                        Text(
                            text = if (index < lives) "❤️" else "🤍",
                            fontSize = 16.sp
                        )
                    }
                }

                // Streak display
                if (streakInfo.currentStreak > 0) {
                    StreakDisplay(
                        streakInfo = streakInfo,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Right side: Timer and score
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = timer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true) {
                        Color.Red
                    } else {
                        RvInk
                    }
                )

                if (totalScore > 0) {
                    Text(
                        text = "Score: $totalScore",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// Data class for division puzzles
data class DivisionPuzzle(
    val dividend: Int,
    val divisor: Int,
    val correctAnswer: Int,
    val difficulty: String
)

// Adaptive division puzzle generator
class AdaptiveDivisionPuzzleGenerator {
    fun generateForLevel(difficultyLevel: DifficultyManager.DifficultyLevel): DivisionPuzzle {
        val (dividendRange, divisors) = when (difficultyLevel.name.lowercase()) {
            "tutorial" -> Pair(Pair(10, 50), listOf(2, 5, 10))
            "beginner" -> Pair(Pair(20, 100), listOf(2, 4, 5, 10))
            "easy" -> Pair(Pair(50, 200), listOf(2, 4, 5, 8, 10))
            "easy+" -> Pair(Pair(100, 300), listOf(3, 4, 6, 8, 9))
            "medium-" -> Pair(Pair(150, 400), listOf(3, 6, 7, 8, 9))
            "medium" -> Pair(Pair(200, 600), listOf(4, 6, 7, 8, 9, 12))
            "medium+" -> Pair(Pair(300, 800), listOf(6, 7, 8, 9, 11, 12))
            "hard-" -> Pair(Pair(400, 1000), listOf(7, 9, 11, 12, 13))
            "hard" -> Pair(Pair(500, 1200), listOf(8, 9, 11, 13, 14))
            "hard+" -> Pair(Pair(600, 1500), listOf(9, 11, 13, 14, 15))
            "expert-" -> Pair(Pair(800, 2000), listOf(11, 13, 15, 16, 17))
            "expert" -> Pair(Pair(1000, 2500), listOf(12, 14, 16, 17, 18))
            "expert+" -> Pair(Pair(1200, 3000), listOf(13, 15, 17, 19, 21))
            "master" -> Pair(Pair(1500, 4000), listOf(14, 16, 18, 19, 22))
            "grandmaster" -> Pair(Pair(2000, 5000), listOf(15, 17, 19, 23, 25))
            else -> Pair(Pair(200, 600), listOf(4, 6, 7, 8, 9, 12))
        }

        val divisor = divisors.random()
        val quotient = Random.nextInt(
            dividendRange.first / divisor,
            dividendRange.second / divisor + 1
        )
        val dividend = quotient * divisor

        return DivisionPuzzle(
            dividend = dividend,
            divisor = divisor,
            correctAnswer = quotient,
            difficulty = difficultyLevel.name
        )
    }
}

// Generate value buttons based on difficulty
fun generateValueButtonsForDifficulty(difficultyLevel: DifficultyManager.DifficultyLevel): List<ValueButton> {
    return when (difficultyLevel.name.lowercase()) {
        "tutorial", "beginner" -> listOf(
            ValueButton(value = 10, count = 0, maxCount = 15),
            ValueButton(value = 5, count = 0, maxCount = 10),
            ValueButton(value = 1, count = 0, maxCount = 20)
        )
        "easy", "easy+" -> listOf(
            ValueButton(value = 50, count = 0, maxCount = 8),
            ValueButton(value = 10, count = 0, maxCount = 20),
            ValueButton(value = 5, count = 0, maxCount = 15),
            ValueButton(value = 1, count = 0, maxCount = 30)
        )
        "medium-", "medium", "medium+" -> listOf(
            ValueButton(value = 100, count = 0, maxCount = 6),
            ValueButton(value = 50, count = 0, maxCount = 10),
            ValueButton(value = 10, count = 0, maxCount = 25),
            ValueButton(value = 1, count = 0, maxCount = 50)
        )
        "hard-", "hard", "hard+" -> listOf(
            ValueButton(value = 200, count = 0, maxCount = 8),
            ValueButton(value = 50, count = 0, maxCount = 15),
            ValueButton(value = 10, count = 0, maxCount = 30),
            ValueButton(value = 1, count = 0, maxCount = 50)
        )
        else -> listOf( // Expert, Master, Grandmaster
            ValueButton(value = 500, count = 0, maxCount = 6),
            ValueButton(value = 100, count = 0, maxCount = 15),
            ValueButton(value = 10, count = 0, maxCount = 40),
            ValueButton(value = 1, count = 0, maxCount = 50)
        )
    }
}

// Helper function to generate division puzzle from difficulty level
fun generateDivisionPuzzleFromDifficulty(
    generator: AdaptiveDivisionPuzzleGenerator,
    difficultyLevel: DifficultyManager.DifficultyLevel
): DivisionPuzzle {
    return generator.generateForLevel(difficultyLevel)
}
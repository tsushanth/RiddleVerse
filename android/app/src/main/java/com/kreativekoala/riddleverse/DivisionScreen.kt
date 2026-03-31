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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

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
                showAdaptationNotification = true
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Enhanced Top Bar with adaptive difficulty info
            AdaptiveDivisionTopGameBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer,
                lives = currentHearts,
                currentDifficulty = currentDifficultyLevel,
                totalScore = totalScore,
                onBack = onBack,
                modifier = Modifier.padding(16.dp)
            )

            // Adaptive difficulty notification
            AnimatedVisibility(
                visible = showAdaptationNotification,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4FC3F7).copy(alpha = 0.9f)
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
                            contentDescription = stringResource(R.string.difficulty_advanced),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.difficulty_advanced),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = adaptationInfo?.adjustmentReason ?: "",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        IconButton(
                            onClick = { showAdaptationNotification = false },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))

            // Division problem display
            AdaptiveDivisionProblemDisplay(
                puzzle = currentPuzzle,
                difficultyLevel = currentDifficultyLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
            )

            Spacer(modifier = Modifier.height(80.dp))

            // Answer circle with current total
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.22f),
                contentAlignment = Alignment.Center
            ) {
                // 3D Shadow effect
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .offset(x = 8.dp, y = 8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD4AC0D).copy(alpha = 0.3f))
                )

                // Main circle
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD700),
                                    Color(0xFFD4AC0D)
                                )
                            )
                        )
                        .shadow(8.dp, CircleShape)
                        .clickable {
                            if (!isAnswered && !feedbackManager.isShowingFeedback) {
                                submitAnswer()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$currentTotal",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "SUBMIT",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 40.dp)
            )
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = puzzle.dividend.toString(),
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )

        Text(
            text = "DIVIDED BY ${puzzle.divisor}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray,
            letterSpacing = 1.sp
        )

        // Difficulty indicator
        Text(
            text = difficultyLevel.name.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD4AC0D)
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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Value buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            valueButtons.forEachIndexed { index, button ->
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

        // Value labels row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            valueButtons.forEach { button ->
                Text(
                    text = button.value.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(60.dp)
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Increment button (top)
        Button(
            onClick = onIncrement,
            enabled = count < maxCount && !isDisabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (count < maxCount) Color(0xFFD4AC0D) else Color.Gray.copy(alpha = 0.3f),
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(width = 60.dp, height = 30.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "+",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Count display
        Card(
            modifier = Modifier.size(width = 60.dp, height = 40.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (count > 0) Color(0xFFD4AC0D).copy(alpha = 0.2f) else Color.Transparent
            ),
            border = BorderStroke(2.dp, Color(0xFFD4AC0D)),
            shape = RoundedCornerShape(4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
        }

        // Decrement button (bottom)
        Button(
            onClick = onDecrement,
            enabled = count > 0 && !isDisabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (count > 0) Color(0xFFD4AC0D) else Color.Gray.copy(alpha = 0.3f),
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(width = 60.dp, height = 30.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "−",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
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
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )

                Column {
                    Text(
                        text = "Level ${level.level}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )

                    // Adaptive difficulty indicator
                    Text(
                        text = currentDifficulty.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD4AC0D)
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
                        Color.Gray
                    }
                )

                if (totalScore > 0) {
                    Text(
                        text = "Score: $totalScore",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
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
// AdaptivePercentagePuzzleScreen.kt - Enhanced with adaptive difficulty
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun AdaptivePercentagePuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String,
    onSubmitAnswer: (Int) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember { mutableStateOf(difficultyManager.getCurrentDifficulty(
        puzzleType = "percentages"
    )) }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // Generate puzzle based on current difficulty
    val mathGenerator = remember { AdaptiveMathPuzzleGenerators() }
    var currentPuzzle by remember(currentDifficultyLevel) {
        mutableStateOf(generatePercentagePuzzleFromDifficulty(mathGenerator, currentDifficultyLevel))
    }

    // Score and performance tracking
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // Game state
    var currentInput by remember { mutableStateOf("") }
    var isAnswered by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            currentPuzzle = generatePercentagePuzzleFromDifficulty(mathGenerator, currentDifficultyLevel)
            isAnswered = false
            currentInput = ""
            currentHearts = currentDifficultyLevel.livesAllowed
            gameStartTime = System.currentTimeMillis()
        }
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
    fun calculateAdaptivePercentageScore(
        isCorrect: Boolean,
        accuracy: Double,
        timeSpent: Int,
        attemptNumber: Int,
        difficulty: DifficultyManager.DifficultyLevel,
        puzzleComplexity: Double, // percentage difficulty factor
        currentStreak: Int
    ): Int {
        if (!isCorrect) return 0

        val basePoints = difficulty.basePoints

        // Percentage complexity multiplier
        val complexityMultiplier = when {
            puzzleComplexity % 10 != 0.0 -> 1.8f // Non-round percentages (e.g., 18%, 23%)
            puzzleComplexity % 5 != 0.0 -> 1.5f  // Semi-round percentages (e.g., 15%, 35%)
            puzzleComplexity >= 50.0 -> 1.3f     // High percentages
            puzzleComplexity <= 10.0 -> 1.2f     // Low percentages
            else -> 1.0f                         // Standard percentages (20%, 25%, 30%)
        }

        // Mental math complexity bonus
        val mentalMathBonus = when {
            puzzleComplexity == 10.0 -> (basePoints * 0.1f).toInt() // Easy: divide by 10
            puzzleComplexity == 20.0 -> (basePoints * 0.15f).toInt() // Medium: divide by 5
            puzzleComplexity == 25.0 -> (basePoints * 0.2f).toInt() // Medium: divide by 4
            puzzleComplexity == 50.0 -> (basePoints * 0.1f).toInt() // Easy: divide by 2
            else -> (basePoints * 0.3f).toInt() // Hard: requires calculation
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
                mentalMathBonus + accuracyBonus + speedBonus + livesBonus - attemptPenalty)

        return maxOf(finalScore, basePoints / 4)
    }

    fun recordGamePerformance(
        difficultyManager: DifficultyManager,
        isCorrect: Boolean,
        timeSpent: Long,
        streak: Int,
        livesRemaining: Int,
        difficulty: DifficultyManager.DifficultyLevel,
        puzzleComplexity: Double,
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
            puzzleType = "percentages",
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF8E44AD),
                        Color(0xFF6B2C91),
                        Color(0xFF4A1F68)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Enhanced Top Bar with adaptive difficulty info
            AdaptivePercentageTopGameBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer,
                lives = currentHearts,
                currentDifficulty = currentDifficultyLevel,
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

            // Enhanced game info with adaptive metrics
            if (totalScore > 0 || attempts > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (totalScore > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.score_label).uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "$totalScore",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentDifficultyLevel.name.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Cyan
                            )
                            currentPuzzle?.let { puzzle ->
                                Text(
                                    text = "${puzzle.percentage}% calculation",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (currentStreak > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.streak_label).uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "🔥 $currentStreak",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF6F00)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            } else {
                Spacer(modifier = Modifier.height(40.dp))
            }

            // Title and percentage problem
            AdaptivePercentagePuzzleHeader(
                puzzle = currentPuzzle,
                difficultyLevel = currentDifficultyLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Input display
            InputDisplay(
                currentInput = currentInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Calculator interface
            CalculatorGrid(
                currentInput = currentInput,
                onNumberClick = { digit ->
                    if (!isAnswered && currentInput.length < 8) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentInput += digit
                    }
                },
                onClear = {
                    if (!isAnswered) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentInput = ""
                    }
                },
                onDecimal = {
                    // No decimal support for percentage answers - they should be whole numbers
                    Log.d("AdaptivePercentage", "🚫 Decimal not allowed - answers must be whole numbers")
                },
                onSubmit = {
                    if (!isAnswered && currentInput.isNotEmpty()) {
                        Log.d("AdaptivePercentage", "🔥 Submit clicked - input: $currentInput")
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        val answer = currentInput.toIntOrNull() ?: 0
                        isAnswered = true
                        attempts++

                        val timeSpent = totalTimeSeconds - timeRemaining
                        val isCorrect = answer == currentPuzzle?.answer?.toInt()

                        currentPuzzle?.let { puzzle ->
                            Log.d("AdaptivePercentage", "🎯 Answer check:")
                            Log.d("AdaptivePercentage", "  Problem: ${puzzle.percentage}% of ${puzzle.total}")
                            Log.d("AdaptivePercentage", "  Expected: ${puzzle.answer}")
                            Log.d("AdaptivePercentage", "  User answer: $answer")
                            Log.d("AdaptivePercentage", "  Result: $isCorrect")

                            // Calculate accuracy and score
                            val accuracy = if (puzzle.answer != 0.0) {
                                1.0 - (abs(answer - puzzle.answer) / puzzle.answer)
                            } else {
                                if (answer == 0) 1.0 else 0.0
                            }

                            val newStreak = if (isCorrect) currentStreak + 1 else 0

                            val attemptScore = calculateAdaptivePercentageScore(
                                isCorrect = isCorrect,
                                accuracy = maxOf(0.0, accuracy),
                                timeSpent = timeSpent,
                                attemptNumber = attempts,
                                difficulty = currentDifficultyLevel,
                                puzzleComplexity = puzzle.percentage.toDouble(),
                                currentStreak = newStreak
                            )

                            if (isCorrect) {
                                totalScore += attemptScore
                                currentStreak = newStreak
                                gamesPlayedThisSession++
                                Log.d("AdaptivePercentage", "✅ Correct! Score: +$attemptScore, Total: $totalScore")
                            } else {
                                currentHearts = maxOf(0, currentHearts - 1)
                                currentStreak = 0
                                Log.d("AdaptivePercentage", "❌ Incorrect. Hearts remaining: $currentHearts")
                            }

                            // Record performance for adaptation
                            recordGamePerformance(
                                difficultyManager = difficultyManager,
                                isCorrect = isCorrect,
                                timeSpent = timeSpent.toLong() * 1000L,
                                streak = newStreak,
                                livesRemaining = currentHearts,
                                difficulty = currentDifficultyLevel,
                                puzzleComplexity = puzzle.percentage.toDouble()
                            ) { config ->
                                adaptationInfo = config
                                if (config.confidenceScore > 0.5f) {
                                    currentDifficultyLevel = config.level
                                    showAdaptationNotification = true
                                }
                            }

                            // Show feedback and advance
                            feedbackManager.showFeedback(
                                puzzleType = "percentage",
                                isCorrect = isCorrect,
                                userAnswer = answer.toString(),
                                correctAnswer = puzzle.answer.toInt().toString(),
                                timeSpent = timeSpent * 1000L,
                                difficulty = currentDifficultyLevel.name,
                                timeRemaining = timeRemaining,
                                totalTime = totalTimeSeconds,
                                onComplete = {
                                    Log.d("AdaptivePercentage", "🎊 Feedback completed")
                                    onSubmitAnswer(answer)
                                    fetchNextPuzzle(totalScore)
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.padding(20.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))
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
            // Time's up - auto-submit with current input or 0
            Log.d("AdaptivePercentage", "⏰ Time's up! Auto-submitting with input: '$currentInput'")
            val finalAnswer = currentInput.toIntOrNull() ?: 0
            isAnswered = true
            attempts++

            val timeSpent = totalTimeSeconds - timeRemaining
            val isCorrect = finalAnswer == currentPuzzle?.answer?.toInt()

            if (!isCorrect) {
                currentHearts = maxOf(0, currentHearts - 1)
                currentStreak = 0
            }

            currentPuzzle?.let { puzzle ->
                // Record performance for adaptation
                recordGamePerformance(
                    difficultyManager = difficultyManager,
                    isCorrect = isCorrect,
                    timeSpent = timeSpent.toLong() * 1000L,
                    streak = if (isCorrect) currentStreak + 1 else 0,
                    livesRemaining = currentHearts,
                    difficulty = currentDifficultyLevel,
                    puzzleComplexity = puzzle.percentage.toDouble()
                ) { config ->
                    adaptationInfo = config
                    if (config.confidenceScore > 0.5f) {
                        currentDifficultyLevel = config.level
                        showAdaptationNotification = true
                    }
                }

                // Show feedback and then advance
                feedbackManager.showFeedback(
                    puzzleType = "percentage",
                    isCorrect = isCorrect,
                    userAnswer = finalAnswer.toString(),
                    correctAnswer = puzzle.answer.toInt().toString(),
                    timeSpent = timeSpent * 1000L,
                    difficulty = currentDifficultyLevel.name,
                    timeRemaining = timeRemaining,
                    totalTime = totalTimeSeconds,
                    onComplete = {
                        Log.d("AdaptivePercentage", "🎊 Timeout feedback completed - advancing to next puzzle")
                        onSubmitAnswer(finalAnswer)
                        fetchNextPuzzle(totalScore)
                    }
                )
            }
        }
    }
}

@Composable
fun AdaptivePercentagePuzzleHeader(
    puzzle: PercentagePuzzle?,
    difficultyLevel: DifficultyManager.DifficultyLevel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CALCULATE THE PERCENTAGE",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            letterSpacing = 1.sp
        )

        Text(
            text = difficultyLevel.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Cyan,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        puzzle?.let { puzzle ->
            // Percentage display
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Percentage circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFE8A44A).copy(alpha = 0.8f),
                                    Color(0xFFD2691E).copy(alpha = 0.6f),
                                    Color(0xFFCC8533).copy(alpha = 0.4f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${puzzle.percentage}%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "OF",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = puzzle.total.toInt().toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "=",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Hint display for lower difficulties
            if (puzzle.hint.isNotEmpty() && difficultyLevel.name.contains("easy", ignoreCase = true) ||
                difficultyLevel.name.contains("tutorial", ignoreCase = true) ||
                difficultyLevel.name.contains("beginner", ignoreCase = true)) {

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "💡 ${puzzle.hint}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun AdaptivePercentageTopGameBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.statusBarsPadding().fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left side: Back button, pause, and level
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
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    text = "Level ${level.level}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Adaptive difficulty indicator
                Text(
                    text = currentDifficulty.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Cyan
                )

                // Level progress bar
                LevelProgressBar(
                    level = level,
                    modifier = Modifier.width(120.dp)
                )
            }
        }

        // Center: Lives display with difficulty-based count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(currentDifficulty.livesAllowed) { index ->
                Text(
                    text = if (index < lives) "❤️" else "🤍",
                    fontSize = 16.sp
                )
            }
        }

        // Right side: Timer with color coding and streak
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = timer,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true) {
                    Color.Red // Red when ≤30 seconds
                } else {
                    Color.White
                }
            )

            // Streak display
            if (streakInfo.currentStreak > 0) {
                StreakDisplay(
                    streakInfo = streakInfo,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// Helper function to generate puzzle from difficulty level
fun generatePercentagePuzzleFromDifficulty(
    generator: AdaptiveMathPuzzleGenerators,
    difficultyLevel: DifficultyManager.DifficultyLevel
): PercentagePuzzle? {
    val puzzle = generator.generatePercentageForLevel(difficultyLevel)
    return puzzle ?: PercentagePuzzle(
        total = 100,
        percentage = 20,
        answer = 20.0,
        difficulty = difficultyLevel.name,
        hint = "Multiply by 20 and divide by 100"
    )
}
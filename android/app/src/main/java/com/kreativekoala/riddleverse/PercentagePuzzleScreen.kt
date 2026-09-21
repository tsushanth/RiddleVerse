// AdaptivePercentagePuzzleScreen.kt - Enhanced with adaptive difficulty
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas),
        contentAlignment = Alignment.TopCenter
    ) {
        val compact = maxHeight < 600.dp
        val wide = maxWidth > maxHeight && maxWidth >= 560.dp
        val keypad: @Composable (Modifier) -> Unit = { keypadModifier ->
            PercentageKeypad(
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
                                    showAdaptationNotification = SHOW_ADAPTATION_NOTICES
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
                modifier = keypadModifier
            )
        }
        // Keypad height leaves room for HUD + problem + input in portrait.
        val keypadHeight = (maxHeight - 56.dp - 56.dp - (if (compact) 96.dp else 160.dp))
            .coerceIn(232.dp, 380.dp)

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = if (compact) 8.dp else 16.dp)
        ) {
            PercentageHud(
                level = currentLevel,
                streak = currentStreak,
                score = totalScore,
                timer = displayTimer,
                lives = currentHearts,
                maxLives = currentDifficultyLevel.livesAllowed,
                onBack = onBack
            )

            if (wide) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AdaptivePercentagePuzzleHeader(
                            puzzle = currentPuzzle,
                            difficultyLevel = currentDifficultyLevel,
                            compact = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PercentageInput(currentInput = currentInput, modifier = Modifier.fillMaxWidth())
                    }
                    keypad(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AdaptivePercentagePuzzleHeader(
                        puzzle = currentPuzzle,
                        difficultyLevel = currentDifficultyLevel,
                        compact = compact,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PercentageInput(currentInput = currentInput, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(if (compact) 8.dp else 16.dp))
                keypad(Modifier.fillMaxWidth().height(keypadHeight))
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
                        showAdaptationNotification = SHOW_ADAPTATION_NOTICES
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!compact) {
            Text(
                text = "CALCULATE THE PERCENTAGE",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvInkSoft,
                letterSpacing = 1.sp
            )

            Text(
                text = difficultyLevel.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = RvInkSoft,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        puzzle?.let { puzzle ->
            // Percentage display
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)
            ) {
                // Percentage circle
                Box(
                    modifier = Modifier
                        .size(if (compact) 72.dp else 100.dp)
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
                        color = RvInk
                    )
                }

                Text(
                    text = "OF",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                Text(
                    text = puzzle.total.toInt().toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                Text(
                    text = "=",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                Text(
                    text = "?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInkSoft
                )
            }

            // Hint display for lower difficulties
            if (!compact && puzzle.hint.isNotEmpty() && difficultyLevel.name.contains("easy", ignoreCase = true) ||
                difficultyLevel.name.contains("tutorial", ignoreCase = true) ||
                difficultyLevel.name.contains("beginner", ignoreCase = true)) {

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = RvSurface
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "💡 ${puzzle.hint}",
                        fontSize = 14.sp,
                        color = RvInkSoft,
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
                    tint = RvInk,
                    modifier = Modifier.size(24.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = null,
                tint = RvInkSoft.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    text = "Level ${level.level}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                // Adaptive difficulty indicator
                Text(
                    text = currentDifficulty.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = RvSky
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
                    RvError // Red when ≤30 seconds
                } else {
                    RvInk
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

/** Compact single-row HUD: back, level, lives, score/streak and timer. */
@Composable
private fun PercentageHud(
    level: UserLevel,
    streak: Int,
    score: Int,
    timer: String,
    lives: Int,
    maxLives: Int,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = RvInk
                )
            }
            Text(
                text = "Level ${level.level}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                maxLines = 1
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(maxLives) { index ->
                Text(
                    text = if (index < lives) "❤️" else "🤍",
                    fontSize = 16.sp
                )
            }
            if (score > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$score",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    maxLines = 1
                )
            }
            if (streak > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "🔥 $streak",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    maxLines = 1
                )
            }
        }
        Text(
            text = timer,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true) {
                RvCoralEdge
            } else {
                RvInk
            }
        )
    }
}

@Composable
private fun PercentageInput(currentInput: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .background(RvSurface, RoundedCornerShape(12.dp))
            .border(2.dp, RvOutline, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = currentInput.ifEmpty { "?" },
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (currentInput.isEmpty()) RvInkSoft else RvInk,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** Fit-to-screen keypad: key rows share the available height; Submit is a pinned 56dp+ bar. */
@Composable
private fun PercentageKeypad(
    currentInput: String,
    onNumberClick: (String) -> Unit,
    onClear: () -> Unit,
    onDecimal: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0..2) {
                    val number = (row * 3 + col + 1).toString()
                    PercentageKey(number, Modifier.weight(1f).fillMaxHeight()) { onNumberClick(number) }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PercentageKey(
                "✕", Modifier.weight(1f).fillMaxHeight(),
                container = RvCoral.copy(alpha = 0.18f),
                description = stringResource(R.string.clear),
                onClick = onClear
            )
            PercentageKey("0", Modifier.weight(2f).fillMaxHeight()) { onNumberClick("0") }
        }
        val ready = currentInput.isNotEmpty()
        RvChunkyButton(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            tone = if (ready) RvToneViolet else RvTone(RvDisabled, RvOutline)
        ) {
            Text(
                text = stringResource(R.string.submit),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (ready) RvOnTone else RvInkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PercentageKey(
    label: String,
    modifier: Modifier,
    container: Color = RvSurface,
    description: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .background(container, RoundedCornerShape(12.dp))
            .border(1.dp, RvOutline, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = RvInk,
            modifier = if (description != null) Modifier.clearAndSetSemantics { } else Modifier
        )
    }
}

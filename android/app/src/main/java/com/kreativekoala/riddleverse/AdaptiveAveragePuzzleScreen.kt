// AdaptiveAveragePuzzleScreen.kt - Enhanced with adaptive difficulty
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kreativekoala.riddleverse.util.DebugLogger
import kotlinx.coroutines.delay
import kotlin.math.*

private const val TAG = "AdaptiveAverage"

@Composable
fun AdaptiveAveragePuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String,
    onSubmitAnswer: (Double) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    var showHint by remember { mutableStateOf(false) }

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("average"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // ✅ NEW: Competitive ranking state
    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "average",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate puzzle based on current difficulty
    val mathGenerator = remember { MathPuzzleGenerators() }
    var currentPuzzle by remember(currentDifficultyLevel) {
        mutableStateOf(generateAveragePuzzleFromDifficulty(mathGenerator, currentDifficultyLevel))
    }

    // Score and performance tracking
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // ✅ NEW: Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAnswers by remember { mutableStateOf(0) }

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
            currentPuzzle = generateAveragePuzzleFromDifficulty(mathGenerator, currentDifficultyLevel)
            isAnswered = false
            currentInput = ""
            currentHearts = currentDifficultyLevel.livesAllowed
            gameStartTime = System.currentTimeMillis()
        }
    }

    // Timer calculation and countdown
    val totalTimeSeconds = currentDifficultyLevel.timeLimit
    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(formatTime(totalTimeSeconds)) }

    // ✅ UPDATED: Enhanced performance recording
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = currentPuzzle.numbers.size,
            totalScore = totalScore,
            puzzleType = "average"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = SHOW_ADAPTATION_NOTICES
            }
        }
    }

    // ✅ UPDATED: Use unified score calculation
    fun calculateScore(isCorrect: Boolean, timeSpent: Long): Int {
        return calculateUnifiedAdaptiveScore(
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            difficulty = currentDifficultyLevel,
            challengeComplexity = currentPuzzle.numbers.size,
            currentStreak = currentStreak,
            challengesCompleted = attempts,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "average"
        )
    }

    fun handleAnswer(userAnsweredYes: Boolean) {
        val timeSpent = System.currentTimeMillis() - gameStartTime
        val answer = currentInput.toDoubleOrNull() ?: 0.0
        val isCorrect = abs(answer - currentPuzzle.average) <= 0.5

        totalAnswers++
        if (isCorrect) {
            correctAnswers++
            currentStreak++
            val score = calculateScore(isCorrect, timeSpent)
            totalScore += score
        } else {
            currentStreak = 0
            currentHearts = maxOf(0, currentHearts - 1)
        }

        recordPerformance(isCorrect, timeSpent)
        isAnswered = true
    }



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        // Background components
        MountainLandscapeBackground()
        GeometricOverlay()

        AverageFitLayout(
            hud = { compact ->
                if (compact) {
                    GameCompactHud(
                        timer = displayTimer,
                        lives = currentHearts,
                        maxLives = currentDifficultyLevel.livesAllowed,
                        score = totalScore,
                        onBack = onBack
                    )
                } else {
                    AdaptiveUnifiedHeader(
                        level = currentLevel,
                        streakInfo = streakInfo,
                        timer = displayTimer,
                        lives = currentHearts,
                        currentDifficulty = currentDifficultyLevel,
                        score = totalScore,
                        puzzleType = "average",
                        competitiveInsight = competitiveInsight,
                        onBack = onBack,
                        onPause = {  },
                        onHint = {
                            showHint = !showHint
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                }
                // Adaptation banners are intentionally off (SHOW_ADAPTATION_NOTICES=false); keep the hook.
                UnifiedAdaptationNotification(
                    adaptationInfo = adaptationInfo,
                    puzzleType = "average",
                    visible = showAdaptationNotification,
                    onDismiss = { showAdaptationNotification = false }
                )
            },
            info = {
                if (totalScore > 0 || attempts > 0) {
                    AverageInfoStrip(
                        buildString {
                            append("${stringResource(R.string.score_label)}: $totalScore")
                            append("  •  ${currentPuzzle.numbers.size} numbers")
                            if (currentStreak > 0) append("  •  🔥 $currentStreak")
                        }
                    )
                }
            },
            question = { compact ->
                AverageQuestionPane(
                    numbers = currentPuzzle.numbers,
                    currentInput = currentInput,
                    compact = compact,
                    subtitle = currentDifficultyLevel.name
                )
            },
            keypad = {
                AverageKeypad(
                currentInput = currentInput,
                onNumberClick = { digit ->
                    if (!isAnswered && currentInput.length < 6) {
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
                    DebugLogger.d(TAG, "Decimal not allowed - answers must be whole numbers")
                },
                onSubmit = {
                    if (!isAnswered && currentInput.isNotEmpty()) {
                        DebugLogger.d(TAG, "Submit clicked - input: $currentInput")
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        val answer = currentInput.toDoubleOrNull() ?: 0.0
                        isAnswered = true
                        attempts++

                        val timeSpent = totalTimeSeconds - timeRemaining
                        val isCorrect = abs(answer - currentPuzzle.average) <= 0.5

                        totalAnswers++
                        if (isCorrect) {
                            correctAnswers++
                            currentStreak++
                            val score = calculateScore(isCorrect, timeSpent.toLong() * 1000L)
                            totalScore += score
                            gamesPlayedThisSession++
                        } else {
                            currentHearts = maxOf(0, currentHearts - 1)
                            currentStreak = 0
                        }

                        recordPerformance(isCorrect, timeSpent.toLong() * 1000L)

                        // Show feedback and advance
                        feedbackManager.showFeedback(
                            puzzleType = "average",
                            isCorrect = isCorrect,
                            userAnswer = String.format("%.0f", answer),
                            correctAnswer = String.format("%.0f", currentPuzzle.average),
                            timeSpent = timeSpent * 1000L,
                            difficulty = currentDifficultyLevel.name,
                            timeRemaining = timeRemaining,
                            totalTime = totalTimeSeconds,
                            onComplete = {
                                DebugLogger.d(TAG, "Feedback completed")
                                onSubmitAnswer(answer)
                                fetchNextPuzzle(totalScore)
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            }
        )

        EnhancedUniversalFeedback(feedbackManager)
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isAnswered) {
        if (timeRemaining > 0 && !isAnswered) {
            delay(1000L)
            timeRemaining--
            displayTimer = formatTime(timeRemaining)
        } else if (timeRemaining == 0 && !isAnswered) {
            DebugLogger.d(TAG, "Time's up! Auto-submitting with input: '$currentInput'")
            val finalAnswer = currentInput.toDoubleOrNull() ?: 0.0
            isAnswered = true
            attempts++

            val timeSpent = totalTimeSeconds - timeRemaining
            val isCorrect = abs(finalAnswer - currentPuzzle.average) <= 0.5

            if (!isCorrect) {
                currentHearts = maxOf(0, currentHearts - 1)
                currentStreak = 0
            }

            recordPerformance(isCorrect, timeSpent.toLong() * 1000L)

            feedbackManager.showFeedback(
                puzzleType = "average",
                isCorrect = isCorrect,
                userAnswer = String.format("%.0f", finalAnswer),
                correctAnswer = String.format("%.0f", currentPuzzle.average),
                timeSpent = timeSpent * 1000L,
                difficulty = currentDifficultyLevel.name,
                timeRemaining = timeRemaining,
                totalTime = totalTimeSeconds,
                onComplete = {
                    DebugLogger.d(TAG, "Timeout feedback completed - advancing to next puzzle")
                    onSubmitAnswer(finalAnswer)
                    fetchNextPuzzle(totalScore)
                }
            )
        }
    }

    // ✅ NEW: Session completion handling
    if (currentHearts <= 0 || isAnswered) {
        UnifiedSessionCompletionHandler(
            puzzleType = "average",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAnswers,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
                totalScore = totalScore,
                averageTimePerPuzzle = totalTimeSeconds/5,
                currentStreak = currentStreak,
                individualTimes = emptyList()
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            // Session completion handled
        }
    }
}

fun formatTime(seconds: Int): String {
    return "${seconds / 60}:${String.format("%02d", seconds % 60)}"
}

@Composable
fun AdaptivePuzzleHeader(
    numbers: List<Int>,
    difficultyLevel: DifficultyManager.DifficultyLevel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "FIND THE AVERAGE",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = RvInkSoft.copy(alpha = 0.9f),
            letterSpacing = 1.sp
        )

        Text(
            text = difficultyLevel.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = RvSky,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Dynamic layout based on number count
        when {
            numbers.size <= 3 -> {
                // Single row for 2-3 numbers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    numbers.forEach { number ->
                        Text(
                            text = number.toString(),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            numbers.size <= 4 -> {
                // 2x2 grid for 4 numbers
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        numbers.take(2).forEach { number ->
                            Text(
                                text = number.toString(),
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        numbers.drop(2).forEach { number ->
                            Text(
                                text = number.toString(),
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            else -> {
                // Grid layout for 5+ numbers
                val rows = ceil(numbers.size / 3.0).toInt()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (row in 0 until rows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            val startIndex = row * 3
                            val endIndex = minOf(startIndex + 3, numbers.size)

                            numbers.subList(startIndex, endIndex).forEach { number ->
                                Text(
                                    text = number.toString(),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RvInk,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * @deprecated Use [AdaptiveUnifiedHeader] instead for consistent UI across all adaptive puzzles.
 */
@Deprecated(
    message = "Use AdaptiveUnifiedHeader from UnifiedAdaptiveIntegration.kt instead",
    replaceWith = ReplaceWith("AdaptiveUnifiedHeader(level, streakInfo, timer, lives, currentDifficulty, score, puzzleType, competitiveInsight, onBack, onPause, onHint)")
)
@Composable
fun AdaptiveAveragesTopGameBar(
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
fun generateAveragePuzzleFromDifficulty(
    generator: MathPuzzleGenerators,
    difficultyLevel: DifficultyManager.DifficultyLevel
): AveragePuzzle {
    val puzzle = generator.generateAverage(difficultyLevel.name)
    return puzzle ?: AveragePuzzle(
        numbers = listOf(10, 20, 30),
        average = 20.0,
        difficulty = difficultyLevel.name,
        hint = "Add all numbers and divide by count"
    )
}
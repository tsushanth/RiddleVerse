// AdaptiveSubtractionPuzzleScreen.kt - Enhanced with adaptive difficulty
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

// Data class for subtraction puzzles
data class SubtractionPuzzle(
    val number1: Int,
    val number2: Int,
    val answer: Int,
    val difficulty: String,
    val hint: String = ""
)

// Adaptive subtraction puzzle generator
class AdaptiveSubtractionPuzzleGenerator {
    fun generateForLevel(difficultyLevel: DifficultyManager.DifficultyLevel): SubtractionPuzzle {
        val (numberRange, allowNegatives, borrowingRequired) = when (difficultyLevel.name.lowercase()) {
            "tutorial" -> Triple(Pair(1, 10), false, false)
            "beginner" -> Triple(Pair(5, 25), false, false)
            "easy" -> Triple(Pair(10, 50), false, true)
            "easy+" -> Triple(Pair(20, 100), false, true)
            "medium-" -> Triple(Pair(50, 200), false, true)
            "medium" -> Triple(Pair(100, 500), false, true)
            "medium+" -> Triple(Pair(200, 750), false, true)
            "hard-" -> Triple(Pair(300, 1000), true, true)
            "hard" -> Triple(Pair(500, 1500), true, true)
            "hard+" -> Triple(Pair(750, 2000), true, true)
            "expert-" -> Triple(Pair(1000, 3000), true, true)
            "expert" -> Triple(Pair(1500, 4000), true, true)
            "expert+" -> Triple(Pair(2000, 5000), true, true)
            "master" -> Triple(Pair(3000, 7500), true, true)
            "grandmaster" -> Triple(Pair(5000, 10000), true, true)
            else -> Triple(Pair(100, 500), false, true)
        }

        val number1 = Random.nextInt(numberRange.first, numberRange.second + 1)
        val number2 = if (allowNegatives) {
            Random.nextInt(numberRange.first, numberRange.second + 1)
        } else {
            Random.nextInt(numberRange.first, minOf(number1, numberRange.second) + 1)
        }

        val answer = abs(number1 - number2)
        val hint = generateSubtractionHint(number1, number2, difficultyLevel.name)

        return SubtractionPuzzle(
            number1 = number1,
            number2 = number2,
            answer = answer,
            difficulty = difficultyLevel.name,
            hint = hint
        )
    }

    private fun generateSubtractionHint(num1: Int, num2: Int, difficulty: String): String {
        return when {
            difficulty.contains("tutorial", ignoreCase = true) ->
                "Count backwards from $num1 by $num2"
            difficulty.contains("beginner", ignoreCase = true) ->
                "Start with $num1 and take away $num2"
            difficulty.contains("easy", ignoreCase = true) ->
                "Find the difference between $num1 and $num2"
            num1 < num2 ->
                "Remember: the result is the distance between the numbers"
            else ->
                "Calculate step by step, borrowing if needed"
        }
    }
}

@Composable
fun AdaptiveSubtractionPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String,
    onSubmitAnswer: (Int) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val TAG = "AdaptiveSubtraction"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("subtraction"))
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
                    puzzleType = "subtraction",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate puzzle based on current difficulty
    val subtractionGenerator = remember { AdaptiveSubtractionPuzzleGenerator() }
    var currentPuzzle by remember(currentDifficultyLevel) {
        mutableStateOf(generateSubtractionPuzzleFromDifficulty(subtractionGenerator, currentDifficultyLevel))
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
            currentPuzzle = generateSubtractionPuzzleFromDifficulty(subtractionGenerator, currentDifficultyLevel)
            isAnswered = false
            currentInput = ""
            currentHearts = currentDifficultyLevel.livesAllowed
            gameStartTime = System.currentTimeMillis()
        }
    }

    // Timer calculation and countdown
    val totalTimeSeconds = currentDifficultyLevel.timeLimit
    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf("${totalTimeSeconds / 60}:${String.format("%02d", totalTimeSeconds % 60)}") }

    // ✅ UPDATED: Performance recording function
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = maxOf(currentPuzzle.number1, currentPuzzle.number2) / 100,
            totalScore = totalScore,
            puzzleType = "subtraction"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
            }
        }
    }

    // ✅ UPDATED: Use unified score calculation
    fun calculateScore(isCorrect: Boolean, timeSpent: Long): Int {
        return calculateUnifiedAdaptiveScore(
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            difficulty = currentDifficultyLevel,
            challengeComplexity = maxOf(currentPuzzle.number1, currentPuzzle.number2) / 100,
            currentStreak = currentStreak,
            challengesCompleted = attempts,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "subtraction"
        )
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isAnswered) {
        if (timeRemaining > 0 && !isAnswered) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !isAnswered) {
            Log.d("AdaptiveSubtraction", "⏰ Time's up! Auto-submitting with input: '$currentInput'")
            val finalAnswer = currentInput.toIntOrNull() ?: 0
            isAnswered = true
            attempts++
            totalAnswers++

            val timeSpent = totalTimeSeconds - timeRemaining
            val isCorrect = finalAnswer == currentPuzzle.answer

            if (!isCorrect) {
                currentHearts = maxOf(0, currentHearts - 1)
                currentStreak = 0
            }

            recordPerformance(isCorrect, timeSpent.toLong() * 1000L)

            feedbackManager.showFeedback(
                puzzleType = "subtraction",
                isCorrect = isCorrect,
                userAnswer = finalAnswer.toString(),
                correctAnswer = currentPuzzle.answer.toString(),
                timeSpent = timeSpent * 1000L,
                difficulty = currentDifficultyLevel.name,
                timeRemaining = timeRemaining,
                totalTime = totalTimeSeconds,
                onComplete = {
                    Log.d("AdaptiveSubtraction", "🎊 Timeout feedback completed - advancing to next puzzle")
                    onSubmitAnswer(finalAnswer)
                    fetchNextPuzzle(totalScore)
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Animated background pattern
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawAnimatedPattern(size)
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ✅ REPLACE: Use unified header instead of AdaptiveSubtractionTopGameBar
            AdaptiveUnifiedHeader(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer,
                lives = currentHearts,
                currentDifficulty = currentDifficultyLevel,
                score = totalScore,
                puzzleType = "subtraction",
                competitiveInsight = competitiveInsight,
                onBack = onBack,
                challengeNumber = attempts + 1, // Current challenge number
                totalChallenges = 5,
                onPause = { /* Add pause functionality if needed */ },
                onHint = {
                    // Show hint if available for current difficulty
                    Log.d(TAG, "Hint requested: ${currentPuzzle.hint}")
                }
            )

            // ✅ REPLACE: Use unified adaptation notification
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "subtraction",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )

            // Score display
            if (totalScore > 0 || attempts > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
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
                            Text(
                                text = "Score: $totalScore",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentDifficultyLevel.name.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Cyan
                            )
                            Text(
                                text = "${currentPuzzle.number1} - ${currentPuzzle.number2}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        if (attempts > 0) {
                            Text(
                                text = "Attempt: $attempts",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Title
            Text(
                text = "FIND THE DIFFERENCE",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Numbers display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = currentPuzzle.number1.toString(),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "−",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = currentPuzzle.number2.toString(),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Hint display for easier difficulties
            if (currentPuzzle.hint.isNotEmpty() && (currentDifficultyLevel.name.contains("tutorial", ignoreCase = true) ||
                        currentDifficultyLevel.name.contains("beginner", ignoreCase = true) ||
                        currentDifficultyLevel.name.contains("easy", ignoreCase = true))) {

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "💡 ${currentPuzzle.hint}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Input display
            InputDisplay(
                currentInput = currentInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Calculator Grid
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
                    // No decimal support for subtraction - answers should be whole numbers
                    Log.d("AdaptiveSubtraction", "🚫 Decimal not allowed - answers must be whole numbers")
                },
                onSubmit = {
                    if (!isAnswered && currentInput.isNotEmpty()) {
                        Log.d("AdaptiveSubtraction", "🔥 Submit clicked - input: $currentInput")
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        val answer = currentInput.toIntOrNull() ?: 0
                        isAnswered = true
                        attempts++
                        totalAnswers++

                        val timeSpent = totalTimeSeconds - timeRemaining
                        val isCorrect = answer == currentPuzzle.answer

                        Log.d("AdaptiveSubtraction", "🎯 Answer check:")
                        Log.d("AdaptiveSubtraction", "  Problem: ${currentPuzzle.number1} - ${currentPuzzle.number2}")
                        Log.d("AdaptiveSubtraction", "  Expected: ${currentPuzzle.answer}")
                        Log.d("AdaptiveSubtraction", "  User answer: $answer")
                        Log.d("AdaptiveSubtraction", "  Result: $isCorrect")

                        if (isCorrect) {
                            correctAnswers++
                            currentStreak++
                            val score = calculateScore(isCorrect, timeSpent.toLong())
                            totalScore += score
                            gamesPlayedThisSession++
                            Log.d("AdaptiveSubtraction", "✅ Correct! Score: +$score, Total: $totalScore")
                        } else {
                            currentStreak = 0
                            currentHearts = maxOf(0, currentHearts - 1)
                            Log.d("AdaptiveSubtraction", "❌ Incorrect. Hearts remaining: $currentHearts")
                        }

                        recordPerformance(isCorrect, timeSpent.toLong() * 1000L)

                        feedbackManager.showFeedback(
                            puzzleType = "subtraction",
                            isCorrect = isCorrect,
                            userAnswer = answer.toString(),
                            correctAnswer = currentPuzzle.answer.toString(),
                            timeSpent = timeSpent * 1000L,
                            difficulty = currentDifficultyLevel.name,
                            timeRemaining = timeRemaining,
                            totalTime = totalTimeSeconds,
                            onComplete = {
                                Log.d("AdaptiveSubtraction", "🎊 Feedback completed")
                                onSubmitAnswer(answer)
                                fetchNextPuzzle(totalScore)
                            }
                        )
                    }
                },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .wrapContentHeight()
            )

            Spacer(modifier = Modifier.height(48.dp))
        }

        // ✅ NEW: Session completion handling
        if (currentHearts <= 0) {
            UnifiedSessionCompletionHandler(
                puzzleType = "subtraction",
                sessionScore = totalScore,
                sessionStats = SessionStatistics(
                    correctAnswers = correctAnswers,
                    totalAnswers = totalAnswers,
                    totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                    bestStreak = currentStreak,
                    winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
                    totalScore = totalScore,
                    averageTimePerPuzzle = if (totalAnswers > 0)
                        ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt() / totalAnswers
                    else 0,
                    currentStreak = currentStreak,
                    individualTimes = emptyList() // Track individual times if needed
                ),
                currentDifficulty = currentDifficultyLevel
            ) { result ->
                fetchNextPuzzle(totalScore)
            }
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
fun AdaptiveSubtractionTopGameBar(
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
        // Left side: Back button and level progression
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = "Pause",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    text = "Level ${level.level}",
                    fontSize = 18.sp,
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

                LevelProgressBar(
                    level = level,
                    modifier = Modifier.width(100.dp)
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

        // Right side: Timer and streak
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = timer,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 10 } == true) {
                    Color.Red // Red when ≤10 seconds
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
fun generateSubtractionPuzzleFromDifficulty(
    generator: AdaptiveSubtractionPuzzleGenerator,
    difficultyLevel: DifficultyManager.DifficultyLevel
): SubtractionPuzzle {
    return generator.generateForLevel(difficultyLevel)
}
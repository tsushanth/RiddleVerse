package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

data class ComparisonPair(
    val pairNumber: Int,
    val leftValue: String,
    val rightValue: String,
    val leftNumeric: Double,
    val rightNumeric: Double,
    val correctAnswer: String, // "left", "right", "equal"
    val operationType: String,
    val difficulty: Double
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MathComparisonPuzzleScreen(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    puzzleData: String,
    onSubmitAnswer: (String) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "MathComparisonScreen"

    // Score tracking state
    var totalScore by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var totalAttempts by remember { mutableIntStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pairStartTimes by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var reactionTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var difficultyScores by remember { mutableStateOf<List<Double>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }

    // Parse puzzle data
    val parsedData = remember(puzzleData) {
        try {
            val questionData = JSONObject(puzzleData)
            val sequenceArray = questionData.getJSONArray("sequence")
            val pairs = mutableListOf<ComparisonPair>()

            for (i in 0 until sequenceArray.length()) {
                val pairObj = sequenceArray.getJSONObject(i)
                pairs.add(
                    ComparisonPair(
                        pairNumber = pairObj.getInt("pairNumber"),
                        leftValue = pairObj.getString("leftValue"),
                        rightValue = pairObj.getString("rightValue"),
                        leftNumeric = pairObj.getDouble("leftNumeric"),
                        rightNumeric = pairObj.getDouble("rightNumeric"),
                        correctAnswer = pairObj.getString("correctAnswer"),
                        operationType = pairObj.getString("operationType"),
                        difficulty = pairObj.getDouble("difficulty")
                    )
                )
            }

            Log.d(TAG, "📊 Parsed ${pairs.size} comparison pairs")
            pairs
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse puzzle data", e)
            emptyList()
        }
    }

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            60 // Default 1 minute
        }
    }

    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentUserLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Game state
    var currentPairIndex by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var livesRemaining by remember { mutableIntStateOf(hearts) }
    var isGameComplete by remember { mutableStateOf(false) }
    var isProcessingAnswer by remember { mutableStateOf(false) }  // Prevent double-clicks

    val currentPair = if (parsedData.isNotEmpty() && currentPairIndex < parsedData.size) {
        parsedData[currentPairIndex]
    } else null

    // Initialize first pair timing
    LaunchedEffect(Unit) {
        gameStartTime = System.currentTimeMillis()
        if (parsedData.isNotEmpty()) {
            pairStartTimes = mapOf(0 to System.currentTimeMillis())
        }
    }

    // Calculate mathematical comparison score
    fun calculateMathComparisonScore(
        correctAnswers: Int,
        totalAttempts: Int,
        avgReactionTime: Long,
        difficulty: String,
        timeSpentMs: Long,
        bestStreak: Int,
        avgDifficulty: Double,
        completedPairs: Int
    ): Int {
        if (correctAnswers == 0) return 0

        // Base points by difficulty
        val basePointsPerPair = when (difficulty.lowercase()) {
            "easy" -> 30
            "medium" -> 40
            "hard" -> 50
            "expert" -> 60
            else -> 40
        }

        val baseScore = correctAnswers * basePointsPerPair

        // Mathematical complexity multiplier based on problem difficulty
        val complexityMultiplier = when {
            avgDifficulty >= 3.0 -> 1.6f // Complex calculations
            avgDifficulty >= 2.0 -> 1.4f // Moderate calculations
            avgDifficulty >= 1.5 -> 1.2f // Simple calculations
            else -> 1.0f // Basic comparisons
        }

        // Accuracy bonus
        val accuracy = if (totalAttempts > 0) correctAnswers.toFloat() / totalAttempts else 0f
        val accuracyBonus = when {
            accuracy >= 0.95f -> (baseScore * 0.4f).toInt()
            accuracy >= 0.85f -> (baseScore * 0.25f).toInt()
            accuracy >= 0.75f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Mathematical reasoning speed bonus
        val avgReactionSeconds = avgReactionTime / 1000f
        val speedBonus = when {
            avgReactionSeconds <= 2.0f -> (baseScore * 0.3f).toInt()
            avgReactionSeconds <= 3.0f -> (baseScore * 0.2f).toInt()
            avgReactionSeconds <= 4.0f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Consecutive correct streak bonus
        val streakBonus = when {
            bestStreak >= completedPairs -> (baseScore * 0.25f).toInt() // Perfect streak
            bestStreak >= completedPairs * 0.7f -> (baseScore * 0.15f).toInt()
            bestStreak >= completedPairs * 0.5f -> (baseScore * 0.05f).toInt()
            else -> 0
        }

        // Completion bonus
        val completionBonus = when {
            completedPairs >= parsedData.size -> (baseScore * 0.2f).toInt() // All pairs
            completedPairs >= parsedData.size * 0.8f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Problem-solving bonus for mathematical thinking
        val reasoningBonus = (baseScore * 0.15f).toInt()

        val finalScore = ((baseScore * complexityMultiplier).toInt() + accuracyBonus + speedBonus + streakBonus + completionBonus + reasoningBonus)

        Log.d(TAG, "🏆 Math comparison score calculation:")
        Log.d(TAG, "  Correct: $correctAnswers/$totalAttempts (${(accuracy * 100).toInt()}%)")
        Log.d(TAG, "  Base score: $baseScore")
        Log.d(TAG, "  Complexity multiplier: ${complexityMultiplier}x (avg difficulty: ${String.format("%.1f", avgDifficulty)})")
        Log.d(TAG, "  Accuracy bonus: $accuracyBonus")
        Log.d(TAG, "  Speed bonus: $speedBonus (avg ${avgReactionSeconds}s)")
        Log.d(TAG, "  Streak bonus: $streakBonus (best: $bestStreak)")
        Log.d(TAG, "  Completion bonus: $completionBonus ($completedPairs pairs)")
        Log.d(TAG, "  Reasoning bonus: $reasoningBonus")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, baseScore / 4) // Minimum 25% of base
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isGameComplete) {
        if (timeRemaining > 0 && !isGameComplete) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !isGameComplete) {
            // Time's up - calculate final score
            totalScore = calculateMathComparisonScore(
                correctAnswers = correctAnswers,
                totalAttempts = totalAttempts,
                avgReactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.average().toLong() else 3000L,
                difficulty = difficulty,
                timeSpentMs = System.currentTimeMillis() - gameStartTime,
                bestStreak = bestStreak,
                avgDifficulty = if (difficultyScores.isNotEmpty()) difficultyScores.average() else 1.0,
                completedPairs = currentPairIndex
            )

            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            isGameComplete = true
            onSubmitAnswer("timeout")
            fetchNextPuzzle(totalScore)
        }
    }



    // Auto-advance after showing result
    LaunchedEffect(showResult) {
        if (showResult) {
            delay(1500) // Show result for 1.5 seconds

            if (currentPairIndex >= parsedData.size - 1 || livesRemaining <= 0) {
                // Game complete - calculate final score
                totalScore = calculateMathComparisonScore(
                    correctAnswers = correctAnswers,
                    totalAttempts = totalAttempts,
                    avgReactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.average().toLong() else 3000L,
                    difficulty = difficulty,
                    timeSpentMs = System.currentTimeMillis() - gameStartTime,
                    bestStreak = bestStreak,
                    avgDifficulty = if (difficultyScores.isNotEmpty()) difficultyScores.average() else 1.0,
                    completedPairs = currentPairIndex + 1
                )

                isGameComplete = true
                val isSuccess = correctAnswers >= (parsedData.size * 0.6f).toInt()

                Log.d(TAG, "🎯 Game completed!")
                Log.d(TAG, "📊 Final score: $totalScore")

                // Show feedback through unified system
                feedbackManager.showFeedback(
                    puzzleType = "mathComparison",
                    isCorrect = isSuccess,
                    userAnswer = "$correctAnswers/${parsedData.size} comparisons correct",
                    correctAnswer = "Compare mathematical expressions accurately",
                    timeSpent = System.currentTimeMillis() - gameStartTime,
                    difficulty = difficulty,
                    timeRemaining = timeRemaining,
                    totalTime = totalTimeSeconds,
                    onComplete = {
                        onSubmitAnswer(if (isSuccess) "correct" else "incorrect")
                        Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                        fetchNextPuzzle(totalScore)
                    }
                )
            } else {
                // Move to next pair
                currentPairIndex++
                selectedAnswer = null
                showResult = false
                isProcessingAnswer = false  // Reset answer lock for next pair

                // Track next pair start time
                pairStartTimes = pairStartTimes + (currentPairIndex to System.currentTimeMillis())
            }
        }
    }

    // Handle answer submission with double-click prevention
    fun handleAnswer(answer: String) {
        // Prevent double-clicks and race conditions
        if (showResult || currentPair == null || isProcessingAnswer) return

        // Lock answer processing immediately
        isProcessingAnswer = true
        selectedAnswer = answer
        showResult = true
        totalAttempts++

        // Calculate reaction time
        val pairStartTime = pairStartTimes[currentPairIndex] ?: System.currentTimeMillis()
        val reactionTime = System.currentTimeMillis() - pairStartTime
        reactionTimes = reactionTimes + reactionTime
        difficultyScores = difficultyScores + currentPair.difficulty

        val isCorrect = answer == currentPair.correctAnswer
        Log.d(TAG, "📝 Pair ${currentPairIndex + 1}: Answer=$answer, Correct=${currentPair.correctAnswer}, " +
                "IsCorrect=$isCorrect, Reaction=${reactionTime}ms, Difficulty=${currentPair.difficulty}")

        if (isCorrect) {
            correctAnswers++
            streak++
            if (streak > bestStreak) bestStreak = streak
        } else {
            livesRemaining--
            streak = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF4CAF50), // Green top
                            Color(0xFF2E7D32)  // Darker green bottom
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Enhanced Header with Score
                EnhancedMathComparisonTopBar(
                    level = currentUserLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    totalScore = totalScore,
                    correctAnswers = correctAnswers,
                    totalAttempts = totalAttempts,
                    streak = streak,
                    onBack = onBack,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Lives indicator
                Row(
                    modifier = Modifier.padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(hearts) { index ->
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (index < livesRemaining) Color(0xFFFF69B4) else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Progress indicator
                Column(
                    modifier = Modifier.padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Question ${currentPairIndex + 1} of ${parsedData.size}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                Color.Black.copy(alpha = 0.3f),
                                RoundedCornerShape(2.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(
                                    fraction = (currentPairIndex + 1).toFloat() / parsedData.size.toFloat()
                                )
                                .background(
                                    Color.Green,
                                    RoundedCornerShape(2.dp)
                                )
                                .animateContentSize()
                        )
                    }
                }

                // Question text with difficulty indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    Text(
                        "Which value is greater?",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (currentPair != null) {
                        Text(
                            "Difficulty: ${String.format("%.1f", currentPair.difficulty)}/5",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }

                if (currentPair != null) {
                    // Clickable Value boxes
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        // First value (clickable)
                        AnimatedContent(
                            targetState = currentPair.leftValue,
                            transitionSpec = {
                                slideInHorizontally { it } + fadeIn() with
                                        slideOutHorizontally { -it } + fadeOut()
                            }
                        ) { leftValue ->
                            ClickableValueCard(
                                value = leftValue,
                                isSelected = selectedAnswer == "left",
                                isCorrect = if (showResult) currentPair.correctAnswer == "left" else null,
                                isWrong = if (showResult && selectedAnswer == "left") currentPair.correctAnswer != "left" else false,
                                enabled = !showResult,
                                onClick = { handleAnswer("left") }
                            )
                        }

                        // Second value (clickable)
                        AnimatedContent(
                            targetState = currentPair.rightValue,
                            transitionSpec = {
                                slideInHorizontally { it } + fadeIn() with
                                        slideOutHorizontally { -it } + fadeOut()
                            }
                        ) { rightValue ->
                            ClickableValueCard(
                                value = rightValue,
                                isSelected = selectedAnswer == "right",
                                isCorrect = if (showResult) currentPair.correctAnswer == "right" else null,
                                isWrong = if (showResult && selectedAnswer == "right") currentPair.correctAnswer != "right" else false,
                                enabled = !showResult,
                                onClick = { handleAnswer("right") }
                            )
                        }
                    }

                    // Equal button
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AnswerButton(
                            text = "EQUAL",
                            isSelected = selectedAnswer == "equal",
                            isCorrect = if (showResult) currentPair.correctAnswer == "equal" else null,
                            isWrong = if (showResult && selectedAnswer == "equal") currentPair.correctAnswer != "equal" else false,
                            enabled = !showResult,
                            onClick = { handleAnswer("equal") },
                            isEqual = true
                        )
                    }

                    // Result feedback
                    if (showResult) {
                        Spacer(modifier = Modifier.height(16.dp))

                        val isCorrect = selectedAnswer == currentPair.correctAnswer

                        AnimatedVisibility(
                            visible = showResult,
                            enter = fadeIn() + slideInVertically()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isCorrect) Color.Green.copy(alpha = 0.2f)
                                        else Color.Red.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        if (isCorrect) "✅ Correct!" else "❌ Wrong answer. Lives: $livesRemaining",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    if (isCorrect && reactionTimes.isNotEmpty()) {
                                        val reactionTime = reactionTimes.last() / 1000.0
                                        Text(
                                            "⚡ ${String.format("%.1f", reactionTime)}s",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 14.sp
                                        )
                                    } else if (!isCorrect) {
                                        Text(
                                            "${currentPair.leftValue} = ${currentPair.leftNumeric.toInt()}, ${currentPair.rightValue} = ${currentPair.rightNumeric.toInt()}",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
private fun EnhancedMathComparisonTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    totalScore: Int,
    correctAnswers: Int,
    totalAttempts: Int,
    streak: Int,
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
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Level ${level.level}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    LevelProgressBar(
                        level = level,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }

            // Center: Timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val timeValue = timer.substringAfter(":").toIntOrNull() ?: 0
                val isUrgent = timer.startsWith("0:") && timeValue <= 30

                Text(
                    text = timer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUrgent) Color.Red else Color.White
                )

                if (streakInfo.currentStreak > 0) {
                    StreakDisplay(
                        streakInfo = streakInfo,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Right side: Streak
            Column(
                horizontalAlignment = Alignment.End
            ) {
                if (streak > 1) {
                    Text(
                        text = "🔥 $streak",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B00)
                    )
                }
            }
        }

        // Score and progress display
        if (totalScore > 0 || totalAttempts > 0) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (totalScore > 0) {
                    Text(
                        text = "${stringResource(R.string.score_label)}: $totalScore",
                        color = Color(0xFFFFEB3B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "🧮 Math Comparison",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                if (totalAttempts > 0) {
                    Text(
                        text = "$correctAnswers/$totalAttempts",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ClickableValueCard(
    value: String,
    isSelected: Boolean,
    isCorrect: Boolean?,
    isWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isCorrect == true -> Color.Green.copy(alpha = 0.3f)
        isWrong -> Color.Red.copy(alpha = 0.3f)
        isSelected -> Color.Blue.copy(alpha = 0.3f)
        else -> Color.Black.copy(alpha = 0.4f)
    }

    val borderColor = when {
        isCorrect == true -> Color.Green
        isWrong -> Color.Red
        isSelected -> Color.Blue
        else -> Color.Black.copy(alpha = 0.4f)
    }

    val animatedBackgroundColor by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(300),
        label = "background"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = borderColor,
        animationSpec = tween(300),
        label = "border"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(animatedBackgroundColor, RoundedCornerShape(12.dp))
            .border(2.dp, animatedBorderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                color = Color(0xFF2196F3),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Show checkmark or X when result is shown
            if (isCorrect == true) {
                Spacer(modifier = Modifier.width(12.dp))
                Text("✓", color = Color.Green, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            } else if (isWrong) {
                Spacer(modifier = Modifier.width(12.dp))
                Text("✗", color = Color.Red, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AnswerButton(
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean?,
    isWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    isEqual: Boolean = false
) {
    val backgroundColor = when {
        isCorrect == true -> Color.Green
        isWrong -> Color.Red
        isEqual -> Color(0xFF00BCD4) // Cyan for EQUAL button
        else -> Color(0xFF2196F3) // Blue for other buttons
    }

    val animatedColor by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(300),
        label = "button_color"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedColor,
            disabledContainerColor = animatedColor.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Show checkmark or X when result is shown
            if (isCorrect == true) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("✓", color = Color.White, fontSize = 20.sp)
            } else if (isWrong) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("✗", color = Color.White, fontSize = 20.sp)
            }
        }
    }
}
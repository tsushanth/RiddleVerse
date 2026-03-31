package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

data class CardData(
    val letter: Char,
    val digit: Int,
    val isLetterFirst: Boolean,
    val textColor: Color
)

@Composable
fun DualCardPuzzleScreen(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "DualCard"

    // Score tracking state
    var totalScore by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var totalAttempts by remember { mutableIntStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var roundStartTimes by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var reactionTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    var taskSwitchCount by remember { mutableIntStateOf(0) }
    var taskSwitchAccuracy by remember { mutableStateOf<Map<String, Int>>(mapOf("even" to 0, "vowel" to 0)) }

    var currentRound by remember { mutableIntStateOf(1) }
    var lives by remember { mutableIntStateOf(hearts) }
    var isCheckingTopCard by remember { mutableStateOf(true) }
    var currentCardData by remember { mutableStateOf(generateRandomCardData()) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrectAnswer by remember { mutableStateOf(false) }
    var gameComplete by remember { mutableStateOf(false) }
    var gameState by remember { mutableStateOf("playing") }

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            90 // Default 1:30 for dual tasks
        }
    }

    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentUserLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    val totalRounds = when (difficulty.lowercase()) {
        "easy" -> 10
        "medium" -> 15
        "hard" -> 20
        "expert" -> 25
        else -> 15
    }

    // Calculate dual-task processing score
    fun calculateDualTaskScore(
        correctAnswers: Int,
        totalAttempts: Int,
        roundsCompleted: Int,
        avgReactionTime: Long,
        difficulty: String,
        timeSpentMs: Long,
        bestStreak: Int,
        taskSwitchCount: Int,
        taskSwitchAccuracy: Map<String, Int>
    ): Int {
        if (correctAnswers == 0) return 0

        // Base points by difficulty
        val basePointsPerRound = when (difficulty.lowercase()) {
            "easy" -> 20
            "medium" -> 30
            "hard" -> 40
            "expert" -> 50
            else -> 30
        }

        val baseScore = correctAnswers * basePointsPerRound

        // Dual-task complexity multiplier
        val complexityMultiplier = when (totalAttempts) {
            in 1..5 -> 1.0f
            in 6..10 -> 1.2f
            in 11..15 -> 1.4f
            in 16..20 -> 1.6f
            else -> 1.8f
        }

        // Accuracy bonus
        val accuracy = if (totalAttempts > 0) correctAnswers.toFloat() / totalAttempts else 0f
        val accuracyBonus = when {
            accuracy >= 0.9f -> (baseScore * 0.35f).toInt()
            accuracy >= 0.8f -> (baseScore * 0.2f).toInt()
            accuracy >= 0.7f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Task switching speed bonus
        val avgReactionSeconds = avgReactionTime / 1000f
        val speedBonus = when {
            avgReactionSeconds <= 1.5f -> (baseScore * 0.25f).toInt()
            avgReactionSeconds <= 2.0f -> (baseScore * 0.15f).toInt()
            avgReactionSeconds <= 2.5f -> (baseScore * 0.05f).toInt()
            else -> 0
        }

        // Cognitive flexibility bonus (task switching)
        val switchingBonus = when {
            taskSwitchCount >= roundsCompleted * 0.8f -> (baseScore * 0.2f).toInt()
            taskSwitchCount >= roundsCompleted * 0.6f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Dual-task balance bonus (both tasks performed well)
        val evenAccuracy = taskSwitchAccuracy["even"] ?: 0
        val vowelAccuracy = taskSwitchAccuracy["vowel"] ?: 0
        val balanceBonus = if (evenAccuracy > 0 && vowelAccuracy > 0) {
            val minAccuracy = minOf(evenAccuracy, vowelAccuracy)
            val maxAccuracy = maxOf(evenAccuracy, vowelAccuracy)
            val balance = minAccuracy.toFloat() / maxAccuracy
            if (balance >= 0.8f) (baseScore * 0.15f).toInt() else 0
        } else 0

        // Streak bonus for sustained performance
        val streakBonus = when {
            bestStreak >= roundsCompleted -> (baseScore * 0.2f).toInt() // Perfect streak
            bestStreak >= roundsCompleted * 0.7f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Executive control bonus
        val executiveBonus = (baseScore * 0.1f).toInt()

        val finalScore = ((baseScore * complexityMultiplier).toInt() + accuracyBonus + speedBonus + switchingBonus + balanceBonus + streakBonus + executiveBonus)

        Log.d(TAG, "🏆 Dual-task score calculation:")
        Log.d(TAG, "  Correct: $correctAnswers/$totalAttempts (${(accuracy * 100).toInt()}%)")
        Log.d(TAG, "  Base score: $baseScore")
        Log.d(TAG, "  Complexity multiplier: ${complexityMultiplier}x")
        Log.d(TAG, "  Accuracy bonus: $accuracyBonus")
        Log.d(TAG, "  Speed bonus: $speedBonus (avg ${avgReactionSeconds}s)")
        Log.d(TAG, "  Switching bonus: $switchingBonus ($taskSwitchCount switches)")
        Log.d(TAG, "  Balance bonus: $balanceBonus (even: $evenAccuracy, vowel: $vowelAccuracy)")
        Log.d(TAG, "  Streak bonus: $streakBonus (best: $bestStreak)")
        Log.d(TAG, "  Executive bonus: $executiveBonus")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, baseScore / 4) // Minimum 25% of base
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, gameState) {
        if (timeRemaining > 0 && gameState == "playing") {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && gameState == "playing") {
            // Time's up - calculate final score
            totalScore = calculateDualTaskScore(
                correctAnswers = correctAnswers,
                totalAttempts = totalAttempts,
                roundsCompleted = currentRound - 1,
                avgReactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.average().toLong() else 3000L,
                difficulty = difficulty,
                timeSpentMs = System.currentTimeMillis() - gameStartTime,
                bestStreak = bestStreak,
                taskSwitchCount = taskSwitchCount,
                taskSwitchAccuracy = taskSwitchAccuracy
            )

            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            gameComplete = true
            gameState = "complete"
        }
    }

    // Initialize first round
    LaunchedEffect(Unit) {
        gameStartTime = System.currentTimeMillis()
        roundStartTimes = mapOf(currentRound to System.currentTimeMillis())
    }



    // Define colors for the cards
    val cardColors = listOf(
        Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF4CAF50),
        Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFFFF5722)
    )

    LaunchedEffect(gameComplete) {
        if (gameComplete && gameState == "complete") {
            delay(1500)

            // Calculate final score if not already calculated
            if (totalScore == 0) {
                totalScore = calculateDualTaskScore(
                    correctAnswers = correctAnswers,
                    totalAttempts = totalAttempts,
                    roundsCompleted = currentRound - 1,
                    avgReactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.average().toLong() else 3000L,
                    difficulty = difficulty,
                    timeSpentMs = System.currentTimeMillis() - gameStartTime,
                    bestStreak = bestStreak,
                    taskSwitchCount = taskSwitchCount,
                    taskSwitchAccuracy = taskSwitchAccuracy
                )
            }

            val isSuccess = correctAnswers >= (totalRounds * 0.6f).toInt()

            feedbackManager.showFeedback(
                puzzleType = "dualTask",
                isCorrect = isSuccess,
                userAnswer = "$correctAnswers/$totalAttempts dual tasks completed",
                correctAnswer = "Switch between even/odd and vowel/consonant tasks",
                timeSpent = System.currentTimeMillis() - gameStartTime,
                difficulty = difficulty,
                timeRemaining = timeRemaining,
                totalTime = totalTimeSeconds,
                onComplete = {
                    onSubmitAnswer(isSuccess)
                    Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                    fetchNextPuzzle(totalScore)
                }
            )
        }
    }

    LaunchedEffect(showFeedback) {
        if (showFeedback) {
            delay(1000)
            showFeedback = false

            if (lives <= 0) {
                // Game over - calculate final score
                totalScore = calculateDualTaskScore(
                    correctAnswers = correctAnswers,
                    totalAttempts = totalAttempts,
                    roundsCompleted = currentRound - 1,
                    avgReactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.average().toLong() else 3000L,
                    difficulty = difficulty,
                    timeSpentMs = System.currentTimeMillis() - gameStartTime,
                    bestStreak = bestStreak,
                    taskSwitchCount = taskSwitchCount,
                    taskSwitchAccuracy = taskSwitchAccuracy
                )

                gameComplete = true
                gameState = "complete"
            } else if (currentRound >= totalRounds) {
                // Completed all rounds - calculate final score
                totalScore = calculateDualTaskScore(
                    correctAnswers = correctAnswers,
                    totalAttempts = totalAttempts,
                    roundsCompleted = currentRound,
                    avgReactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.average().toLong() else 3000L,
                    difficulty = difficulty,
                    timeSpentMs = System.currentTimeMillis() - gameStartTime,
                    bestStreak = bestStreak,
                    taskSwitchCount = taskSwitchCount,
                    taskSwitchAccuracy = taskSwitchAccuracy
                )

                gameComplete = true
                gameState = "complete"
            } else {
                // Move to next round
                currentRound++
                val wasCheckingTopCard = isCheckingTopCard
                isCheckingTopCard = !isCheckingTopCard // Alternate between cards

                // Track task switching
                if (wasCheckingTopCard != isCheckingTopCard) {
                    taskSwitchCount++
                }

                currentCardData = generateRandomCardData()
                roundStartTimes = roundStartTimes + (currentRound to System.currentTimeMillis())
            }
        }
    }

    fun handleAnswer(userAnsweredYes: Boolean) {
        if (gameComplete || showFeedback) return

        totalAttempts++
        val roundStartTime = roundStartTimes[currentRound] ?: System.currentTimeMillis()
        val reactionTime = System.currentTimeMillis() - roundStartTime
        reactionTimes = reactionTimes + reactionTime

        val correct = checkAnswer(isCheckingTopCard, currentCardData, userAnsweredYes)

        Log.d(TAG, "Round $currentRound: Task=${if (isCheckingTopCard) "even" else "vowel"}, " +
                "Answer=$userAnsweredYes, Correct=$correct, Reaction=${reactionTime}ms")

        isCorrectAnswer = correct
        showFeedback = true

        if (correct) {
            correctAnswers++
            streak++
            if (streak > bestStreak) bestStreak = streak

            // Track task-specific accuracy
            val taskType = if (isCheckingTopCard) "even" else "vowel"
            taskSwitchAccuracy = taskSwitchAccuracy + (taskType to (taskSwitchAccuracy[taskType] ?: 0) + 1)
        } else {
            lives--
            streak = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF8B4B6B))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Enhanced Header with Score
            EnhancedDualTaskTopBar(
                level = currentUserLevel,
                streakInfo = streakInfo,
                timer = displayTimer,
                hearts = lives,
                totalScore = totalScore,
                correctAnswers = correctAnswers,
                totalAttempts = totalAttempts,
                streak = streak,
                onBack = onBack,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Progress indicator
            Text(
                text = "Round $currentRound of $totalRounds",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced task indicator with switching coaching
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isCheckingTopCard) "Check if number is EVEN" else "Check if letter is VOWEL",
                    color = Color(0xFFFFD700),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                if (taskSwitchCount > 0) {
                    Text(
                        text = "⚡ Task switches: $taskSwitchCount",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Top Card - Number Even Check
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCheckingTopCard) Color.White else Color(0xFFBBBBBB)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Is the number even?",
                        color = if (isCheckingTopCard) Color.Black else Color(0xFF666666),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isCheckingTopCard) {
                        Text(
                            text = if (currentCardData.isLetterFirst) "${currentCardData.letter}${currentCardData.digit}" else "${currentCardData.digit}${currentCardData.letter}",
                            color = currentCardData.textColor,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Box(
                            modifier = Modifier.height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "—",
                                color = Color(0xFF666666),
                                fontSize = 24.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Card - Vowel Check
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (!isCheckingTopCard) Color.White else Color(0xFFBBBBBB)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Is the letter a vowel?",
                        color = if (!isCheckingTopCard) Color.Black else Color(0xFF666666),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isCheckingTopCard) {
                        Text(
                            text = if (currentCardData.isLetterFirst) "${currentCardData.letter}${currentCardData.digit}" else "${currentCardData.digit}${currentCardData.letter}",
                            color = currentCardData.textColor,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Box(
                            modifier = Modifier.height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "—",
                                color = Color(0xFF666666),
                                fontSize = 24.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Feedback display
            if (showFeedback) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isCorrectAnswer) "Correct! ✓" else "Wrong! ✗",
                        color = if (isCorrectAnswer) Color(0xFF4CAF50) else Color(0xFFFF5722),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (isCorrectAnswer && reactionTimes.isNotEmpty()) {
                        val reactionTime = reactionTimes.last() / 1000.0
                        Text(
                            text = "⚡ ${String.format("%.1f", reactionTime)}s",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Answer buttons
            if (!gameComplete && !showFeedback) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { handleAnswer(false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(
                            text = "NO",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { handleAnswer(true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.yes),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (gameComplete && gameState == "complete") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${stringResource(R.string.game_complete)}",
                        color = Color(0xFFFFD700),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${stringResource(R.string.final_score)}: $totalScore",
                        color = Color.White,
                        fontSize = 18.sp
                    )

                    if (taskSwitchAccuracy.isNotEmpty()) {
                        val evenAcc = taskSwitchAccuracy["even"] ?: 0
                        val vowelAcc = taskSwitchAccuracy["vowel"] ?: 0
                        Text(
                            text = "Even: $evenAcc • Vowel: $vowelAcc",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
private fun EnhancedDualTaskTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    hearts: Int,
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

            // Center: Lives and timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(hearts) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                val timeValue = timer.substringAfter(":").toIntOrNull() ?: 0
                val isUrgent = timer.startsWith("0:") && timeValue <= 30

                Text(
                    text = timer,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUrgent) Color.Red else Color.White
                )
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

                if (streakInfo.currentStreak > 0) {
                    StreakDisplay(
                        streakInfo = streakInfo,
                        modifier = Modifier.padding(top = 4.dp)
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
                        color = Color(0xFFFFD700),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "🧠 Dual Task",
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

private fun generateRandomCardData(): CardData {
    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val digits = 0..9
    val colors = listOf(
        Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF4CAF50),
        Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFFFF5722),
        Color.Black
    )

    return CardData(
        letter = letters.random(),
        digit = digits.random(),
        isLetterFirst = Random.nextBoolean(),
        textColor = colors.random()
    )
}

private fun checkAnswer(
    isCheckingTopCard: Boolean,
    currentCardData: CardData,
    userAnsweredYes: Boolean
): Boolean {
    return if (isCheckingTopCard) {
        // Check if digit is even
        val isEven = currentCardData.digit % 2 == 0
        isEven == userAnsweredYes
    } else {
        // Check if letter is vowel
        val vowels = setOf('A', 'E', 'I', 'O', 'U')
        val isVowel = currentCardData.letter in vowels
        isVowel == userAnsweredYes
    }
}
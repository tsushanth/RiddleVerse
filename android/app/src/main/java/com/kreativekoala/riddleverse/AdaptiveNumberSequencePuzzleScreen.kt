// AdaptiveNumberSequencePuzzleScreen.kt
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveNumberSequencePuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveNumberSequence"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("numberSequence"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // ✅ NEW: Competitive ranking state
    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    // Load competitive insight
    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "numberSequence",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate adaptive configuration
    val adaptiveConfig = remember(currentDifficultyLevel) {
        generateAdaptiveNumberSequenceConfig(currentDifficultyLevel)
    }

    var timeLeft by remember { mutableStateOf(currentDifficultyLevel.timeLimit) }
    var isPaused by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var gameCompleted by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var feedbackColor by remember { mutableStateOf(Color.Red) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // ✅ NEW: Track session for competitive ranking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctRounds by remember { mutableStateOf(0) }
    var totalRounds by remember { mutableStateOf(0) }

    // Game state
    var currentRoundNumbers by remember { mutableStateOf(emptyList<Int>()) }
    var sortedTargetNumbers by remember { mutableStateOf(emptyList<Int>()) }
    var nextExpectedIndex by remember { mutableStateOf(0) }
    var numbersVisible by remember { mutableStateOf(mutableMapOf<Int, Boolean>()) }
    var numberPositions by remember { mutableStateOf(emptyList<NumberPosition>()) }
    var showWrongFeedback by remember { mutableStateOf(false) }
    var wrongFeedbackPosition by remember { mutableStateOf(Offset.Zero) }
    var currentRound by remember { mutableStateOf(1) }
    var totalScore by remember { mutableStateOf(0) }
    var roundStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()
    val haptics = LocalHapticFeedback.current
    var showHint by remember { mutableStateOf(false) }


    // ✅ UPDATED: Performance recording function
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = adaptiveConfig.numberCount,
            totalScore = totalScore,
            puzzleType = "numberSequence"
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
            challengeComplexity = adaptiveConfig.numberCount,
            currentStreak = currentStreak,
            challengesCompleted = currentRound,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "numberSequence"
        )
    }

    fun generateNewRound() {
        val randomNumbers = (1..adaptiveConfig.numberCount).map {
            Random.nextInt(adaptiveConfig.numberRange.first, adaptiveConfig.numberRange.last + 1)
        }.distinct().take(adaptiveConfig.numberCount)

        val finalNumbers = if (randomNumbers.size < adaptiveConfig.numberCount) {
            val additional = generateSequence {
                Random.nextInt(adaptiveConfig.numberRange.first, adaptiveConfig.numberRange.last + 1)
            }.filter { it !in randomNumbers }
                .take(adaptiveConfig.numberCount - randomNumbers.size)
                .toList()
            randomNumbers + additional
        } else {
            randomNumbers
        }

        currentRoundNumbers = finalNumbers
        sortedTargetNumbers = finalNumbers.sorted()
        nextExpectedIndex = 0
        numbersVisible = finalNumbers.associateWith { true }.toMutableMap()
        roundStartTime = System.currentTimeMillis()

        Log.d(TAG, "New adaptive round $currentRound:")
        Log.d(TAG, "Numbers: $finalNumbers")
        Log.d(TAG, "Target order: $sortedTargetNumbers")
        Log.d(TAG, "Difficulty: ${currentDifficultyLevel.name}")
    }

    // Generate positions for current round numbers
    fun generatePositions() {
        val colors = listOf(
            Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFE91E63), Color(0xFFFF9800),
            Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722), Color(0xFF795548),
            Color(0xFF607D8B), Color(0xFF8BC34A), Color(0xFFFF6B35), Color(0xFF6C5CE7)
        )

        val positions = mutableListOf<NumberPosition>()
        val usedPositions = mutableSetOf<Pair<Float, Float>>()

        currentRoundNumbers.forEachIndexed { index, number ->
            var x: Float
            var y: Float
            var attempts = 0

            do {
                x = Random.nextFloat() * 0.7f + 0.15f
                y = Random.nextFloat() * 0.6f + 0.2f
                attempts++
            } while (usedPositions.any {
                    abs(it.first - x) < 0.15f && abs(it.second - y) < 0.15f
                } && attempts < 50)

            usedPositions.add(Pair(x, y))

            positions.add(
                NumberPosition(
                    number = number,
                    x = x,
                    y = y,
                    color = colors[index % colors.size],
                    isVisible = true
                )
            )
        }

        numberPositions = positions
    }

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            generateNewRound()
            generatePositions()
            timeLeft = currentDifficultyLevel.timeLimit
            currentHearts = currentDifficultyLevel.livesAllowed
            roundStartTime = System.currentTimeMillis()
        }
    }

    // Initialize first round
    LaunchedEffect(Unit) {
        generateNewRound()
        generatePositions()
    }

    // Timer countdown
    LaunchedEffect(gameStarted, isPaused, gameCompleted) {
        if (gameStarted && !isPaused && !gameCompleted) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0) {
                // Time's up - record poor performance
                recordPerformance(false, currentDifficultyLevel.timeLimit * 1000L)
                gameCompleted = true
                onGameComplete(false, 10)
            }
        }
    }

    // Wrong feedback animation
    LaunchedEffect(showWrongFeedback) {
        if (showWrongFeedback) {
            delay(800)
            showWrongFeedback = false
        }
    }

    // Check if current round is completed
    LaunchedEffect(numbersVisible.values.toList(), currentRoundNumbers) {
        if (currentRoundNumbers.isNotEmpty() &&
            numbersVisible.isNotEmpty() &&
            numbersVisible.values.none { it }) {

            // Round completed successfully
            totalRounds++
            correctRounds++
            val timeSpent = System.currentTimeMillis() - roundStartTime
            val newStreak = currentStreak + 1

            val roundScore = calculateScore(true, timeSpent)
            totalScore += roundScore
            currentStreak = newStreak
            gamesPlayedThisSession++

            feedbackMessage = "🎉 Round $currentRound Complete! +$roundScore points"
            feedbackColor = Color(0xFF4CAF50)
            showFeedback = true

            Log.d(TAG, "✅ Round completed! Score: +$roundScore, Total: $totalScore")

            // Record successful performance
            recordPerformance(true, timeSpent)

            delay(1500)
            showFeedback = false

            // Start next round
            currentRound++
            generateNewRound()
            generatePositions()

            // Add bonus time for next round
            val bonusTime = adaptiveConfig.timePerNumber * 2
            timeLeft += bonusTime
        }
    }

    // Handle game over when hearts reach 0
    LaunchedEffect(currentHearts) {
        if (currentHearts == 0 && gameStarted && !gameCompleted) {
            gameCompleted = true
            feedbackMessage = "💔 Game Over! Final Score: $totalScore"
            feedbackColor = Color.Red
            showFeedback = true
            delay(2000)
            onGameComplete(false, 10)
        }
    }

    // ✅ NEW: Session completion handling
    if (gameCompleted) {
        UnifiedSessionCompletionHandler(
            puzzleType = "numberSequence",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctRounds,
                totalAnswers = totalRounds,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalRounds > 0) correctRounds.toFloat() / totalRounds else 0f,
                totalScore = totalScore,
                averageTimePerPuzzle = if (totalRounds > 0)
                    ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt() / totalRounds
                else 0,
                currentStreak = currentStreak,
                individualTimes = emptyList() // Can track individual round times if needed
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            onGameComplete(correctRounds > 0, totalScore)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20))
    ) {
        // ✅ REPLACE: Use unified header instead of AdaptiveNumberSequenceTopBar
        AdaptiveUnifiedHeader(
            level = currentLevel,
            streakInfo = streakInfo,
            timer = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
            lives = currentHearts,
            currentDifficulty = currentDifficultyLevel,
            score = totalScore,
            puzzleType = "numbersequence",
            challengeNumber = currentRound,
            totalChallenges = 10, // Or whatever makes sense for sequence rounds
            competitiveInsight = competitiveInsight,
            onBack = {
                gameCompleted = true
                onBack()
            },
            onPause = { isPaused = !isPaused },
            onHint = {
                showHint = !showHint
                if (showHint) {
                    Log.d(TAG, "Hint: Next number to tap is ${sortedTargetNumbers.getOrNull(nextExpectedIndex)}")
                }
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )

        // ✅ REPLACE: Use unified adaptation notification
        UnifiedAdaptationNotification(
            adaptationInfo = adaptationInfo,
            puzzleType = "numberSequence",
            visible = showAdaptationNotification,
            onDismiss = { showAdaptationNotification = false }
        )

        // Instructions
        if (!gameStarted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔢 Adaptive Number Sequence",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tap_ascending_order),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${currentDifficultyLevel.name} • Range: ${adaptiveConfig.numberRange.first}-${adaptiveConfig.numberRange.last} • ${adaptiveConfig.numberCount} numbers per round",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                    Text(
                        text = currentDifficultyLevel.description,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { gameStarted = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text(stringResource(R.string.start).uppercase(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Game Area
        if (gameStarted && !gameCompleted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // Game background with adaptive grid
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val gridSize = 40.dp.toPx()
                    val gridColor = Color.White.copy(alpha = 0.1f)

                    for (x in 0 until (size.width / gridSize).toInt()) {
                        drawLine(
                            color = gridColor,
                            start = Offset(x * gridSize, 0f),
                            end = Offset(x * gridSize, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    for (y in 0 until (size.height / gridSize).toInt()) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y * gridSize),
                            end = Offset(size.width, y * gridSize),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // Numbers with adaptive sizing
                BoxWithConstraints {
                    val screenWidth = maxWidth
                    val screenHeight = maxHeight

                    // Adaptive number size based on difficulty
                    val numberSize = when (currentDifficultyLevel.index) {
                        0 -> 70.dp  // Beginner - larger
                        1 -> 65.dp  // Easy
                        2 -> 60.dp  // Medium
                        3 -> 55.dp  // Hard
                        4 -> 50.dp  // Expert - smaller
                        else -> 60.dp
                    }

                    numberPositions.forEach { position ->
                        val isVisible = numbersVisible[position.number] ?: false

                        if (isVisible) {
                            val xPos = (screenWidth * position.x)
                            val yPos = (screenHeight * position.y)

                            Box(
                                modifier = Modifier
                                    .offset(x = xPos, y = yPos)
                                    .size(numberSize)
                                    .clip(CircleShape)
                                    .background(position.color)
                                    .clickable {
                                        if (!isPaused) {
                                            val expectedNumber = sortedTargetNumbers.getOrNull(nextExpectedIndex)

                                            if (position.number == expectedNumber) {
                                                // Correct number tapped
                                                numbersVisible = numbersVisible.toMutableMap().apply {
                                                    this[position.number] = false
                                                }
                                                nextExpectedIndex++

                                                // Award points for correct tap
                                                val tapScore = calculateScore(
                                                    true,
                                                    System.currentTimeMillis() - roundStartTime
                                                )
                                                totalScore += tapScore
                                            } else {
                                                // Wrong number tapped
                                                totalRounds++
                                                wrongFeedbackPosition = Offset(xPos.value, yPos.value)
                                                showWrongFeedback = true
                                                currentHearts = maxOf(0, currentHearts - 1)
                                                currentStreak = 0

                                                // Record mistake
                                                recordPerformance(
                                                    false,
                                                    System.currentTimeMillis() - roundStartTime
                                                )
                                            }
                                        }
                                    }
                                    .animateContentSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = position.number.toString(),
                                    color = Color.White,
                                    fontSize = when (currentDifficultyLevel.index) {
                                        0 -> 28.sp  // Beginner
                                        1 -> 26.sp  // Easy
                                        2 -> 24.sp  // Medium
                                        3 -> 22.sp  // Hard
                                        4 -> 20.sp  // Expert
                                        else -> 24.sp
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Wrong feedback
                    if (showWrongFeedback) {
                        val scale by animateFloatAsState(
                            targetValue = if (showWrongFeedback) 1.5f else 1f,
                            animationSpec = tween(300)
                        )

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = wrongFeedbackPosition.x.dp,
                                    y = wrongFeedbackPosition.y.dp
                                )
                                .scale(scale)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.wrong),
                                tint = Color.Red,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }

        // Feedback overlay
        if (showFeedback) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = feedbackColor.copy(alpha = 0.9f))
            ) {
                Text(
                    text = feedbackMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Adaptive configuration data class
data class AdaptiveNumberSequenceConfig(
    val numberCount: Int,
    val numberRange: IntRange,
    val timePerNumber: Int,
    val name: String
)

// Generate adaptive configuration based on difficulty level
fun generateAdaptiveNumberSequenceConfig(difficulty: DifficultyManager.DifficultyLevel): AdaptiveNumberSequenceConfig {
    return when (difficulty.index) {
        0 -> AdaptiveNumberSequenceConfig( // Beginner
            numberCount = 4,
            numberRange = 1..10,
            timePerNumber = 8,
            name = "Beginner"
        )
        1 -> AdaptiveNumberSequenceConfig( // Easy
            numberCount = 5,
            numberRange = 1..20,
            timePerNumber = 6,
            name = "Easy"
        )
        2 -> AdaptiveNumberSequenceConfig( // Medium
            numberCount = 7,
            numberRange = 1..50,
            timePerNumber = 5,
            name = "Medium"
        )
        3 -> AdaptiveNumberSequenceConfig( // Hard
            numberCount = 9,
            numberRange = 1..100,
            timePerNumber = 4,
            name = "Hard"
        )
        4 -> AdaptiveNumberSequenceConfig( // Expert
            numberCount = 12,
            numberRange = 1..200,
            timePerNumber = 3,
            name = "Expert"
        )
        else -> AdaptiveNumberSequenceConfig(
            numberCount = 7,
            numberRange = 1..50,
            timePerNumber = 5,
            name = "Medium"
        )
    }
}
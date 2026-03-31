// AdaptiveNumberSumPuzzleScreen.kt - Fixed: Continue to next puzzle instead of ending game
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveNumberSumPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveNumberSum"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("numberSum"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // Competitive ranking state
    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    // Load competitive insight
    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "numberSum",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate adaptive configuration
    val adaptiveConfig = remember(currentDifficultyLevel) {
        generateAdaptiveNumberSumConfig(currentDifficultyLevel)
    }

    var timeLeft by remember { mutableStateOf(currentDifficultyLevel.timeLimit) }
    var isPaused by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var gameCompleted by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(true) }
    var currentScore by remember { mutableStateOf(0) }
    var totalScore by remember { mutableStateOf(0) }
    var currentRound by remember { mutableStateOf(1) }
    var showCompletionMessage by remember { mutableStateOf(false) }
    var completionMessage by remember { mutableStateOf("") }
    var selectedTileIds by remember { mutableStateOf(listOf<Int>()) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // Track session for competitive ranking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAttempts by remember { mutableStateOf(0) }
    var totalAttempts by remember { mutableStateOf(0) }

    // Game state
    var currentConfig by remember { mutableStateOf(adaptiveConfig) }
    var numberTiles by remember { mutableStateOf(createAdaptiveNumberTiles(currentConfig)) }
    var currentSum by remember { mutableStateOf(0) }
    var showWrongFeedback by remember { mutableStateOf(false) }
    var shouldClearWrongFeedback by remember { mutableStateOf(false) }
    var puzzleCompleted by remember { mutableStateOf(false) }
    var roundStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // ✅ NEW: Track max rounds for session completion
    val maxRounds = 10 // Or based on difficulty level
    var shouldShowSessionEnd by remember { mutableStateOf(false) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()
    val haptics = LocalHapticFeedback.current
    var showHint by remember { mutableStateOf(false) }

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            val newConfig = generateAdaptiveNumberSumConfig(currentDifficultyLevel)
            currentConfig = newConfig
            numberTiles = createAdaptiveNumberTiles(newConfig)
            selectedTileIds = listOf()
            currentSum = 0
            showWrongFeedback = false
            puzzleCompleted = false
            currentHearts = currentDifficultyLevel.livesAllowed
            timeLeft = currentDifficultyLevel.timeLimit
            roundStartTime = System.currentTimeMillis()
        }
    }

    // Performance recording function
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = currentConfig.correctNumbers.size,
            totalScore = totalScore,
            puzzleType = "numberSum"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
            }
        }
    }

    // Use unified score calculation
    fun calculateScore(isCorrect: Boolean, timeSpent: Long): Int {
        return calculateUnifiedAdaptiveScore(
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            difficulty = currentDifficultyLevel,
            challengeComplexity = currentConfig.correctNumbers.size,
            currentStreak = currentStreak,
            challengesCompleted = totalAttempts,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "numberSum"
        )
    }

    // Generate new puzzle configuration based on current difficulty
    fun generateNewPuzzle() {
        currentConfig = generateAdaptiveNumberSumConfig(currentDifficultyLevel)
        numberTiles = createAdaptiveNumberTiles(currentConfig)
        selectedTileIds = listOf()
        currentSum = 0
        showWrongFeedback = false
        puzzleCompleted = false
        roundStartTime = System.currentTimeMillis()
        currentRound++

        Log.d(TAG, "New adaptive puzzle generated (Round $currentRound):")
        Log.d(TAG, "Target: ${currentConfig.targetSum}")
        Log.d(TAG, "Correct numbers: ${currentConfig.correctNumbers}")
        Log.d(TAG, "Distractors: ${currentConfig.distractorNumbers}")
    }

    // Handle number tile click
    fun onNumberClick(tileId: Int, number: Int, isCorrect: Boolean) {
        if (isPaused || gameCompleted || puzzleCompleted) return

        if (selectedTileIds.contains(tileId)) {
            // Deselect tile
            selectedTileIds = selectedTileIds - tileId
            currentSum -= number

            numberTiles = numberTiles.map { tile ->
                if (tile.id == tileId) {
                    tile.copy(
                        isSelected = false,
                        showFeedback = false,
                        feedbackType = FeedbackType.NONE
                    )
                } else tile
            }
        } else {
            // Select tile
            selectedTileIds = selectedTileIds + tileId
            currentSum += number

            numberTiles = numberTiles.map { tile ->
                if (tile.id == tileId) {
                    tile.copy(
                        isSelected = true,
                        showFeedback = false,
                        feedbackType = FeedbackType.NONE
                    )
                } else tile
            }

            // Check sum when we have the expected number of selections
            val requiredCount = currentConfig.correctNumbers.size
            if (selectedTileIds.size == requiredCount) {
                totalAttempts++
                val timeSpent = System.currentTimeMillis() - roundStartTime

                if (currentSum == currentConfig.targetSum) {
                    // Correct sum
                    correctAttempts++
                    numberTiles = numberTiles.map { tile ->
                        if (tile.id in selectedTileIds) {
                            tile.copy(showFeedback = true, feedbackType = FeedbackType.CORRECT)
                        } else tile
                    }

                    val newStreak = currentStreak + 1
                    val score = calculateScore(true, timeSpent)

                    currentScore += score
                    totalScore += score
                    currentStreak = newStreak
                    gamesPlayedThisSession++
                    puzzleCompleted = true

                    Log.d(TAG, "✅ Valid solution! Score: +$score, Total: $totalScore")

                    recordPerformance(true, timeSpent)

                    // ✅ NEW: Show feedback via unified system
                    feedbackManager.showFeedback(
                        puzzleType = "numberSum",
                        isCorrect = true,
                        userAnswer = "Sum: $currentSum (${selectedTileIds.size} numbers)",
                        correctAnswer = "Target: ${currentConfig.targetSum}",
                        timeSpent = timeSpent,
                        difficulty = currentDifficultyLevel.name,
                        timeRemaining = timeLeft,
                        totalTime = currentDifficultyLevel.timeLimit,
                        onComplete = {
                            // Continue to next puzzle or end session
                            if (correctAttempts >= maxRounds || currentHearts <= 0) {
                                shouldShowSessionEnd = true
                            } else {
                                generateNewPuzzle()
                                puzzleCompleted = false
                            }
                        }
                    )
                } else {
                    // Wrong sum
                    currentHearts = maxOf(0, currentHearts - 1)
                    currentStreak = 0
                    showWrongFeedback = true

                    Log.d(TAG, "❌ Wrong sum! Target: ${currentConfig.targetSum}, Got: $currentSum")

                    recordPerformance(false, timeSpent)

                    numberTiles = numberTiles.map { tile ->
                        if (tile.id in selectedTileIds) {
                            tile.copy(showFeedback = true, feedbackType = FeedbackType.WRONG)
                        } else tile
                    }

                    // ✅ NEW: Show feedback for wrong answers too
                    feedbackManager.showFeedback(
                        puzzleType = "numberSum",
                        isCorrect = false,
                        userAnswer = "Sum: $currentSum (${selectedTileIds.size} numbers)",
                        correctAnswer = "Target: ${currentConfig.targetSum}",
                        timeSpent = timeSpent,
                        difficulty = currentDifficultyLevel.name,
                        timeRemaining = timeLeft,
                        totalTime = currentDifficultyLevel.timeLimit,
                        onComplete = {
                            // Check if game should end
                            if (currentHearts <= 0) {
                                shouldShowSessionEnd = true
                            } else {
                                // Clear wrong selections and continue
                                shouldClearWrongFeedback = true
                            }
                        }
                    )
                }
            }
        }
    }



    // ✅ REMOVED: Old puzzleCompleted LaunchedEffect that was ending the game
    // This was causing the issue - it immediately ended the game after one puzzle

    // Handle wrong feedback auto-clear
    LaunchedEffect(shouldClearWrongFeedback) {
        if (shouldClearWrongFeedback) {
            delay(1500)

            selectedTileIds = listOf()
            currentSum = 0

            numberTiles = numberTiles.map { tile ->
                tile.copy(
                    isSelected = false,
                    showFeedback = false,
                    feedbackType = FeedbackType.NONE
                )
            }

            shouldClearWrongFeedback = false
        }
    }

    // Initialize first puzzle
    LaunchedEffect(Unit) {
        generateNewPuzzle()
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
                shouldShowSessionEnd = true
            }
        }
    }

    // Handle game over
    LaunchedEffect(currentHearts) {
        if (currentHearts == 0 && gameStarted && !gameCompleted) {
            shouldShowSessionEnd = true
        }
    }

    // ✅ UPDATED: Handle session end properly
    LaunchedEffect(shouldShowSessionEnd) {
        if (shouldShowSessionEnd && !gameCompleted) {
            gameCompleted = true
            onGameComplete(correctAttempts > 0, totalScore)
        }
    }

    // Session completion handling
    if (gameCompleted) {
        UnifiedSessionCompletionHandler(
            puzzleType = "numberSum",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAttempts,
                totalAnswers = totalAttempts,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalAttempts > 0) correctAttempts.toFloat() / totalAttempts else 0f,
                totalScore = totalScore,
                averageTimePerPuzzle = if (totalAttempts > 0)
                    ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt() / totalAttempts
                else 0,
                currentStreak = currentStreak,
                individualTimes = emptyList() // Can track individual round times if needed
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            // Session completion handled
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20))
    ) {
        // Use unified header
        AdaptiveUnifiedHeader(
            level = currentLevel,
            streakInfo = streakInfo,
            timer = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
            lives = currentHearts,
            currentDifficulty = currentDifficultyLevel,
            score = totalScore,
            puzzleType = "numberSum",
            competitiveInsight = competitiveInsight,
            challengeNumber = correctAttempts + 1,
            totalChallenges = maxRounds,
            onBack = {
                gameCompleted = true
                onBack()
            },
            onPause = { isPaused = !isPaused },
            onHint = {
                showHint = !showHint
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )

        // Use unified adaptation notification
        UnifiedAdaptationNotification(
            adaptationInfo = adaptationInfo,
            puzzleType = "numberSum",
            visible = showAdaptationNotification,
            onDismiss = { showAdaptationNotification = false }
        )

        // Game Area
        if (!gameCompleted) {
            Spacer(modifier = Modifier.height(16.dp))

            // Target Sum Display with difficulty info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Target",
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = currentConfig.targetSum.toString(),
                        fontSize = 48.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentSum > 0) {
                            Text(
                                text = "Current: $currentSum",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Text(
                            text = "${currentDifficultyLevel.name} • ${currentConfig.correctNumbers.size} numbers",
                            fontSize = 12.sp,
                            color = Color.Cyan,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Number Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(numberTiles) { tile ->
                    AdaptiveNumberTileComponent(
                        tile = tile,
                        onClick = { onNumberClick(tile.id, tile.number, tile.isCorrect) }
                    )
                }
            }
        }

    }

    // Enhanced Universal Feedback - needs to be in BoxScope
    if (!gameCompleted) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            EnhancedUniversalFeedback(feedbackManager)
        }
    }
}


@Composable
fun AdaptiveNumberTileComponent(
    tile: NumberTile,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (tile.isSelected) 0.95f else 1f,
        animationSpec = tween(150)
    )

    val backgroundColor = when {
        tile.isSelected -> Color(0xFF4CAF50)
        tile.showFeedback && tile.feedbackType == FeedbackType.WRONG -> Color.Red
        else -> Color.White
    }

    val textColor = when {
        tile.isSelected -> Color.White
        tile.showFeedback && tile.feedbackType == FeedbackType.WRONG -> Color.White
        else -> Color.Black
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (tile.showFeedback && tile.feedbackType == FeedbackType.WRONG) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.wrong),
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        } else {
            Text(
                text = tile.number.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

// Adaptive configuration data class
data class AdaptiveNumberSumConfig(
    val targetSum: Int,
    val correctNumbers: List<Int>,
    val distractorNumbers: List<Int>,
    val name: String
)

// Generate adaptive configuration based on difficulty level
fun generateAdaptiveNumberSumConfig(difficulty: DifficultyManager.DifficultyLevel): AdaptiveNumberSumConfig {
    return when (difficulty.index) {
        0 -> { // Beginner
            val correctNumbers = listOf(Random.nextInt(1, 6), Random.nextInt(1, 6))
            val targetSum = correctNumbers.sum()
            val distractors = generateAdaptiveDistractors(correctNumbers, targetSum, 2)
            AdaptiveNumberSumConfig(targetSum, correctNumbers, distractors, "Beginner")
        }
        1 -> { // Easy
            val correctNumbers = listOf(Random.nextInt(2, 8), Random.nextInt(2, 8))
            val targetSum = correctNumbers.sum()
            val distractors = generateAdaptiveDistractors(correctNumbers, targetSum, 3)
            AdaptiveNumberSumConfig(targetSum, correctNumbers, distractors, "Easy")
        }
        2 -> { // Medium
            val correctNumbers = listOf(Random.nextInt(2, 8), Random.nextInt(2, 8), Random.nextInt(1, 5))
            val targetSum = correctNumbers.sum()
            val distractors = generateAdaptiveDistractors(correctNumbers, targetSum, 3)
            AdaptiveNumberSumConfig(targetSum, correctNumbers, distractors, "Medium")
        }
        3 -> { // Hard
            val correctNumbers = listOf(
                Random.nextInt(3, 12), Random.nextInt(3, 12),
                Random.nextInt(2, 8), Random.nextInt(1, 6)
            )
            val targetSum = correctNumbers.sum()
            val distractors = generateAdaptiveDistractors(correctNumbers, targetSum, 3)
            AdaptiveNumberSumConfig(targetSum, correctNumbers, distractors, "Hard")
        }
        4 -> { // Expert
            val correctNumbers = listOf(
                Random.nextInt(5, 15), Random.nextInt(5, 15), Random.nextInt(3, 10),
                Random.nextInt(3, 10), Random.nextInt(1, 8)
            )
            val targetSum = correctNumbers.sum()
            val distractors = generateAdaptiveDistractors(correctNumbers, targetSum, 2)
            AdaptiveNumberSumConfig(targetSum, correctNumbers, distractors, "Expert")
        }
        else -> {
            val correctNumbers = listOf(Random.nextInt(2, 8), Random.nextInt(2, 8), Random.nextInt(1, 5))
            val targetSum = correctNumbers.sum()
            val distractors = generateAdaptiveDistractors(correctNumbers, targetSum, 3)
            AdaptiveNumberSumConfig(targetSum, correctNumbers, distractors, "Medium")
        }
    }
}

// Generate adaptive distractor numbers
fun generateAdaptiveDistractors(correctNumbers: List<Int>, targetSum: Int, maxCount: Int): List<Int> {
    val distractors = mutableListOf<Int>()
    val usedNumbers = correctNumbers.toMutableSet()

    repeat(maxCount) {
        var distractor: Int? = null
        var attempts = 0

        while (attempts < 10 && distractor == null) {
            val baseNumber = correctNumbers.random()
            val candidate = when (Random.nextInt(4)) {
                0 -> baseNumber + Random.nextInt(1, 4)
                1 -> maxOf(1, baseNumber - Random.nextInt(1, 4))
                2 -> targetSum + Random.nextInt(1, 5)
                else -> maxOf(1, targetSum - Random.nextInt(1, 8))
            }

            if (!usedNumbers.contains(candidate) && candidate != targetSum && candidate > 0) {
                distractor = candidate
            }
            attempts++
        }

        if (distractor != null) {
            distractors.add(distractor)
            usedNumbers.add(distractor)
        }
    }

    return distractors
}

// Create adaptive number tiles
fun createAdaptiveNumberTiles(config: AdaptiveNumberSumConfig): List<NumberTile> {
    val allNumbers = (config.correctNumbers + config.distractorNumbers).shuffled()
    return allNumbers.mapIndexed { index, number ->
        NumberTile(
            id = index,
            number = number,
            isCorrect = config.correctNumbers.contains(number)
        )
    }
}
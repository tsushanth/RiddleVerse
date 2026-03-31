// AdaptiveTipBubblePuzzleScreen.kt - Enhanced with adaptive difficulty
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import kotlin.random.Random

data class TipBubble(
    val id: String = UUID.randomUUID().toString(),
    val tipAmount: Double,
    val billAmount: Double,
    val isCorrect: Boolean,
    var positionX: Float,
    var positionY: Float,
    val speed: Float = 2f
)

@Composable
fun TipBubblePuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String = "1:30",
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val TAG = "AdaptiveTipBubble"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember { mutableStateOf(difficultyManager.getCurrentDifficulty(puzzleType = "mathtipping")) }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // Generate tip calculation based on current difficulty
    val tipGenerator = remember { AdaptiveTipCalculationGenerator() }
    var currentTipCalculation by remember(currentDifficultyLevel) {
        mutableStateOf(generateTipCalculationFromDifficulty(tipGenerator, currentDifficultyLevel))
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var reactionTime by remember { mutableStateOf(0L) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    var bubbles by remember { mutableStateOf(listOf<TipBubble>()) }
    var gameActive by remember { mutableStateOf(true) }
    var hasAnswered by remember { mutableStateOf(false) }
    var selectedBubbleId by remember { mutableStateOf<String?>(null) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            currentTipCalculation = generateTipCalculationFromDifficulty(tipGenerator, currentDifficultyLevel)
            hasAnswered = false
            gameActive = true
            selectedBubbleId = null
            bubbles = emptyList()
            currentHearts = currentDifficultyLevel.livesAllowed
            gameStartTime = System.currentTimeMillis()
        }
    }

    // Timer tracking
    val totalTimeSeconds = currentDifficultyLevel.timeLimit
    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf("${totalTimeSeconds / 60}:${String.format("%02d", totalTimeSeconds % 60)}") }

    // Enhanced score calculation for tip calculations
    fun calculateAdaptiveTipScore(
        isCorrect: Boolean,
        reactionTimeMs: Long,
        attemptNumber: Int,
        billAmount: Double,
        tipPercentage: Double,
        selectedAmount: Double,
        correctAmount: Double,
        difficulty: DifficultyManager.DifficultyLevel,
        heartsRemaining: Int,
        currentStreak: Int
    ): Int {
        if (!isCorrect) return 0

        val basePoints = difficulty.basePoints

        // Bill complexity bonus (larger bills = more complex calculations)
        val complexityMultiplier = when {
            billAmount >= 100.0 -> 1.8f // Large bills
            billAmount >= 50.0 -> 1.5f  // Medium bills
            billAmount >= 25.0 -> 1.2f  // Small bills
            else -> 1.0f                // Very small bills
        }

        // Tip percentage difficulty bonus
        val tipComplexityBonus = when {
            tipPercentage % 5 != 0.0 -> (basePoints * 0.4f).toInt() // Non-standard percentages (e.g., 18%, 22%)
            tipPercentage >= 25.0 -> (basePoints * 0.2f).toInt()     // High tip percentages
            tipPercentage <= 10.0 -> (basePoints * 0.1f).toInt()     // Low tip percentages
            else -> 0 // Standard percentages (15%, 20%)
        }

        // Streak bonus
        val streakMultiplier = 1f + (currentStreak * 0.12f)

        // Speed bonus (reaction time)
        val reactionSeconds = reactionTimeMs / 1000f
        val speedBonus = when {
            reactionSeconds <= 2f -> (basePoints * 0.5f).toInt() // Very fast
            reactionSeconds <= 4f -> (basePoints * 0.3f).toInt() // Fast
            reactionSeconds <= 6f -> (basePoints * 0.15f).toInt() // Moderate
            else -> 0
        }

        // First attempt bonus
        val attemptBonus = if (attemptNumber == 1) {
            (basePoints * 0.3f).toInt()
        } else {
            maxOf(0, (basePoints * 0.3f * (1f - (attemptNumber - 1) * 0.2f)).toInt())
        }

        // Hearts preservation bonus
        val heartsBonus = heartsRemaining * (basePoints / 6)

        // Mental math bonus for quick calculations
        val mentalMathBonus = (basePoints * 0.2f).toInt()

        val finalScore = ((basePoints * complexityMultiplier * streakMultiplier).toInt() +
                tipComplexityBonus + speedBonus + attemptBonus + heartsBonus + mentalMathBonus)

        return maxOf(finalScore, basePoints / 4)
    }

    fun recordGamePerformance(
        difficultyManager: DifficultyManager,
        isCorrect: Boolean,
        timeSpent: Long,
        streak: Int,
        livesRemaining: Int,
        difficulty: DifficultyManager.DifficultyLevel,
        tipComplexity: Double,
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
            puzzleType = "mathtipping",
            timestamp = System.currentTimeMillis(), // Fixed: Current timestamp
            timeEfficiency = if (timeSpentSeconds > 0f) { // Fixed: Calculate efficiency
                (expectedTimeForDifficulty / timeSpentSeconds).coerceAtMost(2.0f)
            } else {
                1.0f
            }
        )

        val adaptiveConfig = difficultyManager.recordPerformance(performance)
        onAdaptation(adaptiveConfig)
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, hasAnswered) {
        if (timeRemaining > 0 && !hasAnswered) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !hasAnswered) {
            // Time's up - complete with current score
            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            hasAnswered = true
            gameActive = false

            // Record performance for timeout
            recordGamePerformance(
                difficultyManager = difficultyManager,
                isCorrect = false,
                timeSpent = totalTimeSeconds.toLong() * 1000L,
                streak = 0,
                livesRemaining = currentHearts,
                difficulty = currentDifficultyLevel,
                tipComplexity = currentTipCalculation.tipPercentage
            ) { config ->
                adaptationInfo = config
                if (config.confidenceScore > 0.5f) {
                    currentDifficultyLevel = config.level
                    showAdaptationNotification = true
                }
            }

            onSubmitAnswer(false)
            fetchNextPuzzle(totalScore)
        }
    }

    // Bubble generation effect - Generate 4 bubbles with staggered timing
    LaunchedEffect(gameActive, currentTipCalculation) {
        if (gameActive && !hasAnswered && bubbles.isEmpty()) {
            gameStartTime = System.currentTimeMillis() // Reset timing when bubbles appear

            val newBubbles = mutableListOf<TipBubble>()

            // Add one correct bubble
            newBubbles.add(
                TipBubble(
                    tipAmount = currentTipCalculation.correctTip,
                    billAmount = currentTipCalculation.billAmount,
                    isCorrect = true,
                    positionX = 0f,
                    positionY = screenHeight.value + 150f,
                    speed = 3f
                )
            )

            // Add 3 incorrect bubbles with adaptive difficulty-based variations
            repeat(3) {
                val incorrectTipAmount = generateIncorrectTipAmountForDifficulty(
                    currentTipCalculation.correctTip,
                    currentTipCalculation.tipPercentage,
                    currentDifficultyLevel
                )
                newBubbles.add(
                    TipBubble(
                        tipAmount = incorrectTipAmount,
                        billAmount = currentTipCalculation.billAmount,
                        isCorrect = false,
                        positionX = 0f,
                        positionY = screenHeight.value + 150f + (it * 200f),
                        speed = 3f
                    )
                )
            }

            // Shuffle and assign X positions for 4 columns with proper spacing
            val shuffledBubbles = newBubbles.shuffled()
            val bubbleSize = 120f
            val totalWidth = screenWidth.value
            val padding = 20f
            val availableWidth = totalWidth - (2 * padding) - (4 * bubbleSize)
            val spacingBetween = availableWidth / 3f

            bubbles = shuffledBubbles.mapIndexed { index, bubble ->
                val x = padding + index * (bubbleSize + spacingBetween)

                bubble.copy(
                    positionX = x,
                    positionY = screenHeight.value + 150f + (index * 300f)
                )
            }

            Log.d(TAG, "🎯 Generated bubbles - Correct: $${String.format("%.2f", currentTipCalculation.correctTip)} for ${currentTipCalculation.tipPercentage}% of $${currentTipCalculation.billAmount}")
        }
    }

    // Bubble movement effect
    LaunchedEffect(bubbles) {
        while (gameActive && !hasAnswered) {
            delay(16) // ~60 FPS
            bubbles = bubbles.map { bubble ->
                val newY = bubble.positionY - bubble.speed
                if (newY < -150f) {
                    bubble.copy(positionY = screenHeight.value + 150f)
                } else {
                    bubble.copy(positionY = newY)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Enhanced Top Bar with adaptive difficulty info
            AdaptiveTipBubbleTopGameBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer,
                lives = currentHearts,
                currentDifficulty = currentDifficultyLevel,
                totalScore = totalScore,
                attempts = attempts,
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
                            contentDescription = "Difficulty adjusted",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Difficulty Adapted!",
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
                                contentDescription = "Dismiss",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "TAP THE CORRECT TIP",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced bill info display with difficulty indicator
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bill Amount",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = currentDifficultyLevel.name.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Yellow.copy(alpha = 0.8f)
                        )
                    }

                    Text(
                        text = "$${String.format("%.2f", currentTipCalculation.billAmount)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tip: ${currentTipCalculation.tipPercentage.roundToInt()}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Instructions with performance tips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tap the bubble with the correct tip amount!",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                if (totalScore > 0) {
                    Text(
                        text = "⚡ Quick reactions earn bonus points!",
                        fontSize = 12.sp,
                        color = Color.Yellow.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Bubbles Layer
        Box(modifier = Modifier.fillMaxSize()) {
            bubbles.forEach { bubble ->
                TipBubbleComponent(
                    bubble = bubble,
                    isSelected = selectedBubbleId == bubble.id,
                    onBubbleTapped = { tappedBubble ->
                        if (!hasAnswered) {
                            attempts++
                            hasAnswered = true
                            gameActive = false
                            selectedBubbleId = tappedBubble.id
                            reactionTime = System.currentTimeMillis() - gameStartTime
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                            val isCorrect = tappedBubble.isCorrect

                            if (isCorrect) {
                                // Calculate and award score
                                val newStreak = currentStreak + 1
                                val score = calculateAdaptiveTipScore(
                                    isCorrect = true,
                                    reactionTimeMs = reactionTime,
                                    attemptNumber = attempts,
                                    billAmount = currentTipCalculation.billAmount,
                                    tipPercentage = currentTipCalculation.tipPercentage,
                                    selectedAmount = tappedBubble.tipAmount,
                                    correctAmount = currentTipCalculation.correctTip,
                                    difficulty = currentDifficultyLevel,
                                    heartsRemaining = currentHearts,
                                    currentStreak = newStreak
                                )
                                totalScore = score
                                currentStreak = newStreak
                                gamesPlayedThisSession++

                                Log.d(TAG, "✅ Correct selection! Reaction: ${reactionTime}ms, Score: $totalScore")
                            } else {
                                // Wrong selection - lose a heart
                                currentHearts = maxOf(0, currentHearts - 1)
                                currentStreak = 0

                                Log.d(TAG, "❌ Wrong selection. Hearts: $currentHearts")
                            }

                            // Record performance for adaptation
                            recordGamePerformance(
                                difficultyManager = difficultyManager,
                                isCorrect = isCorrect,
                                timeSpent = reactionTime,
                                streak = if (isCorrect) currentStreak else 0,
                                livesRemaining = currentHearts,
                                difficulty = currentDifficultyLevel,
                                tipComplexity = currentTipCalculation.tipPercentage
                            ) { config ->
                                adaptationInfo = config
                                if (config.confidenceScore > 0.5f) {
                                    currentDifficultyLevel = config.level
                                    showAdaptationNotification = true
                                }
                            }

                            feedbackManager.showFeedback(
                                puzzleType = "tipCalculation",
                                isCorrect = isCorrect,
                                userAnswer = "$${String.format("%.2f", tappedBubble.tipAmount)}",
                                correctAnswer = "$${String.format("%.2f", currentTipCalculation.correctTip)}",
                                timeSpent = reactionTime,
                                difficulty = currentDifficultyLevel.name,
                                timeRemaining = timeRemaining,
                                totalTime = totalTimeSeconds,
                                onComplete = {
                                    onSubmitAnswer(isCorrect)
                                    if (isCorrect) {
                                        Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                                        fetchNextPuzzle(totalScore)
                                    } else {
                                        if (currentHearts > 0) {
                                            // Reset for retry
                                            hasAnswered = false
                                            gameActive = true
                                            selectedBubbleId = null
                                            bubbles = emptyList()
                                        } else {
                                            // Game over
                                            fetchNextPuzzle(totalScore)
                                        }
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .offset(
                            x = with(density) { bubble.positionX.toDp() },
                            y = with(density) { bubble.positionY.toDp() }
                        )
                )
            }
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
fun TipBubbleComponent(
    bubble: TipBubble,
    isSelected: Boolean,
    onBubbleTapped: (TipBubble) -> Unit,
    modifier: Modifier = Modifier
) {
    // Subtle floating animation - same for all bubbles
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = tween(300),
        label = "bubble_scale"
    )

    Card(
        modifier = modifier
            .size(120.dp)
            .scale(scale)
            .shadow(8.dp, CircleShape)
            .clickable { onBubbleTapped(bubble) },
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            // Show green only when selected AND correct, red when selected AND incorrect
            containerColor = when {
                isSelected && bubble.isCorrect -> Color(0xFF4CAF50).copy(alpha = 0.9f)
                isSelected && !bubble.isCorrect -> Color(0xFFE53E3E).copy(alpha = 0.9f)
                else -> Color.White.copy(alpha = 0.9f)
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 3.dp else 2.dp,
            color = when {
                isSelected && bubble.isCorrect -> Color(0xFFFFD700) // Gold for correct
                isSelected && !bubble.isCorrect -> Color.Red // Red for incorrect
                else -> Color.Gray.copy(alpha = 0.5f)
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$",
                    fontSize = 16.sp,
                    color = if (isSelected) Color.White else Color.Black,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("%.2f", bubble.tipAmount),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Color.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AdaptiveTipBubbleTopGameBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    totalScore: Int,
    attempts: Int,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Back button and level progression
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Back button
                IconButton(
                    onClick = {
                        onBack?.invoke()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column {
                    Text(
                        text = "${stringResource(R.string.level_label)} ${level.level}",
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

                    // Level progress bar
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

            // Right side: Timer with urgency indicator and streak
            Column(
                horizontalAlignment = Alignment.End
            ) {
                val timeValue = timer.substringAfter(":").toIntOrNull() ?: 0
                val isUrgent = timer.startsWith("0:") && timeValue <= 30

                Text(
                    text = timer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUrgent) Color.Red else Color.White
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

        // Score and progress display
        if (totalScore > 0 || attempts > 0) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (totalScore > 0) {
                        Text(
                            text = "${stringResource(R.string.score_label)}: $totalScore",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (attempts > 0) {
                        Text(
                            text = "Attempts: $attempts",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = "💡 Mental Math",
                        color = Color.Yellow.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// Data class for tip calculations
data class TipCalculation(
    val billAmount: Double,
    val tipPercentage: Double,
    val correctTip: Double,
    val difficulty: String
)

// Adaptive tip calculation generator
class AdaptiveTipCalculationGenerator {
    fun generateForLevel(difficultyLevel: DifficultyManager.DifficultyLevel): TipCalculation {
        val (billRange, tipPercentages) = when (difficultyLevel.name.lowercase()) {
            "tutorial" -> Pair(Pair(10.0, 25.0), listOf(10.0, 15.0, 20.0))
            "beginner" -> Pair(Pair(15.0, 40.0), listOf(10.0, 15.0, 20.0, 25.0))
            "easy" -> Pair(Pair(20.0, 60.0), listOf(10.0, 15.0, 20.0, 25.0))
            "easy+" -> Pair(Pair(25.0, 80.0), listOf(15.0, 18.0, 20.0, 22.0))
            "medium-" -> Pair(Pair(30.0, 100.0), listOf(15.0, 18.0, 20.0, 22.0, 25.0))
            "medium" -> Pair(Pair(40.0, 150.0), listOf(15.0, 18.0, 20.0, 22.0, 25.0))
            "medium+" -> Pair(Pair(50.0, 200.0), listOf(16.0, 18.0, 20.0, 22.0, 24.0))
            "hard-" -> Pair(Pair(75.0, 250.0), listOf(17.0, 19.0, 21.0, 23.0))
            "hard" -> Pair(Pair(100.0, 300.0), listOf(17.0, 19.0, 21.0, 23.0))
            "hard+" -> Pair(Pair(150.0, 400.0), listOf(17.5, 19.5, 21.5, 23.5))
            "expert-" -> Pair(Pair(200.0, 500.0), listOf(16.5, 18.5, 20.5, 22.5))
            "expert" -> Pair(Pair(250.0, 600.0), listOf(16.5, 18.5, 20.5, 22.5, 24.5))
            "expert+" -> Pair(Pair(300.0, 800.0), listOf(17.25, 19.25, 21.25, 23.25))
            "master" -> Pair(Pair(400.0, 1000.0), listOf(17.75, 19.75, 21.75, 23.75))
            "grandmaster" -> Pair(Pair(500.0, 1500.0), listOf(18.25, 19.85, 21.65, 23.45))
            else -> Pair(Pair(40.0, 150.0), listOf(15.0, 18.0, 20.0, 22.0, 25.0))
        }

        val billAmount = Random.nextDouble(billRange.first, billRange.second)
        val tipPercentage = tipPercentages.random()
        val correctTip = (billAmount * tipPercentage / 100.0)

        return TipCalculation(
            billAmount = billAmount,
            tipPercentage = tipPercentage,
            correctTip = correctTip,
            difficulty = difficultyLevel.name
        )
    }
}

fun generateIncorrectTipAmountForDifficulty(
    correctAmount: Double,
    tipPercentage: Double,
    difficultyLevel: DifficultyManager.DifficultyLevel
): Double {
    val variations = when (difficultyLevel.name.lowercase()) {
        "tutorial", "beginner" -> listOf(
            correctAmount * 0.5,        // 50% of correct (easier to spot)
            correctAmount * 1.5,        // 150% of correct
            correctAmount + 5.0         // Add $5
        )
        "easy", "easy+" -> listOf(
            correctAmount * 0.75,       // 75% of correct
            correctAmount * 1.25,       // 125% of correct
            correctAmount + 3.0,        // Add $3
            correctAmount - 2.0         // Subtract $2
        )
        else -> listOf(
            correctAmount * 0.9,        // 90% of correct (harder to spot)
            correctAmount * 1.1,        // 110% of correct
            correctAmount + 1.5,        // Add $1.50
            correctAmount - 1.5,        // Subtract $1.50
            correctAmount * 0.85,       // 85% of correct
            correctAmount * 1.15        // 115% of correct
        )
    }

    return variations.random().coerceAtLeast(0.01) // Ensure positive amount
}

// Helper function to generate tip calculation from difficulty level
fun generateTipCalculationFromDifficulty(
    generator: AdaptiveTipCalculationGenerator,
    difficultyLevel: DifficultyManager.DifficultyLevel
): TipCalculation {
    return generator.generateForLevel(difficultyLevel)
}
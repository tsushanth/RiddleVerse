// AdaptiveSubscriptionPuzzleScreen.kt - Enhanced with adaptive difficulty
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.random.Random
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvMint
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvSkyEdge
import com.kreativekoala.riddleverse.ui.theme.RvSurface
import com.kreativekoala.riddleverse.ui.theme.RvSurfaceRaised

// Data class for subscription puzzles
data class SubscriptionPuzzle(
    val payment: Double,
    val frequency: String,
    val purpose: String,
    val yearlyTotal: Double,
    val options: List<Int>,
    val difficulty: String
)

// Adaptive subscription puzzle generator
class AdaptiveSubscriptionPuzzleGenerator {
    fun generateForLevel(difficultyLevel: DifficultyManager.DifficultyLevel): SubscriptionPuzzle {
        val (paymentRange, frequencies, purposes) = when (difficultyLevel.name.lowercase()) {
            "tutorial" -> Triple(
                Pair(5.0, 25.0),
                listOf("monthly"),
                listOf("Streaming service", "Music app", "Coffee subscription")
            )
            "beginner" -> Triple(
                Pair(10.0, 40.0),
                listOf("monthly", "weekly"),
                listOf("Gym membership", "Magazine", "Online course", "Food delivery")
            )
            "easy" -> Triple(
                Pair(15.0, 60.0),
                listOf("monthly", "weekly", "quarterly"),
                listOf("Software license", "Fitness app", "News subscription", "Cloud storage")
            )
            "easy+" -> Triple(
                Pair(20.0, 80.0),
                listOf("monthly", "weekly", "quarterly", "biweekly"),
                listOf("Professional software", "Training program", "Premium service")
            )
            "medium-" -> Triple(
                Pair(25.0, 100.0),
                listOf("monthly", "weekly", "quarterly", "biweekly", "semi-annual"),
                listOf("Business software", "Consulting service", "Professional membership")
            )
            "medium" -> Triple(
                Pair(30.0, 150.0),
                listOf("monthly", "weekly", "quarterly", "biweekly", "semi-annual"),
                listOf("Enterprise software", "Professional training", "Insurance premium")
            )
            "medium+" -> Triple(
                Pair(40.0, 200.0),
                listOf("monthly", "weekly", "quarterly", "biweekly", "semi-annual", "daily"),
                listOf("Business consulting", "Premium insurance", "Equipment lease")
            )
            "hard-" -> Triple(
                Pair(50.0, 250.0),
                listOf("weekly", "biweekly", "semi-annual", "daily"),
                listOf("Office space rental", "Vehicle lease", "Equipment maintenance")
            )
            "hard" -> Triple(
                Pair(75.0, 300.0),
                listOf("weekly", "biweekly", "semi-annual", "daily"),
                listOf("Commercial lease", "Fleet management", "Enterprise license")
            )
            "hard+" -> Triple(
                Pair(100.0, 400.0),
                listOf("weekly", "biweekly", "daily"),
                listOf("Industrial equipment", "Large-scale software", "Facility rental")
            )
            "expert-" -> Triple(
                Pair(150.0, 500.0),
                listOf("biweekly", "daily"),
                listOf("Heavy machinery lease", "Enterprise infrastructure", "Industrial services")
            )
            "expert" -> Triple(
                Pair(200.0, 750.0),
                listOf("biweekly", "daily"),
                listOf("Major equipment lease", "Large facility rental", "Enterprise contract")
            )
            "expert+" -> Triple(
                Pair(300.0, 1000.0),
                listOf("daily"),
                listOf("Manufacturing equipment", "Large-scale operations", "Major contracts")
            )
            "master" -> Triple(
                Pair(500.0, 1500.0),
                listOf("daily"),
                listOf("Industrial complex", "Major infrastructure", "Enterprise operations")
            )
            "grandmaster" -> Triple(
                Pair(750.0, 2500.0),
                listOf("daily"),
                listOf("Large-scale industrial", "Major enterprise", "Complex operations")
            )
            else -> Triple(
                Pair(30.0, 150.0),
                listOf("monthly", "weekly", "quarterly", "biweekly"),
                listOf("Software subscription", "Service membership", "Professional license")
            )
        }

        val payment = Random.nextDouble(paymentRange.first, paymentRange.second)
        val frequency = frequencies.random()
        val purpose = purposes.random()

        // Calculate yearly total based on frequency
        val yearlyTotal = when (frequency.lowercase()) {
            "daily" -> payment * 365
            "weekly" -> payment * 52
            "biweekly", "bi-weekly" -> payment * 26
            "monthly" -> payment * 12
            "quarterly" -> payment * 4
            "semi-annual", "semiannual" -> payment * 2
            "annual", "yearly" -> payment
            else -> payment * 12
        }

        // Generate answer options with varying difficulty
        val options = generateOptions(yearlyTotal.toInt(), difficultyLevel)

        return SubscriptionPuzzle(
            payment = payment,
            frequency = frequency,
            purpose = purpose,
            yearlyTotal = yearlyTotal,
            options = options,
            difficulty = difficultyLevel.name
        )
    }

    private fun generateOptions(correctAnswer: Int, difficultyLevel: DifficultyManager.DifficultyLevel): List<Int> {
        val variationRange = when (difficultyLevel.name.lowercase()) {
            "tutorial", "beginner" -> 0.3  // 30% variation (easier to spot)
            "easy", "easy+" -> 0.25         // 25% variation
            "medium-", "medium", "medium+" -> 0.2  // 20% variation
            "hard-", "hard", "hard+" -> 0.15       // 15% variation
            else -> 0.1                     // 10% variation (hardest)
        }

        val options = mutableSetOf<Int>()
        options.add(correctAnswer)

        while (options.size < 4) {
            val variation = Random.nextDouble(-variationRange, variationRange)
            val option = (correctAnswer * (1 + variation)).toInt()
            if (option > 0 && option != correctAnswer) {
                options.add(option)
            }
        }

        return options.shuffled()
    }
}

@Composable
fun AdaptiveSubscriptionPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String,
    onSubmitAnswer: (Int) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val TAG = "AdaptiveSubscription"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("subscription"))
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
                    puzzleType = "subscription",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate puzzle based on current difficulty
    val subscriptionGenerator = remember { AdaptiveSubscriptionPuzzleGenerator() }
    var currentPuzzle by remember(currentDifficultyLevel) {
        mutableStateOf(generateSubscriptionPuzzleFromDifficulty(subscriptionGenerator, currentDifficultyLevel))
    }

    // Score and performance tracking
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }
    var usedHint by remember { mutableStateOf(false) }

    // ✅ NEW: Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAnswers by remember { mutableStateOf(0) }

    // Game state
    var selectedAnswer by remember { mutableStateOf<Int?>(null) }
    var isAnswered by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            currentPuzzle = generateSubscriptionPuzzleFromDifficulty(subscriptionGenerator, currentDifficultyLevel)
            isAnswered = false
            selectedAnswer = null
            showHint = false
            usedHint = false
            currentHearts = currentDifficultyLevel.livesAllowed
            gameStartTime = System.currentTimeMillis()
        }
    }

    // Timer calculation and countdown
    val totalTimeSeconds = currentDifficultyLevel.timeLimit
    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf("${totalTimeSeconds / 60}:${String.format("%02d", totalTimeSeconds % 60)}") }

    val correctAnswer = currentPuzzle.yearlyTotal.toInt()

    // ✅ UPDATED: Performance recording function
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = (currentPuzzle.payment / 100).toInt().coerceIn(1, 5),
            totalScore = totalScore,
            puzzleType = "subscription"
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
            challengeComplexity = (currentPuzzle.payment / 100).toInt().coerceIn(1, 5),
            currentStreak = currentStreak,
            challengesCompleted = attempts,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "subscription"
        )
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isAnswered) {
        if (timeRemaining > 0 && !isAnswered) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !isAnswered) {
            // Time's up - calculate final score
            Log.d("AdaptiveSubscription", "⏰ Time's up! Final score: $totalScore")
            isAnswered = true

            // Record performance for timeout
            recordPerformance(false, totalTimeSeconds.toLong() * 1000L)

            fetchNextPuzzle(totalScore)
        }
    }

    LaunchedEffect(showHint) {
        if (showHint) {
            usedHint = true
            delay(5000)
            showHint = false
        }
    }

    SubscriptionFitLayout(
        payment = currentPuzzle.payment.toInt(),
        frequency = currentPuzzle.frequency,
        hud = { tall ->
            if (tall) {
                // Unified header
                AdaptiveUnifiedHeader(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    lives = currentHearts,
                    currentDifficulty = currentDifficultyLevel,
                    score = totalScore,
                    puzzleType = "subscription",
                    competitiveInsight = competitiveInsight,
                    challengeNumber = attempts + 1,
                    totalChallenges = 10, // Or whatever makes sense for subscription puzzles
                    onBack = onBack,
                    onPause = {
                        // Pause functionality could be implemented if needed
                        Log.d(TAG, "Pause requested")
                    },
                    onHint = {
                        showHint = !showHint
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            } else {
                AdaptiveCompactHud(
                    level = currentLevel,
                    timer = displayTimer.let { if (it.indexOf(':') == 1) "0$it" else it },
                    lives = currentHearts,
                    maxLives = currentDifficultyLevel.livesAllowed,
                    score = totalScore,
                    difficultyName = currentDifficultyLevel.name,
                    challengeText = null,
                    onBack = onBack,
                    onPause = null
                )
            }
        },
        status = {
            // Unified adaptation notification (banner intentionally off via SHOW_ADAPTATION_NOTICES)
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "subscription",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentDifficultyLevel.name} • ${currentPuzzle.frequency.capitalize()} → Annual",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = RvInkSoft,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (attempts > 0) {
                    Text(
                        text = "Attempt: $attempts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = RvInkSoft,
                        maxLines = 1
                    )
                }
            }
        },
        options = { m ->
            AnswerOptionsGrid(
                options = currentPuzzle.options,
                selectedAnswer = selectedAnswer,
                isAnswered = isAnswered,
                onAnswerSelected = { answer ->
                    if (!isAnswered) {
                        Log.d("AdaptiveSubscription", "🔥 Answer selected: $answer")

                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedAnswer = answer
                        isAnswered = true
                        attempts++
                        totalAnswers++

                        val timeSpent = totalTimeSeconds - timeRemaining
                        val isCorrect = answer == correctAnswer

                        if (isCorrect) {
                            correctAnswers++
                            currentStreak++
                            val score = calculateScore(isCorrect, timeSpent.toLong())
                            totalScore += score
                            gamesPlayedThisSession++
                            Log.d("AdaptiveSubscription", "✅ Correct! Score: +$score, Total: $totalScore")
                        } else {
                            currentStreak = 0
                            currentHearts = maxOf(0, currentHearts - 1)
                            Log.d("AdaptiveSubscription", "❌ Incorrect. Hearts remaining: $currentHearts")
                        }

                        recordPerformance(isCorrect, timeSpent.toLong() * 1000L)

                        feedbackManager.showFeedback(
                            puzzleType = "subscription",
                            isCorrect = isCorrect,
                            userAnswer = "$$answer",
                            correctAnswer = "$$correctAnswer",
                            timeSpent = timeSpent * 1000L,
                            difficulty = currentDifficultyLevel.name,
                            timeRemaining = timeRemaining,
                            totalTime = totalTimeSeconds,
                            onComplete = {
                                Log.d("AdaptiveSubscription", "🎊 Feedback completed")
                                onSubmitAnswer(answer)

                                if (currentHearts <= 0) {
                                    Log.d("AdaptiveSubscription", "💀 Game over - no hearts remaining")
                                    fetchNextPuzzle(totalScore)
                                } else {
                                    Log.d("AdaptiveSubscription", "🔄 Moving to next puzzle with $currentHearts hearts remaining")
                                    fetchNextPuzzle(totalScore)
                                }
                            }
                        )
                    }
                },
                modifier = m
            )
        },
        hint = {
            AnimatedVisibility(
                visible = showHint,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                AdaptiveSubscriptionHintBubble(
                    frequency = currentPuzzle.frequency,
                    difficulty = currentDifficultyLevel.name,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        overlay = {
        if (currentHearts <= 0) {
            UnifiedSessionCompletionHandler(
                puzzleType = "subscription",
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
                    individualTimes = emptyList() // Can track individual puzzle times if needed
                ),
                currentDifficulty = currentDifficultyLevel
            ) { result ->
                fetchNextPuzzle(totalScore)
            }
        }

            EnhancedUniversalFeedback(feedbackManager)
        }
    )
}

@Composable
fun AdaptiveSubscriptionTopGameBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    onBack: () -> Unit,
    onHintClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.statusBarsPadding().fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left side: Back button, hint button, and level
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
                    contentDescription = stringResource(R.string.back),
                    tint = RvMint,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = onHintClick,
                modifier = Modifier.size(44.dp)
            ) {
                Text(
                    text = "💡",
                    fontSize = 20.sp
                )
            }

            Column {
                Text(
                    text = "${stringResource(R.string.level_label)} ${level.level}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvMint
                )

                // Adaptive difficulty indicator
                Text(
                    text = currentDifficulty.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = RvSkyEdge
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
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true) {
                    Color.Red
                } else {
                    RvMint
                }
            )

            if (streakInfo.currentStreak > 0) {
                StreakDisplay(
                    streakInfo = streakInfo,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AdaptiveSubscriptionHintBubble(
    frequency: String,
    difficulty: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = RvSurfaceRaised
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🔢",
                    fontSize = 16.sp
                )
                Text(
                    text = "Payment Frequency Multipliers",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )
            }

            // Show payment frequency conversions based on difficulty
            when (difficulty.lowercase()) {
                "tutorial", "beginner" -> {
                    SubscriptionHintRow("Monthly", "× 12", "times per year")
                    if (frequency.lowercase().contains("monthly")) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = RvMint.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "💡 Your frequency: Monthly = 12 payments per year",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
                else -> {
                    SubscriptionHintRow("Daily", "× 365", "times per year")
                    SubscriptionHintRow("Weekly", "× 52", "times per year")
                    SubscriptionHintRow("Biweekly", "× 26", "times per year")
                    SubscriptionHintRow("Monthly", "× 12", "times per year")
                    SubscriptionHintRow("Quarterly", "× 4", "times per year")
                    SubscriptionHintRow("Semi-annual", "× 2", "times per year")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "⚠️ Using hints reduces your final score",
                fontSize = 12.sp,
                color = RvInkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Helper function to generate puzzle from difficulty level
fun generateSubscriptionPuzzleFromDifficulty(
    generator: AdaptiveSubscriptionPuzzleGenerator,
    difficultyLevel: DifficultyManager.DifficultyLevel
): SubscriptionPuzzle {
    return generator.generateForLevel(difficultyLevel)
}
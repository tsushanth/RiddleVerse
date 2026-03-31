package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SubscriptionPuzzleScreen(
    difficulty: String = "Medium",
    timer: String, // e.g., "1:23"
    payment: Double, // e.g., 21.49 - from JSON
    frequency: String, // e.g., "biweekly" - from JSON
    purpose: String, // e.g., "Gym membership" - from JSON
    yearlyTotal: Double, // e.g., 558.74 - from JSON (correct answer)
    options: List<Int>, // e.g., [447, 558, 670, 335] - generated
    onSubmitAnswer: (Int) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: (() -> Unit)? = null
) {
    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var usedHint by remember { mutableStateOf(false) }

    // Game state
    var selectedAnswer by remember { mutableStateOf<Int?>(null) }
    var isAnswered by remember { mutableStateOf(false) }
    var isProcessingAnswer by remember { mutableStateOf(false) }  // Prevent double-clicks
    var showHint by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Timer calculation and countdown
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            120 // Default 2 minutes for subscription calculations
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    val correctAnswer = yearlyTotal.toInt()

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isAnswered) {
        if (timeRemaining > 0 && !isAnswered) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !isAnswered) {
            // Time's up - complete with current score
            Log.d("SubscriptionPuzzle", "⏰ Time's up! Final score: $totalScore")
            fetchNextPuzzle(totalScore)
        }
    }

    // Calculate score based on financial math performance
    fun calculateScore(isCorrect: Boolean, timeSpent: Int, attemptNumber: Int, hintUsed: Boolean): Int {
        if (!isCorrect) return 0

        // Base points by difficulty
        val basePoints = when (difficulty.lowercase()) {
            "easy" -> 40
            "medium" -> 60
            "hard" -> 80
            "expert" -> 100
            else -> 60
        }

        // Financial complexity bonus based on payment amount and frequency
        val complexityMultiplier = when {
            payment >= 100.0 -> 1.4f // High payment amounts
            payment >= 50.0 -> 1.2f
            payment >= 20.0 -> 1.1f
            else -> 1.0f
        }

        // Frequency complexity bonus
        val frequencyBonus = when (frequency.lowercase()) {
            "daily" -> (basePoints * 0.4f).toInt() // Most complex
            "weekly" -> (basePoints * 0.3f).toInt()
            "biweekly", "bi-weekly" -> (basePoints * 0.2f).toInt()
            "monthly" -> (basePoints * 0.1f).toInt()
            "quarterly" -> (basePoints * 0.15f).toInt()
            "semi-annual", "semiannual" -> (basePoints * 0.2f).toInt()
            "annual", "yearly" -> (basePoints * 0.05f).toInt() // Simplest
            else -> 0
        }

        // Time efficiency bonus (faster calculation = bonus)
        val timeBonus = when {
            timeSpent <= totalTimeSeconds * 0.25 -> (basePoints * 0.6f).toInt() // Very fast
            timeSpent <= totalTimeSeconds * 0.5 -> (basePoints * 0.4f).toInt() // Fast
            timeSpent <= totalTimeSeconds * 0.75 -> (basePoints * 0.2f).toInt() // Moderate
            else -> 0
        }

        // Attempt penalty (first try = no penalty)
        val attemptPenalty = when (attemptNumber) {
            1 -> 0
            2 -> (basePoints * 0.25f).toInt()
            3 -> (basePoints * 0.5f).toInt()
            else -> (basePoints * 0.75f).toInt()
        }

        // Hint penalty
        val hintPenalty = if (hintUsed) (basePoints * 0.2f).toInt() else 0

        // Financial literacy bonus
        val literacyBonus = (basePoints * 0.25f).toInt()

        val finalScore = ((basePoints * complexityMultiplier).toInt() + frequencyBonus + timeBonus + literacyBonus - attemptPenalty - hintPenalty)

        Log.d("SubscriptionPuzzle", "🏆 Score calculation:")
        Log.d("SubscriptionPuzzle", "  Payment: $payment $frequency")
        Log.d("SubscriptionPuzzle", "  Base points: $basePoints")
        Log.d("SubscriptionPuzzle", "  Complexity multiplier: ${complexityMultiplier}x")
        Log.d("SubscriptionPuzzle", "  Frequency bonus: $frequencyBonus")
        Log.d("SubscriptionPuzzle", "  Time bonus: $timeBonus (${timeSpent}s)")
        Log.d("SubscriptionPuzzle", "  Literacy bonus: $literacyBonus")
        Log.d("SubscriptionPuzzle", "  Attempt penalty: $attemptPenalty")
        Log.d("SubscriptionPuzzle", "  Hint penalty: $hintPenalty")
        Log.d("SubscriptionPuzzle", "  Final score: $finalScore")

        return maxOf(finalScore, basePoints / 4) // Minimum 25% of base points
    }

    fun resetState() {
        isAnswered = false
        selectedAnswer = null
        gameStartTime = System.currentTimeMillis()
        // Don't reset score - keep for cumulative tracking
    }

    LaunchedEffect(showHint) {
        if (showHint) {
            usedHint = true // Track hint usage for scoring
            delay(5000) // 5 seconds
            showHint = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A2E),
                        Color(0xFF1A1A4A),
                        Color(0xFF2D2D5F)
                    )
                )
            )
    ) {
        // Animated star background
        AnimatedStarField()

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Top Bar with live timer
            EnhancedSubscriptionTopGameBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer, // Use live countdown timer
                onBack = onBack,
                onHintClick = {
                    showHint = !showHint
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                modifier = Modifier.padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            // Score display (if any score accumulated)
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
                            Text(
                                text = "${stringResource(R.string.score_label)}: $totalScore",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4ECDC4)
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

                        // Show calculation complexity info
                        Text(
                            text = "${frequency.capitalize()} → ${yearlyTotal.toInt()}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            } else {
                Spacer(modifier = Modifier.height(40.dp))
            }

            // Game type icons row
            GameTypeIconsRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Concert tickets icon
            TicketsIcon(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Payment display - show actual payment from JSON
            PriceDisplay(
                price = payment.toInt(), // 21.49 -> $21
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Calculation prompt - show actual frequency from JSON
            CalculationPrompt(
                frequency = frequency.uppercase(), // "biweekly" -> "BIWEEKLY"
                period = "A YEAR", // Always "ANNUALLY" since we're calculating yearly total
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            AnimatedVisibility(
                visible = showHint,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                SubscriptionHintBubble(
                    frequency = frequency,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "ANNUALLY IS:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4ECDC4),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Answer options grid with enhanced interaction and double-click prevention
            AnswerOptionsGrid(
                options = options,
                selectedAnswer = selectedAnswer,
                isAnswered = isAnswered,
                onAnswerSelected = { answer ->
                    // Prevent double-clicks and race conditions
                    if (!isAnswered && !isProcessingAnswer) {
                        isProcessingAnswer = true  // Lock immediately
                        Log.d("SubscriptionPuzzle", "Answer selected: $answer")

                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedAnswer = answer
                        isAnswered = true
                        attempts++

                        val timeSpent = totalTimeSeconds - timeRemaining
                        val isCorrect = answer == correctAnswer

                        // Calculate score for this attempt
                        val attemptScore = calculateScore(isCorrect, timeSpent, attempts, usedHint)

                        if (isCorrect) {
                            totalScore = totalScore + attemptScore
                            Log.d("SubscriptionPuzzle", "✅ Correct! Final score: $totalScore (+$attemptScore)")
                        } else {
                            Log.d("SubscriptionPuzzle", "❌ Incorrect. Answer: $answer, Expected: $correctAnswer")
                        }

                        feedbackManager.showFeedback(
                            puzzleType = "purchasing",
                            isCorrect = isCorrect,
                            userAnswer = "$$answer",
                            correctAnswer = "$$correctAnswer",
                            timeSpent = timeSpent * 1000L, // Convert to milliseconds
                            difficulty = difficulty,
                            timeRemaining = timeRemaining,
                            totalTime = totalTimeSeconds,
                            onComplete = {
                                Log.d("SubscriptionPuzzle", "🎊 Feedback completed")
                                onSubmitAnswer(answer)
                                if (isCorrect) {
                                    Log.d("SubscriptionPuzzle", "🎯 Calling fetchNextPuzzle with score: $totalScore")
                                    fetchNextPuzzle(totalScore) // Pass accumulated score
                                } else {
                                    Log.d("SubscriptionPuzzle", "🔄 Incorrect answer, moving to next...")
                                    fetchNextPuzzle(totalScore) // Still pass current score
                                }
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Enhanced progress bar with visual feedback
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 30.dp)
                    .height(6.dp)
                    .background(
                        Color.White.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(3.dp)
                    )
            ) {
                // Progress indicator based on selection
                if (selectedAnswer != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f) // Show 80% progress when answered
                            .fillMaxHeight()
                            .background(
                                Color(0xFF4ECDC4),
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}

// Enhanced top bar with live timer
@Composable
fun EnhancedSubscriptionTopGameBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    onBack: (() -> Unit)?,
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
            // Back button
            IconButton(
                onClick = {
                    Log.d("SubscriptionPuzzle", "🔙 Back button clicked")
                    onBack?.invoke() ?: run {
                        Log.d("SubscriptionPuzzle", "⚠️ No back action provided")
                    }
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color(0xFF4ECDC4),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Hint button
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
                    color = Color(0xFF4ECDC4)
                )

                // Level progress bar
                LevelProgressBar(
                    level = level,
                    modifier = Modifier.width(100.dp)
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
                    Color(0xFF4ECDC4)
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

@Composable
fun GameTypeIconsRow(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Card games icon
        Text(
            text = "🎴",
            fontSize = 32.sp,
            color = Color.White.copy(alpha = 0.6f)
        )

        // Scissors icon
        Text(
            text = "✂️",
            fontSize = 32.sp,
            color = Color.White.copy(alpha = 0.6f)
        )

        // Theater masks icon
        Text(
            text = "🎭",
            fontSize = 32.sp,
            color = Color.White.copy(alpha = 0.6f)
        )

        // Diamond icon
        Text(
            text = "💎",
            fontSize = 32.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun TicketsIcon(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Animated ticket icon
        Text(
            text = "🎟️",
            fontSize = 80.sp,
            modifier = Modifier
                .animateContentSize()
        )
    }
}

@Composable
fun PriceDisplay(
    price: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(80.dp)
            .padding(horizontal = 40.dp)
            .background(
                Color(0xFF2D2D5F).copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$$price",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4ECDC4)
        )
    }
}

@Composable
fun CalculationPrompt(
    frequency: String,
    period: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2D2D5F).copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            Color(0xFF4ECDC4)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        Color(0xFF4ECDC4).copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = frequency,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// Enhanced answer options with better visual feedback
@Composable
fun AnswerOptionsGrid(
    options: List<Int>,
    selectedAnswer: Int?,
    isAnswered: Boolean,
    onAnswerSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // First row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnswerOption(
                amount = options[0],
                isSelected = selectedAnswer == options[0],
                isAnswered = isAnswered,
                onClick = { onAnswerSelected(options[0]) },
                modifier = Modifier.weight(1f)
            )

            AnswerOption(
                amount = options[1],
                isSelected = selectedAnswer == options[1],
                isAnswered = isAnswered,
                onClick = { onAnswerSelected(options[1]) },
                modifier = Modifier.weight(1f)
            )
        }

        // Second row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnswerOption(
                amount = options[2],
                isSelected = selectedAnswer == options[2],
                isAnswered = isAnswered,
                onClick = { onAnswerSelected(options[2]) },
                modifier = Modifier.weight(1f)
            )

            AnswerOption(
                amount = options[3],
                isSelected = selectedAnswer == options[3],
                isAnswered = isAnswered,
                onClick = { onAnswerSelected(options[3]) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AnswerOption(
    amount: Int,
    isSelected: Boolean,
    isAnswered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(enabled = !isAnswered) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isAnswered && isSelected -> Color(0xFF4ECDC4).copy(alpha = 0.5f)
                isSelected -> Color(0xFF4ECDC4).copy(alpha = 0.3f)
                isAnswered -> Color(0xFF2D2D5F).copy(alpha = 0.4f)
                else -> Color(0xFF2D2D5F).copy(alpha = 0.8f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF4ECDC4))
        } else {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isAnswered) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.3f)
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$$amount",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAnswered && !isSelected) {
                    Color.White.copy(alpha = 0.6f)
                } else {
                    Color.White
                }
            )
        }
    }
}

@Composable
fun AnimatedStarField() {
    // Simple star background using Canvas or decorative elements
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // You can implement star animations here
        // For now, just a decorative overlay
    }
}

// Enhanced subscription hint bubble with scoring information
@Composable
fun SubscriptionHintBubble(
    frequency: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
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
                    color = Color(0xFF333333)
                )
            }

            // Show payment frequency conversions
            SubscriptionHintRow("Daily", "× 365", "times per year")
            SubscriptionHintRow("Weekly", "× 52", "times per year")
            SubscriptionHintRow("Biweekly", "× 26", "times per year")
            SubscriptionHintRow("Monthly", "× 12", "times per year")
            SubscriptionHintRow("Quarterly", "× 4", "times per year")
            SubscriptionHintRow("Semi-annual", "× 2", "times per year")

            // Highlight current frequency if recognizable
            if (frequency.lowercase().contains("biweekly") || frequency.lowercase().contains("bi-weekly")) {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4ECDC4).copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "💡 Your frequency: Biweekly = 26 payments per year",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D2D5F),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Scoring note
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "⚠️ Using hints reduces your final score",
                fontSize = 10.sp,
                color = Color(0xFF999999),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SubscriptionHintRow(
    frequency: String,
    multiplier: String,
    note: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = frequency,
            fontSize = 12.sp,
            color = Color(0xFF666666),
            modifier = Modifier.weight(2f)
        )
        Text(
            text = multiplier,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = note,
            fontSize = 10.sp,
            color = Color(0xFF999999),
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End
        )
    }
}

// Preview for development
@Composable
fun SubscriptionPuzzlePreview() {
    SubscriptionPuzzleScreen(
        difficulty = "Medium",
        timer = "1:23",
        payment = 21.49,
        frequency = "biweekly",
        purpose = "Gym membership",
        yearlyTotal = 558.74,
        options = listOf(447, 558, 670, 335),
        onSubmitAnswer = { answer ->
            println("Answer: $answer")
        },
        fetchNextPuzzle = { score ->
            println("Next puzzle with score: $score")
        },
        onBack = {
            println("Back pressed")
        }
    )
}
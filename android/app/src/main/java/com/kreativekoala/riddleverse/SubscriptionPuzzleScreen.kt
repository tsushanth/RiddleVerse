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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.kreativekoala.riddleverse.ui.theme.RvViolet
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvMint
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvSurface
import com.kreativekoala.riddleverse.ui.theme.RvSurfaceRaised

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

    SubscriptionFitLayout(
        payment = payment.toInt(), // 21.49 -> $21
        frequency = frequency, // "biweekly" -> "BIWEEKLY"
        hud = { _ ->
            EnhancedSubscriptionTopGameBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer, // Use live countdown timer
                onBack = onBack,
                onHintClick = {
                    showHint = !showHint
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                modifier = Modifier.padding(top = 4.dp)
            )
        },
        status = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.score_label)}: $totalScore",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    maxLines = 1,
                    modifier = Modifier.testTag("hud_score")
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
                modifier = m
            )
        },
        hint = {
            AnimatedVisibility(
                visible = showHint,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                SubscriptionHintBubble(frequency = frequency, modifier = Modifier.fillMaxWidth())
            }
        },
        overlay = { EnhancedUniversalFeedback(feedbackManager) }
    )
}

/**
 * Shared fit-to-screen skeleton for the subscription screens (plain + adaptive): HUD on top,
 * price + "BIWEEKLY A YEAR" prompt, and the 2x2 answer grid filling the remaining space.
 * Landscape / wide: prompt on the left, answers on the right. Never scrolls.
 */
@Composable
internal fun SubscriptionFitLayout(
    payment: Int,
    frequency: String,
    hud: @Composable (tall: Boolean) -> Unit,
    status: @Composable () -> Unit,
    options: @Composable (Modifier) -> Unit,
    hint: @Composable () -> Unit,
    overlay: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvSurface)
    ) {
        val wide = maxWidth > maxHeight || maxWidth >= 600.dp
        val tall = !wide && maxHeight >= 780.dp
        val compact = !tall
        val pad = if (compact) 8.dp else 16.dp

        val problem: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("subscription_problem"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)
            ) {
                PriceDisplay(price = payment, modifier = Modifier.fillMaxWidth())
                CalculationPrompt(
                    frequency = frequency.uppercase(),
                    period = "A YEAR",
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "ANNUALLY IS:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(pad),
                horizontalArrangement = Arrangement.spacedBy(pad)
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    hud(false)
                    status()
                    Spacer(Modifier.weight(1f))
                    problem()
                    Spacer(Modifier.weight(1f))
                }
                options(Modifier.weight(1f).fillMaxHeight().widthIn(max = 480.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = pad)
                    .padding(bottom = pad)
                    .widthIn(max = 640.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)
            ) {
                hud(tall)
                status()
                if (tall) {
                    GameTypeIconsRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp))
                    TicketsIcon(modifier = Modifier.fillMaxWidth().height(96.dp))
                }
                problem()
                options(Modifier.fillMaxWidth().weight(1f))
            }
        }

        // Hint floats over the play area instead of pushing the answers off screen.
        Box(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp).widthIn(max = 480.dp)
        ) { hint() }
        overlay()
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Back button, hint button, and level
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Back button
            IconButton(
                onClick = {
                    Log.d("SubscriptionPuzzle", "🔙 Back button clicked")
                    onBack?.invoke() ?: run {
                        Log.d("SubscriptionPuzzle", "⚠️ No back action provided")
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = RvInk,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Hint button
            IconButton(
                onClick = onHintClick,
                modifier = Modifier.size(48.dp)
            ) {
                val hintLabel = stringResource(R.string.hint)
                Text(
                    text = "💡",
                    fontSize = 22.sp,
                    modifier = Modifier.semantics { contentDescription = hintLabel }
                )
            }

            Column {
                Text(
                    text = "${stringResource(R.string.level_label)} ${level.level}",
                    fontSize = aCapSp(18f, 1.15f),
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Level progress bar (label omitted: the level is already shown above)
                LevelProgressBar(
                    level = level,
                    modifier = Modifier.width(72.dp),
                    showLabel = false
                )
            }
        }

        // Right side: Timer with color coding and streak
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = timer,
                fontSize = aCapSp(24f, 1.15f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.testTag("hud_timer"),
                color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true) {
                    Color.Red // Red when ≤30 seconds
                } else {
                    RvInk
                }
            )

            // Streak display
            if (streakInfo.currentStreak > 0) {
                StreakDisplay(
                    streakInfo = streakInfo,
                    modifier = Modifier.padding(top = 2.dp)
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
        Text(text = "🎴", fontSize = 28.sp, color = RvInkSoft)
        Text(text = "✂️", fontSize = 28.sp, color = RvInkSoft)
        Text(text = "🎭", fontSize = 28.sp, color = RvInkSoft)
        Text(text = "💎", fontSize = 28.sp, color = RvInkSoft)
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
            fontSize = 64.sp,
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
            .heightIn(min = 56.dp)
            .background(RvSurfaceRaised, shape = RoundedCornerShape(12.dp))
            .border(1.dp, RvOutline, RoundedCornerShape(12.dp))
            .testTag("subscription_price"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$$price",
            fontSize = aCapSp(36f, 1.3f),
            fontWeight = FontWeight.Bold,
            color = RvInk,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 4.dp)
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
        modifier = modifier.heightIn(min = 56.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvViolet)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 420.dp) {
                // Narrow: stack "BIWEEKLY" over "A YEAR" so nothing is squeezed.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = frequency,
                        fontSize = aCapSp(24f, 1.2f),
                        fontWeight = FontWeight.Bold,
                        color = RvViolet,
                        maxLines = 1
                    )
                    Text(
                        text = period,
                        fontSize = aCapSp(18f, 1.2f),
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        maxLines = 1
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = frequency,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvViolet,
                            maxLines = 1
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = period,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// Enhanced answer options with better visual feedback. Rows share the height the parent gives.
@Composable
fun AnswerOptionsGrid(
    options: List<Int>,
    selectedAnswer: Int?,
    isAnswered: Boolean,
    onAnswerSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val gap = if (maxHeight < 260.dp) 8.dp else 12.dp
        val rowCount = ((options.size + 1) / 2).coerceAtLeast(1)
        val optH = if (constraints.hasBoundedHeight) {
            ((maxHeight - gap * (rowCount - 1)) / rowCount).coerceIn(56.dp, 96.dp)
        } else 80.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            options.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    rowOptions.forEach { amount ->
                        AnswerOption(
                            amount = amount,
                            isSelected = selectedAnswer == amount,
                            isAnswered = isAnswered,
                            onClick = { onAnswerSelected(amount) },
                            modifier = Modifier.weight(1f).height(optH)
                        )
                    }
                    if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                }
            }
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
            .heightIn(min = 56.dp)
            .clickable(enabled = !isAnswered) { onClick() }
            .testTag("subscription_option"),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> RvViolet.copy(alpha = 0.15f)
                isAnswered -> RvSurface
                else -> RvSurfaceRaised
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(3.dp, RvViolet)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, if (isAnswered) RvOutline else RvInkSoft)
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$$amount",
                fontSize = aCapSp(24f, 1.3f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = if (isAnswered && !isSelected) RvInkSoft else RvInk
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
                        containerColor = RvMint.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "💡 Your frequency: Biweekly = 26 payments per year",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Scoring note
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
            fontSize = 14.sp,
            color = RvInkSoft,
            modifier = Modifier.weight(2f)
        )
        Text(
            text = multiplier,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = RvInk,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = note,
            fontSize = 12.sp,
            color = RvInkSoft,
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
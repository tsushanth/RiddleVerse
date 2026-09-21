package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*

// Changes needed in SubtractionScreen.kt

@Composable
fun MathDifferenceGameScreen(
    difficulty: String = "Medium",
    timer: String = "1:00",
    hearts: Int = 3,
    level: String, // Keep level indicator (e.g., "3/7")
    number1: Int,
    number2: Int,
    onSubmitAnswer: (Int) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: (() -> Unit)? = null
) {
    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Game state
    var currentInput by remember { mutableStateOf("") }
    var isAnswered by remember { mutableStateOf(false) }

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
            60 // Default 1 minute for subtraction
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    val correctAnswer = abs(number1 - number2)

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isAnswered) {
        if (timeRemaining > 0 && !isAnswered) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !isAnswered) {
            // Time's up - complete with current score
            isAnswered = true
            Log.d("SubtractionScreen", "⏰ Time's up! Final score: $totalScore")
            fetchNextPuzzle(totalScore)
        }
    }

    // Calculate score based on performance factors
    fun calculateScore(isCorrect: Boolean, timeSpent: Int, attemptNumber: Int): Int {
        if (!isCorrect) return 0

        // Base points by difficulty
        val basePoints = when (difficulty.lowercase()) {
            "easy" -> 30
            "medium" -> 50
            "hard" -> 70
            "expert" -> 90
            else -> 50
        }

        // Complexity bonus based on number size
        val complexity = maxOf(number1, number2)
        val complexityMultiplier = when {
            complexity >= 1000 -> 1.5f
            complexity >= 500 -> 1.3f
            complexity >= 100 -> 1.2f
            complexity >= 50 -> 1.1f
            else -> 1.0f
        }

        // Time bonus (faster completion = more points)
        val timeBonus = when {
            timeSpent <= totalTimeSeconds * 0.25 -> basePoints * 0.8f // 80% bonus for completing in 25% time
            timeSpent <= totalTimeSeconds * 0.5 -> basePoints * 0.6f  // 60% bonus for completing in 50% time
            timeSpent <= totalTimeSeconds * 0.75 -> basePoints * 0.4f // 40% bonus for completing in 75% time
            else -> 0f
        }.toInt()

        // Attempt penalty (first try = no penalty)
        val attemptPenalty = when (attemptNumber) {
            1 -> 0
            2 -> basePoints * 0.25 // 25% penalty for second attempt
            3 -> basePoints * 0.5  // 50% penalty for third attempt
            else -> basePoints * 0.75 // 75% penalty for 4+ attempts
        }.toInt()

        // Accuracy bonus for mental math
        val accuracyBonus = (basePoints * 0.2f).toInt() // 20% bonus for correct calculation

        val finalScore = ((basePoints * complexityMultiplier).toInt() + timeBonus + accuracyBonus - attemptPenalty)

        Log.d("SubtractionScreen", "🏆 Score calculation:")
        Log.d("SubtractionScreen", "  Problem: $number1 - $number2 = $correctAnswer")
        Log.d("SubtractionScreen", "  Base points: $basePoints")
        Log.d("SubtractionScreen", "  Complexity multiplier: ${complexityMultiplier}x")
        Log.d("SubtractionScreen", "  Time bonus: $timeBonus (time spent: ${timeSpent}s)")
        Log.d("SubtractionScreen", "  Accuracy bonus: $accuracyBonus")
        Log.d("SubtractionScreen", "  Attempt penalty: $attemptPenalty (attempt #$attemptNumber)")
        Log.d("SubtractionScreen", "  Final score: $finalScore")

        return maxOf(finalScore, basePoints / 4) // Minimum 25% of base points
    }

    // Reset state function
    fun resetState() {
        isAnswered = false
        currentInput = ""
        gameStartTime = System.currentTimeMillis()
        // Don't reset score or hearts - keep for cumulative tracking
    }

    // Fit-to-screen: HUD on top, problem + calculator take the remaining space, no scrolling.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        val wide = maxWidth > maxHeight || maxWidth >= 600.dp
        val compact = maxHeight < 700.dp
        val pad = if (compact) 8.dp else 16.dp

        // Animated background pattern
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAnimatedPattern(size)
        }

        val hud: @Composable () -> Unit = {
            EnhancedSubtractionTopGameBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer, // Use live countdown timer
                hearts = currentHearts, // Use current hearts instead of initial hearts
                roundLevel = level,
                onBack = onBack,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        val scoreLine: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Score: $totalScore",
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
        }
        val problem: @Composable () -> Unit = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "FIND THE DIFFERENCE",
                    fontSize = if (compact) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(if (compact) 4.dp else 12.dp))
                if (compact || wide) {
                    // Single line: 123 − 45
                    Text(
                        text = "$number1 − $number2",
                        fontSize = aCapSp(if (compact) 36f else 44f, 1.3f),
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        maxLines = 1,
                        modifier = Modifier.testTag("subtraction_problem")
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.testTag("subtraction_problem")
                    ) {
                        Text(text = number1.toString(), fontSize = 42.sp, fontWeight = FontWeight.Bold, color = RvInk)
                        Text(text = "−", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = RvInkSoft)
                        Text(text = number2.toString(), fontSize = 42.sp, fontWeight = FontWeight.Bold, color = RvInk)
                    }
                }
            }
        }
        val calculator: @Composable (Modifier) -> Unit = { m ->
            // Calculator grid with enhanced submit logic
            CalculatorGrid(
                currentInput = currentInput,
                isAnswered = isAnswered,
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
                onSubmit = {
                    if (!isAnswered && currentInput.isNotEmpty()) {
                        Log.d("SubtractionScreen", "🔥 Submit clicked - input: $currentInput")
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val answer = currentInput.toIntOrNull() ?: 0
                        isAnswered = true
                        attempts++

                        val timeSpent = totalTimeSeconds - timeRemaining
                        val isCorrect = answer == correctAnswer

                        // Calculate score for this attempt
                        val attemptScore = calculateScore(isCorrect, timeSpent, attempts)

                        if (isCorrect) {
                            totalScore = totalScore + attemptScore
                            Log.d("SubtractionScreen", "✅ Correct! Final score: $totalScore (+$attemptScore)")
                        } else {
                            currentHearts = maxOf(0, currentHearts - 1)
                            Log.d("SubtractionScreen", "❌ Incorrect. Hearts remaining: $currentHearts")

                            // If no hearts left, end the game
                            if (currentHearts == 0) {
                                Log.d("SubtractionScreen", "💔 No hearts remaining. Game over with score: $totalScore")
                                fetchNextPuzzle(totalScore)
                                return@CalculatorGrid
                            }
                        }

                        Log.d("SubtractionScreen", "🎯 Answer: $answer, Correct: $isCorrect, Expected: $correctAnswer")

                        feedbackManager.showFeedback(
                            puzzleType = "math",
                            isCorrect = isCorrect,
                            userAnswer = answer.toString(),
                            correctAnswer = correctAnswer.toString(),
                            timeSpent = timeSpent * 1000L, // Convert to milliseconds
                            difficulty = difficulty,
                            timeRemaining = timeRemaining,
                            totalTime = totalTimeSeconds,
                            onComplete = {
                                Log.d("SubtractionScreen", "🎊 Feedback completed")
                                onSubmitAnswer(answer)

                                // FIXED: Always move forward after feedback, regardless of correct/incorrect
                                Log.d("SubtractionScreen", "🎯 Moving to next puzzle with score: $totalScore")
                                fetchNextPuzzle(totalScore) // Always move forward with total score
                            }
                        )
                    } else {
                        Log.w("SubtractionScreen", "⚠️ Cannot submit - isAnswered: $isAnswered, input: '$currentInput'")
                    }
                },
                modifier = m
            )
        }

        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    hud()
                    scoreLine()
                    Spacer(Modifier.weight(1f))
                    problem()
                    Spacer(Modifier.weight(1f))
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    calculator(Modifier.fillMaxSize().widthIn(max = 360.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = pad)
                    .padding(bottom = pad)
                    .widthIn(max = 480.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
            ) {
                hud()
                scoreLine()
                problem()
                calculator(Modifier.fillMaxWidth().weight(1f))
            }
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}

// Enhanced Calculator Grid with visual state management.
// Also used by AdaptiveSubtractionPuzzleScreen. Key sizes derive from the space given: when the
// parent gives a bounded height the keys share it (48-88dp tall); otherwise they stay square.
@Composable
fun CalculatorGrid(
    currentInput: String,
    isAnswered: Boolean = false,
    onNumberClick: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = if (constraints.hasBoundedHeight && maxHeight < 360.dp) 8.dp else 12.dp
        val displayH = if (constraints.hasBoundedHeight && maxHeight < 360.dp) 48.dp else 60.dp
        val keyH: androidx.compose.ui.unit.Dp? = if (constraints.hasBoundedHeight) {
            ((maxHeight - displayH - gap * 4) / 4).coerceIn(48.dp, 88.dp)
        } else null
        val keyModifier: (Modifier) -> Modifier = { m ->
            if (keyH != null) m.height(keyH) else m.aspectRatio(1f)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            // Display current input with state-aware styling
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(displayH)
                    .testTag("calc_display"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAnswered) RvSurface else RvSurfaceRaised
                ),
                border = androidx.compose.foundation.BorderStroke(2.dp, if (isAnswered) RvOutline else RvInkSoft)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentInput.isEmpty()) {
                            if (isAnswered) "Submitted" else "0"
                        } else {
                            currentInput
                        },
                        fontSize = aCapSp(24f, 1.3f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = if (isAnswered) RvInkSoft else RvInk
                    )
                }
            }

            // Number grid (1-9)
            for (row in 0..2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    for (col in 0..2) {
                        val number = row * 3 + col + 1
                        CalculatorButton(
                            text = number.toString(),
                            onClick = { onNumberClick(number.toString()) },
                            modifier = keyModifier(Modifier.weight(1f)),
                            isEnabled = !isAnswered
                        )
                    }
                }
            }

            // Bottom row (Clear, 0, Submit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                CalculatorButton(
                    text = "✕",
                    onClick = onClear,
                    modifier = keyModifier(Modifier.weight(1f)),
                    isSpecial = true,
                    isEnabled = !isAnswered
                )

                CalculatorButton(
                    text = "0",
                    onClick = { onNumberClick("0") },
                    modifier = keyModifier(Modifier.weight(1f)),
                    isEnabled = !isAnswered
                )

                CalculatorButton(
                    text = if (isAnswered) "SENT" else "SUBMIT",
                    onClick = onSubmit,
                    modifier = keyModifier(Modifier.weight(1f)),
                    isSpecial = true,
                    isEnabled = !isAnswered
                )
            }
        }
    }
}

// Enhanced Calculator Button with state management
@Composable
fun CalculatorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSpecial: Boolean = false,
    isEnabled: Boolean = true
) {
    val isAction = text == "SUBMIT" || text == "SENT"
    Card(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = isEnabled) { onClick() }
            .testTag(if (isAction) "calc_submit" else "calc_key"),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !isEnabled -> RvSurface
                isAction -> RvViolet
                isSpecial -> RvOutline
                else -> RvSurfaceRaised
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (isEnabled) (if (isAction) RvVioletEdge else RvInkSoft) else RvOutline
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = if (isAction) aCapSp(16f, 1.2f) else aCapSp(22f, 1.3f),
                fontWeight = FontWeight.Bold,
                color = when {
                    !isEnabled -> RvInkSoft
                    isAction -> RvOnTone
                    else -> RvInk
                },
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// Enhanced top bar with current hearts and live timer
@Composable
fun EnhancedSubtractionTopGameBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    hearts: Int,
    roundLevel: String, // e.g., "3/7" - the puzzle round indicator
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.statusBarsPadding().fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Back button and level progression
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Back button
            IconButton(
                onClick = {
                    Log.d("SubtractionScreen", "🔙 Back button clicked")
                    onBack?.invoke() ?: run {
                        Log.d("SubtractionScreen", "⚠️ No back action provided")
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

            Column {
                Text(
                    text = "Level ${level.level}",
                    fontSize = aCapSp(18f, 1.15f),
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Level progress bar (label omitted: the level is already shown above)
                LevelProgressBar(
                    level = level,
                    modifier = Modifier.width(80.dp),
                    showLabel = false
                )
            }
        }

        // Center: Timer with color coding
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timer,
                fontSize = aCapSp(22f, 1.15f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.testTag("hud_timer"),
                color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 10 } == true) {
                    Color.Red // Red when ≤10 seconds
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

        // Right side: Hearts and round level with enhanced heart display
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(5) { index ->
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (index < hearts) {
                            RvCoralEdge
                        } else {
                            RvDisabled
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = roundLevel,
                fontSize = aCapSp(16f, 1.15f),
                color = RvInk,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// Animated background pattern remains the same
fun DrawScope.drawAnimatedPattern(size: androidx.compose.ui.geometry.Size) {
    val centerX = size.width / 2
    val centerY = size.height / 2

    // Draw subtle animated circles
    for (i in 1..3) {
        drawCircle(
            color = RvInkSoft.copy(alpha = 0.05f),
            radius = (size.minDimension / 4) * i,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )
    }

    // Draw some floating dots
    for (i in 0..8) {
        val angle = (i * 45f) * (PI / 180f)
        val radius = size.minDimension / 3
        val x = centerX + (cos(angle) * radius).toFloat()
        val y = centerY + (sin(angle) * radius).toFloat()

        drawCircle(
            color = RvInkSoft.copy(alpha = 0.1f),
            radius = 4.dp.toPx(),
            center = Offset(x, y)
        )
    }
}

@Composable
fun AnimatedCrystal(
    rotation: Float,
    scale: Float
) {
    Canvas(
        modifier = Modifier
            .size(120.dp)
            .rotate(rotation)
            .scale(scale)
    ) {
        drawCrystal(size)
    }
}

fun DrawScope.drawCrystal(size: androidx.compose.ui.geometry.Size) {
    val center = Offset(size.width / 2, size.height / 2)
    val crystalSize = size.minDimension * 0.4f

    // Crystal gradient
    val gradient = Brush.radialGradient(
        colors = listOf(
            Color(0xFF87CEEB),
            Color(0xFFE6E6FA),
            Color(0xFFDDA0DD),
            Color(0xFF87CEEB)
        ),
        center = center,
        radius = crystalSize
    )

    // Draw crystal diamond shape
    val path = Path().apply {
        moveTo(center.x, center.y - crystalSize)
        lineTo(center.x + crystalSize * 0.6f, center.y - crystalSize * 0.3f)
        lineTo(center.x + crystalSize * 0.4f, center.y + crystalSize * 0.7f)
        lineTo(center.x - crystalSize * 0.4f, center.y + crystalSize * 0.7f)
        lineTo(center.x - crystalSize * 0.6f, center.y - crystalSize * 0.3f)
        close()
    }

    drawPath(path, gradient)
    drawPath(path, Color.White, style = Stroke(width = 2.dp.toPx()))

    // Inner facets
    drawLine(
        Color.White.copy(alpha = 0.5f),
        Offset(center.x, center.y - crystalSize),
        Offset(center.x, center.y + crystalSize * 0.7f),
        strokeWidth = 1.dp.toPx()
    )

    drawLine(
        Color.White.copy(alpha = 0.3f),
        Offset(center.x - crystalSize * 0.6f, center.y - crystalSize * 0.3f),
        Offset(center.x + crystalSize * 0.6f, center.y - crystalSize * 0.3f),
        strokeWidth = 1.dp.toPx()
    )
}
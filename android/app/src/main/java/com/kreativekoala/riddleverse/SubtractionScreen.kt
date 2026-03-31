package com.kreativekoala.riddleverse

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
            modifier = Modifier.fillMaxSize().padding(bottom = 24.dp)
        ) {
            // Top Bar with live timer and current hearts
            EnhancedSubtractionTopGameBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer, // Use live countdown timer
                hearts = currentHearts, // Use current hearts instead of initial hearts
                roundLevel = level,
                onBack = onBack,
                modifier = Modifier.padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            // Score display (if any score accumulated)
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

                        if (attempts > 0) {
                            Text(
                                text = "Attempt: $attempts",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        // Show problem complexity info
                        val complexity = maxOf(number1, number2)
                        if (complexity >= 100) {
                            Text(
                                text = when {
                                    complexity >= 1000 -> "🔥 Expert"
                                    complexity >= 500 -> "⭐ Hard"
                                    complexity >= 100 -> "💪 Medium"
                                    else -> "✨ Easy"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
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

            // Numbers to compare with enhanced visual feedback
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = number1.toString(),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "−", // Minus symbol
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = number2.toString(),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Calculator Grid with enhanced submit logic
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
                modifier = Modifier.padding(horizontal = 20.dp).wrapContentHeight()
            )

            Spacer(modifier = Modifier.height(48.dp))
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}

// Enhanced Calculator Grid with visual state management
@Composable
fun CalculatorGrid(
    currentInput: String,
    isAnswered: Boolean = false,
    onNumberClick: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Display current input with state-aware styling
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAnswered) {
                    Color.White.copy(alpha = 0.05f)
                } else {
                    Color.Transparent
                }
            ),
            border = androidx.compose.foundation.BorderStroke(
                2.dp,
                if (isAnswered) {
                    Color.White.copy(alpha = 0.2f)
                } else {
                    Color.White.copy(alpha = 0.3f)
                }
            )
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAnswered) {
                        Color.White.copy(alpha = 0.6f)
                    } else {
                        Color.White
                    }
                )
            }
        }

        // Number grid (1-9)
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (col in 0..2) {
                    val number = row * 3 + col + 1
                    CalculatorButton(
                        text = number.toString(),
                        onClick = { onNumberClick(number.toString()) },
                        modifier = Modifier.weight(1f),
                        isEnabled = !isAnswered
                    )
                }
            }
        }

        // Bottom row (Clear, 0, Submit)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CalculatorButton(
                text = "✕",
                onClick = onClear,
                modifier = Modifier.weight(1f),
                isSpecial = true,
                isEnabled = !isAnswered
            )

            CalculatorButton(
                text = "0",
                onClick = { onNumberClick("0") },
                modifier = Modifier.weight(1f),
                isEnabled = !isAnswered
            )

            CalculatorButton(
                text = if (isAnswered) "SENT" else "SUBMIT",
                onClick = onSubmit,
                modifier = Modifier.weight(1f),
                isSpecial = true,
                isEnabled = !isAnswered
            )
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
    val borderAlpha = if (isEnabled) {
        if (isSpecial) 0.6f else 0.3f
    } else {
        0.15f
    }

    val textAlpha = if (isEnabled) 1f else 0.4f

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(enabled = isEnabled) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            Color.White.copy(alpha = borderAlpha)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = if (text == "SUBMIT" || text == "SENT") 14.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = textAlpha),
                textAlign = TextAlign.Center
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
                    Log.d("SubtractionScreen", "🔙 Back button clicked")
                    onBack?.invoke() ?: run {
                        Log.d("SubtractionScreen", "⚠️ No back action provided")
                    }
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
                    text = "Level ${level.level}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Level progress bar
                LevelProgressBar(
                    level = level,
                    modifier = Modifier.width(100.dp)
                )
            }
        }

        // Center: Timer with color coding
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
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

        // Right side: Hearts and round level with enhanced heart display
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(5) { index ->
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (index < hearts) {
                            Color(0xFFFF69B4)
                        } else {
                            Color.White.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = roundLevel,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp)
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
            color = Color.White.copy(alpha = 0.05f),
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
            color = Color.White.copy(alpha = 0.1f),
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
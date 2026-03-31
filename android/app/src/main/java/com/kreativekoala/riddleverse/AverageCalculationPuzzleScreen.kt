package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun AveragePuzzleScreen(
    difficulty: String = "Medium",
    timer: String,
    numbers: List<Int>,
    correctAverage: Double,
    tolerance: Double = 0.5,
    onSubmitAnswer: (Double) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentHearts by remember { mutableStateOf(3) } // Add hearts system

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
            90
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isAnswered) {
        if (timeRemaining > 0 && !isAnswered) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !isAnswered) {
            // Time's up - auto-submit with current input or 0
            Log.d("AveragePuzzle", "⏰ Time's up! Auto-submitting with input: '$currentInput'")
            val finalAnswer = currentInput.toDoubleOrNull() ?: 0.0
            isAnswered = true
            attempts++

            val timeSpent = totalTimeSeconds - timeRemaining
            val isCorrect = kotlin.math.abs(finalAnswer - correctAverage) <= tolerance

            if (!isCorrect) {
                currentHearts = maxOf(0, currentHearts - 1)
            }

            // Show feedback and then advance
            feedbackManager.showFeedback(
                puzzleType = "average",
                isCorrect = isCorrect,
                userAnswer = String.format("%.0f", finalAnswer),
                correctAnswer = String.format("%.0f", correctAverage),
                timeSpent = timeSpent * 1000L,
                difficulty = difficulty,
                timeRemaining = timeRemaining,
                totalTime = totalTimeSeconds,
                onComplete = {
                    Log.d("AveragePuzzle", "🎊 Timeout feedback completed - advancing to next puzzle")
                    onSubmitAnswer(finalAnswer)
                    fetchNextPuzzle(totalScore) // Always advance, even on timeout
                }
            )
        }
    }

    // Calculate score for average calculation
    fun calculateAverageScore(
        isCorrect: Boolean,
        accuracy: Double,
        timeSpent: Int,
        attemptNumber: Int,
        numbers: List<Int>,
        difficulty: String
    ): Int {
        if (!isCorrect) return 0

        val basePoints = when (difficulty.lowercase()) {
            "easy" -> 30
            "medium" -> 50
            "hard" -> 70
            "expert" -> 90
            else -> 50
        }

        val numberComplexity = when {
            numbers.any { it > 100 } -> 1.5f
            numbers.any { it > 50 } -> 1.3f
            numbers.any { it < 10 } -> 1.1f
            else -> 1.0f
        }

        val quantityBonus = when (numbers.size) {
            2 -> 0
            3 -> (basePoints * 0.2f).toInt()
            4 -> (basePoints * 0.4f).toInt()
            5 -> (basePoints * 0.6f).toInt()
            else -> (basePoints * 0.8f).toInt()
        }

        val accuracyBonus = when {
            accuracy >= 0.99 -> (basePoints * 0.5f).toInt()
            accuracy >= 0.95 -> (basePoints * 0.3f).toInt()
            accuracy >= 0.90 -> (basePoints * 0.1f).toInt()
            else -> 0
        }

        val timeBonus = when {
            timeSpent <= totalTimeSeconds * 0.2 -> (basePoints * 0.6f).toInt()
            timeSpent <= totalTimeSeconds * 0.4 -> (basePoints * 0.4f).toInt()
            timeSpent <= totalTimeSeconds * 0.6 -> (basePoints * 0.2f).toInt()
            else -> 0
        }

        val attemptPenalty = when (attemptNumber) {
            1 -> 0
            2 -> (basePoints * 0.2f).toInt()
            3 -> (basePoints * 0.4f).toInt()
            else -> (basePoints * 0.6f).toInt()
        }

        val mathBonus = (basePoints * 0.15f).toInt()
        val finalScore = ((basePoints * numberComplexity).toInt() + quantityBonus + accuracyBonus + timeBonus + mathBonus - attemptPenalty)

        return maxOf(finalScore, basePoints / 4)
    }

    // Reset state function (for retry attempts)
    fun resetState() {
        isAnswered = false
        currentInput = ""
        gameStartTime = System.currentTimeMillis()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background components
        MountainLandscapeBackground()
        GeometricOverlay()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Enhanced Top Bar with hearts
            EnhancedAveragesTopGameBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer,
                lives = currentHearts, // Show current hearts
                onBack = onBack,
                modifier = Modifier.padding(16.dp)
            )

            // Score display
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

                        Text(
                            text = "Avg of ${numbers.size} numbers",
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

            // Title and numbers
            PuzzleHeader(
                numbers = numbers,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Input display
            InputDisplay(
                currentInput = currentInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Calculator interface
            CalculatorGrid(
                currentInput = currentInput,
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
                onDecimal = {
                    // Remove decimal support since we only want whole numbers
                    Log.d("AveragePuzzle", "🚫 Decimal not allowed - answers must be whole numbers")
                },
                onSubmit = {
                    if (!isAnswered && currentInput.isNotEmpty()) {
                        Log.d("AveragePuzzle", "🔥 Submit clicked - input: $currentInput")
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        val answer = currentInput.toDoubleOrNull() ?: 0.0
                        isAnswered = true
                        attempts++

                        val timeSpent = totalTimeSeconds - timeRemaining
                        val isCorrect = kotlin.math.abs(answer - correctAverage) <= tolerance

                        Log.d("AveragePuzzle", "🎯 Answer check:")
                        Log.d("AveragePuzzle", "  Numbers: $numbers")
                        Log.d("AveragePuzzle", "  Sum: ${numbers.sum()}")
                        Log.d("AveragePuzzle", "  Count: ${numbers.size}")
                        Log.d("AveragePuzzle", "  Expected: ${correctAverage}")
                        Log.d("AveragePuzzle", "  User answer: $answer")
                        Log.d("AveragePuzzle", "  Difference: ${kotlin.math.abs(answer - correctAverage)}")
                        Log.d("AveragePuzzle", "  Tolerance: $tolerance")
                        Log.d("AveragePuzzle", "  Result: $isCorrect")

                        // Calculate accuracy and score
                        val accuracy = if (correctAverage != 0.0) {
                            1.0 - (kotlin.math.abs(answer - correctAverage) / correctAverage)
                        } else {
                            if (answer == 0.0) 1.0 else 0.0
                        }

                        val attemptScore = calculateAverageScore(
                            isCorrect = isCorrect,
                            accuracy = maxOf(0.0, accuracy),
                            timeSpent = timeSpent,
                            attemptNumber = attempts,
                            numbers = numbers,
                            difficulty = difficulty
                        )

                        if (isCorrect) {
                            totalScore += attemptScore
                            Log.d("AveragePuzzle", "✅ Correct! Score: +$attemptScore, Total: $totalScore")
                        } else {
                            currentHearts = maxOf(0, currentHearts - 1)
                            Log.d("AveragePuzzle", "❌ Incorrect. Hearts remaining: $currentHearts")
                        }

                        // FIXED: Always show feedback and advance, regardless of correctness
                        feedbackManager.showFeedback(
                            puzzleType = "average",
                            isCorrect = isCorrect,
                            userAnswer = String.format("%.0f", answer), // Show as whole number
                            correctAnswer = String.format("%.0f", correctAverage), // Show as whole number
                            timeSpent = timeSpent * 1000L,
                            difficulty = difficulty,
                            timeRemaining = timeRemaining,
                            totalTime = totalTimeSeconds,
                            onComplete = {
                                Log.d("AveragePuzzle", "🎊 Feedback completed")
                                onSubmitAnswer(answer)

                                // CRITICAL FIX: Always advance to next puzzle
                                fetchNextPuzzle(totalScore)

                            }
                        )
                    }
                },
                modifier = Modifier.padding(20.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
fun InputDisplay(
    currentInput: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(60.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentInput.ifEmpty { "Enter your answer" },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentInput.isEmpty())
                    Color.White.copy(alpha = 0.6f)
                else
                    Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PuzzleHeader(
    numbers: List<Int>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "FIND THE AVERAGE",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            numbers.forEach { number ->
                Text(
                    text = number.toString(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CalculatorGrid(
    currentInput: String,
    onNumberClick: (String) -> Unit,
    onClear: () -> Unit,
    onDecimal: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Number grid (1-9)
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (col in 0..2) {
                    val number = row * 3 + col + 1
                    AverageCalculatorButton(
                        text = number.toString(),
                        onClick = { onNumberClick(number.toString()) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Bottom row (Clear, 0, Decimal)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AverageCalculatorButton(
                text = "✕",
                onClick = onClear,
                modifier = Modifier.weight(1f),
                isSpecial = true
            )

            AverageCalculatorButton(
                text = "0",
                onClick = { onNumberClick("0") },
                modifier = Modifier.weight(1f)
            )

            AverageCalculatorButton(
                text = ".",
                onClick = onDecimal,
                modifier = Modifier.weight(1f)
            )
        }

        // Enhanced submit button
        Button(
            onClick = {
                Log.d("AveragePuzzle", "🎯 SUBMIT BUTTON CLICKED!")
                onSubmit()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentInput.isNotEmpty()) {
                    Color.White.copy(alpha = 0.3f)
                } else {
                    Color.White.copy(alpha = 0.1f)
                }
            ),
            shape = RoundedCornerShape(8.dp),
            enabled = currentInput.isNotEmpty()
        ) {
            Text(
                text = "SUBMIT",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentInput.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun AverageCalculatorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSpecial: Boolean = false,
    isInput: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isSpecial -> Color.Red.copy(alpha = 0.3f)
                isInput -> Color.Blue.copy(alpha = 0.3f)
                else -> Color.White.copy(alpha = 0.2f)
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun MountainLandscapeBackground() {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        drawMountainLandscape(size)
    }
}

@Composable
fun GeometricOverlay() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(0.5.dp)
    ) {
        drawGeometricShapes(size)
    }
}

fun DrawScope.drawMountainLandscape(size: androidx.compose.ui.geometry.Size) {
    // Sky gradient
    val skyGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF4A4A4A),
            Color(0xFF8B4B9D),
            Color(0xFFE8A87C)
        ),
        startY = 0f,
        endY = size.height * 0.7f
    )
    drawRect(skyGradient)

    // Mountains (multiple layers for depth)
    val mountainLayers = listOf(
        listOf(
            Offset(0f, size.height * 0.8f),
            Offset(size.width * 0.2f, size.height * 0.4f),
            Offset(size.width * 0.4f, size.height * 0.6f),
            Offset(size.width * 0.6f, size.height * 0.3f),
            Offset(size.width * 0.8f, size.height * 0.5f),
            Offset(size.width, size.height * 0.7f),
            Offset(size.width, size.height),
            Offset(0f, size.height)
        ),
        listOf(
            Offset(0f, size.height * 0.9f),
            Offset(size.width * 0.3f, size.height * 0.6f),
            Offset(size.width * 0.7f, size.height * 0.4f),
            Offset(size.width, size.height * 0.8f),
            Offset(size.width, size.height),
            Offset(0f, size.height)
        )
    )

    val mountainColors = listOf(
        Color(0xFF2C2C2C).copy(alpha = 0.6f),
        Color(0xFF1A1A1A).copy(alpha = 0.8f)
    )

    mountainLayers.forEachIndexed { index, points ->
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { point ->
                lineTo(point.x, point.y)
            }
            close()
        }
        drawPath(path, mountainColors[index])
    }

    // Foreground trees (simple triangular shapes)
    val treePositions = listOf(
        Offset(size.width * 0.1f, size.height * 0.7f),
        Offset(size.width * 0.15f, size.height * 0.75f),
        Offset(size.width * 0.85f, size.height * 0.65f),
        Offset(size.width * 0.9f, size.height * 0.8f)
    )

    treePositions.forEach { position ->
        val treePath = Path().apply {
            moveTo(position.x, position.y + 100f)
            lineTo(position.x - 30f, position.y + 100f)
            lineTo(position.x, position.y)
            lineTo(position.x + 30f, position.y + 100f)
            close()
        }
        drawPath(treePath, Color(0xFF0D1B2A))
    }
}

fun DrawScope.drawGeometricShapes(size: androidx.compose.ui.geometry.Size) {
    val centerX = size.width / 2
    val centerY = size.height / 2

    // Draw interconnected triangles and lines
    val triangleSize = 100f
    val triangleOffsets = listOf(
        Offset(centerX - triangleSize, centerY - 50f),
        Offset(centerX + triangleSize, centerY - 50f),
        Offset(centerX, centerY + triangleSize)
    )

    // Draw connecting lines
    triangleOffsets.forEach { start ->
        triangleOffsets.forEach { end ->
            if (start != end) {
                drawLine(
                    color = Color.White.copy(alpha = 0.1f),
                    start = start,
                    end = end,
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }

    // Draw triangular outlines
    triangleOffsets.forEach { center ->
        val trianglePath = Path().apply {
            moveTo(center.x, center.y - 30f)
            lineTo(center.x - 25f, center.y + 15f)
            lineTo(center.x + 25f, center.y + 15f)
            close()
        }
        drawPath(
            trianglePath,
            color = Color.White.copy(alpha = 0.15f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }

    // Additional geometric elements
    drawCircle(
        color = Color.White.copy(alpha = 0.1f),
        radius = 60f,
        center = Offset(centerX, centerY),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
    )

    // Cross lines
    drawLine(
        color = Color.White.copy(alpha = 0.2f),
        start = Offset(centerX - 80f, centerY),
        end = Offset(centerX + 80f, centerY),
        strokeWidth = 2.dp.toPx()
    )

    drawLine(
        color = Color.White.copy(alpha = 0.2f),
        start = Offset(centerX, centerY - 80f),
        end = Offset(centerX, centerY + 80f),
        strokeWidth = 2.dp.toPx()
    )
}

// Enhanced top bar with live timer
@Composable
fun EnhancedAveragesTopGameBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.statusBarsPadding().fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left side: Back button, pause, and level
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

            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    text = "Level ${level.level}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Level progress bar
                LevelProgressBar(
                    level = level,
                    modifier = Modifier.width(120.dp)
                )
            }
        }

        // Center: Lives display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(lives) {
                Text(
                    text = "❤️",
                    fontSize = 16.sp
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
    }
}
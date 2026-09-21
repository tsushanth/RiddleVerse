package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
            .background(RvCanvas)
    ) {
        // Background components
        MountainLandscapeBackground()
        GeometricOverlay()

        AverageFitLayout(
            hud = { compact ->
                EnhancedAveragesTopGameBar(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    lives = currentHearts, // Show current hearts
                    onBack = onBack,
                    compact = compact,
                    modifier = Modifier.statusBarsPadding()
                )
            },
            info = {
                if (totalScore > 0 || attempts > 0) {
                    AverageInfoStrip(
                        buildString {
                            append("${stringResource(R.string.score_label)}: $totalScore")
                            if (attempts > 0) append("  •  Attempt: $attempts")
                            append("  •  Avg of ${numbers.size} numbers")
                        }
                    )
                }
            },
            question = { compact ->
                AverageQuestionPane(
                    numbers = numbers,
                    currentInput = currentInput,
                    compact = compact
                )
            },
            keypad = {
                AverageKeypad(
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
                modifier = Modifier.fillMaxSize()
            )
            }
        )

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
            containerColor = RvSurface
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
                color = if (currentInput.isEmpty()) RvInkSoft else RvInk,
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
            color = RvInkSoft.copy(alpha = 0.9f),
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
                    color = RvInk,
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
                containerColor = RvViolet,
                disabledContainerColor = RvDisabled
            ),
            shape = RoundedCornerShape(8.dp),
            enabled = currentInput.isNotEmpty()
        ) {
            Text(
                text = "SUBMIT",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentInput.isNotEmpty()) RvOnTone else RvInkSoft
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
                isSpecial -> RvError.copy(alpha = 0.25f)
                isInput -> RvSky.copy(alpha = 0.3f)
                else -> RvSurface
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = RvInk
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
                    color = RvInkSoft.copy(alpha = 0.1f),
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
            color = RvInkSoft.copy(alpha = 0.15f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }

    // Additional geometric elements
    drawCircle(
        color = RvInkSoft.copy(alpha = 0.1f),
        radius = 60f,
        center = Offset(centerX, centerY),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
    )

    // Cross lines
    drawLine(
        color = RvInkSoft.copy(alpha = 0.2f),
        start = Offset(centerX - 80f, centerY),
        end = Offset(centerX + 80f, centerY),
        strokeWidth = 2.dp.toPx()
    )

    drawLine(
        color = RvInkSoft.copy(alpha = 0.2f),
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = RvInk,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Level ${level.level}",
                fontSize = if (compact) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                LevelProgressBar(level = level, modifier = Modifier.width(120.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(lives) { Text(text = "❤️", fontSize = 16.sp) }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timer,
                fontSize = if (compact) 20.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true) {
                    RvErrorEdge // Red when <=30 seconds
                } else {
                    RvInk
                },
                maxLines = 1
            )
            if (!compact && streakInfo.currentStreak > 0) {
                StreakDisplay(streakInfo = streakInfo, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * Fit-to-screen scaffold for the average puzzles (no scrolling). Portrait: HUD on top, the
 * question (title, numbers, answer display) in the flexible middle and the keypad pinned at the
 * bottom with a height derived from the space that is left. Landscape / wide: question on the
 * left, keypad on the right. Content width is capped for tablets.
 */
@Composable
fun AverageFitLayout(
    modifier: Modifier = Modifier,
    hud: @Composable (compact: Boolean) -> Unit,
    info: @Composable () -> Unit,
    question: @Composable (compact: Boolean) -> Unit,
    keypad: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val compact = wide || maxHeight < 720.dp
        val pad = if (maxHeight < 600.dp) 12.dp else 16.dp
        if (wide) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 1000.dp)
                    .fillMaxSize()
                    .padding(pad)
            ) {
                hud(true)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) { question(true) }
                    Box(
                        modifier = Modifier.weight(1f).widthIn(max = 420.dp).fillMaxHeight()
                    ) { keypad() }
                }
            }
        } else {
            // Height for the keypad: about 55% of what the HUD leaves, but never squashed.
            val hudH = if (compact) 56.dp else 150.dp
            val remaining = maxHeight - pad * 2 - hudH
            val keypadH = (remaining * 0.55f).coerceIn(232.dp, 400.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .padding(pad)
            ) {
                hud(compact)
                if (!compact) {
                    Spacer(Modifier.height(8.dp))
                    info()
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) { question(compact) }
                Box(
                    modifier = Modifier.fillMaxWidth().height(keypadH).testTag("avg_keypad")
                ) { keypad() }
            }
        }
    }
}

/** Title + numbers + answer display. Number size is set in dp so it is stable at any font scale. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AverageQuestionPane(
    numbers: List<Int>,
    currentInput: String,
    compact: Boolean,
    subtitle: String? = null
) {
    val fontScale = LocalDensity.current.fontScale
    val numberDp = when {
        numbers.size <= 3 -> if (compact) 40 else 52
        numbers.size == 4 -> if (compact) 36 else 44
        else -> if (compact) 28 else 36
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "FIND THE AVERAGE",
            fontSize = if (compact) 14.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = RvInkSoft,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = RvVioletEdge,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag("avg_numbers"),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            numbers.forEach { number ->
                Text(
                    text = number.toString(),
                    fontSize = (numberDp / fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
        AverageInputDisplay(
            currentInput = currentInput,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            compact = compact
        )
    }
}

@Composable
fun AverageInputDisplay(
    currentInput: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        modifier = modifier.height(if (compact) 52.dp else 64.dp).testTag("avg_input"),
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = currentInput.ifEmpty { "Enter your answer" },
                fontSize = if (currentInput.isEmpty()) 16.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentInput.isEmpty()) RvInkSoft else RvInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Number pad that fills the box it is given: 4 rows of keys plus the submit bar, all rows
 * share the height equally. The submit bar is the visually strongest control.
 */
@Composable
fun AverageKeypad(
    currentInput: String,
    onNumberClick: (String) -> Unit,
    onClear: () -> Unit,
    onDecimal: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Composable
    fun RowScope.Key(text: String, onClick: () -> Unit, special: Boolean = false) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (special) RvSurface else RvSurfaceRaised,
                contentColor = if (special) RvErrorEdge else RvInk
            ),
            border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0..2) {
                    val number = row * 3 + col + 1
                    Key(number.toString(), { onNumberClick(number.toString()) })
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Key("✕", onClear, special = true)
            Key("0", { onNumberClick("0") })
            // Answers are whole numbers, so there is no decimal key: keep the slot empty.
            Spacer(Modifier.weight(1f))
        }
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().weight(1.1f).testTag("avg_submit"),
            enabled = currentInput.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = RvViolet,
                contentColor = RvOnTone,
                disabledContainerColor = RvDisabled,
                disabledContentColor = RvInkSoft
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.submit), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** One-line score/attempt strip shown above the question on tall screens. */
@Composable
fun AverageInfoStrip(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = RvInkSoft,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

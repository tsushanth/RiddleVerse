package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

data class ChartDataPoint(
    val value: Double,
    val yPosition: Float, // Position on the chart (0-1, where 0 is bottom, 1 is top)
    val index: Int
)

@Composable
fun EstimationPuzzleScreen(
    difficulty: String = "Medium",
    timer: String,
    dataPoints: List<ChartDataPoint>,
    correctSum: Double,
    minValue: Double = 0.0,
    maxValue: Double = 50.0,
    tolerance: Double = 0.5,
    onSubmitAnswer: (Double) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: (() -> Unit)? = null
) {
    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Game state
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var estimatedValue by remember { mutableStateOf<Double?>(null) }
    var hasAnswered by remember { mutableStateOf(false) }
    var currentEstimate by remember { mutableStateOf<Double?>(null) }
    var chartEstimate by remember { mutableStateOf<Double?>(null) }

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Chart dimensions
    val chartHeight = 350.dp
    val chartWidth = 300.dp

    // Timer calculation and countdown
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            120 // Default 2 minutes for estimation
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    // Calculate score based on accuracy and performance factors
    fun calculateScore(estimate: Double, actualSum: Double, timeSpent: Int, attemptNumber: Int): Int {
        val difference = abs(estimate - actualSum)
        val accuracy = maxOf(0.0, 1.0 - (difference / actualSum).coerceAtMost(1.0))

        // Base points by difficulty
        val basePoints = when (difficulty.lowercase()) {
            "easy" -> 60
            "medium" -> 80
            "hard" -> 100
            "expert" -> 120
            else -> 80
        }

        // Accuracy scoring (exponential reward for precision)
        val accuracyScore = when {
            accuracy >= 0.95 -> (basePoints * 1.5).toInt() // Within 5% - excellent
            accuracy >= 0.90 -> (basePoints * 1.3).toInt() // Within 10% - very good
            accuracy >= 0.80 -> (basePoints * 1.1).toInt() // Within 20% - good
            accuracy >= 0.70 -> basePoints // Within 30% - acceptable
            accuracy >= 0.50 -> (basePoints * 0.7).toInt() // Within 50% - partial credit
            accuracy >= 0.30 -> (basePoints * 0.4).toInt() // Within 70% - minimal credit
            else -> (basePoints * 0.1).toInt() // Poor estimate - minimal points
        }

        // Time bonus (faster estimation = bonus points)
        val timeEfficiency = timeSpent.toFloat() / totalTimeSeconds
        val timeBonus = when {
            timeEfficiency <= 0.3f -> (basePoints * 0.4).toInt() // Very fast
            timeEfficiency <= 0.5f -> (basePoints * 0.3).toInt() // Fast
            timeEfficiency <= 0.7f -> (basePoints * 0.2).toInt() // Moderate speed
            else -> 0 // Slow, no bonus
        }

        // Complexity bonus based on data range and number of points
        val dataRange = maxValue - minValue
        val complexityMultiplier = when {
            dataRange >= 200 && dataPoints.size >= 8 -> 1.3f
            dataRange >= 100 && dataPoints.size >= 6 -> 1.2f
            dataRange >= 50 && dataPoints.size >= 4 -> 1.1f
            else -> 1.0f
        }

        // Attempt penalty (first try is best)
        val attemptPenalty = when (attemptNumber) {
            1 -> 0
            2 -> (basePoints * 0.2).toInt()
            3 -> (basePoints * 0.4).toInt()
            else -> (basePoints * 0.6).toInt()
        }

        val finalScore = ((accuracyScore + timeBonus) * complexityMultiplier).toInt() - attemptPenalty

        Log.d("EstimationPuzzle", "🏆 Score calculation:")
        Log.d("EstimationPuzzle", "  Estimate: $estimate, Actual: $actualSum")
        Log.d("EstimationPuzzle", "  Accuracy: ${(accuracy * 100).toInt()}%")
        Log.d("EstimationPuzzle", "  Base points: $basePoints")
        Log.d("EstimationPuzzle", "  Accuracy score: $accuracyScore")
        Log.d("EstimationPuzzle", "  Time bonus: $timeBonus (${timeSpent}s)")
        Log.d("EstimationPuzzle", "  Complexity multiplier: ${complexityMultiplier}x")
        Log.d("EstimationPuzzle", "  Attempt penalty: $attemptPenalty")
        Log.d("EstimationPuzzle", "  Final score: $finalScore")

        return maxOf(finalScore, 10) // Minimum 10 points for attempting
    }

    // Reset state function
    fun resetState() {
        hasAnswered = false
        dragPosition = null
        currentEstimate = null
        chartEstimate = null
        estimatedValue = null
        gameStartTime = System.currentTimeMillis()
        // Don't reset score or attempts - keep for cumulative tracking
    }


    // Submit answer function with enhanced scoring
    fun submitAnswer(estimate: Double) {
        if (!hasAnswered) {
            Log.d("EstimationPuzzle", "🔥 Submit clicked - estimate: $estimate")
            hasAnswered = true
            estimatedValue = estimate
            attempts++

            val timeSpent = totalTimeSeconds - timeRemaining
            val difference = abs(estimate - correctSum)
            val isCorrect = difference <= tolerance

            // Calculate score for this attempt
            val attemptScore = calculateScore(estimate, correctSum, timeSpent, attempts)
            totalScore = totalScore + attemptScore

            Log.d("EstimationPuzzle", "🎯 Answer: $estimate, Correct: $isCorrect, Expected: $correctSum")
            Log.d("EstimationPuzzle", "📊 Difference: $difference, Tolerance: $tolerance")
            Log.d("EstimationPuzzle", "💯 Score: +$attemptScore, Total: $totalScore")

            feedbackManager.showFeedback(
                puzzleType = "estimation",
                isCorrect = isCorrect,
                userAnswer = String.format("%.1f", estimate),
                correctAnswer = String.format("%.1f", correctSum),
                timeSpent = timeSpent * 1000L, // Convert to milliseconds
                difficulty = difficulty,
                timeRemaining = timeRemaining,
                totalTime = totalTimeSeconds,
                onComplete = {
                    Log.d("EstimationPuzzle", "🎊 Feedback completed")
                    onSubmitAnswer(estimate)
                    Log.d("EstimationPuzzle", "🎯 Calling fetchNextPuzzle with score: $totalScore")
                    fetchNextPuzzle(totalScore) // Pass accumulated score
                }
            )

            // Haptic feedback
            haptics.performHapticFeedback(
                if (isCorrect) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
        }
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, hasAnswered) {
        if (timeRemaining > 0 && !hasAnswered) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !hasAnswered) {
            // Time's up - auto-submit current estimate or middle value
            val finalEstimate = currentEstimate ?: ((maxValue + minValue) / 2)
            Log.d("EstimationPuzzle", "⏰ Time's up! Auto-submitting estimate: $finalEstimate")
            submitAnswer(finalEstimate)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar with live timer and enhanced info
            EnhancedEstimationTopGameBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer, // Use live countdown timer
                onBack = onBack,
                modifier = Modifier.padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            // Score display (if any score accumulated)
            if (totalScore > 0 || attempts > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                                color = Color(0xFF4CAF50)
                            )
                        }

                        if (attempts > 0) {
                            Text(
                                text = "Attempt: $attempts",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Gray
                            )
                        }

                        // Show accuracy range for current difficulty
                        Text(
                            text = "Target: ±${String.format("%.1f", tolerance)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF9C27B0)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Chart area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight + 60.dp),
                contentAlignment = Alignment.Center
            ) {
                InteractiveChart(
                    dataPoints = dataPoints,
                    dragPosition = dragPosition,
                    estimatedValue = estimatedValue,
                    minValue = minValue,
                    maxValue = maxValue,
                    chartWidth = chartWidth,
                    chartHeight = chartHeight,
                    isAnswered = hasAnswered,
                    onDrag = { offset ->
                        if (!hasAnswered) {
                            dragPosition = offset

                            val padding = with(density) { 50.dp.toPx() }
                            val chartRect = androidx.compose.ui.geometry.Rect(
                                offset = Offset(padding, padding),
                                size = androidx.compose.ui.geometry.Size(
                                    with(density) { (chartWidth + 80.dp).toPx() } - padding * 2,
                                    with(density) { (chartHeight + 60.dp).toPx() } - padding * 2
                                )
                            )

                            if (offset.x >= chartRect.left && offset.x <= chartRect.right &&
                                offset.y >= chartRect.top && offset.y <= chartRect.bottom) {

                                val relativeY = (chartRect.bottom - offset.y) / chartRect.height
                                val estimated = minValue + (maxValue - minValue) * relativeY

                                currentEstimate = estimated
                                chartEstimate = estimated
                            }

                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragEnd = { offset, chartRect ->
                        if (!hasAnswered) {
                            currentEstimate?.let { estimate ->
                                submitAnswer(estimate)
                            } ?: run {
                                val fallback = (maxValue + minValue) / 2
                                submitAnswer(fallback)
                            }
                        }
                    },
                    modifier = Modifier.size(chartWidth + 80.dp, chartHeight + 60.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced instructions with scoring info
            EstimationInstructionsCard(
                difficulty = difficulty,
                tolerance = tolerance,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Submit section with enhanced feedback
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Current estimate display with accuracy feedback
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentEstimate != null) {
                            Color(0xFF1976D2).copy(alpha = 0.1f)
                        } else {
                            Color.Gray.copy(alpha = 0.1f)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (currentEstimate != null) {
                                "Current Estimate: ${String.format("%.1f", currentEstimate!!)}"
                            } else {
                                "Drag on chart to set estimate"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentEstimate != null) Color(0xFF1976D2) else Color(0xFF666666)
                        )

                        // Show potential accuracy if estimate is made
                        currentEstimate?.let { estimate ->
                            val difference = abs(estimate - correctSum)
                            val accuracyPercent = maxOf(0.0, (1.0 - difference / correctSum) * 100).toInt()

                            Text(
                                text = "Potential Accuracy: ~$accuracyPercent%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    accuracyPercent >= 90 -> Color(0xFF4CAF50)
                                    accuracyPercent >= 70 -> Color(0xFFFF9800)
                                    else -> Color(0xFFFF5722)
                                },
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Submit button with state-aware text
                Button(
                    onClick = {
                        if (!hasAnswered) {
                            if (currentEstimate != null) {
                                submitAnswer(currentEstimate!!)
                            } else {
                                val fallbackEstimate = (maxValue + minValue) / 2
                                submitAnswer(fallbackEstimate)
                            }
                        }
                    },
                    enabled = !hasAnswered,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentEstimate != null) {
                            Color(0xFF1976D2)
                        } else {
                            Color(0xFF666666)
                        },
                        disabledContainerColor = Color(0xFF90A4AE)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = when {
                            hasAnswered -> "SUBMITTED"
                            currentEstimate != null -> "SUBMIT ESTIMATE"
                            else -> "SUBMIT (will use middle value)"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}

// Enhanced instructions card with scoring information
@Composable
fun EstimationInstructionsCard(
    difficulty: String = "Medium",
    tolerance: Double = 0.5,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8E4F3)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = Color(0xFF9C27B0),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Drag finger to estimate the sum",
                    fontSize = 16.sp,
                    color = Color(0xFF9C27B0),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(12.dp))

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF9C27B0),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scoring information
            Text(
                text = "💯 Scoring: Accuracy + Speed + Complexity",
                fontSize = 12.sp,
                color = Color(0xFF666666),
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "🎯 Target range: ±${String.format("%.1f", tolerance)} for perfect score",
                fontSize = 11.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// Enhanced top bar with better timer display
@Composable
fun EnhancedEstimationTopGameBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.statusBarsPadding().fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left side: Back button and level
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Back button
            IconButton(
                onClick = {
                    Log.d("EstimationPuzzle", "🔙 Back button clicked")
                    onBack?.invoke() ?: run {
                        Log.d("EstimationPuzzle", "⚠️ No back action provided")
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color(0xFF333333),
                    modifier = Modifier.size(28.dp)
                )
            }

            Column {
                Text(
                    text = "Level ${level.level}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )

                // Custom level progress bar with better colors
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(6.dp)
                        .background(
                            Color(0xFFE0E0E0),
                            RoundedCornerShape(3.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (level.progressPercentage / 100f).coerceIn(0f, 1f))
                            .background(
                                Color(0xFF9C27B0),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }

        // Right side: Timer and streak with enhanced visual feedback
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
                    Color(0xFF333333)
                }
            )

            // Streak display with better colors
            if (streakInfo.currentStreak > 0) {
                Text(
                    text = "🔥 ${streakInfo.currentStreak}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// Rest of the composables remain the same...
@Composable
fun InteractiveChart(
    dataPoints: List<ChartDataPoint>,
    dragPosition: Offset?,
    estimatedValue: Double?,
    minValue: Double,
    maxValue: Double,
    chartWidth: androidx.compose.ui.unit.Dp,
    chartHeight: androidx.compose.ui.unit.Dp,
    isAnswered: Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset, androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    var chartRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    Canvas(
        modifier = modifier
            .pointerInput(isAnswered) {
                if (!isAnswered) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            android.util.Log.d("EstimationScreen", "🖱️ Drag started at: $offset")
                        },
                        onDragEnd = {
                            android.util.Log.d("EstimationScreen", "🖱️ Drag ended")
                            dragPosition?.let { position ->
                                if (position.x >= chartRect.left && position.x <= chartRect.right &&
                                    position.y >= chartRect.top && position.y <= chartRect.bottom) {
                                    onDragEnd(position, chartRect)
                                }
                            }
                        }
                    ) { change, _ ->
                        val newPosition = Offset(change.position.x, change.position.y)
                        onDrag(newPosition)
                    }
                }
            }
    ) {
        val padding = 50.dp.toPx()
        chartRect = androidx.compose.ui.geometry.Rect(
            offset = Offset(padding, padding),
            size = androidx.compose.ui.geometry.Size(
                size.width - padding * 2,
                size.height - padding * 2
            )
        )

        drawChart(
            dataPoints = dataPoints,
            dragPosition = dragPosition,
            estimatedValue = estimatedValue,
            chartRect = chartRect,
            minValue = minValue,
            maxValue = maxValue,
            isAnswered = isAnswered
        )
    }
}

fun DrawScope.drawChart(
    dataPoints: List<ChartDataPoint>,
    dragPosition: Offset?,
    estimatedValue: Double?,
    chartRect: androidx.compose.ui.geometry.Rect,
    minValue: Double,
    maxValue: Double,
    isAnswered: Boolean
) {
    // Draw Y-axis
    drawLine(
        color = Color(0xFF9C27B0),
        start = Offset(chartRect.left, chartRect.top),
        end = Offset(chartRect.left, chartRect.bottom),
        strokeWidth = 3.dp.toPx()
    )

    // Generate smart Y-axis labels - FIXED to use proper range
    val range = maxValue - minValue
    val step = when {
        range <= 20 -> 5.0
        range <= 50 -> 10.0
        range <= 100 -> 20.0
        range <= 200 -> 50.0
        range <= 500 -> 100.0
        else -> 200.0
    }

    // FIXED: Start from minValue and use proper positioning
    var currentValue = kotlin.math.ceil(minValue / step) * step // Start from first step above minValue

    while (currentValue <= maxValue) {
        // FIXED: Use proper range calculation instead of just maxValue
        val normalizedPosition = (currentValue - minValue) / (maxValue - minValue)
        val yPos = chartRect.bottom - normalizedPosition.toFloat() * chartRect.height

        // Only draw if position is within chart bounds
        if (yPos >= chartRect.top && yPos <= chartRect.bottom) {
            // Draw tick mark
            drawRect(
                color = Color(0xFF9C27B0),
                topLeft = Offset(chartRect.left - 12.dp.toPx(), yPos - 2.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(18.dp.toPx(), 4.dp.toPx())
            )

            // Draw label
            drawContext.canvas.nativeCanvas.drawText(
                "${currentValue.toInt()}",
                chartRect.left - 35.dp.toPx(),
                yPos + 6.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#9C27B0")
                    textSize = 16.sp.toPx()
                    isAntiAlias = true
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        currentValue += step
    }

    val paddingPx = with(density) { 50.dp.toPx() }
    val chartTop = paddingPx
    val chartBottom = size.height - paddingPx

// desiredY = pixel Y for each value (top-down canvas coordinates)
    val desiredYs = dataPoints.map { dp ->
        // You already have yPosition in [0..1] where 1 = top. Convert to pixels.
        // NOTE: your yPosition is "normalized from bottom" in your code; adjust accordingly.
        // If yPosition is 0 at bottom and 1 at top:
        val y = chartBottom - (dp.yPosition * (chartBottom - chartTop))
        y
    }

    fun resolveLabelPositions(
        desiredYs: List<Float>,
        minGapPx: Float,
        topPx: Float,
        bottomPx: Float
    ): List<Float> {
        if (desiredYs.isEmpty()) return emptyList()

        // Work on a sorted copy but return in original order
        data class Item(val y: Float, val idx: Int)
        val items = desiredYs.mapIndexed { i, y -> Item(y, i) }.sortedBy { it.y }.toMutableList()

        // First pass: push down to ensure minGap
        for (i in 1 until items.size) {
            val prev = items[i - 1]
            val cur = items[i]
            val needed = prev.y + minGapPx
            if (cur.y < needed) {
                items[i] = cur.copy(y = needed)
            }
        }

        // Clamp to bounds
        val clamped = items.map { it.copy(y = it.y.coerceIn(topPx, bottomPx)) }.toMutableList()

        // Second pass (bottom-up): pull up if we overflow bottom bound
        for (i in clamped.size - 2 downTo 0) {
            val next = clamped[i + 1]
            val cur = clamped[i]
            val maxAllowed = next.y - minGapPx
            if (cur.y > maxAllowed) {
                clamped[i] = cur.copy(y = maxAllowed.coerceIn(topPx, bottomPx))
            }
        }

        // Restore original order
        val out = FloatArray(desiredYs.size)
        clamped.forEach { out[it.idx] = it.y }
        return out.toList()
    }


// Ensure at least 18dp between labels
    val minGapPx = with(density) { 18.dp.toPx() }
    val resolvedYs = resolveLabelPositions(
        desiredYs = desiredYs,
        minGapPx = minGapPx,
        topPx = chartTop,
        bottomPx = chartBottom
    )

    val labelOffsetX = with(density) { 8.dp.toPx() }      // distance from tick/marker
    val leaderLen = with(density) { 6.dp.toPx() }         // small leader line
    val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = with(density) { 12.sp.toPx() }
        color = android.graphics.Color.DKGRAY
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    dataPoints.forEachIndexed { i, dp ->
        val yPx = resolvedYs[i]

        // Alternate sides to avoid horizontal overlap
        val leftSide = (i % 2 == 0)
        val baseX = if (leftSide) paddingPx else size.width - paddingPx
        val tickX = baseX
        val textX = if (leftSide) baseX + leaderLen + labelOffsetX else baseX - leaderLen - labelOffsetX

        // leader line
        drawLine(
            color = Color.Gray.copy(alpha = 0.6f),
            start = Offset(tickX, yPx),
            end = Offset(if (leftSide) tickX + leaderLen else tickX - leaderLen, yPx),
            strokeWidth = with(density) { 1.dp.toPx() }
        )

        // value text (format to 1 decimal if needed)
        val label = String.format("%.1f", dp.value)
        drawContext.canvas.nativeCanvas.drawText(
            label,
            textX,
            yPx + with(density) { 4.dp.toPx() }, // slight vertical centering
            textPaint
        )
    }


    // Draw drag indicator
    dragPosition?.let { position ->
        if (position.x >= chartRect.left && position.x <= chartRect.right &&
            position.y >= chartRect.top && position.y <= chartRect.bottom) {

            // Draw horizontal indicator line
            drawLine(
                color = Color(0xFF4CAF50),
                start = Offset(chartRect.left, position.y),
                end = Offset(chartRect.right, position.y),
                strokeWidth = 4.dp.toPx()
            )

            // Draw indicator circle
            drawCircle(
                color = Color(0xFF4CAF50),
                radius = 10.dp.toPx(),
                center = Offset(chartRect.left, position.y)
            )
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(chartRect.left, position.y)
            )

            // Calculate and display estimated value
            val relativeY = (chartRect.bottom - position.y) / chartRect.height
            val estimated = minValue + (maxValue - minValue) * relativeY

            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.1f", estimated),
                chartRect.right + 15.dp.toPx(),
                position.y + 6.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#4CAF50")
                    textSize = 22.sp.toPx()
                    isFakeBoldText = true
                    isAntiAlias = true
                }
            )
        }
    }
}

package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// Data classes
data class MathExpression(
    val id: Int,
    val leftNumber: Int,
    val operator: String,
    val rightNumber: Int,
    val result: Int,
    val missingType: MissingType,
    val missingValue: String,
    var yPosition: Float = 0f,
    var isCompleted: Boolean = false,
    var isCorrect: Boolean = false,
    var droppedValue: String? = null
)

enum class MissingType {
    LEFT_NUMBER, OPERATOR, RIGHT_NUMBER, RESULT
}

data class DraggableItem(
    val value: String,
    val isNumber: Boolean,
    var position: Offset = Offset.Zero,
    var isDragging: Boolean = false,
    var isUsed: Boolean = false
)

// Puzzle colors
object PuzzleColors {
    val Background = Color(0xFF2D5D4F)
    val ExpressionBg = Color(0xFF4A7C59)
    val NumberTile = Color(0xFF52C178)
    val OperatorTile = Color(0xFF52C178)
    val MissingSlot = Color(0xFF1A3D2E)
    val CorrectFeedback = Color(0xFF4CAF50)
    val IncorrectFeedback = Color(0xFFFF5252)
    val DragTileNumber = Color(0xFF26A69A)
    val DragTileOperator = Color(0xFFFF9800)
}

enum class GameState {
    PLAYING, WON, TIME_UP
}

@Composable
fun MathExpressionPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    onSubmitAnswer: (Boolean) -> Unit = {},
    fetchNextPuzzle: (Int) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val TAG = "MathExpression"

    // Score tracking state
    var totalScore by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var totalAttempts by remember { mutableIntStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expressionSolveTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var operationComplexity by remember { mutableStateOf<List<String>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    var missedExpressions by remember { mutableIntStateOf(0) }

    var expressions by remember { mutableStateOf(listOf<MathExpression>()) }
    var draggableItems by remember { mutableStateOf(listOf<DraggableItem>()) }
    var gameState by remember { mutableStateOf(GameState.PLAYING) }
    var selectedExpressionId by remember { mutableStateOf<Int?>(null) }
    var nextExpressionId by remember { mutableIntStateOf(1) }
    var lastY by remember { mutableStateOf(1000f) }
    var expressionStartTimes by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            120 // Default 2 minutes
        }
    }

    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentUserLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    val density = LocalDensity.current

    // Calculate algebraic thinking score
    fun calculateAlgebraicThinkingScore(
        correctAnswers: Int,
        totalAttempts: Int,
        avgSolveTime: Long,
        difficulty: String,
        timeSpentMs: Long,
        bestStreak: Int,
        operationTypes: List<String>,
        missedExpressions: Int
    ): Int {
        if (correctAnswers == 0) return 0

        // Base points by difficulty
        val basePointsPerExpression = when (difficulty.lowercase()) {
            "easy" -> 40
            "medium" -> 50
            "hard" -> 60
            "expert" -> 70
            else -> 50
        }

        val baseScore = correctAnswers * basePointsPerExpression

        // Algebraic complexity multiplier based on operations
        val complexityMultiplier = when {
            operationTypes.contains("÷") && operationTypes.contains("×") -> 1.8f // Mixed operations
            operationTypes.contains("÷") -> 1.6f // Division operations
            operationTypes.contains("×") -> 1.4f // Multiplication operations
            operationTypes.contains("-") -> 1.2f // Subtraction operations
            else -> 1.0f // Addition only
        }

        // Problem-solving accuracy bonus
        val accuracy = if (totalAttempts > 0) correctAnswers.toFloat() / totalAttempts else 0f
        val accuracyBonus = when {
            accuracy >= 0.9f -> (baseScore * 0.35f).toInt()
            accuracy >= 0.8f -> (baseScore * 0.2f).toInt()
            accuracy >= 0.7f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Mathematical reasoning speed bonus
        val avgSolveSeconds = avgSolveTime / 1000f
        val speedBonus = when {
            avgSolveSeconds <= 3.0f -> (baseScore * 0.25f).toInt()
            avgSolveSeconds <= 5.0f -> (baseScore * 0.15f).toInt()
            avgSolveSeconds <= 8.0f -> (baseScore * 0.05f).toInt()
            else -> 0
        }

        // Consecutive solving streak bonus
        val streakBonus = when {
            bestStreak >= 5 -> (baseScore * 0.3f).toInt() // Long streak
            bestStreak >= 3 -> (baseScore * 0.15f).toInt() // Good streak
            bestStreak >= 2 -> (baseScore * 0.05f).toInt() // Short streak
            else -> 0
        }

        // Efficiency penalty for missed expressions
        val efficiencyPenalty = missedExpressions * (basePointsPerExpression / 2)

        // Pattern recognition bonus for identifying missing elements
        val patternBonus = (baseScore * 0.15f).toInt()

        // Mathematical fluency bonus
        val fluencyBonus = (baseScore * 0.1f).toInt()

        val finalScore = ((baseScore * complexityMultiplier).toInt() + accuracyBonus + speedBonus + streakBonus + patternBonus + fluencyBonus - efficiencyPenalty)

        Log.d(TAG, "🏆 Algebraic thinking score calculation:")
        Log.d(TAG, "  Correct: $correctAnswers/$totalAttempts (${(accuracy * 100).toInt()}%)")
        Log.d(TAG, "  Base score: $baseScore")
        Log.d(TAG, "  Complexity multiplier: ${complexityMultiplier}x (operations: $operationTypes)")
        Log.d(TAG, "  Accuracy bonus: $accuracyBonus")
        Log.d(TAG, "  Speed bonus: $speedBonus (avg ${avgSolveSeconds}s)")
        Log.d(TAG, "  Streak bonus: $streakBonus (best: $bestStreak)")
        Log.d(TAG, "  Pattern bonus: $patternBonus")
        Log.d(TAG, "  Fluency bonus: $fluencyBonus")
        Log.d(TAG, "  Efficiency penalty: $efficiencyPenalty ($missedExpressions missed)")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, baseScore / 4) // Minimum 25% of base
    }

    // Generate initial expressions and draggable items
    LaunchedEffect(Unit) {
        val newExpressions = generateRandomExpressions(3, nextExpressionId, lastY)
        expressions = newExpressions
        draggableItems = generateDraggableItems()
        nextExpressionId += newExpressions.size
        lastY = newExpressions.maxOf { it.yPosition }
        gameStartTime = System.currentTimeMillis()

        // Track expression start times
        newExpressions.forEach { expr ->
            expressionStartTimes = expressionStartTimes + (expr.id to System.currentTimeMillis())
        }
    }

    // Timer countdown
    LaunchedEffect(timeRemaining, gameState) {
        if (timeRemaining > 0 && gameState == GameState.PLAYING) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && gameState == GameState.PLAYING) {
            // Time's up - calculate final score
            totalScore = calculateAlgebraicThinkingScore(
                correctAnswers = correctAnswers,
                totalAttempts = totalAttempts,
                avgSolveTime = if (expressionSolveTimes.isNotEmpty()) expressionSolveTimes.average().toLong() else 5000L,
                difficulty = difficulty,
                timeSpentMs = System.currentTimeMillis() - gameStartTime,
                bestStreak = bestStreak,
                operationTypes = operationComplexity.distinct(),
                missedExpressions = missedExpressions
            )

            gameState = GameState.TIME_UP
            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
        }
    }

    // Animation for moving expressions
    LaunchedEffect(expressions, gameState) {
        while (gameState == GameState.PLAYING) {
            delay(50) // 60 FPS
            expressions = expressions.map { expr ->
                val newY = expr.yPosition - 3.5f
                // Check if expression went off screen without being solved
                if (newY <= -200f && !expr.isCompleted) {
                    missedExpressions++
                }
                expr.copy(yPosition = newY)
            }
        }
    }

    // Check win condition
    LaunchedEffect(correctAnswers) {
        if (correctAnswers >= 10) { // Complete 10 expressions to win
            // Calculate final score
            totalScore = calculateAlgebraicThinkingScore(
                correctAnswers = correctAnswers,
                totalAttempts = totalAttempts,
                avgSolveTime = if (expressionSolveTimes.isNotEmpty()) expressionSolveTimes.average().toLong() else 5000L,
                difficulty = difficulty,
                timeSpentMs = System.currentTimeMillis() - gameStartTime,
                bestStreak = bestStreak,
                operationTypes = operationComplexity.distinct(),
                missedExpressions = missedExpressions
            )

            gameState = GameState.WON
            Log.d(TAG, "🎉 Game won! Final score: $totalScore")
        }
    }

    // Handle selection of answer option
    fun handleAnswerSelection(selectedValue: String) {
        val selectedExpression = expressions.find { it.id == selectedExpressionId }
        if (selectedExpression != null && !selectedExpression.isCompleted) {
            totalAttempts++

            // Calculate solve time
            val expressionStartTime = expressionStartTimes[selectedExpression.id] ?: System.currentTimeMillis()
            val solveTime = System.currentTimeMillis() - expressionStartTime
            expressionSolveTimes = expressionSolveTimes + solveTime
            operationComplexity = operationComplexity + selectedExpression.operator

            val isCorrect = selectedValue == selectedExpression.missingValue

            Log.d(TAG, "🧮 Expression ${selectedExpression.id}: Expected=${selectedExpression.missingValue}, " +
                    "Selected=$selectedValue, Correct=$isCorrect, SolveTime=${solveTime}ms")

            // Update the expression
            val updatedExpression = selectedExpression.copy(
                droppedValue = selectedValue,
                isCompleted = true,
                isCorrect = isCorrect
            )

            // Update expressions list
            expressions = expressions.map { expr ->
                if (expr.id == selectedExpression.id) updatedExpression else expr
            }

            if (isCorrect) {
                correctAnswers++
                streak++
                if (streak > bestStreak) bestStreak = streak
            } else {
                streak = 0
            }

            // Clear selection
            selectedExpressionId = null

            // Generate new expression after delay
            kotlinx.coroutines.GlobalScope.launch {
                delay(1500) // Give time to see feedback
                val updatedList = expressions.filter { it.id != selectedExpression.id }
                val maxY = updatedList.maxOfOrNull { it.yPosition } ?: 1000f

                // Generate one new expression using latest id and Y
                val newExpr = generateRandomExpressions(1, nextExpressionId, maxY + 300f)

                nextExpressionId += 1
                expressions = updatedList + newExpr
                lastY = newExpr.maxOf { it.yPosition }

                // Track new expression start time
                newExpr.forEach { expr ->
                    expressionStartTimes = expressionStartTimes + (expr.id to System.currentTimeMillis())
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PuzzleColors.Background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Enhanced Header with Score
                EnhancedMathExpressionTopBar(
                    level = currentUserLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    totalScore = totalScore,
                    correctAnswers = correctAnswers,
                    totalAttempts = totalAttempts,
                    streak = streak,
                    onBack = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                // Game area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Moving expressions
                    expressions.forEachIndexed { index, expression ->
                        if (expression.yPosition > -100f) {// Only show if in visible area
                            ExpressionView(
                                expression = expression,
                                modifier = Modifier.offset(
                                    x = 0.dp,
                                    y = with(density) { expression.yPosition.toDp() }
                                ),
                                isSelected = selectedExpressionId == expression.id,
                                onDropZoneClick = {
                                    if (!expression.isCompleted) {
                                        selectedExpressionId = if (selectedExpressionId == expression.id) null else expression.id
                                    }
                                }
                            )
                        }
                    }

                    // Clean up expressions that have moved off screen
                    LaunchedEffect(expressions) {
                        expressions = expressions.filter { it.yPosition > -200f }
                    }
                }

                // Selection items area
                SelectionItemsArea(
                    items = draggableItems,
                    selectedExpressionId = selectedExpressionId,
                    onItemSelected = { selectedValue ->
                        handleAnswerSelection(selectedValue)
                    }
                )
            }

            // Game over overlay
            if (gameState != GameState.PLAYING) {
                GameOverOverlay(
                    gameState = gameState,
                    totalScore = totalScore,
                    correctAnswers = correctAnswers,
                    totalAttempts = totalAttempts,
                    onTryAgain = {
                        // Reset game
                        expressions = generateRandomExpressions(5)
                        draggableItems = generateDraggableItems()
                        totalScore = 0
                        correctAnswers = 0
                        totalAttempts = 0
                        timeRemaining = totalTimeSeconds
                        gameState = GameState.PLAYING
                        selectedExpressionId = null
                        streak = 0
                        bestStreak = 0
                        missedExpressions = 0
                        expressionSolveTimes = emptyList()
                        operationComplexity = emptyList()
                        expressionStartTimes = emptyMap()
                        gameStartTime = System.currentTimeMillis()
                    },
                    onContinue = {
                        val isSuccess = gameState == GameState.WON

                        feedbackManager.showFeedback(
                            puzzleType = "algebraicThinking",
                            isCorrect = isSuccess,
                            userAnswer = "$correctAnswers expressions solved",
                            correctAnswer = "Solve mathematical expressions by filling missing elements",
                            timeSpent = System.currentTimeMillis() - gameStartTime,
                            difficulty = difficulty,
                            timeRemaining = timeRemaining,
                            totalTime = totalTimeSeconds,
                            onComplete = {
                                onSubmitAnswer(isSuccess)
                                Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                                fetchNextPuzzle(totalScore)
                            }
                        )
                    }
                )
            }
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
private fun EnhancedMathExpressionTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    totalScore: Int,
    correctAnswers: Int,
    totalAttempts: Int,
    streak: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Back button and level
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

                Column {
                    Text(
                        text = "Level ${level.level}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    LevelProgressBar(
                        level = level,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }

            // Center: Timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val timeValue = timer.substringAfter(":").toIntOrNull() ?: 0
                val isUrgent = timer.startsWith("0:") && timeValue <= 30

                Text(
                    text = timer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUrgent) Color.Red else Color(0xFFFFEB3B)
                )

                if (streakInfo.currentStreak > 0) {
                    StreakDisplay(
                        streakInfo = streakInfo,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Right side: Streak
            Column(
                horizontalAlignment = Alignment.End
            ) {
                if (streak > 1) {
                    Text(
                        text = "🔥 $streak",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B00)
                    )
                }

                Text(
                    text = "Goal: 10",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // Score and progress display
        if (totalScore > 0 || totalAttempts > 0) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (totalScore > 0) {
                    Text(
                        text = "${stringResource(R.string.score_label)}: $totalScore",
                        color = Color(0xFFFFEB3B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "📊 Math Expressions",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                if (totalAttempts > 0) {
                    Text(
                        text = "$correctAnswers solved",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressionView(
    expression: MathExpression,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onDropZoneClick: () -> Unit
) {
    val backgroundColor = when {
        expression.isCompleted && expression.isCorrect -> PuzzleColors.CorrectFeedback
        expression.isCompleted && !expression.isCorrect -> PuzzleColors.IncorrectFeedback
        else -> PuzzleColors.ExpressionBg
    }

    Row(
        modifier = modifier
            .background(
                backgroundColor,
                RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left number
        if (expression.missingType == MissingType.LEFT_NUMBER) {
            ClickableDropZone(
                isEmpty = expression.droppedValue == null,
                droppedValue = expression.droppedValue,
                isSelected = isSelected,
                onClick = onDropZoneClick
            )
        } else {
            NumberTile(expression.leftNumber.toString())
        }

        // Operator
        if (expression.missingType == MissingType.OPERATOR) {
            ClickableDropZone(
                isEmpty = expression.droppedValue == null,
                droppedValue = expression.droppedValue,
                isSelected = isSelected,
                onClick = onDropZoneClick
            )
        } else {
            OperatorTile(expression.operator)
        }

        // Right number
        if (expression.missingType == MissingType.RIGHT_NUMBER) {
            ClickableDropZone(
                isEmpty = expression.droppedValue == null,
                droppedValue = expression.droppedValue,
                isSelected = isSelected,
                onClick = onDropZoneClick
            )
        } else {
            NumberTile(expression.rightNumber.toString())
        }

        // Equals sign
        Text(
            text = "=",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        // Result
        if (expression.missingType == MissingType.RESULT) {
            ClickableDropZone(
                isEmpty = expression.droppedValue == null,
                droppedValue = expression.droppedValue,
                isSelected = isSelected,
                onClick = onDropZoneClick
            )
        } else {
            NumberTile(expression.result.toString())
        }
    }
}

@Composable
fun NumberTile(text: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(PuzzleColors.NumberTile, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun OperatorTile(operator: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(PuzzleColors.OperatorTile, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = operator,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ClickableDropZone(
    isEmpty: Boolean,
    droppedValue: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                when {
                    isSelected -> Color(0xFFFFEB3B) // Yellow when selected
                    !isEmpty -> PuzzleColors.NumberTile
                    else -> PuzzleColors.MissingSlot
                },
                RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isSelected) 3.dp else 2.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (droppedValue != null) {
            Text(
                text = droppedValue,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = "?",
                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.5f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SelectionItemsArea(
    items: List<DraggableItem>,
    selectedExpressionId: Int?,
    onItemSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E4A3A))
            .padding(16.dp)
    ) {
        // Show instruction text
        if (selectedExpressionId != null) {
            Text(
                text = "Tap an answer to complete the equation:",
                color = Color(0xFFFFEB3B),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        } else {
            Text(
                text = "Tap a missing square (?) in an equation above:",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Numbers grid: 2x4 layout for digits 2-9
        val numbers = items.filter { it.isNumber }
        val operators = items.filter { !it.isNumber }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // First row: 2, 3, 4, 5 + first two operators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // First 4 numbers (2, 3, 4, 5)
                numbers.take(4).forEach { item ->
                    SelectableItem(
                        item = item,
                        isEnabled = selectedExpressionId != null,
                        onClick = { onItemSelected(item.value) }
                    )
                }

                // Add spacing between numbers and operators
                Spacer(modifier = Modifier.width(16.dp))

                // First two operators (+ and -)
                operators.take(2).forEach { item ->
                    SelectableItem(
                        item = item,
                        isEnabled = selectedExpressionId != null,
                        onClick = { onItemSelected(item.value) }
                    )
                }
            }

            // Second row: 6, 7, 8, 9 + last two operators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Last 4 numbers (6, 7, 8, 9)
                numbers.drop(4).forEach { item ->
                    SelectableItem(
                        item = item,
                        isEnabled = selectedExpressionId != null,
                        onClick = { onItemSelected(item.value) }
                    )
                }

                // Add spacing between numbers and operators
                Spacer(modifier = Modifier.width(16.dp))

                // Last two operators (× and ÷)
                operators.drop(2).forEach { item ->
                    SelectableItem(
                        item = item,
                        isEnabled = selectedExpressionId != null,
                        onClick = { onItemSelected(item.value) }
                    )
                }
            }
        }
    }
}

@Composable
fun SelectableItem(
    item: DraggableItem,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        !isEnabled -> (if (item.isNumber) PuzzleColors.DragTileNumber else PuzzleColors.DragTileOperator).copy(alpha = 0.4f)
        else -> if (item.isNumber) PuzzleColors.DragTileNumber else PuzzleColors.DragTileOperator
    }
    val shape = if (item.isNumber) RoundedCornerShape(8.dp) else CircleShape

    Box(
        modifier = Modifier
            .size(56.dp)
            .background(backgroundColor, shape)
            .then(
                if (isEnabled) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.value,
            color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GameOverOverlay(
    gameState: GameState,
    totalScore: Int,
    correctAnswers: Int,
    totalAttempts: Int,
    onTryAgain: () -> Unit,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (gameState) {
                        GameState.WON -> "🎉 Excellent!"
                        GameState.TIME_UP -> "⏰ Time's Up!"
                        else -> stringResource(R.string.game_over)
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D5D4F)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${stringResource(R.string.score_label)}: $totalScore",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )

                    Text(
                        text = "$correctAnswers expressions solved",
                        fontSize = 16.sp,
                        color = Color(0xFF666666)
                    )

                    if (totalAttempts > 0) {
                        val accuracy = (correctAnswers.toFloat() / totalAttempts * 100).toInt()
                        Text(
                            text = "${stringResource(R.string.accuracy)}: $accuracy%",
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onTryAgain,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.try_again))
                    }

                    Button(
                        onClick = onContinue,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF52C178)
                        )
                    ) {
                        Text(stringResource(R.string.continue_label))
                    }
                }
            }
        }
    }
}

// Helper functions
fun generateRandomExpressions(count: Int, startId: Int = 1, baseY: Float = 1000f): List<MathExpression> {
    val expressions = mutableListOf<MathExpression>()
    val availableNumbers = (1..9).toList()
    val availableOperators = listOf("+", "-", "×", "÷")

    repeat(count) { index ->
        // Generate expression that contains at least one element from our available pool
        val expression = generateExpressionWithRequiredElements(availableNumbers, availableOperators)

        expressions.add(
            MathExpression(
                id = startId + index,
                leftNumber = expression.leftNumber,
                operator = expression.operator,
                rightNumber = expression.rightNumber,
                result = expression.result,
                missingType = expression.missingType,
                missingValue = expression.missingValue,
                yPosition = baseY + 300f * index
            )
        )
    }

    return expressions
}

data class GeneratedExpression(
    val leftNumber: Int,
    val operator: String,
    val rightNumber: Int,
    val result: Int,
    val missingType: MissingType,
    val missingValue: String
)

fun generateExpressionWithRequiredElements(
    availableNumbers: List<Int>,
    availableOperators: List<String>
): GeneratedExpression {
    val allOperators = listOf("+", "-", "×", "÷")
    val operator = allOperators.random()

    var leftNumber: Int
    var rightNumber: Int
    var result: Int

    // Generate expression with wider number ranges for more variety
    // BUT ensure result is never 1
    do {
        when (operator) {
            "+" -> {
                leftNumber = Random.nextInt(1, 50)
                rightNumber = Random.nextInt(1, 50)
                result = leftNumber + rightNumber
            }
            "-" -> {
                leftNumber = Random.nextInt(10, 100)
                rightNumber = Random.nextInt(1, leftNumber)
                result = leftNumber - rightNumber
            }
            "×" -> {
                leftNumber = Random.nextInt(1, 25)
                rightNumber = Random.nextInt(1, 15)
                result = leftNumber * rightNumber
            }
            "÷" -> {
                result = Random.nextInt(2, 30) // Start from 2 to avoid result = 1
                rightNumber = Random.nextInt(2, 20)
                leftNumber = result * rightNumber
            }
            else -> {
                leftNumber = 2
                rightNumber = 2
                result = 4
            }
        }
    } while (result == 1) // Keep generating until result is not 1

    // Now ensure at least one element is from our available pool
    ensureRequiredElementPresent(
        leftNumber, operator, rightNumber, result,
        availableNumbers, availableOperators
    ).let { updatedExpression ->
        leftNumber = updatedExpression.first
        val finalOperator = updatedExpression.second
        rightNumber = updatedExpression.third
        result = updatedExpression.fourth

        // Recalculate result if operator changed
        if (finalOperator != operator) {
            result = when (finalOperator) {
                "+" -> leftNumber + rightNumber
                "-" -> leftNumber - rightNumber
                "×" -> leftNumber * rightNumber
                "÷" -> if (rightNumber != 0 && leftNumber % rightNumber == 0) leftNumber / rightNumber else leftNumber
                else -> result
            }
        }

        // Ensure the final result is still not 1
        if (result == 1) {
            // Adjust to make result valid (2-9)
            when (finalOperator) {
                "+" -> {
                    leftNumber = 1
                    rightNumber = availableNumbers.random()
                    result = leftNumber + rightNumber
                }
                "-" -> {
                    val targetResult = availableNumbers.random()
                    leftNumber = targetResult + rightNumber
                    result = targetResult
                }
                "×" -> {
                    leftNumber = availableNumbers.random()
                    rightNumber = 1
                    result = leftNumber
                }
                "÷" -> {
                    result = availableNumbers.random()
                    rightNumber = 1
                    leftNumber = result
                }
            }
        }

        // Choose what to make missing with EQUAL probability for all valid options
        val missingType = chooseMissingTypeEqually(leftNumber, finalOperator, rightNumber, result,
            availableNumbers, availableOperators)

        val missingValue = when (missingType) {
            MissingType.LEFT_NUMBER -> leftNumber.toString()
            MissingType.OPERATOR -> finalOperator
            MissingType.RIGHT_NUMBER -> rightNumber.toString()
            MissingType.RESULT -> result.toString()
        }

        return GeneratedExpression(leftNumber, finalOperator, rightNumber, result, missingType, missingValue)
    }
}

fun chooseMissingTypeEqually(
    leftNum: Int,
    op: String,
    rightNum: Int,
    res: Int,
    availableNumbers: List<Int>,
    availableOperators: List<String>
): MissingType {
    // Collect which elements are in our available pool
    val availableMissingTypes = mutableListOf<MissingType>()

    if (leftNum in availableNumbers) availableMissingTypes.add(MissingType.LEFT_NUMBER)
    if (op in availableOperators) availableMissingTypes.add(MissingType.OPERATOR)
    if (rightNum in availableNumbers) availableMissingTypes.add(MissingType.RIGHT_NUMBER)
    if (res in availableNumbers) availableMissingTypes.add(MissingType.RESULT)

    // Always choose randomly from available options with equal probability
    return if (availableMissingTypes.isNotEmpty()) {
        availableMissingTypes.random()
    } else {
        // Fallback (shouldn't happen due to ensureRequiredElementPresent)
        // But if it does, choose completely randomly
        MissingType.values().random()
    }
}

fun ensureRequiredElementPresent(
    leftNum: Int,
    op: String,
    rightNum: Int,
    res: Int,
    availableNumbers: List<Int>,
    availableOperators: List<String>
): Tuple4<Int, String, Int, Int> {

    // Check if any element is already in our available pool
    val hasAvailableElement = leftNum in availableNumbers ||
            op in availableOperators ||
            rightNum in availableNumbers ||
            res in availableNumbers

    if (hasAvailableElement) {
        return Tuple4(leftNum, op, rightNum, res)
    }

    // If no element is in our pool, replace one randomly
    when (Random.nextInt(4)) {
        0 -> {
            // Replace left number with available number
            val newLeft = availableNumbers.random()
            val newResult = when (op) {
                "+" -> newLeft + rightNum
                "-" -> newLeft - rightNum
                "×" -> newLeft * rightNum
                "÷" -> if (rightNum != 0 && newLeft % rightNum == 0) newLeft / rightNum else newLeft
                else -> res
            }
            return Tuple4(newLeft, op, rightNum, newResult)
        }
        1 -> {
            // Replace operator with available operator
            val newOp = availableOperators.random()
            val newResult = when (newOp) {
                "+" -> leftNum + rightNum
                "-" -> leftNum - rightNum
                "×" -> leftNum * rightNum
                "÷" -> if (rightNum != 0 && leftNum % rightNum == 0) leftNum / rightNum else leftNum
                else -> res
            }
            return Tuple4(leftNum, newOp, rightNum, newResult)
        }
        2 -> {
            // Replace right number with available number
            val newRight = availableNumbers.random()
            val newResult = when (op) {
                "+" -> leftNum + newRight
                "-" -> leftNum - newRight
                "×" -> leftNum * newRight
                "÷" -> if (newRight != 0 && leftNum % newRight == 0) leftNum / newRight else leftNum
                else -> res
            }
            return Tuple4(leftNum, op, newRight, newResult)
        }
        else -> {
            // Replace result with available number (and adjust equation backwards)
            val newResult = availableNumbers.random()
            when (op) {
                "+" -> {
                    // result = left + right, so left = result - right
                    val newLeft = newResult - rightNum
                    if (newLeft > 0) return Tuple4(newLeft, op, rightNum, newResult)
                }
                "-" -> {
                    // result = left - right, so left = result + right
                    val newLeft = newResult + rightNum
                    return Tuple4(newLeft, op, rightNum, newResult)
                }
                "×" -> {
                    // result = left × right, so left = result ÷ right
                    if (rightNum != 0 && newResult % rightNum == 0) {
                        val newLeft = newResult / rightNum
                        return Tuple4(newLeft, op, rightNum, newResult)
                    }
                }
                "÷" -> {
                    // result = left ÷ right, so left = result × right
                    val newLeft = newResult * rightNum
                    return Tuple4(newLeft, op, rightNum, newResult)
                }
            }
            // Fallback: replace left number
            val newLeft = availableNumbers.random()
            val finalResult = when (op) {
                "+" -> newLeft + rightNum
                "-" -> newLeft - rightNum
                "×" -> newLeft * rightNum
                "÷" -> if (rightNum != 0 && newLeft % rightNum == 0) newLeft / rightNum else newLeft
                else -> res
            }
            return Tuple4(newLeft, op, rightNum, finalResult)
        }
    }
}

fun generateDraggableItems(): List<DraggableItem> {
    // Only numbers 2-9 (8 numbers total)
    val numbers = (2..9).map {
        DraggableItem(
            value = it.toString(),
            isNumber = true
        )
    }

    val operators = listOf("+", "-", "×", "÷").map {
        DraggableItem(
            value = it,
            isNumber = false
        )
    }

    return numbers + operators
}

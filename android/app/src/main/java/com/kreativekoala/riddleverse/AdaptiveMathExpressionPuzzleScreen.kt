// AdaptiveMathExpressionPuzzleScreen.kt
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveMathExpressionPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String = "2:30",
    hearts: Int = 3,
    level: String = "1/5",
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveMathExpression"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("mathExpression"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // ✅ NEW: Competitive ranking state
    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    // Load competitive insight
    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "mathExpression",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate adaptive configuration
    val adaptiveConfig = remember(currentDifficultyLevel) {
        generateAdaptiveMathExpressionConfig(currentDifficultyLevel)
    }

    var timeLeft by remember { mutableStateOf(currentDifficultyLevel.timeLimit) }
    var isPaused by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var gameCompleted by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(true) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAttempts by remember { mutableStateOf(0) }

    // ✅ NEW: Track session for competitive ranking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expressionSolveTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var operationComplexity by remember { mutableStateOf<List<String>>(emptyList()) }
    var bestStreak by remember { mutableStateOf(0) }
    var missedExpressions by remember { mutableStateOf(0) }

    var expressions by remember { mutableStateOf(listOf<AdaptiveMathExpression>()) }
    var gameState by remember { mutableStateOf(AdaptiveGameState.PLAYING) }
    var selectedExpressionId by remember { mutableStateOf<Int?>(null) }
    var nextExpressionId by remember { mutableStateOf(1) }
    var lastY by remember { mutableStateOf(1000f) }
    var expressionStartTimes by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Use composable-scoped coroutine instead of GlobalScope
    val coroutineScope = rememberCoroutineScope()

    var currentInputValue by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    var showHint by remember { mutableStateOf(false) }


    val density = LocalDensity.current

    // ✅ UPDATED: Enhanced performance recording
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = adaptiveConfig.expressionComplexity,
            totalScore = totalScore,
            puzzleType = "mathExpression"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
            }
        }
    }

    // ✅ UPDATED: Use unified score calculation
    fun calculateScore(isCorrect: Boolean, timeSpent: Long): Int {
        return calculateUnifiedAdaptiveScore(
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            difficulty = currentDifficultyLevel,
            challengeComplexity = adaptiveConfig.expressionComplexity,
            currentStreak = currentStreak,
            challengesCompleted = correctAnswers,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "mathExpression"
        )
    }

    // Generate initial expressions
    LaunchedEffect(adaptiveConfig) {
        val newExpressions = generateAdaptiveRandomExpressions(adaptiveConfig, 3, nextExpressionId, lastY)
        expressions = newExpressions
        nextExpressionId += newExpressions.size
        lastY = newExpressions.maxOf { it.yPosition }
        gameStartTime = System.currentTimeMillis()

        newExpressions.forEach { expr ->
            expressionStartTimes = expressionStartTimes + (expr.id to System.currentTimeMillis())
        }
    }

    // Timer countdown
    LaunchedEffect(timeLeft, gameState) {
        if (timeLeft > 0 && gameState == AdaptiveGameState.PLAYING && !isPaused) {
            delay(1000L)
            timeLeft--
        } else if (timeLeft == 0 && gameState == AdaptiveGameState.PLAYING) {
            recordPerformance(false, currentDifficultyLevel.timeLimit * 1000L)
            gameState = AdaptiveGameState.TIME_UP
        }
    }

    // Animation for moving expressions
    LaunchedEffect(expressions, gameState) {
        while (gameState == AdaptiveGameState.PLAYING && !isPaused) {
            delay(50)
            expressions = expressions.map { expr ->
                val newY = expr.yPosition - adaptiveConfig.fallSpeed
                if (newY <= -200f && !expr.isCompleted) {
                    missedExpressions++
                }
                expr.copy(yPosition = newY)
            }
        }
    }

    // Check win condition
    LaunchedEffect(correctAnswers) {
        if (correctAnswers >= adaptiveConfig.targetExpressions) {
            gameState = AdaptiveGameState.WON
        }
    }

    // Handle keyboard input selection
    fun handleAnswerSelection(selectedValue: String) {
        when (selectedValue) {
            "CLEAR" -> {
                currentInputValue = ""
                return
            }
            "BACKSPACE" -> {
                if (currentInputValue.isNotEmpty()) {
                    currentInputValue = currentInputValue.dropLast(1)
                }
                return
            }
            else -> {
                val selectedExpression = expressions.find { it.id == selectedExpressionId }
                if (selectedExpression != null) {
                    val filteredValue = when (selectedExpression.missingType) {
                        AdaptiveMissingType.OPERATOR -> {
                            if (selectedValue in listOf("+", "-", "×", "÷")) {
                                selectedValue
                            } else {
                                currentInputValue
                            }
                        }
                        else -> {
                            if (selectedValue.all { it.isDigit() } && currentInputValue.length < 4) {
                                currentInputValue + selectedValue
                            } else {
                                currentInputValue
                            }
                        }
                    }
                    currentInputValue = filteredValue
                }
            }
        }
    }

    // Handle enter press
    fun handleEnterPress() {
        val selectedExpression = expressions.find { it.id == selectedExpressionId }
        if (selectedExpression != null && !selectedExpression.isCompleted && currentInputValue.isNotEmpty()) {
            totalAttempts++

            val expressionStartTime = expressionStartTimes[selectedExpression.id] ?: System.currentTimeMillis()
            val solveTime = System.currentTimeMillis() - expressionStartTime
            expressionSolveTimes = expressionSolveTimes + solveTime
            operationComplexity = operationComplexity + selectedExpression.operator

            val isCorrect = currentInputValue == selectedExpression.missingValue

            val updatedExpression = selectedExpression.copy(
                droppedValue = currentInputValue,
                isCompleted = true,
                isCorrect = isCorrect
            )

            expressions = expressions.map { expr ->
                if (expr.id == selectedExpression.id) updatedExpression else expr
            }

            if (isCorrect) {
                correctAnswers++
                currentStreak++
                if (currentStreak > bestStreak) bestStreak = currentStreak

                val score = calculateScore(true, solveTime)
                totalScore += score
            } else {
                currentStreak = 0
                currentHearts = maxOf(0, currentHearts - 1)
                if (currentHearts == 0) {
                    gameState = AdaptiveGameState.LOST
                }
            }

            recordPerformance(isCorrect, solveTime)

            selectedExpressionId = null
            currentInputValue = ""

            // Use composable-scoped coroutine instead of GlobalScope to prevent memory leaks
            coroutineScope.launch {
                delay(1500)
                val updatedList = expressions.filter { it.id != selectedExpression.id }
                val maxY = updatedList.maxOfOrNull { it.yPosition } ?: 1000f

                val newExpr = generateAdaptiveRandomExpressions(adaptiveConfig, 1, nextExpressionId, maxY + 300f)
                nextExpressionId += 1
                expressions = updatedList + newExpr
                lastY = newExpr.maxOf { it.yPosition }

                newExpr.forEach { expr ->
                    expressionStartTimes = expressionStartTimes + (expr.id to System.currentTimeMillis())
                }
            }
        }
    }

    // ✅ NEW: Session completion handling
    var sessionResult by remember { mutableStateOf<AdaptiveSessionResult?>(null) }

    if (gameState != AdaptiveGameState.PLAYING) {
        UnifiedSessionCompletionHandler(
            puzzleType = "mathExpression",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAttempts,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = bestStreak,
                winRate = if (totalAttempts > 0) correctAnswers.toFloat() / totalAttempts else 0f,
                totalScore = totalScore,
                averageTimePerPuzzle = if (expressionSolveTimes.isNotEmpty())
                    (expressionSolveTimes.average() / 1000).toInt()
                else 0,
                currentStreak = currentStreak,
                individualTimes = emptyList(),
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            sessionResult = result
            onGameComplete(gameState == AdaptiveGameState.WON, totalScore)
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

                // ✅ REPLACE: Use unified header instead of AdaptiveMathExpressionTopBar
                AdaptiveUnifiedHeader(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                    lives = currentHearts,
                    currentDifficulty = currentDifficultyLevel,
                    score = totalScore,
                    puzzleType = "mathExpression",
                    challengeNumber = correctAnswers,
                    totalChallenges = adaptiveConfig.targetExpressions,
                    competitiveInsight = competitiveInsight,
                    onBack = {
                        gameCompleted = true
                        onBack()
                    },
                    onPause = { isPaused = !isPaused },
                    onHint = {
                        showHint = !showHint
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ✅ REPLACE: Use unified adaptation notification
                UnifiedAdaptationNotification(
                    adaptationInfo = adaptationInfo,
                    puzzleType = "mathExpression",
                    visible = showAdaptationNotification,
                    onDismiss = { showAdaptationNotification = false }
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
                        if (expression.yPosition > -100f) {
                            AdaptiveExpressionView(
                                expression = expression,
                                modifier = Modifier.offset(
                                    x = 0.dp,
                                    y = with(density) { expression.yPosition.toDp() }
                                ),
                                isSelected = selectedExpressionId == expression.id,
                                currentInputValue = if (selectedExpressionId == expression.id) currentInputValue else "",
                                onDropZoneClick = {
                                    if (!expression.isCompleted) {
                                        selectedExpressionId = if (selectedExpressionId == expression.id) null else expression.id
                                        currentInputValue = ""
                                    }
                                }
                            )
                        }
                    }

                    LaunchedEffect(expressions) {
                        expressions = expressions.filter { it.yPosition > -200f }
                    }
                }

                // Custom keyboard area
                CustomMathKeyboard(
                    selectedExpressionId = selectedExpressionId,
                    currentInputValue = currentInputValue,
                    onItemSelected = { selectedValue ->
                        handleAnswerSelection(selectedValue)
                    },
                    onEnterPressed = {
                        handleEnterPress()
                    }
                )
            }

            // Game over overlay
            if (gameState != AdaptiveGameState.PLAYING) {
                AdaptiveGameOverOverlay(
                    gameState = gameState,
                    totalScore = totalScore,
                    correctAnswers = correctAnswers,
                    totalAttempts = totalAttempts,
                    targetExpressions = adaptiveConfig.targetExpressions,
                    onTryAgain = { },
                    onContinue = {
                        val isSuccess = gameState == AdaptiveGameState.WON
                        feedbackManager.showFeedback(
                            puzzleType = "mathExpression",
                            isCorrect = isSuccess,
                            userAnswer = "$correctAnswers expressions solved",
                            correctAnswer = "Solve mathematical expressions by filling missing elements",
                            timeSpent = System.currentTimeMillis() - gameStartTime,
                            difficulty = currentDifficultyLevel.name,
                            timeRemaining = timeLeft,
                            totalTime = currentDifficultyLevel.timeLimit,
                            onComplete = {
                                onGameComplete(isSuccess, totalScore)
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

// Rest of the existing functions remain the same...
@Composable
fun CustomMathKeyboard(
    selectedExpressionId: Int?,
    currentInputValue: String,
    onItemSelected: (String) -> Unit,
    onEnterPressed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E4A3A))
            .padding(16.dp)
    ) {
        // Instruction text and current input display
        if (selectedExpressionId != null) {
            Text(
                text = "Tap numbers/operators below, then press ENTER:",
                color = Color(0xFFFFEB3B),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (currentInputValue.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEB3B).copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = "Your answer: $currentInputValue",
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            Text(
                text = "Tap a missing square (?) in an equation above:",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Keyboard grid
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Numbers 2, 3, 4, 5 + operators +, -
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("2", "3", "4", "5").forEach { number ->
                    KeyboardButton(
                        text = number,
                        isEnabled = selectedExpressionId != null,
                        isNumber = true,
                        onClick = { onItemSelected(number) }
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                listOf("+", "-").forEach { operator ->
                    KeyboardButton(
                        text = operator,
                        isEnabled = selectedExpressionId != null,
                        isNumber = false,
                        onClick = { onItemSelected(operator) }
                    )
                }
            }

            // Row 2: Numbers 6, 7, 8, 9 + operators ×, ÷
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("6", "7", "8", "9").forEach { number ->
                    KeyboardButton(
                        text = number,
                        isEnabled = selectedExpressionId != null,
                        isNumber = true,
                        onClick = { onItemSelected(number) }
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                listOf("×", "÷").forEach { operator ->
                    KeyboardButton(
                        text = operator,
                        isEnabled = selectedExpressionId != null,
                        isNumber = false,
                        onClick = { onItemSelected(operator) }
                    )
                }
            }

            // Row 3: Utility buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Clear button
                Button(
                    onClick = { onItemSelected("CLEAR") },
                    enabled = selectedExpressionId != null && currentInputValue.isNotEmpty(),
                    modifier = Modifier.size(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5722),
                        disabledContainerColor = Color(0xFFFF5722).copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "C",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Numbers 0 and 1
                listOf("0", "1").forEach { number ->
                    KeyboardButton(
                        text = number,
                        isEnabled = selectedExpressionId != null,
                        isNumber = true,
                        onClick = { onItemSelected(number) }
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Backspace button
                Button(
                    onClick = { onItemSelected("BACKSPACE") },
                    enabled = selectedExpressionId != null && currentInputValue.isNotEmpty(),
                    modifier = Modifier.size(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800),
                        disabledContainerColor = Color(0xFFFF9800).copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "⌫",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Row 4: Enter button (full width)
            Button(
                onClick = onEnterPressed,
                enabled = selectedExpressionId != null && currentInputValue.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "ENTER ↵",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun KeyboardButton(
    text: String,
    isEnabled: Boolean,
    isNumber: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        !isEnabled -> (if (isNumber) PuzzleColors.DragTileNumber else PuzzleColors.DragTileOperator).copy(alpha = 0.4f)
        else -> if (isNumber) PuzzleColors.DragTileNumber else PuzzleColors.DragTileOperator
    }
    val shape = if (isNumber) RoundedCornerShape(8.dp) else CircleShape

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
            text = text,
            color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AdaptiveExpressionView(
    expression: AdaptiveMathExpression,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    currentInputValue: String = "",
    onDropZoneClick: () -> Unit
) {
    val backgroundColor = when {
        expression.isCompleted && expression.isCorrect -> PuzzleColors.CorrectFeedback
        expression.isCompleted && !expression.isCorrect -> PuzzleColors.IncorrectFeedback
        else -> PuzzleColors.ExpressionBg
    }

    Row(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left number
        if (expression.missingType == AdaptiveMissingType.LEFT_NUMBER) {
            AdaptiveClickableDropZone(
                isEmpty = expression.droppedValue == null,
                droppedValue = expression.droppedValue,
                isSelected = isSelected,
                currentInputValue = currentInputValue,
                onClick = onDropZoneClick
            )
        } else {
            AdaptiveNumberTile(expression.leftNumber.toString())
        }

        // Operator
        if (expression.missingType == AdaptiveMissingType.OPERATOR) {
            AdaptiveClickableDropZone(
                isEmpty = expression.droppedValue == null,
                droppedValue = expression.droppedValue,
                isSelected = isSelected,
                currentInputValue = currentInputValue,
                onClick = onDropZoneClick
            )
        } else {
            AdaptiveOperatorTile(expression.operator)
        }

        // Right number
        if (expression.missingType == AdaptiveMissingType.RIGHT_NUMBER) {
            AdaptiveClickableDropZone(
                isEmpty = expression.droppedValue == null,
                droppedValue = expression.droppedValue,
                isSelected = isSelected,
                currentInputValue = currentInputValue,
                onClick = onDropZoneClick
            )
        } else {
            AdaptiveNumberTile(expression.rightNumber.toString())
        }

        // Equals sign
        Text(
            text = "=",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        // Result
        if (expression.missingType == AdaptiveMissingType.RESULT) {
            AdaptiveClickableDropZone(
                isEmpty = expression.droppedValue == null,
                droppedValue = expression.droppedValue,
                isSelected = isSelected,
                currentInputValue = currentInputValue,
                onClick = onDropZoneClick
            )
        } else {
            AdaptiveNumberTile(expression.result.toString())
        }
    }
}

@Composable
fun AdaptiveClickableDropZone(
    isEmpty: Boolean,
    droppedValue: String?,
    isSelected: Boolean,
    currentInputValue: String = "",
    onClick: () -> Unit
) {
    val minWidth = 48.dp
    val contentWidth = when {
        droppedValue != null && droppedValue.length > 1 -> (droppedValue.length * 20 + 40).dp
        isSelected && currentInputValue.isNotEmpty() && currentInputValue.length > 1 -> (currentInputValue.length * 20 + 40).dp
        else -> minWidth
    }

    Box(
        modifier = Modifier
            .widthIn(min = minWidth)
            .width(contentWidth)
            .height(48.dp)
            .background(
                when {
                    isSelected -> Color(0xFFFFEB3B)
                    !isEmpty -> PuzzleColors.NumberTile
                    else -> PuzzleColors.MissingSlot
                },
                RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isSelected) 3.dp else 2.dp,
                color = if (isSelected) Color(0xFF1976D2) else Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when {
            droppedValue != null -> {
                Text(
                    text = droppedValue,
                    color = Color.White,
                    fontSize = if (droppedValue.length > 2) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            isSelected && currentInputValue.isNotEmpty() -> {
                Text(
                    text = currentInputValue,
                    color = Color.Black,
                    fontSize = if (currentInputValue.length > 2) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            else -> {
                Text(
                    text = "?",
                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.5f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AdaptiveNumberTile(text: String) {
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
fun AdaptiveOperatorTile(operator: String) {
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

// Configuration and data classes
data class AdaptiveMathExpressionConfig(
    val targetExpressions: Int,
    val expressionComplexity: Int,
    val operationTypes: List<String>,
    val numberRange: IntRange,
    val fallSpeed: Float,
    val name: String
)

data class AdaptiveMathExpression(
    val id: Int,
    val leftNumber: Int,
    val operator: String,
    val rightNumber: Int,
    val result: Int,
    val missingType: AdaptiveMissingType,
    val missingValue: String,
    var yPosition: Float = 0f,
    var isCompleted: Boolean = false,
    var isCorrect: Boolean = false,
    var droppedValue: String? = null
)

enum class AdaptiveMissingType {
    LEFT_NUMBER, OPERATOR, RIGHT_NUMBER, RESULT
}

enum class AdaptiveGameState {
    PLAYING, WON, TIME_UP, LOST
}

// Generate adaptive configuration
fun generateAdaptiveMathExpressionConfig(difficulty: DifficultyManager.DifficultyLevel): AdaptiveMathExpressionConfig {
    return when (difficulty.index) {
        0 -> AdaptiveMathExpressionConfig(
            targetExpressions = 5,
            expressionComplexity = 1,
            operationTypes = listOf("+", "-"),
            numberRange = 1..10,
            fallSpeed = 2.0f,
            name = "Beginner"
        )
        1 -> AdaptiveMathExpressionConfig(
            targetExpressions = 8,
            expressionComplexity = 2,
            operationTypes = listOf("+", "-", "×"),
            numberRange = 1..15,
            fallSpeed = 2.5f,
            name = "Easy"
        )
        2 -> AdaptiveMathExpressionConfig(
            targetExpressions = 10,
            expressionComplexity = 3,
            operationTypes = listOf("+", "-", "×"),
            numberRange = 1..25,
            fallSpeed = 3.0f,
            name = "Medium"
        )
        3 -> AdaptiveMathExpressionConfig(
            targetExpressions = 12,
            expressionComplexity = 4,
            operationTypes = listOf("+", "-", "×", "÷"),
            numberRange = 1..50,
            fallSpeed = 3.5f,
            name = "Hard"
        )
        4 -> AdaptiveMathExpressionConfig(
            targetExpressions = 15,
            expressionComplexity = 5,
            operationTypes = listOf("+", "-", "×", "÷"),
            numberRange = 1..100,
            fallSpeed = 4.0f,
            name = "Expert"
        )
        else -> AdaptiveMathExpressionConfig(
            targetExpressions = 10,
            expressionComplexity = 3,
            operationTypes = listOf("+", "-", "×"),
            numberRange = 1..25,
            fallSpeed = 3.0f,
            name = "Medium"
        )
    }
}

fun generateAdaptiveRandomExpressions(config: AdaptiveMathExpressionConfig, count: Int, startId: Int = 1, baseY: Float = 1000f): List<AdaptiveMathExpression> {
    val expressions = mutableListOf<AdaptiveMathExpression>()

    repeat(count) { index ->
        val expression = generateAdaptiveExpressionWithRequiredElements(config)

        expressions.add(
            AdaptiveMathExpression(
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

fun generateAdaptiveExpressionWithRequiredElements(config: AdaptiveMathExpressionConfig): AdaptiveMathExpression {
    val operator = config.operationTypes.random()
    var leftNumber: Int
    var rightNumber: Int
    var result: Int

    do {
        when (operator) {
            "+" -> {
                leftNumber = Random.nextInt(config.numberRange.first, config.numberRange.last + 1)
                rightNumber = Random.nextInt(config.numberRange.first, config.numberRange.last + 1)
                result = leftNumber + rightNumber
            }
            "-" -> {
                leftNumber = Random.nextInt(config.numberRange.first + 5, config.numberRange.last + 1)
                rightNumber = Random.nextInt(config.numberRange.first, leftNumber)
                result = leftNumber - rightNumber
            }
            "×" -> {
                leftNumber = Random.nextInt(config.numberRange.first, minOf(config.numberRange.last, 25))
                rightNumber = Random.nextInt(config.numberRange.first, minOf(config.numberRange.last, 15))
                result = leftNumber * rightNumber
            }
            "÷" -> {
                result = Random.nextInt(maxOf(config.numberRange.first, 2), config.numberRange.last + 1)
                rightNumber = Random.nextInt(maxOf(config.numberRange.first, 2), 20)
                leftNumber = result * rightNumber
            }
            else -> {
                leftNumber = 2
                rightNumber = 2
                result = 4
            }
        }
    } while (result == 1 || result > config.numberRange.last * 2)

    val missingType = AdaptiveMissingType.values().random()

    val missingValue = when (missingType) {
        AdaptiveMissingType.LEFT_NUMBER -> leftNumber.toString()
        AdaptiveMissingType.OPERATOR -> operator
        AdaptiveMissingType.RIGHT_NUMBER -> rightNumber.toString()
        AdaptiveMissingType.RESULT -> result.toString()
    }

    return AdaptiveMathExpression(
        id = 0,
        leftNumber = leftNumber,
        operator = operator,
        rightNumber = rightNumber,
        result = result,
        missingType = missingType,
        missingValue = missingValue
    )
}

@Composable
fun AdaptiveGameOverOverlay(
    gameState: AdaptiveGameState,
    totalScore: Int,
    correctAnswers: Int,
    totalAttempts: Int,
    targetExpressions: Int,
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
                        AdaptiveGameState.WON -> "🎉 Excellent!"
                        AdaptiveGameState.TIME_UP -> "⏰ Time's Up!"
                        AdaptiveGameState.LOST -> "💔 Game Over"
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
                        text = "$correctAnswers/$targetExpressions expressions solved",
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
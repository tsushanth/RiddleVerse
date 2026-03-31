// MemorySquaresPuzzleScreen.kt - Complete client-side rewrite with score tracking
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// Game phases
enum class MemoryGamePhase {
    COUNTDOWN,     // Initial countdown before showing pattern
    MEMORIZE,      // Pattern is visible for memorization
    RECALL,        // User clicks to recreate pattern
    FEEDBACK,      // Show results and feedback
    COMPLETED      // Game completed
}

// Data classes
data class MemorySquareCell(
    val row: Int,
    val col: Int,
    val isTarget: Boolean,
    val isSelected: Boolean = false,
    val isRevealed: Boolean = false
)

@Composable
fun MemorySquaresPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "1:30",
    hearts: Int = 3,
    level: String = "1/5",
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Score tracking state
    var totalScore by remember { mutableIntStateOf(0) }
    var correctSelections by remember { mutableIntStateOf(0) }
    var wrongSelections by remember { mutableIntStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Generate puzzle based on difficulty
    val puzzleConfig = remember(difficulty) {
        when (difficulty.lowercase()) {
            "easy" -> Triple(4, 3, 4) // gridSize, targetCount, memorizeTime
            "medium" -> Triple(5, 5, 3)
            "hard" -> Triple(6, 8, 2)
            "expert" -> Triple(7, 12, 2)
            else -> Triple(5, 5, 3)
        }
    }

    val gridSize = puzzleConfig.first
    val targetCount = puzzleConfig.second
    val memorizeTimeLimit = puzzleConfig.third

    // Timer calculation
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            90 // Default 90 seconds for memory squares
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    // Generate random target positions
    val targetPositions = remember {
        val allPositions = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                allPositions.add(Pair(i, j))
            }
        }
        allPositions.shuffled().take(targetCount).toSet()
    }

    // Game state
    var gamePhase by remember { mutableStateOf(MemoryGamePhase.COUNTDOWN) }
    var currentLives by remember { mutableIntStateOf(hearts) }
    var countdownTime by remember { mutableIntStateOf(3) }
    var memorizeTime by remember { mutableIntStateOf(memorizeTimeLimit) }
    var selectedCells by remember { mutableStateOf(setOf<Pair<Int, Int>>()) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var gameCompleted by remember { mutableStateOf(false) }
    var incorrectSelections by remember { mutableStateOf(setOf<Pair<Int, Int>>()) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Use composable-scoped coroutine instead of GlobalScope
    val coroutineScope = rememberCoroutineScope()

    // Timer countdown effect
    LaunchedEffect(timeRemaining, gameCompleted) {
        if (timeRemaining > 0 && !gameCompleted) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !gameCompleted) {
            // Time's up - complete with current score
            Log.d("MemorySquares", "⏰ Time's up! Final score: $totalScore")
            fetchNextPuzzle(totalScore)
        }
    }

    // Timer effects for game phases
    LaunchedEffect(gamePhase) {
        when (gamePhase) {
            MemoryGamePhase.COUNTDOWN -> {
                while (countdownTime > 0) {
                    delay(1000)
                    countdownTime--
                }
                gamePhase = MemoryGamePhase.MEMORIZE
                memorizeTime = memorizeTimeLimit
            }
            MemoryGamePhase.MEMORIZE -> {
                while (memorizeTime > 0) {
                    delay(1000)
                    memorizeTime--
                }
                gamePhase = MemoryGamePhase.RECALL
                gameStartTime = System.currentTimeMillis() // Reset for recall phase timing
            }
            else -> { /* No timer needed for other phases */ }
        }
    }

    // Calculate memory game score
    fun calculateMemoryScore(
        isCorrect: Boolean,
        correctCount: Int,
        wrongCount: Int,
        timeSpent: Long,
        difficulty: String,
        gridSize: Int,
        targetCount: Int
    ): Int {
        if (correctCount == 0) return 0

        // Base points per correct selection by difficulty
        val basePointsPerCell = when (difficulty.lowercase()) {
            "easy" -> 15
            "medium" -> 25
            "hard" -> 40
            "expert" -> 55
            else -> 25
        }

        // Correct selection score
        val correctScore = correctCount * basePointsPerCell

        // Grid complexity bonus
        val complexityMultiplier = when (gridSize) {
            4 -> 1.0f
            5 -> 1.2f
            6 -> 1.5f
            7 -> 1.8f
            else -> 1.0f
        }

        // Memory capacity bonus (more targets = harder)
        val capacityBonus = when (targetCount) {
            in 1..3 -> 0
            in 4..6 -> (correctScore * 0.2f).toInt()
            in 7..10 -> (correctScore * 0.4f).toInt()
            in 11..15 -> (correctScore * 0.6f).toInt()
            else -> (correctScore * 0.8f).toInt()
        }

        // Accuracy bonus (perfect memory)
        val accuracyBonus = if (isCorrect && wrongCount == 0) {
            (correctScore * 0.5f).toInt()
        } else {
            val accuracy = correctCount.toFloat() / targetCount
            (correctScore * accuracy * 0.2f).toInt()
        }

        // Speed bonus for recall phase
        val timeSeconds = timeSpent / 1000f
        val speedBonus = when {
            timeSeconds <= 10f -> (correctScore * 0.4f).toInt() // Very fast recall
            timeSeconds <= 20f -> (correctScore * 0.2f).toInt() // Fast recall
            timeSeconds <= 30f -> (correctScore * 0.1f).toInt() // Moderate recall
            else -> 0
        }

        // Wrong selection penalty
        val wrongPenalty = wrongCount * (basePointsPerCell / 2)

        // Lives preservation bonus
        val livesBonus = currentLives * (basePointsPerCell / 3)

        val finalScore = ((correctScore * complexityMultiplier).toInt() + capacityBonus + accuracyBonus + speedBonus + livesBonus - wrongPenalty)

        Log.d("MemorySquares", "🏆 Memory score calculation:")
        Log.d("MemorySquares", "  Grid: ${gridSize}x${gridSize}, Targets: $targetCount")
        Log.d("MemorySquares", "  Correct selections: $correctCount/$targetCount")
        Log.d("MemorySquares", "  Wrong selections: $wrongCount")
        Log.d("MemorySquares", "  Base score: $correctScore")
        Log.d("MemorySquares", "  Complexity multiplier: ${complexityMultiplier}x")
        Log.d("MemorySquares", "  Capacity bonus: $capacityBonus")
        Log.d("MemorySquares", "  Accuracy bonus: $accuracyBonus")
        Log.d("MemorySquares", "  Speed bonus: $speedBonus (${timeSeconds}s)")
        Log.d("MemorySquares", "  Lives bonus: $livesBonus")
        Log.d("MemorySquares", "  Wrong penalty: $wrongPenalty")
        Log.d("MemorySquares", "  Final score: $finalScore")

        return maxOf(finalScore, correctScore / 4) // Minimum 25% of base score
    }

    // Handle cell click during recall phase
    fun onCellClick(row: Int, col: Int) {
        if (gamePhase != MemoryGamePhase.RECALL || gameCompleted) return

        val cellPosition = Pair(row, col)
        val isTarget = targetPositions.contains(cellPosition)

        if (selectedCells.contains(cellPosition)) {
            // Unselect cell
            selectedCells = selectedCells - cellPosition
            incorrectSelections = incorrectSelections - cellPosition
            if (isTarget) {
                correctSelections--
            } else {
                wrongSelections--
            }
        } else {
            if (isTarget) {
                // Correct selection
                selectedCells = selectedCells + cellPosition
                correctSelections++

                // Check if all targets are selected
                if (selectedCells.size == targetCount &&
                    selectedCells.all { pos -> targetPositions.contains(pos) }) {

                    val timeSpent = System.currentTimeMillis() - gameStartTime
                    val gameScore = calculateMemoryScore(
                        isCorrect = true,
                        correctCount = correctSelections,
                        wrongCount = wrongSelections,
                        timeSpent = timeSpent,
                        difficulty = difficulty,
                        gridSize = gridSize,
                        targetCount = targetCount
                    )

                    totalScore = gameScore
                    isCorrect = true
                    gamePhase = MemoryGamePhase.FEEDBACK
                    showFeedback = true

                    Log.d("MemorySquares", "✅ Perfect memory! Score: $totalScore")
                }
            } else {
                // Wrong selection - lose a life and mark as incorrect
                incorrectSelections = incorrectSelections + cellPosition
                selectedCells = selectedCells + cellPosition
                currentLives--
                wrongSelections++

                if (currentLives <= 0) {
                    val timeSpent = System.currentTimeMillis() - gameStartTime
                    val gameScore = calculateMemoryScore(
                        isCorrect = false,
                        correctCount = correctSelections,
                        wrongCount = wrongSelections,
                        timeSpent = timeSpent,
                        difficulty = difficulty,
                        gridSize = gridSize,
                        targetCount = targetCount
                    )

                    totalScore = gameScore
                    isCorrect = false
                    gamePhase = MemoryGamePhase.FEEDBACK
                    showFeedback = true

                    Log.d("MemorySquares", "💔 No lives left. Final score: $totalScore")
                } else {
                    // Remove wrong selection after a brief moment
                    // Use composable-scoped coroutine instead of GlobalScope
                    coroutineScope.launch {
                        delay(800)
                        selectedCells = selectedCells - cellPosition
                        incorrectSelections = incorrectSelections - cellPosition
                        wrongSelections--
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF8D6E63))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Enhanced top bar with live timer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White
                )
            }

            Text(
                text = stringResource(R.string.memory_squares),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = displayTimer, // Use live countdown
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (timeRemaining <= 30) Color.Red else Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Enhanced game info bar with score and progress
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = 0.9f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LEVEL $level",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            if (totalScore > 0) {
                Text(
                    text = "SCORE $totalScore",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }

            Text(
                text = difficulty.uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        // Progress indicator if in recall phase
        if (gamePhase == MemoryGamePhase.RECALL && (correctSelections > 0 || wrongSelections > 0)) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "✅ $correctSelections",
                        fontSize = 12.sp,
                        color = Color.Green
                    )
                    Text(
                        text = "❌ $wrongSelections",
                        fontSize = 12.sp,
                        color = Color.Red
                    )
                    Text(
                        text = "❤️ $currentLives",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Phase indicator and timer
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (gamePhase) {
                MemoryGamePhase.COUNTDOWN -> {
                    Text(
                        text = stringResource(R.string.get_ready),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Memorize ${targetCount} squares in ${memorizeTimeLimit}s",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
                MemoryGamePhase.MEMORIZE -> {
                    Text(
                        text = stringResource(R.string.memorize_the_pattern),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${memorizeTime}s remaining",
                        fontSize = 16.sp,
                        color = if (memorizeTime <= 1) Color.Red else Color.White.copy(alpha = 0.8f)
                    )
                }
                MemoryGamePhase.RECALL -> {
                    Text(
                        text = stringResource(R.string.click_squares_you_remember),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Found: ${selectedCells.intersect(targetPositions).size}/$targetCount",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                else -> {
                    Text(
                        text = if (isCorrect) "Perfect!" else "Try Again!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCorrect) Color.Green else Color.Red
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Memory squares grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            val gridSizeDp = 280.dp
            val cellSize = (gridSizeDp.value - 8 * 2 - (gridSize - 1) * 4) / gridSize

            // Grid background
            Box(
                modifier = Modifier
                    .size(gridSizeDp)
                    .background(
                        Color.Black.copy(alpha = 0.8f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (row in 0 until gridSize) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (col in 0 until gridSize) {
                                val cellPosition = Pair(row, col)
                                val isTarget = targetPositions.contains(cellPosition)
                                val isSelected = selectedCells.contains(cellPosition)
                                val isIncorrect = incorrectSelections.contains(cellPosition)
                                val showHighlight = gamePhase == MemoryGamePhase.MEMORIZE && isTarget

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                            when {
                                                showHighlight -> Color(0xFF00BCD4) // Bright cyan for memorization
                                                isSelected && isTarget -> Color(0xFF4CAF50) // Green for correct selection
                                                isIncorrect -> Color(0xFFF44336) // Red for wrong selection
                                                gamePhase == MemoryGamePhase.FEEDBACK && isTarget -> Color(0xFF00BCD4) // Show correct answers
                                                else -> Color(0xFF6D4C41) // Brown for default
                                            },
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { onCellClick(row, col) }
                                        .border(
                                            1.dp,
                                            Color.Black.copy(alpha = 0.2f),
                                            RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Show feedback symbols
                                    when {
                                        isSelected && isTarget -> {
                                            Text(
                                                text = "✓",
                                                color = Color.White,
                                                fontSize = (cellSize * 0.4f).sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        isIncorrect -> {
                                            Text(
                                                text = "✗",
                                                color = Color.White,
                                                fontSize = (cellSize * 0.4f).sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        gamePhase == MemoryGamePhase.FEEDBACK && isTarget && !isSelected -> {
                                            Text(
                                                text = "?",
                                                color = Color.White,
                                                fontSize = (cellSize * 0.4f).sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Countdown overlay
            if (gamePhase == MemoryGamePhase.COUNTDOWN) {
                Box(
                    modifier = Modifier
                        .size(gridSizeDp)
                        .background(
                            Color.Black.copy(alpha = 0.9f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (countdownTime > 0) countdownTime.toString() else "GO!",
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Cyan
                        )

                        if (countdownTime > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Remember $targetCount squares",
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lives indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(hearts) { index ->
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Life",
                    tint = if (index < currentLives) Color.Red else Color.Gray,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons (shown during recall phase)
        if (gamePhase == MemoryGamePhase.RECALL) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        selectedCells = emptySet()
                        incorrectSelections = emptySet()
                        correctSelections = 0
                        wrongSelections = 0
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.clear))
                }

                Button(
                    onClick = {
                        // Submit current selection
                        val correctCount = selectedCells.intersect(targetPositions).size
                        val incorrectCount = selectedCells.size - correctCount
                        val timeSpent = System.currentTimeMillis() - gameStartTime

                        val gameScore = calculateMemoryScore(
                            isCorrect = correctCount == targetCount && incorrectCount == 0,
                            correctCount = correctCount,
                            wrongCount = incorrectCount,
                            timeSpent = timeSpent,
                            difficulty = difficulty,
                            gridSize = gridSize,
                            targetCount = targetCount
                        )

                        totalScore = gameScore
                        isCorrect = correctCount == targetCount && incorrectCount == 0
                        gamePhase = MemoryGamePhase.FEEDBACK
                        showFeedback = true

                        Log.d("MemorySquares", "📊 Manual submit - Score: $totalScore, Correct: $isCorrect")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4FC3F7)
                    )
                ) {
                    Text(stringResource(R.string.submit))
                }
            }
        }
    }

    // Enhanced feedback dialog with detailed scoring
    if (showFeedback) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
                Button(
                    onClick = {
                        showFeedback = false
                        gameCompleted = true
                        onSubmitAnswer(isCorrect)
                        Log.d("MemorySquares", "🎯 Calling fetchNextPuzzle with score: $totalScore")
                        fetchNextPuzzle(totalScore) // Pass accumulated score
                    }
                ) {
                    Text(stringResource(R.string.continue_label))
                }
            },
            title = {
                Text(
                    text = if (isCorrect) "🎉 Perfect Memory!" else if (currentLives > 0) "💪 Good Effort!" else "🎯 ${stringResource(R.string.game_over)}",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column {
                    val correctCount = selectedCells.intersect(targetPositions).size
                    val accuracy = if (targetCount > 0) (correctCount * 100) / targetCount else 0

                    // Score display
                    Text(
                        text = "${stringResource(R.string.final_score)}: $totalScore",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            isCorrect -> "You remembered all $targetCount squares correctly!\n\nAccuracy: 100%\nLives Remaining: $currentLives"
                            currentLives <= 0 -> "You found $correctCount/$targetCount squares correctly ($accuracy%)\n\nNo lives remaining"
                            else -> "You found $correctCount/$targetCount squares correctly ($accuracy%)\n\nLives remaining: $currentLives"
                        },
                        textAlign = TextAlign.Center
                    )

                    // Performance breakdown
                    if (correctSelections > 0 || wrongSelections > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF5F5F5)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Performance:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("✅ $correctSelections", fontSize = 10.sp)
                                    Text("❌ $wrongSelections", fontSize = 10.sp)
                                    Text("Grid: ${gridSize}x${gridSize}", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    if (!isCorrect) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 Tip: Try to create mental patterns or stories to remember the positions!",
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import org.json.JSONObject

data class HintState(
    val wordNumber: Int,
    val level: Int = 0 // 0 = original hint, 1 = starts with hint, 2 = length hint, etc.
)

data class CrosswordCell(
    val x: Int,
    val y: Int,
    val letter: String,
    val isBlocked: Boolean = false,
    var number: Int? = null,
    var userInput: String = "",
    var isHighlighted: Boolean = false,
    var isSelected: Boolean = false,
    var isError: Boolean = false
)

data class CrosswordWord(
    val word: String,
    val hint: String,
    val startX: Int,
    val startY: Int,
    val direction: String, // "horizontal" or "vertical"
    val length: Int,
    val number: Int
)

enum class Direction {
    HORIZONTAL, VERTICAL
}

@Composable
fun CrosswordPuzzleScreen(
    difficulty: String = "Easy",
    timer: String = "5:00",
    puzzleData: String, // JSON string containing crossword data
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var wordsCompleted by remember { mutableStateOf(0) }
    var hintsUsed by remember { mutableStateOf(0) }
    var revealsUsed by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Game state
    var keyboardTrigger by remember { mutableStateOf(0) }
    var forceKeyboardOpen by remember { mutableStateOf(false) }
    var hintStates by remember { mutableStateOf(mutableMapOf<Int, HintState>()) }

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            300 // Default 5 minutes for crossword
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    // Parse crossword data
    val (grid, words, gridWidth, gridHeight) = remember(puzzleData) {
        parseCrosswordData(puzzleData)
    }

    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var currentDirection by remember { mutableStateOf(Direction.HORIZONTAL) }
    var currentHint by remember { mutableStateOf("") }
    var currentWord by remember { mutableStateOf<CrosswordWord?>(null) }
    var isCompleted by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var showHintsDialog by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var completionTime by remember { mutableStateOf("") }
    var recompositionTrigger by remember { mutableStateOf(0) }
    var showIncorrectAnswersDialog by remember { mutableStateOf(false) }

    // Global input state for capturing keyboard input
    var globalInputValue by remember { mutableStateOf("") }

    // Calculate crossword-specific score
    fun calculateCrosswordScore(
        words: List<CrosswordWord>,
        wordsCompleted: Int,
        hintsUsed: Int,
        revealsUsed: Int,
        timeSpent: Int,
        difficulty: String,
        completed: Boolean
    ): Int {
        // Base points per word by difficulty
        val basePointsPerWord = when (difficulty.lowercase()) {
            "easy" -> 25
            "medium" -> 40
            "hard" -> 60
            "expert" -> 80
            else -> 40
        }

        // Word completion score
        val wordCompletionScore = wordsCompleted * basePointsPerWord

        // Vocabulary complexity bonus based on word lengths
        val complexityBonus = words.sumOf { word ->
            when {
                word.word.length >= 7 -> basePointsPerWord / 2 // Long words bonus
                word.word.length >= 5 -> basePointsPerWord / 4 // Medium words bonus
                else -> 0
            }
        }

        // Time efficiency bonus (if completed)
        val timeBonus = if (completed) {
            val timeEfficiency = timeRemaining.toFloat() / totalTimeSeconds
            (wordCompletionScore * timeEfficiency * 0.3f).toInt()
        } else 0

        // Completion bonus
        val completionBonus = if (completed && wordsCompleted == words.size) {
            when (difficulty.lowercase()) {
                "easy" -> 100
                "medium" -> 150
                "hard" -> 200
                "expert" -> 250
                else -> 150
            }
        } else 0

        // Penalty calculations
        val hintPenalty = hintsUsed * (basePointsPerWord / 4) // 25% penalty per hint
        val revealPenalty = revealsUsed * (basePointsPerWord / 2) // 50% penalty per reveal

        // Grid size bonus (5x5 is standard)
        val gridBonus = if (words.size >= 8) basePointsPerWord else 0

        val finalScore = wordCompletionScore + complexityBonus + timeBonus + completionBonus + gridBonus - hintPenalty - revealPenalty

        Log.d("CrosswordPuzzle", "🏆 Crossword score calculation:")
        Log.d("CrosswordPuzzle", "  Words completed: $wordsCompleted/${words.size}")
        Log.d("CrosswordPuzzle", "  Word completion score: $wordCompletionScore")
        Log.d("CrosswordPuzzle", "  Complexity bonus: $complexityBonus")
        Log.d("CrosswordPuzzle", "  Time bonus: $timeBonus")
        Log.d("CrosswordPuzzle", "  Completion bonus: $completionBonus")
        Log.d("CrosswordPuzzle", "  Grid bonus: $gridBonus")
        Log.d("CrosswordPuzzle", "  Hint penalty: $hintPenalty ($hintsUsed hints)")
        Log.d("CrosswordPuzzle", "  Reveal penalty: $revealPenalty ($revealsUsed reveals)")
        Log.d("CrosswordPuzzle", "  Final score: $finalScore")

        return maxOf(finalScore, wordCompletionScore / 4) // Minimum 25% of word completion score
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isCompleted) {
        if (timeRemaining > 0 && !isCompleted) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !isCompleted) {
            // Time's up - calculate final score and complete
            val finalScore = calculateCrosswordScore(
                words = words,
                wordsCompleted = wordsCompleted,
                hintsUsed = hintsUsed,
                revealsUsed = revealsUsed,
                timeSpent = totalTimeSeconds,
                difficulty = difficulty,
                completed = false
            )
            totalScore = finalScore
            Log.d("CrosswordPuzzle", "⏰ Time's up! Final score: $totalScore")
            fetchNextPuzzle(totalScore)
        }
    }

    // Update grid highlighting when selection changes
    LaunchedEffect(selectedCell, currentDirection) {
        selectedCell?.let { (x, y) ->
            updateGridHighlighting(grid, x, y, currentDirection, words)
            updateCurrentHint(x, y, currentDirection, words) { hint, word ->
                currentHint = hint
                currentWord = word
            }
        }
    }

    // Hints Dialog
    if (showHintsDialog) {
        HintsDialog(
            words = words,
            hintStates = hintStates,
            onHintLevelChange = { wordNumber, newLevel ->
                hintsUsed++ // Track hint usage for scoring
                hintStates = hintStates.toMutableMap().apply {
                    put(wordNumber, HintState(wordNumber, newLevel))
                }

                // Recalculate score when hints are used
                totalScore = calculateCrosswordScore(
                    words = words,
                    wordsCompleted = wordsCompleted,
                    hintsUsed = hintsUsed,
                    revealsUsed = revealsUsed,
                    timeSpent = totalTimeSeconds - timeRemaining,
                    difficulty = difficulty,
                    completed = false
                )
            },
            onDismiss = { showHintsDialog = false }
        )
    }

    // Enhanced Completion Dialog with score
    if (showCompletionDialog) {
        EnhancedCompletionDialog(
            time = completionTime,
            totalScore = totalScore,
            wordsCompleted = wordsCompleted,
            totalWords = words.size,
            hintsUsed = hintsUsed,
            revealsUsed = revealsUsed,
            onShare = { /* Handle share */ },
            onReset = {
                clearGrid(grid)
                showErrors = false
                isCompleted = false
                showCompletionDialog = false
                wordsCompleted = 0
                hintsUsed = 0
                revealsUsed = 0
                totalScore = 0
                gameStartTime = System.currentTimeMillis()
            },
            onNext = {
                showCompletionDialog = false
                Log.d("CrosswordPuzzle", "🎯 Calling fetchNextPuzzle with score: $totalScore")
                fetchNextPuzzle(totalScore)
            },
            onDismiss = { showCompletionDialog = false }
        )
    }

    // Incorrect Answers Dialog
    if (showIncorrectAnswersDialog) {
        IncorrectAnswersDialog(
            onDismiss = { showIncorrectAnswersDialog = false },
            onTryAgain = { showIncorrectAnswersDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF00BCD4)) // Cyan background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            // Enhanced Top Bar with live timer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

                Text(
                    text = displayTimer, // Use live countdown timer
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timeRemaining <= 60) Color.Red else Color.White // Red when ≤60s
                )

                IconButton(
                    onClick = {
                        showHintsDialog = true
                        // Don't increment hint counter here - only when actual hints are used
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Help,
                        contentDescription = stringResource(R.string.hint),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Score and progress display
            if (totalScore > 0 || wordsCompleted > 0 || hintsUsed > 0 || revealsUsed > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(8.dp)
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00BCD4)
                            )
                        }

                        Text(
                            text = "Words: $wordsCompleted/${words.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )

                        if (hintsUsed > 0 || revealsUsed > 0) {
                            Text(
                                text = "H:$hintsUsed R:$revealsUsed",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // Current Hint Display
            if (currentHint.isNotEmpty()) {
                currentWord?.let { word ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${word.number} ${if (currentDirection == Direction.HORIZONTAL) "Across" else "Down"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00BCD4)
                                )

                                val currentHintState = hintStates[word.number] ?: HintState(word.number)
                                val displayHint = getProgressiveHint(word, currentHintState.level)

                                Text(
                                    text = displayHint,
                                    fontSize = 14.sp,
                                    color = Color.Black,
                                    lineHeight = 16.sp
                                )
                            }

                            // Show arrow button if more hints are available
                            if (canShowMoreHints(word, hintStates[word.number]?.level ?: 0)) {
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        hintsUsed++ // Track individual hint usage
                                        val currentLevel = hintStates[word.number]?.level ?: 0
                                        hintStates = hintStates.toMutableMap().apply {
                                            put(word.number, HintState(word.number, currentLevel + 1))
                                        }

                                        // Update score when hint is used
                                        totalScore = calculateCrosswordScore(
                                            words = words,
                                            wordsCompleted = wordsCompleted,
                                            hintsUsed = hintsUsed,
                                            revealsUsed = revealsUsed,
                                            timeSpent = totalTimeSeconds - timeRemaining,
                                            difficulty = difficulty,
                                            completed = false
                                        )
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "More hint",
                                        tint = Color(0xFF00BCD4),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Crossword Grid - Centered and larger
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f),
                contentAlignment = Alignment.Center
            ) {
                CrosswordGrid(
                    grid = grid,
                    words = words,
                    gridWidth = gridWidth,
                    gridHeight = gridHeight,
                    onCellClick = { x, y ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        if (selectedCell == Pair(x, y)) {
                            currentDirection = if (currentDirection == Direction.HORIZONTAL) {
                                Direction.VERTICAL
                            } else {
                                Direction.HORIZONTAL
                            }
                        } else {
                            selectedCell = Pair(x, y)
                        }
                    }
                )
            }

            // Bottom action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Enhanced REVEAL button with scoring
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        currentWord?.let { word ->
                            val userAnswer = StringBuilder()
                            for (i in 0 until word.length) {
                                val x = if (currentDirection == Direction.HORIZONTAL) word.startX + i else word.startX
                                val y = if (currentDirection == Direction.VERTICAL) word.startY + i else word.startY
                                userAnswer.append(grid[y][x].userInput)
                            }

                            val userAnswerStr = userAnswer.toString().uppercase()
                            val correctAnswer = word.word.uppercase()
                            val isCorrect = userAnswerStr == correctAnswer

                            if (!isCorrect) {
                                revealsUsed++ // Track reveal usage for scoring

                                // Fill in the correct word
                                for (i in 0 until word.length) {
                                    val x = if (currentDirection == Direction.HORIZONTAL) word.startX + i else word.startX
                                    val y = if (currentDirection == Direction.VERTICAL) word.startY + i else word.startY
                                    grid[y][x].userInput = correctAnswer[i].toString()
                                }

                                // Update words completed count
                                wordsCompleted++

                                // Recalculate score
                                totalScore = calculateCrosswordScore(
                                    words = words,
                                    wordsCompleted = wordsCompleted,
                                    hintsUsed = hintsUsed,
                                    revealsUsed = revealsUsed,
                                    timeSpent = totalTimeSeconds - timeRemaining,
                                    difficulty = difficulty,
                                    completed = false
                                )

                                // Force recomposition to show the revealed letters immediately
                                recompositionTrigger += 1

                                checkCompletion(grid, words) { completed ->
                                    if (completed && !isCompleted) {
                                        isCompleted = true
                                        val allCorrect = validateAllAnswers(grid, words)
                                        if (allCorrect) {
                                            // Final score calculation for completion
                                            totalScore = calculateCrosswordScore(
                                                words = words,
                                                wordsCompleted = words.size,
                                                hintsUsed = hintsUsed,
                                                revealsUsed = revealsUsed,
                                                timeSpent = totalTimeSeconds - timeRemaining,
                                                difficulty = difficulty,
                                                completed = true
                                            )

                                            completionTime = displayTimer
                                            showCompletionDialog = true
                                            onSubmitAnswer(true)
                                        } else {
                                            showIncorrectAnswersDialog = true
                                            isCompleted = false
                                        }
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(2.dp, Color.White)
                ) {
                    Text("REVEAL", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Clear button
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        clearGrid(grid)
                        showErrors = false
                        isCompleted = false
                        selectedCell = null
                        showIncorrectAnswersDialog = false
                        currentHint = ""
                        globalInputValue = ""
                        // Reset scoring when clearing
                        wordsCompleted = 0
                        totalScore = 0
                        gameStartTime = System.currentTimeMillis()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(2.dp, Color.White)
                ) {
                    Text("CLEAR", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Enhanced keyboard with word completion tracking
            CrosswordKeyboard(
                onLetterClick = { letter ->
                    selectedCell?.let { (currentX, currentY) ->
                        val wordBeforeInput = getCurrentWordCompletion(currentX, currentY, currentDirection, grid, words)

                        fillCellAndAdvance(
                            x = currentX,
                            y = currentY,
                            letter = letter,
                            direction = currentDirection,
                            grid = grid,
                            words = words,
                            gridWidth = gridWidth,
                            gridHeight = gridHeight,
                            onAdvance = { newX, newY ->
                                selectedCell = Pair(newX, newY)
                            },
                            onComplete = {
                                recompositionTrigger += 1

                                // Check if a word was just completed
                                val wordAfterInput = getCurrentWordCompletion(currentX, currentY, currentDirection, grid, words)
                                if (!wordBeforeInput && wordAfterInput) {
                                    wordsCompleted++

                                    // Update score when word is completed
                                    totalScore = calculateCrosswordScore(
                                        words = words,
                                        wordsCompleted = wordsCompleted,
                                        hintsUsed = hintsUsed,
                                        revealsUsed = revealsUsed,
                                        timeSpent = totalTimeSeconds - timeRemaining,
                                        difficulty = difficulty,
                                        completed = false
                                    )
                                }

                                checkCompletion(grid, words) { completed ->
                                    if (completed && !isCompleted) {
                                        isCompleted = true
                                        val allCorrect = validateAllAnswers(grid, words)
                                        if (allCorrect) {
                                            // Final score calculation
                                            totalScore = calculateCrosswordScore(
                                                words = words,
                                                wordsCompleted = words.size,
                                                hintsUsed = hintsUsed,
                                                revealsUsed = revealsUsed,
                                                timeSpent = totalTimeSeconds - timeRemaining,
                                                difficulty = difficulty,
                                                completed = true
                                            )

                                            completionTime = displayTimer
                                            showCompletionDialog = true
                                            onSubmitAnswer(true)
                                        } else {
                                            showIncorrectAnswersDialog = true
                                            isCompleted = false
                                        }
                                    }
                                }
                            },
                            selectedCell = selectedCell
                        )
                        recompositionTrigger += 1
                    }
                },
                recompositionTrigger = recompositionTrigger,
                onBackspaceClick = {
                    selectedCell?.let { (currentX, currentY) ->
                        handleBackspace(
                            currentX = currentX,
                            currentY = currentY,
                            direction = currentDirection,
                            grid = grid,
                            words = words,
                            onMoveToPrevious = { newX, newY ->
                                selectedCell = Pair(newX, newY)
                            }
                        )
                        recompositionTrigger += 1
                    }
                },
                modifier = Modifier.weight(0.3f)
            )
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

// Helper function to check if a word is completed
fun getCurrentWordCompletion(
    x: Int,
    y: Int,
    direction: Direction,
    grid: List<List<CrosswordCell>>,
    words: List<CrosswordWord>
): Boolean {
    val currentWord = words.find { word ->
        val wordDirection = if (word.direction == "horizontal") Direction.HORIZONTAL else Direction.VERTICAL
        if (wordDirection == direction) {
            when (direction) {
                Direction.HORIZONTAL -> {
                    y == word.startY && x >= word.startX && x < word.startX + word.length
                }
                Direction.VERTICAL -> {
                    x == word.startX && y >= word.startY && y < word.startY + word.length
                }
            }
        } else false
    }

    return currentWord?.let { word ->
        for (i in 0 until word.length) {
            val cellX = if (direction == Direction.HORIZONTAL) word.startX + i else word.startX
            val cellY = if (direction == Direction.VERTICAL) word.startY + i else word.startY

            if (grid[cellY][cellX].userInput.isEmpty()) {
                return@let false
            }
        }
        true
    } ?: false
}

// Enhanced Completion Dialog with detailed scoring
@Composable
fun EnhancedCompletionDialog(
    time: String,
    totalScore: Int,
    wordsCompleted: Int,
    totalWords: Int,
    hintsUsed: Int,
    revealsUsed: Int,
    onShare: () -> Unit,
    onReset: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF00BCD4)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "${stringResource(R.string.puzzle_complete)} 🎉",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Solved in $time",
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Score display
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Final Score: $totalScore",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00BCD4)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Words: $wordsCompleted/$totalWords",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )

                            if (hintsUsed > 0 || revealsUsed > 0) {
                                Text(
                                    text = "Hints: $hintsUsed • Reveals: $revealsUsed",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "\"Eureka!\"",
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(2.dp, Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.share))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(2.dp, Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.reset),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.reset))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Next button
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.next_puzzle), color = Color(0xFF00BCD4))
                }
            }
        }
    }
}

@Composable
fun IncorrectAnswersDialog(
    onDismiss: () -> Unit,
    onTryAgain: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.some_answers_incorrect),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Try again or use the REVEAL button to fix incorrect answers.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(
                    onClick = onTryAgain,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00BCD4)
                    )
                ) {
                    Text(stringResource(R.string.try_again), color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CrosswordKeyboard(
    onLetterClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    recompositionTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val keyboardRows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M")
    )

    LaunchedEffect(recompositionTrigger) {
        // This will trigger when recompositionTrigger changes
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF87CEEB))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keyboardRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { letter ->
                    KeyboardButton(
                        text = letter,
                        onClick = { onLetterClick(letter) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Add backspace on the last row
                if (row == keyboardRows.last()) {
                    KeyboardButton(
                        text = "⌫",
                        onClick = onBackspaceClick,
                        modifier = Modifier.weight(1.2f),
                        backgroundColor = Color(0xFF5F9EA0)
                    )
                }
            }
        }
    }
}

@Composable
fun KeyboardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 2.dp)
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
fun CrosswordCell(
    cell: CrosswordCell,
    size: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clickable { onClick() }
            .background(
                color = when {
                    cell.isBlocked -> Color.Transparent
                    cell.isError -> Color.Red.copy(alpha = 0.3f)
                    cell.isSelected -> Color(0xFF4CAF50)
                    cell.isHighlighted -> Color(0xFF81C784)
                    else -> Color.White
                }
            )
            .border(
                width = 1.dp,
                color = Color.Gray.copy(alpha = 0.5f)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!cell.isBlocked) {
            // Number in top-left corner
            cell.number?.let { number ->
                Text(
                    text = number.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                )
            }

            // Letter display
            Text(
                text = cell.userInput,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CrosswordGrid(
    grid: List<List<CrosswordCell>>,
    words: List<CrosswordWord>,
    gridWidth: Int,
    gridHeight: Int,
    onCellClick: (Int, Int) -> Unit
) {
    // Fixed cell size for 5x5 grid
    val cellSize = 64.dp

    // Simple grid layout without scrolling
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (y in 0 until gridHeight) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (x in 0 until gridWidth) {
                    val cell = grid[y][x]
                    CrosswordCell(
                        cell = cell,
                        size = cellSize,
                        onClick = { onCellClick(x, y) }
                    )
                }
            }
        }
    }
}

// Parse crossword data function
fun parseCrosswordData(puzzleData: String): Tuple4<List<MutableList<CrosswordCell>>, List<CrosswordWord>, Int, Int> {
    try {
        val json = JSONObject(puzzleData)
        val wordsArray = json.getJSONArray("words")
        val matrix = json.getJSONArray("matrix")

        // Fixed 5x5 grid
        val gridWidth = 5
        val gridHeight = 5

        val tempWords = mutableListOf<CrosswordWord>()
        for (i in 0 until wordsArray.length()) {
            val wordObj = wordsArray.getJSONObject(i)
            val word = CrosswordWord(
                word = wordObj.getString("word"),
                hint = wordObj.getString("hint"),
                startX = wordObj.getInt("startX"),
                startY = wordObj.getInt("startY"),
                direction = wordObj.getString("direction"),
                length = wordObj.getInt("length"),
                number = i + 1
            )
            tempWords.add(word)
        }

        // Create 5x5 grid
        val grid = MutableList(gridHeight) { y ->
            MutableList(gridWidth) { x ->
                val matrixRow = matrix.getJSONArray(y)
                val cellValue = matrixRow.getString(x)
                CrosswordCell(
                    x = x,
                    y = y,
                    letter = if (cellValue == "_") "" else cellValue,
                    isBlocked = cellValue == "_",
                    userInput = ""
                )
            }
        }

        // Add numbers to starting cells
        tempWords.forEach { word ->
            if (word.startY < gridHeight && word.startX < gridWidth) {
                grid[word.startY][word.startX].number = word.number
            }
        }

        return Tuple4(grid, tempWords, gridWidth, gridHeight)
    } catch (e: Exception) {
        println("CrosswordPuzzleScreen: Error: ${e.message}")
        // Return empty 5x5 grid on error
        val emptyGrid = MutableList(5) { y ->
            MutableList(5) { x ->
                CrosswordCell(x = x, y = y, letter = "", isBlocked = true, userInput = "")
            }
        }
        return Tuple4(emptyGrid, emptyList(), 5, 5)
    }
}

// Fill cell and advance function
fun fillCellAndAdvance(
    x: Int,
    y: Int,
    letter: String,
    direction: Direction,
    grid: List<MutableList<CrosswordCell>>,
    words: List<CrosswordWord>,
    gridWidth: Int,
    gridHeight: Int,
    selectedCell: Pair<Int, Int>?,
    onAdvance: (Int, Int) -> Unit,
    onComplete: () -> Unit
) {
    println("CrosswordPuzzleScreen: 📝 Input: '$letter' at ($x, $y) direction=$direction")

    // Fill the current selected cell first
    if (y < grid.size && x < grid[0].size && !grid[y][x].isBlocked) {
        val oldValue = grid[y][x].userInput
        grid[y][x] = grid[y][x].copy(
            userInput = letter.uppercase(),
            isError = false
        )
        println("CrosswordPuzzleScreen:    ✅ Updated cell ($x, $y): '$oldValue' → '${letter.uppercase()}'")
    } else {
        println("CrosswordPuzzleScreen:    ❌ Cannot fill cell ($x, $y): blocked=${grid[y][x].isBlocked}")
        return
    }

    // Find the next cell to advance to
    val currentWord = words.find { word ->
        val wordDirection = if (word.direction == "horizontal") Direction.HORIZONTAL else Direction.VERTICAL
        if (wordDirection == direction) {
            when (direction) {
                Direction.HORIZONTAL -> {
                    y == word.startY && x >= word.startX && x < word.startX + word.length
                }
                Direction.VERTICAL -> {
                    x == word.startX && y >= word.startY && y < word.startY + word.length
                }
            }
        } else false
    }

    // Calculate next position
    if (currentWord != null) {
        val nextX = if (direction == Direction.HORIZONTAL) x + 1 else x
        val nextY = if (direction == Direction.VERTICAL) y + 1 else y

        val withinWordBounds = when (direction) {
            Direction.HORIZONTAL -> {
                nextY == currentWord.startY &&
                        nextX >= currentWord.startX &&
                        nextX < currentWord.startX + currentWord.length
            }
            Direction.VERTICAL -> {
                nextX == currentWord.startX &&
                        nextY >= currentWord.startY &&
                        nextY < currentWord.startY + currentWord.length
            }
        }

        // Only advance if next position is valid
        if (withinWordBounds &&
            nextX < gridWidth && nextY < gridHeight &&
            nextX >= 0 && nextY >= 0 &&
            !grid[nextY][nextX].isBlocked) {

            println("CrosswordPuzzleScreen:    ➡️ Advancing to ($nextX, $nextY)")
            onAdvance(nextX, nextY)
        } else {
            println("CrosswordPuzzleScreen:    🛑 Staying at ($x, $y) - end of word or invalid next position")
        }
    } else {
        println("CrosswordPuzzleScreen:    ❌ No word found containing this cell")
    }

    onComplete()
}

// Handle backspace
fun handleBackspace(
    currentX: Int,
    currentY: Int,
    direction: Direction,
    grid: List<MutableList<CrosswordCell>>,
    words: List<CrosswordWord>,
    onMoveToPrevious: (Int, Int) -> Unit
) {
    if (currentY < grid.size && currentX < grid[0].size && !grid[currentY][currentX].isBlocked) {
        val currentCell = grid[currentY][currentX]
        grid[currentY][currentX] = currentCell.copy(
            userInput = "",
            isError = false
        )
    }
}

// Progressive hint system
fun getProgressiveHint(word: CrosswordWord, level: Int): String {
    return when (level) {
        0 -> word.hint // Original hint
        1 -> "${word.hint}\n💡 Starts with: ${word.word.first()}"
        2 -> "${word.hint}\n💡 Starts with: ${word.word.first()}\n📏 ${word.word.length} letters"
        3 -> {
            val pattern = word.word.mapIndexed { index, char ->
                if (index == 0 || index == word.word.length - 1) char else '_'
            }.joinToString(" ")
            "${word.hint}\n💡 Pattern: $pattern"
        }
        4 -> {
            val vowels = word.word.count { it.uppercaseChar() in "AEIOU" }
            "${word.hint}\n💡 Starts with: ${word.word.first()}\n📏 ${word.word.length} letters\n🔤 Contains $vowels vowel${if (vowels != 1) "s" else ""}"
        }
        else -> word.hint
    }
}

fun canShowMoreHints(word: CrosswordWord, currentLevel: Int): Boolean {
    return currentLevel < 4 // Maximum 5 hint levels (0-4)
}

// Hints Dialog
@Composable
fun HintsDialog(
    words: List<CrosswordWord>,
    hintStates: Map<Int, HintState>,
    onHintLevelChange: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.all_hints),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    words.forEach { word ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF5F5F5)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${word.number} ${if (word.direction == "horizontal") "Across" else "Down"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00BCD4)
                                    )

                                    val currentLevel = hintStates[word.number]?.level ?: 0
                                    val displayHint = getProgressiveHint(word, currentLevel)

                                    Text(
                                        text = displayHint,
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                }

                                if (canShowMoreHints(word, hintStates[word.number]?.level ?: 0)) {
                                    IconButton(
                                        onClick = {
                                            val currentLevel = hintStates[word.number]?.level ?: 0
                                            onHintLevelChange(word.number, currentLevel + 1)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "More hint",
                                            tint = Color(0xFF00BCD4),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00BCD4)
                    )
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

// Grid highlighting functions
fun updateGridHighlighting(
    grid: List<MutableList<CrosswordCell>>,
    selectedX: Int,
    selectedY: Int,
    direction: Direction,
    words: List<CrosswordWord>
) {
    println("CrosswordPuzzleScreen: 🎯 Highlighting: cell ($selectedX, $selectedY) direction=$direction")

    // Clear all highlighting
    grid.forEach { row ->
        row.forEach { cell ->
            cell.isHighlighted = false
            cell.isSelected = false
        }
    }

    // Find the word that contains this cell in the current direction
    val currentWord = words.find { word ->
        val wordDirection = if (word.direction == "horizontal") Direction.HORIZONTAL else Direction.VERTICAL
        val matches = if (wordDirection == direction) {
            when (direction) {
                Direction.HORIZONTAL -> {
                    val inRow = selectedY == word.startY
                    val inRange = selectedX >= word.startX && selectedX < word.startX + word.length
                    println("CrosswordPuzzleScreen:    Checking horizontal word '${word.word}': inRow=$inRow, inRange=$inRange (startX=${word.startX}, endX=${word.startX + word.length - 1})")
                    inRow && inRange
                }
                Direction.VERTICAL -> {
                    val inCol = selectedX == word.startX
                    val inRange = selectedY >= word.startY && selectedY < word.startY + word.length
                    println("CrosswordPuzzleScreen:    Checking vertical word '${word.word}': inCol=$inCol, inRange=$inRange (startY=${word.startY}, endY=${word.startY + word.length - 1})")
                    inCol && inRange
                }
            }
        } else false
        matches
    }

    // Highlight the current word
    currentWord?.let { word ->
        for (i in 0 until word.length) {
            val x = if (direction == Direction.HORIZONTAL) word.startX + i else word.startX
            val y = if (direction == Direction.VERTICAL) word.startY + i else word.startY

            if (y < grid.size && x < grid[0].size) {
                grid[y][x].isHighlighted = true
            }
        }
    }

    // Mark selected cell
    if (selectedY < grid.size && selectedX < grid[0].size) {
        grid[selectedY][selectedX].isSelected = true
    }
}

fun updateCurrentHint(
    x: Int,
    y: Int,
    direction: Direction,
    words: List<CrosswordWord>,
    onHintUpdate: (String, CrosswordWord?) -> Unit
) {
    // Find any word that contains this cell in the current direction
    val currentWord = words.find { word ->
        val wordDirection = if (word.direction == "horizontal") Direction.HORIZONTAL else Direction.VERTICAL
        if (wordDirection == direction) {
            when (direction) {
                Direction.HORIZONTAL -> {
                    y == word.startY && x >= word.startX && x < word.startX + word.length
                }
                Direction.VERTICAL -> {
                    x == word.startX && y >= word.startY && y < word.startY + word.length
                }
            }
        } else false
    }

    if (currentWord != null) {
        onHintUpdate(currentWord.hint, currentWord)
    } else {
        onHintUpdate("", null)
    }
}

fun getDefaultDirection(x: Int, y: Int, words: List<CrosswordWord>, currentDirection: Direction): Direction {
    // Check if this is the start of a word
    val horizontalWord = words.find { it.startX == x && it.startY == y && it.direction == "horizontal" }
    val verticalWord = words.find { it.startX == x && it.startY == y && it.direction == "vertical" }

    return when {
        horizontalWord != null && verticalWord == null -> Direction.HORIZONTAL
        verticalWord != null && horizontalWord == null -> Direction.VERTICAL
        horizontalWord != null && verticalWord != null -> Direction.HORIZONTAL // Prefer horizontal for intersections
        else -> currentDirection // Keep current direction if not a word start
    }
}

fun checkCompletion(
    grid: List<List<CrosswordCell>>,
    words: List<CrosswordWord>,
    onComplete: (Boolean) -> Unit
) {
    val allFilled = words.all { word ->
        for (i in 0 until word.length) {
            val x = if (word.direction == "horizontal") word.startX + i else word.startX
            val y = if (word.direction == "vertical") word.startY + i else word.startY

            if (grid[y][x].userInput.isEmpty()) {
                return@all false
            }
        }
        true
    }

    onComplete(allFilled)
}

fun validateAllAnswers(grid: List<List<CrosswordCell>>, words: List<CrosswordWord>): Boolean {
    return words.all { word ->
        for (i in 0 until word.length) {
            val x = if (word.direction == "horizontal") word.startX + i else word.startX
            val y = if (word.direction == "vertical") word.startY + i else word.startY

            val expectedLetter = word.word[i].toString()
            val userInput = grid[y][x].userInput

            if (userInput != expectedLetter) {
                return@all false
            }
        }
        true
    }
}

fun markErrors(grid: List<MutableList<CrosswordCell>>, words: List<CrosswordWord>) {
    words.forEach { word ->
        for (i in 0 until word.length) {
            val x = if (word.direction == "horizontal") word.startX + i else word.startX
            val y = if (word.direction == "vertical") word.startY + i else word.startY

            val expectedLetter = word.word[i].toString()
            val userInput = grid[y][x].userInput

            if (userInput.isNotEmpty() && userInput != expectedLetter) {
                grid[y][x].isError = true
            }
        }
    }
}

fun clearGrid(grid: List<MutableList<CrosswordCell>>) {
    grid.forEach { row ->
        row.forEach { cell ->
            cell.userInput = ""
            cell.isError = false
            cell.isHighlighted = false
            cell.isSelected = false
        }
    }
}
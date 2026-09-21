// AdaptiveMathCrosswordPuzzleScreen.kt
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveMathCrosswordPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String = "4:00",
    hearts: Int = 3,
    level: String = "1/5",
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveMathCrossword"

    // Adaptive difficulty manager - UPDATED to use EnhancedAdaptiveDifficultyManager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("mathcrossword"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // ✅ NEW: Competitive ranking state
    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "mathcrossword",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate adaptive configuration
    val adaptiveConfig = remember(currentDifficultyLevel) {
        generateAdaptiveMathCrosswordConfig(currentDifficultyLevel)
    }

    var timeLeft by remember { mutableStateOf(currentDifficultyLevel.timeLimit) }
    var isPaused by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var gameCompleted by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(true) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // Game state
    var currentGrid by remember { mutableStateOf<List<List<MathCrosswordCell>>>(emptyList()) }
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var availableNumbers by remember { mutableStateOf<List<String>>(emptyList()) }
    var availableOperators by remember { mutableStateOf<List<String>>(emptyList()) }
    var usedNumbers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var usedOperators by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var totalScore by remember { mutableStateOf(0) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var isComplete by remember { mutableStateOf(false) }
    var selectedCellType by remember { mutableStateOf<CellType?>(null) }
    var answerData by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var puzzleStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // ✅ NEW: Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAnswers by remember { mutableStateOf(0) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()
    val haptics = LocalHapticFeedback.current
    var showHint by remember { mutableStateOf(false) }


    // Generate puzzle data based on adaptive config
    LaunchedEffect(adaptiveConfig) {
        val (puzzleDataStr, answerDataStr) = generateAdaptiveMathCrosswordPuzzle(adaptiveConfig)
        val puzzleResult = parseAdaptiveMathCrosswordData(puzzleDataStr)

        currentGrid = puzzleResult.puzzle.grid
        availableNumbers = puzzleResult.puzzle.availableNumbers
        availableOperators = puzzleResult.puzzle.availableOperators
        answerData = try { org.json.JSONObject(answerDataStr) } catch (e: Exception) { null }

        // Initialize used numbers count
        val numberCounts = mutableMapOf<String, Int>()
        puzzleResult.puzzle.availableNumbers.forEach { number ->
            numberCounts[number] = numberCounts.getOrDefault(number, 0) + 1
        }
        usedNumbers = numberCounts

        // Initialize used operators count
        val operatorCounts = mutableMapOf<String, Int>()
        puzzleResult.puzzle.availableOperators.forEach { operator ->
            operatorCounts[operator] = operatorCounts.getOrDefault(operator, 0) + 1
        }
        usedOperators = operatorCounts

        puzzleStartTime = System.currentTimeMillis()
        sessionStartTime = System.currentTimeMillis()
    }

    // ✅ UPDATED: Enhanced performance recording
    fun recordAdaptivePerformance(
        isCorrect: Boolean,
        timeSpent: Long,
        equationComplexity: Int
    ) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = equationComplexity,
            totalScore = totalScore,
            puzzleType = "mathcrossword"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = SHOW_ADAPTATION_NOTICES
            }
        }
    }

    // ✅ UPDATED: Use unified score calculation
    fun calculateAdaptiveMathCrosswordScore(
        isCorrect: Boolean,
        timeSpent: Long,
        equationComplexity: Int,
        gridUtilization: Float
    ): Int {
        return calculateUnifiedAdaptiveScore(
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            difficulty = currentDifficultyLevel,
            challengeComplexity = equationComplexity,
            currentStreak = currentStreak,
            challengesCompleted = 1,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "mathcrossword"
        )
    }

    // Timer logic
    LaunchedEffect(gameStarted, isPaused, gameCompleted) {
        if (gameStarted && !isPaused && !gameCompleted) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0) {
                // Time's up - record poor performance
                recordAdaptivePerformance(
                    isCorrect = false,
                    timeSpent = currentDifficultyLevel.timeLimit * 1000L,
                    equationComplexity = adaptiveConfig.equationComplexity
                )

                totalAnswers++
                gameCompleted = true
                showFeedback = true
                feedbackMessage = "Time's up! ⏰"
                onGameComplete(false, totalScore)
            }
        }
    }

    // Check completion
    LaunchedEffect(currentGrid) {
        if (currentGrid.isNotEmpty() && checkAllCellsFilled(currentGrid)) {
            val isValid = validateAllEquations(currentGrid)

            if (isValid && !isComplete) {
                gamesPlayedThisSession++
                currentStreak++
                correctAnswers++
                totalAnswers++

                val timeSpent = System.currentTimeMillis() - puzzleStartTime
                val score = calculateAdaptiveMathCrosswordScore(
                    isCorrect = true,
                    timeSpent = timeSpent,
                    equationComplexity = adaptiveConfig.equationComplexity,
                    gridUtilization = adaptiveConfig.gridUtilization
                )

                totalScore += score
                isComplete = true
                gameCompleted = true

                feedbackMessage = "🎉 Perfect! All equations are correct! +$score points"
                showFeedback = true

                // Record successful performance
                recordAdaptivePerformance(
                    isCorrect = true,
                    timeSpent = timeSpent,
                    equationComplexity = adaptiveConfig.equationComplexity
                )

                onGameComplete(true, totalScore)
            } else if (!isValid && checkAllCellsFilled(currentGrid)) {
                currentStreak = 0
                totalAnswers++
                showFeedback = true
                feedbackMessage = "All cells filled but some equations are incorrect. Please check your work! ❌"
            }
        }
    }

    // Update selected cell type when selection changes
    LaunchedEffect(selectedCell) {
        selectedCellType = selectedCell?.let { (row, col) ->
            if (row < currentGrid.size && col < currentGrid[row].size) {
                currentGrid[row][col].cellType
            } else null
        }
    }

    BCrosswordLayout(
        background = Color(0xFFF8F9FF),
        numberCount = usedNumbers.size,
        hud = { compact ->
            if (compact) {
                BCompactHud(
                    level = currentLevel,
                    difficultyName = currentDifficultyLevel.name,
                    timer = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                    lives = currentHearts,
                    maxLives = currentDifficultyLevel.livesAllowed,
                    score = totalScore,
                    streak = streakInfo.currentStreak,
                    onBack = {
                        gameCompleted = true
                        onBack()
                    },
                    onPause = { isPaused = !isPaused }
                )
            } else {
                AdaptiveUnifiedHeader(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                    lives = currentHearts,
                    currentDifficulty = currentDifficultyLevel,
                    score = totalScore,
                    puzzleType = "mathcrossword",
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
            }
            // "<Puzzle> Adapted!" banner (intentionally off via SHOW_ADAPTATION_NOTICES)
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "mathcrossword",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )
        },
        instruction = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Complete the math equations by placing missing numbers and operators",
                        fontSize = 14.sp,
                        color = Color(0xFF0D47A1),
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )

                    Text(
                        text = "Complexity: ${adaptiveConfig.equationComplexity}/5 • Grid: ${adaptiveConfig.gridSize}x${adaptiveConfig.gridSize}",
                        fontSize = 12.sp,
                        color = RvInkSoft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp).fillMaxWidth(),
                        maxLines = 1
                    )
                }
            }
        },
        grid = {
            if (currentGrid.isNotEmpty()) {
                CrosswordGridLayout(
                    grid = currentGrid,
                    selectedCell = selectedCell,
                    onCellClick = { row, col ->
                        val cell = currentGrid[row][col]
                        if (!cell.isFixed && cell.cellType != CellType.BLOCKED) {
                            selectedCell = Pair(row, col)
                        }
                    }
                )
            }
        },
        selectionLabel = {
    selectedCell?.let { (row, col) ->
        val cell = currentGrid[row][col]
        if (!cell.isFixed && cell.cellType != CellType.BLOCKED) {
            Text(
                text = when (cell.cellType) {
                    CellType.NUMBER -> "Select a number:"
                    CellType.OPERATOR -> "Select an operator:"
                    else -> "Select a value:"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = RvInk,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
        },
        numberPad = {
    if (selectedCellType == CellType.NUMBER && usedNumbers.isNotEmpty()) {
        NumberSelectionGrid(
            usedNumbers = usedNumbers,
            onNumberSelected = { number ->
                selectedCell?.let { (row, col) ->
                    val cell = currentGrid[row][col]
                    if (!cell.isFixed && cell.cellType == CellType.NUMBER && usedNumbers[number]!! > 0) {
                        // Remove old value if exists
                        val oldValue = cell.value
                        if (oldValue.isNotEmpty()) {
                            usedNumbers = usedNumbers.toMutableMap().apply {
                                this[oldValue] = this.getOrDefault(oldValue, 0) + 1
                            }
                        }

                        // Place new value
                        currentGrid = currentGrid.mapIndexed { r, rowCells ->
                            if (r == row) {
                                rowCells.mapIndexed { c, cellItem ->
                                    if (c == col) {
                                        cellItem.copy(value = number)
                                    } else cellItem
                                }
                            } else rowCells
                        }

                        // Update available numbers
                        usedNumbers = usedNumbers.toMutableMap().apply {
                            this[number] = this[number]!! - 1
                        }

                        selectedCell = null
                    }
                }
            }
        )
    }
        },
        operatorPad = {
    if (selectedCellType == CellType.OPERATOR && usedOperators.isNotEmpty()) {
        OperatorSelectionGrid(
            usedOperators = usedOperators,
            onOperatorSelected = { operator ->
                selectedCell?.let { (row, col) ->
                    val cell = currentGrid[row][col]
                    if (!cell.isFixed && cell.cellType == CellType.OPERATOR && usedOperators[operator]!! > 0) {
                        // Remove old value if exists
                        val oldValue = cell.value
                        if (oldValue.isNotEmpty()) {
                            usedOperators = usedOperators.toMutableMap().apply {
                                this[oldValue] = this.getOrDefault(oldValue, 0) + 1
                            }
                        }

                        // Place new value
                        currentGrid = currentGrid.mapIndexed { r, rowCells ->
                            if (r == row) {
                                rowCells.mapIndexed { c, cellItem ->
                                    if (c == col) {
                                        cellItem.copy(value = operator)
                                    } else cellItem
                                }
                            } else rowCells
                        }

                        // Update available operators
                        usedOperators = usedOperators.toMutableMap().apply {
                            this[operator] = this[operator]!! - 1
                        }

                        selectedCell = null
                    }
                }
            }
        )
    }
        },
        actions = {
            OutlinedButton(
                onClick = {
                    selectedCell?.let { (row, col) ->
                        if (row < currentGrid.size && col < currentGrid[row].size) {
                            val cell = currentGrid[row][col]
                            if (!cell.isFixed && cell.value.isNotEmpty()) {
                                val oldValue = cell.value

                                // Return value to appropriate pool
                                when (cell.cellType) {
                                    CellType.NUMBER -> {
                                        usedNumbers = usedNumbers.toMutableMap().apply {
                                            this[oldValue] = this.getOrDefault(oldValue, 0) + 1
                                        }
                                    }
                                    CellType.OPERATOR -> {
                                        usedOperators = usedOperators.toMutableMap().apply {
                                            this[oldValue] = this.getOrDefault(oldValue, 0) + 1
                                        }
                                    }
                                    else -> {}
                                }

                                // Clear cell
                                currentGrid = currentGrid.mapIndexed { r, rowCells ->
                                    if (r == row) {
                                        rowCells.mapIndexed { c, cellItem ->
                                            if (c == col) {
                                                cellItem.copy(value = "")
                                            } else cellItem
                                        }
                                    } else rowCells
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = selectedCell != null &&
                        selectedCell!!.let { (row, col) ->
                            row < currentGrid.size && col < currentGrid[row].size &&
                                    !currentGrid[row][col].isFixed &&
                                    currentGrid[row][col].value.isNotEmpty()
                        }
            ) {
                Text(stringResource(R.string.clear), maxLines = 1)
            }

            Button(
                onClick = {
                    if (checkAllCellsFilled(currentGrid)) {
                        val isValid = validateAllEquations(currentGrid)
                        if (isValid) {
                            // Already handled in LaunchedEffect
                        } else {
                            currentStreak = 0
                            totalAnswers++
                            val wrongEquations = findIncorrectEquations(currentGrid)
                            showFeedback = true
                            feedbackMessage = if (wrongEquations.isNotEmpty()) {
                                "Found ${wrongEquations.size} incorrect equation(s). Please check your work! 🤔"
                            } else {
                                "Some equations are incorrect. Keep trying! 🤔"
                            }

                            currentGrid = highlightIncorrectEquations(currentGrid, wrongEquations)
                        }
                    } else {
                        showFeedback = true
                        feedbackMessage = "Please fill in all empty cells first! 📝"
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = !isComplete && checkAllCellsFilled(currentGrid)
            ) {
                Text(stringResource(R.string.check), maxLines = 1)
            }

        }
    )

    // Feedback Dialog
    if (showFeedback) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
                Button(
                    onClick = {
                        showFeedback = false
                        if (isComplete) {
                            // Already called onGameComplete
                        }
                    }
                ) {
                    Text(if (isComplete) stringResource(R.string.continue_label) else stringResource(R.string.ok))
                }
            },
            title = {
                Text(
                    text = if (isComplete) "🎉 Completed!" else "💡 Hint",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(feedbackMessage)
                    if (isComplete) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${stringResource(R.string.score_label)}: $totalScore",
                            fontWeight = FontWeight.Bold,
                            color = RvSuccess
                        )
                    }
                }
            }
        )
    }

    // ✅ NEW: Session completion handling
    if (gameCompleted || isComplete) {
        UnifiedSessionCompletionHandler(
            puzzleType = "mathcrossword",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAnswers,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
                totalScore = totalScore,
                averageTimePerPuzzle = ((System.currentTimeMillis() - puzzleStartTime) / 1000.0).toInt(),
                currentStreak = currentStreak,
                individualTimes = listOf((System.currentTimeMillis() - puzzleStartTime).toInt())
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            // Session completion handled
        }
    }
}

// Configuration data class
data class AdaptiveMathCrosswordConfig(
    val gridSize: Int,
    val equationCount: Int,
    val equationComplexity: Int,
    val gridUtilization: Float,
    val maxValue: Int,
    val name: String
)

// Generate adaptive configuration
fun generateAdaptiveMathCrosswordConfig(difficulty: DifficultyManager.DifficultyLevel): AdaptiveMathCrosswordConfig {
    return when (difficulty.index) {
        0 -> AdaptiveMathCrosswordConfig( // Beginner
            gridSize = 7,
            equationCount = 3,
            equationComplexity = 1,
            gridUtilization = 0.4f,
            maxValue = 15,
            name = "Beginner"
        )
        1 -> AdaptiveMathCrosswordConfig( // Easy
            gridSize = 9,
            equationCount = 5,
            equationComplexity = 2,
            gridUtilization = 0.5f,
            maxValue = 25,
            name = "Easy"
        )
        2 -> AdaptiveMathCrosswordConfig( // Medium
            gridSize = 11,
            equationCount = 8,
            equationComplexity = 3,
            gridUtilization = 0.6f,
            maxValue = 40,
            name = "Medium"
        )
        3 -> AdaptiveMathCrosswordConfig( // Hard
            gridSize = 13,
            equationCount = 12,
            equationComplexity = 4,
            gridUtilization = 0.7f,
            maxValue = 60,
            name = "Hard"
        )
        4 -> AdaptiveMathCrosswordConfig( // Expert
            gridSize = 15,
            equationCount = 15,
            equationComplexity = 5,
            gridUtilization = 0.8f,
            maxValue = 100,
            name = "Expert"
        )
        else -> AdaptiveMathCrosswordConfig(
            gridSize = 11,
            equationCount = 8,
            equationComplexity = 3,
            gridUtilization = 0.6f,
            maxValue = 40,
            name = "Medium"
        )
    }
}

// Generate adaptive puzzle
fun generateAdaptiveMathCrosswordPuzzle(config: AdaptiveMathCrosswordConfig): Pair<String, String> {
    // Use the existing generator but with adaptive parameters
    val difficulty = config.name.lowercase()
    return MathCrosswordGenerator.generatePuzzle(difficulty)
}

// Parse adaptive puzzle data
fun parseAdaptiveMathCrosswordData(puzzleDataJson: String): MathCrosswordPuzzleResult {
    return parseMathCrosswordData(puzzleDataJson, "adaptive")
}
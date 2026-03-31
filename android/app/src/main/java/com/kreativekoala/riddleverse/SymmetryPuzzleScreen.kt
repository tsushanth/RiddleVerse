package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class SymmetryPuzzleData(
    val gridSize: Int,
    val leftPattern: List<List<Boolean>>,
    val correctRightPattern: List<List<Boolean>>,
    val difficulty: String,
    val questionNumber: Int,
    val isMirror: Boolean,
    val mirrorType: String // "horizontal", "vertical", or "none"
)

data class CellPosition(val row: Int, val col: Int)

@Composable
fun SymmetryPuzzleScreen(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    puzzleData: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    var parsedData by remember { mutableStateOf<SymmetryPuzzleData?>(null) }
    var userRightPattern by remember { mutableStateOf<List<MutableList<Boolean>>>(emptyList()) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0) }
    var timeRemaining by remember { mutableStateOf(symparseTimeToSeconds(timer)) }
    var isTimerRunning by remember { mutableStateOf(true) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var puzzleStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var requiredCells by remember { mutableStateOf(0) }

    // Parse puzzle data
    LaunchedEffect(puzzleData) {
        parsedData = parseSymmetryPuzzleData(puzzleData)
        parsedData?.let { data ->
            // Initialize user pattern with all false
            userRightPattern = List(data.gridSize) {
                MutableList(data.gridSize) { false }
            }
            // Count required cells for instant checking
            requiredCells = data.leftPattern.sumOf { row -> row.count { it } }
            puzzleStartTime = System.currentTimeMillis()
        }
    }

    // Auto-check when user selects the required number of cells
    LaunchedEffect(userRightPattern) {
        if (parsedData != null && !showFeedback) {
            val selectedCells = userRightPattern.sumOf { row -> row.count { it } }

            if (selectedCells == requiredCells && requiredCells > 0) {
                // User has selected the exact number of required cells - check instantly
                isTimerRunning = false
                val timeSpent = ((System.currentTimeMillis() - puzzleStartTime) / 1000.0)
                val correct = checkSymmetryPattern(parsedData!!.correctRightPattern, userRightPattern)
                isCorrect = correct

                if (correct) {
                    score = calculateSpeedScore(timeSpent, parsedData!!.gridSize, difficulty)
                    feedbackMessage = when {
                        timeSpent < 2.0 -> "Lightning fast! ⚡️"
                        timeSpent < 4.0 -> "Super quick! 🚀"
                        timeSpent < 7.0 -> "Nice speed! ✨"
                        else -> "Perfect! 🎯"
                    }
                } else {
                    currentHearts = maxOf(0, currentHearts - 1)
                    feedbackMessage = when {
                        timeSpent < 3.0 -> "Too fast! Look closer 👀"
                        else -> "Not quite right 🤔"
                    }
                }

                showFeedback = true
                onSubmitAnswer(correct)

                // Auto-continue after short delay
                delay(if (correct) 1200 else 1800) // Shorter delay for correct, longer for incorrect
                fetchNextPuzzle(score)
            }
        }
    }

    // Timer countdown
    LaunchedEffect(isTimerRunning) {
        while (timeRemaining > 0 && isTimerRunning) {
            delay(1000)
            timeRemaining--
        }
        if (timeRemaining <= 0 && isTimerRunning) {
            // Time's up
            isTimerRunning = false
            showFeedback = true
            isCorrect = false
            feedbackMessage = "Time's up! ⏰"
            onSubmitAnswer(false)
        }
    }

    parsedData?.let { data ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            SymmetryHeader(
                level = level,
                timer = symformatTime(timeRemaining),
                hearts = currentHearts,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Title with mirror indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Symmetry",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E2E2E)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Mirror indicator
                parsedData?.let { puzzle ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (puzzle.isMirror) Color(0xFFE3F2FD) else Color(0xFFF3E5F5)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (puzzle.isMirror) "🪞 MIRROR" else "📋 COPY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (puzzle.isMirror) Color(0xFF1976D2) else Color(0xFF7B1FA2),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Dynamic instruction based on mirror mode
            parsedData?.let { puzzle ->
                Text(
                    text = if (puzzle.isMirror) {
                        when (puzzle.mirrorType) {
                            "horizontal" -> "Create a horizontal mirror reflection ↔️"
                            "vertical" -> "Create a vertical mirror reflection ↕️"
                            else -> "Copy the exact pattern"
                        }
                    } else {
                        "Copy the exact pattern as fast as possible!"
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                )
            }

            // Speed encouragement
            Text(
                text = "⚡ Speed = More Points",
                fontSize = 12.sp,
                color = Color(0xFF2196F3),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Progress with speed indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${data.questionNumber} / ${getMaxQuestions(difficulty)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2196F3)
                )

                // Cell counter for user feedback
                parsedData?.let { puzzle ->
                    val selectedCells = userRightPattern.sumOf { row -> row.count { it } }
                    Text(
                        text = "$selectedCells / $requiredCells",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selectedCells == requiredCells) Color(0xFF4CAF50) else Color(0xFF666666)
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = data.questionNumber.toFloat() / getMaxQuestions(difficulty),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF2196F3),
                trackColor = Color(0xFFE0E0E0)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Grid section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left grid (pattern to copy/mirror)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (parsedData?.isMirror == true) "Mirror this:" else "Copy this:",
                        fontSize = 12.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    SymmetryGrid(
                        pattern = data.leftPattern,
                        isInteractive = false,
                        onCellClick = { _, _ -> }
                    )
                }

                // Divider with mirror indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mirror type indicator
                    parsedData?.let { puzzle ->
                        if (puzzle.isMirror) {
                            Text(
                                text = when (puzzle.mirrorType) {
                                    "horizontal" -> "↔️"
                                    "vertical" -> "↕️"
                                    else -> "="
                                },
                                fontSize = 20.sp,
                                color = Color(0xFF2196F3),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(if (parsedData?.isMirror == true) 100.dp else 120.dp)
                            .background(
                                if (parsedData?.isMirror == true) Color(0xFF2196F3) else Color(0xFFE0E0E0)
                            )
                    )
                }

                // Right grid (user input)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "To here:",
                        fontSize = 12.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    SymmetryGrid(
                        pattern = userRightPattern,
                        isInteractive = !showFeedback,
                        onCellClick = { row, col ->
                            if (!showFeedback) {
                                userRightPattern = userRightPattern.mapIndexed { r, rowList ->
                                    if (r == row) {
                                        rowList.mapIndexed { c, value ->
                                            if (c == col) !value else value
                                        }.toMutableList()
                                    } else {
                                        rowList.toMutableList()
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Feedback section (no continue button - auto-advances)
            if (showFeedback) {
                SymmetrySpeedFeedback(
                    isCorrect = isCorrect,
                    message = feedbackMessage,
                    score = score
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SymmetrySpeedFeedback(
    isCorrect: Boolean,
    message: String,
    score: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect) Color(0xFFE8F5E8) else Color(0xFFFFF3E0)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFE65100),
                textAlign = TextAlign.Center
            )

            if (isCorrect) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "+$score pts",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )

                Text(
                    text = "Speed Bonus Included! ⚡",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SymmetryHeader(
    level: String,
    timer: String,
    hearts: Int,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        Button(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("⏸", fontSize = 20.sp, color = Color(0xFF2E2E2E))
        }

        // Level
        Text(
            text = level,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF666666)
        )

        // Timer
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timer,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2196F3)
            )
        }

        // Hearts
        Row {
            repeat(3) { index ->
                Text(
                    text = if (index < hearts) "❤️" else "🤍",
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun SymmetryGrid(
    pattern: List<List<Boolean>>,
    isInteractive: Boolean,
    onCellClick: (Int, Int) -> Unit
) {
    val gridSize = pattern.size
    val cellSize = when (gridSize) {
        3 -> 35.dp
        4 -> 28.dp
        5 -> 22.dp
        6 -> 18.dp
        else -> 20.dp
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(8.dp)
    ) {
        pattern.forEachIndexed { rowIndex, row ->
            Row {
                row.forEachIndexed { colIndex, isSelected ->
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) Color(0xFF424242) else Color(0xFFF5F5F5)
                            )
                            .border(
                                1.dp,
                                Color(0xFFE0E0E0),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable(enabled = isInteractive) {
                                onCellClick(rowIndex, colIndex)
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun SymmetryFeedback(
    isCorrect: Boolean,
    message: String,
    score: Int,
    onContinue: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect) Color(0xFFE8F5E8) else Color(0xFFFFF3E0)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFE65100),
                textAlign = TextAlign.Center
            )

            if (isCorrect) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Score: +$score points",
                    fontSize = 14.sp,
                    color = Color(0xFF2E7D32)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFFF9800)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isCorrect) stringResource(R.string.continue_label) else stringResource(R.string.try_again),
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Helper functions
fun parseSymmetryPuzzleData(puzzleData: String): SymmetryPuzzleData? {
    return try {
        val json = JSONObject(puzzleData)
        val difficulty = json.optString("difficulty", "Medium")
        val questionNumber = json.optInt("questionNumber", 1)
        val gridSize = json.optInt("gridSize", getDynamicGridSize(difficulty, questionNumber))
        val isMirror = json.optBoolean("isMirror", Random.nextBoolean())
        val mirrorType = json.optString("mirrorType", if (isMirror) listOf("horizontal", "vertical").random() else "none")

        // Parse left pattern
        val leftPatternArray = json.optJSONArray("leftPattern")
        val leftPattern = if (leftPatternArray != null) {
            parsePatternFromJson(leftPatternArray)
        } else {
            generateRandomPattern(gridSize)
        }

        // Generate correct right pattern based on mirror mode
        val correctRightPattern = if (isMirror) {
            when (mirrorType) {
                "horizontal" -> mirrorPatternHorizontally(leftPattern)
                "vertical" -> mirrorPatternVertically(leftPattern)
                else -> leftPattern // fallback to copy
            }
        } else {
            leftPattern // exact copy
        }

        SymmetryPuzzleData(
            gridSize = gridSize,
            leftPattern = leftPattern,
            correctRightPattern = correctRightPattern,
            difficulty = difficulty,
            questionNumber = questionNumber,
            isMirror = isMirror,
            mirrorType = mirrorType
        )
    } catch (e: Exception) {
        // Generate default data if parsing fails
        val gridSize = 3
        val leftPattern = generateRandomPattern(gridSize)
        val isMirror = Random.nextBoolean()
        val mirrorType = if (isMirror) listOf("horizontal", "vertical").random() else "none"
        val correctRightPattern = if (isMirror) {
            when (mirrorType) {
                "horizontal" -> mirrorPatternHorizontally(leftPattern)
                "vertical" -> mirrorPatternVertically(leftPattern)
                else -> leftPattern
            }
        } else {
            leftPattern
        }

        SymmetryPuzzleData(
            gridSize = gridSize,
            leftPattern = leftPattern,
            correctRightPattern = correctRightPattern,
            difficulty = "Medium",
            questionNumber = 1,
            isMirror = isMirror,
            mirrorType = mirrorType
        )
    }
}

fun parsePatternFromJson(jsonArray: JSONArray): List<List<Boolean>> {
    val pattern = mutableListOf<List<Boolean>>()
    for (i in 0 until jsonArray.length()) {
        val row = jsonArray.getJSONArray(i)
        val rowPattern = mutableListOf<Boolean>()
        for (j in 0 until row.length()) {
            rowPattern.add(row.getBoolean(j))
        }
        pattern.add(rowPattern)
    }
    return pattern
}

fun generateRandomPattern(gridSize: Int): List<List<Boolean>> {
    val pattern = mutableListOf<List<Boolean>>()
    val fillPercentage = when (gridSize) {
        3 -> 0.4f  // 40% filled for 3x3
        4 -> 0.35f // 35% filled for 4x4
        5 -> 0.3f  // 30% filled for 5x5
        6 -> 0.25f // 25% filled for 6x6
        else -> 0.3f
    }

    for (row in 0 until gridSize) {
        val rowPattern = mutableListOf<Boolean>()
        for (col in 0 until gridSize) {
            rowPattern.add(Random.nextFloat() < fillPercentage)
        }
        pattern.add(rowPattern)
    }
    return pattern
}

fun getDynamicGridSize(difficulty: String, questionNumber: Int): Int {
    return when (difficulty.lowercase()) {
        "easy" -> when {
            questionNumber <= 15 -> 3
            questionNumber <= 25 -> 4
            else -> 5
        }
        "medium" -> when {
            questionNumber <= 10 -> 3
            questionNumber <= 20 -> 4
            questionNumber <= 35 -> 5
            else -> 6
        }
        "hard" -> when {
            questionNumber <= 5 -> 4
            questionNumber <= 15 -> 5
            else -> 6
        }
        else -> 4
    }
}

fun getMaxQuestions(difficulty: String): Int {
    return when (difficulty.lowercase()) {
        "easy" -> 30
        "medium" -> 40
        "hard" -> 50
        else -> 40
    }
}

fun checkSymmetryPattern(
    correct: List<List<Boolean>>,
    user: List<List<Boolean>>
): Boolean {
    if (correct.size != user.size) return false

    for (i in correct.indices) {
        if (correct[i].size != user[i].size) return false
        for (j in correct[i].indices) {
            if (correct[i][j] != user[i][j]) return false
        }
    }
    return true
}

fun calculateSpeedScore(timeSpentSeconds: Double, gridSize: Int, difficulty: String): Int {
    val baseScore = gridSize * gridSize * 20 // Higher base score (20 per cell instead of 10)

    // Speed multiplier based on time spent
    val speedMultiplier = when {
        timeSpentSeconds < 1.0 -> 3.0   // 300% bonus for under 1 second
        timeSpentSeconds < 2.0 -> 2.5   // 250% bonus for under 2 seconds
        timeSpentSeconds < 3.0 -> 2.0   // 200% bonus for under 3 seconds
        timeSpentSeconds < 5.0 -> 1.5   // 150% bonus for under 5 seconds
        timeSpentSeconds < 8.0 -> 1.2   // 120% bonus for under 8 seconds
        timeSpentSeconds < 12.0 -> 1.0  // 100% (no bonus) for under 12 seconds
        else -> 0.7 // 70% for slower completion
    }

    // Difficulty multiplier
    val difficultyMultiplier = when (difficulty.lowercase()) {
        "easy" -> 1.0
        "medium" -> 1.3
        "hard" -> 1.6
        else -> 1.0
    }

    return (baseScore * speedMultiplier * difficultyMultiplier).toInt().coerceAtLeast(10)
}

// Mirror transformation functions
fun mirrorPatternHorizontally(pattern: List<List<Boolean>>): List<List<Boolean>> {
    return pattern.map { row -> row.reversed() }
}

fun mirrorPatternVertically(pattern: List<List<Boolean>>): List<List<Boolean>> {
    return pattern.reversed()
}

fun mirrorPatternDiagonally(pattern: List<List<Boolean>>): List<List<Boolean>> {
    val size = pattern.size
    val result = MutableList(size) { MutableList(size) { false } }

    for (i in pattern.indices) {
        for (j in pattern[i].indices) {
            result[j][i] = pattern[i][j]
        }
    }

    return result.map { it.toList() }
}

fun symparseTimeToSeconds(timeString: String): Int {
    return try {
        val parts = timeString.split(":")
        val minutes = parts[0].toInt()
        val seconds = parts[1].toInt()
        minutes * 60 + seconds
    } catch (e: Exception) {
        120 // Default 2 minutes
    }
}

fun symformatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}
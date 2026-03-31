package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import org.json.JSONObject

data class DeflectorData(
    val row: Int,
    val col: Int,
    val type: Int, // 1 = slash (/), 2 = backslash (\)
    val isVisible: Boolean = true
)

data class BallPosition(
    val row: Int,
    val col: Int
)

data class BallDirection(
    val dr: Int,
    val dc: Int,
    val name: String
)

enum class PinballGameState {
    MEMORIZING,     // Show deflectors only - user memorizes positions
    GUESSING,       // Show start position only - user selects end position
    SHOWING_RESULT  // Show deflectors + ball path + result
}

@Composable
fun PinballDeflectorPuzzleScreen(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    puzzleData: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "PinballDeflectorScreen"

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var memoryPhaseStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var guessingPhaseStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var correctEndPosition by remember { mutableStateOf<BallPosition?>(null) }
    var trajectoryProgress by remember { mutableStateOf(0f) }

    // Parse puzzle data
    val parsedData = remember(puzzleData) {
        try {
            val questionData = JSONObject(puzzleData)
            val matrix = questionData.getJSONArray("matrix")
            val matrixSize = questionData.getInt("matrixSize")
            val startPos = questionData.getJSONArray("startPosition")
            val startDir = questionData.getJSONObject("startDirection")
            val memoryTime = questionData.getInt("memoryTime")

            val startPosition = BallPosition(
                row = startPos.getInt(0),
                col = startPos.getInt(1)
            )

            val startDirection = BallDirection(
                dr = startDir.getInt("dr"),
                dc = startDir.getInt("dc"),
                name = startDir.getString("name")
            )

            val deflectors = mutableListOf<DeflectorData>()
            for (i in 0 until matrixSize) {
                val row = matrix.getJSONArray(i)
                for (j in 0 until matrixSize) {
                    val value = row.getInt(j)
                    if (value == 1 || value == 2) {
                        deflectors.add(DeflectorData(i, j, value))
                    }
                }
            }

            Log.d(TAG, "📊 Parsed pinball puzzle:")
            Log.d(TAG, "   Matrix size: ${matrixSize}x${matrixSize}")
            Log.d(TAG, "   Start: [${startPosition.row},${startPosition.col}] ${startDirection.name}")
            Log.d(TAG, "   Deflectors: ${deflectors.size}")
            Log.d(TAG, "   Memory time: ${memoryTime}ms")

            mapOf(
                "matrixSize" to matrixSize,
                "startPosition" to startPosition,
                "startDirection" to startDirection,
                "deflectors" to deflectors,
                "memoryTime" to memoryTime,
                "success" to true
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse pinball puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false
    if (!isValidData) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Invalid puzzle data")
                Button(onClick = onBack) { Text(stringResource(R.string.go_back)) }
            }
        }
        return
    }

    // Extract parsed data
    val matrixSize = parsedData["matrixSize"] as Int
    val startPosition = parsedData["startPosition"] as BallPosition
    val startDirection = parsedData["startDirection"] as BallDirection
    @Suppress("UNCHECKED_CAST")
    val allDeflectors = parsedData["deflectors"] as List<DeflectorData>
    val memoryTime = parsedData["memoryTime"] as Int

    // Game state variables
    var gameState by remember { mutableStateOf(PinballGameState.MEMORIZING) }
    var selectedEndPosition by remember { mutableStateOf<BallPosition?>(null) }
    var timeLeft by remember { mutableStateOf(memoryTime / 1000) }
    var ballPath by remember { mutableStateOf<List<BallPosition>>(emptyList()) }
    var isCorrect by remember { mutableStateOf(false) }

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            120 // Default 2 minutes
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Timer countdown effect
    LaunchedEffect(timeRemaining, gameState) {
        if (timeRemaining > 0 && gameState != PinballGameState.SHOWING_RESULT) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && gameState != PinballGameState.SHOWING_RESULT) {
            // Time's up - complete with current score
            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            fetchNextPuzzle(totalScore)
        }
    }

    // Memory timer - only runs during MEMORIZING phase
    LaunchedEffect(gameState) {
        if (gameState == PinballGameState.MEMORIZING) {
            memoryPhaseStartTime = System.currentTimeMillis()
            Log.d(TAG, "🧠 Starting memorization phase - ${timeLeft}s to memorize deflectors")
            while (timeLeft > 0 && gameState == PinballGameState.MEMORIZING) {
                delay(1000)
                timeLeft--
            }
            if (gameState == PinballGameState.MEMORIZING) {
                Log.d(TAG, "⏰ Memorization time up - switching to guessing phase")
                gameState = PinballGameState.GUESSING
                guessingPhaseStartTime = System.currentTimeMillis()
            }
        }
    }

    // Animation for result phase
    LaunchedEffect(gameState) {
        if (gameState == PinballGameState.SHOWING_RESULT) {
            trajectoryProgress = 0f
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
            ) { value, _ ->
                trajectoryProgress = value
            }

            // Auto-proceed after animation completes with a brief delay
            delay(2000L) // 1500ms animation + 500ms pause

            // Use unified feedback system
            feedbackManager.showFeedback(
                puzzleType = "pinballDeflector",
                isCorrect = isCorrect,
                userAnswer = "Position [${selectedEndPosition?.row},${selectedEndPosition?.col}]",
                correctAnswer = "Position [${correctEndPosition?.row},${correctEndPosition?.col}]",
                timeSpent = (totalTimeSeconds - timeRemaining) * 1000L,
                difficulty = difficulty,
                timeRemaining = timeRemaining,
                totalTime = totalTimeSeconds,
                onComplete = {
                    onSubmitAnswer(isCorrect)
                    Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                    fetchNextPuzzle(totalScore)
                }
            )
        }
    }

    // Calculate score for pinball deflector puzzle
    fun calculatePinballScore(
        isCorrect: Boolean,
        memoryPhaseTime: Long,
        guessingPhaseTime: Long,
        attemptNumber: Int,
        matrixSize: Int,
        deflectorCount: Int,
        difficulty: String
    ): Int {
        if (!isCorrect) return 0

        // Base points by difficulty
        val basePoints = when (difficulty.lowercase()) {
            "easy" -> 50
            "medium" -> 75
            "hard" -> 100
            "expert" -> 125
            else -> 75
        }

        // Complexity bonus based on matrix size and deflector count
        val complexityMultiplier = when {
            matrixSize >= 6 && deflectorCount >= 8 -> 2.0f // Very complex
            matrixSize >= 5 && deflectorCount >= 6 -> 1.7f // Complex
            matrixSize >= 4 && deflectorCount >= 4 -> 1.4f // Moderate
            matrixSize >= 3 && deflectorCount >= 2 -> 1.2f // Simple
            else -> 1.0f // Basic
        }

        // Physics understanding bonus
        val physicsBonus = (basePoints * 0.3f).toInt()

        // Memory efficiency (quick memorization)
        val memorySeconds = memoryPhaseTime / 1000f
        val memoryBonus = when {
            memorySeconds <= 3f -> (basePoints * 0.4f).toInt() // Excellent memory
            memorySeconds <= 5f -> (basePoints * 0.2f).toInt() // Good memory
            memorySeconds <= 8f -> (basePoints * 0.1f).toInt() // Average memory
            else -> 0
        }

        // Prediction speed bonus (fast decision making)
        val guessingSeconds = guessingPhaseTime / 1000f
        val speedBonus = when {
            guessingSeconds <= 5f -> (basePoints * 0.3f).toInt() // Very fast
            guessingSeconds <= 10f -> (basePoints * 0.2f).toInt() // Fast
            guessingSeconds <= 15f -> (basePoints * 0.1f).toInt() // Moderate
            else -> 0
        }

        // First attempt bonus
        val attemptBonus = if (attemptNumber == 1) {
            (basePoints * 0.25f).toInt()
        } else {
            maxOf(0, (basePoints * 0.25f * (1f - (attemptNumber - 1) * 0.15f)).toInt())
        }

        // Spatial reasoning bonus
        val spatialBonus = (basePoints * 0.2f).toInt()

        val finalScore = ((basePoints * complexityMultiplier).toInt() + physicsBonus + memoryBonus + speedBonus + attemptBonus + spatialBonus)

        Log.d(TAG, "🏆 Pinball score calculation:")
        Log.d(TAG, "  Matrix: ${matrixSize}x${matrixSize}, Deflectors: $deflectorCount")
        Log.d(TAG, "  Base points: $basePoints")
        Log.d(TAG, "  Complexity multiplier: ${complexityMultiplier}x")
        Log.d(TAG, "  Physics bonus: $physicsBonus")
        Log.d(TAG, "  Memory bonus: $memoryBonus (${memorySeconds}s)")
        Log.d(TAG, "  Speed bonus: $speedBonus (${guessingSeconds}s)")
        Log.d(TAG, "  Attempt bonus: $attemptBonus (attempt #$attemptNumber)")
        Log.d(TAG, "  Spatial bonus: $spatialBonus")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, basePoints / 4) // Minimum 25% of base points
    }

    // Simulation functions (same as before but with corrected deflector logic)
    fun simulateBallPathAndGetEnd(): BallPosition? {
        try {
            var currentPos = startPosition
            var currentDir = startDirection

            repeat(50) { step ->
                Log.d(TAG, "   Step $step: at position [${currentPos.row},${currentPos.col}] moving ${currentDir.name}")

                // Check for deflector at CURRENT position
                val deflector = allDeflectors.find { it.row == currentPos.row && it.col == currentPos.col }
                if (deflector != null) {
                    Log.d(TAG, "   🎯 Hit deflector type ${deflector.type} at [${currentPos.row},${currentPos.col}]")

                    val newDir = when (deflector.type) {
                        1 -> { // Slash / deflector
                            when (currentDir.name) {
                                "UP" -> BallDirection(0, 1, "RIGHT")
                                "RIGHT" -> BallDirection(-1, 0, "UP")
                                "DOWN" -> BallDirection(0, -1, "LEFT")
                                "LEFT" -> BallDirection(1, 0, "DOWN")
                                else -> BallDirection(-currentDir.dc, -currentDir.dr, "deflected_slash")
                            }
                        }
                        2 -> { // Backslash \ deflector
                            when (currentDir.name) {
                                "UP" -> BallDirection(0, -1, "LEFT")
                                "LEFT" -> BallDirection(-1, 0, "UP")
                                "DOWN" -> BallDirection(0, 1, "RIGHT")
                                "RIGHT" -> BallDirection(1, 0, "DOWN")
                                else -> BallDirection(currentDir.dc, currentDir.dr, "deflected_backslash")
                            }
                        }
                        else -> currentDir
                    }

                    Log.d(TAG, "   🔄 Direction changed from ${currentDir.name} to ${newDir.name}")
                    currentDir = newDir
                }

                val nextRow = currentPos.row + currentDir.dr
                val nextCol = currentPos.col + currentDir.dc

                if (nextRow < 0 || nextRow >= matrixSize || nextCol < 0 || nextCol >= matrixSize) {
                    Log.d(TAG, "   ✅ Ball exits at [$nextRow,$nextCol] (outside bounds)")
                    return BallPosition(nextRow, nextCol)
                }

                currentPos = BallPosition(nextRow, nextCol)
            }

            Log.d(TAG, "   ⚠️ Max steps reached, stopping at [${currentPos.row},${currentPos.col}]")
            return currentPos
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error simulating ball path", e)
            return null
        }
    }

    fun simulateBallPath() {
        val path = mutableListOf<BallPosition>()
        var currentPos = startPosition
        var currentDir = startDirection
        var hasExited = false

        path.add(currentPos)
        Log.d(TAG, "🎾 Starting ball trajectory visualization from [${currentPos.row},${currentPos.col}] moving ${currentDir.name}")

        for (step in 1..50) {
            if (hasExited) break

            val deflector = allDeflectors.find { it.row == currentPos.row && it.col == currentPos.col }
            if (deflector != null) {
                Log.d(TAG, "   🎯 Hit deflector type ${deflector.type} at [${currentPos.row},${currentPos.col}]")

                val newDir = when (deflector.type) {
                    1 -> { // Slash / deflector
                        when (currentDir.name) {
                            "UP" -> BallDirection(0, 1, "RIGHT")
                            "RIGHT" -> BallDirection(-1, 0, "UP")
                            "DOWN" -> BallDirection(0, -1, "LEFT")
                            "LEFT" -> BallDirection(1, 0, "DOWN")
                            else -> BallDirection(-currentDir.dc, -currentDir.dr, "deflected_slash")
                        }
                    }
                    2 -> { // Backslash \ deflector
                        when (currentDir.name) {
                            "UP" -> BallDirection(0, -1, "LEFT")
                            "LEFT" -> BallDirection(-1, 0, "UP")
                            "DOWN" -> BallDirection(0, 1, "RIGHT")
                            "RIGHT" -> BallDirection(1, 0, "DOWN")
                            else -> BallDirection(currentDir.dc, currentDir.dr, "deflected_backslash")
                        }
                    }
                    else -> currentDir
                }

                Log.d(TAG, "   🔄 Direction changed from ${currentDir.name} to ${newDir.name}")
                currentDir = newDir
            }

            val nextRow = currentPos.row + currentDir.dr
            val nextCol = currentPos.col + currentDir.dc

            if (nextRow < 0 || nextRow >= matrixSize || nextCol < 0 || nextCol >= matrixSize) {
                path.add(BallPosition(nextRow, nextCol))
                Log.d(TAG, "   ✅ Ball exits at [$nextRow,$nextCol] - path complete with ${path.size} positions")
                hasExited = true
                break
            }

            currentPos = BallPosition(nextRow, nextCol)
            path.add(currentPos)
        }

        ballPath = path
        Log.d(TAG, "🎾 Final trajectory: ${path.size} positions - ${path.joinToString(" → ") { "[${it.row},${it.col}]" }}")
    }

    fun handleCellSelection(row: Int, col: Int) {
        if (gameState == PinballGameState.GUESSING) {
            selectedEndPosition = BallPosition(row, col)
            Log.d(TAG, "🎯 User selected end position: [$row,$col] - Auto-submitting answer")

            // Auto-submit answer immediately when selection is made
            attempts++
            val guessingTime = System.currentTimeMillis() - guessingPhaseStartTime

            try {
                Log.d(TAG, "🎯 Simulating ball path to find correct answer...")
                val simulatedEndPosition = simulateBallPathAndGetEnd()

                if (simulatedEndPosition != null) {
                    correctEndPosition = simulatedEndPosition
                    isCorrect = selectedEndPosition?.row == simulatedEndPosition.row &&
                            selectedEndPosition?.col == simulatedEndPosition.col

                    if (isCorrect) {
                        // Calculate and award score
                        val score = calculatePinballScore(
                            isCorrect = true,
                            memoryPhaseTime = guessingPhaseStartTime - memoryPhaseStartTime,
                            guessingPhaseTime = guessingTime,
                            attemptNumber = attempts,
                            matrixSize = matrixSize,
                            deflectorCount = allDeflectors.size,
                            difficulty = difficulty
                        )
                        totalScore = score
                        Log.d(TAG, "✅ Correct prediction! Score: $totalScore")
                    } else {
                        Log.d(TAG, "❌ Wrong prediction.")
                    }

                    Log.d(TAG, "📝 Answer submitted:")
                    Log.d(TAG, "   Selected: [${selectedEndPosition?.row},${selectedEndPosition?.col}]")
                    Log.d(TAG, "   Correct: [${simulatedEndPosition.row},${simulatedEndPosition.col}]")
                    Log.d(TAG, "   Result: $isCorrect")
                } else {
                    Log.e(TAG, "❌ Could not simulate ball path")
                    isCorrect = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in answer submission", e)
                isCorrect = false
            }

            simulateBallPath()
            gameState = PinballGameState.SHOWING_RESULT
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .padding(16.dp)
        ) {
            // Enhanced header with live timer and score
            EnhancedPinballHeader(
                difficulty = difficulty,
                timer = displayTimer, // Use live countdown
                hearts = hearts,
                level = level,
                totalScore = totalScore,
                attempts = attempts,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Game state indicator
            when (gameState) {
                PinballGameState.MEMORIZING -> {
                    Text(
                        text = "Memorize deflector positions: ${timeLeft}s",
                        color = Color.Yellow,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                PinballGameState.GUESSING -> {
                    Text(
                        text = "Select where the ball will end up",
                        color = Color.Cyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                PinballGameState.SHOWING_RESULT -> {
                    Text(
                        text = if (isCorrect) "Correct! 🎉" else "Incorrect ❌",
                        color = if (isCorrect) Color.Green else Color.Red,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Game board - what to show depends on game state
            PinballGameBoard(
                matrixSize = matrixSize,
                startPosition = startPosition,
                startDirection = startDirection,
                deflectors = allDeflectors,
                selectedEndPosition = selectedEndPosition,
                correctEndPosition = correctEndPosition,
                ballPath = ballPath,
                trajectoryProgress = trajectoryProgress,
                gameState = gameState,
                onCellClick = ::handleCellSelection,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Removed all buttons since the game now auto-proceeds
        }

        // Universal Feedback Overlay - now in BoxScope
        EnhancedUniversalFeedback(feedbackManager)
    }
}

// Enhanced header with score display
@Composable
fun EnhancedPinballHeader(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    totalScore: Int,
    attempts: Int,
    onBack: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(48.dp)
                    .background(Color(0xFF444444), CircleShape)
            ) {
                Text("←", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Pinball Deflector",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = level,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timer,
                    color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true) {
                        Color.Red // Red when ≤30 seconds
                    } else {
                        Color.White
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    repeat(hearts) {
                        Text("❤️", fontSize = 16.sp)
                    }
                }
            }
        }

        // Score and progress display
        if (totalScore > 0 || attempts > 0) {
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
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (totalScore > 0) {
                        Text(
                            text = "${stringResource(R.string.score_label)}: $totalScore",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = difficulty.uppercase(),
                        color = Color.Yellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (attempts > 0) {
                        Text(
                            text = "Attempt: $attempts",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PinballGameBoard(
    matrixSize: Int,
    startPosition: BallPosition,
    startDirection: BallDirection,
    deflectors: List<DeflectorData>,
    selectedEndPosition: BallPosition?,
    correctEndPosition: BallPosition?,
    ballPath: List<BallPosition>,
    trajectoryProgress: Float,
    gameState: PinballGameState,
    onCellClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val boardSize = 350.dp

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Main game board canvas
        Canvas(
            modifier = Modifier
                .size(boardSize)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2D2D2D))
                .border(2.dp, Color(0xFF555555), RoundedCornerShape(12.dp))
        ) {
            val cellSize = size.width / matrixSize

            // Always draw grid
            drawGrid(matrixSize, cellSize)

            // What to show based on game state
            when (gameState) {
                PinballGameState.MEMORIZING -> {
                    // PHASE 1: Show ONLY deflectors - user memorizes positions
                    Log.d("GameBoard", "🧠 MEMORIZING: Showing deflectors only")
                    deflectors.forEach { deflector ->
                        drawDeflector(deflector, cellSize)
                    }
                }

                PinballGameState.GUESSING -> {
                    // PHASE 2: Show ONLY start position - user guesses end position
                    Log.d("GameBoard", "🎯 GUESSING: Showing start position only")

                    // Show selected position if user has made a selection (inside grid only)
                    selectedEndPosition?.let { pos ->
                        if (pos.row >= 0 && pos.row < matrixSize && pos.col >= 0 && pos.col < matrixSize) {
                            drawSelectedEndPosition(pos, cellSize)
                        }
                    }
                }

                PinballGameState.SHOWING_RESULT -> {
                    // PHASE 3: Show EVERYTHING - deflectors, ball path, result
                    Log.d("GameBoard", "📊 SHOWING_RESULT: Showing all elements")

                    // Show deflectors again
                    deflectors.forEach { deflector ->
                        drawDeflector(deflector, cellSize)
                    }

                    // Show selected position (inside grid only)
                    selectedEndPosition?.let { pos ->
                        if (pos.row >= 0 && pos.row < matrixSize && pos.col >= 0 && pos.col < matrixSize) {
                            drawSelectedEndPosition(pos, cellSize)
                        }
                    }

                    // Show animated ball path
                    if (ballPath.isNotEmpty()) {
                        drawAnimatedBallPath(ballPath, cellSize, matrixSize, trajectoryProgress)
                    }
                }
            }
        }

        // Edge positions overlay - behavior depends on game state
        when (gameState) {
            PinballGameState.MEMORIZING -> {
                // Don't show edge positions during memorization
                Log.d("EdgeOverlay", "🧠 MEMORIZING: No edge overlay")
            }

            PinballGameState.GUESSING -> {
                // Show edge positions for user selection
                Log.d("EdgeOverlay", "🎯 GUESSING: Showing selectable edge positions")
                EdgePositionsOverlay(
                    matrixSize = matrixSize,
                    startPosition = startPosition,
                    startDirection = startDirection,
                    selectedEndPosition = selectedEndPosition,
                    correctEndPosition = null, // Don't show correct answer yet
                    gameState = gameState,
                    onCellClick = onCellClick,
                    boardSize = boardSize
                )
            }

            PinballGameState.SHOWING_RESULT -> {
                // Show edge positions with result feedback
                Log.d("EdgeOverlay", "📊 SHOWING_RESULT: Showing edge positions with feedback")
                EdgePositionsOverlay(
                    matrixSize = matrixSize,
                    startPosition = startPosition,
                    startDirection = startDirection,
                    selectedEndPosition = selectedEndPosition,
                    correctEndPosition = correctEndPosition,
                    gameState = gameState,
                    onCellClick = onCellClick, // Disabled in this state
                    boardSize = boardSize
                )
            }
        }
    }
}

// All drawing functions remain the same...
fun DrawScope.drawAnimatedBallPath(
    path: List<BallPosition>,
    cellSize: Float,
    matrixSize: Int,
    progress: Float = 1f
) {
    if (path.isEmpty()) return

    val pathColor = Color(0xFFFF5722) // Orange path
    val dotColor = Color(0xFF00BCD4)  // Cyan dots
    val strokeWidth = 3.dp.toPx()
    val dotRadius = cellSize * 0.08f

    // Calculate how many positions to show based on progress
    val positionsToShow = (path.size * progress).toInt().coerceAtLeast(1)
    val visiblePath = path.take(positionsToShow)

    for (i in 0 until visiblePath.size) {
        val current = visiblePath[i]

        // Only draw positions that are within or just outside the visible grid
        val shouldDraw = (current.row >= -1 && current.row <= matrixSize &&
                current.col >= -1 && current.col <= matrixSize)

        if (!shouldDraw) continue

        val currentX = (current.col + 0.5f) * cellSize
        val currentY = (current.row + 0.5f) * cellSize

        // Draw line to next position (if exists and both positions are drawable)
        if (i < visiblePath.size - 1) {
            val next = visiblePath[i + 1]
            val nextShouldDraw = (next.row >= -1 && next.row <= matrixSize &&
                    next.col >= -1 && next.col <= matrixSize)

            if (nextShouldDraw) {
                val nextX = (next.col + 0.5f) * cellSize
                val nextY = (next.row + 0.5f) * cellSize

                drawLine(
                    color = pathColor,
                    start = Offset(currentX, currentY),
                    end = Offset(nextX, nextY),
                    strokeWidth = strokeWidth
                )
            }
        }

        // Draw dot at current position with fade-in effect
        val dotAlpha = if (i == visiblePath.size - 1) {
            // Animate the leading dot
            ((progress * path.size) - i).coerceIn(0f, 1f)
        } else 1f

        val adjustedX = when {
            current.col < 0 -> 0f
            current.col >= matrixSize -> size.width
            else -> currentX
        }

        val adjustedY = when {
            current.row < 0 -> 0f
            current.row >= matrixSize -> size.height
            else -> currentY
        }

        if (adjustedX >= 0 && adjustedX <= size.width && adjustedY >= 0 && adjustedY <= size.height) {
            drawCircle(
                color = dotColor.copy(alpha = dotAlpha),
                radius = dotRadius,
                center = Offset(adjustedX, adjustedY)
            )

            drawCircle(
                color = Color.White.copy(alpha = dotAlpha),
                radius = dotRadius,
                center = Offset(adjustedX, adjustedY),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }

    // Draw final position dot when animation is complete
    if (progress >= 1f && visiblePath.isNotEmpty()) {
        val finalPos = visiblePath.last()
        val finalX = (finalPos.col + 0.5f) * cellSize
        val finalY = (finalPos.row + 0.5f) * cellSize

        val adjustedFinalX = when {
            finalPos.col < 0 -> 0f
            finalPos.col >= matrixSize -> size.width
            else -> finalX
        }

        val adjustedFinalY = when {
            finalPos.row < 0 -> 0f
            finalPos.row >= matrixSize -> size.height
            else -> finalY
        }

        if (adjustedFinalX >= 0 && adjustedFinalX <= size.width &&
            adjustedFinalY >= 0 && adjustedFinalY <= size.height) {
            drawCircle(
                color = Color(0xFFFF5722),
                radius = cellSize * 0.15f,
                center = Offset(adjustedFinalX, adjustedFinalY)
            )

            drawCircle(
                color = Color.White,
                radius = cellSize * 0.15f,
                center = Offset(adjustedFinalX, adjustedFinalY),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

fun getEdgePositionFromExit(endPosition: BallPosition, matrixSize: Int): Triple<Int, Int, String>? {
    return when {
        endPosition.row < 0 -> Triple(-1, endPosition.col, "top")        // Exited through top
        endPosition.row >= matrixSize -> Triple(matrixSize, endPosition.col, "bottom")  // Exited through bottom
        endPosition.col < 0 -> Triple(endPosition.row, -1, "left")       // Exited through left
        endPosition.col >= matrixSize -> Triple(endPosition.row, matrixSize, "right")  // Exited through right
        else -> null // Position is inside grid (shouldn't happen for end position)
    }
}

@Composable
fun EdgePositionsOverlay(
    matrixSize: Int,
    startPosition: BallPosition,
    startDirection: BallDirection,
    selectedEndPosition: BallPosition?,
    correctEndPosition: BallPosition?,
    gameState: PinballGameState,
    onCellClick: (Int, Int) -> Unit,
    boardSize: androidx.compose.ui.unit.Dp
) {
    val cellSize = boardSize / matrixSize
    val topBottomOffset = 40.dp
    val leftRightOffset = 20.dp

    // Create edge positions
    val edgePositions = mutableListOf<Triple<Int, Int, String>>()

    // Top edge positions
    for (col in 0 until matrixSize) {
        edgePositions.add(Triple(-1, col, "top"))
    }
    // Bottom edge positions
    for (col in 0 until matrixSize) {
        edgePositions.add(Triple(matrixSize, col, "bottom"))
    }
    // Left edge positions
    for (row in 0 until matrixSize) {
        edgePositions.add(Triple(row, -1, "left"))
    }
    // Right edge positions
    for (row in 0 until matrixSize) {
        edgePositions.add(Triple(row, matrixSize, "right"))
    }

    // Convert start position to edge position based on initial direction
    val startEdgePosition = when (startDirection.name) {
        "RIGHT" -> Triple(startPosition.row, -1, "left")
        "LEFT" -> Triple(startPosition.row, matrixSize, "right")
        "DOWN" -> Triple(-1, startPosition.col, "top")
        "UP" -> Triple(matrixSize, startPosition.col, "bottom")
        else -> null
    }

    // Convert correct end position to edge position
    val correctEdgePosition = correctEndPosition?.let {
        getEdgePositionFromExit(it, matrixSize)
    }

    // Convert selected end position to edge position
    val selectedEdgePosition = selectedEndPosition?.let {
        getEdgePositionFromExit(it, matrixSize)
    }

    // Combine all positions
    val allPositions = if (startEdgePosition != null) {
        edgePositions + startEdgePosition
    } else {
        edgePositions
    }.distinct()

    allPositions.forEach { (row, col, edge) ->
        val (offsetX, offsetY) = when (edge) {
            "top" -> Pair(
                cellSize * col + cellSize/2 - boardSize/2,
                -boardSize/2 - topBottomOffset
            )
            "bottom" -> Pair(
                cellSize * col + cellSize/2 - boardSize/2,
                boardSize/2 + topBottomOffset
            )
            "left" -> Pair(
                -boardSize/2 - leftRightOffset,
                cellSize * row + cellSize/2 - boardSize/2
            )
            "right" -> Pair(
                boardSize/2 + leftRightOffset,
                cellSize * row + cellSize/2 - boardSize/2
            )
            else -> Pair(0.dp, 0.dp)
        }

        val isSelected = selectedEdgePosition?.let {
            it.first == row && it.second == col && it.third == edge
        } ?: false

        val isCorrectAnswer = correctEdgePosition?.let {
            it.first == row && it.second == col && it.third == edge
        } ?: false

        val isStartPosition = startEdgePosition?.let {
            it.first == row && it.second == col && it.third == edge
        } ?: false

        // Determine what to show based on game state
        val shouldShowStartPosition = gameState == PinballGameState.GUESSING || gameState == PinballGameState.SHOWING_RESULT
        val shouldShowCorrectAnswer = gameState == PinballGameState.SHOWING_RESULT
        val isClickEnabled = gameState == PinballGameState.GUESSING && !isStartPosition

        Box(
            modifier = Modifier
                .offset(x = offsetX, y = offsetY)
                .size(cellSize * 0.4f)
                .clip(CircleShape)
                .background(
                    when {
                        isStartPosition && shouldShowStartPosition -> Color(0xFF00BCD4)  // Cyan for start
                        isSelected && gameState == PinballGameState.SHOWING_RESULT && !isCorrectAnswer -> Color(0xFFFF5722) // Red for wrong
                        isSelected -> Color(0xFF4CAF50)       // Green for selected
                        isCorrectAnswer && shouldShowCorrectAnswer -> Color(0xFF4CAF50) // Green for correct
                        gameState == PinballGameState.GUESSING -> Color(0xFF666666)     // Gray for selectable
                        else -> Color.Transparent             // Hidden during memorization
                    }
                )
                .border(
                    2.dp,
                    when {
                        isStartPosition && shouldShowStartPosition -> Color.White
                        (isSelected || isCorrectAnswer) && gameState == PinballGameState.SHOWING_RESULT -> Color.White
                        isSelected && gameState == PinballGameState.GUESSING -> Color.White
                        gameState == PinballGameState.GUESSING -> Color(0xFF888888)
                        else -> Color.Transparent
                    },
                    CircleShape
                )
                .clickable(enabled = isClickEnabled) {
                    onCellClick(row, col)
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                isStartPosition && shouldShowStartPosition -> {
                    Text("●", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                isSelected && gameState == PinballGameState.SHOWING_RESULT && !isCorrectAnswer -> {
                    Text("✗", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                }
                isCorrectAnswer && shouldShowCorrectAnswer -> {
                    Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                }
                isSelected && gameState == PinballGameState.GUESSING -> {
                    Text("?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                }
            }
        }
    }
}

fun DrawScope.drawGrid(matrixSize: Int, cellSize: Float) {
    val gridColor = Color(0xFF444444)

    // Draw vertical lines
    for (i in 0..matrixSize) {
        val x = i * cellSize
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }

    // Draw horizontal lines
    for (i in 0..matrixSize) {
        val y = i * cellSize
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx()
        )
    }
}

fun DrawScope.drawDeflector(deflector: DeflectorData, cellSize: Float) {
    val centerX = (deflector.col + 0.5f) * cellSize
    val centerY = (deflector.row + 0.5f) * cellSize
    val strokeWidth = 4.dp.toPx()
    val lineLength = cellSize * 0.6f

    val color = Color(0xFFFFD700) // Gold color for deflectors

    when (deflector.type) {
        1 -> { // Slash /
            drawLine(
                color = color,
                start = Offset(centerX - lineLength/2, centerY + lineLength/2),
                end = Offset(centerX + lineLength/2, centerY - lineLength/2),
                strokeWidth = strokeWidth
            )
        }
        2 -> { // Backslash \
            drawLine(
                color = color,
                start = Offset(centerX - lineLength/2, centerY - lineLength/2),
                end = Offset(centerX + lineLength/2, centerY + lineLength/2),
                strokeWidth = strokeWidth
            )
        }
    }
}

fun DrawScope.drawSelectedEndPosition(position: BallPosition, cellSize: Float) {
    val centerX = (position.col + 0.5f) * cellSize
    val centerY = (position.row + 0.5f) * cellSize
    val radius = cellSize * 0.25f

    // Draw target indicator
    drawCircle(
        color = Color(0xFF4CAF50),
        radius = radius,
        center = Offset(centerX, centerY)
    )

    // Draw X mark
    val markSize = radius * 0.5f
    drawLine(
        color = Color.White,
        start = Offset(centerX - markSize, centerY - markSize),
        end = Offset(centerX + markSize, centerY + markSize),
        strokeWidth = 3.dp.toPx()
    )
    drawLine(
        color = Color.White,
        start = Offset(centerX + markSize, centerY - markSize),
        end = Offset(centerX - markSize, centerY + markSize),
        strokeWidth = 3.dp.toPx()
    )
}
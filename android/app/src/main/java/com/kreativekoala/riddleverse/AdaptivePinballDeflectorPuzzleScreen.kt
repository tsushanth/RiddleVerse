// AdaptivePinballDeflectorPuzzleScreen.kt
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import com.kreativekoala.riddleverse.ui.theme.RvMintEdge
import com.kreativekoala.riddleverse.ui.theme.RvCoralEdge
import com.kreativekoala.riddleverse.ui.theme.RvSkyEdge
import com.kreativekoala.riddleverse.ui.theme.RvSunEdge
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

@Composable
fun AdaptivePinballDeflectorPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String,
    hearts: Int = 3,
    level: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptivePinball"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("pinballDeflector"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // Generate puzzle data based on current difficulty
    val puzzleData = remember(currentDifficultyLevel) {
        generateAdaptivePinballPuzzle(currentDifficultyLevel)
    }

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var memoryPhaseStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var guessingPhaseStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    var correctEndPosition by remember { mutableStateOf<BallPosition?>(null) }
    var trajectoryProgress by remember { mutableStateOf(0f) }

    // Game state variables
    var gameState by remember { mutableStateOf(PinballGameState.MEMORIZING) }
    var selectedEndPosition by remember { mutableStateOf<BallPosition?>(null) }
    var timeLeft by remember { mutableStateOf(puzzleData.memoryTime / 1000) }
    var ballPath by remember { mutableStateOf<List<BallPosition>>(emptyList()) }
    var isCorrect by remember { mutableStateOf(false) }

    // Timer tracking
    val totalTimeSeconds = currentDifficultyLevel.timeLimit
    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(formatTime(totalTimeSeconds)) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            val newPuzzle = generateAdaptivePinballPuzzle(currentDifficultyLevel)
            gameState = PinballGameState.MEMORIZING
            selectedEndPosition = null
            ballPath = emptyList()
            isCorrect = false
            timeLeft = newPuzzle.memoryTime / 1000
            currentHearts = currentDifficultyLevel.livesAllowed
            timeRemaining = currentDifficultyLevel.timeLimit
            displayTimer = formatTime(currentDifficultyLevel.timeLimit)
            gameStartTime = System.currentTimeMillis()
        }
    }

    // Enhanced score calculation for adaptive difficulty
    fun calculateAdaptivePinballScore(
        isCorrect: Boolean,
        memoryPhaseTime: Long,
        guessingPhaseTime: Long,
        attemptNumber: Int,
        matrixSize: Int,
        deflectorCount: Int,
        difficulty: DifficultyManager.DifficultyLevel,
        currentStreak: Int
    ): Int {
        if (!isCorrect) return 0

        val basePoints = difficulty.basePoints

        // Complexity multiplier based on matrix size and deflector count
        val complexityMultiplier = 1f + (matrixSize - 3) * 0.2f + (deflectorCount - 2) * 0.15f

        // Memory efficiency bonus
        val memorySeconds = memoryPhaseTime / 1000f
        val expectedMemoryTime = puzzleData.memoryTime / 1000f
        val memoryBonus = when {
            memorySeconds <= expectedMemoryTime * 0.5f -> (basePoints * 0.4f).toInt()
            memorySeconds <= expectedMemoryTime * 0.7f -> (basePoints * 0.2f).toInt()
            memorySeconds <= expectedMemoryTime * 0.9f -> (basePoints * 0.1f).toInt()
            else -> 0
        }

        // Prediction speed bonus
        val guessingSeconds = guessingPhaseTime / 1000f
        val speedBonus = when {
            guessingSeconds <= 5f -> (basePoints * 0.3f).toInt()
            guessingSeconds <= 10f -> (basePoints * 0.2f).toInt()
            guessingSeconds <= 15f -> (basePoints * 0.1f).toInt()
            else -> 0
        }

        // Streak bonus
        val streakMultiplier = 1f + (currentStreak * 0.1f)

        // Physics understanding bonus
        val physicsBonus = (basePoints * 0.3f * difficulty.complexityMultiplier).toInt()

        // First attempt bonus
        val attemptBonus = if (attemptNumber == 1) {
            (basePoints * 0.25f).toInt()
        } else {
            maxOf(0, (basePoints * 0.25f * (1f - (attemptNumber - 1) * 0.15f)).toInt())
        }

        val finalScore = ((basePoints * complexityMultiplier * streakMultiplier).toInt() +
                physicsBonus + memoryBonus + speedBonus + attemptBonus)

        Log.d(TAG, "🏆 Adaptive pinball score:")
        Log.d(TAG, "  Difficulty: ${difficulty.name}")
        Log.d(TAG, "  Matrix: ${matrixSize}x${matrixSize}, Deflectors: $deflectorCount")
        Log.d(TAG, "  Base points: $basePoints")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, basePoints / 4)
    }

    // Record performance for adaptive difficulty
    fun recordAdaptivePerformance(
        difficultyManager: DifficultyManager,
        isCorrect: Boolean,
        timeSpent: Long,
        streak: Int,
        livesRemaining: Int,
        difficulty: DifficultyManager.DifficultyLevel,
        puzzleComplexity: Int,
        onAdaptation: (DifficultyManager.AdaptiveConfig) -> Unit
    ) {
        val performance = DifficultyManager.PlayerPerformance(
            accuracy = if (isCorrect) 1f else 0f,
            averageTime = timeSpent / 1000f,
            streakLength = streak,
            livesRemaining = livesRemaining,
            gameScore = totalScore,
            difficulty = difficulty.name,
            puzzleType = "pinballDeflector"
        )

        val adaptiveConfig = difficultyManager.recordPerformance(performance)
        onAdaptation(adaptiveConfig)
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, gameState) {
        if (timeRemaining > 0 && gameState != PinballGameState.SHOWING_RESULT) {
            delay(1000L)
            timeRemaining--
            displayTimer = formatTime(timeRemaining)
        } else if (timeRemaining == 0 && gameState != PinballGameState.SHOWING_RESULT) {
            // Time's up - record poor performance
            recordAdaptivePerformance(
                difficultyManager = difficultyManager,
                isCorrect = false,
                timeSpent = totalTimeSeconds * 1000L,
                streak = 0,
                livesRemaining = 0,
                difficulty = currentDifficultyLevel,
                puzzleComplexity = puzzleData.matrixSize
            ) { config ->
                adaptationInfo = config
                if (config.confidenceScore > 0.5f) {
                    currentDifficultyLevel = config.level
                    showAdaptationNotification = SHOW_ADAPTATION_NOTICES
                }
            }

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

            delay(2000L)

            gamesPlayedThisSession++

            // Record performance for adaptation
            val timeSpent = totalTimeSeconds - timeRemaining
            recordAdaptivePerformance(
                difficultyManager = difficultyManager,
                isCorrect = isCorrect,
                timeSpent = timeSpent * 1000L,
                streak = if (isCorrect) currentStreak + 1 else 0,
                livesRemaining = currentHearts,
                difficulty = currentDifficultyLevel,
                puzzleComplexity = puzzleData.matrixSize
            ) { config ->
                adaptationInfo = config
                if (config.confidenceScore > 0.5f) {
                    currentDifficultyLevel = config.level
                    showAdaptationNotification = SHOW_ADAPTATION_NOTICES
                }
            }

            if (isCorrect) {
                currentStreak++
            } else {
                currentStreak = 0
            }

            // Use unified feedback system
            feedbackManager.showFeedback(
                puzzleType = "pinballDeflector",
                isCorrect = isCorrect,
                userAnswer = "Position [${selectedEndPosition?.row},${selectedEndPosition?.col}]",
                correctAnswer = "Position [${correctEndPosition?.row},${correctEndPosition?.col}]",
                timeSpent = timeSpent * 1000L,
                difficulty = currentDifficultyLevel.name,
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

    // Simulation functions
    fun simulateBallPathAndGetEnd(): BallPosition? {
        try {
            var currentPos = puzzleData.startPosition
            var currentDir = puzzleData.startDirection

            repeat(50) { step ->
                val deflector = puzzleData.deflectors.find {
                    it.row == currentPos.row && it.col == currentPos.col
                }
                if (deflector != null) {
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
                    currentDir = newDir
                }

                val nextRow = currentPos.row + currentDir.dr
                val nextCol = currentPos.col + currentDir.dc

                if (nextRow < 0 || nextRow >= puzzleData.matrixSize ||
                    nextCol < 0 || nextCol >= puzzleData.matrixSize) {
                    return BallPosition(nextRow, nextCol)
                }

                currentPos = BallPosition(nextRow, nextCol)
            }

            return currentPos
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error simulating ball path", e)
            return null
        }
    }

    fun simulateBallPath() {
        val path = mutableListOf<BallPosition>()
        var currentPos = puzzleData.startPosition
        var currentDir = puzzleData.startDirection

        path.add(currentPos)

        for (step in 1..50) {
            val deflector = puzzleData.deflectors.find {
                it.row == currentPos.row && it.col == currentPos.col
            }
            if (deflector != null) {
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
                currentDir = newDir
            }

            val nextRow = currentPos.row + currentDir.dr
            val nextCol = currentPos.col + currentDir.dc

            if (nextRow < 0 || nextRow >= puzzleData.matrixSize ||
                nextCol < 0 || nextCol >= puzzleData.matrixSize) {
                path.add(BallPosition(nextRow, nextCol))
                break
            }

            currentPos = BallPosition(nextRow, nextCol)
            path.add(currentPos)
        }

        ballPath = path
    }

    fun handleCellSelection(row: Int, col: Int) {
        if (gameState == PinballGameState.GUESSING) {
            selectedEndPosition = BallPosition(row, col)
            attempts++
            val guessingTime = System.currentTimeMillis() - guessingPhaseStartTime

            try {
                val simulatedEndPosition = simulateBallPathAndGetEnd()

                if (simulatedEndPosition != null) {
                    correctEndPosition = simulatedEndPosition
                    isCorrect = selectedEndPosition?.row == simulatedEndPosition.row &&
                            selectedEndPosition?.col == simulatedEndPosition.col

                    if (isCorrect) {
                        val score = calculateAdaptivePinballScore(
                            isCorrect = true,
                            memoryPhaseTime = guessingPhaseStartTime - memoryPhaseStartTime,
                            guessingPhaseTime = guessingTime,
                            attemptNumber = attempts,
                            matrixSize = puzzleData.matrixSize,
                            deflectorCount = puzzleData.deflectors.size,
                            difficulty = currentDifficultyLevel,
                            currentStreak = currentStreak
                        )
                        totalScore += score
                        Log.d(TAG, "✅ Correct prediction! Score: +$score, Total: $totalScore")
                    } else {
                        currentHearts = maxOf(0, currentHearts - 1)
                        Log.d(TAG, "❌ Wrong prediction. Hearts: $currentHearts")
                    }
                } else {
                    isCorrect = false
                    currentHearts = maxOf(0, currentHearts - 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in answer submission", e)
                isCorrect = false
                currentHearts = maxOf(0, currentHearts - 1)
            }

            simulateBallPath()
            gameState = PinballGameState.SHOWING_RESULT
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(RvCanvas)
        ) {
            val compact = maxHeight < 600.dp
            val pad = if (compact) 8.dp else 16.dp
            val wide = maxWidth > maxHeight && maxWidth >= 480.dp

            val header: @Composable () -> Unit = {
                AdaptivePinballTopBar(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    lives = currentHearts,
                    currentDifficulty = currentDifficultyLevel,
                    totalScore = totalScore,
                    attempts = attempts,
                    onBack = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val notice: @Composable () -> Unit = {
                AnimatedVisibility(
                    visible = showAdaptationNotification,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4FC3F7).copy(alpha = 0.9f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = "Difficulty adjusted",
                                tint = RvInk,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Physics Challenge Adapted!",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RvInk
                                )
                                Text(
                                    text = adaptationInfo?.adjustmentReason ?: "",
                                    fontSize = 10.sp,
                                    color = RvInkSoft.copy(alpha = 0.9f)
                                )
                            }
                            IconButton(
                                onClick = { showAdaptationNotification = false },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = RvInk,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            val status: @Composable () -> Unit = {
                when (gameState) {
                    PinballGameState.MEMORIZING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Memorize deflector positions: ${timeLeft}s",
                                color = RvInk,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentDifficultyLevel.name} • ${puzzleData.matrixSize}x${puzzleData.matrixSize} • ${puzzleData.deflectors.size} deflectors",
                                color = RvSkyEdge,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    PinballGameState.GUESSING -> {
                        Text(
                            text = "Select where the ball will end up",
                            color = RvInk,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    PinballGameState.SHOWING_RESULT -> {
                        Text(
                            text = if (isCorrect) "Correct! 🎉" else "Incorrect ❌",
                            color = if (isCorrect) RvMintEdge else RvCoralEdge,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            val board: @Composable (Modifier) -> Unit = { m ->
                PinballGameBoard(
                    matrixSize = puzzleData.matrixSize,
                    startPosition = puzzleData.startPosition,
                    startDirection = puzzleData.startDirection,
                    deflectors = puzzleData.deflectors,
                    selectedEndPosition = selectedEndPosition,
                    correctEndPosition = correctEndPosition,
                    ballPath = ballPath,
                    trajectoryProgress = trajectoryProgress,
                    gameState = gameState,
                    onCellClick = ::handleCellSelection,
                    modifier = m
                )
            }

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    board(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        modifier = Modifier.weight(0.7f).widthIn(max = 360.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                    ) {
                        header()
                        notice()
                        status()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    header()
                    notice()
                    Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
                    status()
                    Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
                    board(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
fun AdaptivePinballTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    totalScore: Int,
    attempts: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = Modifier.statusBarsPadding()) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF444444), CircleShape)
            ) {
                Text("←", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Adaptive Pinball Deflector",
                    color = RvInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Level ${level.level}",
                    color = RvInkSoft,
                    fontSize = 14.sp
                )
                Text(
                    text = currentDifficulty.name,
                    color = RvSkyEdge,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timer,
                    color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true) {
                        RvCoralEdge
                    } else {
                        RvInk
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    repeat(currentDifficulty.livesAllowed) { index ->
                        Text(
                            text = if (index < lives) "❤️" else "🤍",
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Score and progress display
        if (totalScore > 0 || attempts > 0) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = RvSurface
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
                            color = RvInk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = currentDifficulty.name.uppercase(),
                        color = RvSunEdge,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (attempts > 0) {
                        Text(
                            text = "Attempt: $attempts",
                            color = RvInkSoft.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// Helper function to generate adaptive puzzle data
fun generateAdaptivePinballPuzzle(difficulty: DifficultyManager.DifficultyLevel): AdaptivePinballPuzzleData {
    val matrixSize = when (difficulty.index) {
        0 -> 4  // Beginner
        1 -> 5  // Easy
        2 -> 6  // Medium
        3 -> 7  // Hard
        4 -> 8  // Expert
        else -> 5
    }

    val deflectorCount = when (difficulty.index) {
        0 -> 2  // Beginner
        1 -> 3  // Easy
        2 -> 4  // Medium
        3 -> 5  // Hard
        4 -> 6  // Expert
        else -> 3
    }

    val memoryTime = when (difficulty.index) {
        0 -> 8000   // Beginner - 8 seconds
        1 -> 6000   // Easy - 6 seconds
        2 -> 5000   // Medium - 5 seconds
        3 -> 4000   // Hard - 4 seconds
        4 -> 3000   // Expert - 3 seconds
        else -> 5000
    }

    // Generate deflectors randomly placed
    val deflectors = mutableListOf<DeflectorData>()
    val usedPositions = mutableSetOf<Pair<Int, Int>>()

    repeat(deflectorCount) {
        var row: Int
        var col: Int
        do {
            row = kotlin.random.Random.nextInt(matrixSize)
            col = kotlin.random.Random.nextInt(matrixSize)
        } while (usedPositions.contains(Pair(row, col)))

        usedPositions.add(Pair(row, col))
        deflectors.add(DeflectorData(
            row = row,
            col = col,
            type = if (kotlin.random.Random.nextBoolean()) 1 else 2 // Slash or backslash
        ))
    }

    // Generate random start position and direction
    val edge = kotlin.random.Random.nextInt(4)
    val (startPosition, startDirection) = when (edge) {
        0 -> { // Top edge
            val col = kotlin.random.Random.nextInt(matrixSize)
            Pair(BallPosition(0, col), BallDirection(1, 0, "DOWN"))
        }
        1 -> { // Right edge
            val row = kotlin.random.Random.nextInt(matrixSize)
            Pair(BallPosition(row, matrixSize - 1), BallDirection(0, -1, "LEFT"))
        }
        2 -> { // Bottom edge
            val col = kotlin.random.Random.nextInt(matrixSize)
            Pair(BallPosition(matrixSize - 1, col), BallDirection(-1, 0, "UP"))
        }
        else -> { // Left edge
            val row = kotlin.random.Random.nextInt(matrixSize)
            Pair(BallPosition(row, 0), BallDirection(0, 1, "RIGHT"))
        }
    }

    return AdaptivePinballPuzzleData(
        matrixSize = matrixSize,
        startPosition = startPosition,
        startDirection = startDirection,
        deflectors = deflectors,
        memoryTime = memoryTime
    )
}

data class AdaptivePinballPuzzleData(
    val matrixSize: Int,
    val startPosition: BallPosition,
    val startDirection: BallDirection,
    val deflectors: List<DeflectorData>,
    val memoryTime: Int
)
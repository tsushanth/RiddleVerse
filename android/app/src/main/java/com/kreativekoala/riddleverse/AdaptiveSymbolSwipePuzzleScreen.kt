// AdaptiveSymbolSwipePuzzleScreen.kt
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import kotlin.math.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSymbolSwipePuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit,
    isFirstPuzzle: Boolean = true
) {
    val TAG = "AdaptiveSymbolSwipe"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("symbolSwipe"))
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
                    puzzleType = "symbolSwipe",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate adaptive difficulty settings
    val adaptiveConfig = remember(currentDifficultyLevel) {
        generateAdaptiveSymbolSwipeConfig(currentDifficultyLevel)
    }

    val totalTimeInSeconds = adaptiveConfig.totalTime
    val symbolCount = adaptiveConfig.symbolCount

    // Game state
    var timeLeft by remember { mutableStateOf(totalTimeInSeconds) }
    var isPaused by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var gameCompleted by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(!isFirstPuzzle) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var feedbackColor by remember { mutableStateOf(Color.Green) }

    // Symbol configuration
    var leftSymbol by remember { mutableStateOf<GameSymbol?>(null) }
    var rightSymbol by remember { mutableStateOf<GameSymbol?>(null) }
    var symbolSequence by remember { mutableStateOf(emptyList<SymbolItem>()) }
    var currentSymbolIndex by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    var totalScore by remember { mutableStateOf(0) }
    var multiplier by remember { mutableStateOf(1) }
    var consecutiveCorrect by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // ✅ NEW: Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAnswers by remember { mutableStateOf(0) }

    // Animation states
    var currentSymbolOffset by remember { mutableStateOf(0f) }
    var symbolScale by remember { mutableStateOf(1f) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Parse level to get current puzzle number
    val currentPuzzleNumber = remember(level) {
        try {
            level.split("/")[0].toInt()
        } catch (e: Exception) {
            1
        }
    }

    val totalPuzzles = remember(level) {
        try {
            level.split("/")[1].toInt()
        } catch (e: Exception) {
            5
        }
    }

    // Generate game symbols and sequence
    fun generateGameSymbols() {
        val allSymbols = listOf(
            GameSymbol.Star, GameSymbol.Circle, GameSymbol.Triangle, GameSymbol.Diamond,
            GameSymbol.Square, GameSymbol.Heart, GameSymbol.Hexagon, GameSymbol.Cross
        )

        val selectedSymbols = allSymbols.shuffled().take(2)
        leftSymbol = selectedSymbols[0]
        rightSymbol = selectedSymbols[1]

        val sequence = (1..symbolCount).map { index ->
            SymbolItem(
                symbol = if (Random.nextBoolean()) leftSymbol!! else rightSymbol!!,
                id = index
            )
        }

        symbolSequence = sequence
        currentSymbolIndex = 0

        Log.d(TAG, "Generated adaptive game with symbols:")
        Log.d(TAG, "Left: ${leftSymbol?.name}, Right: ${rightSymbol?.name}")
        Log.d(TAG, "Sequence length: ${sequence.size}")
        Log.d(TAG, "Difficulty: ${currentDifficultyLevel.name}")
        Log.d(TAG, "Time per symbol: ${adaptiveConfig.timePerSymbol}s")
    }

    // Initialize game when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        generateGameSymbols()
        if (gamesPlayedThisSession > 0) {
            gameCompleted = false
            currentSymbolIndex = 0
            score = 0
            consecutiveCorrect = 0
            multiplier = 1
            currentHearts = currentDifficultyLevel.livesAllowed
            timeLeft = adaptiveConfig.totalTime
        }
    }

    // ✅ UPDATED: Performance recording function
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = consecutiveCorrect,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = adaptiveConfig.symbolCount / 10,
            totalScore = totalScore,
            puzzleType = "symbolSwipe"
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
            challengeComplexity = adaptiveConfig.symbolCount / 10,
            currentStreak = consecutiveCorrect,
            challengesCompleted = totalAnswers,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "symbolSwipe"
        )
    }

    // Timer countdown
    LaunchedEffect(gameStarted, isPaused, gameCompleted) {
        if (gameStarted && !isPaused && !gameCompleted) {
            while (timeLeft > 0 && currentSymbolIndex < symbolSequence.size) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0 || currentSymbolIndex >= symbolSequence.size) {
                gameCompleted = true
                val success = currentSymbolIndex >= symbolSequence.size
                val accuracy = if (symbolSequence.isNotEmpty()) {
                    consecutiveCorrect.toFloat() / symbolSequence.size.toFloat()
                } else 0f

                gamesPlayedThisSession++

                recordPerformance(success, (adaptiveConfig.totalTime - timeLeft).toLong() * 1000L)

                onGameComplete(success, totalScore)
            }
        }
    }

    // Get required swipe direction for current symbol
    fun getRequiredDirection(symbol: GameSymbol): SwipeDirection {
        return when (symbol) {
            leftSymbol -> SwipeDirection.LEFT
            rightSymbol -> SwipeDirection.RIGHT
            else -> SwipeDirection.LEFT
        }
    }

    // Handle swipe with unified scoring
    fun handleSwipe(direction: SwipeDirection) {
        if (gameCompleted || !gameStarted || currentSymbolIndex >= symbolSequence.size) return

        val currentSymbol = symbolSequence[currentSymbolIndex].symbol
        val requiredDirection = getRequiredDirection(currentSymbol)
        val isCorrect = direction == requiredDirection

        totalAnswers++

        if (isCorrect) {
            correctAnswers++
            consecutiveCorrect++
            multiplier = when {
                consecutiveCorrect >= 15 -> 5
                consecutiveCorrect >= 10 -> 4
                consecutiveCorrect >= 5 -> 3
                consecutiveCorrect >= 3 -> 2
                else -> 1
            }

            val symbolScore = calculateScore(isCorrect, (adaptiveConfig.timePerSymbol * 1000).toLong())
            val points = symbolScore * multiplier
            score += points
            totalScore += points

            feedbackMessage = if (multiplier > 1) "Perfect! +$points (x${multiplier})" else "Correct! +$points"
            feedbackColor = Color(0xFF4CAF50)
        } else {
            consecutiveCorrect = 0
            multiplier = 1
            currentHearts = maxOf(0, currentHearts - 1)

            feedbackMessage = "Wrong direction! Lives: $currentHearts"
            feedbackColor = Color(0xFFE91E63)

            if (currentHearts == 0) {
                gameCompleted = true
                gamesPlayedThisSession++

                recordPerformance(false, (adaptiveConfig.totalTime - timeLeft).toLong() * 1000L)

                onGameComplete(false, totalScore)
                return
            }
        }

        recordPerformance(isCorrect, (adaptiveConfig.timePerSymbol * 1000).toLong())

        showFeedback = true
        currentSymbolIndex++

        // Check if game completed successfully
        if (currentSymbolIndex >= symbolSequence.size) {
            gameCompleted = true
            gamesPlayedThisSession++

            val finalAccuracy = correctAnswers.toFloat() / totalAnswers.toFloat()

            recordPerformance(true, (adaptiveConfig.totalTime - timeLeft).toLong() * 1000L)

            onGameComplete(true, totalScore)
        }
    }

    // Feedback auto-hide
    LaunchedEffect(showFeedback) {
        if (showFeedback) {
            delay(600)
            showFeedback = false
        }
    }

    // Scale animation for symbol
    val animatedScale by animateFloatAsState(
        targetValue = symbolScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF6A1B9A))
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ✅ REPLACE: Use unified header instead of AdaptiveSymbolSwipeTopBar
        AdaptiveUnifiedHeader(
            level = currentLevel,
            streakInfo = streakInfo,
            timer = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
            lives = currentHearts,
            currentDifficulty = currentDifficultyLevel,
            score = totalScore,
            puzzleType = "symbolSwipe",
            challengeNumber = currentPuzzleNumber,
            totalChallenges = totalPuzzles,
            competitiveInsight = competitiveInsight,
            onBack = onBack,
            onPause = { isPaused = !isPaused },
            onHint = {
                Log.d(TAG, "Hint: Swipe ${if (symbolSequence.getOrNull(currentSymbolIndex)?.symbol == leftSymbol) "LEFT" else "RIGHT"} for current symbol")
            }
        )

        // ✅ REPLACE: Use unified adaptation notification
        UnifiedAdaptationNotification(
            adaptationInfo = adaptationInfo,
            puzzleType = "symbolSwipe",
            visible = showAdaptationNotification,
            onDismiss = { showAdaptationNotification = false }
        )

        // Instructions - only show for first puzzle
        if (!gameStarted && isFirstPuzzle) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔄 Adaptive Symbol Swipe",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "When you see the LEFT symbol, swipe LEFT\nWhen you see the RIGHT symbol, swipe RIGHT",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = currentDifficultyLevel.description,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Show the two symbols that will be used
                    if (leftSymbol != null && rightSymbol != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Gray.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SymbolCanvas(
                                        symbol = leftSymbol!!,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Text("← SWIPE LEFT", fontSize = 12.sp, color = Color.Gray)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Gray.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SymbolCanvas(
                                        symbol = rightSymbol!!,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Text("SWIPE RIGHT →", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            gameStarted = true
                            sessionStartTime = System.currentTimeMillis()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text(stringResource(R.string.start), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Game Area
        if (gameStarted && !gameCompleted && currentSymbolIndex < symbolSequence.size) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // Reference symbols in corners
                if (leftSymbol != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        SymbolCanvas(
                            symbol = leftSymbol!!,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }

                if (rightSymbol != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        SymbolCanvas(
                            symbol = rightSymbol!!,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }

                // Current symbol display
                val currentSymbol = symbolSequence[currentSymbolIndex]
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(200.dp)
                        .offset(x = currentSymbolOffset.dp)
                        .scale(animatedScale)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.95f))
                        .pointerInput(currentSymbolIndex) {
                            detectDragGestures(
                                onDragStart = {
                                    symbolScale = 1.05f
                                },
                                onDragEnd = {
                                    symbolScale = 1f
                                    if (abs(currentSymbolOffset) > 100) {
                                        val direction = if (currentSymbolOffset > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                                        handleSwipe(direction)
                                    }
                                    currentSymbolOffset = 0f
                                }
                            ) { _, dragAmount ->
                                currentSymbolOffset += dragAmount.x * 0.5f
                                currentSymbolOffset = currentSymbolOffset.coerceIn(-300f, 300f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SymbolCanvas(
                        symbol = currentSymbol.symbol,
                        modifier = Modifier.size(120.dp)
                    )
                }

                // Progress dots at bottom
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(minOf(symbolSequence.size, 12)) { index ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        index < currentSymbolIndex -> Color(0xFF4CAF50)
                                        index == currentSymbolIndex -> Color(0xFFFFEB3B)
                                        else -> Color.White.copy(alpha = 0.4f)
                                    }
                                )
                        )
                    }
                }

                // Lives display
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(currentDifficultyLevel.livesAllowed) { index ->
                        val alpha = if (index < currentHearts) 1f else 0.3f
                        Text(
                            text = "●",
                            color = Color(0xFFFFEB3B).copy(alpha = alpha),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Auto-start countdown for non-first puzzles
        if (!gameStarted && !isFirstPuzzle) {
            var countdown by remember { mutableStateOf(3) }

            LaunchedEffect(Unit) {
                while (countdown > 0) {
                    delay(1000)
                    countdown--
                }
                gameStarted = true
                sessionStartTime = System.currentTimeMillis()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.get_ready),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "$countdown",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                        Text(
                            text = currentDifficultyLevel.name,
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Feedback overlay
        if (showFeedback) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = feedbackColor.copy(alpha = 0.9f))
            ) {
                Text(
                    text = feedbackMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ✅ NEW: Session completion handling
        if (gameCompleted && (currentHearts <= 0 || currentSymbolIndex >= symbolSequence.size)) {
            UnifiedSessionCompletionHandler(
                puzzleType = "symbolSwipe",
                sessionScore = totalScore,
                sessionStats = SessionStatistics(
                    correctAnswers = correctAnswers,
                    totalAnswers = totalAnswers,
                    totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                    bestStreak = consecutiveCorrect,
                    winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
                    totalScore = totalScore,
                    averageTimePerPuzzle = if (totalAnswers > 0)
                        ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt() / totalAnswers
                    else 0,
                    currentStreak = consecutiveCorrect,
                    individualTimes = emptyList() // Can track individual symbol times if needed
                ),
                currentDifficulty = currentDifficultyLevel
            ) { result ->
                onGameComplete(result.shouldShowRanking, totalScore)
            }
        }
    }
}

@Composable
fun AdaptiveSymbolSwipeTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    currentPuzzle: Int,
    totalPuzzles: Int,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    score: Int,
    multiplier: Int,
    onBack: () -> Unit,
    onPause: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${currentDifficulty.name} • Puzzle $currentPuzzle/$totalPuzzles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Text(
                    text = timer,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timer.startsWith("00:") && timer.substring(3).toIntOrNull()?.let { it <= 30 } == true) {
                        Color.Red
                    } else {
                        Color.Black
                    }
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.score_label),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "$score",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (multiplier > 1) {
                    Text(
                        text = "x$multiplier",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                }
            }

            IconButton(onClick = onPause) {
                Icon(Icons.Default.Pause, contentDescription = "Pause")
            }
        }

        // Lives display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(currentDifficulty.livesAllowed) { index ->
                Text(
                    text = if (index < lives) "❤️" else "🤍",
                    fontSize = 16.sp
                )
                if (index < currentDifficulty.livesAllowed - 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}

// Adaptive configuration data class
data class AdaptiveSymbolSwipeConfig(
    val symbolCount: Int,
    val timePerSymbol: Float,
    val totalTime: Int,
    val description: String
)

// Generate adaptive configuration based on difficulty level
fun generateAdaptiveSymbolSwipeConfig(difficulty: DifficultyManager.DifficultyLevel): AdaptiveSymbolSwipeConfig {
    return when (difficulty.index) {
        0 -> AdaptiveSymbolSwipeConfig( // Beginner
            symbolCount = 10,
            timePerSymbol = 4.0f,
            totalTime = 40,
            description = "10 symbols, 4s each - Learn the basics"
        )
        1 -> AdaptiveSymbolSwipeConfig( // Easy
            symbolCount = 15,
            timePerSymbol = 3.0f,
            totalTime = 45,
            description = "15 symbols, 3s each - Building speed"
        )
        2 -> AdaptiveSymbolSwipeConfig( // Medium
            symbolCount = 20,
            timePerSymbol = 2.5f,
            totalTime = 50,
            description = "20 symbols, 2.5s each - Good pace"
        )
        3 -> AdaptiveSymbolSwipeConfig( // Hard
            symbolCount = 25,
            timePerSymbol = 2.0f,
            totalTime = 50,
            description = "25 symbols, 2s each - Fast reflexes"
        )
        4 -> AdaptiveSymbolSwipeConfig( // Expert
            symbolCount = 30,
            timePerSymbol = 1.5f,
            totalTime = 45,
            description = "30 symbols, 1.5s each - Lightning speed"
        )
        else -> AdaptiveSymbolSwipeConfig(
            symbolCount = 20,
            timePerSymbol = 2.5f,
            totalTime = 50,
            description = "Standard difficulty"
        )
    }
}
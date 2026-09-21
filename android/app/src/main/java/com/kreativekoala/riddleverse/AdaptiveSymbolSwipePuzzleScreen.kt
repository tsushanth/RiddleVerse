// AdaptiveSymbolSwipePuzzleScreen.kt
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
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
import androidx.compose.ui.platform.testTag
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
                showAdaptationNotification = SHOW_ADAPTATION_NOTICES
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
            feedbackColor = RvSuccess
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        val compact = groupEIsCompact(maxWidth, maxHeight)
        val timerText = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (compact) {
                GroupECompactHud(
                    timer = timerText,
                    onBack = onBack,
                    subtitle = "${currentDifficultyLevel.name} \u2022 ${stringResource(R.string.score_label)} $totalScore \u2022 ${currentSymbolIndex}/${symbolSequence.size}",
                    lives = currentHearts,
                    urgent = timeLeft <= 30,
                    onPause = { isPaused = !isPaused }
                )
            } else {
                // ✅ REPLACE: Use unified header instead of AdaptiveSymbolSwipeTopBar
                Box(modifier = Modifier.testTag("hud_timer")) { AdaptiveUnifiedHeader(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = timerText,
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
                ) }
            }

            // ✅ REPLACE: Use unified adaptation notification
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "symbolSwipe",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Instructions - only show for first puzzle
            if (!gameStarted && isFirstPuzzle) {
                GroupESwipeInstructions(
                    title = "\uD83D\uDD04 Adaptive Symbol Swipe",
                    leftSymbol = leftSymbol,
                    rightSymbol = rightSymbol,
                    extra = if (compact) null else currentDifficultyLevel.description,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                GroupEPrimaryButton(
                    text = stringResource(R.string.start),
                    onClick = {
                        gameStarted = true
                        sessionStartTime = System.currentTimeMillis()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (gameStarted && !gameCompleted && currentSymbolIndex < symbolSequence.size) {
                GroupESwipeField(
                    leftSymbol = leftSymbol,
                    rightSymbol = rightSymbol,
                    currentSymbol = symbolSequence[currentSymbolIndex].symbol,
                    offsetDp = currentSymbolOffset,
                    scale = animatedScale,
                    dragKey = currentSymbolIndex,
                    onDragStart = { symbolScale = 1.05f },
                    onDragEnd = {
                        symbolScale = 1f
                        if (abs(currentSymbolOffset) > 100) {
                            val direction = if (currentSymbolOffset > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                            handleSwipe(direction)
                        }
                        currentSymbolOffset = 0f
                    },
                    onDrag = { dx ->
                        currentSymbolOffset += dx * 0.5f
                        currentSymbolOffset = currentSymbolOffset.coerceIn(-300f, 300f)
                    },
                    progressIndex = currentSymbolIndex,
                    progressTotal = symbolSequence.size,
                    feedbackMessage = if (showFeedback) feedbackMessage else null,
                    feedbackColor = feedbackColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
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
                        colors = CardDefaults.cardColors(containerColor = RvSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.get_ready),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "$countdown",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvViolet
                            )
                            Text(
                                text = currentDifficultyLevel.name,
                                fontSize = 14.sp,
                                color = RvInkSoft,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
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
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised)
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
                    color = RvInkSoft
                )
                Text(
                    text = timer,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timer.startsWith("00:") && timer.substring(3).toIntOrNull()?.let { it <= 30 } == true) {
                        Color.Red
                    } else {
                        RvInk
                    }
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.score_label),
                    fontSize = 12.sp,
                    color = RvInkSoft
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
                        color = RvSun
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
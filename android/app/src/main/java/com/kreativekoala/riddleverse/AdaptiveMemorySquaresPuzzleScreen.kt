// AdaptiveMemorySquaresPuzzleScreen.kt - Enhanced with adaptive difficulty
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun amrecordGamePerformance(
    difficultyManager: DifficultyManager,
    accuracy: Float,
    timeSpent: Float,
    streak: Int,
    livesRemaining: Int,
    score: Int,
    difficulty: DifficultyManager.DifficultyLevel,
    onAdaptation: (DifficultyManager.AdaptiveConfig) -> Unit
) {
    val expectedTimeForDifficulty = difficulty.timeLimit.toFloat()
    val performance = DifficultyManager.PlayerPerformance(
        accuracy = accuracy,
        averageTime = timeSpent,
        streakLength = streak,
        livesRemaining = livesRemaining,
        gameScore = score,
        difficulty = difficulty.name,
        puzzleType = "memorysquares",
        timestamp = System.currentTimeMillis(),
        timeEfficiency = if (timeSpent > 0f) { // Fixed: Calculate efficiency
            (expectedTimeForDifficulty / timeSpent).coerceAtMost(2.0f)
        } else {
            1.0f
        }
    )

    val adaptiveConfig = difficultyManager.recordPerformance(performance)
    onAdaptation(adaptiveConfig)
}

@Composable
fun AdaptiveMemorySquaresPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String = "1:30",
    hearts: Int = 3,
    level: String = "1/5",
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val TAG = "AdaptiveMemorySquares"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var showHint by remember { mutableStateOf(false) }

    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("memorysquares"))
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
                    puzzleType = "memorysquares",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Score and performance tracking
    var totalScore by remember { mutableIntStateOf(0) }
    var correctSelections by remember { mutableIntStateOf(0) }
    var wrongSelections by remember { mutableIntStateOf(0) }
    var currentStreak by remember { mutableIntStateOf(0) }
    var gamesPlayedThisSession by remember { mutableIntStateOf(0) }

    // ✅ NEW: Track session for competitive ranking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Use current difficulty level for puzzle configuration
    val gridSize = currentDifficultyLevel.gridSize
    val targetCount = currentDifficultyLevel.targetCount
    val memorizeTimeLimit = currentDifficultyLevel.memorizeTime
    val livesAllowed = currentDifficultyLevel.livesAllowed
    val haptics = LocalHapticFeedback.current

    // Format time helper
    fun formatTime(seconds: Int): String {
        return "${seconds / 60}:${String.format("%02d", seconds % 60)}"
    }

    // Timer calculation based on difficulty
    val totalTimeSeconds = currentDifficultyLevel.timeLimit
    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(formatTime(totalTimeSeconds)) }

    // Generate random target positions (regenerate when difficulty changes)
    val targetPositions = remember(currentDifficultyLevel) {
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
    var currentLives by remember { mutableIntStateOf(livesAllowed) }
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

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) { // Don't reset on first load
            gamePhase = MemoryGamePhase.COUNTDOWN
            selectedCells = emptySet()
            incorrectSelections = emptySet()
            correctSelections = 0
            wrongSelections = 0
            countdownTime = 3
            memorizeTime = memorizeTimeLimit
            currentLives = livesAllowed
            timeRemaining = totalTimeSeconds
            displayTimer = formatTime(totalTimeSeconds)
            gameCompleted = false
            showFeedback = false
        }
    }

    // ✅ UPDATED: Performance recording function
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentLives,
            difficulty = currentDifficultyLevel,
            challengeComplexity = gridSize * targetCount,
            totalScore = totalScore,
            puzzleType = "memorysquares"
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
            challengeComplexity = gridSize * targetCount,
            currentStreak = currentStreak,
            challengesCompleted = gamesPlayedThisSession,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "memorysquares"
        )
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, gameCompleted) {
        if (timeRemaining > 0 && !gameCompleted) {
            delay(1000L)
            timeRemaining--
            displayTimer = formatTime(timeRemaining)
        } else if (timeRemaining == 0 && !gameCompleted) {
            // Time's up
            val timeSpent = System.currentTimeMillis() - gameStartTime
            val finalScore = calculateScore(false, timeSpent)

            recordPerformance(false, timeSpent)

            totalScore = finalScore
            currentStreak = 0
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
                gameStartTime = System.currentTimeMillis()
            }
            else -> { /* No timer needed for other phases */ }
        }
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
                    val newStreak = currentStreak + 1
                    val gameScore = calculateScore(true, timeSpent)

                    recordPerformance(true, timeSpent)

                    totalScore = gameScore
                    currentStreak = newStreak
                    gamesPlayedThisSession++
                    isCorrect = true
                    gamePhase = MemoryGamePhase.FEEDBACK
                    showFeedback = true
                }
            } else {
                // Wrong selection
                incorrectSelections = incorrectSelections + cellPosition
                selectedCells = selectedCells + cellPosition
                currentLives--
                wrongSelections++

                if (currentLives <= 0) {
                    val timeSpent = System.currentTimeMillis() - gameStartTime
                    val gameScore = calculateScore(false, timeSpent)

                    recordPerformance(false, timeSpent)

                    totalScore = gameScore
                    currentStreak = 0
                    gamesPlayedThisSession++
                    isCorrect = false
                    gamePhase = MemoryGamePhase.FEEDBACK
                    showFeedback = true
                } else {
                    // Remove wrong selection after a brief moment
                    // Use composable-scoped coroutine instead of GlobalScope to prevent memory leaks
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

    // ✅ NEW: Session completion handling
    if (showFeedback) {
        UnifiedSessionCompletionHandler(
            puzzleType = "memorysquares",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = if (isCorrect) 1 else 0,
                totalAnswers = 1,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (isCorrect) 1f else 0f,
                totalScore = totalScore, // Fixed: Use the actual total score
                averageTimePerPuzzle = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(), // Fixed: Time for this puzzle
                currentStreak = currentStreak, // Fixed: Use the current streak value
                individualTimes = listOf(((System.currentTimeMillis() - gameStartTime) / 1000).toInt()) // Fixed: List with current game time
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            // Handle session completion
            onSubmitAnswer(isCorrect)
            fetchNextPuzzle(totalScore)
        }
    }

    MemorySquaresFitLayout(
        modifier = Modifier.background(Color(0xFF8D6E63)),
        hud = { compact ->
            if (compact) {
                GameCompactHud(
                    timer = displayTimer,
                    lives = currentLives,
                    maxLives = livesAllowed,
                    score = totalScore,
                    onBack = onBack
                )
            } else {
                AdaptiveUnifiedHeader(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    lives = currentLives,
                    currentDifficulty = currentDifficultyLevel,
                    score = totalScore,
                    puzzleType = "memorysquares",
                    competitiveInsight = competitiveInsight,
                    onBack = onBack,
                    onHint = {
                        showHint = !showHint
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
        },
        info = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RvNight, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LEVEL $level" + if (currentStreak > 0) "  🔥 $currentStreak" else "", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RvOnTone, maxLines = 1)
                Text(currentDifficultyLevel.name.uppercase() + " • ${gridSize}×${gridSize} • $targetCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RvOnTone, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        phase = {
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
                            color = RvOnTone,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Memorize ${targetCount} squares in ${memorizeTimeLimit}s",
                            fontSize = 16.sp,
                            color = RvOnTone,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    MemoryGamePhase.MEMORIZE -> {
                        Text(
                            text = stringResource(R.string.memorize_the_pattern),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvOnTone,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${memorizeTime}s remaining",
                            fontSize = 16.sp,
                            fontWeight = if (memorizeTime <= 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (memorizeTime <= 1) Color(0xFFFFEB3B) else RvOnTone,
                            maxLines = 1
                        )
                    }
                    MemoryGamePhase.RECALL -> {
                        Text(
                            text = stringResource(R.string.click_squares_you_remember),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvOnTone,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Found: ${selectedCells.intersect(targetPositions).size}/$targetCount" +
                                if (correctSelections > 0 || wrongSelections > 0) "   ✅ $correctSelections   ❌ $wrongSelections" else "",
                            fontSize = 14.sp,
                            color = RvOnTone,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    else -> {
                        Text(
                            text = if (isCorrect) "✓ Perfect!" else "✗ Try Again!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCorrect) Color(0xFFC8E6C9) else Color(0xFFFFCDD2),
                            maxLines = 1
                        )
                    }
                }
            }
        },
        actions = {
            if (gamePhase == MemoryGamePhase.RECALL) {
                MemorySquaresActionBar(
                    onClear = {
                        selectedCells = emptySet()
                        incorrectSelections = emptySet()
                        correctSelections = 0
                        wrongSelections = 0
                    },
                    onSubmit = {
                        val correctCount = selectedCells.intersect(targetPositions).size
                        val incorrectCount = selectedCells.size - correctCount
                        val timeSpent = System.currentTimeMillis() - gameStartTime
                        val finalAccuracy = if (targetCount > 0) correctCount.toFloat() / targetCount else 0f

                        val gameScore = calculateScore(
                            correctCount == targetCount && incorrectCount == 0,
                            timeSpent
                        )

                        recordPerformance(
                            correctCount == targetCount && incorrectCount == 0,
                            timeSpent
                        )

                        totalScore = gameScore
                        currentStreak = if (correctCount == targetCount && incorrectCount == 0) currentStreak + 1 else 0
                        gamesPlayedThisSession++
                        isCorrect = correctCount == targetCount && incorrectCount == 0
                        gamePhase = MemoryGamePhase.FEEDBACK
                        showFeedback = true
                    },
                    submitColor = Color(0xFF4FC3F7)
                )
            }
        },
        board = { size ->
            MemorySquaresBoard(
                size = size,
                gridSize = gridSize,
                targetCount = targetCount,
                targetPositions = targetPositions,
                selectedCells = selectedCells,
                incorrectSelections = incorrectSelections,
                gamePhase = gamePhase,
                countdownTime = countdownTime,
                highlightColor = Color(0xFF00BCD4),
                correctColor = Color(0xFF4CAF50),
                wrongColor = Color(0xFFF44336),
                frameColor = Color.Black.copy(alpha = 0.8f),
                onCellClick = ::onCellClick
            )
        }
    )

    // Enhanced feedback dialog with adaptation info
    if (showFeedback && !gameCompleted) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
                Button(
                    onClick = {
                        showFeedback = false
                        gameCompleted = true
                        onSubmitAnswer(isCorrect)
                        fetchNextPuzzle(totalScore)
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
                            isCorrect -> "Perfect! You remembered all $targetCount squares!\n\nAccuracy: 100%\nStreak: $currentStreak 🔥"
                            currentLives <= 0 -> "You found $correctCount/$targetCount squares ($accuracy%)\n\nStreak broken"
                            else -> "You found $correctCount/$targetCount squares ($accuracy%)\n\nStreak: ${if (isCorrect) currentStreak else 0}"
                        },
                        textAlign = TextAlign.Center
                    )

                    // Show adaptation info if available
                    adaptationInfo?.let { info ->
                        if (SHOW_ADAPTATION_NOTICES && info.confidenceScore > 0.5f) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🎯 Difficulty Adapted",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Next: ${info.level.name}",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    if (!isCorrect) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 Tip: Try grouping nearby squares or creating a mental story!",
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
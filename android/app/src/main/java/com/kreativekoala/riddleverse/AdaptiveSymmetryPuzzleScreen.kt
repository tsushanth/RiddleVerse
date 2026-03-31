// AdaptiveSymmetryPuzzleScreen.kt
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun AdaptiveSymmetryPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String,
    hearts: Int = 3,
    level: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveSymmetry"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("symmetry"))
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
                    puzzleType = "symmetry",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate puzzle data based on current difficulty
    val puzzleData = remember(currentDifficultyLevel) {
        generateAdaptiveSymmetryPuzzle(currentDifficultyLevel)
    }

    // Game state
    var userRightPattern by remember { mutableStateOf<List<MutableList<Boolean>>>(emptyList()) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0) }
    var totalScore by remember { mutableStateOf(0) }
    var timeRemaining by remember { mutableStateOf(currentDifficultyLevel.timeLimit) }
    var isTimerRunning by remember { mutableStateOf(true) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var puzzleStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var requiredCells by remember { mutableStateOf(0) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // ✅ NEW: Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAnswers by remember { mutableStateOf(0) }

    // ✅ NEW: Session completion state
    var sessionResult by remember { mutableStateOf<AdaptiveSessionResult?>(null) }
    var gameCompleted by remember { mutableStateOf(false) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Initialize pattern when puzzle changes
    LaunchedEffect(puzzleData) {
        userRightPattern = List(puzzleData.gridSize) {
            MutableList(puzzleData.gridSize) { false }
        }
        requiredCells = puzzleData.leftPattern.sumOf { row -> row.count { it } }
        puzzleStartTime = System.currentTimeMillis()
        timeRemaining = currentDifficultyLevel.timeLimit
        if (gamesPlayedThisSession > 0) {
            currentHearts = currentDifficultyLevel.livesAllowed
        }
    }

    // ✅ UPDATED: Enhanced score calculation
    fun calculateScore(isCorrect: Boolean, timeSpent: Double): Int {
        return calculateUnifiedAdaptiveScore(
            isCorrect = isCorrect,
            timeSpent = (timeSpent * 1000).toLong(),
            difficulty = currentDifficultyLevel,
            challengeComplexity = puzzleData.gridSize,
            currentStreak = currentStreak,
            challengesCompleted = gamesPlayedThisSession + 1,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "symmetry"
        )
    }

    // ✅ UPDATED: Enhanced performance recording
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = puzzleData.gridSize,
            totalScore = totalScore,
            puzzleType = "symmetry"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
            }
        }
    }

    // Auto-check when user selects the required number of cells
    LaunchedEffect(userRightPattern) {
        if (!showFeedback) {
            val selectedCells = userRightPattern.sumOf { row -> row.count { it } }

            if (selectedCells == requiredCells && requiredCells > 0) {
                isTimerRunning = false
                val timeSpent = ((System.currentTimeMillis() - puzzleStartTime) / 1000.0)
                val correct = checkSymmetryPattern(puzzleData.correctRightPattern, userRightPattern)
                isCorrect = correct

                totalAnswers++

                if (correct) {
                    correctAnswers++
                    val newStreak = currentStreak + 1
                    score = calculateScore(isCorrect = true, timeSpent = timeSpent)
                    totalScore += score
                    currentStreak = newStreak
                    gamesPlayedThisSession++

                    feedbackMessage = when {
                        timeSpent < 1.0 -> "Lightning fast! ⚡️"
                        timeSpent < 2.0 -> "Super quick! 🚀"
                        timeSpent < 4.0 -> "Nice speed! ✨"
                        else -> "Perfect! 🎯"
                    }

                    recordPerformance(true, (timeSpent * 1000).toLong())
                } else {
                    currentHearts = maxOf(0, currentHearts - 1)
                    currentStreak = 0
                    feedbackMessage = when {
                        timeSpent < 1.0 -> "Too fast! Look closer 👀"
                        else -> "Not quite right 🤔"
                    }

                    recordPerformance(false, (timeSpent * 1000).toLong())
                }

                showFeedback = true
                onSubmitAnswer(correct)

                delay(if (correct) 1200 else 1800)

                // Mark game as completed to trigger session completion
                if (correct || currentHearts <= 0) {
                    gameCompleted = true
                } else {
                    fetchNextPuzzle(totalScore)
                }
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
            isTimerRunning = false
            showFeedback = true
            isCorrect = false
            feedbackMessage = "Time's up! ⏰"
            currentStreak = 0
            totalAnswers++

            recordPerformance(false, currentDifficultyLevel.timeLimit * 1000L)
            onSubmitAnswer(false)

            // Mark game as completed to trigger session completion
            gameCompleted = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ✅ REPLACE: Use unified header instead of AdaptiveSymmetryTopBar
        AdaptiveUnifiedHeader(
            level = currentLevel,
            streakInfo = streakInfo,
            timer = formatTime(timeRemaining),
            lives = currentHearts,
            currentDifficulty = currentDifficultyLevel,
            score = totalScore,
            puzzleType = "symmetry",
            competitiveInsight = competitiveInsight,
            onBack = onBack,
            challengeNumber = totalAnswers + 1,
            totalChallenges = 10, // Or whatever makes sense for symmetry puzzles
            onPause = {
                isTimerRunning = !isTimerRunning
                Log.d(TAG, "Timer paused/resumed")
            },
            onHint = {
                // Show hint functionality - could highlight required cells count
                Log.d(TAG, "Hint: You need to select $requiredCells cells total")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ✅ REPLACE: Use unified adaptation notification
        UnifiedAdaptationNotification(
            adaptationInfo = adaptationInfo,
            puzzleType = "symmetry",
            visible = showAdaptationNotification,
            onDismiss = { showAdaptationNotification = false }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Title with mirror indicator and difficulty info
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
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (puzzleData.isMirror) Color(0xFFE3F2FD) else Color(0xFFF3E5F5)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (puzzleData.isMirror) "🪞 MIRROR" else "📋 COPY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (puzzleData.isMirror) Color(0xFF1976D2) else Color(0xFF7B1FA2),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Dynamic instruction and difficulty description
        Text(
            text = if (puzzleData.isMirror) {
                when (puzzleData.mirrorType) {
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

        Text(
            text = currentDifficultyLevel.description,
            fontSize = 12.sp,
            color = Color(0xFF2196F3),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 2.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Enhanced progress with adaptive metrics
        if (totalScore > 0 || gamesPlayedThisSession > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.score_label).uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Text(
                                text = "$totalScore",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2196F3)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentDifficultyLevel.name.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                        Text(
                            text = "${puzzleData.gridSize}x${puzzleData.gridSize}",
                            fontSize = 12.sp,
                            color = Color(0xFF666666)
                        )
                    }

                    // Cell counter for user feedback
                    val selectedCells = userRightPattern.sumOf { row -> row.count { it } }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "CELLS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            text = "$selectedCells / $requiredCells",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedCells == requiredCells) Color(0xFF4CAF50) else Color(0xFF666666)
                        )
                    }

                    if (currentStreak > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.streak_label).uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Text(
                                text = "🔥 $currentStreak",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6F00)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

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
                    text = if (puzzleData.isMirror) "Mirror this:" else "Copy this:",
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                AdaptiveSymmetryGrid(
                    pattern = puzzleData.leftPattern,
                    isInteractive = false,
                    difficulty = currentDifficultyLevel,
                    onCellClick = { _, _ -> }
                )
            }

            // Divider with mirror indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (puzzleData.isMirror) {
                    Text(
                        text = when (puzzleData.mirrorType) {
                            "horizontal" -> "↔️"
                            "vertical" -> "↕️"
                            else -> "="
                        },
                        fontSize = 20.sp,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (puzzleData.isMirror) 100.dp else 120.dp)
                        .background(
                            if (puzzleData.isMirror) Color(0xFF2196F3) else Color(0xFFE0E0E0)
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

                AdaptiveSymmetryGrid(
                    pattern = userRightPattern,
                    isInteractive = !showFeedback,
                    difficulty = currentDifficultyLevel,
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

        // Feedback section
        if (showFeedback) {
            AdaptiveSymmetrySpeedFeedback(
                isCorrect = isCorrect,
                message = feedbackMessage,
                score = score,
                difficultyLevel = currentDifficultyLevel
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }

    // ✅ NEW: Session completion handling outside of LaunchedEffect
    if (gameCompleted) {
        UnifiedSessionCompletionHandler(
            puzzleType = "symmetry",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAnswers,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
                totalScore = totalScore,
                averageTimePerPuzzle = if (totalAnswers > 0)
                    ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt() / totalAnswers
                else 0,
                currentStreak = currentStreak,
                individualTimes = emptyList() // Can track individual puzzle solve times if needed
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            sessionResult = result
            fetchNextPuzzle(totalScore)
        }
    }
}


@Composable
fun AdaptiveSymmetryTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button and level
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

            Column {
                Text(
                    text = "Level ${level.level}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF666666)
                )

                Text(
                    text = currentDifficulty.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
            }
        }

        // Timer
        Text(
            text = timer,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2196F3)
        )

        // Hearts with adaptive count
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

@Composable
fun AdaptiveSymmetryGrid(
    pattern: List<List<Boolean>>,
    isInteractive: Boolean,
    difficulty: DifficultyManager.DifficultyLevel,
    onCellClick: (Int, Int) -> Unit
) {
    val gridSize = pattern.size

    // Adaptive cell size based on difficulty and grid size
    val cellSize = when {
        gridSize <= 3 -> 35.dp
        gridSize == 4 -> 28.dp
        gridSize == 5 -> 22.dp
        gridSize >= 6 -> 18.dp
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
fun AdaptiveSymmetrySpeedFeedback(
    isCorrect: Boolean,
    message: String,
    score: Int,
    difficultyLevel: DifficultyManager.DifficultyLevel
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
                    text = "Difficulty: ${difficultyLevel.name} • Speed Bonus! ⚡",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Helper function to generate adaptive symmetry puzzle
fun generateAdaptiveSymmetryPuzzle(difficulty: DifficultyManager.DifficultyLevel): AdaptiveSymmetryPuzzleData {
    val gridSize = when (difficulty.index) {
        0 -> 3  // Beginner
        1 -> 3  // Easy
        2 -> 4  // Medium
        3 -> 5  // Hard
        4 -> 6  // Expert
        else -> 3
    }

    val isMirror = when (difficulty.index) {
        0 -> false              // Beginner - copy only
        1 -> Random.nextFloat() < 0.3f  // Easy - 30% mirrors
        2 -> Random.nextFloat() < 0.5f  // Medium - 50% mirrors
        3 -> Random.nextFloat() < 0.7f  // Hard - 70% mirrors
        4 -> Random.nextFloat() < 0.8f  // Expert - 80% mirrors
        else -> false
    }

    val mirrorType = if (isMirror) {
        when (difficulty.index) {
            0, 1 -> "horizontal"  // Start with horizontal only
            2 -> listOf("horizontal", "vertical").random()
            3, 4 -> listOf("horizontal", "vertical").random()
            else -> "horizontal"
        }
    } else {
        "none"
    }

    // Generate left pattern
    val leftPattern = generateAdaptiveSymmetryPattern(gridSize, difficulty, isMirror)

    // Generate correct right pattern
    val correctRightPattern = if (isMirror) {
        when (mirrorType) {
            "horizontal" -> mirrorPatternHorizontally(leftPattern)
            "vertical" -> mirrorPatternVertically(leftPattern)
            else -> leftPattern
        }
    } else {
        leftPattern
    }

    return AdaptiveSymmetryPuzzleData(
        gridSize = gridSize,
        leftPattern = leftPattern,
        correctRightPattern = correctRightPattern,
        difficulty = difficulty.name,
        questionNumber = 1,
        isMirror = isMirror,
        mirrorType = mirrorType
    )
}

fun generateAdaptiveSymmetryPattern(
    gridSize: Int,
    difficulty: DifficultyManager.DifficultyLevel,
    isMirror: Boolean = false
): List<List<Boolean>> {
    val pattern = MutableList(gridSize) { MutableList(gridSize) { false } }

    val fillDensity = when (difficulty.index) {
        0 -> if (isMirror) 0.25f else 0.30f  // Beginner
        1 -> if (isMirror) 0.30f else 0.35f  // Easy
        2 -> if (isMirror) 0.35f else 0.40f  // Medium
        3 -> if (isMirror) 0.40f else 0.45f  // Hard
        4 -> if (isMirror) 0.45f else 0.50f  // Expert
        else -> 0.30f
    }

    val totalCells = gridSize * gridSize
    val targetFilledCells = (totalCells * fillDensity).toInt().coerceIn(1, totalCells - 1)

    // Generate random pattern
    val positions = mutableListOf<Pair<Int, Int>>()
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            positions.add(Pair(row, col))
        }
    }
    positions.shuffle()

    for (i in 0 until targetFilledCells.coerceAtMost(positions.size)) {
        val (row, col) = positions[i]
        pattern[row][col] = true
    }

    return pattern.map { it.toList() }
}

data class AdaptiveSymmetryPuzzleData(
    val gridSize: Int,
    val leftPattern: List<List<Boolean>>,
    val correctRightPattern: List<List<Boolean>>,
    val difficulty: String,
    val questionNumber: Int,
    val isMirror: Boolean,
    val mirrorType: String
)
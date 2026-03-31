// AdaptiveDualCardPuzzleScreen.kt
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.random.Random



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveDualCardPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String = "1:30",
    hearts: Int = 3,
    level: String = "1/5",
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveDualCard"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("dualCardTask"))
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
                    puzzleType = "dualCardTask",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate adaptive configuration
    val adaptiveConfig = remember(currentDifficultyLevel) {
        generateAdaptiveDualCardConfig(currentDifficultyLevel)
    }

    var timeLeft by remember { mutableStateOf(currentDifficultyLevel.timeLimit) }
    var isPaused by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var gameCompleted by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(true) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAttempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var roundStartTimes by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var reactionTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var bestStreak by remember { mutableStateOf(0) }
    var taskSwitchCount by remember { mutableStateOf(0) }
    var taskSwitchAccuracy by remember { mutableStateOf<Map<String, Int>>(mapOf("even" to 0, "vowel" to 0)) }

    // ✅ NEW: Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var currentRound by remember { mutableStateOf(1) }
    var isCheckingTopCard by remember { mutableStateOf(true) }
    var currentCardData by remember { mutableStateOf(generateAdaptiveCardData(adaptiveConfig)) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrectAnswer by remember { mutableStateOf(false) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()
    var showHint by remember { mutableStateOf(false) }


    val displayTimer = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60)

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            val newConfig = generateAdaptiveDualCardConfig(currentDifficultyLevel)
            currentCardData = generateAdaptiveCardData(newConfig)
            currentHearts = currentDifficultyLevel.livesAllowed
            timeLeft = currentDifficultyLevel.timeLimit
            gameStartTime = System.currentTimeMillis()
            sessionStartTime = System.currentTimeMillis()
        }
    }

    // ✅ UPDATED: Enhanced performance recording
    fun recordAdaptivePerformance(
        isCorrect: Boolean,
        timeSpent: Long,
        streak: Int,
        livesRemaining: Int
    ) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = streak,
            livesRemaining = livesRemaining,
            difficulty = currentDifficultyLevel,
            challengeComplexity = adaptiveConfig.complexityLevel,
            totalScore = totalScore,
            puzzleType = "dualCardTask"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
            }
        }
    }

    // ✅ UPDATED: Use unified score calculation
    fun calculateScore(
        isCorrect: Boolean,
        reactionTime: Long,
        currentStreak: Int,
        totalSwitches: Int,
        roundsCompleted: Int
    ): Int {
        return calculateUnifiedAdaptiveScore(
            isCorrect = isCorrect,
            timeSpent = reactionTime,
            difficulty = currentDifficultyLevel,
            challengeComplexity = adaptiveConfig.complexityLevel,
            currentStreak = currentStreak,
            challengesCompleted = roundsCompleted,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "dualCardTask"
        )
    }

    // Initialize first round
    LaunchedEffect(Unit) {
        gameStartTime = System.currentTimeMillis()
        sessionStartTime = System.currentTimeMillis()
        roundStartTimes = mapOf(currentRound to System.currentTimeMillis())
    }

    // Timer countdown effect
    LaunchedEffect(timeLeft, gameCompleted) {
        if (timeLeft > 0 && !gameCompleted && !isPaused) {
            delay(1000L)
            timeLeft--
        } else if (timeLeft == 0 && !gameCompleted) {
            // Time's up - record poor performance
            recordAdaptivePerformance(
                isCorrect = false,
                timeSpent = currentDifficultyLevel.timeLimit * 1000L,
                streak = 0,
                livesRemaining = 0
            )

            gameCompleted = true
            onGameComplete(false, totalScore)
        }
    }

    LaunchedEffect(showFeedback) {
        if (showFeedback) {
            delay(1000)
            showFeedback = false

            if (currentHearts <= 0) {
                gameCompleted = true
                onGameComplete(false, totalScore)
            } else if (currentRound >= adaptiveConfig.totalRounds) {
                gameCompleted = true
                onGameComplete(true, totalScore)
            } else {
                currentRound++
                val wasCheckingTopCard = isCheckingTopCard

                // Adaptive task switching based on config
                isCheckingTopCard = when (adaptiveConfig.switchingPattern) {
                    "alternating" -> !isCheckingTopCard
                    "random" -> Random.nextBoolean()
                    "predictable" -> (currentRound % 2 == 1)
                    else -> !isCheckingTopCard
                }

                // Track task switching
                if (wasCheckingTopCard != isCheckingTopCard) {
                    taskSwitchCount++
                }

                currentCardData = generateAdaptiveCardData(adaptiveConfig)
                roundStartTimes = roundStartTimes + (currentRound to System.currentTimeMillis())
            }
        }
    }

    val haptics = LocalHapticFeedback.current

    fun handleAnswer(userAnsweredYes: Boolean) {
        if (gameCompleted || showFeedback) return

        totalAttempts++
        val roundStartTime = roundStartTimes[currentRound] ?: System.currentTimeMillis()
        val reactionTime = System.currentTimeMillis() - roundStartTime
        reactionTimes = reactionTimes + reactionTime

        val correct = checkAdaptiveAnswer(isCheckingTopCard, currentCardData, userAnsweredYes, adaptiveConfig)

        Log.d(TAG, "Round $currentRound: Task=${if (isCheckingTopCard) "even" else "vowel"}, " +
                "Answer=$userAnsweredYes, Correct=$correct, Reaction=${reactionTime}ms")

        isCorrectAnswer = correct
        showFeedback = true
        gamesPlayedThisSession++

        if (correct) {
            correctAnswers++
            currentStreak++
            if (currentStreak > bestStreak) bestStreak = currentStreak

            // Track task-specific accuracy
            val taskType = if (isCheckingTopCard) "even" else "vowel"
            taskSwitchAccuracy = taskSwitchAccuracy + (taskType to (taskSwitchAccuracy[taskType] ?: 0) + 1)

            val score = calculateScore(
                isCorrect = true,
                reactionTime = reactionTime,
                currentStreak = currentStreak,
                totalSwitches = taskSwitchCount,
                roundsCompleted = currentRound
            )
            totalScore += score
        } else {
            currentHearts--
            currentStreak = 0
        }

        // Record performance for adaptation
        recordAdaptivePerformance(
            isCorrect = correct,
            timeSpent = reactionTime,
            streak = currentStreak,
            livesRemaining = currentHearts
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF8B4B6B))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ✅ REPLACE: Use unified header instead of AdaptiveDualCardTopBar
            AdaptiveUnifiedHeader(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer,
                lives = currentHearts,
                currentDifficulty = currentDifficultyLevel,
                score = totalScore,
                puzzleType = "dualCardTask",
                challengeNumber = currentRound,
                totalChallenges = adaptiveConfig.totalRounds,
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

            Spacer(modifier = Modifier.height(24.dp))

            // ✅ REPLACE: Use unified adaptation notification
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "dualCardTask",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced task indicator with adaptive switching info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isCheckingTopCard) "Check if number is EVEN" else "Check if letter is VOWEL",
                    color = Color(0xFFFFD700),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Switching pattern: ${adaptiveConfig.switchingPattern}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                if (taskSwitchCount > 0) {
                    Text(
                        text = "⚡ Task switches: $taskSwitchCount",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Top Card - Number Even Check
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCheckingTopCard) Color.White else Color(0xFFBBBBBB)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Is the number even?",
                        color = if (isCheckingTopCard) Color.Black else Color(0xFF666666),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isCheckingTopCard) {
                        Text(
                            text = if (currentCardData.isLetterFirst) "${currentCardData.letter}${currentCardData.digit}" else "${currentCardData.digit}${currentCardData.letter}",
                            color = currentCardData.textColor,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Box(
                            modifier = Modifier.height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "—",
                                color = Color(0xFF666666),
                                fontSize = 24.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Card - Vowel Check
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (!isCheckingTopCard) Color.White else Color(0xFFBBBBBB)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Is the letter a vowel?",
                        color = if (!isCheckingTopCard) Color.Black else Color(0xFF666666),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isCheckingTopCard) {
                        Text(
                            text = if (currentCardData.isLetterFirst) "${currentCardData.letter}${currentCardData.digit}" else "${currentCardData.digit}${currentCardData.letter}",
                            color = currentCardData.textColor,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Box(
                            modifier = Modifier.height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "—",
                                color = Color(0xFF666666),
                                fontSize = 24.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Feedback display
            if (showFeedback) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isCorrectAnswer) "Correct! ✓" else "Wrong! ✗",
                        color = if (isCorrectAnswer) Color(0xFF4CAF50) else Color(0xFFFF5722),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (isCorrectAnswer && reactionTimes.isNotEmpty()) {
                        val reactionTime = reactionTimes.last() / 1000.0
                        Text(
                            text = "⚡ ${String.format("%.1f", reactionTime)}s",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Answer buttons
            if (!gameCompleted && !showFeedback) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { handleAnswer(false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(
                            text = "NO",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { handleAnswer(true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.yes),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (gameCompleted) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${stringResource(R.string.game_complete)}",
                        color = Color(0xFFFFD700),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${stringResource(R.string.final_score)}: $totalScore",
                        color = Color.White,
                        fontSize = 18.sp
                    )

                    if (taskSwitchAccuracy.isNotEmpty()) {
                        val evenAcc = taskSwitchAccuracy["even"] ?: 0
                        val vowelAcc = taskSwitchAccuracy["vowel"] ?: 0
                        Text(
                            text = "Even: $evenAcc • Vowel: $vowelAcc",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }

    // ✅ NEW: Session completion handling
    if (gameCompleted || currentHearts <= 0 || currentRound >= adaptiveConfig.totalRounds) {
        UnifiedSessionCompletionHandler(
            puzzleType = "dualCardTask",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAttempts,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = bestStreak,
                winRate = if (totalAttempts > 0) correctAnswers.toFloat() / totalAttempts else 0f,
                totalScore = totalScore, // Fixed: Use the accumulated total score
                averageTimePerPuzzle = if (reactionTimes.isNotEmpty()) { // Fixed: Calculate average reaction time
                    (reactionTimes.average() / 1000).toInt()
                } else {
                    0
                },
                currentStreak = currentStreak, // Fixed: Use the current streak value
                individualTimes = reactionTimes.map { (it / 1000).toInt() } // Fixed: Convert reaction times to seconds
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            // Session completion handled
        }
    }
}

@Composable
private fun AdaptiveDualCardTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    totalScore: Int,
    correctAnswers: Int,
    totalAttempts: Int,
    currentStreak: Int,
    currentRound: Int,
    totalRounds: Int,
    onBack: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Back button and level
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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

                Column {
                    Text(
                        text = "Level ${level.level}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    LevelProgressBar(
                        level = level,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }

            // Center: Lives and timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(currentDifficulty.livesAllowed) { index ->
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (index < lives) Color(0xFFFFD700) else Color(0xFF666666),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                val timeValue = timer.substringAfter(":").toIntOrNull() ?: 0
                val isUrgent = timer.startsWith("0:") && timeValue <= 30

                Text(
                    text = timer,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUrgent) Color.Red else Color.White
                )
            }

            // Right side: Progress and streak
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "$currentRound/$totalRounds",
                    fontSize = 14.sp,
                    color = Color.White
                )

                if (currentStreak > 1) {
                    Text(
                        text = "🔥 $currentStreak",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B00)
                    )
                }
            }
        }

        // Score and progress display
        if (totalScore > 0 || totalAttempts > 0) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (totalScore > 0) {
                    Text(
                        text = "${stringResource(R.string.score_label)}: $totalScore",
                        color = Color(0xFFFFD700),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "🧠 ${currentDifficulty.name}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                if (totalAttempts > 0) {
                    Text(
                        text = "$correctAnswers/$totalAttempts",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// Configuration data class
data class AdaptiveDualCardConfig(
    val totalRounds: Int,
    val complexityLevel: Int,
    val switchingPattern: String, // "alternating", "random", "predictable"
    val cardComplexity: Int,
    val name: String
)

// Card data class
data class AdaptiveCardData(
    val letter: Char,
    val digit: Int,
    val isLetterFirst: Boolean,
    val textColor: Color
)

// Generate adaptive configuration
fun generateAdaptiveDualCardConfig(difficulty: DifficultyManager.DifficultyLevel): AdaptiveDualCardConfig {
    return when (difficulty.index) {
        0 -> AdaptiveDualCardConfig( // Beginner
            totalRounds = 8,
            complexityLevel = 1,
            switchingPattern = "predictable",
            cardComplexity = 1,
            name = "Beginner"
        )
        1 -> AdaptiveDualCardConfig( // Easy
            totalRounds = 10,
            complexityLevel = 2,
            switchingPattern = "alternating",
            cardComplexity = 2,
            name = "Easy"
        )
        2 -> AdaptiveDualCardConfig( // Medium
            totalRounds = 15,
            complexityLevel = 3,
            switchingPattern = "alternating",
            cardComplexity = 3,
            name = "Medium"
        )
        3 -> AdaptiveDualCardConfig( // Hard
            totalRounds = 20,
            complexityLevel = 4,
            switchingPattern = "random",
            cardComplexity = 4,
            name = "Hard"
        )
        4 -> AdaptiveDualCardConfig( // Expert
            totalRounds = 25,
            complexityLevel = 5,
            switchingPattern = "random",
            cardComplexity = 5,
            name = "Expert"
        )
        else -> AdaptiveDualCardConfig(
            totalRounds = 15,
            complexityLevel = 3,
            switchingPattern = "alternating",
            cardComplexity = 3,
            name = "Medium"
        )
    }
}

// Generate adaptive card data
fun generateAdaptiveCardData(config: AdaptiveDualCardConfig): AdaptiveCardData {
    val letters = when (config.cardComplexity) {
        1 -> "AEIOU" // Only vowels for easier recognition
        2 -> "AEIOUBC" // Mix with some consonants
        3 -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ" // Full alphabet
        4 -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ" // Full alphabet with more variety
        5 -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ" // Full alphabet maximum complexity
        else -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    }

    val digitRange = when (config.cardComplexity) {
        1 -> 0..5 // Smaller numbers
        2 -> 0..7
        3 -> 0..9 // Full range
        4 -> 0..9
        5 -> 0..9
        else -> 0..9
    }

    val colors = when (config.cardComplexity) {
        1 -> listOf(Color.Black) // Single color
        2 -> listOf(Color.Black, Color.Blue) // Two colors
        3 -> listOf(Color.Black, Color.Blue, Color.Red) // Three colors
        4 -> listOf(Color.Black, Color.Blue, Color.Red, Color.Green) // Four colors
        5 -> listOf( // Full color variety
            Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF4CAF50),
            Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFFFF5722),
            Color.Black
        )
        else -> listOf(Color.Black)
    }

    return AdaptiveCardData(
        letter = letters.random(),
        digit = digitRange.random(),
        isLetterFirst = Random.nextBoolean(),
        textColor = colors.random()
    )
}

// Check adaptive answer
fun checkAdaptiveAnswer(
    isCheckingTopCard: Boolean,
    currentCardData: AdaptiveCardData,
    userAnsweredYes: Boolean,
    config: AdaptiveDualCardConfig
): Boolean {
    return if (isCheckingTopCard) {
        // Check if digit is even
        val isEven = currentCardData.digit % 2 == 0
        isEven == userAnsweredYes
    } else {
        // Check if letter is vowel
        val vowels = setOf('A', 'E', 'I', 'O', 'U')
        val isVowel = currentCardData.letter in vowels
        isVowel == userAnsweredYes
    }
}
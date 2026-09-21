// AdaptiveColorShapeMatchingPuzzleScreen.kt
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveColorShapeMatchingPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveColorShape"

    var showHint by remember { mutableStateOf(false) }

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("colorShapeMatching"))
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
                    puzzleType = "colorShapeMatching",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }


    // Generate adaptive configuration
    val adaptiveConfig = remember(currentDifficultyLevel) {
        generateAdaptiveColorShapeConfig(currentDifficultyLevel)
    }

    var timeLeft by remember { mutableStateOf(currentDifficultyLevel.timeLimit) }
    var isPaused by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var gameCompleted by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(true) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var feedbackColor by remember { mutableStateOf(Color.Red) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // Game state
    var currentChallenge by remember { mutableStateOf(generateAdaptiveChallenge(adaptiveConfig)) }
    var challengeNumber by remember { mutableStateOf(1) }
    var totalScore by remember { mutableStateOf(0) }
    var challengeStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAnswers by remember { mutableStateOf(0) }



    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    val totalChallenges = adaptiveConfig.totalChallenges

    val haptics = LocalHapticFeedback.current
    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            val newConfig = generateAdaptiveColorShapeConfig(currentDifficultyLevel)
            currentChallenge = generateAdaptiveChallenge(newConfig)
            challengeNumber = 1
            currentHearts = currentDifficultyLevel.livesAllowed
            timeLeft = currentDifficultyLevel.timeLimit
            challengeStartTime = System.currentTimeMillis()
        }
    }

    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = adaptiveConfig.complexityLevel,
            totalScore = totalScore,
            puzzleType = "colorShapeMatching"
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
            challengeComplexity = adaptiveConfig.complexityLevel,
            currentStreak = currentStreak,
            challengesCompleted = challengeNumber,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "colorShapeMatching"
        )
    }

    // Enhanced score calculation for adaptive difficulty
    fun calculateAdaptiveColorShapeScore(
        isCorrect: Boolean,
        timeSpent: Long,
        difficulty: DifficultyManager.DifficultyLevel,
        challengeComplexity: Int,
        currentStreak: Int,
        challengesCompleted: Int
    ): Int {
        if (!isCorrect) return 0

        val basePoints = difficulty.basePoints

        // Complexity multiplier based on challenge type
        val complexityMultiplier = 1f + (challengeComplexity - 1) * 0.2f

        // Speed bonus
        val expectedTime = currentDifficultyLevel.timeLimit / totalChallenges.toFloat()
        val timeSpentSeconds = timeSpent / 1000f
        val speedBonus = when {
            timeSpentSeconds <= expectedTime * 0.3f -> (basePoints * 0.3f).toInt()
            timeSpentSeconds <= expectedTime * 0.5f -> (basePoints * 0.2f).toInt()
            timeSpentSeconds <= expectedTime * 0.7f -> (basePoints * 0.1f).toInt()
            else -> 0
        }

        // Streak bonus
        val streakMultiplier = 1f + (currentStreak * 0.1f)

        // Visual processing bonus
        val visualBonus = (basePoints * 0.2f * difficulty.complexityMultiplier).toInt()

        val finalScore = ((basePoints * complexityMultiplier * streakMultiplier).toInt() +
                speedBonus + visualBonus)

        Log.d(TAG, "🏆 Adaptive color shape score:")
        Log.d(TAG, "  Difficulty: ${difficulty.name}")
        Log.d(TAG, "  Complexity: $challengeComplexity")
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
        challengeComplexity: Int,
        onAdaptation: (DifficultyManager.AdaptiveConfig) -> Unit
    ) {
        val performance = DifficultyManager.PlayerPerformance(
            accuracy = if (isCorrect) 1f else 0f,
            averageTime = timeSpent / 1000f,
            streakLength = streak,
            livesRemaining = livesRemaining,
            gameScore = totalScore,
            difficulty = difficulty.name,
            puzzleType = "colorShapeMatching"
        )

        val adaptiveConfig = difficultyManager.recordPerformance(performance)
        onAdaptation(adaptiveConfig)
    }

    fun handleAnswer(userAnsweredYes: Boolean) {
        val timeSpent = System.currentTimeMillis() - challengeStartTime
        val isCorrect = userAnsweredYes == currentChallenge.isCorrectMatch

        totalAnswers++
        if (isCorrect) {
            correctAnswers++
            currentStreak++
            val score = calculateScore(isCorrect, timeSpent)
            totalScore += score
        } else {
            currentStreak = 0
            currentHearts = maxOf(0, currentHearts - 1)
        }

        recordPerformance(isCorrect, timeSpent)
        showFeedback = true
    }

    var sessionResult by remember { mutableStateOf<AdaptiveSessionResult?>(null) }

    if (challengeNumber > totalChallenges || currentHearts <= 0) {
        UnifiedSessionCompletionHandler(
            puzzleType = "colorShapeMatching",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAnswers,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
                totalScore = totalScore,
                averageTimePerPuzzle = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()/5,
                currentStreak = currentStreak,
                individualTimes = emptyList()
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            sessionResult = result
            onGameComplete(challengeNumber > totalChallenges, totalScore)
        }
    }



    // Auto-advance effect after showing feedback
    LaunchedEffect(showFeedback) {
        if (showFeedback) {
            delay(1500L)
            showFeedback = false

            if (currentHearts == 0) {
                gameCompleted = true
                onGameComplete(false, totalScore)
            } else if (challengeNumber >= totalChallenges) {
                gameCompleted = true
                onGameComplete(true, totalScore)
            } else {
                challengeNumber++
                currentChallenge = generateAdaptiveChallenge(adaptiveConfig)
                challengeStartTime = System.currentTimeMillis()
            }
        }
    }

    // Timer countdown
    LaunchedEffect(gameStarted, isPaused, gameCompleted) {
        if (gameStarted && !isPaused && !gameCompleted) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0) {
                // Time's up - record poor performance
                recordAdaptivePerformance(
                    difficultyManager = difficultyManager,
                    isCorrect = false,
                    timeSpent = currentDifficultyLevel.timeLimit * 1000L,
                    streak = 0,
                    livesRemaining = 0,
                    difficulty = currentDifficultyLevel,
                    challengeComplexity = adaptiveConfig.complexityLevel
                ) { config ->
                    adaptationInfo = config
                    if (config.confidenceScore > 0.5f) {
                        currentDifficultyLevel = config.level
                        showAdaptationNotification = SHOW_ADAPTATION_NOTICES
                    }
                }

                gameCompleted = true
                onGameComplete(false, totalScore)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RvSurface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Enhanced header with adaptive difficulty info
        AdaptiveUnifiedHeader(
            level = currentLevel,
            streakInfo = streakInfo,
            timer = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
            lives = currentHearts,
            currentDifficulty = currentDifficultyLevel,
            score = totalScore,
            puzzleType = "colorShapeMatching",
            challengeNumber = challengeNumber,
            totalChallenges = totalChallenges,
            competitiveInsight = competitiveInsight,
            onBack = onBack,
            onPause = { isPaused = !isPaused },
            onHint = {
                showHint = !showHint
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        UnifiedAdaptationNotification(
            adaptationInfo = adaptationInfo,
            puzzleType = "colorShapeMatching",
            visible = showAdaptationNotification,
            onDismiss = { showAdaptationNotification = false }
        )

        // Adaptive difficulty notification
        AnimatedVisibility(
            visible = showAdaptationNotification,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = RvSky.copy(alpha = 0.9f)
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
                        contentDescription = stringResource(R.string.difficulty_advanced),
                        tint = RvInk,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Visual Challenge Adapted!",
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
                            contentDescription = stringResource(R.string.close),
                            tint = RvInk,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress indicator
        LinearProgressIndicator(
            progress = challengeNumber.toFloat() / totalChallenges,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = RvSuccess,
            trackColor = RvOutline
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Instructions
        Text(
            text = "Does the text meaning match the shape color?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = RvInk
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Color name text (if present)
        currentChallenge.colorNameText?.let { colorText ->
            Text(
                text = colorText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = currentChallenge.colorNameFontColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Shape display
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(RvSurfaceRaised, RoundedCornerShape(16.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            ShapeDisplay(
                shapeType = currentChallenge.shapeType,
                color = currentChallenge.shapeColor,
                modifier = Modifier.size(120.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Answer buttons
        if (!showFeedback) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // No button
                Button(
                    onClick = { handleAnswer(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RvError
                    ),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("✗", fontSize = 24.sp, color = RvInk)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NO",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Yes button
                Button(
                    onClick = { handleAnswer(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RvSuccess
                    ),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("✓", fontSize = 24.sp, color = RvInk)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.yes),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                    }
                }
            }
        }

        // Feedback display
        if (showFeedback) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = feedbackColor.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = feedbackMessage,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = feedbackColor,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Score: $totalScore",
                        fontSize = 14.sp,
                        color = RvInkSoft
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AdaptiveColorShapeTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    score: Int,
    challengeNumber: Int,
    totalChallenges: Int,
    onBack: () -> Unit,
    onPause: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top row: Back button, Level, Hearts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(RvSurface, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = RvInk,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Level ${level.level}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Challenge $challengeNumber of $totalChallenges",
                        fontSize = 12.sp,
                        color = RvInkSoft
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(currentDifficulty.livesAllowed) { index ->
                        Text(
                            text = if (index < lives) "❤️" else "🤍",
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: Difficulty, Timer, and Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentDifficulty.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvSky
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

                Text(
                    text = "Score: $score",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvSuccess
                )
            }
        }
    }
}

// Configuration data class
data class AdaptiveColorShapeConfig(
    val totalChallenges: Int,
    val complexityLevel: Int,
    val colorDistractorChance: Float,
    val name: String
)

// Generate adaptive configuration
fun generateAdaptiveColorShapeConfig(difficulty: DifficultyManager.DifficultyLevel): AdaptiveColorShapeConfig {
    return when (difficulty.index) {
        0 -> AdaptiveColorShapeConfig( // Beginner
            totalChallenges = 8,
            complexityLevel = 1,
            colorDistractorChance = 0.3f,
            name = "Beginner"
        )
        1 -> AdaptiveColorShapeConfig( // Easy
            totalChallenges = 10,
            complexityLevel = 2,
            colorDistractorChance = 0.4f,
            name = "Easy"
        )
        2 -> AdaptiveColorShapeConfig( // Medium
            totalChallenges = 12,
            complexityLevel = 3,
            colorDistractorChance = 0.5f,
            name = "Medium"
        )
        3 -> AdaptiveColorShapeConfig( // Hard
            totalChallenges = 15,
            complexityLevel = 4,
            colorDistractorChance = 0.6f,
            name = "Hard"
        )
        4 -> AdaptiveColorShapeConfig( // Expert
            totalChallenges = 20,
            complexityLevel = 5,
            colorDistractorChance = 0.7f,
            name = "Expert"
        )
        else -> AdaptiveColorShapeConfig(
            totalChallenges = 12,
            complexityLevel = 3,
            colorDistractorChance = 0.5f,
            name = "Medium"
        )
    }
}

// Challenge generation
fun generateAdaptiveChallenge(config: AdaptiveColorShapeConfig): ColorShapeChallenge {
    val colors = listOf(
        Color.Red to "RED",
        Color.Blue to "BLUE",
        Color.Green to "GREEN",
        Color.Yellow to "YELLOW",
        Color.Magenta to "PURPLE",
        RvSun to "ORANGE",
        Color(0xFFFFC0CB) to "PINK",
        Color(0xFF8B4513) to "BROWN"
    )

    val shapes = ShapeType.values()

    val shapeColorPair = colors.random()
    val shapeColor = shapeColorPair.first
    val shapeColorName = shapeColorPair.second
    val shapeType = shapes.random()

    // Generate challenge based on complexity level
    val scenario = when (config.complexityLevel) {
        1 -> 0 // Always matches for beginners
        2 -> Random.nextInt(2) // 50/50 for easy
        else -> Random.nextInt(3) // Full complexity for medium+
    }

    return when (scenario) {
        0 -> {
            // Correct color name in correct font color (MATCH)
            ColorShapeChallenge(
                shapeColor = shapeColor,
                shapeType = shapeType,
                colorNameText = shapeColorName,
                colorNameFontColor = shapeColor,
                isCorrectMatch = true
            )
        }
        1 -> {
            // Correct color name in wrong font color (STILL A MATCH - text content matters)
            val wrongFontColor = colors.filter { it.first != shapeColor }.random().first
            ColorShapeChallenge(
                shapeColor = shapeColor,
                shapeType = shapeType,
                colorNameText = shapeColorName,
                colorNameFontColor = wrongFontColor,
                isCorrectMatch = true
            )
        }
        else -> {
            // Wrong color name (NO MATCH)
            val wrongColorName = colors.filter { it.second != shapeColorName }.random().second
            val fontColor = colors.random().first
            ColorShapeChallenge(
                shapeColor = shapeColor,
                shapeType = shapeType,
                colorNameText = wrongColorName,
                colorNameFontColor = fontColor,
                isCorrectMatch = false
            )
        }
    }
}
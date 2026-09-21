// AdaptiveTriangleDotMemoryPuzzleScreen.kt
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdaptiveTriangleDotMemoryPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String,
    hearts: Int = 3,
    level: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveTriangleDot"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("triangleDot"))
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
                    puzzleType = "triangleDot",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate adaptive puzzle data
    val puzzleData = remember(currentDifficultyLevel) {
        generateAdaptiveTriangleDotPuzzleData(currentDifficultyLevel)
    }

    // Generate answer data
    val answerData = remember(puzzleData) {
        generateAdaptiveTriangleDotAnswers(puzzleData)
    }

    // Game state
    var gameState by remember { mutableStateOf("instructions") }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var currentScore by remember { mutableStateOf(0) }
    var totalScore by remember { mutableStateOf(0) }
    var showFeedback by remember { mutableStateOf(false) }
    var lastAnswerCorrect by remember { mutableStateOf(false) }
    var userAnswers by remember { mutableStateOf(mutableListOf<Int>()) }
    var skipFirstComparisonDone by remember { mutableStateOf(false) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // ✅ NEW: Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAnswers by remember { mutableStateOf(0) }

    // Adaptive timing
    var timeRemaining by remember { mutableStateOf(currentDifficultyLevel.timeLimit) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            gameState = "playing"
            currentQuestionIndex = 0
            currentScore = 0
            userAnswers.clear()
            skipFirstComparisonDone = false
            currentHearts = currentDifficultyLevel.livesAllowed
            timeRemaining = currentDifficultyLevel.timeLimit
        }
    }

    // ✅ UPDATED: Enhanced score calculation
    fun calculateScore(
        isCorrect: Boolean,
        questionIndex: Int,
        timeRemaining: Int
    ): Int {
        return calculateUnifiedAdaptiveScore(
            isCorrect = isCorrect,
            timeSpent = (currentDifficultyLevel.timeLimit - timeRemaining) * 1000L,
            difficulty = currentDifficultyLevel,
            challengeComplexity = questionIndex + 1, // Complexity increases with question number
            currentStreak = currentStreak,
            challengesCompleted = questionIndex + 1,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "triangledotmemory"
        )
    }

    // ✅ UPDATED: Enhanced performance recording
    fun recordPerformance(
        correctCount: Int,
        totalQuestions: Int,
        avgResponseTime: Float,
        streak: Int,
        livesRemaining: Int
    ) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = correctCount > 0,
            timeSpent = (avgResponseTime * 1000).toLong(),
            streak = streak,
            livesRemaining = livesRemaining,
            difficulty = currentDifficultyLevel,
            challengeComplexity = totalQuestions,
            totalScore = totalScore,
            puzzleType = "triangledotmemory"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = SHOW_ADAPTATION_NOTICES
            }
        }
    }

    LaunchedEffect(gameState, currentQuestionIndex) {
        if (gameState == "playing" && currentQuestionIndex == 0 && !skipFirstComparisonDone) {
            delay(2000) // Show first triangle for 2 seconds
            currentQuestionIndex++
            skipFirstComparisonDone = true
        }

        if (gameState == "playing" && skipFirstComparisonDone) {
            while (timeRemaining > 0 && gameState == "playing") {
                delay(1000)
                timeRemaining--
            }
            if (timeRemaining <= 0) {
                gameState = "completed"
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(RvCanvas)) {
    val compact = groupEIsCompact(maxWidth, maxHeight)
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .widthIn(max = 720.dp)
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
    ) {
        val timerText = String.format("%d:%02d", timeRemaining / 60, timeRemaining % 60)
        if (compact) {
            GroupECompactHud(
                timer = timerText,
                onBack = onBack,
                subtitle = "${currentDifficultyLevel.name} \u2022 ${stringResource(R.string.score_label)} $totalScore",
                lives = currentHearts,
                urgent = timeRemaining <= 10 && gameState == "playing"
            )
        } else {
            // ✅ REPLACE: Use unified header instead of AdaptiveTriangleDotTopBar
            Box(modifier = Modifier.testTag("hud_timer")) { AdaptiveUnifiedHeader(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = timerText,
                lives = currentHearts,
                currentDifficulty = currentDifficultyLevel,
                score = totalScore,
                puzzleType = "triangledotmemory",
                competitiveInsight = competitiveInsight,
                onBack = onBack,
                challengeNumber = currentQuestionIndex + 1, // Fixed: Current question number
                totalChallenges = puzzleData.totalQuestions, // Fixed: Total questions in sequence
                onPause = { /* Game can be paused by going back */ }, // Fixed: Optional pause functionality
                onHint = { /* No hints for memory games */ } // Fixed: No hints for pure memory tasks
            ) }
        }

        // ✅ REPLACE: Use unified adaptation notification
        UnifiedAdaptationNotification(
            adaptationInfo = adaptationInfo,
            puzzleType = "triangledotmemory",
            visible = showAdaptationNotification,
            onDismiss = { showAdaptationNotification = false }
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (gameState) {
            "instructions" -> {
                AdaptiveTriangleDotInstructionsScreen(
                    instructions = puzzleData.instructions,
                    difficulty = currentDifficultyLevel,
                    onStartGame = {
                        gameState = "playing"
                        sessionStartTime = System.currentTimeMillis()
                        Log.d(TAG, "Starting adaptive triangle dot memory game with ${puzzleData.totalQuestions} questions")
                    }
                )
            }

            "playing" -> {
                AdaptiveTriangleDotGameScreen(
                    currentQuestion = currentQuestionIndex + 1,
                    totalQuestions = puzzleData.totalQuestions,
                    currentStep = puzzleData.sequence[currentQuestionIndex],
                    previousStep = if (currentQuestionIndex == 0) puzzleData.sequence[0] else puzzleData.sequence[currentQuestionIndex - 1],
                    showFeedback = showFeedback,
                    lastAnswerCorrect = lastAnswerCorrect,
                    difficulty = currentDifficultyLevel,
                    onAnswer = { isSame ->
                        if (currentQuestionIndex < 1) return@AdaptiveTriangleDotGameScreen

                        val userAnswer = if (isSame) 1 else 0
                        val correctAnswerIndex = currentQuestionIndex - 1
                        val correctAnswer = answerData.correctAnswers.getOrNull(correctAnswerIndex) ?: 0
                        val isCorrect = userAnswer == correctAnswer

                        Log.d(TAG, "Question ${currentQuestionIndex}: User answered $userAnswer, correct was $correctAnswer")

                        userAnswers.add(userAnswer)
                        lastAnswerCorrect = isCorrect
                        totalAnswers++

                        if (isCorrect) {
                            correctAnswers++
                            currentStreak++
                            val questionScore = calculateScore(
                                isCorrect = true,
                                questionIndex = currentQuestionIndex,
                                timeRemaining = timeRemaining
                            )
                            currentScore += questionScore
                            totalScore += questionScore
                        } else {
                            currentStreak = 0
                            currentHearts = maxOf(0, currentHearts - 1)

                            if (currentHearts <= 0) {
                                gameState = "completed"
                                gamesPlayedThisSession++

                                // Record poor performance
                                recordPerformance(
                                    correctCount = correctAnswers,
                                    totalQuestions = currentQuestionIndex,
                                    avgResponseTime = 3.0f, // Penalty time
                                    streak = 0,
                                    livesRemaining = 0
                                )
                                return@AdaptiveTriangleDotGameScreen
                            }
                        }

                        showFeedback = true

                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1500)
                            showFeedback = false

                            if (currentQuestionIndex < puzzleData.totalQuestions - 1) {
                                currentQuestionIndex++
                            } else {
                                gameState = "completed"
                                gamesPlayedThisSession++

                                // Record successful completion
                                recordPerformance(
                                    correctCount = correctAnswers,
                                    totalQuestions = puzzleData.totalQuestions,
                                    avgResponseTime = 2.0f, // Good response time
                                    streak = currentStreak,
                                    livesRemaining = currentHearts
                                )
                            }
                        }
                    }
                )
            }

            "completed" -> {
                AdaptiveTriangleDotCompletionScreen(
                    score = currentScore,
                    totalScore = totalScore,
                    maxScore = answerData.maxScore,
                    totalQuestions = puzzleData.totalQuestions,
                    correctCount = correctAnswers,
                    difficulty = currentDifficultyLevel,
                    streak = currentStreak,
                    onContinue = {
                        val finalScore = (currentScore.toDouble() / answerData.maxScore.toDouble()) * 100
                        onSubmitAnswer(finalScore >= 60)
                        fetchNextPuzzle(totalScore)
                    }
                )
            }
        }
    }

    // ✅ NEW: Session completion handling outside of game state
    if (gameState == "completed") {
        UnifiedSessionCompletionHandler(
            puzzleType = "triangleDot",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAnswers,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
                totalScore = totalScore, // Fixed: Use the accumulated total score
                averageTimePerPuzzle = if (totalAnswers > 0) { // Fixed: Calculate average time per question
                    ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt() / totalAnswers
                } else {
                    0
                },
                currentStreak = currentStreak, // Fixed: Use the current streak value
                individualTimes = if (totalAnswers > 0) { // Fixed: Estimate individual times
                    List(totalAnswers) {
                        ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt() / totalAnswers
                    }
                } else {
                    emptyList()
                }
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            // Session completion handled
        }
    }
    }
}

@Composable
fun AdaptiveTriangleDotTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    score: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pause button and level info
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(RvSky)
                    .clickable { onBack() }
                    .zIndex(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("||", color = RvInk, fontWeight = FontWeight.Bold)
            }

            Column {
                Text(
                    text = "Level ${level.level}",
                    color = RvInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = currentDifficulty.name.uppercase(),
                    color = RvSkyEdge,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Timer and Score
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(RvSurface)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "TIME $timer",
                    color = RvInk,
                    fontWeight = FontWeight.Bold
                )
            }

            if (score > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RvSurface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "SCORE $score",
                        color = RvInk,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Lives display with adaptive count
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(currentDifficulty.livesAllowed) { index ->
            Text(
                text = if (index < lives) "❤️" else "🤍",
                fontSize = 20.sp
            )
            if (index < currentDifficulty.livesAllowed - 1) Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
private fun AdaptiveTriangleDotInstructionsScreen(
    instructions: TriangleDotInstructions,
    difficulty: DifficultyManager.DifficultyLevel,
    onStartGame: () -> Unit
) {
    GroupETriangleInstructionsBody(
        instructions = instructions,
        extra = "\u26A1 Difficulty: ${difficulty.name} \u2022 Time Limit: ${difficulty.timeLimit}s",
        onStart = onStartGame
    )
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AdaptiveTriangleDotGameScreen(
    currentQuestion: Int,
    totalQuestions: Int,
    currentStep: TriangleDotStep,
    previousStep: TriangleDotStep,
    showFeedback: Boolean,
    lastAnswerCorrect: Boolean,
    difficulty: DifficultyManager.DifficultyLevel,
    onAnswer: (Boolean) -> Unit
) {
    GroupETriangleGameBody(
        currentQuestion = currentQuestion,
        totalQuestions = totalQuestions,
        redDotPosition = currentStep.redDotPosition,
        showFeedback = showFeedback,
        lastAnswerCorrect = lastAnswerCorrect,
        extraLabel = difficulty.name,
        onAnswer = onAnswer
    )
}

@Composable
private fun AdaptiveTriangleDotCompletionScreen(
    score: Int,
    totalScore: Int,
    maxScore: Int,
    totalQuestions: Int,
    correctCount: Int,
    difficulty: DifficultyManager.DifficultyLevel,
    streak: Int,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
            text = "Memory Challenge Complete!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = RvInk,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RvSurface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.final_results),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$score",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                        Text(
                            text = stringResource(R.string.session_score),
                            fontSize = 14.sp,
                            color = RvInk
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalScore",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                        Text(
                            text = stringResource(R.string.total_score),
                            fontSize = 14.sp,
                            color = RvInk
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$correctCount / $totalQuestions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                        Text(
                            text = stringResource(R.string.correct_answers),
                            fontSize = 14.sp,
                            color = RvInk
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val percentage = (correctCount.toDouble() / totalQuestions.toDouble() * 100).toInt()
                        Text(
                            text = "$percentage%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                        Text(
                            text = stringResource(R.string.accuracy),
                            fontSize = 14.sp,
                            color = RvInk
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Difficulty: ${difficulty.name}",
                    fontSize = 14.sp,
                    color = RvInkSoft,
                    fontWeight = FontWeight.Medium
                )

                if (streak > 0) {
                    Text(
                        text = "${stringResource(R.string.best_streak)}: $streak",
                        fontSize = 14.sp,
                        color = RvInk,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

      }

        Spacer(modifier = Modifier.height(8.dp))

        GroupEPrimaryButton(
            text = stringResource(R.string.continue_label_caps),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Helper functions to generate adaptive puzzle data
private fun generateAdaptiveTriangleDotPuzzleData(difficulty: DifficultyManager.DifficultyLevel): TriangleDotPuzzleData {
    val totalQuestions = when (difficulty.index) {
        0 -> 6   // Beginner
        1 -> 8   // Easy
        2 -> 12  // Medium
        3 -> 16  // Hard
        4 -> 20  // Expert
        else -> 8
    }

    val sequence = mutableListOf<TriangleDotStep>()

    // Generate random sequence
    for (i in 0 until totalQuestions) {
        sequence.add(
            TriangleDotStep(
                step = i + 1,
                redDotPosition = (0..2).random(),
                isFirstStep = i == 0
            )
        )
    }

    val instructions = TriangleDotInstructions(
        title = "Adaptive Triangle Dot Memory",
        description = "Remember the position of the red dot in each triangle pattern and compare it to the previous one.",
        steps = listOf(
            "Watch each triangle pattern carefully",
            "Note the position of the red dot (top, bottom-left, or bottom-right)",
            "Compare the current pattern to the previous one",
            "Tap YES if the red dot is in the same position",
            "Tap NO if the red dot moved to a different position"
        ),
        tip = "Focus on the red dot's position rather than the orange dots"
    )

    return TriangleDotPuzzleData(
        sequence = sequence,
        instructions = instructions,
        totalSteps = totalQuestions,
        totalQuestions = totalQuestions - 1, // -1 because first step is just shown
        difficulty = difficulty.name
    )
}

private fun generateAdaptiveTriangleDotAnswers(puzzleData: TriangleDotPuzzleData): TriangleDotAnswerData {
    val correctAnswers = mutableListOf<Int>()

    // Generate correct answers based on sequence
    for (i in 1 until puzzleData.sequence.size) {
        val currentPos = puzzleData.sequence[i].redDotPosition
        val previousPos = puzzleData.sequence[i - 1].redDotPosition

        // 1 if same position, 0 if different
        correctAnswers.add(if (currentPos == previousPos) 1 else 0)
    }

    return TriangleDotAnswerData(
        correctAnswers = correctAnswers,
        totalQuestions = puzzleData.totalQuestions,
        maxScore = puzzleData.totalQuestions * 15 // 15 points per correct answer
    )
}
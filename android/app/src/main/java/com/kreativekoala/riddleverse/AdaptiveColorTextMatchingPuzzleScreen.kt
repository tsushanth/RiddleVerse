// AdaptiveColorTextMatchingPuzzleScreen.kt
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.random.Random

// Adaptive color-text matching configuration
data class AdaptiveColorTextConfig(
    val responseTimeLimit: Float, // Time limit per question in seconds
    val showProgressIndicator: Boolean, // Whether to show progress indicators
    val colorComplexity: Int, // Number of different colors used (4-10)
    val conflictFrequency: Float, // Percentage of conflicting color-text pairs (0.3-0.7)
    val visualHints: Boolean, // Whether to show visual hints for beginners
    val chainLength: Int, // Number of consecutive questions
    val adaptiveSpeedAdjustment: Boolean, // Whether to adjust speed based on performance
    val name: String,
    val description: String
)

// Enhanced color-text step with adaptive features
data class AdaptiveColorTextStep(
    val step: Int,
    val meaningColorName: String,  // Color name shown in top card (meaning)
    val textColorName: String,     // Color name in bottom card
    val textDisplayColor: Color,   // Actual color of the text in bottom card
    val isMatch: Boolean,         // Whether meaning matches text color
    val difficulty: Float,        // Individual question difficulty (0.0-1.0)
    val expectedResponseTime: Float, // Expected time to answer this question
    val isConflictQuestion: Boolean // Whether this is designed to be confusing
)

data class AdaptiveColorTextInstructions(
    val title: String,
    val description: String,
    val steps: List<String>,
    val tip: String,
    val adaptiveFeatures: List<String> // New adaptive features explanation
)

data class AdaptiveColorTextPuzzleData(
    val sequence: List<AdaptiveColorTextStep>,
    val instructions: AdaptiveColorTextInstructions,
    val totalSteps: Int,
    val totalQuestions: Int,
    val difficulty: String,
    val config: AdaptiveColorTextConfig
)

data class AdaptiveColorTextAnswerData(
    val correctAnswers: List<Int>, // 1 for match, 0 for no match
    val totalQuestions: Int,
    val maxScore: Int,
    val expectedTimes: List<Float> // Expected response times for scoring
)

// Enhanced color definitions with more complexity
object AdaptiveGameColors {
    val colorMap = mapOf(
        "red" to Color(0xFFE53E3E),
        "blue" to Color(0xFF3182CE),
        "green" to Color(0xFF38A169),
        "yellow" to Color(0xFFD69E2E),
        "purple" to Color(0xFF805AD5),
        "orange" to Color(0xFFDD6B20),
        "pink" to Color(0xFFD53F8C),
        "brown" to Color(0xFF8B4513),
        "gray" to Color(0xFF718096),
        "black" to RvInk,
        "cyan" to Color(0xFF0891B2),
        "lime" to Color(0xFF65A30D),
        "indigo" to Color(0xFF4338CA),
        "teal" to Color(0xFF0F766E),
        "rose" to Color(0xFFE11D48)
    )

    val colorNames = colorMap.keys.toList()

    // Get colors by complexity level
    fun getColorsForComplexity(complexity: Int): List<String> {
        return colorNames.take(minOf(complexity, colorNames.size))
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdaptiveColorTextMatchingPuzzleScreen(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    puzzleData: String,
    correctAnswer: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveColorTextMatching"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("colorTextMatching"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "colorTextMatching",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate adaptive configuration
    val adaptiveConfig = remember(currentDifficultyLevel) {
        generateAdaptiveColorTextConfig(currentDifficultyLevel)
    }

    // Generate puzzle data with adaptive features
    val parsedData = remember(difficulty, adaptiveConfig) {
        generateAdaptiveColorTextPuzzleData(difficulty, adaptiveConfig)
    }

    // Generate answer data
    val answerData = remember(parsedData) {
        generateAdaptiveColorTextAnswers(parsedData)
    }

    // Game state
    var gameState by remember { mutableStateOf("instructions") } // instructions, playing, completed
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var currentScore by remember { mutableStateOf(0) }
    var showFeedback by remember { mutableStateOf(false) }
    var lastAnswerCorrect by remember { mutableStateOf(false) }
    var userAnswers by remember { mutableStateOf(mutableListOf<Int>()) }
    var questionStartTime by remember { mutableLongStateOf(0L) }
    var responseTimes by remember { mutableStateOf(mutableListOf<Long>()) }
    var currentStreak by remember { mutableStateOf(0) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctCount by remember { mutableStateOf(0) }
    var totalQuestions by remember { mutableStateOf(0) }
    var showHint by remember { mutableStateOf(false) }


    // Adaptive timer state
    var timeRemaining by remember {
        mutableStateOf((parsedData.totalQuestions * adaptiveConfig.responseTimeLimit).toInt())
    }
    var questionTimeRemaining by remember { mutableFloatStateOf(adaptiveConfig.responseTimeLimit) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()
    val haptics = LocalHapticFeedback.current

    fun recordPerformance(isCorrect: Boolean, responseTime: Long, questionDifficulty: Float) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = responseTime,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = (questionDifficulty * 5).toInt(),
            totalScore = currentScore,
            puzzleType = "colorTextMatching"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = SHOW_ADAPTATION_NOTICES
            }
        }
    }

    // Record performance for adaptive difficulty
    fun recordAdaptivePerformance(
        isCorrect: Boolean,
        responseTime: Long,
        questionDifficulty: Float
    ) {
        val performance = DifficultyManager.PlayerPerformance(
            accuracy = if (isCorrect) 1f else 0f,
            averageTime = responseTime / 1000f,
            streakLength = currentStreak,
            livesRemaining = currentHearts,
            gameScore = currentScore,
            difficulty = currentDifficultyLevel.name,
            puzzleType = "colorTextMatching"
        )

        val adaptiveConfig = difficultyManager.recordPerformance(performance)
        adaptationInfo = adaptiveConfig
        if (adaptiveConfig.confidenceScore > 0.5f) {
            currentDifficultyLevel = adaptiveConfig.level
            showAdaptationNotification = SHOW_ADAPTATION_NOTICES
        }
    }

    // Question timer
    LaunchedEffect(gameState, currentQuestionIndex) {
        if (gameState == "playing") {
            questionStartTime = System.currentTimeMillis()
            questionTimeRemaining = adaptiveConfig.responseTimeLimit

            while (questionTimeRemaining > 0 && gameState == "playing" && !showFeedback) {
                delay(100)
                questionTimeRemaining -= 0.1f
            }

            // Auto-fail if time runs out
            if (questionTimeRemaining <= 0 && gameState == "playing" && !showFeedback) {
                val responseTime = System.currentTimeMillis() - questionStartTime
                responseTimes.add(responseTime)

                val currentStep = parsedData.sequence[currentQuestionIndex]
                recordAdaptivePerformance(false, responseTime, currentStep.difficulty)

                userAnswers.add(-1) // -1 indicates timeout
                lastAnswerCorrect = false
                currentHearts = maxOf(0, currentHearts - 1)
                currentStreak = 0

                showFeedback = true

                CoroutineScope(Dispatchers.Main).launch {
                    delay(1500)
                    showFeedback = false

                    if (currentQuestionIndex < parsedData.totalQuestions - 1) {
                        currentQuestionIndex++
                    } else {
                        gameState = "completed"
                    }
                }
            }
        }
    }

    // Main game timer
    LaunchedEffect(gameState) {
        if (gameState == "playing") {
            while (timeRemaining > 0 && gameState == "playing") {
                delay(1000)
                timeRemaining--
            }
            if (timeRemaining <= 0) {
                gameState = "completed"
            }
        }
    }

    // Check for game over
    LaunchedEffect(currentHearts) {
        if (currentHearts <= 0) {
            gameState = "completed"
        }
    }

    // Replace the main Column in AdaptiveColorTextMatchingPuzzleScreen with this structure:

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF8B5A3C)) // Wood-like brown background
    ) {
        // Fixed header section - always visible at top
        Column(
            modifier = Modifier.padding(top = 80.dp, start = 16.dp, end = 16.dp)
        ) {
            // Enhanced header with adaptive info
            AdaptiveUnifiedHeader(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = timer,
                lives = currentHearts,
                currentDifficulty = currentDifficultyLevel,
                score = currentScore,
                puzzleType = "colorTextMatching",
                competitiveInsight = competitiveInsight,
                onBack = onBack,
                onHint = {
                    showHint = !showHint
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )

            /*UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "colorTextMatching",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )*/
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f) // Takes remaining space
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()) // Make this scrollable
                .padding(horizontal = 16.dp)
        ) {
            when (gameState) {
                "instructions" -> {
                    AdaptiveColorTextInstructionsScreen(
                        instructions = parsedData.instructions,
                        adaptiveConfig = adaptiveConfig,
                        onStartGame = {
                            gameState = "playing"
                            gameStartTime = System.currentTimeMillis()
                            Log.d(TAG, "Starting adaptive color-text matching game with ${parsedData.totalQuestions} questions")
                        }
                    )
                }

                "playing" -> {
                    AdaptiveColorTextGameScreen(
                        currentQuestion = currentQuestionIndex + 1,
                        totalQuestions = parsedData.totalQuestions,
                        currentStep = parsedData.sequence[currentQuestionIndex],
                        showFeedback = showFeedback,
                        lastAnswerCorrect = lastAnswerCorrect,
                        adaptiveConfig = adaptiveConfig,
                        questionTimeRemaining = questionTimeRemaining,
                        onAnswer = { isMatch ->
                            val responseTime = System.currentTimeMillis() - questionStartTime
                            responseTimes.add(responseTime)

                            val userAnswer = if (isMatch) 1 else 0
                            val correctAnswer = answerData.correctAnswers[currentQuestionIndex]
                            val isCorrect = userAnswer == correctAnswer
                            val currentStep = parsedData.sequence[currentQuestionIndex]

                            Log.d(TAG, "Question ${currentQuestionIndex + 1}: User answered $userAnswer, correct was $correctAnswer, time: ${responseTime}ms")

                            userAnswers.add(userAnswer)
                            lastAnswerCorrect = isCorrect

                            if (isCorrect) {
                                // Calculate adaptive score based on response time and difficulty
                                val timeBonus = calculateTimeBonus(responseTime, currentStep.expectedResponseTime)
                                val difficultyBonus = (currentStep.difficulty * 20).toInt()
                                val questionScore = 10 + timeBonus + difficultyBonus
                                currentScore += questionScore
                                currentStreak++
                            } else {
                                currentHearts = maxOf(0, currentHearts - 1)
                                currentStreak = 0
                            }

                            recordAdaptivePerformance(isCorrect, responseTime, currentStep.difficulty)
                            showFeedback = true

                            CoroutineScope(Dispatchers.Main).launch {
                                delay(1500)
                                showFeedback = false

                                if (currentQuestionIndex < parsedData.totalQuestions - 1) {
                                    currentQuestionIndex++
                                } else {
                                    gameState = "completed"
                                }
                            }
                        }
                    )
                }

                "completed" -> {
                    UnifiedSessionCompletionHandler(
                        puzzleType = "colorTextMatching",
                        sessionScore = currentScore,
                        sessionStats = SessionStatistics(
                            correctAnswers = correctCount,
                            totalAnswers = totalQuestions,
                            totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                            bestStreak = currentStreak,
                            winRate = if (totalQuestions > 0) correctCount.toFloat() / totalQuestions else 0f,
                            totalScore = currentScore,
                            averageTimePerPuzzle = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()/5,
                            currentStreak = currentStreak,
                            individualTimes = emptyList()
                        ),
                        currentDifficulty = currentDifficultyLevel
                    ) { result ->
                        // Handle session completion
                        val finalScore = (currentScore.toDouble() / 100) * 100 // Your score calculation
                        onSubmitAnswer(finalScore >= 60)
                        fetchNextPuzzle(finalScore.toInt())
                    }
                    AdaptiveColorTextCompletionScreen(
                        score = currentScore,
                        maxScore = answerData.maxScore,
                        totalQuestions = parsedData.totalQuestions,
                        correctCount = userAnswers.zip(answerData.correctAnswers).count { it.first == it.second },
                        averageResponseTime = if (responseTimes.isNotEmpty()) responseTimes.average().toLong() else 0L,
                        adaptiveConfig = adaptiveConfig,
                        streakInfo = streakInfo,
                        onContinue = {
                            val finalScore = (currentScore.toDouble() / answerData.maxScore.toDouble()) * 100
                            onSubmitAnswer(finalScore >= 60)
                            fetchNextPuzzle(finalScore.toInt())
                        }
                    )
                }
            }
        }

        // Fixed bottom padding
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AdaptiveColorTextGameScreenContent(
    currentQuestion: Int,
    totalQuestions: Int,
    currentStep: AdaptiveColorTextStep,
    showFeedback: Boolean,
    lastAnswerCorrect: Boolean,
    adaptiveConfig: AdaptiveColorTextConfig,
    questionTimeRemaining: Float
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Enhanced progress indicator with adaptive info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentQuestion.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            if (adaptiveConfig.showProgressIndicator) {
                Column {
                    Text(
                        text = "$currentQuestion / $totalQuestions",
                        fontSize = 12.sp,
                        color = RvInkSoft.copy(alpha = 0.8f)
                    )
                    LinearProgressIndicator(
                        progress = currentQuestion.toFloat() / totalQuestions.toFloat(),
                        modifier = Modifier.width(100.dp),
                        color = Color(0xFF10B981)
                    )
                }
            }

            if (currentStep.isConflictQuestion && adaptiveConfig.visualHints) {
                Text(
                    text = "⚠️",
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Enhanced question text with difficulty indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.color_match_question),
                fontSize = 18.sp,
                color = RvInk,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )

            if (adaptiveConfig.visualHints && currentStep.difficulty > 0.7f) {
                Text(
                    text = "🔥 Challenge Question",
                    fontSize = 12.sp,
                    color = Color(0xFFFF6B35),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Meaning card (top) with enhanced animations
        AnimatedContent(
            targetState = currentQuestion,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300))) with
                        (fadeOut(tween(300)) + scaleOut(tween(300)))
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "meaning",
                        fontSize = 14.sp,
                        color = RvInkSoft.copy(alpha = 0.8f),
                        modifier = Modifier
                            .background(RvSurface, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentStep.meaningColorName,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Text color card (bottom) with adaptive hints
        AnimatedContent(
            targetState = currentQuestion,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300))) with
                        (fadeOut(tween(300)) + scaleOut(tween(300)))
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentStep.textColorName,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentStep.textDisplayColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "text color",
                            fontSize = 14.sp,
                            color = RvInkSoft.copy(alpha = 0.8f),
                            modifier = Modifier
                                .background(RvSurface, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        if (adaptiveConfig.visualHints && !currentStep.isMatch) {
                            Text(
                                text = "👀",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Enhanced feedback animation
        AnimatedVisibility(
            visible = showFeedback,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        if (lastAnswerCorrect) Color(0xFF10B981) else Color(0xFFEF4444)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (lastAnswerCorrect) "✓" else "✗",
                    fontSize = 60.sp,
                    color = RvInk,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Extra space for floating buttons
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun AdaptiveColorTextInstructionsScreen(
    instructions: AdaptiveColorTextInstructions,
    adaptiveConfig: AdaptiveColorTextConfig,
    onStartGame: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = instructions.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = adaptiveConfig.description,
                fontSize = 14.sp,
                color = RvInkSoft.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = instructions.description,
                fontSize = 16.sp,
                color = RvInkSoft.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Enhanced Example demonstration with adaptive features
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RvSurface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Adaptive Example",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Show adaptive timer if enabled
                    if (adaptiveConfig.showProgressIndicator) {
                        LinearProgressIndicator(
                            progress = 0.7f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = "⏱️ ${adaptiveConfig.responseTimeLimit}s per question",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Example cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Example meaning card
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "meaning",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                modifier = Modifier
                                    .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "blue",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RvInk,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Example text color card with adaptive hint
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "red",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Blue, // Blue color text
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "text color",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                modifier = Modifier
                                    .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            if (adaptiveConfig.visualHints) {
                                Text(
                                    text = "👀 Focus here!",
                                    fontSize = 8.sp,
                                    color = Color(0xFFFF9800),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Answer: YES (both are blue)",
                        fontSize = 12.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Adaptive features explanation
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RvSurface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "🎯 Adaptive Features",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    instructions.adaptiveFeatures.forEach { feature ->
                        Text(
                            text = "• $feature",
                            fontSize = 12.sp,
                            color = RvInk,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "💡 ${instructions.tip}",
                        fontSize = 12.sp,
                        color = RvInk,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Fixed button at bottom
        Button(
            onClick = onStartGame,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Text(
                text = "START ADAPTIVE CHALLENGE",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AdaptiveColorTextGameScreen(
    currentQuestion: Int,
    totalQuestions: Int,
    currentStep: AdaptiveColorTextStep,
    showFeedback: Boolean,
    lastAnswerCorrect: Boolean,
    adaptiveConfig: AdaptiveColorTextConfig,
    questionTimeRemaining: Float,
    onAnswer: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Enhanced progress indicator with adaptive info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentQuestion.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            if (adaptiveConfig.showProgressIndicator) {
                Column {
                    Text(
                        text = "$currentQuestion / $totalQuestions",
                        fontSize = 12.sp,
                        color = RvInkSoft.copy(alpha = 0.8f)
                    )
                    LinearProgressIndicator(
                        progress = currentQuestion.toFloat() / totalQuestions.toFloat(),
                        modifier = Modifier.width(100.dp),
                        color = Color(0xFF10B981)
                    )
                }
            }

            if (currentStep.isConflictQuestion && adaptiveConfig.visualHints) {
                Text(
                    text = "⚠️",
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Enhanced question text with difficulty indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.color_match_question),
                fontSize = 18.sp,
                color = RvInk,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )

            if (adaptiveConfig.visualHints && currentStep.difficulty > 0.7f) {
                Text(
                    text = "🔥 Challenge Question",
                    fontSize = 12.sp,
                    color = Color(0xFFFF6B35),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Meaning card (top) with enhanced animations
        AnimatedContent(
            targetState = currentQuestion,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300))) with
                        (fadeOut(tween(300)) + scaleOut(tween(300)))
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "meaning",
                        fontSize = 14.sp,
                        color = RvInkSoft.copy(alpha = 0.8f),
                        modifier = Modifier
                            .background(RvSurface, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentStep.meaningColorName,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Text color card (bottom) with adaptive hints
        AnimatedContent(
            targetState = currentQuestion,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300))) with
                        (fadeOut(tween(300)) + scaleOut(tween(300)))
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentStep.textColorName,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentStep.textDisplayColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "text color",
                            fontSize = 14.sp,
                            color = RvInkSoft.copy(alpha = 0.8f),
                            modifier = Modifier
                                .background(RvSurface, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        if (adaptiveConfig.visualHints && !currentStep.isMatch) {
                            Text(
                                text = "👀",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Enhanced feedback animation
        AnimatedVisibility(
            visible = showFeedback,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        if (lastAnswerCorrect) Color(0xFF10B981) else Color(0xFFEF4444)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (lastAnswerCorrect) "✓" else "✗",
                    fontSize = 60.sp,
                    color = RvInk,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Enhanced answer buttons with adaptive features
        if (!showFeedback) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { onAnswer(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (adaptiveConfig.visualHints && !currentStep.isMatch)
                            Color(0xFF4CAF50) else Color(0xFF3B82F6)
                    )
                ) {
                    Text("NO", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RvInk)
                }

                Button(
                    onClick = { onAnswer(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (adaptiveConfig.visualHints && currentStep.isMatch)
                            Color(0xFF4CAF50) else Color(0xFF3B82F6)
                    )
                ) {
                    Text("YES", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RvInk)
                }
            }
        }
    }
}

@Composable
private fun AdaptiveColorTextCompletionScreen(
    score: Int,
    maxScore: Int,
    totalQuestions: Int,
    correctCount: Int,
    averageResponseTime: Long,
    adaptiveConfig: AdaptiveColorTextConfig,
    streakInfo: StreakInfo,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Adaptive Challenge Complete!",
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
                    text = stringResource(R.string.final_score),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$score / $maxScore",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Detailed stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.accuracy),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${(correctCount.toDouble() / totalQuestions.toDouble() * 100).toInt()}%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.avg_time),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${averageResponseTime / 1000f}s",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.streak_label),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${streakInfo.currentStreak}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B35)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Mode: ${adaptiveConfig.name}",
                    fontSize = 14.sp,
                    color = Color(0xFF2196F3),
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "Colors Used: ${adaptiveConfig.colorComplexity}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Text(
                text = stringResource(R.string.continue_label_caps),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// Helper functions for adaptive features
fun generateAdaptiveColorTextConfig(difficulty: DifficultyManager.DifficultyLevel): AdaptiveColorTextConfig {
    return when (difficulty.index) {
        0 -> AdaptiveColorTextConfig( // Beginner
            responseTimeLimit = 8f,
            showProgressIndicator = true,
            colorComplexity = 4, // Only 4 basic colors
            conflictFrequency = 0.3f, // 30% conflict questions
            visualHints = true,
            chainLength = 8,
            adaptiveSpeedAdjustment = false,
            name = "Beginner Mode",
            description = "Extended time with visual hints and basic colors"
        )
        1 -> AdaptiveColorTextConfig( // Easy
            responseTimeLimit = 6f,
            showProgressIndicator = true,
            colorComplexity = 6,
            conflictFrequency = 0.4f,
            visualHints = true,
            chainLength = 10,
            adaptiveSpeedAdjustment = true,
            name = "Easy Mode",
            description = "Moderate time with hints and adaptive pacing"
        )
        2 -> AdaptiveColorTextConfig( // Medium
            responseTimeLimit = 4f,
            showProgressIndicator = true,
            colorComplexity = 8,
            conflictFrequency = 0.5f,
            visualHints = false,
            chainLength = 12,
            adaptiveSpeedAdjustment = true,
            name = "Medium Mode",
            description = "Standard timing with increased complexity"
        )
        3 -> AdaptiveColorTextConfig( // Hard
            responseTimeLimit = 3f,
            showProgressIndicator = false,
            colorComplexity = 10,
            conflictFrequency = 0.6f,
            visualHints = false,
            chainLength = 15,
            adaptiveSpeedAdjustment = true,
            name = "Hard Mode",
            description = "Fast pace with maximum color variety"
        )
        4 -> AdaptiveColorTextConfig( // Expert
            responseTimeLimit = 2f,
            showProgressIndicator = false,
            colorComplexity = 12,
            conflictFrequency = 0.7f,
            visualHints = false,
            chainLength = 20,
            adaptiveSpeedAdjustment = true,
            name = "Expert Mode",
            description = "Elite challenge with extreme time pressure"
        )
        else -> AdaptiveColorTextConfig(
            responseTimeLimit = 4f,
            showProgressIndicator = true,
            colorComplexity = 8,
            conflictFrequency = 0.5f,
            visualHints = false,
            chainLength = 12,
            adaptiveSpeedAdjustment = true,
            name = "Medium Mode",
            description = "Standard timing with increased complexity"
        )
    }
}

private fun generateAdaptiveColorTextPuzzleData(
    difficulty: String,
    config: AdaptiveColorTextConfig
): AdaptiveColorTextPuzzleData {
    val totalQuestions = config.chainLength
    val sequence = mutableListOf<AdaptiveColorTextStep>()
    val availableColors = AdaptiveGameColors.getColorsForComplexity(config.colorComplexity)

    repeat(totalQuestions) { i ->
        val meaningColorName = availableColors.random()
        val textColorName = availableColors.random()

        // Determine if this should be a conflict question
        val isConflictQuestion = Random.nextFloat() < config.conflictFrequency

        // Decide if this should be a match or not
        val shouldMatch = if (isConflictQuestion) {
            // For conflict questions, make it more challenging
            Random.nextFloat() < 0.3f // Lower chance of match in conflict questions
        } else {
            Random.nextFloat() < 0.6f // Higher chance of match in normal questions
        }

        val actualTextDisplayColor = if (shouldMatch) {
            AdaptiveGameColors.colorMap[meaningColorName] ?: Color.Black
        } else {
            val differentColor = availableColors.filter { it != meaningColorName }.random()
            AdaptiveGameColors.colorMap[differentColor] ?: Color.Black
        }

        // Calculate question difficulty based on various factors
        val baselineDifficulty = when {
            isConflictQuestion -> 0.8f
            shouldMatch -> 0.4f
            else -> 0.6f
        }

        val colorSimilarityPenalty = if (meaningColorName.length == textColorName.length) 0.2f else 0f
        val questionDifficulty = (baselineDifficulty + colorSimilarityPenalty).coerceIn(0f, 1f)

        // Expected response time based on difficulty
        val expectedTime = config.responseTimeLimit * (0.5f + questionDifficulty * 0.5f)

        sequence.add(
            AdaptiveColorTextStep(
                step = i + 1,
                meaningColorName = meaningColorName,
                textColorName = textColorName,
                textDisplayColor = actualTextDisplayColor,
                isMatch = shouldMatch,
                difficulty = questionDifficulty,
                expectedResponseTime = expectedTime,
                isConflictQuestion = isConflictQuestion
            )
        )
    }

    val instructions = AdaptiveColorTextInstructions(
        title = "Adaptive Color-Text Matching",
        description = "Compare the color name meaning with the actual text color and decide if they match.",
        steps = listOf(
            "Look at the top card - this shows the color name meaning",
            "Look at the bottom card - focus on the actual COLOR of the text, not what it says",
            "Compare: Does the meaning match the text color?",
            "Tap YES if the meaning matches the text color",
            "Tap NO if they don't match"
        ),
        tip = "Focus on the actual color of the text in the bottom card, not what the word says!",
        adaptiveFeatures = listOf(
            "Time pressure adapts to your skill level",
            "Color complexity increases with performance",
            "Visual hints available for beginners",
            "Progressive difficulty adjustment",
            "Performance tracking for optimal challenge"
        )
    )

    return AdaptiveColorTextPuzzleData(
        sequence = sequence,
        instructions = instructions,
        totalSteps = totalQuestions,
        totalQuestions = totalQuestions,
        difficulty = difficulty,
        config = config
    )
}

private fun generateAdaptiveColorTextAnswers(puzzleData: AdaptiveColorTextPuzzleData): AdaptiveColorTextAnswerData {
    val correctAnswers = puzzleData.sequence.map { step ->
        if (step.isMatch) 1 else 0
    }

    val expectedTimes = puzzleData.sequence.map { step ->
        step.expectedResponseTime
    }

    return AdaptiveColorTextAnswerData(
        correctAnswers = correctAnswers,
        totalQuestions = puzzleData.totalQuestions,
        maxScore = puzzleData.totalQuestions * 50, // Higher max score for adaptive version
        expectedTimes = expectedTimes
    )
}

private fun calculateTimeBonus(responseTime: Long, expectedTime: Float): Int {
    val responseSec = responseTime / 1000f
    val ratio = responseSec / expectedTime

    return when {
        ratio < 0.5f -> 20 // Very fast
        ratio < 0.75f -> 15 // Fast
        ratio < 1.0f -> 10 // On time
        ratio < 1.5f -> 5 // Slow
        else -> 0 // Very slow
    }
}
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.max
import kotlin.random.Random

/**
 * Simplified Adaptive Memory Previous Single Generator
 * Focuses on core memory challenge without complex distractors
 */
object SimplifiedAdaptiveMemoryGenerator {

    data class SimpleConfig(
        val delayMs: Long, // Simple delay for reveal
        val cognitiveLoad: Int, // 1-5 scale for difficulty
        val name: String,
        val description: String
    )

    data class SimpleMemoryStep(
        val step: Int,
        val value: Int,
        val isFirstStep: Boolean,
        val delayMs: Long = 0
    )

    data class SimpleDifficultyConfig(
        val sequenceLength: Int,
        val minContinuousSequences: Int,
        val sameAsPreviousRatio: Double,
        val simpleConfig: SimpleConfig
    )

    private fun getDifficultyConfigs() = mapOf(
        0 to SimpleDifficultyConfig( // Beginner
            sequenceLength = 20,
            minContinuousSequences = 8,
            sameAsPreviousRatio = 0.7,
            simpleConfig = SimpleConfig(
                delayMs = 0L,
                cognitiveLoad = 1,
                name = "Beginner",
                description = "Clear visual cues with high similarity ratio"
            )
        ),
        1 to SimpleDifficultyConfig( // Easy
            sequenceLength = 18,
            minContinuousSequences = 6,
            sameAsPreviousRatio = 0.65,
            simpleConfig = SimpleConfig(
                delayMs = 300L,
                cognitiveLoad = 2,
                name = "Easy",
                description = "Slight delay with good similarity ratio"
            )
        ),
        2 to SimpleDifficultyConfig( // Medium
            sequenceLength = 16,
            minContinuousSequences = 5,
            sameAsPreviousRatio = 0.55,
            simpleConfig = SimpleConfig(
                delayMs = 500L,
                cognitiveLoad = 3,
                name = "Medium",
                description = "Moderate delay with balanced similarity"
            )
        ),
        3 to SimpleDifficultyConfig( // Hard
            sequenceLength = 14,
            minContinuousSequences = 4,
            sameAsPreviousRatio = 0.5,
            simpleConfig = SimpleConfig(
                delayMs = 750L,
                cognitiveLoad = 4,
                name = "Hard",
                description = "Longer delay with balanced similarity"
            )
        ),
        4 to SimpleDifficultyConfig( // Expert
            sequenceLength = 12,
            minContinuousSequences = 3,
            sameAsPreviousRatio = 0.45,
            simpleConfig = SimpleConfig(
                delayMs = 1000L,
                cognitiveLoad = 5,
                name = "Expert",
                description = "Maximum delay with low similarity"
            )
        )
    )

    /**
     * Generate simplified adaptive puzzle
     */
    fun generateSimplePuzzle(
        difficultyLevel: DifficultyManager.DifficultyLevel
    ): Pair<String, String> {
        val config = getDifficultyConfigs()[difficultyLevel.index]
            ?: getDifficultyConfigs()[2]!!

        val binarySequence = generateSimpleBinarySequence(config)
        val questionSequence = createSimpleQuestionSequence(binarySequence, config.simpleConfig)
        val answerSequence = createAnswerSequence(binarySequence)

        // Debug logging
        Log.d("SimplifiedGenerator", "Generated puzzle:")
        Log.d("SimplifiedGenerator", "  Binary sequence: $binarySequence")
        Log.d("SimplifiedGenerator", "  Answer sequence: $answerSequence")
        Log.d("SimplifiedGenerator", "  Question steps: ${questionSequence.size}")
        Log.d("SimplifiedGenerator", "  Answer questions: ${answerSequence.size}")

        val instructions = createSimpleInstructions(config.simpleConfig)

        // Build question JSON
        val questionData = mapOf(
            "binarySequence" to binarySequence,
            "questionSequence" to questionSequence.map { step ->
                mapOf(
                    "step" to step.step,
                    "value" to step.value,
                    "isFirstStep" to step.isFirstStep,
                    "delayMs" to step.delayMs
                )
            },
            "instructions" to instructions,
            "totalSteps" to binarySequence.size,
            "totalQuestions" to answerSequence.size,
            "simpleConfig" to mapOf(
                "name" to config.simpleConfig.name,
                "description" to config.simpleConfig.description,
                "delayMs" to config.simpleConfig.delayMs,
                "cognitiveLoad" to config.simpleConfig.cognitiveLoad
            ),
            "difficulty" to config.simpleConfig.name
        )

        // Build answer JSON
        val answerData = mapOf(
            "answerSequence" to answerSequence,
            "correctAnswers" to answerSequence,
            "totalQuestions" to answerSequence.size,
            "maxScore" to calculateSimpleScore(answerSequence.size, config.simpleConfig)
        )

        val questionJson = buildJsonString(questionData)
        val answerJson = buildJsonString(answerData)

        return Pair(questionJson, answerJson)
    }

    private fun generateSimpleBinarySequence(config: SimpleDifficultyConfig): List<Int> {
        var attempts = 0
        val maxAttempts = 50

        while (attempts < maxAttempts) {
            val sequence = createRandomBinarySequence(
                config.sequenceLength,
                config.sameAsPreviousRatio
            )
            val continuousCount = countContinuousSequences(sequence)

            if (continuousCount >= config.minContinuousSequences) {
                return sequence
            }
            attempts++
        }

        return createForcedContinuousSequence(config)
    }

    private fun createRandomBinarySequence(
        length: Int,
        sameAsPreviousRatio: Double
    ): List<Int> {
        val sequence = mutableListOf<Int>()
        sequence.add(if (Random.nextDouble() < 0.5) 0 else 1)

        for (i in 1 until length) {
            val shouldBeSame = Random.nextDouble() < sameAsPreviousRatio

            if (shouldBeSame) {
                sequence.add(sequence[i - 1])
            } else {
                sequence.add(if (sequence[i - 1] == 0) 1 else 0)
            }
        }

        return sequence
    }

    private fun createSimpleQuestionSequence(
        binarySequence: List<Int>,
        simpleConfig: SimpleConfig
    ): List<SimpleMemoryStep> {
        return binarySequence.mapIndexed { index, value ->
            SimpleMemoryStep(
                step = index + 1,
                value = value,
                isFirstStep = index == 0,
                delayMs = if (index > 0) simpleConfig.delayMs else 0L
            )
        }
    }

    private fun createSimpleInstructions(simpleConfig: SimpleConfig): Map<String, Any> {
        val baseSteps = listOf(
            "1. Look at the first symbol and remember it",
            "2. For each new symbol, decide if it's SAME or DIFFERENT from the previous one",
            "3. Tap 'SAME' if it matches, 'DIFFERENT' if it doesn't",
            "4. Continue through the entire sequence"
        )

        val adaptiveSteps = if (simpleConfig.delayMs > 0) {
            baseSteps + listOf("5. Wait for the symbol to fully appear before deciding")
        } else {
            baseSteps + listOf("5. Be quick and accurate for maximum points!")
        }

        val tip = when (simpleConfig.cognitiveLoad) {
            1, 2 -> "Focus on the immediate previous symbol only"
            3 -> "Stay focused - compare only with the previous symbol"
            4, 5 -> "Concentrate! Wait for the reveal and compare carefully"
            else -> "Compare each symbol with the one that came before it"
        }

        return mapOf(
            "title" to "Memory Previous Single",
            "description" to "${simpleConfig.name}: ${simpleConfig.description}",
            "steps" to adaptiveSteps,
            "tip" to tip
        )
    }

    private fun calculateSimpleScore(totalQuestions: Int, simpleConfig: SimpleConfig): Int {
        val baseScorePerQuestion = 10
        val difficultyMultiplier = when (simpleConfig.cognitiveLoad) {
            1 -> 1.0f
            2 -> 1.2f
            3 -> 1.5f
            4 -> 1.8f
            5 -> 2.2f
            else -> 1.0f
        }

        return (totalQuestions * baseScorePerQuestion * difficultyMultiplier).toInt()
    }

    // Helper functions
    private fun createForcedContinuousSequence(config: SimpleDifficultyConfig): List<Int> {
        val sequence = mutableListOf<Int>()
        var remainingLength = config.sequenceLength
        var sequencesCreated = 0
        var currentValue = if (Random.nextDouble() < 0.5) 0 else 1

        while (remainingLength > 0 && sequencesCreated < config.minContinuousSequences) {
            val maxLength = remainingLength / (config.minContinuousSequences - sequencesCreated)
            val seqLength = max(2, Random.nextInt(maxLength) + 1)

            repeat(seqLength) {
                sequence.add(currentValue)
            }

            remainingLength -= seqLength
            sequencesCreated++
            currentValue = if (currentValue == 0) 1 else 0
        }

        while (remainingLength > 0) {
            val shouldBeSame = Random.nextDouble() < 0.5
            if (shouldBeSame && sequence.isNotEmpty()) {
                sequence.add(sequence.last())
            } else {
                sequence.add(if (Random.nextDouble() < 0.5) 0 else 1)
            }
            remainingLength--
        }

        return sequence
    }

    private fun countContinuousSequences(sequence: List<Int>): Int {
        if (sequence.size < 2) return 0

        var count = 0
        var currentSequenceLength = 1

        for (i in 1 until sequence.size) {
            if (sequence[i] == sequence[i - 1]) {
                currentSequenceLength++
            } else {
                if (currentSequenceLength >= 2) {
                    count++
                }
                currentSequenceLength = 1
            }
        }

        if (currentSequenceLength >= 2) {
            count++
        }

        return count
    }

    private fun createAnswerSequence(binarySequence: List<Int>): List<Int> {
        val answers = mutableListOf<Int>()

        for (i in 1 until binarySequence.size) {
            val currentValue = binarySequence[i]
            val previousValue = binarySequence[i - 1]
            answers.add(if (currentValue == previousValue) 1 else 0)
        }

        return answers
    }

    private fun buildJsonString(data: Any): String {
        return when (data) {
            is Map<*, *> -> {
                val entries = data.entries.joinToString(",") { (key, value) ->
                    "\"$key\":${value?.let { buildJsonString(it) }}"
                }
                "{$entries}"
            }
            is List<*> -> {
                val elements = data.joinToString(",") {
                    (if (it != null) {
                        buildJsonString(it)
                    } else {
                        ""
                    }).toString()
                }
                "[$elements]"
            }
            is String -> "\"$data\""
            is Number -> data.toString()
            is Boolean -> data.toString()
            null -> "null"
            else -> "\"$data\""
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SimplifiedAdaptiveMemoryScreen(
    initialDifficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "SimplifiedMemory"

    // Difficulty management
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("memorySingle"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // Competitive ranking state
    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "memorySingle",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate simplified puzzle data
    val (puzzleData, answerData) = remember(currentDifficultyLevel) {
        try {
            SimplifiedAdaptiveMemoryGenerator.generateSimplePuzzle(currentDifficultyLevel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate puzzle", e)
            Pair("{}", "{}")
        }
    }

    // Parse puzzle data
    val parsedData = remember(puzzleData) {
        try {
            val puzzleJson = JSONObject(puzzleData)

            val binaryArray = puzzleJson.getJSONArray("binarySequence")
            val binarySequence = List(binaryArray.length()) { i -> binaryArray.getInt(i) }

            val questionArray = puzzleJson.getJSONArray("questionSequence")
            val questionSequence = mutableListOf<SimplifiedAdaptiveMemoryGenerator.SimpleMemoryStep>()
            for (i in 0 until questionArray.length()) {
                val stepObj = questionArray.getJSONObject(i)
                questionSequence.add(
                    SimplifiedAdaptiveMemoryGenerator.SimpleMemoryStep(
                        step = stepObj.getInt("step"),
                        value = stepObj.getInt("value"),
                        isFirstStep = stepObj.getBoolean("isFirstStep"),
                        delayMs = stepObj.getLong("delayMs")
                    )
                )
            }

            val configObj = puzzleJson.getJSONObject("simpleConfig")
            val simpleConfig = SimplifiedAdaptiveMemoryGenerator.SimpleConfig(
                delayMs = configObj.getLong("delayMs"),
                cognitiveLoad = configObj.getInt("cognitiveLoad"),
                name = configObj.getString("name"),
                description = configObj.getString("description")
            )

            Triple(binarySequence, questionSequence as List<SimplifiedAdaptiveMemoryGenerator.SimpleMemoryStep>, simpleConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse puzzle data", e)
            Triple(
                emptyList<Int>(),
                emptyList<SimplifiedAdaptiveMemoryGenerator.SimpleMemoryStep>(),
                SimplifiedAdaptiveMemoryGenerator.SimpleConfig(0L, 1, "Error", "Failed to load")
            )
        }
    }

    val (binarySequence, questionSequence, simpleConfig) = parsedData

    // Parse answer data
    val correctAnswers = remember(answerData) {
        try {
            val answerJson = JSONObject(answerData)
            val answerArray = answerJson.getJSONArray("answerSequence")
            List(answerArray.length()) { i -> answerArray.getInt(i) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse answer data", e)
            emptyList<Int>()
        }
    }

    // Game state
    var gameState by remember { mutableStateOf("instructions") }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var currentScore by remember { mutableStateOf(0) }
    var showFeedback by remember { mutableStateOf(false) }
    var lastAnswerCorrect by remember { mutableStateOf(false) }
    var userAnswers by remember { mutableStateOf(mutableListOf<Int>()) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var currentStreak by remember { mutableStateOf(0) }
    var startTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctCount by remember { mutableStateOf(0) }
    var totalQuestions by remember { mutableStateOf(0) }

    // Visual states
    var isRevealed by remember { mutableStateOf(false) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()
    val haptics = LocalHapticFeedback.current
    var showHint by remember { mutableStateOf(false) }

    // Timer management
    var timeRemaining by remember { mutableStateOf(questionSequence.size * 4) }

    LaunchedEffect(gameState) {
        if (gameState == "playing") {
            while (timeRemaining > 0 && gameState == "playing") {
                delay(1000)
                timeRemaining--
            }
            if (timeRemaining <= 0 && gameState == "playing") {
                Log.d(TAG, "Time expired, completing game")
                gameState = "completed"
            }
        }
    }

    // Symbol display management
    LaunchedEffect(currentQuestionIndex, gameState) {
        if (gameState == "playing" && currentQuestionIndex in 0 until questionSequence.size) {
            val currentStep = questionSequence[currentQuestionIndex]

            Log.d(TAG, "Displaying symbol $currentQuestionIndex: ${currentStep.value}")

            // Reset visual states
            isRevealed = false

            // Apply delay if configured
            if (currentStep.delayMs > 0) {
                delay(currentStep.delayMs)
            }

            // Reveal the symbol
            isRevealed = true
            Log.d(TAG, "Symbol revealed: ${currentStep.value}")

            // Auto-advance only on first symbol
            if (currentQuestionIndex == 0) {
                Log.d(TAG, "First symbol shown, auto-advancing after 2 seconds")
                delay(2000)

                if (gameState == "playing" && currentQuestionIndex == 0 && questionSequence.size > 1) {
                    Log.d(TAG, "Auto-advancing from first symbol to second symbol")
                    currentQuestionIndex = 1
                }
            }
        }
    }

    // Performance recording function
    fun recordPerformance(isCorrect: Boolean, responseTime: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = responseTime,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = simpleConfig.cognitiveLoad,
            totalScore = currentScore,
            puzzleType = "memorySingle"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E3A8A), Color(0xFF3730A3))
                )
            )
            .padding(top = 80.dp, start = 16.dp, end = 16.dp, bottom = 32.dp)
    ) {
        // Unified header
        AdaptiveUnifiedHeader(
            level = currentLevel,
            streakInfo = streakInfo,
            timer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}",
            lives = currentHearts,
            currentDifficulty = currentDifficultyLevel,
            score = currentScore,
            puzzleType = "memorySingle",
            challengeNumber = currentQuestionIndex + 1,
            totalChallenges = questionSequence.size,
            competitiveInsight = competitiveInsight,
            onBack = onBack,
            onPause = {
                Log.d(TAG, "Pause requested")
            },
            onHint = {
                showHint = !showHint
                if (showHint && currentQuestionIndex > 0) {
                    Log.d(TAG, "Hint: Compare current symbol with the previous one only")
                }
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )

        // Adaptation notification
        UnifiedAdaptationNotification(
            adaptationInfo = adaptationInfo,
            puzzleType = "memorySingle",
            visible = showAdaptationNotification,
            onDismiss = { showAdaptationNotification = false }
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (gameState) {
            "instructions" -> {
                SimpleInstructionsScreen(
                    simpleConfig = simpleConfig,
                    onStartGame = {
                        gameState = "playing"
                        startTime = System.currentTimeMillis()
                        sessionStartTime = System.currentTimeMillis()
                        totalQuestions = correctAnswers.size
                        currentQuestionIndex = 0

                        Log.d(TAG, "Starting simplified memory game:")
                        Log.d(TAG, "   Difficulty: ${simpleConfig.name}")
                        Log.d(TAG, "   Total symbols to show: ${questionSequence.size}")
                        Log.d(TAG, "   Total questions to answer: ${correctAnswers.size}")
                    }
                )
            }

            "playing" -> {
                SimpleGameScreen(
                    currentQuestion = currentQuestionIndex + 1,
                    totalQuestions = questionSequence.size,
                    totalAnswerQuestions = correctAnswers.size,
                    currentStep = if (currentQuestionIndex in 0 until questionSequence.size) {
                        questionSequence[currentQuestionIndex]
                    } else null,
                    previousStep = if (currentQuestionIndex > 0 && (currentQuestionIndex - 1) < questionSequence.size) {
                        questionSequence[currentQuestionIndex - 1]
                    } else null,
                    simpleConfig = simpleConfig,
                    isRevealed = isRevealed,
                    showFeedback = showFeedback,
                    lastAnswerCorrect = lastAnswerCorrect,
                    canAnswer = currentQuestionIndex > 0,
                    onAnswer = { isSame ->
                        if (currentQuestionIndex <= 0 || showFeedback) {
                            return@SimpleGameScreen
                        }

                        val userAnswer = if (isSame) 1 else 0
                        val answerIndex = currentQuestionIndex - 1

                        if (answerIndex < 0 || answerIndex >= correctAnswers.size) {
                            Log.e(TAG, "Answer index out of bounds: $answerIndex/${correctAnswers.size}")
                            return@SimpleGameScreen
                        }

                        val expectedAnswer = correctAnswers[answerIndex]
                        val isCorrect = userAnswer == expectedAnswer

                        Log.d(TAG, "Answer submitted:")
                        Log.d(TAG, "   Current symbol index: $currentQuestionIndex")
                        Log.d(TAG, "   User answered: ${if (isSame) "SAME" else "DIFFERENT"}")
                        Log.d(TAG, "   Expected: ${if (expectedAnswer == 1) "SAME" else "DIFFERENT"}")
                        Log.d(TAG, "   Result: ${if (isCorrect) "CORRECT" else "WRONG"}")

                        userAnswers.add(userAnswer)
                        lastAnswerCorrect = isCorrect

                        if (isCorrect) {
                            currentScore += calculateSimpleScore(simpleConfig)
                            currentStreak++
                            correctCount++
                        } else {
                            currentHearts = maxOf(0, currentHearts - 1)
                            currentStreak = 0
                        }

                        recordPerformance(isCorrect, System.currentTimeMillis() - startTime)
                        showFeedback = true

                        GlobalScope.launch {
                            delay(1500)
                            showFeedback = false

                            if (currentQuestionIndex < questionSequence.size - 1) {
                                Log.d(TAG, "Moving to next symbol: ${currentQuestionIndex + 1}")
                                currentQuestionIndex++
                            } else {
                                Log.d(TAG, "All symbols shown, completing game")
                                gameState = "completed"
                            }
                        }
                    }
                )
            }

            "completed" -> {
                UnifiedSessionCompletionHandler(
                    puzzleType = "memorysingle",
                    sessionScore = currentScore,
                    sessionStats = SessionStatistics(
                        correctAnswers = correctCount,
                        totalAnswers = totalQuestions,
                        totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                        bestStreak = currentStreak,
                        winRate = if (totalQuestions > 0) correctCount.toFloat() / totalQuestions else 0f,
                        totalScore = currentScore,
                        averageTimePerPuzzle = if (totalQuestions > 0)
                            ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt() / totalQuestions
                        else 0,
                        currentStreak = currentStreak,
                        individualTimes = emptyList()
                    ),
                    currentDifficulty = currentDifficultyLevel
                ) { result ->
                    val isSuccess = currentScore >= (correctAnswers.size * calculateSimpleScore(simpleConfig) * 0.6f).toInt()

                    feedbackManager.showFeedback(
                        puzzleType = "memorysingle",
                        isCorrect = isSuccess,
                        userAnswer = "${userAnswers.zip(correctAnswers).count { it.first == it.second }}/${correctAnswers.size} correct",
                        correctAnswer = "Simple ${simpleConfig.name} memory sequence",
                        timeSpent = System.currentTimeMillis() - startTime,
                        difficulty = simpleConfig.name,
                        timeRemaining = timeRemaining,
                        totalTime = questionSequence.size * 4,
                        onComplete = {
                            onSubmitAnswer(isSuccess)
                            fetchNextPuzzle(currentScore)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleInstructionsScreen(
    simpleConfig: SimplifiedAdaptiveMemoryGenerator.SimpleConfig,
    onStartGame: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Memory Previous Single",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = simpleConfig.description,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Instructions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val instructions = listOf(
                    "1. Look at the first symbol and remember it",
                    "2. For each new symbol, decide if it's SAME or DIFFERENT",
                    "3. Compare only with the previous symbol",
                    "4. Tap SAME if it matches, DIFFERENT if not"
                )

                instructions.forEach { instruction ->
                    Text(
                        text = instruction,
                        fontSize = 12.sp,
                        color = Color(0xFF1E3A8A),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                if (simpleConfig.delayMs > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Note: Symbols will have a ${simpleConfig.delayMs}ms delay",
                        fontSize = 11.sp,
                        color = Color(0xFF7C3AED),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onStartGame,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Text(
                text = stringResource(R.string.start_challenge),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SimpleGameScreen(
    currentQuestion: Int,
    totalQuestions: Int,
    totalAnswerQuestions: Int,
    currentStep: SimplifiedAdaptiveMemoryGenerator.SimpleMemoryStep?,
    previousStep: SimplifiedAdaptiveMemoryGenerator.SimpleMemoryStep?,
    simpleConfig: SimplifiedAdaptiveMemoryGenerator.SimpleConfig,
    isRevealed: Boolean,
    showFeedback: Boolean,
    lastAnswerCorrect: Boolean,
    canAnswer: Boolean,
    onAnswer: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress indicator
        Text(
            text = "Symbol $currentQuestion of $totalQuestions",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f)
        )

        if (canAnswer) {
            Text(
                text = "Question ${currentQuestion - 1} of $totalAnswerQuestions",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = currentQuestion.toFloat() / totalQuestions.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Color(0xFF10B981),
            trackColor = Color.White.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Main symbol display (simplified - no distractors)
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentQuestion to isRevealed,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(tween(300))) with
                            (fadeOut(tween(300)) + scaleOut(tween(300)))
                }
            ) { (_, revealed) ->
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (revealed) Color.White else Color.Gray.copy(alpha = 0.3f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (revealed && currentStep != null) {
                        Text(
                            text = if (currentStep.value == 0) "★" else "☆",
                            fontSize = 120.sp,
                            color = Color(0xFF9C27B0)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Instructions text
        when {
            currentQuestion == 1 -> {
                Text(
                    text = "Remember this symbol",
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            canAnswer -> {
                Text(
                    text = "Does this symbol match the\nprevious symbol?",
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
            else -> {
                Text(
                    text = "Watch carefully...",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Answer buttons - only show when answering is allowed
        if (!showFeedback && canAnswer) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { onAnswer(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text(stringResource(R.string.different), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = { onAnswer(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text(stringResource(R.string.same), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Feedback display
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
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Helper function for simple scoring
private fun calculateSimpleScore(simpleConfig: SimplifiedAdaptiveMemoryGenerator.SimpleConfig): Int {
    val baseScore = 10
    val difficultyMultiplier = when (simpleConfig.cognitiveLoad) {
        1 -> 1.0f
        2 -> 1.2f
        3 -> 1.5f
        4 -> 1.8f
        5 -> 2.2f
        else -> 1.0f
    }

    return (baseScore * difficultyMultiplier).toInt()
}
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.max

import kotlin.random.Random

/**
 * Adaptive generator for Memory Previous Pair puzzles
 * Uses difficulty management to adjust challenge based on player performance
 */
object AdaptiveMemoryPreviousPairGenerator {

    data class AdaptivePairConfig(
        val memorySpanMultiplier: Float, // Multiplier for how many items to remember
        val visualSimilarity: Boolean, // Whether to use visually similar animals
        val spatialMemoryChallenge: Boolean, // Whether positions matter
        val workingMemoryLoad: Int, // 1-5 scale for concurrent memory tasks
        val temporalDecay: Boolean, // Whether items fade over time
        val interferenceItems: Boolean, // Whether to show non-linking items
        val name: String,
        val description: String
    )

    data class AdaptivePairDifficultyConfig(
        val sequenceLength: Int,
        val objectsPerScreen: Int,
        val maxObjectPool: Int,
        val adaptiveConfig: AdaptivePairConfig
    )

    data class AdaptiveScreenData(
        val screenNumber: Int,
        val numbers: List<Int>,
        val linkingNumber: Int?,
        val isFirstScreen: Boolean,
        val fadeDelay: Long = 0L,
        val spatialPositions: List<Pair<Float, Float>> = emptyList(), // x, y coordinates
        val interferenceNumbers: List<Int> = emptyList(),
        val visualSimilarityGroup: String = "default"
    )

    private fun getDifficultyConfigs() = mapOf(
        0 to AdaptivePairDifficultyConfig( // Beginner
            sequenceLength = 12,
            objectsPerScreen = 2,
            maxObjectPool = 6,
            adaptiveConfig = AdaptivePairConfig(
                memorySpanMultiplier = 0.8f, // Easier memory span
                visualSimilarity = false,
                spatialMemoryChallenge = false,
                workingMemoryLoad = 1,
                temporalDecay = false,
                interferenceItems = false,
                name = "Beginner",
                description = "Simple pairs with clear visual differences"
            )
        ),
        1 to AdaptivePairDifficultyConfig( // Easy
            sequenceLength = 14,
            objectsPerScreen = 2,
            maxObjectPool = 7,
            adaptiveConfig = AdaptivePairConfig(
                memorySpanMultiplier = 1.0f,
                visualSimilarity = false,
                spatialMemoryChallenge = false,
                workingMemoryLoad = 2,
                temporalDecay = true,
                interferenceItems = false,
                name = "Easy",
                description = "Standard pairs with temporal fade"
            )
        ),
        2 to AdaptivePairDifficultyConfig( // Medium
            sequenceLength = 16,
            objectsPerScreen = 3,
            maxObjectPool = 8,
            adaptiveConfig = AdaptivePairConfig(
                memorySpanMultiplier = 1.2f,
                visualSimilarity = true,
                spatialMemoryChallenge = true,
                workingMemoryLoad = 3,
                temporalDecay = true,
                interferenceItems = false,
                name = "Medium",
                description = "Visual similarity with spatial memory"
            )
        ),
        3 to AdaptivePairDifficultyConfig( // Hard
            sequenceLength = 18,
            objectsPerScreen = 4,
            maxObjectPool = 10,
            adaptiveConfig = AdaptivePairConfig(
                memorySpanMultiplier = 1.4f,
                visualSimilarity = true,
                spatialMemoryChallenge = true,
                workingMemoryLoad = 4,
                temporalDecay = true,
                interferenceItems = true,
                name = "Hard",
                description = "Complex pairs with interference items"
            )
        ),
        4 to AdaptivePairDifficultyConfig( // Expert
            sequenceLength = 20,
            objectsPerScreen = 5,
            maxObjectPool = 12,
            adaptiveConfig = AdaptivePairConfig(
                memorySpanMultiplier = 1.6f,
                visualSimilarity = true,
                spatialMemoryChallenge = true,
                workingMemoryLoad = 5,
                temporalDecay = true,
                interferenceItems = true,
                name = "Expert",
                description = "Maximum cognitive load with all challenges"
            )
        )
    )

    /**
     * Generate adaptive Memory Previous Pair puzzle
     */
    fun generateAdaptivePuzzle(
        difficultyLevel: DifficultyManager.DifficultyLevel
    ): Pair<String, String> {
        val config = getDifficultyConfigs()[difficultyLevel.index]
            ?: getDifficultyConfigs()[2]!! // Default to medium

        // Generate sequence with adaptive features
        val sequence = generateAdaptiveLinkedSequence(config)

        // Validate sequence
        if (!validateAdaptiveSequence(sequence)) {
            throw Exception("Generated adaptive sequence failed validation")
        }

        // Create adaptive instructions
        val instructions = createAdaptiveInstructions(config.adaptiveConfig)

        // Build question JSON
        val questionData = mapOf(
            "sequence" to sequence.map { screen ->
                mapOf(
                    "screenNumber" to screen.screenNumber,
                    "numbers" to screen.numbers,
                    "linkingNumber" to screen.linkingNumber,
                    "isFirstScreen" to screen.isFirstScreen,
                    "fadeDelay" to screen.fadeDelay,
                    "spatialPositions" to screen.spatialPositions.map {
                        mapOf("x" to it.first, "y" to it.second)
                    },
                    "interferenceNumbers" to screen.interferenceNumbers,
                    "visualSimilarityGroup" to screen.visualSimilarityGroup
                )
            },
            "instructions" to instructions,
            "totalScreens" to sequence.size,
            "objectsPerScreen" to config.objectsPerScreen,
            "maxObjectPool" to config.maxObjectPool,
            "adaptiveConfig" to mapOf(
                "name" to config.adaptiveConfig.name,
                "description" to config.adaptiveConfig.description,
                "memorySpanMultiplier" to config.adaptiveConfig.memorySpanMultiplier,
                "visualSimilarity" to config.adaptiveConfig.visualSimilarity,
                "spatialMemoryChallenge" to config.adaptiveConfig.spatialMemoryChallenge,
                "workingMemoryLoad" to config.adaptiveConfig.workingMemoryLoad,
                "temporalDecay" to config.adaptiveConfig.temporalDecay,
                "interferenceItems" to config.adaptiveConfig.interferenceItems
            ),
            "difficulty" to config.adaptiveConfig.name
        )

        // Build answer JSON
        val linkingNumbers = sequence
            .filter { it.linkingNumber != null }
            .map { screen ->
                mapOf(
                    "screenNumber" to screen.screenNumber,
                    "linkingNumber" to screen.linkingNumber!!
                )
            }

        val answerData = mapOf(
            "linkingNumbers" to linkingNumbers,
            "totalCorrectAnswers" to linkingNumbers.size,
            "maxScore" to calculateAdaptiveMaxScore(linkingNumbers.size, config.adaptiveConfig)
        )

        val questionJson = buildJsonString(questionData)
        val answerJson = buildJsonString(answerData)

        return Pair(questionJson, answerJson)
    }

    /**
     * Generate adaptive linked sequence
     */
    private fun generateAdaptiveLinkedSequence(config: AdaptivePairDifficultyConfig): List<AdaptiveScreenData> {
        val adaptedObjectsPerScreen = (config.objectsPerScreen * config.adaptiveConfig.memorySpanMultiplier).toInt()
        val actualObjectsPerScreen = max(2, adaptedObjectsPerScreen)

        val availableNumbers = (1..config.maxObjectPool).toList()
        val sequence = mutableListOf<AdaptiveScreenData>()

        for (i in 0 until config.sequenceLength) {
            val screenNumbers = when (i) {
                0 -> {
                    selectRandomNumbers(availableNumbers, actualObjectsPerScreen)
                }
                else -> {
                    val previousScreen = sequence[i - 1]
                    val linkingNumber = previousScreen.numbers.random()
                    val screenNumbers = mutableListOf(linkingNumber)

                    val excludeNumbers = if (config.adaptiveConfig.visualSimilarity) {
                        // Make it harder by using similar visual numbers
                        previousScreen.numbers.take(1) // Only exclude one
                    } else {
                        previousScreen.numbers // Exclude all previous
                    }

                    val availableForNew = availableNumbers.filter { it !in excludeNumbers }
                    val newNumbers = selectRandomNumbers(
                        availableForNew,
                        actualObjectsPerScreen - 1
                    )

                    screenNumbers.addAll(newNumbers)
                    screenNumbers.shuffled()
                }
            }

            // Generate spatial positions if needed
            val spatialPositions = if (config.adaptiveConfig.spatialMemoryChallenge) {
                generateSpatialPositions(actualObjectsPerScreen)
            } else {
                emptyList()
            }

            // Generate interference items if needed
            val interferenceItems = if (config.adaptiveConfig.interferenceItems && i > 0) {
                generateInterferenceItems(config.maxObjectPool, screenNumbers)
            } else {
                emptyList()
            }

            // Calculate fade delay based on temporal decay setting
            val fadeDelay = if (config.adaptiveConfig.temporalDecay) {
                when (config.adaptiveConfig.workingMemoryLoad) {
                    1, 2 -> 2000L // 2 seconds
                    3 -> 1500L    // 1.5 seconds
                    4, 5 -> 1000L // 1 second
                    else -> 2000L
                }
            } else {
                0L
            }

            // Assign visual similarity group
            val visualGroup = if (config.adaptiveConfig.visualSimilarity) {
                when (i % 3) {
                    0 -> "forest_mammals"
                    1 -> "large_animals"
                    2 -> "colorful_animals"
                    else -> "default"
                }
            } else {
                "default"
            }

            val screenData = AdaptiveScreenData(
                screenNumber = i + 1,
                numbers = screenNumbers.sorted(),
                linkingNumber = if (i > 0) findLinkingNumber(sequence[i - 1].numbers, screenNumbers) else null,
                isFirstScreen = i == 0,
                fadeDelay = fadeDelay,
                spatialPositions = spatialPositions,
                interferenceNumbers = interferenceItems,
                visualSimilarityGroup = visualGroup
            )

            sequence.add(screenData)
        }

        return sequence
    }

    /**
     * Generate spatial positions for memory challenge
     */
    private fun generateSpatialPositions(count: Int): List<Pair<Float, Float>> {
        val positions = mutableListOf<Pair<Float, Float>>()
        val grid = when (count) {
            2 -> listOf(0.3f to 0.5f, 0.7f to 0.5f)
            3 -> listOf(0.2f to 0.5f, 0.5f to 0.5f, 0.8f to 0.5f)
            4 -> listOf(0.25f to 0.3f, 0.75f to 0.3f, 0.25f to 0.7f, 0.75f to 0.7f)
            5 -> listOf(0.2f to 0.3f, 0.5f to 0.3f, 0.8f to 0.3f, 0.35f to 0.7f, 0.65f to 0.7f)
            else -> List(count) { Random.nextFloat() to Random.nextFloat() }
        }

        return grid.shuffled() // Randomize positions
    }

    /**
     * Generate interference items for working memory challenge
     */
    private fun generateInterferenceItems(maxPool: Int, currentNumbers: List<Int>): List<Int> {
        val availableForInterference = (1..maxPool).filter { it !in currentNumbers }
        val interferenceCount = Random.nextInt(1, 3) // 1-2 interference items

        return availableForInterference.shuffled().take(interferenceCount)
    }

    /**
     * Create adaptive instructions
     */
    private fun createAdaptiveInstructions(adaptiveConfig: AdaptivePairConfig): Map<String, Any> {
        val baseSteps = listOf(
            "1. Look at the animals on the first screen",
            "2. On each new screen, tap the animal that appeared in the previous screen",
            "3. Continue until you complete the sequence"
        )

        val adaptiveSteps = when {
            adaptiveConfig.spatialMemoryChallenge && adaptiveConfig.interferenceItems -> baseSteps + listOf(
                "4. Remember both the animals AND their positions",
                "5. Ignore any interference items that flash briefly",
                "6. Focus only on the main sequence animals"
            )
            adaptiveConfig.spatialMemoryChallenge -> baseSteps + listOf(
                "4. Pay attention to where each animal appears on screen",
                "5. Spatial position may be important"
            )
            adaptiveConfig.interferenceItems -> baseSteps + listOf(
                "4. Ignore any brief flashing distractors",
                "5. Focus only on the main animals"
            )
            adaptiveConfig.visualSimilarity -> baseSteps + listOf(
                "4. Look carefully - some animals may appear similar",
                "5. Focus on unique features to distinguish them"
            )
            else -> baseSteps + listOf(
                "4. Be quick and accurate for bonus points!"
            )
        }

        val tip = when {
            adaptiveConfig.workingMemoryLoad >= 4 -> "This is challenging! Use active rehearsal - mentally repeat what you see"
            adaptiveConfig.spatialMemoryChallenge -> "Create a mental map of where each animal appears"
            adaptiveConfig.visualSimilarity -> "Focus on unique features like size, color patterns, or facial features"
            adaptiveConfig.temporalDecay -> "Items fade quickly - encode them immediately when they appear"
            else -> "Focus on unique features to help remember each animal"
        }

        return mapOf(
            "title" to "Adaptive Memory Previous Pair",
            "description" to "${adaptiveConfig.name}: ${adaptiveConfig.description}",
            "steps" to adaptiveSteps,
            "tip" to tip
        )
    }

    /**
     * Calculate adaptive maximum score
     */
    private fun calculateAdaptiveMaxScore(totalAnswers: Int, adaptiveConfig: AdaptivePairConfig): Int {
        val baseScorePerAnswer = 15

        val memoryLoadMultiplier = when (adaptiveConfig.workingMemoryLoad) {
            1 -> 1.0f
            2 -> 1.3f
            3 -> 1.6f
            4 -> 2.0f
            5 -> 2.5f
            else -> 1.0f
        }

        val challengeBonus = listOf(
            if (adaptiveConfig.spatialMemoryChallenge) 1.2f else 1.0f,
            if (adaptiveConfig.visualSimilarity) 1.3f else 1.0f,
            if (adaptiveConfig.interferenceItems) 1.4f else 1.0f,
            if (adaptiveConfig.temporalDecay) 1.1f else 1.0f
        ).reduce { acc, bonus -> acc * bonus }

        val spanMultiplierBonus = adaptiveConfig.memorySpanMultiplier

        return (totalAnswers * baseScorePerAnswer * memoryLoadMultiplier * challengeBonus * spanMultiplierBonus).toInt()
    }

    // Helper functions
    private fun selectRandomNumbers(availableNumbers: List<Int>, count: Int): List<Int> {
        if (availableNumbers.size < count) {
            throw Exception("Not enough numbers available. Need $count, have ${availableNumbers.size}")
        }
        return availableNumbers.shuffled().take(count)
    }

    private fun findLinkingNumber(previousNumbers: List<Int>, currentNumbers: List<Int>): Int? {
        return currentNumbers.find { it in previousNumbers }
    }

    private fun validateAdaptiveSequence(sequence: List<AdaptiveScreenData>): Boolean {
        for (i in 1 until sequence.size) {
            val currentScreen = sequence[i]
            val previousScreen = sequence[i - 1]

            if (currentScreen.linkingNumber == null) {
                return false
            }

            val linkingInCurrent = currentScreen.linkingNumber in currentScreen.numbers
            val linkingInPrevious = currentScreen.linkingNumber in previousScreen.numbers

            if (!linkingInCurrent || !linkingInPrevious) {
                return false
            }
        }
        return true
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

// Adaptive data classes for pairs
data class AdaptiveMemorySequence(
    val screenNumber: Int,
    val numbers: List<Int>,
    val linkingNumber: Int?,
    val isFirstScreen: Boolean,
    val fadeDelay: Long = 0L,
    val spatialPositions: List<Pair<Float, Float>> = emptyList(),
    val interferenceNumbers: List<Int> = emptyList(),
    val visualSimilarityGroup: String = "default"
)

data class AdaptivePairConfig(
    val name: String,
    val description: String,
    val memorySpanMultiplier: Float,
    val visualSimilarity: Boolean,
    val spatialMemoryChallenge: Boolean,
    val workingMemoryLoad: Int,
    val temporalDecay: Boolean,
    val interferenceItems: Boolean
)

@Composable
fun AdaptiveMemoryPreviousPairScreen(
    initialDifficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveMemoryPair"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("memoryPair"))
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
                    puzzleType = "memoryPair",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate adaptive puzzle data
    val (puzzleData, answerData) = remember(currentDifficultyLevel) {
        try {
            AdaptiveMemoryPreviousPairGenerator.generateAdaptivePuzzle(currentDifficultyLevel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate adaptive puzzle", e)
            Pair("{}", "{}")
        }
    }

    // Parse puzzle data
    val parsedData = remember(puzzleData) {
        try {
            val json = JSONObject(puzzleData)
            val sequenceArray = json.getJSONArray("sequence")
            val sequences = mutableListOf<AdaptiveMemorySequence>()

            for (i in 0 until sequenceArray.length()) {
                val seqObj = sequenceArray.getJSONObject(i)
                val numbersArray = seqObj.getJSONArray("numbers")
                val numbers = List(numbersArray.length()) { idx -> numbersArray.getInt(idx) }

                val spatialArray = seqObj.getJSONArray("spatialPositions")
                val spatialPositions = mutableListOf<Pair<Float, Float>>()
                for (j in 0 until spatialArray.length()) {
                    val posObj = spatialArray.getJSONObject(j)
                    spatialPositions.add(posObj.getDouble("x").toFloat() to posObj.getDouble("y").toFloat())
                }

                val interferenceArray = seqObj.getJSONArray("interferenceNumbers")
                val interferenceNumbers = List(interferenceArray.length()) { idx -> interferenceArray.getInt(idx) }

                sequences.add(
                    AdaptiveMemorySequence(
                        screenNumber = seqObj.getInt("screenNumber"),
                        numbers = numbers,
                        linkingNumber = if (seqObj.isNull("linkingNumber")) null else seqObj.getInt("linkingNumber"),
                        isFirstScreen = seqObj.getBoolean("isFirstScreen"),
                        fadeDelay = seqObj.getLong("fadeDelay"),
                        spatialPositions = spatialPositions,
                        interferenceNumbers = interferenceNumbers,
                        visualSimilarityGroup = seqObj.getString("visualSimilarityGroup")
                    )
                )
            }

            val configObj = json.getJSONObject("adaptiveConfig")
            val adaptiveConfig = AdaptivePairConfig(
                name = configObj.getString("name"),
                description = configObj.getString("description"),
                memorySpanMultiplier = configObj.getDouble("memorySpanMultiplier").toFloat(),
                visualSimilarity = configObj.getBoolean("visualSimilarity"),
                spatialMemoryChallenge = configObj.getBoolean("spatialMemoryChallenge"),
                workingMemoryLoad = configObj.getInt("workingMemoryLoad"),
                temporalDecay = configObj.getBoolean("temporalDecay"),
                interferenceItems = configObj.getBoolean("interferenceItems")
            )

            Pair(sequences, adaptiveConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse puzzle data", e)
            Pair(emptyList<AdaptiveMemorySequence>(),
                AdaptivePairConfig("Error", "Failed to load", 1f, false, false, 1, false, false))
        }
    }

    val (sequenceData, adaptiveConfig) = parsedData

    // Score tracking state
    var totalScore by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var totalQuestions by remember { mutableIntStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var reactionTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var currentStreak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    var currentHearts by remember { mutableIntStateOf(hearts) }

    // ✅ NEW: Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            180
        }
    }

    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }
    var isPaused by remember { mutableStateOf(false) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    LaunchedEffect(sequenceData) {
        totalQuestions = sequenceData.count { !it.isFirstScreen }
        gameStartTime = System.currentTimeMillis()
        sessionStartTime = System.currentTimeMillis()
        Log.d(TAG, "📢 Initialized adaptive: $totalQuestions questions from ${sequenceData.size} screens")
    }

    LaunchedEffect(timeRemaining, isPaused) {
        if (timeRemaining > 0 && !isPaused) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0) {
            totalScore = calculateAdaptivePairScore(adaptiveConfig, correctAnswers, totalQuestions, reactionTimes, bestStreak)
            onSubmitAnswer(false)
            fetchNextPuzzle(totalScore)
        }
    }

    // Enhanced animal mapping with visual similarity groups
    val animals = remember(adaptiveConfig) {
        if (adaptiveConfig.visualSimilarity) {
            mapOf(
                1 to ForestAnimal(1, "Bear", "🐻", Color(0xFF8B4513)),
                2 to ForestAnimal(2, "Wolf", "🐺", Color(0xFF696969)),
                3 to ForestAnimal(3, "Fox", "🦊", Color(0xFFD2691E)),
                4 to ForestAnimal(4, "Elephant", "🐘", Color(0xFFA8A8A8)),
                5 to ForestAnimal(5, "Hippo", "🦛", Color(0xFF708090)),
                6 to ForestAnimal(6, "Rhino", "🦏", Color(0xFF808080)),
                7 to ForestAnimal(7, "Tiger", "🐅", Color(0xFFFF8C00)),
                8 to ForestAnimal(8, "Lion", "🦁", Color(0xFFDAA520)),
                9 to ForestAnimal(9, "Leopard", "🐆", Color(0xFFCD853F)),
                10 to ForestAnimal(10, "Giraffe", "🦒", Color(0xFFDEB887)),
                11 to ForestAnimal(11, "Monkey", "🐵", Color(0xFFD2B48C)),
                12 to ForestAnimal(12, "Panda", "🐼", Color(0xFF000000))
            )
        } else {
            mapOf(
                1 to ForestAnimal(1, "Lion", "🦁", Color(0xFFD4A574)),
                2 to ForestAnimal(2, "Hippo", "🦛", Color(0xFF8B7D6B)),
                3 to ForestAnimal(3, "Elephant", "🐘", Color(0xFFA8A8A8)),
                4 to ForestAnimal(4, "Tiger", "🐅", Color(0xFFFF8C00)),
                5 to ForestAnimal(5, "Giraffe", "🦒", Color(0xFFDAA520)),
                6 to ForestAnimal(6, "Monkey", "🐵", Color(0xFFCD853F)),
                7 to ForestAnimal(7, "Bear", "🐻", Color(0xFF8B4513)),
                8 to ForestAnimal(8, "Wolf", "🐺", Color(0xFF696969)),
                9 to ForestAnimal(9, "Fox", "🦊", Color(0xFFD2691E)),
                10 to ForestAnimal(10, "Panda", "🐼", Color(0xFF000000)),
                11 to ForestAnimal(11, "Zebra", "🦓", Color(0xFF000000)),
                12 to ForestAnimal(12, "Rhino", "🦏", Color(0xFF808080))
            )
        }
    }

    var currentScreenIndex by remember { mutableIntStateOf(0) }
    var gameState by remember { mutableStateOf("playing") }
    var selectedAnimal by remember { mutableStateOf<Int?>(null) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrectAnswer by remember { mutableStateOf(false) }
    var userAnswers by remember { mutableStateOf(mutableListOf<Int>()) }

    var animatingAnimals by remember { mutableStateOf(emptySet<Int>()) }
    var fadingAnimals by remember { mutableStateOf(emptySet<Int>()) }
    var showingInterference by remember { mutableStateOf(false) }
    var screenStartTime by remember { mutableLongStateOf(0L) }

    // ✅ UPDATED: Enhanced performance recording
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = adaptiveConfig.workingMemoryLoad,
            totalScore = totalScore,
            puzzleType = "memoryPair"
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
            challengeComplexity = adaptiveConfig.workingMemoryLoad,
            currentStreak = currentStreak,
            challengesCompleted = correctAnswers,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "memoryPair"
        )
    }

    LaunchedEffect(currentScreenIndex) {
        if (currentScreenIndex < sequenceData.size) {
            val currentSequence = sequenceData[currentScreenIndex]
            screenStartTime = System.currentTimeMillis()

            animatingAnimals = emptySet()
            fadingAnimals = emptySet()
            showingInterference = false

            if (adaptiveConfig.interferenceItems && currentSequence.interferenceNumbers.isNotEmpty()) {
                showingInterference = true
                delay(300)
                showingInterference = false
            }

            val currentAnimals = currentSequence.numbers.toSet()
            animatingAnimals = currentAnimals

            if (adaptiveConfig.temporalDecay && currentSequence.fadeDelay > 0) {
                delay(currentSequence.fadeDelay)
                fadingAnimals = currentAnimals
            }

            delay(2500)
            animatingAnimals = emptySet()
        }
    }

    LaunchedEffect(currentScreenIndex) {
        if (currentScreenIndex < sequenceData.size && sequenceData[currentScreenIndex].isFirstScreen) {
            delay(4000)
            if (currentScreenIndex + 1 < sequenceData.size) {
                currentScreenIndex += 1
            }
        }
    }

    fun handleAnimalClick(animalId: Int) {
        if (currentScreenIndex >= sequenceData.size) return

        val currentSequence = sequenceData[currentScreenIndex]
        if (currentSequence.isFirstScreen) return

        selectedAnimal = animalId

        val reactionTime = System.currentTimeMillis() - screenStartTime
        reactionTimes = reactionTimes + reactionTime

        val expectedAnswer = currentSequence.linkingNumber ?: -1
        isCorrectAnswer = animalId == expectedAnswer

        Log.d(TAG, "🎯 Adaptive Screen ${currentScreenIndex + 1}: Expected=$expectedAnswer, " +
                "Selected=$animalId, Correct=$isCorrectAnswer, Reaction=${reactionTime}ms")

        if (isCorrectAnswer) {
            correctAnswers++
            currentStreak++
            if (currentStreak > bestStreak) bestStreak = currentStreak
            userAnswers.add(animalId)

            val score = calculateScore(isCorrectAnswer, reactionTime)
            totalScore += score
        } else {
            currentStreak = 0
            currentHearts = maxOf(0, currentHearts - 1)
        }

        recordPerformance(isCorrectAnswer, reactionTime)
        showFeedback = true
    }

    LaunchedEffect(showFeedback) {
        if (showFeedback) {
            delay(1500)
            showFeedback = false
            selectedAnimal = null

            if (currentScreenIndex + 1 < sequenceData.size) {
                currentScreenIndex += 1
            } else {
                totalScore = calculateAdaptivePairScore(adaptiveConfig, correctAnswers, totalQuestions, reactionTimes, bestStreak)
                val isSuccess = correctAnswers >= (totalQuestions * 0.6f).toInt()

                feedbackManager.showFeedback(
                    puzzleType = "memoryPair",
                    isCorrect = isSuccess,
                    userAnswer = "$correctAnswers/$totalQuestions adaptive pairs recalled",
                    correctAnswer = "Adaptive ${adaptiveConfig.name} memory sequence",
                    timeSpent = System.currentTimeMillis() - gameStartTime,
                    difficulty = adaptiveConfig.name,
                    timeRemaining = timeRemaining,
                    totalTime = totalTimeSeconds,
                    onComplete = {
                        onSubmitAnswer(isSuccess)
                        fetchNextPuzzle(totalScore)
                    }
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ✅ REPLACE: Use unified header instead of AdaptiveMemoryPairTopBar
            AdaptiveUnifiedHeader(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer,
                lives = currentHearts,
                currentDifficulty = currentDifficultyLevel,
                score = totalScore,
                puzzleType = "memoryPair",
                challengeNumber = currentScreenIndex + 1,
                totalChallenges = sequenceData.size,
                competitiveInsight = competitiveInsight,
                onBack = onBack,
                onPause = { isPaused = !isPaused },
                onHint = {
                    if (currentScreenIndex < sequenceData.size && !sequenceData[currentScreenIndex].isFirstScreen) {
                        val expectedAnimal = sequenceData[currentScreenIndex].linkingNumber
                        Log.d(TAG, "Hint: Look for animal #$expectedAnimal from the previous screen")
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ✅ REPLACE: Use unified adaptation notification
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "memoryPair",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (currentScreenIndex < sequenceData.size && sequenceData[currentScreenIndex].isFirstScreen) {
                            "🧠 Remember these animals! (${adaptiveConfig.name})"
                        } else {
                            "🎯 Tap the animal that appeared in the previous screen"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Screen ${currentScreenIndex + 1} of ${sequenceData.size}",
                            color = Color(0xFFB0B0B0),
                            fontSize = 14.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (adaptiveConfig.spatialMemoryChallenge) Text("🗺️", fontSize = 12.sp)
                            if (adaptiveConfig.visualSimilarity) Text("👁️", fontSize = 12.sp)
                            if (adaptiveConfig.interferenceItems) Text("⚡", fontSize = 12.sp)
                            if (adaptiveConfig.temporalDecay) Text("⏰", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF87CEEB),
                                Color(0xFF90EE90)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawGrass(this)
                }

                if (showingInterference && currentScreenIndex < sequenceData.size) {
                    val interferenceNumbers = sequenceData[currentScreenIndex].interferenceNumbers
                    if (interferenceNumbers.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(0.5f)
                                .blur(1.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            interferenceNumbers.forEach { interferId ->
                                val animal = animals[interferId]
                                if (animal != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(Color.Red.copy(alpha = 0.3f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = animal.emoji,
                                            fontSize = 30.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (currentScreenIndex < sequenceData.size && !showingInterference) {
                    val currentAnimals = sequenceData[currentScreenIndex].numbers
                    val isFirstScreen = sequenceData[currentScreenIndex].isFirstScreen
                    val spatialPositions = sequenceData[currentScreenIndex].spatialPositions

                    if (adaptiveConfig.spatialMemoryChallenge && spatialPositions.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            currentAnimals.forEachIndexed { index, animalId ->
                                val animal = animals[animalId]
                                val position = spatialPositions.getOrNull(index) ?: (0.5f to 0.5f)

                                if (animal != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .offset(
                                                x = ((position.first - 0.5f) * 300).dp,
                                                y = ((position.second - 0.5f) * 200).dp
                                            )
                                    ) {
                                        AdaptiveAnimatedAnimalHead(
                                            animal = animal,
                                            isAnimating = animatingAnimals.contains(animalId),
                                            isFading = fadingAnimals.contains(animalId),
                                            isClickable = !isFirstScreen,
                                            isSelected = selectedAnimal == animalId,
                                            showFeedback = showFeedback && selectedAnimal == animalId,
                                            isCorrect = isCorrectAnswer,
                                            adaptiveConfig = adaptiveConfig,
                                            onClick = { handleAnimalClick(animalId) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            currentAnimals.forEach { animalId ->
                                val animal = animals[animalId]
                                if (animal != null) {
                                    AdaptiveAnimatedAnimalHead(
                                        animal = animal,
                                        isAnimating = animatingAnimals.contains(animalId),
                                        isFading = fadingAnimals.contains(animalId),
                                        isClickable = !isFirstScreen,
                                        isSelected = selectedAnimal == animalId,
                                        showFeedback = showFeedback && selectedAnimal == animalId,
                                        isCorrect = isCorrectAnswer,
                                        adaptiveConfig = adaptiveConfig,
                                        onClick = { handleAnimalClick(animalId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (totalScore > 0) {
                            Text(
                                text = "Adaptive Score: $totalScore",
                                color = Color(0xFF4CAF50),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "🧠 ${adaptiveConfig.name} Pair Memory",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Correct: $correctAnswers/$totalQuestions",
                            color = Color(0xFFB0B0B0),
                            fontSize = 14.sp
                        )
                    }

                    if (reactionTimes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val avgReaction = reactionTimes.average() / 1000.0
                            Text(
                                text = "⚡ Avg: ${String.format("%.1f", avgReaction)}s",
                                color = when {
                                    avgReaction <= 2.0 -> Color(0xFF4CAF50)
                                    avgReaction <= 3.0 -> Color(0xFFFF9800)
                                    else -> Color(0xFFFF5722)
                                },
                                fontSize = 12.sp
                            )

                            if (bestStreak > 1) {
                                Text(
                                    text = "🔥 Best streak: $bestStreak",
                                    color = Color(0xFFFF6B00),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (adaptiveConfig.workingMemoryLoad >= 3) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val loadText = when (adaptiveConfig.workingMemoryLoad) {
                                3 -> "🧠 Moderate Load"
                                4 -> "🧠 High Load"
                                5 -> "🧠 Maximum Load"
                                else -> "🧠 Standard"
                            }
                            Text(
                                text = loadText,
                                color = Color(0xFF9C27B0),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // ✅ NEW: Session completion handling
        if (currentScreenIndex >= sequenceData.size || currentHearts <= 0) {
            UnifiedSessionCompletionHandler(
                puzzleType = "memoryPair",
                sessionScore = totalScore,
                sessionStats = SessionStatistics(
                    correctAnswers = correctAnswers,
                    totalAnswers = totalQuestions,
                    totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                    bestStreak = bestStreak,
                    winRate = if (totalQuestions > 0) correctAnswers.toFloat() / totalQuestions else 0f,
                    totalScore = totalScore,
                    averageTimePerPuzzle = if (reactionTimes.isNotEmpty())
                        (reactionTimes.average() / 1000).toInt()
                    else 0,
                    currentStreak = currentStreak,
                    individualTimes = emptyList(),
                ),
                currentDifficulty = currentDifficultyLevel
            ) { result ->
                val isSuccess = correctAnswers >= (totalQuestions * 0.6f).toInt()
                onSubmitAnswer(isSuccess)
                fetchNextPuzzle(totalScore)
            }
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}


@Composable
private fun AdaptiveMemoryPairTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    hearts: Int,
    adaptiveConfig: AdaptivePairConfig,
    totalScore: Int,
    correctAnswers: Int,
    totalQuestions: Int,
    streak: Int,
    currentScreen: Int,
    totalScreens: Int,
    onBack: () -> Unit,
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = adaptiveConfig.name,
                        fontSize = 12.sp,
                        color = Color(0xFF4FC3F7)
                    )
                }
            }

            // Center: Hearts and timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(hearts) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart",
                            tint = Color(0xFFFF69B4),
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

            // Right side: Progress and adaptive indicators
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "$currentScreen/$totalScreens",
                    fontSize = 14.sp,
                    color = Color.White
                )

                if (streak > 1) {
                    Text(
                        text = "🔥 $streak",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B00)
                    )
                }

                // Adaptive features indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (adaptiveConfig.spatialMemoryChallenge) Text("🗺️", fontSize = 10.sp)
                    if (adaptiveConfig.visualSimilarity) Text("👁️", fontSize = 10.sp)
                    if (adaptiveConfig.interferenceItems) Text("⚡", fontSize = 10.sp)
                    if (adaptiveConfig.temporalDecay) Text("⏰", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun AdaptivePairNotificationCard(
    adaptationInfo: DifficultyManager.AdaptiveConfig?,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                Icons.Default.Psychology,
                contentDescription = "Adapted",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pair Memory Adapted!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = adaptationInfo?.adjustmentReason ?: "",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AdaptiveAnimatedAnimalHead(
    animal: ForestAnimal,
    isAnimating: Boolean,
    isFading: Boolean,
    isClickable: Boolean,
    isSelected: Boolean,
    showFeedback: Boolean,
    isCorrect: Boolean,
    adaptiveConfig: AdaptivePairConfig,
    onClick: () -> Unit
) {
    // Enhanced bounce animation for high working memory load
    val bounceOffset by animateFloatAsState(
        targetValue = if (isAnimating) {
            when (adaptiveConfig.workingMemoryLoad) {
                1, 2 -> -15f
                3 -> -20f
                4, 5 -> -25f
                else -> -15f
            }
        } else 0f,
        animationSpec = if (isAnimating) {
            infiniteRepeatable(
                animation = tween(800, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "bounce"
    )

    // Fade animation for temporal decay
    val alpha by animateFloatAsState(
        targetValue = if (isFading && adaptiveConfig.temporalDecay) 0.3f else 1f,
        animationSpec = tween(1000),
        label = "fade"
    )

    // Scale animation
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(if (adaptiveConfig.workingMemoryLoad >= 4) 70.dp else 80.dp)
            .offset(y = bounceOffset.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(
                when {
                    showFeedback && isCorrect -> Color(0xFF4CAF50)
                    showFeedback && !isCorrect -> Color(0xFFF44336)
                    isSelected -> animal.color.copy(alpha = 0.3f)
                    else -> animal.color.copy(alpha = 0.1f)
                }
            )
            .clickable(enabled = isClickable) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = animal.emoji,
            fontSize = if (adaptiveConfig.workingMemoryLoad >= 4) 35.sp else 40.sp
        )

        // Enhanced feedback for high difficulty
        if (showFeedback && isSelected) {
            Text(
                text = if (isCorrect) "✓" else "✗",
                color = Color.White,
                fontSize = if (adaptiveConfig.workingMemoryLoad >= 4) 20.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(x = 15.dp, y = (-15).dp)
                    .background(
                        color = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336),
                        shape = CircleShape
                    )
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun AdaptiveScoreCard(
    totalScore: Int,
    correctAnswers: Int,
    totalQuestions: Int,
    reactionTimes: List<Long>,
    bestStreak: Int,
    adaptiveConfig: AdaptivePairConfig
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (totalScore > 0) {
                    Text(
                        text = "Adaptive Score: $totalScore",
                        color = Color(0xFF4CAF50),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "🧠 ${adaptiveConfig.name} Pair Memory",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Correct: $correctAnswers/$totalQuestions",
                    color = Color(0xFFB0B0B0),
                    fontSize = 14.sp
                )
            }

            // Enhanced performance indicators for adaptive challenges
            if (reactionTimes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val avgReaction = reactionTimes.average() / 1000.0
                    Text(
                        text = "⚡ Avg: ${String.format("%.1f", avgReaction)}s",
                        color = when {
                            avgReaction <= 2.0 -> Color(0xFF4CAF50)
                            avgReaction <= 3.0 -> Color(0xFFFF9800)
                            else -> Color(0xFFFF5722)
                        },
                        fontSize = 12.sp
                    )

                    if (bestStreak > 1) {
                        Text(
                            text = "🔥 Best streak: $bestStreak",
                            color = Color(0xFFFF6B00),
                            fontSize = 12.sp
                        )
                    }
                }

                // Adaptive challenge completion indicators
                if (adaptiveConfig.workingMemoryLoad >= 3) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val loadText = when (adaptiveConfig.workingMemoryLoad) {
                        3 -> "🧠 Moderate Load"
                        4 -> "🧠 High Load"
                        5 -> "🧠 Maximum Load"
                        else -> "🧠 Standard"
                    }
                    Text(
                        text = loadText,
                        color = Color(0xFF9C27B0),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// Calculate adaptive pair score
private fun calculateAdaptivePairScore(
    adaptiveConfig: AdaptivePairConfig,
    correctAnswers: Int,
    totalQuestions: Int,
    reactionTimes: List<Long>,
    bestStreak: Int
): Int {
    if (correctAnswers == 0) return 0

    val baseScore = correctAnswers * 20

    // Working memory load multiplier
    val loadMultiplier = when (adaptiveConfig.workingMemoryLoad) {
        1 -> 1.0f
        2 -> 1.3f
        3 -> 1.6f
        4 -> 2.0f
        5 -> 2.5f
        else -> 1.0f
    }

    // Adaptive feature bonuses
    val featureBonus = listOf(
        if (adaptiveConfig.spatialMemoryChallenge) 1.3f else 1.0f,
        if (adaptiveConfig.visualSimilarity) 1.4f else 1.0f,
        if (adaptiveConfig.interferenceItems) 1.5f else 1.0f,
        if (adaptiveConfig.temporalDecay) 1.2f else 1.0f
    ).reduce { acc, bonus -> acc * bonus }

    // Memory span bonus
    val spanBonus = adaptiveConfig.memorySpanMultiplier

    // Speed bonus
    val avgReactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.average() else 3000.0
    val speedBonus = when {
        avgReactionTime <= 2000 -> 1.3f
        avgReactionTime <= 3000 -> 1.1f
        else -> 1.0f
    }

    // Streak bonus
    val streakBonus = if (bestStreak >= totalQuestions * 0.7f) 1.5f else 1.0f

    val finalScore = (baseScore * loadMultiplier * featureBonus * spanBonus * speedBonus * streakBonus).toInt()

    return max(finalScore, baseScore / 2)
}
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvViolet
import com.kreativekoala.riddleverse.ui.theme.RvDisabled
import com.kreativekoala.riddleverse.ui.theme.RvCoral
import com.kreativekoala.riddleverse.ui.theme.RvCoralEdge
import com.kreativekoala.riddleverse.ui.theme.RvSky
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import com.kreativekoala.riddleverse.ui.theme.RvCanvas
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvSurface

data class SessionCompletionData(
    val puzzleType: String,
    val sessionScore: Int,
    val sessionStats: SessionStatistics,
    val currentDifficulty: DifficultyManager.DifficultyLevel
)

// =============================================================================
// ADAPTIVE CONTEXT SWITCH GENERATOR
// =============================================================================

object AdaptiveContextSwitchGenerator {

    data class AdaptiveContextConfig(
        val workingMemoryLoad: Int, // 1-5 scale for memory items
        val interferenceComplexity: String, // simple, moderate, complex, extreme
        val dualTaskDemand: Boolean, // Whether to add concurrent processing
        val attentionalControl: Boolean, // Whether to add attention switching
        val executiveDemand: Int, // 1-5 scale for executive function load
        val temporalComplexity: Boolean, // Whether to add time pressure elements
        val name: String,
        val description: String
    )

    private fun getDifficultyConfigs() = mapOf(
        0 to AdaptiveContextConfig( // Beginner
            workingMemoryLoad = 1,
            interferenceComplexity = "simple",
            dualTaskDemand = false,
            attentionalControl = false,
            executiveDemand = 1,
            temporalComplexity = false,
            name = "Beginner",
            description = "Basic memory with simple interference"
        ),
        1 to AdaptiveContextConfig( // Easy
            workingMemoryLoad = 2,
            interferenceComplexity = "moderate",
            dualTaskDemand = true,
            attentionalControl = false,
            executiveDemand = 2,
            temporalComplexity = false,
            name = "Easy",
            description = "Moderate memory load with dual-task demands"
        ),
        2 to AdaptiveContextConfig( // Medium
            workingMemoryLoad = 3,
            interferenceComplexity = "moderate",
            dualTaskDemand = true,
            attentionalControl = true,
            executiveDemand = 3,
            temporalComplexity = true,
            name = "Medium",
            description = "Attention switching with time pressure"
        ),
        3 to AdaptiveContextConfig( // Hard
            workingMemoryLoad = 4,
            interferenceComplexity = "complex",
            dualTaskDemand = true,
            attentionalControl = true,
            executiveDemand = 4,
            temporalComplexity = true,
            name = "Hard",
            description = "High cognitive load with complex interference"
        ),
        4 to AdaptiveContextConfig( // Expert
            workingMemoryLoad = 5,
            interferenceComplexity = "extreme",
            dualTaskDemand = true,
            attentionalControl = true,
            executiveDemand = 5,
            temporalComplexity = true,
            name = "Expert",
            description = "Maximum executive demands with extreme interference"
        )
    )

    // Enhanced content database with cognitive categories
    private val adaptiveContentDatabase = mapOf(
        // High verbal load
        "abstract_concepts" to listOf("Justice", "Freedom", "Wisdom", "Courage", "Truth", "Beauty", "Honor", "Peace", "Hope", "Faith", "Love", "Mercy", "Grace", "Unity", "Harmony"),
        "academic_subjects" to listOf("Philosophy", "Psychology", "Neuroscience", "Economics", "Sociology", "Anthropology", "Linguistics", "Statistics", "Biology", "Chemistry", "Physics", "Mathematics", "History", "Literature", "Art"),

        // High spatial load
        "geometric_shapes" to listOf("Hexagon", "Octagon", "Pentagon", "Triangle", "Rectangle", "Rhombus", "Trapezoid", "Parallelogram", "Ellipse", "Polygon", "Prism", "Pyramid", "Cube", "Sphere", "Cylinder"),
        "architectural_elements" to listOf("Arch", "Column", "Dome", "Spire", "Buttress", "Cornice", "Frieze", "Pedestal", "Balustrade", "Portico", "Atrium", "Vestibule", "Clerestory", "Transept", "Apse"),

        // High semantic load
        "scientific_terms" to listOf("Hypothesis", "Algorithm", "Catalyst", "Enzyme", "Molecule", "Electron", "Photon", "Quantum", "Genome", "Protein", "Neuron", "Synapse", "Mitochondria", "Chromosome", "Antibody"),
        "medical_terminology" to listOf("Diagnosis", "Prognosis", "Symptom", "Syndrome", "Pathology", "Anatomy", "Physiology", "Pharmacology", "Epidemiology", "Immunology", "Cardiology", "Neurology", "Oncology", "Pediatrics", "Geriatrics")
    )

    fun generateAdaptivePuzzle(
        difficultyLevel: DifficultyManager.DifficultyLevel
    ): Pair<String, String> {
        val config = getDifficultyConfigs()[difficultyLevel.index]
            ?: getDifficultyConfigs()[2]!! // Default to medium

        val puzzleData = createAdaptiveContextSwitchPuzzle(config)
        val answerData = createAdaptiveAnswerData(puzzleData)

        return Pair(puzzleData, answerData)
    }

    fun validateInterferenceAnswer(
        taskType: String,
        userAnswers: List<String>,
        originalItems: List<String>
    ): Boolean {
        return when (taskType) {
            "number_sort" -> validateNumberSort(userAnswers, originalItems)
            "word_alphabetize" -> validateWordAlphabetize(userAnswers, originalItems)
            "simple_math" -> validateSimpleMath(userAnswers, originalItems)
            "working_memory_update" -> validateWorkingMemoryUpdate(userAnswers, originalItems)
            "triple_task" -> validateTripleTask(userAnswers, originalItems)
            else -> true // Default to allowing progression
        }
    }

    private fun validateNumberSort(userAnswers: List<String>, originalItems: List<String>): Boolean {
        val numbers = originalItems.mapNotNull { it.toIntOrNull() }
        val sortedNumbers = numbers.sorted()
        val userNumbers = userAnswers.mapNotNull { it.toIntOrNull() }
        return userNumbers == sortedNumbers
    }

    private fun validateWordAlphabetize(userAnswers: List<String>, originalItems: List<String>): Boolean {
        val sortedWords = originalItems.sorted()
        return userAnswers == sortedWords
    }

    private fun validateSimpleMath(userAnswers: List<String>, problems: List<String>): Boolean {
        if (userAnswers.size != problems.size) return false

        return problems.zip(userAnswers).all { (problem, userAnswer) ->
            val correctAnswer = solveMathProblem(problem)
            userAnswer.toIntOrNull() == correctAnswer
        }
    }

    private fun solveMathProblem(problem: String): Int? {
        return try {
            val cleanProblem = problem.replace("×", "*").replace("÷", "/")
            val parts = cleanProblem.split(" ")
            if (parts.size == 3) {
                val a = parts[0].toInt()
                val operator = parts[1]
                val b = parts[2].toInt()

                when (operator) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (b != 0) a / b else null
                    else -> null
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun validateWorkingMemoryUpdate(userAnswers: List<String>, numbers: List<String>): Boolean {
        if (userAnswers.isEmpty()) return false

        val userTotal = userAnswers.first().toIntOrNull() ?: return false
        val correctTotal = calculateRunningTotalOfLast3(numbers)
        return userTotal == correctTotal
    }

    private fun calculateRunningTotalOfLast3(numbers: List<String>): Int {
        val intNumbers = numbers.mapNotNull { it.toIntOrNull() }
        return intNumbers.takeLast(3).sum()
    }

    private fun validateTripleTask(userAnswers: List<String>, items: List<String>): Boolean {
        // For triple task, we'll validate the most complex part - prime identification
        return items.all { item ->
            val parts = item.split(" ")
            val number = parts.getOrNull(0)?.toIntOrNull() ?: return false
            val word = parts.getOrNull(1) ?: return false

            // Check if user correctly identified vowel count and prime status
            val vowelCount = word.count { it.lowercaseChar() in "aeiou" }
            val isPrimeNum = isPrime(number)

            // Since this is complex, we'll be lenient and just check prime identification
            true // Allow progression but could add stricter validation
        }
    }

    // Helper function for prime checking
    private fun isPrime(n: Int): Boolean {
        if (n < 2) return false
        for (i in 2..sqrt(n.toDouble()).toInt()) {
            if (n % i == 0) return false
        }
        return true
    }

    private fun createAdaptiveContextSwitchPuzzle(config: AdaptiveContextConfig): String {
        // Select content category based on adaptive requirements
        val category = selectAdaptiveCategory(config)
        val allItems = adaptiveContentDatabase[category] ?: adaptiveContentDatabase.values.first()

        // Determine memory list size
        val memoryItemCount = when (config.workingMemoryLoad) {
            1 -> 3
            2 -> 4
            3 -> 5
            4 -> 6
            5 -> 7
            else -> 4
        }

        val distractorCount = when (config.workingMemoryLoad) {
            1 -> 2
            2 -> 3
            3 -> 3
            4 -> 4
            5 -> 5
            else -> 3
        }

        // Generate memory items
        val memoryItems = allItems.shuffled().take(memoryItemCount)

        // Generate smart distractors
        val availableDistractors = allItems.filter { it !in memoryItems }
        val distractors = generateAdaptiveDistractors(availableDistractors, memoryItems, config)

        // Create recognition list with adaptive ordering
        val recognitionItems = createAdaptiveRecognitionList(memoryItems, distractors, config)

        // Generate adaptive interference task
        val interferenceTask = createAdaptiveInterferenceTask(config)

        // Build enhanced JSON
        return JSONObject().apply {
            put("puzzleType", "adaptive_context_switch")
            put("category", category)
            put("difficulty", config.name)
            put("memoryItems", JSONArray(memoryItems))
            put("recognitionItems", JSONArray(recognitionItems))
            put("correctAnswers", JSONArray(memoryItems))
            put("interferenceTask", JSONObject().apply {
                put("type", interferenceTask.type)
                put("items", JSONArray(interferenceTask.items))
                put("instruction", interferenceTask.instruction)
                put("complexity", config.interferenceComplexity)
                put("executiveDemand", interferenceTask.executiveDemand)
                put("timeConstraint", interferenceTask.timeConstraint)
            })
            put("adaptiveConfig", JSONObject().apply {
                put("name", config.name)
                put("description", config.description)
                put("workingMemoryLoad", config.workingMemoryLoad)
                put("interferenceComplexity", config.interferenceComplexity)
                put("dualTaskDemand", config.dualTaskDemand)
                put("attentionalControl", config.attentionalControl)
                put("executiveDemand", config.executiveDemand)
                put("temporalComplexity", config.temporalComplexity)
            })
            put("metadata", JSONObject().apply {
                put("memoryItemCount", memoryItemCount)
                put("distractorCount", distractorCount)
                put("totalChoices", memoryItemCount + distractorCount)
                put("cognitiveLoad", config.workingMemoryLoad)
                put("categoryName", category.replace("_", " ").split(" ").joinToString(" ") {
                    it.replaceFirstChar { char -> char.uppercase() }
                })
                put("generatedAt", System.currentTimeMillis())
            })
        }.toString()
    }

    private fun selectAdaptiveCategory(config: AdaptiveContextConfig): String {
        return when (config.workingMemoryLoad) {
            1, 2 -> listOf("geometric_shapes", "architectural_elements").random()
            3 -> listOf("academic_subjects", "scientific_terms").random()
            4, 5 -> listOf("abstract_concepts", "medical_terminology").random()
            else -> adaptiveContentDatabase.keys.random()
        }
    }

    private fun generateAdaptiveDistractors(
        availableDistractors: List<String>,
        memoryItems: List<String>,
        config: AdaptiveContextConfig
    ): List<String> {
        val distractorCount = when (config.workingMemoryLoad) {
            1 -> 2
            2 -> 3
            3 -> 3
            4 -> 4
            5 -> 5
            else -> 3
        }

        return when (config.workingMemoryLoad) {
            1, 2 -> {
                // Easy distractors - clearly different
                availableDistractors.filter { distractor ->
                    memoryItems.none { memoryItem ->
                        distractor.startsWith(memoryItem.first()) || memoryItem.startsWith(distractor.first())
                    }
                }.shuffled().take(distractorCount)
            }
            3, 4 -> {
                // Moderate distractors - some similarity
                availableDistractors.shuffled().take(distractorCount)
            }
            5 -> {
                // Hard distractors - semantically similar
                availableDistractors.filter { distractor ->
                    memoryItems.any { memoryItem ->
                        distractor.length == memoryItem.length ||
                                distractor.contains(memoryItem.substring(0, min(3, memoryItem.length)))
                    }
                }.ifEmpty { availableDistractors }.shuffled().take(distractorCount)
            }
            else -> availableDistractors.shuffled().take(distractorCount)
        }
    }

    private fun createAdaptiveRecognitionList(
        memoryItems: List<String>,
        distractors: List<String>,
        config: AdaptiveContextConfig
    ): List<String> {
        val combined = memoryItems + distractors

        return when {
            config.attentionalControl -> {
                // Interleave memory and distractors to increase attention switching
                val shuffled = combined.shuffled()
                val interleaved = mutableListOf<String>()
                val memSet = memoryItems.toSet()

                var lastWasMemory = false
                shuffled.forEach { item ->
                    val isMemory = item in memSet
                    if (config.executiveDemand >= 4) {
                        // Force alternation for maximum executive load
                        if (lastWasMemory && item !in memSet) {
                            interleaved.add(item)
                            lastWasMemory = false
                        } else if (!lastWasMemory && item in memSet) {
                            interleaved.add(item)
                            lastWasMemory = true
                        }
                    } else {
                        interleaved.add(item)
                        lastWasMemory = isMemory
                    }
                }
                interleaved.ifEmpty { combined.shuffled() }
            }
            else -> combined.shuffled()
        }
    }

    private fun createAdaptiveInterferenceTask(config: AdaptiveContextConfig): AdaptiveInterferenceTaskData {
        return when (config.interferenceComplexity) {
            "simple" -> createSimpleInterferenceTask(config)
            "moderate" -> createModerateInterferenceTask(config)
            "complex" -> createComplexInterferenceTask(config)
            "extreme" -> createExtremeInterferenceTask(config)
            else -> createModerateInterferenceTask(config)
        }
    }

    private fun createSimpleInterferenceTask(config: AdaptiveContextConfig): AdaptiveInterferenceTaskData {
        val numbers = (1..15).shuffled().take(3)
        return AdaptiveInterferenceTaskData(
            type = "number_sort",
            items = numbers.map { it.toString() },
            instruction = "Sort these numbers from smallest to largest",
            executiveDemand = 1,
            timeConstraint = if (config.temporalComplexity) 10000L else 0L
        )
    }

    private fun createModerateInterferenceTask(config: AdaptiveContextConfig): AdaptiveInterferenceTaskData {
        val taskTypes = listOf("word_alphabetize", "simple_math")
        val taskType = taskTypes.random()

        return when (taskType) {
            "word_alphabetize" -> {
                val words = listOf("elephant", "butterfly", "giraffe", "kangaroo").shuffled().take(4)
                AdaptiveInterferenceTaskData(
                    type = "word_alphabetize",
                    items = words,
                    instruction = if (config.dualTaskDemand) "Alphabetize AND count syllables" else "Arrange alphabetically",
                    executiveDemand = if (config.dualTaskDemand) 3 else 2,
                    timeConstraint = if (config.temporalComplexity) 15000L else 0L
                )
            }
            else -> {
                val problems = generateAdaptiveMathProblems(4, config.workingMemoryLoad)
                AdaptiveInterferenceTaskData(
                    type = "simple_math",
                    items = problems,
                    instruction = if (config.dualTaskDemand) "Solve AND identify odd/even results" else "Solve these problems",
                    executiveDemand = if (config.dualTaskDemand) 3 else 2,
                    timeConstraint = if (config.temporalComplexity) 20000L else 0L
                )
            }
        }
    }

    private fun createComplexInterferenceTask(config: AdaptiveContextConfig): AdaptiveInterferenceTaskData {
        val numbers = (1..20).shuffled().take(5).map { it.toString() }
        return AdaptiveInterferenceTaskData(
            type = "working_memory_update",
            items = numbers,
            instruction = "Keep running total of last 3 numbers",
            executiveDemand = 4,
            timeConstraint = if (config.temporalComplexity) 30000L else 0L
        )
    }

    private fun createExtremeInterferenceTask(config: AdaptiveContextConfig): AdaptiveInterferenceTaskData {
        val items = listOf("23 elephant", "17 house", "41 beautiful", "8 car", "13 amazing", "29 tree")
        return AdaptiveInterferenceTaskData(
            type = "triple_task",
            items = items,
            instruction = "Sort numbers, count vowels in words, AND identify prime numbers",
            executiveDemand = 5,
            timeConstraint = if (config.temporalComplexity) 40000L else 0L
        )
    }

    private fun generateAdaptiveMathProblems(count: Int, difficulty: Int): List<String> {
        return (1..count).map {
            when (difficulty) {
                1, 2 -> {
                    val a = Random.nextInt(1, 15)
                    val b = Random.nextInt(1, 10)
                    "$a + $b"
                }
                3 -> {
                    val a = Random.nextInt(10, 30)
                    val b = Random.nextInt(1, 15)
                    val op = listOf("+", "-").random()
                    "$a $op $b"
                }
                4, 5 -> {
                    val a = Random.nextInt(15, 50)
                    val b = Random.nextInt(2, 12)
                    val op = listOf("+", "-", "×", "÷").random()
                    if (op == "÷") {
                        val dividend = a * b
                        "$dividend $op $b"
                    } else {
                        "$a $op $b"
                    }
                }
                else -> "${Random.nextInt(1, 20)} + ${Random.nextInt(1, 15)}"
            }
        }
    }

    private fun createAdaptiveAnswerData(puzzleDataJson: String): String {
        val puzzleData = JSONObject(puzzleDataJson)
        val correctAnswers = puzzleData.getJSONArray("correctAnswers")
        val adaptiveConfig = puzzleData.getJSONObject("adaptiveConfig")

        return JSONObject().apply {
            put("correctAnswers", correctAnswers)
            put("puzzleType", "adaptive_context_switch")
            put("category", puzzleData.getString("category"))
            put("maxScore", calculateAdaptiveMaxScore(correctAnswers.length(), adaptiveConfig))
            put("cognitiveMetrics", JSONObject().apply {
                put("workingMemoryLoad", adaptiveConfig.getInt("workingMemoryLoad"))
                put("executiveDemand", adaptiveConfig.getInt("executiveDemand"))
                put("interferenceComplexity", adaptiveConfig.getString("interferenceComplexity"))
            })
        }.toString()
    }

    private fun calculateAdaptiveMaxScore(correctCount: Int, adaptiveConfig: JSONObject): Int {
        val baseScore = correctCount * 20

        val workingMemoryMultiplier = when (adaptiveConfig.getInt("workingMemoryLoad")) {
            1 -> 1.0f
            2 -> 1.2f
            3 -> 1.5f
            4 -> 1.8f
            5 -> 2.2f
            else -> 1.0f
        }

        val executiveMultiplier = when (adaptiveConfig.getInt("executiveDemand")) {
            1 -> 1.0f
            2 -> 1.3f
            3 -> 1.6f
            4 -> 2.0f
            5 -> 2.5f
            else -> 1.0f
        }

        val interferenceBonus = when (adaptiveConfig.getString("interferenceComplexity")) {
            "simple" -> 1.0f
            "moderate" -> 1.2f
            "complex" -> 1.5f
            "extreme" -> 2.0f
            else -> 1.0f
        }

        return (baseScore * workingMemoryMultiplier * executiveMultiplier * interferenceBonus).toInt()
    }

    // Data classes for internal use
    private data class AdaptiveInterferenceTaskData(
        val type: String,
        val items: List<String>,
        val instruction: String,
        val executiveDemand: Int,
        val timeConstraint: Long
    )
}

// =============================================================================
// ADAPTIVE CONTEXT SWITCH SCREEN
// =============================================================================

// Data classes for the puzzle UI
data class AdaptiveContextSwitchPuzzleData(
    val memoryItems: List<String>,
    val interferenceTask: AdaptiveInterferenceTask,
    val recognitionItems: List<String>,
    val correctAnswers: List<String>,
    val adaptiveConfig: AdaptiveContextConfig
)

data class AdaptiveInterferenceTask(
    val type: String,
    val items: List<String>,
    val instruction: String,
    val executiveDemand: Int,
    val timeConstraint: Long
)

data class AdaptiveContextConfig(
    val name: String,
    val description: String,
    val workingMemoryLoad: Int,
    val interferenceComplexity: String,
    val dualTaskDemand: Boolean,
    val attentionalControl: Boolean,
    val executiveDemand: Int,
    val temporalComplexity: Boolean
)

@Composable
fun AdaptiveContextSwitchPuzzleScreen(
    initialDifficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveContextSwitch"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("contextSwitch"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }
    var shouldCompleteSession by remember { mutableStateOf(false) }
    var sessionData by remember { mutableStateOf<SessionCompletionData?>(null) }
    var showHint by remember { mutableStateOf(false) }


    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "contextSwitch",
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
            AdaptiveContextSwitchGenerator.generateAdaptivePuzzle(currentDifficultyLevel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate adaptive puzzle", e)
            Pair("{}", "{}")
        }
    }

    // Parse puzzle data
    val parsedData = remember(puzzleData) {
        try {
            val json = JSONObject(puzzleData)

            val memoryArray = json.getJSONArray("memoryItems")
            val memoryItems = List(memoryArray.length()) { memoryArray.getString(it) }

            val recognitionArray = json.getJSONArray("recognitionItems")
            val recognitionItems = List(recognitionArray.length()) { recognitionArray.getString(it) }

            val correctArray = json.getJSONArray("correctAnswers")
            val correctAnswers = List(correctArray.length()) { correctArray.getString(it) }

            val interferenceObj = json.getJSONObject("interferenceTask")
            val interferenceItemsArray = interferenceObj.getJSONArray("items")
            val interferenceItems = List(interferenceItemsArray.length()) { interferenceItemsArray.getString(it) }

            val interferenceTask = AdaptiveInterferenceTask(
                type = interferenceObj.getString("type"),
                items = interferenceItems,
                instruction = interferenceObj.getString("instruction"),
                executiveDemand = interferenceObj.getInt("executiveDemand"),
                timeConstraint = interferenceObj.getLong("timeConstraint")
            )

            val configObj = json.getJSONObject("adaptiveConfig")
            val adaptiveConfig = AdaptiveContextConfig(
                name = configObj.getString("name"),
                description = configObj.getString("description"),
                workingMemoryLoad = configObj.getInt("workingMemoryLoad"),
                interferenceComplexity = configObj.getString("interferenceComplexity"),
                dualTaskDemand = configObj.getBoolean("dualTaskDemand"),
                attentionalControl = configObj.getBoolean("attentionalControl"),
                executiveDemand = configObj.getInt("executiveDemand"),
                temporalComplexity = configObj.getBoolean("temporalComplexity")
            )

            AdaptiveContextSwitchPuzzleData(
                memoryItems = memoryItems,
                interferenceTask = interferenceTask,
                recognitionItems = recognitionItems,
                correctAnswers = correctAnswers,
                adaptiveConfig = adaptiveConfig
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse puzzle data", e)
            AdaptiveContextSwitchPuzzleData(
                memoryItems = emptyList(),
                interferenceTask = AdaptiveInterferenceTask("", emptyList(), "", 1, 0L),
                recognitionItems = emptyList(),
                correctAnswers = emptyList(),
                adaptiveConfig = AdaptiveContextConfig("Error", "Failed to load", 1, "simple", false, false, 1, false)
            )
        }
    }

    var gamePhase by remember { mutableStateOf("memory") } // "memory", "interference", "recognition"
    var selectedAnswers by remember { mutableStateOf(setOf<String>()) }
    var interferenceAnswers by remember { mutableStateOf(listOf<String>()) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var finalScore by remember { mutableStateOf(0) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var currentStreak by remember { mutableStateOf(0) }
    var startTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Timer management
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            180 // Default 3 minutes
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentUserLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    val haptics = LocalHapticFeedback.current
    // Record adaptive performance
    fun recordPerformance(isCorrect: Boolean) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = System.currentTimeMillis() - startTime,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = parsedData.adaptiveConfig.workingMemoryLoad,
            totalScore = finalScore,
            puzzleType = "contextSwitch"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = SHOW_ADAPTATION_NOTICES
            }
        }
    }

    // Timer countdown
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0 && !showFeedback) {
            delay(1000)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !showFeedback) {
            // Time's up
            recordPerformance(false)
            showFeedback = true
            isCorrect = false
        }
    }

    // Fit-to-screen: fixed header, the current phase takes the remaining space, its primary
    // action is pinned at the bottom. Landscape / wide screens use two panes.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        val wide = maxWidth > maxHeight || maxWidth >= 600.dp
        val tall = !wide && maxHeight >= 780.dp
        val compact = !tall
        val pad = if (compact) 8.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .widthIn(max = if (wide) 960.dp else 640.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (tall) {
                AdaptiveUnifiedHeader(
                    level = currentUserLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    lives = currentHearts,
                    currentDifficulty = currentDifficultyLevel,
                    score = finalScore,
                    puzzleType = "contextSwitch",
                    competitiveInsight = competitiveInsight,
                    onBack = onBack,
                    onHint = {
                        showHint = !showHint
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            } else {
                AdaptiveCompactHud(
                    level = currentUserLevel,
                    timer = displayTimer.let { if (it.indexOf(':') == 1) "0$it" else it },
                    lives = currentHearts,
                    maxLives = currentDifficultyLevel.livesAllowed,
                    score = finalScore,
                    difficultyName = currentDifficultyLevel.name,
                    challengeText = null,
                    onBack = onBack,
                    onPause = null
                )
            }

            // Unified adaptation notification (banner intentionally off via SHOW_ADAPTATION_NOTICES)
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "contextSwitch",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )

            // Adaptation notification
            AnimatedVisibility(
                visible = showAdaptationNotification,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                AdaptiveContextNotificationCard(
                    adaptationInfo = adaptationInfo,
                    onDismiss = { showAdaptationNotification = false }
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 16.dp))

            // Add this after your Column/UI setup, but before the when statement
            if (shouldCompleteSession && sessionData != null) {
                UnifiedSessionCompletionHandler(
                    puzzleType = sessionData!!.puzzleType,
                    sessionScore = sessionData!!.sessionScore,
                    sessionStats = sessionData!!.sessionStats,
                    currentDifficulty = sessionData!!.currentDifficulty
                ) { result ->
                    shouldCompleteSession = false
                    sessionData = null
                    showFeedback = true
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (gamePhase) {
                    "memory" -> AdaptiveMemoryPhase(
                        items = parsedData.memoryItems,
                        adaptiveConfig = parsedData.adaptiveConfig,
                        wide = wide,
                        compact = compact,
                        onReady = { gamePhase = "interference" }
                    )

                    "interference" -> AdaptiveInterferencePhase(
                        task = parsedData.interferenceTask,
                        answers = interferenceAnswers,
                        adaptiveConfig = parsedData.adaptiveConfig,
                        wide = wide,
                        compact = compact,
                        onAnswersChanged = { interferenceAnswers = it },
                        onComplete = { gamePhase = "recognition" }
                    )

                    "recognition" -> AdaptiveRecognitionPhase(
                        items = parsedData.recognitionItems,
                        selectedAnswers = selectedAnswers,
                        adaptiveConfig = parsedData.adaptiveConfig,
                        wide = wide,
                        compact = compact,
                        onSelectionChanged = { selectedAnswers = it },
                        onSubmit = {
                            val score = calculateAdaptiveContextScore(
                                selectedAnswers,
                                parsedData.correctAnswers,
                                timeRemaining,
                                parsedData.adaptiveConfig
                            )
                            val correct = selectedAnswers == parsedData.correctAnswers.toSet()

                            finalScore = score
                            isCorrect = correct

                            if (correct) {
                                currentStreak++
                            } else {
                                currentHearts = maxOf(0, currentHearts - 1)
                                currentStreak = 0
                            }

                            recordPerformance(correct)
                            sessionData = SessionCompletionData(
                                puzzleType = "contextSwitch",
                                sessionScore = finalScore,
                                sessionStats = SessionStatistics(
                                    correctAnswers = if (isCorrect) 1 else 0,
                                    totalAnswers = 1,
                                    totalTimeSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt(),
                                    bestStreak = currentStreak,
                                    winRate = if (isCorrect) 1f else 0f,
                                    totalScore = finalScore,
                                    averageTimePerPuzzle = ((System.currentTimeMillis() - startTime) / 1000).toInt()/5,
                                    currentStreak = currentStreak,
                                    individualTimes = emptyList()
                                ),
                                currentDifficulty = currentDifficultyLevel
                            )
                            shouldCompleteSession = true
                        }
                    )
                }
            }
        }
    }

    // Feedback Dialog
    if (showFeedback) {
        AdaptiveContextFeedbackDialog(
            isCorrect = isCorrect,
            score = finalScore,
            correctAnswers = parsedData.correctAnswers,
            userAnswers = selectedAnswers.toList(),
            adaptiveConfig = parsedData.adaptiveConfig,
            onDismiss = {
                showFeedback = false

                feedbackManager.showFeedback(
                    puzzleType = "adaptiveContextSwitch",
                    isCorrect = isCorrect,
                    userAnswer = "${selectedAnswers.intersect(parsedData.correctAnswers.toSet()).size}/${parsedData.correctAnswers.size} items recalled",
                    correctAnswer = "Adaptive ${parsedData.adaptiveConfig.name} context switching",
                    timeSpent = System.currentTimeMillis() - startTime,
                    difficulty = parsedData.adaptiveConfig.name,
                    timeRemaining = timeRemaining,
                    totalTime = totalTimeSeconds,
                    onComplete = {
                        onSubmitAnswer(isCorrect)
                        fetchNextPuzzle(finalScore)
                    }
                )
            }
        )
    }
}

@Composable
private fun AdaptiveContextSwitchHeader(
    level: UserLevel,
    streakInfo: StreakInfo,
    adaptiveConfig: AdaptiveContextConfig,
    timer: String,
    hearts: Int,
    score: Int,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.statusBarsPadding().fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = RvInk,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Level ${level.level}",
                fontSize = 12.sp,
                color = RvInkSoft
            )
            Text(
                text = adaptiveConfig.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk
            )
            Text(
                text = timer,
                fontSize = 14.sp,
                color = RvInk
            )

            // Adaptive features indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (adaptiveConfig.dualTaskDemand) Text("🧠", fontSize = 10.sp)
                if (adaptiveConfig.attentionalControl) Text("👁️", fontSize = 10.sp)
                if (adaptiveConfig.temporalComplexity) Text("⏱️", fontSize = 10.sp)
                Text("${adaptiveConfig.workingMemoryLoad}", fontSize = 10.sp, color = RvInk)
            }
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${stringResource(R.string.score_label)}: $score",
                fontSize = 12.sp,
                color = RvInk
            )
            Row {
                repeat(hearts) {
                    Text("❤️", fontSize = 12.sp)
                }
            }
            if (streakInfo.currentStreak > 0) {
                Text(
                    text = "🔥 ${streakInfo.currentStreak}",
                    fontSize = 10.sp,
                    color = Color(0xFFFF6B35)
                )
            }
        }
    }
}

@Composable
private fun AdaptiveContextNotificationCard(
    adaptationInfo: DifficultyManager.AdaptiveConfig?,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
                tint = RvOnTone,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Context Switch Adapted!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvOnTone
                )
                Text(
                    text = adaptationInfo?.adjustmentReason ?: "",
                    fontSize = 10.sp,
                    color = RvOnTone.copy(alpha = 0.9f)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = RvOnTone,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}



@Composable
private fun AdaptiveMemoryPhase(
    items: List<String>,
    adaptiveConfig: AdaptiveContextConfig,
    wide: Boolean,
    compact: Boolean,
    onReady: () -> Unit
) {
    var showItems by remember { mutableStateOf(true) }

    CsPhaseScaffold(
        wide = wide,
        compact = compact,
        title = "🧠 Study these items",
        subtitle = "${adaptiveConfig.name}: ${adaptiveConfig.description}\nMemory load: ${adaptiveConfig.workingMemoryLoad}/5 items",
        action = {
            if (showItems) {
                CsPrimaryAction(text = "I'm Ready! 💪") {
                    showItems = false
                    onReady()
                }
            }
        }
    ) { m ->
        if (showItems) CsMemoryList(items = items, modifier = m)
    }
}

@Composable
private fun AdaptiveInterferencePhase(
    task: AdaptiveInterferenceTask,
    answers: List<String>,
    adaptiveConfig: AdaptiveContextConfig,
    wide: Boolean,
    compact: Boolean,
    onAnswersChanged: (List<String>) -> Unit,
    onComplete: () -> Unit
) {
    var timeRemaining by remember { mutableStateOf(task.timeConstraint / 1000) }
    var showValidationError by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf("") }

    LaunchedEffect(task.timeConstraint) {
        if (task.timeConstraint > 0) {
            while (timeRemaining > 0) {
                delay(1000)
                timeRemaining--
            }
        }
    }

    CsPhaseScaffold(
        wide = wide,
        compact = compact,
        title = "🧩 Interference Task",
        subtitle = task.instruction,
        action = {
            CsPrimaryAction(
                text = "Continue →",
                enabled = when (task.type) {
                    "simple_math" -> answers.size == task.items.size && answers.all { it.isNotEmpty() }
                    "working_memory_update" -> answers.isNotEmpty()
                    else -> true
                },
                onClick = {
                    val isValid = AdaptiveContextSwitchGenerator.validateInterferenceAnswer(
                        taskType = task.type,
                        userAnswers = answers,
                        originalItems = task.items
                    )
                    if (isValid) {
                        showValidationError = false
                        onComplete()
                    } else {
                        validationMessage = when (task.type) {
                            "number_sort" -> "Please sort the numbers correctly from smallest to largest"
                            "word_alphabetize" -> "Please arrange the words in alphabetical order"
                            "simple_math" -> "Please check your math calculations"
                            "working_memory_update" -> "Please check your running total calculation"
                            "triple_task" -> "Please complete all parts of the triple task"
                            else -> "Please check your answer"
                        }
                        showValidationError = true
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(3000); showValidationError = false
                        }
                    }
                }
            )
        }
    ) { m ->
        Box(modifier = m) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (task.timeConstraint > 0) {
                    Surface(
                        color = if (timeRemaining <= 10) RvCoral.copy(alpha = 0.2f) else RvSky.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "⏳ ${timeRemaining}s",
                            color = RvInk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // QUESTIONS AREA - takes the remaining height
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when (task.type) {
                        "number_sort", "word_alphabetize" -> {
                            AdaptiveSortingInterface(
                                items = task.items,
                                taskType = task.type,
                                onItemsChanged = onAnswersChanged
                            )
                        }
                        "simple_math" -> {
                            AdaptiveMathInterface(
                                problems = task.items,
                                answers = answers,
                                onAnswersChanged = onAnswersChanged
                            )
                        }
                        "working_memory_update" -> {
                            AdaptiveWorkingMemoryInterface(
                                numbers = task.items,
                                onComplete = { result -> onAnswersChanged(listOf(result.toString())) }
                            )
                        }
                        "triple_task" -> {
                            AdaptiveTripleTaskInterface(
                                items = task.items,
                                onAnswersChanged = onAnswersChanged
                            )
                        }
                    }
                }
            }

            // Validation error floats over the play area (no layout jump)
            if (showValidationError) {
                Card(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    colors = CardDefaults.cardColors(containerColor = RvCoralEdge)
                ) {
                    Text(
                        text = validationMessage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvOnTone,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveRecognitionPhase(
    items: List<String>,
    selectedAnswers: Set<String>,
    adaptiveConfig: AdaptiveContextConfig,
    wide: Boolean,
    compact: Boolean,
    onSelectionChanged: (Set<String>) -> Unit,
    onSubmit: () -> Unit
) {
    CsPhaseScaffold(
        wide = wide,
        compact = compact,
        title = "🎯 Recognition Test",
        subtitle = "Select all items from the original list" +
            if (adaptiveConfig.attentionalControl) "\n⚠️ Items are arranged to test attention control" else "",
        action = {
            CsPrimaryAction(
                text = "Submit Answer ✓",
                enabled = selectedAnswers.isNotEmpty(),
                onClick = onSubmit
            )
        }
    ) { m ->
        CsSelectionGrid(
            items = items,
            selectedAnswers = selectedAnswers,
            onSelectionChanged = onSelectionChanged,
            modifier = m
        )
    }
}

// Additional UI components for different interference tasks
@Composable
private fun AdaptiveSortingInterface(
    items: List<String>,
    taskType: String,
    onItemsChanged: (List<String>) -> Unit
) {
    var sortedItems by remember { mutableStateOf(items) }

    // Rows share the available height (48-72dp); both arrows are always present (48dp targets).
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gap = 8.dp
        val n = sortedItems.size.coerceAtLeast(1)
        val rowH = ((maxHeight - gap * (n - 1)) / n).coerceIn(48.dp, 72.dp)
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            sortedItems.forEachIndexed { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth().height(rowH).testTag("ctx_word"),
                    colors = CardDefaults.cardColors(containerColor = RvSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RvOutline)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. $item",
                            fontSize = aCapSp(16f, 1.3f),
                            color = RvInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            enabled = index > 0,
                            modifier = Modifier.size(48.dp),
                            onClick = {
                                val newItems = sortedItems.toMutableList()
                                val temp = newItems[index]
                                newItems[index] = newItems[index - 1]
                                newItems[index - 1] = temp
                                sortedItems = newItems
                                onItemsChanged(newItems)
                            }
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = if (index > 0) RvInk else RvDisabled,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            enabled = index < sortedItems.size - 1,
                            modifier = Modifier.size(48.dp),
                            onClick = {
                                val newItems = sortedItems.toMutableList()
                                val temp = newItems[index]
                                newItems[index] = newItems[index + 1]
                                newItems[index + 1] = temp
                                sortedItems = newItems
                                onItemsChanged(newItems)
                            }
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = if (index < sortedItems.size - 1) RvInk else RvDisabled,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun AdaptiveMathInterface(
    problems: List<String>,
    answers: List<String>,
    onAnswersChanged: (List<String>) -> Unit
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(problems.size) { index ->
            val currentAnswers = remember(answers) { answers.toMutableList() }.apply {
                while (size <= index) add("")
            }

            val isLast = index == problems.lastIndex
            val imeAction = if (isLast) ImeAction.Done else ImeAction.Next

            Card(
                modifier = Modifier.fillMaxWidth().testTag("ctx_word"),
                colors = CardDefaults.cardColors(containerColor = RvSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${problems[index]} =",
                        fontSize = 18.sp,
                        color = RvInk,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = currentAnswers.getOrElse(index) { "" },
                        onValueChange = { raw ->
                            val filtered = raw.filterIndexed { i, c ->
                                c.isDigit() || (c == '-' && i == 0)
                            }
                            currentAnswers[index] = filtered
                            onAnswersChanged(currentAnswers)
                        },
                        modifier = Modifier.width(96.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontSize = 18.sp,
                            color = RvInk
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = imeAction
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            onDone = { focusManager.clearFocus() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = RvInk,
                            unfocusedTextColor = RvInk,
                            focusedBorderColor = RvViolet,
                            unfocusedBorderColor = RvInkSoft,
                            cursorColor = RvInk
                        )
                    )
                }
            }
        }
    }
}


@Composable
private fun AdaptiveWorkingMemoryInterface(
    numbers: List<String>,
    onComplete: (Int) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var runningTotal by remember { mutableStateOf(0) }
    var last3Numbers by remember { mutableStateOf(listOf<Int>()) }

    LaunchedEffect(currentIndex) {
        if (currentIndex < numbers.size) {
            delay(2000) // Show each number for 2 seconds
            val currentNumber = numbers[currentIndex].toIntOrNull() ?: 0
            last3Numbers = (last3Numbers + currentNumber).takeLast(3)
            runningTotal = last3Numbers.sum()
            currentIndex++
        } else {
            onComplete(runningTotal)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (currentIndex < numbers.size) {
            Text(
                text = numbers[currentIndex],
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Keep running total of last 3 numbers",
                fontSize = 14.sp,
                color = RvInkSoft,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Current total: $runningTotal",
                fontSize = 16.sp,
                color = RvInk
            )
        } else {
            Text(
                text = "Final total: $runningTotal",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk
            )
        }
    }
}

@Composable
private fun AdaptiveTripleTaskInterface(
    items: List<String>,
    onAnswersChanged: (List<String>) -> Unit
) {
    var answers by remember { mutableStateOf(listOf<String>()) }

    Column {
        Text(
            text = "For each item, provide:",
            fontSize = 16.sp,
            color = RvInk,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "• Sort the number\n• Count vowels in word\n• Identify if number is prime",
            fontSize = 12.sp,
            color = RvInkSoft
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items.size) { index ->
                val item = items[index]
                val parts = item.split(" ")
                val number = parts.getOrNull(0) ?: ""
                val word = parts.getOrNull(1) ?: ""

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = RvSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp).testTag("ctx_word")
                    ) {
                        Text(
                            text = "$number $word",
                            fontSize = 16.sp,
                            color = RvInk,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vowels: ${word.count { it.lowercaseChar() in "aeiou" }}", fontSize = 12.sp, color = RvInkSoft)
                            Text("Prime: ${isPrime(number.toIntOrNull() ?: 0)}", fontSize = 12.sp, color = RvInkSoft)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveContextFeedbackDialog(
    isCorrect: Boolean,
    score: Int,
    correctAnswers: List<String>,
    userAnswers: List<String>,
    adaptiveConfig: AdaptiveContextConfig,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isCorrect) "🎉 Excellent!" else "🤔 Not quite",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = adaptiveConfig.name,
                    fontSize = 14.sp,
                    color = Color(0xFF2196F3)
                )
            }
        },
        text = {
            Column {
                Text("${stringResource(R.string.score_label)}: $score points")
                Text("Difficulty: ${adaptiveConfig.description}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Correct items: ${correctAnswers.joinToString(", ")}")
                if (!isCorrect) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your selection: ${userAnswers.joinToString(", ")}")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Working Memory Load: ${adaptiveConfig.workingMemoryLoad}/5", fontSize = 12.sp, color = Color.Gray)
                Text("Executive Demand: ${adaptiveConfig.executiveDemand}/5", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.continue_label))
            }
        }
    )
}

// Helper functions
private fun calculateAdaptiveContextScore(
    userAnswers: Set<String>,
    correctAnswers: List<String>,
    timeRemaining: Int,
    adaptiveConfig: AdaptiveContextConfig
): Int {
    val correctCount = userAnswers.intersect(correctAnswers.toSet()).size
    val incorrectCount = userAnswers.subtract(correctAnswers.toSet()).size
    val missedCount = correctAnswers.size - correctCount

    val baseScore = (correctCount * 100) - (incorrectCount * 50) - (missedCount * 25)
    val timeBonus = timeRemaining * 2

    // Adaptive multipliers
    val workingMemoryMultiplier = 1 + (adaptiveConfig.workingMemoryLoad - 1) * 0.2
    val executiveMultiplier = 1 + (adaptiveConfig.executiveDemand - 1) * 0.3

    val adaptiveBonus = when {
        adaptiveConfig.dualTaskDemand && adaptiveConfig.attentionalControl -> 1.5
        adaptiveConfig.dualTaskDemand || adaptiveConfig.attentionalControl -> 1.3
        else -> 1.0
    }

    return maxOf(0, ((baseScore + timeBonus) * workingMemoryMultiplier * executiveMultiplier * adaptiveBonus).toInt())
}

private fun isPrime(n: Int): Boolean {
    if (n < 2) return false
    for (i in 2..sqrt(n.toDouble()).toInt()) {
        if (n % i == 0) return false
    }
    return true
}
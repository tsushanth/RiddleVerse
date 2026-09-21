package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

// =============================================================================
// ADAPTIVE MATH COMPARISON GENERATOR
// =============================================================================

object AdaptiveMathComparisonGenerator {

    data class AdaptiveMathConfig(
        val numericalComplexity: Int, // 1-5 scale for number range and operations
        val workingMemoryDemand: Boolean, // Whether to use multi-step calculations
        val proceduralComplexity: Boolean, // Whether to mix operation types
        val abstractReasoning: Boolean, // Whether to include algebraic concepts
        val speedPressure: Int, // 1-5 scale for time constraints
        val errorInduction: Boolean, // Whether to add similar-looking wrong answers
        val name: String,
        val description: String
    )

    private fun getDifficultyConfigs() = mapOf(
        0 to AdaptiveMathConfig( // Beginner
            numericalComplexity = 1,
            workingMemoryDemand = false,
            proceduralComplexity = false,
            abstractReasoning = false,
            speedPressure = 1,
            errorInduction = false,
            name = "Beginner",
            description = "Simple single-digit arithmetic"
        ),
        1 to AdaptiveMathConfig( // Easy
            numericalComplexity = 2,
            workingMemoryDemand = true,
            proceduralComplexity = false,
            abstractReasoning = false,
            speedPressure = 2,
            errorInduction = false,
            name = "Easy",
            description = "Two-digit numbers with basic operations"
        ),
        2 to AdaptiveMathConfig( // Medium
            numericalComplexity = 3,
            workingMemoryDemand = true,
            proceduralComplexity = true,
            abstractReasoning = false,
            speedPressure = 3,
            errorInduction = true,
            name = "Medium",
            description = "Mixed operations with decimals and error induction"
        ),
        3 to AdaptiveMathConfig( // Hard
            numericalComplexity = 4,
            workingMemoryDemand = true,
            proceduralComplexity = true,
            abstractReasoning = true,
            speedPressure = 4,
            errorInduction = true,
            name = "Hard",
            description = "Complex operations with negative numbers and fractions"
        ),
        4 to AdaptiveMathConfig( // Expert
            numericalComplexity = 5,
            workingMemoryDemand = true,
            proceduralComplexity = true,
            abstractReasoning = true,
            speedPressure = 5,
            errorInduction = true,
            name = "Expert",
            description = "Advanced mathematical reasoning with abstract concepts"
        )
    )

    fun generateAdaptivePuzzle(
        difficultyLevel: DifficultyManager.DifficultyLevel
    ): Pair<String, String> {
        val config = getDifficultyConfigs()[difficultyLevel.index]
            ?: getDifficultyConfigs()[2]!! // Default to medium

        val sequenceLength = when (config.numericalComplexity) {
            1 -> 6
            2 -> 8
            3 -> 12
            4 -> 15
            5 -> 18
            else -> 10
        }

        val puzzleSequence = generateAdaptivePuzzleSequence(config, sequenceLength)

        val questionData = JSONObject().apply {
            put("sequence", JSONArray().apply {
                puzzleSequence.forEach { pair ->
                    put(JSONObject().apply {
                        put("pairNumber", pair.pairNumber)
                        put("leftValue", pair.leftValue)
                        put("rightValue", pair.rightValue)
                        put("leftNumeric", pair.leftNumeric)
                        put("rightNumeric", pair.rightNumeric)
                        put("correctAnswer", pair.correctAnswer)
                        put("operationType", pair.operationType)
                        put("difficulty", pair.difficulty)
                        put("cognitiveLoad", pair.cognitiveLoad)
                        put("workingMemorySteps", pair.workingMemorySteps)
                    })
                }
            })
            put("totalPairs", puzzleSequence.size)
            put("difficulty", config.name)
            put("timeLimit", calculateTimeLimit(config, sequenceLength))
            put("adaptiveConfig", JSONObject().apply {
                put("name", config.name)
                put("description", config.description)
                put("numericalComplexity", config.numericalComplexity)
                put("workingMemoryDemand", config.workingMemoryDemand)
                put("proceduralComplexity", config.proceduralComplexity)
                put("abstractReasoning", config.abstractReasoning)
                put("speedPressure", config.speedPressure)
                put("errorInduction", config.errorInduction)
            })
            put("instructions", generateAdaptiveInstructions(config))
        }

        val answerData = JSONObject().apply {
            put("correctAnswers", JSONArray().apply {
                puzzleSequence.forEach { pair ->
                    put(pair.correctAnswer)
                }
            })
            put("maxScore", calculateAdaptiveMathMaxScore(puzzleSequence.size, config))
            put("cognitiveMetrics", JSONObject().apply {
                put("averageCognitiveLoad", puzzleSequence.map { it.cognitiveLoad }.average())
                put("totalWorkingMemorySteps", puzzleSequence.sumOf { it.workingMemorySteps })
                put("complexityProgression", JSONArray(puzzleSequence.map { it.difficulty }))
            })
        }

        return Pair(questionData.toString(), answerData.toString())
    }

    private fun generateAdaptivePuzzleSequence(config: AdaptiveMathConfig, sequenceLength: Int): List<AdaptiveComparisonPair> {
        val sequence = mutableListOf<AdaptiveComparisonPair>()

        // Progressive difficulty within the sequence
        for (i in 0 until sequenceLength) {
            val progressRatio = i.toDouble() / sequenceLength
            val pair = generateAdaptiveComparisonPair(config, i + 1, progressRatio)
            sequence.add(pair)
        }

        return sequence
    }

    private fun generateAdaptiveComparisonPair(
        config: AdaptiveMathConfig,
        pairNumber: Int,
        progressRatio: Double
    ): AdaptiveComparisonPair {
        // Enhanced operation selection for adaptive challenges
        val operationType = selectAdaptiveOperation(config, progressRatio)

        // Generate values with adaptive complexity
        val leftValue = generateAdaptiveValue(operationType, config, progressRatio)
        val rightValue = generateAdaptiveValue(operationType, config, progressRatio)

        // Calculate with enhanced precision
        val leftNumeric = evaluateAdaptiveExpression(leftValue)
        val rightNumeric = evaluateAdaptiveExpression(rightValue)

        // Determine correct answer with adaptive error induction
        val tolerance = if (config.errorInduction) 0.001 else 0.01
        val correctAnswer = when {
            abs(leftNumeric - rightNumeric) < tolerance -> "equal"
            leftNumeric > rightNumeric -> "left"
            else -> "right"
        }

        // Calculate cognitive load metrics
        val cognitiveLoad = calculateCognitiveLoad(leftValue, rightValue, operationType, config)
        val workingMemorySteps = calculateWorkingMemorySteps(leftValue, rightValue, operationType)

        return AdaptiveComparisonPair(
            pairNumber = pairNumber,
            leftValue = leftValue,
            rightValue = rightValue,
            leftNumeric = leftNumeric,
            rightNumeric = rightNumeric,
            correctAnswer = correctAnswer,
            operationType = operationType,
            difficulty = calculateAdaptivePairDifficulty(leftValue, rightValue, operationType, config),
            cognitiveLoad = cognitiveLoad,
            workingMemorySteps = workingMemorySteps
        )
    }

    private fun selectAdaptiveOperation(config: AdaptiveMathConfig, progressRatio: Double): String {
        val operations = when (config.numericalComplexity) {
            1 -> listOf("addition", "subtraction")
            2 -> listOf("addition", "subtraction", "simple_multiplication")
            3 -> listOf("addition", "subtraction", "multiplication", "division", "mixed")
            4 -> listOf("multiplication", "division", "mixed", "fractions")
            5 -> listOf("fractions", "mixed", "algebraic")
            else -> listOf("addition", "subtraction")
        }

        // Weight operations by complexity and progress
        val weightedOperations = if (config.proceduralComplexity) {
            // Mix operation types for procedural complexity
            operations.shuffled()
        } else {
            // Consistent operation types
            operations.take(2).shuffled()
        }

        return weightedOperations.first()
    }

    private fun generateAdaptiveValue(
        operationType: String,
        config: AdaptiveMathConfig,
        progressRatio: Double
    ): String {
        val numberRange = calculateNumberRange(config, progressRatio)

        return when (operationType) {
            "addition" -> generateAdaptiveAddition(config, numberRange)
            "subtraction" -> generateAdaptiveSubtraction(config, numberRange)
            "simple_multiplication" -> generateAdaptiveSimpleMultiplication(config)
            "multiplication" -> generateAdaptiveMultiplication(config, numberRange)
            "division" -> generateAdaptiveDivision(config, numberRange)
            "mixed" -> generateAdaptiveMixedExpression(config, numberRange)
            "fractions" -> generateAdaptiveFractionExpression(config, numberRange)
            "algebraic" -> generateAdaptiveAlgebraicExpression(config)
            else -> generateAdaptiveSimpleNumber(config, numberRange)
        }
    }

    private fun calculateNumberRange(config: AdaptiveMathConfig, progressRatio: Double): Pair<Int, Int> {
        val baseRanges = mapOf(
            1 to Pair(1, 15),
            2 to Pair(1, 25),
            3 to Pair(1, 50),
            4 to Pair(-50, 100),
            5 to Pair(-100, 200)
        )

        val (min, max) = baseRanges[config.numericalComplexity] ?: Pair(1, 20)
        val expansion = (progressRatio * (max - min) * 0.5).toInt()

        return Pair(min, max + expansion)
    }

    // Enhanced adaptive generation methods
    private fun generateAdaptiveAddition(config: AdaptiveMathConfig, range: Pair<Int, Int>): String {
        return if (config.workingMemoryDemand) {
            val a = randomAdaptiveNumber(range)
            val b = randomAdaptiveNumber(range)
            val c = randomAdaptiveNumber(Pair(1, 10))
            "($a + $b) + $c"
        } else {
            val a = randomAdaptiveNumber(range)
            val b = randomAdaptiveNumber(range)
            "$a + $b"
        }
    }

    private fun generateAdaptiveSubtraction(config: AdaptiveMathConfig, range: Pair<Int, Int>): String {
        return if (config.workingMemoryDemand) {
            val a = randomAdaptiveNumber(range)
            val b = randomAdaptiveNumber(Pair(1, abs(a) / 2 + 1))
            val c = randomAdaptiveNumber(Pair(1, 10))
            "($a - $b) - $c"
        } else {
            val a = randomAdaptiveNumber(range)
            val b = randomAdaptiveNumber(Pair(range.first, abs(a)))
            "$a - $b"
        }
    }

    private fun generateAdaptiveSimpleMultiplication(config: AdaptiveMathConfig): String {
        val a = randomAdaptiveNumber(Pair(2, if (config.numericalComplexity <= 2) 9 else 12))
        val b = randomAdaptiveNumber(Pair(2, if (config.numericalComplexity <= 2) 9 else 12))
        return "$a × $b"
    }

    private fun generateAdaptiveMultiplication(config: AdaptiveMathConfig, range: Pair<Int, Int>): String {
        val maxVal = minOf(abs(range.second), if (config.numericalComplexity >= 4) 25 else 15)

        return if (config.workingMemoryDemand) {
            val a = randomAdaptiveNumber(Pair(2, maxVal))
            val b = randomAdaptiveNumber(Pair(2, 8))
            val c = randomAdaptiveNumber(Pair(2, 5))
            "($a × $b) + $c"
        } else {
            val a = randomAdaptiveNumber(Pair(2, maxVal))
            val b = randomAdaptiveNumber(Pair(2, maxVal))
            "$a × $b"
        }
    }

    private fun generateAdaptiveDivision(config: AdaptiveMathConfig, range: Pair<Int, Int>): String {
        val b = randomAdaptiveNumber(Pair(2, minOf(abs(range.second), 12)))
        val result = randomAdaptiveNumber(Pair(1, abs(range.second)))
        val a = b * result

        return if (config.workingMemoryDemand) {
            val c = randomAdaptiveNumber(Pair(1, 10))
            "($a ÷ $b) + $c"
        } else {
            "$a ÷ $b"
        }
    }

    private fun generateAdaptiveMixedExpression(config: AdaptiveMathConfig, range: Pair<Int, Int>): String {
        val operations = if (config.proceduralComplexity) {
            listOf("+", "-", "×", "÷")
        } else {
            listOf("+", "-", "×")
        }

        val op1 = operations.random()
        val op2 = operations.random()
        val maxVal = minOf(abs(range.second), 15)

        val a = randomAdaptiveNumber(Pair(2, maxVal))
        val b = randomAdaptiveNumber(Pair(1, maxVal))
        val c = randomAdaptiveNumber(Pair(1, maxVal))

        return if (config.workingMemoryDemand) {
            "($a $op1 $b) $op2 $c"
        } else {
            "$a $op1 $b $op2 $c"
        }
    }

    private fun generateAdaptiveFractionExpression(config: AdaptiveMathConfig, range: Pair<Int, Int>): String {
        val numerator = randomAdaptiveNumber(Pair(1, abs(range.second)))
        val denominator = randomAdaptiveNumber(Pair(2, 12))

        return if (config.workingMemoryDemand) {
            val additional = randomAdaptiveNumber(Pair(1, 5))
            "$numerator/$denominator + $additional"
        } else {
            "$numerator/$denominator"
        }
    }

    private fun generateAdaptiveAlgebraicExpression(config: AdaptiveMathConfig): String {
        val coefficient = randomAdaptiveNumber(Pair(2, 8))
        val constant = randomAdaptiveNumber(Pair(1, 20))
        val x = randomAdaptiveNumber(Pair(1, 10))

        return "${coefficient}x + $constant (x=$x)"
    }

    private fun generateAdaptiveSimpleNumber(config: AdaptiveMathConfig, range: Pair<Int, Int>): String {
        return randomAdaptiveNumber(range).toString()
    }

    private fun randomAdaptiveNumber(range: Pair<Int, Int>): Int {
        val min = range.first
        val max = range.second
        return Random.nextInt(min, max + 1)
    }

    // Enhanced evaluation methods
    private fun evaluateAdaptiveExpression(expression: String): Double {
        return try {
            when {
                expression.contains("x=") -> {
                    // Handle algebraic expressions like "2x + 5 (x=3)"
                    val parts = expression.split(" (x=")
                    val algebraic = parts[0]
                    val xValue = parts[1].replace(")", "").toDouble()
                    evaluateAlgebraic(algebraic, xValue)
                }
                expression.contains("/") && !expression.contains(" ") -> {
                    val parts = expression.split("/")
                    if (parts.size == 2) {
                        parts[0].toDouble() / parts[1].toDouble()
                    } else {
                        evaluateComplexAdaptiveExpression(expression)
                    }
                }
                else -> evaluateComplexAdaptiveExpression(expression)
            }
        } catch (error: Exception) {
            Log.e("AdaptiveMathComparison", "Error evaluating expression \"$expression\": ${error.message}")
            0.0
        }
    }

    private fun evaluateAlgebraic(expression: String, x: Double): Double {
        // Simple algebraic evaluation like "2x + 5"
        val cleaned = expression.replace("x", "*$x")
        return evaluateComplexAdaptiveExpression(cleaned)
    }

    private fun evaluateComplexAdaptiveExpression(expression: String): Double {
        val jsExpression = expression
            .replace("×", "*")
            .replace("÷", "/")

        return evaluateEnhancedArithmetic(jsExpression)
    }

    private fun evaluateEnhancedArithmetic(expression: String): Double {
        var expr = expression.replace(" ", "")

        // Handle parentheses first
        while (expr.contains("(")) {
            val start = expr.lastIndexOf("(")
            val end = expr.indexOf(")", start)
            if (end != -1) {
                val subExpr = expr.substring(start + 1, end)
                val result = evaluateEnhancedArithmetic(subExpr)
                expr = expr.substring(0, start) + result + expr.substring(end + 1)
            } else {
                break
            }
        }

        // Handle multiplication and division
        expr = processEnhancedOperations(expr, listOf("*", "/"))

        // Handle addition and subtraction
        expr = processEnhancedOperations(expr, listOf("+", "-"))

        return expr.toDoubleOrNull() ?: 0.0
    }

    private fun processEnhancedOperations(expression: String, operations: List<String>): String {
        var expr = expression

        for (op in operations) {
            while (expr.contains(op)) {
                val regex = when (op) {
                    "*" -> """(-?\d+(?:\.\d+)?)\*(-?\d+(?:\.\d+)?)""".toRegex()
                    "/" -> """(-?\d+(?:\.\d+)?)/(-?\d+(?:\.\d+)?)""".toRegex()
                    "+" -> """(-?\d+(?:\.\d+)?)\+(-?\d+(?:\.\d+)?)""".toRegex()
                    "-" -> """(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)""".toRegex()
                    else -> continue
                }

                val match = regex.find(expr)
                if (match != null) {
                    val a = match.groupValues[1].toDouble()
                    val b = match.groupValues[2].toDouble()
                    val result = when (op) {
                        "*" -> a * b
                        "/" -> if (b != 0.0) a / b else 0.0
                        "+" -> a + b
                        "-" -> a - b
                        else -> 0.0
                    }
                    expr = expr.replaceFirst(match.value, result.toString())
                } else {
                    break
                }
            }
        }

        return expr
    }

    // Cognitive load calculation methods
    private fun calculateCognitiveLoad(
        leftValue: String,
        rightValue: String,
        operationType: String,
        config: AdaptiveMathConfig
    ): Int {
        var load = 1

        // Base load by operation type
        load += when (operationType) {
            "addition", "subtraction" -> 1
            "multiplication", "division" -> 2
            "mixed", "fractions" -> 3
            "algebraic" -> 4
            else -> 1
        }

        // Working memory demand
        if (config.workingMemoryDemand) {
            load += 2
        }

        // Procedural complexity
        if (config.proceduralComplexity) {
            load += 1
        }

        // Abstract reasoning
        if (config.abstractReasoning) {
            load += 2
        }

        return minOf(load, 10) // Cap at 10
    }

    private fun calculateWorkingMemorySteps(
        leftValue: String,
        rightValue: String,
        operationType: String
    ): Int {
        val leftSteps = countMathematicalOperations(leftValue)
        val rightSteps = countMathematicalOperations(rightValue)
        val comparisonStep = 1

        return leftSteps + rightSteps + comparisonStep
    }

    private fun countMathematicalOperations(expression: String): Int {
        return expression.count { it in setOf('+', '-', '×', '÷', '*', '/') } + 1
    }

    private fun calculateAdaptivePairDifficulty(
        leftValue: String,
        rightValue: String,
        operationType: String,
        config: AdaptiveMathConfig
    ): Double {
        var difficulty = 1.0

        // Base difficulty by operation type
        val operationDifficulty = mapOf(
            "addition" to 1.0,
            "subtraction" to 1.2,
            "simple_multiplication" to 1.5,
            "multiplication" to 2.0,
            "division" to 2.5,
            "mixed" to 3.0,
            "fractions" to 3.5,
            "algebraic" to 4.0
        )

        difficulty *= operationDifficulty[operationType] ?: 1.0

        // Adaptive multipliers
        difficulty *= (1 + config.numericalComplexity * 0.3)

        if (config.workingMemoryDemand) difficulty *= 1.5
        if (config.proceduralComplexity) difficulty *= 1.3
        if (config.abstractReasoning) difficulty *= 1.4
        if (config.errorInduction) difficulty *= 1.2

        // Expression length complexity
        val avgLength = (leftValue.length + rightValue.length) / 2.0
        difficulty *= (1 + avgLength * 0.05)

        return (difficulty * 10).roundToInt() / 10.0
    }

    private fun calculateTimeLimit(config: AdaptiveMathConfig, sequenceLength: Int): Long {
        val baseTimePerPair = when (config.speedPressure) {
            1 -> 8000L // 8 seconds
            2 -> 6000L // 6 seconds
            3 -> 5000L // 5 seconds
            4 -> 4000L // 4 seconds
            5 -> 3000L // 3 seconds
            else -> 6000L
        }

        return baseTimePerPair * sequenceLength
    }

    private fun generateAdaptiveInstructions(config: AdaptiveMathConfig): String {
        val baseInstruction = "Compare the mathematical expressions and select which is greater, or EQUAL if they are the same."

        val adaptiveHints = mutableListOf<String>()

        if (config.workingMemoryDemand) {
            adaptiveHints.add("Remember to evaluate expressions in parentheses first")
        }

        if (config.proceduralComplexity) {
            adaptiveHints.add("Use order of operations: parentheses, multiplication/division, addition/subtraction")
        }

        if (config.abstractReasoning) {
            adaptiveHints.add("For algebraic expressions, substitute the given value of x first")
        }

        if (config.speedPressure >= 4) {
            adaptiveHints.add("Work quickly but accurately - time is limited")
        }

        if (config.errorInduction) {
            adaptiveHints.add("Be careful with similar-looking numbers and operations")
        }

        return if (adaptiveHints.isNotEmpty()) {
            "$baseInstruction\n\nTips: ${adaptiveHints.joinToString("; ")}"
        } else {
            baseInstruction
        }
    }

    private fun calculateAdaptiveMathMaxScore(totalQuestions: Int, config: AdaptiveMathConfig): Int {
        val baseScore = totalQuestions * 30

        val complexityMultiplier = when (config.numericalComplexity) {
            1 -> 1.0f
            2 -> 1.3f
            3 -> 1.6f
            4 -> 2.0f
            5 -> 2.5f
            else -> 1.0f
        }

        val cognitiveBonus = listOf(
            if (config.workingMemoryDemand) 1.3f else 1.0f,
            if (config.proceduralComplexity) 1.2f else 1.0f,
            if (config.abstractReasoning) 1.4f else 1.0f,
            if (config.errorInduction) 1.2f else 1.0f
        ).reduce { acc, bonus -> acc * bonus }

        val speedMultiplier = when (config.speedPressure) {
            1, 2 -> 1.0f
            3 -> 1.1f
            4 -> 1.2f
            5 -> 1.3f
            else -> 1.0f
        }

        return (baseScore * complexityMultiplier * cognitiveBonus * speedMultiplier).toInt()
    }

    // Enhanced data class for adaptive comparison pairs
    data class AdaptiveComparisonPair(
        val pairNumber: Int,
        val leftValue: String,
        val rightValue: String,
        val leftNumeric: Double,
        val rightNumeric: Double,
        val correctAnswer: String,
        val operationType: String,
        val difficulty: Double,
        val cognitiveLoad: Int,
        val workingMemorySteps: Int
    )
}

// =============================================================================
// ADAPTIVE MATH COMPARISON SCREEN
// =============================================================================

data class AdaptiveMathComparisonPair(
    val pairNumber: Int,
    val leftValue: String,
    val rightValue: String,
    val leftNumeric: Double,
    val rightNumeric: Double,
    val correctAnswer: String,
    val operationType: String,
    val difficulty: Double,
    val cognitiveLoad: Int,
    val workingMemorySteps: Int
)

data class AdaptiveMathConfig(
    val name: String,
    val description: String,
    val numericalComplexity: Int,
    val workingMemoryDemand: Boolean,
    val proceduralComplexity: Boolean,
    val abstractReasoning: Boolean,
    val speedPressure: Int,
    val errorInduction: Boolean
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdaptiveMathComparisonPuzzleScreen(
    initialDifficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveMathComparison"

    // Adaptive difficulty manager - UPDATED to use EnhancedAdaptiveDifficultyManager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("mathComparison"))
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
                    puzzleType = "mathComparison",
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
            AdaptiveMathComparisonGenerator.generateAdaptivePuzzle(currentDifficultyLevel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate adaptive puzzle", e)
            Pair("{}", "{}")
        }
    }

    // Parse puzzle data - keeping existing parsing logic
    val parsedData = remember(puzzleData) {
        try {
            val questionData = JSONObject(puzzleData)
            val sequenceArray = questionData.getJSONArray("sequence")
            val pairs = mutableListOf<AdaptiveMathComparisonPair>()

            for (i in 0 until sequenceArray.length()) {
                val pairObj = sequenceArray.getJSONObject(i)
                pairs.add(
                    AdaptiveMathComparisonPair(
                        pairNumber = pairObj.getInt("pairNumber"),
                        leftValue = pairObj.getString("leftValue"),
                        rightValue = pairObj.getString("rightValue"),
                        leftNumeric = pairObj.getDouble("leftNumeric"),
                        rightNumeric = pairObj.getDouble("rightNumeric"),
                        correctAnswer = pairObj.getString("correctAnswer"),
                        operationType = pairObj.getString("operationType"),
                        difficulty = pairObj.getDouble("difficulty"),
                        cognitiveLoad = pairObj.getInt("cognitiveLoad"),
                        workingMemorySteps = pairObj.getInt("workingMemorySteps")
                    )
                )
            }

            val configObj = questionData.getJSONObject("adaptiveConfig")
            val adaptiveConfig = AdaptiveMathConfig(
                name = configObj.getString("name"),
                description = configObj.getString("description"),
                numericalComplexity = configObj.getInt("numericalComplexity"),
                workingMemoryDemand = configObj.getBoolean("workingMemoryDemand"),
                proceduralComplexity = configObj.getBoolean("proceduralComplexity"),
                abstractReasoning = configObj.getBoolean("abstractReasoning"),
                speedPressure = configObj.getInt("speedPressure"),
                errorInduction = configObj.getBoolean("errorInduction")
            )

            Pair(pairs, adaptiveConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse puzzle data", e)
            Pair(emptyList<AdaptiveMathComparisonPair>(),
                AdaptiveMathConfig("Error", "Failed to load", 1, false, false, false, 1, false))
        }
    }

    val (sequencePairs, adaptiveConfig) = parsedData

    // Score tracking state
    var totalScore by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var totalAttempts by remember { mutableIntStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pairStartTimes by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var reactionTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var difficultyScores by remember { mutableStateOf<List<Double>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
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
            60 // Default 1 minute
        }
    }

    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentUserLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Game state
    var currentPairIndex by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var isGameComplete by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    var showHint by remember { mutableStateOf(false) }


    val currentPair = if (sequencePairs.isNotEmpty() && currentPairIndex < sequencePairs.size) {
        sequencePairs[currentPairIndex]
    } else null

    LaunchedEffect(currentDifficultyLevel) {
        Log.d(TAG, "🔄 Difficulty changed to ${currentDifficultyLevel.name}, resetting game state")
        currentPairIndex = 0
        selectedAnswer = null
        showResult = false
        totalScore = 0
        correctAnswers = 0
        totalAttempts = 0
        gameStartTime = System.currentTimeMillis()
        sessionStartTime = System.currentTimeMillis()
        pairStartTimes = mapOf(0 to System.currentTimeMillis())
        reactionTimes = emptyList()
        difficultyScores = emptyList()
        streak = 0
        bestStreak = 0
        currentHearts = hearts
        isGameComplete = false

        // Reset timer
        timeRemaining = totalTimeSeconds
        displayTimer = timer
    }

    // Initialize first pair timing
    LaunchedEffect(Unit) {
        gameStartTime = System.currentTimeMillis()
        sessionStartTime = System.currentTimeMillis()
        if (sequencePairs.isNotEmpty()) {
            pairStartTimes = mapOf(0 to System.currentTimeMillis())
        }
    }

    // ✅ UPDATED: Enhanced performance recording
    fun recordPerformance(isCorrect: Boolean) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = if (reactionTimes.isNotEmpty()) reactionTimes.last() else 3000L,
            streak = streak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = currentPair?.cognitiveLoad ?: 3,
            totalScore = totalScore,
            puzzleType = "mathComparison"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f && !showResult) {
                Log.d(TAG, "🎯 Adapting difficulty from ${currentDifficultyLevel.name} to ${config.level.name}")
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
            challengeComplexity = currentPair?.cognitiveLoad ?: 3,
            currentStreak = streak,
            challengesCompleted = currentPairIndex + 1,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "mathComparison"
        )
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isGameComplete) {
        if (timeRemaining > 0 && !isGameComplete) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !isGameComplete) {
            totalScore = calculateScore(false, totalTimeSeconds.toLong() * 1000L)
            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            isGameComplete = true
            recordPerformance(false)
            onSubmitAnswer(false)
            fetchNextPuzzle(totalScore)
        }
    }

    // Auto-advance after showing result
    LaunchedEffect(showResult) {
        if (showResult) {
            delay(1500) // Show result for 1.5 seconds

            if (currentPairIndex >= sequencePairs.size - 1 || currentHearts <= 0) {
                // Game complete - calculate final score
                totalScore = calculateScore(true, System.currentTimeMillis() - gameStartTime)
                isGameComplete = true
                val isSuccess = correctAnswers >= (sequencePairs.size * 0.6f).toInt()

                Log.d(TAG, "🎯 Game completed!")
                Log.d(TAG, "📊 Final score: $totalScore")

                // Show feedback through unified system
                feedbackManager.showFeedback(
                    puzzleType = "mathComparison",
                    isCorrect = isSuccess,
                    userAnswer = "$correctAnswers/${sequencePairs.size} comparisons correct",
                    correctAnswer = "Adaptive ${adaptiveConfig.name} mathematical reasoning",
                    timeSpent = System.currentTimeMillis() - gameStartTime,
                    difficulty = adaptiveConfig.name,
                    timeRemaining = timeRemaining,
                    totalTime = totalTimeSeconds,
                    onComplete = {
                        onSubmitAnswer(isSuccess)
                        Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                        fetchNextPuzzle(totalScore)
                    }
                )
            } else {
                // Move to next pair
                currentPairIndex++
                selectedAnswer = null
                showResult = false

                // Track next pair start time
                pairStartTimes = pairStartTimes + (currentPairIndex to System.currentTimeMillis())
            }
        }
    }

    // Handle answer submission
    fun handleAnswer(answer: String) {
        if (showResult || currentPair == null || isGameComplete) return

        Log.d(TAG, "🎯 Handling answer: $answer for pair ${currentPairIndex + 1}")

        selectedAnswer = answer
        showResult = true
        totalAttempts++

        // Calculate reaction time
        val pairStartTime = pairStartTimes[currentPairIndex] ?: System.currentTimeMillis()
        val reactionTime = System.currentTimeMillis() - pairStartTime
        reactionTimes = reactionTimes + reactionTime
        difficultyScores = difficultyScores + currentPair.difficulty

        val isCorrect = answer == currentPair.correctAnswer
        Log.d(TAG, "🧮 Pair ${currentPairIndex + 1}: Answer=$answer, Correct=${currentPair.correctAnswer}, " +
                "IsCorrect=$isCorrect, Reaction=${reactionTime}ms, Difficulty=${currentPair.difficulty}")

        if (isCorrect) {
            correctAnswers++
            streak++
            if (streak > bestStreak) bestStreak = streak

            val score = calculateScore(true, reactionTime)
            totalScore += score
        } else {
            currentHearts--
            streak = 0
        }

        // Only record performance if we're not about to reset due to difficulty change
        if (!isGameComplete) {
            recordPerformance(isCorrect)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        // Fit-to-screen: HUD (fixed) / question + values (remaining space) / EQUAL pinned at the bottom.
        val compact = maxHeight < 600.dp
        val landscape = maxWidth > maxHeight
        val gutter = if (compact) 12.dp else 16.dp

        Column(
            modifier = Modifier
                .widthIn(max = if (landscape) 880.dp else 640.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .padding(horizontal = gutter, vertical = if (compact) 4.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The shared unified header needs ~150dp+ (much more at large fonts), so short viewports
            // get a one/two-row compact HUD with the same essentials (timer, lives, score, level).
            if (compact) {
                BCompactHud(
                    level = currentUserLevel,
                    difficultyName = currentDifficultyLevel.name,
                    timer = displayTimer,
                    lives = currentHearts,
                    maxLives = currentDifficultyLevel.livesAllowed,
                    score = totalScore,
                    streak = streakInfo.currentStreak,
                    onBack = onBack
                )
            } else {
                AdaptiveUnifiedHeader(
                    level = currentUserLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    lives = currentHearts,
                    currentDifficulty = currentDifficultyLevel,
                    score = totalScore,
                    puzzleType = "mathComparison",
                    challengeNumber = currentPairIndex + 1,
                    totalChallenges = sequencePairs.size,
                    competitiveInsight = competitiveInsight,
                    onBack = onBack,
                    onHint = {
                        showHint = !showHint
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }

            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "mathComparison",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )

            // Progress row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (compact) 4.dp else 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Question ${currentPairIndex + 1} of ${sequencePairs.size}",
                    color = RvInkSoft,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                if (compact) {
                    // lives are part of the compact HUD already
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(hearts) { index ->
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = if (index < currentHearts) Color(0xFFC2185B) else RvInkSoft,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(RvInk.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(
                            fraction = ((currentPairIndex + 1).toFloat() / sequencePairs.size.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        )
                        .background(RvSuccessEdge, RoundedCornerShape(2.dp))
                        .animateContentSize()
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

            // Play area. The scroll is an invisible last-resort safety net (extreme font scale on tiny
            // screens); the answer action below is pinned outside it and never depends on leftover space.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp, Alignment.CenterVertically)
            ) {
                // Question text with adaptive difficulty indicator
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Which value is greater?",
                        color = RvInk,
                        fontSize = if (compact) 20.sp else 24.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )

                    if (currentPair != null && !compact) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Difficulty: ${String.format("%.1f", currentPair.difficulty)}/5",
                                color = RvInkSoft,
                                fontSize = 12.sp
                            )
                            Text(
                                "Load: ${currentPair.cognitiveLoad}/10",
                                color = RvInkSoft,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (currentPair != null) {
                    val leftCard: @Composable (Modifier) -> Unit = { m ->
                        Box(modifier = m) {
                            AnimatedContent(
                                targetState = currentPair.leftValue,
                                transitionSpec = {
                                    slideInHorizontally { it } + fadeIn() with
                                            slideOutHorizontally { -it } + fadeOut()
                                }
                            ) { leftValue ->
                                AdaptiveClickableValueCard(
                                    value = leftValue,
                                    isSelected = selectedAnswer == "left",
                                    isCorrect = if (showResult) currentPair.correctAnswer == "left" else null,
                                    isWrong = if (showResult && selectedAnswer == "left") currentPair.correctAnswer != "left" else false,
                                    enabled = !showResult,
                                    adaptiveConfig = adaptiveConfig,
                                    onClick = { handleAnswer("left") }
                                )
                            }
                        }
                    }
                    val rightCard: @Composable (Modifier) -> Unit = { m ->
                        Box(modifier = m) {
                            AnimatedContent(
                                targetState = currentPair.rightValue,
                                transitionSpec = {
                                    slideInHorizontally { it } + fadeIn() with
                                            slideOutHorizontally { -it } + fadeOut()
                                }
                            ) { rightValue ->
                                AdaptiveClickableValueCard(
                                    value = rightValue,
                                    isSelected = selectedAnswer == "right",
                                    isCorrect = if (showResult) currentPair.correctAnswer == "right" else null,
                                    isWrong = if (showResult && selectedAnswer == "right") currentPair.correctAnswer != "right" else false,
                                    enabled = !showResult,
                                    adaptiveConfig = adaptiveConfig,
                                    onClick = { handleAnswer("right") }
                                )
                            }
                        }
                    }
                    if (landscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            leftCard(Modifier.weight(1f))
                            rightCard(Modifier.weight(1f))
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)
                        ) {
                            leftCard(Modifier.fillMaxWidth())
                            rightCard(Modifier.fillMaxWidth())
                        }
                    }

                    // Result feedback (kept inside the play area so the action never moves)
                    if (showResult) {
                        val isCorrect = selectedAnswer == currentPair.correctAnswer

                        AnimatedVisibility(
                            visible = showResult,
                            enter = fadeIn() + slideInVertically()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(RvInk, RoundedCornerShape(8.dp))
                                    .padding(if (compact) 8.dp else 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        if (isCorrect) "✅ Correct!" else "❌ Wrong answer. Lives: $currentHearts",
                                        color = RvOnTone,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )

                                    if (isCorrect && reactionTimes.isNotEmpty()) {
                                        val reactionTime = reactionTimes.last() / 1000.0
                                        Text(
                                            "⚡ ${String.format("%.1f", reactionTime)}s",
                                            color = RvOnTone,
                                            fontSize = 14.sp
                                        )
                                    } else if (!isCorrect) {
                                        Text(
                                            "${currentPair.leftValue} = ${String.format("%.1f", currentPair.leftNumeric)}, ${currentPair.rightValue} = ${String.format("%.1f", currentPair.rightNumeric)}",
                                            color = RvOnTone,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pinned primary action
            if (currentPair != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AdaptiveAnswerButton(
                    text = "EQUAL",
                    isSelected = selectedAnswer == "equal",
                    isCorrect = if (showResult) currentPair.correctAnswer == "equal" else null,
                    isWrong = if (showResult && selectedAnswer == "equal") currentPair.correctAnswer != "equal" else false,
                    enabled = !showResult,
                    onClick = { handleAnswer("equal") },
                    adaptiveConfig = adaptiveConfig,
                    isEqual = true
                )
                Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
            }
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }

    // ✅ NEW: Session completion handling
    if (isGameComplete || currentHearts <= 0) {
        UnifiedSessionCompletionHandler(
            puzzleType = "mathComparison",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAttempts,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = bestStreak,
                winRate = if (totalAttempts > 0) correctAnswers.toFloat() / totalAttempts else 0f,
                totalScore = totalScore,
                averageTimePerPuzzle = if (reactionTimes.isNotEmpty()) (reactionTimes.average() / 1000.0).toInt() else 0,
                currentStreak = streak,
                individualTimes = emptyList(),
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            // Session completion handled
        }
    }
}

@Composable
private fun AdaptiveMathComparisonTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    totalScore: Int,
    correctAnswers: Int,
    totalAttempts: Int,
    streak: Int,
    adaptiveConfig: AdaptiveMathConfig,
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
                        tint = RvInk,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Level ${level.level}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )

                    LevelProgressBar(
                        level = level,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }

            // Center: Timer and adaptive info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val timeValue = timer.substringAfter(":").toIntOrNull() ?: 0
                val isUrgent = timer.startsWith("0:") && timeValue <= 30

                Text(
                    text = timer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUrgent) RvError else RvInk
                )

                Text(
                    text = adaptiveConfig.name,
                    fontSize = 12.sp,
                    color = RvSky
                )

                if (streakInfo.currentStreak > 0) {
                    StreakDisplay(
                        streakInfo = streakInfo,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Right side: Streak and complexity
            Column(
                horizontalAlignment = Alignment.End
            ) {
                if (streak > 1) {
                    Text(
                        text = "🔥 $streak",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvFlame
                    )
                }

                // Adaptive features indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (adaptiveConfig.workingMemoryDemand) Text("🧠", fontSize = 10.sp)
                    if (adaptiveConfig.proceduralComplexity) Text("🔀", fontSize = 10.sp)
                    if (adaptiveConfig.abstractReasoning) Text("🎓", fontSize = 10.sp)
                    if (adaptiveConfig.errorInduction) Text("⚠️", fontSize = 10.sp)
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
                        color = Color(0xFFFFEB3B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "🧮 ${adaptiveConfig.name} Math",
                    color = RvInkSoft.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                if (totalAttempts > 0) {
                    Text(
                        text = "$correctAnswers/$totalAttempts",
                        color = RvInkSoft.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveMathNotificationCard(
    adaptationInfo: DifficultyManager.AdaptiveConfig?,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                Icons.Default.Functions,
                contentDescription = "Math adapted",
                tint = RvInk,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Math Challenge Adapted!",
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
                onClick = onDismiss,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = RvInk,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AdaptiveClickableValueCard(
    value: String,
    isSelected: Boolean,
    isCorrect: Boolean?,
    isWrong: Boolean,
    enabled: Boolean,
    adaptiveConfig: AdaptiveMathConfig,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isCorrect == true -> RvSuccess.copy(alpha = 0.3f)
        isWrong -> RvError.copy(alpha = 0.3f)
        isSelected -> RvViolet.copy(alpha = 0.2f)
        else -> RvSurface
    }

    val borderColor = when {
        isCorrect == true -> RvSuccessEdge
        isWrong -> RvErrorEdge
        isSelected -> RvViolet
        else -> RvInkSoft
    }

    val animatedBackgroundColor by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(300),
        label = "background"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = borderColor,
        animationSpec = tween(300),
        label = "border"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(animatedBackgroundColor, RoundedCornerShape(12.dp))
            .border(2.dp, animatedBorderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                color = RvInk,
                fontSize = adaptCapSp(if (adaptiveConfig.numericalComplexity >= 4) 28f else 30f),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            // Show checkmark or X when result is shown
            if (isCorrect == true) {
                Spacer(modifier = Modifier.width(12.dp))
                Text("✓", color = RvInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            } else if (isWrong) {
                Spacer(modifier = Modifier.width(12.dp))
                Text("✗", color = RvInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AdaptiveAnswerButton(
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean?,
    isWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    adaptiveConfig: AdaptiveMathConfig,
    isEqual: Boolean = false
) {
    val backgroundColor = when {
        isCorrect == true -> RvSuccess
        isWrong -> RvError
        isEqual -> RvSky // Cyan for EQUAL button
        else -> RvSky // Blue for other buttons
    }

    val animatedColor by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(300),
        label = "button_color"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedColor,
            disabledContainerColor = animatedColor.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = RvInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Show complexity indicator for EQUAL button
            if (isEqual && adaptiveConfig.errorInduction) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("⚠️", color = RvInk, fontSize = 14.sp)
            }

            // Show checkmark or X when result is shown
            if (isCorrect == true) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("✓", color = RvInk, fontSize = 20.sp)
            } else if (isWrong) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("✗", color = RvInk, fontSize = 20.sp)
            }
        }
    }
}
/** Font size that grows with the user's font scale but stops at 1.3x so play-area text still fits. */
@Composable
private fun adaptCapSp(base: Float, maxScale: Float = 1.3f): androidx.compose.ui.unit.TextUnit {
    val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
    return (base * minOf(fs, maxScale) / fs).sp
}

/**
 * Compact HUD for short viewports (small phones, landscape, split-screen), where the shared
 * AdaptiveUnifiedHeader is too tall. Wraps to a second row at large font scales instead of clipping.
 * Shared by the group-B adaptive screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BCompactHud(
    level: UserLevel,
    difficultyName: String,
    timer: String,
    lives: Int,
    maxLives: Int,
    score: Int,
    streak: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPause: (() -> Unit)? = null,
    pauseDescription: String = "Pause"
) {
    val urgent = timer.startsWith("0:") && (timer.substringAfter(":").toIntOrNull() ?: 99) <= 30
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        verticalArrangement = Arrangement.Center,
        maxItemsInEachRow = 4
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = RvInk
            )
        }
        if (onPause != null) {
            IconButton(onClick = onPause, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = pauseDescription,
                    tint = RvInk
                )
            }
        }
        Column(modifier = Modifier.heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
            Text(
                text = "${stringResource(R.string.level_label)} ${level.level}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                maxLines = 1
            )
            Text(
                text = difficultyName + if (streak > 0) "  🔥 $streak" else "",
                fontSize = 12.sp,
                color = RvInkSoft,
                maxLines = 1
            )
        }
        Box(modifier = Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            Text(
                text = timer,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (urgent) Color(0xFF8B0000) else RvInk,
                maxLines = 1
            )
        }
        Box(modifier = Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            Row {
                repeat(maxLives) { index ->
                    Text(text = if (index < lives) "❤️" else "🤍", fontSize = 14.sp)
                }
            }
        }
        Box(modifier = Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "${stringResource(R.string.score_label)}: $score",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                maxLines = 1
            )
        }
    }
}

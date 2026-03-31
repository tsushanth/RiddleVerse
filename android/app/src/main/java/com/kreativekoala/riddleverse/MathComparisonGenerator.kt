package com.kreativekoala.riddleverse

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

data class ComparisonPuzzleResult(
    val success: Boolean,
    val puzzleData: ComparisonPuzzleData? = null,
    val message: String? = null
)

data class ComparisonPuzzleData(
    val question: String,
    val answer: String,
    val hint: String,
    val difficulty: String,
    val metadata: Map<String, Any>
)

data class ComparisonDifficultyConfig(
    val sequenceLength: Int,
    val numberRange: Pair<Int, Int>,
    val operations: List<String>,
    val allowDecimals: Boolean,
    val allowNegatives: Boolean,
    val timeLimit: Int,
    val scoring: ComparisonScoringConfig
)

data class ComparisonScoringConfig(
    val pointsPerCorrect: Int,
    val timeBonus: Boolean,
    val streakMultiplier: Double
)

class MathComparisonGenerator {
    private val TAG = "MathComparisonGen"
    private var debugMode = false

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
    }

    private fun debugLog(message: String, type: String = "info") {
        if (debugMode) {
            val emoji = when (type) {
                "error" -> "❌"
                "success" -> "✅"
                "warning" -> "⚠️"
                else -> "🔄"
            }
            Log.d(TAG, "$emoji MATH_COMPARISON: $message")
        }
    }

    /**
     * Generate a complete mathematical comparison puzzle
     */
    fun generateMathComparisonPuzzle(difficulty: String = "easy"): ComparisonPuzzleResult {
        debugLog("🎯 Generating $difficulty mathematical comparison puzzle...")

        return try {
            val difficultyConfig = getDifficultyConfig(difficulty)
            val puzzleSequence = generatePuzzleSequence(difficultyConfig)

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
                        })
                    }
                })
                put("totalPairs", puzzleSequence.size)
                put("difficulty", difficulty)
                put("timeLimit", difficultyConfig.timeLimit)
                put("scoring", JSONObject().apply {
                    put("pointsPerCorrect", difficultyConfig.scoring.pointsPerCorrect)
                    put("timeBonus", difficultyConfig.scoring.timeBonus)
                    put("streakMultiplier", difficultyConfig.scoring.streakMultiplier)
                })
                put("instructions", "Compare the values and select which is greater, or select EQUAL if they are the same.")
                put("metadata", JSONObject().apply {
                    put("generatedAt", System.currentTimeMillis())
                    put("difficulty", difficulty)
                    put("sequenceLength", puzzleSequence.size)
                    put("operationTypes", JSONObject().apply {
                        getOperationTypes(puzzleSequence).forEach { (type, count) ->
                            put(type, count)
                        }
                    })
                })
            }

            val answerData = JSONObject().apply {
                put("correctAnswers", JSONArray().apply {
                    puzzleSequence.forEach { pair ->
                        put(pair.correctAnswer)
                    }
                })
                put("scoring", JSONObject().apply {
                    put("pointsPerCorrect", difficultyConfig.scoring.pointsPerCorrect)
                    put("timeBonus", difficultyConfig.scoring.timeBonus)
                    put("streakMultiplier", difficultyConfig.scoring.streakMultiplier)
                })
                put("maxScore", puzzleSequence.size * difficultyConfig.scoring.pointsPerCorrect)
            }

            val puzzleData = ComparisonPuzzleData(
                question = questionData.toString(),
                answer = answerData.toString(),
                hint = "Compare ${puzzleSequence.size} mathematical expressions and choose the greater value or EQUAL",
                difficulty = difficulty,
                metadata = mapOf(
                    "totalPairs" to puzzleSequence.size,
                    "generatedAt" to System.currentTimeMillis(),
                    "puzzleType" to "mathematical_comparison"
                )
            )

            debugLog("✅ Generated ${puzzleSequence.size} comparison pairs for $difficulty difficulty", "success")
            ComparisonPuzzleResult(success = true, puzzleData = puzzleData)

        } catch (error: Exception) {
            debugLog("❌ Generation failed: ${error.message}", "error")
            ComparisonPuzzleResult(
                success = false,
                message = "Math comparison generation failed: ${error.message}"
            )
        }
    }

    /**
     * Get difficulty configuration
     */
    private fun getDifficultyConfig(difficulty: String): ComparisonDifficultyConfig {
        return when (difficulty.lowercase()) {
            "easy" -> ComparisonDifficultyConfig(
                sequenceLength = 8,
                numberRange = Pair(1, 20),
                operations = listOf("addition", "subtraction", "simple_multiplication"),
                allowDecimals = false,
                allowNegatives = false,
                timeLimit = 60000, // 60 seconds
                scoring = ComparisonScoringConfig(
                    pointsPerCorrect = 100,
                    timeBonus = true,
                    streakMultiplier = 1.1
                )
            )
            "medium" -> ComparisonDifficultyConfig(
                sequenceLength = 12,
                numberRange = Pair(1, 50),
                operations = listOf("addition", "subtraction", "multiplication", "division", "mixed"),
                allowDecimals = true,
                allowNegatives = false,
                timeLimit = 90000, // 90 seconds
                scoring = ComparisonScoringConfig(
                    pointsPerCorrect = 150,
                    timeBonus = true,
                    streakMultiplier = 1.2
                )
            )
            "hard" -> ComparisonDifficultyConfig(
                sequenceLength = 15,
                numberRange = Pair(1, 100),
                operations = listOf("multiplication", "division", "mixed", "powers", "fractions"),
                allowDecimals = true,
                allowNegatives = true,
                timeLimit = 120000, // 120 seconds
                scoring = ComparisonScoringConfig(
                    pointsPerCorrect = 200,
                    timeBonus = true,
                    streakMultiplier = 1.3
                )
            )
            else -> getDifficultyConfig("medium") // Default fallback
        }
    }

    /**
     * Generate a sequence of comparison pairs with progressive difficulty
     */
    private fun generatePuzzleSequence(config: ComparisonDifficultyConfig): List<ComparisonPair> {
        val sequence = mutableListOf<ComparisonPair>()
        val sequenceLength = config.sequenceLength

        // Progressive difficulty within the sequence
        for (i in 0 until sequenceLength) {
            val progressRatio = i.toDouble() / sequenceLength
            val currentConfig = getProgressiveConfig(config, progressRatio)

            val pair = generateComparisonPair(currentConfig, i + 1)
            sequence.add(pair)
        }

        return sequence
    }

    /**
     * Adjust difficulty progressively through the sequence
     */
    private fun getProgressiveConfig(baseConfig: ComparisonDifficultyConfig, progressRatio: Double): ComparisonDifficultyConfig {
        val (min, max) = baseConfig.numberRange

        // Increase number range as we progress
        val rangeDiff = max - min
        val newMax = (min + rangeDiff * (0.3 + progressRatio * 0.7)).roundToInt()

        return baseConfig.copy(
            numberRange = Pair(min, newMax)
        )
    }

    /**
     * Generate a single comparison pair
     */
    private fun generateComparisonPair(config: ComparisonDifficultyConfig, pairNumber: Int): ComparisonPair {
        val complexity = (pairNumber - 1).toDouble() / config.sequenceLength

        // Choose operation type based on difficulty progression
        val operationType = selectOperation(config.operations, complexity)

        // Generate the two values to compare
        val leftValue = generateValue(operationType, config, "left")
        val rightValue = generateValue(operationType, config, "right")

        // Calculate actual numeric values
        val leftNumeric = evaluateExpression(leftValue)
        val rightNumeric = evaluateExpression(rightValue)

        // Determine correct answer
        val tolerance = 0.001 // For floating point comparison
        val correctAnswer = when {
            abs(leftNumeric - rightNumeric) < tolerance -> "equal"
            leftNumeric > rightNumeric -> "left"
            else -> "right"
        }

        return ComparisonPair(
            pairNumber = pairNumber,
            leftValue = leftValue,
            rightValue = rightValue,
            leftNumeric = leftNumeric,
            rightNumeric = rightNumeric,
            correctAnswer = correctAnswer,
            operationType = operationType,
            difficulty = calculatePairDifficulty(leftValue, rightValue, operationType)
        )
    }

    /**
     * Select operation type based on complexity
     */
    private fun selectOperation(availableOps: List<String>, complexity: Double): String {
        // Weight operations by complexity
        val operationWeights = mapOf(
            "addition" to 0.1,
            "subtraction" to 0.2,
            "simple_multiplication" to 0.3,
            "multiplication" to 0.5,
            "division" to 0.7,
            "mixed" to 0.8,
            "powers" to 0.9,
            "fractions" to 0.95
        )

        // Filter operations that are appropriate for current complexity
        val suitableOps = availableOps.filter { op ->
            (operationWeights[op] ?: 0.5) <= (complexity + 0.3)
        }

        return if (suitableOps.isNotEmpty()) {
            suitableOps.random()
        } else {
            availableOps.firstOrNull() ?: "addition" // Fallback
        }
    }

    /**
     * Generate a mathematical value/expression
     */
    private fun generateValue(operationType: String, config: ComparisonDifficultyConfig, side: String): String {
        val numberRange = config.numberRange
        val allowDecimals = config.allowDecimals

        return when (operationType) {
            "addition" -> generateAddition(numberRange, allowDecimals)
            "subtraction" -> generateSubtraction(numberRange, allowDecimals)
            "simple_multiplication" -> generateSimpleMultiplication(numberRange)
            "multiplication" -> generateMultiplication(numberRange, allowDecimals)
            "division" -> generateDivision(numberRange, allowDecimals)
            "mixed" -> generateMixedExpression(numberRange, allowDecimals)
            "powers" -> generatePowerExpression(numberRange)
            "fractions" -> generateFractionExpression(numberRange)
            else -> generateSimpleNumber(numberRange, allowDecimals)
        }
    }

    /**
     * Generate addition expression
     */
    private fun generateAddition(range: Pair<Int, Int>, allowDecimals: Boolean): String {
        val a = randomNumber(range, allowDecimals)
        val b = randomNumber(range, allowDecimals)
        return "$a + $b"
    }

    /**
     * Generate subtraction expression
     */
    private fun generateSubtraction(range: Pair<Int, Int>, allowDecimals: Boolean): String {
        val a = randomNumber(range, allowDecimals)
        val maxB = minOf(a.toInt(), range.second)
        val b = randomNumber(Pair(range.first, maxB), allowDecimals)
        return "$a - $b"
    }

    /**
     * Generate simple multiplication (single digits)
     */
    private fun generateSimpleMultiplication(range: Pair<Int, Int>): String {
        val a = randomNumber(Pair(2, 9), false).toInt()
        val b = randomNumber(Pair(2, 9), false).toInt()
        return "$a × $b"
    }

    /**
     * Generate multiplication expression
     */
    private fun generateMultiplication(range: Pair<Int, Int>, allowDecimals: Boolean): String {
        // Keep numbers smaller for multiplication
        val maxVal = minOf(range.second, 15)
        val a = randomNumber(Pair(range.first, maxVal), allowDecimals)
        val b = randomNumber(Pair(range.first, maxVal), allowDecimals)
        return "$a × $b"
    }

    /**
     * Generate division expression
     */
    private fun generateDivision(range: Pair<Int, Int>, allowDecimals: Boolean): String {
        val b = randomNumber(Pair(2, minOf(range.second, 10)), false).toInt()
        val result = randomNumber(Pair(1, range.second), allowDecimals)
        val a = (b * result.toDouble()).let {
            if (allowDecimals) it else it.toInt().toDouble()
        }
        return "$a ÷ $b"
    }

    /**
     * Generate mixed expression
     */
    private fun generateMixedExpression(range: Pair<Int, Int>, allowDecimals: Boolean): String {
        val operations = listOf("+", "-", "×")
        val op1 = operations.random()
        val op2 = operations.random()

        val maxVal = minOf(range.second, 12)
        val a = randomNumber(Pair(range.first, maxVal), allowDecimals)
        val b = randomNumber(Pair(range.first, maxVal), allowDecimals)
        val c = randomNumber(Pair(range.first, maxVal), allowDecimals)

        return "$a $op1 $b $op2 $c"
    }

    /**
     * Generate power expression
     */
    private fun generatePowerExpression(range: Pair<Int, Int>): String {
        val base = randomNumber(Pair(2, minOf(range.second, 8)), false).toInt()
        val exponent = randomNumber(Pair(2, 4), false).toInt()
        return "$base^$exponent"
    }

    /**
     * Generate fraction expression
     */
    private fun generateFractionExpression(range: Pair<Int, Int>): String {
        val numerator = randomNumber(Pair(1, range.second), false).toInt()
        val denominator = randomNumber(Pair(2, 10), false).toInt()
        return "$numerator/$denominator"
    }

    /**
     * Generate simple number
     */
    private fun generateSimpleNumber(range: Pair<Int, Int>, allowDecimals: Boolean): String {
        return randomNumber(range, allowDecimals).toString()
    }

    /**
     * Generate random number within range
     */
    private fun randomNumber(range: Pair<Int, Int>, allowDecimals: Boolean): Double {
        val min = range.first
        val max = range.second

        return if (allowDecimals && Random.nextDouble() < 0.3) { // 30% chance for decimals
            ((Random.nextDouble() * (max - min) + min) * 10).roundToInt() / 10.0
        } else {
            Random.nextInt(min, max + 1).toDouble()
        }
    }

    /**
     * Evaluate mathematical expression to numeric value
     */
    private fun evaluateExpression(expression: String): Double {
        return try {
            // Convert mathematical symbols and evaluate
            val result = when {
                // Handle fractions
                expression.contains("/") && !expression.contains(" ") -> {
                    val parts = expression.split("/")
                    if (parts.size == 2) {
                        val num = parts[0].toDouble()
                        val den = parts[1].toDouble()
                        num / den
                    } else {
                        evaluateComplexExpression(expression)
                    }
                }
                // Handle powers
                expression.contains("^") -> {
                    val parts = expression.split("^")
                    if (parts.size == 2) {
                        val base = parts[0].toDouble()
                        val exp = parts[1].toDouble()
                        base.pow(exp)
                    } else {
                        evaluateComplexExpression(expression)
                    }
                }
                // Handle simple operations
                else -> evaluateComplexExpression(expression)
            }
            result
        } catch (error: Exception) {
            debugLog("Error evaluating expression \"$expression\": ${error.message}", "error")
            0.0
        }
    }

    /**
     * Evaluate complex mathematical expressions
     */
    private fun evaluateComplexExpression(expression: String): Double {
        // Replace mathematical symbols
        val jsExpression = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("^", "**")

        // Simple expression evaluator (handles basic arithmetic with order of operations)
        return evaluateArithmetic(jsExpression)
    }

    /**
     * Simple arithmetic evaluator
     */
    private fun evaluateArithmetic(expression: String): Double {
        // Remove spaces
        val expr = expression.replace(" ", "")

        // Handle parentheses first (if any)
        var workingExpr = expr

        // Handle multiplication and division first
        workingExpr = processOperations(workingExpr, listOf("*", "/"))

        // Handle addition and subtraction
        workingExpr = processOperations(workingExpr, listOf("+", "-"))

        return workingExpr.toDoubleOrNull() ?: 0.0
    }

    /**
     * Process specific operations in order
     */
    private fun processOperations(expression: String, operations: List<String>): String {
        var expr = expression

        for (op in operations) {
            while (expr.contains(op)) {
                val regex = when (op) {
                    "*" -> """(\d+(?:\.\d+)?)\*(\d+(?:\.\d+)?)""".toRegex()
                    "/" -> """(\d+(?:\.\d+)?)/(\d+(?:\.\d+)?)""".toRegex()
                    "+" -> """(\d+(?:\.\d+)?)\+(\d+(?:\.\d+)?)""".toRegex()
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

    /**
     * Calculate difficulty rating for a pair
     */
    private fun calculatePairDifficulty(leftValue: String, rightValue: String, operationType: String): Double {
        var difficulty = 1.0

        // Base difficulty by operation type
        val operationDifficulty = mapOf(
            "addition" to 1.0,
            "subtraction" to 1.2,
            "simple_multiplication" to 1.5,
            "multiplication" to 2.0,
            "division" to 2.5,
            "mixed" to 3.0,
            "powers" to 3.5,
            "fractions" to 4.0
        )

        difficulty *= operationDifficulty[operationType] ?: 1.0

        // Increase difficulty for longer expressions
        val avgLength = (leftValue.length + rightValue.length) / 2.0
        difficulty *= (1 + avgLength * 0.1)

        return (difficulty * 10).roundToInt() / 10.0 // Round to 1 decimal
    }

    /**
     * Get summary of operation types used
     */
    private fun getOperationTypes(sequence: List<ComparisonPair>): Map<String, Int> {
        val types = mutableMapOf<String, Int>()
        sequence.forEach { pair ->
            types[pair.operationType] = (types[pair.operationType] ?: 0) + 1
        }
        return types
    }
}
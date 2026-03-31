// AdaptiveMathPuzzleGenerators.kt - Enhanced generators that work with adaptive difficulty levels
package com.kreativekoala.riddleverse

import android.util.Log
import kotlin.math.*
import kotlin.random.Random

/**
 * Enhanced Math Puzzle Generators that integrate with adaptive difficulty system
 */
class AdaptiveMathPuzzleGenerators {

    companion object {
        private const val TAG = "AdaptiveMathGenerators"
    }

    // Track used combinations to prevent duplicates within session
    private val usedCombinations = mutableMapOf<String, MutableSet<String>>()

    init {
        // Initialize used combinations for each puzzle type
        usedCombinations["average"] = mutableSetOf()
        usedCombinations["estimation"] = mutableSetOf()
        usedCombinations["tipping"] = mutableSetOf()
        usedCombinations["percentages"] = mutableSetOf()
        usedCombinations["division"] = mutableSetOf()
        usedCombinations["subtraction"] = mutableSetOf()
    }

    /**
     * Generate Average Puzzle based on adaptive difficulty level
     */
    fun generateAverageForLevel(difficultyLevel: DifficultyManager.DifficultyLevel): AveragePuzzle? {
        Log.d(TAG, "🎯 Generating Average puzzle for level: ${difficultyLevel.name}")

        // Map adaptive difficulty levels to puzzle parameters
        val (numberCount, numberRange, complexityFactor) = when (difficultyLevel.name.lowercase()) {
            "tutorial" -> Triple(Pair(2, 2), Pair(5, 20), 1.0f)
            "beginner" -> Triple(Pair(2, 3), Pair(10, 30), 1.1f)
            "easy" -> Triple(Pair(3, 3), Pair(10, 50), 1.2f)
            "easy+" -> Triple(Pair(3, 4), Pair(15, 60), 1.3f)
            "medium-" -> Triple(Pair(3, 4), Pair(20, 80), 1.4f)
            "medium" -> Triple(Pair(4, 4), Pair(20, 100), 1.5f)
            "medium+" -> Triple(Pair(4, 5), Pair(30, 120), 1.6f)
            "hard-" -> Triple(Pair(4, 5), Pair(40, 150), 1.7f)
            "hard" -> Triple(Pair(5, 5), Pair(50, 200), 1.8f)
            "hard+" -> Triple(Pair(5, 6), Pair(60, 250), 1.9f)
            "expert-" -> Triple(Pair(5, 6), Pair(80, 300), 2.0f)
            "expert" -> Triple(Pair(6, 6), Pair(100, 400), 2.1f)
            "expert+" -> Triple(Pair(6, 7), Pair(120, 500), 2.2f)
            "master" -> Triple(Pair(7, 8), Pair(150, 600), 2.3f)
            "grandmaster" -> Triple(Pair(8, 10), Pair(200, 800), 2.5f)
            else -> Triple(Pair(4, 4), Pair(20, 100), 1.5f)
        }

        for (attempt in 1..50) {
            try {
                val count = Random.nextInt(numberCount.first, numberCount.second + 1)

                // STRATEGY: Generate target average first (whole number), then generate numbers around it
                val baseAverage = Random.nextInt(
                    (numberRange.first * 1.2 / complexityFactor).toInt(),
                    (numberRange.second * 0.8 / complexityFactor).toInt() + 1
                )

                // Add complexity for higher levels
                val targetAverage = when {
                    difficultyLevel.name.contains("master", ignoreCase = true) -> {
                        // Masters get trickier averages (not perfectly round)
                        baseAverage + Random.nextInt(-5, 6)
                    }
                    difficultyLevel.name.contains("expert", ignoreCase = true) -> {
                        // Expert levels get larger averages
                        baseAverage + Random.nextInt(0, 20)
                    }
                    else -> baseAverage
                }.coerceAtLeast(1)

                Log.d(TAG, "Attempt $attempt: Targeting average $targetAverage with $count numbers")

                val numbers = mutableListOf<Int>()
                var sum = 0

                // Generate first n-1 numbers around the target average
                for (i in 0 until count - 1) {
                    // Add variation based on difficulty level
                    val maxDeviation = when {
                        difficultyLevel.name.contains("tutorial", ignoreCase = true) -> targetAverage / 4
                        difficultyLevel.name.contains("beginner", ignoreCase = true) -> targetAverage / 3
                        difficultyLevel.name.contains("easy", ignoreCase = true) -> targetAverage / 2
                        else -> targetAverage
                    }

                    val deviation = Random.nextInt(-maxDeviation, maxDeviation + 1)
                    val num = (targetAverage + deviation).coerceIn(
                        numberRange.first,
                        (numberRange.second * complexityFactor).toInt()
                    )
                    numbers.add(num)
                    sum += num
                }

                // Calculate last number to achieve exact target average
                val lastNumber = (targetAverage * count) - sum

                // Verify last number is in valid range
                val validRange = numberRange.first..(numberRange.second * complexityFactor).toInt()
                if (lastNumber in validRange) {
                    numbers.add(lastNumber)

                    // Verify the average is exactly a whole number
                    val finalSum = numbers.sum()
                    val finalAverage = finalSum.toDouble() / count

                    if (kotlin.math.abs(finalAverage - finalAverage.toInt()) < 0.001) {
                        val combinationKey = "${difficultyLevel.name}_${numbers.sorted().joinToString("_")}"

                        if (!usedCombinations["average"]!!.contains(combinationKey)) {
                            usedCombinations["average"]!!.add(combinationKey)

                            Log.d(TAG, "✅ Generated Average puzzle for ${difficultyLevel.name} (attempt $attempt)")
                            Log.d(TAG, "  Numbers: $numbers")
                            Log.d(TAG, "  Sum: $finalSum")
                            Log.d(TAG, "  Average: ${finalAverage.toInt()} (verified whole number)")
                            Log.d(TAG, "  Complexity: ${complexityFactor}x")

                            return AveragePuzzle(
                                numbers = numbers,
                                average = finalAverage.toInt().toDouble(),
                                difficulty = difficultyLevel.name,
                                hint = generateAverageHint(count, difficultyLevel.name)
                            )
                        }
                    } else {
                        Log.d(TAG, "Attempt $attempt: Non-whole average $finalAverage, retrying...")
                    }
                } else {
                    Log.d(TAG, "Attempt $attempt: Last number $lastNumber out of range $validRange, retrying...")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating Average for ${difficultyLevel.name} on attempt $attempt: ${e.message}")
                continue
            }
        }

        Log.e(TAG, "❌ Failed to generate Average puzzle for ${difficultyLevel.name} after 50 attempts")
        return null
    }

    /**
     * Generate Math Estimation Puzzle based on adaptive difficulty level
     */
    fun generateEstimationForLevel(difficultyLevel: DifficultyManager.DifficultyLevel): MathEstimationPuzzle? {
        Log.d(TAG, "🎯 Generating Estimation puzzle for level: ${difficultyLevel.name}")

        val (numberCount, numberRange, decimalPlaces) = when (difficultyLevel.name.lowercase()) {
            "tutorial" -> Triple(Pair(2, 2), Pair(5, 25), 0)
            "beginner" -> Triple(Pair(2, 3), Pair(10, 40), 0)
            "easy" -> Triple(Pair(2, 3), Pair(10, 50), 0)
            "easy+" -> Triple(Pair(3, 3), Pair(15, 60), 0)
            "medium-" -> Triple(Pair(3, 4), Pair(20, 80), 1)
            "medium" -> Triple(Pair(3, 4), Pair(20, 100), 1)
            "medium+" -> Triple(Pair(4, 4), Pair(30, 120), 1)
            "hard-" -> Triple(Pair(4, 5), Pair(40, 150), 1)
            "hard" -> Triple(Pair(4, 5), Pair(50, 200), 1)
            "hard+" -> Triple(Pair(5, 5), Pair(60, 250), 2)
            "expert-" -> Triple(Pair(5, 6), Pair(80, 300), 2)
            "expert" -> Triple(Pair(5, 6), Pair(100, 400), 2)
            "expert+" -> Triple(Pair(6, 6), Pair(120, 500), 2)
            "master" -> Triple(Pair(6, 7), Pair(150, 600), 2)
            "grandmaster" -> Triple(Pair(7, 8), Pair(200, 800), 2)
            else -> Triple(Pair(3, 4), Pair(20, 100), 1)
        }

        for (attempt in 1..50) {
            try {
                val count = Random.nextInt(numberCount.first, numberCount.second + 1)
                val numbers = mutableListOf<Double>()

                for (i in 0 until count) {
                    val number = if (decimalPlaces > 0) {
                        val whole = Random.nextInt(numberRange.first, numberRange.second + 1)
                        val decimal = Random.nextDouble()
                        val places = minOf(decimalPlaces, 2)
                        String.format("%.${places}f", whole + decimal).toDouble()
                    } else {
                        Random.nextInt(numberRange.first, numberRange.second + 1).toDouble()
                    }
                    numbers.add(number)
                }

                val sum = numbers.sum().roundToDecimalPlaces(2)
                val combinationKey = "${difficultyLevel.name}_${numbers.sorted().joinToString("_")}"

                if (!usedCombinations["estimation"]!!.contains(combinationKey)) {
                    usedCombinations["estimation"]!!.add(combinationKey)

                    Log.d(TAG, "✅ Generated Estimation puzzle for ${difficultyLevel.name} (attempt $attempt)")
                    return MathEstimationPuzzle(
                        numbers = numbers,
                        sum = sum,
                        difficulty = difficultyLevel.name,
                        hint = generateEstimationHint(difficultyLevel.name, decimalPlaces > 0)
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating Estimation for ${difficultyLevel.name} on attempt $attempt: ${e.message}")
                continue
            }
        }

        Log.e(TAG, "❌ Failed to generate Estimation puzzle for ${difficultyLevel.name} after 50 attempts")
        return null
    }

    /**
     * Generate Percentage Puzzle based on adaptive difficulty level
     */
    fun generatePercentageForLevel(difficultyLevel: DifficultyManager.DifficultyLevel): PercentagePuzzle? {
        Log.d(TAG, "🎯 Generating Percentage puzzle for level: ${difficultyLevel.name}")

        val (baseRange, percentages) = when (difficultyLevel.name.lowercase()) {
            "tutorial" -> Pair(Pair(10, 50), listOf(10, 20, 50))
            "beginner" -> Pair(Pair(20, 80), listOf(10, 20, 25, 50))
            "easy" -> Pair(Pair(20, 100), listOf(10, 20, 25, 50))
            "easy+" -> Pair(Pair(40, 150), listOf(10, 15, 20, 25))
            "medium-" -> Pair(Pair(60, 200), listOf(10, 15, 20, 25, 30))
            "medium" -> Pair(Pair(100, 500), listOf(10, 15, 20, 25, 30))
            "medium+" -> Pair(Pair(150, 600), listOf(15, 20, 25, 30, 35))
            "hard-" -> Pair(Pair(200, 800), listOf(15, 18, 22, 25, 30))
            "hard" -> Pair(Pair(200, 1000), listOf(15, 18, 22, 25, 35))
            "hard+" -> Pair(Pair(300, 1200), listOf(18, 22, 25, 30, 35, 40))
            "expert-" -> Pair(Pair(400, 1500), listOf(12, 18, 22, 28, 35))
            "expert" -> Pair(Pair(500, 2000), listOf(12, 18, 22, 28, 35, 45))
            "expert+" -> Pair(Pair(600, 2500), listOf(15, 22, 28, 35, 42, 48))
            "master" -> Pair(Pair(800, 3000), listOf(16, 24, 32, 36, 44, 52))
            "grandmaster" -> Pair(Pair(1000, 5000), listOf(17, 23, 29, 37, 41, 47, 53))
            else -> Pair(Pair(100, 500), listOf(10, 15, 20, 25, 30))
        }

        for (attempt in 1..50) {
            try {
                val percentage = percentages.random()

                // Generate base number that ensures whole number result
                val gcd = gcd(percentage, 100)
                val requiredDivisor = 100 / gcd

                val minMultiple = kotlin.math.ceil(baseRange.first.toDouble() / requiredDivisor).toInt()
                val maxMultiple = kotlin.math.floor(baseRange.second.toDouble() / requiredDivisor).toInt()

                if (minMultiple > maxMultiple) {
                    continue
                }

                val multiplier = Random.nextInt(minMultiple, maxMultiple + 1)
                val baseNumber = multiplier * requiredDivisor

                val answer = (baseNumber * percentage / 100.0)
                val wholeAnswer = answer.toInt()

                if (kotlin.math.abs(answer - wholeAnswer) > 0.001) {
                    continue
                }

                if (baseNumber !in baseRange.first..baseRange.second) {
                    continue
                }

                val combinationKey = "${difficultyLevel.name}_${baseNumber}_$percentage"

                if (!usedCombinations["percentages"]!!.contains(combinationKey)) {
                    usedCombinations["percentages"]!!.add(combinationKey)

                    Log.d(TAG, "✅ Generated Percentage puzzle for ${difficultyLevel.name} (attempt $attempt)")
                    Log.d(TAG, "  $percentage% of $baseNumber = $wholeAnswer")

                    return PercentagePuzzle(
                        total = baseNumber,
                        percentage = percentage,
                        answer = wholeAnswer.toDouble(),
                        difficulty = difficultyLevel.name,
                        hint = generatePercentageHint(percentage, difficultyLevel.name)
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating Percentage for ${difficultyLevel.name} on attempt $attempt: ${e.message}")
                continue
            }
        }

        Log.e(TAG, "❌ Failed to generate Percentage puzzle for ${difficultyLevel.name} after 50 attempts")
        return null
    }

    /**
     * Generate hints based on difficulty level and puzzle type
     */
    private fun generateAverageHint(numberCount: Int, difficulty: String): String {
        return when {
            difficulty.contains("tutorial", ignoreCase = true) ->
                "Add the numbers: ${numberCount} numbers ÷ ${numberCount} = average"
            difficulty.contains("beginner", ignoreCase = true) ->
                "Step 1: Add all numbers together. Step 2: Divide by $numberCount"
            difficulty.contains("easy", ignoreCase = true) ->
                "Add all $numberCount numbers together and divide by how many numbers there are"
            difficulty.contains("medium", ignoreCase = true) ->
                "Find the sum of all numbers, then divide by the count"
            else ->
                "Calculate the mean of the given numbers"
        }
    }

    private fun generateEstimationHint(difficulty: String, hasDecimals: Boolean): String {
        return when {
            difficulty.contains("tutorial", ignoreCase = true) ->
                "Round each number to the nearest 5, then add them up"
            difficulty.contains("beginner", ignoreCase = true) ->
                "Round each number to the nearest 10 to make addition easier"
            hasDecimals ->
                "Round decimal numbers to make estimation simpler"
            else ->
                "Round each number to make estimation easier"
        }
    }

    private fun generatePercentageHint(percentage: Int, difficulty: String): String {
        return when {
            difficulty.contains("tutorial", ignoreCase = true) ->
                "To find $percentage%, multiply by $percentage and divide by 100"
            percentage == 10 -> "10% is the same as dividing by 10"
            percentage == 20 -> "20% is the same as dividing by 5"
            percentage == 25 -> "25% is the same as dividing by 4"
            percentage == 50 -> "50% is the same as dividing by 2"
            else -> "Multiply by $percentage, then divide by 100"
        }
    }

    // Helper functions
    private fun gcd(a: Int, b: Int): Int {
        return if (b == 0) a else gcd(b, a % b)
    }

    private fun Double.roundToDecimalPlaces(places: Int): Double {
        val factor = 10.0.pow(places)
        return (this * factor).roundToInt() / factor
    }

    /**
     * Clear session cache
     */
    fun clearCache() {
        usedCombinations.values.forEach { it.clear() }
        Log.d(TAG, "🧹 Cleared all adaptive math puzzle generation caches")
    }

    /**
     * Get statistics
     */
    fun getStats(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        usedCombinations.forEach { (type, combinations) ->
            stats[type] = combinations.size
        }
        return stats
    }
}
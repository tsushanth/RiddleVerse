package com.kreativekoala.riddleverse

import android.util.Log
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

/**
 * 🎯 Client-Side Math Puzzle Generators for Android
 * Eliminates server dependency for all math puzzle types
 */

// Data classes for puzzle results
data class MathEstimationPuzzle(
    val numbers: List<Double>,
    val sum: Double,
    val difficulty: String,
    val hint: String
)

data class MathTippingPuzzle(
    val billAmount: Double,
    val tipPercentage: Double,
    val tipAmount: Double,
    val isCorrect: Boolean,
    val difficulty: String,
    val hint: String
)

data class PercentagePuzzle(
    val total: Int,
    val percentage: Int,
    val answer: Double,
    val difficulty: String,
    val hint: String
)

data class AveragePuzzle(
    val numbers: List<Int>,
    val average: Double,
    val difficulty: String,
    val hint: String
)

data class PurchasingPuzzle(
    val payment: Double,
    val frequency: String,
    val yearlyTotal: Double,
    val difficulty: String,
    val hint: String
)

data class DiscountItem(
    val id: Int,
    val name: String,
    val icon: String,
    val originalPrice: Double,
    val discountPercentage: Int?,
    val finalPrice: Double
)

data class DiscountsPuzzle(
    val items: List<DiscountItem>,
    val correctOrder: List<Int>,
    val difficulty: String,
    val hint: String
)

data class ConversionPuzzle(
    val value1: Double,
    val unit1: String,
    val value2: Double,
    val unit2: String,
    val comparison: String, // "equal" or "not equal"
    val difficulty: String,
    val hint: String,
    val metadata: ConversionMetadata
)

class MathPuzzleGenerators {

    companion object {
        private const val TAG = "MathPuzzleGenerators"

        // Difficulty levels
        enum class DifficultyLevel(val value: String) {
            EASY("Easy"),
            MEDIUM("Medium"),
            HARD("Hard"),
            EXPERT("Expert")
        }
    }

    // Track used combinations to prevent duplicates within session
    private val usedCombinations = mutableMapOf<String, MutableSet<String>>()

    init {
        // Initialize used combinations for each puzzle type
        usedCombinations["estimation"] = mutableSetOf()
        usedCombinations["tipping"] = mutableSetOf()
        usedCombinations["percentages"] = mutableSetOf()
        usedCombinations["division"] = mutableSetOf()
        usedCombinations["average"] = mutableSetOf()
        usedCombinations["subtraction"] = mutableSetOf()
        usedCombinations["purchasing"] = mutableSetOf()
        usedCombinations["discounts"] = mutableSetOf()
        usedCombinations["conversion"] = mutableSetOf()
    }

    /**
     * Generate Math Estimation Puzzle
     */
    fun generateMathEstimation(difficulty: String = "Medium"): MathEstimationPuzzle? {
        Log.d(TAG, "🎯 Generating Math Estimation puzzle - difficulty: $difficulty")

        val (numberCount, numberRange, decimalPlaces) = when (difficulty.lowercase()) {
            "easy" -> Triple(Pair(2, 3), Pair(10, 50), 0)
            "medium" -> Triple(Pair(3, 4), Pair(20, 100), 1)
            "hard" -> Triple(Pair(4, 5), Pair(50, 200), 1)
            "expert" -> Triple(Pair(5, 6), Pair(100, 500), 2)
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
                val combinationKey = numbers.sorted().joinToString("_")

                if (!usedCombinations["estimation"]!!.contains(combinationKey)) {
                    usedCombinations["estimation"]!!.add(combinationKey)

                    Log.d(TAG, "✅ Generated Math Estimation puzzle (attempt $attempt)")
                    return MathEstimationPuzzle(
                        numbers = numbers,
                        sum = sum,
                        difficulty = difficulty,
                        hint = "Round each number to make estimation easier"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating Math Estimation on attempt $attempt: ${e.message}")
                continue
            }
        }

        Log.e(TAG, "❌ Failed to generate Math Estimation puzzle after 50 attempts")
        return null
    }

    /**
     * Generate Math Tipping Puzzle
     */
    fun generateMathTipping(difficulty: String = "Medium"): MathTippingPuzzle? {
        Log.d(TAG, "🎯 Generating Math Tipping puzzle - difficulty: $difficulty")

        val (billRange, tipPercentages) = when (difficulty.lowercase()) {
            "easy" -> Pair(Pair(10.0, 50.0), listOf(10, 15, 20))
            "medium" -> Pair(Pair(25.0, 100.0), listOf(15, 18, 20, 22))
            "hard" -> Pair(Pair(50.0, 200.0), listOf(18, 20, 22, 25))
            "expert" -> Pair(Pair(100.0, 500.0), listOf(15, 18, 20, 22, 25, 30))
            else -> Pair(Pair(25.0, 100.0), listOf(15, 18, 20, 22))
        }

        for (attempt in 1..50) {
            try {
                val bill = randomDouble(billRange.first, billRange.second, 2)
                val tipPercent = tipPercentages.random()
                val correctTip = (bill * (tipPercent / 100.0)).roundToDecimalPlaces(2)

                val combinationKey = "${(bill * 2).roundToInt() / 2.0}_$tipPercent"

                if (!usedCombinations["tipping"]!!.contains(combinationKey)) {
                    usedCombinations["tipping"]!!.add(combinationKey)

                    Log.d(TAG, "✅ Generated Math Tipping puzzle (attempt $attempt)")
                    return MathTippingPuzzle(
                        billAmount = bill,
                        tipPercentage = tipPercent.toDouble(),
                        tipAmount = correctTip,
                        isCorrect = true,
                        difficulty = difficulty,
                        hint = "Calculate $tipPercent% tip on ${String.format("%.2f", bill)}"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating Math Tipping on attempt $attempt: ${e.message}")
                continue
            }
        }

        Log.e(TAG, "❌ Failed to generate Math Tipping puzzle after 50 attempts")
        return null
    }

    /**
     * Generate Percentages Puzzle
     */
    fun generatePercentages(difficulty: String = "Medium"): PercentagePuzzle? {
        Log.d(TAG, "🎯 Generating Percentages puzzle - difficulty: $difficulty")

        val (baseRange, percentages) = when (difficulty.lowercase()) {
            "easy" -> Pair(Pair(20, 100), listOf(10, 20, 25, 50))
            "medium" -> Pair(Pair(100, 500), listOf(10, 15, 20, 25, 30))
            "hard" -> Pair(Pair(200, 1000), listOf(15, 18, 22, 25, 35))
            "expert" -> Pair(Pair(500, 2000), listOf(12, 18, 22, 28, 35, 45))
            else -> Pair(Pair(100, 500), listOf(10, 15, 20, 25, 30))
        }

        for (attempt in 1..50) {
            try {
                val percentage = percentages.random()

                // STRATEGY 1: Generate base number that ensures whole number result
                // We want: (baseNumber * percentage / 100) to be a whole number
                // So: baseNumber must be divisible by (100 / gcd(percentage, 100))

                val gcd = gcd(percentage, 100)
                val requiredDivisor = 100 / gcd

                Log.d(TAG, "Percentage: $percentage%, GCD: $gcd, Required divisor: $requiredDivisor")

                // Generate a base number that's divisible by the required divisor
                val minMultiple = kotlin.math.ceil(baseRange.first.toDouble() / requiredDivisor).toInt()
                val maxMultiple = kotlin.math.floor(baseRange.second.toDouble() / requiredDivisor).toInt()

                if (minMultiple > maxMultiple) {
                    Log.w(TAG, "No valid multiples for $percentage% in range ${baseRange.first}-${baseRange.second}")
                    continue
                }

                val multiplier = Random.nextInt(minMultiple, maxMultiple + 1)
                val baseNumber = multiplier * requiredDivisor

                // Calculate the answer - this WILL be a whole number
                val answer = (baseNumber * percentage / 100.0)
                val wholeAnswer = answer.toInt()

                // Verify it's actually a whole number
                if (kotlin.math.abs(answer - wholeAnswer) > 0.001) {
                    Log.w(TAG, "Attempt $attempt: Generated non-whole answer: $answer for $percentage% of $baseNumber")
                    continue
                }

                // Verify the base number is in our desired range
                if (baseNumber !in baseRange.first..baseRange.second) {
                    Log.w(TAG, "Attempt $attempt: Base number $baseNumber outside range ${baseRange.first}-${baseRange.second}")
                    continue
                }

                val combinationKey = "${baseNumber}_$percentage"

                if (!usedCombinations["percentages"]!!.contains(combinationKey)) {
                    usedCombinations["percentages"]!!.add(combinationKey)

                    Log.d(TAG, "✅ Generated Percentages puzzle (attempt $attempt)")
                    Log.d(TAG, "  $percentage% of $baseNumber = $wholeAnswer (verified whole number)")

                    return PercentagePuzzle(
                        total = baseNumber,
                        percentage = percentage,
                        answer = wholeAnswer.toDouble(), // Store as Double for compatibility but guaranteed to be whole
                        difficulty = difficulty,
                        hint = "What is $percentage% of $baseNumber?"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating Percentages on attempt $attempt: ${e.message}")
                continue
            }
        }

        Log.e(TAG, "❌ Failed to generate Percentages puzzle after 50 attempts")
        return null
    }

    /**
     * Calculate Greatest Common Divisor using Euclidean algorithm
     */
    private fun gcd(a: Int, b: Int): Int {
        return if (b == 0) a else gcd(b, a % b)
    }



    /**
     * Generate Average Puzzle
     */
    fun generateAverage(difficulty: String = "Medium"): AveragePuzzle? {
        Log.d(TAG, "🎯 Generating Average puzzle - difficulty: $difficulty")

        val (numberCount, numberRange) = when (difficulty.lowercase()) {
            "easy" -> Pair(Pair(2, 3), Pair(10, 50))
            "medium" -> Pair(Pair(3, 4), Pair(20, 100))
            "hard" -> Pair(Pair(4, 5), Pair(50, 200))
            "expert" -> Pair(Pair(5, 6), Pair(100, 500))
            else -> Pair(Pair(3, 4), Pair(20, 100))
        }

        for (attempt in 1..50) {
            try {
                val count = Random.nextInt(numberCount.first, numberCount.second + 1)

                // STRATEGY: Generate target average first (whole number), then generate numbers around it
                val targetAverage = Random.nextInt(
                    (numberRange.first * 1.2).toInt(),
                    (numberRange.second * 0.8).toInt() + 1
                )

                Log.d(TAG, "Attempt $attempt: Targeting average $targetAverage with $count numbers")

                val numbers = mutableListOf<Int>()
                var sum = 0

                // Generate first n-1 numbers around the target average
                for (i in 0 until count - 1) {
                    // Generate numbers within reasonable range of target average
                    val deviation = Random.nextInt(-targetAverage / 3, targetAverage / 3 + 1)
                    val num = (targetAverage + deviation).coerceIn(numberRange.first, numberRange.second)
                    numbers.add(num)
                    sum += num
                }

                // Calculate last number to achieve exact target average
                val lastNumber = (targetAverage * count) - sum

                // Verify last number is in valid range
                if (lastNumber in numberRange.first..numberRange.second) {
                    numbers.add(lastNumber)

                    // Verify the average is exactly a whole number
                    val finalSum = numbers.sum()
                    val finalAverage = finalSum.toDouble() / count

                    if (kotlin.math.abs(finalAverage - finalAverage.toInt()) < 0.001) {
                        val combinationKey = numbers.sorted().joinToString("_")

                        if (!usedCombinations["average"]!!.contains(combinationKey)) {
                            usedCombinations["average"]!!.add(combinationKey)

                            Log.d(TAG, "✅ Generated Average puzzle (attempt $attempt)")
                            Log.d(TAG, "  Numbers: $numbers")
                            Log.d(TAG, "  Sum: $finalSum")
                            Log.d(TAG, "  Average: ${finalAverage.toInt()} (verified whole number)")

                            return AveragePuzzle(
                                numbers = numbers,
                                average = finalAverage.toInt().toDouble(), // Ensure it's stored as whole number
                                difficulty = difficulty,
                                hint = "Add all numbers together and divide by how many numbers there are"
                            )
                        }
                    } else {
                        Log.d(TAG, "Attempt $attempt: Non-whole average $finalAverage, retrying...")
                    }
                } else {
                    Log.d(TAG, "Attempt $attempt: Last number $lastNumber out of range, retrying...")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating Average on attempt $attempt: ${e.message}")
                continue
            }
        }

        Log.e(TAG, "❌ Failed to generate Average puzzle after 50 attempts")
        return null
    }



    /**
     * Generate Purchasing Puzzle
     */
    fun generatePurchasing(difficulty: String = "Medium"): PurchasingPuzzle? {
        Log.d(TAG, "🎯 Generating Purchasing puzzle - difficulty: $difficulty")

        val frequencies = listOf("weekly", "biweekly", "monthly", "quarterly", "yearly")
        val frequencyMultipliers = mapOf(
            "weekly" to 52,
            "biweekly" to 26,
            "monthly" to 12,
            "quarterly" to 4,
            "yearly" to 1
        )

        for (attempt in 1..50) {
            try {
                val frequency = frequencies.random()

                val payment = when (difficulty.lowercase()) {
                    "easy" -> randomDouble(10.0, 100.0, 2)
                    "medium" -> randomDouble(50.0, 300.0, 2)
                    "hard" -> randomDouble(200.0, 800.0, 2)
                    "expert" -> randomDouble(500.0, 2000.0, 2)
                    else -> randomDouble(50.0, 300.0, 2)
                }

                val yearlyTotal = (payment * frequencyMultipliers[frequency]!!).roundToDecimalPlaces(2)
                val combinationKey = "${payment}_$frequency"

                if (!usedCombinations["purchasing"]!!.contains(combinationKey)) {
                    usedCombinations["purchasing"]!!.add(combinationKey)

                    Log.d(TAG, "✅ Generated Purchasing puzzle (attempt $attempt)")
                    return PurchasingPuzzle(
                        payment = payment,
                        frequency = frequency,
                        yearlyTotal = yearlyTotal,
                        difficulty = difficulty,
                        hint = "Multiply the $frequency payment by the number of periods in a year"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating Purchasing on attempt $attempt: ${e.message}")
                continue
            }
        }

        Log.e(TAG, "❌ Failed to generate Purchasing puzzle after 50 attempts")
        return null
    }

    /**
     * Generate Discounts Puzzle
     */
    fun generateDiscounts(difficulty: String = "Medium"): DiscountsPuzzle? {
        Log.d(TAG, "🎯 Generating Discounts puzzle - difficulty: $difficulty")

        val itemNames = listOf(
            "Laptop", "Phone", "Tablet", "Watch", "Headphones",
            "Camera", "Speaker", "Monitor", "Keyboard", "Mouse",
            "Jacket", "Shoes", "Backpack", "Sunglasses", "Book"
        )

        val itemIcons = listOf(
            "💻", "📱", "📊", "⌚", "🎧",
            "📷", "🔊", "🖥️", "⌨️", "🖱️",
            "🧥", "👟", "🎒", "🕶️", "📚"
        )

        for (attempt in 1..50) {
            try {
                val itemCount = Random.nextInt(3, 6) // 3-5 items
                val items = mutableListOf<DiscountItem>()
                val usedNames = mutableSetOf<String>()

                for (i in 0 until itemCount) {
                    val availableNames = itemNames.filter { !usedNames.contains(it) }
                    if (availableNames.isEmpty()) break

                    val name = availableNames.random()
                    usedNames.add(name)
                    val icon = itemIcons[itemNames.indexOf(name)]

                    val (originalPrice, discountPercentage) = when (difficulty.lowercase()) {
                        "easy" -> {
                            val price = randomDouble(20.0, 200.0, 2)
                            val discount = if (Random.nextFloat() < 0.7f) listOf(10, 15, 20, 25).random() else null
                            Pair(price, discount)
                        }
                        "medium" -> {
                            val price = randomDouble(100.0, 500.0, 2)
                            val discount = if (Random.nextFloat() < 0.8f) listOf(15, 20, 25, 30, 35).random() else null
                            Pair(price, discount)
                        }
                        "hard" -> {
                            val price = randomDouble(300.0, 1000.0, 2)
                            val discount = if (Random.nextFloat() < 0.9f) listOf(20, 25, 30, 35, 40, 45).random() else null
                            Pair(price, discount)
                        }
                        "expert" -> {
                            val price = randomDouble(500.0, 2000.0, 2)
                            val discount = listOf(25, 30, 35, 40, 45, 50).random()
                            Pair(price, discount)
                        }
                        else -> {
                            val price = randomDouble(100.0, 500.0, 2)
                            val discount = if (Random.nextFloat() < 0.8f) listOf(15, 20, 25, 30, 35).random() else null
                            Pair(price, discount)
                        }
                    }

                    val finalPrice = if (discountPercentage != null) {
                        (originalPrice * (1.0 - discountPercentage / 100.0)).roundToDecimalPlaces(2)
                    } else {
                        originalPrice
                    }

                    items.add(
                        DiscountItem(
                            id = i + 1,
                            name = name,
                            icon = icon,
                            originalPrice = originalPrice,
                            discountPercentage = discountPercentage,
                            finalPrice = finalPrice
                        )
                    )
                }

                if (items.size >= 3) {
                    val sortedItems = items.sortedBy { it.finalPrice }
                    val correctOrder = sortedItems.map { it.id }

                    val combinationKey = items.map { "${it.originalPrice}_${it.discountPercentage ?: 0}" }.sorted().joinToString("|")

                    if (!usedCombinations["discounts"]!!.contains(combinationKey)) {
                        usedCombinations["discounts"]!!.add(combinationKey)

                        Log.d(TAG, "✅ Generated Discounts puzzle (attempt $attempt)")
                        return DiscountsPuzzle(
                            items = items,
                            correctOrder = correctOrder,
                            difficulty = difficulty,
                            hint = "Calculate the final price after applying discounts, then order from least to most expensive"
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating Discounts on attempt $attempt: ${e.message}")
                continue
            }
        }

        Log.e(TAG, "❌ Failed to generate Discounts puzzle after 50 attempts")
        return null
    }


    /**
     * Clear session cache
     */
    fun clearCache() {
        usedCombinations.values.forEach { it.clear() }
        Log.d(TAG, "🧹 Cleared all math puzzle generation caches")
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

    // Helper classes and functions
    private data class ConversionRule(
        val from: String,
        val to: String,
        val factor: Double,
        val precision: Int
    )

    private fun randomDouble(min: Double, max: Double, decimals: Int): Double {
        val value = Random.nextDouble(min, max)
        return value.roundToDecimalPlaces(decimals)
    }

    private fun Double.roundToDecimalPlaces(places: Int): Double {
        val factor = 10.0.pow(places)
        return (this * factor).roundToInt() / factor
    }

    // Add this method to your existing MathPuzzleGenerators class

    /**
     * Generate a mathematical comparison puzzle (client-side)
     */
    fun generateMathComparison(difficulty: String): String? {
        return try {
            val generator = MathComparisonGenerator()
            generator.setDebugMode(true) // Enable debug logging

            val result = generator.generateMathComparisonPuzzle(difficulty)

            if (result.success && result.puzzleData != null) {
                // Return the question JSON string that can be used directly by MathComparisonPuzzleScreen
                result.puzzleData.question
            } else {
                Log.e(TAG, "❌ Math comparison generation failed: ${result.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in generateMathComparison: ${e.message}")
            null
        }
    }

    /**
     * Validate generated math comparison puzzle data
     */
    fun validateMathComparisonPuzzle(puzzleJson: String): Boolean {
        return try {
            val questionData = JSONObject(puzzleJson)

            // Check required fields
            val hasSequence = questionData.has("sequence")
            val hasTotalPairs = questionData.has("totalPairs")
            val hasTimeLimit = questionData.has("timeLimit")
            val hasInstructions = questionData.has("instructions")

            if (!hasSequence || !hasTotalPairs || !hasTimeLimit || !hasInstructions) {
                Log.w(TAG, "⚠️ Missing required fields in math comparison puzzle")
                return false
            }

            // Validate sequence array
            val sequence = questionData.getJSONArray("sequence")
            if (sequence.length() == 0) {
                Log.w(TAG, "⚠️ Empty sequence in math comparison puzzle")
                return false
            }

            // Validate first pair structure
            val firstPair = sequence.getJSONObject(0)
            val requiredPairFields = listOf(
                "pairNumber", "leftValue", "rightValue",
                "leftNumeric", "rightNumeric", "correctAnswer",
                "operationType", "difficulty"
            )

            val hasAllPairFields = requiredPairFields.all { field ->
                firstPair.has(field)
            }

            if (!hasAllPairFields) {
                Log.w(TAG, "⚠️ Missing required fields in comparison pair")
                return false
            }

            Log.d(TAG, "✅ Math comparison puzzle validation passed")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error validating math comparison puzzle: ${e.message}")
            false
        }
    }
}
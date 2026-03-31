package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlin.math.*
import kotlin.random.Random

/**
 * Client-side Conversion Puzzle Generator
 * Generates unit conversion comparison puzzles with progressive difficulty
 */
open class ConversionPuzzleGenerator {
    private val debugMode = true
    private val converter = UniversalConverter()

    companion object {
        private const val TAG = "ConversionGenerator"

        // Conversion tolerance for equality comparisons (0.1% or 0.001, whichever is larger)
        private const val EQUALITY_TOLERANCE_PERCENT = 0.001
        private const val MIN_EQUALITY_TOLERANCE = 0.001
    }

    private fun debugLog(message: String, type: String = "info") {
        if (debugMode) {
            val emoji = when (type) {
                "error" -> "❌"
                "success" -> "✅"
                "warning" -> "⚠️"
                else -> "🔄"
            }
            Log.d(TAG, "$emoji CONVERSION_GEN: $message")
        }
    }

    /**
     * Generate a complete conversion puzzle
     */
    fun generateConversionPuzzle(difficulty: String = "medium"): ConversionPuzzleData? {
        debugLog("🎯 Generating $difficulty conversion puzzle...")

        try {
            val difficultyConfig = getDifficultyConfig(difficulty)
            val conversionType = selectConversionType(difficultyConfig)
            val unitPair = selectUnitPair(conversionType, difficultyConfig)

            // Generate values with controlled comparison result
            val comparisonData = generateComparisonValues(
                unitPair.first,
                unitPair.second,
                conversionType,
                difficultyConfig
            )

            val leftBlock = ConversionBlock(
                id = 1,
                label = formatValueLabel(comparisonData.value1, unitPair.first),
                value = comparisonData.value1,
                unit = unitPair.first,
                color = Color(0xFFE91E63)
            )

            val rightBlock = ConversionBlock(
                id = 2,
                label = formatValueLabel(comparisonData.value2, unitPair.second),
                value = comparisonData.value2,
                unit = unitPair.second,
                color = Color(0xFF2196F3)
            )

            val puzzleData = ConversionPuzzleData(
                leftBlock = leftBlock,
                rightBlock = rightBlock,
                correctAnswer = comparisonData.correctAnswer,
                conversionType = conversionType,
                difficulty = difficulty,
                hint = generateHint(conversionType, leftBlock, rightBlock),
                metadata = ConversionMetadata(
                    generatedAt = System.currentTimeMillis(),
                    conversionType = conversionType.name,
                    difficulty = difficulty,
                    unit1 = unitPair.first,
                    unit2 = unitPair.second,
                    comparisonRatio = comparisonData.actualRatio
                )
            )

            debugLog("✅ Generated ${conversionType.displayName} puzzle: ${leftBlock.label} vs ${rightBlock.label}", "success")
            return puzzleData

        } catch (e: Exception) {
            debugLog("❌ Generation failed: ${e.message}", "error")
            return null
        }
    }

    /**
     * Get difficulty configuration
     */
    private fun getDifficultyConfig(difficulty: String): ConversionDifficultyConfig {
        return when (difficulty.lowercase()) {
            "easy" -> ConversionDifficultyConfig(
                valueRange = Pair(1.0, 50.0),
                preferredTypes = listOf(ConversionType.DISTANCE, ConversionType.WEIGHT, ConversionType.TIME),
                allowComplexUnits = false,
                targetDifferenceRange = Pair(1.5, 5.0), // 50% to 400% difference
                equalityChance = 0.15, // 15% chance for equal values
                decimalPlaces = 1
            )
            "medium" -> ConversionDifficultyConfig(
                valueRange = Pair(0.5, 100.0),
                preferredTypes = listOf(ConversionType.TEMPERATURE, ConversionType.VOLUME, ConversionType.AREA),
                allowComplexUnits = true,
                targetDifferenceRange = Pair(1.2, 3.0), // 20% to 200% difference
                equalityChance = 0.12, // 12% chance for equal values
                decimalPlaces = 2
            )
            "hard" -> ConversionDifficultyConfig(
                valueRange = Pair(0.1, 500.0),
                preferredTypes = ConversionType.values().toList(),
                allowComplexUnits = true,
                targetDifferenceRange = Pair(1.1, 2.5), // 10% to 150% difference
                equalityChance = 0.10, // 10% chance for equal values
                decimalPlaces = 3
            )
            else -> getDifficultyConfig("medium") // Default to medium
        }
    }

    /**
     * Select conversion type based on difficulty preferences
     */
    private fun selectConversionType(config: ConversionDifficultyConfig): ConversionType {
        val availableTypes = if (config.preferredTypes.isNotEmpty()) {
            config.preferredTypes
        } else {
            ConversionType.values().filter { it != ConversionType.UNKNOWN }.toList()
        }

        return availableTypes[Random.nextInt(availableTypes.size)]
    }

    /**
     * Select appropriate unit pair for the conversion type
     */
    private fun selectUnitPair(conversionType: ConversionType, config: ConversionDifficultyConfig): Pair<String, String> {
        val unitSets = getUnitSets(conversionType, config.allowComplexUnits)
        val availableUnits = unitSets.keys.toList()

        // Ensure we pick two different units
        val unit1 = availableUnits[Random.nextInt(availableUnits.size)]
        val remainingUnits = availableUnits.filter { it != unit1 }
        val unit2 = remainingUnits[Random.nextInt(remainingUnits.size)]

        return Pair(unit1, unit2)
    }

    /**
     * Get unit sets for each conversion type
     */
    private fun getUnitSets(conversionType: ConversionType, allowComplex: Boolean): Map<String, Double> {
        return when (conversionType) {
            ConversionType.TEMPERATURE -> if (allowComplex) {
                mapOf("celsius" to 1.0, "fahrenheit" to 1.0, "kelvin" to 1.0)
            } else {
                mapOf("celsius" to 1.0, "fahrenheit" to 1.0)
            }

            ConversionType.WEIGHT -> if (allowComplex) {
                mapOf("grams" to 1.0, "kilograms" to 1000.0, "pounds" to 453.592, "ounces" to 28.3495)
            } else {
                mapOf("grams" to 1.0, "kilograms" to 1000.0, "pounds" to 453.592)
            }

            ConversionType.DISTANCE -> if (allowComplex) {
                mapOf("meters" to 1.0, "kilometers" to 1000.0, "miles" to 1609.34, "feet" to 0.3048, "inches" to 0.0254, "yards" to 0.9144, "centimeters" to 0.01)
            } else {
                mapOf("meters" to 1.0, "kilometers" to 1000.0, "miles" to 1609.34, "feet" to 0.3048)
            }

            ConversionType.VOLUME -> if (allowComplex) {
                mapOf("liters" to 1.0, "milliliters" to 0.001, "gallons" to 3.78541, "quarts" to 0.946353, "pints" to 0.473176, "cups" to 0.236588)
            } else {
                mapOf("liters" to 1.0, "milliliters" to 0.001, "gallons" to 3.78541)
            }

            ConversionType.TIME -> if (allowComplex) {
                mapOf("seconds" to 1.0, "minutes" to 60.0, "hours" to 3600.0, "days" to 86400.0, "weeks" to 604800.0)
            } else {
                mapOf("seconds" to 1.0, "minutes" to 60.0, "hours" to 3600.0)
            }

            ConversionType.AREA -> if (allowComplex) {
                mapOf("square meters" to 1.0, "square kilometers" to 1_000_000.0, "square feet" to 0.092903, "square miles" to 2_590_000.0, "acres" to 4047.0)
            } else {
                mapOf("square meters" to 1.0, "square feet" to 0.092903, "acres" to 4047.0)
            }

            ConversionType.SPEED -> if (allowComplex) {
                mapOf("meters per second" to 1.0, "kilometers per hour" to 0.277778, "miles per hour" to 0.44704, "feet per second" to 0.3048)
            } else {
                mapOf("kilometers per hour" to 0.277778, "miles per hour" to 0.44704)
            }

            ConversionType.UNKNOWN -> mapOf("unit1" to 1.0, "unit2" to 2.0)
        }
    }

    /**
     * Generate comparison values with controlled relationship
     */
    private fun generateComparisonValues(
        unit1: String,
        unit2: String,
        conversionType: ConversionType,
        config: ConversionDifficultyConfig
    ): ComparisonData {

        // Decide if values should be equal
        val shouldBeEqual = Random.nextDouble() < config.equalityChance

        if (shouldBeEqual) {
            return generateEqualValues(unit1, unit2, conversionType, config)
        } else {
            return generateUnequalValues(unit1, unit2, conversionType, config)
        }
    }

    /**
     * Generate equal values (after conversion)
     */
    private fun generateEqualValues(
        unit1: String,
        unit2: String,
        conversionType: ConversionType,
        config: ConversionDifficultyConfig
    ): ComparisonData {

        // Start with a base value in unit1
        val baseValue = generateRandomValue(config.valueRange, config.decimalPlaces)

        // Convert to unit2 to get the equivalent value
        val comparisonResult = converter.compareValues(baseValue, unit1, 1.0, unit2)

        // If comparison result is 0, units have same scale
        // Otherwise, calculate the equivalent value
        val equivalentValue = if (abs(comparisonResult) < 0.0001) {
            baseValue
        } else {
            // Use the converter to find equivalent value
            calculateEquivalentValue(baseValue, unit1, unit2, conversionType)
        }

        return ComparisonData(
            value1 = baseValue,
            value2 = equivalentValue,
            correctAnswer = ComparisonResult.EQUAL,
            actualRatio = 1.0
        )
    }

    /**
     * Generate unequal values with controlled difference
     */
    private fun generateUnequalValues(
        unit1: String,
        unit2: String,
        conversionType: ConversionType,
        config: ConversionDifficultyConfig
    ): ComparisonData {

        val baseValue = generateRandomValue(config.valueRange, config.decimalPlaces)

        // Calculate equivalent value first
        val equivalentValue = calculateEquivalentValue(baseValue, unit1, unit2, conversionType)

        // Determine target ratio within difficulty range
        val targetRatio = Random.nextDouble(config.targetDifferenceRange.first, config.targetDifferenceRange.second)
        val makeLeftLarger = Random.nextBoolean()

        val (finalValue1, finalValue2, correctAnswer) = if (makeLeftLarger) {
            // Make left value larger
            val adjustedValue2 = equivalentValue / targetRatio
            Triple(baseValue, adjustedValue2, ComparisonResult.LEFT_HEAVIER)
        } else {
            // Make right value larger
            val adjustedValue2 = equivalentValue * targetRatio
            Triple(baseValue, adjustedValue2, ComparisonResult.RIGHT_HEAVIER)
        }

        // Round values appropriately
        val roundedValue1 = roundToDecimalPlaces(finalValue1, config.decimalPlaces)
        val roundedValue2 = roundToDecimalPlaces(finalValue2, config.decimalPlaces)

        // Verify the comparison is still correct after rounding
        val verificationResult = converter.compareValues(roundedValue1, unit1, roundedValue2, unit2)
        val verifiedAnswer = when {
            abs(verificationResult) < calculateTolerance(roundedValue1, roundedValue2) -> ComparisonResult.EQUAL
            verificationResult > 0 -> ComparisonResult.LEFT_HEAVIER
            else -> ComparisonResult.RIGHT_HEAVIER
        }

        return ComparisonData(
            value1 = roundedValue1,
            value2 = roundedValue2,
            correctAnswer = verifiedAnswer,
            actualRatio = if (verifiedAnswer == ComparisonResult.EQUAL) 1.0 else targetRatio
        )
    }

    /**
     * Calculate equivalent value between units using high precision
     */
    private fun calculateEquivalentValue(value: Double, fromUnit: String, toUnit: String, conversionType: ConversionType): Double {
        // For temperature, we need special handling due to offset-based conversions
        if (conversionType == ConversionType.TEMPERATURE) {
            return when {
                fromUnit.lowercase() == "celsius" && toUnit.lowercase() == "fahrenheit" -> {
                    value * 9.0 / 5.0 + 32.0
                }
                fromUnit.lowercase() == "fahrenheit" && toUnit.lowercase() == "celsius" -> {
                    (value - 32.0) * 5.0 / 9.0
                }
                fromUnit.lowercase() == "celsius" && toUnit.lowercase() == "kelvin" -> {
                    value + 273.15
                }
                fromUnit.lowercase() == "kelvin" && toUnit.lowercase() == "celsius" -> {
                    value - 273.15
                }
                fromUnit.lowercase() == "fahrenheit" && toUnit.lowercase() == "kelvin" -> {
                    (value - 32.0) * 5.0 / 9.0 + 273.15
                }
                fromUnit.lowercase() == "kelvin" && toUnit.lowercase() == "fahrenheit" -> {
                    (value - 273.15) * 9.0 / 5.0 + 32.0
                }
                else -> value // Same unit
            }
        }

        // For other conversions, use ratio-based approach
        // Find the ratio by comparing 1 unit of fromUnit to 1 unit of toUnit
        val comparisonResult = converter.compareValues(1.0, fromUnit, 1.0, toUnit)

        if (abs(comparisonResult) < 0.0001) {
            return value // Same scale
        }

        // Binary search to find equivalent value
        return findEquivalentValueBinarySearch(value, fromUnit, toUnit)
    }

    /**
     * Use binary search to find equivalent value with high precision
     */
    private fun findEquivalentValueBinarySearch(targetValue: Double, fromUnit: String, toUnit: String): Double {
        var low = 0.001
        var high = targetValue * 10000.0 // Start with wide range
        var iterations = 0
        val maxIterations = 100
        val targetTolerance = 1e-10

        while (iterations < maxIterations && (high - low) > targetTolerance) {
            val mid = (low + high) / 2.0
            val comparison = converter.compareValues(targetValue, fromUnit, mid, toUnit)

            when {
                abs(comparison) < targetTolerance -> return mid
                comparison > 0 -> low = mid
                else -> high = mid
            }
            iterations++
        }

        return (low + high) / 2.0
    }

    /**
     * Calculate appropriate tolerance for equality comparison
     */
    private fun calculateTolerance(value1: Double, value2: Double): Double {
        val maxValue = maxOf(abs(value1), abs(value2))
        val percentageTolerance = maxValue * EQUALITY_TOLERANCE_PERCENT
        return maxOf(percentageTolerance, MIN_EQUALITY_TOLERANCE)
    }

    /**
     * Generate random value within range
     */
    private fun generateRandomValue(range: Pair<Double, Double>, decimalPlaces: Int): Double {
        val value = Random.nextDouble(range.first, range.second)
        return roundToDecimalPlaces(value, decimalPlaces)
    }

    /**
     * Round to specified decimal places
     */
    private fun roundToDecimalPlaces(value: Double, decimalPlaces: Int): Double {
        val multiplier = 10.0.pow(decimalPlaces)
        return (value * multiplier).roundToLong() / multiplier
    }

    /**
     * Format value label for display
     */
    private fun formatValueLabel(value: Double, unit: String): String {
        val shortUnit = converter.getShortUnit(unit)
        val formattedValue = if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            "%.3f".format(value).trimEnd('0').trimEnd('.')
        }
        return "$formattedValue $shortUnit"
    }

    /**
     * Generate contextual hint
     */
    private fun generateHint(conversionType: ConversionType, leftBlock: ConversionBlock, rightBlock: ConversionBlock): String {
        return when (conversionType) {
            ConversionType.TEMPERATURE -> "Remember: Water freezes at 0°C/32°F and boils at 100°C/212°F"
            ConversionType.WEIGHT -> "Tip: 1 kg ≈ 2.2 lbs, 1 lb ≈ 454 g"
            ConversionType.DISTANCE -> "Tip: 1 mile ≈ 1.6 km, 1 foot ≈ 30.5 cm"
            ConversionType.VOLUME -> "Tip: 1 gallon ≈ 3.8 L, 1 L = 1000 mL"
            ConversionType.TIME -> "Tip: 1 hour = 60 minutes, 1 day = 24 hours"
            ConversionType.AREA -> "Tip: 1 acre ≈ 4047 m², area scales with the square of distance"
            ConversionType.SPEED -> "Tip: 60 mph ≈ 96 km/h ≈ 27 m/s"
            ConversionType.UNKNOWN -> "Compare the values to determine which is greater"
        }
    }

    /**
     * Generate multiple puzzles for batch generation
     */
    fun generatePuzzleBatch(count: Int, difficulty: String = "medium"): List<ConversionPuzzleData> {
        debugLog("🎯 Generating batch of $count ${difficulty} puzzles...")

        val puzzles = mutableListOf<ConversionPuzzleData>()
        var attempts = 0
        val maxAttempts = count * 3 // Allow some failed generations

        while (puzzles.size < count && attempts < maxAttempts) {
            attempts++
            generateConversionPuzzle(difficulty)?.let { puzzle ->
                puzzles.add(puzzle)
            }
        }

        debugLog("✅ Generated ${puzzles.size}/$count puzzles after $attempts attempts", "success")
        return puzzles
    }
}

/**
 * Data classes for puzzle generation
 */
data class ConversionDifficultyConfig(
    val valueRange: Pair<Double, Double>,
    val preferredTypes: List<ConversionType>,
    val allowComplexUnits: Boolean,
    val targetDifferenceRange: Pair<Double, Double>,
    val equalityChance: Double,
    val decimalPlaces: Int
)

data class ComparisonData(
    val value1: Double,
    val value2: Double,
    val correctAnswer: ComparisonResult,
    val actualRatio: Double
)

data class ConversionPuzzleData(
    val leftBlock: ConversionBlock,
    val rightBlock: ConversionBlock,
    val correctAnswer: ComparisonResult,
    val conversionType: ConversionType,
    val difficulty: String,
    val hint: String,
    val metadata: ConversionMetadata
)

data class ConversionMetadata(
    val generatedAt: Long,
    val conversionType: String,
    val difficulty: String,
    val unit1: String,
    val unit2: String,
    val comparisonRatio: Double
)
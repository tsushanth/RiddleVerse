package com.kreativekoala.riddleverse

import kotlin.math.*

/**
 * Enhanced Universal Converter with High Precision
 * Uses exact conversion factors and handles edge cases for maximum accuracy
 */
class EnhancedUniversalConverter {

    companion object {
        // High precision constants
        private const val CELSIUS_TO_KELVIN_OFFSET = 273.15
        private const val FAHRENHEIT_TO_CELSIUS_RATIO = 5.0 / 9.0
        private const val CELSIUS_TO_FAHRENHEIT_RATIO = 9.0 / 5.0
        private const val FAHRENHEIT_OFFSET = 32.0

        // Exact conversion factors (using precise values)
        private const val POUND_TO_GRAMS = 453.59237
        private const val OUNCE_TO_GRAMS = 28.349523125
        private const val MILE_TO_METERS = 1609.344
        private const val FOOT_TO_METERS = 0.3048
        private const val INCH_TO_METERS = 0.0254
        private const val YARD_TO_METERS = 0.9144
        private const val GALLON_US_TO_LITERS = 3.785411784
        private const val QUART_US_TO_LITERS = 0.946352946
        private const val PINT_US_TO_LITERS = 0.473176473
        private const val CUP_US_TO_LITERS = 0.2365882365
        private const val SQUARE_FOOT_TO_SQUARE_METERS = 0.09290304
        private const val ACRE_TO_SQUARE_METERS = 4046.8564224
        private const val SQUARE_MILE_TO_SQUARE_METERS = 2589988.110336
        private const val MPH_TO_MPS = 0.44704
        private const val KMH_TO_MPS = 1.0 / 3.6

        // Comparison tolerance
        private const val COMPARISON_TOLERANCE = 1e-12
    }

    /**
     * Base units for each conversion type with exact conversion functions
     */
    private val temperatureToKelvin = mapOf<String, (Double) -> Double>(
        "celsius" to { temp -> temp + CELSIUS_TO_KELVIN_OFFSET },
        "fahrenheit" to { temp -> (temp - FAHRENHEIT_OFFSET) * FAHRENHEIT_TO_CELSIUS_RATIO + CELSIUS_TO_KELVIN_OFFSET },
        "kelvin" to { temp -> temp }
    )

    private val weightToGrams = mapOf<String, (Double) -> Double>(
        "grams" to { weight -> weight },
        "kilograms" to { weight -> weight * 1000.0 },
        "pounds" to { weight -> weight * POUND_TO_GRAMS },
        "lbs" to { weight -> weight * POUND_TO_GRAMS },
        "ounces" to { weight -> weight * OUNCE_TO_GRAMS }
    )

    private val distanceToMeters = mapOf<String, (Double) -> Double>(
        "meters" to { dist -> dist },
        "kilometers" to { dist -> dist * 1000.0 },
        "miles" to { dist -> dist * MILE_TO_METERS },
        "feet" to { dist -> dist * FOOT_TO_METERS },
        "inches" to { dist -> dist * INCH_TO_METERS },
        "yards" to { dist -> dist * YARD_TO_METERS },
        "centimeters" to { dist -> dist * 0.01 }
    )

    private val volumeToLiters = mapOf<String, (Double) -> Double>(
        "liters" to { vol -> vol },
        "milliliters" to { vol -> vol * 0.001 },
        "gallons" to { vol -> vol * GALLON_US_TO_LITERS },
        "quarts" to { vol -> vol * QUART_US_TO_LITERS },
        "pints" to { vol -> vol * PINT_US_TO_LITERS },
        "cups" to { vol -> vol * CUP_US_TO_LITERS }
    )

    private val timeToSeconds = mapOf<String, (Double) -> Double>(
        "seconds" to { time -> time },
        "minutes" to { time -> time * 60.0 },
        "hours" to { time -> time * 3600.0 },
        "days" to { time -> time * 86400.0 },
        "weeks" to { time -> time * 604800.0 }
    )

    private val areaToSquareMeters = mapOf<String, (Double) -> Double>(
        "square meters" to { area -> area },
        "square kilometres" to { area -> area * 1_000_000.0 },
        "square kilometers" to { area -> area * 1_000_000.0 },
        "square feet" to { area -> area * SQUARE_FOOT_TO_SQUARE_METERS },
        "square miles" to { area -> area * SQUARE_MILE_TO_SQUARE_METERS },
        "acres" to { area -> area * ACRE_TO_SQUARE_METERS }
    )

    private val speedToMPS = mapOf<String, (Double) -> Double>(
        "meters per second" to { speed -> speed },
        "kilometres per hour" to { speed -> speed * KMH_TO_MPS },
        "kilometers per hour" to { speed -> speed * KMH_TO_MPS },
        "miles per hour" to { speed -> speed * MPH_TO_MPS },
        "feet per second" to { speed -> speed * FOOT_TO_METERS }
    )

    /**
     * High precision comparison with proper tolerance handling
     */
    fun compareValues(value1: Double, unit1: String, value2: Double, unit2: String): Double {
        val normalizedUnit1 = unit1.lowercase().trim()
        val normalizedUnit2 = unit2.lowercase().trim()

        // Handle temperature conversions with special precision
        temperatureToKelvin[normalizedUnit1]?.let { converter1 ->
            temperatureToKelvin[normalizedUnit2]?.let { converter2 ->
                val temp1 = converter1(value1)
                val temp2 = converter2(value2)
                val difference = temp1 - temp2
                return if (abs(difference) < COMPARISON_TOLERANCE) 0.0 else difference
            }
        }

        // Handle weight conversions
        weightToGrams[normalizedUnit1]?.let { converter1 ->
            weightToGrams[normalizedUnit2]?.let { converter2 ->
                val weight1 = converter1(value1)
                val weight2 = converter2(value2)
                val difference = weight1 - weight2
                return if (abs(difference) < COMPARISON_TOLERANCE) 0.0 else difference
            }
        }

        // Handle distance conversions
        distanceToMeters[normalizedUnit1]?.let { converter1 ->
            distanceToMeters[normalizedUnit2]?.let { converter2 ->
                val dist1 = converter1(value1)
                val dist2 = converter2(value2)
                val difference = dist1 - dist2
                return if (abs(difference) < COMPARISON_TOLERANCE) 0.0 else difference
            }
        }

        // Handle volume conversions
        volumeToLiters[normalizedUnit1]?.let { converter1 ->
            volumeToLiters[normalizedUnit2]?.let { converter2 ->
                val vol1 = converter1(value1)
                val vol2 = converter2(value2)
                val difference = vol1 - vol2
                return if (abs(difference) < COMPARISON_TOLERANCE) 0.0 else difference
            }
        }

        // Handle time conversions
        timeToSeconds[normalizedUnit1]?.let { converter1 ->
            timeToSeconds[normalizedUnit2]?.let { converter2 ->
                val time1 = converter1(value1)
                val time2 = converter2(value2)
                val difference = time1 - time2
                return if (abs(difference) < COMPARISON_TOLERANCE) 0.0 else difference
            }
        }

        // Handle area conversions
        areaToSquareMeters[normalizedUnit1]?.let { converter1 ->
            areaToSquareMeters[normalizedUnit2]?.let { converter2 ->
                val area1 = converter1(value1)
                val area2 = converter2(value2)
                val difference = area1 - area2
                return if (abs(difference) < COMPARISON_TOLERANCE) 0.0 else difference
            }
        }

        // Handle speed conversions
        speedToMPS[normalizedUnit1]?.let { converter1 ->
            speedToMPS[normalizedUnit2]?.let { converter2 ->
                val speed1 = converter1(value1)
                val speed2 = converter2(value2)
                val difference = speed1 - speed2
                return if (abs(difference) < COMPARISON_TOLERANCE) 0.0 else difference
            }
        }

        // If no conversion found, compare as raw values
        val difference = value1 - value2
        return if (abs(difference) < COMPARISON_TOLERANCE) 0.0 else difference
    }

    /**
     * Convert value from one unit to another with high precision
     */
    fun convertValue(value: Double, fromUnit: String, toUnit: String): Double {
        val normalizedFromUnit = fromUnit.lowercase().trim()
        val normalizedToUnit = toUnit.lowercase().trim()

        // Special handling for temperature (offset-based conversions)
        if (isTemperatureUnit(normalizedFromUnit) && isTemperatureUnit(normalizedToUnit)) {
            return convertTemperature(value, normalizedFromUnit, normalizedToUnit)
        }

        // For other units, convert to base unit then to target unit
        val baseValue = convertToBaseUnit(value, normalizedFromUnit)
        return convertFromBaseUnit(baseValue, normalizedFromUnit, normalizedToUnit)
    }

    /**
     * Convert temperature with precise formulas
     */
    private fun convertTemperature(value: Double, fromUnit: String, toUnit: String): Double {
        return when {
            fromUnit == toUnit -> value
            fromUnit == "celsius" && toUnit == "fahrenheit" -> {
                value * CELSIUS_TO_FAHRENHEIT_RATIO + FAHRENHEIT_OFFSET
            }
            fromUnit == "fahrenheit" && toUnit == "celsius" -> {
                (value - FAHRENHEIT_OFFSET) * FAHRENHEIT_TO_CELSIUS_RATIO
            }
            fromUnit == "celsius" && toUnit == "kelvin" -> {
                value + CELSIUS_TO_KELVIN_OFFSET
            }
            fromUnit == "kelvin" && toUnit == "celsius" -> {
                value - CELSIUS_TO_KELVIN_OFFSET
            }
            fromUnit == "fahrenheit" && toUnit == "kelvin" -> {
                (value - FAHRENHEIT_OFFSET) * FAHRENHEIT_TO_CELSIUS_RATIO + CELSIUS_TO_KELVIN_OFFSET
            }
            fromUnit == "kelvin" && toUnit == "fahrenheit" -> {
                (value - CELSIUS_TO_KELVIN_OFFSET) * CELSIUS_TO_FAHRENHEIT_RATIO + FAHRENHEIT_OFFSET
            }
            else -> value
        }
    }

    /**
     * Check if unit is a temperature unit
     */
    private fun isTemperatureUnit(unit: String): Boolean {
        return temperatureToKelvin.containsKey(unit)
    }

    /**
     * Convert to base unit for the measurement type
     */
    private fun convertToBaseUnit(value: Double, unit: String): Double {
        return when {
            weightToGrams.containsKey(unit) -> weightToGrams[unit]!!(value)
            distanceToMeters.containsKey(unit) -> distanceToMeters[unit]!!(value)
            volumeToLiters.containsKey(unit) -> volumeToLiters[unit]!!(value)
            timeToSeconds.containsKey(unit) -> timeToSeconds[unit]!!(value)
            areaToSquareMeters.containsKey(unit) -> areaToSquareMeters[unit]!!(value)
            speedToMPS.containsKey(unit) -> speedToMPS[unit]!!(value)
            else -> value
        }
    }

    /**
     * Convert from base unit to target unit
     */
    private fun convertFromBaseUnit(baseValue: Double, originalUnit: String, targetUnit: String): Double {
        // Find the conversion category and convert back
        when {
            weightToGrams.containsKey(originalUnit) && weightToGrams.containsKey(targetUnit) -> {
                return baseValue / weightToGrams[targetUnit]!!(1.0)
            }
            distanceToMeters.containsKey(originalUnit) && distanceToMeters.containsKey(targetUnit) -> {
                return baseValue / distanceToMeters[targetUnit]!!(1.0)
            }
            volumeToLiters.containsKey(originalUnit) && volumeToLiters.containsKey(targetUnit) -> {
                return baseValue / volumeToLiters[targetUnit]!!(1.0)
            }
            timeToSeconds.containsKey(originalUnit) && timeToSeconds.containsKey(targetUnit) -> {
                return baseValue / timeToSeconds[targetUnit]!!(1.0)
            }
            areaToSquareMeters.containsKey(originalUnit) && areaToSquareMeters.containsKey(targetUnit) -> {
                return baseValue / areaToSquareMeters[targetUnit]!!(1.0)
            }
            speedToMPS.containsKey(originalUnit) && speedToMPS.containsKey(targetUnit) -> {
                return baseValue / speedToMPS[targetUnit]!!(1.0)
            }
        }
        return baseValue
    }

    fun getConversionType(unit1: String, unit2: String): ConversionType {
        val normalizedUnit1 = unit1.lowercase().trim()
        val normalizedUnit2 = unit2.lowercase().trim()

        return when {
            temperatureToKelvin.containsKey(normalizedUnit1) || temperatureToKelvin.containsKey(normalizedUnit2) -> ConversionType.TEMPERATURE
            weightToGrams.containsKey(normalizedUnit1) || weightToGrams.containsKey(normalizedUnit2) -> ConversionType.WEIGHT
            distanceToMeters.containsKey(normalizedUnit1) || distanceToMeters.containsKey(normalizedUnit2) -> ConversionType.DISTANCE
            volumeToLiters.containsKey(normalizedUnit1) || volumeToLiters.containsKey(normalizedUnit2) -> ConversionType.VOLUME
            timeToSeconds.containsKey(normalizedUnit1) || timeToSeconds.containsKey(normalizedUnit2) -> ConversionType.TIME
            areaToSquareMeters.containsKey(normalizedUnit1) || areaToSquareMeters.containsKey(normalizedUnit2) -> ConversionType.AREA
            speedToMPS.containsKey(normalizedUnit1) || speedToMPS.containsKey(normalizedUnit2) -> ConversionType.SPEED
            else -> ConversionType.UNKNOWN
        }
    }

    fun getShortUnit(unit: String): String {
        return when (unit.lowercase().trim()) {
            "celsius" -> "°C"
            "fahrenheit" -> "°F"
            "kelvin" -> "K"
            "grams" -> "g"
            "kilograms" -> "kg"
            "pounds", "lbs" -> "lbs"
            "ounces" -> "oz"
            "meters" -> "m"
            "kilometers", "kilometres" -> "km"
            "miles" -> "mi"
            "feet" -> "ft"
            "inches" -> "in"
            "yards" -> "yd"
            "centimeters" -> "cm"
            "liters" -> "L"
            "milliliters" -> "mL"
            "gallons" -> "gal"
            "quarts" -> "qt"
            "pints" -> "pt"
            "cups" -> "cups"
            "seconds" -> "sec"
            "minutes" -> "min"
            "hours" -> "hr"
            "days" -> "days"
            "weeks" -> "wks"
            "square meters" -> "m²"
            "square kilometers", "square kilometres" -> "km²"
            "square feet" -> "ft²"
            "square miles" -> "mi²"
            "acres" -> "acres"
            "meters per second" -> "m/s"
            "kilometers per hour", "kilometres per hour" -> "km/h"
            "miles per hour" -> "mph"
            "feet per second" -> "ft/s"
            else -> unit
        }
    }

    /**
     * Get relative comparison tolerance based on the magnitude of values
     */
    fun getComparisonTolerance(value1: Double, value2: Double): Double {
        val maxValue = maxOf(abs(value1), abs(value2))
        return when {
            maxValue < 1.0 -> 1e-6
            maxValue < 100.0 -> 1e-5
            maxValue < 10000.0 -> 1e-4
            else -> 1e-3
        }
    }

    /**
     * Precise equality check with context-appropriate tolerance
     */
    fun areEqual(value1: Double, unit1: String, value2: Double, unit2: String): Boolean {
        val comparisonResult = compareValues(value1, unit1, value2, unit2)
        val tolerance = getComparisonTolerance(value1, value2)
        return abs(comparisonResult) < tolerance
    }
}
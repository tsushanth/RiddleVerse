package com.kreativekoala.riddleverse

import java.lang.Math.pow
import kotlin.math.round
import kotlin.random.Random

data class SubscriptionPuzzleData(
    val payment: Double,
    val frequency: String,
    val purpose: String,
    val yearlyTotal: Double,
    val difficulty: String,
    val hint: String,
    val options: List<Int>
)

class SubscriptionPuzzleGenerator {

    private val frequencies = listOf("weekly", "biweekly", "monthly", "quarterly", "yearly")

    private val frequencyMultipliers = mapOf(
        "weekly" to 52,
        "biweekly" to 26,
        "monthly" to 12,
        "quarterly" to 4,
        "yearly" to 1
    )

    private val subscriptionPurposes = listOf(
        "Gym membership",
        "Music streaming",
        "Video streaming",
        "Cloud storage",
        "Software license",
        "Magazine subscription",
        "Meal delivery",
        "Coffee subscription",
        "Internet service",
        "Phone plan",
        "Insurance premium",
        "Parking pass",
        "Transit pass",
        "Newsletter subscription",
        "App subscription",
        "VPN service",
        "Domain hosting",
        "Online courses",
        "Gaming subscription",
        "Beauty box"
    )

    fun generateSubscriptionPuzzle(difficulty: String): SubscriptionPuzzleData? {
        return try {
            val frequency = frequencies.random()
            val payment = generatePaymentAmount(difficulty)
            val purpose = subscriptionPurposes.random()

            val multiplier = frequencyMultipliers[frequency] ?: 1
            val yearlyTotal = round(payment * multiplier * 100) / 100

            val hint = "Multiply the $frequency payment by the number of periods in a year"

            val options = generateOptions(yearlyTotal.toInt())

            SubscriptionPuzzleData(
                payment = payment,
                frequency = frequency,
                purpose = purpose,
                yearlyTotal = yearlyTotal,
                difficulty = difficulty,
                hint = hint,
                options = options
            )
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionGenerator", "Failed to generate subscription puzzle", e)
            null
        }
    }

    private fun generatePaymentAmount(difficulty: String): Double {
        return when (difficulty.lowercase()) {
            "easy" -> randomFloat(10.0, 100.0, 2)
            "medium" -> randomFloat(50.0, 300.0, 2)
            "hard" -> randomFloat(200.0, 800.0, 2)
            "expert" -> randomFloat(500.0, 2000.0, 2)
            else -> randomFloat(50.0, 300.0, 2) // Default to medium
        }
    }

    private fun randomFloat(min: Double, max: Double, decimals: Int): Double {
        val range = max - min
        val randomValue = min + (Random.nextDouble() * range)
        val multiplier = pow(10.0, decimals.toDouble())
        return round(randomValue * multiplier) / multiplier
    }

    private fun generateOptions(correctAnswer: Int): List<Int> {
        val options = mutableSetOf<Int>()
        options.add(correctAnswer)

        // Generate 3 incorrect options with some variation
        val variations = listOf(
            (correctAnswer * 0.7).toInt(),  // 30% less
            (correctAnswer * 1.3).toInt(),  // 30% more
            (correctAnswer * 0.5).toInt(),  // 50% less
            (correctAnswer * 1.5).toInt(),  // 50% more
            (correctAnswer * 0.9).toInt(),  // 10% less
            (correctAnswer * 1.1).toInt(),  // 10% more
            correctAnswer + Random.nextInt(50, 200), // Random addition
            correctAnswer - Random.nextInt(50, 200)  // Random subtraction
        )

        // Add unique variations until we have 4 options total
        for (variation in variations.shuffled()) {
            if (variation > 0 && variation != correctAnswer && options.size < 4) {
                options.add(variation)
            }
        }

        // If we still don't have enough options, generate some more
        while (options.size < 4) {
            val randomVariation = correctAnswer + Random.nextInt(-300, 300)
            if (randomVariation > 0 && randomVariation != correctAnswer) {
                options.add(randomVariation)
            }
        }

        return options.toList().shuffled()
    }
}
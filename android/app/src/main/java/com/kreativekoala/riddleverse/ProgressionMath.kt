package com.kreativekoala.riddleverse

import kotlin.math.pow

/**
 * Pure progression math (no Android, no storage) so it can be unit tested on the JVM.
 * ProgressionEngine owns persistence and delegates the calculations here.
 */
internal object ProgressionMath {

    val TIER_THRESHOLDS = listOf(
        "Bronze" to 0,
        "Silver" to 500,
        "Gold" to 1500,
        "Platinum" to 3000,
        "Diamond" to 6000,
        "Master" to 10000
    )

    private val DIFFICULTY_MULTIPLIERS = mapOf(
        "Easy" to 1.0f,
        "Medium" to 1.25f,
        "Hard" to 1.5f
    )

    private val BASE_SCORES = mapOf(
        "math" to mapOf("Easy" to 10, "Medium" to 15, "Hard" to 20),
        "anagram" to mapOf("Easy" to 15, "Medium" to 20, "Hard" to 25),
        "trivia" to mapOf("Easy" to 12, "Medium" to 17, "Hard" to 22),
        "wordsearch" to mapOf("Easy" to 14, "Medium" to 19, "Hard" to 24),
        "crypto" to mapOf("Easy" to 18, "Medium" to 23, "Hard" to 28),
        "wordsnake" to mapOf("Easy" to 16, "Medium" to 21, "Hard" to 26)
    )

    fun baseScore(puzzleType: String, difficulty: String): Int =
        BASE_SCORES[puzzleType.lowercase()]?.get(difficulty) ?: 15

    fun difficultyMultiplier(difficulty: String): Float =
        DIFFICULTY_MULTIPLIERS[difficulty] ?: 1.0f

    /** Up to +50% of the base score, scaled by the fraction of time left. */
    fun timeBonus(timeRemaining: Int, totalTime: Int, baseScore: Int): Int {
        if (timeRemaining <= 0 || totalTime <= 0) return 0
        val timePercentage = timeRemaining.toDouble() / totalTime.toDouble()
        val maxBonus = baseScore.toDouble() * 0.5
        return (maxBonus * timePercentage.pow(0.7)).toInt()
    }

    fun streakBonus(baseScore: Int, currentStreak: Int): Int = when {
        currentStreak >= 10 -> (baseScore * 0.5).toInt()
        currentStreak >= 5 -> (baseScore * 0.3).toInt()
        currentStreak >= 3 -> (baseScore * 0.2).toInt()
        else -> 0
    }

    fun streakMultiplier(streak: Int): Float = when {
        streak >= 10 -> 2.0f
        streak >= 5 -> 1.5f
        streak >= 3 -> 1.25f
        else -> 1.0f
    }

    fun scoreBreakdown(
        puzzleType: String,
        baseScore: Int,
        timeRemaining: Int,
        totalTime: Int,
        difficulty: String,
        currentStreak: Int
    ): ScoreBreakdown {
        val actualBase = if (baseScore > 0) baseScore else baseScore(puzzleType, difficulty)
        val timeBonus = timeBonus(timeRemaining, totalTime, actualBase)
        val streakBonus = streakBonus(actualBase, currentStreak)
        val multiplier = difficultyMultiplier(difficulty)
        val total = ((actualBase + timeBonus + streakBonus).toFloat() * multiplier).toInt()
        return ScoreBreakdown(
            baseScore = actualBase,
            timeBonus = timeBonus,
            streakBonus = streakBonus,
            difficultyMultiplier = multiplier,
            totalScore = total,
            xpGained = total
        )
    }

    /** XP needed to clear [level] and reach the next one. */
    fun xpRequiredForLevel(level: Int): Int =
        (100 * level * (1 + level * 0.1)).toInt()

    /** Total XP at which [level] begins (0 for level 1). */
    fun xpAtStartOfLevel(level: Int): Int =
        (1 until level).sumOf { xpRequiredForLevel(it) }

    fun levelFromXp(totalXP: Int): Int {
        var level = 1
        while (xpAtStartOfLevel(level + 1) <= totalXP) level++
        return level
    }

    fun tierIndexForXp(totalXP: Int): Int =
        TIER_THRESHOLDS.indexOfLast { it.second <= totalXP }.coerceAtLeast(0)

    /**
     * Daily-streak transition when a puzzle is completed.
     * Must be evaluated from the activity state *before* today's play is recorded,
     * otherwise [hasPlayedToday] is always true and the streak can never advance.
     */
    fun nextDailyStreak(
        hasEverPlayed: Boolean,
        hasPlayedToday: Boolean,
        hasPlayedYesterday: Boolean,
        currentStreak: Int
    ): Int = when {
        !hasEverPlayed -> 1
        hasPlayedToday -> maxOf(currentStreak, 1)
        hasPlayedYesterday -> currentStreak + 1
        else -> 1
    }
}

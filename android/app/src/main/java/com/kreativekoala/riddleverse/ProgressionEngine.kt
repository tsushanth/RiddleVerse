package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import java.util.Calendar
import kotlin.math.pow

/**
 * Pure Game Logic Engine - Handles all progression calculations
 * Does NOT handle: Data storage, UI feedback, sound effects
 * Does handle: XP calculations, level progression, achievements, streaks, scoring
 */
class ProgressionEngine(
    private val context: Context,
    private val userStatsManager: UserStatsManager
) {

    companion object {
        // Tier system configuration
        private val TIER_THRESHOLDS = listOf(
            "Bronze" to 0,
            "Silver" to 500,
            "Gold" to 1500,
            "Platinum" to 3000,
            "Diamond" to 6000,
            "Master" to 10000
        )

        // Difficulty multipliers for scoring
        private val DIFFICULTY_MULTIPLIERS = mapOf(
            "Easy" to 1.0f,
            "Medium" to 1.25f,
            "Hard" to 1.5f
        )

        // Base scores for different puzzle types
        private val BASE_SCORES = mapOf(
            "math" to mapOf("Easy" to 10, "Medium" to 15, "Hard" to 20),
            "anagram" to mapOf("Easy" to 15, "Medium" to 20, "Hard" to 25),
            "trivia" to mapOf("Easy" to 12, "Medium" to 17, "Hard" to 22),
            "wordsearch" to mapOf("Easy" to 14, "Medium" to 19, "Hard" to 24),
            "crypto" to mapOf("Easy" to 18, "Medium" to 23, "Hard" to 28),
            "wordsnake" to mapOf("Easy" to 16, "Medium" to 21, "Hard" to 26)
        )

        // Achievement definitions
        val ACHIEVEMENT_DEFINITIONS = listOf(
            Achievement("first_correct", "First Success", "Solve your first puzzle", "🎯"),
            Achievement("streak_3", "On a Roll", "Get 3 correct in a row", "🔥", maxProgress = 3),
            Achievement("streak_10", "Unstoppable", "Get 10 correct in a row", "⚡", maxProgress = 10),
            Achievement("streak_25", "Legendary", "Get 25 correct in a row", "🏆", maxProgress = 25),
            Achievement("speed_demon", "Speed Demon", "Solve 5 puzzles under 30s", "💨", maxProgress = 5),
            Achievement("math_master", "Math Master", "Solve 25 math puzzles", "🧮", maxProgress = 25),
            Achievement("word_wizard", "Word Wizard", "Solve 25 word puzzles", "📚", maxProgress = 25),
            Achievement("level_5", "Rising Star", "Reach level 5", "⭐", maxProgress = 5),
            Achievement("level_10", "Expert", "Reach level 10", "👑", maxProgress = 10),
            Achievement("level_20", "Master", "Reach level 20", "💎", maxProgress = 20),
            Achievement("hundred_points", "Century", "Score 100+ points in one puzzle", "💯"),
            Achievement("perfect_week", "Perfect Week", "Play 7 days in a row", "📅", maxProgress = 7),
            Achievement("daily_streak_30", "Month Master", "30 day streak", "🗓️", maxProgress = 30)
        )
    }

    // ================================
    // MAIN PROGRESSION PROCESSING
    // ================================

    /**
     * Process a puzzle completion and return all progression results
     */
    fun processPuzzleCompletion(
        puzzleType: String,
        isCorrect: Boolean,
        score: Int,
        timeSpentSeconds: Int,
        timeRemaining: Int,
        totalTime: Int,
        difficulty: String = "Medium",
        hintsUsed: Int = 0
    ): ProgressionResult {

        Log.d("ProgressionEngine", "🎮 Processing completion: $puzzleType, correct=$isCorrect, score=$score")

        // Get current state before changes
        val oldLevel = getCurrentLevel()
        val oldStreak = getCurrentStreak()
        val oldDailyStreak = getCurrentDailyStreak()

        // Record the completion in data layer
        userStatsManager.recordPuzzleCompletion(puzzleType, score, timeSpentSeconds, isCorrect, difficulty)
        userStatsManager.recordDailyActivity()
        userStatsManager.recordWeeklyActivity()

        // Calculate progression results
        val scoreBreakdown = if (isCorrect) {
            calculateScoreBreakdown(puzzleType, score, timeRemaining, totalTime, difficulty, getCurrentStreak() + 1)
        } else {
            ScoreBreakdown(0, 0, 0, 1.0f, 0, 0)
        }

        // Update streaks
        val newStreak = updateQuestionStreak(isCorrect)
        val newDailyStreak = updateDailyStreak()

        // Update XP and check for level up
        var levelUpInfo: LevelUpInfo? = null
        if (isCorrect) {
            val xpGained = scoreBreakdown.xpGained
            userStatsManager.recordSessionData(xpGained, timeSpentSeconds)

            val newLevel = getCurrentLevel()
            if (newLevel.level > oldLevel.level) {
                levelUpInfo = LevelUpInfo(oldLevel.level, newLevel.level, newLevel.level * 50)
            }
        }

        // Check for new achievements
        val newAchievements = checkForNewAchievements(puzzleType, isCorrect, scoreBreakdown, newStreak, newDailyStreak)

        // Create streak info
        val streakInfo = StreakInfo(
            currentStreak = newStreak,
            bestStreak = getBestStreak(),
            streakMultiplier = getStreakMultiplier(newStreak),
            dailyStreak = newDailyStreak,
            hasDailyStreakBonus = newDailyStreak >= 3
        )

        val result = ProgressionResult(
            scoreBreakdown = scoreBreakdown,
            levelUp = levelUpInfo,
            newAchievements = newAchievements,
            streakInfo = streakInfo,
            totalXPGained = scoreBreakdown.xpGained,
            isCorrect = isCorrect
        )

        Log.d("ProgressionEngine", "✅ Progression complete: ${result.totalXPGained}XP, level=${getCurrentLevel().level}")

        return result
    }

    // ================================
    // SCORING CALCULATIONS
    // ================================

    /**
     * Calculate detailed score breakdown
     */
    private fun calculateScoreBreakdown(
        puzzleType: String,
        baseScore: Int,
        timeRemaining: Int,
        totalTime: Int,
        difficulty: String,
        currentStreak: Int
    ): ScoreBreakdown {

        // Use provided score or calculate base score
        val actualBaseScore = if (baseScore > 0) baseScore else getBaseScore(puzzleType, difficulty)

        // Time bonus calculation
        val timeBonus = calculateTimeBonus(timeRemaining, totalTime, actualBaseScore)

        // Streak bonus calculation
        val streakBonus = calculateStreakBonus(actualBaseScore, currentStreak)

        // Difficulty multiplier
        val difficultyMultiplier = DIFFICULTY_MULTIPLIERS[difficulty] ?: 1.0f

        // Calculate total score
        val subtotal = (actualBaseScore + timeBonus + streakBonus).toFloat()
        val totalScore = (subtotal * difficultyMultiplier).toInt()

        // XP calculation (same as total score for now)
        val xpGained = totalScore

        return ScoreBreakdown(
            baseScore = actualBaseScore,
            timeBonus = timeBonus,
            streakBonus = streakBonus,
            difficultyMultiplier = difficultyMultiplier,
            totalScore = totalScore,
            xpGained = xpGained
        )
    }

    /**
     * Get base score for puzzle type and difficulty
     */
    private fun getBaseScore(puzzleType: String, difficulty: String): Int {
        return BASE_SCORES[puzzleType.lowercase()]?.get(difficulty) ?: 15
    }

    /**
     * Calculate time-based bonus points
     */
    private fun calculateTimeBonus(timeRemaining: Int, totalTime: Int, baseScore: Int): Int {
        if (timeRemaining <= 0 || totalTime <= 0) return 0

        val timePercentage = timeRemaining.toDouble() / totalTime.toDouble()
        val maxBonus = baseScore.toDouble() * 0.5 // Max 50% bonus

        // Exponential curve for better rewards for fast completion
        val bonus = maxBonus * timePercentage.pow(0.7)

        return bonus.toInt()
    }

    /**
     * Calculate streak-based bonus points
     */
    private fun calculateStreakBonus(baseScore: Int, currentStreak: Int): Int {
        return when {
            currentStreak >= 10 -> (baseScore * 0.5).toInt()
            currentStreak >= 5 -> (baseScore * 0.3).toInt()
            currentStreak >= 3 -> (baseScore * 0.2).toInt()
            else -> 0
        }
    }

    // ================================
    // LEVEL PROGRESSION
    // ================================

    /**
     * Get current user level based on total XP
     */
    fun getCurrentLevel(): UserLevel {
        val globalStats = userStatsManager.getGlobalStats()
        val totalXP = globalStats.totalXP

        val level = calculateLevelFromXP(totalXP)
        val xpForCurrentLevel = if (level > 1) getXPRequiredForLevel(level - 1) else 0
        val xpForNextLevel = getXPRequiredForLevel(level)
        val currentXP = totalXP - xpForCurrentLevel
        val xpToNext = xpForNextLevel - xpForCurrentLevel

        return UserLevel(
            level = level,
            currentXP = currentXP,
            xpToNextLevel = xpToNext,
            totalXP = totalXP
        )
    }

    /**
     * Calculate level from total XP
     */
    private fun calculateLevelFromXP(totalXP: Int): Int {
        var level = 1
        var xpNeeded = 0

        while (xpNeeded <= totalXP) {
            xpNeeded += getXPRequiredForLevel(level)
            if (xpNeeded <= totalXP) level++
        }

        return level
    }

    /**
     * Get XP required for a specific level
     */
    private fun getXPRequiredForLevel(level: Int): Int {
        return (100 * level * (1 + level * 0.1)).toInt()
    }

    // ================================
    // TIER SYSTEM
    // ================================

    /**
     * Get current tier information
     */
    fun getTierInfo(): TierInfo {
        val globalStats = userStatsManager.getGlobalStats()
        val totalXP = globalStats.totalXP

        val currentTierIndex = TIER_THRESHOLDS.indexOfLast { it.second <= totalXP }
        val currentTier = TIER_THRESHOLDS[currentTierIndex].first
        val nextTierIndex = (currentTierIndex + 1).coerceAtMost(TIER_THRESHOLDS.size - 1)
        val nextTier = TIER_THRESHOLDS[nextTierIndex].first

        val pointsInTier = totalXP - TIER_THRESHOLDS[currentTierIndex].second
        val pointsToNextTier = TIER_THRESHOLDS[nextTierIndex].second - TIER_THRESHOLDS[currentTierIndex].second
        val progress = if (pointsToNextTier > 0) pointsInTier.toFloat() / pointsToNextTier else 1f

        val rewards = when (nextTier) {
            "Silver" -> listOf("🥈 Silver Badge", "🎨 Theme Pack")
            "Gold" -> listOf("🥇 Gold Badge", "👑 Crown Icon")
            "Platinum" -> listOf("💎 Platinum Badge", "🌟 Exclusive Themes")
            "Diamond" -> listOf("💠 Diamond Badge", "🎭 Custom Avatar")
            "Master" -> listOf("👑 Master Crown", "🌟 Ultimate Rewards")
            else -> listOf("🎁 Special Reward")
        }

        return TierInfo(
            currentTier = currentTier,
            nextTier = if (currentTierIndex == TIER_THRESHOLDS.size - 1) currentTier else nextTier,
            pointsInTier = pointsInTier,
            pointsToNextTier = pointsToNextTier,
            progressPercentage = progress,
            nextTierRewards = rewards,
            currentLevel = calculateLevelFromXP(totalXP),
            totalXP = totalXP
        )
    }

    // ================================
    // STREAK MANAGEMENT
    // ================================

    /**
     * Update question-based streak
     */
    private fun updateQuestionStreak(isCorrect: Boolean): Int {
        val currentStreak = getCurrentStreak()
        val newStreak = if (isCorrect) currentStreak + 1 else 0
        val bestStreak = getBestStreak()

        // Update stored values
        val context = this.context
        val prefs = context.getSharedPreferences("progression_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("current_streak", newStreak)
            .putInt("best_streak", maxOf(newStreak, bestStreak))
            .apply()

        return newStreak
    }

    /**
     * Update daily streak based on activity
     */
    private fun updateDailyStreak(): Int {
        val dailyData = userStatsManager.getDailyActivityData()

        val newDailyStreak = when {
            dailyData.lastActiveDate.isEmpty() -> 1 // First time
            dailyData.hasPlayedYesterday || dailyData.hasPlayedToday -> {
                // Continue or maintain streak
                getCurrentDailyStreak() + if (!dailyData.hasPlayedToday) 1 else 0
            }
            else -> 1 // Streak broken, restart
        }

        // Store daily streak
        val context = this.context
        val prefs = context.getSharedPreferences("progression_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("daily_streak", newDailyStreak)
            .putInt("best_daily_streak", maxOf(newDailyStreak, prefs.getInt("best_daily_streak", 0)))
            .apply()

        return newDailyStreak
    }

    /**
     * Get current question streak
     */
    fun getCurrentStreak(): Int {
        val prefs = context.getSharedPreferences("progression_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("current_streak", 0)
    }

    /**
     * Get best question streak
     */
    fun getBestStreak(): Int {
        val prefs = context.getSharedPreferences("progression_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("best_streak", 0)
    }

    /**
     * Get current daily streak
     */
    fun getCurrentDailyStreak(): Int {
        val prefs = context.getSharedPreferences("progression_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("daily_streak", 0)
    }

    /**
     * Get streak multiplier for scoring
     */
    private fun getStreakMultiplier(streak: Int): Float {
        return when {
            streak >= 10 -> 2.0f
            streak >= 5 -> 1.5f
            streak >= 3 -> 1.25f
            else -> 1.0f
        }
    }

    // ================================
    // ACHIEVEMENT SYSTEM
    // ================================

    /**
     * Check for newly unlocked achievements
     */
    private fun checkForNewAchievements(
        puzzleType: String,
        isCorrect: Boolean,
        scoreBreakdown: ScoreBreakdown,
        questionStreak: Int,
        dailyStreak: Int
    ): List<Achievement> {

        if (!isCorrect) return emptyList()

        val newAchievements = mutableListOf<Achievement>()

        // Track total puzzles solved
        val globalStats = userStatsManager.getGlobalStats()
        val totalPuzzles = globalStats.totalGamesPlayed

        // Check various achievement conditions
        checkAndUnlockAchievement("first_correct", totalPuzzles >= 1, newAchievements)
        checkAndUnlockAchievement("streak_3", questionStreak >= 3, newAchievements)
        checkAndUnlockAchievement("streak_10", questionStreak >= 10, newAchievements)
        checkAndUnlockAchievement("streak_25", questionStreak >= 25, newAchievements)
        checkAndUnlockAchievement("hundred_points", scoreBreakdown.totalScore >= 100, newAchievements)

        // Level-based achievements
        val currentLevel = getCurrentLevel().level
        checkAndUnlockAchievement("level_5", currentLevel >= 5, newAchievements)
        checkAndUnlockAchievement("level_10", currentLevel >= 10, newAchievements)
        checkAndUnlockAchievement("level_20", currentLevel >= 20, newAchievements)

        // Daily streak achievements
        checkAndUnlockAchievement("perfect_week", dailyStreak >= 7, newAchievements)
        checkAndUnlockAchievement("daily_streak_30", dailyStreak >= 30, newAchievements)

        // Puzzle type specific achievements
        val puzzleStats = userStatsManager.getPuzzleStats(puzzleType)
        if (puzzleType.contains("math", true)) {
            checkAndUnlockAchievement("math_master", puzzleStats.wins >= 25, newAchievements)
        }
        if (puzzleType.contains("word", true) || puzzleType.contains("anagram", true)) {
            checkAndUnlockAchievement("word_wizard", puzzleStats.wins >= 25, newAchievements)
        }

        return newAchievements
    }

    /**
     * Check and unlock a specific achievement
     */
    private fun checkAndUnlockAchievement(
        achievementId: String,
        condition: Boolean,
        newAchievements: MutableList<Achievement>
    ) {
        if (condition && !userStatsManager.isAchievementUnlocked(achievementId)) {
            userStatsManager.unlockAchievement(achievementId)
            ACHIEVEMENT_DEFINITIONS.find { it.id == achievementId }?.let { achievement ->
                newAchievements.add(achievement.copy(isUnlocked = true))
            }
        }
    }

    /**
     * Get all achievements with current progress
     */
    fun getAllAchievements(): List<Achievement> {
        val globalStats = userStatsManager.getGlobalStats()
        val currentLevel = getCurrentLevel().level
        val currentStreak = getCurrentStreak()
        val currentDailyStreak = getCurrentDailyStreak()

        return ACHIEVEMENT_DEFINITIONS.map { achievement ->
            val isUnlocked = userStatsManager.isAchievementUnlocked(achievement.id)

            val progress = if (isUnlocked) {
                achievement.maxProgress
            } else {
                when (achievement.id) {
                    "first_correct" -> if (globalStats.totalGamesPlayed > 0) 1 else 0
                    "streak_3" -> minOf(currentStreak, 3)
                    "streak_10" -> minOf(currentStreak, 10)
                    "streak_25" -> minOf(currentStreak, 25)
                    "level_5" -> minOf(currentLevel, 5)
                    "level_10" -> minOf(currentLevel, 10)
                    "level_20" -> minOf(currentLevel, 20)
                    "perfect_week" -> minOf(currentDailyStreak, 7)
                    "daily_streak_30" -> minOf(currentDailyStreak, 30)
                    "math_master" -> minOf(userStatsManager.getPuzzleStats("math").wins, 25)
                    "word_wizard" -> {
                        val wordWins = userStatsManager.getPuzzleStats("anagram").wins +
                                userStatsManager.getPuzzleStats("wordsearch").wins +
                                userStatsManager.getPuzzleStats("wordsnake").wins
                        minOf(wordWins, 25)
                    }
                    else -> 0
                }
            }

            achievement.copy(
                isUnlocked = isUnlocked,
                progress = progress
            )
        }
    }

    // ================================
    // WEEKLY CHALLENGES
    // ================================

    /**
     * Get current weekly challenge information
     */
    fun getWeeklyChallengeInfo(): WeeklyChallengeInfo {
        val weeklyData = userStatsManager.getWeeklyActivityData()

        return WeeklyChallengeInfo(
            weeklyGoal = 25, // Can be made configurable
            weeklyProgress = weeklyData.weeklyProgress,
            timeUntilReset = weeklyData.timeUntilReset,
            weeklyReward = 200,
            currentWeek = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
        )
    }
}



data class StreakInfo(
    val currentStreak: Int,
    val bestStreak: Int,
    val streakMultiplier: Float,
    val dailyStreak: Int = 0,
    val hasDailyStreakBonus: Boolean = false
) {
    val hasStreakBonus: Boolean get() = currentStreak >= 3
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val isUnlocked: Boolean = false,
    val progress: Int = 0,
    val maxProgress: Int = 1
) {
    val progressPercentage: Float
        get() = if (maxProgress > 0) progress.toFloat() / maxProgress.toFloat() else 0f
}

data class TierInfo(
    val currentTier: String,
    val nextTier: String,
    val pointsInTier: Int,
    val pointsToNextTier: Int,
    val progressPercentage: Float,
    val nextTierRewards: List<String>,
    val currentLevel: Int = 1,
    val totalXP: Int = 0
)

data class WeeklyChallengeInfo(
    val weeklyGoal: Int,
    val weeklyProgress: Int,
    val timeUntilReset: Long,
    val weeklyReward: Int,
    val currentWeek: Int
) {
    val isCompleted: Boolean get() = weeklyProgress >= weeklyGoal
    val progressPercentage: Float get() = if (weeklyGoal > 0) weeklyProgress.toFloat() / weeklyGoal else 0f
}
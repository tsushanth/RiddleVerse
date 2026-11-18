package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
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
        // Achievement definitions
        val ACHIEVEMENT_DEFINITIONS = listOf(
            Achievement("first_correct", R.string.stats_ach_first_correct_title, R.string.stats_ach_first_correct_desc, "🎯"),
            Achievement("streak_3", R.string.stats_ach_streak_3_title, R.string.stats_ach_streak_3_desc, "🔥", maxProgress = 3),
            Achievement("streak_10", R.string.stats_ach_streak_10_title, R.string.stats_ach_streak_10_desc, "⚡", maxProgress = 10),
            Achievement("streak_25", R.string.stats_ach_streak_25_title, R.string.stats_ach_streak_25_desc, "🏆", maxProgress = 25),
            Achievement("speed_demon", R.string.onb_sample_ach2_title, R.string.stats_ach_speed_demon_desc, "💨", maxProgress = 5),
            Achievement("math_master", R.string.onb_sample_ach1_title, R.string.stats_ach_math_master_desc, "🧮", maxProgress = 25),
            Achievement("word_wizard", R.string.stats_ach_word_wizard_title, R.string.stats_ach_word_wizard_desc, "📚", maxProgress = 25),
            Achievement("level_5", R.string.stats_ach_level_5_title, R.string.stats_ach_level_5_desc, "⭐", maxProgress = 5),
            Achievement("level_10", R.string.common_difficulty_expert, R.string.stats_ach_level_10_desc, "👑", maxProgress = 10),
            Achievement("level_20", R.string.xa_difficulty_master, R.string.stats_ach_level_20_desc, "💎", maxProgress = 20),
            Achievement("hundred_points", R.string.stats_ach_hundred_points_title, R.string.stats_ach_hundred_points_desc, "💯"),
            Achievement("perfect_week", R.string.stats_ach_perfect_week_title, R.string.stats_ach_perfect_week_desc, "📅", maxProgress = 7),
            Achievement("daily_streak_30", R.string.stats_ach_daily_streak_30_title, R.string.stats_ach_daily_streak_30_desc, "🗓️", maxProgress = 30)
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
        // Snapshot before recording today's play; afterwards hasPlayedToday is always true
        val dailyBefore = userStatsManager.getDailyActivityData()

        // Record the completion in data layer
        userStatsManager.recordPuzzleCompletion(puzzleType, score, timeSpentSeconds, isCorrect, difficulty)
        userStatsManager.recordDailyActivity()
        userStatsManager.recordWeeklyActivity()

        // Calculate progression results
        val scoreBreakdown = if (isCorrect) {
            ProgressionMath.scoreBreakdown(puzzleType, score, timeRemaining, totalTime, difficulty, getCurrentStreak() + 1)
        } else {
            ScoreBreakdown(0, 0, 0, 1.0f, 0, 0)
        }

        // Update streaks
        val newStreak = updateQuestionStreak(isCorrect)
        val newDailyStreak = updateDailyStreak(dailyBefore)

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
            streakMultiplier = ProgressionMath.streakMultiplier(newStreak),
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

    // ================================
    // LEVEL PROGRESSION
    // ================================

    /**
     * Get current user level based on total XP
     */
    fun getCurrentLevel(): UserLevel {
        val globalStats = userStatsManager.getGlobalStats()
        val totalXP = globalStats.totalXP

        val level = ProgressionMath.levelFromXp(totalXP)
        val currentXP = totalXP - ProgressionMath.xpAtStartOfLevel(level)
        val xpToNext = ProgressionMath.xpRequiredForLevel(level)

        return UserLevel(
            level = level,
            currentXP = currentXP,
            xpToNextLevel = xpToNext,
            totalXP = totalXP
        )
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

        val currentTierIndex = ProgressionMath.tierIndexForXp(totalXP)
        val currentTier = ProgressionMath.TIER_THRESHOLDS[currentTierIndex].first
        val nextTierIndex = (currentTierIndex + 1).coerceAtMost(ProgressionMath.TIER_THRESHOLDS.size - 1)
        val nextTier = ProgressionMath.TIER_THRESHOLDS[nextTierIndex].first

        val pointsInTier = totalXP - ProgressionMath.TIER_THRESHOLDS[currentTierIndex].second
        val pointsToNextTier = ProgressionMath.TIER_THRESHOLDS[nextTierIndex].second - ProgressionMath.TIER_THRESHOLDS[currentTierIndex].second
        val progress = if (pointsToNextTier > 0) pointsInTier.toFloat() / pointsToNextTier else 1f

        val rewards = when (nextTier) {
            "Silver" -> listOf(R.string.stats_reward_silver_badge, R.string.stats_reward_theme_pack)
            "Gold" -> listOf(R.string.stats_reward_gold_badge, R.string.stats_reward_crown_icon)
            "Platinum" -> listOf(R.string.stats_reward_platinum_badge, R.string.stats_reward_exclusive_themes)
            "Diamond" -> listOf(R.string.stats_reward_diamond_badge, R.string.stats_reward_custom_avatar)
            "Master" -> listOf(R.string.stats_reward_master_crown, R.string.stats_reward_ultimate_rewards)
            else -> listOf(R.string.stats_reward_special)
        }

        val shownNextTier = if (currentTierIndex == ProgressionMath.TIER_THRESHOLDS.size - 1) currentTier else nextTier
        return TierInfo(
            currentTier = currentTier,
            nextTier = shownNextTier,
            currentTierRes = tierNameRes(currentTier),
            nextTierRes = tierNameRes(shownNextTier),
            pointsInTier = pointsInTier,
            pointsToNextTier = pointsToNextTier,
            progressPercentage = progress,
            nextTierRewards = rewards,
            currentLevel = ProgressionMath.levelFromXp(totalXP),
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
    private fun updateDailyStreak(before: DailyActivityData): Int {
        val newDailyStreak = ProgressionMath.nextDailyStreak(
            hasEverPlayed = before.lastActiveDate.isNotEmpty(),
            hasPlayedToday = before.hasPlayedToday,
            hasPlayedYesterday = before.hasPlayedYesterday,
            currentStreak = getCurrentDailyStreak()
        )

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
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: String,
    val isUnlocked: Boolean = false,
    val progress: Int = 0,
    val maxProgress: Int = 1
) {
    val progressPercentage: Float
        get() = if (maxProgress > 0) progress.toFloat() / maxProgress.toFloat() else 0f
}

data class TierInfo(
    /** Persisted/compared tier id ("Bronze", "Silver", ...); never shown. Show [currentTierRes] / [nextTierRes]. */
    val currentTier: String,
    val nextTier: String,
    @StringRes val currentTierRes: Int,
    @StringRes val nextTierRes: Int,
    val pointsInTier: Int,
    val pointsToNextTier: Int,
    val progressPercentage: Float,
    /** String resource ids of the reward labels. */
    val nextTierRewards: List<Int>,
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
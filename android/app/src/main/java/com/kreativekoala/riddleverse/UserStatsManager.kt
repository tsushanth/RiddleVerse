package com.kreativekoala.riddleverse

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pure Data Storage Layer - Only handles data persistence and retrieval
 * Does NOT handle: XP calculations, UI feedback, sound effects, progression logic
 */
class UserStatsManager private constructor(val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: UserStatsManager? = null

        fun getInstance(context: Context): UserStatsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserStatsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val sharedPrefs = context.getSharedPreferences("user_puzzle_stats", Context.MODE_PRIVATE)
    private val globalPrefs = context.getSharedPreferences("user_global_stats", Context.MODE_PRIVATE)
    private val dailyCompletionPrefs = context.getSharedPreferences("daily_completions", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Get user ID for data separation
    private fun getUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: "guest_user"
    }

    // ================================
    // PUZZLE-SPECIFIC STATISTICS
    // ================================

    /**
     * Record a puzzle completion - Pure data storage
     */
    fun recordPuzzleCompletion(
        puzzleType: String,
        score: Int,
        timeSpentSeconds: Int,
        isWin: Boolean,
        difficulty: String = "Medium"
    ) {
        val userId = getUserId()
        val key = "${userId}_${puzzleType}"

        // Get existing stats or create new
        val existingStats = getPuzzleStats(puzzleType)

        // Update stats
        val updatedStats = existingStats.copy(
            totalPlays = existingStats.totalPlays + 1,
            wins = if (isWin) existingStats.wins + 1 else existingStats.wins,
            highScore = maxOf(existingStats.highScore, score),
            totalScore = existingStats.totalScore + score,
            totalTimeSpentSeconds = existingStats.totalTimeSpentSeconds + timeSpentSeconds,
            lastPlayedTimestamp = System.currentTimeMillis()
        )

        // Add to top scores (keep top 10, exclude 0 scores from incorrect answers)
        val newTopScores = (updatedStats.topScores.map { (it as? Number)?.toInt() ?: it } + score)
            .filterIsInstance<Int>()
            .filter { it > 0 }
            .sortedDescending()
            .take(10)

        val finalStats = updatedStats.copy(topScores = newTopScores)

        // Save to SharedPreferences
        sharedPrefs.edit()
            .putString(key, gson.toJson(finalStats))
            .apply()

        Log.d("UserStatsManager", "📊 Recorded completion for $puzzleType: isWin=$isWin, score=$score")
    }

    /**
     * Get statistics for a specific puzzle type
     */
    fun getPuzzleStats(puzzleType: String): UserPuzzleStats {
        val userId = getUserId()
        val key = "${userId}_${puzzleType}"

        val jsonString = sharedPrefs.getString(key, null)
        return if (jsonString != null) {
            try {
                gson.fromJson(jsonString, UserPuzzleStats::class.java)
            } catch (e: Exception) {
                Log.e("UserStatsManager", "Failed to parse stats for $puzzleType", e)
                UserPuzzleStats()
            }
        } else {
            UserPuzzleStats()
        }
    }

    /**
     * Get all puzzle type statistics
     */
    fun getAllUserStats(): Map<String, UserPuzzleStats> {
        val userId = getUserId()
        val allStats = mutableMapOf<String, UserPuzzleStats>()

        val allKeys = sharedPrefs.all.keys.filter { it.startsWith("${userId}_") }

        allKeys.forEach { key ->
            val puzzleType = key.removePrefix("${userId}_")
            val jsonString = sharedPrefs.getString(key, null)
            if (jsonString != null) {
                try {
                    val stats = gson.fromJson(jsonString, UserPuzzleStats::class.java)
                    allStats[puzzleType] = stats
                } catch (e: Exception) {
                    Log.e("UserStatsManager", "Failed to parse stats for $puzzleType", e)
                }
            }
        }

        return allStats
    }

    // ================================
    // GLOBAL USER STATISTICS
    // ================================

    /**
     * Record session-level data (called after puzzle completion)
     */
    fun recordSessionData(
        totalXPGained: Int,
        timePlayedSeconds: Int
    ) {
        val userId = getUserId()
        val globalStats = getGlobalStats()

        val updatedGlobalStats = globalStats.copy(
            totalXP = globalStats.totalXP + totalXPGained,
            totalGamesPlayed = globalStats.totalGamesPlayed + 1,
            totalTimePlayedSeconds = globalStats.totalTimePlayedSeconds + timePlayedSeconds,
            lastActiveTimestamp = System.currentTimeMillis()
        )

        globalPrefs.edit()
            .putString("${userId}_global", gson.toJson(updatedGlobalStats))
            .apply()

        Log.d("UserStatsManager", "📈 Recorded session: +${totalXPGained}XP, ${timePlayedSeconds}s")
    }

    /**
     * Get global user statistics
     */
    fun getGlobalStats(): GlobalUserStats {
        val userId = getUserId()
        val jsonString = globalPrefs.getString("${userId}_global", null)

        return if (jsonString != null) {
            try {
                gson.fromJson(jsonString, GlobalUserStats::class.java)
            } catch (e: Exception) {
                Log.e("UserStatsManager", "Failed to parse global stats", e)
                GlobalUserStats()
            }
        } else {
            GlobalUserStats()
        }
    }

    // ================================
    // DAILY TRACKING
    // ================================

    /**
     * Record daily play activity
     */
    fun recordDailyActivity() {
        val userId = getUserId()
        val today = getCurrentDateString()

        // Mark today as played
        val dailyKey = "${userId}_daily_${today}"
        val currentCount = globalPrefs.getInt(dailyKey, 0)
        globalPrefs.edit().putInt(dailyKey, currentCount + 1).apply()

        // Update last active date
        globalPrefs.edit()
            .putString("${userId}_last_active_date", today)
            .apply()

        Log.d("UserStatsManager", "📅 Recorded daily activity for $today")
    }

    /**
     * Get daily activity data
     */
    fun getDailyActivityData(): DailyActivityData {
        val userId = getUserId()
        val today = getCurrentDateString()
        val yesterday = getYesterdayDateString()

        val todayCount = globalPrefs.getInt("${userId}_daily_${today}", 0)
        val yesterdayCount = globalPrefs.getInt("${userId}_daily_${yesterday}", 0)
        val lastActiveDate = globalPrefs.getString("${userId}_last_active_date", "")

        return DailyActivityData(
            todayCount = todayCount,
            yesterdayCount = yesterdayCount,
            lastActiveDate = lastActiveDate ?: "",
            hasPlayedToday = todayCount > 0,
            hasPlayedYesterday = yesterdayCount > 0
        )
    }

    // ================================
    // WEEKLY TRACKING
    // ================================

    /**
     * Record weekly play activity
     */
    fun recordWeeklyActivity() {
        val userId = getUserId()
        val currentWeek = getCurrentWeekString()

        val weeklyKey = "${userId}_weekly_${currentWeek}"
        val currentCount = globalPrefs.getInt(weeklyKey, 0)
        globalPrefs.edit().putInt(weeklyKey, currentCount + 1).apply()

        Log.d("UserStatsManager", "📊 Recorded weekly activity for $currentWeek")
    }

    /**
     * Get weekly activity data
     */
    fun getWeeklyActivityData(): WeeklyActivityData {
        val userId = getUserId()
        val currentWeek = getCurrentWeekString()
        val weeklyProgress = globalPrefs.getInt("${userId}_weekly_${currentWeek}", 0)

        // Calculate time until week reset (Sunday)
        val calendar = Calendar.getInstance()
        val daysUntilSunday = (Calendar.SUNDAY - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
        val hoursUntilSunday = 24 - calendar.get(Calendar.HOUR_OF_DAY)
        val timeUntilReset = (daysUntilSunday * 24 * 60 * 60 + hoursUntilSunday * 60 * 60) * 1000L

        return WeeklyActivityData(
            currentWeek = currentWeek,
            weeklyProgress = weeklyProgress,
            timeUntilReset = timeUntilReset
        )
    }

    // ================================
    // ACHIEVEMENT PERSISTENCE
    // ================================

    /**
     * Mark achievement as unlocked
     */
    fun unlockAchievement(achievementId: String) {
        val userId = getUserId()
        globalPrefs.edit()
            .putBoolean("${userId}_achievement_${achievementId}", true)
            .apply()

        Log.d("UserStatsManager", "🏆 Unlocked achievement: $achievementId")
    }

    /**
     * Check if achievement is unlocked
     */
    fun isAchievementUnlocked(achievementId: String): Boolean {
        val userId = getUserId()
        return globalPrefs.getBoolean("${userId}_achievement_${achievementId}", false)
    }

    /**
     * Get achievement progress value
     */
    fun getAchievementProgress(achievementId: String): Int {
        val userId = getUserId()
        return globalPrefs.getInt("${userId}_achievement_progress_${achievementId}", 0)
    }

    /**
     * Set achievement progress value
     */
    fun setAchievementProgress(achievementId: String, progress: Int) {
        val userId = getUserId()
        globalPrefs.edit()
            .putInt("${userId}_achievement_progress_${achievementId}", progress)
            .apply()
    }

    // ================================
    // UTILITY METHODS
    // ================================

    /**
     * Clear all user data (for testing/reset)
     */
    fun clearAllData() {
        val userId = getUserId()
        val puzzleEditor = sharedPrefs.edit()
        val globalEditor = globalPrefs.edit()

        // Clear puzzle stats
        sharedPrefs.all.keys
            .filter { it.startsWith("${userId}_") }
            .forEach { puzzleEditor.remove(it) }

        // Clear global stats
        globalPrefs.all.keys
            .filter { it.startsWith("${userId}_") }
            .forEach { globalEditor.remove(it) }

        puzzleEditor.apply()
        globalEditor.apply()

        Log.d("UserStatsManager", "🗑️ Cleared all data for user: $userId")
    }

    /**
     * Import stats from external source (migration helper)
     */
    fun importPuzzleStats(puzzleType: String, stats: UserPuzzleStats) {
        val userId = getUserId()
        val key = "${userId}_${puzzleType}"

        sharedPrefs.edit()
            .putString(key, gson.toJson(stats))
            .apply()

        Log.d("UserStatsManager", "📥 Imported stats for $puzzleType")
    }

    // ================================
    // PRIVATE HELPER METHODS
    // ================================

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun getYesterdayDateString(): String {
        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yesterday.time)
    }

    private fun getCurrentWeekString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-W${calendar.get(Calendar.WEEK_OF_YEAR)}"
    }

    private fun getUserEmail(): String {
        return FirebaseAuth.getInstance().currentUser?.email ?: "guest_user"
    }

    fun getTodayCompletionCount(): Int {
        val userEmail = getUserEmail()
        val userId = getUserId()
        val today = getCurrentDateString()

        // Check multiple sources for today's count
        val source1 = dailyCompletionPrefs.getInt("${userEmail}_${today}_completions", 0)
        val source2 = globalPrefs.getInt("${userId}_daily_${today}", 0)
        val source3 = getDailyActivityData().todayCount

        // Return the maximum value to ensure accuracy
        val maxCount = maxOf(source1, source2, source3)

        Log.d("UserStatsManager", "📊 Today's completion count: source1=$source1, source2=$source2, source3=$source3, using=$maxCount")

        return maxCount
    }


}

// ================================
// DATA CLASSES - Pure Data Objects
// ================================

/**
 * Statistics for individual puzzle types
 */
data class UserPuzzleStats(
    val totalPlays: Int = 0,
    val wins: Int = 0,
    val highScore: Int = 0,
    val totalScore: Int = 0,
    val topScores: List<Int> = emptyList(),
    val totalTimeSpentSeconds: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
    val currentStreak: Int = 0,
) {
    // Computed properties
    val averageScore: Int get() = if (totalPlays > 0) totalScore / totalPlays else 0
    val winRate: Float get() = if (totalPlays > 0) wins.toFloat() / totalPlays.toFloat() else 0f
    val totalTimeSpentHours: Float get() = totalTimeSpentSeconds / 3600f
}

/**
 * Global user statistics across all puzzle types
 */
data class GlobalUserStats(
    val totalXP: Int = 0,
    val totalGamesPlayed: Int = 0,
    val totalTimePlayedSeconds: Int = 0,
    val lastActiveTimestamp: Long = 0L,
    val joinDate: Long = System.currentTimeMillis()
) {
    val totalTimePlayedHours: Float get() = totalTimePlayedSeconds / 3600f
    val averageXPPerGame: Int get() = if (totalGamesPlayed > 0) totalXP / totalGamesPlayed else 0
}

/**
 * Daily activity tracking data
 */
data class DailyActivityData(
    val todayCount: Int,
    val yesterdayCount: Int,
    val lastActiveDate: String,
    val hasPlayedToday: Boolean,
    val hasPlayedYesterday: Boolean
)

/**
 * Weekly activity tracking data
 */
data class WeeklyActivityData(
    val currentWeek: String,
    val weeklyProgress: Int,
    val timeUntilReset: Long
)
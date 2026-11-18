package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import androidx.annotation.AnyRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dashboard Data Layer - Prepares and formats data for dashboard display
 * Does NOT handle: Raw data storage, game logic, UI rendering
 * Does handle: Data aggregation, formatting for display, real-time refresh coordination
 */
class DashboardDataProvider(
    private val context: Context,
    private val userStatsManager: UserStatsManager,
    private val progressionEngine: ProgressionEngine
) {

    /** Strings come from the app language (the player's in-app choice), not just the device language. */
    private val res: Context get() = DailyReminder.localized(context)

    private fun str(@StringRes id: Int, vararg args: Any): String = res.getString(id, *args)

    private fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        res.resources.getQuantityString(id, quantity, *args)

    // ================================
    // MAIN DASHBOARD DATA
    // ================================

    /**
     * Get comprehensive dashboard data - Main entry point
     */
    fun getDashboardData(): DashboardData {
        Log.d("DashboardDataProvider", "📊 Preparing dashboard data")

        return DashboardData(
            tierInfo = progressionEngine.getTierInfo(),
            levelInfo = progressionEngine.getCurrentLevel(),
            streakInfo = getStreakDisplayInfo(),
            weeklyChallenge = progressionEngine.getWeeklyChallengeInfo(),
            achievements = progressionEngine.getAllAchievements(),
            puzzlePerformance = getPuzzlePerformanceData(),
            timeAnalytics = getTimeAnalyticsData(),
            seasonalEvent = getCurrentSeasonalEvent(),
            dailyStats = getDailyStatsData(),
            globalStats = getGlobalStatsData()
        )
    }

    // ================================
    // STREAK INFORMATION
    // ================================

    /**
     * Get formatted streak information for display
     */
    private fun getStreakDisplayInfo(): StreakDisplayInfo {
        val questionStreak = progressionEngine.getCurrentStreak()
        val bestStreak = progressionEngine.getBestStreak()
        val dailyStreak = progressionEngine.getCurrentDailyStreak()
        val dailyData = userStatsManager.getDailyActivityData()

        return StreakDisplayInfo(
            currentQuestionStreak = questionStreak,
            bestQuestionStreak = bestStreak,
            currentDailyStreak = dailyStreak,
            longestDailyStreak = getBestDailyStreakFromPrefs(),
            hasPlayedToday = dailyData.hasPlayedToday,
            streakActive = questionStreak > 0 || dailyStreak > 0,
            questionStreakMultiplier = getQuestionStreakMultiplier(questionStreak),
            dailyStreakBonus = getDailyStreakBonus(dailyStreak),
            streakMessage = generateStreakMessage(questionStreak, dailyStreak, dailyData.hasPlayedToday)
        )
    }

    /**
     * Get best daily streak from preferences
     */
    private fun getBestDailyStreakFromPrefs(): Int {
        val prefs = context.getSharedPreferences("progression_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("best_daily_streak", 0)
    }


    /**
     * Get question streak multiplier
     */
    private fun getQuestionStreakMultiplier(streak: Int): Float {
        return when {
            streak >= 10 -> 2.0f
            streak >= 5 -> 1.5f
            streak >= 3 -> 1.25f
            else -> 1.0f
        }
    }

    /**
     * Get daily streak bonus multiplier
     */
    private fun getDailyStreakBonus(dailyStreak: Int): Float {
        return when {
            dailyStreak >= 30 -> 2.0f
            dailyStreak >= 14 -> 1.75f
            dailyStreak >= 7 -> 1.5f
            dailyStreak >= 3 -> 1.25f
            else -> 1.0f
        }
    }

    /**
     * Generate motivational streak message
     */
    private fun generateStreakMessage(questionStreak: Int, dailyStreak: Int, hasPlayedToday: Boolean): String {
        return when {
            dailyStreak >= 30 -> plural(R.plurals.stats_streak_msg_incredible, dailyStreak, dailyStreak)
            dailyStreak >= 7 -> plural(R.plurals.stats_streak_msg_amazing, dailyStreak, dailyStreak)
            questionStreak >= 10 -> str(R.string.stats_streak_msg_unstoppable, questionStreak)
            questionStreak >= 3 -> plural(R.plurals.stats_streak_msg_on_fire, questionStreak, questionStreak)
            hasPlayedToday -> str(R.string.stats_streak_msg_great_job)
            dailyStreak > 0 -> plural(R.plurals.stats_streak_msg_keep_alive, dailyStreak, dailyStreak)
            else -> str(R.string.stats_streak_msg_start)
        }
    }

    // ================================
    // PUZZLE PERFORMANCE DATA
    // ================================

    /**
     * Get formatted puzzle performance data
     */
    private fun getPuzzlePerformanceData(): List<PuzzlePerformanceInfo> {
        val allStats = userStatsManager.getAllUserStats()

        return allStats.map { (puzzleType, stats) ->
            val averageTime = if (stats.totalPlays > 0) {
                stats.totalTimeSpentSeconds.toFloat() / stats.totalPlays
            } else 0f

            PuzzlePerformanceInfo(
                puzzleType = formatPuzzleTypeName(puzzleType),
                totalSolved = stats.wins,
                totalPlayed = stats.totalPlays,
                accuracy = stats.winRate,
                averageTime = averageTime,
                highScore = stats.highScore,
                totalTime = stats.totalTimeSpentHours,
                recentActivity = formatLastPlayed(stats.lastPlayedTimestamp),
                progressStatus = calculateProgressStatus(stats),
                difficultyRecommendation = recommendDifficulty(stats),
                improvementTip = generateImprovementTip(puzzleType, stats)
            )
        }.sortedByDescending { it.totalSolved }
    }

    /**
     * Format puzzle type name for display
     */
    private fun formatPuzzleTypeName(puzzleType: String): String {
        return PuzzleTypeNames.display(res, puzzleType)
    }

    /**
     * Calculate progress status for puzzle type
     */
    private fun calculateProgressStatus(stats: UserPuzzleStats): String {
        return when {
            stats.totalPlays == 0 -> str(R.string.stats_status_not_started)
            stats.winRate >= 0.9f -> str(R.string.stats_status_mastered)
            stats.winRate >= 0.75f -> str(R.string.common_difficulty_advanced)
            stats.winRate >= 0.5f -> str(R.string.stats_status_improving)
            else -> str(R.string.stats_status_learning)
        }
    }

    /**
     * Recommend difficulty based on performance
     */
    private fun recommendDifficulty(stats: UserPuzzleStats): String {
        return when {
            stats.winRate >= 0.85f && stats.totalPlays >= 10 -> str(R.string.riddle_tier_hard)
            stats.winRate >= 0.7f && stats.totalPlays >= 5 -> str(R.string.riddle_tier_medium)
            stats.winRate < 0.6f -> str(R.string.riddle_tier_easy)
            else -> str(R.string.riddle_tier_medium)
        }
    }

    /**
     * Generate improvement tip based on performance
     */
    private fun generateImprovementTip(puzzleType: String, stats: UserPuzzleStats): String {
        return when {
            stats.totalPlays == 0 -> str(R.string.stats_tip_first_puzzle, formatPuzzleTypeName(puzzleType))
            stats.winRate < 0.5f -> str(R.string.stats_tip_try_easier)
            stats.winRate >= 0.85f -> str(R.string.stats_tip_excelling)
            else -> str(R.string.stats_tip_keep_practicing)
        }
    }

    // ================================
    // TIME ANALYTICS DATA
    // ================================

    /**
     * Get time-based analytics for dashboard
     */
    private fun getTimeAnalyticsData(): TimeAnalyticsInfo {
        val globalStats = userStatsManager.getGlobalStats()
        val allStats = userStatsManager.getAllUserStats()

        val averageSessionTime = if (globalStats.totalGamesPlayed > 0) {
            globalStats.totalTimePlayedSeconds.toFloat() / globalStats.totalGamesPlayed
        } else 0f

        val fastestPuzzleType = allStats.minByOrNull { (_, stats) ->
            if (stats.totalPlays > 0) stats.totalTimeSpentSeconds.toFloat() / stats.totalPlays else Float.MAX_VALUE
        }

        val mostPlayedToday = getTodaysMostPlayedPuzzle()
        val productiveHours = getProductiveHours()

        return TimeAnalyticsInfo(
            totalHours = globalStats.totalTimePlayedHours,
            averageSessionTime = averageSessionTime,
            totalSessions = globalStats.totalGamesPlayed,
            fastestPuzzleType = fastestPuzzleType?.let { formatPuzzleTypeName(it.key) } ?: str(R.string.stats_none),
            mostProductiveHour = productiveHours.first,
            dailyAverageMinutes = calculateDailyAverageMinutes(),
            timeDistribution = getTimeDistribution(),
            efficiency = calculateEfficiency(),
            timeMessage = generateTimeMessage(globalStats.totalTimePlayedHours)
        )
    }

    /**
     * Get today's most played puzzle type
     */
    private fun getTodaysMostPlayedPuzzle(): String {
        // This would need more detailed daily tracking to implement fully
        // For now, return the overall most played
        val allStats = userStatsManager.getAllUserStats()
        val mostPlayed = allStats.maxByOrNull { it.value.totalPlays }
        return mostPlayed?.let { formatPuzzleTypeName(it.key) } ?: str(R.string.stats_none)
    }

    /**
     * Get most productive hours (simplified)
     */
    private fun getProductiveHours(): Pair<String, String> {
        // This would need hourly tracking to implement fully
        // For now, return generic productive hours
        return Pair(str(R.string.stats_productive_hours), str(R.string.stats_most_active_time))
    }

    /**
     * Calculate daily average minutes
     */
    private fun calculateDailyAverageMinutes(): Float {
        val globalStats = userStatsManager.getGlobalStats()
        val daysSinceJoin = ((System.currentTimeMillis() - globalStats.joinDate) / (24 * 60 * 60 * 1000f)).coerceAtLeast(1f)
        return (globalStats.totalTimePlayedSeconds / 60f) / daysSinceJoin
    }

    /**
     * Get time distribution across puzzle types
     */
    private fun getTimeDistribution(): Map<String, Float> {
        val allStats = userStatsManager.getAllUserStats()
        val totalTime = allStats.values.sumOf { it.totalTimeSpentSeconds }

        return if (totalTime > 0) {
            allStats.mapKeys { formatPuzzleTypeName(it.key) }
                .mapValues { (it.value.totalTimeSpentSeconds.toFloat() / totalTime) * 100f }
        } else {
            emptyMap()
        }
    }

    /**
     * Calculate efficiency score
     */
    private fun calculateEfficiency(): Float {
        val globalStats = userStatsManager.getGlobalStats()
        val allStats = userStatsManager.getAllUserStats()

        if (allStats.isEmpty() || globalStats.totalTimePlayedSeconds == 0) return 0f

        val avgWinRate = allStats.values.map { it.winRate }.average().toFloat()
        val avgXPPerMinute = (globalStats.totalXP.toFloat() / (globalStats.totalTimePlayedSeconds / 60f)).coerceAtMost(100f)

        return ((avgWinRate * 50f) + (avgXPPerMinute * 0.5f)).coerceAtMost(100f)
    }

    /**
     * Generate time-based message
     */
    private fun generateTimeMessage(totalHours: Float): String {
        val days = (totalHours / 24).toInt()
        val hours = (totalHours % 24).toInt()

        return when {
            totalHours >= 100 -> plural(R.plurals.stats_time_msg_over_days, days, days)
            totalHours >= 24 -> plural(R.plurals.stats_time_msg_days_and_hours, days, days, hours)
            totalHours >= 10 -> plural(R.plurals.stats_time_msg_puzzle_solving, totalHours.toInt(), totalHours.toInt())
            totalHours >= 1 -> plural(R.plurals.stats_time_msg_cognitive, totalHours.toInt(), totalHours.toInt())
            else -> str(R.string.stats_time_msg_start)
        }
    }

    // ================================
    // SEASONAL EVENTS
    // ================================

    /**
     * Get current seasonal event if any is active
     */
    private fun getCurrentSeasonalEvent(): SeasonalEventInfo? {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)

        return when (currentMonth) {
            Calendar.OCTOBER -> SeasonalEventInfo(
                eventName = str(R.string.stats_event_halloween_name),
                description = str(R.string.stats_event_halloween_desc),
                progress = getSeasonalProgress("halloween"),
                maxProgress = 31,
                endTime = getEndOfMonth(Calendar.OCTOBER),
                theme = "🎃",
                rewards = listOf(str(R.string.stats_event_halloween_badge), str(R.string.stats_event_halloween_theme), str(R.string.stats_event_halloween_bonus))
            )
            Calendar.DECEMBER -> SeasonalEventInfo(
                eventName = str(R.string.stats_event_winter_name),
                description = str(R.string.stats_event_winter_desc),
                progress = getSeasonalProgress("winter"),
                maxProgress = 25,
                endTime = getEndOfMonth(Calendar.DECEMBER),
                theme = "❄️",
                rewards = listOf(str(R.string.stats_event_winter_badge), str(R.string.stats_event_winter_theme), str(R.string.stats_event_winter_effects))
            )
            Calendar.FEBRUARY -> SeasonalEventInfo(
                eventName = str(R.string.stats_event_valentine_name),
                description = str(R.string.stats_event_valentine_desc),
                progress = getSeasonalProgress("valentine"),
                maxProgress = 14,
                endTime = getEndOfMonth(Calendar.FEBRUARY),
                theme = "💕",
                rewards = listOf(str(R.string.stats_event_valentine_badge), str(R.string.stats_event_valentine_theme), str(R.string.stats_event_valentine_double_xp))
            )
            else -> null
        }
    }

    /**
     * Get seasonal event progress
     */
    private fun getSeasonalProgress(eventType: String): Int {
        val prefs = context.getSharedPreferences("user_global_stats", Context.MODE_PRIVATE)
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest_user"
        return prefs.getInt("${userId}_seasonal_${eventType}", 0)
    }

    /**
     * Get end of month timestamp
     */
    private fun getEndOfMonth(month: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        return calendar.timeInMillis
    }

    // ================================
    // DAILY STATISTICS
    // ================================

    /**
     * Get daily statistics for dashboard
     */
    private fun getDailyStatsData(): DailyStatsInfo {
        val dailyData = userStatsManager.getDailyActivityData()
        val weeklyData = userStatsManager.getWeeklyActivityData()

        return DailyStatsInfo(
            todayCount = dailyData.todayCount,
            yesterdayCount = dailyData.yesterdayCount,
            weeklyProgress = weeklyData.weeklyProgress,
            hasPlayedToday = dailyData.hasPlayedToday,
            dailyGoal = 5, // Can be made configurable
            weeklyGoal = 25,
            dailyStreak = progressionEngine.getCurrentDailyStreak(),
            motivationMessage = generateDailyMotivation(dailyData, weeklyData)
        )
    }

    /**
     * Generate daily motivation message
     */
    private fun generateDailyMotivation(
        dailyData: DailyActivityData,
        weeklyData: WeeklyActivityData
    ): String {
        return when {
            !dailyData.hasPlayedToday -> str(R.string.stats_motivation_start)
            dailyData.todayCount >= 10 -> plural(R.plurals.stats_motivation_amazing, dailyData.todayCount, dailyData.todayCount)
            dailyData.todayCount >= 5 -> plural(R.plurals.stats_motivation_great_work, dailyData.todayCount, dailyData.todayCount)
            weeklyData.weeklyProgress >= 20 -> str(R.string.stats_motivation_almost_weekly)
            else -> str(R.string.stats_motivation_keep_going)
        }
    }

    // ================================
    // GLOBAL STATISTICS FORMATTING
    // ================================

    /**
     * Get formatted global statistics
     */
    private fun getGlobalStatsData(): GlobalStatsInfo {
        val globalStats = userStatsManager.getGlobalStats()
        val levelInfo = progressionEngine.getCurrentLevel()

        return GlobalStatsInfo(
            totalXP = globalStats.totalXP,
            currentLevel = levelInfo.level,
            totalGamesPlayed = globalStats.totalGamesPlayed,
            totalTimeHours = globalStats.totalTimePlayedHours,
            averageXPPerGame = globalStats.averageXPPerGame,
            memberSince = formatJoinDate(globalStats.joinDate),
            nextLevelProgress = levelInfo.progressPercentage,
            xpToNextLevel = levelInfo.xpToNextLevel,
            rank = calculateApproximateRank(globalStats.totalXP),
            lifetimeStats = generateLifetimeStats(globalStats)
        )
    }

    /**
     * Format join date for display
     */
    private fun formatJoinDate(timestamp: Long): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    /**
     * Calculate approximate rank based on XP (simplified)
     */
    private fun calculateApproximateRank(totalXP: Int): String {
        return when {
            totalXP >= 10000 -> str(R.string.stats_rank_top_percent, 1)
            totalXP >= 5000 -> str(R.string.stats_rank_top_percent, 5)
            totalXP >= 2000 -> str(R.string.stats_rank_top_percent, 10)
            totalXP >= 1000 -> str(R.string.stats_rank_top_percent, 25)
            totalXP >= 500 -> str(R.string.stats_rank_top_percent, 50)
            else -> str(R.string.stats_rank_getting_started)
        }
    }

    /**
     * Generate lifetime statistics summary
     */
    private fun generateLifetimeStats(globalStats: GlobalUserStats): List<String> {
        val stats = mutableListOf<String>()

        if (globalStats.totalGamesPlayed >= 100) {
            stats.add(plural(R.plurals.stats_lifetime_games, globalStats.totalGamesPlayed, globalStats.totalGamesPlayed))
        }

        if (globalStats.totalTimePlayedHours >= 10) {
            stats.add(plural(R.plurals.stats_lifetime_hours, globalStats.totalTimePlayedHours.toInt(), globalStats.totalTimePlayedHours.toInt()))
        }

        if (globalStats.totalXP >= 1000) {
            stats.add(str(R.string.stats_lifetime_xp, globalStats.totalXP))
        }

        val daysSinceJoin = ((System.currentTimeMillis() - globalStats.joinDate) / (24 * 60 * 60 * 1000f)).toInt()
        if (daysSinceJoin >= 30) {
            stats.add(plural(R.plurals.stats_lifetime_days, daysSinceJoin, daysSinceJoin))
        }

        return stats.ifEmpty { listOf(str(R.string.stats_lifetime_just_started)) }
    }

    // ================================
    // UTILITY METHODS
    // ================================

    /**
     * Format last played timestamp
     */
    private fun formatLastPlayed(timestamp: Long): String {
        if (timestamp == 0L) return str(R.string.stats_never)

        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> str(R.string.stats_just_now)
            diff < 3_600_000 -> str(R.string.browse_time_minutes_ago, diff / 60_000)
            diff < 86_400_000 -> str(R.string.browse_time_hours_ago, diff / 3_600_000)
            diff < 604_800_000 -> str(R.string.browse_time_days_ago, diff / 86_400_000)
            else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
        }
    }

    /**
     * Refresh all dashboard data
     */
    fun refreshData(): DashboardData {
        Log.d("DashboardDataProvider", "🔄 Refreshing dashboard data")
        return getDashboardData()
    }
}

data class DashboardData(
    val tierInfo: TierInfo,                    // Current tier progress (Bronze, Silver, etc.)
    val levelInfo: UserLevel,                  // Level and XP information
    val streakInfo: StreakDisplayInfo,         // Question and daily streaks
    val weeklyChallenge: WeeklyChallengeInfo,  // Weekly progress
    val achievements: List<Achievement>,        // All achievements with progress
    val puzzlePerformance: List<PuzzlePerformanceInfo>, // Performance per puzzle type
    val timeAnalytics: TimeAnalyticsInfo,      // Time-based analytics
    val seasonalEvent: SeasonalEventInfo?,     // Current seasonal event (if any)
    val dailyStats: DailyStatsInfo,           // Daily activity stats
    val globalStats: GlobalStatsInfo          // Overall user statistics
)

// ================================
// DASHBOARD DATA CLASSES
// ================================

/**
 * Enhanced streak information for display
 */
data class StreakDisplayInfo(
    val currentQuestionStreak: Int,
    val bestQuestionStreak: Int,
    val currentDailyStreak: Int,
    val longestDailyStreak: Int,
    val hasPlayedToday: Boolean,
    val streakActive: Boolean,
    val questionStreakMultiplier: Float,
    val dailyStreakBonus: Float,
    val streakMessage: String
)

/**
 * Puzzle performance information for display
 */
data class PuzzlePerformanceInfo(
    val puzzleType: String,
    val totalSolved: Int,
    val totalPlayed: Int,
    val accuracy: Float,
    val averageTime: Float,
    val highScore: Int,
    val totalTime: Float,
    val recentActivity: String,
    val progressStatus: String,
    val difficultyRecommendation: String,
    val improvementTip: String
)

/**
 * Time analytics information
 */
data class TimeAnalyticsInfo(
    val totalHours: Float,
    val averageSessionTime: Float,
    val totalSessions: Int,
    val fastestPuzzleType: String,
    val mostProductiveHour: String,
    val dailyAverageMinutes: Float,
    val timeDistribution: Map<String, Float>,
    val efficiency: Float,
    val timeMessage: String
)

/**
 * Seasonal event information
 */
data class SeasonalEventInfo(
    val eventName: String,
    val description: String,
    val progress: Int,
    val maxProgress: Int,
    val endTime: Long,
    val theme: String,
    val rewards: List<String>
) {
    val isCompleted: Boolean get() = progress >= maxProgress
    val progressPercentage: Float get() = if (maxProgress > 0) progress.toFloat() / maxProgress else 0f
}

/**
 * Daily statistics information
 */
data class DailyStatsInfo(
    val todayCount: Int,
    val yesterdayCount: Int,
    val weeklyProgress: Int,
    val hasPlayedToday: Boolean,
    val dailyGoal: Int,
    val weeklyGoal: Int,
    val dailyStreak: Int,
    val motivationMessage: String
)

/**
 * Global statistics information for display
 */
data class GlobalStatsInfo(
    val totalXP: Int,
    val currentLevel: Int,
    val totalGamesPlayed: Int,
    val totalTimeHours: Float,
    val averageXPPerGame: Int,
    val memberSince: String,
    val nextLevelProgress: Float,
    val xpToNextLevel: Int,
    val rank: String,
    val lifetimeStats: List<String>
)

/**
 * A UI string carried as a resource id plus arguments, so logic classes stay free of a Context and the UI layer
 * resolves it in the player's current language. When [quantity] is set, [res] is a plurals resource.
 */
data class StatsText(
    @AnyRes val res: Int,
    val args: List<Any> = emptyList(),
    val quantity: Int? = null
) {
    fun resolve(context: Context): String {
        val a = args.toTypedArray()
        return if (quantity != null) context.resources.getQuantityString(res, quantity, *a)
        else context.getString(res, *a)
    }
}

/** Resolves a [StatsText] (or an empty string for null) in the current composition's language. */
@Composable
fun StatsText?.asString(): String {
    val context = LocalContext.current
    return this?.resolve(context) ?: ""
}

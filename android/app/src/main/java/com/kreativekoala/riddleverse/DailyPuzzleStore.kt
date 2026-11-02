package com.kreativekoala.riddleverse

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// --- daily puzzle (feat/daily-puzzle-share) --- local-only persistence (no account, no network).
// The rules live in DailyStreakRules / DailyPuzzleSchedule; this class only reads and writes SharedPreferences.

/** A saved, solved daily. */
data class DailySolveResult(
    val day: Long,
    val type: String,
    val seconds: Int,
    val attempts: Int,
    val streak: Int,
)

/** Returned by [DailyPuzzleStore.recordSolve]. */
data class DailySolveRecord(
    val result: DailySolveResult,
    /** False when this day had already been solved (nothing was awarded this time). */
    val firstSolve: Boolean,
    val reward: StreakReward?,
)

object DailyPuzzleStore {
    private const val PREFS = "daily_puzzle_v1"
    private const val OLD_PREFS = "daily_streak_data" // DailyStreakManager's open-streak file

    private const val K_MIGRATED = "migrated"
    private const val K_CURRENT = "streak_current"
    private const val K_BEST = "streak_best"
    private const val K_LAST = "last_solve_day"
    private const val K_TOTAL = "total_solved"
    private const val K_RES_DAY = "result_day"
    private const val K_RES_TYPE = "result_type"
    private const val K_RES_SECS = "result_seconds"
    private const val K_RES_TRIES = "result_attempts"
    private const val K_RES_STREAK = "result_streak"
    private const val K_FAIL_DAY = "fail_day"
    private const val K_FAIL_COUNT = "fail_count"

    private fun prefs(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun today(): Long = DailyPuzzleSchedule.dayOf(System.currentTimeMillis())

    /** Runs the one-time carry-over of the old open-streak. Safe to call any number of times. */
    @Synchronized
    fun ensureMigrated(c: Context) {
        val p = prefs(c)
        if (p.getBoolean(K_MIGRATED, false)) return
        val old = c.applicationContext.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayLocal = fmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayLocal = fmt.format(cal.time)
        val migrated = DailyStreakRules.migrateOpenStreak(
            openStreak = old.getInt("current_streak", 0),
            openBest = old.getInt("best_streak", 0),
            lastOpenedLocal = old.getString("last_opened_day", "") ?: "",
            todayLocal = todayLocal,
            yesterdayLocal = yesterdayLocal,
            todayUtcDay = today(),
        )
        p.edit().apply {
            putInt(K_CURRENT, migrated.current)
            putInt(K_BEST, migrated.best)
            putLong(K_LAST, migrated.lastSolveDay ?: -1L)
            putInt(K_TOTAL, 0)
            putBoolean(K_MIGRATED, true)
        }.commit()
    }

    fun streakState(c: Context): DailySolveStreak {
        ensureMigrated(c)
        val p = prefs(c)
        val last = p.getLong(K_LAST, -1L)
        return DailySolveStreak(
            current = p.getInt(K_CURRENT, 0),
            best = p.getInt(K_BEST, 0),
            lastSolveDay = if (last < 0) null else last,
            totalSolved = p.getInt(K_TOTAL, 0),
        )
    }

    /** The streak to display right now (0 once a full day has been missed). */
    fun displayStreak(c: Context, day: Long = today()): Int = DailyStreakRules.display(streakState(c), day)

    fun resultFor(c: Context, day: Long): DailySolveResult? {
        val p = prefs(c)
        if (p.getLong(K_RES_DAY, -1L) != day) return null
        return DailySolveResult(
            day = day,
            type = p.getString(K_RES_TYPE, "") ?: "",
            seconds = p.getInt(K_RES_SECS, 0),
            attempts = p.getInt(K_RES_TRIES, 1),
            streak = p.getInt(K_RES_STREAK, 0),
        )
    }

    /** Failed tries so far for [day] (0 if none). */
    fun failedAttempts(c: Context, day: Long): Int {
        val p = prefs(c)
        return if (p.getLong(K_FAIL_DAY, -1L) == day) p.getInt(K_FAIL_COUNT, 0) else 0
    }

    @Synchronized
    fun recordFailure(c: Context, day: Long): Int {
        val n = failedAttempts(c, day) + 1
        prefs(c).edit().putLong(K_FAIL_DAY, day).putInt(K_FAIL_COUNT, n).apply()
        return n
    }

    /**
     * Saves a solve of [day]'s puzzle, extends the streak and pays the streak coins. Idempotent: a second call for
     * the same day returns the saved result with [DailySolveRecord.firstSolve] false and pays nothing.
     */
    @Synchronized
    fun recordSolve(c: Context, day: Long, type: String, seconds: Int): DailySolveRecord {
        resultFor(c, day)?.let { return DailySolveRecord(it, firstSolve = false, reward = null) }
        val outcome = DailyStreakRules.applySolve(streakState(c), day)
        if (!outcome.counted) {
            // Solved a day at or before the last recorded solve (e.g. clock moved back): keep state, report plainly.
            val r = DailySolveResult(day, type, seconds, failedAttempts(c, day) + 1, outcome.streak)
            return DailySolveRecord(r, firstSolve = false, reward = null)
        }
        val attempts = failedAttempts(c, day) + 1
        val result = DailySolveResult(day, type, seconds.coerceAtLeast(0), attempts, outcome.streak)
        val s = outcome.state
        prefs(c).edit().apply {
            putInt(K_CURRENT, s.current)
            putInt(K_BEST, s.best)
            putLong(K_LAST, s.lastSolveDay ?: -1L)
            putInt(K_TOTAL, s.totalSolved)
            putLong(K_RES_DAY, day)
            putString(K_RES_TYPE, type)
            putInt(K_RES_SECS, result.seconds)
            putInt(K_RES_TRIES, attempts)
            putInt(K_RES_STREAK, outcome.streak)
        }.commit()
        // Same reward table and same local coin ledger the open-streak used, now paid only for solving.
        val reward = DailyStreakManager.calculateStreakReward(outcome.streak)
        DailyStreakManager.addCoins(c, reward.coins)
        return DailySolveRecord(result, firstSolve = true, reward = reward)
    }

    fun shareResultOf(c: Context, r: DailySolveResult): DailyShareResult = DailyShareResult(
        number = DailyPuzzleSchedule.numberOf(r.day),
        gameName = PuzzleTypeNames.display(r.type),
        seconds = r.seconds,
        attempts = r.attempts,
        streak = r.streak,
    )
}

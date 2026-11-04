package com.kreativekoala.riddleverse

// --- daily puzzle (feat/daily-puzzle-share) --- pure streak rules. Days are UTC epoch days
// (see DailyPuzzleSchedule), never local dates, so changing time zone cannot add or remove a day.

data class DailySolveStreak(
    val current: Int = 0,
    val best: Int = 0,
    /** UTC epoch day of the most recent solve, or null if the player has never solved (or migrated nothing). */
    val lastSolveDay: Long? = null,
    val totalSolved: Int = 0,
)

data class DailySolveOutcome(
    val state: DailySolveStreak,
    /** The streak length after this solve (what the share card shows). */
    val streak: Int,
    /** False when [day] was already solved: nothing changes and no reward is due. */
    val counted: Boolean,
)

object DailyStreakRules {

    /**
     * The streak to show on [today]. It stays alive through the day after the last solve (you still have today
     * to extend it); once a whole day passes with no solve it reads 0.
     */
    fun display(s: DailySolveStreak, today: Long): Int {
        val last = s.lastSolveDay ?: return 0
        return if (last >= today - 1) s.current else 0
    }

    /** True when the player has solved [today] already. */
    fun solvedOn(s: DailySolveStreak, day: Long): Boolean = (s.lastSolveDay ?: Long.MIN_VALUE) >= day

    /**
     * Records a solve of the puzzle for [day].
     *  - same day twice (or a day at or before the last solve, e.g. the clock moved back): no change, idempotent
     *  - the day after the last solve: streak + 1
     *  - anything else (first ever solve, or a missed day): streak restarts at 1
     * Opening the app, or failing a puzzle, never reaches this function, so neither can extend a streak.
     */
    fun applySolve(s: DailySolveStreak, day: Long): DailySolveOutcome {
        val last = s.lastSolveDay
        if (last != null && day <= last) return DailySolveOutcome(s, streak = s.current, counted = false)
        val next = if (last != null && last == day - 1) s.current + 1 else 1
        val updated = DailySolveStreak(
            current = next,
            best = maxOf(s.best, next),
            lastSolveDay = day,
            totalSolved = s.totalSolved + 1,
        )
        return DailySolveOutcome(updated, streak = next, counted = true)
    }

    /**
     * One-time carry-over of the old "days you opened the app" streak so nobody's current streak is wiped.
     *
     * A live open-streak (last opened today or yesterday, local dates) becomes a solve-streak of the same length,
     * credited as if the last solve were yesterday: solving today continues it, skipping today ends it tomorrow.
     * A streak already broken by the old rules migrates as 0. The best streak is always kept.
     */
    fun migrateOpenStreak(
        openStreak: Int,
        openBest: Int,
        lastOpenedLocal: String,
        todayLocal: String,
        yesterdayLocal: String,
        todayUtcDay: Long,
    ): DailySolveStreak {
        val alive = openStreak > 0 && (lastOpenedLocal == todayLocal || lastOpenedLocal == yesterdayLocal)
        val current = if (alive) openStreak else 0
        return DailySolveStreak(
            current = current,
            best = maxOf(openBest, current),
            lastSolveDay = if (current > 0) todayUtcDay - 1 else null,
            totalSolved = 0,
        )
    }
}

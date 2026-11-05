package com.kreativekoala.riddleverse

// --- daily puzzle (feat/daily-puzzle-share) --- pure streak rules. Days are UTC epoch days
// (see DailyPuzzleSchedule), never local dates, so changing time zone cannot add or remove a day.

data class DailySolveStreak(
    val current: Int = 0,
    val best: Int = 0,
    /** UTC epoch day of the most recent solve, or null if the player has never solved (or migrated nothing). */
    val lastSolveDay: Long? = null,
    val totalSolved: Int = 0,
    /** Streak freezes in the bag (0..[DailyStreakRules.MAX_FREEZES]). A freeze quietly covers one missed day. */
    val freezes: Int = 0,
)

data class DailySolveOutcome(
    val state: DailySolveStreak,
    /** The streak length after this solve (what the share card shows). */
    val streak: Int,
    /** False when [day] was already solved: nothing changes and no reward is due. */
    val counted: Boolean,
    /** Missed days this solve bridged by spending freezes (0 when none were needed). */
    val freezesUsed: Int = 0,
    /** True when this solve earned a new freeze (every [DailyStreakRules.FREEZE_EVERY] streak days, up to the cap). */
    val freezeEarned: Boolean = false,
)

object DailyStreakRules {
    /** Everyone starts with this many freezes (also what existing players get once, so a missed day is forgiven). */
    const val STARTING_FREEZES = 1
    const val MAX_FREEZES = 2
    /** Supporters (optional one-time purchase) hold more freezes and earn them a little faster. */
    const val SUPPORTER_MAX_FREEZES = 5
    const val SUPPORTER_FREEZE_EVERY = 5
    /** A new freeze is earned each time a streak reaches a multiple of this many days. */
    const val FREEZE_EVERY = 7

    /** Whole days with no solve between the last solve and [day] (0 when [day] is the day after the last solve). */
    private fun missedBetween(last: Long, day: Long): Long = (day - last - 1).coerceAtLeast(0)

    /**
     * The streak to show on [today]. It stays alive through the day after the last solve (you still have today
     * to extend it), and also while the missed days can still be covered by freezes in the bag; once the gap is
     * bigger than that it reads 0.
     */
    fun display(s: DailySolveStreak, today: Long): Int {
        val last = s.lastSolveDay ?: return 0
        return if (missedBetween(last, today) <= s.freezes) s.current else 0
    }

    /** True when the player has solved [today] already. */
    fun solvedOn(s: DailySolveStreak, day: Long): Boolean = (s.lastSolveDay ?: Long.MIN_VALUE) >= day

    /**
     * Records a solve of the puzzle for [day].
     *  - same day twice (or a day at or before the last solve, e.g. the clock moved back): no change, idempotent
     *  - the day after the last solve: streak + 1
     *  - one or more missed days that the freezes in the bag can cover: the freezes are spent and the streak + 1
     *  - anything else (first ever solve, or more missed days than freezes): streak restarts at 1
     * Opening the app, or failing a puzzle, never reaches this function, so neither can extend a streak.
     */
    fun applySolve(
        s: DailySolveStreak, day: Long,
        maxFreezes: Int = MAX_FREEZES, freezeEvery: Int = FREEZE_EVERY,
    ): DailySolveOutcome {
        val last = s.lastSolveDay
        if (last != null && day <= last) return DailySolveOutcome(s, streak = s.current, counted = false)
        val missed = if (last == null) 0L else missedBetween(last, day)
        val bridged = last != null && s.current > 0 && missed in 1..s.freezes.toLong()
        val continues = last != null && (missed == 0L || bridged)
        val next = if (continues) s.current + 1 else 1
        val used = if (bridged) missed.toInt() else 0
        var freezes = s.freezes - used
        // A restarted streak keeps the bag as it is; only a continuing streak can earn.
        val earned = continues && next % freezeEvery == 0 && freezes < maxFreezes
        if (earned) freezes += 1
        val updated = DailySolveStreak(
            current = next,
            best = maxOf(s.best, next),
            lastSolveDay = day,
            totalSolved = s.totalSolved + 1,
            freezes = freezes,
        )
        return DailySolveOutcome(updated, streak = next, counted = true, freezesUsed = used, freezeEarned = earned)
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
            freezes = STARTING_FREEZES,
        )
    }
}

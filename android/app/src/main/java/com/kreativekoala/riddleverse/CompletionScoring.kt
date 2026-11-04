package com.kreativekoala.riddleverse

/**
 * Points shown on the completion screen, written into the share text and posted to the global leaderboard.
 * One function, one number: callers must not rescale it afterwards.
 *
 * The input is the session's real performance. [SessionStatistics.totalScore] already sums, per correct answer,
 * a difficulty base (50..200), a speed bonus (up to +60%) and a streak bonus (up to +30%), so a 5-puzzle session
 * of medium puzzles lands around 400..950. That keeps new rows on the same 100..1000 scale as the older rows
 * (which were a random 100..500, doubled for non-custom puzzles, i.e. 200..1000).
 *
 * Never random: equal performance gives equal points, and better performance gives more (up to the cap).
 */
object CompletionScoring {
    const val MIN_POINTS = 10
    const val MAX_POINTS = 1000
    /** Custom (user-made) puzzles keep the half rate they effectively had before (their points were never doubled). */
    const val CUSTOM_PERCENT = 50
    /** Highest per-answer score the game can produce (expert 200 + 60% speed + 30% streak = 380), rounded up. */
    const val MAX_PER_CORRECT_ANSWER = 400

    fun pointsFor(stats: SessionStatistics?, isCustomPuzzle: Boolean): Int {
        val rate = if (isCustomPuzzle) CUSTOM_PERCENT else 100
        val cap = MAX_POINTS * rate / 100
        if (stats == null) return MIN_POINTS
        val correct = stats.correctAnswers.coerceIn(0, stats.totalAnswers.coerceAtLeast(0))
        // A score the game could not have produced from this many correct answers is not trusted.
        val plausible = minOf(stats.totalScore.coerceAtLeast(0).toLong(), correct.toLong() * MAX_PER_CORRECT_ANSWER)
        val scaled = plausible * rate / 100
        return scaled.coerceIn(MIN_POINTS.toLong(), cap.toLong()).toInt()
    }
}

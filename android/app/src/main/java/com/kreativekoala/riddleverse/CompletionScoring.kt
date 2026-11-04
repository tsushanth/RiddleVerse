package com.kreativekoala.riddleverse

/**
 * Points shown on the completion screen, written into the share text and posted to the global leaderboard.
 * One function, one number: callers must not rescale it afterwards.
 *
 * The input is the session's real performance. [SessionStatistics.totalScore] sums, per correct answer, a difficulty
 * base (50..200), a speed bonus (up to +60%) and a streak bonus (up to +30%); it is 50..380 for one puzzle.
 *
 * Scale: the old leaderboard rows came from a random 100..500 that non-custom completions doubled (200..1000, mean
 * about 540). To keep old and new rows comparable, non-custom points are the session score times
 * [LEADERBOARD_SCALE] (that same factor of 2, now applied once, here, so the card, the share text and the post agree):
 * one easy puzzle = 100, one medium puzzle 200..320, one expert puzzle up to 760, a good multi-puzzle session hits
 * the 1000 cap. Custom (user-made) puzzles keep the half rate they effectively had before (factor 1, cap 500).
 *
 * Never random: equal performance gives equal points, and better performance gives more (up to the cap).
 */
object CompletionScoring {
    const val MIN_POINTS = 10
    const val MAX_POINTS = 1000
    const val LEADERBOARD_SCALE = 2
    /** Custom puzzles earn half of what a regular puzzle does for the same session. */
    const val CUSTOM_SCALE = 1
    /** Highest per-answer score the game can produce (expert 200 + 60% speed + 30% streak = 380), rounded up. */
    const val MAX_PER_CORRECT_ANSWER = 400

    fun pointsFor(stats: SessionStatistics?, isCustomPuzzle: Boolean): Int {
        val scale = if (isCustomPuzzle) CUSTOM_SCALE else LEADERBOARD_SCALE
        val cap = MAX_POINTS * scale / LEADERBOARD_SCALE
        if (stats == null) return MIN_POINTS
        val correct = stats.correctAnswers.coerceIn(0, stats.totalAnswers.coerceAtLeast(0))
        // A score the game could not have produced from this many correct answers is not trusted.
        val plausible = minOf(stats.totalScore.coerceAtLeast(0).toLong(), correct.toLong() * MAX_PER_CORRECT_ANSWER)
        return (plausible * scale).coerceIn(MIN_POINTS.toLong(), cap.toLong()).toInt()
    }
}

package com.kreativekoala.riddleverse

// --- daily puzzle (feat/daily-puzzle-share) --- pure, spoiler-free share formatting.
// Nothing here ever receives the puzzle, the seed or the solution: only the number, game name, time and tries.

/** What a finished daily looks like to someone else. */
data class DailyShareResult(
    val number: Int,
    val gameName: String,
    val seconds: Int,
    /** Total tries including the one that solved it (1 = first try). */
    val attempts: Int,
    val streak: Int,
)

object DailyShareText {
    const val PLAY_URL =
        "https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse" +
            "&utm_source=share&utm_medium=daily&utm_campaign=card"

    private const val MAX_SQUARES = 6

    /** 1:42, 0:07, 1:05:09. Negative input reads 0:00. */
    fun formatTime(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** One square per try, wrong tries red and the solving try green: "🟥🟥🟩". Long runs collapse to "…🟩". */
    fun attemptGrid(attempts: Int): String {
        val n = attempts.coerceAtLeast(1)
        return if (n <= MAX_SQUARES) "🟥".repeat(n - 1) + "🟩"
        else "🟥".repeat(MAX_SQUARES - 1) + "…🟩"
    }

    /** "RiddleVerse Daily #123 ✅ 1:42 🔥5" */
    fun headline(r: DailyShareResult): String {
        val flame = if (r.streak > 0) " 🔥${r.streak}" else ""
        return "RiddleVerse Daily #${r.number} ✅ ${formatTime(r.seconds)}$flame"
    }

    /** Full text for the share sheet: headline, game + tries grid, then the Play link. */
    fun build(r: DailyShareResult): String =
        headline(r) + "\n" + r.gameName + " " + attemptGrid(r.attempts) + "\n" + PLAY_URL
}

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
    /** Puzzle type key of the daily game (e.g. "sudoku"): the shared link opens that game's web page / the app. Null = game list. */
    val slug: String? = null,
)

object DailyShareText {
    /** Link for a daily result: the playable web page of that game (App Link opens the app when installed), tagged src=share. */
    fun playUrl(slug: String?): String = ShareLinks.playUrl(slug, ShareLinks.CHANNEL_OTHER, medium = "daily")

    /** English template for [headline]; the app passes the localized string `done_share_headline` instead. %1$d = number, %2$s = time. */
    const val DEFAULT_HEADLINE_TEMPLATE = "RiddleVerse Daily #%1\$d ✅ %2\$s"

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
    fun headline(r: DailyShareResult, template: String = DEFAULT_HEADLINE_TEMPLATE): String {
        val flame = if (r.streak > 0) " 🔥${r.streak}" else ""
        return String.format(template, r.number, formatTime(r.seconds)) + flame
    }

    /** Full text for the share sheet: headline, game + tries grid, then the playable link. */
    fun build(r: DailyShareResult, headlineTemplate: String = DEFAULT_HEADLINE_TEMPLATE): String =
        headline(r, headlineTemplate) + "\n" + r.gameName + " " + attemptGrid(r.attempts) + "\n" + playUrl(r.slug)
}

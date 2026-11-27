package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import org.json.JSONObject

/**
 * The first puzzle a new player meets: a 4x4 Sudoku with five blanks, generated on the device (no network, no
 * language), tap only, with no lives and no time limit, so it can always be finished.
 */
object StarterPuzzle {
    const val ID_PREFIX = "starter_"
    /** puzzle_type in puzzle_events, so first-puzzle outcomes are not mixed up with ordinary easy Sudoku. */
    const val TELEMETRY_TYPE = "starter"
    const val SIZE = 4
    const val CLUES = 11

    fun create(seed: Long = System.nanoTime()): Puzzle = Puzzle(
        puzzleId = "$ID_PREFIX$seed",
        puzzleType = GridGame.SUDOKU.key,
        question = JSONObject().put("game", GridGame.SUDOKU.key).put("difficulty", "easy").put("seed", seed).toString(),
        answer = "completed",
        hint = GridPuzzleFactory.hintFor(GridGame.SUDOKU),
        difficulty = "Easy",
        options = emptyList()
    )

    fun isStarter(puzzle: Puzzle?): Boolean = puzzle?.puzzleId?.startsWith(ID_PREFIX) == true

    fun spec(seed: Long): GridSpec = SudokuGenerator.generate(SIZE, 2, 2, CLUES, seed).let {
        GridSpec(GridGame.SUDOKU, it.size, "easy", it.puzzle, it.solution, boxRows = it.boxRows, boxCols = it.boxCols)
    }
}

/**
 * Who gets the starter puzzle without pressing Play: a brand-new install that reached Home (for example by signing
 * in from the first screen) and has not been shown any puzzle yet. Once per install.
 */
object StarterOffer {
    private const val PREFS = "starter_v1"
    private const val K_PLAYED = "any_puzzle_shown"
    private const val K_OFFERED = "offered"
    const val NEW_INSTALL_WINDOW_MS = 24 * 3600 * 1000L

    fun shouldAutoStart(installAgeMs: Long, playedAny: Boolean, offered: Boolean): Boolean =
        installAgeMs in 0..NEW_INSTALL_WINDOW_MS && !playedAny && !offered

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markPuzzleShown(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean(K_PLAYED, false)) p.edit().putBoolean(K_PLAYED, true).apply()
    }

    fun markOffered(c: Context) { prefs(c).edit().putBoolean(K_OFFERED, true).apply() }

    /** Reads disk: call off the main thread. True at most once; the caller then opens [intent]. */
    fun claim(c: Context, now: Long = System.currentTimeMillis()): Boolean = try {
        val installed = c.packageManager.getPackageInfo(c.packageName, 0).firstInstallTime
        val p = prefs(c)
        shouldAutoStart(now - installed, p.getBoolean(K_PLAYED, false), p.getBoolean(K_OFFERED, false))
            .also { if (it) markOffered(c) }
    } catch (_: Throwable) { false }

    fun intent(context: Context, puzzle: Puzzle = StarterPuzzle.create()): Intent =
        Intent(context, PuzzleActivity::class.java).apply {
            putExtra("puzzle", Gson().toJson(puzzle))
            putExtra("screenType", FirstRunPlan.SCREEN_TYPE)
            putExtra("difficulty", "Easy")
            putExtra("score", 0)
            putExtra("targetPuzzleCount", FirstRunPlan.TARGET_PUZZLE_COUNT)
            putExtra("startTime", System.currentTimeMillis())
            putExtra("puzzleSource", "starter")
            putExtra(FirstRun.EXTRA_IS_FIRST_RUN_PUZZLE, true)
        }
}

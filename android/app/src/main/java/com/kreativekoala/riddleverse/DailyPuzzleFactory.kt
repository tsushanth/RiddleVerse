package com.kreativekoala.riddleverse

import android.content.Context
import org.json.JSONObject

// --- daily puzzle (feat/daily-puzzle-share) --- turns a DailyPuzzleSpec into the normal Puzzle record the existing
// game screens already render. The seed travels in Puzzle.question, exactly as it does for ordinary play, so no
// game screen needed to change.

class DailyPuzzle(val spec: DailyPuzzleSpec, val puzzle: Puzzle, val screenType: String)

object DailyPuzzleFactory {

    /** May load word lists from assets for the word games, so call off the main thread. */
    fun build(context: Context, spec: DailyPuzzleSpec): DailyPuzzle {
        val d = DailyPuzzleSchedule.DIFFICULTY
        val type = spec.type
        val grid = GridGame.fromKey(type)
        return when {
            grid != null -> DailyPuzzle(
                spec,
                GridPuzzleFactory.createPuzzle(grid, d, spec.seed).copy(puzzleId = id(spec)),
                "Grid Puzzle Screen",
            )
            type in SpatialPuzzles.TYPES -> DailyPuzzle(
                spec,
                Puzzle(
                    puzzleId = id(spec),
                    puzzleType = type,
                    question = JSONObject().put("seed", spec.seed).put("difficulty", d).put("game", type).toString(),
                    answer = "completed",
                    hint = "Today's puzzle is the same for everyone.",
                    difficulty = d,
                    options = emptyList(),
                ),
                SpatialPuzzles.screenOption(type),
            )
            type in WordPuzzleRegistry.TYPES -> DailyPuzzle(
                spec,
                WordPuzzleRegistry.createPuzzle(type, d, context, spec.seed).copy(puzzleId = id(spec)),
                WordPuzzleRegistry.screenTypeName(type),
            )
            else -> throw IllegalArgumentException("Not a daily game: $type")
        }
    }

    private fun id(spec: DailyPuzzleSpec) = "daily_${spec.number}_${spec.type}"
}

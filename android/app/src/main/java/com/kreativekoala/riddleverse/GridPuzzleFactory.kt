package com.kreativekoala.riddleverse

import org.json.JSONObject

/** The grid-logic games added by feat/puzzles-grid. [key] is the puzzleType string used app-wide. */
enum class GridGame(val key: String, val title: String, val emoji: String) {
    SUDOKU("sudoku", "Sudoku", "🔢"),
    BINAIRO("binarygrid", "Binary Grid", "⚫"),
    CAGE_MATH("cagemath", "Cage Math", "➕"),
    SIGN_GRID("signgrid", "Sign Grid", "⚖️");

    companion object {
        fun fromKey(key: String?): GridGame? = values().firstOrNull { it.key == key?.lowercase() }
    }
}

/** Game-agnostic description of a generated grid puzzle, consumed by [GridPuzzleScreen]. */
class GridSpec(
    val game: GridGame,
    val n: Int,
    val difficulty: String,
    val given: IntArray,        // 0 = empty, otherwise fixed clue value
    val solution: IntArray,
    val boxRows: Int = 0,
    val boxCols: Int = 0,
    val cages: List<KenKenCage> = emptyList(),
    val cageOf: IntArray = IntArray(0),
    val hRel: IntArray = IntArray(0),
    val vRel: IntArray = IntArray(0)
) {
    /** Number of selectable symbols (Binary Grid has 2: 0 and 1, stored as values 1 and 2). */
    val symbols: Int get() = if (game == GridGame.BINAIRO) 2 else n

    fun symbolText(v: Int): String = when {
        v == 0 -> ""
        game == GridGame.BINAIRO -> (v - 1).toString()
        else -> v.toString()
    }
}

object GridPuzzleFactory {

    fun isGridType(puzzleType: String?): Boolean = GridGame.fromKey(puzzleType) != null

    fun normalizeDifficulty(d: String?): String = when (d?.lowercase()) {
        "easy", "beginner", "tutorial" -> "easy"
        "hard", "expert", "master", "grandmaster", "hard+", "hard-" -> "hard"
        else -> "medium"
    }

    fun build(game: GridGame, difficulty: String, seed: Long): GridSpec {
        val d = normalizeDifficulty(difficulty)
        return when (game) {
            GridGame.SUDOKU -> SudokuGenerator.generate(d, seed).let {
                GridSpec(game, it.size, d, it.puzzle, it.solution, boxRows = it.boxRows, boxCols = it.boxCols)
            }
            GridGame.BINAIRO -> BinairoGenerator.generate(d, seed).let {
                GridSpec(game, it.size, d, it.puzzle, it.solution)
            }
            GridGame.CAGE_MATH -> KenKenGenerator.generate(d, seed).let {
                GridSpec(game, it.size, d, IntArray(it.size * it.size), it.solution, cages = it.cages, cageOf = it.cageOf)
            }
            GridGame.SIGN_GRID -> FutoshikiGenerator.generate(d, seed).let {
                GridSpec(game, it.size, d, it.givens, it.solution, hRel = it.hRel, vRel = it.vRel)
            }
        }
    }

    /** Builds the [Puzzle] record for the normal puzzle flow. The grid itself is regenerated from the seed. */
    fun createPuzzle(game: GridGame, difficulty: String, seed: Long = System.nanoTime()): Puzzle {
        val d = normalizeDifficulty(difficulty)
        val q = JSONObject().put("game", game.key).put("difficulty", d).put("seed", seed).toString()
        return Puzzle(
            puzzleId = "${game.key}_$seed",
            puzzleType = game.key,
            question = q,
            answer = "completed",
            hint = hintFor(game),
            difficulty = difficulty,
            options = emptyList()
        )
    }

    fun createPuzzleOrNull(puzzleType: String?, difficulty: String): Puzzle? =
        GridGame.fromKey(puzzleType)?.let { createPuzzle(it, difficulty) }

    fun seedFrom(puzzle: Puzzle): Long = try {
        JSONObject(puzzle.question).getLong("seed")
    } catch (e: Exception) {
        puzzle.puzzleId.hashCode().toLong()
    }

    fun hintFor(game: GridGame): String = when (game) {
        GridGame.SUDOKU -> "Every row, column and box holds each digit exactly once"
        GridGame.BINAIRO -> "Equal 0s and 1s per line, never three in a row"
        GridGame.CAGE_MATH -> "Fill the grid so each cage hits its target"
        GridGame.SIGN_GRID -> "Digits never repeat in a line and every sign must hold"
    }

    fun rulesFor(game: GridGame): String = when (game) {
        GridGame.SUDOKU -> "Fill every empty cell so each row, each column and each box contains every digit exactly once. " +
            "Use the pencil tool to jot candidate digits. Every puzzle has exactly one solution."
        GridGame.BINAIRO -> "Fill every cell with 0 or 1. No more than two equal digits may touch in a row or column, " +
            "each row and column has as many 0s as 1s, and no two rows (or two columns) are identical."
        GridGame.CAGE_MATH -> "Fill the grid with digits so none repeats in any row or column. " +
            "The number and sign in a cage corner give the target: the cage's digits must add, subtract, multiply or divide to it. " +
            "A cage with only a number is a single cell. Digits may repeat inside a cage if they are in different rows and columns."
        GridGame.SIGN_GRID -> "Fill the grid so no digit repeats in any row or column. " +
            "A sign between two cells points to the smaller one: the cell at the narrow end holds the smaller digit."
    }
}

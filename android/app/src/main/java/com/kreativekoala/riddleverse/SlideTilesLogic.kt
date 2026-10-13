package com.kreativekoala.riddleverse

import kotlin.random.Random

// --- classic puzzles (feat/puzzles-classic) --- Slide Tiles: pure game logic (no Android deps)

/** Board sizes and shuffle depth per difficulty tier. */
data class SlideTilesConfig(val n: Int, val shuffleMoves: Int)

object SlideTilesRules {
    fun configFor(tier: Int): SlideTilesConfig = when (tier) {
        0 -> SlideTilesConfig(3, 12)
        1 -> SlideTilesConfig(3, 24)
        2 -> SlideTilesConfig(4, 40)
        3 -> SlideTilesConfig(4, 80)
        else -> SlideTilesConfig(5, 110)
    }

    fun solvedTiles(n: Int): List<Int> = List(n * n) { if (it == n * n - 1) 0 else it + 1 }

    /** Standard parity test: true when [tiles] (0 = blank, row-major) can reach the solved layout. */
    fun isSolvable(n: Int, tiles: List<Int>): Boolean {
        val seq = tiles.filter { it != 0 }
        var inversions = 0
        for (i in seq.indices) for (j in i + 1 until seq.size) if (seq[i] > seq[j]) inversions++
        return if (n % 2 == 1) {
            inversions % 2 == 0
        } else {
            val blankRowFromBottom = n - tiles.indexOf(0) / n
            // For even widths: solvable iff (inversions + blank row counted from bottom) is odd
            (inversions + blankRowFromBottom) % 2 == 1
        }
    }

    /**
     * Shuffles by performing [moves] legal slides from the solved board (never undoing the
     * previous slide), so the result is solvable by construction. Never returns a solved board.
     */
    fun scramble(n: Int, moves: Int, rng: Random): List<Int> {
        var board = SlideTilesGame.solved(n)
        var lastBlank = -1
        var made = 0
        while (made < moves || board.isSolved) {
            val blank = board.blankIndex
            val options = board.neighborsOfBlank().filter { it != lastBlank }
            val pick = options[rng.nextInt(options.size)]
            lastBlank = blank
            board = board.slideAdjacent(pick)
            made++
        }
        return board.tiles
    }
}

data class SlideTilesGame(
    val n: Int,
    val tiles: List<Int>,
    val moves: Int,
    val par: Int,
    val history: List<List<Int>> = emptyList(),
) {
    val blankIndex get() = tiles.indexOf(0)
    val isSolved get() = tiles == SlideTilesRules.solvedTiles(n)
    val canUndo get() = history.isNotEmpty()

    fun neighborsOfBlank(): List<Int> {
        val b = blankIndex
        val r = b / n
        val c = b % n
        return buildList {
            if (r > 0) add(b - n)
            if (r < n - 1) add(b + n)
            if (c > 0) add(b - 1)
            if (c < n - 1) add(b + 1)
        }
    }

    /** Swaps the blank with an adjacent tile at [idx]. */
    internal fun slideAdjacent(idx: Int): SlideTilesGame {
        val b = blankIndex
        val t = tiles.toMutableList()
        t[b] = t[idx]
        t[idx] = 0
        return copy(tiles = t)
    }

    /**
     * Tapping a tile in the blank's row/column slides every tile between it and the blank
     * (each shifted tile counts as one move). Returns null if the tap is not legal.
     */
    fun tap(idx: Int): SlideTilesGame? {
        if (idx !in tiles.indices || tiles[idx] == 0) return null
        val b = blankIndex
        val sameRow = idx / n == b / n
        val sameCol = idx % n == b % n
        if (!sameRow && !sameCol) return null
        val step = if (sameRow) (if (idx > b) 1 else -1) else (if (idx > b) n else -n)
        val t = tiles.toMutableList()
        var pos = b
        var shifted = 0
        while (pos != idx) {
            t[pos] = t[pos + step]
            pos += step
            shifted++
        }
        t[idx] = 0
        return copy(tiles = t, moves = moves + shifted, history = history + listOf(tiles))
    }

    fun undo(): SlideTilesGame {
        if (history.isEmpty()) return this
        val prev = history.last()
        val shifted = tiles.indices.count { tiles[it] != prev[it] } - 1
        return copy(tiles = prev, moves = maxOf(0, moves - maxOf(1, shifted)), history = history.dropLast(1))
    }

    fun encode(): String = listOf(
        n, moves, par, tiles.joinToString(","),
        history.joinToString(";") { it.joinToString(",") },
    ).joinToString("|")

    companion object {
        fun solved(n: Int) = SlideTilesGame(n, SlideTilesRules.solvedTiles(n), 0, 0)

        fun newGame(tier: Int, seed: Long): SlideTilesGame {
            val cfg = SlideTilesRules.configFor(tier)
            val tiles = SlideTilesRules.scramble(cfg.n, cfg.shuffleMoves, Random(seed))
            return SlideTilesGame(cfg.n, tiles, 0, cfg.shuffleMoves)
        }

        fun decode(s: String): SlideTilesGame? = try {
            val p = s.split("|")
            val n = p[0].toInt()
            SlideTilesGame(
                n = n, moves = p[1].toInt(), par = p[2].toInt(),
                tiles = p[3].split(",").map { it.toInt() },
                history = if (p.size > 4 && p[4].isNotEmpty()) p[4].split(";").map { h -> h.split(",").map { it.toInt() } } else emptyList(),
            ).takeIf { it.tiles.size == n * n }
        } catch (e: Exception) {
            null
        }
    }
}

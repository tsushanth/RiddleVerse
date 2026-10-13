package com.kreativekoala.riddleverse

import kotlin.random.Random

// --- classic puzzles (feat/puzzles-classic) --- Tile Merge: pure game logic (no Android deps)

enum class MergeDir { UP, DOWN, LEFT, RIGHT }

/** Per-difficulty goal: reach [target] within [moveBudget] effective moves. */
data class TileMergeConfig(val target: Int, val moveBudget: Int)

object TileMergeRules {
    const val SIZE = 4

    fun configFor(tier: Int): TileMergeConfig = when (tier) {
        0 -> TileMergeConfig(128, 150)
        1 -> TileMergeConfig(256, 260)
        2 -> TileMergeConfig(512, 500)
        3 -> TileMergeConfig(1024, 900)
        else -> TileMergeConfig(2048, 1700)
    }

    /** Slides a line toward index 0, merging equal neighbours once. Returns (line, scoreGained). */
    fun slideLine(line: List<Int>): Pair<List<Int>, Int> {
        val packed = line.filter { it != 0 }
        val out = ArrayList<Int>(line.size)
        var gained = 0
        var i = 0
        while (i < packed.size) {
            if (i + 1 < packed.size && packed[i] == packed[i + 1]) {
                val merged = packed[i] * 2
                out.add(merged)
                gained += merged
                i += 2
            } else {
                out.add(packed[i])
                i++
            }
        }
        while (out.size < line.size) out.add(0)
        return out to gained
    }
}

/**
 * Immutable game state. Spawns are derived from (seed, moves) so a given seed + move
 * sequence always yields the same game and the state can be restored from a string.
 */
data class TileMergeGame(
    val cells: List<Int>,
    val score: Int,
    val moves: Int,
    val seed: Long,
    val reachedTarget: Boolean = false,
    val prevCells: List<Int>? = null,
    val prevScore: Int = 0,
    val prevMoves: Int = 0,
    val prevReached: Boolean = false,
) {
    val size get() = TileMergeRules.SIZE
    val maxTile get() = cells.maxOrNull() ?: 0
    val canUndo get() = prevCells != null

    fun canMove(): Boolean {
        if (cells.any { it == 0 }) return true
        for (r in 0 until size) for (c in 0 until size) {
            val v = cells[r * size + c]
            if (c + 1 < size && cells[r * size + c + 1] == v) return true
            if (r + 1 < size && cells[(r + 1) * size + c] == v) return true
        }
        return false
    }

    /** Applies a swipe. Returns this unchanged if nothing moved. [target] updates [reachedTarget]. */
    fun move(dir: MergeDir, target: Int): TileMergeGame {
        val n = size
        val next = MutableList(n * n) { 0 }
        var gained = 0
        for (k in 0 until n) {
            val idx = (0 until n).map { j ->
                when (dir) {
                    MergeDir.LEFT -> k * n + j
                    MergeDir.RIGHT -> k * n + (n - 1 - j)
                    MergeDir.UP -> j * n + k
                    MergeDir.DOWN -> (n - 1 - j) * n + k
                }
            }
            val (line, g) = TileMergeRules.slideLine(idx.map { cells[it] })
            gained += g
            idx.forEachIndexed { j, cellIndex -> next[cellIndex] = line[j] }
        }
        if (next == cells) return this
        val moved = copy(
            cells = next, score = score + gained, moves = moves + 1,
            prevCells = cells, prevScore = score, prevMoves = moves, prevReached = reachedTarget,
        )
        val spawned = moved.spawn()
        return spawned.copy(reachedTarget = reachedTarget || spawned.maxTile >= target)
    }

    fun undo(): TileMergeGame {
        val p = prevCells ?: return this
        return copy(
            cells = p, score = prevScore, moves = prevMoves, reachedTarget = prevReached,
            prevCells = null, prevScore = 0, prevMoves = 0, prevReached = false,
        )
    }

    private fun spawn(): TileMergeGame {
        val empty = cells.indices.filter { cells[it] == 0 }
        if (empty.isEmpty()) return this
        val rng = Random(seed * 1_000_003L + moves)
        val spot = empty[rng.nextInt(empty.size)]
        val value = if (rng.nextInt(10) == 0) 4 else 2
        return copy(cells = cells.toMutableList().also { it[spot] = value })
    }

    fun encode(): String = listOf(
        seed, moves, score, if (reachedTarget) 1 else 0, cells.joinToString(","),
        prevCells?.joinToString(",") ?: "-", prevScore, prevMoves, if (prevReached) 1 else 0,
    ).joinToString("|")

    companion object {
        fun newGame(seed: Long): TileMergeGame {
            val rng = Random(seed)
            val n = TileMergeRules.SIZE
            val cells = MutableList(n * n) { 0 }
            val spots = (0 until n * n).shuffled(rng).take(2)
            spots.forEach { cells[it] = if (rng.nextInt(10) == 0) 4 else 2 }
            return TileMergeGame(cells, 0, 0, seed)
        }

        fun decode(s: String): TileMergeGame? = try {
            val p = s.split("|")
            TileMergeGame(
                cells = p[4].split(",").map { it.toInt() },
                score = p[2].toInt(), moves = p[1].toInt(), seed = p[0].toLong(),
                reachedTarget = p[3] == "1",
                prevCells = if (p[5] == "-") null else p[5].split(",").map { it.toInt() },
                prevScore = p[6].toInt(), prevMoves = p[7].toInt(), prevReached = p[8] == "1",
            ).takeIf { it.cells.size == TileMergeRules.SIZE * TileMergeRules.SIZE }
        } catch (e: Exception) {
            null
        }
    }
}

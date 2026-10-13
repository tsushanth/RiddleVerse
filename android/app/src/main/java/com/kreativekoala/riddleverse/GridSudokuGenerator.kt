package com.kreativekoala.riddleverse

import kotlin.random.Random

/** Sudoku puzzle: values 0 = empty, 1..size = digit. Box shape is boxRows x boxCols (4x4: 2x2, 6x6: 2x3, 9x9: 3x3). */
class SudokuPuzzle(
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
    val puzzle: IntArray,
    val solution: IntArray
) {
    val clueCount: Int get() = puzzle.count { it != 0 }
}

/** Local, deterministic (seeded) Sudoku generator with a unique-solution guarantee. */
object SudokuGenerator {

    /** Tier -> (size, boxRows, boxCols, target clue count). */
    fun configFor(difficulty: String): IntArray = when (difficulty.lowercase()) {
        "easy", "beginner" -> intArrayOf(4, 2, 2, 8)
        "medium" -> intArrayOf(6, 2, 3, 16)
        else -> intArrayOf(9, 3, 3, 30)
    }

    fun generate(difficulty: String, seed: Long): SudokuPuzzle {
        val c = configFor(difficulty)
        return generate(c[0], c[1], c[2], c[3], seed)
    }

    fun generate(size: Int, boxRows: Int, boxCols: Int, targetClues: Int, seed: Long): SudokuPuzzle {
        val rnd = Random(seed)
        val solution = IntArray(size * size)
        check(fill(solution, size, boxRows, boxCols, rnd)) { "could not build a Sudoku solution" }
        val puzzle = solution.copyOf()
        val order = (0 until size * size).shuffled(rnd)
        var clues = size * size
        for (cell in order) {
            if (clues <= targetClues) break
            val keep = puzzle[cell]
            puzzle[cell] = 0
            if (countSolutions(puzzle, size, boxRows, boxCols, 2) == 1) clues-- else puzzle[cell] = keep
        }
        return SudokuPuzzle(size, boxRows, boxCols, puzzle, solution)
    }

    private fun candidatesMask(g: IntArray, n: Int, br: Int, bc: Int, cell: Int): Int {
        val r = cell / n
        val c = cell % n
        var used = 0
        for (i in 0 until n) {
            used = used or (1 shl g[r * n + i]) or (1 shl g[i * n + c])
        }
        val r0 = r / br * br
        val c0 = c / bc * bc
        for (dr in 0 until br) for (dc in 0 until bc) used = used or (1 shl g[(r0 + dr) * n + c0 + dc])
        val all = ((1 shl (n + 1)) - 2)
        return all and used.inv()
    }

    private fun fill(g: IntArray, n: Int, br: Int, bc: Int, rnd: Random): Boolean {
        var best = -1
        var bestMask = 0
        var bestCount = Int.MAX_VALUE
        for (cell in g.indices) {
            if (g[cell] != 0) continue
            val m = candidatesMask(g, n, br, bc, cell)
            val k = Integer.bitCount(m)
            if (k < bestCount) { best = cell; bestMask = m; bestCount = k; if (k <= 1) break }
        }
        if (best == -1) return true
        if (bestCount == 0) return false
        val digits = (1..n).filter { bestMask and (1 shl it) != 0 }.shuffled(rnd)
        for (d in digits) {
            g[best] = d
            if (fill(g, n, br, bc, rnd)) return true
        }
        g[best] = 0
        return false
    }

    /** Counts solutions up to [limit] (MRV backtracking). Does not mutate [puzzle]. */
    fun countSolutions(puzzle: IntArray, n: Int, br: Int, bc: Int, limit: Int): Int {
        val g = puzzle.copyOf()
        var count = 0
        fun go() {
            if (count >= limit) return
            var best = -1
            var bestMask = 0
            var bestCount = Int.MAX_VALUE
            for (cell in g.indices) {
                if (g[cell] != 0) continue
                val m = candidatesMask(g, n, br, bc, cell)
                val k = Integer.bitCount(m)
                if (k < bestCount) { best = cell; bestMask = m; bestCount = k; if (k <= 1) break }
            }
            if (best == -1) { count++; return }
            if (bestCount == 0) return
            for (d in 1..n) {
                if (bestMask and (1 shl d) == 0) continue
                g[best] = d
                go()
                if (count >= limit) break
            }
            g[best] = 0
        }
        go()
        return count
    }

    /** True when the (possibly partial) grid has no row/column/box duplicate. */
    fun isConsistent(g: IntArray, n: Int, br: Int, bc: Int): Boolean {
        for (cell in g.indices) {
            val v = g[cell]
            if (v == 0) continue
            g[cell] = 0
            val ok = candidatesMask(g, n, br, bc, cell) and (1 shl v) != 0
            g[cell] = v
            if (!ok) return false
        }
        return true
    }
}

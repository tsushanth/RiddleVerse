package com.kreativekoala.riddleverse

import kotlin.random.Random

/**
 * Binairo / Takuzu puzzle. Cell values: 0 = empty, 1 = symbol "0", 2 = symbol "1".
 * Rules: no three equal neighbours in a row/column, equal count of each symbol per line,
 * no two identical rows and no two identical columns.
 */
class BinairoPuzzle(val size: Int, val puzzle: IntArray, val solution: IntArray) {
    val clueCount: Int get() = puzzle.count { it != 0 }
}

object BinairoGenerator {

    /** Tier -> (size, target clue count). */
    fun configFor(difficulty: String): IntArray = when (difficulty.lowercase()) {
        "easy", "beginner" -> intArrayOf(6, 20)
        "medium" -> intArrayOf(8, 28)
        else -> intArrayOf(10, 38)
    }

    fun generate(difficulty: String, seed: Long): BinairoPuzzle {
        val c = configFor(difficulty)
        return generate(c[0], c[1], seed)
    }

    fun generate(size: Int, targetClues: Int, seed: Long): BinairoPuzzle {
        require(size % 2 == 0)
        val rnd = Random(seed)
        val solution = IntArray(size * size)
        check(fill(solution, size, 0, rnd)) { "no binairo solution" }
        val puzzle = solution.copyOf()
        var clues = size * size
        for (cell in (0 until size * size).shuffled(rnd)) {
            if (clues <= targetClues) break
            val keep = puzzle[cell]
            puzzle[cell] = 0
            if (countSolutions(puzzle, size, 2) == 1) clues-- else puzzle[cell] = keep
        }
        return BinairoPuzzle(size, puzzle, solution)
    }

    /** Whether placing [v] at [cell] keeps all *local* rules satisfiable given the current grid [g]. */
    private fun canPlace(g: IntArray, n: Int, cell: Int, v: Int): Boolean {
        val r = cell / n
        val c = cell % n
        // no three equal in a row (horizontal window)
        for (start in c - 2..c) {
            var same = 0
            for (k in 0..2) {
                val cc = start + k
                if (cc < 0 || cc >= n) { same = -9; break }
                val x = if (cc == c) v else g[r * n + cc]
                if (x == v) same++
            }
            if (same == 3) return false
        }
        for (start in r - 2..r) {
            var same = 0
            for (k in 0..2) {
                val rr = start + k
                if (rr < 0 || rr >= n) { same = -9; break }
                val x = if (rr == r) v else g[rr * n + c]
                if (x == v) same++
            }
            if (same == 3) return false
        }
        // symbol counts
        var rowCount = 1
        var colCount = 1
        for (i in 0 until n) {
            if (i != c && g[r * n + i] == v) rowCount++
            if (i != r && g[i * n + c] == v) colCount++
        }
        if (rowCount > n / 2 || colCount > n / 2) return false
        return true
    }

    private fun rowsEqual(g: IntArray, n: Int, a: Int, b: Int): Boolean {
        for (i in 0 until n) if (g[a * n + i] != g[b * n + i] || g[a * n + i] == 0) return false
        return true
    }

    private fun colsEqual(g: IntArray, n: Int, a: Int, b: Int): Boolean {
        for (i in 0 until n) if (g[i * n + a] != g[i * n + b] || g[i * n + a] == 0) return false
        return true
    }

    /** After placing at [cell], reject if its (now complete) row/column duplicates another complete one. */
    private fun uniqueLinesOk(g: IntArray, n: Int, cell: Int): Boolean {
        val r = cell / n
        val c = cell % n
        if ((0 until n).all { g[r * n + it] != 0 }) {
            for (o in 0 until n) if (o != r && rowsEqual(g, n, r, o)) return false
        }
        if ((0 until n).all { g[it * n + c] != 0 }) {
            for (o in 0 until n) if (o != c && colsEqual(g, n, c, o)) return false
        }
        return true
    }

    private fun fill(g: IntArray, n: Int, cell: Int, rnd: Random): Boolean {
        if (cell == n * n) return true
        val order = if (rnd.nextBoolean()) intArrayOf(1, 2) else intArrayOf(2, 1)
        for (v in order) {
            if (!canPlace(g, n, cell, v)) continue
            g[cell] = v
            if (uniqueLinesOk(g, n, cell) && fill(g, n, cell + 1, rnd)) return true
            g[cell] = 0
        }
        return false
    }

    /** Counts solutions up to [limit]. Does not mutate [puzzle]. Cells are filled in order of fewest options. */
    fun countSolutions(puzzle: IntArray, n: Int, limit: Int): Int {
        val g = puzzle.copyOf()
        // reject inconsistent starting grids
        for (i in g.indices) {
            val v = g[i]
            if (v == 0) continue
            g[i] = 0
            val ok = canPlace(g, n, i, v)
            g[i] = v
            if (!ok) return 0
        }
        var count = 0
        fun go() {
            if (count >= limit) return
            // pick the empty cell with the fewest legal values
            var best = -1
            var bestOpts = 3
            var bestMask = 0
            for (cell in g.indices) {
                if (g[cell] != 0) continue
                var mask = 0
                var opts = 0
                for (v in 1..2) if (canPlace(g, n, cell, v)) { mask = mask or v; opts++ }
                if (opts < bestOpts) { best = cell; bestOpts = opts; bestMask = mask; if (opts == 0) return }
                if (bestOpts <= 1) break
            }
            if (best == -1) {
                if (isSolved(g, n)) count++
                return
            }
            for (v in 1..2) {
                if (bestMask and v == 0) continue
                g[best] = v
                if (uniqueLinesOk(g, n, best)) go()
                g[best] = 0
                if (count >= limit) return
            }
        }
        go()
        return count
    }

    /** Full rule check of a completely filled grid. */
    fun isSolved(g: IntArray, n: Int): Boolean {
        if (g.any { it == 0 }) return false
        for (i in 0 until n) {
            var rowOnes = 0
            var colOnes = 0
            for (j in 0 until n) {
                if (g[i * n + j] == 2) rowOnes++
                if (g[j * n + i] == 2) colOnes++
                if (j >= 2) {
                    if (g[i * n + j] == g[i * n + j - 1] && g[i * n + j] == g[i * n + j - 2]) return false
                    if (g[j * n + i] == g[(j - 1) * n + i] && g[j * n + i] == g[(j - 2) * n + i]) return false
                }
            }
            if (rowOnes != n / 2 || colOnes != n / 2) return false
        }
        for (a in 0 until n) for (b in a + 1 until n) {
            if (rowsEqual(g, n, a, b) || colsEqual(g, n, a, b)) return false
        }
        return true
    }
}

package com.kreativekoala.riddleverse

import kotlin.random.Random

/**
 * Futoshiki: Latin square (1..size) with inequality signs between adjacent cells and a few givens.
 * [hRel] has size*(size-1) entries: relation between (r,c) and (r,c+1) at index r*(size-1)+c.
 * [vRel] has (size-1)*size entries: relation between (r,c) and (r+1,c) at index r*size+c.
 * Relation values: 0 none, 1 = first cell is smaller, -1 = first cell is larger.
 */
class FutoshikiPuzzle(
    val size: Int,
    val givens: IntArray,
    val hRel: IntArray,
    val vRel: IntArray,
    val solution: IntArray
) {
    val clueCount: Int get() = givens.count { it != 0 } + hRel.count { it != 0 } + vRel.count { it != 0 }
}

object FutoshikiGenerator {

    fun sizeFor(difficulty: String): Int = when (difficulty.lowercase()) {
        "easy", "beginner" -> 4
        "medium" -> 5
        else -> 6
    }

    fun generate(difficulty: String, seed: Long): FutoshikiPuzzle =
        generate(sizeFor(difficulty), difficulty.lowercase(), seed)

    fun generate(n: Int, difficulty: String, seed: Long): FutoshikiPuzzle {
        val rnd = Random(seed)
        val sol = latinSquare(n, rnd)
        val density = when (difficulty) {
            "easy", "beginner" -> 0.55
            "medium" -> 0.42
            else -> 0.32
        }
        val h = IntArray(n * (n - 1))
        val v = IntArray((n - 1) * n)
        for (r in 0 until n) for (c in 0 until n - 1) {
            if (rnd.nextDouble() < density) h[r * (n - 1) + c] = if (sol[r * n + c] < sol[r * n + c + 1]) 1 else -1
        }
        for (r in 0 until n - 1) for (c in 0 until n) {
            if (rnd.nextDouble() < density) v[r * n + c] = if (sol[r * n + c] < sol[(r + 1) * n + c]) 1 else -1
        }
        val givens = IntArray(n * n)
        // add givens (random order) until unique
        val order = (0 until n * n).shuffled(rnd)
        var idx = 0
        while (countSolutions(n, givens, h, v, 2) != 1) {
            val cell = order[idx++]
            givens[cell] = sol[cell]
        }
        val hard = difficulty != "easy" && difficulty != "beginner"
        if (hard) {
            // minimise givens
            for (cell in order) {
                if (givens[cell] == 0) continue
                val keep = givens[cell]
                givens[cell] = 0
                if (countSolutions(n, givens, h, v, 2) != 1) givens[cell] = keep
            }
        }
        if (difficulty == "hard") {
            // minimise inequality signs too
            val signs = (h.indices.map { 0 to it } + v.indices.map { 1 to it }).shuffled(rnd)
            for ((kind, i) in signs) {
                val arr = if (kind == 0) h else v
                if (arr[i] == 0) continue
                val keep = arr[i]
                arr[i] = 0
                if (countSolutions(n, givens, h, v, 2) != 1) arr[i] = keep
            }
        }
        return FutoshikiPuzzle(n, givens, h, v, sol)
    }

    private fun latinSquare(n: Int, rnd: Random): IntArray {
        val rowPerm = (0 until n).shuffled(rnd)
        val colPerm = (0 until n).shuffled(rnd)
        val symPerm = (1..n).shuffled(rnd)
        val g = IntArray(n * n)
        for (r in 0 until n) for (c in 0 until n) g[r * n + c] = symPerm[(rowPerm[r] + colPerm[c]) % n]
        return g
    }

    /** Counts solutions up to [limit]. */
    fun countSolutions(n: Int, givens: IntArray, h: IntArray, v: IntArray, limit: Int): Int {
        val g = IntArray(n * n)
        var count = 0
        fun go(cell: Int) {
            if (count >= limit) return
            if (cell == n * n) { count++; return }
            val r = cell / n
            val c = cell % n
            for (d in 1..n) {
                if (givens[cell] != 0 && givens[cell] != d) continue
                var ok = true
                for (i in 0 until c) if (g[r * n + i] == d) { ok = false; break }
                if (ok) for (i in 0 until r) if (g[i * n + c] == d) { ok = false; break }
                if (!ok) continue
                if (c > 0) {
                    val rel = h[r * (n - 1) + c - 1]
                    val left = g[cell - 1]
                    if (rel == 1 && !(left < d)) continue
                    if (rel == -1 && !(left > d)) continue
                }
                if (r > 0) {
                    val rel = v[(r - 1) * n + c]
                    val up = g[cell - n]
                    if (rel == 1 && !(up < d)) continue
                    if (rel == -1 && !(up > d)) continue
                }
                // forward check: a given to the right/below must satisfy relation with d
                if (c < n - 1 && givens[cell + 1] != 0) {
                    val rel = h[r * (n - 1) + c]
                    if (rel == 1 && !(d < givens[cell + 1])) continue
                    if (rel == -1 && !(d > givens[cell + 1])) continue
                }
                g[cell] = d
                go(cell + 1)
                g[cell] = 0
                if (count >= limit) return
            }
        }
        go(0)
        return count
    }

    /** Checks all rules for a fully filled grid. */
    fun isSolved(p: FutoshikiPuzzle, g: IntArray): Boolean {
        val n = p.size
        if (g.any { it == 0 }) return false
        for (i in 0 until n) {
            val row = (0 until n).map { g[i * n + it] }.toSet()
            val col = (0 until n).map { g[it * n + i] }.toSet()
            if (row.size != n || col.size != n) return false
        }
        for (r in 0 until n) for (c in 0 until n - 1) {
            val rel = p.hRel[r * (n - 1) + c]
            val a = g[r * n + c]
            val b = g[r * n + c + 1]
            if (rel == 1 && a >= b) return false
            if (rel == -1 && a <= b) return false
        }
        for (r in 0 until n - 1) for (c in 0 until n) {
            val rel = p.vRel[r * n + c]
            val a = g[r * n + c]
            val b = g[(r + 1) * n + c]
            if (rel == 1 && a >= b) return false
            if (rel == -1 && a <= b) return false
        }
        for (i in g.indices) if (p.givens[i] != 0 && p.givens[i] != g[i]) return false
        return true
    }
}

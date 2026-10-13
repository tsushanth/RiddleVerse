package com.kreativekoala.riddleverse

import kotlin.random.Random

/**
 * Conduit (pipe-connect, rotating tiles) engine. Pure Kotlin.
 * Masks: N=1, E=2, S=4, W=8. A level is a random spanning tree rooted at the source, so a solution
 * always exists; tiles are then scrambled by random rotations.
 */
class ConduitPuzzle(val n: Int, val source: Int, val solution: IntArray, val start: IntArray) {
    /** minimum number of taps needed to reach the generator's solution */
    val par: Int = start.indices.sumOf { ConduitEngine.tapsToReach(start[it], solution[it]) }
}

object ConduitEngine {
    const val N = 1
    const val E = 2
    const val S = 4
    const val W = 8
    private val DR = intArrayOf(-1, 0, 1, 0)
    private val DC = intArrayOf(0, 1, 0, -1)
    private val BIT = intArrayOf(N, E, S, W)

    fun sizeFor(difficulty: String) = when (difficulty.lowercase()) { "easy" -> 4; "hard" -> 7; else -> 5 }

    fun rotCW(m: Int): Int = ((m shl 1) or (m shr 3)) and 15

    /** taps (clockwise) to turn [from] into [to], or 0 if not reachable (different shape) */
    fun tapsToReach(from: Int, to: Int): Int {
        var m = from
        for (t in 0..3) { if (m == to) return t; m = rotCW(m) }
        return 0
    }

    fun opposite(d: Int) = (d + 2) % 4

    fun neighbour(n: Int, i: Int, d: Int): Int {
        val r = i / n + DR[d]; val c = i % n + DC[d]
        return if (r < 0 || c < 0 || r >= n || c >= n) -1 else r * n + c
    }

    fun generate(difficulty: String, seed: Long): ConduitPuzzle {
        val n = sizeFor(difficulty)
        val rnd = Random(seed)
        for (attempt in 0 until 50) {
            val source = (n / 2) * n + (n / 2)
            val sol = IntArray(n * n)
            val inTree = BooleanArray(n * n)
            inTree[source] = true
            val frontier = ArrayList<IntArray>() // (from, dir)
            fun addFrontier(i: Int) {
                for (d in 0..3) {
                    val nb = neighbour(n, i, d)
                    if (nb >= 0 && !inTree[nb]) frontier.add(intArrayOf(i, d))
                }
            }
            addFrontier(source)
            while (frontier.isNotEmpty()) {
                val k = rnd.nextInt(frontier.size)
                val f = frontier[k]
                frontier[k] = frontier[frontier.size - 1]; frontier.removeAt(frontier.size - 1)
                val to = neighbour(n, f[0], f[1])
                if (to < 0 || inTree[to]) continue
                inTree[to] = true
                sol[f[0]] = sol[f[0]] or BIT[f[1]]
                sol[to] = sol[to] or BIT[opposite(f[1])]
                addFrontier(to)
            }
            val start = IntArray(n * n) { i ->
                var m = sol[i]
                repeat(rnd.nextInt(4)) { m = rotCW(m) }
                m
            }
            val p = ConduitPuzzle(n, source, sol, start)
            if (!isSolved(n, source, start) && p.par >= n * n / 3) return p
        }
        // deterministic fallback: rotate every non-symmetric tile once
        val n2 = sizeFor(difficulty)
        val rnd2 = Random(seed)
        val sol = IntArray(n2 * n2)
        // simple comb tree
        for (r in 0 until n2) for (c in 0 until n2) {
            val i = r * n2 + c
            if (c + 1 < n2) { sol[i] = sol[i] or E; sol[i + 1] = sol[i + 1] or W }
        }
        for (r in 0 until n2 - 1) { val i = r * n2; sol[i] = sol[i] or S; sol[i + n2] = sol[i + n2] or N }
        // remove cycles: only column 0 vertical + all rows horizontal => a comb (tree)
        val start = IntArray(n2 * n2) { var m = sol[it]; repeat(1 + rnd2.nextInt(3)) { m = rotCW(m) }; m }
        return ConduitPuzzle(n2, 0, sol, start)
    }

    /** Powered cells: flood from source across matched connections. */
    fun powered(n: Int, source: Int, masks: IntArray): BooleanArray {
        val on = BooleanArray(n * n)
        val stack = IntArray(n * n); var sp = 0
        stack[sp++] = source; on[source] = true
        while (sp > 0) {
            val c = stack[--sp]
            for (d in 0..3) {
                if (masks[c] and BIT[d] == 0) continue
                val nb = neighbour(n, c, d)
                if (nb < 0 || on[nb]) continue
                if (masks[nb] and BIT[opposite(d)] == 0) continue
                on[nb] = true; stack[sp++] = nb
            }
        }
        return on
    }

    /** No pipe end points at a wall or at a tile that does not connect back. */
    fun noLeaks(n: Int, masks: IntArray): Boolean {
        for (i in masks.indices) for (d in 0..3) {
            if (masks[i] and BIT[d] == 0) continue
            val nb = neighbour(n, i, d)
            if (nb < 0 || masks[nb] and BIT[opposite(d)] == 0) return false
        }
        return true
    }

    fun isSolved(n: Int, source: Int, masks: IntArray): Boolean =
        noLeaks(n, masks) && powered(n, source, masks).all { it }

    /**
     * Backtracking solver used for verification and tests: finds any rotation assignment (as final masks)
     * that is leak-free and fully powered. Returns null if none exists.
     */
    fun solve(n: Int, source: Int, start: IntArray): IntArray? {
        val cur = IntArray(n * n)
        fun options(m: Int): List<Int> {
            val out = ArrayList<Int>(4); var x = m
            repeat(4) { if (x !in out) out.add(x); x = rotCW(x) }
            return out
        }
        fun rec(i: Int): Boolean {
            if (i == n * n) return powered(n, source, cur).all { it }
            val r = i / n; val c = i % n
            for (m in options(start[i])) {
                if (r == 0 && m and N != 0) continue
                if (c == 0 && m and W != 0) continue
                if (r == n - 1 && m and S != 0) continue
                if (c == n - 1 && m and E != 0) continue
                if (r > 0 && ((cur[i - n] and S != 0) != (m and N != 0))) continue
                if (c > 0 && ((cur[i - 1] and E != 0) != (m and W != 0))) continue
                cur[i] = m
                if (rec(i + 1)) return true
            }
            return false
        }
        return if (rec(0)) cur.copyOf() else null
    }
}

class ConduitState(val puzzle: ConduitPuzzle) {
    val n = puzzle.n
    var masks = puzzle.start.copyOf()
    var moves = 0
        private set
    var hintsUsed = 0
    private val undoStack = ArrayList<Int>()

    fun canUndo() = undoStack.isNotEmpty()

    fun rotate(i: Int) {
        masks[i] = ConduitEngine.rotCW(masks[i]); moves++; undoStack.add(i)
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val i = undoStack.removeAt(undoStack.size - 1)
        repeat(3) { masks[i] = ConduitEngine.rotCW(masks[i]) }
        if (moves > 0) moves--
        return true
    }

    fun restart() { masks = puzzle.start.copyOf(); moves = 0; undoStack.clear() }

    fun solved() = ConduitEngine.isSolved(n, puzzle.source, masks)

    /** A tile that differs from the generator's solution, or -1. */
    fun hint(): Int {
        val i = masks.indices.firstOrNull { masks[it] != puzzle.solution[it] } ?: return -1
        hintsUsed++
        return i
    }

    fun stars(): Int = when {
        moves <= puzzle.par + puzzle.par / 5 -> 3
        moves <= puzzle.par * 2 -> 2
        else -> 1
    }

    fun score(): Int = (40 + stars() * 20 - hintsUsed * 8).coerceAtLeast(10)

    fun encode(): String = "$moves;$hintsUsed;" + masks.joinToString(",")

    fun restore(s: String) {
        try {
            val p = s.split(';')
            moves = p[0].toInt(); hintsUsed = p[1].toInt()
            val m = p[2].split(',').map { it.toInt() }
            if (m.size == masks.size) masks = m.toIntArray()
            undoStack.clear()
        } catch (_: Exception) { restart() }
    }
}

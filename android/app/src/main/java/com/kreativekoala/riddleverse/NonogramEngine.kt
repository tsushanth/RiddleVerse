package com.kreativekoala.riddleverse

import kotlin.random.Random

/**
 * Pixel Logic (nonogram) engine. Pure Kotlin, no Android dependencies, so it is JVM unit-testable.
 *
 * Cell values in a player grid: 0 = unknown, 1 = filled, 2 = marked empty (X).
 * Solution grids use 1 = filled, 0 = empty.
 */
data class NonogramPuzzle(
    val size: Int,
    val solution: BooleanArray,       // size*size, row-major
    val rowClues: List<IntArray>,
    val colClues: List<IntArray>
) {
    fun solved(r: Int, c: Int) = solution[r * size + c]
}

object NonogramEngine {

    fun sizeFor(difficulty: String): Int = when (difficulty.lowercase()) {
        "easy" -> 5
        "hard" -> 15
        else -> 10
    }

    fun cluesOf(line: BooleanArray): IntArray {
        val out = ArrayList<Int>()
        var run = 0
        for (b in line) {
            if (b) run++ else if (run > 0) { out.add(run); run = 0 }
        }
        if (run > 0) out.add(run)
        return if (out.isEmpty()) intArrayOf(0) else out.toIntArray()
    }

    fun cluesFor(size: Int, sol: BooleanArray): Pair<List<IntArray>, List<IntArray>> {
        val rows = (0 until size).map { r -> cluesOf(BooleanArray(size) { c -> sol[r * size + c] }) }
        val cols = (0 until size).map { c -> cluesOf(BooleanArray(size) { r -> sol[r * size + c] }) }
        return rows to cols
    }

    /**
     * Line solver. [known] has -1 unknown, 0 empty, 1 filled. Returns the tightened line
     * (same encoding) or null if the line is contradictory.
     */
    fun solveLine(clues: IntArray, known: IntArray): IntArray? {
        val n = known.size
        val blocks = if (clues.size == 1 && clues[0] == 0) IntArray(0) else clues
        val k = blocks.size
        // ok[pos][j]: cells pos.. can host blocks j.. consistently
        val ok = Array(n + 2) { BooleanArray(k + 1) }
        for (pos in n downTo 0) {
            for (j in k downTo 0) {
                if (pos >= n) { ok[pos][j] = (j == k); continue }
                var res = false
                if (known[pos] != 1 && ok[pos + 1][j]) res = true
                if (!res && j < k) {
                    val len = blocks[j]
                    if (pos + len <= n) {
                        var fits = true
                        for (t in pos until pos + len) if (known[t] == 0) { fits = false; break }
                        if (fits) {
                            if (pos + len == n) res = ok[n][j + 1]
                            else if (known[pos + len] != 1) res = ok[pos + len + 1][j + 1]
                        }
                    }
                }
                ok[pos][j] = res
            }
        }
        if (!ok[0][0]) return null
        val reach = Array(n + 2) { BooleanArray(k + 1) }
        val canFill = BooleanArray(n)
        val canEmpty = BooleanArray(n)
        reach[0][0] = true
        for (pos in 0 until n) {
            for (j in 0..k) {
                if (!reach[pos][j]) continue
                if (known[pos] != 1 && ok[pos + 1][j]) {
                    canEmpty[pos] = true
                    reach[pos + 1][j] = true
                }
                if (j < k) {
                    val len = blocks[j]
                    if (pos + len <= n) {
                        var fits = true
                        for (t in pos until pos + len) if (known[t] == 0) { fits = false; break }
                        if (fits) {
                            if (pos + len == n) {
                                if (ok[n][j + 1]) {
                                    for (t in pos until n) canFill[t] = true
                                    reach[n][j + 1] = true
                                }
                            } else if (known[pos + len] != 1 && ok[pos + len + 1][j + 1]) {
                                for (t in pos until pos + len) canFill[t] = true
                                canEmpty[pos + len] = true
                                reach[pos + len + 1][j + 1] = true
                            }
                        }
                    }
                }
            }
        }
        return IntArray(n) { i ->
            when {
                canFill[i] && !canEmpty[i] -> 1
                canEmpty[i] && !canFill[i] -> 0
                else -> -1
            }
        }
    }

    /**
     * Propagates line logic to a fixed point. Returns the grid (-1/0/1) or null on contradiction.
     */
    fun propagate(size: Int, rows: List<IntArray>, cols: List<IntArray>, start: IntArray? = null): IntArray? {
        val g = start?.copyOf() ?: IntArray(size * size) { -1 }
        var changed = true
        while (changed) {
            changed = false
            for (r in 0 until size) {
                val line = IntArray(size) { g[r * size + it] }
                val res = solveLine(rows[r], line) ?: return null
                for (c in 0 until size) if (res[c] != line[c]) {
                    if (line[c] != -1) return null
                    g[r * size + c] = res[c]; changed = true
                }
            }
            for (c in 0 until size) {
                val line = IntArray(size) { g[it * size + c] }
                val res = solveLine(cols[c], line) ?: return null
                for (r in 0 until size) if (res[r] != line[r]) {
                    if (line[r] != -1) return null
                    g[r * size + c] = res[r]; changed = true
                }
            }
        }
        return g
    }

    /** True when line logic alone fully determines the grid (which implies uniqueness). */
    fun solvableByLogic(p: NonogramPuzzle): Boolean {
        val g = propagate(p.size, p.rowClues, p.colClues) ?: return false
        return g.all { it != -1 }
    }

    /**
     * Counts solutions (up to [limit]) using propagation + branching. Used for uniqueness tests
     * independent of the logic-only check.
     */
    fun countSolutions(size: Int, rows: List<IntArray>, cols: List<IntArray>, limit: Int = 2): Int {
        fun rec(state: IntArray): Int {
            val g = propagate(size, rows, cols, state) ?: return 0
            val idx = g.indexOfFirst { it == -1 }
            if (idx == -1) return 1
            var total = 0
            for (v in 0..1) {
                val next = g.copyOf(); next[idx] = v
                total += rec(next)
                if (total >= limit) return total
            }
            return total
        }
        return rec(IntArray(size * size) { -1 })
    }

    fun generate(difficulty: String, seed: Long): NonogramPuzzle {
        val size = sizeFor(difficulty)
        val rnd = Random(seed)
        var best: NonogramPuzzle? = null
        for (attempt in 0 until 400) {
            // Smooth-ish random picture: random fill plus a few blob dilations make nicer shapes.
            val density = 0.50 + rnd.nextDouble() * 0.15
            val sol = BooleanArray(size * size) { rnd.nextDouble() < density }
            if (size >= 10) {
                // clump: copy neighbour with some probability so pictures are less noisy
                for (i in 0 until size * size) {
                    val r = i / size; val c = i % size
                    if (r > 0 && c > 0 && rnd.nextDouble() < 0.35) sol[i] = sol[(r - 1) * size + c]
                }
            }
            if (sol.count { it } < size * size / 4) continue
            val (rc, cc) = cluesFor(size, sol)
            val p = NonogramPuzzle(size, sol, rc, cc)
            if (solvableByLogic(p)) return p
            if (best == null) best = p
        }
        // Fallback (extremely unlikely): repair by filling ambiguous cells until unique & logic solvable.
        var p = best!!
        val sol = p.solution.copyOf()
        for (step in 0 until size * size) {
            val g = propagate(size, p.rowClues, p.colClues) ?: break
            val unk = g.indices.filter { g[it] == -1 }
            if (unk.isEmpty()) return p
            sol[unk[rnd.nextInt(unk.size)]] = true
            val (rc, cc) = cluesFor(size, sol)
            p = NonogramPuzzle(size, sol.copyOf(), rc, cc)
        }
        // Last resort: a full row-banded picture is always logic-solvable.
        val banded = BooleanArray(size * size) { (it / size) % 2 == 0 || (it % size) == 0 }
        val (rc, cc) = cluesFor(size, banded)
        return NonogramPuzzle(size, banded, rc, cc)
    }
}

/** Mutable play state with undo, mark modes and hints. */
class NonogramState(val puzzle: NonogramPuzzle) {
    val size = puzzle.size
    val cells = IntArray(size * size)           // 0 unknown, 1 fill, 2 X
    private val history = ArrayList<IntArray>() // each entry: pairs (index, previousValue)
    var hintsUsed = 0
        private set
    var undoCount = 0
        private set

    fun canUndo() = history.isNotEmpty()

    /** Apply a stroke atomically: list of (index) set to [value] (toggle semantics handled by caller). */
    fun applyStroke(indices: IntArray, value: Int) {
        val rec = ArrayList<Int>()
        for (i in indices) {
            if (cells[i] != value) {
                rec.add(i); rec.add(cells[i])
                cells[i] = value
            }
        }
        if (rec.isNotEmpty()) history.add(rec.toIntArray())
    }

    private var strokeRec: ArrayList<Int>? = null

    /** Live drag painting: begin, paint cells, then end (one undo step) or cancel (revert). */
    fun beginStroke() { strokeRec = ArrayList() }

    fun paint(i: Int, value: Int) {
        val rec = strokeRec ?: return
        if (cells[i] != value) { rec.add(i); rec.add(cells[i]); cells[i] = value }
    }

    fun endStroke() {
        val rec = strokeRec ?: return
        strokeRec = null
        if (rec.isNotEmpty()) history.add(rec.toIntArray())
    }

    fun cancelStroke() {
        val rec = strokeRec ?: return
        strokeRec = null
        var i = rec.size - 2
        while (i >= 0) { cells[rec[i]] = rec[i + 1]; i -= 2 }
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        val rec = history.removeAt(history.size - 1)
        var i = rec.size - 2
        while (i >= 0) { cells[rec[i]] = rec[i + 1]; i -= 2 }
        undoCount++
        return true
    }

    fun clear() {
        if (cells.all { it == 0 }) return
        applyStroke(IntArray(cells.size) { it }, 0)
    }

    fun filledMatches(): Boolean {
        for (i in cells.indices) if ((cells[i] == 1) != puzzle.solution[i]) return false
        return true
    }

    fun isRowSatisfied(r: Int): Boolean =
        NonogramEngine.cluesOf(BooleanArray(size) { cells[r * size + it] == 1 }).contentEquals(puzzle.rowClues[r])

    fun isColSatisfied(c: Int): Boolean =
        NonogramEngine.cluesOf(BooleanArray(size) { cells[it * size + c] == 1 }).contentEquals(puzzle.colClues[c])

    /** Indices the player filled that are not part of the solution. */
    fun mistakes(): IntArray = cells.indices.filter { cells[it] == 1 && !puzzle.solution[it] }.toIntArray()

    /** Reveals one cell: first fixes a mistake, otherwise reveals a not-yet-correct solution cell. Returns index or -1. */
    fun hint(): Int {
        val bad = cells.indices.firstOrNull { cells[it] == 1 && !puzzle.solution[it] }
        val target = bad ?: cells.indices.firstOrNull { puzzle.solution[it] && cells[it] != 1 } ?: return -1
        val value = if (bad != null) 2 else 1
        applyStroke(intArrayOf(target), value)
        hintsUsed++
        return target
    }

    fun encode(): String {
        val sb = StringBuilder()
        sb.append(hintsUsed).append(',').append(undoCount).append('|')
        for (v in cells) sb.append(v)
        return sb.toString()
    }

    fun restore(s: String) {
        val parts = s.split('|')
        if (parts.size != 2 || parts[1].length != cells.size) return
        val head = parts[0].split(',')
        hintsUsed = head.getOrNull(0)?.toIntOrNull() ?: 0
        undoCount = head.getOrNull(1)?.toIntOrNull() ?: 0
        for (i in cells.indices) cells[i] = (parts[1][i] - '0').coerceIn(0, 2)
        history.clear()
    }

    fun score(): Int {
        val base = when (size) { 5 -> 60; 10 -> 100; else -> 150 }
        return (base - hintsUsed * 15).coerceAtLeast(base / 4)
    }
}

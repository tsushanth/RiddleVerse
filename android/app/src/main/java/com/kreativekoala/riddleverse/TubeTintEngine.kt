package com.kreativekoala.riddleverse

import kotlin.random.Random

/**
 * Tube Tint (liquid sort) engine. Pure Kotlin.
 *
 * A tube is a list of colour ids from bottom to top, with capacity [TubeTintEngine.CAPACITY].
 * Pour rule: pour the top run of one colour from A into B if B is empty or B's top is the same colour,
 * moving as many units as B has room for.
 */
class TubeTintPuzzle(val colors: Int, val tubes: List<IntArray>, val capacity: Int) {
    fun copyTubes(): List<MutableList<Int>> = tubes.map { it.toMutableList() }
}

object TubeTintEngine {
    const val CAPACITY = 4

    /** colours, empty tubes */
    fun configFor(difficulty: String): Pair<Int, Int> = when (difficulty.lowercase()) {
        "easy" -> 5 to 2
        "hard" -> 11 to 2
        else -> 8 to 2
    }

    fun topRun(t: List<Int>): Int {
        if (t.isEmpty()) return 0
        val c = t[t.size - 1]
        var n = 0
        var i = t.size - 1
        while (i >= 0 && t[i] == c) { n++; i-- }
        return n
    }

    fun canPour(tubes: List<List<Int>>, from: Int, to: Int, capacity: Int = CAPACITY): Boolean {
        if (from == to) return false
        val a = tubes[from]; val b = tubes[to]
        if (a.isEmpty() || b.size >= capacity) return false
        if (b.isNotEmpty() && b[b.size - 1] != a[a.size - 1]) return false
        // Pointless: pouring a uniform tube into an empty tube just relocates it.
        if (b.isEmpty() && a.all { it == a[0] }) return false
        return true
    }

    /** Executes the pour; returns number of units moved (0 if illegal). */
    fun pour(tubes: List<MutableList<Int>>, from: Int, to: Int, capacity: Int = CAPACITY): Int {
        if (!canPour(tubes, from, to, capacity)) return 0
        val a = tubes[from]; val b = tubes[to]
        val n = minOf(topRun(a), capacity - b.size)
        val c = a[a.size - 1]
        repeat(n) { a.removeAt(a.size - 1); b.add(c) }
        return n
    }

    fun isTubeDone(t: List<Int>, capacity: Int = CAPACITY) = t.size == capacity && t.all { it == t[0] }
    fun isSolved(tubes: List<List<Int>>, capacity: Int = CAPACITY): Boolean =
        tubes.all { it.isEmpty() || isTubeDone(it, capacity) }

    private fun key(tubes: List<List<Int>>): String {
        // Tube order is irrelevant for solvability; sort for canonical form.
        return tubes.map { t -> t.joinToString(",") }.sorted().joinToString("|")
    }

    /**
     * DFS solver with a visited set and node cap. Returns the move list (from,to) or null if none found
     * within [maxNodes]. Deterministic.
     */
    fun solve(start: List<List<Int>>, capacity: Int = CAPACITY, maxNodes: Int = 250_000): List<IntArray>? {
        val visited = HashSet<String>()
        var nodes = 0
        val path = ArrayList<IntArray>()
        val cur = start.map { it.toMutableList() }

        fun score(t: List<MutableList<Int>>, a: Int, b: Int): Int {
            // prefer pours that complete/extend runs and avoid revealing mixed tubes
            val src = t[a]; val dst = t[b]
            var s = 0
            if (dst.isNotEmpty()) s += 10 else s += 0
            if (dst.isNotEmpty() && dst.all { it == dst[0] }) s += 5
            val n = minOf(topRun(src), capacity - dst.size)
            if (n == src.size) s += 4
            return s + n
        }

        fun dfs(): Boolean {
            if (isSolved(cur, capacity)) return true
            if (++nodes > maxNodes) return false
            if (!visited.add(key(cur))) return false
            val moves = ArrayList<IntArray>()
            for (a in cur.indices) for (b in cur.indices) {
                if (canPour(cur, a, b, capacity)) moves.add(intArrayOf(a, b, score(cur, a, b)))
            }
            moves.sortByDescending { it[2] }
            for (m in moves) {
                val a = cur[m[0]]; val b = cur[m[1]]
                val aBefore = ArrayList(a); val bBefore = ArrayList(b)
                pour(cur, m[0], m[1], capacity)
                path.add(intArrayOf(m[0], m[1]))
                if (dfs()) return true
                path.removeAt(path.size - 1)
                a.clear(); a.addAll(aBefore); b.clear(); b.addAll(bBefore)
                if (nodes > maxNodes) return false
            }
            return false
        }
        return if (dfs()) path else null
    }

    /**
     * Generates a puzzle by starting from the solved layout and applying random REVERSE unit moves
     * (the inverse of a legal unit pour), then keeps only layouts the solver can finish.
     */
    fun generate(difficulty: String, seed: Long): TubeTintPuzzle {
        val (colors, empties) = configFor(difficulty)
        val cap = CAPACITY
        val rnd = Random(seed)
        for (attempt in 0 until 60) {
            val tubes = ArrayList<MutableList<Int>>()
            for (c in 0 until colors) tubes.add(MutableList(cap) { c })
            repeat(empties) { tubes.add(mutableListOf()) }
            val steps = colors * cap * 6
            var done = 0
            var guard = 0
            while (done < steps && guard < steps * 20) {
                guard++
                val b = rnd.nextInt(tubes.size)
                val tb = tubes[b]
                if (tb.isEmpty()) continue
                val c = tb[tb.size - 1]
                // reverse of a legal unit pour: unit below must be same colour (or none)
                if (tb.size >= 2 && tb[tb.size - 2] != c) continue
                val a = rnd.nextInt(tubes.size)
                if (a == b || tubes[a].size >= cap) continue
                tb.removeAt(tb.size - 1); tubes[a].add(c); done++
            }
            // shuffle tube order a bit so empties aren't always last
            tubes.shuffle(rnd)
            if (isSolved(tubes, cap)) continue
            if (tubes.count { isTubeDone(it, cap) } > colors / 2) continue
            if (solve(tubes, cap, 60_000) != null) {
                return TubeTintPuzzle(colors, tubes.map { it.toIntArray() }, cap)
            }
        }
        // Fallback: a trivially solvable layout (two tubes need a swap using the empties).
        val tubes = ArrayList<IntArray>()
        for (c in 0 until colors) tubes.add(IntArray(cap) { c })
        tubes[0][cap - 1] = 1; tubes[1][cap - 1] = 0
        repeat(empties) { tubes.add(IntArray(0)) }
        return TubeTintPuzzle(colors, tubes, cap)
    }
}

/** Play state with undo, restart, and add-tube support. */
class TubeTintState(private val puzzle: TubeTintPuzzle) {
    val capacity = puzzle.capacity
    var tubes: MutableList<MutableList<Int>> = puzzle.copyTubes().toMutableList()
    private val undoStack = ArrayList<Array<IntArray>>() // snapshots
    var moves = 0
        private set
    var extraTubeAdded = false
        private set
    var hintsUsed = 0

    private fun snapshot(): Array<IntArray> = Array(tubes.size) { tubes[it].toIntArray() }

    fun canUndo() = undoStack.isNotEmpty()

    fun pour(from: Int, to: Int): Boolean {
        if (!TubeTintEngine.canPour(tubes, from, to, capacity)) return false
        undoStack.add(snapshot())
        TubeTintEngine.pour(tubes, from, to, capacity)
        moves++
        return true
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val s = undoStack.removeAt(undoStack.size - 1)
        tubes = s.map { it.toMutableList() }.toMutableList()
        if (moves > 0) moves--
        return true
    }

    fun addTube(): Boolean {
        if (extraTubeAdded) return false
        undoStack.add(snapshot())
        tubes.add(mutableListOf())
        extraTubeAdded = true
        return true
    }

    fun restart() {
        tubes = puzzle.copyTubes().toMutableList()
        undoStack.clear(); moves = 0; extraTubeAdded = false
    }

    fun solved() = TubeTintEngine.isSolved(tubes, capacity)

    /** Next pour suggested by the solver, or null if the solver cannot finish from here. */
    fun hint(): IntArray? {
        val path = TubeTintEngine.solve(tubes, capacity, 120_000) ?: return null
        hintsUsed++
        return path.firstOrNull()
    }

    fun score(): Int {
        val c = puzzle.colors
        val base = 40 + c * 8
        val s = base - hintsUsed * 10 - (if (extraTubeAdded) 10 else 0) - (moves - c * 3).coerceAtLeast(0) / 2
        return s.coerceAtLeast(10)
    }

    fun encode(): String =
        "${moves};${if (extraTubeAdded) 1 else 0};${hintsUsed};" + tubes.joinToString("/") { it.joinToString(",") }

    fun restore(s: String) {
        try {
            val p = s.split(';')
            moves = p[0].toInt(); extraTubeAdded = p[1] == "1"; hintsUsed = p[2].toInt()
            tubes = p[3].split('/').map { t ->
                if (t.isEmpty()) mutableListOf<Int>() else t.split(',').map { it.toInt() }.toMutableList()
            }.toMutableList()
            undoStack.clear()
        } catch (_: Exception) {
            restart()
        }
    }
}

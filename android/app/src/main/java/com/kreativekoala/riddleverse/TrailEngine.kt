package com.kreativekoala.riddleverse

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

// --- trail (feat/game-trail) ---
// Trail: draw ONE continuous line from dot 1 through the numbered dots in order, visiting every cell exactly once.
// Optional walls sit between neighbouring cells and the line can never cross them. Pure Kotlin, deterministic by seed.
// Every generated puzzle has exactly one solution (verified by [TrailSolver], which counts solutions up to 2).

/**
 * @param waypoints cell index of dot k+1 (so waypoints[0] is where the line starts and the last entry is where it ends)
 * @param wallE wallE[i] = wall between cell i and the cell to its right
 * @param wallS wallS[i] = wall between cell i and the cell below it
 * @param solution the unique solution: every cell, in visiting order
 * @param band 0 easy, 1 medium, 2 hard
 * @param nodes solver search nodes needed to prove the solution unique (a branching measure of difficulty)
 */
class TrailPuzzle(
    val n: Int,
    val waypoints: IntArray,
    val wallE: BooleanArray,
    val wallS: BooleanArray,
    val solution: IntArray,
    val band: Int,
    val nodes: Int
) {
    val cells: Int get() = n * n
    val wallCount: Int = wallE.count { it } + wallS.count { it }
    private val dotAt = IntArray(n * n) { -1 }.also { a -> waypoints.forEachIndexed { k, c -> a[c] = k } }

    /** 0-based index of the dot on [cell], or -1 */
    fun dotAt(cell: Int): Int = dotAt[cell]

    fun adjacent(a: Int, b: Int): Boolean {
        val d = abs(a - b)
        return (d == n) || (d == 1 && a / n == b / n)
    }

    /** true when the line may NOT go between neighbouring cells a and b (wall, or not neighbours at all) */
    fun blocked(a: Int, b: Int): Boolean {
        if (a < 0 || b < 0 || a >= cells || b >= cells || !adjacent(a, b)) return true
        return when (b - a) {
            1 -> wallE[a]
            -1 -> wallE[b]
            n -> wallS[a]
            else -> wallS[b]
        }
    }

    /** Independent validity check (used by tests and by the generator's self-check). */
    fun isValidSolution(path: IntArray): Boolean {
        if (path.size != cells) return false
        val seen = BooleanArray(cells)
        var nextDot = 0
        for (i in path.indices) {
            val c = path[i]
            if (c < 0 || c >= cells || seen[c]) return false
            seen[c] = true
            if (i > 0 && blocked(path[i - 1], c)) return false
            val d = dotAt[c]
            if (d >= 0) { if (d != nextDot) return false; nextDot++ }
        }
        return nextDot == waypoints.size && path[0] == waypoints[0] && path[cells - 1] == waypoints[waypoints.size - 1]
    }
}

class TrailSolveResult(val count: Int, val nodes: Int, val aborted: Boolean, val solutions: List<IntArray>)

/**
 * Counts solutions (up to [limit]) of a Trail puzzle with a depth-first search over 64-bit cell masks.
 * Pruning: dead-end cells, connectivity of the unvisited region, the next dot being reachable without passing another
 * dot, and the final dot only being enterable last.
 */
internal class TrailSolver(
    private val n: Int,
    private val wp: IntArray,
    wallE: BooleanArray,
    wallS: BooleanArray,
    private val limit: Int,
    private val nodeLimit: Int
) {
    private val m = n * n
    private val all = if (m >= 64) -1L else (1L shl m) - 1L
    private val nbr = LongArray(m)
    private var canE = 0L
    private var canW = 0L
    private var canS = 0L
    private var canN = 0L
    private val wpAt = IntArray(m) { -1 }
    private val endCell = wp[wp.size - 1]
    private val endBit = 1L shl endCell
    private var wpMask = 0L
    private val path = IntArray(m)
    private val found = ArrayList<IntArray>()
    var count = 0; private set
    var nodes = 0; private set
    var aborted = false; private set

    init {
        for (c in 0 until m) {
            val col = c % n
            if (col + 1 < n && !wallE[c]) {
                canE = canE or (1L shl c); canW = canW or (1L shl (c + 1))
                nbr[c] = nbr[c] or (1L shl (c + 1)); nbr[c + 1] = nbr[c + 1] or (1L shl c)
            }
            if (c + n < m && !wallS[c]) {
                canS = canS or (1L shl c); canN = canN or (1L shl (c + n))
                nbr[c] = nbr[c] or (1L shl (c + n)); nbr[c + n] = nbr[c + n] or (1L shl c)
            }
        }
        wp.forEachIndexed { k, c -> wpAt[c] = k; wpMask = wpMask or (1L shl c) }
    }

    fun run(): TrailSolveResult {
        dfs(wp[0], 1L shl wp[0], 1, 1)
        return TrailSolveResult(count, nodes, aborted, found)
    }

    private fun spread(r: Long, mask: Long): Long =
        (r or ((r and canE) shl 1) or ((r and canW) ushr 1) or ((r and canS) shl n) or ((r and canN) ushr n)) and mask

    private fun feasible(cur: Int, visited: Long, k: Int): Boolean {
        val u = all and visited.inv()
        if (u == 0L) return true
        val avail = u or (1L shl cur)
        val h0 = (avail ushr 1) and canE
        val h1 = (avail shl 1) and canW
        val h2 = (avail ushr n) and canS
        val h3 = (avail shl n) and canN
        var ones = h0
        var twos = 0L
        twos = twos or (ones and h1); ones = ones xor h1
        twos = twos or (ones and h2); ones = ones xor h2
        twos = twos or (ones and h3)
        val any = h0 or h1 or h2 or h3
        // every unvisited cell needs two usable neighbours (in and out); only the final dot may be a dead end
        if ((u and twos.inv() and endBit.inv()) != 0L) return false
        if ((u and endBit) != 0L && (any and endBit) == 0L) return false
        // unvisited region must be one connected piece reachable from here
        var reach = nbr[cur] and u
        if (reach == 0L) return false
        while (true) {
            val nx = spread(reach, u)
            if (nx == reach) break
            reach = nx
        }
        if (reach != u) return false
        // the next dot must be reachable without passing through a later dot
        val tgt = wp[k]
        val free = (u and wpMask.inv()) or (1L shl tgt)
        var r2 = nbr[cur] and free
        if ((r2 and (1L shl tgt)) == 0L) {
            while (true) {
                val nx = spread(r2, free)
                if (nx == r2) return false
                r2 = nx
                if ((r2 and (1L shl tgt)) != 0L) break
            }
        }
        return true
    }

    private fun dfs(cur: Int, visited: Long, k: Int, depth: Int) {
        if (aborted || count >= limit) return
        nodes++
        if (nodes > nodeLimit) { aborted = true; return }
        path[depth - 1] = cur
        if (depth == m) {
            count++
            found.add(path.copyOf())
            return
        }
        var cand = nbr[cur] and all and visited.inv()
        while (cand != 0L) {
            val nb = cand.countTrailingZeroBits()
            cand = cand and (cand - 1)
            val wi = wpAt[nb]
            if (wi >= 0 && wi != k) continue
            if (nb == endCell && depth != m - 1) continue
            val nv = visited or (1L shl nb)
            val nk = if (wi >= 0) k + 1 else k
            if (!feasible(nb, nv, nk)) continue
            dfs(nb, nv, nk, depth + 1)
            if (aborted || count >= limit) return
        }
    }
}

object TrailEngine {
    const val MIN_N = 5
    const val MAX_N = 8

    /** Per-solve and per-puzzle search budgets. Counted in nodes (not time) so generation stays deterministic. */
    private const val SOLVE_NODE_LIMIT = 30_000
    private const val REDUCE_NODE_BUDGET = 60_000

    fun bandFor(difficulty: String): Int = when (difficulty.lowercase()) { "easy" -> 0; "hard" -> 2; else -> 1 }

    /** Easy 5/6, Medium 6/7, Hard 7/8; the seed picks which of the two. */
    fun sizeFor(difficulty: String, seed: Long): Int {
        val hi = (seed and 1L) == 1L
        return when (bandFor(difficulty)) { 0 -> if (hi) 6 else 5; 2 -> if (hi) 8 else 7; else -> if (hi) 7 else 6 }
    }

    fun generate(difficulty: String, seed: Long): TrailPuzzle =
        generate(sizeFor(difficulty, seed), bandFor(difficulty), seed)

    fun solve(p: TrailPuzzle, limit: Int = 2, nodeLimit: Int = 2_000_000): TrailSolveResult =
        TrailSolver(p.n, p.waypoints, p.wallE, p.wallS, limit, nodeLimit).run()

    fun solve(n: Int, waypoints: IntArray, wallE: BooleanArray, wallS: BooleanArray, limit: Int = 2, nodeLimit: Int = 2_000_000) =
        TrailSolver(n, waypoints, wallE, wallS, limit, nodeLimit).run()

    /** A uniformly-ish random Hamiltonian path on the n x n grid (backbite moves from a snake). */
    internal fun randomHamiltonianPath(n: Int, rnd: Random): IntArray {
        val m = n * n
        val path = IntArray(m) { i -> val r = i / n; val k = i % n; r * n + (if (r % 2 == 0) k else n - 1 - k) }
        val pos = IntArray(m)
        for (i in 0 until m) pos[path[i]] = i
        fun reverse(from: Int, to: Int) {
            var a = from; var b = to
            while (a < b) {
                val t = path[a]; path[a] = path[b]; path[b] = t
                pos[path[a]] = a; pos[path[b]] = b
                a++; b--
            }
        }
        val nb = IntArray(4)
        repeat(40 * m) {
            if (rnd.nextBoolean()) reverse(0, m - 1)
            val e = path[m - 1]
            val r = e / n; val c = e % n
            var cnt = 0
            if (r > 0) nb[cnt++] = e - n
            if (r < n - 1) nb[cnt++] = e + n
            if (c > 0) nb[cnt++] = e - 1
            if (c < n - 1) nb[cnt++] = e + 1
            val u = nb[rnd.nextInt(cnt)]
            if (u != path[m - 2]) reverse(pos[u] + 1, m - 1)
        }
        return path
    }

    fun generate(n: Int, band: Int, seed: Long): TrailPuzzle {
        require(n in 2..8) { "n must be 2..8" }
        val m = n * n
        var salt = 0
        while (true) {
            val rnd = Random(seed * 0x9E3779B97F4A7C15uL.toLong() + n * 1_000_003L + band * 7919L + salt * 104_729L)
            salt++
            val p = attempt(n, m, band, seed, rnd)
            if (p != null) return p
        }
    }

    private fun attempt(n: Int, m: Int, band: Int, seed: Long, rnd: Random): TrailPuzzle? {
        val sol = randomHamiltonianPath(n, rnd)
        val pos = IntArray(m).also { for (i in 0 until m) it[sol[i]] = i }
        val wallE = BooleanArray(m)
        val wallS = BooleanArray(m)
        // waypoints stored as indices along the solution (sorted), so dot order is always the solution order
        val wpIdx = java.util.TreeSet<Int>().apply { add(0); add(m - 1) }
        val initial = when (band) { 0 -> max(3, (m * 0.30).toInt()); 1 -> max(3, (m * 0.14).toInt()); else -> 3 }
        while (wpIdx.size < initial) wpIdx.add(rnd.nextInt(m))
        val wallBias = when (band) { 0 -> 0.0; 1 -> 0.25; else -> 0.55 }
        var spent = 0

        fun wpCells() = IntArray(wpIdx.size).also { var i = 0; for (x in wpIdx) it[i++] = sol[x] }

        fun addWallBetween(a: Int, b: Int) {
            if (b == a + 1) wallE[a] = true else if (b == a - 1) wallE[b] = true else if (b == a + n) wallS[a] = true else wallS[b] = true
        }

        var result: TrailSolveResult
        var rounds = 0
        while (true) {
            if (++rounds > 400) return null
            result = TrailSolver(n, wpCells(), wallE, wallS, 2, SOLVE_NODE_LIMIT).run()
            spent += result.nodes
            if (result.aborted) {
                // too hard to prove within budget: give the player more dots
                var added = 0
                while (added < 2 && wpIdx.size < m) { if (wpIdx.add(rnd.nextInt(m))) added++ }
                continue
            }
            if (result.count == 1) break
            if (result.count == 0) return null // cannot happen (the generator path is always a solution); defensive
            // two solutions found: at least one differs from the generator's path
            val q = result.solutions.firstOrNull { !it.contentEquals(sol) } ?: return null
            val posQ = IntArray(m).also { for (i in 0 until m) it[q[i]] = i }
            var done = false
            if (rnd.nextDouble() >= wallBias) {
                // add a dot where Q leaves the window between its neighbouring dots
                val cands = ArrayList<Int>()
                for (c in 0 until m) {
                    val i = pos[c]
                    if (wpIdx.contains(i)) continue
                    val a = wpIdx.lower(i)!!
                    val b = wpIdx.higher(i)!!
                    val pq = posQ[c]
                    if (pq < posQ[sol[a]] || pq > posQ[sol[b]]) cands.add(i)
                }
                if (cands.isNotEmpty()) { wpIdx.add(cands[rnd.nextInt(cands.size)]); done = true }
            }
            if (!done) {
                // add a wall on an edge Q uses but the generator path does not
                val cands = ArrayList<Int>()
                for (i in 0 until m - 1) {
                    val a = q[i]; val b = q[i + 1]
                    if (abs(pos[a] - pos[b]) != 1) cands.add(i)
                }
                if (cands.isEmpty()) return null
                val i = cands[rnd.nextInt(cands.size)]
                addWallBetween(q[i], q[i + 1])
            }
        }

        // reduction: drop dots / walls that are not needed for uniqueness (harder puzzles), within a node budget
        if (band >= 1) {
            val tries = if (band == 1) 4 else 10
            val removable = ArrayList<Int>()
            for (x in wpIdx) if (x != 0 && x != m - 1) removable.add(x)
            removable.shuffle(rnd)
            var t = 0
            for (x in removable) {
                if (t >= tries || spent > REDUCE_NODE_BUDGET || wpIdx.size <= 3) break
                t++
                wpIdx.remove(x)
                val r = TrailSolver(n, wpCells(), wallE, wallS, 2, SOLVE_NODE_LIMIT).run()
                spent += r.nodes
                if (r.aborted || r.count != 1) wpIdx.add(x)
            }
        }

        val finalRes = TrailSolver(n, wpCells(), wallE, wallS, 2, Int.MAX_VALUE).run()
        if (finalRes.count != 1) return null
        val puzzle = TrailPuzzle(n, wpCells(), wallE, wallS, sol, band, finalRes.nodes)
        return if (puzzle.isValidSolution(sol)) puzzle else null
    }
}

/** Pointer-to-cell maths, kept pure so flicks and diagonals can be unit tested. */
object TrailGeometry {
    /** cell under (x, y) on a board of n x n cells of side [cell] whose top-left corner is the origin, or -1 outside */
    fun cellAt(x: Float, y: Float, cell: Float, n: Int): Int {
        if (cell <= 0f) return -1
        val c = Math.floor((x / cell).toDouble()).toInt()
        val r = Math.floor((y / cell).toDouble()).toInt()
        return if (c < 0 || r < 0 || c >= n || r >= n) -1 else r * n + c
    }

    /** true when (x, y) is still inside [headCell] grown by [margin] x cell on every side (stops edge flicker) */
    fun stickyHit(x: Float, y: Float, headCell: Int, cell: Float, n: Int, margin: Float = 0.18f): Boolean {
        val c = headCell % n; val r = headCell / n
        val m = cell * margin
        return x >= c * cell - m && x <= (c + 1) * cell + m && y >= r * cell - m && y <= (r + 1) * cell + m
    }

    /**
     * Distinct cells crossed by the straight move from (x0, y0) to (x1, y1), sampled finely enough that a fast flick
     * cannot skip a cell. Points outside the board are clamped onto it.
     */
    fun cellsAlong(x0: Float, y0: Float, x1: Float, y1: Float, cell: Float, n: Int): IntArray {
        val side = cell * n
        val eps = 0.001f
        fun clamp(v: Float) = v.coerceIn(0f, side - eps)
        val out = ArrayList<Int>()
        val dist = hypot(x1 - x0, y1 - y0)
        val steps = max(1, ceil(dist / (cell * 0.2f)).toInt())
        var last = -1
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val c = cellAt(clamp(x0 + (x1 - x0) * t), clamp(y0 + (y1 - y0) * t), cell, n)
            if (c >= 0 && c != last) { out.add(c); last = c }
        }
        return out.toIntArray()
    }
}

/** The player's line. Pure state so every rule is unit tested. */
class TrailState(val p: TrailPuzzle) {
    private val m = p.cells
    private val path = IntArray(m)
    private val onPath = BooleanArray(m)
    private val posOnPath = IntArray(m)
    var length = 0; private set
    var dotsVisited = 0; private set
    var hintsUsed = 0; private set
    var resets = 0; private set

    init { clear() }

    private fun clear() {
        java.util.Arrays.fill(onPath, false)
        length = 0; dotsVisited = 0
        push(p.waypoints[0])
    }

    val head: Int get() = path[length - 1]
    fun cellAtStep(i: Int): Int = path[i]
    fun onPath(cell: Int): Boolean = onPath[cell]
    fun pathCells(): IntArray = path.copyOf(length)
    val totalDots: Int get() = p.waypoints.size

    private fun push(c: Int) {
        path[length] = c; posOnPath[c] = length; length++
        onPath[c] = true
        if (p.dotAt(c) >= 0) dotsVisited++
    }

    private fun pop() {
        val c = path[length - 1]
        length--
        onPath[c] = false
        if (p.dotAt(c) >= 0) dotsVisited--
    }

    fun canStep(from: Int, to: Int): Boolean {
        if (p.blocked(from, to) || onPath[to]) return false
        val d = p.dotAt(to)
        if (d >= 0 && d != dotsVisited) return false // dots must be reached in order
        // the last dot may only close the line once every other cell is covered
        if (d == totalDots - 1 && length != m - 1) return false
        return true
    }

    /** Extend by one cell, or step back one cell when [to] is the previous cell. Returns true if the line changed. */
    fun step(to: Int): Boolean {
        if (length >= 2 && to == path[length - 2]) { pop(); return true }
        if (!canStep(head, to)) return false
        push(to)
        return true
    }

    /** Remove the last cell (never the start). */
    fun undo(): Boolean { if (length <= 1) return false; pop(); return true }

    fun truncateTo(cell: Int): Boolean {
        if (!onPath[cell] || cell == head) return false
        while (head != cell) pop()
        return true
    }

    /**
     * Move the line's end towards [target]: dragging onto a cell already on the line cuts back to it; otherwise walk
     * towards the target one cell at a time, choosing a legal orthogonal step so diagonal drags and fast flicks that jump
     * several cells still work. Stops where the walk gets blocked.
     */
    fun advanceTo(target: Int): Boolean {
        if (target < 0 || target >= m || target == head) return false
        if (onPath[target]) return truncateTo(target)
        var changed = false
        var guard = 0
        while (head != target && guard++ < 2 * p.n) {
            val h = head
            val dr = target / p.n - h / p.n
            val dc = target % p.n - h % p.n
            val vert = if (dr != 0) h + (if (dr > 0) p.n else -p.n) else -1
            val horiz = if (dc != 0) h + (if (dc > 0) 1 else -1) else -1
            val first = if (abs(dr) >= abs(dc)) vert else horiz
            val second = if (abs(dr) >= abs(dc)) horiz else vert
            val next = when {
                first >= 0 && canStep(h, first) -> first
                second >= 0 && canStep(h, second) -> second
                else -> -1
            }
            if (next < 0) break
            push(next)
            changed = true
            if (onPath[target] && head == target) break
        }
        return changed
    }

    /** Tap (screen reader friendly): cut back to a cell on the line, or extend onto an adjacent cell. */
    fun tap(cell: Int): Boolean {
        if (cell < 0 || cell >= m || cell == head) return false
        if (onPath[cell]) return truncateTo(cell)
        return if (p.adjacent(head, cell)) step(cell) else false
    }

    fun reset() { clear(); resets++ }

    fun solved(): Boolean = length == m && head == p.waypoints[totalDots - 1] && dotsVisited == totalDots

    /** Number of leading cells that match the solution. */
    fun correctPrefix(): Int {
        var i = 0
        while (i < length && path[i] == p.solution[i]) i++
        return i
    }

    /**
     * Reveals the next [count] cells of the solution. If the line has strayed from the solution it is first cut back to
     * the last correct cell. Returns the revealed cells (empty when already solved).
     */
    fun hint(count: Int = 3): IntArray {
        val ok = correctPrefix()
        if (ok < length) truncateTo(p.solution[ok - 1])
        hintsUsed++
        val from = ok.coerceAtMost(m)
        val to = (from + count).coerceAtMost(m)
        return if (from >= m) IntArray(0) else p.solution.copyOfRange(from, to)
    }

    fun stars(): Int = when { hintsUsed == 0 -> 3; hintsUsed <= 2 -> 2; else -> 1 }
    fun score(): Int = (40 + stars() * 20 + p.band * 10 - hintsUsed * 8).coerceAtLeast(10)

    fun encode(): String = pathCells().joinToString(",") + "|" + hintsUsed + "|" + resets
    fun restore(s: String) {
        try {
            val parts = s.split("|")
            val cells = parts[0].split(",").filter { it.isNotEmpty() }.map { it.toInt() }
            if (cells.isEmpty() || cells[0] != p.waypoints[0]) return
            clear()
            for (i in 1 until cells.size) if (!step(cells[i])) break
            hintsUsed = parts.getOrNull(1)?.toIntOrNull() ?: 0
            resets = parts.getOrNull(2)?.toIntOrNull() ?: 0
        } catch (_: Exception) { clear() }
    }
}

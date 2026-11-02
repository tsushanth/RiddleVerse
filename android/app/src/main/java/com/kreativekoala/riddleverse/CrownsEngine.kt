package com.kreativekoala.riddleverse

import kotlin.math.abs
import kotlin.random.Random

/**
 * Crowns engine. Pure Kotlin, no Android dependencies, so it is JVM unit-testable.
 *
 * An N x N grid is split into N connected coloured regions. Place exactly one crown in every row, every column and
 * every region, with no two crowns touching (diagonals included).
 *
 * Generation, deterministic by seed:
 *  1. build a valid crown placement (one per row and column, no touching),
 *  2. grow N connected regions outwards from the crown cells,
 *  3. count solutions with a backtracking solver; while there is a second solution, move one cell of the alternative
 *     solution into a neighbouring region (keeping the first solution intact) until the solution is unique,
 *  4. rate the puzzle with a logic solver (band 1..4) and keep the candidate that best matches the wanted band.
 *
 * Cell values in a player grid: 0 = empty, 1 = mark (x), 2 = crown.
 */
class CrownsPuzzle(
    val n: Int,
    /** region id (0 until n) of every cell, row-major */
    val regions: IntArray,
    /** solution[row] = column of the crown in that row */
    val solution: IntArray,
    /** hardest technique needed: 1 singles, 2 pointing/confinement, 3 grouped confinement, 4 trial and error */
    val band: Int,
    /** number of trial placements the logic solver needed (0 below band 4) */
    val trials: Int
) {
    fun regionOf(row: Int, col: Int) = regions[row * n + col]
    fun isSolutionCell(index: Int) = solution[index / n] == index % n
}

object CrownsEngine {
    const val MIN_N = 5
    const val MAX_N = 9

    /** Candidates kept per puzzle while chasing the wanted band. Bounded so generation time stays predictable. */
    private const val MAX_CANDIDATES = 10
    private const val MAX_REPAIRS = 160

    /** Grid size for a start-screen difficulty. A plain "5".."9" selects that size directly. */
    fun sizeFor(difficulty: String, seed: Long): Int {
        difficulty.trim().toIntOrNull()?.let { if (it in MIN_N..MAX_N) return it }
        val coin = Random(seed xor 0x5DEECE66DL).nextInt(2)
        return when (difficulty.lowercase()) {
            "easy" -> 5 + coin       // 5 or 6
            "hard" -> 8 + coin       // 8 or 9
            else -> 7
        }
    }

    /** Band range the generator aims for at a given grid size. */
    fun wantedBand(difficulty: String, n: Int): IntRange = when {
        difficulty.trim().toIntOrNull() != null -> if (n <= 6) 1..2 else if (n == 7) 2..3 else 3..4
        difficulty.equals("easy", true) -> 1..2
        difficulty.equals("hard", true) -> 3..4
        else -> 2..3
    }

    fun generate(difficulty: String, seed: Long): CrownsPuzzle {
        val n = sizeFor(difficulty, seed)
        return generate(n, seed, wantedBand(difficulty, n))
    }

    fun generate(n: Int, seed: Long, wanted: IntRange = 1..4): CrownsPuzzle {
        require(n in MIN_N..MAX_N) { "size $n out of range" }
        val rnd = Random(seed * 6364136223846793005L + n * 1442695040888963407L + 11)
        var best: CrownsPuzzle? = null
        var bestDist = Int.MAX_VALUE
        var found = 0
        while (found < MAX_CANDIDATES) {
            val candidate = buildUnique(n, rnd) ?: continue
            found++
            val (band, trials) = rate(n, candidate.second)
            val dist = when {
                band in wanted -> 0
                band < wanted.first -> wanted.first - band
                else -> band - wanted.last
            }
            if (dist < bestDist) {
                bestDist = dist
                best = CrownsPuzzle(n, candidate.second, candidate.first, band, trials)
            }
            if (dist == 0) break
        }
        return best!!
    }

    // ---------------------------------------------------------------- construction

    /** Returns (solution, regions) with exactly one solution, or null if the repair budget ran out. */
    private fun buildUnique(n: Int, rnd: Random): Pair<IntArray, IntArray>? {
        val sol = randomPlacement(n, rnd)
        val regions = grow(n, sol, rnd)
        repeat(MAX_REPAIRS) {
            val found = solutions(n, regions, 2)
            if (found.size == 1) return sol to regions
            val alt = found.firstOrNull { !it.contentEquals(sol) } ?: return sol to regions
            if (!repairOnce(n, regions, sol, alt, rnd)) return null
        }
        return null
    }

    private fun randomPlacement(n: Int, rnd: Random): IntArray {
        val cols = IntArray(n) { -1 }
        val used = BooleanArray(n)
        fun rec(r: Int): Boolean {
            if (r == n) return true
            for (c in (0 until n).shuffled(rnd)) {
                if (used[c]) continue
                if (r > 0 && abs(cols[r - 1] - c) < 2) continue
                cols[r] = c; used[c] = true
                if (rec(r + 1)) return true
                used[c] = false; cols[r] = -1
            }
            return false
        }
        check(rec(0))
        return cols
    }

    private val DR = intArrayOf(-1, 0, 1, 0)
    private val DC = intArrayOf(0, 1, 0, -1)

    private fun grow(n: Int, sol: IntArray, rnd: Random): IntArray {
        val reg = IntArray(n * n) { -1 }
        // region ids are shuffled so a region's colour never reveals the row of its crown
        val ids = (0 until n).shuffled(rnd)
        for (r in 0 until n) reg[r * n + sol[r]] = ids[r]
        var assigned = n
        val cand = IntArray(n * n * 4)
        while (assigned < n * n) {
            var k = 0
            for (i in 0 until n * n) {
                if (reg[i] != -1) continue
                val r = i / n; val c = i % n
                for (d in 0 until 4) {
                    val rr = r + DR[d]; val cc = c + DC[d]
                    if (rr in 0 until n && cc in 0 until n && reg[rr * n + cc] != -1) {
                        cand[k++] = i * 4 + d
                    }
                }
            }
            val pick = cand[rnd.nextInt(k)]
            val i = pick / 4; val d = pick % 4
            reg[i] = reg[(i / n + DR[d]) * n + (i % n + DC[d])]
            assigned++
        }
        return reg
    }

    /**
     * Kills the alternative solution [alt] by moving one of its cells into a neighbouring region.
     * [sol] always stays valid because only non-crown cells of [sol] move, and never a region's last cell.
     */
    private fun repairOnce(n: Int, reg: IntArray, sol: IntArray, alt: IntArray, rnd: Random): Boolean {
        val rows = (0 until n).filter { alt[it] != sol[it] }.shuffled(rnd)
        for (r in rows) {
            val x = r * n + alt[r]
            val from = reg[x]
            val moves = ArrayList<Int>()
            for (d in 0 until 4) {
                val rr = r + DR[d]; val cc = alt[r] + DC[d]
                if (rr in 0 until n && cc in 0 until n && reg[rr * n + cc] != from) moves.add(reg[rr * n + cc])
            }
            for (to in moves.shuffled(rnd)) {
                if (stillConnectedWithout(n, reg, from, x, sol)) {
                    reg[x] = to
                    return true
                }
            }
        }
        return false
    }

    /** True if region [id] minus cell [skip] is still one connected piece (flood from the region's crown cell). */
    private fun stillConnectedWithout(n: Int, reg: IntArray, id: Int, skip: Int, sol: IntArray): Boolean {
        var start = -1
        var total = 0
        for (i in 0 until n * n) if (reg[i] == id && i != skip) { total++; if (sol[i / n] == i % n) start = i }
        if (start == -1) return false
        val seen = BooleanArray(n * n)
        val stack = IntArray(n * n)
        var sp = 0
        stack[sp++] = start; seen[start] = true
        var count = 0
        while (sp > 0) {
            val i = stack[--sp]; count++
            val r = i / n; val c = i % n
            for (d in 0 until 4) {
                val rr = r + DR[d]; val cc = c + DC[d]
                if (rr !in 0 until n || cc !in 0 until n) continue
                val j = rr * n + cc
                if (j != skip && !seen[j] && reg[j] == id) { seen[j] = true; stack[sp++] = j }
            }
        }
        return count == total
    }

    // ---------------------------------------------------------------- exact solver

    /** Backtracking, one row at a time. Returns up to [limit] solutions (column per row). */
    fun solutions(n: Int, reg: IntArray, limit: Int): List<IntArray> {
        val out = ArrayList<IntArray>(limit)
        val cols = IntArray(n)
        fun rec(r: Int, colMask: Int, regMask: Int) {
            if (r == n) { out.add(cols.copyOf()); return }
            for (c in 0 until n) {
                if (out.size >= limit) return
                if (colMask and (1 shl c) != 0) continue
                if (r > 0 && abs(cols[r - 1] - c) < 2) continue
                val g = reg[r * n + c]
                if (regMask and (1 shl g) != 0) continue
                cols[r] = c
                rec(r + 1, colMask or (1 shl c), regMask or (1 shl g))
            }
        }
        rec(0, 0, 0)
        return out
    }

    fun countSolutions(n: Int, reg: IntArray, limit: Int = 2): Int = solutions(n, reg, limit).size

    // ---------------------------------------------------------------- rules

    /** True when [cols] (column per row) satisfies every rule for the region map [reg]. */
    fun isValidPlacement(n: Int, reg: IntArray, cols: IntArray): Boolean {
        if (cols.size != n) return false
        val colSeen = BooleanArray(n); val regSeen = BooleanArray(n)
        for (r in 0 until n) {
            val c = cols[r]
            if (c !in 0 until n || colSeen[c]) return false
            colSeen[c] = true
            val g = reg[r * n + c]
            if (regSeen[g]) return false
            regSeen[g] = true
            if (r > 0 && abs(cols[r - 1] - c) < 2) return false
        }
        return true
    }

    /** True if every region is a single edge-connected piece and ids are exactly 0 until n. */
    fun regionsConnected(n: Int, reg: IntArray): Boolean {
        for (id in 0 until n) {
            val first = reg.indexOfFirst { it == id }
            if (first == -1) return false
            val seen = BooleanArray(n * n)
            val stack = ArrayDeque<Int>()
            stack.add(first); seen[first] = true
            var count = 0
            while (stack.isNotEmpty()) {
                val i = stack.removeLast(); count++
                for (d in 0 until 4) {
                    val rr = i / n + DR[d]; val cc = i % n + DC[d]
                    if (rr !in 0 until n || cc !in 0 until n) continue
                    val j = rr * n + cc
                    if (!seen[j] && reg[j] == id) { seen[j] = true; stack.add(j) }
                }
            }
            if (count != reg.count { it == id }) return false
        }
        return true
    }

    // ---------------------------------------------------------------- logic rating

    /** Returns (band, trials). Band: 1 singles, 2 pointing, 3 grouped confinement, 4 needs trial and error. */
    fun rate(n: Int, reg: IntArray): Pair<Int, Int> {
        val l = Logic(n, reg)
        l.propagate()
        if (!l.bad && l.placed == n) return l.maxLevel to 0
        var trials = 0
        var progress = true
        while (progress && !l.bad && l.placed < n) {
            progress = false
            for (i in 0 until n * n) {
                if (!l.alive[i]) continue
                val probe = l.copy()
                probe.place(i)
                probe.propagate()
                trials++
                if (probe.bad) {
                    l.alive[i] = false
                    l.propagate()
                    progress = true
                    if (l.bad || l.placed == n) break
                }
            }
        }
        return 4 to trials
    }

    /** Candidate-elimination solver used only to rate difficulty. Dimensions: 0 row, 1 column, 2 region. */
    private class Logic(val n: Int, val reg: IntArray) {
        val total = n * n
        var alive = BooleanArray(total) { true }
        val done = Array(3) { BooleanArray(n) }
        var placed = 0
        var bad = false
        var maxLevel = 1

        fun copy(): Logic {
            val c = Logic(n, reg)
            c.alive = alive.copyOf()
            for (d in 0 until 3) done[d].copyInto(c.done[d])
            c.placed = placed; c.bad = bad; c.maxLevel = maxLevel
            return c
        }

        fun dim(d: Int, i: Int) = when (d) { 0 -> i / n; 1 -> i % n; else -> reg[i] }

        fun place(i: Int) {
            if (!alive[i]) { bad = true; return }
            val r = i / n; val c = i % n; val g = reg[i]
            placed++
            done[0][r] = true; done[1][c] = true; done[2][g] = true
            for (j in 0 until total) {
                if (!alive[j]) continue
                val jr = j / n; val jc = j % n
                if (jr == r || jc == c || reg[j] == g || (abs(jr - r) <= 1 && abs(jc - c) <= 1)) alive[j] = false
            }
        }

        /** Runs the rules, cheapest first, until nothing changes. */
        fun propagate() {
            while (!bad && placed < n) {
                if (singles()) continue
                if (bad) return
                if (pointing()) { maxLevel = maxOf(maxLevel, 2); continue }
                if (bad) return
                if (groups()) { maxLevel = maxOf(maxLevel, 3); continue }
                return
            }
        }

        private fun singles(): Boolean {
            var any = false
            for (d in 0 until 3) for (k in 0 until n) {
                if (done[d][k]) continue
                var cnt = 0; var at = -1
                for (i in 0 until total) if (alive[i] && dim(d, i) == k) { cnt++; at = i }
                if (cnt == 0) { bad = true; return any }
                if (cnt == 1) { place(at); any = true; if (bad) return true }
            }
            return any
        }

        /** Level 2: confinement of one item to one line, and cells that touch every candidate of an item. */
        private fun pointing(): Boolean {
            if (subsets(1, 1)) return true
            for (d in 0 until 3) for (k in 0 until n) {
                if (done[d][k]) continue
                val cells = (0 until total).filter { alive[it] && dim(d, it) == k }
                if (cells.isEmpty()) { bad = true; return false }
                var killed = false
                for (x in 0 until total) {
                    if (!alive[x] || dim(d, x) == k) continue
                    val xr = x / n; val xc = x % n
                    if (cells.all { abs(it / n - xr) <= 1 && abs(it % n - xc) <= 1 }) { alive[x] = false; killed = true }
                }
                if (killed) return true
            }
            return false
        }

        private fun groups(): Boolean = subsets(2, 4)

        private val pairs = arrayOf(2 to 0, 2 to 1, 0 to 2, 1 to 2, 0 to 1, 1 to 0)

        /**
         * k items of kind a whose candidates all sit inside k items of kind b: those k b-items belong to the a-items,
         * so every other candidate in them can go.
         */
        private fun subsets(kMin: Int, kMax: Int): Boolean {
            for ((a, b) in pairs) {
                val bm = IntArray(n)
                for (j in 0 until total) if (alive[j]) bm[dim(a, j)] = bm[dim(a, j)] or (1 shl dim(b, j))
                var itemMask = 0
                for (k in 0 until n) if (!done[a][k]) itemMask = itemMask or (1 shl k)
                val limit = 1 shl n
                for (s in 1 until limit) {
                    if (s and itemMask.inv() != 0) continue
                    val k = Integer.bitCount(s)
                    if (k < kMin || k > kMax) continue
                    var union = 0
                    for (item in 0 until n) if (s and (1 shl item) != 0) union = union or bm[item]
                    val pc = Integer.bitCount(union)
                    if (pc < k) { bad = true; return false }
                    if (pc != k) continue
                    var killed = false
                    for (j in 0 until total) {
                        if (!alive[j]) continue
                        if (union and (1 shl dim(b, j)) != 0 && s and (1 shl dim(a, j)) == 0) { alive[j] = false; killed = true }
                    }
                    if (killed) return true
                }
            }
            return false
        }
    }
}

/** Player-side state: tap cycling, conflicts, undo, hints. Pure Kotlin so it is unit-testable. */
class CrownsState(val puzzle: CrownsPuzzle) {
    val n = puzzle.n
    val cells = IntArray(n * n)
    var hintsUsed = 0
        private set
    private val undoStack = ArrayList<IntArray>()   // [index, previous value]

    fun canUndo() = undoStack.isNotEmpty()

    /** empty -> mark -> crown -> empty. Returns the new value. */
    fun tap(i: Int): Int {
        val v = (cells[i] + 1) % 3
        set(i, v)
        return v
    }

    fun nextValue(i: Int) = (cells[i] + 1) % 3

    private fun set(i: Int, v: Int) {
        if (cells[i] == v) return
        undoStack.add(intArrayOf(i, cells[i]))
        cells[i] = v
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val u = undoStack.removeAt(undoStack.size - 1)
        cells[u[0]] = u[1]
        return true
    }

    fun clear() {
        for (i in cells.indices) set(i, 0)
    }

    fun crownCount() = cells.count { it == 2 }

    /** Crowns that break a rule: same row, column or region as another crown, or touching one. */
    fun conflicts(): BooleanArray {
        val bad = BooleanArray(n * n)
        val crowns = cells.indices.filter { cells[it] == 2 }
        for (a in crowns.indices) for (b in a + 1 until crowns.size) {
            val i = crowns[a]; val j = crowns[b]
            val ri = i / n; val ci = i % n; val rj = j / n; val cj = j % n
            if (ri == rj || ci == cj || puzzle.regions[i] == puzzle.regions[j] ||
                (abs(ri - rj) <= 1 && abs(ci - cj) <= 1)
            ) { bad[i] = true; bad[j] = true }
        }
        return bad
    }

    fun hasConflicts() = conflicts().any { it }

    /** Solved when n crowns stand with no conflict: that is exactly one per row, column and region. */
    fun isSolved() = crownCount() == n && !hasConflicts()

    /** Rows, columns and regions that already hold a crown (for the progress readout and dimming). */
    fun rowHasCrown(r: Int) = (0 until n).any { cells[r * n + it] == 2 }

    class Hint(val cell: Int, val removed: Boolean)

    /**
     * Removes a wrong crown if there is one, otherwise places a correct crown in the smallest region still empty.
     * Returns null when the board is already solved.
     */
    fun hint(): Hint? {
        val wrong = cells.indices.firstOrNull { cells[it] == 2 && !puzzle.isSolutionCell(it) }
        if (wrong != null) {
            set(wrong, 0); hintsUsed++
            return Hint(wrong, true)
        }
        val placedRegions = BooleanArray(n)
        for (i in cells.indices) if (cells[i] == 2) placedRegions[puzzle.regions[i]] = true
        val sizes = IntArray(n)
        for (g in puzzle.regions) sizes[g]++
        var best = -1
        for (r in 0 until n) {
            val i = r * n + puzzle.solution[r]
            if (cells[i] == 2) continue
            if (best == -1 || sizes[puzzle.regions[i]] < sizes[puzzle.regions[best]]) best = i
        }
        if (best == -1) return null
        set(best, 2); hintsUsed++
        return Hint(best, false)
    }

    fun score(): Int = (n * 40 - hintsUsed * 30).coerceAtLeast(10)

    fun stars(): Int = when {
        hintsUsed == 0 -> 3
        hintsUsed <= 2 -> 2
        else -> 1
    }

    fun encode(): String = cells.joinToString("") { it.toString() } + ";" + hintsUsed

    fun restore(s: String) {
        val parts = s.split(";")
        val body = parts[0]
        if (body.length != cells.size) return
        for (i in body.indices) cells[i] = (body[i] - '0').coerceIn(0, 2)
        hintsUsed = parts.getOrNull(1)?.toIntOrNull() ?: 0
        undoStack.clear()
    }
}

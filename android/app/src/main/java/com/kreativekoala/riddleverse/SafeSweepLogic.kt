package com.kreativekoala.riddleverse

import kotlin.random.Random

// --- classic puzzles (feat/puzzles-classic) --- Safe Sweep: pure game logic (no Android deps)

data class SafeSweepConfig(val rows: Int, val cols: Int, val mines: Int, val sizeLabel: String)

object SafeSweepRules {
    fun configFor(tier: Int): SafeSweepConfig = when (tier) {
        0 -> SafeSweepConfig(8, 8, 8, "Small")
        1 -> SafeSweepConfig(8, 8, 11, "Small")
        2 -> SafeSweepConfig(10, 10, 16, "Medium")
        3 -> SafeSweepConfig(10, 10, 22, "Medium")
        else -> SafeSweepConfig(12, 14, 34, "Large")
    }
}

enum class SweepStatus { PLAYING, WON, LOST }

/** Cell state chars: '.' hidden, 'F' flagged, 'R' revealed. */
class SafeSweepGame private constructor(
    val rows: Int,
    val cols: Int,
    val mineCount: Int,
    val seed: Long,
    /** null until the first reveal (mines are placed then, so the first tap is always safe). */
    private val mines: BooleanArray?,
    private val state: CharArray,
    val status: SweepStatus,
    val explodedIndex: Int,
    val actions: Int,
) {
    val size get() = rows * cols
    val started get() = mines != null
    val flagCount get() = state.count { it == 'F' }
    val minesLeft get() = mineCount - flagCount
    val revealedCount get() = state.count { it == 'R' }

    fun isRevealed(i: Int) = state[i] == 'R'
    fun isFlagged(i: Int) = state[i] == 'F'
    fun isMine(i: Int) = mines?.get(i) == true

    fun neighbors(i: Int): List<Int> {
        val r = i / cols
        val c = i % cols
        val out = ArrayList<Int>(8)
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until rows && nc in 0 until cols) out.add(nr * cols + nc)
        }
        return out
    }

    fun adjacentMines(i: Int): Int = neighbors(i).count { isMine(it) }

    private fun copyWith(
        mines: BooleanArray? = this.mines,
        state: CharArray = this.state,
        status: SweepStatus = this.status,
        exploded: Int = this.explodedIndex,
    ) = SafeSweepGame(rows, cols, mineCount, seed, mines, state, status, exploded, actions + 1)

    fun toggleFlag(i: Int): SafeSweepGame {
        if (status != SweepStatus.PLAYING || state[i] == 'R') return this
        val s = state.copyOf()
        s[i] = if (s[i] == 'F') '.' else 'F'
        return copyWith(state = s)
    }

    /** Reveals a hidden cell, or chords on a satisfied revealed number. */
    fun tap(i: Int): SafeSweepGame {
        if (status != SweepStatus.PLAYING) return this
        return when (state[i]) {
            'F' -> this
            'R' -> chord(i)
            else -> reveal(listOf(i))
        }
    }

    fun chord(i: Int): SafeSweepGame {
        if (status != SweepStatus.PLAYING || state[i] != 'R') return this
        val n = adjacentMines(i)
        if (n == 0) return this
        val nb = neighbors(i)
        if (nb.count { state[it] == 'F' } != n) return this
        val targets = nb.filter { state[it] == '.' }
        if (targets.isEmpty()) return this
        return reveal(targets)
    }

    private fun reveal(targets: List<Int>): SafeSweepGame {
        val m: BooleanArray = mines ?: placeMines(targets.first())
        val s = state.copyOf()
        val stack = ArrayDeque<Int>()
        var exploded = -1
        for (t in targets) {
            if (s[t] != '.') continue
            if (m[t]) {
                exploded = t
                s[t] = 'R'
                continue
            }
            stack.addLast(t)
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                if (s[cur] != '.') continue
                s[cur] = 'R'
                if (adjacentOf(m, cur) == 0) {
                    for (nb in neighbors(cur)) if (s[nb] == '.') stack.addLast(nb)
                }
            }
        }
        if (exploded >= 0) {
            for (k in s.indices) if (m[k] && s[k] != 'F') s[k] = 'R'
            return copyWith(mines = m, state = s, status = SweepStatus.LOST, exploded = exploded)
        }
        val safeLeft = s.indices.count { !m[it] && s[it] != 'R' }
        if (safeLeft == 0) {
            for (k in s.indices) if (m[k]) s[k] = 'F'
            return copyWith(mines = m, state = s, status = SweepStatus.WON)
        }
        return copyWith(mines = m, state = s)
    }

    private fun adjacentOf(m: BooleanArray, i: Int) = neighbors(i).count { m[it] }

    /** Places mines avoiding [first] and (when there is room) its neighbours, so the first tap opens an area. */
    private fun placeMines(first: Int): BooleanArray {
        val rng = Random(seed)
        val protectedCells = (neighbors(first) + first).toSet()
        var pool = (0 until size).filter { it !in protectedCells }
        if (pool.size < mineCount) pool = (0 until size).filter { it != first }
        val arr = BooleanArray(size)
        pool.shuffled(rng).take(mineCount).forEach { arr[it] = true }
        return arr
    }

    fun encode(): String = listOf(
        rows, cols, mineCount, seed, status.name, explodedIndex, actions,
        mines?.joinToString("") { if (it) "1" else "0" } ?: "-",
        String(state),
    ).joinToString("|")

    companion object {
        fun newGame(tier: Int, seed: Long): SafeSweepGame {
            val c = SafeSweepRules.configFor(tier)
            return newGame(c.rows, c.cols, c.mines, seed)
        }

        fun newGame(rows: Int, cols: Int, mines: Int, seed: Long): SafeSweepGame {
            require(mines in 1 until rows * cols)
            return SafeSweepGame(rows, cols, mines, seed, null, CharArray(rows * cols) { '.' }, SweepStatus.PLAYING, -1, 0)
        }

        fun decode(s: String): SafeSweepGame? = try {
            val p = s.split("|")
            val rows = p[0].toInt()
            val cols = p[1].toInt()
            val mines = if (p[7] == "-") null else BooleanArray(rows * cols) { p[7][it] == '1' }
            val state = p[8].toCharArray()
            if (state.size != rows * cols) null
            else SafeSweepGame(rows, cols, p[2].toInt(), p[3].toLong(), mines, state, SweepStatus.valueOf(p[4]), p[5].toInt(), p[6].toInt())
        } catch (e: Exception) {
            null
        }

        /** Test/helper constructor with an explicit mine layout. */
        fun withMines(rows: Int, cols: Int, mineIdx: Set<Int>, seed: Long = 0): SafeSweepGame =
            SafeSweepGame(rows, cols, mineIdx.size, seed, BooleanArray(rows * cols) { it in mineIdx }, CharArray(rows * cols) { '.' }, SweepStatus.PLAYING, -1, 0)
    }
}

package com.kreativekoala.riddleverse

import kotlin.random.Random

// --- classic puzzles (feat/puzzles-classic) --- Light Flip: pure game logic (no Android deps)

object LightFlipRules {
    const val N = 5

    /** How many random presses are applied to the solved board, per difficulty tier. */
    fun scramblePresses(tier: Int): Int = when (tier) {
        0 -> 3
        1 -> 5
        2 -> 8
        3 -> 12
        else -> 16
    }

    /** Bitmask of cells toggled when pressing [idx] (itself + orthogonal neighbours). */
    fun pressMask(n: Int, idx: Int): Int {
        val r = idx / n
        val c = idx % n
        var m = 1 shl idx
        if (r > 0) m = m or (1 shl (idx - n))
        if (r < n - 1) m = m or (1 shl (idx + n))
        if (c > 0) m = m or (1 shl (idx - 1))
        if (c < n - 1) m = m or (1 shl (idx + 1))
        return m
    }

    /**
     * Minimum-weight set of presses (bitmask) that turns every light off, or null if impossible.
     * Gaussian elimination over GF(2); free variables are enumerated to find the lightest solution.
     */
    fun solveMinimal(n: Int, state: Int): Int? {
        val m = n * n
        // rows[i]: bit j = does pressing j toggle cell i; bit m = state of cell i
        val rows = LongArray(m) { i ->
            var row = 0L
            for (j in 0 until m) if (pressMask(n, j) and (1 shl i) != 0) row = row or (1L shl j)
            if (state and (1 shl i) != 0) row = row or (1L shl m)
            row
        }
        val pivotCol = IntArray(m) { -1 }
        var r = 0
        for (c in 0 until m) {
            var p = -1
            for (i in r until m) if (rows[i] and (1L shl c) != 0L) { p = i; break }
            if (p < 0) continue
            val tmp = rows[p]; rows[p] = rows[r]; rows[r] = tmp
            for (i in 0 until m) if (i != r && rows[i] and (1L shl c) != 0L) rows[i] = rows[i] xor rows[r]
            pivotCol[r] = c
            r++
        }
        val coefMask = (1L shl m) - 1
        for (i in r until m) if (rows[i] and (1L shl m) != 0L && rows[i] and coefMask == 0L) return null
        var particular = 0
        for (i in 0 until r) if (rows[i] and (1L shl m) != 0L) particular = particular or (1 shl pivotCol[i])
        val pivots = (0 until r).map { pivotCol[it] }.toSet()
        val nullVectors = (0 until m).filter { it !in pivots }.map { f ->
            var v = 1 shl f
            for (i in 0 until r) if (rows[i] and (1L shl f) != 0L) v = v or (1 shl pivotCol[i])
            v
        }
        check(nullVectors.size <= 16) { "null space too large" }
        var best = particular
        for (combo in 1 until (1 shl nullVectors.size)) {
            var x = particular
            for (k in nullVectors.indices) if (combo and (1 shl k) != 0) x = x xor nullVectors[k]
            if (Integer.bitCount(x) < Integer.bitCount(best)) best = x
        }
        return best
    }
}

/** [lights] is a bitmask: bit set = light ON. Goal: all off. */
data class LightFlipGame(
    val n: Int,
    val lights: Int,
    val moves: Int,
    val par: Int,
    val hintsUsed: Int = 0,
    val history: List<Int> = emptyList(),
) {
    val isSolved get() = lights == 0
    val canUndo get() = history.isNotEmpty()
    fun isOn(idx: Int) = lights and (1 shl idx) != 0

    fun press(idx: Int): LightFlipGame {
        if (idx !in 0 until n * n) return this
        return copy(lights = lights xor LightFlipRules.pressMask(n, idx), moves = moves + 1, history = history + idx)
    }

    fun undo(): LightFlipGame {
        if (history.isEmpty()) return this
        val idx = history.last()
        return copy(lights = lights xor LightFlipRules.pressMask(n, idx), moves = maxOf(0, moves - 1), history = history.dropLast(1))
    }

    /** A cell that is part of a minimum solution from the current position, or null if solved. */
    fun hintCell(): Int? {
        val sol = LightFlipRules.solveMinimal(n, lights) ?: return null
        return (0 until n * n).firstOrNull { sol and (1 shl it) != 0 }
    }

    fun withHintUsed() = copy(hintsUsed = hintsUsed + 1)

    fun encode(): String = listOf(n, lights, moves, par, hintsUsed, history.joinToString(",")).joinToString("|")

    companion object {
        fun newGame(tier: Int, seed: Long): LightFlipGame {
            val n = LightFlipRules.N
            val rng = Random(seed)
            val presses = LightFlipRules.scramblePresses(tier)
            while (true) {
                val picks = (0 until n * n).shuffled(rng).take(presses)
                var lights = 0
                picks.forEach { lights = lights xor LightFlipRules.pressMask(n, it) }
                if (lights == 0) continue
                val par = Integer.bitCount(LightFlipRules.solveMinimal(n, lights)!!)
                return LightFlipGame(n, lights, 0, par)
            }
        }

        fun decode(s: String): LightFlipGame? = try {
            val p = s.split("|")
            LightFlipGame(
                n = p[0].toInt(), lights = p[1].toInt(), moves = p[2].toInt(), par = p[3].toInt(),
                hintsUsed = p[4].toInt(),
                history = if (p.size > 5 && p[5].isNotEmpty()) p[5].split(",").map { it.toInt() } else emptyList(),
            )
        } catch (e: Exception) {
            null
        }
    }
}

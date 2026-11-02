package com.kreativekoala.riddleverse

import java.time.LocalDate

// --- daily puzzle (feat/daily-puzzle-share) --- pure, Android-free scheduling. Everything here is a function of
// the clock, so every phone computes the same puzzle for the same day with no backend.

/** One day's shared puzzle: which game, and the seed every player's generator is fed. */
data class DailyPuzzleSpec(
    /** UTC epoch day (days since 1970-01-01). */
    val day: Long,
    /** "Daily #N": 1 on [DailyPuzzleSchedule.FIRST_DAY]. */
    val number: Int,
    /** Puzzle type key, e.g. "sudoku". Always a member of [DailyPuzzleSchedule.ROTATION]. */
    val type: String,
    val seed: Long,
)

/**
 * The day boundary is UTC midnight for everyone, so the puzzle changes at the same instant worldwide
 * (4 or 5 pm US Pacific, 5:30 am India). The streak uses the same boundary, so nobody can lose or gain a
 * day by changing time zone.
 */
object DailyPuzzleSchedule {
    const val DAY_MS = 86_400_000L

    /** Daily #1. Changing this renumbers every puzzle, so never edit it after release. */
    val FIRST_DAY: Long = LocalDate.of(2026, 11, 1).toEpochDay()

    /**
     * Games eligible for the daily. Rules for membership:
     *  - generated from a seed carried in the Puzzle (no per-device randomness inside the screen),
     *  - a unique/guaranteed solution, so everyone can solve it,
     *  - a failed attempt does not reveal the answer (so a retry is still a real solve).
     * Left out on purpose: Math games and Memory Match (being triaged), Cage Math, the five "classic" games (their
     * screens seed from the clock), Piece Pack and Letter Balloons (can be lost by luck), Word Guess and Code Cracker
     * (a loss shows the answer, so a retry would be meaningless).
     * Changing this list reshuffles every future day, so only add to it deliberately.
     */
    val ROTATION: List<String> = listOf(
        "sudoku", "binarygrid", "signgrid", "pixellogic", "tubetint", "conduit", "crateshift", "wordladder", "wordhive",
    )

    /** Difficulty label handed to every generator. One fixed level keeps the puzzle identical for all players. */
    const val DIFFICULTY = "Medium"

    fun dayOf(epochMillis: Long): Long = Math.floorDiv(epochMillis, DAY_MS)

    fun numberOf(day: Long): Int = (day - FIRST_DAY + 1).toInt()

    /** Milliseconds from [nowMillis] until the next puzzle (the next UTC midnight). Always in 1..[DAY_MS]. */
    fun millisUntilNext(nowMillis: Long): Long = (dayOf(nowMillis) + 1) * DAY_MS - nowMillis

    /** The puzzle for [day]. Pure and stable: the same day always maps to the same spec. */
    fun specFor(day: Long): DailyPuzzleSpec {
        val index = day - FIRST_DAY
        val cycle = Math.floorDiv(index, ROTATION.size.toLong())
        val slot = Math.floorMod(index, ROTATION.size.toLong()).toInt()
        val type = order(cycle)[slot]
        return DailyPuzzleSpec(day = day, number = numberOf(day), type = type, seed = seedFor(day, type))
    }

    /** Each cycle of [ROTATION].size days plays every game exactly once, in a per-cycle shuffled order. */
    internal fun order(cycle: Long): List<String> {
        val current = shuffled(cycle).toMutableList()
        // Never play the same game on the last day of one cycle and the first day of the next.
        // Positions 0 and 1 are the only ones ever swapped, so the "last of previous cycle" check
        // can use the raw shuffle of the previous cycle (its last slot is never touched).
        if (current.first() == shuffled(cycle - 1).last()) {
            val t = current[0]; current[0] = current[1]; current[1] = t
        }
        return current
    }

    private fun shuffled(cycle: Long): List<String> {
        val list = ROTATION.toMutableList()
        val rng = SplitMix64(cycle * -0x61c8864680b583ebL + 0x5851F42D4C957F2DL)
        for (i in list.indices.reversed()) { // Fisher-Yates
            val j = (rng.next() ushr 1).rem(i + 1L).toInt()
            val t = list[i]; list[i] = list[j]; list[j] = t
        }
        return list
    }

    /** Positive 63-bit seed derived from the day and the game. Explicit mixing, not String.hashCode, so it can never change. */
    internal fun seedFor(day: Long, type: String): Long {
        var h = 1125899906842597L
        for (c in type) h = 31 * h + c.code
        return SplitMix64(day * -0x61c8864680b583ebL xor h).next() and Long.MAX_VALUE
    }

    /** SplitMix64: tiny, fully specified generator, identical on every JVM and ART version. */
    internal class SplitMix64(private var state: Long) {
        fun next(): Long {
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}

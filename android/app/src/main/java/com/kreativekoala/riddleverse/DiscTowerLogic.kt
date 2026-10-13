package com.kreativekoala.riddleverse

// --- classic puzzles (feat/puzzles-classic) --- Disc Tower (Hanoi rules): pure game logic (no Android deps)

object DiscTowerRules {
    const val PEGS = 3
    const val TARGET_PEG = 2

    fun discsFor(tier: Int): Int = when (tier) {
        0 -> 3
        1 -> 4
        2 -> 5
        3 -> 6
        else -> 7
    }

    fun parFor(discs: Int): Int = (1 shl discs) - 1
}

/** pegs[p] lists disc sizes bottom to top (1 = smallest). */
data class DiscTowerGame(
    val discs: Int,
    val pegs: List<List<Int>>,
    val moves: Int,
    val history: List<Pair<Int, Int>> = emptyList(),
) {
    val par get() = DiscTowerRules.parFor(discs)
    val isSolved get() = pegs[DiscTowerRules.TARGET_PEG].size == discs
    val canUndo get() = history.isNotEmpty()

    fun canMove(from: Int, to: Int): Boolean {
        if (from == to || from !in 0 until DiscTowerRules.PEGS || to !in 0 until DiscTowerRules.PEGS) return false
        val src = pegs[from].lastOrNull() ?: return false
        val dst = pegs[to].lastOrNull()
        return dst == null || dst > src
    }

    fun move(from: Int, to: Int): DiscTowerGame {
        if (!canMove(from, to)) return this
        val np = pegs.map { it.toMutableList() }
        np[to].add(np[from].removeAt(np[from].lastIndex))
        return copy(pegs = np, moves = moves + 1, history = history + (from to to))
    }

    fun undo(): DiscTowerGame {
        if (history.isEmpty()) return this
        val (from, to) = history.last()
        val np = pegs.map { it.toMutableList() }
        np[from].add(np[to].removeAt(np[to].lastIndex))
        return copy(pegs = np, moves = maxOf(0, moves - 1), history = history.dropLast(1))
    }

    /** Next move of a shortest solution from the current (legal) position to "all discs on the target peg". */
    fun hintMove(): Pair<Int, Int>? {
        if (isSolved) return null
        return nextMove(discs, DiscTowerRules.TARGET_PEG)
    }

    private fun pegOf(disc: Int) = pegs.indexOfFirst { disc in it }

    private fun nextMove(k: Int, target: Int): Pair<Int, Int>? {
        if (k == 0) return null
        val p = pegOf(k)
        if (p == target) return nextMove(k - 1, target)
        val spare = 3 - p - target // pegs are 0,1,2 so the third peg is 3 - a - b
        val smallerOnSpare = (1 until k).all { it in pegs[spare] }
        return if (smallerOnSpare) p to target else nextMove(k - 1, spare)
    }

    fun encode(): String = listOf(
        discs, moves, pegs.joinToString(";") { it.joinToString(",") },
        history.joinToString(",") { "${it.first}${it.second}" },
    ).joinToString("|")

    companion object {
        fun newGame(discs: Int): DiscTowerGame =
            DiscTowerGame(discs, listOf((discs downTo 1).toList(), emptyList(), emptyList()), 0)

        fun decode(s: String): DiscTowerGame? = try {
            val p = s.split("|")
            val discs = p[0].toInt()
            val pegs = p[2].split(";").map { if (it.isEmpty()) emptyList() else it.split(",").map(String::toInt) }
            DiscTowerGame(
                discs, pegs, p[1].toInt(),
                if (p.size > 3 && p[3].isNotEmpty()) p[3].split(",").map { it[0] - '0' to it[1] - '0' } else emptyList(),
            ).takeIf { pegs.size == 3 && pegs.sumOf { it.size } == discs }
        } catch (e: Exception) {
            null
        }
    }
}

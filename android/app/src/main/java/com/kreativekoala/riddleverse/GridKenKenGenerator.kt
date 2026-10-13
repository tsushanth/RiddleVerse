package com.kreativekoala.riddleverse

import kotlin.random.Random

/** One arithmetic cage. [op] is one of '+', '-', '*', '/', '='. [cells] are row-major indices. */
class KenKenCage(val cells: IntArray, val op: Char, val target: Int) {
    /** Display label such as "12x", "3-", "2" (single cell). */
    val label: String
        get() = when (op) {
            '=' -> "$target"
            '*' -> "${target}×"
            '/' -> "${target}÷"
            '-' -> "$target−"
            else -> "$target+"
        }
}

/** Arithmetic-cage Latin square puzzle (values 1..size, no givens beyond single-cell cages). */
class KenKenPuzzle(val size: Int, val cages: List<KenKenCage>, val solution: IntArray) {
    val cageOf: IntArray = IntArray(size * size).also { a ->
        cages.forEachIndexed { i, c -> c.cells.forEach { a[it] = i } }
    }
}

object KenKenGenerator {

    /** Tier -> size. */
    fun sizeFor(difficulty: String): Int = when (difficulty.lowercase()) {
        "easy", "beginner" -> 4
        "medium" -> 5
        else -> 6
    }

    fun generate(difficulty: String, seed: Long): KenKenPuzzle =
        generate(sizeFor(difficulty), difficulty.lowercase(), seed)

    fun generate(size: Int, difficulty: String, seed: Long): KenKenPuzzle {
        val rnd = Random(seed)
        val solution = latinSquare(size, rnd)
        var attempt = 0
        var cages = buildCages(size, solution, difficulty, rnd)
        while (attempt < 80 && countSolutions(size, cages, 2) != 1) {
            cages = buildCages(size, solution, difficulty, rnd)
            attempt++
        }
        // Fallback: split multi-cell cages into single givens until unique (always terminates: all-singles is unique).
        while (countSolutions(size, cages, 2) != 1) {
            val multi = cages.filter { it.cells.size > 1 }
            val victim = multi[rnd.nextInt(multi.size)]
            val rest = cages.filter { it !== victim }.toMutableList()
            victim.cells.forEach { rest.add(KenKenCage(intArrayOf(it), '=', solution[it])) }
            cages = rest
        }
        return KenKenPuzzle(size, cages.sortedBy { it.cells.min() }, solution)
    }

    private fun latinSquare(n: Int, rnd: Random): IntArray {
        val rowPerm = (0 until n).shuffled(rnd)
        val colPerm = (0 until n).shuffled(rnd)
        val symPerm = (1..n).shuffled(rnd)
        val g = IntArray(n * n)
        for (r in 0 until n) for (c in 0 until n) g[r * n + c] = symPerm[(rowPerm[r] + colPerm[c]) % n]
        return g
    }

    private fun maxCageSize(difficulty: String) = when (difficulty) {
        "easy", "beginner" -> 3
        "medium" -> 3
        else -> 4
    }

    private fun buildCages(n: Int, sol: IntArray, difficulty: String, rnd: Random): List<KenKenCage> {
        val owner = IntArray(n * n) { -1 }
        val groups = ArrayList<MutableList<Int>>()
        val maxSize = maxCageSize(difficulty)
        for (start in (0 until n * n).shuffled(rnd)) {
            if (owner[start] != -1) continue
            val wanted = when (rnd.nextInt(10)) {
                0 -> 1
                in 1..5 -> 2
                in 6..8 -> minOf(3, maxSize)
                else -> maxSize
            }
            val cells = mutableListOf(start)
            owner[start] = groups.size
            while (cells.size < wanted) {
                val frontier = cells.flatMap { neighbours(it, n) }.filter { owner[it] == -1 }.distinct()
                if (frontier.isEmpty()) break
                val next = frontier[rnd.nextInt(frontier.size)]
                owner[next] = groups.size
                cells.add(next)
            }
            groups.add(cells)
        }
        return groups.map { makeCage(it, sol, difficulty, rnd) }
    }

    private fun neighbours(cell: Int, n: Int): List<Int> {
        val r = cell / n
        val c = cell % n
        val out = ArrayList<Int>(4)
        if (r > 0) out.add(cell - n)
        if (r < n - 1) out.add(cell + n)
        if (c > 0) out.add(cell - 1)
        if (c < n - 1) out.add(cell + 1)
        return out
    }

    private fun makeCage(cells: List<Int>, sol: IntArray, difficulty: String, rnd: Random): KenKenCage {
        val vals = cells.map { sol[it] }
        val arr = cells.sorted().toIntArray()
        if (cells.size == 1) return KenKenCage(arr, '=', vals[0])
        val sum = vals.sum()
        val prod = vals.fold(1) { a, b -> a * b }
        if (cells.size == 2) {
            val hi = vals.max()
            val lo = vals.min()
            val options = ArrayList<Char>()
            options.add('+')
            options.add('-')
            if (difficulty != "easy" && difficulty != "beginner") options.add('*')
            if ((difficulty == "hard") && hi % lo == 0) options.add('/')
            return when (options[rnd.nextInt(options.size)]) {
                '+' -> KenKenCage(arr, '+', sum)
                '-' -> KenKenCage(arr, '-', hi - lo)
                '*' -> KenKenCage(arr, '*', prod)
                else -> KenKenCage(arr, '/', hi / lo)
            }
        }
        val mult = difficulty != "easy" && difficulty != "beginner" && rnd.nextBoolean()
        return if (mult) KenKenCage(arr, '*', prod) else KenKenCage(arr, '+', sum)
    }

    /** True if the (fully assigned) cage values satisfy the cage. */
    fun cageSatisfied(cage: KenKenCage, v: List<Int>): Boolean = when (cage.op) {
        '=' -> v[0] == cage.target
        '+' -> v.sum() == cage.target
        '*' -> v.fold(1) { a, b -> a * b } == cage.target
        '-' -> kotlin.math.abs(v[0] - v[1]) == cage.target
        else -> (v.max() % v.min() == 0) && v.max() / v.min() == cage.target
    }

    /** Counts solutions up to [limit]. */
    fun countSolutions(n: Int, cages: List<KenKenCage>, limit: Int): Int {
        val cageOf = IntArray(n * n)
        cages.forEachIndexed { i, c -> c.cells.forEach { cageOf[it] = i } }
        val lastCell = IntArray(cages.size) { cages[it].cells.max() }
        val g = IntArray(n * n)
        var count = 0
        fun cageOk(ci: Int, upTo: Int): Boolean {
            val cage = cages[ci]
            val vals = ArrayList<Int>(cage.cells.size)
            var unassigned = 0
            for (cell in cage.cells) if (cell <= upTo) vals.add(g[cell]) else unassigned++
            if (unassigned == 0) return cageSatisfied(cage, vals)
            return when (cage.op) {
                '+' -> vals.sum() + unassigned <= cage.target
                '*' -> cage.target % vals.fold(1) { a, b -> a * b } == 0
                else -> true
            }
        }
        fun go(cell: Int) {
            if (count >= limit) return
            if (cell == n * n) { count++; return }
            val r = cell / n
            val c = cell % n
            for (d in 1..n) {
                var clash = false
                for (i in 0 until c) if (g[r * n + i] == d) { clash = true; break }
                if (!clash) for (i in 0 until r) if (g[i * n + c] == d) { clash = true; break }
                if (clash) continue
                g[cell] = d
                if (cageOk(cageOf[cell], cell)) go(cell + 1)
                g[cell] = 0
                if (count >= limit) return
            }
        }
        go(0)
        return count
    }
}

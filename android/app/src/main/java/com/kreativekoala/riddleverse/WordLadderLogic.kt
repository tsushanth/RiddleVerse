package com.kreativekoala.riddleverse

import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.Random

/** Word graph over same-length words; edges join words that differ in exactly one letter. */
class WordLadderGraph(words: Collection<String>) {
    val words: List<String> = words.map { it.lowercase() }.distinct().sorted()
    private val wordSet = this.words.toHashSet()
    private val buckets: Map<String, List<String>> = HashMap<String, MutableList<String>>().also { m ->
        for (w in this.words) for (i in w.indices) {
            m.getOrPut(w.substring(0, i) + "_" + w.substring(i + 1)) { ArrayList() }.add(w)
        }
    }

    fun contains(w: String) = w.lowercase() in wordSet

    /** Neighbours in sorted order (deterministic). */
    fun neighbors(w: String): List<String> {
        val out = java.util.TreeSet<String>()
        for (i in w.indices) buckets[w.substring(0, i) + "_" + w.substring(i + 1)]?.let { out.addAll(it) }
        out.remove(w)
        return out.toList()
    }

    /** Distances from [start] to every reachable word (BFS). */
    fun distancesFrom(start: String): Map<String, Int> {
        val dist = HashMap<String, Int>()
        if (!contains(start)) return dist
        dist[start] = 0
        val q = ArrayDeque<String>()
        q.add(start)
        while (q.isNotEmpty()) {
            val cur = q.poll()
            for (n in neighbors(cur)) if (n !in dist) {
                dist[n] = dist.getValue(cur) + 1
                q.add(n)
            }
        }
        return dist
    }

    /** A shortest path start..target inclusive, or null if unreachable. Deterministic. */
    fun shortestPath(start: String, target: String): List<String>? {
        if (!contains(start) || !contains(target)) return null
        val prev = HashMap<String, String>()
        val seen = HashSet<String>().apply { add(start) }
        val q = ArrayDeque<String>()
        q.add(start)
        while (q.isNotEmpty()) {
            val cur = q.poll()
            if (cur == target) break
            for (n in neighbors(cur)) if (seen.add(n)) {
                prev[n] = cur
                q.add(n)
            }
        }
        if (target != start && target !in prev) return null
        val path = ArrayList<String>()
        var c: String? = target
        while (c != null) {
            path.add(c)
            c = prev[c]
        }
        return path.reversed()
    }
}

object WordLadderRules {
    fun differsByOne(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) if (a[i] != b[i]) diff++
        return diff == 1
    }

    /** 3 stars at par or better, 2 within +2, else 1. */
    fun stars(steps: Int, par: Int): Int = when {
        steps <= par -> 3
        steps <= par + 2 -> 2
        else -> 1
    }

    fun score(steps: Int, par: Int, hintsUsed: Int): Int =
        (40 + stars(steps, par) * 30 - hintsUsed * 15).coerceAtLeast(10)

    /** Why [candidate] cannot be appended to [chain], or null if it can. */
    fun rejection(graph: WordLadderGraph, chain: List<String>, candidate: String): String? {
        val last = chain.last()
        return when {
            candidate.length != last.length -> "Needs ${last.length} letters"
            candidate == last -> "Change exactly one letter"
            !differsByOne(last, candidate) -> "Change exactly one letter"
            candidate in chain -> "Already used"
            !graph.contains(candidate) -> "Not in word list"
            else -> null
        }
    }
}

data class WordLadderPuzzle(val start: String, val target: String, val par: Int, val length: Int, val solution: List<String>) {
    fun toJson(seed: Long, difficulty: String): JSONObject = JSONObject().apply {
        put("game", "wordladder")
        put("start", start); put("target", target); put("par", par); put("length", length)
        put("solution", JSONArray(solution)); put("seed", seed); put("difficulty", difficulty)
    }

    companion object {
        fun fromJson(o: JSONObject): WordLadderPuzzle {
            val arr = o.getJSONArray("solution")
            return WordLadderPuzzle(
                o.getString("start"), o.getString("target"), o.getInt("par"), o.getInt("length"),
                List(arr.length()) { arr.getString(it) }
            )
        }
    }
}

object WordLadderGenerator {
    /** word length and inclusive par (steps = number of words added after the start) window per difficulty. */
    fun config(difficulty: String): Triple<Int, Int, Int> = when (difficulty.lowercase()) {
        "easy" -> Triple(3, 3, 4)
        "hard" -> Triple(5, 5, 7)
        else -> Triple(4, 4, 6)
    }

    fun poolFor(lists: WordPuzzleLists, length: Int): List<String> =
        lists.top(length, if (length == 5) 2500 else 3000)

    /** Deterministic for a given seed + word lists. Always returns a solvable puzzle (BFS-verified), or null if the pool is degenerate. */
    fun generate(lists: WordPuzzleLists, difficulty: String, seed: Long): WordLadderPuzzle? {
        val (len, minPar, maxPar) = config(difficulty)
        val pool = poolFor(lists, len)
        if (pool.size < 20) return null
        val graph = WordLadderGraph(pool)
        val rnd = Random(seed)
        // Start words come from the more familiar half of the pool.
        val starts = pool.take(pool.size / 2).sorted()
        val startSet = starts.toHashSet()
        repeat(200) {
            val start = starts[rnd.nextInt(starts.size)]
            val dist = graph.distancesFrom(start)
            val candidates = dist.filter { (w, d) -> d in minPar..maxPar && w in startSet }.keys.sorted()
            if (candidates.isNotEmpty()) {
                val target = candidates[rnd.nextInt(candidates.size)]
                val path = graph.shortestPath(start, target) ?: return@repeat
                return WordLadderPuzzle(start, target, path.size - 1, len, path)
            }
        }
        return null
    }
}

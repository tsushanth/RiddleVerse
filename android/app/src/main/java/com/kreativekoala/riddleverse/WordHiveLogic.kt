package com.kreativekoala.riddleverse

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/** Pure rules for "Word Hive": seven letters, one required centre letter, words of 4+ letters. */
object WordHiveRules {
    const val MIN_WORD = 4
    const val PANGRAM_BONUS = 7

    /** (name, minimum percent of the maximum score), ascending. */
    val ranks: List<Pair<String, Int>> = listOf(
        "Newcomer" to 0, "Getting Going" to 2, "Buzzing" to 5, "Steady" to 8, "Solid" to 15,
        "Sharp" to 25, "Great" to 40, "Amazing" to 50, "Genius" to 70, "Queen of the Hive" to 100
    )

    fun isPangram(word: String, letters: Set<Char>) = word.toSet() == letters

    fun wordScore(word: String, letters: Set<Char>): Int =
        (if (word.length == MIN_WORD) 1 else word.length) + if (isPangram(word, letters)) PANGRAM_BONUS else 0

    /** Every common word buildable from [letters] that contains [center]. Deterministic (sorted). */
    fun solutions(common: Collection<String>, letters: Set<Char>, center: Char): List<String> =
        common.filter { w ->
            w.length >= MIN_WORD && center in w && w.all { it in letters }
        }.distinct().sorted()

    fun rankIndex(score: Int, maxScore: Int): Int {
        if (maxScore <= 0) return 0
        val pct = score * 100.0 / maxScore
        var idx = 0
        for ((i, r) in ranks.withIndex()) if (pct + 1e-9 >= r.second) idx = i
        return idx
    }

    /** Percent thresholds of the rank the player must reach to count the puzzle as solved. */
    fun targetRankIndex(difficulty: String): Int = when (difficulty.lowercase()) {
        "easy" -> 6      // Great
        "hard" -> 8      // Genius
        else -> 7        // Amazing
    }

    fun rejection(
        entry: String, letters: Set<Char>, center: Char, solutions: Set<String>, found: Collection<String>
    ): String? = when {
        entry.length < MIN_WORD -> "Too short"
        entry.any { it !in letters } -> "Bad letters"
        center !in entry -> "Missing centre letter"
        entry in found -> "Already found"
        entry !in solutions -> "Not in word list"
        else -> null
    }
}

data class WordHivePuzzle(
    val letters: List<Char>,   // 6 outer letters, sorted, for stable positions
    val center: Char,
    val words: List<String>,
    val maxScore: Int
) {
    val letterSet: Set<Char> get() = (letters + center).toSet()
    val pangrams: List<String> get() = words.filter { WordHiveRules.isPangram(it, letterSet) }

    fun toJson(seed: Long, difficulty: String) = JSONObject().apply {
        put("game", "wordhive")
        put("center", center.toString())
        put("outer", letters.joinToString(""))
        put("words", JSONArray(words))
        put("maxScore", maxScore)
        put("seed", seed); put("difficulty", difficulty)
    }

    companion object {
        fun fromJson(o: JSONObject): WordHivePuzzle {
            val arr = o.getJSONArray("words")
            return WordHivePuzzle(
                o.getString("outer").toList(), o.getString("center")[0],
                List(arr.length()) { arr.getString(it) }, o.getInt("maxScore")
            )
        }
    }
}

object WordHiveGenerator {
    /** Acceptable number of words per difficulty. */
    private fun window(difficulty: String): IntRange = when (difficulty.lowercase()) {
        "easy" -> 18..40
        "hard" -> 40..90
        else -> 25..60
    }

    /**
     * Picks a common 7-distinct-letter word (no 's', so plural spam cannot inflate it) as the pangram seed,
     * chooses a centre letter, and keeps the first candidate whose word count fits the window.
     * Deterministic for a given seed. Returns null only for a degenerate word list.
     */
    fun generate(lists: WordPuzzleLists, difficulty: String, seed: Long): WordHivePuzzle? {
        val common = lists.common.filter { it.length in 4..12 && 's' !in it }
        val seeds = common.filter { it.length >= 7 && it.toSet().size == 7 }
            .distinctBy { it.toSet().sorted().joinToString("") }
            .take(4000)
            .sorted()
        if (seeds.isEmpty()) return null
        val rnd = Random(seed)
        val order = seeds.indices.shuffled(rnd)
        val win = window(difficulty)
        var best: WordHivePuzzle? = null
        var bestGap = Int.MAX_VALUE
        for ((tries, idx) in order.withIndex()) {
            if (tries >= 150) break
            val set = seeds[idx].toSet()
            val sortedLetters = set.sorted()
            val center = sortedLetters[rnd.nextInt(7)]
            val sols = WordHiveRules.solutions(common, set, center)
            if (sols.none { WordHiveRules.isPangram(it, set) }) continue
            val gap = when {
                sols.size < win.first -> win.first - sols.size
                sols.size > win.last -> sols.size - win.last
                else -> 0
            }
            val p = WordHivePuzzle(sortedLetters.filter { it != center }, center, sols, sols.sumOf { WordHiveRules.wordScore(it, set) })
            if (gap == 0) return p
            if (gap < bestGap) { best = p; bestGap = gap }
        }
        return best
    }
}

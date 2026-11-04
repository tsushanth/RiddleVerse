package com.kreativekoala.riddleverse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

// --- linkup (feat/game-linkup) --- "Linkup": 16 tiles, find the 4 hidden groups of 4, 4 mistakes allowed.
// Content: assets/linkup/puzzles.json, authored by hand (see scripts/linkup, validated by validate_linkup.py and LinkupLogicTest).

data class LinkupGroup(val tier: Int, val title: String, val words: List<String>)

/**
 * One puzzle. [groups] are ordered tier 1 (easy) to tier 4 (tricky). [redHerrings] are the words the author declared as
 * fitting a second group on purpose; the hint avoids them because they would give the wrong impression.
 */
data class LinkupPuzzle(val id: String, val groups: List<LinkupGroup>, val redHerrings: Set<String> = emptySet()) {
    val words: List<String> get() = groups.flatMap { it.words }

    fun groupIndexOf(word: String): Int = groups.indexOfFirst { word in it.words }

    fun toJson(seed: Long, difficulty: String) = JSONObject().apply {
        put("game", "linkup")
        put("id", id)
        put("groups", JSONArray().also { arr ->
            groups.forEach { g ->
                arr.put(JSONObject().put("tier", g.tier).put("title", g.title).put("words", JSONArray(g.words)))
            }
        })
        put("herrings", JSONArray(redHerrings.sorted()))
        put("seed", seed); put("difficulty", difficulty)
    }

    companion object {
        fun fromJson(o: JSONObject): LinkupPuzzle {
            val ga = o.getJSONArray("groups")
            val groups = List(ga.length()) { i ->
                val g = ga.getJSONObject(i)
                val wa = g.getJSONArray("words")
                LinkupGroup(g.getInt("tier"), g.getString("title"), List(wa.length()) { wa.getString(it) })
            }
            val herrings = HashSet<String>()
            o.optJSONArray("herrings")?.let { for (i in 0 until it.length()) herrings += it.getString(i) }
            o.optJSONArray("ambiguity")?.let { for (i in 0 until it.length()) herrings += it.getJSONObject(i).getString("word") }
            return LinkupPuzzle(o.getString("id"), groups, herrings)
        }
    }
}

/** The curated content bank (assets/linkup/puzzles.json). */
class LinkupBank(val puzzles: List<LinkupPuzzle>) {
    companion object {
        fun parse(text: String): LinkupBank {
            val arr = JSONObject(text).getJSONArray("puzzles")
            return LinkupBank(List(arr.length()) { LinkupPuzzle.fromJson(arr.getJSONObject(it)) })
        }

        @Volatile private var cached: LinkupBank? = null

        fun get(context: Context): LinkupBank {
            cached?.let { return it }
            synchronized(this) {
                cached?.let { return it }
                val bank = context.applicationContext.assets.open("linkup/puzzles.json").use { parse(it.bufferedReader().readText()) }
                cached = bank
                return bank
            }
        }
    }
}

/**
 * Deterministic, repeat-free selection over the bank. Puzzle number [counter] of an install is
 * `permutation(installSeed, counter / n)[counter % n]`: every puzzle is served once before any is served again, a
 * new shuffle starts each round, and the first puzzle of a round is never the last one of the previous round.
 */
object LinkupSelector {
    private fun mix(installSeed: Int, epoch: Int): Long = installSeed.toLong() * 0x9E3779B97F4A7C15uL.toLong() + epoch.toLong() * 0xBF58476D1CE4E5B9uL.toLong() + 17L

    fun permutation(installSeed: Int, epoch: Int, n: Int): List<Int> {
        require(n > 0) { "empty bank" }
        val p = (0 until n).toList().shuffled(Random(mix(installSeed, epoch))).toMutableList()
        if (epoch > 0 && n > 2) {
            val prevLast = (0 until n).toList().shuffled(Random(mix(installSeed, epoch - 1))).last()
            if (p[0] == prevLast) { val t = p[0]; p[0] = p[1]; p[1] = t }
        }
        return p
    }

    fun indexFor(installSeed: Int, n: Int, counter: Int): Int {
        require(counter >= 0)
        return permutation(installSeed, counter / n, n)[counter % n]
    }
}

/** Per-install position in the bank sequence. Only a seed and a counter are stored (local, never sent anywhere). */
class LinkupProgress(
    private val store: WordPuzzleKeyValueStore,
    private val newSeed: () -> Int = { kotlin.random.Random.nextInt(1, Int.MAX_VALUE) }
) {
    fun installSeed(): Int {
        var s = store.getInt(KEY_SEED, 0)
        if (s == 0) { s = newSeed(); store.putInt(KEY_SEED, s) }
        return s
    }

    fun cursor(): Int = store.getInt(KEY_CURSOR, 0)

    /** Index of the next puzzle for a bank of [n]; advances the counter. */
    fun take(n: Int): Int {
        val c = cursor()
        store.putInt(KEY_CURSOR, if (c == Int.MAX_VALUE) 0 else c + 1)
        return LinkupSelector.indexFor(installSeed(), n, c)
    }

    private companion object {
        const val KEY_SEED = "linkup_install_seed"
        const val KEY_CURSOR = "linkup_cursor"
    }
}

object LinkupGenerator {
    fun next(bank: LinkupBank, progress: LinkupProgress): LinkupPuzzle = bank.puzzles[progress.take(bank.puzzles.size)]

    /** JSON the screen renders. A puzzle is consumed when it is generated, so leaving it unfinished does not replay it. */
    fun generateJson(context: Context, difficulty: String): String {
        val progress = LinkupProgress(PrefsKeyValueStore(context))
        val cursor = progress.cursor()
        val p = next(LinkupBank.get(context), progress)
        return p.toJson(cursor.toLong(), difficulty).toString()
    }
}

enum class LinkupResult { SOLVED, CLOSE, WRONG, ALREADY_TRIED, INCOMPLETE, FINISHED }

/** Everything the player has done in one puzzle. Small and string-encodable so it survives rotation. */
data class LinkupState(
    val solved: List<Int> = emptyList(),
    val selected: List<String> = emptyList(),
    val mistakes: Int = 0,
    val hints: Int = 0,
    val hinted: List<String> = emptyList(),
    val tried: List<String> = emptyList(),
    val shuffle: Int = 0,
    val gaveUp: Boolean = false
) {
    fun encode(): String = listOf(
        solved.joinToString(","), selected.joinToString(","), mistakes.toString(), hints.toString(),
        hinted.joinToString(","), tried.joinToString(";"), shuffle.toString(), if (gaveUp) "1" else "0"
    ).joinToString("|")

    companion object {
        fun decode(s: String): LinkupState {
            val f = s.split("|")
            if (f.size != 8) return LinkupState()
            fun list(x: String, sep: Char) = x.split(sep).filter { it.isNotEmpty() }
            return try {
                LinkupState(
                    solved = list(f[0], ',').map { it.toInt() }, selected = list(f[1], ','),
                    mistakes = f[2].toInt(), hints = f[3].toInt(), hinted = list(f[4], ','),
                    tried = list(f[5], ';'), shuffle = f[6].toInt(), gaveUp = f[7] == "1"
                )
            } catch (_: NumberFormatException) { LinkupState() }
        }
    }
}

object LinkupRules {
    const val GROUP_SIZE = 4
    const val MAX_MISTAKES = 4
    const val GROUPS = 4

    /** [closeFeedback]: tell the player when a wrong guess had three tiles of one group. */
    class Profile(val maxHints: Int, val closeFeedback: Boolean, val baseScore: Int)

    fun profile(difficulty: String): Profile = when (difficulty.lowercase()) {
        "easy" -> Profile(maxHints = 3, closeFeedback = true, baseScore = 80)
        "hard" -> Profile(maxHints = 1, closeFeedback = false, baseScore = 120)
        else -> Profile(maxHints = 2, closeFeedback = true, baseScore = 100)
    }

    fun tierName(tier: Int) = when (tier) { 1 -> "Easy"; 2 -> "Medium"; 3 -> "Tricky"; else -> "Very tricky" }

    /** Shape marker per tier so the tier is never conveyed by colour alone. */
    fun tierGlyph(tier: Int) = when (tier) { 1 -> "●"; 2 -> "▲"; 3 -> "■"; else -> "★" }

    fun isWon(s: LinkupState) = s.solved.size >= GROUPS
    fun isLost(s: LinkupState) = !isWon(s) && (s.mistakes >= MAX_MISTAKES || s.gaveUp)
    fun isOver(s: LinkupState) = isWon(s) || isLost(s)
    fun mistakesLeft(s: LinkupState) = (MAX_MISTAKES - s.mistakes).coerceAtLeast(0)
    fun hintsLeft(s: LinkupState, profile: Profile) = (profile.maxHints - s.hints).coerceAtLeast(0)

    /** Tiles still in play, in a stable shuffled order (shuffle button changes [LinkupState.shuffle]). */
    fun available(p: LinkupPuzzle, s: LinkupState): List<String> {
        val solvedWords = s.solved.flatMap { p.groups[it].words }.toSet()
        val seed = p.id.hashCode().toLong() * 1_000_003L + s.shuffle * 7_919L
        return p.words.shuffled(Random(seed)).filter { it !in solvedWords }
    }

    fun toggle(p: LinkupPuzzle, s: LinkupState, word: String): LinkupState {
        if (isOver(s) || word !in available(p, s)) return s
        return if (word in s.selected) s.copy(selected = s.selected - word)
        else if (s.selected.size >= GROUP_SIZE) s
        else s.copy(selected = s.selected + word)
    }

    fun deselectAll(s: LinkupState) = s.copy(selected = emptyList())

    fun shuffle(s: LinkupState) = s.copy(shuffle = s.shuffle + 1)

    fun giveUp(s: LinkupState) = if (isOver(s)) s else s.copy(gaveUp = true, selected = emptyList())

    private fun key(words: List<String>) = words.sorted().joinToString(",")

    fun submit(p: LinkupPuzzle, s: LinkupState): Pair<LinkupState, LinkupResult> {
        if (isOver(s)) return s to LinkupResult.FINISHED
        if (s.selected.size != GROUP_SIZE) return s to LinkupResult.INCOMPLETE
        val k = key(s.selected)
        if (k in s.tried) return s to LinkupResult.ALREADY_TRIED
        val counts = s.selected.groupingBy { p.groupIndexOf(it) }.eachCount()
        val (bestGroup, bestCount) = counts.maxByOrNull { it.value }!!.let { it.key to it.value }
        if (bestCount == GROUP_SIZE && bestGroup !in s.solved) {
            return s.copy(solved = s.solved + bestGroup, selected = emptyList()) to LinkupResult.SOLVED
        }
        val next = s.copy(mistakes = s.mistakes + 1, tried = s.tried + k)
        return next to if (bestCount == GROUP_SIZE - 1) LinkupResult.CLOSE else LinkupResult.WRONG
    }

    /**
     * Marks one tile of the easiest unsolved group. Prefers a tile that is not a declared red herring (a herring tile would
     * be the most likely to mislead) and one the player has not already been shown. Returns null when no hint is left.
     */
    fun hint(p: LinkupPuzzle, s: LinkupState, profile: Profile): Pair<LinkupState, String?> {
        if (isOver(s) || hintsLeft(s, profile) == 0) return s to null
        val idx = p.groups.indices.filter { it !in s.solved }.minByOrNull { p.groups[it].tier } ?: return s to null
        val candidates = p.groups[idx].words.filter { it !in s.hinted }
        val word = candidates.firstOrNull { it !in p.redHerrings } ?: candidates.firstOrNull() ?: return s to null
        return s.copy(hints = s.hints + 1, hinted = s.hinted + word) to word
    }

    fun score(s: LinkupState, profile: Profile): Int =
        if (isWon(s)) (profile.baseScore - 10 * s.mistakes - 10 * s.hints).coerceAtLeast(10) else 0

    /** Groups not found yet, easiest first (revealed when the game is lost). */
    fun unsolvedGroups(p: LinkupPuzzle, s: LinkupState): List<LinkupGroup> =
        p.groups.indices.filter { it !in s.solved }.map { p.groups[it] }.sortedBy { it.tier }
}
// --- end linkup ---

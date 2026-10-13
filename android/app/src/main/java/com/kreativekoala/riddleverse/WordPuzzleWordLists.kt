package com.kreativekoala.riddleverse

import android.content.Context
import java.io.InputStream

/**
 * Word data shared by the word puzzles added in feat/puzzles-word
 * (Word Guess, Word Ladder, Letter Balloons, Word Hive).
 *
 * SOURCES / LICENSES (record kept here and in the branch report):
 *  - assets/word_puzzles/valid5.txt: every 5-letter entry of the app's existing
 *    assets/database/words.db (re-used, not new data; just exported as text so
 *    guesses can be validated synchronously without opening SQLite).
 *  - assets/word_puzzles/common_words.txt: words 3..12 letters long that appear BOTH in
 *    words.db and in Peter Norvig's count_1w.txt (Google Web Trillion Word Corpus unigram
 *    counts, https://norvig.com/ngrams/, MIT licence), ordered by descending frequency
 *    (line order == frequency rank), capped at 42,000 lines. Only the ordering and the
 *    words themselves are used; no counts are shipped.
 * No network access is used at runtime.
 */
class WordPuzzleLists(commonRaw: List<String>, valid5Raw: Collection<String>) {

    /** Common words, most frequent first, offensive words removed. */
    val common: List<String> = commonRaw
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() && it.all { c -> c in 'a'..'z' } && !WordBlocklist.isBlocked(it) }

    val commonSet: Set<String> = common.toHashSet()

    val commonByLength: Map<Int, List<String>> = common.groupBy { it.length }

    /** Everything accepted as a Word Guess guess (superset of the answer pool, includes common 5s). */
    val valid5: Set<String> = (valid5Raw.map { it.trim().lowercase() }.filter { it.length == 5 } +
        (commonByLength[5] ?: emptyList())).toHashSet()

    /** Most frequent [limit] common words of [length]. */
    fun top(length: Int, limit: Int): List<String> = (commonByLength[length] ?: emptyList()).take(limit)

    /**
     * Answer pool for Word Guess: frequent 5-letter words that are not simple "-s" plurals
     * or third-person forms of a shorter common word.
     */
    fun answerPool(fromRank: Int, toRank: Int): List<String> {
        val fives = (commonByLength[5] ?: emptyList()).filter { w ->
            !(w.endsWith("s") && !w.endsWith("ss") && commonSet.contains(w.dropLast(1)))
        }
        if (fives.isEmpty()) return emptyList()
        val from = fromRank.coerceIn(0, fives.size)
        val to = toRank.coerceIn(from, fives.size)
        return fives.subList(from, to)
    }

    companion object {
        fun parse(commonStream: InputStream, valid5Stream: InputStream): WordPuzzleLists =
            WordPuzzleLists(
                commonStream.bufferedReader().readLines(),
                valid5Stream.bufferedReader().readLines()
            )

        @Volatile private var cached: WordPuzzleLists? = null

        fun get(context: Context): WordPuzzleLists {
            cached?.let { return it }
            synchronized(this) {
                cached?.let { return it }
                val assets = context.applicationContext.assets
                val lists = assets.open("word_puzzles/common_words.txt").use { c ->
                    assets.open("word_puzzles/valid5.txt").use { v -> parse(c, v) }
                }
                cached = lists
                return lists
            }
        }
    }
}

/** Words that must never be used as answers, targets or counted words. Exact match plus simple inflections. */
object WordBlocklist {
    private val blocked: Set<String> = (
        "ass arse arses asshole bastard bitch bitches bitchy blowjob boob boobs bollocks bugger " +
        "butt buttocks clit cock cocks coon crap crappy cum cums cunt cunts damn dick dicks dildo " +
        "dyke fag faggot fags fuck fucks fucker fucked fucking gook handjob hell hoe hoes homo " +
        "horny jerk jism jizz kike kinky knob lesbo lust milf nazi negro nigga nigger nude nuded " +
        "orgy paki penis piss pissed porn porno prick pubic pussy queer rape raped rapes rapist " +
        "rectum retard schlong scrotum semen sex sexy shag shit shits shitty skank slut sluts " +
        "smut spic tits titty twat vagina vulva wank wanker whore whores wop xxx anal anus " +
        "arsehole bimbo bondage bukkake dago douche fetish gay gays genital " +
        "genitals gigolo hooker incest molest nipple nipples nympho pedo pervert phallus porn " +
        "pornos prostitute rimjob sodomy turd turds urinal wetback"
        ).split(" ").filter { it.isNotEmpty() }.toHashSet()

    fun isBlocked(word: String): Boolean {
        val w = word.lowercase()
        if (w in blocked) return true
        for (suffix in listOf("s", "es", "ed", "er", "ing", "y")) {
            if (w.endsWith(suffix) && w.length - suffix.length >= 3 && w.dropLast(suffix.length) in blocked) return true
        }
        return false
    }
}

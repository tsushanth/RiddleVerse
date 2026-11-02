package com.kreativekoala.riddleverse

import java.text.Normalizer

// --- riddle pack (feat/riddle-pack) --- answer matching for typed riddle answers.
// Deliberately stricter than AnswerMatcher (no synonym table, no partial match): a riddle has ONE answer, so
// "tree" must not be accepted for "oak". scripts/riddles/riddle_norm.py mirrors normalize()/variants()/same().

object RiddleMatcher {
    /** Leading filler words that are dropped ("it is a clock", "the clock"). */
    private val FILLERS = setOf("a", "an", "the", "it", "its", "is", "are", "they", "theyre", "i", "think", "maybe", "this", "that", "answer")

    /** Lower-case, accent-free, apostrophes removed, punctuation -> space, leading fillers dropped. */
    fun normalize(s: String): List<String> {
        val nfd = Normalizer.normalize(s.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
        val sb = StringBuilder(nfd.length)
        for (c in nfd) {
            when {
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> {}
                c == '\'' || c == '’' -> {}
                c == '&' -> sb.append(" and ")
                c in 'a'..'z' || c in '0'..'9' -> sb.append(c)
                else -> sb.append(' ')
            }
        }
        var toks = sb.toString().split(' ').filter { it.isNotEmpty() }
        while (toks.size > 1 && toks.first() in FILLERS) toks = toks.drop(1)
        return toks
    }

    /** A token plus its simple singular forms, so "cities" ~ "city", "boxes" ~ "box", "leaves" ~ "leaf". */
    fun variants(t: String): Set<String> {
        val v = mutableSetOf(t)
        if (t.length > 4 && t.endsWith("ies")) v += t.dropLast(3) + "y"
        if (t.length > 4 && t.endsWith("ves")) { v += t.dropLast(3) + "f"; v += t.dropLast(3) + "fe" }
        if (t.length > 3 && t.endsWith("es")) v += t.dropLast(2)
        if (t.length > 2 && t.endsWith("s") && !t.endsWith("ss")) v += t.dropLast(1)
        return v
    }

    /** Same words in the same order, each pair equal up to a plural form. */
    fun same(a: String, b: String): Boolean {
        val ta = normalize(a)
        val tb = normalize(b)
        return ta.isNotEmpty() && ta.size == tb.size && ta.indices.all { i -> variants(ta[i]).any { it in variants(tb[i]) } }
    }

    private fun compact(s: String) = normalize(s).joinToString("")

    /** Edits tolerated for a typo: none for short answers and numbers, one for 7+ letters, two for 12+. */
    internal fun typoBudget(accepted: String): Int {
        val c = compact(accepted)
        return when {
            normalize(accepted).any { it.length == 1 } -> 0 // "the letter M" must not accept "the letter R"
            c.length < 7 || c.all { it.isDigit() } -> 0
            c.length >= 12 -> 2
            else -> 1
        }
    }

    fun matchesForm(input: String, accepted: String): Boolean {
        if (same(input, accepted)) return true
        val u = compact(input)
        val c = compact(accepted)
        if (u.isEmpty() || c.isEmpty()) return false
        if (u == c) return true // "tea pot" ~ "teapot"
        val budget = typoBudget(accepted)
        return budget > 0 && kotlin.math.abs(u.length - c.length) <= budget && editDistance(u, c) <= budget
    }

    fun isCorrect(input: String, riddle: Riddle): Boolean = riddle.acceptedAnswers.any { matchesForm(input, it) }

    /** Optimal string alignment distance: insert, delete, substitute or swap two neighbouring letters, each costs 1. */
    internal fun editDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }
}

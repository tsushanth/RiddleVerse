package com.kreativekoala.riddleverse

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Content-file checks for assets/riddles/riddles_v1.json (the Python validator in scripts/riddles covers the same ground from the authoring side). */
class RiddleBankTest {
    private val bank = RiddleTestSupport.bank

    private val stop = setOf("what", "am", "you", "me", "my", "of", "to", "and", "in", "on", "at", "for", "with", "but", "or", "no", "not",
        "have", "has", "can", "do", "does", "your", "we", "us", "our", "who", "how", "when", "than", "so", "as", "by", "from")

    private fun containsPhrase(text: List<String>, phrase: List<String>): Boolean {
        if (phrase.isEmpty()) return false
        for (i in 0..text.size - phrase.size) {
            if (phrase.indices.all { j -> RiddleMatcher.variants(text[i + j]).any { it in RiddleMatcher.variants(phrase[j]) } }) return true
        }
        return false
    }

    @Test fun tierCountsAreExact() {
        assertEquals(300, bank.size)
        assertEquals(100, bank.count { it.tier == RiddleTier.EASY })
        assertEquals(120, bank.count { it.tier == RiddleTier.MEDIUM })
        assertEquals(80, bank.count { it.tier == RiddleTier.HARD })
    }

    @Test fun everyEntryParses() {
        // RiddleBank.parse silently skips malformed entries, so the count of raw entries must equal the parsed count.
        val rawCount = JSONObject(RiddleTestSupport.raw).getJSONArray("riddles").length()
        assertEquals(rawCount, bank.size)
        assertEquals(1, JSONObject(RiddleTestSupport.raw).getInt("version"))
    }

    @Test fun schemaAndLengthLimits() {
        val ids = HashSet<String>()
        for (r in bank) {
            assertTrue("id ${r.id}", Regex("[emh]\\d{3}").matches(r.id) && r.id[0] == r.tier.key[0])
            assertTrue("dup id ${r.id}", ids.add(r.id))
            assertTrue("${r.id} q length", r.question.length in 15..240)
            assertTrue("${r.id} answer length", r.answer.length in 1..40)
            assertTrue("${r.id} alt count", r.alternates.size <= 3)
            r.alternates.forEach { assertTrue("${r.id} alt length", it.length in 1..40) }
            assertEquals("${r.id} hint count", 3, r.hints.size)
            r.hints.forEach { assertTrue("${r.id} hint length", it.length in 1..140) }
            assertTrue("${r.id} why", r.explanation.isNotBlank() && r.explanation.length <= 160 && '\n' !in r.explanation)
        }
    }

    @Test fun fileIsAscii() {
        assertTrue(RiddleTestSupport.raw.all { it.code < 128 })
    }

    @Test fun noDuplicateRiddlesOrAcceptedAnswers() {
        val texts = HashSet<String>()
        for (r in bank) assertTrue("duplicate riddle text ${r.id}", texts.add(RiddleMatcher.normalize(r.question).joinToString(" ")))
        val forms = bank.flatMap { r -> r.acceptedAnswers.map { r.id to it } }
        for (i in forms.indices) for (j in i + 1 until forms.size) {
            if (RiddleMatcher.same(forms[i].second, forms[j].second)) {
                fail("accepted answers collide: ${forms[i]} vs ${forms[j]}")
            }
        }
    }

    private fun fail(msg: String): Nothing = throw AssertionError(msg)

    @Test fun answerIsNeverLeakedInRiddleOrFirstTwoHints() {
        for (r in bank) {
            for (form in r.acceptedAnswers) {
                val phrase = RiddleMatcher.normalize(form)
                if (phrase.all { it.length < 3 } && !form.all { it.isDigit() }) continue
                for ((where, text) in listOf("q" to r.question, "h1" to r.hints[0], "h2" to r.hints[1])) {
                    assertFalse("${r.id}: '$form' appears in $where", containsPhrase(RiddleMatcher.normalize(text), phrase))
                }
            }
        }
    }

    @Test fun thirdHintNamesTheFirstLetter() {
        for (r in bank) {
            val core = RiddleMatcher.normalize(r.answer).joinToString(" ")
            val h3 = r.hints[2].lowercase()
            val letter = Regex("(?:the )?letter ([a-z])").matchEntire(r.answer.lowercase())
            when {
                letter != null -> assertTrue("${r.id} h3", h3.contains(letter.groupValues[1]))
                r.answer.all { it.isDigit() } -> assertTrue("${r.id} h3", h3.contains("starts with ${r.answer[0]}"))
                else -> assertTrue("${r.id} h3 '${r.hints[2]}'", h3.contains("starts with ${core[0]}"))
            }
        }
    }

    @Test fun everyAcceptedFormIsAcceptedByItsOwnRiddle() {
        for (r in bank) for (form in r.acceptedAnswers) assertTrue("${r.id} rejects '$form'", RiddleMatcher.isCorrect(form, r))
    }

    /** Typing riddle B's answer into riddle A must not count as correct (catches over-tolerant alternates and typo matches). */
    @Test fun noRiddleAcceptsAnotherRiddlesAnswer() {
        for (a in bank) for (b in bank) {
            if (a.id == b.id) continue
            for (form in b.acceptedAnswers) {
                assertFalse("${a.id} (${a.answer}) wrongly accepts '${form}' from ${b.id}", RiddleMatcher.isCorrect(form, a))
            }
        }
    }

    @Test fun bankWideNearDuplicateScan() {
        fun sig(q: String) = RiddleMatcher.normalize(q).filter { it.length > 2 && it !in stop }.toSet()
        val sigs = bank.map { it.id to sig(it.question) }
        for (i in sigs.indices) for (j in i + 1 until sigs.size) {
            val a = sigs[i].second
            val b = sigs[j].second
            if (a.size < 5 || b.size < 5) continue
            val jac = a.intersect(b).size.toDouble() / a.union(b).size
            assertTrue("near duplicate ${sigs[i].first} / ${sigs[j].first} (jaccard $jac)", jac < 0.6)
        }
    }
}

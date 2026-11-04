package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RiddleDeckTest {
    private class MemStore : RiddleStore {
        val m = HashMap<String, String>()
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
    }

    private val bank = RiddleTestSupport.bank

    @Test fun noRepeatUntilTierIsExhausted() {
        val deck = RiddleDeck(MemStore())
        val rnd = Random(1)
        val drawn = (1..100).map { deck.next(RiddleTier.EASY, bank, rnd).id }
        assertEquals(100, drawn.toSet().size)
        assertTrue(drawn.all { it.startsWith("e") })
        assertEquals(0, deck.remaining(RiddleTier.EASY, bank))
        assertEquals(0, deck.passesCompleted(RiddleTier.EASY))
    }

    @Test fun newPassStartsAfterExhaustionAndDoesNotRepeatTheLastRiddleFirst() {
        for (seed in 0 until 20) {
            val store = MemStore()
            val d = RiddleDeck(store)
            val r = Random(seed)
            var l = ""
            repeat(80) { l = d.next(RiddleTier.HARD, bank, r).id }
            val first = d.next(RiddleTier.HARD, bank, r).id
            assertNotEquals("seed $seed repeated the last riddle", l, first)
            assertEquals(1, d.passesCompleted(RiddleTier.HARD))
            assertEquals(79, d.remaining(RiddleTier.HARD, bank))
        }
    }

    @Test fun tiersHaveIndependentDecks() {
        val deck = RiddleDeck(MemStore())
        val rnd = Random(3)
        repeat(10) { deck.next(RiddleTier.EASY, bank, rnd) }
        assertEquals(90, deck.remaining(RiddleTier.EASY, bank))
        assertEquals(120, deck.remaining(RiddleTier.MEDIUM, bank))
    }

    @Test fun progressSurvivesRestartBecauseItLivesInTheStore() {
        val store = MemStore()
        val rnd = Random(4)
        val first = (1..40).map { RiddleDeck(store).next(RiddleTier.MEDIUM, bank, rnd).id } // a new deck object each time = new process
        val second = (1..80).map { RiddleDeck(store).next(RiddleTier.MEDIUM, bank, rnd).id }
        assertEquals(120, (first + second).toSet().size)
    }

    @Test fun unknownIdsFromAnOlderBankAreIgnored() {
        val store = MemStore()
        store.putString("seen_easy", "e001,e999,zzz")
        val deck = RiddleDeck(store)
        assertEquals(99, deck.remaining(RiddleTier.EASY, bank))
        val next = deck.next(RiddleTier.EASY, bank, Random(5))
        assertNotEquals("e001", next.id)
    }

    @Test fun differentSeedsGiveDifferentFirstRiddles() {
        val firsts = (0 until 30).map { RiddleDeck(MemStore()).next(RiddleTier.MEDIUM, bank, Random(it)).id }.toSet()
        assertTrue("picks should be random, got ${firsts.size} distinct", firsts.size > 10)
    }
}

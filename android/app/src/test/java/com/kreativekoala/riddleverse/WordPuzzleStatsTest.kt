package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class WordPuzzleStatsTest {
    private class Fake : WordPuzzleKeyValueStore {
        val m = HashMap<String, Int>()
        override fun getInt(key: String, default: Int) = m[key] ?: default
        override fun putInt(key: String, value: Int) { m[key] = value }
    }

    @Test fun streakAndDistribution() {
        val s = WordPuzzleStats(Fake())
        s.record("wordguess", true, 3, 6)
        s.record("wordguess", true, 3, 6)
        s.record("wordguess", false, 6, 6)
        s.record("wordguess", true, 5, 6)
        val snap = s.snapshot("wordguess", 6)
        assertEquals(4, snap.played); assertEquals(3, snap.won)
        assertEquals(1, snap.streak); assertEquals(2, snap.bestStreak)
        assertEquals(listOf(0, 0, 2, 0, 1, 0), snap.distribution)
        assertEquals(75, snap.winPercent)
        assertEquals(0, s.snapshot("other").played)
    }
}

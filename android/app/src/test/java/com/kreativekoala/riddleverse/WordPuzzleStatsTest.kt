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

    /** The result card reads snapshot() in the same pass that first shows it, before the LaunchedEffect records the game. */
    @Test fun snapshotSubscribesCallerToRecord() {
        val s = WordPuzzleStats(Fake())
        val reads = mutableSetOf<Any>()
        val snap = androidx.compose.runtime.snapshots.Snapshot.takeSnapshot { reads.add(it) }
        try { snap.enter { s.snapshot("wordguess", 6) } } finally { snap.dispose() }
        assertTrue("snapshot() must read observable state so the card recomposes after record()", reads.isNotEmpty())
        s.record("wordguess", true, 2, 6)
        assertEquals(1, s.snapshot("wordguess", 6).played)
    }
}

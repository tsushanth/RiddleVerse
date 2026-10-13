package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class WordPuzzleListsTest {
    @Test fun listsLoadAndAreFiltered() {
        val l = WordPuzzleTestSupport.lists
        assertTrue(l.common.size > 30000)
        assertTrue(l.valid5.size >= 8900)
        assertTrue("crane" in l.valid5)
        assertTrue(l.common.all { it.all { c -> c in 'a'..'z' } })
        assertTrue(l.common.none { WordBlocklist.isBlocked(it) })
        assertFalse("fuck" in l.commonSet)
    }

    @Test fun blocklistCatchesInflections() {
        assertTrue(WordBlocklist.isBlocked("shits"))
        assertTrue(WordBlocklist.isBlocked("Whores"))
        assertFalse(WordBlocklist.isBlocked("grass"))
        assertFalse(WordBlocklist.isBlocked("class"))
        assertFalse(WordBlocklist.isBlocked("passes"))
    }

    @Test fun answerPoolHasNoSimplePlurals() {
        val l = WordPuzzleTestSupport.lists
        val pool = l.answerPool(0, 1400)
        assertTrue(pool.none { it.endsWith("s") && !it.endsWith("ss") && it.dropLast(1) in l.commonSet })
        assertTrue(pool.all { it.length == 5 })
    }
}

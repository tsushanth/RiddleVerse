package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class WordLadderLogicTest {
    private val graph = WordLadderGraph(listOf("cold", "cord", "card", "ward", "warm", "word", "worm", "corm", "core", "cure"))

    @Test fun bfsFindsShortestPath() {
        val p = graph.shortestPath("cold", "warm")!!
        assertEquals("cold", p.first()); assertEquals("warm", p.last())
        assertEquals(4, p.size - 1) // cold-cord-card-ward-warm
        for (i in 0 until p.size - 1) assertTrue(WordLadderRules.differsByOne(p[i], p[i + 1]))
    }

    @Test fun unreachableIsNull() {
        val g = WordLadderGraph(listOf("cold", "cord", "zzzz"))
        assertNull(g.shortestPath("cold", "zzzz"))
        assertNull(g.shortestPath("cold", "nope"))
    }

    @Test fun differsByOneRules() {
        assertTrue(WordLadderRules.differsByOne("cat", "cot"))
        assertFalse(WordLadderRules.differsByOne("cat", "cat"))
        assertFalse(WordLadderRules.differsByOne("cat", "dog"))
        assertFalse(WordLadderRules.differsByOne("cat", "cats"))
    }

    @Test fun rejections() {
        val chain = listOf("cold", "cord")
        assertEquals("Already used", WordLadderRules.rejection(graph, chain, "cold"))
        assertEquals("Change exactly one letter", WordLadderRules.rejection(graph, chain, "warm"))
        assertEquals("Change exactly one letter", WordLadderRules.rejection(graph, chain, "cord"))
        assertEquals("Not in word list", WordLadderRules.rejection(graph, chain, "cork"))
        assertNull(WordLadderRules.rejection(graph, chain, "card"))
    }

    @Test fun starsAndScore() {
        assertEquals(3, WordLadderRules.stars(4, 4))
        assertEquals(2, WordLadderRules.stars(6, 4))
        assertEquals(1, WordLadderRules.stars(7, 4))
        assertTrue(WordLadderRules.score(4, 4, 0) > WordLadderRules.score(4, 4, 2))
    }

    @Test fun generatedPuzzlesAreSolvableWithCorrectPar() {
        val lists = WordPuzzleTestSupport.lists
        for (d in listOf("Easy", "Medium", "Hard")) for (seed in 0L until 40L) {
            val p = WordLadderGenerator.generate(lists, d, seed)
            assertNotNull("$d $seed", p)
            p!!
            val (len, minPar, maxPar) = WordLadderGenerator.config(d)
            assertEquals(len, p.start.length)
            assertTrue("par ${p.par}", p.par in minPar..maxPar)
            val g = WordLadderGraph(WordLadderGenerator.poolFor(lists, len))
            val path = g.shortestPath(p.start, p.target)
            assertNotNull(path)
            assertEquals(p.par, path!!.size - 1)
            assertEquals(p.par + 1, p.solution.size)
            assertEquals(p.start, p.solution.first()); assertEquals(p.target, p.solution.last())
            for (i in 0 until p.solution.size - 1) assertTrue(WordLadderRules.differsByOne(p.solution[i], p.solution[i + 1]))
            assertEquals("no shortcut", p.par, g.distancesFrom(p.start).getValue(p.target))
        }
    }

    @Test fun deterministicForSeed() {
        val lists = WordPuzzleTestSupport.lists
        val a = WordLadderGenerator.generate(lists, "Medium", 7)!!
        val b = WordLadderGenerator.generate(lists, "Medium", 7)!!
        assertEquals(a, b)
        val c = WordLadderGenerator.generate(lists, "Medium", 8)!!
        assertNotEquals(a, c)
    }

    @Test fun jsonRoundTrip() {
        val p = WordLadderGenerator.generate(WordPuzzleTestSupport.lists, "Easy", 3)!!
        assertEquals(p, WordLadderPuzzle.fromJson(p.toJson(3, "Easy")))
    }
}

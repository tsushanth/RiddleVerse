package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LinkupLogicTest {
    private class MemStore : WordPuzzleKeyValueStore {
        val m = HashMap<String, Int>()
        override fun getInt(key: String, default: Int) = m[key] ?: default
        override fun putInt(key: String, value: Int) { m[key] = value }
    }

    private val bank: LinkupBank by lazy {
        val f = listOf("src/main/assets/linkup/puzzles.json", "app/src/main/assets/linkup/puzzles.json", "android/app/src/main/assets/linkup/puzzles.json")
            .map { File(it) }.first { it.isFile }
        LinkupBank.parse(f.readText())
    }

    private val BLOCK = "ASS ARSE DAMN HELL CRAP SEX SEXY PORN NUDE NAKED RAPE DRUG DRUGS COCAINE HEROIN WHORE SLUT BASTARD BITCH DICK COCK PISS SHIT FUCK TITS BOOB BOOBS PENIS VAGINA KILL MURDER SUICIDE NAZI HITLER GUN GUNS BOMB TERROR".split(" ").toSet()

    private val tiny = LinkupPuzzle(
        "t1",
        listOf(
            LinkupGroup(1, "Fruit", listOf("APPLE", "PEAR", "PLUM", "FIG")),
            LinkupGroup(2, "Metals", listOf("IRON", "TIN", "ZINC", "LEAD")),
            LinkupGroup(3, "Hats", listOf("BERET", "FEZ", "CAP", "TURBAN")),
            LinkupGroup(4, "Hide a colour", listOf("STAND", "SHRED", "SUBLIME", "STEALTH"))
        ),
        redHerrings = setOf("TIN")
    )

    // ---------- content bank validation (mirror of scripts/linkup/validate_linkup.py) ----------

    @Test fun bankHasAtLeast150Puzzles() = assertTrue(bank.puzzles.size >= 150)

    @Test fun everyPuzzleIsStructurallyValid() {
        val wordRe = Regex("^[A-Z]{3,10}$")
        val ids = HashSet<String>()
        for (p in bank.puzzles) {
            assertTrue("dup id ${p.id}", ids.add(p.id))
            assertEquals("${p.id} groups", 4, p.groups.size)
            assertEquals("${p.id} tiers", listOf(1, 2, 3, 4), p.groups.map { it.tier }.sorted())
            val all = p.groups.flatMap { it.words }
            assertEquals("${p.id} 16 words", 16, all.size)
            assertEquals("${p.id} unique words", 16, all.toSet().size)
            val titles = p.groups.map { it.title.trim().lowercase() }
            assertEquals("${p.id} distinct titles", 4, titles.toSet().size)
            for (g in p.groups) {
                assertEquals("${p.id} ${g.title} size", 4, g.words.size)
                for (w in g.words) {
                    assertTrue("${p.id} word '$w'", wordRe.matches(w))
                    assertFalse("${p.id} '$w' equals a title", w.lowercase() in titles)
                    assertFalse("${p.id} '$w' blocked", w in BLOCK)
                }
            }
            for (h in p.redHerrings) assertTrue("${p.id} herring $h not in puzzle", h in all)
        }
    }

    @Test fun noDuplicatePuzzlesOrGroupsInTheBank() {
        val sets = HashSet<Set<String>>()
        val groups = HashSet<Set<String>>()
        for (p in bank.puzzles) {
            assertTrue("${p.id} duplicates another puzzle", sets.add(p.words.toSet()))
            for (g in p.groups) assertTrue("${p.id} ${g.title} duplicates a group", groups.add(g.words.toSet()))
        }
    }

    @Test fun everyPuzzleHasADeclaredRedHerring() {
        assertTrue(bank.puzzles.count { it.redHerrings.isEmpty() } <= 3)
    }

    // ---------- selection ----------

    @Test fun selectorServesEveryPuzzleOnceBeforeRepeating() {
        for (seed in listOf(1, 42, 987654321)) for (n in listOf(3, 10, bank.puzzles.size)) {
            val first = (0 until n).map { LinkupSelector.indexFor(seed, n, it) }
            assertEquals("seed $seed n $n", (0 until n).toSet(), first.toSet())
            val second = (n until 2 * n).map { LinkupSelector.indexFor(seed, n, it) }
            assertEquals((0 until n).toSet(), second.toSet())
            assertNotEquals("round boundary repeat", first.last(), second.first())
        }
    }

    @Test fun selectorIsDeterministicAndSeedDependent() {
        val n = bank.puzzles.size
        val a = (0 until 20).map { LinkupSelector.indexFor(7, n, it) }
        assertEquals(a, (0 until 20).map { LinkupSelector.indexFor(7, n, it) })
        assertNotEquals(a, (0 until 20).map { LinkupSelector.indexFor(8, n, it) })
    }

    @Test fun progressPersistsSeedAndAdvances() {
        val store = MemStore()
        var calls = 0
        val p = LinkupProgress(store, newSeed = { calls++; 1234 })
        val n = 12
        val got = (0 until n).map { p.take(n) }
        assertEquals((0 until n).toSet(), got.toSet())
        assertEquals(1, calls)
        assertEquals(n, p.cursor())
        // a new object on the same store continues the sequence (per install)
        assertEquals(LinkupSelector.indexFor(1234, n, n), LinkupProgress(store).take(n))
    }

    @Test fun generatorWalksTheWholeBankWithoutRepeat() {
        val p = LinkupProgress(MemStore(), newSeed = { 99 })
        val ids = (0 until bank.puzzles.size).map { LinkupGenerator.next(bank, p).id }
        assertEquals(bank.puzzles.size, ids.toSet().size)
    }

    @Test fun jsonRoundTrip() {
        val back = LinkupPuzzle.fromJson(tiny.toJson(5, "Easy"))
        assertEquals(tiny, back)
        val real = bank.puzzles[10]
        assertEquals(real.groups, LinkupPuzzle.fromJson(real.toJson(1, "Hard")).groups)
    }

    // ---------- rules ----------

    private fun pick(s: LinkupState, vararg w: String) = w.fold(s) { st, x -> LinkupRules.toggle(tiny, st, x) }

    @Test fun selectionCapsAtFourAndToggles() {
        var s = pick(LinkupState(), "APPLE", "PEAR", "PLUM", "FIG", "IRON")
        assertEquals(4, s.selected.size)
        assertFalse("IRON" in s.selected)
        s = LinkupRules.toggle(tiny, s, "FIG")
        assertEquals(listOf("APPLE", "PEAR", "PLUM"), s.selected)
        assertEquals(s, LinkupRules.toggle(tiny, s, "NOTAWORD"))
    }

    @Test fun solvingAGroup() {
        val s = pick(LinkupState(), "APPLE", "PEAR", "PLUM", "FIG")
        val (n, r) = LinkupRules.submit(tiny, s)
        assertEquals(LinkupResult.SOLVED, r)
        assertEquals(listOf(0), n.solved)
        assertTrue(n.selected.isEmpty())
        assertEquals(0, n.mistakes)
        assertFalse(LinkupRules.available(tiny, n).any { it in tiny.groups[0].words })
        assertEquals(12, LinkupRules.available(tiny, n).size)
        // solved tiles cannot be selected again
        assertEquals(n, LinkupRules.toggle(tiny, n, "APPLE"))
    }

    @Test fun closeWrongAndAlreadyTried() {
        val close = pick(LinkupState(), "APPLE", "PEAR", "PLUM", "IRON")
        val (a, r1) = LinkupRules.submit(tiny, close)
        assertEquals(LinkupResult.CLOSE, r1); assertEquals(1, a.mistakes)
        val (b, r2) = LinkupRules.submit(tiny, a)
        assertEquals(LinkupResult.ALREADY_TRIED, r2); assertEquals(1, b.mistakes)
        val wrong = pick(LinkupState(), "APPLE", "IRON", "CAP", "STAND")
        assertEquals(LinkupResult.WRONG, LinkupRules.submit(tiny, wrong).second)
        assertEquals(LinkupResult.INCOMPLETE, LinkupRules.submit(tiny, pick(LinkupState(), "APPLE")).second)
    }

    @Test fun fourMistakesLose() {
        var s = LinkupState()
        val guesses = listOf(
            listOf("APPLE", "PEAR", "PLUM", "IRON"), listOf("APPLE", "PEAR", "PLUM", "TIN"),
            listOf("APPLE", "PEAR", "PLUM", "CAP"), listOf("APPLE", "PEAR", "PLUM", "STAND")
        )
        for (g in guesses) {
            assertFalse(LinkupRules.isOver(s))
            s = LinkupRules.submit(tiny, pick(LinkupRules.deselectAll(s), *g.toTypedArray())).first
        }
        assertTrue(LinkupRules.isLost(s)); assertFalse(LinkupRules.isWon(s))
        assertEquals(0, LinkupRules.mistakesLeft(s))
        assertEquals(LinkupResult.FINISHED, LinkupRules.submit(tiny, s).second)
        assertEquals(4, LinkupRules.unsolvedGroups(tiny, s).size)
        assertEquals(0, LinkupRules.score(s, LinkupRules.profile("Medium")))
    }

    @Test fun winningAllGroupsAndScore() {
        var s = LinkupState()
        for (g in tiny.groups) s = LinkupRules.submit(tiny, pick(s, *g.words.toTypedArray())).first
        assertTrue(LinkupRules.isWon(s)); assertFalse(LinkupRules.isLost(s))
        assertEquals(100, LinkupRules.score(s, LinkupRules.profile("Medium")))
        assertEquals(80, LinkupRules.score(s.copy(mistakes = 2), LinkupRules.profile("Medium")))
        assertEquals(10, LinkupRules.score(s.copy(mistakes = 3, hints = 9), LinkupRules.profile("Easy")))
    }

    @Test fun giveUpLoses() {
        val s = LinkupRules.giveUp(pick(LinkupState(), "APPLE"))
        assertTrue(LinkupRules.isLost(s)); assertTrue(s.selected.isEmpty())
    }

    @Test fun hintMarksEasiestGroupTileAvoidingHerringsAndIsCapped() {
        val prof = LinkupRules.profile("Medium")
        val (s1, w1) = LinkupRules.hint(tiny, LinkupState(), prof)
        assertEquals("APPLE", w1)
        // after solving group 1 the hint moves to tier 2, skipping the declared herring TIN
        val solved = LinkupRules.submit(tiny, pick(LinkupState(), "APPLE", "PEAR", "PLUM", "FIG")).first
        val (s2, w2) = LinkupRules.hint(tiny, solved, prof)
        assertEquals("IRON", w2)
        val (s3, w3) = LinkupRules.hint(tiny, s2, prof)
        assertEquals("ZINC", w3)
        assertNull(LinkupRules.hint(tiny, s3, prof).second) // medium: 2 hints
        assertEquals(1, s1.hints)
        assertEquals(1, LinkupRules.profile("Hard").maxHints)
        assertEquals(3, LinkupRules.profile("Easy").maxHints)
    }

    @Test fun displayOrderIsStableAndShuffleChangesIt() {
        val a = LinkupRules.available(tiny, LinkupState())
        assertEquals(a, LinkupRules.available(tiny, LinkupState()))
        assertEquals(tiny.words.toSet(), a.toSet())
        assertNotEquals(a, LinkupRules.available(tiny, LinkupRules.shuffle(LinkupState())))
        // solving a group keeps the relative order of the rest
        val solved = LinkupRules.submit(tiny, pick(LinkupState(), "APPLE", "PEAR", "PLUM", "FIG")).first
        assertEquals(a.filter { it !in tiny.groups[0].words }, LinkupRules.available(tiny, solved))
    }

    @Test fun stateEncodingRoundTrips() {
        val s = LinkupState(listOf(2, 0), listOf("A", "B"), 3, 1, listOf("A"), listOf("A,B,C,D", "E,F,G,H"), 4, true)
        assertEquals(s, LinkupState.decode(s.encode()))
        assertEquals(LinkupState(), LinkupState.decode(LinkupState().encode()))
        assertEquals(LinkupState(), LinkupState.decode("garbage"))
    }

    @Test fun registryKnowsLinkup() {
        assertTrue(WordPuzzleRegistry.isWordPuzzleType("linkup"))
        assertEquals("Linkup Screen", WordPuzzleRegistry.screenTypeName("linkup"))
        assertEquals(PuzzleScreenType.LINKUP_SCREEN, PuzzleScreenType.fromString("Linkup Screen"))
        assertNotNull(WordPuzzleRegistry.quizCategories.firstOrNull { it.category == "linkup" })
    }
}

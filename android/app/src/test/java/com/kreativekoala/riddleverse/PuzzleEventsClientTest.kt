package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class PuzzleEventsClientTest {
    private var now = 1_000_000L
    private val sent = mutableListOf<Pair<String, String?>>()
    private var status = 202
    private var throwOnSend = false
    private var enabled = true

    private fun client(token: String? = null) = PuzzleEventsClient(
        "inst-1", "1.2.3",
        send = { b, t -> if (throwOnSend) throw java.io.IOException("x"); sent += b to t; status },
        tokenProvider = { token }, clock = { now }, enabled = { enabled }
    )

    private fun ev(type: String = "start", pt: String = "sudoku", err: String? = null) =
        PuzzleEvent(type, pt, "Hard", "s1", ts = now, errorMessage = err)

    @Test fun bodyHasContractFields() {
        val c = client()
        c.enqueue(ev("complete").copy(durationMs = 5000, score = 7, moves = 3, hintsUsed = 1, mistakes = 2))
        assertEquals(1, c.flush())
        val b = sent[0].first
        assertTrue(b.startsWith("""{"installId":"inst-1","platform":"android","appVersion":"1.2.3","events":[{"type":"complete","puzzleType":"sudoku","difficulty":"hard","sessionId":"s1","durationMs":5000,"score":7,"moves":3,"hintsUsed":1,"mistakes":2,"ts":$now}]}"""))
    }

    @Test fun bearerTokenPassedWhenPresent() {
        val c = client("tok"); c.enqueue(ev()); c.flush()
        assertEquals("tok", sent[0].second)
        val a = client(); a.enqueue(ev()); a.flush()
        assertNull(sent[1].second)
    }

    @Test fun puzzleTypeWhitelist() {
        assertEquals("tile_merge2", PuzzleEventsClient.sanitizePuzzleType(" Tile_Merge2 "))
        assertNull(PuzzleEventsClient.sanitizePuzzleType("a-b"))
        assertNull(PuzzleEventsClient.sanitizePuzzleType("x".repeat(41)))
        assertNull(PuzzleEventsClient.sanitizePuzzleType(""))
        assertNull(PuzzleEventsClient.sanitizePuzzleType(null))
        val c = client(); c.enqueue(ev(pt = "bad type!")); assertEquals(0, c.size)
        c.enqueue(ev(type = "bogus")); assertEquals(0, c.size)
    }

    @Test fun difficultyNormalizedOrOmitted() {
        assertEquals("easy", PuzzleEventsClient.sanitizeDifficulty("EASY"))
        assertNull(PuzzleEventsClient.sanitizeDifficulty("insane"))
        val c = client(); c.enqueue(ev().copy(difficulty = "weird")); c.flush()
        assertFalse(sent[0].first.contains("difficulty"))
    }

    @Test fun errorMessageTruncatedAndScrubbed() {
        val long = "a".repeat(500)
        assertEquals(300, PuzzleEventsClient.sanitizeError(long)!!.length)
        val s = PuzzleEventsClient.sanitizeError("fail for bob@example.com at https://x.y/z?q=1\nline")!!
        assertFalse(s.contains("@")); assertFalse(s.contains("http")); assertFalse(s.contains("\n"))
        assertNull(PuzzleEventsClient.sanitizeError("   "))
        val c = client(); c.enqueue(ev("start", err = "ignored")); c.flush()
        assertFalse(sent[0].first.contains("errorMessage"))
        c.enqueue(ev("error", err = "say \"hi\"\\")); c.flush()
        assertTrue(sent[1].first.contains("""\"hi\"\\"""))
    }

    @Test fun flushSignalAtTwenty() {
        val c = client()
        repeat(19) { assertFalse(c.enqueue(ev())) }
        assertTrue(c.enqueue(ev()))
    }

    @Test fun batchesOfFifty() {
        val c = client()
        repeat(120) { c.enqueue(ev()) }
        assertEquals(120, c.flush())
        assertEquals(3, sent.size)
        assertEquals(50, Regex("\"type\"").findAll(sent[0].first).count())
        assertEquals(20, Regex("\"type\"").findAll(sent[2].first).count())
    }

    @Test fun queueBoundedDropsOldest() {
        val c = client()
        repeat(250) { i -> c.enqueue(ev().copy(sessionId = "s$i")) }
        assertEquals(200, c.size)
        c.flush(force = true)
        assertFalse(sent.joinToString { it.first }.contains("\"s0\""))
        assertTrue(sent.joinToString { it.first }.contains("\"s249\""))
    }

    @Test fun failureKeepsEventsAndBacksOffExponentially() {
        val c = client(); c.enqueue(ev()); status = 503
        assertEquals(0, c.flush())
        assertEquals(1, c.size); assertEquals(1, sent.size)
        assertEquals(0, c.flush())                 // inside backoff window: no send
        assertEquals(1, sent.size)
        now += 15_000; c.flush()                   // second failure -> 30 s
        assertEquals(2, sent.size); assertEquals(2, c.consecutiveFailures)
        now += 29_000; c.flush(); assertEquals(2, sent.size)
        now += 2_000; status = 202
        assertEquals(1, c.flush()); assertEquals(0, c.size); assertEquals(0, c.consecutiveFailures)
    }

    @Test fun backoffCurve() {
        assertEquals(0, PuzzleEventsClient.backoffMs(0))
        assertEquals(15_000, PuzzleEventsClient.backoffMs(1))
        assertEquals(30_000, PuzzleEventsClient.backoffMs(2))
        assertEquals(15 * 60_000L, PuzzleEventsClient.backoffMs(30))
    }

    @Test fun status404And429AreRetriedLater_400Dropped() {
        val c = client(); c.enqueue(ev()); status = 404; c.flush(); assertEquals(1, c.size)
        val d = client(); d.enqueue(ev()); status = 429; d.flush(); assertEquals(1, d.size)
        val e = client(); e.enqueue(ev()); status = 400; e.flush(); assertEquals(0, e.size)
    }

    @Test fun networkExceptionNeverThrows() {
        throwOnSend = true
        val c = client(); c.enqueue(ev())
        assertEquals(0, c.flush()); assertEquals(1, c.size)
    }

    @Test fun disabledSendsNothingAndClears() {
        val c = client(); c.enqueue(ev()); enabled = false
        assertEquals(0, c.flush()); assertTrue(sent.isEmpty()); assertEquals(0, c.size)
        assertFalse(c.enqueue(ev())); assertEquals(0, c.size)
    }

    @Test fun trackerPairsStartCompleteAbandon() {
        val t = PuzzleSessionTracker { now }
        val s = t.start("p1", "sudoku", "easy")!!
        assertEquals("start", s.type)
        assertNull(t.start("p1", "sudoku", "easy"))      // recompose: no duplicate start
        now += 4000
        val f = t.finish("p1", "sudoku", "easy", true, 90)
        assertEquals("complete", f.type); assertEquals(4000L, f.durationMs); assertEquals(s.sessionId, f.sessionId)
        assertNull(t.abandon("p1"))                       // completed: no abandon
        t.start("p2", "wordguess", null)
        now += 1000
        val a = t.abandon("p2")!!
        assertEquals("abandon", a.type); assertEquals(1000L, a.durationMs)
        assertNull(t.abandon("p2"))                       // only once
        assertEquals("fail", t.finish("p3", "x", null, false, 0).type)
    }
}

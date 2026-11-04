package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionScoringTest {
    private fun stats(score: Int, correct: Int, total: Int = 5, time: Int = 120) = SessionStatistics(
        totalScore = score, correctAnswers = correct, totalAnswers = total,
        winRate = if (total > 0) correct.toFloat() / total else 0f,
        totalTimeSeconds = time, averageTimePerPuzzle = if (total > 0) time / total else 0,
        bestStreak = correct, currentStreak = correct, individualTimes = List(maxOf(total, 0)) { time / maxOf(total, 1) }
    )

    @Test fun equalPerformanceGivesEqualPoints() {
        val a = CompletionScoring.pointsFor(stats(320, 5), false)
        repeat(50) { assertEquals(a, CompletionScoring.pointsFor(stats(320, 5), false)) }
    }

    @Test fun pointsComeFromTheRealSessionScoreOnTheLeaderboardScale() {
        assertEquals(640, CompletionScoring.pointsFor(stats(320, 5), false))
        assertEquals(100, CompletionScoring.pointsFor(stats(50, 1, 1), false)) // one slow easy puzzle (seen on device)
        assertEquals(320, CompletionScoring.pointsFor(stats(160, 1, 1), false)) // one fast medium puzzle
    }

    @Test fun betterPerformanceGivesMorePoints() {
        val worse = CompletionScoring.pointsFor(stats(150, 3), false)
        val better = CompletionScoring.pointsFor(stats(325, 5), false)
        assertTrue(better > worse)
        assertTrue(CompletionScoring.pointsFor(stats(160, 1, 1), false) > CompletionScoring.pointsFor(stats(100, 1, 1), false))
    }

    @Test fun typicalSessionsLandOnTheOldRange() {
        for (raw in listOf(50, 100, 160, 300, 380)) {
            val p = CompletionScoring.pointsFor(stats(raw, 1, 1), false)
            assertTrue("$raw -> $p", p in 100..1000)
        }
    }

    @Test fun nothingCorrectStillGetsTheMinimumNotZero() {
        assertEquals(CompletionScoring.MIN_POINTS, CompletionScoring.pointsFor(stats(0, 0), false))
        assertEquals(CompletionScoring.MIN_POINTS, CompletionScoring.pointsFor(null, false))
    }

    @Test fun absurdScoresAreBounded() {
        assertEquals(CompletionScoring.MAX_POINTS, CompletionScoring.pointsFor(stats(Int.MAX_VALUE, 5), false))
        assertEquals(CompletionScoring.MAX_POINTS, CompletionScoring.pointsFor(stats(50_000, 5), false))
        // a score the game cannot produce from one correct answer is capped by that answer (400 raw, x2)
        assertEquals(CompletionScoring.MAX_PER_CORRECT_ANSWER * 2, CompletionScoring.pointsFor(stats(900, 1), false))
        // negative and nonsense counts never go below the minimum or throw
        assertEquals(CompletionScoring.MIN_POINTS, CompletionScoring.pointsFor(stats(-50, -3, -1), false))
    }

    @Test fun customPuzzlesEarnHalfAndAreNeverDoubled() {
        assertEquals(320, CompletionScoring.pointsFor(stats(320, 5), true))
        assertEquals(CompletionScoring.MAX_POINTS / 2, CompletionScoring.pointsFor(stats(5000, 5), true))
        assertTrue(CompletionScoring.pointsFor(stats(320, 5), true) < CompletionScoring.pointsFor(stats(320, 5), false))
    }
}

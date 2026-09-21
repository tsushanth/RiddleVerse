package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionMathTest {

    // ---- levels -------------------------------------------------------

    @Test fun xpRequiredForLevel_matchesFormula() {
        assertEquals(110, ProgressionMath.xpRequiredForLevel(1))
        assertEquals(240, ProgressionMath.xpRequiredForLevel(2))
        assertEquals(390, ProgressionMath.xpRequiredForLevel(3))
    }

    @Test fun xpAtStartOfLevel_isCumulative() {
        assertEquals(0, ProgressionMath.xpAtStartOfLevel(1))
        assertEquals(110, ProgressionMath.xpAtStartOfLevel(2))
        assertEquals(350, ProgressionMath.xpAtStartOfLevel(3))
        assertEquals(740, ProgressionMath.xpAtStartOfLevel(4))
    }

    @Test fun levelFromXp_boundaries() {
        assertEquals(1, ProgressionMath.levelFromXp(0))
        assertEquals(1, ProgressionMath.levelFromXp(109))
        assertEquals(2, ProgressionMath.levelFromXp(110))
        assertEquals(2, ProgressionMath.levelFromXp(349))
        assertEquals(3, ProgressionMath.levelFromXp(350))
        assertEquals(3, ProgressionMath.levelFromXp(739))
        assertEquals(4, ProgressionMath.levelFromXp(740))
    }

    @Test fun levelFromXp_isMonotonic() {
        var previous = 1
        for (xp in 0..50_000 step 7) {
            val level = ProgressionMath.levelFromXp(xp)
            assertTrue("level dropped at xp=$xp", level >= previous)
            previous = level
        }
    }

    @Test fun xpWithinLevel_alwaysBetweenZeroAndRequired() {
        // Regression: currentXP used to be computed from the previous level's
        // requirement instead of the cumulative start of the level (wrong from level 3).
        for (xp in 0..20_000 step 13) {
            val level = ProgressionMath.levelFromXp(xp)
            val within = xp - ProgressionMath.xpAtStartOfLevel(level)
            assertTrue("negative progress at xp=$xp", within >= 0)
            assertTrue("progress >= level size at xp=$xp", within < ProgressionMath.xpRequiredForLevel(level))
        }
    }

    // ---- tiers --------------------------------------------------------

    @Test fun tierIndexForXp_boundaries() {
        assertEquals(0, ProgressionMath.tierIndexForXp(0))
        assertEquals(0, ProgressionMath.tierIndexForXp(499))
        assertEquals(1, ProgressionMath.tierIndexForXp(500))
        assertEquals(4, ProgressionMath.tierIndexForXp(9_999))
        assertEquals(5, ProgressionMath.tierIndexForXp(10_000))
        assertEquals(5, ProgressionMath.tierIndexForXp(1_000_000))
    }

    @Test fun tierIndexForXp_negativeXpDoesNotCrash() {
        assertEquals(0, ProgressionMath.tierIndexForXp(-5))
    }

    // ---- scoring ------------------------------------------------------

    @Test fun baseScore_knownAndUnknownTypes() {
        assertEquals(10, ProgressionMath.baseScore("math", "Easy"))
        assertEquals(20, ProgressionMath.baseScore("MATH", "Hard"))
        assertEquals(15, ProgressionMath.baseScore("unknown-type", "Medium"))
        assertEquals(15, ProgressionMath.baseScore("math", "Impossible"))
    }

    @Test fun timeBonus_noTimeLeftOrInvalidTotal() {
        assertEquals(0, ProgressionMath.timeBonus(0, 60, 20))
        assertEquals(0, ProgressionMath.timeBonus(-5, 60, 20))
        assertEquals(0, ProgressionMath.timeBonus(30, 0, 20))
    }

    @Test fun timeBonus_isCappedAtHalfOfBase() {
        assertEquals(10, ProgressionMath.timeBonus(60, 60, 20))
    }

    @Test fun timeBonus_shrinksAsTimeRunsOut() {
        val fast = ProgressionMath.timeBonus(50, 60, 40)
        val slow = ProgressionMath.timeBonus(10, 60, 40)
        assertTrue(fast > slow)
    }

    @Test fun streakBonus_tiers() {
        assertEquals(0, ProgressionMath.streakBonus(20, 2))
        assertEquals(4, ProgressionMath.streakBonus(20, 3))
        assertEquals(6, ProgressionMath.streakBonus(20, 5))
        assertEquals(10, ProgressionMath.streakBonus(20, 10))
        assertEquals(10, ProgressionMath.streakBonus(20, 99))
    }

    @Test fun streakMultiplier_tiers() {
        assertEquals(1.0f, ProgressionMath.streakMultiplier(0), 0f)
        assertEquals(1.25f, ProgressionMath.streakMultiplier(3), 0f)
        assertEquals(1.5f, ProgressionMath.streakMultiplier(5), 0f)
        assertEquals(2.0f, ProgressionMath.streakMultiplier(10), 0f)
    }

    @Test fun scoreBreakdown_usesProvidedScoreWhenPositive() {
        val b = ProgressionMath.scoreBreakdown("math", 30, 0, 60, "Easy", 1)
        assertEquals(30, b.baseScore)
        assertEquals(30, b.totalScore)
        assertEquals(b.totalScore, b.xpGained)
    }

    @Test fun scoreBreakdown_fallsBackToTableWhenScoreIsZero() {
        val b = ProgressionMath.scoreBreakdown("crypto", 0, 0, 60, "Hard", 1)
        assertEquals(28, b.baseScore)
        assertEquals(42, b.totalScore) // 28 * 1.5
    }

    @Test fun scoreBreakdown_combinesTimeStreakAndDifficulty() {
        // base 20, full time (+10), streak 5 (+6) => 36, Hard x1.5 => 54
        val b = ProgressionMath.scoreBreakdown("math", 20, 60, 60, "Hard", 5)
        assertEquals(10, b.timeBonus)
        assertEquals(6, b.streakBonus)
        assertEquals(54, b.totalScore)
    }

    // ---- daily streak -------------------------------------------------

    @Test fun nextDailyStreak_firstEverPlayStartsAtOne() {
        assertEquals(1, ProgressionMath.nextDailyStreak(false, false, false, 0))
    }

    @Test fun nextDailyStreak_playedYesterdayAdvances() {
        assertEquals(4, ProgressionMath.nextDailyStreak(true, false, true, 3))
    }

    @Test fun nextDailyStreak_secondPuzzleSameDayDoesNotAdvance() {
        assertEquals(3, ProgressionMath.nextDailyStreak(true, true, true, 3))
    }

    @Test fun nextDailyStreak_gapResetsToOne() {
        assertEquals(1, ProgressionMath.nextDailyStreak(true, false, false, 9))
    }

    @Test fun nextDailyStreak_neverReportsZeroOnADayWithActivity() {
        assertEquals(1, ProgressionMath.nextDailyStreak(true, true, false, 0))
    }

    @Test fun nextDailyStreak_recoversUsersStuckAtZero() {
        // Regression: the old logic evaluated after today's play was recorded, so the
        // streak was stuck at 0 forever. A stuck user who played yesterday now moves to 1.
        assertEquals(1, ProgressionMath.nextDailyStreak(true, false, true, 0))
    }
}

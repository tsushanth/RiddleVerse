package com.kreativekoala.riddleverse

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestDataMigrationTest {
    private val gson = Gson()
    private fun stats(plays: Int, wins: Int, high: Int, total: Int, top: List<Int> = emptyList()) =
        gson.toJson(UserPuzzleStats(totalPlays = plays, wins = wins, highScore = high, totalScore = total, topScores = top,
            totalTimeSpentSeconds = plays * 10, lastPlayedTimestamp = plays.toLong()))
    private fun parse(s: Any?) = gson.fromJson(s as String, UserPuzzleStats::class.java)

    /** Applies a plan like SharedPrefsStore does. */
    private fun Map<String, Any?>.applyPlan(p: GuestDataMigration.Plan): Map<String, Any?> {
        val m = toMutableMap(); p.removes.forEach { m.remove(it) }; m.putAll(p.puts); return m
    }

    @Test fun puzzleStatsMoveToUid() {
        val before = mapOf<String, Any?>("guest_user_anagram" to stats(3, 2, 40, 90, listOf(40, 30, 20)))
        val after = before.applyPlan(GuestDataMigration.planPuzzleStats(before, "uid1"))
        assertNull(after["guest_user_anagram"])
        val s = parse(after["uid1_anagram"])
        assertEquals(3, s.totalPlays); assertEquals(2, s.wins); assertEquals(40, s.highScore)
    }

    @Test fun puzzleStatsMergeWithExistingAccountData() {
        val before = mapOf<String, Any?>(
            "guest_user_anagram" to stats(3, 2, 40, 90, listOf(40, 30, 20)),
            "uid1_anagram" to stats(10, 7, 55, 300, listOf(55, 50)),
            "uid2_anagram" to stats(1, 1, 5, 5) // other account untouched
        )
        val after = before.applyPlan(GuestDataMigration.planPuzzleStats(before, "uid1"))
        val s = parse(after["uid1_anagram"])
        assertEquals(13, s.totalPlays); assertEquals(9, s.wins); assertEquals(55, s.highScore); assertEquals(390, s.totalScore)
        assertEquals(listOf(55, 50, 40, 30, 20), s.topScores)
        assertEquals(before["uid2_anagram"], after["uid2_anagram"])
    }

    @Test fun migrationIsIdempotent() {
        val before = mapOf<String, Any?>("guest_user_anagram" to stats(3, 2, 40, 90))
        val once = before.applyPlan(GuestDataMigration.planPuzzleStats(before, "uid1"))
        val twice = once.applyPlan(GuestDataMigration.planPuzzleStats(once, "uid1"))
        assertEquals(once, twice)
    }

    @Test fun corruptGuestStatsAreDroppedNotCrashing() {
        val before = mapOf<String, Any?>("guest_user_anagram" to "{not json", "uid1_anagram" to stats(1, 1, 1, 1))
        val after = before.applyPlan(GuestDataMigration.planPuzzleStats(before, "uid1"))
        assertNull(after["guest_user_anagram"])
        assertEquals(1, parse(after["uid1_anagram"]).totalPlays)
    }

    @Test fun globalCountersAreSummedAndBestsKept() {
        val before = mapOf<String, Any?>(
            "guest_user_global" to gson.toJson(GlobalUserStats(100, 5, 600, 50L, 1000L)),
            "uid1_global" to gson.toJson(GlobalUserStats(400, 20, 3000, 90L, 500L)),
            "guest_user_daily_2026-11-01" to 3, "uid1_daily_2026-11-01" to 2,
            "guest_user_weekly_2026-W44" to 4,
            "guest_user_last_active_date" to "2026-11-01", "uid1_last_active_date" to "2026-10-30",
            "guest_user_achievement_first_win" to true,
            "guest_user_achievement_progress_streak" to 3, "uid1_achievement_progress_streak" to 5
        )
        val after = before.applyPlan(GuestDataMigration.planGlobalStats(before, "uid1"))
        val g = gson.fromJson(after["uid1_global"] as String, GlobalUserStats::class.java)
        assertEquals(500, g.totalXP); assertEquals(25, g.totalGamesPlayed); assertEquals(3600, g.totalTimePlayedSeconds)
        assertEquals(90L, g.lastActiveTimestamp); assertEquals(500L, g.joinDate)
        assertEquals(5, after["uid1_daily_2026-11-01"]); assertEquals(4, after["uid1_weekly_2026-W44"])
        assertEquals("2026-11-01", after["uid1_last_active_date"])
        assertEquals(true, after["uid1_achievement_first_win"])
        assertEquals(5, after["uid1_achievement_progress_streak"])
        assertTrue(after.keys.none { it.startsWith("guest_user_") })
    }

    @Test fun dailyCompletionsNeedAnEmail() {
        val before = mapOf<String, Any?>("guest_user_2026-11-01_completions" to 2, "a@b.com_2026-11-01_completions" to 1)
        val none = GuestDataMigration.planDailyCompletions(before, null)
        assertTrue(none.puts.isEmpty()); assertTrue(none.removes.isEmpty())
        val after = before.applyPlan(GuestDataMigration.planDailyCompletions(before, "a@b.com"))
        assertEquals(3, after["a@b.com_2026-11-01_completions"])
        assertFalse(after.containsKey("guest_user_2026-11-01_completions"))
    }

    @Test fun groupCompletionIsUnioned() {
        val before = mapOf<String, Any?>(
            "completed_types__daily_mix" to "anagram,wordprefix",
            "completed_types_a@b.com_daily_mix" to "wordprefix,sudoku",
            "completed_types_x@y.com_daily_mix" to "other"
        )
        val after = before.applyPlan(GuestDataMigration.planGroupCompletion(before, "a@b.com"))
        assertEquals("anagram,sudoku,wordprefix", after["completed_types_a@b.com_daily_mix"])
        assertFalse(after.containsKey("completed_types__daily_mix"))
        assertEquals("other", after["completed_types_x@y.com_daily_mix"])
    }

    @Test fun groupCompletionWithoutEmailIsLeftAlone() {
        val before = mapOf<String, Any?>("completed_types__daily_mix" to "anagram")
        val p = GuestDataMigration.planGroupCompletion(before, "")
        assertTrue(p.removes.isEmpty())
    }
}

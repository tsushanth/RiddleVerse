package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyStreakRulesTest {
    private val none = DailySolveStreak()
    private val d0 = 20_000L

    @Test fun firstSolve_startsAtOne() {
        val o = DailyStreakRules.applySolve(none, d0)
        assertTrue(o.counted); assertEquals(1, o.streak); assertEquals(d0, o.state.lastSolveDay)
        assertEquals(1, o.state.best); assertEquals(1, o.state.totalSolved)
    }

    @Test fun consecutiveDays_extend() {
        var s = none
        for (i in 0 until 5) s = DailyStreakRules.applySolve(s, d0 + i).state
        assertEquals(5, s.current); assertEquals(5, s.best); assertEquals(5, s.totalSolved)
    }

    @Test fun sameDayTwice_isIdempotent() {
        val once = DailyStreakRules.applySolve(none, d0).state
        val twice = DailyStreakRules.applySolve(once, d0)
        assertFalse(twice.counted)
        assertEquals(once, twice.state)
        assertEquals(1, twice.streak)
    }

    @Test fun missedDay_resetsToOne_butKeepsBest() {
        var s = none
        for (i in 0 until 4) s = DailyStreakRules.applySolve(s, d0 + i).state
        val o = DailyStreakRules.applySolve(s, d0 + 3 + 2) // skipped one day
        assertEquals(1, o.streak); assertEquals(4, o.state.best)
    }

    @Test fun clockMovedBack_doesNotCountOrBreak() {
        val s = DailyStreakRules.applySolve(none, d0 + 10).state
        val o = DailyStreakRules.applySolve(s, d0 + 5)
        assertFalse(o.counted); assertEquals(s, o.state)
    }

    @Test fun display_aliveToday_andTomorrow_thenZero() {
        val s = DailyStreakRules.applySolve(none, d0).state
        assertEquals(1, DailyStreakRules.display(s, d0))      // solved today
        assertEquals(1, DailyStreakRules.display(s, d0 + 1))  // still time to play today
        assertEquals(0, DailyStreakRules.display(s, d0 + 2))  // a whole day missed
        assertEquals(0, DailyStreakRules.display(none, d0))
    }

    @Test fun solvedOn() {
        val s = DailyStreakRules.applySolve(none, d0).state
        assertTrue(DailyStreakRules.solvedOn(s, d0)); assertFalse(DailyStreakRules.solvedOn(s, d0 + 1))
    }

    @Test fun openingTheApp_orFailing_isNotASolve() {
        // There is no API that extends a streak except applySolve; display() alone never changes state.
        val s = DailyStreakRules.applySolve(none, d0).state
        assertEquals(s, s.copy())
        assertEquals(0, DailyStreakRules.display(s, d0 + 5))
    }

    // Timezone boundary: the streak is counted in UTC days, so what the phone's clock says never matters.
    @Test fun timezoneBoundary_solveLateInTokyo_andEarlyInLosAngeles_areConsecutiveUtcDays() {
        fun utcDay(z: ZonedDateTime) = DailyPuzzleSchedule.dayOf(z.toInstant().toEpochMilli())
        // 2026-11-02 08:30 Tokyo = 2026-11-01 23:30 UTC (day N); 2026-11-02 01:00 Los Angeles = 2026-11-02 09:00 UTC (day N+1)
        val a = utcDay(ZonedDateTime.of(2026, 11, 2, 8, 30, 0, 0, ZoneId.of("Asia/Tokyo")))
        val b = utcDay(ZonedDateTime.of(2026, 11, 2, 1, 0, 0, 0, ZoneId.of("America/Los_Angeles")))
        assertEquals(a + 1, b)
        val s1 = DailyStreakRules.applySolve(none, a).state
        assertEquals(2, DailyStreakRules.applySolve(s1, b).streak)
    }

    @Test fun timezoneBoundary_travellerCannotSolveTwiceInOneUtcDay() {
        // Same UTC day seen from two zones with different local dates.
        val tokyo = ZonedDateTime.of(2026, 11, 2, 7, 0, 0, 0, ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli()      // 22:00 UTC Nov 1
        val honolulu = ZonedDateTime.of(2026, 11, 1, 13, 0, 0, 0, ZoneId.of("Pacific/Honolulu")).toInstant().toEpochMilli() // 23:00 UTC Nov 1
        val day = DailyPuzzleSchedule.dayOf(tokyo)
        assertEquals(day, DailyPuzzleSchedule.dayOf(honolulu))
        val once = DailyStreakRules.applySolve(none, day).state
        assertFalse(DailyStreakRules.applySolve(once, DailyPuzzleSchedule.dayOf(honolulu)).counted)
    }

    // ---- migration of the old "days you opened the app" streak ----

    private val today = "2026-11-02"
    private val yesterday = "2026-11-01"
    private val utc = LocalDate.of(2026, 11, 2).toEpochDay()

    @Test fun migration_liveStreakOpenedToday_isKept_andContinuesIfSolvedToday() {
        val m = DailyStreakRules.migrateOpenStreak(12, 20, today, today, yesterday, utc)
        assertEquals(12, m.current); assertEquals(20, m.best)
        assertEquals(12, DailyStreakRules.display(m, utc))
        assertEquals(13, DailyStreakRules.applySolve(m, utc).streak)
    }

    @Test fun migration_openedYesterday_isKept_untilEndOfToday() {
        val m = DailyStreakRules.migrateOpenStreak(7, 7, yesterday, today, yesterday, utc)
        assertEquals(7, DailyStreakRules.display(m, utc))
        assertEquals(8, DailyStreakRules.applySolve(m, utc).streak)
        assertEquals(0, DailyStreakRules.display(m, utc + 1)) // not solved today -> gone tomorrow
    }

    @Test fun migration_alreadyBrokenStreak_isZero_butBestKept() {
        val m = DailyStreakRules.migrateOpenStreak(9, 15, "2026-10-20", today, yesterday, utc)
        assertEquals(0, m.current); assertEquals(15, m.best); assertNull(m.lastSolveDay)
        assertEquals(1, DailyStreakRules.applySolve(m, utc).streak)
    }

    @Test fun migration_neverOpenedBefore_isEmpty() {
        val m = DailyStreakRules.migrateOpenStreak(0, 0, "", today, yesterday, utc)
        assertEquals(DailySolveStreak(), m)
    }

    @Test fun migration_bestNeverBelowCurrent() {
        val m = DailyStreakRules.migrateOpenStreak(5, 3, today, today, yesterday, utc)
        assertEquals(5, m.best)
    }
}

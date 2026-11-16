package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class DailyRetentionTest {
    private val d0 = 20_000L

    // ---- streak freezes ----

    @Test fun oneMissedDay_withAFreeze_keepsTheStreakAndSpendsIt() {
        val s = DailySolveStreak(current = 4, best = 4, lastSolveDay = d0, totalSolved = 4, freezes = 1)
        val o = DailyStreakRules.applySolve(s, d0 + 2)
        assertTrue(o.counted); assertEquals(5, o.streak); assertEquals(1, o.freezesUsed); assertEquals(0, o.state.freezes)
    }

    @Test fun twoMissedDays_needTwoFreezes() {
        val s = DailySolveStreak(current = 4, best = 4, lastSolveDay = d0, freezes = 2)
        val o = DailyStreakRules.applySolve(s, d0 + 3)
        assertEquals(5, o.streak); assertEquals(2, o.freezesUsed); assertEquals(0, o.state.freezes)
        val one = DailyStreakRules.applySolve(s.copy(freezes = 1), d0 + 3)
        assertEquals(1, one.streak); assertEquals(0, one.freezesUsed); assertEquals(1, one.state.freezes) // restart keeps the bag
    }

    @Test fun noFreeze_oneMissedDay_resets() {
        val s = DailySolveStreak(current = 4, best = 9, lastSolveDay = d0, freezes = 0)
        val o = DailyStreakRules.applySolve(s, d0 + 2)
        assertEquals(1, o.streak); assertEquals(9, o.state.best)
    }

    @Test fun consecutiveSolves_spendNothing() {
        val s = DailySolveStreak(current = 2, best = 2, lastSolveDay = d0, freezes = 1)
        val o = DailyStreakRules.applySolve(s, d0 + 1)
        assertEquals(3, o.streak); assertEquals(0, o.freezesUsed); assertEquals(1, o.state.freezes)
    }

    @Test fun freezeEarnedEverySeventhDay_cappedAtMax() {
        var s = DailySolveStreak(current = 6, best = 6, lastSolveDay = d0, freezes = 0)
        val seven = DailyStreakRules.applySolve(s, d0 + 1)
        assertTrue(seven.freezeEarned); assertEquals(1, seven.state.freezes)
        s = DailySolveStreak(current = 13, best = 13, lastSolveDay = d0, freezes = DailyStreakRules.MAX_FREEZES)
        val fourteen = DailyStreakRules.applySolve(s, d0 + 1)
        assertFalse(fourteen.freezeEarned); assertEquals(DailyStreakRules.MAX_FREEZES, fourteen.state.freezes)
    }

    @Test fun display_staysAliveWhileFreezesCoverTheGap() {
        val s = DailySolveStreak(current = 5, best = 5, lastSolveDay = d0, freezes = 1)
        assertEquals(5, DailyStreakRules.display(s, d0 + 1))
        assertEquals(5, DailyStreakRules.display(s, d0 + 2)) // one missed day, one freeze
        assertEquals(0, DailyStreakRules.display(s, d0 + 3)) // two missed days
        assertEquals(0, DailyStreakRules.display(s.copy(freezes = 0), d0 + 2))
    }

    @Test fun sameDaySolveTwice_neverSpendsOrEarns() {
        val s = DailySolveStreak(current = 6, best = 6, lastSolveDay = d0, freezes = 1)
        val o = DailyStreakRules.applySolve(s, d0)
        assertFalse(o.counted); assertEquals(s, o.state); assertEquals(0, o.freezesUsed); assertFalse(o.freezeEarned)
    }

    @Test fun migratedPlayersStartWithTheStartingFreeze() {
        val m = DailyStreakRules.migrateOpenStreak(3, 3, "2026-11-04", "2026-11-04", "2026-11-03", d0)
        assertEquals(DailyStreakRules.STARTING_FREEZES, m.freezes)
    }

    // ---- reminder policy ----

    @Test fun reminder_sendsOnlyWhenEnabledAllowedUnsolvedAndNotIgnored() {
        assertTrue(ReminderPolicy.shouldSend(true, true, false, 0))
        assertFalse(ReminderPolicy.shouldSend(false, true, false, 0))
        assertFalse(ReminderPolicy.shouldSend(true, false, false, 0))
        assertFalse(ReminderPolicy.shouldSend(true, true, true, 0))
        assertTrue(ReminderPolicy.shouldSend(true, true, false, ReminderPolicy.MAX_UNOPENED - 1))
        assertFalse(ReminderPolicy.shouldSend(true, true, false, ReminderPolicy.MAX_UNOPENED))
    }

    @Test fun reminder_messageMentionsStreakOnlyWhenThereIsOne() {
        // The text itself lives in string resources (localized); the policy only chooses which one and the plural count.
        val streaking = ReminderPolicy.message(5)
        assertEquals(R.plurals.daily_reminder_title_streak, streaking.title); assertEquals(5, streaking.streak)
        assertEquals(R.string.daily_reminder_body_streak, streaking.body)
        listOf(0, 1).forEach { s ->
            val m = ReminderPolicy.message(s)
            assertEquals(R.string.daily_reminder_title, m.title); assertNull(m.streak); assertEquals(R.string.daily_reminder_body, m.body)
        }
    }

    @Test fun reminder_nextTriggerIsTheNextLocalHour() {
        val utc = TimeZone.getTimeZone("UTC")
        val day = 24 * 3600 * 1000L
        val base = 100 * day // 00:00 UTC
        assertEquals(base + 12 * 3600_000L, ReminderPolicy.nextTriggerAt(base + 3 * 3600_000L, 12, utc))
        assertEquals(base + day + 12 * 3600_000L, ReminderPolicy.nextTriggerAt(base + 12 * 3600_000L, 12, utc)) // already 12:00 -> tomorrow
        assertEquals(base + day + 8 * 3600_000L, ReminderPolicy.nextTriggerAt(base + 13 * 3600_000L, 8, utc))
        val la = TimeZone.getTimeZone("America/Los_Angeles")
        val t = ReminderPolicy.nextTriggerAt(base, 12, la)
        assertTrue(t > base && t - base <= day + 1)
    }

    // ---- welcome back ----

    @Test fun welcomeBack_needsAtLeastThreeDaysAway() {
        assertEquals(0, WelcomeBackRules.awayDays(null, 500))
        assertEquals(0, WelcomeBackRules.awayDays(-1, 500))
        assertEquals(0, WelcomeBackRules.awayDays(499, 500))
        assertEquals(0, WelcomeBackRules.awayDays(498, 500))
        assertEquals(3, WelcomeBackRules.awayDays(497, 500))
        assertEquals(60, WelcomeBackRules.awayDays(440, 500))
    }

    @Test fun welcomeBack_messageStatesFactsOnly() {
        val alive = WelcomeBackRules.message(4, 6, 6, 12)
        assertEquals(R.string.welcome_back_msg_streak, alive.res); assertEquals(listOf(4, 6, 12), alive.args)
        val best = WelcomeBackRules.message(9, 0, 8, 12)
        assertEquals(R.string.welcome_back_msg_best, best.res); assertEquals(listOf(9, 8, 12), best.args)
        val plain = WelcomeBackRules.message(30, 0, 0, 12)
        assertEquals(R.string.welcome_back_msg_plain, plain.res); assertEquals(listOf(30, 12), plain.args)
    }
}

class SupporterRulesTest {
    private fun show(
        flag: Boolean = true, pkg: Boolean = true, account: Boolean = true, sup: Boolean = false, solved: Boolean = true,
        shows: Int = 0, last: Long? = null, today: Long = 100L, dismissed: Boolean = false,
    ) = SupporterRules.shouldShow(flag, pkg, account, sup, solved, shows, last, today, dismissed)

    @Test fun shownOnlyWhenEverythingLinesUp() {
        assertTrue(show())
        assertFalse("flag off", show(flag = false))
        assertFalse("no products loaded", show(pkg = false))
        assertFalse("guest", show(account = false))
        assertFalse("already a supporter", show(sup = true))
        assertFalse("not after a win", show(solved = false))
        assertFalse("said no thanks", show(dismissed = true))
    }

    @Test fun cappedAndSpacedOut() {
        assertFalse(show(shows = SupporterRules.MAX_SHOWS))
        assertTrue(show(shows = SupporterRules.MAX_SHOWS - 1, last = 100L - SupporterRules.MIN_DAYS_BETWEEN_SHOWS))
        assertFalse(show(shows = 1, last = 100L - SupporterRules.MIN_DAYS_BETWEEN_SHOWS + 1))
    }

    @Test fun flagFileParsing_anythingOddMeansOff() {
        assertTrue(SupporterRules.parseFlag("{\"supporter_offer\": true}"))
        assertFalse(SupporterRules.parseFlag("{\"supporter_offer\": false}"))
        assertFalse(SupporterRules.parseFlag("{}"))
        assertFalse(SupporterRules.parseFlag("<html>502</html>"))
        assertFalse(SupporterRules.parseFlag("{\"supporter_offer\": \"yes\"}"))
        assertFalse(SupporterRules.parseFlag(null))
    }

    @Test fun supporterFreezeLimits() {
        val s = DailySolveStreak(current = 4, best = 4, lastSolveDay = 10L, freezes = 4)
        val o = DailyStreakRules.applySolve(s, 11L, maxFreezes = DailyStreakRules.SUPPORTER_MAX_FREEZES, freezeEvery = DailyStreakRules.SUPPORTER_FREEZE_EVERY)
        assertTrue(o.freezeEarned); assertEquals(5, o.state.freezes) // 5th streak day earns, cap 5
        val capped = DailyStreakRules.applySolve(o.state.copy(current = 9, lastSolveDay = 20L), 21L,
            maxFreezes = DailyStreakRules.SUPPORTER_MAX_FREEZES, freezeEvery = DailyStreakRules.SUPPORTER_FREEZE_EVERY)
        assertFalse(capped.freezeEarned); assertEquals(5, capped.state.freezes)
        assertFalse(DailyStreakRules.applySolve(s.copy(freezes = 2, current = 4), 11L).freezeEarned) // free: every 7th only
    }
}

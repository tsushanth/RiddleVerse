package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class FunnelTest {
    private val day = 24 * 3600 * 1000L
    private class Mem : FunnelStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
    }

    // UTC midnight-aligned epoch so "local day" boundaries are easy to reason about.
    private val t0 = 100 * day + 10 * 3600 * 1000L // 10:00 on day 100
    private var now = t0
    private fun state(store: FunnelStore = Mem(), install: Long = t0, offset: Long = 0L) =
        FunnelState(store, install, clock = { now }, offsetMs = { offset })

    @Test fun stepsLoggedOncePerInstallAndSurviveRestart() {
        val store = Mem()
        val s = state(store)
        assertTrue(s.shouldLog("first_open"))
        assertFalse(s.shouldLog("first_open"))
        assertTrue(s.shouldLog("welcome_shown"))
        val restarted = state(store) // new process, same prefs
        assertFalse(restarted.shouldLog("first_open"))
        assertFalse(restarted.shouldLog("welcome_shown"))
        assertTrue(restarted.shouldLog("home_shown"))
    }

    @Test fun unknownStepsRejected() {
        val s = state()
        assertFalse(s.shouldLog("user@example.com"))
        assertFalse(s.shouldLog("app_open"))
        assertFalse(s.shouldLog(""))
    }

    @Test fun allEighteenStepsAreAllowListed() {
        val s = state()
        val install = listOf("first_open", "welcome_shown", "auth_started", "auth_done", "guest_started",
            "onboarding_done", "home_shown", "first_puzzle_started", "first_puzzle_completed")
        val retention = listOf("daily_completed", "streak_3", "streak_7", "reminder_enabled",
            "notif_permission_granted", "notif_permission_denied", "welcome_back_shown",
            "supporter_offer_shown", "supporter_purchased")
        assertEquals(install.toSet(), PuzzleEventsClient.INSTALL_STEPS)
        assertEquals(retention.toSet(), PuzzleEventsClient.RETENTION_STEPS)
        assertEquals((install + retention).toSet(), PuzzleEventsClient.FUNNEL_STEPS)
        (install + retention).forEach { assertTrue(it, s.shouldLog(it)) }
    }

    @Test fun legacyInstallStillReportsRetentionSteps() {
        // The 21k dormant installs are all "legacy": they must report the comeback steps even though they skip install steps.
        val s = state(install = t0 - 90 * day)
        assertFalse(s.shouldLog("first_open"))
        assertTrue(s.shouldLog("daily_completed"))
        assertFalse(s.shouldLog("daily_completed"))
        assertTrue(s.shouldLog("welcome_back_shown"))
    }

    @Test fun dailySolvedIsRepeatableWithDaysAndStreakInScore() {
        val c = client()
        assertFalse(c.enqueue(PuzzleEvent("daily_solved", "app", null, "s", score = 4, ts = now))) // missing days -> rejected
        assertEquals(0, c.size)
        c.enqueue(PuzzleEvent("daily_solved", "app", "hard", "s", score = 4, ts = now, daysSinceInstall = 12, hintsUsed = 3))
        c.enqueue(PuzzleEvent("daily_solved", "app", null, "s2", score = 5, ts = now, daysSinceInstall = 13))
        assertEquals(2, c.size)
        c.flush(force = true)
        val body = sent.last()
        assertTrue(body, body.contains("\"type\":\"daily_solved\"") && body.contains("\"score\":4") && body.contains("\"daysSinceInstall\":12"))
        assertFalse(body, body.contains("hintsUsed") || body.contains("difficulty"))
    }

    @Test fun daysSinceInstallDoesNotConsumeTheDailyAppOpen() {
        val s = state()
        assertEquals(0, s.daysSinceInstall())
        assertEquals(0, s.appOpenDays())
        now = t0 + 3 * day
        assertEquals(3, s.daysSinceInstall())
        assertEquals(3, s.appOpenDays())
    }

    @Test fun appOpenOncePerLocalDayWithDaysSinceInstall() {
        val s = state()
        assertEquals(0, s.appOpenDays())
        now += 3 * 3600 * 1000L // 13:00 same day
        assertNull(s.appOpenDays())
        now = t0 + 14 * 3600 * 1000L // 00:00 next day (day 101)
        assertEquals(1, s.appOpenDays())
        assertNull(s.appOpenDays())
        now = t0 + 6 * day
        assertEquals(6, s.appOpenDays())
        now = t0 + 7 * day
        assertEquals(7, s.appOpenDays())
    }

    @Test fun localDayBoundaryUsesTimezoneOffset() {
        // Installed 23:00 UTC; in UTC+2 that is already 01:00 the next local day.
        val install = 100 * day + 23 * 3600 * 1000L
        now = install + 30 * 60 * 1000L
        val s = FunnelState(Mem(), install, clock = { now }, offsetMs = { 2 * 3600 * 1000L })
        assertEquals(0, s.appOpenDays())
        now = install + 24 * 3600 * 1000L // same local clock time, next day
        assertEquals(1, s.appOpenDays())
    }

    @Test fun appOpenPersistsAcrossRestarts() {
        val store = Mem()
        assertEquals(0, state(store).appOpenDays())
        assertNull(state(store).appOpenDays())
    }

    @Test fun clockBeforeInstallClampsToZero() {
        now = t0 - 5 * day
        assertEquals(0, state().appOpenDays())
    }

    @Test fun legacyInstallSkipsOneTimeStepsButStillReportsOpens() {
        now = t0
        val s = state(install = t0 - 30 * day) // updated into this version, installed 30 days ago
        assertFalse(s.shouldLog("first_open"))
        assertFalse(s.shouldLog("home_shown"))
        assertEquals(30, s.appOpenDays())
    }

    @Test fun newInstallJustUnderLegacyThresholdIsNotLegacy() {
        val s = state(install = t0 - FunnelState.LEGACY_AFTER_MS + 1)
        assertTrue(s.shouldLog("first_open"))
    }

    // ---- allow-listing in the client + wire format ----

    private val sent = mutableListOf<String>()
    private fun client(enabled: Boolean = true) = PuzzleEventsClient("inst-1", "1.0", send = { b, _ -> sent += b; 202 },
        clock = { now }, enabled = { enabled })

    @Test fun funnelEventSerialisedWithAppPuzzleTypeAndNoExtras() {
        val c = client()
        c.enqueue(PuzzleEvent("funnel", "whatever", "hard", "s", score = 9, ts = 5, funnelStep = "first_open"))
        c.flush()
        assertEquals("""{"installId":"inst-1","platform":"android","appVersion":"1.0","events":[{"type":"funnel","puzzleType":"app","sessionId":"s","funnelStep":"first_open","ts":5}]}""", sent[0])
    }

    @Test fun appOpenSerialised() {
        val c = client()
        c.enqueue(PuzzleEvent("app_open", "app", null, "s", ts = 5, daysSinceInstall = 7))
        c.flush()
        assertTrue(sent[0].contains(""""type":"app_open","puzzleType":"app","sessionId":"s","daysSinceInstall":7,"ts":5"""))
    }

    @Test fun clientDropsUnknownStepsMissingStepAndBadDays() {
        val c = client()
        c.enqueue(PuzzleEvent("funnel", "app", null, "s", ts = 1, funnelStep = "bob@example.com"))
        c.enqueue(PuzzleEvent("funnel", "app", null, "s", ts = 1))
        c.enqueue(PuzzleEvent("app_open", "app", null, "s", ts = 1))
        c.enqueue(PuzzleEvent("app_open", "app", null, "s", ts = 1, daysSinceInstall = -1))
        c.enqueue(PuzzleEvent("app_open", "app", null, "s", ts = 1, daysSinceInstall = 99999))
        assertEquals(0, c.size)
    }

    @Test fun stepAndDaysFieldsStrippedFromOtherEventTypes() {
        val c = client()
        c.enqueue(PuzzleEvent("start", "sudoku", null, "s", ts = 1, funnelStep = "first_open", daysSinceInstall = 3))
        c.flush()
        assertFalse(sent[0].contains("funnelStep")); assertFalse(sent[0].contains("daysSinceInstall"))
    }

    @Test fun disabledClientDropsFunnelEvents() {
        val c = client(enabled = false)
        c.enqueue(PuzzleEvent("funnel", "app", null, "s", ts = 1, funnelStep = "first_open"))
        assertEquals(0, c.size)
    }

    @Test fun queueStaysBoundedWhenOffline() {
        val c = PuzzleEventsClient("i", "1", send = { _, _ -> throw java.io.IOException() }, clock = { now })
        repeat(500) { c.enqueue(PuzzleEvent("app_open", "app", null, "s", ts = 1, daysSinceInstall = 1)) }
        assertEquals(PuzzleEventsClient.MAX_QUEUE, c.size)
    }
}

package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestRulesTest {
    @Test fun noUserIsGuest() { assertFalse(GuestRules.hasAccount(hasUser = false, isAnonymous = false)) }
    @Test fun anonymousUserIsGuest() { assertFalse(GuestRules.hasAccount(hasUser = true, isAnonymous = true)) }
    @Test fun realUserHasAccount() { assertTrue(GuestRules.hasAccount(hasUser = true, isAnonymous = false)) }
    // Magic-link / Telegram sessions have a Firebase user but no e-mail or name: they are accounts and must not be gated.
    @Test fun blankProfileRealUserStillHasAccount() { assertTrue(GuestRules.hasAccount(true, false)) }

    @Test fun freshDeviceGoesToFirstRun() {
        assertEquals(GuestRules.LaunchRoute.FIRST_RUN, GuestRules.launchRoute(hasUser = false, guestStarted = false))
    }
    @Test fun returningGuestGoesHome() {
        assertEquals(GuestRules.LaunchRoute.HOME, GuestRules.launchRoute(hasUser = false, guestStarted = true))
    }
    @Test fun existingSignedInUserGoesHomeUnchanged() {
        assertEquals(GuestRules.LaunchRoute.HOME, GuestRules.launchRoute(hasUser = true, guestStarted = false))
        assertEquals(GuestRules.LaunchRoute.HOME, GuestRules.launchRoute(hasUser = true, guestStarted = true))
    }

    @Test fun onboardingCardOnlyWhenNothingDoneOrDismissed() {
        assertTrue(GuestRules.shouldOfferOnboardingCard(false, false, false))
        assertFalse(GuestRules.shouldOfferOnboardingCard(true, false, false))
        assertFalse(GuestRules.shouldOfferOnboardingCard(false, true, false)) // existing users finished the old Home intro
        assertFalse(GuestRules.shouldOfferOnboardingCard(false, false, true))
    }
}

class SignupPromptPolicyTest {
    private fun play(s: SignupPromptState) = SignupPromptPolicy.afterCompletion(s)

    @Test fun neverBeforeFirstCompletion() {
        assertFalse(SignupPromptPolicy.shouldShow(true, SignupPromptState()))
    }

    @Test fun showsAfterFirstCompletion() {
        assertTrue(SignupPromptPolicy.shouldShow(true, play(SignupPromptState())))
    }

    @Test fun neverForSignedInUsers() {
        assertFalse(SignupPromptPolicy.shouldShow(false, play(SignupPromptState())))
    }

    @Test fun afterShownWaitsForFourthSession() {
        var s = SignupPromptPolicy.afterShown(play(SignupPromptState()))
        for (i in 2..3) { s = play(s); assertFalse("session $i", SignupPromptPolicy.shouldShow(true, s)) }
        s = play(s)
        assertTrue(SignupPromptPolicy.shouldShow(true, s))
    }

    @Test fun capsAtThreePrompts() {
        var s = SignupPromptState()
        var shown = 0
        repeat(60) {
            s = play(s)
            if (SignupPromptPolicy.shouldShow(true, s)) { shown++; s = SignupPromptPolicy.afterShown(s) }
        }
        assertEquals(3, shown)
    }

    @Test fun optOutStopsEverything() {
        val s = SignupPromptPolicy.afterOptOut(play(SignupPromptState()))
        assertFalse(SignupPromptPolicy.shouldShow(true, s))
        assertFalse(SignupPromptPolicy.shouldShow(true, play(play(play(play(s))))))
    }

    @Test fun skippedMilestoneStillShowsOnceNotBackToBack() {
        // App was killed before prompts 1 and 2 were recorded; 5 sessions done, 0 shown.
        var s = SignupPromptState(completedSessions = 5)
        assertTrue(SignupPromptPolicy.shouldShow(true, s))
        s = SignupPromptPolicy.afterShown(s)
        assertFalse("not again next session", SignupPromptPolicy.shouldShow(true, play(s)))
        assertTrue(SignupPromptPolicy.shouldShow(true, play(play(s))))
    }
}

class FirstRunPlanTest {
    @Test fun firstSessionIsOnePuzzle() { assertEquals(1, FirstRunPlan.TARGET_PUZZLE_COUNT) }

    @Test fun screenTypeIsKnownToThePuzzleScreen() {
        assertTrue(PuzzleScreenType.values().any { it.typeName == FirstRunPlan.SCREEN_TYPE })
    }
}

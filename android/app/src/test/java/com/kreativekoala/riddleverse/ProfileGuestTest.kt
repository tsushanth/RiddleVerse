package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class ProfileGuestTest {
    @Test fun noUserIsGuest() = assertTrue(isGuestProfile(false, false, null, null))
    @Test fun anonymousUserIsGuestEvenWithName() = assertTrue(isGuestProfile(true, true, "Sam", "s@x.com"))
    @Test fun userWithoutNameOrEmailIsGuest() {
        assertTrue(isGuestProfile(true, false, null, null))
        assertTrue(isGuestProfile(true, false, "", "  "))
    }
    @Test fun emailOnlyOrNameOnlyIsSignedIn() {
        assertFalse(isGuestProfile(true, false, null, "a@b.com"))
        assertFalse(isGuestProfile(true, false, "Sam", null))
        assertFalse(isGuestProfile(true, false, "Sam", "a@b.com"))
    }

    @Test fun realUserBouncesGuestAndForcedDoNot() {
        assertTrue(shouldBounceFromAuth(true, false, "Sam", "a@b.com", false))
        assertFalse(shouldBounceFromAuth(false, false, null, null, false))          // nobody signed in
        assertFalse(shouldBounceFromAuth(true, true, "Sam", "a@b.com", false))      // anonymous
        assertFalse(shouldBounceFromAuth(true, false, "", "", false))               // bridged user with no identity
        assertFalse(shouldBounceFromAuth(true, false, "Sam", "a@b.com", true))      // explicit request always opens
    }
    @Test fun bounceRuleAgreesWithProfileCardRule() {
        for (anon in listOf(false, true)) for (n in listOf(null, "", "Sam")) for (e in listOf(null, " ", "a@b.com")) {
            val guest = isGuestProfile(true, anon, n, e)
            assertEquals(!guest, shouldBounceFromAuth(true, anon, n, e, false))
        }
    }
}

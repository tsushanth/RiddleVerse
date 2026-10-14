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
}

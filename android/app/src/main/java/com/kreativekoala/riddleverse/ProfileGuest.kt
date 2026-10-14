package com.kreativekoala.riddleverse

import com.google.firebase.auth.FirebaseUser

/**
 * A "guest" has no usable identity to show: no user at all, an anonymous Firebase user, or a user with neither
 * a display name nor an e-mail (anonymous / magic-link sessions can look like this). Guests get a "Guest" label
 * and a sign-in button on the Profile card instead of an empty card.
 */
fun isGuestProfile(hasUser: Boolean, isAnonymous: Boolean, displayName: String?, email: String?): Boolean =
    !hasUser || isAnonymous || (displayName.isNullOrBlank() && email.isNullOrBlank())

fun isGuestProfile(user: FirebaseUser?): Boolean =
    isGuestProfile(user != null, user?.isAnonymous == true, user?.displayName, user?.email)

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

/**
 * AuthActivity opens, or sends the user straight back to Home because they are "already signed in".
 * Bounce only for a real (non-guest) user and never when the screen was explicitly requested ([forceShow]),
 * using the SAME guest rule as the Profile card so the two can never disagree.
 */
fun shouldBounceFromAuth(hasUser: Boolean, isAnonymous: Boolean, displayName: String?, email: String?, forceShow: Boolean): Boolean =
    !forceShow && hasUser && !isGuestProfile(hasUser, isAnonymous, displayName, email)

fun shouldBounceFromAuth(user: FirebaseUser?, forceShow: Boolean): Boolean =
    shouldBounceFromAuth(user != null, user?.isAnonymous == true, user?.displayName, user?.email, forceShow)

/** Intent extra: open the sign-in / sign-up screen no matter who is currently signed in. */
const val EXTRA_FORCE_SHOW_AUTH = "force_show_auth"

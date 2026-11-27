package com.kreativekoala.riddleverse

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/*
 * Guest-first play.
 *
 * A "guest" is a device with NO Firebase account (currentUser == null) or an anonymous Firebase user. The app already
 * had this concept: ProfileGuest.kt labels such a profile "Guest", UserStatsManager / DashboardDataProvider already
 * key local progress under "guest_user", and every backend call tolerates a blank user id. This file adds only:
 *   - the pure rules (who is a guest, where launch goes, when to nudge for an account),
 *   - a tiny SharedPreferences-backed state holder,
 *   - the soft-gate helpers features use to say "sign in to ...",
 *   - a no-op Funnel hook for the analytics work.
 *
 * Deliberately NOT done: Firebase anonymous auth. It needs the provider enabled in the console, creates a server
 * identity per install, and AuthActivity's email path (custom token) cannot link to it anyway.
 */

// ---------------------------------------------------------------------------------------------------------------
// Pure rules (unit tested, no Android types)
// ---------------------------------------------------------------------------------------------------------------

object GuestRules {
    /** A real account: a Firebase user that is not anonymous. Magic-link / Telegram users (no e-mail) count as accounts. */
    fun hasAccount(hasUser: Boolean, isAnonymous: Boolean): Boolean = hasUser && !isAnonymous

    fun hasAccount(user: FirebaseUser?): Boolean = hasAccount(user != null, user?.isAnonymous == true)

    fun isGuest(user: FirebaseUser?): Boolean = !hasAccount(user)

    enum class LaunchRoute { HOME, FIRST_RUN }

    /**
     * Cold start with no deep link. Anyone with a Firebase user (account OR anonymous, unchanged from before) goes
     * straight to Home. A device with no user goes to the first-run screen until the person has pressed Play once;
     * after that it is a returning guest and goes to Home.
     */
    fun launchRoute(hasUser: Boolean, guestStarted: Boolean): LaunchRoute =
        if (hasUser || guestStarted) LaunchRoute.HOME else LaunchRoute.FIRST_RUN

    /** The in-Home onboarding card: only while neither onboarding has been completed or dismissed. */
    fun shouldOfferOnboardingCard(appPrefsCompleted: Boolean, tutorialPrefsCompleted: Boolean, cardDismissed: Boolean): Boolean =
        !appPrefsCompleted && !tutorialPrefsCompleted && !cardDismissed
}

/** What we know about how often a guest has been nudged to make an account. */
data class SignupPromptState(
    val completedSessions: Int = 0,
    val promptsShown: Int = 0,
    val lastShownAtSession: Int = 0,
    val optedOut: Boolean = false
)

object SignupPromptPolicy {
    /** Nudge after the 1st finished puzzle session, then the 4th, then the 10th. Never more than three times. */
    val SCHEDULE: List<Int> = listOf(1, 4, 10)

    /** Never two nudges in back-to-back sessions, even if the app skipped a milestone. */
    const val MIN_SESSIONS_BETWEEN = 2

    fun shouldShow(isGuest: Boolean, s: SignupPromptState): Boolean {
        if (!isGuest || s.optedOut) return false
        if (s.promptsShown >= SCHEDULE.size) return false
        if (s.completedSessions < SCHEDULE[s.promptsShown]) return false
        if (s.promptsShown > 0 && s.completedSessions - s.lastShownAtSession < MIN_SESSIONS_BETWEEN) return false
        return true
    }

    fun afterCompletion(s: SignupPromptState): SignupPromptState = s.copy(completedSessions = s.completedSessions + 1)

    fun afterShown(s: SignupPromptState): SignupPromptState =
        s.copy(promptsShown = s.promptsShown + 1, lastShownAtSession = s.completedSessions)

    fun afterOptOut(s: SignupPromptState): SignupPromptState = s.copy(optedOut = true)
}

object FirstRunPlan {
    /** One puzzle, then the normal completion screen (reminder offer or account nudge). */
    const val TARGET_PUZZLE_COUNT = 1

    /** The screen of [StarterPuzzle]. */
    const val SCREEN_TYPE = "Grid Puzzle Screen"
}

// ---------------------------------------------------------------------------------------------------------------
// Funnel hook. The real [Funnel] lives in Funnel.kt; [Funnel.log] (FunnelStep, props) maps these onto its allow-list.
// ---------------------------------------------------------------------------------------------------------------

enum class FunnelStep(val key: String) {
    FIRST_RUN_SHOWN("first_run_shown"),
    PLAY_TAPPED("play_tapped"),
    SIGN_IN_LINK_TAPPED("sign_in_link_tapped"),
    FIRST_PUZZLE_LAUNCHED("first_puzzle_launched"),       // props: type
    FIRST_PUZZLE_COMPLETED("first_puzzle_completed"),
    SESSION_COMPLETED_AS_GUEST("session_completed_as_guest"),
    SIGNUP_PROMPT_SHOWN("signup_prompt_shown"),           // props: nth
    SIGNUP_PROMPT_ACCEPTED("signup_prompt_accepted"),
    SIGNUP_PROMPT_DISMISSED("signup_prompt_dismissed"),   // props: opted_out
    ONBOARDING_CARD_SHOWN("onboarding_card_shown"),
    ONBOARDING_OPENED("onboarding_opened"),
    ONBOARDING_SKIPPED("onboarding_skipped"),
    ONBOARDING_COMPLETED("onboarding_completed"),
    ONBOARDING_CARD_DISMISSED("onboarding_card_dismissed"),
    GUEST_GATE_SHOWN("guest_gate_shown"),                 // props: feature
    GUEST_GATE_SIGN_IN("guest_gate_sign_in"),             // props: feature
    GUEST_PROGRESS_MIGRATED("guest_progress_migrated");   // props: puzzle_types, daily_keys

    /**
     * The backend funnel allow-list ([PuzzleEventsClient.FUNNEL_STEPS]) is closed and has no guest-specific steps, so
     * only the steps that already exist there are forwarded; the rest are local-only (debug log) until the
     * migration grows the list.
     */
    val funnelStep: String?
        get() = when (this) {
            FIRST_RUN_SHOWN -> "welcome_shown"          // the first-run screen replaces the old welcome screen
            PLAY_TAPPED -> "guest_started"
            FIRST_PUZZLE_LAUNCHED -> "first_puzzle_started"
            FIRST_PUZZLE_COMPLETED -> "first_puzzle_completed"
            ONBOARDING_COMPLETED -> "onboarding_done"
            else -> null
        }
}

// ---------------------------------------------------------------------------------------------------------------
// Persisted state
// ---------------------------------------------------------------------------------------------------------------

object GuestPrefs {
    private const val FILE = "guest_state"
    private const val K_STARTED = "guest_started"
    private const val K_SESSIONS = "completed_sessions"
    private const val K_PROMPTS = "prompts_shown"
    private const val K_LAST_SHOWN = "last_shown_at_session"
    private const val K_OPTED_OUT = "prompt_opted_out"
    private const val K_ONBOARDING_CARD_DISMISSED = "onboarding_card_dismissed"

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isStarted(ctx: Context): Boolean = p(ctx).getBoolean(K_STARTED, false)

    fun markStarted(ctx: Context) { p(ctx).edit().putBoolean(K_STARTED, true).apply() }

    /** Sign-in and sign-out both end the guest era; the prompt counters restart for a later guest session. */
    fun clear(ctx: Context) { p(ctx).edit().clear().apply() }

    fun promptState(ctx: Context): SignupPromptState = p(ctx).let {
        SignupPromptState(
            completedSessions = it.getInt(K_SESSIONS, 0),
            promptsShown = it.getInt(K_PROMPTS, 0),
            lastShownAtSession = it.getInt(K_LAST_SHOWN, 0),
            optedOut = it.getBoolean(K_OPTED_OUT, false)
        )
    }

    private fun save(ctx: Context, s: SignupPromptState) {
        p(ctx).edit()
            .putInt(K_SESSIONS, s.completedSessions).putInt(K_PROMPTS, s.promptsShown)
            .putInt(K_LAST_SHOWN, s.lastShownAtSession).putBoolean(K_OPTED_OUT, s.optedOut)
            .apply()
    }

    /** A guest finished a puzzle session. Returns the new state. */
    fun recordSessionCompleted(ctx: Context): SignupPromptState =
        SignupPromptPolicy.afterCompletion(promptState(ctx)).also { save(ctx, it) }

    fun recordPromptShown(ctx: Context): SignupPromptState =
        SignupPromptPolicy.afterShown(promptState(ctx)).also { save(ctx, it) }

    fun recordPromptOptOut(ctx: Context): SignupPromptState =
        SignupPromptPolicy.afterOptOut(promptState(ctx)).also { save(ctx, it) }

    fun isOnboardingCardDismissed(ctx: Context): Boolean = p(ctx).getBoolean(K_ONBOARDING_CARD_DISMISSED, false)

    fun dismissOnboardingCard(ctx: Context) { p(ctx).edit().putBoolean(K_ONBOARDING_CARD_DISMISSED, true).apply() }
}

// ---------------------------------------------------------------------------------------------------------------
// Soft gates
// ---------------------------------------------------------------------------------------------------------------

/** What a guest was trying to do when the soft gate appeared. [analyticsKey] is the funnel value; the dialog title is localized. */
enum class GuestFeature(val analyticsKey: String, @StringRes val titleRes: Int) {
    CREATE_PUZZLES("create puzzles", R.string.onb_gate_title_create_puzzles),
    PLAY_DAILY_QUIZZES("play daily quizzes", R.string.onb_gate_title_play_daily_quizzes),
    BUY_COINS("buy coins", R.string.onb_gate_title_buy_coins),
    SUBSCRIBE("subscribe", R.string.onb_gate_title_subscribe)
}

object GuestGate {
    /**
     * Sentinel stored in GameGenerationViewModel.errorMessage and compared against (needsSignIn). Keep the value; to
     * show it to the user use [SIGN_IN_TO_CREATE_RES] (localized, same English text).
     */
    const val SIGN_IN_TO_CREATE = "Sign in to create games. It is free and takes under a minute."

    @StringRes const val SIGN_IN_TO_CREATE_RES = R.string.onb_gate_sign_in_to_create

    fun isGuest(): Boolean = GuestRules.isGuest(FirebaseAuth.getInstance().currentUser)

    /** Opens the sign-in / create-account screen even if something else looks signed in. */
    fun signInIntent(ctx: Context): Intent =
        Intent(ctx, AuthActivity::class.java).putExtra(EXTRA_FORCE_SHOW_AUTH, true)

    /**
     * Returns true when the caller may go ahead. For a guest it shows a short "sign in to <feature>" dialog and
     * returns false.
     */
    fun requireAccount(activity: Activity, feature: GuestFeature): Boolean {
        if (!isGuest()) return true
        Funnel.log(FunnelStep.GUEST_GATE_SHOWN, mapOf("feature" to feature.analyticsKey))
        if (activity.isFinishing || activity.isDestroyed) return false
        AlertDialog.Builder(activity)
            .setTitle(feature.titleRes)
            .setMessage(R.string.onb_gate_message)
            .setPositiveButton(R.string.common_sign_in_or_create) { _, _ ->
                Funnel.log(FunnelStep.GUEST_GATE_SIGN_IN, mapOf("feature" to feature.analyticsKey))
                activity.startActivity(signInIntent(activity))
            }
            .setNegativeButton(R.string.guest_save_not_now, null)
            .show()
        return false
    }
}

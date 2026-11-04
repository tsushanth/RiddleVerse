package com.kreativekoala.riddleverse

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.util.TimeZone

/** Minimal key/value store so [FunnelState] is JVM-testable (SharedPreferences in production). */
interface FunnelStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/**
 * Pure once-per-install / once-per-local-day bookkeeping for [Funnel]. No Android types.
 *
 * - [shouldLog]: each funnel step is reported at most once per install (first occurrence wins).
 * - [appOpenDays]: returns whole local days since install the first time it is called on a given
 *   local day, else null (so at most one `app_open` per local day).
 * - Installs whose APK was first installed more than [LEGACY_AFTER_MS] before this state was created
 *   (i.e. users who updated into this version) never report the one-time steps, so they do not
 *   pollute the new-install funnel; they still report `app_open` with the true days since install.
 */
class FunnelState(
    private val store: FunnelStore,
    private val installTimeMs: Long,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val offsetMs: (Long) -> Long = { TimeZone.getDefault().getOffset(it).toLong() }
) {
    companion object {
        const val LEGACY_AFTER_MS = 2 * 24 * 3600 * 1000L
        private const val DAY_MS = 24 * 3600 * 1000L
        private const val K_SEEN = "funnel_seen"
        private const val K_LAST_OPEN_DAY = "funnel_last_open_day"
    }

    private fun localDay(ms: Long): Long = Math.floorDiv(ms + offsetMs(ms), DAY_MS)

    @Synchronized
    private fun seen(): MutableSet<String> {
        val raw = store.get(K_SEEN)
        if (raw == null) {
            // First run of the funnel on this install: decide new vs legacy once.
            val legacy = clock() - installTimeMs > LEGACY_AFTER_MS
            val init = if (legacy) PuzzleEventsClient.FUNNEL_STEPS.toMutableSet() else mutableSetOf()
            store.put(K_SEEN, init.joinToString(","))
            return init
        }
        return raw.split(',').filter { it.isNotEmpty() }.toMutableSet()
    }

    /** True exactly once per install for an allow-listed [step]. */
    @Synchronized
    fun shouldLog(step: String): Boolean {
        if (step !in PuzzleEventsClient.FUNNEL_STEPS) return false
        val s = seen()
        if (!s.add(step)) return false
        store.put(K_SEEN, s.joinToString(","))
        return true
    }

    /** Days since install if no `app_open` was reported yet for today's local date, else null. */
    @Synchronized
    fun appOpenDays(): Int? {
        val now = clock()
        val today = localDay(now)
        if (store.get(K_LAST_OPEN_DAY)?.toLongOrNull() == today) return null
        store.put(K_LAST_OPEN_DAY, today.toString())
        return (today - localDay(installTimeMs)).coerceIn(0, PuzzleEventsClient.MAX_DAYS_SINCE_INSTALL.toLong()).toInt()
    }
}

/**
 * Anonymous install funnel + day-N return events. Rides on [PuzzleEvents] (same queue, endpoint, anonymous
 * install id, backoff, bounded 200-event in-memory queue). No emails, no device ids, no free text: only the
 * allow-listed step name or days_since_install is sent. Never throws, never blocks the UI.
 *
 * Debug builds only log (and queue when the "debug_emit" pref in [PREFS] is true) so test installs do not pollute retention. Also off when [PuzzleEvents.setEnabled] is false.
 * Note: the repo has no "qa" build type ("qa" is a puzzle type), so DEBUG is the exclusion.
 */
object Funnel {
    private const val PREFS = "funnel_state"
    @Volatile private var state: FunnelState? = null
    private var startedActivities = 0
    @Volatile private var debugEmit = false

    fun init(app: Application) {
        try {
            if (state != null) return
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Debug builds track steps (logcat tag "Funnel") but only queue them when this pref is set, so test
            // installs do not pollute the production funnel.
            debugEmit = BuildConfig.DEBUG && prefs.getBoolean("debug_emit", false)
            val installTime = try {
                app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
            } catch (_: Throwable) { System.currentTimeMillis() }
            state = FunnelState(object : FunnelStore {
                override fun get(key: String) = prefs.getString(key, null)
                override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
            }, installTime)
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?) {}
                override fun onActivityStarted(a: Activity) {
                    // First foreground activity of a process = one candidate app open (deduped per local day).
                    val first = synchronized(Funnel) { startedActivities++ == 0 }
                    if (first) { log("first_open"); appOpen() }
                }
                override fun onActivityResumed(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) {
                    synchronized(Funnel) { startedActivities = (startedActivities - 1).coerceAtLeast(0) }
                }
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
                override fun onActivityDestroyed(a: Activity) {}
            })
        } catch (_: Throwable) { }
    }

    /** Guest-flow steps ([FunnelStep]) mapped onto the allow-list; steps with no allow-listed equivalent stay local. */
    @Suppress("UNUSED_PARAMETER")
    fun log(step: FunnelStep, props: Map<String, Any?> = emptyMap()) {
        val mapped = step.funnelStep
        if (mapped != null) log(mapped)
        else if (BuildConfig.DEBUG) android.util.Log.d("Funnel", "local-only step ${step.key} $props")
    }

    /** Report an install-funnel step (see [PuzzleEventsClient.FUNNEL_STEPS]). Only the first call per install sends. */
    fun log(step: String) {
        try {
            val st = state ?: return
            if (!st.shouldLog(step)) return
            if (BuildConfig.DEBUG) android.util.Log.d("Funnel", "step=$step emit=$debugEmit")
            if (BuildConfig.DEBUG && !debugEmit) return
            PuzzleEvents.emitFunnel(
                PuzzleEvent("funnel", PuzzleEventsClient.APP_PUZZLE_TYPE, null, PuzzleEventsClient.newId(),
                    ts = System.currentTimeMillis(), funnelStep = step)
            )
        } catch (_: Throwable) { }
    }

    private fun appOpen() {
        try {
            val days = state?.appOpenDays() ?: return
            if (BuildConfig.DEBUG) android.util.Log.d("Funnel", "app_open days=$days emit=$debugEmit")
            if (BuildConfig.DEBUG && !debugEmit) return
            PuzzleEvents.emitFunnel(
                PuzzleEvent("app_open", PuzzleEventsClient.APP_PUZZLE_TYPE, null, PuzzleEventsClient.newId(),
                    ts = System.currentTimeMillis(), daysSinceInstall = days)
            )
        } catch (_: Throwable) { }
    }
}

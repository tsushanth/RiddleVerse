package com.kreativekoala.riddleverse

import android.app.Application
import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Runs [block] at most once, whoever gets there first (the startup thread or a caller that needs it right now).
 *
 * - [ensure] never throws and never runs [block] twice. If another thread is inside [block] the caller waits for it,
 *   so after [ensure] returns the SDK is initialised (or its init failed and was logged; [succeeded] tells which).
 * - A block that throws is not retried: the callers of the SDK already tolerate "not initialised" the same way they did
 *   when `Application.onCreate` crashed or logged the failure once.
 *
 * Pure Kotlin so the contract is JVM-testable (see AppStartupTest).
 */
class OnceInit(private val name: String, private val block: () -> Unit) {
    private val lock = Any()
    @Volatile private var finished = false
    @Volatile private var ok = false

    /** True once [block] has run to completion (successfully or not). */
    val isFinished: Boolean get() = finished

    /** True only if [block] completed without throwing. */
    val succeeded: Boolean get() = finished && ok

    fun ensure(): Boolean {
        if (finished) return ok
        synchronized(lock) {
            if (finished) return ok
            try {
                block()
                ok = true
            } catch (t: Throwable) {
                Log.e("AppStartup", "$name init failed", t)
            } finally {
                finished = true
            }
            return ok
        }
    }
}

/**
 * Cold-start work that used to run on the main thread inside `MyApplication.onCreate`
 * (Play vitals: "slow app startup" ANR clusters on 182-184, mostly low-end phones with slow storage).
 *
 * One daemon thread ([executor]) runs, in order: the SDK/library inits and the SharedPreferences warm-ups. Every consumer
 * that needs one of those SDKs goes through [ensureExperimentManager] / [ensureRatingKit] / [ensureTikTok] (or posts to
 * [executor] behind them), so nothing can see an uninitialised SDK:
 *  - automatic/startup work (RatingKit.trackAppOpen, TikTok events) is queued on [executor], FIFO behind the inits;
 *  - user-initiated work (paywall shown, rating moment, purchase) calls `ensure*`, which runs the init inline if the
 *    background thread has not reached it yet, so first use can never crash or be lost.
 *
 * Firebase itself is NOT moved: FirebaseInitProvider already initialises the default FirebaseApp before
 * `Application.onCreate`, and the explicit `FirebaseApp.initializeApp` call that stays on main is a no-op after that.
 */
object AppStartup {
    private const val TAG = "AppStartup"

    /** Serial, FIFO. Tasks posted here run after everything [start] queued. */
    val executor: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "rv-startup").apply { isDaemon = true } }

    @Volatile private var app: Application? = null

    @Volatile private var experimentManager = OnceInit("ExperimentManager") {}
    @Volatile private var ratingKit = OnceInit("RatingKit") {}
    @Volatile private var tikTok = OnceInit("TikTok") {}

    /** Call once from Application.onCreate (main thread). Only queues work: returns immediately. */
    @Synchronized
    fun start(application: Application) {
        if (app != null) return
        app = application
        experimentManager = OnceInit("ExperimentManager") {
            com.kreativekoala.paywallkit.manager.ExperimentManager.init(application)
        }
        ratingKit = OnceInit("RatingKit") {
            com.kreativekoala.ratingkit.RatingKit.init(application, appId = "riddleverse")
        }
        tikTok = OnceInit("TikTok") { TikTokHelper.initialize(application) }

        executor.execute {
            // The first screen reads this on the main thread (MainActivity.navigateToDefaultScreen).
            safely("prefs guest_state") { application.getSharedPreferences("guest_state", 0).all }
            // Main will touch FirebaseAuth in the first screen (signed-in check): build it here, in parallel with the
            // activity start, instead of on the main thread.
            safely("FirebaseAuth warm-up") { com.google.firebase.auth.FirebaseAuth.getInstance() }
            ratingKit.ensure()
            experimentManager.ensure()
            // Home's SharedPreferences files and the puzzle queue (a JSON parse in its constructor): load them here so the
            // main-thread reads during the first Home composition find them in memory (StrictMode showed 170-190 ms each).
            for (name in WARM_PREFS) safely("prefs $name") { application.getSharedPreferences(name, 0).all }
            safely("PuzzleQueueManager") { PuzzleQueueManager.getInstance(application) }
            tikTok.ensure()
        }
    }

    /** SharedPreferences that the first screens read on the main thread (found with StrictMode in debug builds). */
    private val WARM_PREFS = listOf(
        "retention_v1", "daily_reminder_v1", "funnel_state", "puzzle_events",
        "app_prefs", "tutorial_prefs", "web_promo_banner", "puzzle_group_completion", "recent_puzzles",
        "daily_streak_data", "daily_puzzle_v1",
    )

    fun ensureExperimentManager(): Boolean = experimentManager.ensure()
    fun ensureRatingKit(): Boolean = ratingKit.ensure()
    fun ensureTikTok(): Boolean = tikTok.ensure()

    /** Run [block] on the startup thread, after the inits. Never throws into the caller or the executor. */
    fun post(block: () -> Unit) {
        try {
            executor.execute { safely("posted task", block) }
        } catch (t: Throwable) {
            Log.w(TAG, "post rejected: ${t.message}")
        }
    }

    private inline fun safely(what: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) { Log.w(TAG, "$what failed: ${t.message}") }
    }
}

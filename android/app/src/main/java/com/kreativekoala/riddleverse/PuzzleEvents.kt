package com.kreativekoala.riddleverse

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Android wiring for [PuzzleEventsClient]: schedules flushes (15 s after the first queued event,
 * immediately at 20 events, and when the app goes to background). Every entry point is
 * fire-and-forget and swallows all errors. Nothing is persisted except the random install id.
 *
 * Privacy: the app has no analytics opt-out setting today. [setEnabled] (SharedPreferences
 * "puzzle_events"/"enabled", default true) is the switch to wire to one if it is ever added.
 */
object PuzzleEvents {
    private const val ENDPOINT = "https://puzzleverseai.com/api/puzzle-events"
    private const val PREFS = "puzzle_events"
    private const val FLUSH_INTERVAL_MS = 15_000L

    private val io = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "puzzle-events").apply { isDaemon = true } }
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS).build()
    }
    private val tracker = PuzzleSessionTracker()
    @Volatile private var client: PuzzleEventsClient? = null
    @Volatile private var appCtx: Context? = null
    @Volatile private var enabledFlag = true
    private var pending: ScheduledFuture<*>? = null
    private var startedActivities = 0

    /**
     * Main thread: only stores the context and registers the lifecycle hook (no I/O). The SharedPreferences read/write and the
     * client construction run on [io] (a cold-start ANR sample was blocked here); everything that uses [client] is also
     * posted to [io], so it always runs after this and no early event is lost.
     */
    fun init(app: Application) {
        try {
            if (appCtx != null) return
            appCtx = app.applicationContext
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?) {}
                override fun onActivityStarted(a: Activity) { synchronized(this@PuzzleEvents) { startedActivities++ } }
                override fun onActivityResumed(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) {
                    val none = synchronized(this@PuzzleEvents) { startedActivities = (startedActivities - 1).coerceAtLeast(0); startedActivities == 0 }
                    if (none) { if (client != null) flushSoon(0) else try { io.execute { flushSoon(0) } } catch (_: Throwable) { } }
                }
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
                override fun onActivityDestroyed(a: Activity) {}
            })
            io.execute { try { initClient(app) } catch (_: Throwable) { } }
        } catch (_: Throwable) { }
    }

    /** Runs on [io]. */
    private fun initClient(app: Application) {
        if (client != null) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabledFlag = prefs.getBoolean("enabled", true)
        var id = prefs.getString("install_id", null)
        if (id.isNullOrEmpty()) { id = UUID.randomUUID().toString(); prefs.edit().putString("install_id", id).apply() }
        client = PuzzleEventsClient(
            installId = id, appVersion = BuildConfig.VERSION_NAME,
            send = { body, token -> post(body, token) },
            tokenProvider = { null }, // events are anonymous: no account id is attached (privacy)
            enabled = { enabledFlag }
        )
    }

    fun setEnabled(enabled: Boolean) {
        enabledFlag = enabled
        try { appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("enabled", enabled)?.apply() } catch (_: Throwable) { }
    }

    /** Puzzle screen composed: emits `start` once per puzzle id. */
    fun puzzleShown(puzzle: Puzzle?, fallbackType: String?) = guarded {
        val (key, type, diff) = describe(puzzle, fallbackType) ?: return@guarded
        tracker.start(key, type, diff)?.let { emit(it) }
        Funnel.log("first_puzzle_started")
    }

    /**
     * `complete` or `fail`. Reported when a word game shows its result, and again through PuzzleCompletionHandler on
     * Continue (which is the only report for the other games); the second report of one result is dropped.
     */
    fun puzzleFinished(puzzle: Puzzle?, fallbackType: String?, success: Boolean, score: Int) = guarded {
        val (key, type, diff) = describe(puzzle, fallbackType) ?: return@guarded
        val event = tracker.finish(key, type, diff, success, score) ?: return@guarded
        emit(event)
        if (success) Funnel.log("first_puzzle_completed")
    }

    /** Puzzle screen left; emits `abandon` if it was not completed. Skipped on rotation. */
    fun puzzleLeft(puzzle: Puzzle?, fallbackType: String?, configChange: Boolean) = guarded {
        if (configChange) return@guarded
        val (key, _, _) = describe(puzzle, fallbackType) ?: return@guarded
        tracker.abandon(key)?.let { emit(it) }
    }

    /** A share sheet was opened for [puzzleType] (or a custom game, passed as "custom"). [channel] = telegram, whatsapp or other. */
    fun shareSent(puzzleType: String?, channel: String) = guarded {
        emit(PuzzleEvent("share_sent", puzzleType ?: "custom", null, PuzzleEventsClient.newId(), ts = System.currentTimeMillis(), channel = channel))
    }

    /** The app was opened from a link: [target] = a puzzle type, or "app"; [channel] = the link's src (share, telegram, auth, ...). */
    fun deepLinkOpen(target: String?, channel: String) = guarded {
        emit(PuzzleEvent("deeplink_open", target ?: "app", null, PuzzleEventsClient.newId(), ts = System.currentTimeMillis(), channel = channel))
    }

    /** A Telegram promo (banner, result card, daily card, settings row) was tapped. [surface] is the label of the place. */
    fun telegramCta(surface: String) = guarded {
        emit(PuzzleEvent("telegram_cta", "app", null, PuzzleEventsClient.newId(), ts = System.currentTimeMillis(), channel = surface))
    }

    /** Handled exception around puzzle generation. Pass only a short message, never user content. */
    fun error(puzzleType: String?, difficulty: String?, message: String?) = guarded {
        val sid = PuzzleEventsClient.newId()
        emit(PuzzleEvent("error", puzzleType ?: return@guarded, difficulty, sid, errorMessage = message, ts = System.currentTimeMillis()))
    }

    private fun describe(p: Puzzle?, fallback: String?): Triple<String, String, String?>? {
        val type = PuzzleEventsClient.sanitizePuzzleType(p?.puzzleType ?: fallback) ?: return null
        return Triple(p?.puzzleId ?: type, type, PuzzleEventsClient.sanitizeDifficulty(p?.difficulty))
    }

    /** Used by [Funnel]: same queue, endpoint and install id as puzzle events. */
    internal fun emitFunnel(e: PuzzleEvent) = guarded { emit(e) }

    /** Queued on [io], behind [initClient], so an event raised during startup is kept instead of dropped. */
    private fun emit(e: PuzzleEvent) {
        try {
            io.execute {
                try {
                    val c = client ?: return@execute
                    val full = c.enqueue(e)
                    flushSoon(if (full) 0 else FLUSH_INTERVAL_MS)
                } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
    }

    @Synchronized
    private fun flushSoon(delayMs: Long) {
        val c = client ?: return
        val p = pending
        if (delayMs > 0 && p != null && !p.isDone) return   // a flush is already scheduled
        p?.cancel(false)
        pending = try {
            io.schedule({ try { c.flush() } catch (_: Throwable) { }; reschedule() }, delayMs, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) { null }
    }

    private fun reschedule() {
        val c = client ?: return
        if (c.size > 0) flushSoon(maxOf(FLUSH_INTERVAL_MS, 1_000L))
    }

    private fun post(body: String, token: String?): Int {
        val rb = Request.Builder().url(ENDPOINT).post(body.toRequestBody("application/json".toMediaType()))
        if (!token.isNullOrEmpty()) rb.header("Authorization", "Bearer $token")
        http.newCall(rb.build()).execute().use {
            if (BuildConfig.DEBUG) android.util.Log.d("PuzzleEvents", "POST ${it.code} body=${body.take(600)}")
            return it.code
        }
    }

    private inline fun guarded(block: () -> Unit) { try { block() } catch (_: Throwable) { } }
}

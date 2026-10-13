package com.kreativekoala.riddleverse

import java.util.UUID

/** One puzzle telemetry event. All free text is sanitized in [PuzzleEventsClient.enqueue]. */
data class PuzzleEvent(
    val type: String,
    val puzzleType: String,
    val difficulty: String? = null,
    val sessionId: String,
    val durationMs: Long? = null,
    val score: Int? = null,
    val moves: Int? = null,
    val hintsUsed: Int? = null,
    val mistakes: Int? = null,
    val errorMessage: String? = null,
    val ts: Long
)

/**
 * Pure, JVM-testable batching client for POST /api/puzzle-events.
 * The HTTP call, token lookup and clock are injected. Holds events in memory only.
 * Never throws out of its public methods.
 */
class PuzzleEventsClient(
    private val installId: String,
    private val appVersion: String,
    /** POST [body] (with optional bearer token); return the HTTP status. May throw on network errors. */
    private val send: (body: String, token: String?) -> Int,
    private val tokenProvider: () -> String? = { null },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val enabled: () -> Boolean = { true }
) {
    companion object {
        const val MAX_QUEUE = 200
        const val MAX_BATCH = 50
        const val FLUSH_AT = 20
        const val BASE_BACKOFF_MS = 15_000L
        const val MAX_BACKOFF_MS = 15 * 60_000L
        const val MAX_ERROR_CHARS = 300
        private val TYPES = setOf("start", "complete", "fail", "abandon", "hint", "undo", "error")
        private val PUZZLE_TYPE = Regex("^[a-z0-9_]{1,40}$")
        private val DIFFICULTIES = setOf("easy", "medium", "hard")
        private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        private val URL = Regex("https?://\\S+")

        fun sanitizePuzzleType(raw: String?): String? {
            val t = raw?.trim()?.lowercase() ?: return null
            return if (PUZZLE_TYPE.matches(t)) t else null
        }

        fun sanitizeDifficulty(raw: String?): String? = raw?.trim()?.lowercase()?.takeIf { it in DIFFICULTIES }

        fun sanitizeError(raw: String?): String? {
            if (raw == null) return null
            val s = raw.replace(EMAIL, "<email>").replace(URL, "<url>")
                .map { if (it.isISOControl()) ' ' else it }.joinToString("")
                .trim().take(MAX_ERROR_CHARS)
            return s.ifEmpty { null }
        }

        fun backoffMs(failures: Int): Long {
            if (failures <= 0) return 0
            val shift = (failures - 1).coerceAtMost(20)
            return (BASE_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
        }

        fun newId(): String = UUID.randomUUID().toString()

        private fun esc(s: String): String {
            val sb = StringBuilder(s.length + 2)
            for (c in s) when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
            return sb.toString()
        }
    }

    private val lock = Any()
    private val queue = ArrayDeque<PuzzleEvent>()
    private var failures = 0
    private var nextAttemptAt = 0L

    val size: Int get() = synchronized(lock) { queue.size }
    val consecutiveFailures: Int get() = synchronized(lock) { failures }

    /** Returns true when the queue has reached [FLUSH_AT] and the caller should flush now. */
    fun enqueue(event: PuzzleEvent): Boolean = try {
        if (!enabled()) false else {
            val type = event.type
            val pt = sanitizePuzzleType(event.puzzleType)
            if (type !in TYPES || pt == null) false else {
                val clean = event.copy(
                    puzzleType = pt,
                    difficulty = sanitizeDifficulty(event.difficulty),
                    durationMs = event.durationMs?.coerceAtLeast(0),
                    score = event.score?.coerceAtLeast(0),
                    moves = event.moves?.coerceAtLeast(0),
                    hintsUsed = event.hintsUsed?.coerceAtLeast(0),
                    mistakes = event.mistakes?.coerceAtLeast(0),
                    errorMessage = if (type == "error") sanitizeError(event.errorMessage) else null
                )
                synchronized(lock) {
                    queue.addLast(clean)
                    while (queue.size > MAX_QUEUE) queue.removeFirst()
                    queue.size >= FLUSH_AT
                }
            }
        }
    } catch (_: Throwable) { false }

    fun buildBody(events: List<PuzzleEvent>): String {
        val sb = StringBuilder()
        sb.append("{\"installId\":\"").append(esc(installId)).append("\",\"platform\":\"android\",\"appVersion\":\"")
            .append(esc(appVersion)).append("\",\"events\":[")
        events.forEachIndexed { i, e ->
            if (i > 0) sb.append(',')
            sb.append("{\"type\":\"").append(esc(e.type)).append("\",\"puzzleType\":\"").append(esc(e.puzzleType)).append('"')
            e.difficulty?.let { sb.append(",\"difficulty\":\"").append(esc(it)).append('"') }
            sb.append(",\"sessionId\":\"").append(esc(e.sessionId)).append('"')
            e.durationMs?.let { sb.append(",\"durationMs\":").append(it) }
            e.score?.let { sb.append(",\"score\":").append(it) }
            e.moves?.let { sb.append(",\"moves\":").append(it) }
            e.hintsUsed?.let { sb.append(",\"hintsUsed\":").append(it) }
            e.mistakes?.let { sb.append(",\"mistakes\":").append(it) }
            e.errorMessage?.let { sb.append(",\"errorMessage\":\"").append(esc(it)).append('"') }
            sb.append(",\"ts\":").append(e.ts).append('}')
        }
        return sb.append("]}").toString()
    }

    /**
     * Sends queued events in batches of up to [MAX_BATCH]. Call from a background thread.
     * On failure the batch is put back (bounded) and exponential backoff applies; no inline retries.
     * Returns the number of events accepted by the server.
     */
    fun flush(force: Boolean = false): Int {
        var accepted = 0
        try {
            if (!enabled()) { synchronized(lock) { queue.clear() }; return 0 }
            while (true) {
                val batch: List<PuzzleEvent> = synchronized(lock) {
                    if (queue.isEmpty() || (!force && clock() < nextAttemptAt)) emptyList()
                    else List(minOf(MAX_BATCH, queue.size)) { queue.removeFirst() }
                }
                if (batch.isEmpty()) break
                val status = try {
                    val token = try { tokenProvider() } catch (_: Throwable) { null }
                    send(buildBody(batch), token)
                } catch (_: Throwable) { -1 }
                synchronized(lock) {
                    when {
                        status in 200..299 -> { failures = 0; nextAttemptAt = 0; accepted += batch.size }
                        // 4xx other than 429/404/408: the payload itself is rejected; drop it so we never loop.
                        status in 400..499 && status != 429 && status != 404 && status != 408 -> { }
                        else -> {
                            failures++
                            nextAttemptAt = clock() + backoffMs(failures)
                            batch.asReversed().forEach { queue.addFirst(it) }
                            while (queue.size > MAX_QUEUE) queue.removeFirst()
                        }
                    }
                }
                if (status !in 200..299) break
            }
        } catch (_: Throwable) { }
        return accepted
    }
}

/** Tracks per-puzzle sessions so start/complete/abandon pair up and abandon fires at most once. */
class PuzzleSessionTracker(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private class Session(val id: String, val startedAt: Long, val type: String, val difficulty: String?)
    private val active = HashMap<String, Session>()

    @Synchronized
    fun start(key: String, puzzleType: String, difficulty: String?): PuzzleEvent? {
        if (active.containsKey(key)) return null
        if (active.size > 50) active.clear()
        val s = Session(PuzzleEventsClient.newId(), clock(), puzzleType, difficulty)
        active[key] = s
        return PuzzleEvent("start", puzzleType, difficulty, s.id, ts = s.startedAt)
    }

    @Synchronized
    fun finish(key: String, puzzleType: String, difficulty: String?, success: Boolean, score: Int?): PuzzleEvent {
        val s = active.remove(key)
        val now = clock()
        return PuzzleEvent(
            if (success) "complete" else "fail", puzzleType, difficulty,
            s?.id ?: PuzzleEventsClient.newId(),
            durationMs = s?.let { now - it.startedAt }, score = score, ts = now
        )
    }

    /** Returns an abandon event if the puzzle is still active, else null. */
    @Synchronized
    fun abandon(key: String): PuzzleEvent? {
        val s = active.remove(key) ?: return null
        val now = clock()
        return PuzzleEvent("abandon", s.type, s.difficulty, s.id, durationMs = now - s.startedAt, ts = now)
    }

    @Synchronized
    fun sessionIdOf(key: String): String? = active[key]?.id
}

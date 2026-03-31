// DebugLogger.kt - Production-safe logging utility
package com.kreativekoala.riddleverse.util

import android.util.Log

/**
 * Production-safe logging utility that only logs in debug builds.
 * Replaces scattered Log.d() calls throughout the codebase.
 *
 * Usage:
 * ```
 * DebugLogger.d("MyTag", "Debug message")
 * DebugLogger.e("MyTag", "Error message", exception)
 * ```
 */
object DebugLogger {

    // Use reflection to get BuildConfig.DEBUG to avoid direct dependency
    private val isDebug: Boolean by lazy {
        try {
            val buildConfigClass = Class.forName("com.kreativekoala.riddleverse.BuildConfig")
            val debugField = buildConfigClass.getField("DEBUG")
            debugField.getBoolean(null)
        } catch (e: Exception) {
            true // Default to true if we can't determine
        }
    }

    /**
     * Log a debug message (only in debug builds)
     */
    fun d(tag: String, message: String) {
        if (isDebug) {
            Log.d(tag, message)
        }
    }

    /**
     * Log an info message (only in debug builds)
     */
    fun i(tag: String, message: String) {
        if (isDebug) {
            Log.i(tag, message)
        }
    }

    /**
     * Log a warning message (always logs in production for important warnings)
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    /**
     * Log an error message (always logs in production)
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    /**
     * Log a verbose message (only in debug builds)
     */
    fun v(tag: String, message: String) {
        if (isDebug) {
            Log.v(tag, message)
        }
    }

    /**
     * Log performance metrics (only in debug builds)
     */
    fun performance(tag: String, operation: String, durationMs: Long) {
        if (isDebug) {
            Log.d(tag, "PERF: $operation took ${durationMs}ms")
        }
    }

    /**
     * Log puzzle-specific events (only in debug builds)
     */
    fun puzzle(tag: String, puzzleType: String, event: String, details: Map<String, Any> = emptyMap()) {
        if (isDebug) {
            val detailStr = if (details.isNotEmpty()) {
                details.entries.joinToString(", ") { "${it.key}=${it.value}" }
            } else ""
            Log.d(tag, "PUZZLE[$puzzleType]: $event ${if (detailStr.isNotEmpty()) "($detailStr)" else ""}")
        }
    }

    /**
     * Log analytics events (only in debug builds)
     */
    fun analytics(tag: String, eventName: String, params: Map<String, Any>? = null) {
        if (isDebug) {
            val paramStr = params?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: ""
            Log.d(tag, "ANALYTICS: $eventName ${if (paramStr.isNotEmpty()) "[$paramStr]" else ""}")
        }
    }

    /**
     * Log adaptive difficulty changes (only in debug builds)
     */
    fun adaptive(tag: String, puzzleType: String, fromLevel: String, toLevel: String, reason: String) {
        if (isDebug) {
            Log.d(tag, "ADAPTIVE[$puzzleType]: $fromLevel -> $toLevel ($reason)")
        }
    }

    /**
     * Log score calculations (only in debug builds)
     */
    fun score(tag: String, puzzleType: String, baseScore: Int, finalScore: Int, bonuses: Map<String, Int>) {
        if (isDebug) {
            val bonusStr = bonuses.entries.joinToString(", ") { "${it.key}=+${it.value}" }
            Log.d(tag, "SCORE[$puzzleType]: base=$baseScore, bonuses=[$bonusStr], final=$finalScore")
        }
    }
}

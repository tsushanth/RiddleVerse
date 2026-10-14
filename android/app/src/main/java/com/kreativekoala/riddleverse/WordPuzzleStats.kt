package com.kreativekoala.riddleverse

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Minimal key/value seam so stats logic is JVM-testable without Android. */
interface WordPuzzleKeyValueStore {
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
}

class PrefsKeyValueStore(context: Context) : WordPuzzleKeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences("word_puzzle_stats", Context.MODE_PRIVATE)
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
}

data class WordPuzzleStatsSnapshot(val played: Int, val won: Int, val streak: Int, val bestStreak: Int, val distribution: List<Int>) {
    val winPercent: Int get() = if (played == 0) 0 else (won * 100.0 / played).toInt()
}

/** Per-game played / won / streak counters (+ solve-in-N distribution for guess-count games). Local only. */
class WordPuzzleStats(private val store: WordPuzzleKeyValueStore) {
    /**
     * Bumped by every [record]. [snapshot] reads it, so a composable that shows a snapshot recomposes once the
     * result has been recorded. The screens record from a LaunchedEffect that runs AFTER the result card first
     * composes; without this the card kept the pre-record snapshot and showed "Played 0  Won 0%" for the first game.
     */
    private var version by mutableIntStateOf(0)

    fun record(game: String, won: Boolean, attempts: Int = 0, distributionSize: Int = 0) {
        store.putInt("${game}_played", store.getInt("${game}_played", 0) + 1)
        if (won) {
            store.putInt("${game}_won", store.getInt("${game}_won", 0) + 1)
            val s = store.getInt("${game}_streak", 0) + 1
            store.putInt("${game}_streak", s)
            if (s > store.getInt("${game}_best", 0)) store.putInt("${game}_best", s)
            if (attempts in 1..distributionSize) {
                store.putInt("${game}_dist_$attempts", store.getInt("${game}_dist_$attempts", 0) + 1)
            }
        } else {
            store.putInt("${game}_streak", 0)
        }
        version++
    }

    fun snapshot(game: String, distributionSize: Int = 0): WordPuzzleStatsSnapshot {
        @Suppress("UNUSED_VARIABLE") val observed = version // subscribe the caller to record()
        return readSnapshot(game, distributionSize)
    }

    private fun readSnapshot(game: String, distributionSize: Int) = WordPuzzleStatsSnapshot(
        store.getInt("${game}_played", 0), store.getInt("${game}_won", 0),
        store.getInt("${game}_streak", 0), store.getInt("${game}_best", 0),
        (1..distributionSize).map { store.getInt("${game}_dist_$it", 0) }
    )
}

package com.kreativekoala.riddleverse

import android.content.Context

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
    }

    fun snapshot(game: String, distributionSize: Int = 0) = WordPuzzleStatsSnapshot(
        store.getInt("${game}_played", 0), store.getInt("${game}_won", 0),
        store.getInt("${game}_streak", 0), store.getInt("${game}_best", 0),
        (1..distributionSize).map { store.getInt("${game}_dist_$it", 0) }
    )
}

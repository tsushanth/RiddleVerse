package com.kreativekoala.riddleverse

import android.content.Context

object UserScoreStore {
    private const val PREFS = "user_score_prefs"
    private const val KEY_SCORE = "cached_score"

    fun load(context: Context): Int {
        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SCORE, 0)
    }

    fun save(context: Context, score: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SCORE, score)
            .apply()
    }
}
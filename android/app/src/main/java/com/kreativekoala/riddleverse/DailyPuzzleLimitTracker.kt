package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tracks daily puzzle regeneration limits and hidden puzzles in user settings.
 * Automatically resets every day at midnight.
 */
class DailyPuzzleLimitTracker private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("daily_puzzle_limits", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: DailyPuzzleLimitTracker? = null

        private const val KEY_CURRENT_DATE = "current_date"
        private const val KEY_LIMITED_PUZZLES = "limited_puzzles"
        private const val KEY_HIDDEN_PUZZLES = "hidden_puzzles" // NEW: Track hidden puzzles

        fun getInstance(context: Context): DailyPuzzleLimitTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DailyPuzzleLimitTracker(context).also { INSTANCE = it }
            }
        }
    }

    /**
     * Get current date as string in YYYY-MM-DD format
     */
    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    /**
     * Check if we need to reset limits for a new day
     */
    private fun checkAndResetIfNewDay() {
        val currentDate = getCurrentDateString()
        val storedDate = prefs.getString(KEY_CURRENT_DATE, "")

        if (currentDate != storedDate) {
            Log.d("DailyPuzzleLimitTracker", "New day detected: $currentDate (was: $storedDate)")

            // Clear all limits, hidden puzzles, and update date
            prefs.edit()
                .putString(KEY_CURRENT_DATE, currentDate)
                .putStringSet(KEY_LIMITED_PUZZLES, emptySet())
                .putStringSet(KEY_HIDDEN_PUZZLES, emptySet()) // NEW: Reset hidden puzzles
                .apply()

            Log.d("DailyPuzzleLimitTracker", "Reset daily limits and hidden puzzles for new day: $currentDate")
        }
    }

    // EXISTING METHODS (unchanged)

    /**
     * Add a puzzle type to the daily limit list
     */
    fun addPuzzleToLimitList(puzzleType: String) {
        checkAndResetIfNewDay()

        val currentLimitedPuzzles = getLimitedPuzzleTypes().toMutableSet()
        currentLimitedPuzzles.add(puzzleType)

        prefs.edit()
            .putStringSet(KEY_LIMITED_PUZZLES, currentLimitedPuzzles)
            .apply()

        Log.d("DailyPuzzleLimitTracker", "Added $puzzleType to daily limits. Current list: $currentLimitedPuzzles")
    }

    /**
     * Remove a puzzle type from the daily limit list (useful for manual override)
     */
    fun removePuzzleFromLimitList(puzzleType: String) {
        checkAndResetIfNewDay()

        val currentLimitedPuzzles = getLimitedPuzzleTypes().toMutableSet()
        if (currentLimitedPuzzles.remove(puzzleType)) {
            prefs.edit()
                .putStringSet(KEY_LIMITED_PUZZLES, currentLimitedPuzzles)
                .apply()

            Log.d("DailyPuzzleLimitTracker", "Removed $puzzleType from daily limits. Current list: $currentLimitedPuzzles")
        }
    }

    /**
     * Check if a puzzle type has hit its daily limit
     */
    fun isPuzzleLimited(puzzleType: String): Boolean {
        checkAndResetIfNewDay()
        return getLimitedPuzzleTypes().contains(puzzleType)
    }

    /**
     * Get all puzzle types that have hit their daily limits
     */
    fun getLimitedPuzzleTypes(): Set<String> {
        checkAndResetIfNewDay()
        return prefs.getStringSet(KEY_LIMITED_PUZZLES, emptySet()) ?: emptySet()
    }

    /**
     * Get count of limited puzzle types
     */
    fun getLimitedPuzzleCount(): Int {
        return getLimitedPuzzleTypes().size
    }

    /**
     * Check if user can regenerate a specific puzzle type
     */
    fun canRegeneratePuzzle(puzzleType: String): Boolean {
        return !isPuzzleLimited(puzzleType)
    }

    /**
     * Get the current date being tracked
     */
    fun getCurrentTrackedDate(): String {
        checkAndResetIfNewDay()
        return prefs.getString(KEY_CURRENT_DATE, getCurrentDateString()) ?: getCurrentDateString()
    }

    /**
     * Manual reset (useful for testing or admin functions)
     */
    fun manualReset() {
        val currentDate = getCurrentDateString()
        prefs.edit()
            .putString(KEY_CURRENT_DATE, currentDate)
            .putStringSet(KEY_LIMITED_PUZZLES, emptySet())
            .putStringSet(KEY_HIDDEN_PUZZLES, emptySet()) // NEW: Reset hidden puzzles
            .apply()

        Log.d("DailyPuzzleLimitTracker", "Manual reset completed for date: $currentDate")
    }

    /**
     * Get debug information
     */
    fun getDebugInfo(): Map<String, Any> {
        checkAndResetIfNewDay()
        return mapOf(
            "currentDate" to getCurrentDateString(),
            "trackedDate" to getCurrentTrackedDate(),
            "limitedPuzzles" to getLimitedPuzzleTypes().toList(),
            "limitedCount" to getLimitedPuzzleCount(),
            "hiddenPuzzles" to getHiddenPuzzleTypes().toList(), // NEW
            "hiddenCount" to getHiddenPuzzleCount() // NEW
        )
    }

    // NEW METHODS FOR PUZZLE HIDING

    /**
     * Hide a puzzle type for today (user chooses to hide after hitting limits)
     */
    fun hidePuzzleType(puzzleType: String) {
        checkAndResetIfNewDay()

        val hiddenPuzzles = getHiddenPuzzleTypes().toMutableSet()
        if (hiddenPuzzles.add(puzzleType)) {
            prefs.edit()
                .putStringSet(KEY_HIDDEN_PUZZLES, hiddenPuzzles)
                .apply()

            Log.d("DailyPuzzleLimitTracker", "Hidden puzzle type: $puzzleType. Total hidden: ${hiddenPuzzles.size}")
        }
    }

    /**
     * Unhide a puzzle type (if user wants to see it again)
     */
    fun unhidePuzzleType(puzzleType: String) {
        checkAndResetIfNewDay()

        val hiddenPuzzles = getHiddenPuzzleTypes().toMutableSet()
        if (hiddenPuzzles.remove(puzzleType)) {
            prefs.edit()
                .putStringSet(KEY_HIDDEN_PUZZLES, hiddenPuzzles)
                .apply()

            Log.d("DailyPuzzleLimitTracker", "Unhidden puzzle type: $puzzleType. Total hidden: ${hiddenPuzzles.size}")
        }
    }

    /**
     * Check if a puzzle type is hidden for today
     */
    fun isPuzzleTypeHidden(puzzleType: String): Boolean {
        checkAndResetIfNewDay()
        return getHiddenPuzzleTypes().contains(puzzleType)
    }

    /**
     * Get all hidden puzzle types for today
     */
    fun getHiddenPuzzleTypes(): Set<String> {
        checkAndResetIfNewDay()
        return prefs.getStringSet(KEY_HIDDEN_PUZZLES, emptySet()) ?: emptySet()
    }

    /**
     * Get count of hidden puzzle types
     */
    fun getHiddenPuzzleCount(): Int {
        return getHiddenPuzzleTypes().size
    }

    /**
     * Get filtered puzzle types for For You tab (removes hidden ones)
     */
    fun getFilteredPuzzleTypes(originalTypes: List<String>): List<String> {
        val hiddenTypes = getHiddenPuzzleTypes()
        return originalTypes.filter { !hiddenTypes.contains(it) }
    }

    /**
     * Get alternative puzzle types when some are hidden
     * This generates alternatives to replace hidden types in For You tab
     */
    fun getAlternativePuzzleTypes(originalTypes: List<String>, targetCount: Int = 6): List<String> {
        val hiddenTypes = getHiddenPuzzleTypes()
        val visibleOriginals = originalTypes.filter { !hiddenTypes.contains(it) }

        if (visibleOriginals.size >= targetCount) {
            return visibleOriginals.take(targetCount)
        }

        // Need to find alternatives from all available types
        val allAvailableTypes = QuizCategories.getAllAvailablePuzzleTypes()
        val alternatives = allAvailableTypes.filter { type ->
            !hiddenTypes.contains(type) && !originalTypes.contains(type)
        }

        val result = (visibleOriginals + alternatives.shuffled()).take(targetCount)

        Log.d("DailyPuzzleLimitTracker", "Generated ${result.size} alternatives (${hiddenTypes.size} types hidden)")
        return result
    }

    /**
     * Check if puzzle should show "hide" option (limited but not yet hidden)
     */
    fun shouldShowHideOption(puzzleType: String): Boolean {
        return isPuzzleLimited(puzzleType) && !isPuzzleTypeHidden(puzzleType)
    }

    /**
     * Check if puzzle should show "show again" option (currently hidden)
     */
    fun shouldShowUnhideOption(puzzleType: String): Boolean {
        return isPuzzleTypeHidden(puzzleType)
    }
}
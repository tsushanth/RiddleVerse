package com.kreativekoala.riddleverse

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages tutorial preferences and tracks which tutorials the user has seen
 */
class TutorialPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tutorial_prefs", Context.MODE_PRIVATE)

    /**
     * Check if user has seen tutorial for a specific puzzle type
     */
    fun hasSeenTutorial(puzzleType: String): Boolean {
        return prefs.getBoolean("tutorial_seen_$puzzleType", false)
    }

    /**
     * Mark tutorial as seen for a specific puzzle type
     */
    fun markTutorialSeen(puzzleType: String) {
        prefs.edit().putBoolean("tutorial_seen_$puzzleType", true).apply()
    }

    /**
     * Check if we should show tutorial prompt banner
     */
    fun shouldShowTutorialPrompt(puzzleType: String): Boolean {
        return !hasSeenTutorial(puzzleType)
    }

    /**
     * Reset tutorial preferences (for testing or user request)
     */
    fun resetTutorialPreferences() {
        prefs.edit().clear().apply()
    }

    /**
     * Reset specific puzzle type tutorial
     */
    fun resetTutorialForPuzzleType(puzzleType: String) {
        prefs.edit().remove("tutorial_seen_$puzzleType").apply()
    }

    /**
     * Check if auto-tutorial is enabled (default: true for first-time experience)
     */
    fun isAutoTutorialEnabled(): Boolean {
        return prefs.getBoolean("auto_tutorial_enabled", true)
    }

    /**
     * Enable or disable auto-tutorial for first-time puzzle plays
     */
    fun setAutoTutorialEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_tutorial_enabled", enabled).apply()
    }

    /**
     * Check if this puzzle type has an interactive tutorial available
     */
    fun hasInteractiveTutorial(puzzleType: String): Boolean = TutorialRegistry.hasTutorial(puzzleType)

    /**
     * Check if tutorial should auto-show for first-time users
     * Returns true if: user hasn't seen tutorial AND auto-tutorial is enabled AND puzzle has a tutorial
     */
    fun shouldAutoShowTutorial(puzzleType: String): Boolean {
        return !hasSeenTutorial(puzzleType) &&
               isAutoTutorialEnabled() &&
               hasInteractiveTutorial(puzzleType)
    }

    /**
     * Check if user has completed the onboarding tutorial
     */
    fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }

    /**
     * Mark onboarding tutorial as completed
     */
    fun markOnboardingCompleted() {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
    }
}
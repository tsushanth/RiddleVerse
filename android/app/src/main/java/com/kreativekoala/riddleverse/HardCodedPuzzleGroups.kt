package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log

object HardcodedPuzzleGroups {

    fun getAllGroups(context: Context? = null, userId: String? = null): List<PuzzleGroup> {
        val baseGroups = listOf(



            PuzzleGroup(
                id = "visual_memory",
                name = "Visual Memory",
                description = "Test your visual recall and pattern recognition",
                puzzleTypes = listOf("find_object", "imagepuzzle", "memorysquares", "memoryprevioussingle", "memorypreviouspair", "imagevortex", "pinballdeflector"),
                completedTypes = emptySet(),
                category = "Memory",
                difficulty = "Medium",
                totalTypes = 7
            ),




            PuzzleGroup(
                id = "vocabulary_builder",
                name = "Vocabulary Builder",
                description = "Enhance your language skills and word knowledge",
                puzzleTypes = listOf("antonyms", "wordprefix", "synonyms", "letterset", "connotationwords"),
                completedTypes = emptySet(),
                category = "English",
                difficulty = "Medium",
                totalTypes = 5
            ),

            PuzzleGroup(
                id = "logic_puzzles",
                name = "Logic Puzzles",
                description = "Critical thinking and reasoning challenges",
                puzzleTypes = listOf("storyPuzzle", "uniqueobject", "numbersequence"),
                completedTypes = emptySet(),
                category = "Logic",
                difficulty = "Medium",
                totalTypes = 3
            ),

            PuzzleGroup(
                id = "focus_training",
                name = "Focus Training",
                description = "Attention and concentration exercises",
                puzzleTypes = listOf("colormatching", "symbolswipe"),
                completedTypes = emptySet(),
                category = "Focus",
                difficulty = "Easy",
                totalTypes = 2
            ),

            PuzzleGroup(
                id = "story_memory",
                name = "Story Memory",
                description = "Audio-based memory and retention challenges",
                puzzleTypes = listOf("memorystory", "memoryretention", "memorysequencing"),
                completedTypes = emptySet(),
                category = "Memory",
                difficulty = "Hard",
                totalTypes = 3
            ),

            PuzzleGroup(
                id = "word_puzzles",
                name = "Word Puzzles",
                description = "Classic word games and vocabulary challenges",
                puzzleTypes = listOf("anagram", "wordsnake", "crypto",  "wordsearch"),
                completedTypes = emptySet(),
                category = "English",
                difficulty = "Easy",
                totalTypes = 4
            ),

            PuzzleGroup(
                id = "trivia_master",
                name = "Trivia Master",
                description = "Test your general knowledge across various topics",
                puzzleTypes = listOf("trivia", "imagematch"),
                completedTypes = emptySet(),
                category = "General",
                difficulty = "Easy",
                totalTypes = 2
            ),

            PuzzleGroup(
                id = "basic_math",
                name = "Basic Math",
                description = "Fundamental mathematical operations and calculations",
                puzzleTypes = listOf("subtraction", "average", "division", "math"),
                completedTypes = emptySet(),
                category = "Math",
                difficulty = "Easy",
                totalTypes = 4
            ),

            PuzzleGroup(
                id = "advanced_math",
                name = "Advanced Math",
                description = "Complex mathematical problems and estimations",
                puzzleTypes = listOf("mathtipping","mathestimation", "percentages", "mathcomparison"),
                completedTypes = emptySet(),
                category = "Math",
                difficulty = "Medium",
                totalTypes = 4
            ),

            PuzzleGroup(
                id = "practical_math",
                name = "Practical Math",
                description = "Real-world mathematical applications",
                puzzleTypes = listOf("discounts", "purchasing", "conversion", "numbersum"),
                completedTypes = emptySet(),
                category = "Math",
                difficulty = "Medium",
                totalTypes = 4
            ),

            PuzzleGroup(
                id = "beginner_mix",
                name = "Beginner Mix",
                description = "Perfect starting point for new puzzle solvers",
                puzzleTypes = listOf("math", "anagram", "trivia", "colormatching"),
                completedTypes = emptySet(),
                category = "Mixed",
                difficulty = "Easy",
                totalTypes = 4
            ),

            PuzzleGroup(
                id = "brain_training",
                name = "Brain Training",
                description = "Comprehensive cognitive workout",
                puzzleTypes = listOf("memorysquares", "antonyms", "uniqueobject"),
                completedTypes = emptySet(),
                category = "Mixed",
                difficulty = "Medium",
                totalTypes = 4
            ),

            PuzzleGroup(
                id = "expert_challenge",
                name = "Expert Challenge",
                description = "Ultimate test for puzzle masters",
                puzzleTypes = listOf("pinballdeflector", "memorysequencing", "mathcomparison", "storyPuzzle"),
                completedTypes = emptySet(),
                category = "Mixed",
                difficulty = "Hard",
                totalTypes = 4
            ),

            PuzzleGroup(
                id = "speed_round",
                name = "Speed Round",
                description = "Quick-fire puzzles for rapid thinking",
                puzzleTypes = listOf("symbolswipe", "numbersum", "colormatching", "subtraction"),
                completedTypes = emptySet(),
                category = "Reaction",
                difficulty = "Medium",
                totalTypes = 4
            )
        )

        // ADDED: Populate actual completion status if context and userId are provided
        return if (context != null && userId != null) {
            baseGroups.map { group ->
                val completedTypes = GroupCompletionManager.getCompletedTypes(context, userId, group.id)
                group.copy(
                    completedTypes = completedTypes
                    // Note: completedCount and isCompleted are calculated properties in PuzzleGroup
                )
            }
        } else {
            baseGroups
        }
    }

    // UPDATED: All these functions now need context and userId
    fun getPopularGroups(context: Context? = null, userId: String? = null): List<PuzzleGroup> {
        return getAllGroups(context, userId).filter { group ->
            group.id in listOf(
                "basic_math",
                "word_puzzles",
                "visual_memory",
                "beginner_mix",
                "brain_training"
            )
        }
    }

    fun getFavoriteGroups(context: Context? = null, userId: String? = null): List<PuzzleGroup> {
        return getAllGroups(context, userId).filter { group ->
            group.id in listOf(
                "expert_challenge",
                "story_memory",
                "vocabulary_builder"
            )
        }
    }
}

// UNCHANGED: GroupCompletionManager remains the same
object GroupCompletionManager {
    private const val PREFS_NAME = "puzzle_group_completion"
    private const val COMPLETED_TYPES_KEY = "completed_types_"
    private const val TAG = "GroupCompletionManager"

    private val observers = mutableListOf<(String, String) -> Unit>()

    fun registerObserver(observer: (groupId: String, userId: String) -> Unit) {
        observers.add(observer)
        Log.d(TAG, "Observer registered. Total observers: ${observers.size}")
    }

    fun unregisterObserver(observer: (groupId: String, userId: String) -> Unit) {
        observers.remove(observer)
        Log.d(TAG, "Observer unregistered. Total observers: ${observers.size}")
    }

    private fun notifyObservers(groupId: String, userId: String) {
        Log.d(TAG, "🔔 Notifying ${observers.size} observers for group: $groupId, user: $userId")
        observers.forEach { observer ->
            try {
                observer(groupId, userId)
            } catch (e: Exception) {
                Log.e(TAG, "Observer notification failed", e)
            }
        }
    }

    fun getCompletedTypes(context: Context, userId: String, groupId: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${COMPLETED_TYPES_KEY}${userId}_$groupId"
        val completedString = prefs.getString(key, "") ?: ""
        val completed = if (completedString.isBlank()) {
            emptySet()
        } else {
            completedString.split(",").toSet()
        }

        return completed
    }

    fun markTypeCompleted(context: Context, userId: String, groupId: String, puzzleType: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${COMPLETED_TYPES_KEY}${userId}_$groupId"
        val currentCompleted = getCompletedTypes(context, userId, groupId).toMutableSet()

        if (!currentCompleted.contains(puzzleType)) {
            currentCompleted.add(puzzleType)

            prefs.edit()
                .putString(key, currentCompleted.joinToString(","))
                .apply()

            Log.d(TAG, "✅ Marked '$puzzleType' as completed in group '$groupId'. Total completed: ${currentCompleted.size}")

            // Notify observers
            notifyObservers(groupId, userId)
        } else {
            Log.d(TAG, "ℹ️ '$puzzleType' already completed in group '$groupId'")
        }
    }

    fun isTypeCompleted(context: Context, userId: String, groupId: String, puzzleType: String): Boolean {
        return getCompletedTypes(context, userId, groupId).contains(puzzleType)
    }

    fun getGroupProgress(context: Context, userId: String, groupId: String, totalTypes: Int): Pair<Int, Boolean> {
        val completedTypes = getCompletedTypes(context, userId, groupId)
        val isCompleted = completedTypes.size >= totalTypes
        return Pair(completedTypes.size, isCompleted)
    }

    /**
     * Reset all completion data for daily reset
     */
    fun resetAllCompletion(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Get all keys that start with completion prefix
            val allKeys = prefs.all.keys
            val completionKeys = allKeys.filter { it.startsWith(COMPLETED_TYPES_KEY) }

            Log.d(TAG, "🔄 Resetting ${completionKeys.size} completion entries")

            // Clear all completion data
            val editor = prefs.edit()
            completionKeys.forEach { key ->
                editor.remove(key)
                Log.d(TAG, "   Cleared: $key")
            }
            editor.apply()

            Log.d(TAG, "✅ All completion data reset successfully")

            // Notify observers of the reset
            observers.forEach { observer ->
                try {
                    observer("ALL_GROUPS_RESET", "system_reset")
                } catch (e: Exception) {
                    Log.e(TAG, "Observer notification failed during reset", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to reset completion data", e)
        }
    }
}

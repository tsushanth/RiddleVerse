package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

class PuzzleCompletionHandler(
    private val viewModel: PuzzleViewModel,
    private val context: Context,
    private val onComplete: () -> Unit
) {
    private val TAG = "PuzzleCompletionHandler"

    fun handlePuzzleCompletion(isCorrect: Boolean, inTime: Boolean = true, score: Int = 0) {
        Log.d(TAG, "🎯 Puzzle completed - correct: $isCorrect, inTime: $inTime")

        // Track answer in viewModel for ALL answers (correct AND incorrect)
        // This handles: media cleanup, timing, score, stats recording, streaks, analytics
        viewModel.handlePuzzleCompletion(context, isCorrect, inTime)

        Log.d(TAG, "📊 After viewModel tracking:")
        Log.d(TAG, "  Correct: ${viewModel.getCorrectAnswerCount()}/${viewModel.getTotalAnswerCount()}")
        Log.d(TAG, "  Session Score: ${viewModel.getCurrentScore()}")
        Log.d(TAG, "  Streak: ${viewModel.getStreakCount()}")

        // Group tracking logic (only for correct answers)
        if (isCorrect) {
            val activity = context as? PuzzleActivity
            val sourceGroupId = activity?.intent?.getStringExtra("sourceGroupId")
            val puzzleType = viewModel.getCurrentPuzzle()?.puzzleType ?: activity?.intent?.getStringExtra("puzzleType")
            val userId = FirebaseAuth.getInstance().currentUser?.email ?: ""
            val currentScore = if (score > 0) score else viewModel.getCurrentScore()

            if (sourceGroupId != null && puzzleType != null && userId.isNotEmpty()) {
                try {
                    val prefs = context.getSharedPreferences("puzzle_completion", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("${userId}_${puzzleType}_completed", true)
                        .putInt("${userId}_${puzzleType}_best_score", currentScore)
                        .putInt("${userId}_${puzzleType}_play_count",
                            prefs.getInt("${userId}_${puzzleType}_play_count", 0) + 1)
                        .apply()

                    Log.d(TAG, "✅ Group completion tracked successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to track puzzle completion", e)
                }
            }
        }

        handleFlowProgression()
    }

    private fun getCurrentPuzzleId(): String? {
        return try {
            // Try to get from current puzzle first
            val currentPuzzle = viewModel.getCurrentPuzzle()
            if (currentPuzzle?.puzzleId != null) {
                return currentPuzzle.puzzleId
            }

            // Fallback: extract from intent
            val activity = context as? PuzzleActivity
            val puzzleJson = activity?.intent?.getStringExtra("puzzle")
            if (puzzleJson != null) {
                val puzzle = com.google.gson.Gson().fromJson(puzzleJson, Puzzle::class.java)
                puzzle.puzzleId
            } else {
                // Last fallback: generate from puzzle data
                val puzzleType = viewModel.getCurrentPuzzle()?.puzzleType
                    ?: activity?.intent?.getStringExtra("puzzleType")
                val timestamp = activity?.intent?.getLongExtra("startTime", 0) ?: 0
                if (puzzleType != null && timestamp > 0) {
                    "${puzzleType}_${timestamp}"
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current puzzle ID", e)
            null
        }
    }

    private fun handleFlowProgression() {
        if (viewModel.isCustomPuzzleFlow) {
            // CUSTOM PUZZLE FLOW
            Log.d(TAG, "🎨 Handling custom puzzle completion")
            Log.d(TAG, "📊 Progress: ${viewModel.currentQuestionNumber} of ${viewModel.totalQuestions}")

            val nextIntent = viewModel.getNextCustomPuzzleIntent(context)
            if (nextIntent != null) {
                Log.d(TAG, "➡️ Moving to next custom puzzle")
                context.startActivity(nextIntent)
                (context as? PuzzleActivity)?.finish()
            } else {
                Log.d(TAG, "🏁 All custom puzzles completed")
                onComplete()
            }

        } else {
            // AI-GENERATED PUZZLE FLOW
            Log.d(TAG, "🤖 Handling AI puzzle completion")
            Log.d(TAG, "📊 Progress: ${viewModel.currentPuzzleNumber} of ${viewModel.targetPuzzleCount}")

            if (viewModel.isLastAIPuzzle) {
                Log.d(TAG, "🏁 Reached target puzzle count, showing completion")
                onComplete()
                return
            }

            // Move to next AI puzzle
            viewModel.moveToNextAIPuzzle()
            handleNextAIPuzzle()
        }
    }

    private fun handleNextAIPuzzle() {
        val currentPuzzle = viewModel.getCurrentPuzzle() ?: return
        val activity = context as? PuzzleActivity
        val sourceGroupId = activity?.intent?.getStringExtra("sourceGroupId")
        val sourceGroupName = activity?.intent?.getStringExtra("sourceGroupName")
        val puzzleType = activity?.intent?.getStringExtra("puzzleType")

        // Store cumulative session stats on current activity's intent
        // so PuzzleFetcher can carry them to the next PuzzleActivity
        activity?.intent?.apply {
            putExtra("cumulativeCorrectAnswers", viewModel.getCorrectAnswerCount())
            putExtra("cumulativeTotalAnswers", viewModel.getTotalAnswerCount())
            putExtra("cumulativeSessionScore", viewModel.getCurrentScore())
            putExtra("cumulativeBestStreak", viewModel.getBestStreakThisSession())
            putExtra("cumulativeCurrentStreak", viewModel.getStreakCount())
            putExtra("sessionStartTime", viewModel.getSessionStartTime())
        }

        Log.d(TAG, "🎯 About to fetch next puzzle with group context:")
        Log.d(TAG, "  sourceGroupId: $sourceGroupId")
        Log.d(TAG, "  sourceGroupName: $sourceGroupName")
        Log.d(TAG, "  puzzleType: $puzzleType")
        Log.d(TAG, "  cumulative stats: ${viewModel.getCorrectAnswerCount()}/${viewModel.getTotalAnswerCount()} correct, score=${viewModel.getCurrentScore()}")

        PuzzleFetcher.fetchNextPuzzleOfSameType(
            context = context,
            currentPuzzle = currentPuzzle,
            screenType = viewModel.screenType.typeName,
            targetPuzzleCount = viewModel.targetPuzzleCount,
            currentPuzzleNumber = viewModel.currentPuzzleNumber,
            sourceGroupId = sourceGroupId,
            sourceGroupName = sourceGroupName,
            originalPuzzleType = puzzleType,
            onSuccess = {
                Log.d(TAG, "✅ Next AI puzzle fetched successfully")
            },
            onError = { error ->
                Log.e(TAG, "❌ Failed to fetch next AI puzzle: $error")
                onComplete()
            },
            onNoMorePuzzles = {
                Log.d(TAG, "🏁 No more AI puzzles available")
                onComplete()
            }
        )
    }
}
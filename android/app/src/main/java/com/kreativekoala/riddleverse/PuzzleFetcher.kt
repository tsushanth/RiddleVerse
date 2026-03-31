package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject

object PuzzleFetcher {
    private const val TAG = "PuzzleFetcher"

    fun fetchNextPuzzleOfSameType(
        context: Context,
        currentPuzzle: Puzzle,
        screenType: String,
        targetPuzzleCount: Int,
        currentPuzzleNumber: Int,
        sourceGroupId: String? = null,
        sourceGroupName: String? = null,
        originalPuzzleType: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onNoMorePuzzles: () -> Unit
    ) {
        val puzzleType = currentPuzzle.puzzleType ?: "math"
        val difficulty = currentPuzzle.difficulty ?: "easy"

        Log.d(TAG, "Fetching next puzzle of same type")
        Log.d(TAG, "Parameters: type=$puzzleType, difficulty=$difficulty, screenType=$screenType")


        if (isOfflinePuzzleType(puzzleType)) {
            generateLocalPuzzle(context, puzzleType, difficulty, screenType, targetPuzzleCount, currentPuzzleNumber, sourceGroupId, sourceGroupName, originalPuzzleType, onSuccess)
        } else {
            fetchServerPuzzleNonBlocking(context, puzzleType, difficulty, screenType, targetPuzzleCount, currentPuzzleNumber, sourceGroupId, sourceGroupName, originalPuzzleType, onSuccess, onError, onNoMorePuzzles)
        }
    }

    private fun generateLocalPuzzle(
        context: Context,
        puzzleType: String,
        difficulty: String,
        screenType: String,
        targetPuzzleCount: Int,
        currentPuzzleNumber: Int,
        sourceGroupId: String?,
        sourceGroupName: String?,
        originalPuzzleType: String?,
        onSuccess: () -> Unit
    ) {
        Log.d(TAG, "Using local puzzle generation for type: $puzzleType")

        val localPuzzle = LocalPuzzleGenerator.generatePuzzle(puzzleType, difficulty, context)

        Log.d(TAG, "Created local puzzle: ${localPuzzle.puzzleId}")

        // Create new intent with the local puzzle
        val intent = Intent(context, PuzzleActivity::class.java).apply {
            putExtra("puzzle", Gson().toJson(localPuzzle))
            putExtra("screenType", screenType)
            putExtra("difficulty", difficulty)
            putExtra("score", 0)
            putExtra("startTime", System.currentTimeMillis())
            putExtra("targetPuzzleCount", targetPuzzleCount)
            putExtra("currentPuzzleNumber", currentPuzzleNumber)

            // Preserve group context and cumulative stats
            if (context is PuzzleActivity) {
                val currentIntent = context.intent
                putExtra("sourceGroupId", currentIntent.getStringExtra("sourceGroupId"))
                putExtra("sourceGroupName", currentIntent.getStringExtra("sourceGroupName"))
                putExtra("puzzleType", currentIntent.getStringExtra("puzzleType"))

                // Carry cumulative session stats
                putExtra("cumulativeCorrectAnswers", currentIntent.getIntExtra("cumulativeCorrectAnswers", 0))
                putExtra("cumulativeTotalAnswers", currentIntent.getIntExtra("cumulativeTotalAnswers", 0))
                putExtra("cumulativeSessionScore", currentIntent.getIntExtra("cumulativeSessionScore", 0))
                putExtra("cumulativeBestStreak", currentIntent.getIntExtra("cumulativeBestStreak", 0))
                putExtra("cumulativeCurrentStreak", currentIntent.getIntExtra("cumulativeCurrentStreak", 0))
                putExtra("sessionStartTime", currentIntent.getLongExtra("sessionStartTime", System.currentTimeMillis()))

                Log.d(TAG, "Preserving group context + cumulative stats for local puzzle")
            }
        }

        Log.d(TAG, "Starting local puzzle $currentPuzzleNumber of $targetPuzzleCount")
        context.startActivity(intent)
        (context as? PuzzleActivity)?.finish()
        onSuccess()
    }

    private fun fetchServerPuzzleNonBlocking(
        context: Context,
        puzzleType: String,
        difficulty: String,
        screenType: String,
        targetPuzzleCount: Int,
        currentPuzzleNumber: Int,
        sourceGroupId: String?,
        sourceGroupName: String?,
        originalPuzzleType: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onNoMorePuzzles: () -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val puzzleQueueManager = PuzzleQueueManager.getInstance(context)
                val queuedPuzzle = puzzleQueueManager.getNextPuzzleForPlay(puzzleType, difficulty)

                if (queuedPuzzle != null) {
                    val newPuzzle = queuedPuzzle.toPuzzle()

                    val intent = Intent(context, PuzzleActivity::class.java).apply {
                        putExtra("puzzle", Gson().toJson(newPuzzle))
                        putExtra("screenType", screenType)
                        putExtra("difficulty", difficulty)
                        putExtra("score", 0)
                        putExtra("startTime", System.currentTimeMillis())
                        putExtra("targetPuzzleCount", targetPuzzleCount)
                        putExtra("currentPuzzleNumber", currentPuzzleNumber)

                        if (context is PuzzleActivity) {
                            val currentIntent = context.intent
                            putExtra("sourceGroupId", currentIntent.getStringExtra("sourceGroupId"))
                            putExtra("sourceGroupName", currentIntent.getStringExtra("sourceGroupName"))
                            putExtra("puzzleType", currentIntent.getStringExtra("puzzleType"))

                            // Carry cumulative session stats
                            putExtra("cumulativeCorrectAnswers", currentIntent.getIntExtra("cumulativeCorrectAnswers", 0))
                            putExtra("cumulativeTotalAnswers", currentIntent.getIntExtra("cumulativeTotalAnswers", 0))
                            putExtra("cumulativeSessionScore", currentIntent.getIntExtra("cumulativeSessionScore", 0))
                            putExtra("cumulativeBestStreak", currentIntent.getIntExtra("cumulativeBestStreak", 0))
                            putExtra("cumulativeCurrentStreak", currentIntent.getIntExtra("cumulativeCurrentStreak", 0))
                            putExtra("sessionStartTime", currentIntent.getLongExtra("sessionStartTime", System.currentTimeMillis()))
                        }
                    }

                    context.startActivity(intent)
                    (context as? PuzzleActivity)?.finish()
                    onSuccess()
                } else {
                    onNoMorePuzzles()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking queue", e)
                onError("Unable to load puzzle: ${e.message}")
            }
        }
    }

}
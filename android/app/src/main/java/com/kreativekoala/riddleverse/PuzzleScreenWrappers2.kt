package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun ColorShapeMatchingScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    // Extract initial difficulty from the puzzle, defaulting to "Medium"
    val initialDifficulty = currentPuzzle.difficulty ?: "Medium"

    // Format timer - you might want to make this configurable based on puzzle settings
    val timer = "2:00"

    // Extract hearts/lives - could be made configurable
    val hearts = 3

    // Format level display
    val level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}"

    // Use the adaptive color shape screen
    AdaptiveColorShapeMatchingPuzzleScreen(
        initialDifficulty = initialDifficulty,
        timer = timer,
        hearts = hearts,
        level = level,
        onGameComplete = { success, score ->
            Log.d("PuzzleScreen", "🎨 Adaptive color shape matching game completed: success=$success, score=$score")
            handlePuzzleCompletion(success, true, score)
        },
        onBack = onBack
    )
}

@Composable
fun SymmetryScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    context: Context, // Keep for potential future use, but not needed for adaptive version
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "SymmetryWrapper"

    // Extract difficulty from puzzle data
    val difficulty = remember(currentPuzzle) {
        currentPuzzle.difficulty?.takeIf { it.isNotEmpty() } ?: "Medium"
    }

    Log.d(TAG, "🔄 Setting up adaptive symmetry puzzle:")
    Log.d(TAG, "  Difficulty: $difficulty")
    Log.d(TAG, "  Puzzle ID: ${currentPuzzle.puzzleId}")
    Log.d(TAG, "  Current puzzle number: ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}")

    // Use the adaptive version which handles its own puzzle generation and progression
    AdaptiveSymmetryPuzzleScreen(
        initialDifficulty = difficulty,
        timer = "2:30", // This will be overridden by the adaptive system based on difficulty
        hearts = 3, // This will be overridden by the adaptive system
        level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
        onSubmitAnswer = { isCorrect ->
            Log.d(TAG, "📝 Adaptive symmetry answer submitted: $isCorrect")

            // Optional: Record to old system for backwards compatibility/analytics
            // Note: The adaptive system handles its own performance tracking
            try {
                SymmetryPuzzleGenerator.recordResult(context, isCorrect, 0)
                Log.d(TAG, "✅ Recorded result to legacy system for compatibility")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to record to legacy system (non-critical): ${e.message}")
            }
        },
        fetchNextPuzzle = { finalScore ->
            Log.d(TAG, "🔄 Fetching next puzzle after adaptive symmetry completion:")
            Log.d(TAG, "   Final score: $finalScore")

            // Determine success based on score (adaptive system provides this)
            val success = finalScore > 0

            handlePuzzleCompletion(success, success, finalScore)
        },
        onBack = onBack
    )
}

@Composable
fun SymbolSwipeScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "SymbolSwipeWrapper"

    // Extract difficulty from puzzle data
    val difficulty = remember(currentPuzzle) {
        currentPuzzle.difficulty?.takeIf { it.isNotEmpty() } ?: "Medium"
    }

    // Determine if this is the first puzzle in the session for instruction display
    val isFirstPuzzle = remember {
        (if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber) == 1
    }

    Log.d(TAG, "🔄 Setting up adaptive symbol swipe puzzle:")
    Log.d(TAG, "  Difficulty: $difficulty")
    Log.d(TAG, "  Puzzle ID: ${currentPuzzle.puzzleId}")
    Log.d(TAG, "  Current puzzle number: ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}")
    Log.d(TAG, "  Is first puzzle: $isFirstPuzzle")

    // Use the adaptive version which handles its own configuration and scoring
    AdaptiveSymbolSwipePuzzleScreen(
        initialDifficulty = difficulty,
        timer = "2:30", // This will be overridden by the adaptive system based on difficulty
        hearts = 3, // This will be overridden by the adaptive system
        level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
        onGameComplete = { success, score ->
            Log.d(TAG, "🔄 Adaptive symbol swipe game completed:")
            Log.d(TAG, "   Success: $success")
            Log.d(TAG, "   Final score: $score")

            // The adaptive system provides accurate success/failure state
            handlePuzzleCompletion(success, success, score)
        },
        onBack = onBack,
        isFirstPuzzle = isFirstPuzzle
    )
}

@Composable
fun ColorTextMatchingScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🎨 Showing AdaptiveColorTextMatchingPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            val puzzleData = if (currentPuzzle.question.startsWith("{")) {
                currentPuzzle.question
            } else {
                """
                {
                    "difficulty": "${currentPuzzle.difficulty ?: "Medium"}",
                    "totalQuestions": ${when((currentPuzzle.difficulty ?: "Medium").lowercase()) {
                    "easy" -> 10
                    "medium" -> 15
                    "hard" -> 20
                    else -> 15
                }},
                    "gameType": "adaptiveColorTextMatching"
                }
                """.trimIndent()
            }

            mapOf(
                "puzzleData" to puzzleData,
                "correctAnswer" to (currentPuzzle.answer.ifEmpty { "adaptive_generated" }),
                "difficulty" to (currentPuzzle.difficulty ?: "Medium"),
                "success" to true
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse adaptive color-text matching puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val puzzleDataStr = parsedData["puzzleData"] as String
        val correctAnswer = parsedData["correctAnswer"] as String
        val difficulty = parsedData["difficulty"] as String

        // Adaptive timer - will be managed internally by the adaptive screen
        val timerText = when (difficulty.lowercase()) {
            "easy" -> "4:00"    // Longer for adaptive easy mode
            "medium" -> "3:00"  // Standard adaptive time
            "hard" -> "2:00"    // Shorter for adaptive hard mode
            else -> "3:00"
        }

        // Use the adaptive version
        ColorTextMatchingPuzzleScreen(
            difficulty = difficulty,
            timer = timerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            puzzleData = puzzleDataStr,
            correctAnswer = correctAnswer,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Adaptive color-text matching answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after adaptive color-text matching completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid adaptive color-text matching puzzle data, moving to next puzzle")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading adaptive puzzle...")
            }
        }
    }
}
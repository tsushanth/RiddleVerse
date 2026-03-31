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
import org.json.JSONArray

@Composable
fun ContextSwitchScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    context: Context,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🧠 Showing AdaptiveContextSwitchPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            // For adaptive version, we don't need the old generator
            // The adaptive system will generate puzzles internally
            val puzzleData = if (currentPuzzle.question.startsWith("{")) {
                currentPuzzle.question
            } else {
                // Create minimal puzzle data for adaptive version
                """
                {
                    "difficulty": "${currentPuzzle.difficulty ?: "Medium"}",
                    "puzzleType": "adaptive_context_switch",
                    "gameType": "adaptiveContextSwitch"
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
            Log.e("PuzzleScreen", "❌ Failed to prepare adaptive context switch puzzle data", e)

            // Fallback with basic adaptive data
            try {
                val fallbackData = """
                {
                    "difficulty": "${currentPuzzle.difficulty ?: "Medium"}",
                    "puzzleType": "adaptive_context_switch",
                    "gameType": "adaptiveContextSwitch"
                }
                """.trimIndent()

                mapOf(
                    "puzzleData" to fallbackData,
                    "correctAnswer" to "adaptive_generated",
                    "difficulty" to (currentPuzzle.difficulty ?: "Medium"),
                    "success" to true
                )
            } catch (genException: Exception) {
                Log.e("PuzzleScreen", "❌ Failed to create fallback adaptive puzzle", genException)
                mapOf("success" to false)
            }
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val difficulty = parsedData["difficulty"] as String

        // Adaptive timer - longer times for complex cognitive tasks
        val timerText = when (difficulty.lowercase()) {
            "easy" -> "5:00"    // More time for adaptive easy mode
            "medium" -> "4:00"  // Standard adaptive time
            "hard" -> "3:30"    // Challenging but manageable
            else -> "4:00"
        }

        // Use the adaptive version - note the different parameter signature
        AdaptiveContextSwitchPuzzleScreen(
            initialDifficulty = difficulty,
            timer = timerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Adaptive context switch answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after adaptive context switch completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid adaptive context switch puzzle data, moving to next puzzle")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading adaptive context switch puzzle...")
            }
        }
    }
}

@Composable
fun MathCrosswordScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🧮 Showing MathCrosswordPuzzleScreen")

    // Extract initial difficulty from the puzzle, defaulting to "Medium"
    val initialDifficulty = currentPuzzle.difficulty ?: "Medium"

    // Format timer - crossword puzzles need more time
    val timer = when (initialDifficulty.lowercase()) {
        "easy" -> "5:00"
        "medium" -> "4:00"
        "hard" -> "3:30"
        else -> "4:00"
    }

    // Extract hearts/lives - could be made configurable
    val hearts = 3

    // Format level display
    val level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}"

    // Use the adaptive math crossword screen
    AdaptiveMathCrosswordPuzzleScreen(
        initialDifficulty = initialDifficulty,
        timer = timer,
        hearts = hearts,
        level = level,
        onGameComplete = { success, score ->
            Log.d("PuzzleScreen", "🧮 Adaptive math crossword game completed: success=$success, score=$score")
            handlePuzzleCompletion(success, true, score)
        },
        onBack = onBack
    )
}

@Composable
fun PinballDeflectorScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🎯 Showing AdaptivePinballDeflectorPuzzleScreen")

    // Extract initial difficulty from the puzzle, defaulting to "Medium"
    val initialDifficulty = currentPuzzle.difficulty ?: "Medium"

    // Calculate timer based on difficulty for consistency
    val timerText = when (initialDifficulty.lowercase()) {
        "easy" -> "2:00"
        "medium" -> "1:45"
        "hard" -> "1:30"
        else -> "1:45"
    }

    // Extract hearts/lives - could be made configurable
    val hearts = 3

    // Format level display
    val level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}"

    // Use the adaptive pinball deflector screen
    AdaptivePinballDeflectorPuzzleScreen(
        initialDifficulty = initialDifficulty,
        timer = timerText,
        hearts = hearts,
        level = level,
        onSubmitAnswer = { isCorrect ->
            Log.d("PuzzleScreen", "📝 Adaptive pinball deflector answer submitted: $isCorrect")
        },
        fetchNextPuzzle = { finalScore ->
            Log.d("PuzzleScreen", "🔄 Fetching next puzzle after adaptive feedback completion, score=$finalScore")
            handlePuzzleCompletion(true, true, finalScore)
        },
        onBack = onBack
    )
}

@Composable
fun UniqueObjectScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "UniqueObjectWrapper"

    // Extract difficulty from puzzle data
    val difficulty = remember(currentPuzzle) {
        currentPuzzle.difficulty?.takeIf { it.isNotEmpty() } ?: "Medium"
    }

    Log.d(TAG, "🎯 Setting up adaptive unique object puzzle:")
    Log.d(TAG, "  Difficulty: $difficulty")
    Log.d(TAG, "  Puzzle ID: ${currentPuzzle.puzzleId}")
    Log.d(TAG, "  Current puzzle number: ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}")

    // Use the adaptive version which handles its own puzzle generation and complexity management
    AdaptiveUniqueObjectPuzzleScreen(
        initialDifficulty = difficulty,
        timer = "2:00", // This will be overridden by the adaptive system based on difficulty
        hearts = 3, // This will be overridden by the adaptive system
        level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
        onSubmitAnswer = { isCorrect ->
            Log.d(TAG, "📝 Adaptive unique object answer submitted: $isCorrect")
            // The adaptive system handles answer evaluation internally
        },
        fetchNextPuzzle = { finalScore ->
            Log.d(TAG, "🔄 Fetching next puzzle after adaptive unique object completion:")
            Log.d(TAG, "   Final score: $finalScore")

            // Determine success based on score (adaptive system provides this)
            val success = finalScore > 0

            handlePuzzleCompletion(success, success, finalScore)
        },
        onBack = onBack
    )
}
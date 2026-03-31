package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson

@Composable
fun NumberSequenceScreenWrapper(
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

    // Use the adaptive number sequence screen
    AdaptiveNumberSequencePuzzleScreen(
        initialDifficulty = initialDifficulty,
        timer = timer,
        hearts = hearts,
        level = level,
        onGameComplete = { success, score ->
            Log.d("PuzzleScreen", "🔢 Adaptive number sequence game completed: success=$success, score=$score")
            handlePuzzleCompletion(success, true, score)
        },
        onBack = onBack
    )
}

@Composable
fun NumberSumScreenWrapper(
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

    // Use the adaptive number sum screen
    AdaptiveNumberSumPuzzleScreen(
        initialDifficulty = initialDifficulty,
        timer = timer,
        hearts = hearts,
        level = level,
        onGameComplete = { success, score ->
            Log.d("PuzzleScreen", "🧮 Adaptive number sum game completed: success=$success, score=$score")
            handlePuzzleCompletion(success, true, score)
        },
        onBack = onBack
    )
}

@Composable
fun MathExpressionScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    // Extract initial difficulty from the puzzle, defaulting to "Medium"
    val initialDifficulty = currentPuzzle.difficulty ?: "Medium"

    // Format timer - math expression puzzles need adequate time for calculations
    val timer = when (initialDifficulty.lowercase()) {
        "easy" -> "3:00"
        "medium" -> "2:30"
        "hard" -> "2:00"
        else -> "2:30"
    }

    // Extract hearts/lives - could be made configurable
    val hearts = 3

    // Format level display
    val level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}"

    // Use the adaptive math expression screen
    AdaptiveMathExpressionPuzzleScreen(
        initialDifficulty = initialDifficulty,
        timer = timer,
        hearts = hearts,
        level = level,
        onGameComplete = { success, score ->
            Log.d("PuzzleScreen", "🧮 Adaptive math expression game completed: success=$success, score=$score")
            handlePuzzleCompletion(success, true, score)
        },
        onBack = onBack
    )
}

@Composable
fun MemorySquaresScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    AdaptiveMemorySquaresPuzzleScreen(
        initialDifficulty = currentPuzzle.difficulty ?: "Easy",
        timer = "1:30",
        hearts = 3,
        level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
        onSubmitAnswer = { isCorrect ->
            Log.d("PuzzleScreen", "📝 Memory squares answer submitted: $isCorrect")
        },
        fetchNextPuzzle = { finalScore ->
            Log.d("PuzzleScreen", "🔄 Memory squares completed - using normal completion flow")
            handlePuzzleCompletion(true, true, finalScore)
        },
        onBack = onBack
    )
}

@Composable
fun DualCardScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    // Extract initial difficulty from the puzzle, defaulting to "Medium"
    val initialDifficulty = currentPuzzle.difficulty ?: "Medium"

    // Format timer - dual task puzzles need shorter time
    val timer = when (initialDifficulty.lowercase()) {
        "easy" -> "2:00"
        "medium" -> "1:30"
        "hard" -> "1:15"
        else -> "1:30"
    }

    // Extract hearts/lives - could be made configurable
    val hearts = 3

    // Format level display
    val level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}"

    // Use the adaptive dual card screen
    AdaptiveDualCardPuzzleScreen(
        initialDifficulty = initialDifficulty,
        timer = timer,
        hearts = hearts,
        level = level,
        onGameComplete = { success, score ->
            Log.d("PuzzleScreen", "🃏 Adaptive dual card game completed: success=$success, score=$score")
            handlePuzzleCompletion(success, true, score)
        },
        onBack = onBack
    )
}

@Composable
fun ImageVortexScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🖼️ Showing AdaptiveImageVortexPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            // For adaptive version, we simplify the data parsing since
            // the adaptive system manages complexity internally
            if (currentPuzzle.question.startsWith("{")) {
                val puzzleData = org.json.JSONObject(currentPuzzle.question)
                val targetCount = puzzleData.optInt("targetCount", 15) // Increased default for adaptive
                val timeLimit = puzzleData.optInt("timeLimit", 180) // Longer default for adaptive challenges

                mapOf(
                    "targetCount" to targetCount,
                    "timeLimit" to timeLimit,
                    "difficulty" to (currentPuzzle.difficulty ?: "Medium"),
                    "puzzleType" to "adaptive_image_vortex",
                    "success" to true
                )
            } else {
                // Default adaptive configuration
                mapOf(
                    "targetCount" to 15, // Adaptive default sequence length
                    "timeLimit" to 180, // 3 minutes for adaptive challenges
                    "difficulty" to (currentPuzzle.difficulty ?: "Medium"),
                    "puzzleType" to "adaptive_image_vortex",
                    "success" to true
                )
            }
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse adaptive image vortex data, using defaults", e)
            mapOf(
                "targetCount" to 15,
                "timeLimit" to 180,
                "difficulty" to (currentPuzzle.difficulty ?: "Medium"),
                "puzzleType" to "adaptive_image_vortex",
                "success" to true
            )
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val difficulty = parsedData["difficulty"] as String
        val timeLimit = parsedData["timeLimit"] as Int

        // Enhanced timer calculation for adaptive challenges
        val timerText = when {
            timeLimit > 0 -> "${timeLimit / 60}:${String.format("%02d", timeLimit % 60)}"
            difficulty.lowercase() == "easy" -> "4:00"     // More time for adaptive easy mode
            difficulty.lowercase() == "medium" -> "3:00"   // Standard adaptive time
            difficulty.lowercase() == "hard" -> "2:30"     // Challenging but fair for adaptive hard
            else -> "3:00"
        }

        // Use the adaptive version - note the simplified parameter signature
        AdaptiveImageVortexPuzzleScreen(
            difficulty = difficulty,
            timer = timerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "🖼️ Adaptive image vortex completed: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after adaptive image vortex completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid adaptive image vortex puzzle data, moving to next puzzle")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading adaptive visual attention puzzle...")
            }
        }
    }
}

@Composable
fun SwipeWordScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val wordQuestion = try {
        Gson().fromJson(currentPuzzle.question, WordConnotationQuestion::class.java)
    } catch (e: Exception) {
        Log.e("PuzzleScreen", "❌ Failed to parse WordConnotationQuestion", e)
        WordConnotationQuestion("", emptyList(), emptyList(), "")
    }

    SwipeWordScreen(
        questionData = wordQuestion,
        onPuzzleCompleted = { finalScore ->
            Log.d("PuzzleScreen", "🔄 SwipeWord puzzle completed")
            handlePuzzleCompletion(true, true, finalScore)
        },
        onBackToHome = onBack
    )
}

@Composable
fun JumbleInputScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    JumbleInputScreen(
        clueText = currentPuzzle.hint ?: "No hint available",
        targetWord = currentPuzzle.answer,
        shuffledLetters = currentPuzzle.question.toCharArray().toList(),
        score = viewModel.localScore,
        timerSeconds = 60,
        onSubmit = { answer, score ->
            Log.d("PuzzleScreen", "📝 Jumble answer submitted: $answer")
            val isCorrect = answer.equals(currentPuzzle.answer, ignoreCase = true)
            handlePuzzleCompletion(isCorrect, true, score)
        },
        onSkip = {
            Log.d("PuzzleScreen", "⏭️ Jumble puzzle skipped")
            handlePuzzleCompletion(false, false, 0)
        },
        onBackToHome = onBack
    )
}
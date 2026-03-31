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
fun FallingGameScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    context: Context,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    FallingGameScreen(
        puzzleId = currentPuzzle.puzzleId,
        question = currentPuzzle.question,
        difficulty = currentPuzzle.difficulty ?: "Easy",
        options = currentPuzzle.options ?: emptyList(),
        correctAnswer = currentPuzzle.answer,
        selectedOption = viewModel.selectedOption,
        timerSeconds = 60,
        questionNumber = if (viewModel.isCustomPuzzleFlow) {
            viewModel.currentQuestionNumber
        } else {
            viewModel.currentPuzzleNumber
        },
        totalQuestions = if (viewModel.isCustomPuzzleFlow) {
            viewModel.totalQuestions
        } else {
            viewModel.targetPuzzleCount
        },
        onOptionSelected = { viewModel.updateSelectedOption(it) },
        onContinue = { selectedAnswer ->
            Log.d("PuzzleScreen", "🔄 Falling game onContinue called")
            val isCorrect = selectedAnswer == currentPuzzle.answer
            handlePuzzleCompletion(isCorrect, true, 0)
        },
        onCorrectAnswer = { inTime ->
            Log.d("PuzzleScreen", "🎉 Falling game onCorrectAnswer - inTime: $inTime")
            viewModel.handlePuzzleCompletion(context, true, inTime)
        },
        onHint = { viewModel.toggleHintDialog() },
        onBack = onBack
    )
}

@Composable
fun PendulumChoiceScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    context: Context,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    PendulumChoiceScreen(
        puzzleId = currentPuzzle.puzzleId,
        question = currentPuzzle.question,
        difficulty = currentPuzzle.difficulty ?: "Easy",
        options = currentPuzzle.options ?: emptyList(),
        correctAnswer = currentPuzzle.answer,
        selectedOption = viewModel.selectedOption,
        timerSeconds = 90,
        questionNumber = if (viewModel.isCustomPuzzleFlow) {
            viewModel.currentQuestionNumber
        } else {
            viewModel.currentPuzzleNumber
        },
        totalQuestions = if (viewModel.isCustomPuzzleFlow) {
            viewModel.totalQuestions
        } else {
            viewModel.targetPuzzleCount
        },
        onOptionSelected = { viewModel.updateSelectedOption(it) },
        onContinue = { selectedAnswer ->
            Log.d("PuzzleScreen", "🔄 Pendulum choice onContinue called")
            val isCorrect = selectedAnswer == currentPuzzle.answer
            viewModel.handlePuzzleCompletion(context, isCorrect, true)
        },
        onCorrectAnswer = { inTime ->
            Log.d("PuzzleScreen", "🎉 Pendulum choice onCorrectAnswer - inTime: $inTime")
            viewModel.handlePuzzleCompletion(context, true, inTime)
        },
        onHint = { viewModel.toggleHintDialog() },
        onBack = onBack
    )
}

@Composable
fun MatchScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    context: Context,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    MatchScreen(
        puzzleId = currentPuzzle.puzzleId,
        question = currentPuzzle.question,
        difficulty = currentPuzzle.difficulty ?: "Easy",
        options = currentPuzzle.options ?: emptyList(),
        correctAnswer = currentPuzzle.answer,
        selectedOption = viewModel.selectedOption,
        timerSeconds = 90,
        questionNumber = if (viewModel.isCustomPuzzleFlow) {
            viewModel.currentQuestionNumber
        } else {
            viewModel.currentPuzzleNumber
        },
        totalQuestions = if (viewModel.isCustomPuzzleFlow) {
            viewModel.totalQuestions
        } else {
            viewModel.targetPuzzleCount
        },
        onOptionSelected = { viewModel.updateSelectedOption(it) },
        onContinue = { selectedAnswer ->
            Log.d("PuzzleScreen", "🔄 Match screen onContinue called")

            val nextIntent = viewModel.getNextPuzzleIntent(context)
            if (nextIntent != null) {
                context.startActivity(nextIntent)
                (context as? PuzzleActivity)?.finish()
            } else {
                onComplete()
            }
        },
        onCorrectAnswer = { inTime ->
            Log.d("PuzzleScreen", "🎉 Match screen onCorrectAnswer - inTime: $inTime")
            viewModel.handlePuzzleCompletion(context, true, inTime)
        },
        onHint = { viewModel.toggleHintDialog() },
        onBack = onBack
    )
}

@Composable
fun TriangleDotMemoryScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "TriangleDotWrapper"

    // Extract difficulty from puzzle data
    val difficulty = remember(currentPuzzle) {
        currentPuzzle.difficulty?.takeIf { it.isNotEmpty() } ?: "Medium"
    }

    Log.d(TAG, "🎯 Setting up adaptive triangle dot memory puzzle:")
    Log.d(TAG, "  Difficulty: $difficulty")
    Log.d(TAG, "  Puzzle ID: ${currentPuzzle.puzzleId}")
    Log.d(TAG, "  Current puzzle number: ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}")

    // Use the adaptive version which handles its own puzzle generation and scoring
    AdaptiveTriangleDotMemoryPuzzleScreen(
        initialDifficulty = difficulty,
        timer = "2:00", // This will be overridden by the adaptive system
        hearts = 3, // This will be overridden by the adaptive system
        level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
        onSubmitAnswer = { isCorrect ->
            Log.d(TAG, "📝 Adaptive triangle dot memory answer submitted: $isCorrect")
            // The adaptive system handles the answer evaluation internally
        },
        fetchNextPuzzle = { finalScore ->
            Log.d(TAG, "🔄 Fetching next puzzle after adaptive triangle dot memory completion")
            Log.d(TAG, "   Final score: $finalScore")

            // Determine success based on score (adaptive system provides this)
            val success = finalScore > 0

            handlePuzzleCompletion(success, success, finalScore)
        },
        onBack = onBack
    )
}

// Helper function for subscription screen
private fun generateSubscriptionOptionsFromCorrect(correctAnswer: Double): List<Int> {
    val options = mutableListOf<Int>()

    // Always include the correct answer first
    options.add(correctAnswer.toInt())

    // Generate 3 variations around the correct answer
    val variations = listOf(
        (correctAnswer * 0.8).toInt(),  // 20% less
        (correctAnswer * 1.2).toInt(),  // 20% more
        (correctAnswer * 0.6).toInt()   // 40% less
    )

    // Add variations that are different from correct answer
    variations.forEach { variation ->
        if (variation != correctAnswer.toInt() && !options.contains(variation) && variation > 0) {
            options.add(variation)
        }
    }

    // Return exactly 4 options, shuffled
    return options.take(4).shuffled()
}
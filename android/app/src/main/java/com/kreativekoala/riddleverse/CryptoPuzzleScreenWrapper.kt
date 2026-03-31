package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

@Composable
fun CryptoWordScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "AdaptiveCryptoWrapper"
    Log.d(TAG, "🔐 Generating Adaptive Crypto puzzle client-side")

    // Initialize the preprocessor
    val preprocessor = remember { CryptoPuzzlePreprocessor() }

    // State for the generated puzzle data
    var cryptoPuzzleData by remember { mutableStateOf<CryptoPuzzleData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var puzzleAnalysis by remember { mutableStateOf<PuzzleAnalysis?>(null) }

    // Create unique key for state reset
    val puzzleKey = currentPuzzle.puzzleId

    // Generate puzzle data on first composition
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            val difficulty = currentPuzzle.difficulty ?: "Medium"

            // Use actual puzzle data from server
            val originalQuote = currentPuzzle.question ?: "DEFAULT QUOTE"
            val author = currentPuzzle.answer ?: "Unknown"
            val hintWithAuthor = currentPuzzle.hint ?: "Quote by \"Unknown\""

            // Preprocess the quote to create challenging puzzle
            val config = preprocessor.preprocessQuote(
                originalText = originalQuote,
                difficulty = difficulty,
                targetWord = null // We don't need target word for hints
            )

            // Analyze the puzzle
            val analysis = preprocessor.analyzePuzzle(config)
            puzzleAnalysis = analysis

            // Convert to your existing CryptoPuzzleData format
            cryptoPuzzleData = CryptoPuzzleData(
                originalText = config.originalText,
                numberMapping = config.numberMapping,
                revealedLetters = config.revealedLetters,
                targetWord = hintWithAuthor // Store the full hint format
            )

            // Log adaptive puzzle details
            Log.d(TAG, "✅ Generated adaptive crypto puzzle from server data")
            Log.d(TAG, "📊 Puzzle Analysis:")
            Log.d(TAG, "  Original quote: ${currentPuzzle.question}")
            Log.d(TAG, "  Author: ${currentPuzzle.answer}")
            Log.d(TAG, "  Hint: ${currentPuzzle.hint}")
            Log.d(TAG, "  Difficulty: $difficulty")
            Log.d(TAG, "  Hidden: ${analysis.hiddenLetters}/${analysis.uniqueLetters} letters (${analysis.hiddenPercentage}%)")
            Log.d(TAG, "  Revealed hints: ${analysis.revealedHints}")
            Log.d(TAG, "  Top frequent: ${analysis.topFrequentLetters.joinToString(", ")}")
            Log.d(TAG, "  Hidden letters: ${config.hiddenLetters.sorted()}")
            Log.d(TAG, "  Revealed letters: ${config.revealedLetters.sorted()}")
            Log.d(TAG, "  Number mapping: ${config.numberMapping}")
            Log.d(TAG, "🎯 Ready for adaptive difficulty system")

            isLoading = false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating adaptive crypto puzzle: ${e.message}")
            handlePuzzleCompletion(false, false, 0)
        }
    }

    // Show loading state while generating
    if (isLoading || cryptoPuzzleData == null) {
        LoadingScreen(message = stringResource(R.string.loading_puzzle))
        return
    }

    // Use key() to force state reset and call the ADAPTIVE screen
    key(puzzleKey) {
        Log.d(TAG, "🔄 Recomposing AdaptiveCryptoWordScreen with key: $puzzleKey")

        // Call the ADAPTIVE crypto screen instead of the original
        AdaptiveCryptoWordScreenWithTimer(
            initialPuzzleData = cryptoPuzzleData!!,
            initialDifficulty = currentPuzzle.difficulty ?: "Medium",
            timer = when (currentPuzzle.difficulty?.lowercase()) {
                "easy" -> "3:00"
                "medium" -> "2:30"
                "hard" -> "2:00"
                "expert" -> "1:30"
                else -> "2:30"
            },
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            onBack = onBack,
            onComplete = { isCorrect, finalScore ->
                Log.d(TAG, "🎊 Adaptive crypto puzzle completed - Success: $isCorrect, Score: $finalScore")
                handlePuzzleCompletion(true, isCorrect, finalScore)
            }
        )
    }
}
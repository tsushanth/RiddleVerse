package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Client-side Conversion Screen Wrapper
 * Uses local generator instead of server data
 */
@Composable
fun ClientSideConversionScreenWrapper(
    difficulty: String = "medium",
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🎮 Showing Client-side ConversionPuzzleScreen")

    val generator = remember { ConversionPuzzleGenerator() }
    var puzzleData by remember { mutableStateOf<ConversionPuzzleData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    // Generate puzzle on first composition
    LaunchedEffect(Unit) {
        try {
            val generatedPuzzle = generator.generateConversionPuzzle(difficulty)
            if (generatedPuzzle != null) {
                puzzleData = generatedPuzzle
                isLoading = false
                Log.d("PuzzleScreen", "✅ Generated conversion puzzle: ${generatedPuzzle.conversionType.displayName}")
            } else {
                Log.e("PuzzleScreen", "❌ Failed to generate conversion puzzle")
                hasError = true
                isLoading = false
            }
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Error generating conversion puzzle", e)
            hasError = true
            isLoading = false
        }
    }

    when {
        isLoading -> {
            LoadingState()
        }

        hasError -> {
            ErrorState(onRetry = {
                isLoading = true
                hasError = false
                // Trigger regeneration by changing a state that LaunchedEffect depends on
            }, onSkip = {
                handlePuzzleCompletion(false, false, 0)
            })
        }

        puzzleData != null -> {
            ConversionPuzzleScreen(
                difficulty = difficulty,
                timer = getTimerForDifficulty(difficulty),
                hearts = 3,
                level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
                leftBlock = puzzleData!!.leftBlock,
                rightBlock = puzzleData!!.rightBlock,
                correctAnswer = puzzleData!!.correctAnswer,
                conversionType = puzzleData!!.conversionType,
                onSubmitAnswer = { answer ->
                    Log.d("PuzzleScreen", "📝 Conversion answer submitted: $answer")
                    val isCorrect = answer == puzzleData!!.correctAnswer
                    Log.d("PuzzleScreen", "🎯 Answer result: correct=$isCorrect (expected: ${puzzleData!!.correctAnswer})")

                    // Log additional debug info
                    Log.d("PuzzleScreen", "🔍 Conversion details:")
                    Log.d("PuzzleScreen", "  Left: ${puzzleData!!.leftBlock.label} (${puzzleData!!.leftBlock.value} ${puzzleData!!.leftBlock.unit})")
                    Log.d("PuzzleScreen", "  Right: ${puzzleData!!.rightBlock.label} (${puzzleData!!.rightBlock.value} ${puzzleData!!.rightBlock.unit})")
                    Log.d("PuzzleScreen", "  Type: ${puzzleData!!.conversionType.displayName}")
                    Log.d("PuzzleScreen", "  User answer: $answer")
                    Log.d("PuzzleScreen", "  Correct answer: ${puzzleData!!.correctAnswer}")
                },
                fetchNextPuzzle = { finalScore ->
                    Log.d("PuzzleScreen", "🔄 Fetching next puzzle after feedback completion (Score: $finalScore)")
                    handlePuzzleCompletion(true, true, finalScore)
                },
                onBack = onBack
            )
        }

        else -> {
            ErrorState(onRetry = {
                isLoading = true
                hasError = false
            }, onSkip = {
                handlePuzzleCompletion(false, false, 0)
            })
        }
    }
}

/**
 * Get appropriate timer based on difficulty
 */
private fun getTimerForDifficulty(difficulty: String): String {
    return when (difficulty.lowercase()) {
        "easy" -> "2:00"
        "medium" -> "1:45"
        "hard" -> "1:30"
        else -> "1:45"
    }
}

/**
 * Loading state component
 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.loading_puzzle),
                color = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}

/**
 * Error state component
 */
@Composable
private fun ErrorState(
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⚠️",
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge
            )

            Text(
                text = stringResource(R.string.error_loading_puzzle),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.ui.graphics.Color.White
            )

            Text(
                text = "There was an issue creating the conversion puzzle. You can try again or skip to the next puzzle.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text(stringResource(R.string.try_again))
                }

                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    )
                ) {
                    Text(stringResource(R.string.skip_puzzle))
                }
            }
        }
    }
}

/**
 * Enhanced Conversion Generator with Validation
 * This version includes additional validation and error handling
 */
class ValidatedConversionPuzzleGenerator : ConversionPuzzleGenerator() {

    companion object {
        private const val TAG = "ValidatedConversionGen"
        private const val MAX_GENERATION_ATTEMPTS = 5
    }

    /**
     * Generate puzzle with validation and retry logic
     */
    fun generateValidatedPuzzle(difficulty: String = "medium", maxAttempts: Int = MAX_GENERATION_ATTEMPTS): ConversionPuzzleData? {
        repeat(maxAttempts) { attempt ->
            try {
                val puzzle = generateConversionPuzzle(difficulty)

                if (puzzle != null && validatePuzzle(puzzle)) {
                    Log.d(TAG, "✅ Successfully generated and validated puzzle on attempt ${attempt + 1}")
                    return puzzle
                } else {
                    Log.w(TAG, "⚠️ Generated puzzle failed validation on attempt ${attempt + 1}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Generation attempt ${attempt + 1} failed: ${e.message}")
            }
        }

        Log.e(TAG, "❌ Failed to generate valid puzzle after $maxAttempts attempts")
        return null
    }

    /**
     * Validate generated puzzle for correctness
     */
    private fun validatePuzzle(puzzle: ConversionPuzzleData): Boolean {
        try {
            val converter = UniversalConverter()

            // Test the comparison logic
            val comparisonResult = converter.compareValues(
                puzzle.leftBlock.value,
                puzzle.leftBlock.unit,
                puzzle.rightBlock.value,
                puzzle.rightBlock.unit
            )

            val expectedAnswer = when {
                abs(comparisonResult) < 0.001 -> ComparisonResult.EQUAL
                comparisonResult > 0 -> ComparisonResult.LEFT_HEAVIER
                else -> ComparisonResult.RIGHT_HEAVIER
            }

            val isValid = expectedAnswer == puzzle.correctAnswer

            if (!isValid) {
                Log.w(TAG, "⚠️ Validation failed:")
                Log.w(TAG, "  Expected: $expectedAnswer")
                Log.w(TAG, "  Generated: ${puzzle.correctAnswer}")
                Log.w(TAG, "  Comparison result: $comparisonResult")
                Log.w(TAG, "  Left: ${puzzle.leftBlock.value} ${puzzle.leftBlock.unit}")
                Log.w(TAG, "  Right: ${puzzle.rightBlock.value} ${puzzle.rightBlock.unit}")
            }

            return isValid

        } catch (e: Exception) {
            Log.e(TAG, "❌ Validation error: ${e.message}")
            return false
        }
    }
}

/**
 * Advanced wrapper that uses the validated generator
 */
@Composable
fun AdvancedConversionScreenWrapper(
    difficulty: String = "medium",
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val generator = remember { ValidatedConversionPuzzleGenerator() }
    var puzzleData by remember { mutableStateOf<ConversionPuzzleData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }

    // Generate puzzle with retry logic
    LaunchedEffect(retryCount) {
        isLoading = true
        hasError = false

        try {
            val generatedPuzzle = generator.generateValidatedPuzzle(difficulty)
            if (generatedPuzzle != null) {
                puzzleData = generatedPuzzle
                isLoading = false
                Log.d("PuzzleScreen", "✅ Generated validated conversion puzzle: ${generatedPuzzle.conversionType.displayName}")
            } else {
                Log.e("PuzzleScreen", "❌ Failed to generate validated conversion puzzle")
                hasError = true
                isLoading = false
            }
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Error generating validated conversion puzzle", e)
            hasError = true
            isLoading = false
        }
    }

    when {
        isLoading -> LoadingState()

        hasError -> ErrorState(
            onRetry = { retryCount++ },
            onSkip = { handlePuzzleCompletion(false, false, 0) }
        )

        puzzleData != null -> ConversionPuzzleScreen(
            difficulty = difficulty,
            timer = getTimerForDifficulty(difficulty),
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            leftBlock = puzzleData!!.leftBlock,
            rightBlock = puzzleData!!.rightBlock,
            correctAnswer = puzzleData!!.correctAnswer,
            conversionType = puzzleData!!.conversionType,
            onSubmitAnswer = { answer ->
                val isCorrect = answer == puzzleData!!.correctAnswer
                Log.d("PuzzleScreen", "📝 Validated conversion answer: $answer (correct: $isCorrect)")
            },
            fetchNextPuzzle = { finalScore ->
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )

        else -> ErrorState(
            onRetry = { retryCount++ },
            onSkip = { handlePuzzleCompletion(false, false, 0) }
        )
    }
}
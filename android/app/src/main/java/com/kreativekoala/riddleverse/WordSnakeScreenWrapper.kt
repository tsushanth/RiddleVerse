package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun WordSnakeScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "WordSnakeWrapper"
    Log.d(TAG, "🐍 Generating WordSnake puzzle client-side")

    // State for the puzzle data
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Create unique key for state reset
    val puzzleKey = currentPuzzle.puzzleId

    // Parse puzzle data on first composition
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            val difficulty = currentPuzzle.difficulty ?: "Medium"

            // Log puzzle details
            Log.d(TAG, "✅ Using WordSnake puzzle from server")
            Log.d(TAG, "📊 Puzzle Details:")
            Log.d(TAG, "  Question (JSON): ${currentPuzzle.question}")
            Log.d(TAG, "  Answer: ${currentPuzzle.answer}")
            Log.d(TAG, "  Hint: ${currentPuzzle.hint}")
            Log.d(TAG, "  Difficulty: $difficulty")

            isLoading = false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing word snake puzzle: ${e.message}")
            errorMessage = "Failed to load puzzle: ${e.message}"
            isLoading = false
        }
    }

    // Show loading state
    if (isLoading) {
        WSLoadingScreen(message = "Loading Word Snake puzzle...")
        return
    }

    // Show error state
    if (errorMessage != null) {
        ErrorScreen(
            message = errorMessage!!,
            onRetry = {
                errorMessage = null
                isLoading = true
            },
            onBack = onBack
        )
        return
    }

    // Use key() to force state reset
    key(puzzleKey) {
        Log.d(TAG, "🔄 Recomposing WordSnakeScreen with key: $puzzleKey")

        WordSnakeScreen(
            difficulty = currentPuzzle.difficulty ?: "Medium",
            timer = when (currentPuzzle.difficulty?.lowercase()) {
                "easy" -> "5:00"
                "medium" -> "4:00"
                "hard" -> "3:00"
                "expert" -> "2:30"
                else -> "4:00"
            },
            puzzleData = currentPuzzle.question ?: "{}",
            onSubmitAnswer = { isCorrect ->
                Log.d(TAG, "🎊 WordSnake puzzle completed - Success: $isCorrect")
                // The completion is handled by fetchNextPuzzle callback
            },
            fetchNextPuzzle = { finalScore ->
                Log.d(TAG, "🎯 WordSnake completed with score: $finalScore")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    }
}

@Composable
fun WSLoadingScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(message)
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(message)
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.back))
                }
            }
        }
    }
}
// PuzzleWrapperUtils.kt - Common utilities for puzzle screen wrappers
package com.kreativekoala.riddleverse

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kreativekoala.riddleverse.util.DebugLogger

/**
 * Common data holder for puzzle wrapper state
 */
data class PuzzleWrapperConfig(
    val difficulty: String,
    val timerText: String,
    val levelDisplay: String,
    val hearts: Int = 3
)

/**
 * Extension function to get level display string from ViewModel
 */
fun PuzzleViewModel.getLevelDisplay(): String {
    return if (isCustomPuzzleFlow) {
        "$currentQuestionNumber/$totalQuestions"
    } else {
        "$currentPuzzleNumber/$targetPuzzleCount"
    }
}

/**
 * Extension function to get question/puzzle number
 */
fun PuzzleViewModel.getCurrentNumber(): Int {
    return if (isCustomPuzzleFlow) currentQuestionNumber else currentPuzzleNumber
}

/**
 * Extension function to get total questions/puzzles
 */
fun PuzzleViewModel.getTotalCount(): Int {
    return if (isCustomPuzzleFlow) totalQuestions else targetPuzzleCount
}

/**
 * Get timer text based on difficulty level
 */
fun getTimerForDifficulty(difficulty: String, puzzleType: String = ""): String {
    return when (difficulty.lowercase()) {
        "easy", "beginner", "tutorial" -> when (puzzleType.lowercase()) {
            "geography", "geographycountry", "geographycity" -> "4:00"
            "crossword", "mathcrossword" -> "8:00"
            else -> "4:00"
        }
        "medium", "medium-", "medium+" -> when (puzzleType.lowercase()) {
            "geography", "geographycountry", "geographycity" -> "6:00"
            "crossword", "mathcrossword" -> "10:00"
            else -> "3:00"
        }
        "hard", "hard-", "hard+" -> when (puzzleType.lowercase()) {
            "geography", "geographycountry", "geographycity" -> "7:30"
            "crossword", "mathcrossword" -> "12:00"
            else -> "2:00"
        }
        "expert", "expert-", "expert+", "master", "grandmaster" -> "1:30"
        else -> "3:00"
    }
}

/**
 * Create wrapper config from puzzle and viewModel
 */
fun createWrapperConfig(
    puzzle: Puzzle,
    viewModel: PuzzleViewModel,
    puzzleType: String = ""
): PuzzleWrapperConfig {
    val difficulty = puzzle.difficulty ?: "Medium"
    return PuzzleWrapperConfig(
        difficulty = difficulty,
        timerText = getTimerForDifficulty(difficulty, puzzleType),
        levelDisplay = viewModel.getLevelDisplay(),
        hearts = 3
    )
}

/**
 * Standard loading state composable for wrappers
 */
@Composable
fun PuzzleLoadingState(message: String? = null) {
    val displayMessage = message ?: stringResource(R.string.loading_next_puzzle)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(displayMessage)
        }
    }
}

/**
 * Handle invalid puzzle data - show loading and trigger completion
 */
@Composable
fun HandleInvalidPuzzleData(
    puzzleType: String,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    LaunchedEffect(Unit) {
        DebugLogger.e("PuzzleWrapper", "Invalid puzzle data for $puzzleType, moving to next puzzle")
        handlePuzzleCompletion(false, false, 0)
    }
    PuzzleLoadingState()
}

/**
 * Parse JSON puzzle data safely
 */
fun parsePuzzleData(
    puzzle: Puzzle,
    defaultGameType: String,
    additionalDefaults: Map<String, Any> = emptyMap()
): Result<Map<String, Any>> {
    return try {
        val difficulty = puzzle.difficulty ?: "Medium"

        val puzzleData = if (puzzle.question.startsWith("{")) {
            puzzle.question
        } else {
            val maxQuestions = when (difficulty.lowercase()) {
                "easy" -> 8
                "medium" -> 12
                "hard" -> 15
                else -> 10
            }

            buildString {
                append("{")
                append("\"difficulty\": \"$difficulty\",")
                append("\"maxQuestions\": $maxQuestions,")
                append("\"gameType\": \"$defaultGameType\"")
                additionalDefaults.forEach { (key, value) ->
                    append(",")
                    when (value) {
                        is String -> append("\"$key\": \"$value\"")
                        is Number -> append("\"$key\": $value")
                        is Boolean -> append("\"$key\": $value")
                        else -> append("\"$key\": \"$value\"")
                    }
                }
                append("}")
            }
        }

        Result.success(mapOf(
            "puzzleData" to puzzleData,
            "correctAnswer" to puzzle.answer.ifEmpty { "generated" },
            "difficulty" to difficulty
        ))
    } catch (e: Exception) {
        DebugLogger.e("PuzzleWrapper", "Failed to parse puzzle data", e)
        Result.failure(e)
    }
}

/**
 * Standard multiple choice wrapper configuration
 */
@Composable
fun rememberMultipleChoiceConfig(
    puzzle: Puzzle,
    viewModel: PuzzleViewModel
): Map<String, Any>? {
    return remember(puzzle) {
        try {
            mapOf(
                "question" to puzzle.question,
                "correctAnswer" to puzzle.answer,
                "options" to (puzzle.options ?: emptyList<String>()),
                "success" to (puzzle.question.isNotEmpty() && !puzzle.options.isNullOrEmpty())
            )
        } catch (e: Exception) {
            DebugLogger.e("PuzzleWrapper", "Failed to parse multiple choice data", e)
            null
        }
    }
}

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
fun SentenceTransitionsScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "📝 Showing Sentence Transitions Puzzle")

    // Parse the sentence transitions data
    val parsedData = remember(currentPuzzle) {
        try {
            val question = currentPuzzle.question
            val correctAnswer = currentPuzzle.answer
            val options = currentPuzzle.options ?: emptyList()

            Log.d("PuzzleScreen", "📊 Parsed sentence transitions puzzle:")
            Log.d("PuzzleScreen", "   Question: $question")
            Log.d("PuzzleScreen", "   Correct answer: $correctAnswer")
            Log.d("PuzzleScreen", "   Options: $options")

            mapOf(
                "question" to question,
                "correctAnswer" to correctAnswer,
                "options" to options,
                "success" to (question.isNotEmpty() && options.isNotEmpty())
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse sentence transitions data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val question = parsedData["question"] as String
        val correctAnswer = parsedData["correctAnswer"] as String
        @Suppress("UNCHECKED_CAST")
        val options = parsedData["options"] as List<String>

        MultipleChoicePuzzleScreen(
            puzzleId = currentPuzzle.puzzleId,
            question = question,
            difficulty = currentPuzzle.difficulty ?: "Medium",
            options = options,
            selectedOption = viewModel.selectedOption,
            correctAnswer = correctAnswer,
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
                Log.d("PuzzleScreen", "🔄 Sentence Transitions onContinue called")
                Log.d("PuzzleScreen", "📝 Selected: $selectedAnswer, Correct: $correctAnswer")

                val isCorrect = selectedAnswer == correctAnswer
                handlePuzzleCompletion(isCorrect, true, 0)
            },
            onCorrectAnswer = { inTime ->
                Log.d("PuzzleScreen", "🎉 onCorrectAnswer called - inTime: $inTime")
            },
            onHint = { viewModel.toggleHintDialog() },
            onBack = onBack
        )
    } else {
        // Show error state or move to next puzzle
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid sentence transitions puzzle data, moving to next puzzle")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading next puzzle...")
            }
        }
    }
}

@Composable
fun GeographyCountryScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🌍 Showing GeographyCountryDragDropPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            val puzzleData = if (currentPuzzle.question.startsWith("{")) {
                currentPuzzle.question
            } else {
                """
                {
                    "difficulty": "${currentPuzzle.difficulty ?: "Medium"}",
                    "maxQuestions": ${when((currentPuzzle.difficulty ?: "Medium").lowercase()) {
                    "easy" -> 8
                    "medium" -> 12
                    "hard" -> 15
                    else -> 10
                }},
                    "gameType": "geographyCountryDragDrop",
                    "countryDatabase": "world_major_countries_66"
                }
                """.trimIndent()
            }

            mapOf(
                "puzzleData" to puzzleData,
                "correctAnswer" to (currentPuzzle.answer.ifEmpty { "generated" }),
                "difficulty" to (currentPuzzle.difficulty ?: "Medium"),
                "success" to true
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse geography country drag drop puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val puzzleDataStr = parsedData["puzzleData"] as String
        val correctAnswer = parsedData["correctAnswer"] as String
        val difficulty = parsedData["difficulty"] as String

        val timerText = when (difficulty.lowercase()) {
            "easy" -> "4:00"
            "medium" -> "6:00"
            "hard" -> "7:30"
            else -> "5:00"
        }

        GeographyCountryDragDropPuzzleScreen(
            difficulty = difficulty,
            timer = timerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            puzzleData = puzzleDataStr,
            correctAnswer = correctAnswer,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Geography country drag drop answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after geography country completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid geography country drag drop puzzle data, moving to next puzzle")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading next puzzle...")
            }
        }
    }
}

@Composable
fun GeographyCityScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🌍 Showing GeographyDragDropPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            val puzzleData = if (currentPuzzle.question.startsWith("{")) {
                currentPuzzle.question
            } else {
                """
                {
                    "difficulty": "${currentPuzzle.difficulty ?: "Medium"}",
                    "maxQuestions": ${when((currentPuzzle.difficulty ?: "Medium").lowercase()) {
                    "easy" -> 8
                    "medium" -> 12
                    "hard" -> 15
                    else -> 10
                }},
                    "gameType": "geographyDragDrop",
                    "cityDatabase": "world_major_cities_50"
                }
                """.trimIndent()
            }

            mapOf(
                "puzzleData" to puzzleData,
                "correctAnswer" to (currentPuzzle.answer.ifEmpty { "generated" }),
                "difficulty" to (currentPuzzle.difficulty ?: "Medium"),
                "success" to true
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse geography drag drop puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val puzzleDataStr = parsedData["puzzleData"] as String
        val correctAnswer = parsedData["correctAnswer"] as String
        val difficulty = parsedData["difficulty"] as String

        val timerText = when (difficulty.lowercase()) {
            "easy" -> "4:00"
            "medium" -> "6:00"
            "hard" -> "7:30"
            else -> "5:00"
        }

        GeographyDragDropPuzzleScreen(
            difficulty = difficulty,
            timer = timerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            puzzleData = puzzleDataStr,
            correctAnswer = correctAnswer,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Geography cities drag drop answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after geography cities completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid geography drag drop puzzle data, moving to next puzzle")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading next puzzle...")
            }
        }
    }
}

@Composable
fun MultipleChoiceScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    context: Context,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🎮 Showing MultipleChoicePuzzleScreen")

    MultipleChoicePuzzleScreen(
        puzzleId = currentPuzzle.puzzleId,
        question = currentPuzzle.question,
        difficulty = currentPuzzle.difficulty ?: "Easy",
        options = currentPuzzle.options ?: emptyList(),
        selectedOption = viewModel.selectedOption,
        correctAnswer = currentPuzzle.answer,
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
            Log.d("PuzzleScreen", "🔄 MultipleChoice onContinue called")
            Log.d("PuzzleScreen", "📝 Selected: $selectedAnswer, Correct: ${currentPuzzle.answer}")

            val isCorrect = selectedAnswer == currentPuzzle.answer
            handlePuzzleCompletion(isCorrect, true, 0)
        },
        onCorrectAnswer = { inTime ->
            Log.d("PuzzleScreen", "🎉 onCorrectAnswer called - inTime: $inTime")
            viewModel.handlePuzzleCompletion(context, true, inTime)
        },
        onHint = { viewModel.toggleHintDialog() },
        onBack = onBack,
        puzzleType = "multipleChoice",
        useEnhancedTheme = false
    )
}
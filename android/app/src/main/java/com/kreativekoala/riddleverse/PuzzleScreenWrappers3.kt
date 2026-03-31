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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun CrosswordScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🎮 Showing CrosswordPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            val crosswordData = JSONObject(currentPuzzle.question)

            val matrix = crosswordData.getJSONArray("matrix")
            val wordsArray = crosswordData.getJSONArray("words")
            val width = crosswordData.getInt("width")
            val height = crosswordData.getInt("height")

            val words = mutableListOf<String>()
            for (i in 0 until wordsArray.length()) {
                val wordObj = wordsArray.getJSONObject(i)
                words.add(wordObj.getString("word"))
            }

            val difficulty = currentPuzzle.difficulty ?: "Easy"
            val timerText = when (difficulty.lowercase()) {
                "easy" -> "10:00"
                "medium" -> "8:00"
                "hard" -> "6:00"
                else -> "8:00"
            }

            mapOf(
                "gridWidth" to width,
                "gridHeight" to height,
                "words" to words,
                "difficulty" to difficulty,
                "timer" to timerText,
                "crosswordData" to currentPuzzle.question,
                "success" to true
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse crossword puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val difficulty = parsedData["difficulty"] as String
        val timerText = parsedData["timer"] as String
        val crosswordData = parsedData["crosswordData"] as String

        CrosswordPuzzleScreen(
            difficulty = difficulty,
            timer = timerText,
            puzzleData = crosswordData,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Crossword answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after feedback completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid crossword puzzle data, moving to next puzzle")
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
fun WordPrefixScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🎮 Showing WordPrefixPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            // The prefix is in the question field (simple string)
            val prefix = currentPuzzle.question

            // The puzzle data is in the answer field (JSON string)
            val answerJsonString = currentPuzzle.answer

            val answerData = JSONObject(answerJsonString)
            val timeLimit = answerData.getInt("timeLimit")
            val allWordsArray = answerData.getJSONArray("allWords")
            val targetsJson = answerData.getJSONObject("targets")
            val metadataJson = answerData.getJSONObject("metadata")
            val totalWords = metadataJson.getInt("totalWords")

            val timerText = "${timeLimit / 60}:${String.format("%02d", timeLimit % 60)}"

            mapOf(
                "prefix" to prefix,
                "answerJson" to answerJsonString,
                "totalWords" to totalWords,
                "timeLimit" to timeLimit,
                "targets" to mapOf(
                    "bronze" to targetsJson.getInt("bronze"),
                    "silver" to targetsJson.getInt("silver"),
                    "gold" to targetsJson.getInt("gold")
                ),
                "timer" to timerText,
                "success" to true
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse word prefix puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val prefix = parsedData["prefix"] as String
        val answerJson = parsedData["answerJson"] as String
        val timerText = parsedData["timer"] as String

        WordPrefixPuzzleScreen(
            difficulty = currentPuzzle.difficulty ?: "Easy",
            timer = timerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            puzzleData = answerJson,
            prefix = prefix,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Word prefix game result: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after word prefix completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid word prefix puzzle data, moving to next puzzle")
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
fun MemoryPreviousPairScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🧠 Showing MemoryPreviousPairPuzzleScreen")

    val difficulty = currentPuzzle.difficulty ?: "Medium"

    // Calculate timer based on difficulty for memory challenges
    val timerText = when (difficulty.lowercase()) {
        "easy" -> "4:00"     // More time for easy mode
        "medium" -> "3:30"   // Standard time
        "hard" -> "3:00"     // Challenging but manageable for hard
        else -> "3:30"
    }

    // Parse puzzle data
    val parsedData = remember(currentPuzzle) {
        try {
            if (currentPuzzle.question.startsWith("{")) {
                val questionJson = JSONObject(currentPuzzle.question)
                val totalScreens = questionJson.optInt("totalScreens", 8) // Default sequence length

                // Calculate custom timer based on screens if provided
                val customTimerSeconds = when (difficulty.lowercase()) {
                    "easy" -> totalScreens * 8
                    "medium" -> totalScreens * 6
                    "hard" -> totalScreens * 4
                    else -> totalScreens * 6
                }
                val customTimerText = "${customTimerSeconds / 60}:${String.format("%02d", customTimerSeconds % 60)}"

                mapOf(
                    "difficulty" to difficulty,
                    "timerText" to customTimerText,
                    "totalScreens" to totalScreens,
                    "puzzleType" to "memory_previous_pair",
                    "puzzleData" to currentPuzzle.question,
                    "correctAnswer" to currentPuzzle.answer,
                    "success" to true
                )
            } else {
                // Default configuration - create simple puzzle data structure
                val defaultPuzzleData = """
                {
                    "sequence": [
                        {
                            "screenNumber": 1,
                            "numbers": [1, 2, 3],
                            "linkingNumber": null,
                            "isFirstScreen": true
                        },
                        {
                            "screenNumber": 2,
                            "numbers": [2, 4, 5],
                            "linkingNumber": 2,
                            "isFirstScreen": false
                        },
                        {
                            "screenNumber": 3,
                            "numbers": [4, 1, 6],
                            "linkingNumber": 4,
                            "isFirstScreen": false
                        },
                        {
                            "screenNumber": 4,
                            "numbers": [1, 3, 2],
                            "linkingNumber": 1,
                            "isFirstScreen": false
                        }
                    ]
                }
                """.trimIndent()

                mapOf(
                    "difficulty" to difficulty,
                    "timerText" to timerText,
                    "totalScreens" to 4,
                    "puzzleType" to "memory_previous_pair",
                    "puzzleData" to defaultPuzzleData,
                    "correctAnswer" to "sequence_completion",
                    "success" to true
                )
            }
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse memory previous pair data, using defaults", e)

            // Fallback puzzle data
            val fallbackPuzzleData = """
            {
                "sequence": [
                    {
                        "screenNumber": 1,
                        "numbers": [1, 2],
                        "linkingNumber": null,
                        "isFirstScreen": true
                    },
                    {
                        "screenNumber": 2,
                        "numbers": [2, 3],
                        "linkingNumber": 2,
                        "isFirstScreen": false
                    },
                    {
                        "screenNumber": 3,
                        "numbers": [3, 4],
                        "linkingNumber": 3,
                        "isFirstScreen": false
                    }
                ]
            }
            """.trimIndent()

            mapOf(
                "difficulty" to difficulty,
                "timerText" to timerText,
                "totalScreens" to 3,
                "puzzleType" to "memory_previous_pair",
                "puzzleData" to fallbackPuzzleData,
                "correctAnswer" to "sequence_completion",
                "success" to true
            )
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val finalDifficulty = parsedData["difficulty"] as String
        val finalTimerText = parsedData["timerText"] as String
        val puzzleData = parsedData["puzzleData"] as String
        val correctAnswer = parsedData["correctAnswer"] as String

        Log.d("PuzzleScreen", "🧠 Starting memory previous pair with difficulty: $finalDifficulty, timer: $finalTimerText")

        // Use the original MemoryPreviousPairPuzzleScreen
        MemoryPreviousPairPuzzleScreen(
            difficulty = finalDifficulty,
            timer = finalTimerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            puzzleData = puzzleData,
            correctAnswer = correctAnswer,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Memory previous pair answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Memory pair puzzle completed with score: $finalScore")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid memory previous pair puzzle data, moving to next puzzle")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading memory puzzle...")
            }
        }
    }
}

@Composable
fun MemoryPreviousSingleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🧠 Showing AdaptiveMemoryPreviousSingleScreen")

    // For adaptive version, we don't need complex puzzle data parsing
    // The adaptive system handles puzzle generation internally
    val difficulty = currentPuzzle.difficulty ?: "Medium"

    // Calculate timer based on difficulty for adaptive memory challenges
    val timerText = when (difficulty.lowercase()) {
        "easy" -> "3:30"     // More time for adaptive easy mode
        "medium" -> "3:00"   // Standard adaptive time
        "hard" -> "2:30"     // Challenging but manageable for adaptive hard
        else -> "3:00"
    }

    // Parse any existing puzzle data (optional - adaptive system will override)
    val parsedData = remember(currentPuzzle) {
        try {
            if (currentPuzzle.question.startsWith("{")) {
                val questionJson = JSONObject(currentPuzzle.question)
                val totalQuestions = questionJson.optInt("totalQuestions", 10) // Default sequence length
                val totalSteps = questionJson.optInt("totalSteps", 12)

                // Calculate custom timer based on questions if provided
                val customTimerSeconds = when (difficulty.lowercase()) {
                    "easy" -> totalQuestions * 4
                    "medium" -> totalQuestions * 3
                    "hard" -> totalQuestions * 2
                    else -> totalQuestions * 3
                }
                val customTimerText = "${customTimerSeconds / 60}:${String.format("%02d", customTimerSeconds % 60)}"

                mapOf(
                    "difficulty" to difficulty,
                    "timerText" to customTimerText,
                    "totalQuestions" to totalQuestions,
                    "totalSteps" to totalSteps,
                    "puzzleType" to "adaptive_memory_previous_single",
                    "success" to true
                )
            } else {
                // Default adaptive configuration
                mapOf(
                    "difficulty" to difficulty,
                    "timerText" to timerText,
                    "totalQuestions" to 10, // Default adaptive sequence length
                    "totalSteps" to 12,
                    "puzzleType" to "adaptive_memory_previous_single",
                    "success" to true
                )
            }
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse adaptive memory previous single data, using defaults", e)
            mapOf(
                "difficulty" to difficulty,
                "timerText" to timerText,
                "totalQuestions" to 10,
                "totalSteps" to 12,
                "puzzleType" to "adaptive_memory_previous_single",
                "success" to true
            )
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val finalDifficulty = parsedData["difficulty"] as String
        val finalTimerText = parsedData["timerText"] as String

        Log.d("PuzzleScreen", "🧠 Starting adaptive memory previous single with difficulty: $finalDifficulty, timer: $finalTimerText")

        // Use the adaptive version - note the different parameter signature
        SimplifiedAdaptiveMemoryScreen(
            initialDifficulty = finalDifficulty,
            timer = finalTimerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Adaptive memory previous single answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Adaptive memory single puzzle completed with score: $finalScore")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid adaptive memory previous single puzzle data, moving to next puzzle")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading adaptive memory puzzle...")
            }
        }
    }
}
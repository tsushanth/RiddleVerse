package com.kreativekoala.riddleverse

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
fun AntonymBalloonScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🎮 Showing AntonymBalloonPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            val questionString = currentPuzzle.question.trim()

            val antonymPairs = if (questionString.startsWith("[")) {
                val pairsArray = JSONArray(questionString)
                val pairs = mutableListOf<AntonymPair>()

                for (i in 0 until pairsArray.length()) {
                    val pairArray = pairsArray.getJSONArray(i)
                    if (pairArray.length() >= 2) {
                        val word1 = pairArray.getString(0)
                        val word2 = pairArray.getString(1)
                        pairs.add(AntonymPair(word1 = word1, word2 = word2, id = i))
                    }
                }
                pairs
            } else if (questionString.startsWith("{")) {
                val antonymData = JSONObject(questionString)
                val pairsArray = antonymData.getJSONArray("pairs")
                val pairs = mutableListOf<AntonymPair>()

                for (i in 0 until pairsArray.length()) {
                    val pairObj = pairsArray.getJSONObject(i)
                    val word1 = pairObj.getString("word1")
                    val word2 = pairObj.getString("word2")
                    pairs.add(AntonymPair(word1 = word1, word2 = word2, id = i))
                }
                pairs
            } else {
                emptyList()
            }

            val difficulty = currentPuzzle.difficulty ?: "Medium"
            val timerText = when (difficulty.lowercase()) {
                "easy" -> "3:00"
                "medium" -> "2:30"
                "hard" -> "2:00"
                else -> "2:30"
            }

            mapOf(
                "antonymPairs" to antonymPairs,
                "difficulty" to difficulty,
                "timer" to timerText,
                "success" to antonymPairs.isNotEmpty()
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse antonym puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val difficulty = parsedData["difficulty"] as String
        val timerText = parsedData["timer"] as String

        AntonymBalloonPuzzleScreen(
            difficulty = difficulty,
            timer = timerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            puzzleData = currentPuzzle.question,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Antonym balloon answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after feedback completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid antonym puzzle data, moving to next puzzle")
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
fun SynonymGroupingScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🎮 Showing SynonymGroupingPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            val questionString = currentPuzzle.question.trim()

            val synonymSets = if (questionString.startsWith("{")) {
                val outerJson = JSONObject(questionString)
                val actualQuestionString = if (outerJson.has("question")) {
                    outerJson.getString("question")
                } else {
                    questionString
                }

                val questionData = JSONObject(actualQuestionString)
                val wordSetsArray = questionData.getJSONArray("wordSets")
                val sets = mutableListOf<List<String>>()

                for (i in 0 until wordSetsArray.length()) {
                    val setArray = wordSetsArray.getJSONArray(i)
                    val words = mutableListOf<String>()
                    for (j in 0 until setArray.length()) {
                        words.add(setArray.getString(j))
                    }
                    if (words.isNotEmpty()) {
                        sets.add(words)
                    }
                }
                sets
            } else if (questionString.startsWith("[")) {
                val setsArray = JSONArray(questionString)
                val sets = mutableListOf<List<String>>()

                for (i in 0 until setsArray.length()) {
                    val setArray = setsArray.getJSONArray(i)
                    val words = mutableListOf<String>()
                    for (j in 0 until setArray.length()) {
                        words.add(setArray.getString(j))
                    }
                    if (words.isNotEmpty()) {
                        sets.add(words)
                    }
                }
                sets
            } else {
                emptyList()
            }

            val difficulty = currentPuzzle.difficulty ?: "Medium"
            val timerText = when (difficulty.lowercase()) {
                "easy" -> "3:00"
                "medium" -> "2:30"
                "hard" -> "2:00"
                "expert" -> "1:30"
                else -> "2:30"
            }

            mapOf(
                "synonymSets" to synonymSets,
                "difficulty" to difficulty,
                "timer" to timerText,
                "success" to (synonymSets.isNotEmpty() && synonymSets.size == 3)
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse synonym puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        @Suppress("UNCHECKED_CAST")
        val synonymSets = parsedData["synonymSets"] as List<List<String>>
        val difficulty = parsedData["difficulty"] as String
        val timerText = parsedData["timer"] as String

        SynonymGroupingPuzzleScreen(
            difficulty = difficulty,
            timer = timerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            synonymSets = synonymSets,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Synonym grouping answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after feedback completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid synonym puzzle data, moving to next puzzle")
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
fun MemoryStoryScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🧠 Showing MemoryStoryPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            val memoryData = JSONObject(currentPuzzle.question)

            val storyCard = memoryData.getString("storyCard")
            val question = memoryData.getString("question")
            val scenario = memoryData.optString("scenario", "grocery")
            val character = memoryData.optString("character", "Someone")
            val itemCount = memoryData.optInt("itemCount", 3)
            val audioUrl = memoryData.optString("audioUrl", null)

            val optionsArray = memoryData.getJSONArray("options")
            val options = mutableListOf<String>()
            for (i in 0 until optionsArray.length()) {
                options.add(optionsArray.getString(i))
            }

            val correctItemsArray = JSONArray(currentPuzzle.answer)
            val correctItems = mutableListOf<String>()
            for (i in 0 until correctItemsArray.length()) {
                correctItems.add(correctItemsArray.getString(i))
            }

            mapOf(
                "storyCard" to storyCard,
                "question" to question,
                "options" to options,
                "correctItems" to correctItems,
                "scenario" to scenario,
                "character" to character,
                "itemCount" to itemCount,
                "audioUrl" to audioUrl,
                "success" to true
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse memory story puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val difficulty = currentPuzzle.difficulty ?: "Medium"

        MemoryStoryPuzzleScreen(
            difficulty = difficulty,
            timer = "2:00",
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            puzzleData = currentPuzzle.question,
            correctAnswer = currentPuzzle.answer,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Memory story answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after feedback completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid memory story puzzle data, moving to next puzzle")
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
fun MemorySequencingScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🧠 Showing MemorySequencingPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            val questionData = JSONObject(currentPuzzle.question)

            val topic = questionData.getString("topic")
            val description = questionData.optString("description", "")
            val partOneTitle = questionData.optString("partOneTitle", "Part One")
            val partTwoTitle = questionData.optString("partTwoTitle", "Part Two")
            val partOneNarrative = questionData.optString("partOneNarrative", "")
            val partTwoNarrative = questionData.optString("partTwoNarrative", "")
            val partOneAudioScript = questionData.optString("partOneAudioScript", "")
            val partTwoAudioScript = questionData.optString("partTwoAudioScript", "")

            val partOneAudioUrl = if (partOneAudioScript.isNotEmpty()) {
                "https://uujjodxicvifmiwlimob.supabase.co/storage/v1/object/public/puzzle-audio/${currentPuzzle.puzzleId}_part1.mp3"
            } else null

            val partTwoAudioUrl = if (partTwoAudioScript.isNotEmpty()) {
                "https://uujjodxicvifmiwlimob.supabase.co/storage/v1/object/public/puzzle-audio/${currentPuzzle.puzzleId}_part2.mp3"
            } else null

            val itemsArray = questionData.getJSONArray("items")
            val items = mutableListOf<SequenceItem>()

            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                items.add(
                    SequenceItem(
                        id = itemObj.getString("id"),
                        text = itemObj.getString("name"),
                        partNumber = itemObj.getInt("partNumber"),
                        correctOrder = itemObj.getInt("correctOrder")
                    )
                )
            }

            val formattedPuzzleData = JSONObject().apply {
                put("topic", topic)
                put("description", description)
                put("partOneTitle", partOneTitle)
                put("partTwoTitle", partTwoTitle)
                put("partOneNarrative", partOneNarrative)
                put("partTwoNarrative", partTwoNarrative)
                put("partOneAudioScript", partOneAudioScript)
                put("partTwoAudioScript", partTwoAudioScript)
                put("partOneAudioUrl", partOneAudioUrl ?: "")
                put("partTwoAudioUrl", partTwoAudioUrl ?: "")
                put("items", itemsArray)
            }

            mapOf(
                "topic" to topic,
                "items" to items,
                "partOneAudioUrl" to partOneAudioUrl,
                "partTwoAudioUrl" to partTwoAudioUrl,
                "formattedData" to formattedPuzzleData.toString(),
                "success" to true
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse memory sequencing data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val formattedData = parsedData["formattedData"] as String
        val correctAnswer = currentPuzzle.answer

        SequencingPuzzleScreen(
            difficulty = currentPuzzle.difficulty ?: "Medium",
            timer = "3:00",
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            puzzleData = formattedData,
            correctAnswer = correctAnswer,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Memory sequencing answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after sequencing completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid memory sequencing data, moving to next")
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
fun MemoryRetentionScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🧠 Showing MemoryRetentionPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            val puzzleData = currentPuzzle.question
            val questionJson = JSONObject(puzzleData)

            val topic = questionJson.optString("topic", "Memory Retention")
            val audioUrl = questionJson.optString("audioUrl", "")
            val estimatedDuration = questionJson.optLong("estimatedDuration", 60000L)

            val subjectsArray = questionJson.optJSONArray("subjects")
            val factsArray = questionJson.optJSONArray("facts")

            val subjectsCount = subjectsArray?.length() ?: 0
            val factsCount = factsArray?.length() ?: 0

            mapOf(
                "puzzleData" to puzzleData,
                "correctAnswer" to currentPuzzle.answer,
                "topic" to topic,
                "subjectsCount" to subjectsCount,
                "factsCount" to factsCount,
                "success" to (subjectsCount > 0 && factsCount > 0)
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse memory retention puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val puzzleData = parsedData["puzzleData"] as String
        val correctAnswer = parsedData["correctAnswer"] as String
        val difficulty = currentPuzzle.difficulty ?: "Easy"

        val timerText = when (difficulty.lowercase()) {
            "easy" -> "3:00"
            "medium" -> "2:30"
            "hard" -> "2:00"
            else -> "2:30"
        }

        MemoryRetentionPuzzleScreen(
            difficulty = difficulty,
            timer = timerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            puzzleData = puzzleData,
            correctAnswer = correctAnswer,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Memory retention answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after feedback completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid memory retention puzzle data, moving to next puzzle")
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
// ProgressiveRevealPuzzleScreen.kt
package com.kreativekoala.riddleverse

import android.graphics.BlurMaskFilter
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.json.JSONArray
import androidx.compose.ui.res.stringResource
import org.json.JSONObject

// Data classes for Progressive Revelation Puzzle
data class ProgressiveRevealPuzzleData(
    val puzzleId: String,
    val type: String,
    val category: String,
    val correctAnswer: String,
    val clues: List<ProgressiveClue>,
    val image: ProgressiveImageInfo,
    val gameFlow: GameFlowConfig,
    val clientConfig: ClientConfig,
    val instructions: String,
    val timeLimit: Long,
    val difficulty: String
)

data class ProgressiveClue(
    val level: Int,
    val difficulty: String,
    val text: String,
    val hint: String?
)

data class ProgressiveImageInfo(
    val url: String,
    val fileName: String,
    val blurLevels: List<BlurLevel>,
    val uploadedAt: String
)

data class BlurLevel(
    val step: Int,
    val blur: Int,
    val description: String
)

data class GameFlowConfig(
    val totalSteps: Int,
    val timePerClue: Long,
    val maxTime: Long,
    val scoringSystem: Map<String, Int>
)

data class ClientConfig(
    val blurProperty: String,
    val blurFunction: String,
    val blurUnit: String,
    val transitionDuration: String,
    val fallbackBlur: String
)

// Main wrapper component
@Composable
fun ProgressiveRevealPuzzleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "ProgressiveRevealWrapper"
    Log.d(TAG, "Loading progressive reveal puzzle: ${currentPuzzle.puzzleId}")

    // State for puzzle data
    var puzzleData by remember { mutableStateOf<ProgressiveRevealPuzzleData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val mediaCache = remember { MediaCacheManager.getInstance(context) }

    // Parse puzzle data
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            Log.d(TAG, "Raw question data: ${currentPuzzle.question}")
            Log.d(TAG, "Raw answer data: ${currentPuzzle.answer}")

            val questionJson = JSONObject(currentPuzzle.question ?: "{}")
            val answerJson = JSONObject(currentPuzzle.answer ?: "{}")

            // Extract basic information
            val puzzleId = questionJson.optString("puzzleId", currentPuzzle.puzzleId)
            val type = questionJson.optString("type", "progressive_revelation")
            val category = questionJson.optString("category", "general")
            val instructions = questionJson.optString("instructions", "Guess what's in the image!")
            val timeLimit = questionJson.optLong("timeLimit", 300000L) // 5 minutes
            val difficulty = questionJson.optString("difficulty", "medium")

            // Get correct answer
            val correctAnswer = answerJson.optString("correctAnswer", "")
            if (correctAnswer.isEmpty()) {
                throw Exception("No correct answer found in puzzle data")
            }

            // Parse clues
            val cluesArray = questionJson.optJSONArray("clues") ?: JSONArray()
            val clues = mutableListOf<ProgressiveClue>()

            for (i in 0 until cluesArray.length()) {
                val clueObj = cluesArray.getJSONObject(i)
                val clue = ProgressiveClue(
                    level = clueObj.optInt("level", i + 1),
                    difficulty = clueObj.optString("difficulty", "medium"),
                    text = clueObj.optString("text", ""),
                    hint = clueObj.optString("hint", null)
                )
                clues.add(clue)
                Log.d(TAG, "Parsed clue ${clue.level}: ${clue.text}")
            }

            // Parse image information
            val imageObj = questionJson.optJSONObject("image") ?: JSONObject()
            val imageUrl = imageObj.optString("url", "")
            val fileName = imageObj.optString("fileName", "puzzle-image.jpg")
            val uploadedAt = imageObj.optString("uploadedAt", "")

            val cachedImageUrl = if (imageUrl.isNotEmpty() && imageUrl.startsWith("http")) {
                try {
                    mediaCache.getCachedMediaUrl(imageUrl)
                } catch (e: Exception) {
                    Log.w(TAG, "Cache error for $imageUrl: ${e.message}")
                    imageUrl // Fallback to original
                }
            } else {
                imageUrl // Keep non-HTTP URLs as-is
            }

            // Parse blur levels
            val blurLevelsArray = imageObj.optJSONArray("blurLevels") ?: JSONArray()
            val blurLevels = mutableListOf<BlurLevel>()

            for (i in 0 until blurLevelsArray.length()) {
                val blurObj = blurLevelsArray.getJSONObject(i)
                val blurLevel = BlurLevel(
                    step = blurObj.optInt("step", i + 1),
                    blur = blurObj.optInt("blur", 0),
                    description = blurObj.optString("description", "")
                )
                blurLevels.add(blurLevel)
                Log.d(TAG, "Blur level ${blurLevel.step}: ${blurLevel.blur}px - ${blurLevel.description}")
            }

            val imageInfo = ProgressiveImageInfo(
                url = cachedImageUrl,
                fileName = fileName,
                blurLevels = blurLevels,
                uploadedAt = uploadedAt
            )

            // Parse game flow
            val gameFlowObj = questionJson.optJSONObject("gameFlow") ?: JSONObject()
            val scoringObj = gameFlowObj.optJSONObject("scoringSystem") ?: JSONObject()

            val scoringSystem = mutableMapOf<String, Int>()
            scoringObj.keys().forEach { key ->
                scoringSystem[key] = scoringObj.optInt(key, 0)
            }

            val gameFlow = GameFlowConfig(
                totalSteps = gameFlowObj.optInt("totalSteps", 5),
                timePerClue = gameFlowObj.optLong("timePerClue", 60000L),
                maxTime = gameFlowObj.optLong("maxTime", 300000L),
                scoringSystem = scoringSystem
            )

            // Parse client config
            val clientConfigObj = questionJson.optJSONObject("clientConfig") ?: JSONObject()
            val clientConfig = ClientConfig(
                blurProperty = clientConfigObj.optString("blurProperty", "blur"),
                blurFunction = clientConfigObj.optString("blurFunction", "blur"),
                blurUnit = clientConfigObj.optString("blurUnit", "dp"),
                transitionDuration = clientConfigObj.optString("transitionDuration", "0.5s"),
                fallbackBlur = clientConfigObj.optString("fallbackBlur", "opacity: 0.3")
            )

            puzzleData = ProgressiveRevealPuzzleData(
                puzzleId = puzzleId,
                type = type,
                category = category,
                correctAnswer = correctAnswer,
                clues = clues,
                image = imageInfo,
                gameFlow = gameFlow,
                clientConfig = clientConfig,
                instructions = instructions,
                timeLimit = timeLimit,
                difficulty = difficulty
            )

            Log.d(TAG, "Successfully parsed progressive reveal puzzle")
            Log.d(TAG, "Answer: $correctAnswer")
            Log.d(TAG, "Clues: ${clues.size}")
            Log.d(TAG, "Blur levels: ${blurLevels.size}")
            Log.d(TAG, "Image URL: $imageUrl")

            isLoading = false

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing progressive puzzle data: ${e.message}", e)
            errorMessage = "Failed to load progressive puzzle: ${e.message}"
            hasError = true
            isLoading = false
        }
    }

    // Render based on state
    when {
        isLoading -> {
            LoadingScreen(message = "Loading progressive reveal puzzle...")
        }

        hasError -> {
            SimpleErrorScreen(
                message = errorMessage,
                onRetry = {
                    isLoading = true
                    hasError = false
                },
                onSkip = {
                    handlePuzzleCompletion(false, false, 0)
                }
            )
        }

        puzzleData != null -> {
            ProgressiveRevealPuzzleScreen(
                puzzleData = puzzleData!!,
                round = "ROUND ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber} of ${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
                onPuzzleComplete = { score, isCorrect ->
                    Log.d(TAG, "Progressive puzzle completed! Score: $score, Correct: $isCorrect")
                    handlePuzzleCompletion(isCorrect, isCorrect, score)
                },
                onBack = onBack
            )
        }
    }
}

@Composable
fun ProgressiveRevealPuzzleScreen(
    puzzleData: ProgressiveRevealPuzzleData,
    round: String,
    onPuzzleComplete: (Int, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "ProgressiveRevealScreen"

    // Game state
    var currentStep by remember { mutableStateOf(1) }
    var userAnswer by remember { mutableStateOf("") }
    var gameCompleted by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var timeRemaining by remember { mutableStateOf(puzzleData.timeLimit / 1000) }
    var showHint by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackColor by remember { mutableStateOf(Color.Green) }
    var isCorrect by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    var delayedCompletion by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }


    // Current clue and blur level
    val currentClue = if (currentStep <= puzzleData.clues.size) {
        puzzleData.clues[currentStep - 1]
    } else null

    val currentBlurLevel = puzzleData.image.blurLevels.find { it.step == currentStep }

    LaunchedEffect(delayedCompletion) {
        delayedCompletion?.let { (finalScore, wasCorrect) ->
            delay(3000) // 3 second delay
            onPuzzleComplete(finalScore, wasCorrect)
            delayedCompletion = null
        }
    }
    // Timer
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0 && !gameCompleted) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining <= 0 && !gameCompleted) {
            // Time's up
            gameCompleted = true
            feedback = "Time's up! The answer was: ${puzzleData.correctAnswer}"
            feedbackColor = Color.Red
            delayedCompletion = Pair(0, false)
        }
    }

    // Auto-hide feedback
    LaunchedEffect(feedback) {
        if (feedback != null && !gameCompleted && !isCorrect) {
            delay(3500) // Show feedback for 3.5 seconds
            if (!isCorrect && !gameCompleted) {
                // Auto-advance to next clue or end game
                if (currentStep < puzzleData.clues.size) {
                    currentStep++
                    userAnswer = ""
                    showHint = false
                } else {
                    currentStep = puzzleData.gameFlow.totalSteps
                    gameCompleted = true
                    feedback = "The answer was: ${puzzleData.correctAnswer}"
                    delayedCompletion = Pair(0, false)
                    return@LaunchedEffect // Don't clear feedback in this case
                }
            }
            feedback = null
            showHint = false
        } else if (feedback != null && !gameCompleted) {
            delay(2500)
            feedback = null
            showHint = false
        }
    }

    // Functions
    fun submitAnswer() {
        if (userAnswer.trim().isEmpty()) return

        // Use enhanced client-side answer matching (mimics server embedding similarity)
        val matchResult = checkProgressiveAnswer(
            userAnswer = userAnswer,
            correctAnswer = puzzleData.correctAnswer,
            puzzleData = puzzleData,
            currentStep = currentStep
        )

        Log.d(TAG, "Answer check result: ${matchResult.explanation} (confidence: ${matchResult.confidence})")

        if (matchResult.isMatch) {
            // Correct answer! Calculate score based on match quality and step
            val baseScore = puzzleData.gameFlow.scoringSystem["correctAtStep$currentStep"] ?: 20

            // Adjust score based on match confidence and type (like server would)
            val scoreMultiplier = when (matchResult.matchType) {
                MatchType.EXACT -> 1.0
                MatchType.SYNONYM -> 0.95
                MatchType.PARTIAL -> 0.90
                MatchType.PLURAL_VARIATION -> 0.98
                MatchType.FUZZY -> 0.85                // Penalize typos slightly
                MatchType.NO_MATCH -> 0.0
            }

            val finalScore = (baseScore * scoreMultiplier * matchResult.confidence).toInt()
            score = finalScore
            isCorrect = true
            gameCompleted = true

            // Provide specific feedback based on match type
            feedback = when (matchResult.matchType) {
                MatchType.EXACT -> "Perfect! You earned $finalScore points!"
                MatchType.SYNONYM -> "Correct (good alternative)! You earned $finalScore points!"
                MatchType.PARTIAL -> "Close enough! You earned $finalScore points!"
                MatchType.FUZZY -> "Correct (watch the spelling)! You earned $finalScore points!"
                MatchType.PLURAL_VARIATION -> "Correct! You earned $finalScore points!"
                else -> "Correct! You earned $finalScore points!"
            }
            feedbackColor = Color.Green

            // Reveal the clear image
            currentStep = puzzleData.gameFlow.totalSteps

            Log.d(TAG, "Correct answer at step $currentStep, score: $finalScore, match: ${matchResult.matchType}")

            delayedCompletion = Pair(score, true)

        } else {
            // Wrong answer - provide helpful feedback based on how close they were
            val similarity = matchResult.confidence

            feedback = when {
                similarity > 0.65 -> "So close! Check your spelling or try a different form of the word."
                similarity > 0.45 -> "You're getting warmer! Think about similar or related words."
                similarity > 0.25 -> "Not quite right, but you're thinking in the right direction."
                else -> "Not the right answer. Try the next clue for more help!"
            }

            feedbackColor = when {
                similarity > 0.65 -> Color.Yellow   // Very close
                similarity > 0.45 -> Color.Cyan  // Getting there
                else -> Color.Red                  // Not close
            }

            if (currentStep < puzzleData.clues.size) {
                // Move to next clue after showing feedback
                // The LaunchedEffect will handle the delay and advancement
            } else {
                // No more clues, reveal answer
                currentStep = puzzleData.gameFlow.totalSteps
                gameCompleted = true
                feedback = "The answer was: ${puzzleData.correctAnswer}"
                delayedCompletion = Pair(0, false)
            }
        }

        keyboardController?.hide()
    }

    fun skipToNextClue() {
        if (currentStep < puzzleData.clues.size) {
            currentStep++
            userAnswer = ""
            showHint = false
        } else {
            // Reveal final image
            currentStep = puzzleData.gameFlow.totalSteps
        }
    }

    fun revealAnswer() {
        currentStep = puzzleData.gameFlow.totalSteps
        gameCompleted = true
        feedback = "Answer revealed: ${puzzleData.correctAnswer}"
        feedbackColor = Color.Blue
        delayedCompletion = Pair(0, false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header
        ProgressiveRevealHeader(
            difficulty = puzzleData.difficulty,
            round = round,
            timeRemaining = timeRemaining,
            score = score,
            category = puzzleData.category,
            currentStep = currentStep,
            totalSteps = puzzleData.gameFlow.totalSteps,
            onBack = onBack
        )

        // Main content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image section with progressive blur
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mystery Image",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = currentBlurLevel?.description ?: "Clear",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progressive blur image
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = puzzleData.image.url,
                            contentDescription = "Mystery puzzle image",
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (currentBlurLevel != null && currentBlurLevel.blur > 0) {
                                        Modifier.blur(radius = currentBlurLevel.blur.dp)
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentScale = ContentScale.Crop
                        )

                        // Step indicator overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.7f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Step $currentStep/${puzzleData.gameFlow.totalSteps}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Clue section
            if (!gameCompleted && currentClue != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Clue header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Clue ${currentClue.level}",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = currentClue.difficulty.uppercase(),
                                color = when (currentClue.difficulty.lowercase()) {
                                    "hardest" -> Color.Red
                                    "hard" -> Color.Cyan
                                    "medium" -> Color.Yellow
                                    "easy" -> Color.Green
                                    else -> Color.Gray
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Clue text
                        Text(
                            text = currentClue.text,
                            color = Color.White,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )

                        // Hint section
                        if (!currentClue.hint.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { showHint = !showHint },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Blue.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = stringResource(R.string.hint),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (showHint) "Hide Hint" else "Show Hint",
                                    fontSize = 14.sp
                                )
                            }

                            AnimatedVisibility(visible = showHint) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Blue.copy(alpha = 0.2f)
                                    ),
                                    border = BorderStroke(1.dp, Color.Blue.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = currentClue.hint!!,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Answer input section (only show if not completed)
            if (!gameCompleted && currentClue != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Your Answer:",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = userAnswer,
                            onValueChange = { newValue ->
                                userAnswer = newValue
                            },
                            placeholder = { Text("What do you think it is?", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = if (userAnswer.length > 2) {
                                    // Show border color hint based on similarity
                                    val quickCheck = checkProgressiveAnswer(userAnswer, puzzleData.correctAnswer, puzzleData, currentStep)
                                    when {
                                        quickCheck.confidence > 0.6 -> Color.Green
                                        quickCheck.confidence > 0.4 -> Color.Yellow
                                        quickCheck.confidence > 0.2 -> Color.Cyan
                                        else -> Color.Blue
                                    }
                                } else Color.Blue,
                                unfocusedBorderColor = Color.Gray
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (userAnswer.trim().isNotEmpty()) {
                                        submitAnswer()
                                    }
                                }
                            ),
                            singleLine = true,
                            supportingText = {
                                // Show encouraging text based on current input
                                if (userAnswer.length > 2) {
                                    val quickCheck = checkProgressiveAnswer(userAnswer, puzzleData.correctAnswer, puzzleData, currentStep)

                                    Text(
                                        text = when {
                                            quickCheck.confidence > 0.6 -> "🔥 Very close!"
                                            quickCheck.confidence > 0.4 -> "🌡️ Getting warmer..."
                                            quickCheck.confidence > 0.2 -> "🤔 Keep trying..."
                                            else -> "💡 Think about the clues"
                                        },
                                        color = when {
                                            quickCheck.confidence > 0.6 -> Color.Green
                                            quickCheck.confidence > 0.4 -> Color.Yellow
                                            quickCheck.confidence > 0.2 -> Color.Cyan
                                            else -> Color.Gray
                                        },
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Submit button
                            Button(
                                onClick = { submitAnswer() },
                                enabled = userAnswer.trim().isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Green,
                                    disabledContainerColor = Color.Gray
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Submit", fontWeight = FontWeight.Bold)
                            }

                            // Next clue button
                            if (currentStep < puzzleData.clues.size) {
                                Button(
                                    onClick = { skipToNextClue() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Next Clue")
                                }
                            } else {
                                Button(
                                    onClick = { revealAnswer() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reveal")
                                }
                            }
                        }
                    }
                }
            }

            // Game completed section
            if (gameCompleted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCorrect) Color.Green.copy(alpha = 0.2f)
                        else Color.Magenta.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isCorrect) Color.Green else Color.Magenta
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Visibility,
                            contentDescription = "Result",
                            tint = if (isCorrect) Color.Green else Color.Magenta,
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = puzzleData.correctAnswer,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        if (isCorrect) {
                            Text(
                                text = "Congratulations!",
                                color = Color.Green,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "You earned $score points!",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                text = if (score > 0) "You earned $score points" else "Better luck next time!",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Feedback overlay
        AnimatedVisibility(
            visible = feedback != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            feedback?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(feedbackColor.copy(alpha = 0.9f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Progress indicator
        LinearProgressIndicator(
            progress = currentStep.toFloat() / puzzleData.gameFlow.totalSteps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.Blue,
            trackColor = Color.Gray
        )
    }
}



@Composable
fun ProgressiveRevealHeader(
    difficulty: String,
    round: String,
    timeRemaining: Long,
    score: Int,
    category: String,
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.statusBarsPadding().padding(16.dp)
    ) {
        // Top row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = difficulty.uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = round,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }

            Text(
                text = formatTime(timeRemaining),
                color = if (timeRemaining < 60) Color.Red else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title and score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Progressive Reveal",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = category.uppercase(),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${stringResource(R.string.score_label)}: $score",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Step $currentStep/$totalSteps",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}
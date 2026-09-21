package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import org.json.JSONObject
import androidx.compose.ui.platform.LocalContext
import com.kreativekoala.riddleverse.ui.theme.RvCanvas
import com.kreativekoala.riddleverse.ui.theme.RvError
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvSuccess
import com.kreativekoala.riddleverse.ui.theme.RvSurface
import com.kreativekoala.riddleverse.ui.theme.RvViolet

/**
 * Wrapper for Which Is Real puzzle screen
 * Integrates the puzzle into the main puzzle flow
 */
@Composable
fun RealOrAiScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "RealOrAiWrapper"
    val context = LocalContext.current

    // State for puzzle data
    var mergedPuzzleData by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var timeLimit by remember { mutableStateOf(90000) }
    var hasSubmittedAnswer by remember { mutableStateOf(false) }
    val mediaCache = remember { MediaCacheManager.getInstance(context) }

    // Parse puzzle data ONCE when puzzle ID changes
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            Log.d(TAG, "=== NEW PUZZLE LOADED ===")
            Log.d(TAG, "Parsing puzzle: ${currentPuzzle.puzzleId}")

            // Reset submission state for new puzzle
            hasSubmittedAnswer = false

            val questionJson = JSONObject(currentPuzzle.question ?: "{}")
            val answerJson = JSONObject(currentPuzzle.answer ?: "{}")

            // Extract basic information
            val correctAnswer = answerJson.getString("correctAnswer")
            timeLimit = questionJson.optInt("timeLimit", 90000)

            // Parse and cache images
            val imageAJson = questionJson.getJSONObject("imageA")
            val imageBJson = questionJson.getJSONObject("imageB")

            val imageAUrl = imageAJson.getString("url")
            val imageBUrl = imageBJson.getString("url")

            Log.d(TAG, "Image URLs:")
            Log.d(TAG, "  Image A: $imageAUrl")
            Log.d(TAG, "  Image B: $imageBUrl")

            // Cache images
            val cachedImageA = try {
                if (imageAUrl.startsWith("http")) {
                    mediaCache.getCachedMediaUrl(imageAUrl)
                } else imageAUrl
            } catch (e: Exception) {
                Log.w(TAG, "Cache error for imageA: ${e.message}")
                imageAUrl
            }

            val cachedImageB = try {
                if (imageBUrl.startsWith("http")) {
                    mediaCache.getCachedMediaUrl(imageBUrl)
                } else imageBUrl
            } catch (e: Exception) {
                Log.w(TAG, "Cache error for imageB: ${e.message}")
                imageBUrl
            }

            // Update image URLs in question JSON
            imageAJson.put("url", cachedImageA)
            imageBJson.put("url", cachedImageB)
            questionJson.put("imageA", imageAJson)
            questionJson.put("imageB", imageBJson)

            // Merge the correct answer into the question JSON
            questionJson.put("correctAnswer", correctAnswer)
            mergedPuzzleData = questionJson.toString()

            Log.d(TAG, "Successfully parsed puzzle")
            Log.d(TAG, "Correct answer: $correctAnswer")
            Log.d(TAG, "Time limit: $timeLimit ms")

            isLoading = false

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing puzzle data: ${e.message}", e)
            errorMessage = "Failed to load puzzle: ${e.message}"
            hasError = true
            isLoading = false
        }
    }

    // Render based on state
    when {
        isLoading -> {
            LoadingScreen(message = "Loading Real or AI puzzle...")
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

        mergedPuzzleData != null -> {
            // Calculate difficulty and timer display
            val difficulty = currentPuzzle.difficulty ?: "Medium"
            val timerDisplay = formatMillisToTimer(timeLimit)

            WhichIsRealScreen(
                difficulty = difficulty,
                timer = timerDisplay,
                puzzleData = mergedPuzzleData!!,
                onSubmitAnswer = { isCorrect ->
                    // This is called when user selects an image
                    Log.d(TAG, "Answer submitted: $isCorrect")
                    hasSubmittedAnswer = true
                },
                fetchNextPuzzle = { score ->
                    // ✅ FIXED: Only call completion handler once
                    if (!hasSubmittedAnswer) {
                        Log.w(TAG, "fetchNextPuzzle called but no answer submitted yet")
                        return@WhichIsRealScreen
                    }

                    Log.d(TAG, "Moving to next puzzle with score: $score")
                    val isCorrect = score > 0

                    // This will trigger PuzzleViewModel to load the next puzzle
                    handlePuzzleCompletion(isCorrect, isCorrect, score)
                },
                onBack = onBack
            )
        }
    }
}

/**
 * Format milliseconds to timer display (M:SS)
 */
private fun formatMillisToTimer(millis: Int): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${String.format("%02d", seconds)}"
}

/**
 * Loading screen component
 */
@Composable
private fun ROAILoadingScreen(message: String) {
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
                text = message,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Error screen with retry and skip options
 */
@Composable
private fun ROAISimpleErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⚠️ Error Loading Puzzle",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onSkip) {
                    Text("Skip Puzzle")
                }

                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}
data class WhichIsRealPuzzle(
    val puzzleId: String,
    val category: String,
    val displayCategory: String,
    val subjectName: String,
    val description: String,
    val imageA: ImageData,
    val imageB: ImageData,
    val correctAnswer: String, // "image_a" or "image_b"
    val instructions: Instructions,
    val expertClues: ExpertClues,
    val timeLimit: Int,
    val reasoningEnabled: Boolean
)

data class ImageData(
    val url: String,
    val position: String
)

data class Instructions(
    val task: String,
    val method: String,
    val scoring: String,
    val tips: List<String>
)

data class ExpertClues(
    val real: List<String>,
    val ai: List<String>
)

data class CommunityReasoning(
    val id: String,
    val reasoning: String,
    val wasCorrect: Boolean,
    val helpfulVotes: Int
)

/**
 * Wrapper for Which Is Real puzzle screen
 * Handles puzzle data parsing and completion logic
 */

/**
 * Validate Which Is Real puzzle data format
 */
fun validateWhichIsRealData(puzzleData: String): Boolean {
    return try {
        val json = JSONObject(puzzleData)

        // Check required fields
        val hasRequiredFields = json.has("puzzleId") &&
                json.has("category") &&
                json.has("imageA") &&
                json.has("imageB") &&
                json.has("correctAnswer") &&
                json.has("instructions") &&
                json.has("expertClues")

        if (!hasRequiredFields) {
            Log.w("WhichIsRealWrapper", "⚠️ Missing required fields in puzzle data")
            return false
        }

        // Validate image data
        val imageA = json.getJSONObject("imageA")
        val imageB = json.getJSONObject("imageB")

        val hasImageUrls = imageA.has("url") && imageB.has("url")
        if (!hasImageUrls) {
            Log.w("WhichIsRealWrapper", "⚠️ Missing image URLs")
            return false
        }

        // Validate correct answer
        val correctAnswer = json.getString("correctAnswer")
        if (correctAnswer != "image_a" && correctAnswer != "image_b") {
            Log.w("WhichIsRealWrapper", "⚠️ Invalid correctAnswer value: $correctAnswer")
            return false
        }

        Log.d("WhichIsRealWrapper", "✅ Puzzle data validation passed")
        true
    } catch (e: Exception) {
        Log.e("WhichIsRealWrapper", "❌ Puzzle data validation failed: ${e.message}", e)
        false
    }
}

@Composable
fun WhichIsRealScreen(
    difficulty: String = "Medium",
    timer: String = "2:00",
    puzzleData: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val feedbackManager = rememberUnifiedFeedbackManager()

    // Parse puzzle data
    val puzzle = remember(puzzleData) {
        parseWhichIsRealData(puzzleData)
    }

    // Game state
    var showWelcome by remember { mutableStateOf(true) }
    var gameStarted by remember { mutableStateOf(false) }
    var selectedImage by remember { mutableStateOf<String?>(null) }
    var isAnswerRevealed by remember { mutableStateOf(false) }
    var userWasCorrect by remember { mutableStateOf(false) }
    var totalScore by remember { mutableStateOf(0) }
    var showReasoningDialog by remember { mutableStateOf(false) }
    var showCommunityReasoningDialog by remember { mutableStateOf(false) }
    var userReasoning by remember { mutableStateOf("") }
    var showExpertClues by remember { mutableStateOf(false) }

    // Timer state
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            120 // Default 2 minutes
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }
    var gameStartTime by remember { mutableLongStateOf(0L) }

    // Timer countdown
    LaunchedEffect(timeRemaining, gameStarted, isAnswerRevealed) {
        if (timeRemaining > 0 && gameStarted && !isAnswerRevealed) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !isAnswerRevealed) {
            // Time's up - force reveal
            isAnswerRevealed = true
            userWasCorrect = false
            totalScore = 0
        }
    }

    // Calculate score
    fun calculateScore(correct: Boolean, timeSpent: Long, providedReasoning: Boolean): Int {
        if (!correct) return 0

        val baseScore = when (difficulty.lowercase()) {
            "easy" -> 100
            "medium" -> 150
            "hard" -> 200
            else -> 150
        }

        // Speed bonus (faster = higher score)
        val timeBonus = when {
            timeSpent <= 15000 -> (baseScore * 0.4f).toInt()
            timeSpent <= 30000 -> (baseScore * 0.2f).toInt()
            timeSpent <= 60000 -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Reasoning bonus
        val reasoningBonus = if (providedReasoning) (baseScore * 0.2f).toInt() else 0

        return baseScore + timeBonus + reasoningBonus
    }

    // Handle answer submission
    fun submitAnswer(selectedPosition: String) {
        val timeSpent = System.currentTimeMillis() - gameStartTime
        val correct = selectedPosition == puzzle.correctAnswer

        selectedImage = selectedPosition
        isAnswerRevealed = true
        userWasCorrect = correct

        haptics.performHapticFeedback(
            if (correct) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
        )

        if (correct) {
            // Show reasoning dialog for feedback (30% chance)
            if (Math.random() < 0.3) {
                showReasoningDialog = true
            }
        }

        // Calculate initial score (before reasoning bonus)
        totalScore = calculateScore(correct, timeSpent, false)

        Log.d("WhichIsReal", "Answer submitted: $selectedPosition, Correct: $correct, Score: $totalScore")
    }

    // Submit reasoning
    fun submitReasoning(reasoning: String) {
        if (reasoning.isNotBlank()) {
            userReasoning = reasoning
            // Add reasoning bonus
            val timeSpent = System.currentTimeMillis() - gameStartTime
            totalScore = calculateScore(userWasCorrect, timeSpent, true)

            // TODO: Save reasoning to Supabase
            Log.d("WhichIsReal", "User reasoning submitted: $reasoning")
        }
        showReasoningDialog = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas) // Dark purple-blue background
    ) {
        when {
            showWelcome -> {
                WhichIsRealWelcomeScreen(
                    onStart = {
                        showWelcome = false
                        gameStarted = true
                        gameStartTime = System.currentTimeMillis()
                    },
                    onBack = onBack
                )
            }

            else -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Bar
                    TopBar(
                        displayTimer = displayTimer,
                        timeRemaining = timeRemaining,
                        totalScore = totalScore,
                        onBack = onBack,
                        onShowClues = { showExpertClues = !showExpertClues }
                    )

                    // Main content
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Challenge description
                        item {
                            ChallengeCard(
                                puzzle = puzzle,
                                showExpertClues = showExpertClues
                            )
                        }

                        // Image comparison
                        item {
                            ImageComparisonSection(
                                puzzle = puzzle,
                                selectedImage = selectedImage,
                                isAnswerRevealed = isAnswerRevealed,
                                userWasCorrect = userWasCorrect,
                                onImageSelected = { position ->
                                    if (!isAnswerRevealed) {
                                        submitAnswer(position)
                                    }
                                },
                                haptics = haptics
                            )
                        }

                        // Result section (after answer)
                        if (isAnswerRevealed) {
                            item {
                                ResultSection(
                                    userWasCorrect = !userWasCorrect,
                                    puzzle = puzzle,
                                    totalScore = totalScore,
                                    onViewCommunityReasoning = {
                                        showCommunityReasoningDialog = true
                                    }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        onSubmitAnswer(userWasCorrect)
                                        fetchNextPuzzle(totalScore)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RvViolet
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "Continue to Next Puzzle",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reasoning Dialog
        if (showReasoningDialog) {
            ReasoningDialog(
                onSubmit = { reasoning ->
                    submitReasoning(reasoning)
                },
                onSkip = {
                    showReasoningDialog = false
                }
            )
        }

        // Community Reasoning Dialog
        if (showCommunityReasoningDialog) {
            CommunityReasoningDialog(
                puzzle = puzzle,
                onDismiss = { showCommunityReasoningDialog = false }
            )
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
fun WhichIsRealWelcomeScreen(
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = RvInk
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "🔍 Which Is Real?",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Educational message
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = RvSurface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = RvViolet,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Why This Matters",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                    }

                    Text(
                        text = "In today's world, AI can generate incredibly realistic images. " +
                                "Distinguishing between real and AI-generated content is becoming " +
                                "an essential skill for media literacy and critical thinking.",
                        fontSize = 14.sp,
                        color = RvInkSoft,
                        lineHeight = 20.sp
                    )

                    HorizontalDivider(
                        color = RvOutline,
                        thickness = 1.dp
                    )

                    Text(
                        text = "🎯 Your Challenge:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvViolet
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BulletPoint("Look at two similar images")
                        BulletPoint("Identify which one is AI-generated")
                        BulletPoint("Learn from expert clues and community insights")
                        BulletPoint("Develop your AI detection skills!")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start button
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RvViolet
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Start Challenge",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            fontSize = 14.sp,
            color = RvInkSoft
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = RvInkSoft
        )
    }
}

@Composable
fun TopBar(
    displayTimer: String,
    timeRemaining: Int,
    totalScore: Int,
    onBack: () -> Unit,
    onShowClues: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = RvInk
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = displayTimer,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (timeRemaining <= 30) Color.Red else RvInk
            )
            if (totalScore > 0) {
                Text(
                    text = "${stringResource(R.string.score_label)}: $totalScore",
                    fontSize = 12.sp,
                    color = RvViolet
                )
            }
        }

        IconButton(
            onClick = onShowClues,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Help,
                contentDescription = "Expert Clues",
                tint = RvInk
            )
        }
    }
}

@Composable
fun ChallengeCard(
    puzzle: WhichIsRealPuzzle,
    showExpertClues: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RvSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = puzzle.subjectName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk
            )

            Text(
                text = puzzle.description,
                fontSize = 14.sp,
                color = RvInkSoft
            )

            AnimatedVisibility(visible = showExpertClues) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(
                        color = RvOutline,
                        thickness = 1.dp
                    )

                    Text(
                        text = "🎓 Expert Clues:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvViolet
                    )

                    Text(
                        text = "Look for real photos:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvSuccess
                    )
                    puzzle.expertClues.real.take(2).forEach { clue ->
                        Text(
                            text = "• $clue",
                            fontSize = 11.sp,
                            color = RvInkSoft
                        )
                    }

                    Text(
                        text = "AI tells:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvError
                    )
                    puzzle.expertClues.ai.take(2).forEach { clue ->
                        Text(
                            text = "• $clue",
                            fontSize = 11.sp,
                            color = RvInkSoft
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImageComparisonSection(
    puzzle: WhichIsRealPuzzle,
    selectedImage: String?,
    isAnswerRevealed: Boolean,
    userWasCorrect: Boolean,
    onImageSelected: (String) -> Unit,
    haptics: HapticFeedback
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Which image is AI-generated?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = RvInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Image A
        ImageCard(
            imageUrl = puzzle.imageA.url,
            position = puzzle.imageA.position,
            isSelected = selectedImage == puzzle.imageA.position,
            isAnswerRevealed = isAnswerRevealed,
            isCorrectAnswer = puzzle.correctAnswer != puzzle.imageA.position,  // ✅ INVERTED!
            label = "Image A",
            onSelect = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onImageSelected(puzzle.imageA.position)
            }
        )

        // Image B
        ImageCard(
            imageUrl = puzzle.imageB.url,
            position = puzzle.imageB.position,
            isSelected = selectedImage == puzzle.imageB.position,
            isAnswerRevealed = isAnswerRevealed,
            isCorrectAnswer = puzzle.correctAnswer != puzzle.imageB.position,  // ✅ INVERTED!
            label = "Image B",
            onSelect = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onImageSelected(puzzle.imageB.position)
            }
        )
    }
}

@Composable
fun ImageCard(
    imageUrl: String,
    position: String,
    isSelected: Boolean,
    isAnswerRevealed: Boolean,
    isCorrectAnswer: Boolean,
    label: String,
    onSelect: () -> Unit
) {
    val borderColor = when {
        isAnswerRevealed && isCorrectAnswer -> RvError // Red for AI
        isAnswerRevealed && !isCorrectAnswer -> RvSuccess // Green for real
        isSelected -> RvViolet // Purple for selected
        else -> RvOutline
    }

    val borderWidth = if (isSelected || isAnswerRevealed) 4.dp else 2.dp

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var isImageLoading by remember { mutableStateOf(true) }
    var hasImageError by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(screenHeight * 0.28f)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !isAnswerRevealed) { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = label,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
                onLoading = { isImageLoading = true },
                onSuccess = { isImageLoading = false; hasImageError = false },
                onError = { isImageLoading = false; hasImageError = true }
            )

            // Loading indicator
            if (isImageLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = RvInk,
                        strokeWidth = 3.dp
                    )
                }
            }

            // Error placeholder
            if (hasImageError && !isImageLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = "Image failed to load",
                            tint = RvOnTone,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Image unavailable",
                            color = RvOnTone,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Label
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(
                        Color.Black.copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvOnTone
                )
            }

            // Result indicator
            if (isAnswerRevealed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(
                            if (isCorrectAnswer) RvError else RvSuccess,  // ✅ BACK TO ORIGINAL
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isCorrectAnswer) "🤖 AI" else "📷 Real",  // ✅ BACK TO ORIGINAL
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvOnTone
                    )
                }
            }
        }
    }
}

@Composable
fun ResultSection(
    userWasCorrect: Boolean,
    puzzle: WhichIsRealPuzzle,
    totalScore: Int,
    onViewCommunityReasoning: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (userWasCorrect)
                RvSuccess.copy(alpha = 0.2f)
            else
                RvError.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (userWasCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (userWasCorrect) RvSuccess else RvError,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = if (userWasCorrect) "Correct! 🎉" else "Not quite...",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvOnTone
                    )
                    if (userWasCorrect) {
                        Text(
                            text = "${stringResource(R.string.score_label)}: +$totalScore",
                            fontSize = 14.sp,
                            color = RvViolet
                        )
                    }
                }
            }

            HorizontalDivider(
                color = RvOutline,
                thickness = 1.dp
            )

            Text(
                text = if (userWasCorrect)
                    "Great eye for detail! You correctly identified the AI-generated image."
                else
                    "The AI-generated image was ${if (puzzle.correctAnswer == "image_a") "Image A" else "Image B"}.",
                fontSize = 14.sp,
                color = RvOnTone.copy(alpha = 0.9f)
            )

            OutlinedButton(
                onClick = onViewCommunityReasoning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = RvOnTone
                ),
                border = BorderStroke(2.dp, RvOutline)
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Learn from Community Insights")
            }
        }
    }
}

@Composable
fun ReasoningDialog(
    onSubmit: (String) -> Unit,
    onSkip: () -> Unit
) {
    var reasoning by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onSkip) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = RvSurface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🎁 Bonus Question",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Help others learn! Why did you think this was the AI image?",
                    fontSize = 14.sp,
                    color = RvInkSoft
                )

                OutlinedTextField(
                    value = reasoning,
                    onValueChange = { reasoning = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = {
                        Text(
                            "E.g., 'The lighting looked too perfect' or 'Unusual texture in the background'",
                            fontSize = 12.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = RvInk,
                        unfocusedTextColor = RvInk,
                        focusedBorderColor = RvViolet,
                        unfocusedBorderColor = RvOutline
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RvInk
                        ),
                        border = BorderStroke(2.dp, RvOutline)
                    ) {
                        Text("Skip")
                    }

                    Button(
                        onClick = { onSubmit(reasoning) },
                        modifier = Modifier.weight(1f),
                        enabled = reasoning.length >= 10,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RvViolet
                        )
                    ) {
                        Text("Submit (+20% bonus)")
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityReasoningDialog(
    puzzle: WhichIsRealPuzzle,
    onDismiss: () -> Unit
) {
    // Mock community reasoning - in production, fetch from Supabase
    val communityReasonings = remember {
        listOf(
            CommunityReasoning(
                id = "1",
                reasoning = "The lighting was too perfect and even across the entire image",
                wasCorrect = true,
                helpfulVotes = 42
            ),
            CommunityReasoning(
                id = "2",
                reasoning = "Small details in the background looked slightly off",
                wasCorrect = true,
                helpfulVotes = 38
            ),
            CommunityReasoning(
                id = "3",
                reasoning = "The texture had an unnaturally smooth quality",
                wasCorrect = true,
                helpfulVotes = 31
            )
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = RvSurface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "💡 Community Insights",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                Text(
                    text = "Learn from others who identified the AI image correctly:",
                    fontSize = 14.sp,
                    color = RvInkSoft
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(communityReasonings) { reasoning ->
                        CommunityReasoningItem(reasoning)
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RvViolet
                    )
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun CommunityReasoningItem(reasoning: CommunityReasoning) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RvSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = reasoning.reasoning,
                fontSize = 13.sp,
                color = RvInk
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = null,
                    tint = RvViolet,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${reasoning.helpfulVotes} found this helpful",
                    fontSize = 11.sp,
                    color = RvInkSoft
                )
            }
        }
    }
}

// Parse puzzle data
fun parseWhichIsRealData(puzzleData: String): WhichIsRealPuzzle {
    try {
        val json = JSONObject(puzzleData)

        val imageAJson = json.getJSONObject("imageA")
        val imageBJson = json.getJSONObject("imageB")
        val instructionsJson = json.getJSONObject("instructions")
        val expertCluesJson = json.getJSONObject("expertClues")

        val realClues = mutableListOf<String>()
        val realCluesArray = expertCluesJson.getJSONArray("real")
        for (i in 0 until realCluesArray.length()) {
            realClues.add(realCluesArray.getString(i))
        }

        val aiClues = mutableListOf<String>()
        val aiCluesArray = expertCluesJson.getJSONArray("ai")
        for (i in 0 until aiCluesArray.length()) {
            aiClues.add(aiCluesArray.getString(i))
        }

        val tips = mutableListOf<String>()
        val tipsArray = instructionsJson.getJSONArray("tips")
        for (i in 0 until tipsArray.length()) {
            tips.add(tipsArray.getString(i))
        }

        return WhichIsRealPuzzle(
            puzzleId = json.getString("puzzleId"),
            category = json.getString("category"),
            displayCategory = json.getString("displayCategory"),
            subjectName = json.getString("subjectName"),
            description = json.getString("description"),
            imageA = ImageData(
                url = imageAJson.getString("url"),
                position = imageAJson.getString("position")
            ),
            imageB = ImageData(
                url = imageBJson.getString("url"),
                position = imageBJson.getString("position")
            ),
            correctAnswer = json.getString("correctAnswer"),
            instructions = Instructions(
                task = instructionsJson.getString("task"),
                method = instructionsJson.getString("method"),
                scoring = instructionsJson.getString("scoring"),
                tips = tips
            ),
            expertClues = ExpertClues(
                real = realClues,
                ai = aiClues
            ),
            timeLimit = json.getInt("timeLimit"),
            reasoningEnabled = json.getBoolean("reasoningEnabled")
        )
    } catch (e: Exception) {
        Log.e("WhichIsReal", "Error parsing puzzle data: ${e.message}", e)
        throw e
    }
}
// Simplified ImageQuestionScreenWrapper.kt with 5-second countdown
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

// Simplified data classes
data class SimpleImageQuestionPuzzleData(
    val puzzleId: String,
    val imageUrl: String,
    val theme: String,
    val description: String,
    val questions: List<ImageQuestion>,
    val totalQuestions: Int,
    val timeLimit: Long,
    val difficulty: String
)

data class ImageQuestion(
    val id: Int,
    val question: String,
    val answer: String,
    val type: String,
    val difficulty: String,
    val options: List<String>,
    val hint: String
)

data class QuestionAnswer(
    val questionId: Int,
    val selectedAnswer: String,
    val isCorrect: Boolean,
    val answeredAt: Long = System.currentTimeMillis()
)

enum class GamePhase {
    STUDYING_IMAGE,    // 5-second countdown
    ANSWERING_QUESTIONS, // Questions visible, image hidden
    COMPLETED          // Show results
}

// Penalty settings
const val IMAGE_REVEAL_PENALTY = 20 // Points deducted for revealing image

@Composable
fun ImageQuestionScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "ImageQuestionWrapper"
    val context = LocalContext.current
    val mediaCache = remember { MediaCacheManager.getInstance(context) }

    Log.d(TAG, "Loading Image Question puzzle: ${currentPuzzle.puzzleId}")

    // State for puzzle data and media caching
    var puzzleData by remember { mutableStateOf<SimpleImageQuestionPuzzleData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoadingMedia by remember { mutableStateOf(false) }
    var cachedImageUrl by remember { mutableStateOf<String?>(null) }

    // Parse puzzle data and cache media
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            Log.d(TAG, "Raw question data: ${currentPuzzle.question}")

            val questionJson = JSONObject(currentPuzzle.question ?: "{}")
            Log.d(TAG, "Question JSON keys: ${questionJson.keys().asSequence().toList()}")

            // Extract basic puzzle information
            val puzzleId = questionJson.optString("puzzleId", currentPuzzle.puzzleId)
            val imageUrl = questionJson.optString("imageUrl", "")
            val theme = questionJson.optString("theme", "Image Questions")
            val description = questionJson.optString("description", "")
            val totalQuestions = questionJson.optInt("totalQuestions", 0)
            val timeLimit = questionJson.optLong("timeLimit", 120000L)
            val difficulty = currentPuzzle.difficulty ?: "Medium"

            Log.d(TAG, "Parsed image URL: '$imageUrl'")
            Log.d(TAG, "Total questions: $totalQuestions")

            // Validate image readiness
            if (imageUrl.isEmpty() || imageUrl == "null" || imageUrl.contains("PLACEHOLDER")) {
                Log.e(TAG, "Image not ready yet: '$imageUrl'")
                errorMessage = "Image is still being generated. Please try again in a moment."
                hasError = true
                isLoading = false
                return@LaunchedEffect
            }

            if (!imageUrl.startsWith("http")) {
                Log.e(TAG, "Invalid image URL format: '$imageUrl'")
                errorMessage = "Invalid image URL. Please try again."
                hasError = true
                isLoading = false
                return@LaunchedEffect
            }

            // Cache the main image URL
            if (imageUrl.isNotEmpty() && imageUrl.startsWith("http")) {
                isLoadingMedia = true
                Log.d(TAG, "Caching main image: $imageUrl")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val cached = mediaCache.getCachedMediaUrl(imageUrl)
                        Log.d(TAG, "Image cached successfully: $cached")

                        withContext(Dispatchers.Main) {
                            cachedImageUrl = cached
                            isLoadingMedia = false
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to cache main image, using original URL", e)
                        withContext(Dispatchers.Main) {
                            cachedImageUrl = imageUrl
                            isLoadingMedia = false
                        }
                    }
                }
            } else {
                cachedImageUrl = imageUrl
                isLoadingMedia = false
            }

            // Parse questions array
            val questionsArray = questionJson.optJSONArray("questions") ?: JSONArray()
            val questions = mutableListOf<ImageQuestion>()

            for (i in 0 until questionsArray.length()) {
                val qJson = questionsArray.getJSONObject(i)

                // Parse options array
                val optionsArray = qJson.optJSONArray("options") ?: JSONArray()
                val options = mutableListOf<String>()
                for (j in 0 until optionsArray.length()) {
                    options.add(optionsArray.getString(j))
                }

                questions.add(
                    ImageQuestion(
                        id = qJson.optInt("id", i + 1),
                        question = qJson.optString("question", ""),
                        answer = qJson.optString("answer", ""),
                        type = qJson.optString("type", "identification"),
                        difficulty = qJson.optString("difficulty", difficulty),
                        options = options,
                        hint = qJson.optString("hint", "Look carefully at the image")
                    )
                )
            }

            if (questions.isEmpty()) {
                Log.e(TAG, "No questions found in puzzle data")
                errorMessage = "No questions available for this puzzle."
                hasError = true
                isLoading = false
                return@LaunchedEffect
            }

            // Wait for media caching to complete before creating puzzle data
            while (isLoadingMedia) {
                delay(100)
            }

            // Create puzzle data with cached URL
            puzzleData = SimpleImageQuestionPuzzleData(
                puzzleId = puzzleId,
                imageUrl = cachedImageUrl ?: imageUrl,
                theme = theme,
                description = description,
                questions = questions.shuffled(),
                totalQuestions = totalQuestions,
                timeLimit = timeLimit,
                difficulty = difficulty
            )

            Log.d(TAG, "Successfully parsed image question puzzle with cached media")
            isLoading = false

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing puzzle data: ${e.message}", e)
            errorMessage = "Failed to load puzzle: ${e.message}"
            hasError = true
            isLoading = false
        }
    }

    when {
        isLoading || isLoadingMedia -> {
            IQLoadingScreen(
                message = when {
                    isLoadingMedia -> "Preparing image..."
                    else -> "Loading image question puzzle..."
                }
            )
        }

        hasError -> {
            IQErrorScreen(
                message = errorMessage,
                onRetry = {
                    Log.d(TAG, "Retrying puzzle load")
                    isLoading = true
                    hasError = false
                },
                onSkip = {
                    Log.d(TAG, "Skipping puzzle due to error")
                    handlePuzzleCompletion(false, false, 0)
                }
            )
        }

        puzzleData != null -> {
            SimpleImageQuestionPuzzleScreen(
                puzzleData = puzzleData!!,
                difficulty = currentPuzzle.difficulty ?: "Medium",
                round = "ROUND ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber} of ${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
                onPuzzleComplete = { score, correctCount ->
                    Log.d(TAG, "Image question puzzle completed! Correct: $correctCount/${puzzleData!!.totalQuestions}, Score: $score")
                    val isComplete = correctCount >= (puzzleData!!.totalQuestions * 0.6).toInt()
                    handlePuzzleCompletion(isComplete, isComplete, score)
                },
                onBack = onBack
            )
        }
    }
}

@Composable
fun SimpleImageQuestionPuzzleScreen(
    puzzleData: SimpleImageQuestionPuzzleData,
    difficulty: String,
    round: String,
    onPuzzleComplete: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "SimpleImageQuestionScreen"

    // Game state
    var gamePhase by remember { mutableStateOf(GamePhase.STUDYING_IMAGE) }
    var studyTimeRemaining by remember { mutableStateOf(5) }
    var gameTimeRemaining by remember { mutableStateOf(puzzleData.timeLimit / 1000) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf<List<QuestionAnswer>>(emptyList()) }
    var imageRevealed by remember { mutableStateOf(false) }
    var imageRevealCount by remember { mutableStateOf(0) }
    var imageLoaded by remember { mutableStateOf(false) }
    var imageLoading by remember { mutableStateOf(true) }
    var imageError by remember { mutableStateOf(false) }

    // ADD THIS NEW STATE FOR GRID
    var showGrid by remember { mutableStateOf(false) }

    LaunchedEffect(gamePhase, imageLoaded) {
        if (gamePhase == GamePhase.STUDYING_IMAGE && imageLoaded) {
            while (studyTimeRemaining > 0) {
                delay(1000)
                studyTimeRemaining--
            }
            // Move to questions phase
            gamePhase = GamePhase.ANSWERING_QUESTIONS
            Log.d(TAG, "🔒 Study time over - image hidden, starting questions")
        }
    }

    // Main game timer (only during questions phase)
    LaunchedEffect(gamePhase, gameTimeRemaining) {
        if (gamePhase == GamePhase.ANSWERING_QUESTIONS && gameTimeRemaining > 0) {
            delay(1000)
            gameTimeRemaining--
        } else if (gameTimeRemaining <= 0 && gamePhase == GamePhase.ANSWERING_QUESTIONS) {
            Log.d(TAG, "⏰ Time's up!")
            gamePhase = GamePhase.COMPLETED
        }
    }

    // Auto-complete when all questions answered
    LaunchedEffect(answers.size) {
        if (answers.size >= puzzleData.totalQuestions && gamePhase == GamePhase.ANSWERING_QUESTIONS) {
            Log.d(TAG, "🎉 All questions answered!")
            gamePhase = GamePhase.COMPLETED
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header (unchanged)
        SimpleImageQuestionHeader(
            difficulty = difficulty,
            round = round,
            gamePhase = gamePhase,
            studyTimeRemaining = studyTimeRemaining,
            gameTimeRemaining = gameTimeRemaining,
            currentQuestion = currentQuestionIndex + 1,
            totalQuestions = puzzleData.totalQuestions,
            correctCount = answers.count { it.isCorrect },
            onBack = onBack
        )

        when (gamePhase) {
            GamePhase.STUDYING_IMAGE -> {
                StudyImagePhase(
                    imageUrl = puzzleData.imageUrl,
                    theme = puzzleData.theme,
                    timeRemaining = if (imageLoaded) studyTimeRemaining else 0, // 0 triggers loading state
                    totalQuestions = puzzleData.totalQuestions,
                    showGrid = showGrid,
                    onToggleGrid = { showGrid = !showGrid },
                    onImageLoaded = {
                        imageLoaded = true
                        imageLoading = false
                    },
                    onImageError = {
                        imageError = true
                        imageLoading = false
                    }
                )
            }

            GamePhase.ANSWERING_QUESTIONS -> {
                // UPDATED: Pass grid parameters
                AnsweringQuestionsPhase(
                    questions = puzzleData.questions,
                    currentQuestionIndex = currentQuestionIndex,
                    answers = answers,
                    imageUrl = puzzleData.imageUrl,
                    theme = puzzleData.theme,
                    imageRevealed = imageRevealed,
                    imageRevealCount = imageRevealCount,
                    showGrid = showGrid,
                    onAnswerSelected = { answer ->
                        val currentQuestion = puzzleData.questions[currentQuestionIndex]
                        val isCorrect = answer == currentQuestion.answer

                        // Remove existing answer
                        answers = answers.filter { it.questionId != currentQuestion.id }

                        // Add new answer
                        answers = answers + QuestionAnswer(
                            questionId = currentQuestion.id,
                            selectedAnswer = answer,
                            isCorrect = isCorrect
                        )

                        // Move to next question
                        if (currentQuestionIndex < puzzleData.totalQuestions - 1) {
                            currentQuestionIndex++
                        }
                    },
                    onRevealImage = {
                        imageRevealed = true
                        imageRevealCount++
                        Log.d(TAG, "👁️ Image revealed - penalty: $IMAGE_REVEAL_PENALTY points")
                    },
                    onToggleGrid = { showGrid = !showGrid }
                )
            }

            GamePhase.COMPLETED -> {
                val correctAnswers = answers.count { it.isCorrect }
                val baseScore = calculateScore(correctAnswers, puzzleData.totalQuestions, gameTimeRemaining, puzzleData.timeLimit)
                val penaltyDeduction = imageRevealCount * IMAGE_REVEAL_PENALTY
                val finalScore = maxOf(0, baseScore - penaltyDeduction)

                ResultsPhase(
                    baseScore = baseScore,
                    finalScore = finalScore,
                    penaltyDeduction = penaltyDeduction,
                    imageRevealCount = imageRevealCount,
                    correctAnswers = correctAnswers,
                    totalQuestions = puzzleData.totalQuestions,
                    answers = answers,
                    questions = puzzleData.questions,
                    imageUrl = puzzleData.imageUrl, // ADD THIS
                    theme = puzzleData.theme,        // ADD THIS
                    onContinue = {
                        onPuzzleComplete(finalScore, correctAnswers)
                    }
                )
            }
        }

        // Progress indicator (unchanged)
        if (gamePhase == GamePhase.ANSWERING_QUESTIONS) {
            ProgressIndicator(
                answeredCount = answers.size,
                totalQuestions = puzzleData.totalQuestions,
                correctCount = answers.count { it.isCorrect },
                imageRevealCount = imageRevealCount,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun SimpleImageQuestionHeader(
    difficulty: String,
    round: String,
    gamePhase: GamePhase,
    studyTimeRemaining: Int,
    gameTimeRemaining: Long,
    currentQuestion: Int,
    totalQuestions: Int,
    correctCount: Int,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
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
            Text(
                text = "Image Questions",
                color = Color.Cyan,
                fontSize = 8.sp
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            when (gamePhase) {
                GamePhase.STUDYING_IMAGE -> {
                    Text(
                        text = "Study: $studyTimeRemaining",
                        color = Color.Yellow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Memorize the image!",
                        color = Color.Yellow,
                        fontSize = 10.sp
                    )
                }
                GamePhase.ANSWERING_QUESTIONS -> {
                    Text(
                        text = formatTime(gameTimeRemaining),
                        color = if (gameTimeRemaining < 30) Color.Red else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Q: $currentQuestion/$totalQuestions • ✓: $correctCount",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
                GamePhase.COMPLETED -> {
                    Text(
                        text = "${stringResource(R.string.completed)}!",
                        color = Color.Green,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun StudyImagePhase(
    imageUrl: String,
    theme: String,
    timeRemaining: Int,
    totalQuestions: Int,
    showGrid: Boolean = false,
    onToggleGrid: () -> Unit = {},
    onImageLoaded: () -> Unit = {},
    onImageError: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Show loading or countdown based on image state
        if (timeRemaining <= 0) {
            // Loading state
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.Yellow,
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = "Loading image...",
                    color = Color.Yellow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Countdown will start when ready",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Countdown state
            Text(
                text = timeRemaining.toString(),
                color = Color.Yellow,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Study this image carefully!",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "You'll answer $totalQuestions questions about it",
                color = Color.Cyan,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // Image with loading handling
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.1f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main image with loading callback
                val painter = rememberAsyncImagePainter(
                    model = imageUrl,
                    onSuccess = { onImageLoaded() },
                    onError = { onImageError() }
                )

                Image(
                    painter = painter,
                    contentDescription = "Study Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Grid overlay (only show when countdown is active)
                if (showGrid && timeRemaining > 0) {
                    GridOverlay(
                        modifier = Modifier.fillMaxSize(),
                        gridColor = Color.White.copy(alpha = 0.8f),
                        showLabels = true
                    )
                }

                // Theme overlay (bottom-left) - only when countdown active
                if (timeRemaining > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = theme,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Grid toggle button (only when countdown active)
                if (timeRemaining > 0) {
                    FloatingActionButton(
                        onClick = onToggleGrid,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(40.dp),
                        containerColor = if (showGrid) Color.Yellow.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.8f)
                    ) {
                        Icon(
                            imageVector = if (showGrid) Icons.Default.GridOff else Icons.Default.GridOn,
                            contentDescription = if (showGrid) "Hide Grid" else "Show Grid",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Grid instruction (only when countdown is active and grid is shown)
        if (showGrid && timeRemaining > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.2f)),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "🔍 Grid sections: Top (1,2,3), Middle (4,5,6), Bottom (7,8,9)\nLeft (1,4,7), Center (2,5,6), Right (3,6,9)",
                    color = Color.Yellow,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun AnsweringQuestionsPhase(
    questions: List<ImageQuestion>,
    currentQuestionIndex: Int,
    answers: List<QuestionAnswer>,
    imageUrl: String,
    theme: String,
    imageRevealed: Boolean,
    imageRevealCount: Int,
    showGrid: Boolean = false,
    onAnswerSelected: (String) -> Unit,
    onRevealImage: () -> Unit,
    onToggleGrid: () -> Unit = {}
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    if (currentQuestionIndex < questions.size) {
        val currentQuestion = questions[currentQuestionIndex]
        val selectedAnswer = answers.find { it.questionId == currentQuestion.id }?.selectedAnswer

        // Check if this is a location-based question
        val isLocationQuestion = currentQuestion.question.lowercase().contains("where") ||
                currentQuestion.question.lowercase().contains("location") ||
                currentQuestion.options.any { it.contains("top") || it.contains("bottom") || it.contains("left") || it.contains("right") || it.contains("center") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Image area - hidden or revealed
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLocationQuestion) screenHeight * 0.28f else screenHeight * 0.22f),
                colors = CardDefaults.cardColors(
                    containerColor = if (imageRevealed) Color.Transparent else Color.Gray.copy(alpha = 0.3f)
                )
            ) {
                if (imageRevealed) {
                    // Show the actual image with optional grid
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = rememberAsyncImagePainter(imageUrl),
                            contentDescription = "Revealed Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // 3x3 Grid overlay for location questions
                        if (showGrid || isLocationQuestion) {
                            GridOverlay(
                                modifier = Modifier.fillMaxSize(),
                                gridColor = Color.White.copy(alpha = 0.7f),
                                showLabels = isLocationQuestion
                            )
                        }

                        // Penalty indicator overlay (top-right)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(
                                    Color.Red.copy(alpha = 0.8f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "-${IMAGE_REVEAL_PENALTY * imageRevealCount} pts",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Theme overlay (bottom-left)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .background(
                                    Color.Black.copy(alpha = 0.7f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                text = theme,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Grid toggle button (top-left) - only for location questions
                        if (isLocationQuestion) {
                            FloatingActionButton(
                                onClick = onToggleGrid,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .size(32.dp),
                                containerColor = if (showGrid) Color.Yellow.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.8f)
                            ) {
                                Icon(
                                    imageVector = if (showGrid) Icons.Default.GridOff else Icons.Default.GridOn,
                                    contentDescription = if (showGrid) "Hide Grid" else "Show Grid",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Hidden image with reveal button
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onRevealImage() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = "Hidden Image",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Image Hidden",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = if (isLocationQuestion) "Location Question - Use Your Memory!" else "Use Your Memory!",
                            color = if (isLocationQuestion) Color.Yellow else Color.Cyan,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onRevealImage,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red.copy(alpha = 0.8f)
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Text(
                                    text = "Reveal Image",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "-$IMAGE_REVEAL_PENALTY points",
                                    color = Color.Yellow,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // Location question hint
            if (isLocationQuestion && !imageRevealed) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.1f)),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "📍 This is a location question. Try to remember where objects were positioned in the 3x3 grid!",
                        color = Color.Yellow,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Question card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Question header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Question ${currentQuestionIndex + 1} of ${questions.size}",
                            color = Color.Cyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLocationQuestion) {
                                Text(
                                    text = "LOCATION",
                                    color = Color.Yellow,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = currentQuestion.type.replaceFirstChar { it.uppercase() }.replace("_", " "),
                                    color = Color.Yellow,
                                    fontSize = 10.sp
                                )
                            }

                            // Show if image was revealed for this question
                            if (imageRevealed) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = "Image Revealed",
                                    tint = Color.Red,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Question text
                    Text(
                        text = currentQuestion.question,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Answer options
                    currentQuestion.options.forEach { option ->
                        val isSelected = selectedAnswer == option

                        Button(
                            onClick = { onAnswerSelected(option) },
                            enabled = selectedAnswer == null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color.Blue else Color.Gray.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = option,
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }

                    // Hint (if answered)
                    if (selectedAnswer != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = "💡 ${currentQuestion.hint}",
                                color = Color.Yellow,
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GridOverlay(
    modifier: Modifier = Modifier,
    gridColor: Color = Color.White.copy(alpha = 0.8f),
    showLabels: Boolean = false
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Draw 3x3 grid lines
        val strokeWidth = 2.dp.toPx()
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)

        // Vertical lines
        for (i in 1..2) {
            val x = (width / 3) * i
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, height),
                strokeWidth = strokeWidth,
                pathEffect = pathEffect
            )
        }

        // Horizontal lines
        for (i in 1..2) {
            val y = (height / 3) * i
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(width, y),
                strokeWidth = strokeWidth,
                pathEffect = pathEffect
            )
        }
    }

    // Add position labels if requested
    if (showLabels) {
        Box(modifier = modifier) {
            val positions = listOf(
                "1" to Alignment.TopStart,
                "2" to Alignment.TopCenter,
                "3" to Alignment.TopEnd,
                "4" to Alignment.CenterStart,
                "5" to Alignment.Center,
                "6" to Alignment.CenterEnd,
                "7" to Alignment.BottomStart,
                "8" to Alignment.BottomCenter,
                "9" to Alignment.BottomEnd
            )

            positions.forEach { (number, alignment) ->
                Box(
                    modifier = Modifier
                        .align(alignment)
                        .offset(
                            x = when (alignment) {
                                Alignment.TopStart, Alignment.CenterStart, Alignment.BottomStart -> 8.dp
                                Alignment.TopEnd, Alignment.CenterEnd, Alignment.BottomEnd -> (-8).dp
                                else -> 0.dp
                            },
                            y = when (alignment) {
                                Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd -> 8.dp
                                Alignment.BottomStart, Alignment.BottomCenter, Alignment.BottomEnd -> (-8).dp
                                else -> 0.dp
                            }
                        )
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            CircleShape
                        )
                        .size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = number,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ResultsPhase(
    baseScore: Int,
    finalScore: Int,
    penaltyDeduction: Int,
    imageRevealCount: Int,
    correctAnswers: Int,
    totalQuestions: Int,
    answers: List<QuestionAnswer>,
    questions: List<ImageQuestion>,
    imageUrl: String, // Add this parameter
    theme: String, // Add this parameter
    onContinue: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // State for controlling grid display on results screen
    var showResultsGrid by remember { mutableStateOf(true) } // Default to true for comparison

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "🎉 Results",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            // Score card with penalty breakdown
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${stringResource(R.string.final_score)}: $finalScore",
                        color = Color.Cyan,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (penaltyDeduction > 0) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Base Score: $baseScore",
                            color = Color.White,
                            fontSize = 16.sp
                        )

                        Text(
                            text = "Image Reveals: $imageRevealCount × $IMAGE_REVEAL_PENALTY = -$penaltyDeduction",
                            color = Color.Red,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Next time, try to rely more on your memory!",
                                    color = Color.Red,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Correct: $correctAnswers/$totalQuestions",
                        color = Color.White,
                        fontSize = 18.sp
                    )

                    val percentage = (correctAnswers.toDouble() / totalQuestions * 100).toInt()
                    Text(
                        text = "$percentage% ${stringResource(R.string.accuracy)}",
                        color = when {
                            percentage >= 80 -> Color.Green
                            percentage >= 60 -> Color.Yellow
                            else -> Color.Red
                        },
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val performance = when {
                        percentage >= 90 && penaltyDeduction == 0 -> "🌟 Perfect Memory!"
                        percentage >= 90 -> "⭐ Excellent!"
                        percentage >= 80 && penaltyDeduction == 0 -> "🧠 Great Memory!"
                        percentage >= 80 -> "👍 Great Work!"
                        percentage >= 70 -> "👌 Good Work!"
                        percentage >= 60 -> "✅ Passed!"
                        else -> "📚 Keep Practicing!"
                    }

                    Text(
                        text = performance,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // NEW: Reference Image Section
        item {
            Text(
                text = "📸 Reference Image",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Header with grid toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Compare with your answers",
                            color = Color.Cyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Grid toggle button
                        Button(
                            onClick = { showResultsGrid = !showResultsGrid },
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showResultsGrid) Color.Yellow.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.6f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (showResultsGrid) Icons.Default.GridOff else Icons.Default.GridOn,
                                    contentDescription = if (showResultsGrid) "Hide Grid" else "Show Grid",
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (showResultsGrid) "Hide Grid" else "Show Grid",
                                    color = Color.Black,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Image with optional grid
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(screenHeight * 0.32f),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Main image
                            Image(
                                painter = rememberAsyncImagePainter(imageUrl),
                                contentDescription = "Reference Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // Grid overlay
                            if (showResultsGrid) {
                                GridOverlay(
                                    modifier = Modifier.fillMaxSize(),
                                    gridColor = Color.White.copy(alpha = 0.9f),
                                    showLabels = true
                                )
                            }

                            // Theme overlay (bottom-left)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(
                                        Color.Black.copy(alpha = 0.7f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = theme,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Grid reference overlay (top-right) when grid is shown
                            if (showResultsGrid) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(
                                            Color.Yellow.copy(alpha = 0.9f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = "3×3 Grid Reference",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Grid explanation when visible
                    if (showResultsGrid) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = "📍 Grid positions: Top (1,2,3), Middle (4,5,6), Bottom (7,8,9)\nLeft (1,4,7), Center (2,5,8), Right (3,6,9)",
                                color = Color.Yellow,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "📝 Question Review",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Enhanced question review with location highlighting
        itemsIndexed(questions) { index, question ->
            val userAnswer = answers.find { it.questionId == question.id }

            // Check if this is a location question
            val isLocationQuestion = question.question.lowercase().contains("where") ||
                    question.question.lowercase().contains("location") ||
                    question.options.any { it.contains("top") || it.contains("bottom") || it.contains("left") || it.contains("right") || it.contains("center") }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (userAnswer?.isCorrect == true) {
                        Color.Green.copy(alpha = 0.2f)
                    } else {
                        Color.Red.copy(alpha = 0.2f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Question ${index + 1}",
                                color = Color.Cyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Location question indicator
                            if (isLocationQuestion) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "LOCATION",
                                        color = Color.Yellow,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = if (userAnswer?.isCorrect == true) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (userAnswer?.isCorrect == true) "Correct" else "Incorrect",
                            tint = if (userAnswer?.isCorrect == true) Color.Green else Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = question.question,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Answer section with enhanced formatting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Your answer:",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = userAnswer?.selectedAnswer ?: "Not answered",
                                color = if (userAnswer?.isCorrect == true) Color.Green else Color.Red,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (userAnswer?.isCorrect == false) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Correct answer:",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = question.answer,
                                    color = Color.Green,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Location question tip
                    if (isLocationQuestion) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Blue.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color.Blue,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "💡 Check the reference image above with the grid to see exact positions",
                                    color = Color.Blue,
                                    fontSize = 11.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Summary statistics
        item {
            val locationQuestions = questions.filter { question ->
                question.question.lowercase().contains("where") ||
                        question.question.lowercase().contains("location") ||
                        question.options.any { it.contains("top") || it.contains("bottom") || it.contains("left") || it.contains("right") || it.contains("center") }
            }

            if (locationQuestions.isNotEmpty()) {
                val locationCorrect = answers.count { answer ->
                    locationQuestions.any { q -> q.id == answer.questionId && answer.isCorrect }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Blue.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📍 Location Questions Summary",
                            color = Color.Blue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Correct: $locationCorrect/${locationQuestions.size}",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        val locationPercentage = (locationCorrect.toDouble() / locationQuestions.size * 100).toInt()
                        Text(
                            text = "$locationPercentage% accuracy on spatial memory",
                            color = when {
                                locationPercentage >= 75 -> Color.Green
                                locationPercentage >= 50 -> Color.Yellow
                                else -> Color.Red
                            },
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
            ) {
                Text(
                    text = stringResource(R.string.continue_label),
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Add some bottom padding for better scroll experience
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ProgressIndicator(
    answeredCount: Int,
    totalQuestions: Int,
    correctCount: Int,
    imageRevealCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = answeredCount.toFloat() / totalQuestions,
            modifier = Modifier.fillMaxWidth(),
            color = Color.Cyan,
            trackColor = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Progress: $answeredCount/$totalQuestions",
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = "Correct: $correctCount",
                color = Color.Green,
                fontSize = 12.sp
            )
            if (imageRevealCount > 0) {
                Text(
                    text = "Reveals: $imageRevealCount (-${imageRevealCount * IMAGE_REVEAL_PENALTY})",
                    color = Color.Red,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun IQLoadingScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = Color.Cyan,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun IQErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⚠️ Error",
                color = Color.Red,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text(stringResource(R.string.skip))
                }
            }
        }
    }
}

// Helper functions
private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun calculateScore(
    correctAnswers: Int,
    totalQuestions: Int,
    timeRemaining: Long,
    totalTime: Long
): Int {
    val baseScore = (correctAnswers.toDouble() / totalQuestions * 100).toInt()
    val timeBonus = ((timeRemaining.toDouble() / totalTime) * 20).toInt()
    return (baseScore + timeBonus).coerceAtMost(100)
}
// Enhanced SimpleImageMatchPuzzleScreen.kt with URL validation and fallback
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.json.JSONArray
import androidx.compose.ui.res.stringResource
import org.json.JSONObject

// Enhanced data classes with URL validation
data class SimpleImageMatchData(
    val puzzleId: String,
    val theme: String,
    val title: String,
    val description: String,
    val primaryImage: ImageInfo,
    val questions: List<SimpleImageQuestion>,
    val totalQuestions: Int,
    val timeLimit: Long,
    val instructions: String,
    val hasLegacyUrls: Boolean = false // Flag for legacy Pixabay URLs
)

data class ImageInfo(
    val url: String,
    val photographer: String,
    val isLegacyPixabayUrl: Boolean = false,
    val fallbackUrl: String? = null
)

data class SimpleImageQuestion(
    val id: Int,
    val question: String,
    val answer: String,
    val type: String,
    val options: List<String>,
    val hint: String,
    val difficulty: String,
    val imageInfo: ImageInfo? = null, // Enhanced with ImageInfo
    var isAnswered: Boolean = false,
    var selectedAnswer: String? = null,
    var isCorrect: Boolean? = null
)

// URL validation utilities
object UrlValidator {
    private const val TAG = "UrlValidator"

    fun isPixabayUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.contains("pixabay.com") ||
                url.contains("cdn.pixabay.com") ||
                url.contains("get.pxhere.com")
    }

    fun isSupabaseUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.contains("supabase.co/storage")
    }

    fun validateImageUrl(url: String?): ImageUrlStatus {
        return when {
            url.isNullOrEmpty() -> ImageUrlStatus.EMPTY
            isSupabaseUrl(url) -> ImageUrlStatus.VALID_SUPABASE
            isPixabayUrl(url) -> ImageUrlStatus.LEGACY_PIXABAY
            else -> ImageUrlStatus.UNKNOWN_EXTERNAL
        }
    }

    fun logUrlAnalysis(puzzleId: String, urls: List<String>) {
        Log.w(TAG, "🔍 URL Analysis for puzzle: $puzzleId")
        urls.forEach { url ->
            val status = validateImageUrl(url)
            val emoji = when (status) {
                ImageUrlStatus.VALID_SUPABASE -> "✅"
                ImageUrlStatus.LEGACY_PIXABAY -> "⚠️"
                ImageUrlStatus.UNKNOWN_EXTERNAL -> "❓"
                ImageUrlStatus.EMPTY -> "❌"
            }
            Log.w(TAG, "$emoji ${status.name}: ${url.take(80)}...")
        }
    }
}

enum class ImageUrlStatus {
    VALID_SUPABASE,
    LEGACY_PIXABAY,
    UNKNOWN_EXTERNAL,
    EMPTY
}

// Fixed Android puzzle parser to handle the new server response format
// Replace the parsing section in your SimpleImageMatchPuzzleScreenWrapper

@Composable
fun SimpleImageMatchPuzzleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "SimpleImageMatchWrapper"
    Log.d(TAG, "🖼️ Loading simple image match puzzle: ${currentPuzzle.puzzleId}")

    // State for puzzle data
    var puzzleData by remember { mutableStateOf<SimpleImageMatchData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var urlWarnings by remember { mutableStateOf<List<String>>(emptyList()) }

    // Parse puzzle data with FIXED parsing logic
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            Log.d(TAG, "📄 Raw question data: ${currentPuzzle.question}")

            val questionJson = JSONObject(currentPuzzle.question ?: "{}")

            // Extract basic information
            val puzzleId = questionJson.optString("puzzleId", currentPuzzle.puzzleId)
            val theme = questionJson.optString("theme", "Image Quiz")
            val title = questionJson.optString("title", "Image Puzzle")
            val description = questionJson.optString("description", "Answer questions about this image")
            val totalQuestions = questionJson.optInt("totalQuestions", 2)
            val timeLimit = questionJson.optLong("timeLimit", 120000)
            val instructions = questionJson.optString("instructions", "Look at the image and answer the questions")

            // Parse primary image with validation
            val primaryImageObj = questionJson.optJSONObject("primaryImage") ?: JSONObject()
            val primaryImageUrl = primaryImageObj.optString("url", "")
            val primaryImageUrlStatus = UrlValidator.validateImageUrl(primaryImageUrl)

            val primaryImage = ImageInfo(
                url = primaryImageUrl,
                photographer = primaryImageObj.optString("photographer", "Unknown"),
                isLegacyPixabayUrl = primaryImageUrlStatus == ImageUrlStatus.LEGACY_PIXABAY,
                fallbackUrl = primaryImageObj.optString("fallbackUrl", null)
            )

            // Track all URLs for analysis
            val allUrls = mutableListOf<String>()
            if (primaryImageUrl.isNotEmpty()) allUrls.add(primaryImageUrl)

            // FIXED: Parse questions with correct answer handling
            val questionsArray = questionJson.optJSONArray("questions") ?: JSONArray()
            val questions = mutableListOf<SimpleImageQuestion>()
            val warnings = mutableListOf<String>()

            // CRITICAL FIX: Try to get answers from the "answer" field if available
            val answerData = try {
                val answerString = currentPuzzle.answer
                if (!answerString.isNullOrEmpty()) {
                    Log.d(TAG, "🔍 Found answer data: ${answerString.take(200)}...")
                    JSONObject(answerString)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Could not parse answer data: ${e.message}")
                null
            }

            // Get correct answers array if available
            val correctAnswersArray = answerData?.optJSONArray("correctAnswers")
            val answerQuestionsArray = answerData?.optJSONArray("questions")

            for (i in 0 until questionsArray.length()) {
                val questionObj = questionsArray.getJSONObject(i)
                val optionsArray = questionObj.optJSONArray("options") ?: JSONArray()
                val options = mutableListOf<String>()

                for (j in 0 until optionsArray.length()) {
                    options.add(optionsArray.getString(j))
                }

                // FIXED: Get correct answer from multiple sources
                var correctAnswer = ""

                // Method 1: Try from the question object itself
                correctAnswer = questionObj.optString("correctAnswer", "")

                // Method 2: Try from answer data by question ID
                if (correctAnswer.isEmpty() && answerQuestionsArray != null) {
                    val questionId = questionObj.optInt("id", i + 1)
                    for (k in 0 until answerQuestionsArray.length()) {
                        val answerQuestionObj = answerQuestionsArray.getJSONObject(k)
                        if (answerQuestionObj.optInt("id", -1) == questionId) {
                            correctAnswer = answerQuestionObj.optString("correctAnswer", "")
                            Log.d(TAG, "✅ Found correct answer for question $questionId: $correctAnswer")
                            break
                        }
                    }
                }

                // Method 3: Try from correctAnswers array by index
                if (correctAnswer.isEmpty() && correctAnswersArray != null && i < correctAnswersArray.length()) {
                    val answerValue = correctAnswersArray.opt(i)
                    if (answerValue != null && answerValue != JSONObject.NULL) {
                        correctAnswer = answerValue.toString()
                        Log.d(TAG, "✅ Found correct answer from array index $i: $correctAnswer")
                    }
                }

                // Log the result
                if (correctAnswer.isEmpty()) {
                    Log.e(TAG, "❌ No correct answer found for question ${i + 1}: ${questionObj.optString("question", "")}")
                    warnings.add("Question ${i + 1} missing correct answer")
                } else {
                    Log.d(TAG, "✅ Question ${i + 1} correct answer: $correctAnswer")
                }

                // Parse question image if present
                val imageObj = questionObj.optJSONObject("image")
                var questionImageInfo: ImageInfo? = null

                if (imageObj != null) {
                    val imageUrl = imageObj.optString("url", "")
                    val imageUrlStatus = UrlValidator.validateImageUrl(imageUrl)

                    if (imageUrl.isNotEmpty()) {
                        allUrls.add(imageUrl)

                        questionImageInfo = ImageInfo(
                            url = imageUrl,
                            photographer = imageObj.optString("photographer", "Unknown"),
                            isLegacyPixabayUrl = imageUrlStatus == ImageUrlStatus.LEGACY_PIXABAY,
                            fallbackUrl = imageObj.optString("fallbackUrl", null)
                        )

                        // Add warning for legacy URLs
                        if (imageUrlStatus == ImageUrlStatus.LEGACY_PIXABAY) {
                            warnings.add("Question ${i + 1} uses legacy Pixabay URL")
                        }
                    }
                }

                val question = SimpleImageQuestion(
                    id = questionObj.optInt("id", i + 1),
                    question = questionObj.optString("question", ""),
                    answer = correctAnswer, // FIXED: Use the correctly parsed answer
                    type = questionObj.optString("type", "general"),
                    options = options,
                    hint = questionObj.optString("hint", ""),
                    difficulty = questionObj.optString("difficulty", "medium"),
                    imageInfo = questionImageInfo
                )

                questions.add(question)
                Log.d(TAG, "❓ Question ${question.id}: ${question.question}")
                Log.d(TAG, "✅ Answer ${question.id}: ${question.answer}")
            }

            // Analyze all URLs found
            UrlValidator.logUrlAnalysis(puzzleId, allUrls)

            // Check for legacy URLs
            val hasLegacyUrls = primaryImage.isLegacyPixabayUrl ||
                    questions.any { it.imageInfo?.isLegacyPixabayUrl == true }

            if (hasLegacyUrls) {
                warnings.add("This puzzle contains legacy Pixabay URLs that may expire")
                Log.w(TAG, "⚠️ LEGACY PIXABAY URLs DETECTED in puzzle: $puzzleId")
            }

            // Add warning for primary image
            if (primaryImage.isLegacyPixabayUrl) {
                warnings.add("Primary image uses legacy Pixabay URL")
            }

            // VALIDATION: Check if we have any questions with missing answers
            val questionsWithoutAnswers = questions.filter { it.answer.isEmpty() }
            if (questionsWithoutAnswers.isNotEmpty()) {
                Log.e(TAG, "❌ ${questionsWithoutAnswers.size} questions missing answers!")
                questionsWithoutAnswers.forEach { q ->
                    Log.e(TAG, "   Question ${q.id}: ${q.question}")
                }
                warnings.add("${questionsWithoutAnswers.size} questions missing correct answers")
            }

            puzzleData = SimpleImageMatchData(
                puzzleId = puzzleId,
                theme = theme,
                title = title,
                description = description,
                primaryImage = primaryImage,
                questions = questions,
                totalQuestions = totalQuestions,
                timeLimit = timeLimit,
                instructions = instructions,
                hasLegacyUrls = hasLegacyUrls
            )

            urlWarnings = warnings
            Log.d(TAG, "✅ Successfully parsed simple image match puzzle")

            if (hasLegacyUrls) {
                Log.w(TAG, "⚠️ Puzzle has ${warnings.size} URL warnings")
            }

            // Final validation log
            Log.d(TAG, "📊 Final puzzle stats:")
            Log.d(TAG, "   Questions: ${questions.size}")
            Log.d(TAG, "   Questions with answers: ${questions.count { it.answer.isNotEmpty() }}")
            Log.d(TAG, "   Questions without answers: ${questionsWithoutAnswers.size}")

            isLoading = false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing puzzle data: ${e.message}", e)
            errorMessage = "Failed to load puzzle: ${e.message}"
            hasError = true
            isLoading = false
        }
    }

    // Rest of the composable remains the same...
    when {
        isLoading -> {
            LoadingScreen(message = "Loading image puzzle...")
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
            // Show warnings if any legacy URLs detected
            if (urlWarnings.isNotEmpty()) {
                LaunchedEffect(urlWarnings) {
                    Log.w(TAG, "🚨 URL WARNINGS for puzzle ${puzzleData!!.puzzleId}:")
                    urlWarnings.forEach { warning ->
                        Log.w(TAG, "   - $warning")
                    }
                }
            }

            SimpleImageMatchPuzzleScreen(
                puzzleData = puzzleData!!,
                difficulty = currentPuzzle.difficulty ?: "Medium",
                round = "ROUND ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber} of ${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
                urlWarnings = urlWarnings,
                onPuzzleComplete = { score, correctAnswers ->
                    Log.d(TAG, "🎉 Image puzzle completed! Score: $score, Correct: $correctAnswers")
                    val isComplete = correctAnswers >= puzzleData!!.totalQuestions
                    handlePuzzleCompletion(isComplete, isComplete, score)
                },
                onBack = onBack
            )
        }
    }
}

@Composable
fun SimpleImageMatchPuzzleScreen(
    puzzleData: SimpleImageMatchData,
    difficulty: String,
    round: String,
    urlWarnings: List<String> = emptyList(),
    onPuzzleComplete: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "SimpleImageMatchScreen"
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Game state
    var questions by remember { mutableStateOf(puzzleData.questions) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var correctAnswers by remember { mutableStateOf(0) }
    var wrongAnswers by remember { mutableStateOf(0) }
    var timeRemaining by remember { mutableStateOf(puzzleData.timeLimit / 1000) }
    var gameCompleted by remember { mutableStateOf(false) }

    // Current question
    val currentQuestion = if (currentQuestionIndex < questions.size) {
        questions[currentQuestionIndex]
    } else null

    // Selection state
    var selectedAnswer by remember { mutableStateOf<String?>(null) }

    // Feedback state
    var showFeedback by remember { mutableStateOf<String?>(null) }
    var feedbackColor by remember { mutableStateOf(Color.Green) }

    // Warning display state
    var showUrlWarning by remember { mutableStateOf(puzzleData.hasLegacyUrls) }

    // Timer
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0 && !gameCompleted) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining <= 0 && !gameCompleted) {
            gameCompleted = true
            val score = calculateImageScore(correctAnswers, puzzleData.totalQuestions, wrongAnswers, timeRemaining)
            onPuzzleComplete(score, correctAnswers)
        }
    }

    // Auto-hide feedback and advance to next question
    LaunchedEffect(showFeedback) {
        if (showFeedback != null) {
            delay(2000)
            showFeedback = null

            // Move to next question or complete game
            if (currentQuestionIndex < questions.size - 1) {
                currentQuestionIndex++
                selectedAnswer = null
            } else if (!gameCompleted) {
                gameCompleted = true
                val score = calculateImageScore(correctAnswers, puzzleData.totalQuestions, wrongAnswers, timeRemaining)
                onPuzzleComplete(score, correctAnswers)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header with enhanced URL status
        SimpleImageMatchHeader(
            difficulty = difficulty,
            round = round,
            timeRemaining = timeRemaining,
            correctAnswers = correctAnswers,
            totalQuestions = puzzleData.totalQuestions,
            title = puzzleData.title,
            description = puzzleData.description,
            hasLegacyUrls = puzzleData.hasLegacyUrls,
            onBack = onBack
        )

        // URL Warning Banner
        AnimatedVisibility(
            visible = showUrlWarning && urlWarnings.isNotEmpty(),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, Color.Yellow)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color.Yellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Legacy image URLs detected - may load slowly",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = { showUrlWarning = false }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.Yellow,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Main content area
        if (currentQuestion != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enhanced Image section with URL status
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight * 0.32f),
                    colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f)),
                    border = if (puzzleData.primaryImage.isLegacyPixabayUrl) {
                        BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.5f))
                    } else null
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
                                text = "🖼️ ${puzzleData.theme.uppercase()}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Image status indicator
                            if (puzzleData.primaryImage.isLegacyPixabayUrl) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Legacy URL",
                                        tint = Color.Yellow,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "LEGACY",
                                        color = Color.Yellow,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Permanent URL",
                                        tint = Color.Green,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "STABLE",
                                        color = Color.Green,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Enhanced AsyncImage with error handling
                        EnhancedAsyncImage(
                            imageInfo = puzzleData.primaryImage,
                            contentDescription = "Puzzle Image",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }

                // Question section (unchanged)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
                                text = "Question ${currentQuestionIndex + 1} of ${puzzleData.totalQuestions}",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Text(
                                text = currentQuestion.difficulty.uppercase(),
                                color = when (currentQuestion.difficulty.lowercase()) {
                                    "easy" -> Color.Green
                                    "hard" -> Color.Red
                                    else -> Color.Yellow
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Question text
                        Text(
                            text = currentQuestion.question,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Answer options
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentQuestion.options) { option ->
                                AnswerOptionItem(
                                    option = option,
                                    isSelected = selectedAnswer == option,
                                    onClick = {
                                        selectedAnswer = option
                                    }
                                )
                            }
                        }

                        // Hint section
                        if (currentQuestion.hint.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Blue.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = stringResource(R.string.hint),
                                        tint = Color.Cyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "💡 ${currentQuestion.hint}",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Feedback overlay (unchanged)
        AnimatedVisibility(
            visible = showFeedback != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            showFeedback?.let { feedback ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(feedbackColor.copy(alpha = 0.9f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = feedback,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Bottom controls (unchanged)
        SimpleImageBottomControls(
            selectedAnswer = selectedAnswer,
            currentQuestion = currentQuestion,
            onSubmit = {
                if (selectedAnswer != null && currentQuestion != null) {
                    val isCorrect = selectedAnswer == currentQuestion.answer

                    if (isCorrect) {
                        correctAnswers++
                        showFeedback = "🎉 Correct! ${currentQuestion.answer}"
                        feedbackColor = Color.Green
                    } else {
                        wrongAnswers++
                        showFeedback = "❌ Wrong! The correct answer is: ${currentQuestion.answer}"
                        feedbackColor = Color.Red
                    }

                    // Update question state
                    questions = questions.map { q ->
                        if (q.id == currentQuestion.id) {
                            q.copy(
                                isAnswered = true,
                                selectedAnswer = selectedAnswer,
                                isCorrect = isCorrect
                            )
                        } else q
                    }
                }
            },
            onSkip = {
                val score = calculateImageScore(correctAnswers, puzzleData.totalQuestions, wrongAnswers, timeRemaining)
                onPuzzleComplete(score, correctAnswers)
            }
        )

        // Progress bar (unchanged)
        LinearProgressIndicator(
            progress = (currentQuestionIndex + 1).toFloat() / puzzleData.totalQuestions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.Green,
            trackColor = Color.Gray
        )
    }
}

// Enhanced AsyncImage component with fallback handling
@Composable
fun EnhancedAsyncImage(
    imageInfo: ImageInfo,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val TAG = "EnhancedAsyncImage"

    // Log URL status
    LaunchedEffect(imageInfo.url) {
        if (imageInfo.isLegacyPixabayUrl) {
            Log.w(TAG, "⚠️ Loading legacy Pixabay URL: ${imageInfo.url.take(50)}...")
        } else {
            Log.d(TAG, "✅ Loading stable URL: ${imageInfo.url.take(50)}...")
        }
    }

    AsyncImage(
        model = imageInfo.url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        onError = { error ->
            Log.e(TAG, "❌ Image load failed: ${imageInfo.url}")
            Log.e(TAG, "Error: ${error.result.throwable?.message}")

            if (imageInfo.isLegacyPixabayUrl) {
                Log.w(TAG, "🚨 Legacy Pixabay URL failed to load - this puzzle needs migration!")
            }
        },
        onSuccess = {
            if (imageInfo.isLegacyPixabayUrl) {
                Log.w(TAG, "⚠️ Legacy Pixabay URL loaded successfully, but may fail in future")
            } else {
                Log.d(TAG, "✅ Image loaded successfully")
            }
        }
    )
}

// Enhanced header with URL status
@Composable
fun SimpleImageMatchHeader(
    difficulty: String,
    round: String,
    timeRemaining: Long,
    correctAnswers: Int,
    totalQuestions: Int,
    title: String,
    description: String,
    hasLegacyUrls: Boolean = false,
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
                Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = Color.White)
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

                // URL status indicator
                if (hasLegacyUrls) {
                    Text(
                        text = "LEGACY URLS",
                        color = Color.Yellow,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = formatImageTime(timeRemaining),
                color = if (timeRemaining < 30) Color.Red else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Title and description
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = description,
            color = Color.Gray,
            fontSize = 12.sp
        )

        // Score display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(totalQuestions) { index ->
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Progress",
                        tint = if (index < correctAnswers) Color.Yellow else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = "$correctAnswers/$totalQuestions",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Rest of the components remain unchanged...
@Composable
fun AnswerOptionItem(
    option: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color.Blue.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.3f)
        ),
        border = if (isSelected) BorderStroke(2.dp, Color.White) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = option,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SimpleImageBottomControls(
    selectedAnswer: String?,
    currentQuestion: SimpleImageQuestion?,
    onSubmit: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Skip button
        Button(
            onClick = onSkip,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
            modifier = Modifier.weight(0.3f)
        ) {
            Text(stringResource(R.string.skip), fontSize = 14.sp)
        }

        // Submit button
        Button(
            onClick = onSubmit,
            enabled = selectedAnswer != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedAnswer != null) Color.Green else Color.Gray
            ),
            modifier = Modifier.weight(0.7f)
        ) {
            Text(
                text = if (selectedAnswer != null) stringResource(R.string.submit_answer) else "Select an Answer",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Helper functions (unchanged)
private fun formatImageTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun calculateImageScore(
    correctAnswers: Int,
    totalQuestions: Int,
    wrongAnswers: Int,
    timeRemaining: Long
): Int {
    val baseScore = (correctAnswers.toDouble() / totalQuestions * 100).toInt()
    val wrongPenalty = wrongAnswers * 5
    val timeBonus = (timeRemaining / 10).toInt()
    return maxOf(0, baseScore - wrongPenalty + timeBonus)
}
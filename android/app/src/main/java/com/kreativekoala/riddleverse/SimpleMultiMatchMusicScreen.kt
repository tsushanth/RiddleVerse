// SimpleMultiMatchMusicScreen.kt - Simple Vertical Multi-Match Music Puzzle
package com.kreativekoala.riddleverse

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.res.stringResource
import org.json.JSONArray
import org.json.JSONObject

// Simplified data classes
data class SimpleMultiMatchMusicData(
    val puzzleId: String,
    val theme: String,
    val description: String,
    val questions: List<SimpleMusicQuestion>,
    val totalQuestions: Int,
    val timeLimit: Long
)

data class SimpleMusicQuestion(
    val id: Int,
    val audioUrl: String,
    val answer: String,
    val hint: String,
    val metadata: SimpleMusicMetadata,
    var isAnswered: Boolean = false
)

data class SimpleMusicMetadata(
    val duration: Int,
    val albumImageUrl: String,
    val requestedArtist: String,
    val requestedTitle: String
)

enum class SimpleAudioState {
    STOPPED, PLAYING, PAUSED, LOADING
}

class AsyncMediaPlayerManager {
    private var currentPlayer: MediaPlayer? = null
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun releaseCurrentPlayer() {
        currentPlayer?.let { player ->
            currentPlayer = null
            backgroundScope.launch {
                // Add timeout protection to prevent finalize() timeout
                withTimeoutOrNull(2000L) {
                    try {
                        if (player.isPlaying) {
                            player.stop()
                        }
                        player.reset()
                        player.release()
                        Log.d("MediaPlayerManager", "Player released successfully")
                    } catch (e: Exception) {
                        Log.w("MediaPlayerManager", "Error releasing player: ${e.message}")
                    }
                } ?: run {
                    Log.w("MediaPlayerManager", "Player release timed out, forcing reset")
                    try {
                        player.reset()
                    } catch (e: Exception) {
                        Log.e("MediaPlayerManager", "Failed to reset after timeout", e)
                    }
                }
            }
        }
    }

    fun setCurrentPlayer(player: MediaPlayer) {
        releaseCurrentPlayer()
        currentPlayer = player
    }

    fun getCurrentPlayer(): MediaPlayer? = currentPlayer

    fun cleanup() {
        releaseCurrentPlayer()
        backgroundScope.cancel()
    }
}

@Composable
fun SimpleMultiMatchMusicPuzzleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "SimpleMultiMatchWrapper"
    val context = LocalContext.current
    val mediaCache = remember { MediaCacheManager.getInstance(context) }

    Log.d(TAG, "Loading simple multi-match music puzzle: ${currentPuzzle.puzzleId}")

    // State for puzzle data and media caching
    var puzzleData by remember { mutableStateOf<SimpleMultiMatchMusicData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoadingMedia by remember { mutableStateOf(false) }
    var mediaCacheProgress by remember { mutableStateOf(0f) }

    // Parse puzzle data and cache media
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            Log.d(TAG, "Raw question data: ${currentPuzzle.question}")

            val questionJson = JSONObject(currentPuzzle.question ?: "{}")

            // Extract basic information
            val puzzleId = questionJson.optString("puzzleId", currentPuzzle.puzzleId)
            val theme = questionJson.optString("theme", "Music Quiz")
            val description = questionJson.optString("description", "Match songs to their titles")
            val totalQuestions = questionJson.optInt("totalTracks", 8)
            val timeLimit = questionJson.optLong("timeLimit", 360000)

            // Parse questions
            val questionsArray = questionJson.optJSONArray("questions") ?: JSONArray()
            val originalQuestions = mutableListOf<SimpleMusicQuestion>()

            for (i in 0 until questionsArray.length()) {
                val questionObj = questionsArray.getJSONObject(i)
                val metadataObj = questionObj.optJSONObject("metadata") ?: JSONObject()

                val question = SimpleMusicQuestion(
                    id = questionObj.optInt("id", i + 1),
                    audioUrl = questionObj.optString("audioUrl", ""),
                    answer = questionObj.optString("answer", ""),
                    hint = questionObj.optString("hint", ""),
                    metadata = SimpleMusicMetadata(
                        duration = metadataObj.optInt("duration", 30),
                        albumImageUrl = metadataObj.optString("albumImageUrl", ""),
                        requestedArtist = metadataObj.optString("requestedArtist", ""),
                        requestedTitle = metadataObj.optString("requestedTitle", "")
                    )
                )

                originalQuestions.add(question)
                Log.d(TAG, "Question ${question.id}: ${question.answer}")
            }

            // Cache audio files before creating puzzle data
            if (originalQuestions.isNotEmpty()) {
                isLoadingMedia = true
                Log.d(TAG, "Caching ${originalQuestions.size} audio files...")

                val cachedQuestions = mutableListOf<SimpleMusicQuestion>()

                originalQuestions.forEachIndexed { index, question ->
                    try {
                        // Update progress
                        mediaCacheProgress = (index.toFloat() / originalQuestions.size)

                        // Cache audio URL
                        val cachedAudioUrl = if (question.audioUrl.isNotEmpty() && question.audioUrl.startsWith("http")) {
                            mediaCache.getCachedMediaUrl(question.audioUrl)
                        } else {
                            question.audioUrl
                        }

                        // Cache album image URL
                        val cachedAlbumImageUrl = if (question.metadata.albumImageUrl.isNotEmpty() &&
                            question.metadata.albumImageUrl.startsWith("http")) {
                            mediaCache.getCachedMediaUrl(question.metadata.albumImageUrl)
                        } else {
                            question.metadata.albumImageUrl
                        }

                        // Create cached question
                        cachedQuestions.add(
                            question.copy(
                                audioUrl = cachedAudioUrl,
                                metadata = question.metadata.copy(
                                    albumImageUrl = cachedAlbumImageUrl
                                )
                            )
                        )

                        Log.d(TAG, "Cached audio for question ${question.id}: $cachedAudioUrl")

                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to cache media for question ${question.id}, using original URLs", e)
                        cachedQuestions.add(question)
                    }
                }

                mediaCacheProgress = 1f
                isLoadingMedia = false

                // Create puzzle data with cached URLs
                puzzleData = SimpleMultiMatchMusicData(
                    puzzleId = puzzleId,
                    theme = theme,
                    description = description,
                    questions = cachedQuestions,
                    totalQuestions = totalQuestions,
                    timeLimit = timeLimit
                )

                Log.d(TAG, "Successfully parsed music puzzle with cached audio files")
            } else {
                // No questions to cache
                puzzleData = SimpleMultiMatchMusicData(
                    puzzleId = puzzleId,
                    theme = theme,
                    description = description,
                    questions = originalQuestions,
                    totalQuestions = totalQuestions,
                    timeLimit = timeLimit
                )
                isLoadingMedia = false
            }

            isLoading = false

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing puzzle data: ${e.message}", e)
            errorMessage = "Failed to load puzzle: ${e.message}"
            hasError = true
            isLoading = false
            isLoadingMedia = false
        }
    }

    when {
        isLoading || isLoadingMedia -> {
            MusicLoadingScreen(
                message = when {
                    isLoadingMedia -> "Preparing audio files..."
                    else -> "Loading music puzzle..."
                },
                progress = if (isLoadingMedia) mediaCacheProgress else null
            )
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
            SimpleMultiMatchMusicPuzzleScreen(
                puzzleData = puzzleData!!,
                difficulty = currentPuzzle.difficulty ?: "Medium",
                round = "ROUND ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber} of ${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
                onPuzzleComplete = { score, correctAnswers ->
                    Log.d(TAG, "Music puzzle completed! Score: $score, Correct: $correctAnswers")
                    val isComplete = correctAnswers >= puzzleData!!.totalQuestions
                    handlePuzzleCompletion(isComplete, isComplete, score)
                },
                onBack = onBack
            )
        }
    }
}

@Composable
fun MusicLoadingScreen(
    message: String,
    progress: Float? = null
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
            if (progress != null) {
                // Show progress for audio caching
                CircularProgressIndicator(
                    progress = progress,
                    color = Color.Cyan,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp
                )

                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = Color.Cyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                // Standard loading indicator
                CircularProgressIndicator(
                    color = Color.Cyan,
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                text = message,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            if (progress != null) {
                Text(
                    text = "Downloading audio files for offline playback",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SimpleMultiMatchMusicPuzzleScreen(
    puzzleData: SimpleMultiMatchMusicData,
    difficulty: String,
    round: String,
    onPuzzleComplete: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "SimpleMultiMatchScreen"
    val context = LocalContext.current

    // Game state
    var questions by remember { mutableStateOf(puzzleData.questions) }
    var answers by remember { mutableStateOf(puzzleData.questions.map { it.answer }.shuffled()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var wrongAnswers by remember { mutableStateOf(0) }
    var timeRemaining by remember { mutableStateOf(puzzleData.timeLimit / 1000) }
    var gameCompleted by remember { mutableStateOf(false) }

    // Selection state
    var selectedQuestionId by remember { mutableStateOf<Int?>(null) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }

    // Feedback state
    var showFeedback by remember { mutableStateOf<String?>(null) }
    var feedbackColor by remember { mutableStateOf(Color.Green) }

    // Audio state - using the manager
    val mediaPlayerManager = remember { AsyncMediaPlayerManager() }
    var currentPlayingId by remember { mutableStateOf<Int?>(null) }
    var audioState by remember { mutableStateOf(SimpleAudioState.STOPPED) }

    // Timer
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0 && !gameCompleted) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining <= 0 && !gameCompleted) {
            gameCompleted = true
            val score = calculateSimpleMusicScore(correctAnswers, puzzleData.totalQuestions, wrongAnswers, timeRemaining)
            onPuzzleComplete(score, correctAnswers)
        }
    }

    // Auto-hide feedback
    LaunchedEffect(showFeedback) {
        if (showFeedback != null) {
            delay(2000)
            showFeedback = null
        }
    }

    // Cleanup media player manager
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerManager.cleanup()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header
        SimpleMultiMatchHeader(
            difficulty = difficulty,
            round = round,
            timeRemaining = timeRemaining,
            correctAnswers = correctAnswers,
            totalQuestions = puzzleData.totalQuestions,
            theme = puzzleData.theme,
            description = puzzleData.description,
            onBack = onBack
        )

        // Main content area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left side - Music samples
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "🎵 Music Samples",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(questions.filter { !it.isAnswered }) { question ->
                            MusicSampleItem(
                                question = question,
                                isSelected = selectedQuestionId == question.id,
                                isPlaying = currentPlayingId == question.id && audioState == SimpleAudioState.PLAYING,
                                onClick = {
                                    selectedQuestionId = if (selectedQuestionId == question.id) null else question.id
                                },
                                onPlayPause = {
                                    val currentPlayer = mediaPlayerManager.getCurrentPlayer()

                                    if (currentPlayingId == question.id && audioState == SimpleAudioState.PLAYING) {
                                        // Pause current
                                        currentPlayer?.pause()
                                        audioState = SimpleAudioState.PAUSED
                                    } else {
                                        // Stop any current playback and start new
                                        mediaPlayerManager.releaseCurrentPlayer() // This is now async!
                                        currentPlayingId = question.id
                                        audioState = SimpleAudioState.LOADING

                                        try {
                                            val newPlayer = MediaPlayer().apply {
                                                setDataSource(question.audioUrl)
                                                prepareAsync()
                                                setOnPreparedListener {
                                                    start()
                                                    audioState = SimpleAudioState.PLAYING
                                                }
                                                setOnCompletionListener {
                                                    audioState = SimpleAudioState.STOPPED
                                                    currentPlayingId = null
                                                }
                                                setOnErrorListener { _, _, _ ->
                                                    audioState = SimpleAudioState.STOPPED
                                                    currentPlayingId = null
                                                    Log.e(TAG, "Error playing audio for question ${question.id}")
                                                    true
                                                }
                                            }
                                            mediaPlayerManager.setCurrentPlayer(newPlayer)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error setting up media player: ${e.message}")
                                            audioState = SimpleAudioState.STOPPED
                                            currentPlayingId = null
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Right side - Answers
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "🎯 Song Titles",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(answers.filter { answer ->
                            questions.find { it.answer == answer }?.isAnswered != true
                        }) { answer ->
                            AnswerItem(
                                answer = answer,
                                isSelected = selectedAnswer == answer,
                                onClick = {
                                    selectedAnswer = if (selectedAnswer == answer) null else answer
                                }
                            )
                        }
                    }
                }
            }
        }

        // Feedback overlay
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

        // Bottom controls
        SimpleBottomControls(
            selectedQuestionId = selectedQuestionId,
            selectedAnswer = selectedAnswer,
            questionsRemaining = questions.count { !it.isAnswered },
            onSubmit = {
                if (selectedQuestionId != null && selectedAnswer != null) {
                    val question = questions.find { it.id == selectedQuestionId }
                    val isCorrect = question?.answer == selectedAnswer

                    if (isCorrect) {
                        correctAnswers++
                        showFeedback = "🎉 Correct! Great job!"
                        feedbackColor = Color.Green

                        // Remove question and answer
                        questions = questions.map { q ->
                            if (q.id == selectedQuestionId) q.copy(isAnswered = true) else q
                        }

                        // Stop any playing audio - now async!
                        mediaPlayerManager.releaseCurrentPlayer()
                        currentPlayingId = null
                        audioState = SimpleAudioState.STOPPED

                        // Check completion
                        if (correctAnswers >= puzzleData.totalQuestions) {
                            gameCompleted = true
                            val score = calculateSimpleMusicScore(correctAnswers, puzzleData.totalQuestions, wrongAnswers, timeRemaining)
                            onPuzzleComplete(score, correctAnswers)
                        }

                    } else {
                        wrongAnswers++
                        showFeedback = "❌ Wrong! Try again"
                        feedbackColor = Color.Red
                    }

                    selectedQuestionId = null
                    selectedAnswer = null
                }
            },
            onSkip = {
                val score = calculateSimpleMusicScore(correctAnswers, puzzleData.totalQuestions, wrongAnswers, timeRemaining)
                onPuzzleComplete(score, correctAnswers)
            }
        )

        // Progress bar
        LinearProgressIndicator(
            progress = correctAnswers.toFloat() / puzzleData.totalQuestions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.Green,
            trackColor = Color.Gray
        )
    }
}

@Composable
fun MusicSampleItem(
    question: SimpleMusicQuestion,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit
) {
    val isCached = question.audioUrl.startsWith("file://") ||
            question.audioUrl.contains("cache")

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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Play/Pause button
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isPlaying) Color.Red else Color.Green,
                        CircleShape
                    )
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Question info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Song #${question.id}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Cache indicator
                    if (isCached) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = "Cached",
                            tint = Color.Green,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Text(
                    text = "${question.metadata.duration}s preview",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            // Selection indicator
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
fun AnswerItem(
    answer: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color.Green.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.3f)
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
                text = answer,
                color = Color.White,
                fontSize = 14.sp,
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
fun SimpleMultiMatchHeader(
    difficulty: String,
    round: String,
    timeRemaining: Long,
    correctAnswers: Int,
    totalQuestions: Int,
    theme: String,
    description: String,
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
            }

            Text(
                text = formatSimpleTime(timeRemaining),
                color = if (timeRemaining < 30) Color.Red else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Title and description
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = theme,
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

@Composable
fun SimpleBottomControls(
    selectedQuestionId: Int?,
    selectedAnswer: String?,
    questionsRemaining: Int,
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

        // Answer button
        Button(
            onClick = onSubmit,
            enabled = selectedQuestionId != null && selectedAnswer != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedQuestionId != null && selectedAnswer != null)
                    Color.Green else Color.Gray
            ),
            modifier = Modifier.weight(0.7f)
        ) {
            Text(
                text = if (selectedQuestionId != null && selectedAnswer != null)
                    stringResource(R.string.submit_answer) else "Select Music & Song",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SimpleErrorScreen(
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
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
private fun formatSimpleTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun calculateSimpleMusicScore(
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
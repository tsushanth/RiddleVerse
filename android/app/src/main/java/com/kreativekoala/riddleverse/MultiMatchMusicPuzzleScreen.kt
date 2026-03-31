// MultiMatchMusicPuzzleScreen.kt - Circular Multi-Match Music Puzzle
package com.kreativekoala.riddleverse

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

// Data classes for Multi-Match Music Puzzle
data class MultiMatchMusicData(
    val puzzleId: String,
    val theme: String,
    val description: String,
    val questions: List<MusicQuestion>,
    val totalQuestions: Int,
    val timeLimit: Long,
    val gameSettings: MusicGameSettings
)

data class MusicQuestion(
    val id: Int,
    val audioUrl: String,
    val question: String,
    val answer: String,
    val options: List<String>,
    val hint: String,
    val metadata: MusicMetadata,
    var isAnswered: Boolean = false,
    var isCorrect: Boolean = false
)

data class MusicMetadata(
    val deezerId: Long,
    val duration: Int,
    val albumImageUrl: String,
    val requestedArtist: String,
    val requestedTitle: String
)

data class MusicGameSettings(
    val autoPlay: Boolean,
    val allowReplay: Boolean,
    val shuffleQuestions: Boolean,
    val showProgress: Boolean,
    val previewDuration: Int
)

data class CircleItem(
    val id: Int,
    val text: String,
    val audioUrl: String? = null,
    val isQuestion: Boolean,
    var angle: Float,
    var isSelected: Boolean = false,
    var isAnswered: Boolean = false,
    var isCorrect: Boolean = false,
    val originalIndex: Int
)

enum class AudioState {
    STOPPED, PLAYING, PAUSED, LOADING
}

// shared helper
private fun angleFrom(center: Offset, point: Offset): Float {
    val dx = point.x - center.x
    val dy = point.y - center.y
    return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
}

@Composable
private fun OrbitRings(center: Offset, innerR: Float, outerR: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val dot = with (this) { 3.dp.toPx() }
        val gap = with (this) { 10.dp.toPx() }

        fun drawDotted(radius: Float, color: Color) {
            val circumference = 2 * Math.PI * radius
            val count = (circumference / (dot + gap)).roundToInt().coerceAtLeast(24)
            repeat(count) { i ->
                val t = i.toFloat() / count.toFloat() * (2f * Math.PI).toFloat()
                val x = center.x + cos(t) * radius
                val y = center.y + sin(t) * radius
                drawCircle(color, dot / 2, Offset(x, y))
            }
        }

        drawDotted(innerR, Color(0x55FFFFFF))
        drawDotted(outerR, Color(0x66FFFFFF))
    }
}

@Composable
private fun SelectionLine(
    from: Offset?, to: Offset?
) {
    if (from != null && to != null) {
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                Color(0x88FFFFFF),
                start = from, end = to, strokeWidth = 4f
            )
        }
    }
}

@Composable
fun BottomAnswerBar(
    enabled: Boolean,
    onAnswer: () -> Unit,
    onSkip: () -> Unit,
    onHint: () -> Unit
) {
    Box(Modifier.fillMaxWidth().padding(16.dp)) {
        // Skip (left)
        TextButton(
            onClick = onSkip,
            modifier = Modifier.align(Alignment.CenterStart)
        ) { Text(stringResource(R.string.skip)) }

        // Answer (center)
        Button(
            onClick = onAnswer,
            enabled = enabled,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .height(56.dp)
                .fillMaxWidth(0.6f)
        ) {
            Text("Answer")
        }

        // Hint (right)
        IconButton(
            onClick = onHint,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp)
        ) { Icon(Icons.Default.Lightbulb, null, tint = Color.Yellow) }
    }
}


@Composable
fun MultiMatchMusicPuzzleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "MultiMatchMusicWrapper"
    Log.d(TAG, "🎵 Loading multi-match music puzzle: ${currentPuzzle.puzzleId}")

    // State for puzzle data
    var puzzleData by remember { mutableStateOf<MultiMatchMusicData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Parse puzzle data
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            Log.d(TAG, "📄 Raw question data: ${currentPuzzle.question}")

            val questionJson = JSONObject(currentPuzzle.question ?: "{}")
            Log.d(TAG, "📋 Question JSON keys: ${questionJson.keys().asSequence().toList()}")

            // Extract basic information
            val puzzleId = questionJson.optString("puzzleId", currentPuzzle.puzzleId)
            val theme = questionJson.optString("theme", "Music Quiz")
            val description = questionJson.optString("description", "Match songs to their titles")
            val totalQuestions = questionJson.optInt("totalTracks", 8)
            val timeLimit = questionJson.optLong("timeLimit", 360000)

            // Parse game settings
            val gameSettingsJson = questionJson.optJSONObject("gameSettings") ?: JSONObject()
            val gameSettings = MusicGameSettings(
                autoPlay = gameSettingsJson.optBoolean("autoPlay", true),
                allowReplay = gameSettingsJson.optBoolean("allowReplay", true),
                shuffleQuestions = gameSettingsJson.optBoolean("shuffleQuestions", true),
                showProgress = gameSettingsJson.optBoolean("showProgress", true),
                previewDuration = gameSettingsJson.optInt("previewDuration", 30)
            )

            // Parse questions
            val questionsArray = questionJson.optJSONArray("questions") ?: JSONArray()
            val questions = mutableListOf<MusicQuestion>()

            for (i in 0 until questionsArray.length()) {
                val questionObj = questionsArray.getJSONObject(i)
                val metadataObj = questionObj.optJSONObject("metadata") ?: JSONObject()

                val question = MusicQuestion(
                    id = questionObj.optInt("id", i + 1),
                    audioUrl = questionObj.optString("audioUrl", ""),
                    question = questionObj.optString("question", "What song is this?"),
                    answer = questionObj.optString("answer", ""),
                    options = questionObj.optJSONArray("options")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList(),
                    hint = questionObj.optString("hint", ""),
                    metadata = MusicMetadata(
                        deezerId = metadataObj.optLong("deezerId", 0),
                        duration = metadataObj.optInt("duration", 30),
                        albumImageUrl = metadataObj.optString("albumImageUrl", ""),
                        requestedArtist = metadataObj.optString("requestedArtist", ""),
                        requestedTitle = metadataObj.optString("requestedTitle", "")
                    )
                )

                questions.add(question)
                Log.d(TAG, "🎵 Question ${question.id}: ${question.answer}")
                Log.d(TAG, "  🎵 Audio URL: ${question.audioUrl}")
                Log.d(TAG, "  🎤 Artist: ${question.metadata.requestedArtist}")
            }

            puzzleData = MultiMatchMusicData(
                puzzleId = puzzleId,
                theme = theme,
                description = description,
                questions = questions,
                totalQuestions = totalQuestions,
                timeLimit = timeLimit,
                gameSettings = gameSettings
            )

            Log.d(TAG, "✅ Successfully parsed multi-match music puzzle:")
            Log.d(TAG, "  Theme: $theme")
            Log.d(TAG, "  Total questions: $totalQuestions")
            Log.d(TAG, "  Time limit: ${timeLimit / 1000}s")

            isLoading = false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing puzzle data: ${e.message}", e)
            errorMessage = "Failed to load puzzle: ${e.message}"
            hasError = true
            isLoading = false
        }
    }

    when {
        isLoading -> {
            LoadingScreen(message = "Loading music puzzle...")
        }

        hasError -> {
            MusicErrorScreen(
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
            MultiMatchMusicPuzzleScreen(
                puzzleData = puzzleData!!,
                difficulty = currentPuzzle.difficulty ?: "Medium",
                round = "ROUND ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber} of ${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
                onPuzzleComplete = { score, correctAnswers ->
                    Log.d(TAG, "🎉 Music puzzle completed! Score: $score, Correct: $correctAnswers")
                    val isComplete = correctAnswers >= puzzleData!!.totalQuestions
                    handlePuzzleCompletion(isComplete, isComplete, score)
                },
                onBack = onBack
            )
        }
    }
}

@Composable
fun MultiMatchMusicPuzzleScreen(
    puzzleData: MultiMatchMusicData,
    difficulty: String,
    round: String,
    onPuzzleComplete: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "MultiMatchMusicScreen"

    // --- Ring rotation offsets ---
    var qRotation by remember { mutableStateOf(0f) } // inner ring
    var aRotation by remember { mutableStateOf(0f) } // outer ring

    // --- Game state ---
    var questions by remember { mutableStateOf(puzzleData.questions) }
    var correctAnswers by remember { mutableStateOf(0) }
    var wrongAnswers by remember { mutableStateOf(0) }
    var timeRemaining by remember { mutableStateOf(puzzleData.timeLimit / 1000) }
    var gameCompleted by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf<String?>(null) }
    var feedbackColor by remember { mutableStateOf(Color.Green) }

    // --- Circle state ---
    var centerPosition by remember { mutableStateOf(Offset.Zero) }
    var circleRadius by remember { mutableStateOf(0f) }
    var questionItems by remember { mutableStateOf<List<CircleItem>>(emptyList()) }
    var answerItems by remember { mutableStateOf<List<CircleItem>>(emptyList()) }
    var selectedQuestion by remember { mutableStateOf<CircleItem?>(null) }
    var selectedAnswer by remember { mutableStateOf<CircleItem?>(null) }

    // --- Audio state ---
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentPlayingId by remember { mutableStateOf<Int?>(null) }
    var audioState by remember { mutableStateOf(AudioState.STOPPED) }
    var playbackProgress by remember { mutableStateOf(0f) }

    // Helper: evenly spaced base angles, start at top
    fun baseAngleFor(index: Int, count: Int): Float =
        90f + index * (360f / count.coerceAtLeast(1))

    // Normalize to [-180, 180] to avoid big jumps
    fun normalizeDelta(delta: Float): Float {
        var d = ((delta + 540f) % 360f) - 180f
        if (d < -180f) d += 360f
        return d
    }

    // ---- Init lists (angle is irrelevant; we compute derived angles at draw time) ----
    LaunchedEffect(puzzleData) {
        val remaining = questions.filter { !it.isAnswered }
        questionItems = remaining.mapIndexed { idx, q ->
            CircleItem(
                id = q.id,
                text = "♪ ${q.id}",
                audioUrl = q.audioUrl,
                isQuestion = true,
                angle = 0f,             // ignored later
                originalIndex = idx
            )
        }

        val answers = remaining.map { it.answer }.shuffled()
        answerItems = answers.mapIndexed { idx, ans ->
            val qId = remaining.firstOrNull { it.answer == ans }?.id ?: 0
            CircleItem(
                id = qId,
                text = ans,
                isQuestion = false,
                angle = 0f,             // ignored later
                originalIndex = idx
            )
        }
    }

    // ---- Timer ----
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0 && !gameCompleted) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining <= 0 && !gameCompleted) {
            gameCompleted = true
            val score = calculateMusicScore(correctAnswers, puzzleData.totalQuestions, wrongAnswers, timeRemaining)
            onPuzzleComplete(score, correctAnswers)
        }
    }

    // ---- Audio progress tracking ----
    LaunchedEffect(audioState) {
        if (audioState == AudioState.PLAYING && mediaPlayer != null) {
            while (audioState == AudioState.PLAYING) {
                try {
                    val cur = mediaPlayer?.currentPosition ?: 0
                    val dur = mediaPlayer?.duration ?: 1
                    playbackProgress = cur.toFloat() / dur.toFloat()
                    delay(100)
                } catch (_: Exception) { break }
            }
        }
    }

    // ---- Cleanup ----
    DisposableEffect(Unit) { onDispose { mediaPlayer?.release() } }

    // ---- Feedback auto hide ----
    LaunchedEffect(showFeedback) {
        if (showFeedback != null) {
            delay(2000)
            showFeedback = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        MultiMatchMusicHeader(
            difficulty = difficulty,
            round = round,
            timeRemaining = timeRemaining,
            correctAnswers = correctAnswers,
            totalQuestions = puzzleData.totalQuestions,
            wrongAnswers = wrongAnswers,
            theme = puzzleData.theme,
            description = puzzleData.description,
            onBack = onBack
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { coords ->
                    centerPosition = Offset(coords.size.width / 2f, coords.size.height / 2f)
                    circleRadius = min(coords.size.width, coords.size.height) / 3f
                }
        ) {
            if (circleRadius > 0f && centerPosition != Offset.Zero) {
                OrbitRings(centerPosition, circleRadius * 0.6f, circleRadius)

                // ---- INNER RING (questions) ----
                val qRemaining = questionItems.filter { !it.isAnswered }
                qRemaining.forEachIndexed { idx, raw ->
                    val derivedAngle = baseAngleFor(idx, qRemaining.size) + qRotation
                    val item = raw.copy(angle = derivedAngle)

                    QuestionCircleItem(
                        item = item,
                        centerPosition = centerPosition,
                        radius = circleRadius * 0.6f,
                        isSelected = selectedQuestion?.id == item.id,
                        isPlaying = currentPlayingId == item.id && audioState == AudioState.PLAYING,
                        playbackProgress = if (currentPlayingId == item.id) playbackProgress else 0f,
                        onClick = {
                            selectedQuestion = if (selectedQuestion?.id == item.id) null else item
                            selectedAnswer = null
                        },
                        onPlayPause = {
                            if (currentPlayingId == item.id && audioState == AudioState.PLAYING) {
                                mediaPlayer?.pause()
                                audioState = AudioState.PAUSED
                            } else {
                                mediaPlayer?.release()
                                currentPlayingId = item.id
                                audioState = AudioState.LOADING
                                try {
                                    mediaPlayer = MediaPlayer().apply {
                                        setDataSource(item.audioUrl ?: "")
                                        prepareAsync()
                                        setOnPreparedListener { start(); audioState = AudioState.PLAYING }
                                        setOnCompletionListener {
                                            audioState = AudioState.STOPPED
                                            currentPlayingId = null
                                            playbackProgress = 0f
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            audioState = AudioState.STOPPED
                                            currentPlayingId = null
                                            true
                                        }
                                    }
                                } catch (_: Exception) {
                                    audioState = AudioState.STOPPED
                                    currentPlayingId = null
                                }
                            }
                        },
                        // Convert absolute angle -> delta, then rotate the whole ring
                        onDrag = { newAbsAngle ->
                            val curAbs = derivedAngle
                            val delta = normalizeDelta(newAbsAngle - curAbs)
                            qRotation = (qRotation + delta + 360f) % 360f
                        },
                        modifier = Modifier
                    )
                }

                // ---- OUTER RING (answers) ----
                val aRemaining = answerItems.filter { !it.isAnswered }
                aRemaining.forEachIndexed { idx, raw ->
                    val derivedAngle = baseAngleFor(idx, aRemaining.size) + aRotation
                    val item = raw.copy(angle = derivedAngle)

                    AnswerCircleItem(
                        item = item,
                        centerPosition = centerPosition,
                        radius = circleRadius,
                        isSelected = selectedAnswer?.id == item.id,
                        onClick = {
                            selectedAnswer = if (selectedAnswer?.id == item.id) null else item
                            selectedQuestion = null
                        },
                        onDrag = { newAbsAngle ->
                            val curAbs = derivedAngle
                            val delta = normalizeDelta(newAbsAngle - curAbs)
                            aRotation = (aRotation + delta + 360f) % 360f
                        },
                        modifier = Modifier
                    )
                }

                // ---- Center prompt ----
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(2f)
                        .size(180.dp)
                        .background(Color(0x33000000), CircleShape)
                        .border(3.dp, Color.White, CircleShape)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Which track matches?",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ---- Feedback overlay ----
            showFeedback?.let { feedback ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(feedbackColor.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = feedback,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ---- Bottom controls ----
        BottomAnswerBar(
            enabled = selectedQuestion != null && selectedAnswer != null,
            onAnswer = {
                if (selectedQuestion != null && selectedAnswer != null) {
                    val q = questions.find { it.id == selectedQuestion!!.id }
                    val isCorrect = q?.answer == selectedAnswer!!.text
                    if (isCorrect) {
                        correctAnswers++
                        feedbackColor = Color.Green
                        showFeedback = "Correct! 🎉"

                        questionItems = questionItems.map { it ->
                            if (it.id == selectedQuestion!!.id) it.copy(isAnswered = true) else it
                        }
                        answerItems = answerItems.map { it ->
                            if (it.id == selectedAnswer!!.id) it.copy(isAnswered = true) else it
                        }
                        questions = questions.map { qq ->
                            if (qq.id == selectedQuestion!!.id) qq.copy(isAnswered = true, isCorrect = true) else qq
                        }

                        mediaPlayer?.release(); mediaPlayer = null
                        currentPlayingId = null
                        audioState = AudioState.STOPPED

                        if (correctAnswers >= puzzleData.totalQuestions) {
                            gameCompleted = true
                            val score = calculateMusicScore(
                                correctAnswers, puzzleData.totalQuestions, wrongAnswers, timeRemaining
                            )
                            onPuzzleComplete(score, correctAnswers)
                        }
                    } else {
                        wrongAnswers++
                        feedbackColor = Color.Red
                        showFeedback = "Wrong! Try again 🤔"
                    }
                    selectedQuestion = null
                    selectedAnswer = null
                }
            },
            onSkip = {
                val score = calculateMusicScore(
                    correctAnswers, puzzleData.totalQuestions, wrongAnswers, timeRemaining
                )
                onPuzzleComplete(score, correctAnswers)
            },
            onHint = {}
        )

        if (puzzleData.gameSettings.showProgress) {
            LinearProgressIndicator(
                progress = correctAnswers.toFloat() / puzzleData.totalQuestions,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                color = Color.Green,
                trackColor = Color.Gray
            )
        }
    }
}


@Composable
fun QuestionCircleItem(
    item: CircleItem,
    centerPosition: Offset,
    radius: Float,
    isSelected: Boolean,
    isPlaying: Boolean,
    playbackProgress: Float,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var myOffsetInParent by remember { mutableStateOf(Offset.Zero) }

    val itemPosition = remember(item.angle, centerPosition, radius) {
        val angleRad = Math.toRadians(item.angle.toDouble())
        Offset(
            x = centerPosition.x + cos(angleRad).toFloat() * radius,
            y = centerPosition.y + sin(angleRad).toFloat() * radius
        )
    }

    val posPx = remember(item.angle, centerPosition, radius) {
        val r = Math.toRadians(item.angle.toDouble())
        Offset(
            x = centerPosition.x + cos(r).toFloat() * radius,
            y = centerPosition.y + sin(r).toFloat() * radius
        )
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                myOffsetInParent = coords.positionInParent()
            }
            .offset { IntOffset((posPx.x - 40f).roundToInt(), (posPx.y - 40f).roundToInt()) }
            .size(80.dp) // or .size(100.dp, 50.dp) for answers
            .background(
                if (isSelected) Color.Red else if (isPlaying) Color.Blue else Color.Gray,
                CircleShape
            )
            .border(if (isSelected) 3.dp else 1.dp, Color.White, CircleShape)
            .clickable(onClick = onClick)
            .pointerInput(item.id) {
                detectDragGestures(onDrag = { change, _ ->
                    val absolute = myOffsetInParent + change.position // now in parent space
                    val newAngle = angleFrom(centerPosition, absolute)
                    onDrag(((newAngle + 360f) % 360f))
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Play/pause button
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Question number
            Text(
                text = item.id.toString(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Progress ring
        if (isPlaying && playbackProgress > 0) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer {
                        rotationZ = -90f // Start from top
                    }
            ) {
                // This would need a custom progress ring implementation
                // For now, showing a simple indicator
            }
        }
    }
}

@Composable
fun AnswerCircleItem(
    item: CircleItem,
    centerPosition: Offset,
    radius: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    var myOffsetInParent by remember { mutableStateOf(Offset.Zero) }
    val itemPosition = remember(item.angle, centerPosition, radius) {
        val angleRad = Math.toRadians(item.angle.toDouble())
        Offset(
            x = centerPosition.x + cos(angleRad).toFloat() * radius,
            y = centerPosition.y + sin(angleRad).toFloat() * radius
        )
    }

    val posPx = remember(item.angle, centerPosition, radius) {
        val r = Math.toRadians(item.angle.toDouble())
        Offset(
            x = centerPosition.x + cos(r).toFloat() * radius,
            y = centerPosition.y + sin(r).toFloat() * radius
        )
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                myOffsetInParent = coords.positionInParent()
            }
            .offset {
                IntOffset((posPx.x - 50f).roundToInt(), (posPx.y - 25f).roundToInt())
            }
            .size(width = 100.dp, height = 50.dp)
            .background(
                if (isSelected) Color.Green else Color.White,
                RoundedCornerShape(25.dp)
            )
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color.White else Color.Gray,
                shape = RoundedCornerShape(25.dp)
            )
            .clickable { onClick() }
            .pointerInput(item.id) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        val absolute = myOffsetInParent + change.position // now in parent space
                        val newAngle = angleFrom(centerPosition, absolute)
                        onDrag(newAngle)
                    }
                )
            }
        ,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.text.split(" - ").firstOrNull() ?: item.text,
            color = if (isSelected) Color.White else Color.Black,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun MultiMatchMusicHeader(
    difficulty: String,
    round: String,
    timeRemaining: Long,
    correctAnswers: Int,
    totalQuestions: Int,
    wrongAnswers: Int,
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Lives indicator
                repeat(3) { index ->
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "Life",
                        tint = if (index < (3 - wrongAnswers.coerceAtMost(3))) Color.Red else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = formatTime(timeRemaining),
                    color = if (timeRemaining < 30) Color.Red else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Title and progress
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
            horizontalArrangement = Arrangement.SpaceBetween
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
fun MultiMatchBottomControls(
    selectedQuestion: CircleItem?,
    selectedAnswer: CircleItem?,
    questionsRemaining: Int,
    onSubmit: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Skip button
        Button(
            onClick = onSkip,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
            modifier = Modifier.size(width = 80.dp, height = 48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = stringResource(R.string.skip),
                    modifier = Modifier.size(16.dp)
                )
                Text(stringResource(R.string.skip), fontSize = 12.sp)
            }
        }

        // Answer button
        Button(
            onClick = onSubmit,
            enabled = selectedQuestion != null && selectedAnswer != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedQuestion != null && selectedAnswer != null)
                    Color.Green else Color.Gray
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .height(48.dp)
        ) {
            Text(
                text = "Answer",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Hint button
        IconButton(
            onClick = { /* Show hint */ },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = stringResource(R.string.hint),
                tint = Color.Yellow,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun MusicErrorScreen(
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
private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun calculateMusicScore(
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
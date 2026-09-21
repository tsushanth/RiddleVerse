// MultiMatchMusicPuzzleScreen.kt - Circular Multi-Match Music Puzzle
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.testTag
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

        drawDotted(innerR, RvInkSoft.copy(alpha = 0.35f))
        drawDotted(outerR, RvInkSoft.copy(alpha = 0.45f))
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
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Skip (left)
        TextButton(
            onClick = onSkip,
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text(stringResource(R.string.skip), fontSize = 14.sp, color = RvInk, maxLines = 1) }

        // Answer (center, primary action)
        Button(
            onClick = onAnswer,
            enabled = enabled,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RvViolet,
                contentColor = RvOnTone,
                disabledContainerColor = RvDisabled,
                disabledContentColor = RvInkSoft
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
        ) {
            Text("Answer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // Hint (right)
        IconButton(
            onClick = onHint,
            modifier = Modifier.size(48.dp)
        ) { Icon(Icons.Default.Lightbulb, stringResource(R.string.hint), tint = RvSunEdge) }
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
    var feedbackColor by remember { mutableStateOf(RvSuccess) }

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

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(RvCanvas),
        contentAlignment = Alignment.TopCenter
    ) {
    val compactScreen = maxHeight < 600.dp
    // Ring geometry is derived from the measured play area (see onGloballyPositioned below).
    val density = LocalDensity.current
    val outerR = with(density) { circleRadius.toDp() }
    val questionDiameter = (outerR * 0.6f * 0.75f).coerceIn(48.dp, 80.dp)
    val pillHeight = (outerR * 0.4f).coerceIn(40.dp, 50.dp)
    val ringN = maxOf(questionItems.count { !it.isAnswered }, 1)
    val pillWidth = (outerR.value * 2f * Math.PI.toFloat() / ringN * 0.9f).coerceIn(72f, 110f).dp
    Column(
        modifier = Modifier.fillMaxHeight().widthIn(max = 1000.dp).fillMaxWidth()
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
                    // Outer ring radius: as large as fits, leaving room for the answer pills.
                    val pillReserve = with(density) { 56.dp.toPx() }
                    circleRadius = (min(coords.size.width, coords.size.height) / 2f - pillReserve)
                        .coerceAtLeast(min(coords.size.width, coords.size.height) / 4f)
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
                        diameter = questionDiameter,
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
                        itemWidth = pillWidth,
                        itemHeight = pillHeight,
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
                        .size((outerR * 1.2f - questionDiameter - 8.dp).coerceIn(72.dp, 180.dp))
                        .background(RvSurface, CircleShape)
                        .border(3.dp, RvOutline, CircleShape)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Which track matches?",
                        color = RvInk,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        fontSize = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
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
                        color = RvInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ---- Bottom controls ----
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BottomAnswerBar(
            enabled = selectedQuestion != null && selectedAnswer != null,
            onAnswer = {
                if (selectedQuestion != null && selectedAnswer != null) {
                    val q = questions.find { it.id == selectedQuestion!!.id }
                    val isCorrect = q?.answer == selectedAnswer!!.text
                    if (isCorrect) {
                        correctAnswers++
                        feedbackColor = RvSuccess
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
                        feedbackColor = RvError
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
        }

        if (puzzleData.gameSettings.showProgress && !compactScreen) {
            LinearProgressIndicator(
                progress = correctAnswers.toFloat() / puzzleData.totalQuestions,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = RvSuccess,
                trackColor = RvOutline
            )
        }
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
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 80.dp
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
                val half = diameter.toPx() / 2f
                IntOffset((posPx.x - half).roundToInt(), (posPx.y - half).roundToInt())
            }
            .size(diameter)
            .testTag("question_circle_${item.id}")
            .background(
                if (isSelected) RvCoral else if (isPlaying) RvSky else RvSurfaceRaised,
                CircleShape
            )
            .border(if (isSelected) 4.dp else 2.dp, if (isSelected) RvInk else RvInkSoft, CircleShape)
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
                modifier = Modifier.size(if (diameter < 64.dp) 24.dp else 32.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = RvInk,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Question number
            Text(
                text = item.id.toString(),
                color = RvInk,
                fontSize = 14.sp,
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
    modifier: Modifier = Modifier,
    itemWidth: androidx.compose.ui.unit.Dp = 100.dp,
    itemHeight: androidx.compose.ui.unit.Dp = 50.dp
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
                IntOffset(
                    (posPx.x - itemWidth.toPx() / 2f).roundToInt(),
                    (posPx.y - itemHeight.toPx() / 2f).roundToInt()
                )
            }
            .size(width = itemWidth, height = itemHeight)
            .testTag("answer_pill_${item.text}")
            .background(
                if (isSelected) RvSuccess else RvSurfaceRaised,
                RoundedCornerShape(25.dp)
            )
            .border(
                width = if (isSelected) 4.dp else 2.dp,
                color = if (isSelected) RvInk else RvInkSoft,
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
            color = if (isSelected) RvInk else RvInk,
            fontSize = 12.sp,
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
        modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Single HUD row: back, difficulty/round, lives, timer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = RvInk)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = difficulty.uppercase(),
                    color = RvInk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = round,
                    color = RvInkSoft,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Lives indicator
                repeat(3) { index ->
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "Life",
                        tint = if (index < (3 - wrongAnswers.coerceAtMost(3))) RvCoralEdge else RvOutline,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = formatTime(timeRemaining),
                    color = if (timeRemaining < 30) RvCoralEdge else RvInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                )
            }
        }

        // Theme + progress on one line (description dropped: not needed to play)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = theme,
                color = RvInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Star,
                contentDescription = "Progress",
                tint = RvSunEdge,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "$correctAnswers/$totalQuestions",
                color = RvInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
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
            colors = ButtonDefaults.buttonColors(containerColor = RvInkSoft),
            modifier = Modifier.size(width = 96.dp, height = 48.dp)
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
                    RvSuccess else RvInkSoft
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
                tint = RvSunEdge,
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
            .background(RvCanvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⚠️ Error",
                color = RvError,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = message,
                color = RvInk,
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
                    colors = ButtonDefaults.buttonColors(containerColor = RvInkSoft)
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
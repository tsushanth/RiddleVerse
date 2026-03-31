package com.kreativekoala.riddleverse

import android.content.ContentValues.TAG
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.compose.ui.res.stringResource
import kotlin.math.*
import kotlin.random.Random

enum class MemoryRetentionPhase {
    AUDIO_INTRO,
    LISTENING_FACTS,
    FINAL_FEEDBACK
}

data class RetentionSubject(
    val id: String,
    val name: String,
    val description: String,
    val color: Color = generateSubjectColor()
)

data class RetentionFact(
    val id: String,
    val text: String,
    val correctSubject: String,
    val showTiming: Long,
    val isVisible: Boolean = false,
    val isAnswered: Boolean = false,
    val wasCorrect: Boolean = false
)

data class FactDragState(
    val factId: String,
    val offset: Offset = Offset.Zero,
    val isDragging: Boolean = false
)

@Composable
fun MemoryRetentionPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "3:00",
    hearts: Int = 3,
    level: String = "1/5",
    puzzleData: String,
    correctAnswer: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val TAG = "MemoryRetention"

    // Score tracking state
    var totalScore by remember { mutableIntStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var listeningStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var factAnswerTimes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var dragInteractions by remember { mutableIntStateOf(0) }

    var currentPhase by remember { mutableStateOf(MemoryRetentionPhase.AUDIO_INTRO) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var audioProgress by remember { mutableStateOf(0L) }
    var audioDuration by remember { mutableStateOf(0L) }

    var subjects by remember { mutableStateOf(listOf<RetentionSubject>()) }
    var facts by remember { mutableStateOf(listOf<RetentionFact>()) }
    var factDragStates by remember { mutableStateOf(mapOf<String, FactDragState>()) }

    var correctAnswers by remember { mutableIntStateOf(0) }
    var totalAnswered by remember { mutableIntStateOf(0) }

    val haptics = LocalHapticFeedback.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()
    val coroutineScope = rememberCoroutineScope()

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            180 // Default 3 minutes
        }
    }

    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, currentPhase) {
        if (timeRemaining > 0 && currentPhase != MemoryRetentionPhase.FINAL_FEEDBACK) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && currentPhase != MemoryRetentionPhase.FINAL_FEEDBACK) {
            // Time's up - complete with current score
            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            currentPhase = MemoryRetentionPhase.FINAL_FEEDBACK
        }
    }

    // Parse puzzle data
    val parsedData = remember(puzzleData, correctAnswer) {
        try {
            val questionData = JSONObject(puzzleData)
            val answerData = JSONObject(correctAnswer)

            val topic = questionData.getString("topic")
            val essay = questionData.getString("essay")
            val audioUrl = questionData.optString("audioUrl", null)
            val estimatedDuration = questionData.optLong("estimatedDuration", 60000L)

            // Parse subjects
            val subjectsArray = questionData.getJSONArray("subjects")
            val subjectsList = mutableListOf<RetentionSubject>()
            for (i in 0 until subjectsArray.length()) {
                val subjectObj = subjectsArray.getJSONObject(i)
                subjectsList.add(
                    RetentionSubject(
                        id = subjectObj.getString("id"),
                        name = subjectObj.getString("name"),
                        description = subjectObj.getString("description")
                    )
                )
            }

            // Parse facts
            val factsArray = questionData.getJSONArray("facts")
            val factsList = mutableListOf<RetentionFact>()
            for (i in 0 until factsArray.length()) {
                val factObj = factsArray.getJSONObject(i)
                factsList.add(
                    RetentionFact(
                        id = factObj.getString("id"),
                        text = factObj.getString("text"),
                        correctSubject = factObj.getString("correctSubject"),
                        showTiming = factObj.getLong("showTiming")
                    )
                )
            }

            Log.d(TAG, "📊 Parsed memory retention puzzle:")
            Log.d(TAG, "   Topic: $topic")
            Log.d(TAG, "   Subjects: ${subjectsList.size}")
            Log.d(TAG, "   Facts: ${factsList.size}")
            Log.d(TAG, "   Audio URL: $audioUrl")
            Log.d(TAG, "   Estimated duration: ${estimatedDuration}ms")

            mapOf(
                "topic" to topic,
                "essay" to essay,
                "subjects" to subjectsList,
                "facts" to factsList,
                "audioUrl" to audioUrl,
                "estimatedDuration" to estimatedDuration,
                "success" to true
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse memory retention puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (!isValidData) {
        LaunchedEffect(Unit) {
            Log.e(TAG, "❌ Invalid memory retention puzzle data")
            onSubmitAnswer(false)
        }
        return
    }

    @Suppress("UNCHECKED_CAST")
    val topic = parsedData["topic"] as String
    @Suppress("UNCHECKED_CAST")
    val essay = parsedData["essay"] as String
    val audioUrl = parsedData["audioUrl"] as? String
    val estimatedDuration = parsedData["estimatedDuration"] as Long

    // Initialize subjects and facts
    LaunchedEffect(parsedData) {
        @Suppress("UNCHECKED_CAST")
        subjects = parsedData["subjects"] as List<RetentionSubject>
        @Suppress("UNCHECKED_CAST")
        facts = parsedData["facts"] as List<RetentionFact>

        factDragStates = facts.associate { fact ->
            fact.id to FactDragState(factId = fact.id)
        }
    }

    // Calculate retention memory score
    fun calculateRetentionMemoryScore(
        correctCount: Int,
        totalFacts: Int,
        avgResponseTime: Long,
        listeningDuration: Long,
        difficulty: String,
        dragInteractions: Int
    ): Int {
        if (correctCount == 0) return 0

        val basePointsPerFact = when (difficulty.lowercase()) {
            "easy" -> 25
            "medium" -> 35
            "hard" -> 45
            "expert" -> 55
            else -> 35
        }

        val baseScore = correctCount * basePointsPerFact

        // Information processing multiplier
        val processingMultiplier = when (totalFacts) {
            in 1..3 -> 1.0f
            in 4..6 -> 1.3f
            in 7..10 -> 1.6f
            else -> 2.0f
        }

        // Accuracy bonus
        val accuracy = correctCount.toFloat() / totalFacts
        val accuracyBonus = when {
            accuracy >= 0.9f -> (baseScore * 0.4f).toInt()
            accuracy >= 0.8f -> (baseScore * 0.25f).toInt()
            accuracy >= 0.7f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Response speed bonus
        val avgResponseSeconds = avgResponseTime / 1000f
        val speedBonus = when {
            avgResponseSeconds <= 3f -> (baseScore * 0.3f).toInt()
            avgResponseSeconds <= 5f -> (baseScore * 0.2f).toInt()
            avgResponseSeconds <= 8f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Listening attention bonus
        val listeningSeconds = listeningDuration / 1000f
        val attentionBonus = when {
            listeningSeconds >= 30f -> (baseScore * 0.2f).toInt()
            listeningSeconds >= 20f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Interaction efficiency bonus
        val efficiencyBonus = when {
            dragInteractions <= totalFacts * 2 -> (baseScore * 0.15f).toInt()
            dragInteractions <= totalFacts * 3 -> (baseScore * 0.05f).toInt()
            else -> 0
        }

        // Cognitive load bonus for multitasking
        val cognitiveBonus = (baseScore * 0.15f).toInt()

        val finalScore = ((baseScore * processingMultiplier).toInt() + accuracyBonus + speedBonus + attentionBonus + efficiencyBonus + cognitiveBonus)

        Log.d(TAG, "🏆 Retention memory score calculation:")
        Log.d(TAG, "  Correct: $correctCount/$totalFacts")
        Log.d(TAG, "  Base score: $baseScore")
        Log.d(TAG, "  Processing multiplier: ${processingMultiplier}x")
        Log.d(TAG, "  Accuracy bonus: $accuracyBonus")
        Log.d(TAG, "  Speed bonus: $speedBonus (avg ${avgResponseSeconds}s)")
        Log.d(TAG, "  Attention bonus: $attentionBonus (listened ${listeningSeconds}s)")
        Log.d(TAG, "  Efficiency bonus: $efficiencyBonus ($dragInteractions drags)")
        Log.d(TAG, "  Cognitive bonus: $cognitiveBonus")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, baseScore / 4)
    }

    LaunchedEffect(facts) {
        Log.d("MemoryRetention", "🔍 Current facts state:")
        facts.forEachIndexed { index, fact ->
            Log.d("MemoryRetention", "   Fact $index: visible=${fact.isVisible}, timing=${fact.showTiming}ms, text=${fact.text}")
        }
        Log.d("MemoryRetention", "🕐 Current audio progress: ${audioProgress}ms")
    }

    // Audio progress tracking
    LaunchedEffect(isAudioPlaying, mediaPlayer) {
        while (isAudioPlaying && mediaPlayer != null) {
            try {
                val currentMediaPlayer = mediaPlayer

                if (currentMediaPlayer?.isPlaying == true) {
                    val currentPosition = currentMediaPlayer.currentPosition.toLong()
                    audioProgress = currentPosition

                    facts = facts.map { fact ->
                        if (!fact.isVisible && currentPosition >= fact.showTiming) {
                            Log.d(TAG, "🎯 Showing fact: ${fact.text} at ${currentPosition}ms")
                            fact.copy(isVisible = true)
                        } else {
                            fact
                        }
                    }
                } else if (currentMediaPlayer != null && currentMediaPlayer.isPlaying.not() && isAudioPlaying) {
                    Log.w(TAG, "⚠️ MediaPlayer not playing but isAudioPlaying is true")
                    audioProgress += 100L

                    facts = facts.map { fact ->
                        if (!fact.isVisible && audioProgress >= fact.showTiming) {
                            Log.d(TAG, "🎯 Showing fact (estimated): ${fact.text} at ${audioProgress}ms")
                            fact.copy(isVisible = true)
                        } else {
                            fact
                        }
                    }
                }

                delay(100)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error tracking audio progress: ${e.message}")
                audioProgress += 100L

                facts = facts.map { fact ->
                    if (!fact.isVisible && audioProgress >= fact.showTiming) {
                        Log.d(TAG, "🎯 Showing fact (fallback): ${fact.text} at ${audioProgress}ms")
                        fact.copy(isVisible = true)
                    } else {
                        fact
                    }
                }

                if (audioProgress > estimatedDuration + 5000) {
                    Log.w(TAG, "⚠️ Breaking audio progress loop due to repeated errors")
                    break
                }
            }
        }
    }

    // Cleanup media player
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        when (currentPhase) {
            MemoryRetentionPhase.AUDIO_INTRO -> {
                AudioIntroScreen(
                    onBegin = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentPhase = MemoryRetentionPhase.LISTENING_FACTS
                        listeningStartTime = System.currentTimeMillis()
                    },
                    onBack = onBack
                )
            }

            MemoryRetentionPhase.LISTENING_FACTS -> {
                EnhancedMemoryRetentionGameScreen(
                    topic = topic,
                    subjects = subjects,
                    facts = facts,
                    factDragStates = factDragStates,
                    audioUrl = audioUrl,
                    estimatedDuration = estimatedDuration,
                    audioProgress = audioProgress,
                    isAudioPlaying = isAudioPlaying,
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    hearts = hearts,
                    totalScore = totalScore,
                    correctAnswers = correctAnswers,
                    totalAnswered = totalAnswered,
                    onAudioStarted = {
                        Log.d(TAG, "🎵 Audio started")
                        isAudioPlaying = true
                    },
                    onAudioCompleted = {
                        Log.d(TAG, "🎵 Audio completed")
                        isAudioPlaying = false

                        facts = facts.map { it.copy(isVisible = true) }

                        coroutineScope.launch {
                            delay(2000)

                            // Calculate final score
                            val avgResponseTime = if (factAnswerTimes.isNotEmpty()) {
                                factAnswerTimes.values.average().toLong()
                            } else 3000L

                            val listeningDuration = System.currentTimeMillis() - listeningStartTime

                            totalScore = calculateRetentionMemoryScore(
                                correctCount = correctAnswers,
                                totalFacts = facts.size,
                                avgResponseTime = avgResponseTime,
                                listeningDuration = listeningDuration,
                                difficulty = difficulty,
                                dragInteractions = dragInteractions
                            )

                            currentPhase = MemoryRetentionPhase.FINAL_FEEDBACK
                        }
                    },
                    onMediaPlayerCreated = {
                        mediaPlayer = it
                        audioDuration = it.duration.toLong()
                    },
                    onFactDrag = { factId, offset ->
                        factDragStates = factDragStates.toMutableMap().apply {
                            put(factId, (get(factId) ?: FactDragState(factId)).copy(
                                offset = offset,
                                isDragging = true
                            ))
                        }
                        dragInteractions++
                    },
                    onFactDrop = { factId, subjectId ->
                        val fact = facts.find { it.id == factId }
                        if (fact != null && fact.isVisible && !fact.isAnswered) {
                            val isCorrect = fact.correctSubject == subjectId

                            // Track answer time
                            factAnswerTimes = factAnswerTimes + (factId to System.currentTimeMillis())

                            Log.d(TAG, "🎯 Fact dropped: ${fact.text} -> $subjectId (correct: $isCorrect)")

                            if (isCorrect) {
                                correctAnswers++
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }

                            totalAnswered++

                            facts = facts.map { f ->
                                if (f.id == factId) {
                                    f.copy(isAnswered = true, wasCorrect = isCorrect)
                                } else f
                            }
                        }

                        factDragStates = factDragStates.toMutableMap().apply {
                            put(factId, FactDragState(factId))
                        }
                    },
                    onBack = onBack
                )
            }

            MemoryRetentionPhase.FINAL_FEEDBACK -> {
                LaunchedEffect(Unit) {
                    val accuracy = if (totalAnswered > 0) {
                        (correctAnswers.toDouble() / totalAnswered * 100).toInt()
                    } else 0

                    val isSuccess = accuracy >= 70

                    Log.d(TAG, "🏁 Final results: $correctAnswers/$totalAnswered (${accuracy}%), score: $totalScore")

                    feedbackManager.showFeedback(
                        puzzleType = "retentionMemory",
                        isCorrect = isSuccess,
                        userAnswer = "$correctAnswers/$totalAnswered facts correct (${accuracy}%)",
                        correctAnswer = "Listen and categorize facts by subject",
                        timeSpent = System.currentTimeMillis() - gameStartTime,
                        difficulty = difficulty,
                        timeRemaining = timeRemaining,
                        totalTime = totalTimeSeconds,
                        onComplete = {
                            onSubmitAnswer(isSuccess)
                            Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                            fetchNextPuzzle(totalScore)
                        }
                    )
                }
            }
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
private fun AudioIntroScreen(
    onBegin: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "MEMORY RETENTION",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "REQUIRES AUDIO",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(60.dp))

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    Color.White.copy(alpha = 0.1f),
                    CircleShape
                )
                .scale(scale),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Audio",
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = "LISTEN TO THE ESSAY AND\nDRAG FACTS TO SUBJECTS",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onBegin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00BCD4)
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = stringResource(R.string.begin),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun EnhancedMemoryRetentionGameScreen(
    topic: String,
    subjects: List<RetentionSubject>,
    facts: List<RetentionFact>,
    factDragStates: Map<String, FactDragState>,
    audioUrl: String?,
    estimatedDuration: Long,
    audioProgress: Long,
    isAudioPlaying: Boolean,
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    hearts: Int,
    totalScore: Int,
    correctAnswers: Int,
    totalAnswered: Int,
    onAudioStarted: () -> Unit,
    onAudioCompleted: () -> Unit,
    onMediaPlayerCreated: (MediaPlayer) -> Unit,
    onFactDrag: (String, Offset) -> Unit,
    onFactDrop: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var draggedOverSubjectId by remember { mutableStateOf<String?>(null) }

    // Start audio when screen loads
    LaunchedEffect(audioUrl) {
        try {
            Log.d(TAG, "🎵 Starting audio setup: $audioUrl")

            if (!audioUrl.isNullOrEmpty() && audioUrl != "null" && audioUrl.startsWith("http")) {
                Log.d(TAG, "🔊 Starting audio playback: $audioUrl")

                val mediaPlayer = MediaPlayer().apply {
                    setOnPreparedListener { mp ->
                        Log.d(TAG, "✅ Audio prepared, duration: ${mp.duration}ms")
                        try {
                            mp.start()
                            onAudioStarted()
                            onMediaPlayerCreated(mp)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error starting audio playback", e)
                            mp.release()
                            coroutineScope.launch {
                                onAudioStarted()
                                delay(estimatedDuration)
                                onAudioCompleted()
                            }
                        }
                    }
                    setOnCompletionListener { mp ->
                        Log.d(TAG, "🎵 Audio completed")
                        try {
                            mp.release()
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error releasing MediaPlayer", e)
                        }
                        onAudioCompleted()
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "❌ Audio error: what=$what, extra=$extra")
                        try {
                            mp.release()
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error releasing MediaPlayer in error handler", e)
                        }
                        coroutineScope.launch {
                            onAudioStarted()
                            delay(estimatedDuration)
                            onAudioCompleted()
                        }
                        true
                    }
                    setDataSource(audioUrl)
                }

                try {
                    mediaPlayer.prepareAsync()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to prepare media player", e)
                    mediaPlayer.release()
                    coroutineScope.launch {
                        onAudioStarted()
                        delay(estimatedDuration)
                        onAudioCompleted()
                    }
                }
            } else {
                Log.w(TAG, "⚠️ Invalid or missing audio URL, using estimated timing fallback")
                coroutineScope.launch {
                    onAudioStarted()
                    delay(estimatedDuration)
                    onAudioCompleted()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio setup failed", e)
            coroutineScope.launch {
                onAudioStarted()
                delay(estimatedDuration)
                onAudioCompleted()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Enhanced top bar with score
        EnhancedRetentionTopBar(
            level = level,
            streakInfo = streakInfo,
            timer = timer,
            lives = hearts,
            totalScore = totalScore,
            correctAnswers = correctAnswers,
            totalAnswered = totalAnswered,
            onBack = onBack,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = topic,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "DRAG FACTS TO SUBJECTS",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Audio progress bar
        AudioProgressBar(
            progress = if (estimatedDuration > 0) audioProgress.toFloat() / estimatedDuration else 0f,
            isPlaying = isAudioPlaying,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Main game area with drop zone detection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            // Subject zones with individual feedback states
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                subjects.forEachIndexed { index, subject ->
                    var subjectFeedback by remember { mutableStateOf<String?>(null) }

                    SubjectZoneWithFeedback(
                        subject = subject,
                        feedbackState = subjectFeedback,
                        isDraggedOver = draggedOverSubjectId == subject.id,
                        modifier = Modifier.weight(1f),
                        onFactDropped = { factId ->
                            val fact = facts.find { it.id == factId }
                            if (fact != null) {
                                val isCorrect = fact.correctSubject == subject.id
                                subjectFeedback = if (isCorrect) "correct" else "incorrect"

                                coroutineScope.launch {
                                    delay(1000)
                                    subjectFeedback = null
                                }

                                onFactDrop(factId, subject.id)
                            }
                        }
                    )
                }
            }

            // Floating facts that appear at timed intervals
            facts.filter { it.isVisible && !it.isAnswered }.forEach { fact ->
                val dragState = factDragStates[fact.id] ?: FactDragState(fact.id)

                FloatingFactWithDropDetection(
                    fact = fact,
                    dragState = dragState,
                    subjects = subjects,
                    onDrag = { offset -> onFactDrag(fact.id, offset) },
                    onDrop = { droppedSubjectId ->
                        draggedOverSubjectId = null
                        if (droppedSubjectId != null) {
                            onFactDrop(fact.id, droppedSubjectId)
                        } else {
                            onFactDrag(fact.id, Offset.Zero)
                        }
                    },
                    onDragOver = { subjectId ->
                        draggedOverSubjectId = subjectId
                    },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                dragState.offset.x.roundToInt(),
                                dragState.offset.y.roundToInt()
                            )
                        }
                        .zIndex(if (dragState.isDragging) 1f else 0f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Status info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isAudioPlaying) "Listening..." else "Audio complete",
                fontSize = 14.sp,
                color = if (isAudioPlaying) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.5f)
            )

            val answeredCount = facts.count { it.isAnswered }
            val totalVisible = facts.count { it.isVisible }
            Text(
                text = "$answeredCount/$totalVisible facts",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SubjectZoneWithFeedback(
    subject: RetentionSubject,
    feedbackState: String?,
    isDraggedOver: Boolean = false,
    modifier: Modifier = Modifier,
    onFactDropped: (String) -> Unit
) {
    var isHighlighted by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .padding(8.dp)
            .border(
                width = if (isHighlighted || isDraggedOver) 3.dp else 1.dp,
                color = when {
                    feedbackState == "correct" -> Color.Green
                    feedbackState == "incorrect" -> Color.Red
                    isDraggedOver -> Color.Yellow
                    isHighlighted -> Color.White
                    else -> subject.color.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .let {
                if (feedbackState == "incorrect") {
                    val infiniteTransition = rememberInfiniteTransition(label = "blink")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(200),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                    it.graphicsLayer { this.alpha = alpha }
                } else it
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                feedbackState == "correct" -> Color.Green.copy(alpha = 0.3f)
                feedbackState == "incorrect" -> Color.Red.copy(alpha = 0.3f)
                isDraggedOver -> Color.Yellow.copy(alpha = 0.3f)
                else -> subject.color.copy(alpha = 0.2f)
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = subject.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subject.description,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (feedbackState != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (feedbackState == "correct") "✓" else "✗",
                    fontSize = 24.sp,
                    color = if (feedbackState == "correct") Color.Green else Color.Red
                )
            }
        }
    }
}

@Composable
private fun FloatingFactWithDropDetection(
    fact: RetentionFact,
    dragState: FactDragState,
    subjects: List<RetentionSubject>,
    onDrag: (Offset) -> Unit,
    onDrop: (String?) -> Unit,
    onDragOver: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var localOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    val initialPosition by remember {
        mutableStateOf(
            Offset(
                x = Random.nextFloat() * 200f + 50f,
                y = Random.nextFloat() * 100f + 300f
            )
        )
    }

    Card(
        modifier = modifier
            .width(280.dp)
            .offset {
                IntOffset(
                    (initialPosition.x + localOffset.x).roundToInt(),
                    (initialPosition.y + localOffset.y).roundToInt()
                )
            }
            .pointerInput(fact.id) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        localOffset = Offset.Zero
                        onDrag(Offset.Zero)
                    },
                    onDrag = { change, dragAmount ->
                        localOffset += dragAmount
                        onDrag(localOffset)

                        val currentPosition = Offset(
                            initialPosition.x + localOffset.x,
                            initialPosition.y + localOffset.y
                        )
                        val droppedSubject = detectDropTarget(currentPosition, subjects)
                        onDragOver(droppedSubject?.id)
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragOver(null)

                        val finalPosition = Offset(
                            initialPosition.x + localOffset.x,
                            initialPosition.y + localOffset.y
                        )

                        val droppedSubject = detectDropTarget(finalPosition, subjects)
                        onDrop(droppedSubject?.id)

                        localOffset = Offset.Zero
                        onDrag(Offset.Zero)
                    }
                )
            }
            .scale(if (isDragging) 1.05f else 1f)
            .graphicsLayer {
                shadowElevation = if (isDragging) 16.dp.toPx() else 8.dp.toPx()
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = if (isDragging) 0.9f else 0.95f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 16.dp else 8.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 8.dp)
            ) {
                val lineColor = Color.Gray
                val lineWidth = 2.dp.toPx()
                val spacing = 4.dp.toPx()

                repeat(4) { index ->
                    val y = index * spacing + spacing
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = lineWidth
                    )
                }
            }

            Text(
                text = fact.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun detectDropTarget(position: Offset, subjects: List<RetentionSubject>): RetentionSubject? {
    val screenWidth = 400f
    val zoneWidth = screenWidth / subjects.size

    val zoneIndex = (position.x / zoneWidth).toInt().coerceIn(0, subjects.size - 1)

    return if (position.y < 200f && position.y > 50f) {
        subjects.getOrNull(zoneIndex)
    } else {
        null
    }
}

@Composable
private fun AudioProgressBar(
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier.height(4.dp),
        color = if (isPlaying) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.3f),
        trackColor = Color.White.copy(alpha = 0.1f)
    )
}

@Composable
private fun EnhancedRetentionTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    totalScore: Int,
    correctAnswers: Int,
    totalAnswered: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Back button and level
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Level ${level.level}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    LevelProgressBar(
                        level = level,
                        modifier = Modifier.width(120.dp)
                    )
                }
            }

            // Center: Lives display
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(lives) {
                    Text(
                        text = "❤️",
                        fontSize = 16.sp
                    )
                }
            }

            // Right side: Timer and streak
            Column(
                horizontalAlignment = Alignment.End
            ) {
                val timeValue = timer.substringAfter(":").toIntOrNull() ?: 0
                val isUrgent = timer.startsWith("0:") && timeValue <= 30

                Text(
                    text = timer,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUrgent) Color.Red else Color.White
                )

                if (streakInfo.currentStreak > 0) {
                    StreakDisplay(
                        streakInfo = streakInfo,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Score and progress display
        if (totalScore > 0 || totalAnswered > 0) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (totalScore > 0) {
                        Text(
                            text = "Score: $totalScore",
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "🧠 Retention Memory",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )

                    if (totalAnswered > 0) {
                        Text(
                            text = "$correctAnswers/$totalAnswered facts",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private fun generateSubjectColor(): Color {
    val colors = listOf(
        Color(0xFF6B73FF), Color(0xFF9C27B0), Color(0xFF2196F3),
        Color(0xFF00BCD4), Color(0xFF4CAF50), Color(0xFF8BC34A),
        Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFFE91E63)
    )
    return colors[Random.nextInt(colors.size)]
}
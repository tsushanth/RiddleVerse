package com.kreativekoala.riddleverse

import android.content.ContentValues.TAG
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.res.stringResource
import kotlin.math.*
import kotlin.random.Random

enum class MemoryStoryPhase {
    AUDIO_INTRO,
    LISTENING,
    ANSWERING,
    FEEDBACK
}

fun shuffleOptions(options: List<String>): List<String> {
    return options.shuffled()
}

@Composable
fun MemoryStoryPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    puzzleData: String,
    correctAnswer: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val TAG = "MemoryStoryPuzzle"

    // Score tracking state
    var totalScore by remember { mutableIntStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var listeningStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var answeringStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectionEvents by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }

    var currentPhase by remember { mutableStateOf(MemoryStoryPhase.AUDIO_INTRO) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var audioWaves by remember { mutableStateOf(generateInitialWaves()) }

    val haptics = LocalHapticFeedback.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            120 // Default 2 minutes
        }
    }

    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, currentPhase) {
        if (timeRemaining > 0 && currentPhase != MemoryStoryPhase.FEEDBACK) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && currentPhase != MemoryStoryPhase.FEEDBACK) {
            // Time's up - complete with current score
            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            onSubmitAnswer(false)
            fetchNextPuzzle(totalScore)
        }
    }

    // Parse puzzle data
    val parsedData = remember(puzzleData) {
        try {
            val questionData = JSONObject(puzzleData)

            val storyCard = questionData.getString("storyCard")
            val question = questionData.getString("question")
            val scenario = questionData.optString("scenario", "grocery")
            val character = questionData.optString("character", "Someone")
            val itemCount = questionData.optInt("itemCount", 3)
            val audioUrl = questionData.optString("audioUrl", null)

            val optionsArray = questionData.getJSONArray("options")
            val options = mutableListOf<String>()
            for (i in 0 until optionsArray.length()) {
                options.add(optionsArray.getString(i))
            }

            Log.d(TAG, "📊 Parsed memory story:")
            Log.d(TAG, "   Story: $storyCard")
            Log.d(TAG, "   Question: $question")
            Log.d(TAG, "   Options: $options")
            Log.d(TAG, "   Item count: $itemCount")

            mapOf(
                "storyCard" to storyCard,
                "question" to question,
                "options" to options,
                "scenario" to scenario,
                "character" to character,
                "itemCount" to itemCount,
                "audioUrl" to audioUrl,
                "success" to true
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse memory story data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (!isValidData) {
        LaunchedEffect(Unit) {
            Log.e(TAG, "❌ Invalid memory story data")
            onSubmitAnswer(false)
        }
        return
    }

    @Suppress("UNCHECKED_CAST")
    val storyCard = parsedData["storyCard"] as String
    @Suppress("UNCHECKED_CAST")
    val question = parsedData["question"] as String
    @Suppress("UNCHECKED_CAST")
    val options = remember(parsedData) {
        shuffleOptions(parsedData["options"] as List<String>)
    }
    val audioUrl = parsedData["audioUrl"] as? String
    val itemCount = parsedData["itemCount"] as? Int ?: 3

    // Parse correct items
    val correctItems = remember(correctAnswer) {
        try {
            val correctItemsArray = JSONArray(correctAnswer)
            val items = mutableListOf<String>()
            for (i in 0 until correctItemsArray.length()) {
                items.add(correctItemsArray.getString(i))
            }
            Log.d(TAG, "✅ Correct items: $items")
            items
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse correct answer", e)
            emptyList<String>()
        }
    }

    // Calculate auditory memory score
    fun calculateAuditoryMemoryScore(
        correctItems: Set<String>,
        selectedItems: Set<String>,
        totalCorrectItems: Int,
        listeningTimeMs: Long,
        selectionTimeMs: Long,
        difficulty: String,
        selectionEvents: List<Pair<String, Long>>
    ): Int {
        val correctCount = correctItems.intersect(selectedItems).size
        val incorrectCount = selectedItems.size - correctCount
        val missedCount = totalCorrectItems - correctCount

        if (correctCount == 0) return 0

        // Base points by difficulty
        val basePointsPerItem = when (difficulty.lowercase()) {
            "easy" -> 20
            "medium" -> 30
            "hard" -> 40
            "expert" -> 50
            else -> 30
        }

        val baseScore = correctCount * basePointsPerItem

        // Auditory comprehension bonus
        val comprehensionMultiplier = when (totalCorrectItems) {
            in 1..3 -> 1.0f
            in 4..6 -> 1.3f
            in 7..10 -> 1.6f
            else -> 2.0f
        }

        // Perfect recall bonus
        val perfectRecallBonus = if (correctCount == totalCorrectItems && incorrectCount == 0) {
            (baseScore * 0.5f).toInt()
        } else {
            0
        }

        // Accuracy penalty for wrong selections
        val accuracyPenalty = incorrectCount * (basePointsPerItem / 3)

        // Listening efficiency bonus (shorter listening time = better focus)
        val listeningSeconds = listeningTimeMs / 1000f
        val listeningBonus = when {
            listeningSeconds <= 20f -> (baseScore * 0.2f).toInt()
            listeningSeconds <= 40f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Selection speed bonus (quick confident decisions)
        val selectionSeconds = selectionTimeMs / 1000f
        val speedBonus = when {
            selectionSeconds <= 30f -> (baseScore * 0.25f).toInt()
            selectionSeconds <= 60f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Sequential selection bonus (systematic approach)
        val sequentialBonus = if (selectionEvents.size >= 2) {
            val timeDiffs = selectionEvents.zipWithNext { a, b -> b.second - a.second }
            val avgTimeBetween = timeDiffs.average()
            if (avgTimeBetween < 3000 && timeDiffs.all { it < 5000 }) { // Consistent timing
                (baseScore * 0.15f).toInt()
            } else 0
        } else 0

        // Memory retention bonus
        val retentionBonus = (baseScore * 0.1f).toInt()

        val finalScore = ((baseScore * comprehensionMultiplier).toInt() + perfectRecallBonus + listeningBonus + speedBonus + sequentialBonus + retentionBonus - accuracyPenalty)

        Log.d(TAG, "🏆 Auditory memory score calculation:")
        Log.d(TAG, "  Correct: $correctCount/$totalCorrectItems, Wrong: $incorrectCount")
        Log.d(TAG, "  Base score: $baseScore")
        Log.d(TAG, "  Comprehension multiplier: ${comprehensionMultiplier}x")
        Log.d(TAG, "  Perfect recall bonus: $perfectRecallBonus")
        Log.d(TAG, "  Listening bonus: $listeningBonus (${listeningSeconds}s)")
        Log.d(TAG, "  Speed bonus: $speedBonus (${selectionSeconds}s)")
        Log.d(TAG, "  Sequential bonus: $sequentialBonus")
        Log.d(TAG, "  Retention bonus: $retentionBonus")
        Log.d(TAG, "  Accuracy penalty: $accuracyPenalty")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, baseScore / 4) // Minimum 25% of base
    }

    // Animate audio waves while playing
    LaunchedEffect(isAudioPlaying) {
        while (isAudioPlaying) {
            audioWaves = generateAnimatedWaves()
            delay(100)
        }
    }

    // Cleanup media player safely
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.reset()

                    // Release on background thread to avoid ANR
                    CoroutineScope(Dispatchers.IO).launch {
                        withTimeoutOrNull(2000L) {
                            try {
                                player.release()
                                Log.d("MemoryStory", "MediaPlayer released safely")
                            } catch (e: Exception) {
                                Log.e("MemoryStory", "Error releasing MediaPlayer", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MemoryStory", "Error during MediaPlayer cleanup", e)
            }
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
            MemoryStoryPhase.AUDIO_INTRO -> {
                AudioIntroScreen(
                    onBegin = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentPhase = MemoryStoryPhase.LISTENING
                        listeningStartTime = System.currentTimeMillis()
                    },
                    onBack = onBack
                )
            }

            MemoryStoryPhase.LISTENING -> {
                EnhancedAudioListeningScreen(
                    storyCard = storyCard,
                    audioUrl = audioUrl,
                    audioWaves = audioWaves,
                    isPlaying = isAudioPlaying,
                    onAudioStarted = { isAudioPlaying = true },
                    onAudioCompleted = {
                        isAudioPlaying = false
                        currentPhase = MemoryStoryPhase.ANSWERING
                        answeringStartTime = System.currentTimeMillis()
                    },
                    onMediaPlayerCreated = { mediaPlayer = it },
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    hearts = hearts,
                    totalScore = totalScore,
                    onBack = onBack
                )
            }

            MemoryStoryPhase.ANSWERING -> {
                EnhancedAnswerSelectionScreen(
                    question = question,
                    options = options,
                    selectedItems = selectedItems,
                    correctItems = correctItems,
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    hearts = hearts,
                    difficulty = difficulty,
                    totalScore = totalScore,
                    onItemToggle = { item ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val currentTime = System.currentTimeMillis()

                        selectedItems = if (selectedItems.contains(item)) {
                            selectedItems - item
                        } else {
                            selectionEvents = selectionEvents + (item to currentTime)
                            selectedItems + item
                        }
                    },
                    onSubmit = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        val currentTime = System.currentTimeMillis()
                        val listeningTime = answeringStartTime - listeningStartTime
                        val selectionTime = currentTime - answeringStartTime

                        // Calculate score
                        totalScore = calculateAuditoryMemoryScore(
                            correctItems = correctItems.toSet(),
                            selectedItems = selectedItems,
                            totalCorrectItems = correctItems.size,
                            listeningTimeMs = listeningTime,
                            selectionTimeMs = selectionTime,
                            difficulty = difficulty,
                            selectionEvents = selectionEvents
                        )

                        val correctCount = selectedItems.intersect(correctItems.toSet()).size
                        val incorrectCount = selectedItems.size - correctCount
                        val isCorrect = correctCount == correctItems.size && incorrectCount == 0

                        Log.d(TAG, "📊 Final results: $correctCount/${correctItems.size} correct, score: $totalScore")

                        feedbackManager.showFeedback(
                            puzzleType = "auditoryMemory",
                            isCorrect = isCorrect,
                            userAnswer = selectedItems.sorted().joinToString(", "),
                            correctAnswer = correctItems.sorted().joinToString(", "),
                            timeSpent = currentTime - gameStartTime,
                            difficulty = difficulty,
                            timeRemaining = timeRemaining,
                            totalTime = totalTimeSeconds,
                            onComplete = {
                                onSubmitAnswer(isCorrect)
                                Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                                fetchNextPuzzle(totalScore)
                            }
                        )
                    },
                    onBack = onBack
                )
            }

            MemoryStoryPhase.FEEDBACK -> {
                // Handled by unified feedback system
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
            text = "AUDITORY MEMORY",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "THIS GAME REQUIRES AUDIO",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
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
            text = "LISTEN CAREFULLY AND\nREMEMBER THE DETAILS",
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
private fun EnhancedAudioListeningScreen(
    storyCard: String,
    audioUrl: String?,
    audioWaves: List<Float>,
    isPlaying: Boolean,
    onAudioStarted: () -> Unit,
    onAudioCompleted: () -> Unit,
    onMediaPlayerCreated: (MediaPlayer) -> Unit,
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    hearts: Int,
    totalScore: Int,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        try {
            if (!audioUrl.isNullOrEmpty()) {
                Log.d("MemoryStory", "🔊 Starting audio playbook: $audioUrl")
                val mediaPlayer = MediaPlayer().apply {
                    try {
                        setOnErrorListener { mp, what, extra ->
                            Log.e(TAG, "Audio error: what=$what, extra=$extra")
                            CoroutineScope(Dispatchers.IO).launch {
                                withTimeoutOrNull(2000L) {
                                    try { mp.release() } catch (e: Exception) { }
                                }
                            }
                            true // Error handled
                        }
                        setDataSource(audioUrl)
                        setOnPreparedListener {
                            try {
                                Log.d("MemoryStory", "✅ Audio prepared, starting playbook")
                                start()
                                onAudioStarted()
                            } catch (e: Exception) {
                                Log.e("MemoryStory", "Error starting playback", e)
                                onAudioCompleted()
                            }
                        }
                        setOnCompletionListener {
                            Log.d("MemoryStory", "🎵 Audio playback completed")
                            onAudioCompleted()
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.e("MemoryStory", "❌ Audio error: what=$what, extra=$extra")
                            onAudioCompleted()
                            true
                        }
                        prepareAsync()
                    } catch (e: Exception) {
                        Log.e("MemoryStory", "Error setting up MediaPlayer", e)
                        release() // Clean up the MediaPlayer if setup fails
                        onAudioCompleted()
                        return@apply
                    }
                }
                onMediaPlayerCreated(mediaPlayer)
            } else {
                Log.w("MemoryStory", "⚠️ No audio URL provided, using fallback timing")
                onAudioStarted()
                delay(4000)
                onAudioCompleted()
            }
        } catch (e: Exception) {
            Log.e("MemoryStory", "❌ Audio playback failed", e)
            onAudioStarted()
            delay(4000)
            onAudioCompleted()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Enhanced top bar with score
        EnhancedMemoryStoryTopBar(
            level = level,
            streakInfo = streakInfo,
            timer = timer,
            lives = hearts,
            totalScore = totalScore,
            onBack = onBack,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = if (storyCard.contains("business", ignoreCase = true)) {
                "BUSINESS TRAVEL"
            } else if (storyCard.contains("pack", ignoreCase = true)) {
                "PACKING ESSENTIALS"
            } else if (storyCard.contains("grocery", ignoreCase = true) || storyCard.contains("shop", ignoreCase = true)) {
                "SHOPPING LIST"
            } else if (storyCard.contains("recipe", ignoreCase = true) || storyCard.contains("cook", ignoreCase = true)) {
                "COOKING INGREDIENTS"
            } else {
                "MEMORY CHALLENGE"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 40.dp)
        ) {
            drawAudioWaves(audioWaves, isPlaying)
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = if (isPlaying) "LISTENING..." else "LISTEN CAREFULLY",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (isPlaying) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (storyCard.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = storyCard,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                    lineHeight = 18.sp
                )
            }
        }

        if (!audioUrl.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Audio Playing",
                    tint = if (isPlaying) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPlaying) "Playing audio..." else "Audio ready",
                    fontSize = 12.sp,
                    color = if (isPlaying) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EnhancedAnswerSelectionScreen(
    question: String,
    options: List<String>,
    selectedItems: Set<String>,
    correctItems: List<String>,
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    hearts: Int,
    difficulty: String,
    totalScore: Int,
    onItemToggle: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Enhanced top bar with score
        EnhancedMemoryStoryTopBar(
            level = level,
            streakInfo = streakInfo,
            timer = timer,
            lives = hearts,
            totalScore = totalScore,
            onBack = onBack,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = question,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(options) { option ->
                MemoryOptionCard(
                    option = option,
                    isSelected = selectedItems.contains(option),
                    onClick = { onItemToggle(option) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Progress indicator
        Text(
            text = "Selected: ${selectedItems.size} / ${correctItems.size} items",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (difficulty.isNotEmpty() && totalScore == 0) {
            Text(
                text = "⚡ Quick, accurate selections earn bonus points!",
                fontSize = 12.sp,
                color = Color.Yellow.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            enabled = selectedItems.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00BCD4),
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = stringResource(R.string.submit),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun MemoryOptionCard(
    option: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onClick() }
            .let { modifier ->
                if (isSelected) {
                    modifier.border(2.dp, Color(0xFF00BCD4), RoundedCornerShape(12.dp))
                } else {
                    modifier
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(0xFF00BCD4).copy(alpha = 0.2f)
            } else {
                Color.White.copy(alpha = 0.1f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = option,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color(0xFF00BCD4) else Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun EnhancedMemoryStoryTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    totalScore: Int,
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

        // Score display
        if (totalScore > 0) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "🎯 Score: $totalScore",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

// Helper functions for audio wave generation and drawing
private fun generateInitialWaves(): List<Float> {
    return List(50) { 0.1f }
}

private fun generateAnimatedWaves(): List<Float> {
    return List(50) { Random.nextFloat() * 0.8f + 0.2f }
}

private fun DrawScope.drawAudioWaves(waves: List<Float>, isPlaying: Boolean) {
    val waveColor = if (isPlaying) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.3f)
    val barWidth = size.width / waves.size
    val centerY = size.height / 2

    waves.forEachIndexed { index, amplitude ->
        val x = index * barWidth + barWidth / 2
        val barHeight = amplitude * size.height * 0.8f

        drawLine(
            color = waveColor,
            start = Offset(x, centerY - barHeight / 2),
            end = Offset(x, centerY + barHeight / 2),
            strokeWidth = barWidth * 0.8f,
            cap = StrokeCap.Round
        )
    }
}
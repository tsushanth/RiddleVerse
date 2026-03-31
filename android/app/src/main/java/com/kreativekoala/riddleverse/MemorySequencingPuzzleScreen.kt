package com.kreativekoala.riddleverse

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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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

enum class SequencingPhase {
    AUDIO_INTRO,     // Screen 1: Audio required notice
    PART_ONE_AUDIO,  // Screen 2: Playing part 1 audio with waves
    PART_ONE_SEQUENCE, // Screen 3: Sequence part 1 items
    PART_ONE_FEEDBACK, // Screen 4: Show feedback for part 1
    PART_TWO_INTRO,   // Screen 5: Part 2 intro
    PART_TWO_AUDIO,   // Screen 6: Playing part 2 audio with waves
    PART_TWO_SEQUENCE, // Screen 7: Sequence part 2 items
    PART_TWO_FEEDBACK, // Screen 8: Show feedback for part 2
    FINAL_SEQUENCE,   // Screen 9: Sequence all items together
    FINAL_FEEDBACK    // Screen 10: Final feedback
}

data class SequenceItem(
    val id: String,
    val text: String,
    val partNumber: Int, // 1 or 2
    val correctOrder: Int,
    val isCorrect: Boolean = false,
    val isIncorrect: Boolean = false
)

@Composable
fun SequencingPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    puzzleData: String, // JSON string containing puzzle data
    correctAnswer: String, // JSON array string of correct sequence
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val TAG = "SequencingPuzzle"
    val scope = rememberCoroutineScope()

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var phaseScores by remember { mutableStateOf(mutableMapOf<String, Int>()) }
    var attempts by remember { mutableStateOf(mutableMapOf<String, Int>()) }

    // Game state
    var currentPhase by remember { mutableStateOf(SequencingPhase.AUDIO_INTRO) }
    var currentItems by remember { mutableStateOf(listOf<SequenceItem>()) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var audioWaves by remember { mutableStateOf(generateInitialWaves()) }
    var draggedItemIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

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

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, currentPhase) {
        if (timeRemaining > 0) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0) {
            // Time's up - complete with current score
            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            fetchNextPuzzle(totalScore)
        }
    }

    // Parse puzzle data
    val parsedData = remember(puzzleData) {
        try {
            val questionData = JSONObject(puzzleData)

            val topic = questionData.getString("topic")
            val description = questionData.getString("description")
            val partOneAudioUrl = questionData.optString("partOneAudioUrl", null)
            val partTwoAudioUrl = questionData.optString("partTwoAudioUrl", null)

            // Parse items array
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

            Log.d(TAG, "📊 Parsed sequencing puzzle:")
            Log.d(TAG, "   Topic: $topic")
            Log.d(TAG, "   Items: ${items.size}")

            mapOf(
                "topic" to topic,
                "description" to description,
                "items" to items,
                "partOneAudioUrl" to partOneAudioUrl,
                "partTwoAudioUrl" to partTwoAudioUrl,
                "success" to true
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse sequencing puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (!isValidData) {
        LaunchedEffect(Unit) {
            Log.e(TAG, "❌ Invalid sequencing puzzle data")
            onSubmitAnswer(false)
        }
        return
    }

    @Suppress("UNCHECKED_CAST")
    val topic = parsedData["topic"] as String
    @Suppress("UNCHECKED_CAST")
    val description = parsedData["description"] as String
    @Suppress("UNCHECKED_CAST")
    val allItems = parsedData["items"] as List<SequenceItem>
    val partOneAudioUrl = parsedData["partOneAudioUrl"] as? String
    val partTwoAudioUrl = parsedData["partTwoAudioUrl"] as? String

    // Calculate score for each phase
    fun calculatePhaseScore(phase: String, isCorrect: Boolean, attemptCount: Int, timeSpent: Long): Int {
        if (!isCorrect) return 0

        val basePoints = when (difficulty.lowercase()) {
            "easy" -> when (phase) {
                "part1" -> 25
                "part2" -> 25
                "final" -> 50
                else -> 25
            }
            "medium" -> when (phase) {
                "part1" -> 35
                "part2" -> 35
                "final" -> 70
                else -> 35
            }
            "hard" -> when (phase) {
                "part1" -> 45
                "part2" -> 45
                "final" -> 90
                else -> 45
            }
            "expert" -> when (phase) {
                "part1" -> 55
                "part2" -> 55
                "final" -> 110
                else -> 55
            }
            else -> when (phase) {
                "part1" -> 35
                "part2" -> 35
                "final" -> 70
                else -> 35
            }
        }

        // Memory retention bonus (less time spent = better memory)
        val memoryBonus = when {
            timeSpent <= 15000L -> (basePoints * 0.5f).toInt() // 50% bonus for quick completion
            timeSpent <= 30000L -> (basePoints * 0.3f).toInt() // 30% bonus for fast completion
            timeSpent <= 60000L -> (basePoints * 0.1f).toInt() // 10% bonus for moderate speed
            else -> 0
        }

        // Complexity bonus for final phase (handling all items)
        val complexityBonus = if (phase == "final") {
            val itemCount = allItems.size
            when {
                itemCount >= 8 -> (basePoints * 0.4f).toInt()
                itemCount >= 6 -> (basePoints * 0.3f).toInt()
                itemCount >= 4 -> (basePoints * 0.2f).toInt()
                else -> 0
            }
        } else 0

        // Attempt penalty
        val attemptPenalty = when (attemptCount) {
            1 -> 0
            2 -> (basePoints * 0.25f).toInt()
            3 -> (basePoints * 0.5f).toInt()
            else -> (basePoints * 0.75f).toInt()
        }

        // Sequence accuracy bonus
        val sequenceBonus = (basePoints * 0.2f).toInt()

        val finalScore = basePoints + memoryBonus + complexityBonus + sequenceBonus - attemptPenalty

        Log.d(TAG, "🏆 Score calculation for $phase:")
        Log.d(TAG, "  Base points: $basePoints")
        Log.d(TAG, "  Memory bonus: $memoryBonus (time: ${timeSpent}ms)")
        Log.d(TAG, "  Complexity bonus: $complexityBonus")
        Log.d(TAG, "  Sequence bonus: $sequenceBonus")
        Log.d(TAG, "  Attempt penalty: $attemptPenalty (attempt #$attemptCount)")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, basePoints / 4) // Minimum 25% of base points
    }

    // Enhanced submit logic with scoring
    fun handlePhaseSubmit(phase: String, items: List<SequenceItem>) {
        val phaseKey = phase.lowercase()
        val currentAttempt = attempts.getOrDefault(phaseKey, 0) + 1
        attempts[phaseKey] = currentAttempt

        val timeSpent = System.currentTimeMillis() - gameStartTime
        val isCorrect = checkSequenceCorrectness(items)

        if (isCorrect) {
            val phaseScore = calculatePhaseScore(phaseKey, true, currentAttempt, timeSpent)
            phaseScores[phaseKey] = phaseScore
            totalScore = totalScore + phaseScore

            Log.d(TAG, "✅ $phase completed correctly! Score: +$phaseScore, Total: $totalScore")
        } else {
            currentHearts = maxOf(0, currentHearts - 1)
            Log.d(TAG, "❌ $phase incorrect. Hearts remaining: $currentHearts")

            if (currentHearts == 0) {
                Log.d(TAG, "💔 No hearts remaining. Game over with score: $totalScore")
                fetchNextPuzzle(totalScore)
                return
            }
        }

        // Continue with existing phase logic...
        when (phase) {
            "part1" -> {
                if (isCorrect) {
                    currentItems = items // Keep correct order
                } else {
                    currentItems = showFeedbackAndCorrectOrder(items)
                }
                currentPhase = SequencingPhase.PART_ONE_FEEDBACK
            }
            "part2" -> {
                if (isCorrect) {
                    currentItems = items
                } else {
                    currentItems = showFeedbackAndCorrectOrder(items)
                }
                currentPhase = SequencingPhase.PART_TWO_FEEDBACK
            }
            "final" -> {
                // Use unified feedback for final submission
                feedbackManager.showFeedback(
                    puzzleType = "sequencing",
                    isCorrect = isCorrect,
                    userAnswer = items.map { it.text }.joinToString(" → "),
                    correctAnswer = allItems.sortedBy { it.correctOrder }.map { it.text }.joinToString(" → "),
                    timeSpent = timeSpent,
                    difficulty = difficulty,
                    timeRemaining = timeRemaining,
                    totalTime = totalTimeSeconds,
                    onComplete = {
                        onSubmitAnswer(isCorrect)
                        if (isCorrect) {
                            Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                            fetchNextPuzzle(totalScore)
                        } else {
                            currentItems = showFeedbackAndCorrectOrder(allItems)
                            currentPhase = SequencingPhase.FINAL_FEEDBACK
                        }
                    }
                )
            }
        }
    }

    // Animate audio waves while playing
    LaunchedEffect(isAudioPlaying) {
        while (isAudioPlaying) {
            audioWaves = generateAnimatedWaves()
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.let { player ->
                CoroutineScope(Dispatchers.IO).launch {
                    withTimeoutOrNull(2000L) {
                        try {
                            if (player.isPlaying) player.stop()
                            player.reset()
                            player.release()
                        } catch (e: Exception) {
                            Log.e(TAG, "Cleanup error", e)
                        }
                    }
                }
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
            SequencingPhase.AUDIO_INTRO -> {
                AudioIntroScreen(
                    onBegin = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        gameStartTime = System.currentTimeMillis() // Reset timer for actual gameplay
                        currentPhase = SequencingPhase.PART_ONE_AUDIO
                    },
                    onBack = onBack
                )
            }

            SequencingPhase.PART_ONE_AUDIO -> {
                AudioListeningScreen(
                    title = topic,
                    subtitle = "PART ONE OF TWO",
                    description = description,
                    audioUrl = partOneAudioUrl,
                    audioWaves = audioWaves,
                    isPlaying = isAudioPlaying,
                    onAudioStarted = {
                        Log.d(TAG, "🎵 Part 1 audio started")
                        isAudioPlaying = true
                    },
                    onAudioCompleted = {
                        Log.d(TAG, "🎵 Part 1 audio completed")
                        isAudioPlaying = false
                        val part1Items = allItems.filter { it.partNumber == 1 }.shuffled()
                        currentItems = part1Items
                        Log.d(TAG, "📝 Part 1 items set: ${part1Items.map { it.text }}")
                        currentPhase = SequencingPhase.PART_ONE_SEQUENCE
                    },
                    onMediaPlayerCreated = { mediaPlayer = it },
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer, // Use live timer
                    hearts = currentHearts, // Use current hearts
                    onBack = onBack
                )
            }

            SequencingPhase.PART_ONE_SEQUENCE -> {
                EnhancedSequencingScreen(
                    title = topic,
                    subtitle = "PART ONE - ARRANGE IN ORDER",
                    items = currentItems,
                    draggedItemIndex = draggedItemIndex,
                    dragOffset = dragOffset,
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    hearts = currentHearts,
                    totalScore = totalScore,
                    phaseScores = phaseScores,
                    currentPhase = "Part 1",
                    onItemDrag = { index, offset ->
                        draggedItemIndex = index
                        dragOffset = offset
                    },
                    onItemDrop = { fromIndex, toIndex ->
                        Log.d(TAG, "🔄 Dropping item from $fromIndex to $toIndex")
                        if (fromIndex != toIndex && fromIndex >= 0 && toIndex >= 0 && fromIndex < currentItems.size) {
                            val newItems = currentItems.toMutableList()
                            val item = newItems.removeAt(fromIndex)
                            val clampedToIndex = toIndex.coerceIn(0, newItems.size)
                            newItems.add(clampedToIndex, item)
                            currentItems = newItems
                            Log.d(TAG, "✅ New order: ${newItems.map { it.text }}")
                        }
                        draggedItemIndex = -1
                        dragOffset = Offset.Zero
                    },
                    onSubmit = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        handlePhaseSubmit("part1", currentItems)
                    },
                    onBack = onBack
                )
            }

            SequencingPhase.PART_ONE_FEEDBACK -> {
                FeedbackScreen(
                    title = "PART ONE - CORRECT ORDER",
                    items = currentItems,
                    onContinue = {
                        currentPhase = SequencingPhase.PART_TWO_INTRO
                    }
                )
            }

            SequencingPhase.PART_TWO_INTRO -> {
                PartTwoIntroScreen(
                    topic = topic,
                    onContinue = {
                        currentPhase = SequencingPhase.PART_TWO_AUDIO
                    }
                )
            }

            SequencingPhase.PART_TWO_AUDIO -> {
                AudioListeningScreen(
                    title = topic,
                    subtitle = "PART TWO OF TWO",
                    description = description,
                    audioUrl = partTwoAudioUrl,
                    audioWaves = audioWaves,
                    isPlaying = isAudioPlaying,
                    onAudioStarted = {
                        Log.d(TAG, "🎵 Part 2 audio started")
                        isAudioPlaying = true
                    },
                    onAudioCompleted = {
                        Log.d(TAG, "🎵 Part 2 audio completed")
                        isAudioPlaying = false
                        val part2Items = allItems.filter { it.partNumber == 2 }.shuffled()
                        currentItems = part2Items
                        Log.d(TAG, "📝 Part 2 items set: ${part2Items.map { it.text }}")
                        currentPhase = SequencingPhase.PART_TWO_SEQUENCE
                    },
                    onMediaPlayerCreated = { mediaPlayer = it },
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    hearts = currentHearts,
                    onBack = onBack
                )
            }

            SequencingPhase.PART_TWO_SEQUENCE -> {
                EnhancedSequencingScreen(
                    title = topic,
                    subtitle = "PART TWO - ARRANGE IN ORDER",
                    items = currentItems,
                    draggedItemIndex = draggedItemIndex,
                    dragOffset = dragOffset,
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    hearts = currentHearts,
                    totalScore = totalScore,
                    phaseScores = phaseScores,
                    currentPhase = "Part 2",
                    onItemDrag = { index, offset ->
                        draggedItemIndex = index
                        dragOffset = offset
                    },
                    onItemDrop = { fromIndex, toIndex ->
                        Log.d(TAG, "🔄 Dropping item from $fromIndex to $toIndex")
                        if (fromIndex != toIndex && fromIndex >= 0 && toIndex >= 0 && fromIndex < currentItems.size) {
                            val newItems = currentItems.toMutableList()
                            val item = newItems.removeAt(fromIndex)
                            val clampedToIndex = toIndex.coerceIn(0, newItems.size)
                            newItems.add(clampedToIndex, item)
                            currentItems = newItems
                            Log.d(TAG, "✅ New order: ${newItems.map { it.text }}")
                        }
                        draggedItemIndex = -1
                        dragOffset = Offset.Zero
                    },
                    onSubmit = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        handlePhaseSubmit("part2", currentItems)
                    },
                    onBack = onBack
                )
            }

            SequencingPhase.PART_TWO_FEEDBACK -> {
                FeedbackScreen(
                    title = "PART TWO - CORRECT ORDER",
                    items = currentItems,
                    onContinue = {
                        currentPhase = SequencingPhase.FINAL_SEQUENCE
                    }
                )
            }

            SequencingPhase.FINAL_SEQUENCE -> {
                LaunchedEffect(Unit) {
                    if (currentItems.isEmpty() || currentItems.size != allItems.size) {
                        Log.d(TAG, "🔄 Setting up final sequence with all items shuffled")
                        currentItems = allItems.shuffled()
                        Log.d(TAG, "📝 Final sequence items: ${currentItems.map { it.text }}")
                    }
                }

                EnhancedSequencingScreen(
                    title = topic,
                    subtitle = "COMPLETE SEQUENCE",
                    items = currentItems,
                    draggedItemIndex = draggedItemIndex,
                    dragOffset = dragOffset,
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = displayTimer,
                    hearts = currentHearts,
                    totalScore = totalScore,
                    phaseScores = phaseScores,
                    currentPhase = "Final",
                    onItemDrag = { index, offset ->
                        draggedItemIndex = index
                        dragOffset = offset
                    },
                    onItemDrop = { fromIndex, toIndex ->
                        Log.d(TAG, "🔄 Dropping item from $fromIndex to $toIndex")
                        if (fromIndex != toIndex && fromIndex >= 0 && toIndex >= 0 && fromIndex < currentItems.size) {
                            val newItems = currentItems.toMutableList()
                            val item = newItems.removeAt(fromIndex)
                            val clampedToIndex = toIndex.coerceIn(0, newItems.size)
                            newItems.add(clampedToIndex, item)
                            currentItems = newItems
                            Log.d(TAG, "✅ New order: ${newItems.map { it.text }}")
                        }
                        draggedItemIndex = -1
                        dragOffset = Offset.Zero
                    },
                    onSubmit = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        handlePhaseSubmit("final", currentItems)
                    },
                    onBack = onBack
                )
            }

            SequencingPhase.FINAL_FEEDBACK -> {
                FeedbackScreen(
                    title = "COMPLETE SEQUENCE - CORRECT ORDER",
                    items = allItems.sortedBy { it.correctOrder },
                    onContinue = {
                        fetchNextPuzzle(totalScore)
                    }
                )
            }
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

// Enhanced Sequencing Screen with score display
@Composable
private fun EnhancedSequencingScreen(
    title: String,
    subtitle: String,
    items: List<SequenceItem>,
    draggedItemIndex: Int,
    dragOffset: Offset,
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    hearts: Int,
    totalScore: Int,
    phaseScores: Map<String, Int>,
    currentPhase: String,
    onItemDrag: (Int, Offset) -> Unit,
    onItemDrop: (Int, Int) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Top bar with live timer and current hearts
        EnhancedSequencingTopBar(
            level = level,
            streakInfo = streakInfo,
            timer = timer,
            lives = hearts,
            onBack = onBack,
            modifier = Modifier.padding(16.dp)
        )

        // Score display (if any score accumulated)
        if (totalScore > 0 || phaseScores.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Score: $totalScore",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = currentPhase,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // Show phase breakdown if available
                    if (phaseScores.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            phaseScores.forEach { (phase, score) ->
                                Text(
                                    text = "${phase.capitalize()}: +$score",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Spacer(modifier = Modifier.height(20.dp))
        }

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "DRAG TO REORDER",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Draggable items list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items) { index, item ->
                DraggableSequenceItem(
                    item = item,
                    index = index,
                    isDragged = draggedItemIndex == index,
                    dragOffset = if (draggedItemIndex == index) dragOffset else Offset.Zero,
                    onDrag = { offset -> onItemDrag(index, offset) },
                    onDrop = { toIndex -> onItemDrop(index, toIndex) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Submit button
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00BCD4)
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

// Enhanced top bar with current hearts and live timer
@Composable
private fun EnhancedSequencingTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.statusBarsPadding().fillMaxWidth(),
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

                // Level progress bar
                LevelProgressBar(
                    level = level,
                    modifier = Modifier.width(120.dp)
                )
            }
        }

        // Center: Lives display with enhanced styling
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(5) { index ->
                Text(
                    text = if (index < lives) "❤️" else "🤍",
                    fontSize = 16.sp
                )
            }
        }

        // Right side: Timer with color coding and streak
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = timer,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true) {
                    Color.Red // Red when ≤30 seconds
                } else {
                    Color.White
                }
            )

            // Streak display
            if (streakInfo.currentStreak > 0) {
                StreakDisplay(
                    streakInfo = streakInfo,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// Rest of the composables remain the same but with enhanced audio handling...
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
        // Back button
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
            text = "THIS GAME REQUIRES AUDIO",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(60.dp))

        // Audio icon with pulsing animation
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
            text = "LISTEN TO TWO PARTS AND\nSEQUENCE THE EVENTS",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Scoring information
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💯 Scoring Guide",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• Memory Bonus: Quick sequencing\n• Complexity Bonus: Final phase\n• Sequence Accuracy: Correct order\n• Hearts Lost: Wrong sequences",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }

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
private fun AudioListeningScreen(
    title: String,
    subtitle: String,
    description: String,
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
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var hasStartedAudio by remember { mutableStateOf(false) }

    // IMPROVED: Better audio handling with proper cleanup
    LaunchedEffect(audioUrl) {
        if (hasStartedAudio) return@LaunchedEffect // Prevent multiple audio starts
        hasStartedAudio = true

        try {
            Log.d("SequencingPuzzle", "🎵 AudioListeningScreen starting audio setup")
            Log.d("SequencingPuzzle", "🎵 Audio URL: $audioUrl")

            if (!audioUrl.isNullOrEmpty() && audioUrl != "null" && audioUrl.startsWith("http")) {
                Log.d("SequencingPuzzle", "🔊 Creating MediaPlayer for: $audioUrl")

                val mediaPlayer = MediaPlayer()

                // Set up error handling first
                mediaPlayer.setOnErrorListener { mp, what, extra ->
                    Log.e("SequencingPuzzle", "❌ Audio error: what=$what, extra=$extra")

                    // Clean up on error and use fallback
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            mp.release()
                        } catch (e: Exception) {
                            Log.e("SequencingPuzzle", "Error releasing failed MediaPlayer", e)
                        }
                    }

                    // Fallback: simulate audio duration
                    coroutineScope.launch {
                        onAudioStarted()
                        val fallbackDuration = when {
                            subtitle.contains("ONE") -> 6000L
                            subtitle.contains("TWO") -> 5000L
                            else -> 5000L
                        }
                        delay(fallbackDuration)
                        onAudioCompleted()
                    }
                    true // Error handled
                }

                mediaPlayer.setOnPreparedListener { mp ->
                    Log.d("SequencingPuzzle", "✅ Audio prepared successfully, duration: ${mp.duration}ms")
                    try {
                        mp.start()
                        onAudioStarted()
                    } catch (e: Exception) {
                        Log.e("SequencingPuzzle", "❌ Failed to start audio", e)
                        // Use fallback on start failure
                        coroutineScope.launch(Dispatchers.IO) {
                            try { mp.release() } catch (ex: Exception) { }
                        }
                        coroutineScope.launch {
                            onAudioStarted()
                            delay(5000L)
                            onAudioCompleted()
                        }
                    }
                }

                mediaPlayer.setOnCompletionListener { mp ->
                    Log.d("SequencingPuzzle", "🎵 Audio playback completed normally")
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            mp.release()
                        } catch (e: Exception) {
                            Log.e("SequencingPuzzle", "Error releasing completed MediaPlayer", e)
                        }
                    }
                    onAudioCompleted()
                }

                try {
                    mediaPlayer.setDataSource(audioUrl)
                    mediaPlayer.prepareAsync()
                    onMediaPlayerCreated(mediaPlayer)

                    // IMPROVED: Reduced safety timeout and better error handling
                    coroutineScope.launch {
                        delay(12000L) // 12 second safety timeout (reduced from 15)
                        if (isPlaying) {
                            Log.w("SequencingPuzzle", "⏰ Audio timeout reached, forcing completion")
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    if (mediaPlayer.isPlaying) mediaPlayer.stop()
                                    mediaPlayer.release()
                                } catch (e: Exception) {
                                    Log.e("SequencingPuzzle", "Error during timeout cleanup", e)
                                }
                            }
                            onAudioCompleted()
                        }
                    }

                } catch (e: Exception) {
                    Log.e("SequencingPuzzle", "❌ Failed to setup MediaPlayer", e)
                    coroutineScope.launch(Dispatchers.IO) {
                        try { mediaPlayer.release() } catch (ex: Exception) { }
                    }
                    // Immediate fallback
                    coroutineScope.launch {
                        onAudioStarted()
                        delay(4000)
                        onAudioCompleted()
                    }
                }

            } else {
                Log.w("SequencingPuzzle", "⚠️ Invalid or missing audio URL: '$audioUrl', using fallback")
                // Fallback with better duration estimation
                coroutineScope.launch {
                    onAudioStarted()
                    val estimatedDuration = when {
                        subtitle.contains("ONE") -> 6000L
                        subtitle.contains("TWO") -> 5000L
                        else -> 5000L
                    }
                    delay(estimatedDuration)
                    onAudioCompleted()
                }
            }

        } catch (e: Exception) {
            Log.e("SequencingPuzzle", "❌ Audio setup completely failed", e)
            // Emergency fallback
            coroutineScope.launch {
                onAudioStarted()
                delay(4000)
                onAudioCompleted()
            }
        }
    }

    // IMPROVED: Add manual skip option for audio issues
    var showSkipOption by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            delay(8000L) // Show skip option after 8 seconds
            showSkipOption = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Top bar
        EnhancedSequencingTopBar(
            level = level,
            streakInfo = streakInfo,
            timer = timer,
            lives = hearts,
            onBack = onBack,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Audio wave visualization
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

        // IMPROVED: Add skip option for accessibility
        if (showSkipOption && isPlaying) {
            Spacer(modifier = Modifier.height(20.dp))

            TextButton(
                onClick = {
                    Log.d("SequencingPuzzle", "🏃 User chose to skip audio")
                    onAudioCompleted()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Skip Audio →",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Description card
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
                text = description,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

// Draggable item, feedback screen, and helper functions remain the same...
@Composable
private fun DraggableSequenceItem(
    item: SequenceItem,
    index: Int,
    isDragged: Boolean,
    dragOffset: Offset,
    onDrag: (Offset) -> Unit,
    onDrop: (Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var dragState by remember { mutableStateOf(Offset.Zero) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset {
                if (isDragged) IntOffset(dragState.x.roundToInt(), dragState.y.roundToInt())
                else IntOffset.Zero
            }
            .zIndex(if (isDragged) 1f else 0f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragState = Offset.Zero
                        onDrag(Offset.Zero)
                    },
                    onDrag = { change, dragAmount ->
                        dragState += dragAmount
                        onDrag(dragState)
                    },
                    onDragEnd = {
                        // Calculate drop position based on drag offset
                        val itemHeight = 80.dp.toPx()
                        val offsetItems = (dragState.y / itemHeight).roundToInt()
                        val newIndex = (index + offsetItems).coerceIn(0, Int.MAX_VALUE)
                        onDrop(newIndex)
                        dragState = Offset.Zero
                    }
                )
            }
            .let { modifier ->
                when {
                    item.isCorrect -> modifier.border(2.dp, Color.Green, RoundedCornerShape(12.dp))
                    item.isIncorrect -> modifier.border(2.dp, Color.Red, RoundedCornerShape(12.dp))
                    else -> modifier
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                item.isCorrect -> Color.Green.copy(alpha = 0.2f)
                item.isIncorrect -> Color.Red.copy(alpha = 0.2f)
                isDragged -> Color(0xFF00BCD4).copy(alpha = 0.3f)
                else -> Color.White.copy(alpha = 0.1f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "${index + 1}.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.width(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = item.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.weight(1f),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun FeedbackScreen(
    title: String,
    items: List<SequenceItem>,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Show correct order
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items) { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Green.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Green,
                            modifier = Modifier.width(24.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = item.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00BCD4)
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = stringResource(R.string.continue_label_caps),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun PartTwoIntroScreen(
    topic: String,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = topic.uppercase(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "PART TWO OF TWO",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(60.dp))

        // Animated decorative elements
        val infiniteTransition = rememberInfiniteTransition(label = "decoration")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rotation }
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 4

                drawCircle(
                    color = Color(0xFF00BCD4).copy(alpha = 0.3f),
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )

                drawCircle(
                    color = Color(0xFF00BCD4).copy(alpha = 0.5f),
                    radius = radius / 2,
                    center = center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00BCD4)
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = "Continue to Part Two",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// Helper functions remain the same...
private fun checkSequenceCorrectness(items: List<SequenceItem>): Boolean {
    Log.d("SequencingPuzzle", "🔍 Checking sequence correctness for ${items.size} items:")

    // For part-specific sequences, we need to check relative order within the part
    val partNumbers = items.map { it.partNumber }.distinct()

    if (partNumbers.size == 1) {
        // Single part - check if items are in correct relative order for this part
        val partNumber = partNumbers.first()
        val sortedItems = items.sortedBy { it.correctOrder }

        Log.d("SequencingPuzzle", "Checking part $partNumber sequence:")
        sortedItems.forEachIndexed { index, item ->
            Log.d("SequencingPuzzle", "  Expected position ${index + 1}: ${item.text} (global order: ${item.correctOrder})")
        }

        Log.d("SequencingPuzzle", "Current order:")
        items.forEachIndexed { index, item ->
            Log.d("SequencingPuzzle", "  Position ${index + 1}: ${item.text} (global order: ${item.correctOrder})")
        }

        // Check if current order matches the correct relative order
        val isCorrect = items.zip(sortedItems).all { (current, expected) ->
            current.id == expected.id
        }

        Log.d("SequencingPuzzle", "✅ Part $partNumber sequence correct: $isCorrect")
        return isCorrect
    } else {
        // Multiple parts - check global chronological order
        Log.d("SequencingPuzzle", "Checking complete sequence (all parts):")

        val isCorrect = items.mapIndexed { index, item ->
            val expectedGlobalOrder = index + 1
            val actualGlobalOrder = item.correctOrder
            val matches = actualGlobalOrder == expectedGlobalOrder

            Log.d("SequencingPuzzle", "  Position ${index + 1}: ${item.text} - Expected global order: $expectedGlobalOrder, Actual: $actualGlobalOrder, Match: $matches")
            matches
        }.all { it }

        Log.d("SequencingPuzzle", "✅ Complete sequence correct: $isCorrect")
        return isCorrect
    }
}

private fun showFeedbackAndCorrectOrder(items: List<SequenceItem>): List<SequenceItem> {
    Log.d("SequencingPuzzle", "📊 Showing feedback and correct order")

    // Sort by correct order to get the right sequence
    val correctlyOrdered = items.sortedBy { it.correctOrder }

    Log.d("SequencingPuzzle", "Correct order should be:")
    correctlyOrdered.forEachIndexed { index, item ->
        Log.d("SequencingPuzzle", "  ${index + 1}. ${item.text} (global order: ${item.correctOrder})")
    }

    Log.d("SequencingPuzzle", "User's order was:")
    items.forEachIndexed { index, item ->
        Log.d("SequencingPuzzle", "  ${index + 1}. ${item.text} (global order: ${item.correctOrder})")
    }

    // Mark items as correct (green) since we're showing the correct order
    return correctlyOrdered.map { item ->
        item.copy(
            isCorrect = true,
            isIncorrect = false
        )
    }
}

// Audio wave functions
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
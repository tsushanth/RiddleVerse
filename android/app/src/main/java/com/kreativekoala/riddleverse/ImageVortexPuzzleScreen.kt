package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import kotlin.random.Random

data class ImageItem(
    val id: Int,
    val emoji: String,
    val isNew: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageVortexPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "2:30",
    hearts: Int = 3,
    level: String = "12 / 53",
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "ImageVortex"

    // Score tracking state
    var totalScore by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var totalAttempts by remember { mutableIntStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var levelStartTimes by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var reactionTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    var incorrectSelections by remember { mutableIntStateOf(0) }

    // Available emojis for the puzzle
    val availableEmojis = listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
        "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔",
        "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺",
        "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🦟",
        "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍈", "🍒", "🍑",
        "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥒", "🌶️",
        "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸",
        "🥅", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🏵️", "🎗️", "🎫"
    )

    var currentImages by remember { mutableStateOf(listOf<ImageItem>()) }
    var currentLevel by remember { mutableIntStateOf(1) }
    var targetPuzzleCount by remember { mutableIntStateOf(10) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackCorrect by remember { mutableStateOf(false) }
    var selectedImageId by remember { mutableStateOf<Int?>(null) }
    var gameComplete by remember { mutableStateOf(false) }
    var usedEmojis by remember { mutableStateOf(setOf<String>()) }
    var nextImageId by remember { mutableIntStateOf(1) }

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            150 // Default 2:30
        }
    }

    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }
    var isGameActive by remember { mutableStateOf(true) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentUserLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Calculate visual attention score
    fun calculateVisualAttentionScore(
        correctAnswers: Int,
        totalAttempts: Int,
        levelsCompleted: Int,
        avgReactionTime: Long,
        difficulty: String,
        timeSpentMs: Long,
        bestStreak: Int,
        incorrectSelections: Int
    ): Int {
        if (correctAnswers == 0) return 0

        // Base points by difficulty
        val basePointsPerLevel = when (difficulty.lowercase()) {
            "easy" -> 15
            "medium" -> 25
            "hard" -> 35
            "expert" -> 45
            else -> 25
        }

        val baseScore = correctAnswers * basePointsPerLevel

        // Visual processing complexity multiplier
        val complexityMultiplier = when (levelsCompleted) {
            in 1..3 -> 1.0f
            in 4..6 -> 1.2f
            in 7..10 -> 1.5f
            else -> 1.8f
        }

        // Perfect accuracy bonus
        val accuracy = if (totalAttempts > 0) correctAnswers.toFloat() / totalAttempts else 0f
        val accuracyBonus = when {
            accuracy >= 0.95f -> (baseScore * 0.4f).toInt()
            accuracy >= 0.85f -> (baseScore * 0.25f).toInt()
            accuracy >= 0.75f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Visual discrimination speed bonus
        val avgReactionSeconds = avgReactionTime / 1000f
        val speedBonus = when {
            avgReactionSeconds <= 1.0f -> (baseScore * 0.3f).toInt()
            avgReactionSeconds <= 1.5f -> (baseScore * 0.2f).toInt()
            avgReactionSeconds <= 2.0f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Streak bonus for sustained attention
        val streakBonus = when {
            bestStreak >= levelsCompleted -> (baseScore * 0.25f).toInt() // Perfect streak
            bestStreak >= levelsCompleted * 0.7f -> (baseScore * 0.15f).toInt()
            bestStreak >= levelsCompleted * 0.5f -> (baseScore * 0.05f).toInt()
            else -> 0
        }

        // Efficiency penalty for incorrect selections
        val efficiencyPenalty = incorrectSelections * (basePointsPerLevel / 3)

        // Completion bonus for finishing levels
        val completionBonus = when {
            levelsCompleted >= 10 -> (baseScore * 0.3f).toInt()
            levelsCompleted >= 7 -> (baseScore * 0.2f).toInt()
            levelsCompleted >= 5 -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Visual attention bonus for sustained focus
        val attentionBonus = (baseScore * 0.1f).toInt()

        val finalScore = ((baseScore * complexityMultiplier).toInt() + accuracyBonus + speedBonus + streakBonus + completionBonus + attentionBonus - efficiencyPenalty)

        Log.d(TAG, "🏆 Visual attention score calculation:")
        Log.d(TAG, "  Correct: $correctAnswers/$totalAttempts (${(accuracy * 100).toInt()}%)")
        Log.d(TAG, "  Base score: $baseScore")
        Log.d(TAG, "  Complexity multiplier: ${complexityMultiplier}x")
        Log.d(TAG, "  Accuracy bonus: $accuracyBonus")
        Log.d(TAG, "  Speed bonus: $speedBonus (avg ${avgReactionSeconds}s)")
        Log.d(TAG, "  Streak bonus: $streakBonus (best: $bestStreak)")
        Log.d(TAG, "  Completion bonus: $completionBonus ($levelsCompleted levels)")
        Log.d(TAG, "  Attention bonus: $attentionBonus")
        Log.d(TAG, "  Efficiency penalty: $efficiencyPenalty")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, baseScore / 4) // Minimum 25% of base
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isGameActive) {
        if (timeRemaining > 0 && isGameActive && !gameComplete) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && isGameActive) {
            // Time's up - complete with current score
            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            isGameActive = false
            gameComplete = true

            // Calculate final score before submission
            totalScore = calculateVisualAttentionScore(
                correctAnswers = correctAnswers,
                totalAttempts = totalAttempts,
                levelsCompleted = currentLevel - 1,
                avgReactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.average().toLong() else 2000L,
                difficulty = difficulty,
                timeSpentMs = System.currentTimeMillis() - gameStartTime,
                bestStreak = bestStreak,
                incorrectSelections = incorrectSelections
            )

            onSubmitAnswer(false)
            fetchNextPuzzle(totalScore)
        }
    }



    // Reset game function
    fun resetGame() {
        currentImages = listOf()
        currentLevel = 1
        showFeedback = false
        feedbackCorrect = false
        selectedImageId = null
        gameComplete = false
        usedEmojis = setOf()
        totalScore = 0
        correctAnswers = 0
        totalAttempts = 0
        timeRemaining = totalTimeSeconds
        displayTimer = timer
        isGameActive = true
        nextImageId = 1
        levelStartTimes = emptyMap()
        reactionTimes = emptyList()
        streak = 0
        bestStreak = 0
        incorrectSelections = 0
        gameStartTime = System.currentTimeMillis()

        // Initialize first image again
        val shuffled = availableEmojis.shuffled()
        val firstImage = ImageItem(nextImageId, shuffled[0], true)
        currentImages = listOf(firstImage)
        usedEmojis = setOf(shuffled[0])
        nextImageId++
        levelStartTimes = mapOf(currentLevel to System.currentTimeMillis())
    }

    // Initialize first images
    LaunchedEffect(Unit) {
        val shuffled = availableEmojis.shuffled()
        val firstImage = ImageItem(nextImageId, shuffled[0], true)
        currentImages = listOf(firstImage)
        usedEmojis = setOf(shuffled[0])
        nextImageId++
        levelStartTimes = mapOf(currentLevel to System.currentTimeMillis())
        gameStartTime = System.currentTimeMillis()
    }

    // Add new image for next level
    fun addNewImage() {
        val availableForNew = availableEmojis.filter { it !in usedEmojis }
        if (availableForNew.isNotEmpty()) {
            val newEmoji = availableForNew.random()
            val newImage = ImageItem(nextImageId, newEmoji, true)

            // Mark all previous images as not new
            val updatedPreviousImages = currentImages.map { it.copy(isNew = false) }

            currentImages = updatedPreviousImages + newImage
            usedEmojis = usedEmojis + newEmoji
            nextImageId++

            // Track level start time
            levelStartTimes = levelStartTimes + (currentLevel to System.currentTimeMillis())
        }
    }

    // Handle wrong answer - retry same level with different new image
    fun retryCurrentLevel() {
        val availableForNew = availableEmojis.filter { it !in usedEmojis }
        if (availableForNew.isNotEmpty()) {
            val newEmoji = availableForNew.random()

            // Keep all previous images (without the wrong new one), mark them as not new
            val previousImages = currentImages.dropLast(1).map { it.copy(isNew = false) }

            // Create new image with UNIQUE ID and different emoji, marked as NEW
            val newImage = ImageItem(nextImageId, newEmoji, true)

            // Remove the old emoji from used set and add new one
            val oldNewEmoji = currentImages.last().emoji
            usedEmojis = (usedEmojis - oldNewEmoji) + newEmoji
            nextImageId++

            // Update the images list with previous + new replacement
            currentImages = previousImages + newImage

            // Reset level start time for retry
            levelStartTimes = levelStartTimes + (currentLevel to System.currentTimeMillis())

            Log.d(TAG, "🔄 Retrying level $currentLevel")
            Log.d(TAG, "🔄 Old new emoji: $oldNewEmoji, New emoji: $newEmoji")
        }
    }

    fun handleImageClick(imageId: Int) {
        if (showFeedback || gameComplete || !isGameActive) return

        selectedImageId = imageId
        totalAttempts++

        val clickedImage = currentImages.find { it.id == imageId }
        val isCorrect = clickedImage?.isNew == true

        // Calculate reaction time
        val levelStartTime = levelStartTimes[currentLevel] ?: System.currentTimeMillis()
        val reactionTime = System.currentTimeMillis() - levelStartTime
        reactionTimes = reactionTimes + reactionTime

        Log.d(TAG, "🖼️ Image clicked: ID=$imageId, emoji=${clickedImage?.emoji}, isNew=${clickedImage?.isNew}")
        Log.d(TAG, "🖼️ Level: $currentLevel, Reaction time: ${reactionTime}ms")
        Log.d(TAG, "🖼️ Is correct: $isCorrect")

        feedbackCorrect = isCorrect
        showFeedback = true

        if (isCorrect) {
            correctAnswers++
            streak++
            if (streak > bestStreak) bestStreak = streak
        } else {
            incorrectSelections++
            streak = 0
        }
    }

    // Handle feedback completion
    LaunchedEffect(showFeedback) {
        if (showFeedback) {
            delay(1500) // Show feedback for 1.5 seconds
            showFeedback = false
            selectedImageId = null // Clear selection

            if (feedbackCorrect) {
                // Correct answer
                if (currentLevel >= targetPuzzleCount) {
                    // Game complete - calculate final score
                    gameComplete = true
                    isGameActive = false

                    totalScore = calculateVisualAttentionScore(
                        correctAnswers = correctAnswers,
                        totalAttempts = totalAttempts,
                        levelsCompleted = currentLevel,
                        avgReactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.average().toLong() else 2000L,
                        difficulty = difficulty,
                        timeSpentMs = System.currentTimeMillis() - gameStartTime,
                        bestStreak = bestStreak,
                        incorrectSelections = incorrectSelections
                    )

                    Log.d(TAG, "🎉 Image Vortex game completed!")
                    Log.d(TAG, "📊 Final score: $totalScore")
                    Log.d(TAG, "📊 Levels completed: $currentLevel")

                    // Show feedback through unified system
                    feedbackManager.showFeedback(
                        puzzleType = "visualAttention",
                        isCorrect = true,
                        userAnswer = "$correctAnswers/$totalAttempts levels completed",
                        correctAnswer = "Identify new images in visual search",
                        timeSpent = System.currentTimeMillis() - gameStartTime,
                        difficulty = difficulty,
                        timeRemaining = timeRemaining,
                        totalTime = totalTimeSeconds,
                        onComplete = {
                            onSubmitAnswer(true)
                            Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                            fetchNextPuzzle(totalScore)
                        }
                    )
                } else {
                    // Move to next level
                    currentLevel++
                    addNewImage()
                }
            } else {
                // Wrong answer - retry same level
                retryCurrentLevel()
            }
        }
    }

    // Format timer
    fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp)
        ) {
            // Enhanced Header with Score
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                            text = "Image Vortex",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E3A59)
                        )

                        IconButton(onClick = onBack) {
                            Text("❌", fontSize = 20.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = level,
                            fontSize = 16.sp,
                            color = Color(0xFF666666)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(hearts) {
                                Text("❤️", fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "⏱️ ${formatTime(timeRemaining)}",
                                fontSize = 16.sp,
                                color = if (timeRemaining < 30) Color.Red else Color(0xFF666666),
                                fontWeight = if (timeRemaining < 30) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Score and streak display
                    if (totalScore > 0 || correctAnswers > 0) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (totalScore > 0) {
                                Text(
                                    text = "${stringResource(R.string.score_label)}: $totalScore",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                            }

                            Text(
                                text = "$correctAnswers/$totalAttempts correct",
                                fontSize = 14.sp,
                                color = Color(0xFF2196F3)
                            )

                            if (streak > 1) {
                                Text(
                                    text = "🔥 $streak",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF6B00)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Enhanced Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎯 Find the NEW image!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Level $currentLevel: Tap the image that just appeared",
                        fontSize = 16.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )

                    // Performance coaching
                    if (correctAnswers > 0 && reactionTimes.isNotEmpty()) {
                        val avgReaction = reactionTimes.average() / 1000.0
                        Text(
                            text = "⚡ Avg speed: ${String.format("%.1f", avgReaction)}s",
                            fontSize = 12.sp,
                            color = if (avgReaction <= 1.5) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Images randomly positioned
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Generate random positions for each image
                currentImages.forEach { imageItem ->
                    val randomX = remember(imageItem.id, currentLevel) { Random.nextFloat() * 0.8f }
                    val randomY = remember(imageItem.id, currentLevel) { Random.nextFloat() * 0.8f }

                    RandomPositionedEmoji(
                        imageItem = imageItem,
                        isSelected = selectedImageId == imageItem.id,
                        showFeedback = showFeedback,
                        feedbackCorrect = feedbackCorrect,
                        xOffset = randomX,
                        yOffset = randomY,
                        onClick = { handleImageClick(imageItem.id) }
                    )
                }

                // Feedback overlay
                if (showFeedback) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (feedbackCorrect) Color(0xFF4CAF50) else Color(0xFFF44336)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (feedbackCorrect) "✅" else "❌",
                                    fontSize = 48.sp
                                )
                                Text(
                                    text = if (feedbackCorrect) stringResource(R.string.correct) else "${stringResource(R.string.try_again)}!",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (feedbackCorrect) {
                                    val reactionTime = if (reactionTimes.isNotEmpty()) {
                                        reactionTimes.last() / 1000.0
                                    } else 0.0

                                    Text(
                                        text = "⚡ ${String.format("%.1f", reactionTime)}s",
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced Progress bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${stringResource(R.string.progress)}: $currentLevel / $targetPuzzleCount",
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )

                        if (bestStreak > 1) {
                            Text(
                                text = "Best streak: $bestStreak",
                                fontSize = 12.sp,
                                color = Color(0xFFFF6B00)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = currentLevel.toFloat() / targetPuzzleCount.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF2196F3),
                        trackColor = Color(0xFFE0E0E0)
                    )
                }
            }
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
fun RandomPositionedEmoji(
    imageItem: ImageItem,
    isSelected: Boolean,
    showFeedback: Boolean,
    feedbackCorrect: Boolean,
    xOffset: Float,
    yOffset: Float,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected && showFeedback) 1.3f else if (imageItem.isNew) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (showFeedback && !isSelected) 0.5f else 1f,
        animationSpec = tween(300),
        label = "alpha"
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val xPosition = (maxWidth * xOffset)
        val yPosition = (maxHeight * yOffset)

        Box(
            modifier = Modifier
                .offset(x = xPosition, y = yPosition)
                .size(60.dp)
                .scale(scale)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Main emoji
            Text(
                text = imageItem.emoji,
                fontSize = 48.sp,
                modifier = Modifier.graphicsLayer(alpha = alpha)
            )

            // Subtle glow effect for new items
            if (imageItem.isNew && !showFeedback) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .background(
                            color = Color(0xFF2196F3).copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .scale(1.2f)
                )
            }

            // Show feedback overlay
            if (isSelected && showFeedback) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = (if (feedbackCorrect) Color(0xFF4CAF50) else Color(0xFFF44336))
                                .copy(alpha = 0.8f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (feedbackCorrect) "✅" else "❌",
                        fontSize = 24.sp
                    )
                }
            }
        }
    }
}
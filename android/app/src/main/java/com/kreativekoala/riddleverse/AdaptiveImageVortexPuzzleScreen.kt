// AdaptiveImageVortexPuzzleScreen.kt - FIXED VERSION
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.animation.*
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.res.stringResource
import kotlin.random.Random

// FIXED: Proper progression-based configuration
data class AdaptiveImageVortexConfig(
    val startingImages: Int, // Always start with this many images
    val imagesAddedPerLevel: Int, // How many new images to add each level
    val maxImages: Int, // Maximum images on screen
    val responseTimeLimit: Float, // Time limit per level in seconds
    val showVisualHints: Boolean, // Whether to show hints for new items
    val studyTimePerImage: Float, // Time to study existing images before new ones appear
    val sequenceLength: Int, // Number of levels to complete
    val difficultyName: String,
    val description: String
)

// Enhanced image item with proper state tracking
data class AdaptiveImageItem(
    val id: Int,
    val emoji: String,
    val isNew: Boolean = false, // TRUE only for images added in current level
    val wasAdded: Boolean = false, // Was this added in current level (for animation)
    val addedAtTime: Long = 0L, // When was this image added
    val scaleFactor: Float = 1.0f,
    val isVisible: Boolean = true // For study phase management
)

enum class IVGamePhase {
    STUDYING_EXISTING, // Show existing images for study
    ADDING_NEW_IMAGES, // Add new images with animation
    WAITING_FOR_ANSWER, // User should click the new image(s)
    SHOWING_FEEDBACK, // Show if correct/incorrect
    COMPLETED // Game over
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveImageVortexPuzzleScreen(
    difficulty: String = "Beginner", // CHANGED: Default to Beginner
    timer: String = "2:30",
    hearts: Int = 3,
    level: String = "12 / 53",
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveImageVortex"

    // FIXED: Proper difficulty management with storage
    var currentDifficultyLevel by remember { mutableStateOf(getDifficultyFromString(difficulty)) }
    var showDifficultyChanged by remember { mutableStateOf(false) }

    // Load user's saved difficulty on start
    LaunchedEffect(Unit) {
        val savedDifficulty = loadUserImageVortexDifficulty()
        if (savedDifficulty != currentDifficultyLevel.name) {
            currentDifficultyLevel = getDifficultyFromString(savedDifficulty)
            Log.d(TAG, "🎯 Loaded saved difficulty: ${currentDifficultyLevel.name}")
        }
    }

    // Generate proper configuration based on difficulty
    val config = remember(currentDifficultyLevel) {
        generateProperImageVortexConfig(currentDifficultyLevel)
    }

    Log.d(TAG, "🎮 Starting Image Vortex with config: ${config.difficultyName}")
    Log.d(TAG, "📊 Config details: startingImages=${config.startingImages}, addedPerLevel=${config.imagesAddedPerLevel}, studyTime=${config.studyTimePerImage}s")

    // Enhanced emoji categories
    val emojiCategories = remember {
        mapOf(
            "animals" to listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵"),
            "food" to listOf("🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍈", "🍑", "🍐", "🥭", "🍍", "🥥", "🥝", "🍅"),
            "sports" to listOf("⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸", "🥅", "🏆", "🥇", "🥈", "🥉"),
            "nature" to listOf("🌲", "🌳", "🌴", "🌵", "🌶️", "🌷", "🌸", "🌹", "🌺", "🌻", "🌼", "🌽", "🥦", "🥒", "🍄"),
            "transport" to listOf("🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🚚", "🚛", "🚜", "🏍️", "🚲"),
            "objects" to listOf("⚽", "🎈", "🎁", "🎀", "🎊", "🎉", "🎂", "🕯️", "🔔", "🎵", "🎶", "🎸", "🎹", "🎨", "🖼️")
        )
    }

    val availableEmojis = remember { emojiCategories.values.flatten().shuffled() }

    // FIXED: Game state for proper progression
    var gamePhase by remember { mutableStateOf(IVGamePhase.STUDYING_EXISTING) }
    var currentLevel by remember { mutableIntStateOf(1) }
    var currentImages by remember { mutableStateOf(listOf<AdaptiveImageItem>()) }
    var usedEmojis by remember { mutableStateOf(setOf<String>()) }
    var nextImageId by remember { mutableIntStateOf(1) }

    // Study and response timing
    var studyTimeRemaining by remember { mutableFloatStateOf(config.studyTimePerImage) }
    var responseTimeRemaining by remember { mutableFloatStateOf(config.responseTimeLimit) }
    var levelStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // User interaction
    var selectedImageIds by remember { mutableStateOf(setOf<Int>()) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackCorrect by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }

    // Performance tracking
    var totalScore by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var totalAttempts by remember { mutableIntStateOf(0) }
    var reactionTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    var currentHearts by remember { mutableIntStateOf(hearts) }
    var gameComplete by remember { mutableStateOf(false) }

    // Game timer
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else 150
    }
    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }

    val haptics = LocalHapticFeedback.current

    // Declare endGame function
    fun endGame(success: Boolean, message: String) {
        Log.d(TAG, "🎮 Game ended: success=$success, finalScore=$totalScore")
        gameComplete = true

        // Save difficulty progression
        if (success && correctAnswers >= config.sequenceLength * 0.8) {
            // Consider advancing difficulty
            val nextDifficulty = getNextDifficulty(currentDifficultyLevel)
            if (nextDifficulty != currentDifficultyLevel.name) {
                saveUserImageVortexDifficulty(nextDifficulty)
                showDifficultyChanged = SHOW_ADAPTATION_NOTICES
                Log.d(TAG, "📈 Difficulty advanced to: $nextDifficulty")
            }
        }

        onSubmitAnswer(success)
        fetchNextPuzzle(totalScore)
    }

    // Declare handleLevelResult function
    fun handleLevelResult(success: Boolean, message: String) {
        totalAttempts++
        feedbackCorrect = success
        feedbackMessage = message
        showFeedback = true
        gamePhase = IVGamePhase.SHOWING_FEEDBACK

        Log.d(TAG, "📊 Level $currentLevel result: success=$success, message='$message'")

        if (success) {
            correctAnswers++
            streak++
            if (streak > bestStreak) bestStreak = streak

            // Calculate score
            val levelScore = calculateLevelScore(
                reactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.last() else 0L,
                timeLimit = config.responseTimeLimit,
                difficulty = currentDifficultyLevel,
                imagesCount = currentImages.size,
                streak = streak
            )
            totalScore += levelScore

            Log.d(TAG, "🏆 Level completed! Score: +$levelScore, Total: $totalScore, Streak: $streak")
        } else {
            streak = 0
            currentHearts = maxOf(0, currentHearts - 1)
            Log.d(TAG, "💔 Level failed. Hearts remaining: $currentHearts")
        }
    }


    fun handleImageClick(imageId: Int) {
        if (gamePhase != IVGamePhase.WAITING_FOR_ANSWER || showFeedback) return

        val clickedImage = currentImages.find { it.id == imageId } ?: return
        val reactionTime = System.currentTimeMillis() - levelStartTime

        Log.d(TAG, "🖱️ Clicked image: ${clickedImage.emoji}, isNew: ${clickedImage.isNew}, reactionTime: ${reactionTime}ms")

        selectedImageIds = selectedImageIds + imageId
        val newImagesInLevel = currentImages.filter { it.isNew }
        val isCorrectSelection = clickedImage.isNew

        Log.d(TAG, "🎯 New images in level: ${newImagesInLevel.map { it.emoji }}")
        Log.d(TAG, "✅ Correct selection: $isCorrectSelection")

        // Check if all new images have been selected
        val allNewImagesSelected = newImagesInLevel.all { it.id in selectedImageIds }

        if (isCorrectSelection) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)

            if (allNewImagesSelected) {
                // Level complete!
                reactionTimes = reactionTimes + reactionTime
                handleLevelResult(true, "Perfect! Found all new images!")
            } else {
                // Correct but more to find
                Log.d(TAG, "✅ Correct selection, but more new images to find")
            }
        } else {
            // Wrong selection
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            reactionTimes = reactionTimes + reactionTime
            handleLevelResult(false, "Oops! That wasn't a new image.")
        }
    }

    // FIXED: Initialize with starting images
    LaunchedEffect(Unit) {
        Log.d(TAG, "🎬 Initializing level 1 with ${config.startingImages} images")
        val initialResult = generateInitialLevel(
            startingImageCount = config.startingImages,
            availableEmojis = availableEmojis,
            startId = 1
        )
        currentImages = initialResult.first
        usedEmojis = initialResult.second
        nextImageId = currentImages.size + 1
        levelStartTime = System.currentTimeMillis()

        Log.d(TAG, "✅ Level 1 initialized with images: ${currentImages.map { "${it.emoji}(new=${it.isNew})" }}")
    }

    // FIXED: Proper phase management with study time
    LaunchedEffect(gamePhase, studyTimeRemaining) {
        when (gamePhase) {
            IVGamePhase.STUDYING_EXISTING -> {
                if (currentLevel == 1) {
                    // Skip study phase for level 1 (no existing images to study)
                    Log.d(TAG, "⏭️ Level 1: Going directly to answer phase")
                    gamePhase = IVGamePhase.WAITING_FOR_ANSWER  // CHANGE: Skip ADDING_NEW_IMAGES
                    responseTimeRemaining = config.responseTimeLimit
                    levelStartTime = System.currentTimeMillis()
                } else {
                    Log.d(TAG, "📖 Study phase: ${studyTimeRemaining}s remaining")
                    while (studyTimeRemaining > 0 && gamePhase == IVGamePhase.STUDYING_EXISTING) {
                        delay(100)
                        studyTimeRemaining -= 0.1f
                    }
                    if (gamePhase == IVGamePhase.STUDYING_EXISTING) {
                        Log.d(TAG, "⏰ Study time up, adding new images")
                        gamePhase = IVGamePhase.ADDING_NEW_IMAGES
                    }
                }
            }
            IVGamePhase.ADDING_NEW_IMAGES -> {
                if (currentLevel == 1) {
                    Log.e(TAG, "❌ ADDING_NEW_IMAGES should not happen for Level 1")
                    gamePhase = IVGamePhase.WAITING_FOR_ANSWER
                    responseTimeRemaining = config.responseTimeLimit
                    levelStartTime = System.currentTimeMillis()
                    return@LaunchedEffect
                }
                Log.d(TAG, "➕ Adding ${config.imagesAddedPerLevel} new images to level $currentLevel")
                val addResult = addNewImagesToLevel(
                    existingImages = currentImages,
                    imagesToAdd = config.imagesAddedPerLevel,
                    usedEmojis = usedEmojis,
                    availableEmojis = availableEmojis,
                    startId = nextImageId
                )

                currentImages = addResult.first
                usedEmojis = addResult.second
                nextImageId = currentImages.maxOfOrNull { it.id }?.plus(1) ?: 1

                Log.d(TAG, "✅ Added new images. Total images: ${currentImages.size}")
                Log.d(TAG, "📝 Current images: ${currentImages.map { "${it.emoji}(new=${it.isNew})" }}")

                // Brief delay to show animation, then start response phase
                delay(800)
                gamePhase = IVGamePhase.WAITING_FOR_ANSWER
                responseTimeRemaining = config.responseTimeLimit
                levelStartTime = System.currentTimeMillis()
            }
            else -> {} // Handle other phases elsewhere
        }
    }

    // Response timer
    LaunchedEffect(gamePhase, responseTimeRemaining) {
        if (gamePhase == IVGamePhase.WAITING_FOR_ANSWER && responseTimeRemaining > 0) {
            while (responseTimeRemaining > 0 && gamePhase == IVGamePhase.WAITING_FOR_ANSWER) {
                delay(100)
                responseTimeRemaining -= 0.1f
            }

            // Time's up - auto fail
            if (gamePhase == IVGamePhase.WAITING_FOR_ANSWER && responseTimeRemaining <= 0) {
                Log.d(TAG, "⏰ Response time up! Auto-failing level $currentLevel")
                handleLevelResult(false, "Time's up! Try to be faster next time.")
            }
        }
    }

    // Main game timer
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0 && !gameComplete) {
            delay(1000L)
            timeRemaining--
        } else if (timeRemaining == 0) {
            endGame(false, "Time's up!")
        }
    }

    // Handle feedback completion and level progression
    LaunchedEffect(showFeedback, feedbackCorrect) {
        if (showFeedback) {
            delay(2000) // Show feedback for 2 seconds
            showFeedback = false

            if (!feedbackCorrect && currentHearts > 0) {
                // Retry same level
                Log.d(TAG, "🔄 Retrying level $currentLevel")
                gamePhase = IVGamePhase.STUDYING_EXISTING
                studyTimeRemaining = config.studyTimePerImage
                selectedImageIds = emptySet()
            } else if (feedbackCorrect && currentLevel < config.sequenceLength) {
                // Next level
                currentLevel++
                Log.d(TAG, "⬆️ Advancing to level $currentLevel")

                // Mark all current images as "old" (not new anymore)
                currentImages = currentImages.map { it.copy(isNew = false) }

                gamePhase = IVGamePhase.STUDYING_EXISTING
                studyTimeRemaining = config.studyTimePerImage
                selectedImageIds = emptySet()
            } else {
                // Game complete
                endGame(feedbackCorrect, if (feedbackCorrect) "Congratulations!" else "Game Over")
            }
        }
    }

    // Heart check
    LaunchedEffect(currentHearts) {
        if (currentHearts <= 0) {
            endGame(false, "No more hearts!")
        }
    }




    // UI
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(RvSurface)) {
        val compact = maxHeight < 600.dp
        CompositionLocalProvider(LocalIvCompact provides compact) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 8.dp else 16.dp)
        ) {
            // Header
            ProperImageVortexHeader(
                difficulty = currentDifficultyLevel.name,
                timer = ivFormatTime(timeRemaining),
                hearts = currentHearts,
                level = "$currentLevel / ${config.sequenceLength}",
                score = totalScore,
                streak = streak,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 12.dp))

            // Phase indicator
            PhaseIndicatorCard(
                gamePhase = gamePhase,
                currentLevel = currentLevel,
                studyTimeRemaining = studyTimeRemaining,
                responseTimeRemaining = responseTimeRemaining,
                config = config,
                selectedCount = selectedImageIds.size,
                totalNewImages = currentImages.count { it.isNew }
            )

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

            // Images display area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Render all images
                currentImages.forEach { imageItem ->
                    ProperPositionedEmoji(
                        imageItem = imageItem,
                        isSelected = imageItem.id in selectedImageIds,
                        gamePhase = gamePhase,
                        showFeedback = showFeedback,
                        feedbackCorrect = feedbackCorrect,
                        onClick = { handleImageClick(imageItem.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

            // Progress
            ProgressCard(
                currentLevel = currentLevel,
                totalLevels = config.sequenceLength,
                correctAnswers = correctAnswers,
                totalAttempts = totalAttempts,
                bestStreak = bestStreak
            )
        }
        }

        // Feedback overlay
        if (showFeedback) {
            FeedbackOverlay(
                isCorrect = feedbackCorrect,
                message = feedbackMessage,
                streak = if (feedbackCorrect) streak else 0
            )
        }

        // Difficulty advancement notification
        if (showDifficultyChanged) {
            DifficultyAdvancementOverlay(
                newDifficulty = currentDifficultyLevel.name,
                onDismiss = { showDifficultyChanged = false }
            )
        }
    }
}

// FIXED: Proper configuration generation
fun generateProperImageVortexConfig(difficulty: DifficultyLevel): AdaptiveImageVortexConfig {
    return when (difficulty.name.lowercase()) {
        "beginner" -> AdaptiveImageVortexConfig(
            startingImages = 1,
            imagesAddedPerLevel = 1,
            maxImages = 6,
            responseTimeLimit = 8f,
            showVisualHints = true,
            studyTimePerImage = 3f,
            sequenceLength = 8,
            difficultyName = "Beginner",
            description = "Start with 1 image, add 1 each level"
        )
        "easy" -> AdaptiveImageVortexConfig(
            startingImages = 1,
            imagesAddedPerLevel = 1,
            maxImages = 8,
            responseTimeLimit = 6f,
            showVisualHints = true,
            studyTimePerImage = 2.5f,
            sequenceLength = 10,
            difficultyName = "Easy",
            description = "Start with 2 images, add 1 each level"
        )
        "medium" -> AdaptiveImageVortexConfig(
            startingImages = 1,
            imagesAddedPerLevel = 2,
            maxImages = 12,
            responseTimeLimit = 5f,
            showVisualHints = false,
            studyTimePerImage = 2f,
            sequenceLength = 12,
            difficultyName = "Medium",
            description = "Start with 3 images, add 2 each level"
        )
        "hard" -> AdaptiveImageVortexConfig(
            startingImages = 1,
            imagesAddedPerLevel = 2,
            maxImages = 16,
            responseTimeLimit = 4f,
            showVisualHints = false,
            studyTimePerImage = 1.5f,
            sequenceLength = 15,
            difficultyName = "Hard",
            description = "Start with 4 images, add 2 each level"
        )
        "expert" -> AdaptiveImageVortexConfig(
            startingImages = 1,
            imagesAddedPerLevel = 3,
            maxImages = 20,
            responseTimeLimit = 3f,
            showVisualHints = false,
            studyTimePerImage = 1f,
            sequenceLength = 18,
            difficultyName = "Expert",
            description = "Start with 5 images, add 3 each level"
        )
        else -> generateProperImageVortexConfig(DifficultyLevel("Beginner", 0, 10, 60f, "Beginner level"))
    }
}

// Helper functions for proper game logic

fun generateInitialLevel(
    startingImageCount: Int,
    availableEmojis: List<String>,
    startId: Int
): Pair<List<AdaptiveImageItem>, Set<String>> {
    val images = mutableListOf<AdaptiveImageItem>()
    val usedEmojis = mutableSetOf<String>()
    var currentId = startId

    // For level 1, all images are "new" since there are no previous images
    repeat(startingImageCount) {
        if (availableEmojis.isNotEmpty()) {
            val emoji = (availableEmojis - usedEmojis).randomOrNull() ?: availableEmojis.random()
            images.add(
                AdaptiveImageItem(
                    id = currentId++,
                    emoji = emoji,
                    isNew = true, // All images are new in first level
                    wasAdded = true,
                    addedAtTime = System.currentTimeMillis()
                )
            )
            usedEmojis.add(emoji)
        }
    }

    return images to usedEmojis
}

fun addNewImagesToLevel(
    existingImages: List<AdaptiveImageItem>,
    imagesToAdd: Int,
    usedEmojis: Set<String>,
    availableEmojis: List<String>,
    startId: Int
): Pair<List<AdaptiveImageItem>, Set<String>> {
    val newImages = mutableListOf<AdaptiveImageItem>()
    val updatedUsedEmojis = usedEmojis.toMutableSet()
    var currentId = startId

    // Mark all existing images as NOT new anymore
    val updatedExistingImages = existingImages.map { it.copy(isNew = false) }

    // Add new images
    repeat(imagesToAdd) {
        val availableForNew = availableEmojis - updatedUsedEmojis
        if (availableForNew.isNotEmpty()) {
            val emoji = availableForNew.random()
            newImages.add(
                AdaptiveImageItem(
                    id = currentId++,
                    emoji = emoji,
                    isNew = true, // These are the NEW images to find
                    wasAdded = true,
                    addedAtTime = System.currentTimeMillis()
                )
            )
            updatedUsedEmojis.add(emoji)
        }
    }

    return (updatedExistingImages + newImages) to updatedUsedEmojis
}

fun calculateLevelScore(
    reactionTime: Long,
    timeLimit: Float,
    difficulty: DifficultyLevel,
    imagesCount: Int,
    streak: Int
): Int {
    val baseScore = difficulty.basePoints

    // Speed bonus
    val reactionSeconds = reactionTime / 1000f
    val speedBonus = when {
        reactionSeconds <= timeLimit * 0.3f -> (baseScore * 0.5f).toInt()
        reactionSeconds <= timeLimit * 0.5f -> (baseScore * 0.3f).toInt()
        reactionSeconds <= timeLimit * 0.75f -> (baseScore * 0.1f).toInt()
        else -> 0
    }

    // Complexity bonus
    val complexityBonus = (imagesCount - 2) * 5 // More images = more points

    // Streak bonus
    val streakBonus = when {
        streak >= 5 -> (baseScore * 0.3f).toInt()
        streak >= 3 -> (baseScore * 0.2f).toInt()
        streak >= 2 -> (baseScore * 0.1f).toInt()
        else -> 0
    }

    return baseScore + speedBonus + complexityBonus + streakBonus
}

// Helper data class for difficulty levels
data class DifficultyLevel(
    val name: String,
    val index: Int,
    val basePoints: Int,
    val timeLimit: Float,
    val description: String
)

fun getDifficultyFromString(difficultyName: String): DifficultyLevel {
    return when (difficultyName.lowercase()) {
        "beginner" -> DifficultyLevel("Beginner", 0, 10, 60f, "Perfect for learning")
        "easy" -> DifficultyLevel("Easy", 1, 15, 45f, "Getting comfortable")
        "medium" -> DifficultyLevel("Medium", 2, 20, 30f, "Good challenge")
        "hard" -> DifficultyLevel("Hard", 3, 25, 20f, "Expert level")
        "expert" -> DifficultyLevel("Expert", 4, 30, 15f, "Master level")
        else -> DifficultyLevel("Beginner", 0, 10, 60f, "Perfect for learning")
    }
}

// PLACEHOLDER: You'll need to implement these based on your storage system
fun loadUserImageVortexDifficulty(): String {
    // TODO: Load from SharedPreferences or user profile
    return "Beginner" // Default to beginner
}

fun saveUserImageVortexDifficulty(difficulty: String) {
    // TODO: Save to SharedPreferences or user profile
    Log.d("ImageVortex", "💾 Saving difficulty: $difficulty")
}

fun getNextDifficulty(currentDifficulty: DifficultyLevel): String {
    return when (currentDifficulty.index) {
        0 -> "Easy"
        1 -> "Medium"
        2 -> "Hard"
        3 -> "Expert"
        else -> currentDifficulty.name // Stay at Expert
    }
}

fun ivFormatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

// UI Components (you'll need to implement these based on your existing UI patterns)

/** True on short viewports (small phones, landscape, split-screen): HUD collapses to single rows. */
private val LocalIvCompact = compositionLocalOf { false }

@Composable
fun ProperImageVortexHeader(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    score: Int,
    streak: Int,
    onBack: () -> Unit
) {
    val compact = LocalIvCompact.current
    // Single compact HUD row: back, difficulty/level, lives, score, timer
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = RvInk
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = difficulty.uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = "${stringResource(R.string.level_label)} $level" + if (streak > 1) "  🔥 $streak" else "",
                fontSize = 14.sp,
                color = RvInkSoft,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "❤️ $hearts",
                    fontSize = 14.sp,
                    color = RvInk,
                    maxLines = 1
                )
                Text(
                    text = timer,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    maxLines = 1
                )
            }
            Text(
                text = "${stringResource(R.string.score_label)}: $score",
                fontSize = 14.sp,
                color = RvInkSoft,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PhaseIndicatorCard(
    gamePhase: IVGamePhase,
    currentLevel: Int,
    studyTimeRemaining: Float,
    responseTimeRemaining: Float,
    config: AdaptiveImageVortexConfig,
    selectedCount: Int,
    totalNewImages: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (gamePhase) {
                IVGamePhase.STUDYING_EXISTING -> Color.Blue.copy(alpha = 0.1f)
                IVGamePhase.ADDING_NEW_IMAGES -> Color.Green.copy(alpha = 0.1f)
                IVGamePhase.WAITING_FOR_ANSWER -> Color.Cyan.copy(alpha = 0.1f)
                else -> Color.Gray.copy(alpha = 0.1f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (LocalIvCompact.current) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val (title, subtitle, timeText, timeColor) = when (gamePhase) {
                IVGamePhase.STUDYING_EXISTING -> {
                    if (currentLevel == 1) {
                        Tuple4("🎯 Level 1", "Find the ${config.imagesAddedPerLevel} image${if (config.imagesAddedPerLevel > 1) "s" else ""}!", "Ready!", Color.Green)
                    } else {
                        Tuple4("📖 Study Phase", "Memorize the existing images", "${studyTimeRemaining.toInt()}s", Color.Blue)
                    }
                }
                IVGamePhase.ADDING_NEW_IMAGES -> {
                    Tuple4("➕ Adding Images", "Watch for the NEW image${if (config.imagesAddedPerLevel > 1) "s" else ""}!", "Get Ready!", Color.Green)
                }
                IVGamePhase.WAITING_FOR_ANSWER -> {
                    if (currentLevel == 1) {
                        Tuple4("🎯 Level 1", "Tap the image to start!", "${responseTimeRemaining.toInt()}s",
                            if (responseTimeRemaining <= 2f) Color.Red else Color.Cyan)
                    } else {
                        Tuple4("🎯 Find the NEW Images!", "Tap the ${totalNewImages} image${if (totalNewImages > 1) "s" else ""} that just appeared", "${responseTimeRemaining.toInt()}s",
                            if (responseTimeRemaining <= 2f) Color.Red else Color.Cyan)
                    }
                }
                else -> {
                    Tuple4("", "", "", Color.Gray)
                }
            }

            val compact = LocalIvCompact.current
            // Timer colours darkened for contrast on the light card; wording is unchanged.
            val readableTimeColor = when (timeColor) {
                Color.Red -> Color(0xFFB3261E)
                Color.Cyan -> Color(0xFF00696B)
                Color.Green -> Color(0xFF15803D)
                Color.Blue -> Color(0xFF1565C0)
                else -> timeColor
            }
            if (compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            fontSize = 14.sp,
                            color = RvInkSoft,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = timeText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = readableTimeColor,
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = RvInkSoft,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Text(
                    text = timeText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = readableTimeColor
                )
            }

            if (gamePhase == IVGamePhase.WAITING_FOR_ANSWER && selectedCount > 0) {
                Text(
                    text = "Selected: $selectedCount / $totalNewImages",
                    fontSize = 14.sp,
                    color = Color(0xFF1565C0)
                )
            }
        }
    }
}


@Composable
fun ProperPositionedEmoji(
    imageItem: AdaptiveImageItem,
    isSelected: Boolean,
    gamePhase: IVGamePhase,
    showFeedback: Boolean,
    feedbackCorrect: Boolean,
    onClick: () -> Unit
) {
    // Generate consistent position based on ID
    val xOffset = remember(imageItem.id) { Random(imageItem.id).nextFloat() * 0.8f + 0.1f }
    val yOffset = remember(imageItem.id) { Random(imageItem.id + 1000).nextFloat() * 0.8f + 0.1f }

    // Animation for newly added images
    val scale by animateFloatAsState(
        targetValue = when {
            imageItem.wasAdded && gamePhase == IVGamePhase.ADDING_NEW_IMAGES -> 1.3f
            isSelected -> 1.2f
            imageItem.isNew -> 1.1f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = when (gamePhase) {
            IVGamePhase.STUDYING_EXISTING -> if (imageItem.isNew) 0.3f else 1.0f
            else -> 1.0f
        },
        label = "alpha"
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // FIXED: Ensure valid coerceIn range
        val emojiSize = if (LocalIvCompact.current) 56.dp else 70.dp
        val emojiSp = with(LocalDensity.current) { (emojiSize - 22.dp).toSp() } // glyph stays same size at any font scale
        val safeMaxWidth = maxOf(emojiSize, maxWidth)
        val safeMaxHeight = maxOf(emojiSize, maxHeight)

        val xPosition = (safeMaxWidth * xOffset).coerceIn(0.dp, safeMaxWidth - emojiSize)
        val yPosition = (safeMaxHeight * yOffset).coerceIn(0.dp, safeMaxHeight - emojiSize)

        Box(
            modifier = Modifier
                .offset(x = xPosition, y = yPosition)
                .size(emojiSize)
                .scale(scale)
                .clickable(
                    enabled = gamePhase == IVGamePhase.WAITING_FOR_ANSWER && !showFeedback
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .border(
                            width = 3.dp,
                            color = Color.Blue,
                            shape = CircleShape
                        )
                )
            }

            // Main emoji
            Text(
                text = imageItem.emoji,
                fontSize = emojiSp,
                modifier = Modifier.graphicsLayer(alpha = alpha)
            )

            // Feedback overlay
            if (isSelected && showFeedback) {
                Box(
                    modifier = Modifier
                        .size(95.dp)
                        .background(
                            color = (if (feedbackCorrect) Color.Green else Color.Red).copy(alpha = 0.8f),
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

@Composable
fun ProgressCard(
    currentLevel: Int,
    totalLevels: Int,
    correctAnswers: Int,
    totalAttempts: Int,
    bestStreak: Int
) {
    val compact = LocalIvCompact.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Level: $currentLevel / $totalLevels",
                    fontSize = 14.sp,
                    color = RvInk,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${stringResource(R.string.best_streak)}: $bestStreak",
                    fontSize = 12.sp,
                    color = Color(0xFFB3261E),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = currentLevel.toFloat() / totalLevels.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF1565C0),
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )

            // Secondary stats are dropped on short screens to keep the play area large.
            if (!compact) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Correct: $correctAnswers",
                        fontSize = 12.sp,
                        color = Color(0xFF15803D),
                        maxLines = 1
                    )
                    Text(
                        text = "Total: $totalAttempts",
                        fontSize = 12.sp,
                        color = RvInkSoft,
                        maxLines = 1
                    )
                    if (totalAttempts > 0) {
                        Text(
                            text = "${(correctAnswers * 100 / totalAttempts)}% accuracy",
                            fontSize = 12.sp,
                            color = Color(0xFF1565C0),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeedbackOverlay(
    isCorrect: Boolean,
    message: String,
    streak: Int
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isCorrect) Color.Green else Color.Red
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isCorrect) "✅" else "❌",
                    fontSize = 48.sp
                )

                Text(
                    text = if (isCorrect) "Excellent!" else "Try Again!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                Text(
                    text = message,
                    fontSize = 16.sp,
                    color = RvInk,
                    textAlign = TextAlign.Center
                )

                if (isCorrect && streak > 1) {
                    Text(
                        text = "🔥 Streak: $streak",
                        fontSize = 14.sp,
                        color = RvInk,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DifficultyAdvancementOverlay(
    newDifficulty: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Blue),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉",
                    fontSize = 48.sp
                )

                Text(
                    text = stringResource(R.string.difficulty_advanced),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                Text(
                    text = "You're now playing at $newDifficulty level!",
                    fontSize = 16.sp,
                    color = RvInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = RvSurfaceRaised)
                ) {
                    Text(
                        text = stringResource(R.string.continue_label),
                        color = Color.Blue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
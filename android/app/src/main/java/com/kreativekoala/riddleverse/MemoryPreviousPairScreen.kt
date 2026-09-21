package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import androidx.compose.ui.res.stringResource
import kotlin.math.sin
import com.kreativekoala.riddleverse.ui.theme.RvCanvas
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvSurface

data class MemorySequence(
    val screenNumber: Int,
    val numbers: List<Int>,
    val linkingNumber: Int?,
    val isFirstScreen: Boolean
)

data class ForestAnimal(
    val id: Int,
    val name: String,
    val emoji: String,
    val color: Color
)

@Composable
fun MemoryPreviousPairPuzzleScreen(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    puzzleData: String,
    correctAnswer: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "MemoryPairGame"

    // Score tracking state
    var totalScore by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var totalQuestions by remember { mutableIntStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var screenStartTimes by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var reactionTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var memoryLoadScores by remember { mutableStateOf<List<Int>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }

    // Parse puzzle data
    val sequenceData = remember(puzzleData) {
        try {
            val json = JSONObject(puzzleData)
            val sequenceArray = json.getJSONArray("sequence")
            val sequences = mutableListOf<MemorySequence>()

            for (i in 0 until sequenceArray.length()) {
                val seqObj = sequenceArray.getJSONObject(i)
                val numbersArray = seqObj.getJSONArray("numbers")
                val numbers = List(numbersArray.length()) { idx -> numbersArray.getInt(idx) }

                sequences.add(
                    MemorySequence(
                        screenNumber = seqObj.getInt("screenNumber"),
                        numbers = numbers,
                        linkingNumber = if (seqObj.isNull("linkingNumber")) null else seqObj.getInt("linkingNumber"),
                        isFirstScreen = seqObj.getBoolean("isFirstScreen")
                    )
                )
            }
            sequences
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            120 // Default 2 minutes for memory pairs
        }
    }

    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentUserLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Calculate total questions on initialization
    LaunchedEffect(sequenceData) {
        totalQuestions = sequenceData.count { !it.isFirstScreen }
        gameStartTime = System.currentTimeMillis()
        Log.d(TAG, "📊 Initialized: $totalQuestions questions from ${sequenceData.size} screens")
    }

    // Calculate pair memory score
    fun calculatePairMemoryScore(
        correctAnswers: Int,
        totalQuestions: Int,
        avgReactionTime: Long,
        difficulty: String,
        timeSpentMs: Long,
        bestStreak: Int,
        memoryLoadScores: List<Int>,
        sequenceComplexity: Int
    ): Int {
        if (correctAnswers == 0) return 0

        // Base points by difficulty
        val basePointsPerQuestion = when (difficulty.lowercase()) {
            "easy" -> 25
            "medium" -> 35
            "hard" -> 45
            "expert" -> 55
            else -> 35
        }

        val baseScore = correctAnswers * basePointsPerQuestion

        // Memory span complexity multiplier
        val complexityMultiplier = when (sequenceComplexity) {
            in 1..3 -> 1.0f
            in 4..6 -> 1.3f
            in 7..10 -> 1.6f
            else -> 2.0f
        }

        // Perfect recall bonus
        val accuracy = if (totalQuestions > 0) correctAnswers.toFloat() / totalQuestions else 0f
        val recallBonus = when {
            accuracy >= 0.95f -> (baseScore * 0.4f).toInt()
            accuracy >= 0.85f -> (baseScore * 0.25f).toInt()
            accuracy >= 0.75f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Memory processing speed bonus
        val avgReactionSeconds = avgReactionTime / 1000f
        val speedBonus = when {
            avgReactionSeconds <= 2.0f -> (baseScore * 0.25f).toInt()
            avgReactionSeconds <= 3.0f -> (baseScore * 0.15f).toInt()
            avgReactionSeconds <= 4.0f -> (baseScore * 0.05f).toInt()
            else -> 0
        }

        // Memory load handling bonus (average of load scores)
        val avgMemoryLoad = if (memoryLoadScores.isNotEmpty()) {
            memoryLoadScores.average()
        } else 0.0
        val loadBonus = when {
            avgMemoryLoad >= 4.0 -> (baseScore * 0.2f).toInt()
            avgMemoryLoad >= 3.0 -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Consecutive recall streak bonus
        val streakBonus = when {
            bestStreak >= totalQuestions -> (baseScore * 0.3f).toInt() // Perfect streak
            bestStreak >= totalQuestions * 0.7f -> (baseScore * 0.15f).toInt()
            bestStreak >= totalQuestions * 0.5f -> (baseScore * 0.05f).toInt()
            else -> 0
        }

        // Sequential memory bonus for maintaining temporal order
        val sequentialBonus = (baseScore * 0.15f).toInt()

        // Working memory bonus for active maintenance
        val workingMemoryBonus = (baseScore * 0.1f).toInt()

        val finalScore = ((baseScore * complexityMultiplier).toInt() + recallBonus + speedBonus + loadBonus + streakBonus + sequentialBonus + workingMemoryBonus)

        Log.d(TAG, "🏆 Pair memory score calculation:")
        Log.d(TAG, "  Correct: $correctAnswers/$totalQuestions (${(accuracy * 100).toInt()}%)")
        Log.d(TAG, "  Base score: $baseScore")
        Log.d(TAG, "  Complexity multiplier: ${complexityMultiplier}x")
        Log.d(TAG, "  Recall bonus: $recallBonus")
        Log.d(TAG, "  Speed bonus: $speedBonus (avg ${avgReactionSeconds}s)")
        Log.d(TAG, "  Load bonus: $loadBonus (avg load: ${String.format("%.1f", avgMemoryLoad)})")
        Log.d(TAG, "  Streak bonus: $streakBonus (best: $bestStreak)")
        Log.d(TAG, "  Sequential bonus: $sequentialBonus")
        Log.d(TAG, "  Working memory bonus: $workingMemoryBonus")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, baseScore / 4) // Minimum 25% of base
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0) {
            // Time's up - calculate final score
            totalScore = calculatePairMemoryScore(
                correctAnswers = correctAnswers,
                totalQuestions = totalQuestions,
                avgReactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.average().toLong() else 3000L,
                difficulty = difficulty,
                timeSpentMs = System.currentTimeMillis() - gameStartTime,
                bestStreak = bestStreak,
                memoryLoadScores = memoryLoadScores,
                sequenceComplexity = sequenceData.size
            )

            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            onSubmitAnswer(false)
            fetchNextPuzzle(totalScore)
        }
    }



    // Animal mapping
    val animals = remember {
        mapOf(
            1 to ForestAnimal(1, "Lion", "🦁", Color(0xFFD4A574)),
            2 to ForestAnimal(2, "Hippo", "🦛", Color(0xFF8B7D6B)),
            3 to ForestAnimal(3, "Elephant", "🐘", Color(0xFFA8A8A8)),
            4 to ForestAnimal(4, "Tiger", "🐅", Color(0xFFFF8C00)),
            5 to ForestAnimal(5, "Giraffe", "🦒", Color(0xFFDAA520)),
            6 to ForestAnimal(6, "Monkey", "🐵", Color(0xFFCD853F))
        )
    }

    var currentScreenIndex by remember { mutableIntStateOf(0) }
    var gameState by remember { mutableStateOf("playing") }
    var selectedAnimal by remember { mutableStateOf<Int?>(null) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrectAnswer by remember { mutableStateOf(false) }
    var userAnswers by remember { mutableStateOf(mutableListOf<Int>()) }

    // Animation states
    var animatingAnimals by remember { mutableStateOf(emptySet<Int>()) }
    var showingScreen by remember { mutableStateOf(false) }

    // Timer effect for screen display
    LaunchedEffect(currentScreenIndex) {
        if (currentScreenIndex < sequenceData.size) {
            showingScreen = true
            val currentSequence = sequenceData[currentScreenIndex]

            // Track screen start time for non-first screens
            if (!currentSequence.isFirstScreen) {
                screenStartTimes = screenStartTimes + (currentScreenIndex to System.currentTimeMillis())
            }

            // Start animal animations
            val currentAnimals = currentSequence.numbers.toSet()
            animatingAnimals = currentAnimals

            // Stop animations after 2 seconds
            delay(2000)
            animatingAnimals = emptySet()
        }
    }

    // Auto-advance effect for first screen
    LaunchedEffect(currentScreenIndex) {
        if (currentScreenIndex < sequenceData.size && sequenceData[currentScreenIndex].isFirstScreen) {
            delay(3000) // Show first screen for 3 seconds
            if (currentScreenIndex + 1 < sequenceData.size) {
                currentScreenIndex += 1
            }
        }
    }

    fun handleAnimalClick(animalId: Int) {
        if (currentScreenIndex >= sequenceData.size) return

        val currentSequence = sequenceData[currentScreenIndex]
        if (currentSequence.isFirstScreen) return // Can't click on first screen

        selectedAnimal = animalId

        // Calculate reaction time
        val screenStartTime = screenStartTimes[currentScreenIndex] ?: System.currentTimeMillis()
        val reactionTime = System.currentTimeMillis() - screenStartTime
        reactionTimes = reactionTimes + reactionTime

        // Calculate memory load score based on number of items to remember
        val memoryLoad = currentSequence.numbers.size
        memoryLoadScores = memoryLoadScores + memoryLoad

        // Check if answer is correct
        val expectedAnswer = currentSequence.linkingNumber ?: -1
        isCorrectAnswer = animalId == expectedAnswer

        Log.d(TAG, "🎯 Screen ${currentScreenIndex + 1}: Expected=$expectedAnswer, " +
                "Selected=$animalId, Correct=$isCorrectAnswer, Reaction=${reactionTime}ms, " +
                "Memory load=$memoryLoad")

        if (isCorrectAnswer) {
            correctAnswers++
            streak++
            if (streak > bestStreak) bestStreak = streak
            userAnswers.add(animalId)
        } else {
            streak = 0
        }

        showFeedback = true
    }

    // Auto-advance after feedback effect
    LaunchedEffect(showFeedback) {
        if (showFeedback) {
            delay(1500) // Show feedback for 1.5 seconds
            showFeedback = false
            selectedAnimal = null

            if (currentScreenIndex + 1 < sequenceData.size) {
                currentScreenIndex += 1
            } else {
                // Game complete - calculate final score
                totalScore = calculatePairMemoryScore(
                    correctAnswers = correctAnswers,
                    totalQuestions = totalQuestions,
                    avgReactionTime = if (reactionTimes.isNotEmpty()) reactionTimes.average().toLong() else 3000L,
                    difficulty = difficulty,
                    timeSpentMs = System.currentTimeMillis() - gameStartTime,
                    bestStreak = bestStreak,
                    memoryLoadScores = memoryLoadScores,
                    sequenceComplexity = sequenceData.size
                )

                gameState = "complete"
                val isSuccess = correctAnswers >= (totalQuestions * 0.6f).toInt()

                Log.d(TAG, "🎯 Game completed!")
                Log.d(TAG, "📊 Final stats:")
                Log.d(TAG, "   Total questions: $totalQuestions")
                Log.d(TAG, "   Correct answers: $correctAnswers")
                Log.d(TAG, "   Final score: $totalScore")
                Log.d(TAG, "   Success: $isSuccess")

                // Show feedback through unified system
                feedbackManager.showFeedback(
                    puzzleType = "pairMemory",
                    isCorrect = isSuccess,
                    userAnswer = "$correctAnswers/$totalQuestions pairs recalled",
                    correctAnswer = "Remember animal sequences and identify links",
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(RvCanvas)) {
        // Fit-to-screen: HUD (fixed), instruction, forest scene (all remaining space), stats footer.
        val compact = maxHeight < 600.dp
        val gutter = if (compact) 12.dp else 16.dp
        val isFirst = currentScreenIndex < sequenceData.size && sequenceData[currentScreenIndex].isFirstScreen
        val darkGreen = Color(0xFF15803D)
        val darkOrange = Color(0xFFB45309)

        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = gutter, vertical = if (compact) 4.dp else 12.dp)
        ) {
            BCompactHud(
                level = currentUserLevel,
                difficultyName = difficulty,
                timer = displayTimer,
                lives = hearts,
                maxLives = hearts,
                score = totalScore,
                streak = streak,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(if (compact) 2.dp else 8.dp))

            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RvSurface)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 12.dp)
                ) {
                    Text(
                        text = if (isFirst) {
                            "🧠 Remember these animals!"
                        } else {
                            "🎯 Tap the animal that appeared in the previous screen"
                        },
                        color = RvInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (compact) 2 else 3
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Screen ${currentScreenIndex + 1} of ${sequenceData.size}",
                            color = RvInkSoft,
                            fontSize = 14.sp,
                            maxLines = 1
                        )

                        if (!compact && currentScreenIndex < sequenceData.size) {
                            val memoryLoad = sequenceData[currentScreenIndex].numbers.size
                            Text(
                                text = "Memory load: $memoryLoad items",
                                color = darkGreen,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

            // Forest scene: fills the remaining height; animal size is derived from the scene
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF87CEEB), // Sky blue
                                Color(0xFF90EE90)  // Light green
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Grass background
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawGrass(this)
                }

                // Animals
                if (currentScreenIndex < sequenceData.size) {
                    val currentAnimals = sequenceData[currentScreenIndex].numbers
                    val isFirstScreen = sequenceData[currentScreenIndex].isFirstScreen

                    // Best grid (cols x rows) so every head is >= 48dp when possible and never overflows
                    val n = currentAnimals.size.coerceAtLeast(1)
                    val pad = 24.dp // room for the bounce (20dp) and the result badge
                    val availW = (maxWidth - pad * 2).coerceAtLeast(48.dp)
                    val availH = (maxHeight - pad * 2).coerceAtLeast(48.dp)
                    var bestCols = 1
                    var bestSize = 0.dp
                    for (c in 1..n) {
                        val r = (n + c - 1) / c
                        val size = minOf(96.dp, (availW - 8.dp * (c - 1)) / c, (availH - 8.dp * (r - 1)) / r)
                        if (size > bestSize) { bestSize = size; bestCols = c }
                    }

                    CompositionLocalProvider(LocalMpAnimalSize provides bestSize.coerceAtLeast(40.dp)) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            currentAnimals.chunked(bestCols).forEach { rowAnimals ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowAnimals.forEach { animalId ->
                                        val animal = animals[animalId]
                                        if (animal != null) {
                                            AnimatedAnimalHead(
                                                animal = animal,
                                                isAnimating = animatingAnimals.contains(animalId),
                                                isClickable = !isFirstScreen,
                                                isSelected = selectedAnimal == animalId,
                                                showFeedback = showFeedback && selectedAnimal == animalId,
                                                isCorrect = isCorrectAnswer,
                                                onClick = { handleAnimalClick(animalId) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

            // Stats footer (one line)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Correct: $correctAnswers/$totalQuestions",
                    color = RvInkSoft,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                if (!compact && reactionTimes.isNotEmpty()) {
                    val avgReaction = reactionTimes.average() / 1000.0
                    Text(
                        text = "⚡ Avg: ${String.format("%.1f", avgReaction)}s",
                        color = if (avgReaction <= 2.5) darkGreen else darkOrange,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                if (!compact && bestStreak > 1) {
                    Text(
                        text = "🔥 Best streak: $bestStreak",
                        color = darkOrange,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

/** Head size chosen by the forest scene so animals always fit (see MemoryPreviousPairPuzzleScreen). */
internal val LocalMpAnimalSize = compositionLocalOf { 80.dp }

@Composable
fun AnimatedAnimalHead(
    animal: ForestAnimal,
    isAnimating: Boolean,
    isClickable: Boolean,
    isSelected: Boolean,
    showFeedback: Boolean,
    isCorrect: Boolean,
    onClick: () -> Unit
) {
    // Bounce animation
    val bounceOffset by animateFloatAsState(
        targetValue = if (isAnimating) -20f else 0f,
        animationSpec = if (isAnimating) {
            infiniteRepeatable(
                animation = tween(800, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "bounce"
    )

    // Scale animation for selection
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "scale"
    )

    val headSize = LocalMpAnimalSize.current
    val emojiSp = with(androidx.compose.ui.platform.LocalDensity.current) { (headSize * 0.5f).toSp() } // glyph follows the head, not the font scale
    Box(
        modifier = Modifier
            .size(headSize)
            .offset(y = bounceOffset.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                when {
                    showFeedback && isCorrect -> Color(0xFF15803D)
                    showFeedback && !isCorrect -> Color(0xFFB3261E)
                    isSelected -> animal.color.copy(alpha = 0.3f)
                    else -> animal.color.copy(alpha = 0.1f)
                }
            )
            .clickable(enabled = isClickable) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = animal.emoji,
            fontSize = emojiSp
        )

        // Feedback overlay
        if (showFeedback && isSelected) {
            Text(
                text = if (isCorrect) "✓" else "✗",
                color = RvOnTone,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(x = headSize / 4, y = -(headSize / 4))
                    .background(
                        color = if (isCorrect) Color(0xFF15803D) else Color(0xFFB3261E),
                        shape = CircleShape
                    )
                    .padding(4.dp)
            )
        }
    }
}

fun drawGrass(drawScope: DrawScope) {
    val grassColor = Color(0xFF228B22)
    val grassHeight = 60f
    val grassWidth = drawScope.size.width

    // Draw multiple grass blades
    for (x in 0 until grassWidth.toInt() step 20) {
        val path = Path().apply {
            moveTo(x.toFloat(), drawScope.size.height)
            quadraticBezierTo(
                x + 10f, drawScope.size.height - grassHeight,
                x + 15f, drawScope.size.height - grassHeight + 10f
            )
            lineTo(x + 5f, drawScope.size.height)
            close()
        }
        drawScope.drawPath(path, grassColor)
    }
}
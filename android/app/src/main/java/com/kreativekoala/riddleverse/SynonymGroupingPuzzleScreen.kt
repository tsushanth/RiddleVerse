package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.json.JSONArray
import kotlin.math.*

data class SynonymSet(
    val id: Int,
    val words: List<String>,
    val displayWord: String, // The word shown at the bottom
    val category: String = "",
    val color: Color
)

data class SynonymWord(
    val word: String,
    val setId: Int,
    val isDisplayed: Boolean = false
)

@Composable
fun SynonymGroupingPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "2:30",
    hearts: Int = 3,
    level: String = "1/5",
    synonymSets: List<List<String>>, // Accept parsed synonym sets directly
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Initialize unified feedback system
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Convert the synonym sets to our data structure
    val synonymSetData = remember(synonymSets) {
        val colors = listOf(
            Color(0xFFE91E63), // Pink
            Color(0xFF2196F3), // Blue
            Color(0xFF4CAF50)  // Green
        )

        synonymSets.mapIndexed { index, words ->
            SynonymSet(
                id = index,
                words = words,
                displayWord = words.first(), // Use first word as display word
                color = colors[index % colors.size]
            )
        }
    }

    // Create the word queue (all words except display words)
    val wordQueue = remember(synonymSetData) {
        synonymSetData.flatMap { set ->
            set.words.drop(1).map { word ->
                SynonymWord(word, set.id)
            }
        }.shuffled().toMutableList()
    }

    // Score tracking state
    var score by remember { mutableIntStateOf(0) }
    var currentHearts by remember { mutableIntStateOf(hearts) }
    var consecutiveCorrect by remember { mutableIntStateOf(0) }
    var totalAnswered by remember { mutableIntStateOf(0) }

    // Game state
    var currentWordIndex by remember { mutableIntStateOf(0) }
    var selectedSetId by remember { mutableIntStateOf(-1) }
    var gameComplete by remember { mutableStateOf(false) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Animation states
    var showCurrentWord by remember { mutableStateOf(true) }
    var lastAnswerCorrect by remember { mutableStateOf(false) }

    // Timer state - convert timer string to seconds and implement countdown
    val totalTimeSeconds = remember(timer) {
        when (timer) {
            "3:00" -> 180
            "2:30" -> 150
            "2:00" -> 120
            "1:30" -> 90
            else -> 150
        }
    }

    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, gameComplete) {
        if (timeRemaining > 0 && !gameComplete) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining == 0 && !gameComplete) {
            gameComplete = true
            Log.d("SynonymGrouping", "💔 Time's up! Final score: $score")
            fetchNextPuzzle(score) // Pass final accumulated score
        }
    }

    // Calculate score for individual answers
    fun calculateAnswerScore(isCorrect: Boolean, consecutiveStreak: Int): Int {
        if (!isCorrect) return 0

        val basePoints = when (difficulty.lowercase()) {
            "easy" -> 15
            "medium" -> 25
            "hard" -> 35
            "expert" -> 45
            else -> 25
        }

        // Streak multiplier for consecutive correct answers
        val streakMultiplier = when {
            consecutiveStreak >= 5 -> 2.0f
            consecutiveStreak >= 3 -> 1.5f
            consecutiveStreak >= 2 -> 1.2f
            else -> 1.0f
        }

        // Time bonus (more time remaining = bonus points)
        val timeBonus = when {
            timeRemaining > totalTimeSeconds * 0.8 -> basePoints * 0.3f // 30% bonus
            timeRemaining > totalTimeSeconds * 0.6 -> basePoints * 0.2f // 20% bonus
            timeRemaining > totalTimeSeconds * 0.4 -> basePoints * 0.1f // 10% bonus
            else -> 0f
        }

        val totalPoints = ((basePoints * streakMultiplier) + timeBonus).toInt()

        Log.d("SynonymGrouping", "🏆 Answer score calculation:")
        Log.d("SynonymGrouping", "  Base points: $basePoints")
        Log.d("SynonymGrouping", "  Streak multiplier: ${streakMultiplier}x (streak: $consecutiveStreak)")
        Log.d("SynonymGrouping", "  Time bonus: ${timeBonus.toInt()}")
        Log.d("SynonymGrouping", "  Total points: $totalPoints")

        return maxOf(totalPoints, basePoints) // Minimum base points
    }

    // Calculate final completion bonus
    fun calculateCompletionBonus(): Int {
        val accuracy = if (totalAnswered > 0) consecutiveCorrect.toFloat() / totalAnswered else 0f
        val timeEfficiency = timeRemaining.toFloat() / totalTimeSeconds

        val baseBonus = when (difficulty.lowercase()) {
            "easy" -> 50
            "medium" -> 75
            "hard" -> 100
            "expert" -> 125
            else -> 75
        }

        // Accuracy bonus (90%+ accuracy gets full bonus)
        val accuracyBonus = when {
            accuracy >= 0.9f -> baseBonus * 0.5f
            accuracy >= 0.8f -> baseBonus * 0.3f
            accuracy >= 0.7f -> baseBonus * 0.1f
            else -> 0f
        }

        // Time efficiency bonus
        val timeBonus = (baseBonus * timeEfficiency * 0.3f).toInt()

        val totalBonus = (accuracyBonus + timeBonus).toInt()

        Log.d("SynonymGrouping", "🎉 Completion bonus calculation:")
        Log.d("SynonymGrouping", "  Accuracy: ${(accuracy * 100).toInt()}%")
        Log.d("SynonymGrouping", "  Time efficiency: ${(timeEfficiency * 100).toInt()}%")
        Log.d("SynonymGrouping", "  Base bonus: $baseBonus")
        Log.d("SynonymGrouping", "  Accuracy bonus: ${accuracyBonus.toInt()}")
        Log.d("SynonymGrouping", "  Time bonus: $timeBonus")
        Log.d("SynonymGrouping", "  Total bonus: $totalBonus")

        return totalBonus
    }

    // Game completion check - show feedback when all words are done
    LaunchedEffect(currentWordIndex, wordQueue.size) {
        if (currentWordIndex >= wordQueue.size && !gameComplete) {
            Log.d("SynonymGrouping", "🎯 All words completed! currentWordIndex=$currentWordIndex, queueSize=${wordQueue.size}")
            gameComplete = true

            // Calculate and add completion bonus
            val completionBonus = calculateCompletionBonus()
            score += completionBonus

            Log.d("SynonymGrouping", "🎉 Game completed with final score: $score (includes $completionBonus bonus)")

            // Small delay to let UI settle
            delay(300)

            // Use unified feedback for completion
            feedbackManager.showFeedback(
                puzzleType = "synonyms",
                isCorrect = true,
                userAnswer = "All words grouped correctly",
                correctAnswer = "Perfect! All synonyms matched!",
                timeSpent = System.currentTimeMillis() - gameStartTime,
                difficulty = difficulty,
                timeRemaining = timeRemaining,
                totalTime = totalTimeSeconds,
                onComplete = {
                    Log.d("SynonymGrouping", "🔄 Feedback completed, calling fetchNextPuzzle with score: $score")
                    onSubmitAnswer(true)
                    fetchNextPuzzle(score) // Pass final accumulated score
                }
            )
        }
    }

    // Word display effect
    LaunchedEffect(currentWordIndex, wordQueue.size) {
        // Ensure word is shown after any state changes
        delay(300) // Wait for animation to complete
        if (currentWordIndex < wordQueue.size && !showCurrentWord) {
            showCurrentWord = true
        }
    }

    fun handleSetSelection(setId: Int) {
        if (gameComplete || currentWordIndex >= wordQueue.size) return

        selectedSetId = setId
        val currentWord = wordQueue[currentWordIndex]
        val isCorrect = currentWord.setId == setId
        lastAnswerCorrect = isCorrect
        totalAnswered++

        // Calculate score for this answer
        val answerScore = calculateAnswerScore(isCorrect, consecutiveCorrect)

        if (isCorrect) {
            consecutiveCorrect++
            score += answerScore
            Log.d("SynonymGrouping", "✅ Correct! Score: $score (+$answerScore), Streak: $consecutiveCorrect")
        } else {
            consecutiveCorrect = 0 // Reset streak
            currentHearts = maxOf(0, currentHearts - 1)
            Log.d("SynonymGrouping", "❌ Incorrect. Hearts: $currentHearts, Score: $score")

            // Check if game over due to no hearts
            if (currentHearts == 0) {
                gameComplete = true
                Log.d("SynonymGrouping", "💔 No hearts remaining. Game over with score: $score")
                fetchNextPuzzle(score)
                return
            }
        }

        // Get the correct set info for feedback
        val correctSet = synonymSetData.find { it.id == currentWord.setId }
        val selectedSet = synonymSetData.find { it.id == setId }

        // Use unified feedback system
        feedbackManager.showFeedback(
            puzzleType = "synonyms",
            isCorrect = isCorrect,
            userAnswer = "Selected: ${selectedSet?.displayWord ?: "Unknown"}",
            correctAnswer = "${currentWord.word} belongs to: ${correctSet?.displayWord ?: "Unknown"}",
            timeSpent = System.currentTimeMillis() - gameStartTime,
            difficulty = difficulty,
            timeRemaining = timeRemaining,
            totalTime = totalTimeSeconds,
            onComplete = {
                // Check if game should end due to no hearts
                if (currentHearts <= 0) {
                    gameComplete = true
                    onSubmitAnswer(false)
                    fetchNextPuzzle(score)
                    return@showFeedback
                }

                // Move to next word
                currentWordIndex++
                selectedSetId = -1 // Reset selection
                showCurrentWord = true

                Log.d("SynonymGrouping", "🔄 Moving to next word: index=$currentWordIndex, total=${wordQueue.size}")
                // The completion check will be handled by the LaunchedEffect above
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E))
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // Header with enhanced info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }

                Text(
                    text = difficulty.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = "Timer",
                        tint = if (timeRemaining <= 30) Color.Red else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "${timeRemaining / 60}:${(timeRemaining % 60).toString().padStart(2, '0')}",
                        color = if (timeRemaining <= 30) Color.Red else Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Level, Hearts, and Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = level,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${currentWordIndex}/${wordQueue.size} words",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }

                Row {
                    repeat(hearts) { index ->
                        Text(
                            text = if (index < currentHearts) "❤️" else "🤍",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score and Streak Display
            if (score > 0 || consecutiveCorrect > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2A2A3E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (score > 0) {
                            Text(
                                text = "${stringResource(R.string.score_label)}: $score",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (consecutiveCorrect > 1) {
                            Text(
                                text = "🔥 ${consecutiveCorrect} streak!",
                                color = Color(0xFFFF9800),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2A2A3E)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Group the synonyms! Tap the set that matches the word above.",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Add more space to push content down
            Spacer(modifier = Modifier.height(64.dp))

            // Main Game Area - moved down and simplified
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Central word display - simplified
                    if (currentWordIndex < wordQueue.size && !gameComplete) {
                        Card(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Text(
                                text = wordQueue.getOrNull(currentWordIndex)?.word ?: "Loading...",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(32.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Show a placeholder when game is complete (feedback will show the real completion)
                        Card(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Text(
                                text = "🎉",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(32.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Add space between word and buttons
                    Spacer(modifier = Modifier.height(80.dp))

                    // Synonym set buttons - arranged horizontally without arrows
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        synonymSetData.forEach { set ->
                            SynonymSetButton(
                                synonymSet = set,
                                isSelected = selectedSetId == set.id,
                                isHighlighted = false, // Remove highlighting since no arrows
                                onClick = { handleSetSelection(set.id) }
                            )
                        }
                    }
                }
            }

            // Enhanced Score Display at Bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.score_label)}: $score",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                if (totalAnswered > 0) {
                    Text(
                        text = "${stringResource(R.string.accuracy)}: ${((consecutiveCorrect.toFloat() / totalAnswered) * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Use unified feedback system
        EnhancedUniversalFeedback(feedbackManager)
    }
}

// Rest of the composables remain the same...
@Composable
fun SynonymSetButton(
    synonymSet: SynonymSet,
    isSelected: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "buttonScale"
    )

    Card(
        modifier = Modifier
            .size(100.dp) // Slightly larger buttons
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = synonymSet.color.copy(alpha = 0.2f)
        ),
        shape = CircleShape,
        border = BorderStroke(
            width = 2.dp,
            color = synonymSet.color
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = synonymSet.displayWord,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

// Extension function to draw arrow heads
fun DrawScope.drawArrowHead(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float
) {
    val arrowLength = 20f
    val arrowAngle = PI / 6

    val angle = atan2(end.y - start.y, end.x - start.x)

    val arrowPoint1 = Offset(
        end.x - arrowLength * cos(angle - arrowAngle).toFloat(),
        end.y - arrowLength * sin(angle - arrowAngle).toFloat()
    )

    val arrowPoint2 = Offset(
        end.x - arrowLength * cos(angle + arrowAngle).toFloat(),
        end.y - arrowLength * sin(angle + arrowAngle).toFloat()
    )

    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(arrowPoint1.x, arrowPoint1.y)
        moveTo(end.x, end.y)
        lineTo(arrowPoint2.x, arrowPoint2.y)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth)
    )
}
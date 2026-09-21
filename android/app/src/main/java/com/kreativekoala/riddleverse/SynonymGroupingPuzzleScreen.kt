package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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
            RvSky, // Blue
            RvSuccess  // Green
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(RvCanvas)) {
        val compact = groupEIsCompact(maxWidth, maxHeight)
        val wide = groupEIsWide(maxWidth, maxHeight)
        val timerText = "${timeRemaining / 60}:${(timeRemaining % 60).toString().padStart(2, '0')}"
        val wordText = wordQueue.getOrNull(currentWordIndex)?.word

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HUD: back, timer, level + score, hearts (single row).
            GroupECompactHud(
                timer = timerText,
                onBack = onBack,
                subtitle = "$level \u2022 ${stringResource(R.string.score_label)} $score",
                lives = currentHearts,
                urgent = timeRemaining <= 30
            )

            // Progress + streak (one slim row).
            GroupEFontCap {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${currentWordIndex}/${wordQueue.size} words",
                        color = RvInkSoft,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    LinearProgressIndicator(
                        progress = if (wordQueue.isEmpty()) 0f else currentWordIndex.toFloat() / wordQueue.size,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = RvSuccess,
                        trackColor = RvOutline
                    )
                    if (consecutiveCorrect > 1) {
                        Text(
                            text = "\uD83D\uDD25 $consecutiveCorrect",
                            color = RvInk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Instruction (dropped on short screens where the play area needs the room).
            if (!compact) {
                GroupEFontCap {
                    Text(
                        text = "Group the synonyms! Tap the set that matches the word above.",
                        color = RvInk,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Play area: the word to place + the sets to place it into. Fills all remaining space.
            val wordCard: @Composable (Modifier) -> Unit = { m ->
                Box(modifier = m, contentAlignment = Alignment.Center) {
                    if (currentWordIndex < wordQueue.size && !gameComplete) {
                        Card(
                            modifier = Modifier
                                .widthIn(min = 160.dp)
                                .testTag("synonym_word"),
                            colors = CardDefaults.cardColors(containerColor = RvSurface),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(2.dp, RvOutline)
                        ) {
                            Text(
                                text = wordText ?: "Loading...",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        // Placeholder when the game is complete (feedback shows the real completion)
                        Card(
                            modifier = Modifier.testTag("synonym_word"),
                            colors = CardDefaults.cardColors(containerColor = RvSurface),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "\uD83C\uDF89",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvSuccess,
                                modifier = Modifier.padding(24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            val choices: @Composable (Modifier) -> Unit = { m ->
                Column(modifier = m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    synonymSetData.forEach { set ->
                        GroupESynonymChoice(
                            synonymSet = set,
                            isSelected = selectedSetId == set.id,
                            onClick = { handleSetSelection(set.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (wide) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    wordCard(Modifier.weight(1f).fillMaxHeight())
                    choices(Modifier.weight(1f))
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    wordCard(Modifier.weight(1f).fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    // Answers pinned to the bottom, in thumb reach.
                    choices(Modifier.fillMaxWidth())
                }
            }
        }

        // Use unified feedback system
        EnhancedUniversalFeedback(feedbackManager)
    }
}

/**
 * Answer button for one synonym set: full-width, >= 64dp tall, coloured outline + dot (set colour is game
 * meaning), thicker outline + check when selected. Text follows the system font scale (capped) and wraps.
 */
@Composable
private fun GroupESynonymChoice(
    synonymSet: SynonymSet,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    GroupEFontCap(max = 1.5f) {
        Row(
            modifier = modifier
                .testTag("synonym_set_${synonymSet.id}")
                .heightIn(min = 64.dp)
                .clip(shape)
                .background(synonymSet.color.copy(alpha = if (isSelected) 0.3f else 0.15f), shape)
                .border(if (isSelected) 4.dp else 2.dp, synonymSet.color, shape)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(synonymSet.color, CircleShape)
            )
            Text(
                text = synonymSet.displayWord,
                color = RvInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Text(text = "\u2713", color = RvInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
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
                color = RvInk,
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
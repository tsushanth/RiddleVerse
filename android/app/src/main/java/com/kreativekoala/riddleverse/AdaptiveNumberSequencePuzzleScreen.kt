// AdaptiveNumberSequencePuzzleScreen.kt
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveNumberSequencePuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveNumberSequence"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("numberSequence"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // ✅ NEW: Competitive ranking state
    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    // Load competitive insight
    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "numberSequence",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate adaptive configuration
    val adaptiveConfig = remember(currentDifficultyLevel) {
        generateAdaptiveNumberSequenceConfig(currentDifficultyLevel)
    }

    var timeLeft by remember { mutableStateOf(currentDifficultyLevel.timeLimit) }
    var isPaused by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var gameCompleted by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var feedbackColor by remember { mutableStateOf(RvError) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // ✅ NEW: Track session for competitive ranking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctRounds by remember { mutableStateOf(0) }
    var totalRounds by remember { mutableStateOf(0) }

    // Game state
    var currentRoundNumbers by remember { mutableStateOf(emptyList<Int>()) }
    var sortedTargetNumbers by remember { mutableStateOf(emptyList<Int>()) }
    var nextExpectedIndex by remember { mutableStateOf(0) }
    var numbersVisible by remember { mutableStateOf(mutableMapOf<Int, Boolean>()) }
    var numberPositions by remember { mutableStateOf(emptyList<NumberPosition>()) }
    var showWrongFeedback by remember { mutableStateOf(false) }
    var wrongFeedbackPosition by remember { mutableStateOf(Offset.Zero) }
    var currentRound by remember { mutableStateOf(1) }
    var totalScore by remember { mutableStateOf(0) }
    var roundStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()
    val haptics = LocalHapticFeedback.current
    var showHint by remember { mutableStateOf(false) }


    // ✅ UPDATED: Performance recording function
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = adaptiveConfig.numberCount,
            totalScore = totalScore,
            puzzleType = "numberSequence"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = SHOW_ADAPTATION_NOTICES
            }
        }
    }

    // ✅ UPDATED: Use unified score calculation
    fun calculateScore(isCorrect: Boolean, timeSpent: Long): Int {
        return calculateUnifiedAdaptiveScore(
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            difficulty = currentDifficultyLevel,
            challengeComplexity = adaptiveConfig.numberCount,
            currentStreak = currentStreak,
            challengesCompleted = currentRound,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "numberSequence"
        )
    }

    fun generateNewRound() {
        val randomNumbers = (1..adaptiveConfig.numberCount).map {
            Random.nextInt(adaptiveConfig.numberRange.first, adaptiveConfig.numberRange.last + 1)
        }.distinct().take(adaptiveConfig.numberCount)

        val finalNumbers = if (randomNumbers.size < adaptiveConfig.numberCount) {
            val additional = generateSequence {
                Random.nextInt(adaptiveConfig.numberRange.first, adaptiveConfig.numberRange.last + 1)
            }.filter { it !in randomNumbers }
                .take(adaptiveConfig.numberCount - randomNumbers.size)
                .toList()
            randomNumbers + additional
        } else {
            randomNumbers
        }

        currentRoundNumbers = finalNumbers
        sortedTargetNumbers = finalNumbers.sorted()
        nextExpectedIndex = 0
        numbersVisible = finalNumbers.associateWith { true }.toMutableMap()
        roundStartTime = System.currentTimeMillis()

        Log.d(TAG, "New adaptive round $currentRound:")
        Log.d(TAG, "Numbers: $finalNumbers")
        Log.d(TAG, "Target order: $sortedTargetNumbers")
        Log.d(TAG, "Difficulty: ${currentDifficultyLevel.name}")
    }

    // Generate positions for current round numbers
    fun generatePositions() {
        val colors = listOf(
            RvSky, RvSuccess, Color(0xFFE91E63), RvSun,
            RvGrape, RvSky, RvFlame, RvInk,
            RvInkSoft, Color(0xFF8BC34A), RvFlame, Color(0xFF6C5CE7)
        )

        val positions = mutableListOf<NumberPosition>()
        val usedPositions = mutableSetOf<Pair<Float, Float>>()

        currentRoundNumbers.forEachIndexed { index, number ->
            var x: Float
            var y: Float
            var attempts = 0

            do {
                x = Random.nextFloat() * 0.7f + 0.15f
                y = Random.nextFloat() * 0.6f + 0.2f
                attempts++
            } while (usedPositions.any {
                    abs(it.first - x) < 0.15f && abs(it.second - y) < 0.15f
                } && attempts < 50)

            usedPositions.add(Pair(x, y))

            positions.add(
                NumberPosition(
                    number = number,
                    x = x,
                    y = y,
                    color = colors[index % colors.size],
                    isVisible = true
                )
            )
        }

        numberPositions = positions
    }

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            generateNewRound()
            generatePositions()
            timeLeft = currentDifficultyLevel.timeLimit
            currentHearts = currentDifficultyLevel.livesAllowed
            roundStartTime = System.currentTimeMillis()
        }
    }

    // Initialize first round
    LaunchedEffect(Unit) {
        generateNewRound()
        generatePositions()
    }

    // Timer countdown
    LaunchedEffect(gameStarted, isPaused, gameCompleted) {
        if (gameStarted && !isPaused && !gameCompleted) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0) {
                // Time's up - record poor performance
                recordPerformance(false, currentDifficultyLevel.timeLimit * 1000L)
                gameCompleted = true
                onGameComplete(false, 10)
            }
        }
    }

    // Wrong feedback animation
    LaunchedEffect(showWrongFeedback) {
        if (showWrongFeedback) {
            delay(800)
            showWrongFeedback = false
        }
    }

    // Check if current round is completed
    LaunchedEffect(numbersVisible.values.toList(), currentRoundNumbers) {
        if (currentRoundNumbers.isNotEmpty() &&
            numbersVisible.isNotEmpty() &&
            numbersVisible.values.none { it }) {

            // Round completed successfully
            totalRounds++
            correctRounds++
            val timeSpent = System.currentTimeMillis() - roundStartTime
            val newStreak = currentStreak + 1

            val roundScore = calculateScore(true, timeSpent)
            totalScore += roundScore
            currentStreak = newStreak
            gamesPlayedThisSession++

            feedbackMessage = "🎉 Round $currentRound Complete! +$roundScore points"
            feedbackColor = RvSuccess
            showFeedback = true

            Log.d(TAG, "✅ Round completed! Score: +$roundScore, Total: $totalScore")

            // Record successful performance
            recordPerformance(true, timeSpent)

            delay(1500)
            showFeedback = false

            // Start next round
            currentRound++
            generateNewRound()
            generatePositions()

            // Add bonus time for next round
            val bonusTime = adaptiveConfig.timePerNumber * 2
            timeLeft += bonusTime
        }
    }

    // Handle game over when hearts reach 0
    LaunchedEffect(currentHearts) {
        if (currentHearts == 0 && gameStarted && !gameCompleted) {
            gameCompleted = true
            feedbackMessage = "💔 Game Over! Final Score: $totalScore"
            feedbackColor = RvError
            showFeedback = true
            delay(2000)
            onGameComplete(false, 10)
        }
    }

    // ✅ NEW: Session completion handling
    if (gameCompleted) {
        UnifiedSessionCompletionHandler(
            puzzleType = "numberSequence",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctRounds,
                totalAnswers = totalRounds,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalRounds > 0) correctRounds.toFloat() / totalRounds else 0f,
                totalScore = totalScore,
                averageTimePerPuzzle = if (totalRounds > 0)
                    ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt() / totalRounds
                else 0,
                currentStreak = currentStreak,
                individualTimes = emptyList() // Can track individual round times if needed
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            onGameComplete(correctRounds > 0, totalScore)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        val compact = groupEIsCompact(maxWidth, maxHeight)
        val timerText = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HUD: compact single row on short/landscape screens, shared header otherwise.
            if (compact) {
                GroupECompactHud(
                    timer = timerText,
                    onBack = {
                        gameCompleted = true
                        onBack()
                    },
                    subtitle = "${currentDifficultyLevel.name} \u2022 ${stringResource(R.string.score_label)} $totalScore",
                    lives = currentHearts,
                    urgent = timeLeft <= 30,
                    onPause = { isPaused = !isPaused }
                )
            } else {
                // ✅ REPLACE: Use unified header instead of AdaptiveNumberSequenceTopBar
                Box(modifier = Modifier.testTag("hud_timer")) { AdaptiveUnifiedHeader(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = timerText,
                    lives = currentHearts,
                    currentDifficulty = currentDifficultyLevel,
                    score = totalScore,
                    puzzleType = "numbersequence",
                    challengeNumber = currentRound,
                    totalChallenges = 10, // Or whatever makes sense for sequence rounds
                    competitiveInsight = competitiveInsight,
                    onBack = {
                        gameCompleted = true
                        onBack()
                    },
                    onPause = { isPaused = !isPaused },
                    onHint = {
                        showHint = !showHint
                        if (showHint) {
                            Log.d(TAG, "Hint: Next number to tap is ${sortedTargetNumbers.getOrNull(nextExpectedIndex)}")
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                ) }
            }

            // ✅ REPLACE: Use unified adaptation notification
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "numberSequence",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!gameStarted) {
                // Instructions: content may scroll as a last-resort safety net; Start is pinned below it.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        colors = CardDefaults.cardColors(containerColor = RvSurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "\uD83D\uDD22 Adaptive Number Sequence",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.tap_ascending_order),
                                fontSize = 16.sp,
                                color = RvInk,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${currentDifficultyLevel.name} \u2022 Range: ${adaptiveConfig.numberRange.first}-${adaptiveConfig.numberRange.last} \u2022 ${adaptiveConfig.numberCount} numbers per round",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                color = RvInkSoft
                            )
                            if (!compact) {
                                Text(
                                    text = currentDifficultyLevel.description,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    color = RvInkSoft,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                GroupEPrimaryButton(
                    text = stringResource(R.string.start).uppercase(),
                    onClick = { gameStarted = true },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (!gameCompleted) {
                // Play field: fills all remaining space, numbers are placed inside it (never clipped).
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("seq_area")
                        .clip(RoundedCornerShape(16.dp))
                        .background(RvSurface)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridSize = 40.dp.toPx()
                        val gridColor = RvOutline

                        for (x in 0 until (size.width / gridSize).toInt()) {
                            drawLine(
                                color = gridColor,
                                start = Offset(x * gridSize, 0f),
                                end = Offset(x * gridSize, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        for (y in 0 until (size.height / gridSize).toInt()) {
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y * gridSize),
                                end = Offset(size.width, y * gridSize),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (isPaused) 0.35f else 1f)
                    ) {
                        val fieldW = maxWidth
                        val fieldH = maxHeight
                        val pad = 8.dp

                        // Adaptive number size: difficulty preference, shrunk to what the field can hold.
                        val preferred = when (currentDifficultyLevel.index) {
                            0 -> 70.dp
                            1 -> 65.dp
                            2 -> 60.dp
                            3 -> 56.dp
                            4 -> 52.dp
                            else -> 60.dp
                        }
                        val numberSize = minOf(preferred, minOf(fieldW, fieldH) * 0.2f).coerceAtLeast(48.dp)

                        numberPositions.forEach { position ->
                            val isVisible = numbersVisible[position.number] ?: false

                            if (isVisible) {
                                // Map the normalised slot (0.15..0.85, 0.2..0.8) onto the field minus the circle size.
                                val fx = ((position.x - 0.15f) / 0.7f).coerceIn(0f, 1f)
                                val fy = ((position.y - 0.2f) / 0.6f).coerceIn(0f, 1f)
                                val xPos = pad + (fieldW - numberSize - pad * 2) * fx
                                val yPos = pad + (fieldH - numberSize - pad * 2) * fy

                                Box(
                                    modifier = Modifier
                                        .offset(x = xPos, y = yPos)
                                        .size(numberSize)
                                        .testTag("seq_number")
                                        .clip(CircleShape)
                                        .background(position.color)
                                        .clickable {
                                            if (!isPaused) {
                                                val expectedNumber = sortedTargetNumbers.getOrNull(nextExpectedIndex)

                                                if (position.number == expectedNumber) {
                                                    // Correct number tapped
                                                    numbersVisible = numbersVisible.toMutableMap().apply {
                                                        this[position.number] = false
                                                    }
                                                    nextExpectedIndex++

                                                    // Award points for correct tap
                                                    val tapScore = calculateScore(
                                                        true,
                                                        System.currentTimeMillis() - roundStartTime
                                                    )
                                                    totalScore += tapScore
                                                } else {
                                                    // Wrong number tapped
                                                    totalRounds++
                                                    wrongFeedbackPosition = Offset(xPos.value, yPos.value)
                                                    showWrongFeedback = true
                                                    currentHearts = maxOf(0, currentHearts - 1)
                                                    currentStreak = 0

                                                    // Record mistake
                                                    recordPerformance(
                                                        false,
                                                        System.currentTimeMillis() - roundStartTime
                                                    )
                                                }
                                            }
                                        }
                                        .animateContentSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Fixed-size target: number text does not follow the system font scale.
                                    GroupEFontCap(max = 1f) {
                                        Text(
                                            text = position.number.toString(),
                                            color = groupEOn(position.color),
                                            fontSize = (numberSize.value * 0.42f).coerceAtLeast(20f).sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Wrong feedback
                        if (showWrongFeedback) {
                            val scale by animateFloatAsState(
                                targetValue = if (showWrongFeedback) 1.5f else 1f,
                                animationSpec = tween(300)
                            )

                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = wrongFeedbackPosition.x.dp,
                                        y = wrongFeedbackPosition.y.dp
                                    )
                                    .scale(scale)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.wrong),
                                    tint = RvError,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    // Feedback banner overlays the field instead of pushing it around.
                    if (showFeedback) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(8.dp),
                            colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                            border = androidx.compose.foundation.BorderStroke(2.dp, feedbackColor)
                        ) {
                            Text(
                                text = feedbackMessage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                color = RvInk,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (showFeedback) {
                // Game over banner (field is gone once the game is completed).
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                    border = androidx.compose.foundation.BorderStroke(2.dp, feedbackColor)
                ) {
                    Text(
                        text = feedbackMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = RvInk,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Group E primary action: chunky violet button, >= 56dp tall, white text at >= 4.5:1. */
@Composable
internal fun GroupEPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: RvTone = RvToneViolet
) {
    val fill = if (enabled) tone else RvTone(RvDisabled, RvDisabled)
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .rvChunky(fill, corner = 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (enabled) RvOnTone else RvInkSoft,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Adaptive configuration data class
data class AdaptiveNumberSequenceConfig(
    val numberCount: Int,
    val numberRange: IntRange,
    val timePerNumber: Int,
    val name: String
)

// Generate adaptive configuration based on difficulty level
fun generateAdaptiveNumberSequenceConfig(difficulty: DifficultyManager.DifficultyLevel): AdaptiveNumberSequenceConfig {
    return when (difficulty.index) {
        0 -> AdaptiveNumberSequenceConfig( // Beginner
            numberCount = 4,
            numberRange = 1..10,
            timePerNumber = 8,
            name = "Beginner"
        )
        1 -> AdaptiveNumberSequenceConfig( // Easy
            numberCount = 5,
            numberRange = 1..20,
            timePerNumber = 6,
            name = "Easy"
        )
        2 -> AdaptiveNumberSequenceConfig( // Medium
            numberCount = 7,
            numberRange = 1..50,
            timePerNumber = 5,
            name = "Medium"
        )
        3 -> AdaptiveNumberSequenceConfig( // Hard
            numberCount = 9,
            numberRange = 1..100,
            timePerNumber = 4,
            name = "Hard"
        )
        4 -> AdaptiveNumberSequenceConfig( // Expert
            numberCount = 12,
            numberRange = 1..200,
            timePerNumber = 3,
            name = "Expert"
        )
        else -> AdaptiveNumberSequenceConfig(
            numberCount = 7,
            numberRange = 1..50,
            timePerNumber = 5,
            name = "Medium"
        )
    }
}
// ---------------------------------------------------------------------------------------------
// Group E shared layout helpers (used by the Group E game screens).
// ---------------------------------------------------------------------------------------------

/** Compact mode: short viewports (small phones, split screen) and landscape phones. */
internal fun groupEIsCompact(maxWidth: Dp, maxHeight: Dp): Boolean = maxHeight < 700.dp || maxWidth > maxHeight

/** Wide mode: side-by-side panes (landscape and tablets). */
internal fun groupEIsWide(maxWidth: Dp, maxHeight: Dp): Boolean = maxWidth > maxHeight || maxWidth >= 600.dp

/** Caps the system font scale for HUD text so it can never crowd out the play area. */
@Composable
internal fun GroupEFontCap(max: Float = 1.3f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, max))) { content() }
}

/**
 * Single-row HUD (back, timer + subtitle, lives, optional pause). Every tappable is 48dp.
 * Replaces the tall shared header on short viewports.
 */
@Composable
internal fun GroupECompactHud(
    timer: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    lives: Int? = null,
    urgent: Boolean = false,
    onPause: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    GroupEFontCap {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = RvInk)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (urgent) RvErrorEdge else RvInk,
                    maxLines = 1,
                    modifier = Modifier.testTag("hud_timer")
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = RvInkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (lives != null) {
                Text(text = "\u2764\uFE0F $lives", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RvInk, maxLines = 1)
            }
            actions()
            if (onPause != null) {
                IconButton(onClick = onPause, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = RvInk)
                }
            }
        }
    }
}

/** Readable text colour (ink or white) for a given fill, so labels keep >= 4.5:1 contrast. */
internal fun groupEOn(fill: Color): Color {
    fun lin(c: Float): Float = if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    val l = 0.2126f * lin(fill.red) + 0.7152f * lin(fill.green) + 0.0722f * lin(fill.blue)
    val ink = RvInk
    val inkL = 0.2126f * lin(ink.red) + 0.7152f * lin(ink.green) + 0.0722f * lin(ink.blue)
    val contrastInk = (l + 0.05f) / (inkL + 0.05f)
    val contrastWhite = 1.05f / (l + 0.05f)
    return if (contrastInk >= contrastWhite) ink else Color.White
}

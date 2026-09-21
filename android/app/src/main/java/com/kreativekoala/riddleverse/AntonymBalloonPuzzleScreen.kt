package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import androidx.compose.ui.res.stringResource
import org.json.JSONObject
import kotlin.random.Random
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class AntonymPair(
    val word1: String,
    val word2: String,
    val id: Int
)

data class BalloonItem(
    val word: String,
    val pairId: Int,
    val id: String,
    val x: Float,
    val y: Float,
    val color: Color,
    var isSelected: Boolean = false,
    var isBurst: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntonymBalloonPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    puzzleData: String = "", // JSON string containing antonym pairs
    onSubmitAnswer: (Boolean) -> Unit = {},
    fetchNextPuzzle: (Int) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var matchStreak by remember { mutableStateOf(0) }
    var wrongAttempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Game state
    var balloons by remember { mutableStateOf<List<BalloonItem>>(emptyList()) }
    var selectedBalloon by remember { mutableStateOf<BalloonItem?>(null) }
    var antonymPairs by remember { mutableStateOf<List<AntonymPair>>(emptyList()) }
    var matchedPairs by remember { mutableStateOf(0) }
    var totalPairs by remember { mutableStateOf(0) }
    var gamePhase by remember { mutableStateOf(1) }
    var isGameComplete by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            val minutes = parts[0].toIntOrNull() ?: 2
            val seconds = parts[1].toIntOrNull() ?: 0
            minutes * 60 + seconds
        } else {
            120 // Default 2 minutes
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isGameComplete, isLoading) {
        if (timeRemaining > 0 && !isGameComplete && !isLoading) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining <= 0 && !isGameComplete) {
            // Time's up - complete with current score
            isGameComplete = true
            showFeedback = true
            Log.d("AntonymBalloon", "⏰ Time's up! Final score: $totalScore")
        }
    }

    // Calculate score for matching pairs
    fun calculateMatchScore(isCorrect: Boolean, timeSpent: Long, streak: Int): Int {
        if (!isCorrect) return 0

        // Base points by difficulty
        val basePoints = when (difficulty.lowercase()) {
            "easy" -> 20
            "medium" -> 30
            "hard" -> 40
            "expert" -> 50
            else -> 30
        }

        // Streak multiplier for consecutive correct matches
        val streakMultiplier = when {
            streak >= 5 -> 2.0f
            streak >= 3 -> 1.5f
            streak >= 2 -> 1.2f
            else -> 1.0f
        }

        // Time efficiency bonus (faster matching = bonus)
        val matchTimeSeconds = timeSpent / 1000f
        val timeBonus = when {
            matchTimeSeconds <= 3f -> (basePoints * 0.5f).toInt() // Very fast
            matchTimeSeconds <= 5f -> (basePoints * 0.3f).toInt() // Fast
            matchTimeSeconds <= 8f -> (basePoints * 0.1f).toInt() // Moderate
            else -> 0
        }

        // Vocabulary complexity bonus
        val complexityBonus = when {
            antonymPairs.any { pair ->
                (pair.word1.length >= 8 || pair.word2.length >= 8)
            } -> (basePoints * 0.2f).toInt()
            else -> 0
        }

        val finalScore = ((basePoints * streakMultiplier).toInt() + timeBonus + complexityBonus)

        Log.d("AntonymBalloon", "🏆 Match score calculation:")
        Log.d("AntonymBalloon", "  Base points: $basePoints")
        Log.d("AntonymBalloon", "  Streak multiplier: ${streakMultiplier}x (streak: $streak)")
        Log.d("AntonymBalloon", "  Time bonus: $timeBonus (${matchTimeSeconds}s)")
        Log.d("AntonymBalloon", "  Complexity bonus: $complexityBonus")
        Log.d("AntonymBalloon", "  Final score: $finalScore")

        return maxOf(finalScore, basePoints) // Minimum base points
    }

    // Calculate completion bonus
    fun calculateCompletionBonus(): Int {
        val accuracy = if (matchedPairs + wrongAttempts > 0) {
            matchedPairs.toFloat() / (matchedPairs + wrongAttempts)
        } else 1f

        val timeEfficiency = timeRemaining.toFloat() / totalTimeSeconds

        val baseBonus = when (difficulty.lowercase()) {
            "easy" -> 50
            "medium" -> 75
            "hard" -> 100
            "expert" -> 125
            else -> 75
        }

        // Accuracy bonus (high accuracy = full bonus)
        val accuracyBonus = when {
            accuracy >= 0.9f -> (baseBonus * 0.8f).toInt()
            accuracy >= 0.8f -> (baseBonus * 0.6f).toInt()
            accuracy >= 0.7f -> (baseBonus * 0.4f).toInt()
            else -> 0
        }

        // Time completion bonus
        val timeBonus = (baseBonus * timeEfficiency * 0.5f).toInt()

        // Perfect game bonus
        val perfectBonus = if (wrongAttempts == 0 && matchedPairs == totalPairs) {
            baseBonus / 2
        } else 0

        val totalBonus = accuracyBonus + timeBonus + perfectBonus

        Log.d("AntonymBalloon", "🎉 Completion bonus calculation:")
        Log.d("AntonymBalloon", "  Accuracy: ${(accuracy * 100).toInt()}%")
        Log.d("AntonymBalloon", "  Time efficiency: ${(timeEfficiency * 100).toInt()}%")
        Log.d("AntonymBalloon", "  Accuracy bonus: $accuracyBonus")
        Log.d("AntonymBalloon", "  Time bonus: $timeBonus")
        Log.d("AntonymBalloon", "  Perfect bonus: $perfectBonus")
        Log.d("AntonymBalloon", "  Total bonus: $totalBonus")

        return totalBonus
    }

    // Parse antonym pairs from puzzle data (existing logic remains the same)
    LaunchedEffect(puzzleData) {
        if (puzzleData.isNotEmpty()) {
            try {
                val pairs = if (puzzleData.startsWith("{")) {
                    val json = JSONObject(puzzleData)
                    val pairsArray = json.getJSONArray("pairs")
                    val pairsList = mutableListOf<AntonymPair>()

                    for (i in 0 until pairsArray.length()) {
                        val pairObj = pairsArray.getJSONObject(i)
                        pairsList.add(
                            AntonymPair(
                                word1 = pairObj.getString("word1"),
                                word2 = pairObj.getString("word2"),
                                id = i
                            )
                        )
                    }
                    pairsList
                } else {
                    val pairsArray = JSONArray(puzzleData)
                    val pairsList = mutableListOf<AntonymPair>()

                    for (i in 0 until pairsArray.length()) {
                        val pairArray = pairsArray.getJSONArray(i)
                        if (pairArray.length() >= 2) {
                            pairsList.add(
                                AntonymPair(
                                    word1 = pairArray.getString(0),
                                    word2 = pairArray.getString(1),
                                    id = i
                                )
                            )
                        }
                    }
                    pairsList
                }

                antonymPairs = pairs
                totalPairs = pairs.size
                gameStartTime = System.currentTimeMillis() // Reset start time for actual gameplay
                setupBalloons(pairs, 1, screenWidth, screenHeight) { balloonList ->
                    balloons = balloonList
                    isLoading = false
                }

                Log.d("AntonymBalloon", "✅ Loaded ${pairs.size} antonym pairs from puzzle data")
            } catch (e: Exception) {
                Log.e("AntonymBalloon", "❌ Error parsing puzzle data", e)
                fetchAntonymPuzzle(context) { pairs ->
                    if (pairs.isNotEmpty()) {
                        antonymPairs = pairs
                        totalPairs = pairs.size
                        gameStartTime = System.currentTimeMillis()
                        setupBalloons(pairs, 1, screenWidth, screenHeight) { balloonList ->
                            balloons = balloonList
                            isLoading = false
                        }
                    } else {
                        isLoading = false
                    }
                }
            }
        } else {
            fetchAntonymPuzzle(context) { pairs ->
                if (pairs.isNotEmpty()) {
                    antonymPairs = pairs
                    totalPairs = pairs.size
                    gameStartTime = System.currentTimeMillis()
                    setupBalloons(pairs, 1, screenWidth, screenHeight) { balloonList ->
                        balloons = balloonList
                        isLoading = false
                    }
                } else {
                    isLoading = false
                }
            }
        }
    }

    // Enhanced balloon click handler with scoring
    fun handleBalloonClick(balloon: BalloonItem) {
        if (balloon.isBurst || isGameComplete) return

        if (selectedBalloon == null) {
            // First balloon selected
            selectedBalloon = balloon
            balloons = balloons.map {
                if (it.id == balloon.id) it.copy(isSelected = true)
                else it.copy(isSelected = false)
            }
        } else if (selectedBalloon?.id == balloon.id) {
            // Same balloon clicked - deselect
            selectedBalloon = null
            balloons = balloons.map { it.copy(isSelected = false) }
        } else {
            // Second balloon selected - check for match
            val firstBalloon = selectedBalloon!!
            val matchStartTime = System.currentTimeMillis()
            val matchTime = matchStartTime - gameStartTime

            if (firstBalloon.pairId == balloon.pairId) {
                // Correct match!
                matchStreak++
                val matchScore = calculateMatchScore(true, matchTime, matchStreak)
                totalScore = totalScore + matchScore

                val correctPair = antonymPairs.find { it.id == balloon.pairId }
                val pairText = "${correctPair?.word1} ↔ ${correctPair?.word2}"

                Log.d("AntonymBalloon", "✅ Correct match! Score: +$matchScore, Total: $totalScore, Streak: $matchStreak")

                feedbackManager.showFeedback(
                    puzzleType = "antonymBalloon",
                    isCorrect = true,
                    userAnswer = "Matched: $pairText",
                    correctAnswer = pairText,
                    timeSpent = matchTime,
                    difficulty = difficulty,
                    timeRemaining = timeRemaining,
                    totalTime = totalTimeSeconds,
                    onComplete = {
                        balloons = balloons.map {
                            if (it.pairId == balloon.pairId) it.copy(isBurst = true, isSelected = false)
                            else it.copy(isSelected = false)
                        }
                        matchedPairs++
                        selectedBalloon = null
                        gameStartTime = System.currentTimeMillis() // Reset for next match

                        // Check if all pairs are matched
                        if (matchedPairs == totalPairs) {
                            val completionBonus = calculateCompletionBonus()
                            totalScore = totalScore + completionBonus

                            Log.d("AntonymBalloon", "🎉 All pairs matched! Completion bonus: +$completionBonus, Final score: $totalScore")

                            isGameComplete = true
                            showFeedback = true
                        }
                    }
                )
            } else {
                // Incorrect match
                matchStreak = 0 // Reset streak
                wrongAttempts++
                currentHearts = maxOf(0, currentHearts - 1)

                Log.d("AntonymBalloon", "❌ Wrong match. Hearts: $currentHearts, Wrong attempts: $wrongAttempts")

                feedbackManager.showFeedback(
                    puzzleType = "antonymBalloon",
                    isCorrect = false,
                    userAnswer = "Tried: ${firstBalloon.word} + ${balloon.word}",
                    correctAnswer = "Find matching antonym pairs",
                    timeSpent = matchTime,
                    difficulty = difficulty,
                    timeRemaining = timeRemaining,
                    totalTime = totalTimeSeconds,
                    onComplete = {
                        selectedBalloon = null
                        balloons = balloons.map { it.copy(isSelected = false) }
                        gameStartTime = System.currentTimeMillis() // Reset for next attempt

                        // Check if no hearts left
                        if (currentHearts <= 0) {
                            Log.d("AntonymBalloon", "💔 No hearts remaining. Game over with score: $totalScore")
                            isGameComplete = true
                            showFeedback = true
                        }
                    }
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE4C5A0),
                            Color(0xFFD4A574)
                        )
                    )
                )
        )

        if (isLoading) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF8A4DFF))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading antonym puzzle...", color = RvInk)
                }
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
              val compact = maxHeight < 600.dp
              Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .statusBarsPadding()
              ) {
                // Single-row HUD: back, timer, difficulty, hearts, level
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = RvInk)
                    }

                    // Timer with color coding
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(RvSurface, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = "Timer",
                            tint = if (timeRemaining <= 30) RvCoralEdge else RvGrapeEdge
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = displayTimer,
                            color = if (timeRemaining <= 30) RvCoralEdge else RvInk,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = difficulty.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = RvInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    // Hearts with current state
                    Row {
                        repeat(hearts) { index ->
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Heart",
                                tint = if (index < currentHearts) RvCoralEdge else RvInkSoft.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = level,
                        color = RvInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

                // Instructions + progress (single slim card; text dropped in compact mode)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = RvSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!compact) {
                            Text(
                                text = "🎈 Match Antonym Pairs 🎈",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvGrapeEdge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Tap balloons to match opposite words",
                                fontSize = 14.sp,
                                color = RvInkSoft,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Progress and score display
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (compact) 0.dp else 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stringResource(R.string.progress)}: $matchedPairs / $totalPairs",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = RvInk,
                                maxLines = 1
                            )

                            if (totalScore > 0) {
                                Text(
                                    text = "${stringResource(R.string.score_label)}: $totalScore",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RvInk,
                                    maxLines = 1
                                )
                            }

                            if (matchStreak > 1) {
                                Text(
                                    text = "🔥$matchStreak",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RvInk,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Balloons area: balloons are laid out on a grid that always fits the area
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("balloon_area")
                ) {
                    val n = balloons.size.coerceAtLeast(1)
                    var bestCols = 1
                    var bestDiameter = 0f
                    for (c in 1..n) {
                        val r = (n + c - 1) / c
                        val d = minOf(maxWidth.value / c - 8f, maxHeight.value / r - 36f)
                        if (d > bestDiameter) { bestDiameter = d; bestCols = c }
                    }
                    val cols = bestCols
                    val rows = (n + cols - 1) / cols
                    val diameter = bestDiameter.coerceIn(56f, 110f).dp
                    val cellW = maxWidth / cols
                    val cellH = maxHeight / rows
                    balloons.forEachIndexed { index, balloon ->
                        val col = index % cols
                        val row = index / cols
                        key(balloon.id + balloon.isSelected) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !balloon.isBurst,
                                exit = scaleOut(animationSpec = tween(300)) + fadeOut(),
                                modifier = Modifier.offset(
                                    x = cellW * col + (cellW - diameter) / 2,
                                    y = cellH * row + (cellH - diameter - 30.dp).coerceAtLeast(0.dp) / 2
                                )
                            ) {
                                BalloonView(
                                    balloon = balloon,
                                    size = diameter,
                                    onClick = { handleBalloonClick(balloon) }
                                )
                            }
                        }
                    }
                }
              }
            }
        }

        EnhancedUniversalFeedback(feedbackManager)

        // Enhanced game complete overlay with score
        if (showFeedback && isGameComplete) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RvSurface),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val success = matchedPairs == totalPairs

                        Text(
                            text = if (success) "🎉 Puzzle Complete!" else "💔 Game Over",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (success) RvSuccess else RvError
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Score display
                        if (totalScore > 0) {
                            Text(
                                text = "${stringResource(R.string.final_score)}: $totalScore",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvSky
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            text = when {
                                success -> "Great job matching all antonym pairs!"
                                currentHearts <= 0 -> "No hearts remaining"
                                else -> "Time's up!"
                            },
                            fontSize = 16.sp,
                            color = RvInkSoft,
                            textAlign = TextAlign.Center
                        )

                        // Performance stats
                        if (matchedPairs > 0 || wrongAttempts > 0) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = RvSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Matches: $matchedPairs",
                                            fontSize = 12.sp,
                                            color = RvInkSoft
                                        )
                                        Text(
                                            text = "Mistakes: $wrongAttempts",
                                            fontSize = 12.sp,
                                            color = RvInkSoft
                                        )
                                    }

                                    if (matchedPairs + wrongAttempts > 0) {
                                        val accuracy = (matchedPairs.toFloat() / (matchedPairs + wrongAttempts) * 100).toInt()
                                        Text(
                                            text = "${stringResource(R.string.accuracy)}: $accuracy%",
                                            fontSize = 12.sp,
                                            color = RvInkSoft,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                Log.d("AntonymBalloon", "🎯 Submitting result: success=$success, score=$totalScore")
                                onSubmitAnswer(success)
                                fetchNextPuzzle(totalScore) // Pass accumulated score
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A4DFF))
                        ) {
                            Text("Continue", color = RvOnTone)
                        }
                    }
                }
            }
        }
    }
}

// Enhanced Balloon View with better visual feedback
@Composable
fun BalloonView(
    balloon: BalloonItem,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 80.dp
) {
    val scale by animateFloatAsState(
        targetValue = if (balloon.isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "balloonScale"
    )

    val floatOffset by rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "floatAnimation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        // Balloon
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .offset(y = floatOffset.dp)
                .background(balloon.color, CircleShape)
                .border(
                    width = if (balloon.isSelected) 4.dp else 2.dp,
                    color = if (balloon.isSelected) RvInk else RvInk.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = balloon.word,
                color = RvInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // String
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .background(RvSurface)
        )
    }
}

// Balloon setup and fetch functions remain the same...
private fun setupBalloons(
    pairs: List<AntonymPair>,
    phase: Int,
    screenWidth: Int,
    screenHeight: Int,
    onComplete: (List<BalloonItem>) -> Unit
) {
    val balloonColors = listOf(
        Color(0xFFE91E63), // Pink
        RvSky, // Blue
        RvSuccess, // Green
        RvSun, // Orange
        RvGrape, // Purple
        RvSky, // Cyan
        Color(0xFFFFEB3B), // Yellow
        RvError  // Red
    )

    val allWords = mutableListOf<String>()

    pairs.forEach { pair ->
        allWords.add(pair.word1)
        allWords.add(pair.word2)
    }

    // Shuffle for random positioning
    allWords.shuffle()

    // Random positioning across the screen
    val balloonSize = 180 // Size of each balloon in dp
    val horizontalPadding = 40 // 20dp padding on each side
    val verticalPadding = 100 // Space for top bar, instructions, etc.
    val minSpacing = 80 // Minimum distance between balloon centers

    val availableWidth = (screenWidth - horizontalPadding - balloonSize).coerceAtLeast(500)
    val availableHeight = (screenHeight - verticalPadding - balloonSize).coerceAtLeast(300)

    // Generate random positions with collision avoidance
    val usedPositions = mutableListOf<Pair<Float, Float>>()

    val balloons = allWords.mapIndexed { index, word ->
        val pair = pairs.find { it.word1 == word || it.word2 == word }!!
        val colorIndex = index % balloonColors.size  // Random colors

        // Find a position that doesn't overlap with existing balloons
        var x: Float
        var y: Float
        var attempts = 0
        do {
            x = Random.nextFloat() * (availableWidth - balloonSize)
            y = Random.nextFloat() * (availableHeight - balloonSize)
            attempts++

            // Check if this position is too close to any existing balloon
            val tooClose = usedPositions.any { (existingX, existingY) ->
                val distance = kotlin.math.sqrt(
                    (x - existingX) * (x - existingX) + (y - existingY) * (y - existingY)
                )
                distance < minSpacing
            }

            // If we've tried too many times, just use the position to avoid infinite loop
            if (!tooClose || attempts > 50) {
                usedPositions.add(Pair(x, y))
                break
            }
        } while (attempts < 50)

        val finalX = x
        val finalY = y

        BalloonItem(
            word = word,
            pairId = pair.id,
            id = "${word}_$index",
            x = finalX,
            y = finalY,
            color = balloonColors[colorIndex]
        )
    }

    onComplete(balloons)
}

private fun fetchAntonymPuzzle(
    context: Context,
    onSuccess: (List<AntonymPair>) -> Unit
) {
    // CHANGED: Use non-blocking approach
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val puzzleQueueManager = PuzzleQueueManager.getInstance(context)

            // CHANGED: Use non-blocking method - returns immediately if queue is empty
            val queuedPuzzle = puzzleQueueManager.getNextPuzzleForPlay("antonyms", "medium")

            if (queuedPuzzle != null) {
                Log.d("AntonymPuzzle", "✅ Got antonym puzzle from queue: ${queuedPuzzle.puzzleId}")

                // Parse antonym pairs from the queued puzzle
                val antonymPairs = parseAntonymPairsFromPuzzle(queuedPuzzle)

                if (antonymPairs.isNotEmpty()) {
                    Log.d("AntonymPuzzle", "Successfully parsed ${antonymPairs.size} antonym pairs from queue")
                    onSuccess(antonymPairs)
                    return@launch
                }
            }

            Log.d("AntonymPuzzle", "⚡ Queue empty, immediate fallback to direct API")

            // CHANGED: Immediate fallback to direct API - no waiting
            fetchAntonymPuzzleDirect(context, onSuccess)

        } catch (e: Exception) {
            Log.e("AntonymPuzzle", "Error checking queue, immediate API fallback", e)

            // CHANGED: Immediate fallback to direct API
            fetchAntonymPuzzleDirect(context, onSuccess)
        }
    }
}

/**
 * Parse antonym pairs from QueuedPuzzle
 */
private fun parseAntonymPairsFromPuzzle(queuedPuzzle: QueuedPuzzle): List<AntonymPair> {
    return try {
        // Try to parse from question field if it contains JSON
        if (queuedPuzzle.question.trim().startsWith("{")) {
            val questionJson = JSONObject(queuedPuzzle.question)
            val pairsArray = questionJson.getJSONArray("pairs")
            val antonymPairs = mutableListOf<AntonymPair>()

            for (i in 0 until pairsArray.length()) {
                val pairObj = pairsArray.getJSONObject(i)
                val word1 = pairObj.getString("word1")
                val word2 = pairObj.getString("word2")

                antonymPairs.add(
                    AntonymPair(
                        word1 = word1,
                        word2 = word2,
                        id = i
                    )
                )
            }

            antonymPairs
        } else {
            // If not JSON, try to parse from options or other fields
            // This is a fallback for simpler antonym puzzle formats
            if (queuedPuzzle.options.size >= 2) {
                listOf(
                    AntonymPair(
                        word1 = queuedPuzzle.options[0],
                        word2 = queuedPuzzle.options[1],
                        id = 0
                    )
                )
            } else {
                emptyList()
            }
        }
    } catch (e: Exception) {
        Log.e("AntonymPuzzle", "Error parsing antonym pairs from queued puzzle", e)
        emptyList()
    }
}

/**
 * Original direct API call method - kept as fallback
 */
private fun fetchAntonymPuzzleDirect(
    context: Context,
    onSuccess: (List<AntonymPair>) -> Unit
) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    val userEmail = FirebaseAuth.getInstance().currentUser?.email

    val url = "https://puzzleverseai.com/fetch-next-puzzle-ios/antonyms?userId=$userId&email=$userEmail&difficulty=medium"

    Log.d("AntonymPuzzle", "Fetching antonym puzzle from: $url")

    val request = Request.Builder()
        .url(url)
        .addHeader("Accept", "application/json")
        .build()

    val client = OkHttpClient()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("AntonymPuzzle", "Failed to fetch antonym puzzle", e)
            Handler(Looper.getMainLooper()).post {
                // Fallback to sample data for development
                val samplePairs = listOf(
                    AntonymPair("hot", "cold", 1),
                    AntonymPair("big", "small", 2),
                    AntonymPair("happy", "sad", 3),
                    AntonymPair("fast", "slow", 4)
                )
                onSuccess(samplePairs)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()
            Log.d("AntonymPuzzle", "Response: $responseBody")

            Handler(Looper.getMainLooper()).post {
                if (!response.isSuccessful || responseBody == null) {
                    Log.e("AntonymPuzzle", "API request failed with code: ${response.code}")
                    // Fallback to sample data
                    val samplePairs = listOf(
                        AntonymPair("hot", "cold", 1),
                        AntonymPair("big", "small", 2),
                        AntonymPair("happy", "sad", 3),
                        AntonymPair("fast", "slow", 4)
                    )
                    onSuccess(samplePairs)
                    return@post
                }

                val json = try {
                    JSONObject(responseBody)
                } catch (e: Exception) {
                    Log.e("AntonymPuzzle", "Failed to parse JSON response", e)
                    null
                }

                if (json == null) {
                    Log.e("AntonymPuzzle", "Invalid JSON response")
                    // Fallback to sample data
                    val samplePairs = listOf(
                        AntonymPair("hot", "cold", 1),
                        AntonymPair("big", "small", 2),
                        AntonymPair("happy", "sad", 3),
                        AntonymPair("fast", "slow", 4)
                    )
                    onSuccess(samplePairs)
                    return@post
                }

                val success = json.optBoolean("success", false)

                if (!success) {
                    Log.w("AntonymPuzzle", "API returned success=false")
                    // Fallback to sample data
                    val samplePairs = listOf(
                        AntonymPair("hot", "cold", 1),
                        AntonymPair("big", "small", 2),
                        AntonymPair("happy", "sad", 3),
                        AntonymPair("fast", "slow", 4)
                    )
                    onSuccess(samplePairs)
                    return@post
                }

                // Parse puzzle data
                val puzzleDataJson = json.optJSONObject("puzzleData")
                if (puzzleDataJson == null) {
                    Log.e("AntonymPuzzle", "No puzzleData in response")
                    // Fallback to sample data
                    val samplePairs = listOf(
                        AntonymPair("hot", "cold", 1),
                        AntonymPair("big", "small", 2),
                        AntonymPair("happy", "sad", 3),
                        AntonymPair("fast", "slow", 4)
                    )
                    onSuccess(samplePairs)
                    return@post
                }

                try {
                    // Expected format: {"pairs": [{"word1": "hot", "word2": "cold"}, ...]}
                    val pairsArray = puzzleDataJson.getJSONArray("pairs")
                    val antonymPairs = mutableListOf<AntonymPair>()

                    for (i in 0 until pairsArray.length()) {
                        val pairObj = pairsArray.getJSONObject(i)
                        val word1 = pairObj.getString("word1")
                        val word2 = pairObj.getString("word2")

                        antonymPairs.add(
                            AntonymPair(
                                word1 = word1,
                                word2 = word2,
                                id = i
                            )
                        )
                    }

                    Log.d("AntonymPuzzle", "Successfully parsed ${antonymPairs.size} antonym pairs")
                    antonymPairs.forEach { pair ->
                        Log.d("AntonymPuzzle", "   Pair ${pair.id}: ${pair.word1} ↔ ${pair.word2}")
                    }

                    onSuccess(antonymPairs)

                } catch (e: Exception) {
                    Log.e("AntonymPuzzle", "Error parsing antonym pairs", e)
                    // Fallback to sample data
                    val samplePairs = listOf(
                        AntonymPair("hot", "cold", 1),
                        AntonymPair("big", "small", 2),
                        AntonymPair("happy", "sad", 3),
                        AntonymPair("fast", "slow", 4)
                    )
                    onSuccess(samplePairs)
                }
            }
        }
    })
}
// AdaptiveUniqueObjectPuzzleScreen.kt
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.ui.graphics.graphicsLayer
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.sqrt
import androidx.compose.ui.res.stringResource
import kotlin.random.Random

@Composable
fun AdaptiveUniqueObjectPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String,
    hearts: Int = 3,
    level: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "AdaptiveUniqueObject"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("uniqueObject"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // ✅ NEW: Competitive ranking state
    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "uniqueObject",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }

    // ✅ NEW: Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAnswers by remember { mutableStateOf(0) }

    // ✅ NEW: Session completion state
    var sessionResult by remember { mutableStateOf<AdaptiveSessionResult?>(null) }
    var gameCompleted by remember { mutableStateOf(false) }

    // Generate puzzle data based on current difficulty
    val puzzleData = remember(currentDifficultyLevel) {
        generateAdaptiveUniqueObjectPuzzle(currentDifficultyLevel)
    }

    // Game state
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var isGameActive by remember { mutableStateOf(true) }

    // Timer calculation and countdown
    val totalTimeSeconds = currentDifficultyLevel.timeLimit
    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(formatTime(totalTimeSeconds)) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            isGameActive = true
            showFeedback = false
            selectedIndex = null
            currentHearts = currentDifficultyLevel.livesAllowed
            gameStartTime = System.currentTimeMillis()
            timeRemaining = currentDifficultyLevel.timeLimit
            displayTimer = formatTime(currentDifficultyLevel.timeLimit)
        }
    }

    // ✅ UPDATED: Enhanced performance recording
    fun recordPerformance(isCorrect: Boolean, timeSpent: Long) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = puzzleData.objects.size,
            totalScore = totalScore,
            puzzleType = "uniqueObject"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
            }
        }
    }

    // ✅ UPDATED: Use unified score calculation
    fun calculateScore(isCorrect: Boolean, timeSpent: Long): Int {
        return calculateUnifiedAdaptiveScore(
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            difficulty = currentDifficultyLevel,
            challengeComplexity = puzzleData.objects.size,
            currentStreak = currentStreak,
            challengesCompleted = attempts,
            timeLimit = currentDifficultyLevel.timeLimit,
            puzzleType = "uniqueObject"
        )
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isGameActive) {
        if (timeRemaining > 0 && isGameActive) {
            delay(1000L)
            timeRemaining--
            displayTimer = formatTime(timeRemaining)
        } else if (timeRemaining <= 0 && isGameActive) {
            // Time's up - complete with current score
            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            isGameActive = false
            totalAnswers++

            recordPerformance(false, totalTimeSeconds * 1000L)

            // Mark game as completed to trigger session completion
            gameCompleted = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2D1B69))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // ✅ REPLACE: Use unified header instead of AdaptiveUniqueObjectTopBar
            AdaptiveUnifiedHeader(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer,
                lives = currentHearts,
                currentDifficulty = currentDifficultyLevel,
                score = totalScore,
                puzzleType = "uniqueObject",
                competitiveInsight = competitiveInsight,
                onBack = onBack,
                challengeNumber = attempts, // Fixed: Current attempt number
                totalChallenges = 1, // Fixed: Single challenge per session for unique object puzzles
                onPause = { /* Visual puzzles don't need pause functionality */ }, // Fixed: Optional pause
                onHint = { /* Hints would give away the answer for visual puzzles */ } // Fixed: No hints for visual identification
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ REPLACE: Use unified adaptation notification
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "uniqueObject",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )

            // Score and progress display
            if (totalScore > 0 || attempts > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (totalScore > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.score_label),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "$totalScore",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentDifficultyLevel.name.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Cyan
                            )
                            Text(
                                text = "${puzzleData.objects.size} objects",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        if (currentStreak > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.streak_label),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "🔥 $currentStreak",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF6F00)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Enhanced hearts display
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(currentDifficultyLevel.livesAllowed) { index ->
                    Text(
                        text = if (index < currentHearts) "❤️" else "🤍",
                        fontSize = 20.sp
                    )
                    if (index < currentDifficultyLevel.livesAllowed - 1) Spacer(modifier = Modifier.width(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Instruction with difficulty info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = puzzleData.instruction,
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = currentDifficultyLevel.description,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Objects scattered randomly across screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Generate random positions for each object
                val objectPositions = remember(puzzleData.objects.size) {
                    generateRandomPositions(puzzleData.objects.size)
                }

                puzzleData.objects.forEachIndexed { index, obj ->
                    val position = objectPositions[index]

                    Box(
                        modifier = Modifier
                            .offset(
                                x = (position.first * 80).dp, // Reduced spacing for more objects
                                y = (position.second * 80).dp
                            )
                    ) {
                        AdaptiveObjectItem(
                            uniqueObject = obj,
                            shapeMappings = puzzleData.shapeMappings,
                            colorMappings = puzzleData.colorMappings,
                            isSelected = selectedIndex == index,
                            difficulty = currentDifficultyLevel,
                            onClick = {
                                if (isGameActive && !showFeedback) {
                                    selectedIndex = index
                                    attempts++
                                    totalAnswers++

                                    val timeSpent = System.currentTimeMillis() - gameStartTime

                                    // Check answer immediately on selection
                                    val answerResult = checkUniqueObjectAnswer(puzzleData, index)
                                    isCorrect = answerResult

                                    if (isCorrect) {
                                        // Calculate and award score
                                        correctAnswers++
                                        val newStreak = currentStreak + 1
                                        val score = calculateScore(true, timeSpent)
                                        totalScore += score
                                        currentStreak = newStreak
                                        gamesPlayedThisSession++

                                        Log.d(TAG, "✅ Correct selection! Score: +$score, Total: $totalScore")

                                        recordPerformance(true, timeSpent)

                                        // Mark game as completed for success
                                        gameCompleted = true

                                        // Show feedback through unified system
                                        feedbackManager.showFeedback(
                                            puzzleType = "uniqueObject",
                                            isCorrect = true,
                                            userAnswer = "Object at position $index",
                                            correctAnswer = "Unique object found",
                                            timeSpent = timeSpent,
                                            difficulty = currentDifficultyLevel.name,
                                            timeRemaining = timeRemaining,
                                            totalTime = totalTimeSeconds,
                                            onComplete = {
                                                onSubmitAnswer(true)
                                                Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                                                fetchNextPuzzle(totalScore)
                                            }
                                        )
                                    } else {
                                        // Wrong selection - lose a heart
                                        currentHearts = maxOf(0, currentHearts - 1)
                                        currentStreak = 0

                                        Log.d(TAG, "❌ Wrong selection. Hearts: $currentHearts")

                                        if (currentHearts <= 0) {
                                            // Game over
                                            isGameActive = false

                                            recordPerformance(false, timeSpent)

                                            // Mark game as completed for game over
                                            gameCompleted = true

                                            feedbackManager.showFeedback(
                                                puzzleType = "uniqueObject",
                                                isCorrect = false,
                                                userAnswer = "Object at position $index",
                                                correctAnswer = "Different unique object",
                                                timeSpent = timeSpent,
                                                difficulty = currentDifficultyLevel.name,
                                                timeRemaining = timeRemaining,
                                                totalTime = totalTimeSeconds,
                                                onComplete = {
                                                    onSubmitAnswer(false)
                                                    fetchNextPuzzle(totalScore)
                                                }
                                            )
                                        } else {
                                            // Show brief error feedback but continue game
                                            showFeedback = true
                                            GlobalScope.launch {
                                                delay(1500)
                                                showFeedback = false
                                                selectedIndex = null
                                            }
                                        }
                                    }

                                    Log.d(TAG, "🔍 Object selected at index $index")
                                    Log.d(TAG, "🎯 Answer result: correct=$isCorrect")

                                    if (isCorrect) {
                                        isGameActive = false
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Back button - positioned as floating action button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.3f),
                    shape = CircleShape
                )
                .size(48.dp)
        ) {
            Text("←", color = Color.White, fontSize = 24.sp)
        }

        // Enhanced Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)

        // Brief error feedback for wrong selections (when hearts remain)
        AnimatedVisibility(
            visible = showFeedback && !isCorrect && currentHearts > 0,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF44336)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "❌ Not the unique object",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Keep looking! Hearts remaining: $currentHearts",
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // ✅ NEW: Session completion handling outside of click handlers
    if (gameCompleted) {
        UnifiedSessionCompletionHandler(
            puzzleType = "uniqueObject",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAnswers,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
                totalScore = totalScore, // Fixed: Use the accumulated total score
                averageTimePerPuzzle = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(), // Fixed: Time for this puzzle session
                currentStreak = currentStreak, // Fixed: Use the current streak value
                individualTimes = listOf(((System.currentTimeMillis() - gameStartTime) / 1000).toInt()) // Fixed: List with current puzzle time
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            sessionResult = result
            if (isCorrect) {
                onSubmitAnswer(true)
            } else {
                onSubmitAnswer(false)
            }
            fetchNextPuzzle(totalScore)
        }
    }
}

@Composable
fun AdaptiveUniqueObjectTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Level and difficulty
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "Level ${level.level}",
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                text = currentDifficulty.name.uppercase(),
                color = Color.Yellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Lives display with difficulty-based count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(currentDifficulty.livesAllowed) { index ->
                Text(
                    text = if (index < lives) "❤️" else "🤍",
                    fontSize = 16.sp
                )
            }
        }

        // Timer with color coding
        Text(
            text = timer,
            color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 10 } == true) {
                Color.Red
            } else if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true) {
                Color(0xFFFFA500)
            } else {
                Color.White
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AdaptiveObjectItem(
    uniqueObject: UniqueObject,
    shapeMappings: Map<String, String>,
    colorMappings: Map<String, String>,
    isSelected: Boolean,
    difficulty: DifficultyManager.DifficultyLevel,
    onClick: () -> Unit
) {
    val shape = shapeMappings[uniqueObject.shape.toString()] ?: "square"
    val colorName = colorMappings[uniqueObject.color.toString()] ?: "blue"
    val color = getColorFromName(colorName)

    // Adaptive object size based on difficulty
    val objectSize = when (difficulty.index) {
        0 -> 110.dp // Beginner - larger objects
        1 -> 100.dp // Easy
        2 -> 90.dp  // Medium
        3 -> 80.dp  // Hard
        4 -> 70.dp  // Expert - smaller objects
        else -> 100.dp
    }

    when (shape.lowercase()) {
        "circle" -> {
            Box(
                modifier = Modifier
                    .size(objectSize)
                    .background(color, CircleShape)
                    .border(
                        width = if (isSelected) 4.dp else 2.dp,
                        color = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                    .clickable { onClick() }
            )
        }
        "square" -> {
            Box(
                modifier = Modifier
                    .size(objectSize)
                    .background(color, RoundedCornerShape(8.dp))
                    .border(
                        width = if (isSelected) 4.dp else 2.dp,
                        color = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onClick() }
            )
        }
        "triangle" -> {
            Box(
                modifier = Modifier
                    .size(objectSize)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(objectSize * 0.8f)
                        .background(
                            color,
                            RoundedCornerShape(topStart = 50.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        )
                        .border(
                            width = if (isSelected) 4.dp else 2.dp,
                            color = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(topStart = 50.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        )
                )
            }
        }
        "diamond" -> {
            Box(
                modifier = Modifier
                    .size(objectSize)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(objectSize * 0.7f)
                        .background(color, RoundedCornerShape(8.dp))
                        .border(
                            width = if (isSelected) 4.dp else 2.dp,
                            color = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .graphicsLayer {
                            rotationZ = 45f
                        }
                )
            }
        }
        "hexagon" -> {
            Box(
                modifier = Modifier
                    .size(objectSize)
                    .background(color, RoundedCornerShape(16.dp))
                    .border(
                        width = if (isSelected) 4.dp else 2.dp,
                        color = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onClick() }
            )
        }
        "star" -> {
            Box(
                modifier = Modifier
                    .size(objectSize)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "⭐",
                    fontSize = (objectSize.value * 0.6f).sp,
                    modifier = Modifier
                        .background(
                            if (isSelected) Color.Yellow.copy(alpha = 0.3f) else Color.Transparent,
                            CircleShape
                        )
                        .padding(8.dp)
                )
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .size(objectSize)
                    .background(color, RoundedCornerShape(8.dp))
                    .border(
                        width = if (isSelected) 4.dp else 2.dp,
                        color = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onClick() }
            )
        }
    }
}

// Helper function to generate adaptive puzzle data
fun generateAdaptiveUniqueObjectPuzzle(difficulty: DifficultyManager.DifficultyLevel): UniqueObjectPuzzleData {
    val objectCount = when (difficulty.index) {
        0 -> 6   // Beginner
        1 -> 9   // Easy
        2 -> 12  // Medium
        3 -> 16  // Hard
        4 -> 20  // Expert
        else -> 9
    }

    val shapeRange = when (difficulty.index) {
        0 -> 3  // Basic shapes
        1 -> 4  // Add diamond
        2 -> 5  // Add hexagon
        3 -> 6  // Add star
        4 -> 6  // All shapes
        else -> 4
    }

    val colorRange = when (difficulty.index) {
        0 -> 3  // Basic colors
        1 -> 4  // Add green
        2 -> 6  // Add purple, orange
        3 -> 7  // Add pink
        4 -> 8  // All colors
        else -> 4
    }

    // Generate objects with one unique
    val objects = mutableListOf<UniqueObject>()
    val usedCombinations = mutableSetOf<String>()

    // First, create duplicate groups
    val duplicateGroupCount = (objectCount - 1) / 2
    var remainingObjects = objectCount - 1 // Reserve 1 for unique

    for (group in 1..duplicateGroupCount) {
        if (remainingObjects < 2) break

        val groupSize = if (group == duplicateGroupCount) remainingObjects else 2

        var obj: UniqueObject
        do {
            obj = UniqueObject(
                shape = Random.nextInt(shapeRange),
                color = Random.nextInt(colorRange)
            )
        } while (usedCombinations.contains("${obj.shape}-${obj.color}"))

        usedCombinations.add("${obj.shape}-${obj.color}")
        repeat(groupSize) { objects.add(obj) }
        remainingObjects -= groupSize
    }

    // Add the unique object
    var uniqueObj: UniqueObject
    do {
        uniqueObj = UniqueObject(
            shape = Random.nextInt(shapeRange),
            color = Random.nextInt(colorRange)
        )
    } while (usedCombinations.contains("${uniqueObj.shape}-${uniqueObj.color}"))

    objects.add(uniqueObj)
    objects.shuffle()

    // Create mappings
    val shapeMappings = mutableMapOf<String, String>()
    val shapeNames = listOf("circle", "square", "triangle", "diamond", "hexagon", "star")
    for (i in 0 until shapeRange) {
        shapeMappings[i.toString()] = shapeNames[i]
    }

    val colorMappings = mutableMapOf<String, String>()
    val colorNames = listOf("red", "blue", "yellow", "green", "purple", "orange", "pink", "cyan")
    for (i in 0 until colorRange) {
        colorMappings[i.toString()] = colorNames[i]
    }

    return UniqueObjectPuzzleData(
        objects = objects,
        totalObjects = objects.size,
        instruction = "Find the odd one out, and tap on it.",
        shapeMappings = shapeMappings,
        colorMappings = colorMappings,
        layout = ObjectLayout(
            rows = ceil(sqrt(objects.size.toDouble())).toInt(),
            cols = ceil(sqrt(objects.size.toDouble())).toInt(),
            totalCells = objects.size
        )
    )
}

// Helper function to check the answer
fun checkUniqueObjectAnswer(puzzleData: UniqueObjectPuzzleData, selectedIndex: Int): Boolean {
    val shapeColorCounts = mutableMapOf<String, MutableList<Int>>()

    puzzleData.objects.forEachIndexed { index, obj ->
        val combination = "${obj.shape}-${obj.color}"
        if (!shapeColorCounts.containsKey(combination)) {
            shapeColorCounts[combination] = mutableListOf()
        }
        shapeColorCounts[combination]!!.add(index)
    }

    val uniqueCombination = shapeColorCounts.entries.find { it.value.size == 1 }
    val correctIndex = uniqueCombination?.value?.firstOrNull()

    return selectedIndex == correctIndex
}
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

// Data classes for unique object puzzle
data class UniqueObject(
    val shape: Int,
    val color: Int
)

data class ObjectLayout(
    val rows: Int,
    val cols: Int,
    val totalCells: Int
)

data class UniqueObjectPuzzleData(
    val objects: List<UniqueObject>,
    val totalObjects: Int,
    val instruction: String,
    val shapeMappings: Map<String, String>,
    val colorMappings: Map<String, String>,
    val layout: ObjectLayout
)

data class UniqueObjectAnswer(
    val uniqueObjectIndex: Int,
    val uniqueObject: UniqueObject,
    val explanation: String
)

@Composable
fun UniqueObjectPuzzleScreen(
    difficulty: String,
    timer: String,
    hearts: Int = 3,
    level: String,
    puzzleData: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "UniqueObjectPuzzle"

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentHearts by remember { mutableStateOf(hearts) }

    // Parse puzzle data
    val parsedData = remember(puzzleData) {
        try {
            val puzzleJson = JSONObject(puzzleData)

            // Parse objects array
            val objectsArray = puzzleJson.getJSONArray("objects")
            val objects = mutableListOf<UniqueObject>()
            for (i in 0 until objectsArray.length()) {
                val objJson = objectsArray.getJSONObject(i)
                objects.add(UniqueObject(
                    shape = objJson.getInt("shape"),
                    color = objJson.getInt("color")
                ))
            }

            // Parse shape mappings
            val shapeMappingsJson = puzzleJson.getJSONObject("shapeMappings")
            val shapeMappings = mutableMapOf<String, String>()
            shapeMappingsJson.keys().forEach { key ->
                shapeMappings[key] = shapeMappingsJson.getString(key)
            }

            // Parse color mappings
            val colorMappingsJson = puzzleJson.getJSONObject("colorMappings")
            val colorMappings = mutableMapOf<String, String>()
            colorMappingsJson.keys().forEach { key ->
                colorMappings[key] = colorMappingsJson.getString(key)
            }

            // Parse layout
            val layoutJson = puzzleJson.getJSONObject("layout").getJSONObject("grid")
            val layout = ObjectLayout(
                rows = layoutJson.getInt("rows"),
                cols = layoutJson.getInt("cols"),
                totalCells = layoutJson.getInt("totalCells")
            )

            Log.d(TAG, "📊 Parsed unique object puzzle:")
            Log.d(TAG, "   Objects: ${objects.size}")
            Log.d(TAG, "   Layout: ${layout.rows}×${layout.cols}")
            Log.d(TAG, "   Shape mappings: $shapeMappings")
            Log.d(TAG, "   Color mappings: $colorMappings")

            UniqueObjectPuzzleData(
                objects = objects,
                totalObjects = puzzleJson.getInt("totalObjects"),
                instruction = puzzleJson.getString("instruction"),
                shapeMappings = shapeMappings,
                colorMappings = colorMappings,
                layout = layout
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse puzzle data", e)
            null
        }
    }

    // Game state
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var isGameActive by remember { mutableStateOf(true) }

    // Timer calculation and countdown
    val totalTimeSeconds = remember(timer) {
        parseTimeToSeconds(timer)
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

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
            fetchNextPuzzle(totalScore)
        }
    }

    // Calculate score for unique object identification
    fun calculateUniqueObjectScore(
        isCorrect: Boolean,
        timeSpent: Long,
        attemptNumber: Int,
        objectCount: Int,
        difficulty: String,
        heartsRemaining: Int
    ): Int {
        if (!isCorrect) return 0

        // Base points by difficulty
        val basePoints = when (difficulty.lowercase()) {
            "easy" -> 40
            "medium" -> 60
            "hard" -> 80
            "expert" -> 100
            else -> 60
        }

        // Object complexity bonus based on number of objects
        val complexityMultiplier = when {
            objectCount >= 20 -> 2.0f // Very complex
            objectCount >= 15 -> 1.7f // Complex
            objectCount >= 10 -> 1.4f // Moderate
            objectCount >= 6 -> 1.2f // Simple
            else -> 1.0f // Very simple
        }

        // Pattern recognition bonus (finding unique among many)
        val patternBonus = when {
            objectCount >= 15 -> (basePoints * 0.6f).toInt()
            objectCount >= 10 -> (basePoints * 0.4f).toInt()
            objectCount >= 6 -> (basePoints * 0.2f).toInt()
            else -> 0
        }

        // Speed bonus (faster identification = higher score)
        val timeSeconds = timeSpent / 1000f
        val speedBonus = when {
            timeSeconds <= 5f -> (basePoints * 0.5f).toInt() // Very fast
            timeSeconds <= 10f -> (basePoints * 0.3f).toInt() // Fast
            timeSeconds <= 15f -> (basePoints * 0.1f).toInt() // Moderate
            else -> 0
        }

        // First attempt bonus
        val attemptBonus = if (attemptNumber == 1) {
            (basePoints * 0.3f).toInt()
        } else {
            maxOf(0, (basePoints * 0.3f * (1f - (attemptNumber - 1) * 0.1f)).toInt())
        }

        // Hearts preservation bonus
        val heartsBonus = heartsRemaining * (basePoints / 10)

        // Visual discrimination bonus
        val discriminationBonus = (basePoints * 0.2f).toInt()

        val finalScore = ((basePoints * complexityMultiplier).toInt() + patternBonus + speedBonus + attemptBonus + heartsBonus + discriminationBonus)

        Log.d(TAG, "🏆 Unique object score calculation:")
        Log.d(TAG, "  Object count: $objectCount")
        Log.d(TAG, "  Base points: $basePoints")
        Log.d(TAG, "  Complexity multiplier: ${complexityMultiplier}x")
        Log.d(TAG, "  Pattern bonus: $patternBonus")
        Log.d(TAG, "  Speed bonus: $speedBonus (${timeSeconds}s)")
        Log.d(TAG, "  Attempt bonus: $attemptBonus (attempt #$attemptNumber)")
        Log.d(TAG, "  Hearts bonus: $heartsBonus")
        Log.d(TAG, "  Discrimination bonus: $discriminationBonus")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, basePoints / 4) // Minimum 25% of base points
    }

    if (parsedData == null) {
        // Error state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.error_loading_puzzle), color = Color.Red)
                Button(onClick = onBack) {
                    Text(stringResource(R.string.go_back))
                }
            }
        }
        return
    }

    // Main UI
    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RvCanvas)
        )

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
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
        ) {
            // Single-row HUD: back, level/difficulty, hearts, score, timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = RvInk
                    )
                }

                // Level and difficulty
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = level,
                        color = RvInk,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Text(
                        text = difficulty.uppercase(),
                        color = RvInkSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Hearts
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally)
                ) {
                    repeat(hearts) { index ->
                        Text(
                            text = if (index < currentHearts) "❤️" else "🤍",
                            fontSize = 20.sp
                        )
                    }
                }

                if (totalScore > 0) {
                    Text(
                        text = "Score: $totalScore",
                        color = RvInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Timer with color coding
                Text(
                    text = displayTimer, // Use live countdown
                    color = if (timeRemaining <= 30) RvCoralEdge else RvInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 12.dp))

            // Instruction
            Text(
                text = parsedData.instruction,
                color = RvInk,
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 12.dp))

            // Objects laid out on the best-fitting grid for the available area
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("object_area")
            ) {
                val n = parsedData.objects.size.coerceAtLeast(1)
                var bestCols = 1
                var bestSize = 0f
                for (c in 1..n) {
                    val r = (n + c - 1) / c
                    val d = minOf(maxWidth.value / c, maxHeight.value / r) - 8f
                    if (d > bestSize) { bestSize = d; bestCols = c }
                }
                val cols = bestCols
                val rows = (n + cols - 1) / cols
                val objectSize = bestSize.coerceIn(48f, 100f).dp
                val cellW = maxWidth / cols
                val cellH = maxHeight / rows

                parsedData.objects.forEachIndexed { index, obj ->
                    Box(
                        modifier = Modifier
                            .testTag("unique_object_$index")
                            .offset(
                                x = cellW * (index % cols) + (cellW - objectSize) / 2,
                                y = cellH * (index / cols) + (cellH - objectSize) / 2
                            )
                    ) {
                        ObjectItem(
                            uniqueObject = obj,
                            size = objectSize,
                            shapeMappings = parsedData.shapeMappings,
                            colorMappings = parsedData.colorMappings,
                            isSelected = selectedIndex == index,
                            onClick = {
                                if (isGameActive && !showFeedback) {
                                    selectedIndex = index
                                    attempts++

                                    val timeSpent = System.currentTimeMillis() - gameStartTime

                                    // Check answer immediately on selection
                                    val answerResult = checkAnswer(puzzleData, index)
                                    isCorrect = answerResult

                                    if (isCorrect) {
                                        // Calculate and award score
                                        val score = calculateUniqueObjectScore(
                                            isCorrect = true,
                                            timeSpent = timeSpent,
                                            attemptNumber = attempts,
                                            objectCount = parsedData.objects.size,
                                            difficulty = difficulty,
                                            heartsRemaining = currentHearts
                                        )
                                        totalScore = score

                                        Log.d(TAG, "✅ Correct selection! Score: $totalScore")

                                        // Show feedback through unified system
                                        feedbackManager.showFeedback(
                                            puzzleType = "uniqueObject",
                                            isCorrect = true,
                                            userAnswer = "Object at position $index",
                                            correctAnswer = "Unique object found",
                                            timeSpent = timeSpent,
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
                                        // Wrong selection - lose a heart
                                        currentHearts = maxOf(0, currentHearts - 1)

                                        Log.d(TAG, "❌ Wrong selection. Hearts: $currentHearts")

                                        if (currentHearts <= 0) {
                                            // Game over
                                            isGameActive = false

                                            feedbackManager.showFeedback(
                                                puzzleType = "uniqueObject",
                                                isCorrect = false,
                                                userAnswer = "Object at position $index",
                                                correctAnswer = "Different unique object",
                                                timeSpent = timeSpent,
                                                difficulty = difficulty,
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
                                            kotlinx.coroutines.GlobalScope.launch {
                                                delay(1500)
                                                showFeedback = false
                                                selectedIndex = null
                                            }
                                        }
                                    }

                                    Log.d(TAG, "📝 Object selected at index $index")
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

        }
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
                    containerColor = RvError
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "❌ Not the unique object",
                        color = RvInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Keep looking! Hearts remaining: $currentHearts",
                        color = RvInk,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Helper function to generate random positions
fun generateRandomPositions(objectCount: Int): List<Pair<Float, Float>> {
    val positions = mutableListOf<Pair<Float, Float>>()
    val minDistance = 1.0f // Minimum distance between objects
    val maxAttempts = 50

    // Screen bounds (accounting for object size)
    val maxX = 2.5f // Multiply by 100 for dp conversion
    val maxY = 4.0f // Multiply by 100 for dp conversion

    for (i in 0 until objectCount) {
        var attempts = 0
        var validPosition: Pair<Float, Float>

        do {
            validPosition = Pair(
                Random.nextFloat() * maxX,
                Random.nextFloat() * maxY
            )
            attempts++
        } while (attempts < maxAttempts &&
            positions.any { existing ->
                val distance = sqrt(
                    (existing.first - validPosition.first).let { it * it } +
                            (existing.second - validPosition.second).let { it * it }
                )
                distance < minDistance
            })

        positions.add(validPosition)
    }

    return positions
}

@Composable
fun ObjectItem(
    uniqueObject: UniqueObject,
    shapeMappings: Map<String, String>,
    colorMappings: Map<String, String>,
    isSelected: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 100.dp
) {
    val shape = shapeMappings[uniqueObject.shape.toString()] ?: "square"
    val colorName = colorMappings[uniqueObject.color.toString()] ?: "blue"
    val color = getColorFromName(colorName)

    // Calculate size based on screen space - make objects larger
    val objectSize = size

    when (shape.lowercase()) {
        "circle" -> {
            Box(
                modifier = Modifier
                    .size(objectSize)
                    .background(color, CircleShape)
                    .border(
                        width = if (isSelected) 4.dp else 2.dp,
                        color = if (isSelected) RvInk else RvInkSoft,
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
                        color = if (isSelected) RvInk else RvInkSoft,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onClick() }
            )
        }
        "triangle" -> {
            // Custom triangle shape using Canvas or a triangle-like approach
            Box(
                modifier = Modifier
                    .size(objectSize)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                // Create triangle using a rotated square
                Box(
                    modifier = Modifier
                        .size(objectSize * 0.8f)
                        .background(
                            color,
                            RoundedCornerShape(topStart = 50.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        )
                        .border(
                            width = if (isSelected) 4.dp else 2.dp,
                            color = if (isSelected) RvInk else RvInkSoft,
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
                // Create diamond using rotated square
                Box(
                    modifier = Modifier
                        .size(objectSize * 0.7f)
                        .background(color, RoundedCornerShape(8.dp))
                        .border(
                            width = if (isSelected) 4.dp else 2.dp,
                            color = if (isSelected) RvInk else RvInkSoft,
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
                        color = if (isSelected) RvInk else RvInkSoft,
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
                // Use star emoji or create custom star shape
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
            // Default to square
            Box(
                modifier = Modifier
                    .size(objectSize)
                    .background(color, RoundedCornerShape(8.dp))
                    .border(
                        width = if (isSelected) 4.dp else 2.dp,
                        color = if (isSelected) RvInk else RvInkSoft,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onClick() }
            )
        }
    }
}

// Helper functions
fun getColorFromName(colorName: String): Color {
    return when (colorName.lowercase()) {
        "red" -> Color(0xFFE91E63)
        "blue" -> RvSky
        "yellow" -> Color(0xFFFFC107)
        "green" -> RvSuccess
        "purple" -> RvGrape
        "orange" -> RvSun
        "pink" -> Color(0xFFE91E63)
        "cyan" -> RvSky
        else -> RvSky // Default blue
    }
}

private fun getShapeFromName(shapeName: String): RoundedCornerShape {
    return when (shapeName.lowercase()) {
        "circle" -> CircleShape as RoundedCornerShape
        "square" -> RoundedCornerShape(8.dp)
        "triangle" -> RoundedCornerShape(8.dp)
        "diamond" -> RoundedCornerShape(8.dp)
        "hexagon" -> RoundedCornerShape(8.dp)
        "star" -> RoundedCornerShape(8.dp)
        else -> RoundedCornerShape(8.dp) // Default square-ish
    }
}

private fun parseTimeToSeconds(timeString: String): Int {
    return try {
        val parts = timeString.split(":")
        if (parts.size == 2) {
            val minutes = parts[0].toInt()
            val seconds = parts[1].toInt()
            minutes * 60 + seconds
        } else {
            90 // Default 90 seconds
        }
    } catch (e: Exception) {
        90 // Default 90 seconds
    }
}

private fun checkAnswer(puzzleDataString: String, selectedIndex: Int): Boolean {
    return try {
        // Parse the puzzle data to get the correct answer
        val puzzleJson = JSONObject(puzzleDataString)
        val objectsArray = puzzleJson.getJSONArray("objects")

        // Find the unique object by counting occurrences
        val shapeColorCounts = mutableMapOf<String, MutableList<Int>>()

        for (i in 0 until objectsArray.length()) {
            val obj = objectsArray.getJSONObject(i)
            val shape = obj.getInt("shape")
            val color = obj.getInt("color")
            val combination = "$shape-$color"

            if (!shapeColorCounts.containsKey(combination)) {
                shapeColorCounts[combination] = mutableListOf()
            }
            shapeColorCounts[combination]!!.add(i)
        }

        // Find the combination that appears only once
        val uniqueCombination = shapeColorCounts.entries.find { it.value.size == 1 }
        val correctIndex = uniqueCombination?.value?.firstOrNull()

        Log.d("UniqueObjectCheck", "🔍 Shape-color combinations: $shapeColorCounts")
        Log.d("UniqueObjectCheck", "🎯 Correct index: $correctIndex, Selected: $selectedIndex")

        selectedIndex == correctIndex
    } catch (e: Exception) {
        Log.e("UniqueObjectCheck", "❌ Error checking answer", e)
        false
    }
}
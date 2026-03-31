// FindDifferencesScreenWrapper.kt
package com.kreativekoala.riddleverse

import android.util.Log
import android.graphics.RectF
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONArray
import java.lang.Math.pow
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

// Data classes for Find Differences puzzle
data class FindDifferencesPuzzleData(
    val puzzleId: String,
    val imageUrl: String,
    val theme: String,
    val instructions: String,
    val totalDifferences: Int,
    val timeLimit: Long,
    val differences: List<DifferenceData>,
    val gameSettings: GameSettings
)

data class DifferenceData(
    val id: String, // Changed to String to match server format
    val description: String,
    val side: String,
    val x: Double, // Percentage
    val y: Double, // Percentage
    val tolerance: Double,
    val type: String,
    val hint: String,
    val confidence: Double = 0.7,
    val detectionMethod: String = "generated"
)

data class GameSettings(
    val clickTolerance: Double,
    val maxWrongClicks: Int,
    val hintSystem: Boolean,
    val scoringSystem: String
)

data class FoundDifference(
    val id: String, // Changed to String to match server format
    val x: Float, // Pixel coordinates
    val y: Float,
    val foundAt: Long = System.currentTimeMillis()
)

@Composable
fun FindDifferencesScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "FindDifferencesWrapper"
    Log.d(TAG, "🖼️ Loading Find Differences puzzle: ${currentPuzzle.puzzleId}")

    // State for puzzle data
    var puzzleData by remember { mutableStateOf<FindDifferencesPuzzleData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Parse puzzle data on first composition
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            Log.d(TAG, "📋 Raw puzzle data inspection:")
            Log.d(TAG, "📋 Puzzle ID: ${currentPuzzle.puzzleId}")
            Log.d(TAG, "📋 Question type: ${currentPuzzle.question?.javaClass?.simpleName}")
            Log.d(TAG, "📋 Question content: ${currentPuzzle.question}")

            // Parse the question data safely
            val questionData = try {
                when (currentPuzzle.question) {
                    is String -> {
                        Log.d(TAG, "📋 Parsing question as JSON string")
                        JSONObject(currentPuzzle.question as String)
                    }
                    null -> {
                        Log.w(TAG, "📋 Question is null")
                        JSONObject()
                    }
                    else -> {
                        Log.d(TAG, "📋 Converting non-string question to JSON")
                        // For any other type, convert to string first
                        val questionStr = currentPuzzle.question.toString()
                        try {
                            JSONObject(questionStr)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse as JSON, creating empty: ${e.message}")
                            JSONObject()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing question data: ${e.message}")
                JSONObject()
            }

            Log.d(TAG, "📋 Parsed JSON keys: ${questionData.keys().asSequence().toList()}")

            // The actual puzzle data is nested inside a "question" object
            val actualQuestionData = if (questionData.has("question")) {
                questionData.getJSONObject("question")
            } else {
                questionData // Fallback to root if no nesting
            }

            Log.d(TAG, "📋 Actual question keys: ${actualQuestionData.keys().asSequence().toList()}")

            // Extract puzzle information from the nested question object
            val puzzleId = actualQuestionData.optString("puzzleId", currentPuzzle.puzzleId)
            val imageUrl = actualQuestionData.optString("imageUrl", "")
            val theme = actualQuestionData.optString("theme", "Find the differences")
            val instructions = actualQuestionData.optString("instructions", "Find all differences between the images")

            // Handle totalDifferences - might be in different places
            var totalDifferences = actualQuestionData.optInt("totalDifferences", 0)
            if (totalDifferences == 0) {
                // Try differences array length
                val differencesArray = actualQuestionData.optJSONArray("differences")
                if (differencesArray != null) {
                    totalDifferences = differencesArray.length()
                }
            }
            if (totalDifferences == 0) {
                totalDifferences = 5 // Default fallback
            }

            val timeLimit = actualQuestionData.optLong("timeLimit", 300000) // 5 minutes default

            Log.d(TAG, "🖼️ Parsed image URL: '$imageUrl'")
            Log.d(TAG, "🎯 Puzzle ID: '$puzzleId'")
            Log.d(TAG, "📊 Total differences: $totalDifferences")

            // Check if image is ready - be more flexible with validation
            if (imageUrl.isEmpty() || imageUrl == "null") {
                Log.e(TAG, "❌ Image URL is empty or null: '$imageUrl'")
                errorMessage = "Image URL not found. Please try again."
                hasError = true
                isLoading = false
                return@LaunchedEffect
            }

            // Only check for PLACEHOLDER if it's actually there, not just any URL
            if (imageUrl.contains("PLACEHOLDER") || imageUrl.contains("placeholder")) {
                Log.e(TAG, "❌ Image still contains placeholder: '$imageUrl'")
                errorMessage = "Image is still being generated. Please try again in a moment."
                hasError = true
                isLoading = false
                return@LaunchedEffect
            }

            // Validate URL format more flexibly
            if (!imageUrl.startsWith("http")) {
                Log.e(TAG, "❌ Invalid image URL format: '$imageUrl'")
                errorMessage = "Invalid image URL format. Please try again."
                hasError = true
                isLoading = false
                return@LaunchedEffect
            }

            // Parse differences array - this is the key part for your server data
            val differencesArray = when {
                actualQuestionData.has("differences") -> {
                    val differencesValue = actualQuestionData.get("differences")
                    when (differencesValue) {
                        is JSONArray -> differencesValue
                        is List<*> -> {
                            // Convert List to JSONArray
                            val jsonArray = JSONArray()
                            differencesValue.forEach { item ->
                                when (item) {
                                    is Map<*, *> -> {
                                        val jsonObj = JSONObject()
                                        item.forEach { (key, value) ->
                                            if (key != null) {
                                                jsonObj.put(key.toString(), value)
                                            }
                                        }
                                        jsonArray.put(jsonObj)
                                    }
                                    else -> jsonArray.put(item)
                                }
                            }
                            jsonArray
                        }
                        else -> JSONArray()
                    }
                }
                else -> JSONArray()
            }
            val differences = mutableListOf<DifferenceData>()

            Log.d(TAG, "🔍 Parsing ${differencesArray.length()} differences...")

            for (i in 0 until differencesArray.length()) {
                val diffJson = differencesArray.getJSONObject(i)

                // Handle server format where coordinates might be x_percentage/y_percentage or just x/y
                val xCoord = if (diffJson.has("x_percentage")) {
                    diffJson.optDouble("x_percentage")
                } else {
                    diffJson.optDouble("x", 50.0)
                }

                val yCoord = if (diffJson.has("y_percentage")) {
                    diffJson.optDouble("y_percentage")
                } else {
                    diffJson.optDouble("y", 50.0)
                }

                val difference = DifferenceData(
                    id = diffJson.optString("id", "diff_$i"),
                    description = diffJson.optString("description", "Difference ${i + 1}"),
                    side = diffJson.optString("side", "right"), // Server uses "right" for split-screen
                    x = xCoord,
                    y = yCoord,
                    tolerance = diffJson.optDouble("tolerance", 20.0), // Server default is 20.0
                    type = diffJson.optString("type", diffJson.optString("detectionMethod", "generated")),
                    hint = diffJson.optString("hint", diffJson.optString("description", "Look carefully")),
                    confidence = diffJson.optDouble("confidence", 0.7),
                    detectionMethod = diffJson.optString("detectionMethod", "generated")
                )

                differences.add(difference)

                val differenceSide = if (difference.x >= 50.0) "RIGHT" else "LEFT"
                Log.d(TAG, "✅ Parsed difference ${i + 1}: ${difference.description} at (${difference.x}%, ${difference.y}%) [$differenceSide] tolerance: ${difference.tolerance}")
            }

            // Parse game settings - use server defaults if not present
            val gameSettingsJson = when {
                actualQuestionData.has("gameSettings") -> {
                    val gameSettingsValue = actualQuestionData.get("gameSettings")
                    when (gameSettingsValue) {
                        is JSONObject -> gameSettingsValue
                        is Map<*, *> -> {
                            val jsonObj = JSONObject()
                            gameSettingsValue.forEach { (key, value) ->
                                if (key != null) {
                                    jsonObj.put(key.toString(), value)
                                }
                            }
                            jsonObj
                        }
                        else -> JSONObject()
                    }
                }
                else -> JSONObject()
            }

            val gameSettings = GameSettings(
                clickTolerance = gameSettingsJson.optDouble("clickTolerance", 20.0), // Match server tolerance
                maxWrongClicks = gameSettingsJson.optInt("maxWrongClicks", 8),
                hintSystem = gameSettingsJson.optBoolean("hintSystem", true),
                scoringSystem = gameSettingsJson.optString("scoringSystem", "per_difference")
            )

            // Create puzzle data
            puzzleData = FindDifferencesPuzzleData(
                puzzleId = puzzleId,
                imageUrl = imageUrl,
                theme = theme,
                instructions = instructions,
                totalDifferences = totalDifferences,
                timeLimit = timeLimit,
                differences = differences,
                gameSettings = gameSettings
            )

            Log.d(TAG, "✅ Successfully parsed find differences puzzle:")
            Log.d(TAG, "  Image URL: $imageUrl")
            Log.d(TAG, "  Total differences: $totalDifferences")
            Log.d(TAG, "  Parsed differences: ${differences.size}")
            Log.d(TAG, "  Theme: $theme")
            Log.d(TAG, "  Time limit: ${timeLimit / 1000}s")

            // Log each difference for debugging
            differences.forEachIndexed { index, diff ->
                val side = if (diff.x >= 50.0) "RIGHT" else "LEFT"
                Log.d(TAG, "  Diff $index: ${diff.id} - ${diff.description} at (${diff.x}%, ${diff.y}%) [$side] [${diff.detectionMethod}] confidence: ${diff.confidence}")
            }

            isLoading = false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing puzzle data: ${e.message}", e)
            Log.e(TAG, "📄 Raw question data: ${currentPuzzle.question}")
            Log.e(TAG, "📄 Question data type: ${currentPuzzle.question?.javaClass?.simpleName}")
            errorMessage = "Failed to load puzzle: ${e.message}"
            hasError = true
            isLoading = false
        }
    }

    when {
        isLoading -> {
            LoadingScreen(message = "Loading find differences puzzle...")
        }

        hasError -> {
            FdErrorScreen(
                message = errorMessage,
                onRetry = {
                    Log.d(TAG, "🔄 Retrying puzzle load")
                    isLoading = true
                    hasError = false
                },
                onSkip = {
                    Log.d(TAG, "⭐ Skipping puzzle due to error")
                    handlePuzzleCompletion(false, false, 0)
                }
            )
        }

        puzzleData != null -> {
            FindDifferencesPuzzleScreen(
                puzzleData = puzzleData!!,
                difficulty = currentPuzzle.difficulty ?: "Medium",
                round = "ROUND ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber} of ${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
                onPuzzleComplete = { score, foundCount ->
                    Log.d(TAG, "🎉 Puzzle completed! Found: $foundCount/${puzzleData!!.totalDifferences}, Score: $score")
                    val isComplete = foundCount >= puzzleData!!.totalDifferences
                    handlePuzzleCompletion(isComplete, isComplete, score)
                },
                onBack = onBack,
                currentPuzzle = currentPuzzle,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun FdErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⚠️ Error",
                color = Color.Red,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = onRetry) {
                    Text("Retry")
                }

                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Skip")
                }
            }
        }
    }
}

// Enhanced data classes
data class ZoneHint(
    val zone: String,
    val generalDescription: String,
    val specificHint: String
)

enum class ViewMode {
    LANDSCAPE_FULL,
    PORTRAIT_ZOOMABLE,
    ZONES_LIST
}

@Composable
fun FindDifferencesPuzzleScreen(
    puzzleData: FindDifferencesPuzzleData,
    difficulty: String,
    round: String,
    onPuzzleComplete: (Int, Int) -> Unit,
    onBack: () -> Unit,
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel
) {
    val TAG = "EnhancedFindDifferences"

    // Game state
    var foundDifferences by remember { mutableStateOf<List<FoundDifference>>(emptyList()) }
    var wrongClicks by remember { mutableStateOf(0) }
    var hintsUsed by remember { mutableStateOf(0) }
    var timeRemaining by remember { mutableStateOf(puzzleData.timeLimit / 1000) }
    var gameCompleted by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf<DifferenceData?>(null) }

    // Mobile optimizations
    var viewMode by remember { mutableStateOf(ViewMode.LANDSCAPE_FULL) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Detect if should start in landscape mode
    LaunchedEffect(configuration.screenWidthDp) {
        viewMode = if (configuration.screenWidthDp > configuration.screenHeightDp) {
            ViewMode.LANDSCAPE_FULL
        } else {
            ViewMode.PORTRAIT_ZOOMABLE
        }
    }

    // Generate zone-based hints
    val zoneHints = remember(puzzleData.differences) {
        generateZoneHints(puzzleData.differences)
    }

    // Timer and completion logic
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0 && !gameCompleted) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining <= 0 && !gameCompleted) {
            gameCompleted = true
            val score = calculateScore(foundDifferences.size, puzzleData.totalDifferences, wrongClicks, hintsUsed)
            onPuzzleComplete(score, foundDifferences.size)
        }
    }

    LaunchedEffect(foundDifferences.size) {
        if (foundDifferences.size >= puzzleData.totalDifferences && !gameCompleted) {
            gameCompleted = true
            val score = calculateScore(foundDifferences.size, puzzleData.totalDifferences, wrongClicks, hintsUsed)
            onPuzzleComplete(score, foundDifferences.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Enhanced Header with view mode controls
        EnhancedFindDifferencesHeader(
            difficulty = difficulty,
            round = round,
            timeRemaining = timeRemaining,
            foundCount = foundDifferences.size,
            totalDifferences = puzzleData.totalDifferences,
            wrongClicks = wrongClicks,
            maxWrongClicks = puzzleData.gameSettings.maxWrongClicks,
            viewMode = viewMode,
            onBack = onBack,
            onViewModeChange = { newMode ->
                viewMode = newMode
                // Reset zoom when changing modes
                scale = 1f
                offsetX = 0f
                offsetY = 0f
            },
            onHint = {
                if (puzzleData.gameSettings.hintSystem && hintsUsed < 3) {
                    val unfoundDifferences = puzzleData.differences.filter { diff ->
                        !foundDifferences.any { found -> found.id == diff.id }
                    }
                    if (unfoundDifferences.isNotEmpty()) {
                        showHint = unfoundDifferences.first()
                        hintsUsed++
                    }
                }
            },
            canUseHint = puzzleData.gameSettings.hintSystem && hintsUsed < 3
        )

        when (viewMode) {
            ViewMode.LANDSCAPE_FULL -> {
                LandscapeImageView(
                    puzzleData = puzzleData,
                    foundDifferences = foundDifferences,
                    showHint = showHint,
                    gameCompleted = gameCompleted,
                    onImageClick = { x, y, imageSize ->
                        handleImageClick(
                            x = x,
                            y = y,
                            imageSize = imageSize,
                            puzzleData = puzzleData,
                            foundDifferences = foundDifferences,
                            onDifferenceFound = { difference, clickX, clickY ->
                                foundDifferences = foundDifferences + FoundDifference(
                                    id = difference.id,
                                    x = clickX,
                                    y = clickY
                                )
                                showHint = null
                            },
                            onWrongClick = { wrongClicks++ }
                        )
                    },
                    onImageSizeChanged = { imageSize = it }
                )
            }

            ViewMode.PORTRAIT_ZOOMABLE -> {
                ZoomableImageView(
                    puzzleData = puzzleData,
                    foundDifferences = foundDifferences,
                    showHint = showHint,
                    gameCompleted = gameCompleted,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    onTransform = { newScale, newOffsetX, newOffsetY ->
                        scale = newScale
                        offsetX = newOffsetX
                        offsetY = newOffsetY
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onImageClick = { x, y, imageSize ->
                        handleImageClick(
                            x = x,
                            y = y,
                            imageSize = imageSize,
                            puzzleData = puzzleData,
                            foundDifferences = foundDifferences,
                            onDifferenceFound = { difference, clickX, clickY ->
                                foundDifferences = foundDifferences + FoundDifference(
                                    id = difference.id,
                                    x = clickX,
                                    y = clickY
                                )
                                showHint = null
                            },
                            onWrongClick = { wrongClicks++ }
                        )
                    },
                    onImageSizeChanged = { imageSize = it }
                )
            }

            ViewMode.ZONES_LIST -> {
                ZonesListView(
                    zoneHints = zoneHints,
                    foundDifferences = foundDifferences,
                    puzzleData = puzzleData,
                    hintsUsed = hintsUsed,
                    onZoneHintUsed = { zone ->
                        hintsUsed++
                        // Find a difference in this zone and show hint
                        val zoneDifferences = puzzleData.differences.filter { diff ->
                            getZoneForCoordinates(diff.x, diff.y) == zone.zone &&
                                    !foundDifferences.any { found -> found.id == diff.id }
                        }
                        if (zoneDifferences.isNotEmpty()) {
                            showHint = zoneDifferences.first()
                        }
                    }
                )
            }
        }

        // Progress and status
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            LinearProgressIndicator(
                progress = foundDifferences.size.toFloat() / puzzleData.totalDifferences,
                modifier = Modifier.fillMaxWidth(),
                color = Color.Green,
                trackColor = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Found: ${foundDifferences.size}/${puzzleData.totalDifferences}",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Text(
                    text = "Wrong: $wrongClicks/${puzzleData.gameSettings.maxWrongClicks}",
                    color = if (wrongClicks >= puzzleData.gameSettings.maxWrongClicks * 0.8) Color.Red else Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun EnhancedFindDifferencesHeader(
    difficulty: String,
    round: String,
    timeRemaining: Long,
    foundCount: Int,
    totalDifferences: Int,
    wrongClicks: Int,
    maxWrongClicks: Int,
    viewMode: ViewMode,
    onBack: () -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onHint: () -> Unit,
    canUseHint: Boolean
) {
    Column(modifier = Modifier.statusBarsPadding()) {
        // Top row with basic controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = difficulty.uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = round,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }

            Text(
                text = formatTime(timeRemaining),
                color = if (timeRemaining < 30) Color.Red else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // View mode selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewModeButton(
                    icon = Icons.Default.Landscape,
                    label = "Full",
                    isSelected = viewMode == ViewMode.LANDSCAPE_FULL,
                    onClick = { onViewModeChange(ViewMode.LANDSCAPE_FULL) }
                )
                ViewModeButton(
                    icon = Icons.Default.ZoomIn,
                    label = "Zoom",
                    isSelected = viewMode == ViewMode.PORTRAIT_ZOOMABLE,
                    onClick = { onViewModeChange(ViewMode.PORTRAIT_ZOOMABLE) }
                )
                ViewModeButton(
                    icon = Icons.Default.List,
                    label = "Zones",
                    isSelected = viewMode == ViewMode.ZONES_LIST,
                    onClick = { onViewModeChange(ViewMode.ZONES_LIST) }
                )
            }

            IconButton(
                onClick = onHint,
                enabled = canUseHint
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    "Hint",
                    tint = if (canUseHint) Color.Yellow else Color.Gray
                )
            }
        }
    }
}

@Composable
fun ViewModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.Blue else Color.Gray
        ),
        modifier = Modifier.size(width = 70.dp, height = 36.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = Color.White
            )
            Text(
                text = label,
                fontSize = 8.sp,
                color = Color.White
            )
        }
    }
}
private fun actualImageBounds(imageSize: IntSize): RectF {
    val imageAspect = 1792f / 1024f
    val containerAspect = imageSize.width.toFloat() / imageSize.height.toFloat()

    return if (containerAspect > imageAspect) {
        // Container wider -> image uses full HEIGHT, pillarboxes L/R
        val h = imageSize.height.toFloat()
        val w = h * imageAspect
        val x0 = (imageSize.width - w) / 2f
        RectF(x0, 0f, x0 + w, h)
    } else {
        // Container taller -> image uses full WIDTH, letterboxes T/B
        val w = imageSize.width.toFloat()
        val h = w / imageAspect
        val y0 = (imageSize.height - h) / 2f
        RectF(0f, y0, w, y0 + h)
    }
}

private fun toFullImagePercent(x: Double, y: Double, side: String?): Pair<Double, Double> =
    when (side?.lowercase()) {
        "right" -> 50.0 + x * 0.5 to y   // map 0–100 to 50–100
        "left"  -> x * 0.5 to y          // map 0–100 to 0–50
        else    -> x to y                // already full-image %
    }

@Composable
fun ZoomableImageView(
    puzzleData: FindDifferencesPuzzleData,
    foundDifferences: List<FoundDifference>,
    showHint: DifferenceData?,
    modifier: Modifier = Modifier,
    gameCompleted: Boolean,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onTransform: (Float, Float, Float) -> Unit,
    onImageClick: (Float, Float, IntSize) -> Unit,
    onImageSizeChanged: (IntSize) -> Unit
) {
    val density = LocalDensity.current
    var imgSize by remember { mutableStateOf(IntSize.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)

        val scaledW = imgSize.width * newScale
        val scaledH = imgSize.height * newScale
        val maxX = ((scaledW - imgSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledH - imgSize.height) / 2f).coerceAtLeast(0f)

        val newOffX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
        val newOffY = (offsetY + panChange.y).coerceIn(-maxY, maxY)

        onTransform(newScale, newOffX, newOffY)
    }

    Box(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
            .transformable(transformState)
            .onGloballyPositioned {
                imgSize = it.size
                onImageSizeChanged(it.size)
            }
    ) {
        // Base image
        Image(
            painter = rememberAsyncImagePainter(puzzleData.imageUrl),
            contentDescription = "Find differences puzzle",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Fit
        )

        // Found differences markers
        foundDifferences.forEach { found ->
            val xPx = (found.x).coerceIn(0f, imgSize.width.toFloat())
            val yPx = (found.y).coerceIn(0f, imgSize.height.toFloat())
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (xPx - 15f).toDp() },
                        y = with(density) { (yPx - 15f).toDp() }
                    )
                    .size(30.dp)
                    .background(Color.Green.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.found),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }







        // Hint marker - Updated for both sides
        showHint?.let { hint ->
            if (imgSize != IntSize.Zero) {
                val bounds = actualImageBounds(imgSize)
                val (hx, hy) = toFullImagePercent(showHint.x, showHint.y, showHint.side)
                val hintX = bounds.left + (hx / 100f) * bounds.width()
                val hintY = bounds.top  + (hy / 100f) * bounds.height()

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (hintX - 20f).roundToInt(),
                                (hintY - 20f).roundToInt()
                            )
                        }
                        .size(40.dp)
                        .background(Color.Yellow.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(Icons.Default.Lightbulb, "Hint", tint = Color.Black, modifier = Modifier.size(24.dp))
                }
            }
        }


        // Tap overlay - Updated for both sides
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(gameCompleted, scale, offsetX, offsetY) {
                    detectTapGestures { raw ->
                        if (gameCompleted || imgSize == IntSize.Zero) return@detectTapGestures

                        // Convert screen tap to image coordinates accounting for ContentScale.Fit
                        val displayAspectRatio = imgSize.width.toFloat() / imgSize.height.toFloat()
                        val originalAspectRatio = 1792f / 1024f

                        var imageX: Float
                        var imageY: Float

                        if (displayAspectRatio > originalAspectRatio) {
                            // Display is wider - image has letterboxing
                            val scaledHeight = imgSize.width / originalAspectRatio
                            val letterboxHeight = (imgSize.height - scaledHeight) / 2f

                            imageX = (raw.x - offsetX) / scale
                            imageY = ((raw.y - offsetY) / scale - letterboxHeight).coerceAtLeast(0f)

                            // Convert to percentage of actual image
                            imageX = (imageX / imgSize.width * 100f).coerceIn(0f, 100f)
                            imageY = (imageY / scaledHeight * 100f).coerceIn(0f, 100f)
                        } else {
                            // Display is taller - image has pillarboxing
                            val scaledWidth = imgSize.height * originalAspectRatio
                            val pillarboxWidth = (imgSize.width - scaledWidth) / 2f

                            imageX = ((raw.x - offsetX) / scale - pillarboxWidth).coerceAtLeast(0f)
                            imageY = (raw.y - offsetY) / scale

                            // Convert to percentage of actual image
                            imageX = (imageX / scaledWidth * 100f).coerceIn(0f, 100f)
                            imageY = (imageY / imgSize.height * 100f).coerceIn(0f, 100f)
                        }

                        // Only process clicks within the actual image bounds
                        if (imageX >= 0 && imageX <= 100 && imageY >= 0 && imageY <= 100) {
                            // Convert back to pixel coordinates for the click handler
                            val pixelX = imageX / 100f * imgSize.width
                            val pixelY = imageY / 100f * imgSize.height
                            onImageClick(pixelX, pixelY, imgSize)
                        }
                    }
                }
        )

        // Instructions overlay when not zoomed
        if (scale <= 1.2f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(
                        Color.Black.copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = "Pinch to zoom • Drag to pan • Tap to find differences",
                    color = Color.White,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LandscapeImageView(
    puzzleData: FindDifferencesPuzzleData,
    foundDifferences: List<FoundDifference>,
    showHint: DifferenceData?,
    gameCompleted: Boolean,
    onImageClick: (Float, Float, IntSize) -> Unit,
    onImageSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var imgSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .padding(8.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(puzzleData.imageUrl),
            contentDescription = "Find differences puzzle",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .onGloballyPositioned { coordinates ->
                    imgSize = coordinates.size
                    onImageSizeChanged(coordinates.size)
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (!gameCompleted) {
                            onImageClick(offset.x, offset.y, size)
                        }
                    }
                },
            contentScale = ContentScale.Fit
        )

        // Found differences markers
        foundDifferences.forEach { found ->
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (found.x - 15).toDp() },
                        y = with(density) { (found.y - 15).toDp() }
                    )
                    .size(30.dp)
                    .background(Color.Green.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    "Found",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Hint marker - Updated for both sides
        showHint?.let { hint ->
            if (imgSize != IntSize.Zero) {
                val displayAspectRatio = imgSize.width.toFloat() / imgSize.height.toFloat()
                val originalAspectRatio = 1792f / 1024f

                var hintX: Float
                var hintY: Float

                if (displayAspectRatio > originalAspectRatio) {
                    val scaledHeight = imgSize.width / originalAspectRatio
                    val letterboxHeight = (imgSize.height - scaledHeight) / 2f

                    // Direct mapping - no side conversion needed
                    hintX = (hint.x / 100.0 * imgSize.width).toFloat()
                    hintY = (hint.y / 100.0 * scaledHeight + letterboxHeight).toFloat()
                } else {
                    val scaledWidth = imgSize.height * originalAspectRatio
                    val pillarboxWidth = (imgSize.width - scaledWidth) / 2f

                    // Direct mapping - no side conversion needed
                    hintX = (hint.x / 100.0 * scaledWidth + pillarboxWidth).toFloat()
                    hintY = (hint.y / 100.0 * imgSize.height).toFloat()
                }

                val hintSide = if (hint.x >= 50.0) "RIGHT" else "LEFT"
                Log.d("HintPosition", "🎯 Hint ${hint.id} at (${hint.x}%, ${hint.y}%) [$hintSide] -> ($hintX, $hintY) px")

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (hintX - 20f).toDp() },
                            y = with(density) { (hintY - 20f).toDp() }
                        )
                        .size(40.dp)
                        .background(Color.Yellow.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        "Hint",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }

                LaunchedEffect(hint) {
                    delay(3000)
                    // Clear hint after 3 seconds
                }
            }
        }
    }
}

@Composable
fun ZonesListView(
    zoneHints: List<ZoneHint>,
    foundDifferences: List<FoundDifference>,
    puzzleData: FindDifferencesPuzzleData,
    hintsUsed: Int,
    onZoneHintUsed: (ZoneHint) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Zone-Based Hints",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Use hints to narrow down where to look for differences",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(zoneHints) { zoneHint ->
            ZoneHintCard(
                zoneHint = zoneHint,
                foundDifferences = foundDifferences,
                puzzleData = puzzleData,
                canUseHint = hintsUsed < 3,
                onUseHint = { onZoneHintUsed(zoneHint) }
            )
        }
    }
}

@Composable
fun ZoneHintCard(
    zoneHint: ZoneHint,
    foundDifferences: List<FoundDifference>,
    puzzleData: FindDifferencesPuzzleData,
    canUseHint: Boolean,
    onUseHint: () -> Unit
) {
    val zoneDifferences = puzzleData.differences.filter { diff ->
        getZoneForCoordinates(diff.x, diff.y) == zoneHint.zone
    }
    val foundInZone = foundDifferences.filter { found ->
        zoneDifferences.any { it.id == found.id }
    }
    val remainingInZone = zoneDifferences.size - foundInZone.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (remainingInZone == 0) Color.Green.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f)
        )
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
                    text = zoneHint.zone,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (remainingInZone == 0) {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Complete",
                        tint = Color.Green
                    )
                } else {
                    Text(
                        text = "$remainingInZone left",
                        color = Color.Yellow,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = zoneHint.generalDescription,
                color = Color.White,
                fontSize = 14.sp
            )

            if (remainingInZone > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onUseHint,
                    enabled = canUseHint,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Yellow.copy(alpha = 0.8f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            "Hint",
                            tint = Color.Black
                        )
                        Text(
                            "Get Specific Hint",
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

// Helper functions
private fun generateZoneHints(differences: List<DifferenceData>): List<ZoneHint> {
    val zones = differences.groupBy { getZoneForCoordinates(it.x, it.y) }

    return zones.map { (zone, diffs) ->
        ZoneHint(
            zone = zone,
            generalDescription = "Look carefully at the $zone area - there ${if (diffs.size == 1) "is 1 difference" else "are ${diffs.size} differences"} hidden here.",
            specificHint = diffs.first().hint
        )
    }
}

private fun getZoneForCoordinates(x: Double, y: Double): String {
    return when {
        x < 33 && y < 33 -> "Top-Left Corner"
        x < 66 && y < 33 -> "Top-Center Area"
        y < 33 -> "Top-Right Corner"
        x < 33 && y < 66 -> "Middle-Left Area"
        x < 66 && y < 66 -> "Center Area"
        y < 66 -> "Middle-Right Area"
        x < 33 -> "Bottom-Left Corner"
        x < 66 -> "Bottom-Center Area"
        else -> "Bottom-Right Corner"
    }
}

private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun calculateScore(
    foundCount: Int,
    totalDifferences: Int,
    wrongClicks: Int,
    hintsUsed: Int
): Int {
    val baseScore = (foundCount.toDouble() / totalDifferences * 100).toInt()
    val wrongClickPenalty = wrongClicks * 5
    val hintPenalty = hintsUsed * 10
    return maxOf(0, baseScore - wrongClickPenalty - hintPenalty)
}

// Updated click handler - supports both sides
private fun handleImageClick(
    x: Float,
    y: Float,
    imageSize: IntSize,
    puzzleData: FindDifferencesPuzzleData,
    foundDifferences: List<FoundDifference>,
    onDifferenceFound: (DifferenceData, Float, Float) -> Unit,
    onWrongClick: () -> Unit
) {
    val TAG = "ImageClickHandler"

    Log.d(TAG, "🎯 Click at screen ($x, $y)")
    Log.d(TAG, "📱 Image container size: ${imageSize.width} x ${imageSize.height}")

    // Calculate the actual image dimensions within the container for ContentScale.Fit
    val containerAspect = imageSize.width.toFloat() / imageSize.height.toFloat()
    val originalAspect = 1792f / 1024f // Split-screen image aspect ratio

    Log.d(TAG, "📐 Container aspect: $containerAspect, Original aspect: $originalAspect")


    val bounds = actualImageBounds(imageSize)
    if (x !in bounds.left..bounds.right || y !in bounds.top..bounds.bottom) {
        onWrongClick(); return
    }

    val clickFullX = (x - bounds.left) / bounds.width()  * 100.0
    val clickFullY = (y - bounds.top)  / bounds.height() * 100.0

    val unfound = puzzleData.differences.filter { d -> foundDifferences.none { it.id == d.id } }
    for (d in unfound) {
        val (dx, dy) = toFullImagePercent(d.x, d.y, d.side)
        val dist = hypot(dx - clickFullX, dy - clickFullY)
        if (dist <= d.tolerance) {
            onDifferenceFound(d, x, y)
            return
        }
    }
    onWrongClick()
}
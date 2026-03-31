// WaldoPuzzleScreenWrapper.kt - Enhanced Where's Waldo Style Puzzle with Dual Detection
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.pow

// Enhanced data classes for Waldo puzzle with dual detection
data class WaldoPuzzleData(
    val puzzleId: String,
    val theme: String,
    val description: String,
    val imageUrl: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val gridConfig: WaldoGridConfig,
    val hiddenObjects: List<WaldoHiddenObject>,
    val totalObjects: Int,
    val timeLimit: Long,
    val gameSettings: WaldoGameSettings,
    val imageStatus: String,
    val generationMethod: String
)

data class WaldoGridConfig(
    val rows: Int,
    val cols: Int,
    val totalCells: Int
)

data class WaldoHiddenObject(
    val id: Int,
    val name: String,
    val description: String,
    // Grid-based coordinates
    val gridCell: Int,
    val gridRow: Int?,
    val gridCol: Int?,
    // Percentage-based coordinates
    val xPercent: Float?,
    val yPercent: Float?,
    // Detection metadata
    val confidence: String,
    val location: String,
    val hint: String,
    val difficulty: String,
    val detectionMethod: String?,
    // Game state
    var found: Boolean = false,
    var foundAt: Long? = null
)

data class WaldoGameSettings(
    val allowHints: Boolean,
    val showProgress: Boolean,
    val highlightFound: Boolean,
    val gridInteraction: Boolean
)

data class WaldoFoundObject(
    val id: Int,
    val name: String,
    val x: Float, // Pixel coordinates
    val y: Float,
    val foundAt: Long = System.currentTimeMillis(),
    val gridCell: Int,
    val detectionMethod: String
)

enum class WaldoViewMode {
    ZOOMABLE_VIEW,
    OBJECTS_LIST
}

@Composable
fun WaldoPuzzleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "WaldoPuzzleWrapper"
    Log.d(TAG, "🔍 Loading enhanced Waldo puzzle: ${currentPuzzle.puzzleId}")

    // State for puzzle data
    var puzzleData by remember { mutableStateOf<WaldoPuzzleData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Parse puzzle data on first composition
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            Log.d(TAG, "📄 Raw question data: ${currentPuzzle.question}")

            val questionJson = JSONObject(currentPuzzle.question ?: "{}")
            Log.d(TAG, "📋 Question JSON keys: ${questionJson.keys().asSequence().toList()}")

            // Extract basic puzzle information
            val puzzleId = questionJson.optString("puzzleId", currentPuzzle.puzzleId)
            val theme = questionJson.optString("theme", "Find the objects")
            val description = questionJson.optString("description", "")
            val imageUrl = questionJson.optString("imageUrl", "")
            val imageWidth = questionJson.optInt("imageWidth", 1024)
            val imageHeight = questionJson.optInt("imageHeight", 1024)
            val totalObjects = questionJson.optInt("totalObjects", 1)
            val timeLimit = questionJson.optLong("timeLimit", 120000)
            val imageStatus = questionJson.optString("imageStatus", "uploaded")
            val generationMethod = questionJson.optString("generationMethod", "dalle_with_vision_detection")

            Log.d(TAG, "🖼️ Parsed image URL: '$imageUrl'")
            Log.d(TAG, "🎯 Theme: '$theme'")
            Log.d(TAG, "📊 Total objects: $totalObjects")
            Log.d(TAG, "🔧 Generation method: $generationMethod")

            // Validate image readiness
            if (imageUrl.isEmpty() || imageUrl == "null" || imageUrl.contains("PLACEHOLDER")) {
                Log.e(TAG, "❌ Image not ready yet: '$imageUrl'")
                errorMessage = "Image is still being generated. Please try again in a moment."
                hasError = true
                isLoading = false
                return@LaunchedEffect
            }

            if (!imageUrl.startsWith("http")) {
                Log.e(TAG, "❌ Invalid image URL format: '$imageUrl'")
                errorMessage = "Invalid image URL. Please try again."
                hasError = true
                isLoading = false
                return@LaunchedEffect
            }

            // Parse grid configuration
            val gridConfigJson = questionJson.optJSONObject("gridConfig")
            val gridConfig = gridConfigJson?.let {
                WaldoGridConfig(
                    rows = it.optInt("rows", 6),
                    cols = it.optInt("cols", 8),
                    totalCells = it.optInt("totalCells", 48)
                )
            } ?: WaldoGridConfig(6, 8, 48) // Default grid

            Log.d(TAG, "🔢 Grid config: ${gridConfig.cols}x${gridConfig.rows} (${gridConfig.totalCells} cells)")

            // Parse hidden objects with enhanced coordinates
            val hiddenObjectsArray = questionJson.optJSONArray("hiddenObjects") ?: JSONArray()
            val hiddenObjects = mutableListOf<WaldoHiddenObject>()

            for (i in 0 until hiddenObjectsArray.length()) {
                val objJson = hiddenObjectsArray.getJSONObject(i)

                // Extract both grid and percentage coordinates
                val gridCell = objJson.optInt("gridCell", 0)
                val gridRow = if (objJson.has("gridRow")) objJson.optInt("gridRow") else null
                val gridCol = if (objJson.has("gridCol")) objJson.optInt("gridCol") else null
                val xPercent = if (objJson.has("xPercent")) objJson.optDouble("xPercent").toFloat() else null
                val yPercent = if (objJson.has("yPercent")) objJson.optDouble("yPercent").toFloat() else null
                val detectionMethod = objJson.optString("detectionMethod", "grid_only")

                val hiddenObject = WaldoHiddenObject(
                    id = objJson.optInt("id", i + 1),
                    name = objJson.optString("name", "Unknown object"),
                    description = objJson.optString("description", ""),
                    gridCell = gridCell,
                    gridRow = gridRow,
                    gridCol = gridCol,
                    xPercent = xPercent,
                    yPercent = yPercent,
                    confidence = objJson.optString("confidence", "medium"),
                    location = objJson.optString("location", ""),
                    hint = objJson.optString("hint", "Look carefully"),
                    difficulty = objJson.optString("difficulty", "medium"),
                    detectionMethod = detectionMethod,
                    found = objJson.optBoolean("found", false),
                    foundAt = if (objJson.isNull("foundAt")) null else objJson.optLong("foundAt")
                )

                hiddenObjects.add(hiddenObject)

                // Enhanced logging for dual detection
                Log.d(TAG, "🎯 Object ${hiddenObject.id}: ${hiddenObject.name}")
                Log.d(TAG, "  📍 Grid: cell=$gridCell, row=$gridRow, col=$gridCol")
                Log.d(TAG, "  📐 Percentage: x=${xPercent}%, y=${yPercent}%")
                Log.d(TAG, "  🔍 Detection method: $detectionMethod")
                Log.d(TAG, "  📊 Confidence: ${hiddenObject.confidence}")
                Log.d(TAG, "  🎪 Difficulty: ${hiddenObject.difficulty}")
                Log.d(TAG, "  💡 Hint: ${hiddenObject.hint}")
                Log.d(TAG, "  📍 Location: ${hiddenObject.location}")
            }

            // Parse game settings
            val gameSettingsJson = questionJson.optJSONObject("gameSettings") ?: JSONObject()
            val gameSettings = WaldoGameSettings(
                allowHints = gameSettingsJson.optBoolean("allowHints", true),
                showProgress = gameSettingsJson.optBoolean("showProgress", true),
                highlightFound = gameSettingsJson.optBoolean("highlightFound", true),
                gridInteraction = gameSettingsJson.optBoolean("gridInteraction", true)
            )

            // Create puzzle data
            puzzleData = WaldoPuzzleData(
                puzzleId = puzzleId,
                theme = theme,
                description = description,
                imageUrl = imageUrl,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                gridConfig = gridConfig,
                hiddenObjects = hiddenObjects,
                totalObjects = totalObjects,
                timeLimit = timeLimit,
                gameSettings = gameSettings,
                imageStatus = imageStatus,
                generationMethod = generationMethod
            )

            Log.d(TAG, "✅ Successfully parsed enhanced Waldo puzzle:")
            Log.d(TAG, "  Image URL: $imageUrl")
            Log.d(TAG, "  Theme: $theme")
            Log.d(TAG, "  Description: $description")
            Log.d(TAG, "  Total objects: $totalObjects")
            Log.d(TAG, "  Time limit: ${timeLimit / 1000}s")
            Log.d(TAG, "  Generation method: $generationMethod")
            Log.d(TAG, "  Grid-based interaction: ${gameSettings.gridInteraction}")

            isLoading = false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing puzzle data: ${e.message}", e)
            Log.e(TAG, "📄 Raw question data: ${currentPuzzle.question}")
            errorMessage = "Failed to load puzzle: ${e.message}"
            hasError = true
            isLoading = false
        }
    }

    when {
        isLoading -> {
            LoadingScreen(message = "Loading enhanced Waldo puzzle...")
        }

        hasError -> {
            WaldoErrorScreen(
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
            WaldoPuzzleScreen(
                puzzleData = puzzleData!!,
                difficulty = currentPuzzle.difficulty ?: "Medium",
                round = "ROUND ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber} of ${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
                onPuzzleComplete = { score, foundCount ->
                    Log.d(TAG, "🎉 Enhanced Waldo puzzle completed! Found: $foundCount/${puzzleData!!.totalObjects}, Score: $score")
                    val isComplete = foundCount >= puzzleData!!.totalObjects
                    handlePuzzleCompletion(isComplete, isComplete, score)
                },
                onBack = onBack
            )
        }
    }
}

@Composable
fun WaldoPuzzleScreen(
    puzzleData: WaldoPuzzleData,
    difficulty: String,
    round: String,
    onPuzzleComplete: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "WaldoPuzzleScreen"

    // Game state
    var hiddenObjects by remember { mutableStateOf(puzzleData.hiddenObjects) }
    var foundObjects by remember { mutableStateOf<List<WaldoFoundObject>>(emptyList()) }
    var wrongClicks by remember { mutableStateOf(0) }
    var hintsUsed by remember { mutableStateOf(0) }
    var timeRemaining by remember { mutableStateOf(puzzleData.timeLimit / 1000) }
    var gameCompleted by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf<WaldoHiddenObject?>(null) }

    // View state
    var viewMode by remember { mutableStateOf(WaldoViewMode.ZOOMABLE_VIEW) }

    val configuration = LocalConfiguration.current

    // Timer effect
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0 && !gameCompleted) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining <= 0 && !gameCompleted) {
            Log.d(TAG, "⏰ Time's up!")
            gameCompleted = true
            val score = calculateWaldoScore(foundObjects.size, puzzleData.totalObjects, wrongClicks, hintsUsed, timeRemaining)
            onPuzzleComplete(score, foundObjects.size)
        }
    }

    // Check completion
    LaunchedEffect(foundObjects.size) {
        if (foundObjects.size >= puzzleData.totalObjects && !gameCompleted) {
            Log.d(TAG, "🎉 All objects found!")
            gameCompleted = true
            val score = calculateWaldoScore(foundObjects.size, puzzleData.totalObjects, wrongClicks, hintsUsed, timeRemaining)
            onPuzzleComplete(score, foundObjects.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header
        WaldoPuzzleHeader(
            difficulty = difficulty,
            round = round,
            timeRemaining = timeRemaining,
            foundCount = foundObjects.size,
            totalObjects = puzzleData.totalObjects,
            wrongClicks = wrongClicks,
            theme = puzzleData.theme,
            description = puzzleData.description,
            viewMode = viewMode,
            onBack = onBack,
            onViewModeChange = { viewMode = it },
            onHint = {
                if (puzzleData.gameSettings.allowHints && hintsUsed < 3) {
                    val unfoundObjects = hiddenObjects.filter { !it.found }
                    if (unfoundObjects.isNotEmpty()) {
                        showHint = unfoundObjects.first()
                        hintsUsed++
                        Log.d(TAG, "💡 Hint used for: ${showHint?.name}, hints used: $hintsUsed/3")
                    }
                }
            },
            canUseHint = puzzleData.gameSettings.allowHints && hintsUsed < 3
        )

        when (viewMode) {
            WaldoViewMode.ZOOMABLE_VIEW -> {
                WaldoZoomableImageView(
                    puzzleData = puzzleData,
                    hiddenObjects = hiddenObjects,
                    foundObjects = foundObjects,
                    showHint = showHint,
                    gameCompleted = gameCompleted,
                    onObjectClick = { x, y, imageSize ->
                        handleEnhancedWaldoObjectClick(
                            x = x,
                            y = y,
                            imageSize = imageSize,
                            puzzleData = puzzleData,
                            hiddenObjects = hiddenObjects,
                            foundObjects = foundObjects,
                            onObjectFound = { obj, clickX, clickY, detectionMethod ->
                                // Update hidden objects list
                                hiddenObjects = hiddenObjects.map { hiddenObj ->
                                    if (hiddenObj.id == obj.id) {
                                        hiddenObj.copy(found = true, foundAt = System.currentTimeMillis())
                                    } else hiddenObj
                                }

                                // Add to found objects list
                                foundObjects = foundObjects + WaldoFoundObject(
                                    id = obj.id,
                                    name = obj.name,
                                    x = clickX,
                                    y = clickY,
                                    gridCell = obj.gridCell,
                                    detectionMethod = detectionMethod
                                )

                                showHint = null

                                Log.d(TAG, "✅ Found Waldo object: ${obj.name}")
                                Log.d(TAG, "  🎯 Detection method: $detectionMethod")
                                Log.d(TAG, "  📍 Grid cell: ${obj.gridCell}")
                                Log.d(TAG, "  📐 Coordinates: ($clickX, $clickY)")
                            },
                            onWrongClick = {
                                wrongClicks++
                                Log.d(TAG, "❌ Wrong click! Total wrong clicks: $wrongClicks")
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            WaldoViewMode.OBJECTS_LIST -> {
                WaldoObjectsListView(
                    hiddenObjects = hiddenObjects,
                    foundObjects = foundObjects,
                    hintsUsed = hintsUsed,
                    onObjectHintUsed = { obj ->
                        hintsUsed++
                        showHint = obj
                        Log.d(TAG, "💡 Hint used from list for: ${obj.name}, hints used: $hintsUsed/3")
                    },
                    canUseHint = puzzleData.gameSettings.allowHints && hintsUsed < 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        // Progress and status bar
        if (puzzleData.gameSettings.showProgress) {
            WaldoProgressBar(
                foundCount = foundObjects.size,
                totalObjects = puzzleData.totalObjects,
                wrongClicks = wrongClicks,
                hintsUsed = hintsUsed
            )
        }
    }
}

@Composable
fun WaldoPuzzleHeader(
    difficulty: String,
    round: String,
    timeRemaining: Long,
    foundCount: Int,
    totalObjects: Int,
    wrongClicks: Int,
    theme: String,
    description: String,
    viewMode: WaldoViewMode,
    onBack: () -> Unit,
    onViewModeChange: (WaldoViewMode) -> Unit,
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
                Text(
                    text = "ENHANCED WALDO",
                    color = Color.Red,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = formatWaldoTime(timeRemaining),
                color = if (timeRemaining < 30) Color.Red else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Theme and description
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = theme,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        // View mode selector and controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WaldoViewModeButton(
                    icon = Icons.Default.ZoomIn,
                    label = "Image",
                    isSelected = viewMode == WaldoViewMode.ZOOMABLE_VIEW,
                    onClick = { onViewModeChange(WaldoViewMode.ZOOMABLE_VIEW) }
                )
                WaldoViewModeButton(
                    icon = Icons.Default.List,
                    label = "Objects",
                    isSelected = viewMode == WaldoViewMode.OBJECTS_LIST,
                    onClick = { onViewModeChange(WaldoViewMode.OBJECTS_LIST) }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$foundCount/$totalObjects",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

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
}

@Composable
fun WaldoViewModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.Red else Color.Gray
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

@Composable
fun WaldoZoomableImageView(
    puzzleData: WaldoPuzzleData,
    hiddenObjects: List<WaldoHiddenObject>,
    foundObjects: List<WaldoFoundObject>,
    showHint: WaldoHiddenObject?,
    gameCompleted: Boolean,
    onObjectClick: (Float, Float, IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)

        val scaledW = imageSize.width * newScale
        val scaledH = imageSize.height * newScale
        val maxX = ((scaledW - imageSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledH - imageSize.height) / 2f).coerceAtLeast(0f)

        val newOffX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
        val newOffY = (offsetY + panChange.y).coerceIn(-maxY, maxY)

        scale = newScale
        offsetX = newOffX
        offsetY = newOffY
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
            .onGloballyPositioned { coordinates ->
                imageSize = coordinates.size
            }
    ) {
        // Base image
        Image(
            painter = rememberAsyncImagePainter(puzzleData.imageUrl),
            contentDescription = "Enhanced Waldo puzzle image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Found objects markers with enhanced positioning
        if (puzzleData.gameSettings.highlightFound) {
            foundObjects.forEach { found ->
                val actualImageBounds = calculateActualImageBounds(imageSize, puzzleData)

                val markerX = actualImageBounds.left + found.x
                val markerY = actualImageBounds.top + found.y

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (markerX - 15).toDp() },
                            y = with(density) { (markerY - 15).toDp() }
                        )
                        .size(30.dp)
                        .background(Color.Green.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        "Found ${found.name}",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Enhanced hint marker with dual positioning
        showHint?.let { hint ->
            if (imageSize != IntSize.Zero) {
                val actualImageBounds = calculateActualImageBounds(imageSize, puzzleData)

                // Use percentage coordinates if available, otherwise fall back to grid
                val (hintX, hintY) = if (hint.xPercent != null && hint.yPercent != null) {
                    val x = actualImageBounds.left + (hint.xPercent / 100f) * actualImageBounds.width
                    val y = actualImageBounds.top + (hint.yPercent / 100f) * actualImageBounds.height
                    Log.d("WaldoHint", "Using percentage coordinates for ${hint.name}: ${hint.xPercent}%, ${hint.yPercent}% -> ($x, $y)")
                    Pair(x, y)
                } else {
                    // Fall back to grid coordinates
                    val row = hint.gridRow ?: (hint.gridCell / puzzleData.gridConfig.cols)
                    val col = hint.gridCol ?: (hint.gridCell % puzzleData.gridConfig.cols)

                    val cellWidth = actualImageBounds.width / puzzleData.gridConfig.cols
                    val cellHeight = actualImageBounds.height / puzzleData.gridConfig.rows

                    val x = actualImageBounds.left + col * cellWidth + cellWidth / 2
                    val y = actualImageBounds.top + row * cellHeight + cellHeight / 2
                    Log.d("WaldoHint", "Using grid coordinates for ${hint.name}: grid cell ${hint.gridCell} (row=$row, col=$col) -> ($x, $y)")
                    Pair(x, y)
                }

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (hintX - 25).toDp() },
                            y = with(density) { (hintY - 25).toDp() }
                        )
                        .size(50.dp)
                        .background(Color.Yellow.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            "Hint for ${hint.name}",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "?",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Auto-hide hint after 5 seconds
                LaunchedEffect(hint) {
                    delay(5000)
                }
            }
        }

        // Enhanced tap overlay for detecting clicks
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(gameCompleted, scale, offsetX, offsetY) {
                    detectTapGestures { rawTap ->
                        if (gameCompleted || imageSize == IntSize.Zero) return@detectTapGestures

                        // Convert screen tap to image coordinates by inverting transform
                        val adjustedX = (rawTap.x - offsetX) / scale
                        val adjustedY = (rawTap.y - offsetY) / scale

                        val actualImageBounds = calculateActualImageBounds(imageSize, puzzleData)

                        // Only process clicks within the actual image bounds
                        if (adjustedX >= actualImageBounds.left &&
                            adjustedX <= actualImageBounds.right &&
                            adjustedY >= actualImageBounds.top &&
                            adjustedY <= actualImageBounds.bottom) {

                            // Convert to image-relative coordinates
                            val imageRelativeX = adjustedX - actualImageBounds.left
                            val imageRelativeY = adjustedY - actualImageBounds.top

                            Log.d("WaldoClick", "📍 Click detected at screen: (${rawTap.x}, ${rawTap.y})")
                            Log.d("WaldoClick", "📍 Adjusted for transform: ($adjustedX, $adjustedY)")
                            Log.d("WaldoClick", "📍 Image relative: ($imageRelativeX, $imageRelativeY)")
                            Log.d("WaldoClick", "📐 Image bounds: $actualImageBounds")

                            onObjectClick(
                                imageRelativeX,
                                imageRelativeY,
                                IntSize(actualImageBounds.width.toInt(), actualImageBounds.height.toInt())
                            )
                        } else {
                            Log.d("WaldoClick", "❌ Click outside image bounds: ($adjustedX, $adjustedY)")
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
                    text = "Pinch to zoom • Drag to pan • Tap to find hidden objects",
                    color = Color.White,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun WaldoObjectsListView(
    hiddenObjects: List<WaldoHiddenObject>,
    foundObjects: List<WaldoFoundObject>,
    hintsUsed: Int,
    onObjectHintUsed: (WaldoHiddenObject) -> Unit,
    canUseHint: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Hidden Objects",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Find all the hidden objects in the scene",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(hiddenObjects) { obj ->
            WaldoObjectCard(
                hiddenObject = obj,
                isFound = obj.found,
                canUseHint = canUseHint,
                onUseHint = { onObjectHintUsed(obj) }
            )
        }
    }
}

@Composable
fun WaldoObjectCard(
    hiddenObject: WaldoHiddenObject,
    isFound: Boolean,
    canUseHint: Boolean,
    onUseHint: () -> Unit
) {
    var isLocationRevealed by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFound) Color.Green.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f)
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
                Column {
                    Text(
                        text = hiddenObject.name.replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (hiddenObject.description.isNotEmpty()) {
                        Text(
                            text = hiddenObject.description,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = hiddenObject.difficulty,
                            color = when (hiddenObject.difficulty) {
                                "easy" -> Color.Green
                                "medium" -> Color.Yellow
                                "hard" -> Color.Red
                                else -> Color.White
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = hiddenObject.confidence,
                            color = Color.Magenta,
                            fontSize = 10.sp
                        )

                        // Show detection method
                        hiddenObject.detectionMethod?.let { method ->
                            Text(
                                text = method,
                                color = Color.Cyan,
                                fontSize = 9.sp
                            )
                        }

                        // Show location details only when revealed or object is found
                        if (isLocationRevealed || isFound) {
                            Text(
                                text = "Grid: ${hiddenObject.gridCell}",
                                color = Color.Cyan,
                                fontSize = 10.sp
                            )

                            // Show percentage coordinates if available
                            if (hiddenObject.xPercent != null && hiddenObject.yPercent != null) {
                                Text(
                                    text = "Pos: ${hiddenObject.xPercent?.toInt()}%,${hiddenObject.yPercent?.toInt()}%",
                                    color = Color.Green,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                if (isFound) {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Found",
                        tint = Color.Green,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Search,
                        "Not found",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (hiddenObject.location.isNotEmpty() && (isLocationRevealed || isFound)) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Location: ${hiddenObject.location}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            if (!isFound) {
                Spacer(modifier = Modifier.height(8.dp))

                // Button row for hint and reveal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Hint button
                    Button(
                        onClick = onUseHint,
                        enabled = canUseHint,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Yellow.copy(alpha = 0.8f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                "Hint",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Hint",
                                color = Color.Black,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Reveal location button
                    Button(
                        onClick = { isLocationRevealed = !isLocationRevealed },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLocationRevealed) Color.Red.copy(alpha = 0.8f) else Color.Blue.copy(alpha = 0.8f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (isLocationRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                if (isLocationRevealed) "Hide Location" else "Reveal Location",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                if (isLocationRevealed) "Hide" else "Reveal",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Show hint text after the buttons if hint is being used
                if (canUseHint) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "💡 ${hiddenObject.hint}",
                        color = Color.Yellow,
                        fontSize = 10.sp,
                        style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    )
                }
            }
        }
    }
}

@Composable
fun WaldoProgressBar(
    foundCount: Int,
    totalObjects: Int,
    wrongClicks: Int,
    hintsUsed: Int
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        LinearProgressIndicator(
            progress = foundCount.toFloat() / totalObjects,
            modifier = Modifier.fillMaxWidth(),
            color = Color.Red,
            trackColor = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Found: $foundCount/$totalObjects",
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = "Wrong: $wrongClicks",
                color = if (wrongClicks > 5) Color.Red else Color.White,
                fontSize = 12.sp
            )
            Text(
                text = "Hints: $hintsUsed/3",
                color = Color.Yellow,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun WaldoErrorScreen(
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
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
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

// Enhanced helper functions
private fun formatWaldoTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun calculateWaldoScore(
    foundCount: Int,
    totalObjects: Int,
    wrongClicks: Int,
    hintsUsed: Int,
    timeRemaining: Long
): Int {
    val baseScore = (foundCount.toDouble() / totalObjects * 100).toInt()
    val wrongClickPenalty = wrongClicks * 3
    val hintPenalty = hintsUsed * 5
    val timeBonus = (timeRemaining / 10).toInt() // Bonus for time remaining
    return maxOf(0, baseScore - wrongClickPenalty - hintPenalty + timeBonus)
}

private fun calculateActualImageBounds(
    containerSize: IntSize,
    puzzleData: WaldoPuzzleData
): androidx.compose.ui.geometry.Rect {
    val imageAspectRatio = puzzleData.imageWidth.toFloat() / puzzleData.imageHeight.toFloat()
    val containerAspectRatio = containerSize.width.toFloat() / containerSize.height.toFloat()

    return if (imageAspectRatio > containerAspectRatio) {
        // Image is wider - use full width, calculate height
        val actualHeight = containerSize.width / imageAspectRatio
        val yOffset = (containerSize.height - actualHeight) / 2
        androidx.compose.ui.geometry.Rect(
            offset = androidx.compose.ui.geometry.Offset(0f, yOffset),
            size = androidx.compose.ui.geometry.Size(containerSize.width.toFloat(), actualHeight)
        )
    } else {
        // Image is taller - use full height, calculate width
        val actualWidth = containerSize.height * imageAspectRatio
        val xOffset = (containerSize.width - actualWidth) / 2
        androidx.compose.ui.geometry.Rect(
            offset = androidx.compose.ui.geometry.Offset(xOffset, 0f),
            size = androidx.compose.ui.geometry.Size(actualWidth, containerSize.height.toFloat())
        )
    }
}

private fun convertPercentageToPixel(
    xPercent: Float,
    yPercent: Float,
    imageSize: IntSize
): androidx.compose.ui.geometry.Offset {
    return androidx.compose.ui.geometry.Offset(
        x = (xPercent / 100f) * imageSize.width,
        y = (yPercent / 100f) * imageSize.height
    )
}

private fun convertPixelToPercentage(
    x: Float,
    y: Float,
    imageSize: IntSize
): Pair<Float, Float> {
    return Pair(
        (x / imageSize.width) * 100f,
        (y / imageSize.height) * 100f
    )
}

private fun getGridCellFromCoordinates(
    x: Float,
    y: Float,
    imageSize: IntSize,
    gridConfig: WaldoGridConfig
): Int {
    val cellWidth = imageSize.width.toFloat() / gridConfig.cols
    val cellHeight = imageSize.height.toFloat() / gridConfig.rows

    val col = (x / cellWidth).toInt().coerceIn(0, gridConfig.cols - 1)
    val row = (y / cellHeight).toInt().coerceIn(0, gridConfig.rows - 1)

    return row * gridConfig.cols + col
}

private fun handleEnhancedWaldoObjectClick(
    x: Float,
    y: Float,
    imageSize: IntSize,
    puzzleData: WaldoPuzzleData,
    hiddenObjects: List<WaldoHiddenObject>,
    foundObjects: List<WaldoFoundObject>,
    onObjectFound: (WaldoHiddenObject, Float, Float, String) -> Unit,
    onWrongClick: () -> Unit
) {
    val TAG = "EnhancedWaldoClick"

    // Convert click coordinates to percentage
    val (clickXPercent, clickYPercent) = convertPixelToPercentage(x, y, imageSize)
    val clickedGridCell = getGridCellFromCoordinates(x, y, imageSize, puzzleData.gridConfig)

    Log.d(TAG, "🎯 Enhanced click analysis:")
    Log.d(TAG, "  📍 Pixel coordinates: ($x, $y)")
    Log.d(TAG, "  📐 Percentage: (${clickXPercent.toInt()}%, ${clickYPercent.toInt()}%)")
    Log.d(TAG, "  🔢 Grid cell: $clickedGridCell")
    Log.d(TAG, "  📏 Image size: $imageSize")

    // Find unfound objects
    val unfoundObjects = hiddenObjects.filter { obj ->
        !obj.found && !foundObjects.any { found -> found.id == obj.id }
    }

    Log.d(TAG, "🔍 Checking ${unfoundObjects.size} unfound objects:")

    for (obj in unfoundObjects) {
        Log.d(TAG, "  🎯 Object ${obj.id}: ${obj.name}")
        Log.d(TAG, "    📍 Grid: cell=${obj.gridCell}, row=${obj.gridRow}, col=${obj.gridCol}")
        Log.d(TAG, "    📐 Percentage: x=${obj.xPercent}%, y=${obj.yPercent}%")
        Log.d(TAG, "    🔍 Detection method: ${obj.detectionMethod}")

        var found = false
        var detectionMethod = ""

        // First, try percentage-based detection if available
        if (obj.xPercent != null && obj.yPercent != null) {
            val xDiff = abs(clickXPercent - obj.xPercent)
            val yDiff = abs(clickYPercent - obj.yPercent)

            // Allow 8% tolerance for percentage-based detection (increased tolerance)
            val percentageTolerance = 8f

            Log.d(TAG, "    📐 Percentage diff: x=${xDiff.toInt()}%, y=${yDiff.toInt()}% (tolerance: ${percentageTolerance.toInt()}%)")

            if (xDiff <= percentageTolerance && yDiff <= percentageTolerance) {
                found = true
                detectionMethod = "percentage_match"
                Log.d(TAG, "    ✅ FOUND via percentage coordinates!")
            }
        }

        // If not found via percentage, try grid-based detection
        if (!found) {
            // Check exact grid cell match
            if (obj.gridCell == clickedGridCell) {
                found = true
                detectionMethod = "grid_exact_match"
                Log.d(TAG, "    ✅ FOUND via exact grid match!")
            } else {
                // Check adjacent grid cells for more forgiving gameplay
                val objRow = obj.gridRow ?: (obj.gridCell / puzzleData.gridConfig.cols)
                val objCol = obj.gridCol ?: (obj.gridCell % puzzleData.gridConfig.cols)
                val clickRow = clickedGridCell / puzzleData.gridConfig.cols
                val clickCol = clickedGridCell % puzzleData.gridConfig.cols

                val rowDiff = abs(objRow - clickRow)
                val colDiff = abs(objCol - clickCol)

                // Allow 2 cells tolerance in any direction
                val gridTolerance = 2

                Log.d(TAG, "    🔢 Grid diff: row=$rowDiff, col=$colDiff (tolerance: $gridTolerance)")

                if (rowDiff <= gridTolerance && colDiff <= gridTolerance) {
                    found = true
                    detectionMethod = "grid_adjacent_match"
                    Log.d(TAG, "    ✅ FOUND via adjacent grid cells!")
                }
            }
        }

        if (found) {
            Log.d(TAG, "🎉 Object found: ${obj.name} via $detectionMethod")
            onObjectFound(obj, x, y, detectionMethod)
            return
        } else {
            Log.d(TAG, "    ❌ No match for ${obj.name}")
        }
    }

    Log.d(TAG, "❌ No objects found near click location")
    onWrongClick()
}
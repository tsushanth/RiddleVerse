// FindObjectScreenWrapper.kt - Updated for Natural Discovery Approach
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import org.json.JSONObject
import org.json.JSONArray
import java.lang.Math.pow
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs

// Enhanced data classes for Natural Discovery approach
data class FindObjectPuzzleData(
    val puzzleId: String,
    val imageUrl: String,
    val theme: String,
    val description: String,
    val instructions: String,
    val totalObjects: Int,
    val timeLimit: Long,
    val objectsToFind: List<DiscoveredObject>,
    val allDiscoveredObjects: List<DiscoveredObject>?, // All objects found by vision
    val gridConfig: GridConfig,
    val gameSettings: ObjectGameSettings,
    val difficulty: String,
    val discoveryMethod: String = "natural_analysis",
    val sceneAnalysis: String? = null
)

data class DiscoveredObject(
    val id: String,
    val name: String,
    val x: Double, // Percentage
    val y: Double, // Percentage
    val tolerance: Double,
    val difficulty: String,
    val hint: String,
    val description: String,
    val confidence: String, // excellent/good/fair
    val size: String, // large/medium/small
    val visibility: String, // clear/partial/unclear
    val gridIndex: Int,
    val gridRow: Int,
    val gridCol: Int,
    val discoveryMethod: String = "natural_analysis",
    val objectTypeName: String? = null, // The type name this object belongs to
    val totalInstancesOfType: Int = 1, // How many instances of this object type exist
    val allValidInstances: List<ObjectInstance>? = null, // All valid instances for this object type
    val isPrimaryInstance: Boolean = false // Is this the primary target instance
)

data class ObjectInstance(
    val id: String,
    val x: Double,
    val y: Double,
    val gridIndex: Int,
    val instanceNumber: Int = 1,
    val confidence: String = "good"
)

data class GridConfig(
    val rows: Int,
    val cols: Int,
    val totalCells: Int
)

data class ObjectGameSettings(
    val clickTolerance: Double,
    val maxWrongClicks: Int,
    val hintSystem: Boolean,
    val scoringSystem: String,
    val gridBasedValidation: Boolean = true
)

data class FoundObject(
    val id: String,
    val name: String,
    val x: Float, // Pixel coordinates
    val y: Float,
    val foundAt: Long = System.currentTimeMillis(),
    val gridIndex: Int? = null,
    val confidence: String? = null,
    val size: String? = null
)

enum class ObjectViewMode {
    LANDSCAPE_FULL,
    PORTRAIT_ZOOMABLE,
    OBJECTS_LIST
}

@Composable
fun FindObjectScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "FindObjectWrapper"
    Log.d(TAG, "🔍 Loading Natural Discovery Find Object puzzle: ${currentPuzzle.puzzleId}")

    // State for puzzle data
    var puzzleData by remember { mutableStateOf<FindObjectPuzzleData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val mediaCache = remember { MediaCacheManager.getInstance(context) }

    var cachedImageUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingMedia by remember { mutableStateOf(false) }


    // Parse puzzle data on first composition
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            Log.d(TAG, "📄 Raw question data: ${currentPuzzle.question}")

            val questionJson = JSONObject(currentPuzzle.question ?: "{}")
            Log.d(TAG, "📋 Question JSON keys: ${questionJson.keys().asSequence().toList()}")

            // Extract basic puzzle information
            val puzzleId = questionJson.optString("puzzleId", currentPuzzle.puzzleId)
            val imageUrl = questionJson.optString("imageUrl", "")
            val theme = questionJson.optString("theme", "Find the objects")
            val description = questionJson.optString("description", "")
            val instructions = questionJson.optString("instructions", "Find all hidden objects in this image")
            val totalObjects = questionJson.optInt("totalObjects", 1)
            val timeLimit = questionJson.optLong("timeLimit", 300000)
            val difficulty = currentPuzzle.difficulty ?: "Medium"
            val discoveryMethod = questionJson.optString("discoveryMethod", "natural_analysis")
            val sceneAnalysis = questionJson.optString("sceneAnalysis", null)

            Log.d(TAG, "🖼️ Parsed image URL: '$imageUrl'")
            Log.d(TAG, "🎯 Discovery method: '$discoveryMethod'")
            Log.d(TAG, "📊 Total objects: $totalObjects")

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
            if (imageUrl.isNotEmpty() && imageUrl.startsWith("http")) {
                isLoadingMedia = true

                // Get cached media URL
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val cached = mediaCache.getCachedMediaUrl(imageUrl)
                        Log.d(TAG, "Using cached image: $cached")

                        withContext(Dispatchers.Main) {
                            cachedImageUrl = cached
                            isLoadingMedia = false
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get cached media, using original URL", e)
                        withContext(Dispatchers.Main) {
                            cachedImageUrl = imageUrl
                            isLoadingMedia = false
                        }
                    }
                }
            } else {
                cachedImageUrl = imageUrl
                isLoadingMedia = false
            }

            // Parse grid configuration
            val gridConfigJson = questionJson.optJSONObject("gridConfig")
            val gridConfig = gridConfigJson?.let {
                GridConfig(
                    rows = it.optInt("rows", 6),
                    cols = it.optInt("cols", 8),
                    totalCells = it.optInt("totalCells", 48)
                )
            } ?: GridConfig(6, 8, 48) // Default grid

            Log.d(TAG, "🔢 Grid config: ${gridConfig.cols}x${gridConfig.rows} (${gridConfig.totalCells} cells)")

            // Parse main objects to find
            val objectsArray = questionJson.optJSONArray("objectsToFind") ?: JSONArray()
            val objectsToFind = mutableListOf<DiscoveredObject>()

            for (i in 0 until objectsArray.length()) {
                val objJson = objectsArray.getJSONObject(i)

                // Parse all valid instances if available
                val allValidInstancesArray = objJson.optJSONArray("allValidInstances")
                val allValidInstances = mutableListOf<ObjectInstance>()

                if (allValidInstancesArray != null) {
                    for (j in 0 until allValidInstancesArray.length()) {
                        val instJson = allValidInstancesArray.getJSONObject(j)
                        allValidInstances.add(
                            ObjectInstance(
                                id = instJson.optString("id", "inst_$j"),
                                x = instJson.optDouble("x", 50.0),
                                y = instJson.optDouble("y", 50.0),
                                gridIndex = instJson.optInt("gridIndex", 0),
                                instanceNumber = instJson.optInt("instanceNumber", j + 1),
                                confidence = instJson.optString("confidence", "good")
                            )
                        )
                    }
                }

                objectsToFind.add(
                    DiscoveredObject(
                        id = objJson.optString("id", i.toString()),
                        name = objJson.optString("name", "Unknown object"),
                        x = objJson.optDouble("x", 50.0),
                        y = objJson.optDouble("y", 50.0),
                        tolerance = objJson.optDouble("tolerance", 15.0),
                        difficulty = objJson.optString("difficulty", "medium"),
                        hint = objJson.optString("hint", "Look carefully"),
                        description = objJson.optString("description", ""),
                        confidence = objJson.optString("confidence", "good"),
                        size = objJson.optString("size", "medium"),
                        visibility = objJson.optString("visibility", "clear"),
                        gridIndex = objJson.optInt("gridIndex", 0),
                        gridRow = objJson.optInt("gridRow", 0),
                        gridCol = objJson.optInt("gridCol", 0),
                        discoveryMethod = objJson.optString("discoveryMethod", "natural_analysis"),
                        objectTypeName = objJson.optString("objectTypeName", null),
                        totalInstancesOfType = objJson.optInt("totalInstancesOfType", 1),
                        allValidInstances = if (allValidInstances.isNotEmpty()) allValidInstances else null,
                        isPrimaryInstance = objJson.optBoolean("isPrimaryInstance", false)
                    )
                )
            }

            // Parse all discovered objects (if available)
            val allInstancesArray = questionJson.optJSONArray("allFoundInstances") ?:
            questionJson.optJSONArray("allDiscoveredObjects")
            val allDiscoveredObjects = mutableListOf<DiscoveredObject>()

            if (allInstancesArray != null) {
                for (i in 0 until allInstancesArray.length()) {
                    val objJson = allInstancesArray.getJSONObject(i)
                    allDiscoveredObjects.add(
                        DiscoveredObject(
                            id = objJson.optString("id", "discovered_$i"),
                            name = objJson.optString("name", "Unknown object"),
                            x = objJson.optDouble("x", 50.0),
                            y = objJson.optDouble("y", 50.0),
                            tolerance = objJson.optDouble("tolerance", 15.0),
                            difficulty = objJson.optString("difficulty", "medium"),
                            hint = objJson.optString("hint", "Look carefully"),
                            description = objJson.optString("description", ""),
                            confidence = objJson.optString("confidence", "good"),
                            size = objJson.optString("size", "medium"),
                            visibility = objJson.optString("visibility", "clear"),
                            gridIndex = objJson.optInt("gridIndex", 0),
                            gridRow = objJson.optInt("gridRow", 0),
                            gridCol = objJson.optInt("gridCol", 0),
                            discoveryMethod = objJson.optString("discoveryMethod", "natural_analysis")
                        )
                    )
                }
            }

            // Parse game settings
            val gameSettingsJson = questionJson.optJSONObject("gameSettings") ?: JSONObject()
            val gameSettings = ObjectGameSettings(
                clickTolerance = gameSettingsJson.optDouble("clickTolerance", 15.0),
                maxWrongClicks = gameSettingsJson.optInt("maxWrongClicks", 15),
                hintSystem = gameSettingsJson.optBoolean("hintSystem", true),
                scoringSystem = gameSettingsJson.optString("scoringSystem", "per_object"),
                gridBasedValidation = gameSettingsJson.optBoolean("gridBasedValidation", true)
            )

            // Create puzzle data
            puzzleData = FindObjectPuzzleData(
                puzzleId = puzzleId,
                imageUrl = cachedImageUrl ?: imageUrl,
                theme = theme,
                description = description,
                instructions = instructions,
                totalObjects = totalObjects,
                timeLimit = timeLimit,
                objectsToFind = objectsToFind,
                allDiscoveredObjects = if (allDiscoveredObjects.isNotEmpty()) allDiscoveredObjects else null,
                gridConfig = gridConfig,
                gameSettings = gameSettings,
                difficulty = difficulty,
                discoveryMethod = discoveryMethod,
                sceneAnalysis = sceneAnalysis
            )

            Log.d(TAG, "✅ Successfully parsed natural discovery puzzle:")
            Log.d(TAG, "  Image URL: $imageUrl")
            Log.d(TAG, "  Discovery method: $discoveryMethod")
            Log.d(TAG, "  Total target objects: $totalObjects")
            Log.d(TAG, "  All discovered objects: ${allDiscoveredObjects.size}")
            Log.d(TAG, "  Theme: $theme")
            Log.d(TAG, "  Time limit: ${timeLimit / 1000}s")
            Log.d(TAG, "  Grid-based validation: ${gameSettings.gridBasedValidation}")

            objectsToFind.forEachIndexed { index, obj ->
                val instanceInfo = if (obj.totalInstancesOfType > 1) {
                    " [${obj.totalInstancesOfType} instances available]"
                } else ""
                Log.d(TAG, "    ${index + 1}. ${obj.name} at (${obj.x}%, ${obj.y}%) [Grid: ${obj.gridIndex} (${obj.gridRow},${obj.gridCol})] - ${obj.confidence} confidence, ${obj.size} size$instanceInfo")

                obj.allValidInstances?.forEachIndexed { instIndex, instance ->
                    Log.d(TAG, "      Instance ${instIndex + 1}: (${instance.x}%, ${instance.y}%) Grid: ${instance.gridIndex}")
                }
            }

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
            LoadingScreen(message = "Loading natural discovery puzzle...")
        }

        hasError -> {
            FoErrorScreen(
                message = errorMessage,
                onRetry = {
                    Log.d(TAG, "🔄 Retrying puzzle load")
                    isLoading = true
                    hasError = false
                },
                onSkip = {
                    Log.d(TAG, "⏭️ Skipping puzzle due to error")
                    handlePuzzleCompletion(false, false, 0)
                }
            )
        }

        puzzleData != null -> {
            FindObjectPuzzleScreen(
                puzzleData = puzzleData!!,
                difficulty = currentPuzzle.difficulty ?: "Medium",
                round = "ROUND ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber} of ${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
                onPuzzleComplete = { score, foundCount ->
                    Log.d(TAG, "🎉 Natural discovery puzzle completed! Found: $foundCount/${puzzleData!!.totalObjects}, Score: $score")
                    val isComplete = foundCount >= puzzleData!!.totalObjects
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
fun FindObjectPuzzleScreen(
    puzzleData: FindObjectPuzzleData,
    difficulty: String,
    round: String,
    onPuzzleComplete: (Int, Int) -> Unit,
    onBack: () -> Unit,
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel
) {
    val TAG = "FindObjectScreen"

    // Game state
    var foundObjects by remember { mutableStateOf<List<FoundObject>>(emptyList()) }
    var wrongClicks by remember { mutableStateOf(0) }
    var hintsUsed by remember { mutableStateOf(0) }
    var timeRemaining by remember { mutableStateOf(puzzleData.timeLimit / 1000) }
    var gameCompleted by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf<DiscoveredObject?>(null) }

    // Mobile optimizations
    var viewMode by remember { mutableStateOf(ObjectViewMode.LANDSCAPE_FULL) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    val configuration = LocalConfiguration.current

    // Detect orientation for initial view mode
    LaunchedEffect(configuration.screenWidthDp) {
        viewMode = if (configuration.screenWidthDp > configuration.screenHeightDp) {
            ObjectViewMode.LANDSCAPE_FULL
        } else {
            ObjectViewMode.PORTRAIT_ZOOMABLE
        }
    }

    // Timer effect
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0 && !gameCompleted) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining <= 0 && !gameCompleted) {
            Log.d(TAG, "⏰ Time's up!")
            gameCompleted = true
            val score = calculateNaturalObjectScore(foundObjects.size, puzzleData.totalObjects, wrongClicks, hintsUsed)
            onPuzzleComplete(score, foundObjects.size)
        }
    }

    // Check completion
    LaunchedEffect(foundObjects.size) {
        if (foundObjects.size >= puzzleData.totalObjects && !gameCompleted) {
            Log.d(TAG, "🎉 All natural objects found!")
            gameCompleted = true
            val score = calculateNaturalObjectScore(foundObjects.size, puzzleData.totalObjects, wrongClicks, hintsUsed)
            onPuzzleComplete(score, foundObjects.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        // Header with natural discovery info
        NaturalFindObjectHeader(
            difficulty = difficulty,
            round = round,
            timeRemaining = timeRemaining,
            foundCount = foundObjects.size,
            totalObjects = puzzleData.totalObjects,
            wrongClicks = wrongClicks,
            maxWrongClicks = puzzleData.gameSettings.maxWrongClicks,
            viewMode = viewMode,
            discoveryMethod = puzzleData.discoveryMethod,
            onBack = onBack,
            onViewModeChange = { newMode ->
                viewMode = newMode
                scale = 1f
                offsetX = 0f
                offsetY = 0f
            },
            onHint = {
                if (puzzleData.gameSettings.hintSystem && hintsUsed < 3) {
                    val unfoundObjects = puzzleData.objectsToFind.filter { obj ->
                        !foundObjects.any { found -> found.id == obj.id }
                    }
                    if (unfoundObjects.isNotEmpty()) {
                        showHint = unfoundObjects.first()
                        hintsUsed++
                    }
                }
            },
            canUseHint = puzzleData.gameSettings.hintSystem && hintsUsed < 3,
            gridConfig = puzzleData.gridConfig
        )

        when (viewMode) {
            ObjectViewMode.LANDSCAPE_FULL -> {
                NaturalObjectLandscapeView(
                    puzzleData = puzzleData,
                    foundObjects = foundObjects,
                    showHint = showHint,
                    gameCompleted = gameCompleted,
                    onImageClick = { x, y, imageSize ->
                        handleNaturalObjectClick(
                            x = x,
                            y = y,
                            imageSize = imageSize,
                            puzzleData = puzzleData,
                            foundObjects = foundObjects,
                            onObjectFound = { obj, clickX, clickY ->
                                val gridIndex = calculateGridIndex(
                                    clickX, clickY, imageSize, puzzleData.gridConfig
                                )
                                foundObjects = foundObjects + FoundObject(
                                    id = obj.id,
                                    name = obj.name,
                                    x = clickX,
                                    y = clickY,
                                    gridIndex = gridIndex,
                                    confidence = obj.confidence,
                                    size = obj.size
                                )
                                showHint = null
                                Log.d(TAG, "✅ Found natural object ${obj.name} at grid cell $gridIndex (${obj.confidence} confidence)")
                            },
                            onWrongClick = { wrongClicks++ }
                        )
                    },
                    onImageSizeChanged = { imageSize = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            ObjectViewMode.PORTRAIT_ZOOMABLE -> {
                ZoomableImage(
                    imageUrl = puzzleData.imageUrl,
                    gameCompleted = gameCompleted,
                    onScaleOffsetChange = { newScale, newOffsetX, newOffsetY ->
                        scale = newScale
                        offsetX = newOffsetX
                        offsetY = newOffsetY
                    },
                    onTapAdjusted = { x, y, imageSize ->
                        handleNaturalObjectClick(
                            x = x,
                            y = y,
                            imageSize = imageSize,
                            puzzleData = puzzleData,
                            foundObjects = foundObjects,
                            onObjectFound = { obj, clickX, clickY ->
                                val gridIndex = calculateGridIndex(
                                    clickX, clickY, imageSize, puzzleData.gridConfig
                                )
                                foundObjects = foundObjects + FoundObject(
                                    id = obj.id,
                                    name = obj.name,
                                    x = clickX,
                                    y = clickY,
                                    gridIndex = gridIndex,
                                    confidence = obj.confidence,
                                    size = obj.size
                                )
                                showHint = null
                                Log.d(TAG, "✅ Found natural object ${obj.name} at grid cell $gridIndex")
                            },
                            onWrongClick = { wrongClicks++ }
                        )
                    },
                    showHint = showHint,
                    foundObjects = foundObjects,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            ObjectViewMode.OBJECTS_LIST -> {
                NaturalObjectsListView(
                    objectsToFind = puzzleData.objectsToFind,
                    allDiscoveredObjects = puzzleData.allDiscoveredObjects,
                    foundObjects = foundObjects,
                    hintsUsed = hintsUsed,
                    sceneAnalysis = puzzleData.sceneAnalysis,
                    onObjectHintUsed = { obj ->
                        hintsUsed++
                        showHint = obj
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        // Compact progress and status (single row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Found: ${foundObjects.size}/${puzzleData.totalObjects}",
                color = RvInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            LinearProgressIndicator(
                progress = foundObjects.size.toFloat() / puzzleData.totalObjects,
                modifier = Modifier.weight(1f),
                color = RvSuccess,
                trackColor = RvOutline
            )
            Text(
                text = "Wrong: $wrongClicks/${puzzleData.gameSettings.maxWrongClicks}",
                color = if (wrongClicks >= puzzleData.gameSettings.maxWrongClicks * 0.8) RvCoralEdge else RvInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun NaturalFindObjectHeader(
    difficulty: String,
    round: String,
    timeRemaining: Long,
    foundCount: Int,
    totalObjects: Int,
    wrongClicks: Int,
    maxWrongClicks: Int,
    viewMode: ObjectViewMode,
    discoveryMethod: String,
    onBack: () -> Unit,
    onViewModeChange: (ObjectViewMode) -> Unit,
    onHint: () -> Unit,
    canUseHint: Boolean,
    gridConfig: GridConfig
) {
    BoxWithConstraints(modifier = Modifier.statusBarsPadding()) {
        val wide = maxWidth >= 560.dp
        val modeButtons: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ObjectViewModeButton(
                    icon = Icons.Default.Landscape,
                    label = "Full",
                    isSelected = viewMode == ObjectViewMode.LANDSCAPE_FULL,
                    onClick = { onViewModeChange(ObjectViewMode.LANDSCAPE_FULL) }
                )
                ObjectViewModeButton(
                    icon = Icons.Default.ZoomIn,
                    label = "Zoom",
                    isSelected = viewMode == ObjectViewMode.PORTRAIT_ZOOMABLE,
                    onClick = { onViewModeChange(ObjectViewMode.PORTRAIT_ZOOMABLE) }
                )
                ObjectViewModeButton(
                    icon = Icons.Default.List,
                    label = "Objects",
                    isSelected = viewMode == ObjectViewMode.OBJECTS_LIST,
                    onClick = { onViewModeChange(ObjectViewMode.OBJECTS_LIST) }
                )
            }
        }
        val hintButton: @Composable () -> Unit = {
            IconButton(
                onClick = onHint,
                enabled = canUseHint,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    stringResource(R.string.hint),
                    tint = if (canUseHint) RvSunEdge else RvInkSoft
                )
            }
        }
        Column {
            // Single compact HUD row: back, title, timer (+ mode buttons and hint when there is room)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = RvInk)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = difficulty.uppercase(),
                        color = RvInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = round,
                        color = RvInkSoft,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                Text(
                    text = formatObjectTime(timeRemaining),
                    color = if (timeRemaining < 30) RvCoralEdge else RvInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (wide) {

                    modeButtons()
                    hintButton()
                } else {
                    hintButton()
                }
            }

            if (!wide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    modeButtons()

                }
            }
        }
    }
}

@Composable
fun ObjectViewModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) RvViolet else RvSurface
        ),
        modifier = Modifier.defaultMinSize(minWidth = 72.dp).heightIn(min = 48.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) RvOnTone else RvInk
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (isSelected) RvOnTone else RvInk
            )
        }
    }
}

@Composable
private fun ZoomableImage(
    imageUrl: String,
    gameCompleted: Boolean,
    // expose current transform to the parent
    onScaleOffsetChange: (Float, Float, Float) -> Unit,
    // taps returned in *image coordinates* (pre-transform)
    onTapAdjusted: (Float, Float, IntSize) -> Unit,
    // overlays
    showHint: DiscoveredObject? = null,
    foundObjects: List<FoundObject> = emptyList(),
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var imgSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var offX by remember { mutableStateOf(0f) }
    var offY by remember { mutableStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)

        val scaledW = imgSize.width * newScale
        val scaledH = imgSize.height * newScale
        val maxX = ((scaledW - imgSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledH - imgSize.height) / 2f).coerceAtLeast(0f)

        val newOffX = (offX + panChange.x).coerceIn(-maxX, maxX)
        val newOffY = (offY + panChange.y).coerceIn(-maxY, maxY)

        scale = newScale
        offX = newOffX
        offY = newOffY
        onScaleOffsetChange(scale, offX, offY)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .padding(8.dp)
            // transform the WHOLE box (image + overlays)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offX
                translationY = offY
            }
            .transformable(transformState)
            .onGloballyPositioned { imgSize = it.size }
    ) {
        // base image
        Image(
            painter = rememberAsyncImagePainter(imageUrl),
            contentDescription = "Natural discovery find objects puzzle",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Fit
        )

        // FOUND markers (positions are in image pixels; Box is already transformed)
        foundObjects.forEach { f ->
            val xPx = (f.x).coerceIn(0f, imgSize.width.toFloat())
            val yPx = (f.y).coerceIn(0f, imgSize.height.toFloat())
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
                    tint = RvOnTone,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // HINT marker (object coordinates are percentages -> convert to image px)
        showHint?.let { hint ->
            if (imgSize != IntSize.Zero) {
                // Calculate hint position accounting for potential cropping
                val displayAspectRatio = imgSize.width.toFloat() / imgSize.height.toFloat()
                val originalAspectRatio = 1.0f // DALL-E generates 1024x1024 (square)

                var hintX = (hint.x / 100.0 * imgSize.width).toFloat()
                var hintY = (hint.y / 100.0 * imgSize.height).toFloat()

                // If display is wider than original (cropped vertically)
                if (displayAspectRatio > originalAspectRatio) {
                    // Image is cropped top/bottom, adjust Y coordinate
                    val cropFactor = displayAspectRatio / originalAspectRatio
                    val visibleHeight = imgSize.height / cropFactor
                    val cropOffset = (imgSize.height - visibleHeight) / 2

                    hintY = (hint.y / 100.0 * visibleHeight + cropOffset).toFloat()

                    // Check if hint is in visible area
                    if (hintY < 0 || hintY > imgSize.height) {
                        Log.d("HintPosition", "⚠️ Hint for ${hint.name} is outside visible area due to cropping")
                        return@let // Don't show hint if it's outside visible area
                    }
                }

                // Main hint marker
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (hintX - 20f).toDp() },
                            y = with(density) { (hintY - 20f).toDp() }
                        )
                        .size(40.dp) // don't multiply by scale; container already scales it
                        .background(Color.Yellow.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = stringResource(R.string.hint),
                        tint = RvInk,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Description tooltip - especially important for multiple instances
                if (hint.description.isNotEmpty()) {
                    val showDescription = hint.totalInstancesOfType > 1 || hint.description.length <= 50

                    if (showDescription) {
                        // Position tooltip to avoid going off screen
                        val tooltipX = (hintX + 45f).coerceAtMost(imgSize.width.toFloat() - 200f)
                        val tooltipY = (hintY - 40f).coerceAtLeast(10f)

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = with(density) { tooltipX.toDp() },
                                    y = with(density) { tooltipY.toDp() }
                                )
                                .background(
                                    RvInk.copy(alpha = 0.9f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .widthIn(max = 200.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = hint.name.replaceFirstChar { it.uppercase() },
                                    color = Color.Yellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = hint.description,
                                    color = RvInk,
                                    fontSize = 10.sp,
                                    maxLines = 3,
                                    lineHeight = 12.sp
                                )

                                // Show instance info if multiple instances exist
                                if (hint.totalInstancesOfType > 1) {
                                    Text(
                                        text = "1 of ${hint.totalInstancesOfType} instances",
                                        color = Color.Cyan,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Object name only (fallback when no description or single instance)
                if (hint.description.isEmpty() || (hint.totalInstancesOfType == 1 && hint.description.length > 50)) {
                    val nameX = (hintX + 45f).coerceAtMost(imgSize.width.toFloat() - 100f)
                    val nameY = (hintY - 15f).coerceAtLeast(10f)

                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { nameX.toDp() },
                                y = with(density) { nameY.toDp() }
                            )
                            .background(
                                RvInk.copy(alpha = 0.8f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = hint.name.replaceFirstChar { it.uppercase() },
                            color = Color.Yellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Auto-hide hint after 4 seconds (increased time for reading description)
                LaunchedEffect(hint) {
                    delay(4000)
                }
            }
        }

        // TAP overlay (convert screen tap -> image coords by inverting current transform)
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(gameCompleted, scale, offX, offY) {
                    detectTapGestures { raw ->
                        if (gameCompleted || imgSize == IntSize.Zero) return@detectTapGestures
                        val adjX = (raw.x - offX) / scale
                        val adjY = (raw.y - offY) / scale
                        if (adjX in 0f..imgSize.width.toFloat() &&
                            adjY in 0f..imgSize.height.toFloat()
                        ) {
                            onTapAdjusted(adjX, adjY, imgSize)
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
                        RvInk.copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = "Pinch to zoom • Drag to pan • Tap objects found by AI vision",
                    color = RvInk,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun NaturalObjectLandscapeView(
    puzzleData: FindObjectPuzzleData,
    foundObjects: List<FoundObject>,
    showHint: DiscoveredObject?,
    gameCompleted: Boolean,
    onImageClick: (Float, Float, IntSize) -> Unit,
    onImageSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .padding(8.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(puzzleData.imageUrl),
            contentDescription = "Natural discovery find objects puzzle",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .onGloballyPositioned { coordinates ->
                    imageSize = coordinates.size
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

        // Found objects markers
        foundObjects.forEach { found ->
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
                    tint = RvOnTone,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Hint marker - adjust for potential image cropping
        showHint?.let { hint ->
            if (imageSize != IntSize.Zero) {
                // Calculate hint position accounting for potential cropping
                val displayAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
                val originalAspectRatio = 1.0f // DALL-E generates 1024x1024 (square)

                var adjustedHintX = (hint.x / 100.0 * imageSize.width).toFloat()
                var adjustedHintY = (hint.y / 100.0 * imageSize.height).toFloat()

                // If display is wider than original (cropped vertically)
                if (displayAspectRatio > originalAspectRatio) {
                    // Image is cropped top/bottom, adjust Y coordinate
                    val cropFactor = displayAspectRatio / originalAspectRatio
                    val visibleHeight = imageSize.height / cropFactor
                    val cropOffset = (imageSize.height - visibleHeight) / 2

                    adjustedHintY = (hint.y / 100.0 * visibleHeight + cropOffset).toFloat()

                    // Check if hint is in visible area
                    if (adjustedHintY < 0 || adjustedHintY > imageSize.height) {
                        Log.d("HintPosition", "⚠️ Hint for ${hint.name} is outside visible area due to cropping")
                        return@let // Don't show hint if it's outside visible area
                    }
                }

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (adjustedHintX - 20).toDp() },
                            y = with(density) { (adjustedHintY - 20).toDp() }
                        )
                        .size(40.dp)
                        .background(Color.Yellow.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        "Hint",
                        tint = RvInk,
                        modifier = Modifier.size(24.dp)
                    )
                }

                LaunchedEffect(hint) {
                    delay(3000)
                }
            }
        }
    }
}

@Composable
fun NaturalObjectsListView(
    objectsToFind: List<DiscoveredObject>,
    allDiscoveredObjects: List<DiscoveredObject>?,
    foundObjects: List<FoundObject>,
    hintsUsed: Int,
    sceneAnalysis: String?,
    onObjectHintUsed: (DiscoveredObject) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Objects to Find",
                color = RvInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "These objects were naturally discovered by AI vision in the scene",
                color = Color.Cyan,
                fontSize = 12.sp
            )

            sceneAnalysis?.let { analysis ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = analysis,
                    color = RvInkSoft,
                    fontSize = 10.sp,
                    maxLines = 2
                )
            }

            allDiscoveredObjects?.let { allObjects ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Total objects discovered: ${allObjects.size} • Selected for puzzle: ${objectsToFind.size}",
                    color = Color.Yellow,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        items(objectsToFind) { obj ->
            NaturalObjectCard(
                discoveredObject = obj,
                isFound = foundObjects.any { it.id == obj.id },
                canUseHint = hintsUsed < 3,
                onUseHint = { onObjectHintUsed(obj) }
            )
        }
    }
}

@Composable
fun NaturalObjectCard(
    discoveredObject: DiscoveredObject,
    isFound: Boolean,
    canUseHint: Boolean,
    onUseHint: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFound) Color.Green.copy(alpha = 0.3f) else RvInkSoft.copy(alpha = 0.2f)
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
                        text = discoveredObject.name.replaceFirstChar { it.uppercase() },
                        color = RvInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${discoveredObject.confidence} confidence",
                            color = when (discoveredObject.confidence) {
                                "excellent" -> Color.Green
                                "good" -> Color.Yellow
                                "fair" -> RvSun // Orange
                                else -> RvInkSoft
                            },
                            fontSize = 10.sp
                        )
                        Text(
                            text = "${discoveredObject.size} size",
                            color = Color.Cyan,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "${discoveredObject.visibility} visibility",
                            color = Color.Magenta,
                            fontSize = 10.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Grid: ${discoveredObject.gridIndex} (${discoveredObject.gridRow}, ${discoveredObject.gridCol})",
                            color = RvInkSoft,
                            fontSize = 10.sp
                        )

                        if (discoveredObject.totalInstancesOfType > 1) {
                            Text(
                                text = "${discoveredObject.totalInstancesOfType} instances available",
                                color = RvSun, // Orange
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (isFound) {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Found",
                        tint = Color.Green
                    )
                } else {
                    Text(
                        text = discoveredObject.difficulty,
                        color = when (discoveredObject.difficulty) {
                            "easy" -> Color.Green
                            "medium" -> Color.Yellow
                            "hard" -> Color.Red
                            else -> RvInk
                        },
                        fontSize = 12.sp
                    )
                }
            }

            if (discoveredObject.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = discoveredObject.description,
                    color = RvInkSoft,
                    fontSize = 12.sp
                )
            }

            if (!isFound) {
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
                            tint = RvInk
                        )
                        Text(
                            "Show Hint",
                            color = RvInk
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FoErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas),
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
                color = RvInk,
                fontSize = 14.sp,
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
                    colors = ButtonDefaults.buttonColors(containerColor = RvInkSoft)
                ) {
                    Text("Skip")
                }
            }
        }
    }
}

// Helper functions
private fun formatObjectTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun calculateNaturalObjectScore(
    foundCount: Int,
    totalObjects: Int,
    wrongClicks: Int,
    hintsUsed: Int
): Int {
    val baseScore = (foundCount.toDouble() / totalObjects * 100).toInt()
    val wrongClickPenalty = wrongClicks * 2 // Reduced penalty for natural discovery
    val hintPenalty = hintsUsed * 3 // Reduced penalty since objects are naturally placed
    return maxOf(0, baseScore - wrongClickPenalty - hintPenalty)
}

private fun calculateGridIndex(
    x: Float,
    y: Float,
    imageSize: IntSize,
    gridConfig: GridConfig
): Int {
    val xPercent = (x / imageSize.width) * 100.0
    val yPercent = (y / imageSize.height) * 100.0

    val col = ((xPercent / 100.0) * gridConfig.cols).toInt().coerceIn(0, gridConfig.cols - 1)
    val row = ((yPercent / 100.0) * gridConfig.rows).toInt().coerceIn(0, gridConfig.rows - 1)

    return row * gridConfig.cols + col
}

private fun handleNaturalObjectClick(
    x: Float,
    y: Float,
    imageSize: IntSize,
    puzzleData: FindObjectPuzzleData,
    foundObjects: List<FoundObject>,
    onObjectFound: (DiscoveredObject, Float, Float) -> Unit,
    onWrongClick: () -> Unit
) {
    val xPercent = (x / imageSize.width) * 100.0
    val yPercent = (y / imageSize.height) * 100.0

    // Get object types that haven't been found yet
    val unfoundObjectTypes = puzzleData.objectsToFind.filter { obj ->
        !foundObjects.any { found ->
            found.name.lowercase() == obj.name.lowercase() ||
                    found.name.lowercase() == (obj.objectTypeName ?: obj.name).lowercase()
        }
    }

    val clickedGridIndex = calculateGridIndex(x, y, imageSize, puzzleData.gridConfig)

    Log.d("NaturalObjectClick", "Click at ($xPercent%, $yPercent%) -> Grid: $clickedGridIndex")
    Log.d("NaturalObjectClick", "Checking ${unfoundObjectTypes.size} unfound object types")

    // Store all potential matches with their distances
    data class ObjectMatch(
        val obj: DiscoveredObject,
        val instance: ObjectInstance,
        val distance: Double,
        val matchType: String // "grid" or "distance"
    )

    val potentialMatches = mutableListOf<ObjectMatch>()

    for (obj in unfoundObjectTypes) {
        val objectTypeName = obj.objectTypeName ?: obj.name
        Log.d("NaturalObjectClick", "Checking object type: $objectTypeName (${obj.totalInstancesOfType} instances)")

        // Check all valid instances for this object type
        val instancesToCheck = obj.allValidInstances ?: listOf(
            ObjectInstance(
                id = obj.id,
                x = obj.x,
                y = obj.y,
                gridIndex = obj.gridIndex,
                instanceNumber = 1,
                confidence = obj.confidence
            )
        )

        for ((instIndex, instance) in instancesToCheck.withIndex()) {
            // Calculate distance first
            val distance = sqrt(
                pow(instance.x - xPercent, 2.0) +
                        pow(instance.y - yPercent, 2.0)
            )

            // Enhanced tolerance based on object characteristics
            val enhancedTolerance = when {
                obj.size == "large" -> obj.tolerance + 8.0
                obj.size == "small" -> obj.tolerance + 3.0
                else -> obj.tolerance + 5.0
            }.let { baseTol ->
                when (obj.confidence) {
                    "excellent" -> baseTol + 3.0
                    "fair" -> baseTol - 2.0
                    else -> baseTol
                }
            }.let { baseTol ->
                if (obj.totalInstancesOfType > 1) baseTol + 5.0 else baseTol
            }

            // Check grid-based matching
            val gridMatch = if (instance.gridIndex == clickedGridIndex) {
                true
            } else {
                val instRow = instance.gridIndex / puzzleData.gridConfig.cols
                val instCol = instance.gridIndex % puzzleData.gridConfig.cols
                val clickRow = clickedGridIndex / puzzleData.gridConfig.cols
                val clickCol = clickedGridIndex % puzzleData.gridConfig.cols
                abs(instRow - clickRow) <= 1 && abs(instCol - clickCol) <= 1
            }

            val distanceMatch = distance <= enhancedTolerance

            Log.d("NaturalObjectClick", "  Instance ${instIndex + 1}: grid=$gridMatch, distance=$distanceMatch (${distance.toInt()}% <= $enhancedTolerance%)")

            // Add to potential matches if either condition is met
            if (gridMatch || distanceMatch) {
                val matchType = if (gridMatch) "grid" else "distance"
                potentialMatches.add(
                    ObjectMatch(
                        obj = obj,
                        instance = instance,
                        distance = distance,
                        matchType = matchType
                    )
                )
                Log.d("NaturalObjectClick", "    Added as potential match: ${obj.name} (distance: ${distance.toInt()}%, type: $matchType)")
            }
        }
    }

    // Sort by distance (closest first) and pick the best match
    if (potentialMatches.isNotEmpty()) {
        val bestMatch = potentialMatches.minByOrNull { it.distance }!!

        Log.d("NaturalObjectClick", "✅ Best match found: ${bestMatch.obj.name} at distance ${bestMatch.distance.toInt()}% (${bestMatch.matchType} match)")

        // Create a result object that represents this specific instance
        val foundInstance = bestMatch.obj.copy(
            x = bestMatch.instance.x,
            y = bestMatch.instance.y,
            gridIndex = bestMatch.instance.gridIndex,
            gridRow = bestMatch.instance.gridIndex / puzzleData.gridConfig.cols,
            gridCol = bestMatch.instance.gridIndex % puzzleData.gridConfig.cols
        )

        onObjectFound(foundInstance, x, y)
        return
    }

    Log.d("NaturalObjectClick", "❌ No natural object match found")
    onWrongClick()
}
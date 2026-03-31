// AdaptiveImagePuzzleScreen.kt
package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.random.Random
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import org.json.JSONObject
import kotlin.math.roundToInt
// Enhanced AdaptiveImagePuzzleCompletionScreen with Share functionality
// Add these imports at the top of the file
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import android.net.Uri
import java.io.File
import android.provider.MediaStore
import android.content.ContentValues
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Rect
import android.graphics.RectF

// Add these new data classes after your existing ones
data class DragState(
    val isDragging: Boolean = false,
    val dragOffset: Offset = Offset.Zero,
    val startPosition: Offset = Offset.Zero
)

data class EnhancedImagePuzzlePiece(
    val id: Int,
    val correctRow: Int,
    val correctCol: Int,
    val bitmap: Bitmap?,
    val currentPosition: Offset = Offset.Zero,
    val currentRotation: Float = 0f,
    val isPlaced: Boolean = false,
    val isCorrect: Boolean = false,
    val placedInRow: Int = -1,
    val placedInCol: Int = -1,
    val scale: Float = 1f,
    val dragState: DragState = DragState()
)

// Enum for different removal methods
enum class PieceRemovalMethod {
    LONG_PRESS_WITH_BUTTON,  // Long press or click X button
    DRAG_BACK,               // Drag back to pieces panel
    DOUBLE_TAP               // Double tap to remove
}

@Composable
fun ImagePuzzleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "ImagePuzzleWrapper"
    Log.d(TAG, "🧩 Loading AI Image Puzzle: ${currentPuzzle.puzzleId}")
    val context = LocalContext.current
    val mediaCache = remember { MediaCacheManager.getInstance(context) }

    // State for puzzle data
    var puzzleDataString by remember { mutableStateOf("") }
    var correctAnswer by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoadingMedia by remember { mutableStateOf(false) }
    var cachedImageUrl by remember { mutableStateOf<String?>(null) }

    // Helper function to generate fallback image URL
    fun generateFallbackImageUrl(theme: String, description: String, width: Int, height: Int): String {
        val fallbackUrls = listOf(
            "https://picsum.photos/$width/$height?random=${System.currentTimeMillis()}",
            "https://picsum.photos/$width/$height?grayscale&random=${System.currentTimeMillis()}",
            "https://via.placeholder.com/${width}x${height}/4F46E5/FFFFFF?text=Puzzle+Image"
        )

        // Try to use a themed URL if possible
        return when {
            theme.contains("lion", true) -> "https://picsum.photos/$width/$height?random=1"
            theme.contains("nature", true) -> "https://picsum.photos/$width/$height?random=2"
            theme.contains("city", true) -> "https://picsum.photos/$width/$height?random=3"
            else -> fallbackUrls.random()
        }
    }

    // Helper function to generate fallback puzzle metadata
    fun generateFallbackPuzzleMetadata(gridSize: Int, pieceSize: Int): JSONObject {
        return JSONObject().apply {
            val piecesArray = org.json.JSONArray()

            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    val pieceId = row * gridSize + col
                    val piece = JSONObject().apply {
                        put("id", pieceId)
                        put("row", row)
                        put("col", col)
                        put("correctPosition", JSONObject().apply {
                            put("x", col * pieceSize)
                            put("y", row * pieceSize)
                        })
                        put("sourceRect", JSONObject().apply {
                            put("x", col * pieceSize)
                            put("y", row * pieceSize)
                            put("width", pieceSize)
                            put("height", pieceSize)
                        })
                        put("isCorner", (row == 0 || row == gridSize - 1) && (col == 0 || col == gridSize - 1))
                        put("isEdge", row == 0 || row == gridSize - 1 || col == 0 || col == gridSize - 1)

                        // Adjacent pieces
                        val adjacentPieces = org.json.JSONArray()
                        if (row > 0) adjacentPieces.put((row - 1) * gridSize + col) // Top
                        if (row < gridSize - 1) adjacentPieces.put((row + 1) * gridSize + col) // Bottom
                        if (col > 0) adjacentPieces.put(row * gridSize + (col - 1)) // Left
                        if (col < gridSize - 1) adjacentPieces.put(row * gridSize + (col + 1)) // Right
                        put("adjacentPieces", adjacentPieces)
                    }
                    piecesArray.put(piece)
                }
            }

            put("pieces", piecesArray)
            put("gridSize", gridSize)
            put("pieceSize", pieceSize)
            put("totalPieces", gridSize * gridSize)
            put("cornerPieces", 4)
            put("edgePieces", (gridSize - 2) * 4)
            put("centerPieces", (gridSize - 2) * (gridSize - 2))
            put("generatedFallback", true)
        }
    }

    // Helper function to generate answer from puzzle data
    fun generateAnswerFromPuzzleData(puzzleMetadata: JSONObject?, totalPieces: Int, gridSize: Int, snapTolerance: Double): String {
        return JSONObject().apply {
            put("totalPieces", totalPieces)
            put("gridSize", gridSize)
            put("maxScore", totalPieces * 10)
            put("completionCriteria", JSONObject().apply {
                put("allPiecesPlaced", true)
                put("correctPositions", true)
                put("tolerance", snapTolerance)
            })

            // Generate correct assembly from metadata if available
            val piecesArray = puzzleMetadata?.optJSONArray("pieces")
            if (piecesArray != null && piecesArray.length() > 0) {
                val correctAssemblyArray = org.json.JSONArray()
                for (i in 0 until piecesArray.length()) {
                    val piece = piecesArray.getJSONObject(i)
                    val assemblyPiece = JSONObject().apply {
                        put("pieceId", piece.getInt("id"))
                        put("correctRow", piece.getInt("row"))
                        put("correctCol", piece.getInt("col"))
                        put("correctPosition", piece.getJSONObject("correctPosition"))
                    }
                    correctAssemblyArray.put(assemblyPiece)
                }
                put("correctAssembly", correctAssemblyArray)
            } else {
                // Generate simple correct assembly
                val correctAssemblyArray = org.json.JSONArray()
                for (row in 0 until gridSize) {
                    for (col in 0 until gridSize) {
                        val pieceId = row * gridSize + col
                        val assemblyPiece = JSONObject().apply {
                            put("pieceId", pieceId)
                            put("correctRow", row)
                            put("correctCol", col)
                            put("correctPosition", JSONObject().apply {
                                put("x", col * (1024 / gridSize))
                                put("y", row * (1024 / gridSize))
                            })
                        }
                        correctAssemblyArray.put(assemblyPiece)
                    }
                }
                put("correctAssembly", correctAssemblyArray)
            }

            // Add bonus scoring
            put("bonusScore", JSONObject().apply {
                put("timeBonus", 27000)
                put("efficiencyBonus", 50)
            })
            put("puzzleType", "image_assembly")
            put("scoringMethod", "piece_placement")
            put("difficultyModifier", 1.2)
        }.toString()
    }

    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            Log.d(TAG, "📋 Raw question data: ${currentPuzzle.question}")
            Log.d(TAG, "📋 Raw answer data: ${currentPuzzle.answer}")
            Log.d(TAG, "📋 Is custom puzzle flow: ${viewModel.isCustomPuzzleFlow}")

            val questionJsonString = currentPuzzle.question ?: "{}"
            Log.d(TAG, "📋 Question string: $questionJsonString")

            // Enhanced parsing logic to handle custom vs general puzzles
            val puzzleDataJson = try {
                val questionJson = JSONObject(questionJsonString)

                // Check if this is a custom puzzle (nested structure)
                when {
                    // Custom puzzle format - question contains JSON string
                    viewModel.isCustomPuzzleFlow && questionJson.has("question") -> {
                        Log.d(TAG, "📋 Detected CUSTOM puzzle format - extracting nested question")
                        val innerQuestionString = questionJson.getString("question")
                        Log.d(TAG, "📋 Inner question string: $innerQuestionString")
                        JSONObject(innerQuestionString)
                    }

                    // Custom puzzle format - direct puzzle data in question field
                    viewModel.isCustomPuzzleFlow && questionJson.has("puzzleId") -> {
                        Log.d(TAG, "📋 Detected CUSTOM puzzle format - direct data")
                        questionJson
                    }

                    // General puzzle format - direct format
                    !viewModel.isCustomPuzzleFlow && questionJson.has("puzzleId") -> {
                        Log.d(TAG, "📋 Detected GENERAL puzzle format - direct data")
                        questionJson
                    }

                    // Fallback: try to detect format by checking for known custom puzzle fields
                    questionJson.has("question") -> {
                        Log.d(TAG, "📋 Fallback: detected nested format")
                        val innerQuestionString = questionJson.getString("question")
                        JSONObject(innerQuestionString)
                    }

                    // Default: use as-is
                    else -> {
                        Log.d(TAG, "📋 Using question JSON as-is")
                        questionJson
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to parse question JSON: ${e.message}")
                // Create a minimal valid JSON to prevent crashes
                JSONObject().apply {
                    put("puzzleId", currentPuzzle.puzzleId)
                    put("type", "image_puzzle")
                    put("theme", "Image Puzzle")
                    put("gridSize", 3)
                    put("totalPieces", 9)
                    put("imageUrl", "")
                    put("timeLimit", 300000)
                }
            }

            Log.d(TAG, "📋 Final puzzle JSON keys: ${puzzleDataJson.keys().asSequence().toList()}")

            // Extract puzzle information
            val puzzleId = puzzleDataJson.optString("puzzleId", currentPuzzle.puzzleId)
            val puzzleType = puzzleDataJson.optString("type", "image_puzzle")
            val theme = puzzleDataJson.optString("theme", "Image Puzzle")
            val description = puzzleDataJson.optString("description", "Assemble the image by arranging puzzle pieces")

            // Enhanced image URL extraction
            var imageUrl = puzzleDataJson.optString("imageUrl", "")
            if (imageUrl.isEmpty() || imageUrl == "null") {
                imageUrl = puzzleDataJson.optString("image_url", "")
                if (imageUrl.isEmpty()) {
                    imageUrl = puzzleDataJson.optString("url", "")
                }
            }

            val imageWidth = puzzleDataJson.optInt("imageWidth", 1024)
            val imageHeight = puzzleDataJson.optInt("imageHeight", 1024)

            Log.d(TAG, "🖼️ Original image URL: '$imageUrl'")

            // Simple image URL validation and fallback handling
            var finalImageUrl = imageUrl
            var usedFallback = false

            when {
                imageUrl.isNotEmpty() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) -> {
                    // Get cached version of the image
                    finalImageUrl = try {
                        mediaCache.getCachedMediaUrl(imageUrl)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cache error for $imageUrl, using original: ${e.message}")
                        imageUrl
                    }
                }
                imageUrl.startsWith("file://") || imageUrl.startsWith("/") -> {
                    finalImageUrl = imageUrl // Keep local paths as-is
                }
                else -> {
                    Log.w(TAG, "Invalid/empty image URL, using fallback")
                    finalImageUrl = generateFallbackImageUrl(theme, description, imageWidth, imageHeight)
                    usedFallback = true
                }
            }

            val gridSize = puzzleDataJson.optInt("gridSize", 3)
            val totalPieces = puzzleDataJson.optInt("totalPieces", gridSize * gridSize)
            val pieceSize = puzzleDataJson.optInt("pieceSize", imageWidth / gridSize)
            val timeLimit = puzzleDataJson.optLong("timeLimit", 300000)

            Log.d(TAG, "🖼️ Final image URL: '$finalImageUrl'")
            Log.d(TAG, "📊 Grid size: ${gridSize}x${gridSize} = $totalPieces pieces")
            Log.d(TAG, "🎨 Theme: $theme")
            if (usedFallback) {
                Log.d(TAG, "🔄 Used fallback image due to invalid/missing original")
            }

            // Enhanced answer parsing for custom vs general puzzles
            val answerJsonString = try {
                when {
                    // Custom puzzle - answer might be in nested format
                    viewModel.isCustomPuzzleFlow && currentPuzzle.answer?.isNotEmpty() == true -> {
                        Log.d(TAG, "📋 Using CUSTOM puzzle answer")
                        val answerJson = JSONObject(currentPuzzle.answer!!)

                        // Check if answer is nested
                        if (answerJson.has("answer")) {
                            Log.d(TAG, "📋 Extracting nested answer from custom puzzle")
                            answerJson.getString("answer")
                        } else {
                            Log.d(TAG, "📋 Using direct answer from custom puzzle")
                            currentPuzzle.answer!!
                        }
                    }

                    // General puzzle - direct answer
                    !viewModel.isCustomPuzzleFlow && currentPuzzle.answer?.isNotEmpty() == true -> {
                        Log.d(TAG, "📋 Using GENERAL puzzle answer")
                        currentPuzzle.answer!!
                    }

                    // Fallback: extract from question if available
                    questionJsonString.contains("\"answer\"") -> {
                        Log.d(TAG, "📋 Extracting answer from question JSON")
                        val outerJson = JSONObject(questionJsonString)
                        outerJson.optString("answer", "{}")
                    }

                    // Generate answer from puzzle data
                    else -> {
                        Log.d(TAG, "📋 Generating answer from puzzle data")
                        generateAnswerFromPuzzleData(
                            puzzleDataJson.optJSONObject("puzzleMetadata"),
                            totalPieces,
                            gridSize,
                            20.0
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Could not parse answer data: ${e.message}")
                generateAnswerFromPuzzleData(
                    puzzleDataJson.optJSONObject("puzzleMetadata"),
                    totalPieces,
                    gridSize,
                    20.0
                )
            }

            // Parse puzzle metadata
            val puzzleMetadataJson = puzzleDataJson.optJSONObject("puzzleMetadata")
                ?: generateFallbackPuzzleMetadata(gridSize, pieceSize)

            // Create puzzle data for screen
            val puzzleDataForScreen = JSONObject().apply {
                put("puzzleId", puzzleId)
                put("type", puzzleType)
                put("imageUrl", finalImageUrl)
                put("theme", theme)
                put("description", description)
                put("imageWidth", imageWidth)
                put("imageHeight", imageHeight)
                put("gridSize", gridSize)
                put("totalPieces", totalPieces)
                put("pieceSize", pieceSize)
                put("timeLimit", timeLimit)
                put("difficulty", currentPuzzle.difficulty ?: "Easy")
                put("puzzleMetadata", puzzleMetadataJson)

                // Add debug info
                put("isCustomPuzzle", viewModel.isCustomPuzzleFlow)
                put("parsedFrom", if (viewModel.isCustomPuzzleFlow) "custom" else "general")
                put("usedFallback", usedFallback)
            }

            puzzleDataString = puzzleDataForScreen.toString()
            correctAnswer = answerJsonString

            Log.d(TAG, "✅ Successfully parsed ${if (viewModel.isCustomPuzzleFlow) "CUSTOM" else "GENERAL"} image puzzle:")
            Log.d(TAG, "  Final Image URL: $finalImageUrl")
            Log.d(TAG, "  Grid: ${gridSize}x${gridSize}")
            Log.d(TAG, "  Total pieces: $totalPieces")
            Log.d(TAG, "  Theme: $theme")
            Log.d(TAG, "  Used fallback: $usedFallback")

            isLoading = false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing puzzle data: ${e.message}", e)
            Log.e(TAG, "🔄 Raw question data: ${currentPuzzle.question}")
            Log.e(TAG, "🔄 Raw answer data: ${currentPuzzle.answer}")
            Log.e(TAG, "🔄 Is custom puzzle flow: ${viewModel.isCustomPuzzleFlow}")
            errorMessage = "Failed to load puzzle: ${e.message}"
            hasError = true
            isLoading = false
        }
    }



    // Rest of the composable remains the same...
    when {
        isLoading -> {
            AILoadingScreen(message = "Loading image puzzle...")
        }

        hasError -> {
            ImagePuzzleErrorScreen(
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

        puzzleDataString.isNotEmpty() -> {
            AdaptiveImagePuzzleScreen(
                difficulty = currentPuzzle.difficulty ?: "Easy",
                timer = "ROUND ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber} of ${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
                hearts = 3,
                level = "Level 0",
                puzzleData = puzzleDataString,
                correctAnswer = correctAnswer,
                onSubmitAnswer = { isSuccess ->
                    Log.d(TAG, "🎉 Image puzzle completed! Success: $isSuccess")
                    handlePuzzleCompletion(isSuccess, isSuccess, if (isSuccess) 100 else 0)
                },
                fetchNextPuzzle = { score ->
                    Log.d(TAG, "➡️ Fetching next puzzle with score: $score")
                },
                onBack = onBack
            )
        }
    }
}

@Composable
fun ImagePuzzleErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2D3748)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🧩 Puzzle Error",
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
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text(stringResource(R.string.retry), color = Color.White)
                }

                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text(stringResource(R.string.skip), color = Color.White)
                }
            }
        }
    }
}

@Composable
fun AILoadingScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2D3748)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = Color(0xFF10B981),
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = message,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Adaptive image puzzle configuration
data class AdaptiveImagePuzzleConfig(
    val gridSize: Int, // 2x2, 3x3, 4x4
    val allowRotation: Boolean,
    val snapTolerance: Float, // Distance tolerance for snapping (dp)
    val timeLimit: Long, // Time limit in seconds
    val showPreview: Boolean, // Show original image as reference
    val hintSystem: Boolean, // Enable hint system
    val adaptiveComplexity: Boolean, // Adjust complexity based on performance
    val pieceShuffle: Boolean, // Shuffle pieces initially
    val name: String,
    val description: String
)

// Individual puzzle piece data
data class ImagePuzzlePiece(
    val id: Int,
    val correctRow: Int,
    val correctCol: Int,
    val bitmap: Bitmap?, // The cropped piece bitmap
    val currentPosition: Offset = Offset.Zero,
    val currentRotation: Float = 0f,
    val isPlaced: Boolean = false,
    val isCorrect: Boolean = false,
    val placedInRow: Int = -1,
    val placedInCol: Int = -1,
    val scale: Float = 1f
)

// Puzzle data structure
data class AdaptiveImagePuzzleData(
    val puzzleId: String,
    val imageUrl: String,
    val theme: String,
    val description: String,
    val gridSize: Int,
    val totalPieces: Int,
    val timeLimit: Long,
    val pieces: List<ImagePuzzlePiece>,
    val originalBitmap: Bitmap?,
    val config: AdaptiveImagePuzzleConfig,
    val difficulty: String
)

// Grid slot data
data class GridSlot(
    val row: Int,
    val col: Int,
    val position: Offset = Offset.Zero,
    val size: IntSize = IntSize.Zero,
    val occupied: Boolean = false,
    val correctPieceId: Int = -1,
    val currentPieceId: Int = -1
)


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdaptiveImagePuzzleScreen(
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
    val TAG = "AdaptiveImagePuzzle"
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("imagePuzzle"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // Generate adaptive configuration
    val adaptiveConfig = remember(currentDifficultyLevel) {
        generateAdaptiveImagePuzzleConfig(currentDifficultyLevel)
    }

    // Parse puzzle data
    var puzzleDataParsed by remember { mutableStateOf<AdaptiveImagePuzzleData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Game state
    var gameState by remember { mutableStateOf("loading") } // loading, instructions, playing, completed
    var puzzlePieces by remember { mutableStateOf(listOf<ImagePuzzlePiece>()) }
    var gridSlots by remember { mutableStateOf(listOf<GridSlot>()) }
    var selectedPieceId by remember { mutableStateOf(-1) }
    var draggingPiece by remember { mutableStateOf<ImagePuzzlePiece?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var timeRemaining by remember { mutableLongStateOf(0L) }
    var currentScore by remember { mutableStateOf(0) }
    var hintsUsed by remember { mutableStateOf(0) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var showPreview by remember { mutableStateOf(false) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Add removal method state
    var removalMethod by remember { mutableStateOf(PieceRemovalMethod.LONG_PRESS_WITH_BUTTON) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    LaunchedEffect(puzzleData) {
        try {
            Log.d(TAG, "🔄 Parsing image puzzle data...")

            val questionJson = JSONObject(puzzleData)
            val puzzleId = questionJson.optString("puzzleId", "img_puzzle_${System.currentTimeMillis()}")
            val imageUrl = questionJson.optString("imageUrl", "")
            val theme = questionJson.optString("theme", "Image Puzzle")
            val description = questionJson.optString("description", "Assemble the image pieces")
            val gridSize = questionJson.optInt("gridSize", adaptiveConfig.gridSize)
            val totalPieces = questionJson.optInt("totalPieces", gridSize * gridSize)
            val puzzleTimeLimit = questionJson.optLong("timeLimit", adaptiveConfig.timeLimit * 1000)

            // Simple validation - just check if we have an image URL
            if (imageUrl.isEmpty() || imageUrl == "null") {
                throw Exception("No image URL provided for puzzle")
            }

            Log.d(TAG, "📊 Puzzle config: ${gridSize}x${gridSize} = $totalPieces pieces")
            Log.d(TAG, "🖼️ Image URL: $imageUrl")
            Log.d(TAG, "⏱️ Time limit: ${puzzleTimeLimit / 1000}s")

            // Create puzzle manager and load pieces
            val puzzleManager = ImagePuzzleManager(context)
            val pieces = try {
                withTimeout(30000) { // 30 second timeout for piece creation
                    puzzleManager.loadAndProcessImage(imageUrl, gridSize)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "⚠️ Puzzle piece creation timed out")
                null
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Puzzle piece creation failed: ${e.message}")
                null
            }

            if (pieces == null || pieces.isEmpty()) {
                throw Exception("Failed to create puzzle pieces from image")
            }

            // Create puzzle data
            val createdPuzzleData = AdaptiveImagePuzzleData(
                puzzleId = puzzleId,
                imageUrl = imageUrl,
                theme = theme,
                description = description,
                gridSize = gridSize,
                totalPieces = totalPieces,
                timeLimit = puzzleTimeLimit,
                pieces = pieces,
                originalBitmap = null, // Will be loaded separately when needed
                config = adaptiveConfig,
                difficulty = difficulty
            )

            puzzleDataParsed = createdPuzzleData
            puzzlePieces = pieces
            gridSlots = createGridSlots(gridSize)
            timeRemaining = puzzleTimeLimit / 1000
            gameState = "instructions"
            isLoading = false

            Log.d(TAG, "✅ Puzzle created successfully with ${pieces.size} pieces")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading puzzle: ${e.message}", e)
            errorMessage = "Failed to load puzzle: ${e.message}"
            hasError = true
            isLoading = false
        }
    }

    // Timer
    LaunchedEffect(gameState, timeRemaining) {
        if (gameState == "playing" && timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining <= 0 && gameState == "playing") {
            gameState = "completed"
        }
    }

    // Check completion
    LaunchedEffect(puzzlePieces) {
        if (gameState == "playing") {
            val correctPieces = puzzlePieces.count { it.isCorrect }
            if (correctPieces == puzzleDataParsed?.totalPieces) {
                gameState = "completed"
                val timeBonus = calculateTimeBonus(timeRemaining, puzzleDataParsed!!.timeLimit / 1000)
                currentScore = (correctPieces * 10) + timeBonus - (hintsUsed * 5)
            }
        }
    }

    // Record performance for adaptive difficulty
    fun recordAdaptivePerformance(isCorrect: Boolean, completionTime: Long) {
        val performance = DifficultyManager.PlayerPerformance(
            accuracy = if (isCorrect) 1f else 0f,
            averageTime = completionTime / 1000f,
            streakLength = if (isCorrect) 1 else 0,
            livesRemaining = currentHearts,
            gameScore = currentScore,
            difficulty = currentDifficultyLevel.name,
            puzzleType = "imagePuzzle"
        )

        val adaptiveConfig = difficultyManager.recordPerformance(performance)
        adaptationInfo = adaptiveConfig
        if (adaptiveConfig.confidenceScore > 0.5f) {
            currentDifficultyLevel = adaptiveConfig.level
            showAdaptationNotification = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2D3748)) // Dark blue-gray background
            .padding(top = 80.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        when {
            isLoading -> {
                AILoadingScreen(message = "Loading image puzzle...")
            }

            hasError -> {
                ErrorScreen(
                    message = errorMessage,
                    onRetry = {
                        isLoading = true
                        hasError = false
                    },
                    onBack = {
                        onBack()
                    }
                )
            }

            puzzleDataParsed != null -> {
                // Enhanced header with adaptive info
                AdaptiveImagePuzzleHeader(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    difficulty = currentDifficultyLevel.name,
                    timer = formatTime(timeRemaining),
                    hearts = currentHearts,
                    adaptiveConfig = adaptiveConfig,
                    currentScore = currentScore,
                    completedPieces = puzzlePieces.count { it.isCorrect },
                    totalPieces = puzzleDataParsed!!.totalPieces,
                    onBack = onBack,
                    onPreview = { showPreview = !showPreview }
                )

                // Adaptive difficulty notification
                AnimatedVisibility(
                    visible = showAdaptationNotification,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    AdaptationNotificationCard(
                        adaptationInfo = adaptationInfo,
                        onDismiss = { showAdaptationNotification = false }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (gameState) {
                    "instructions" -> {
                        AdaptiveImagePuzzleInstructions(
                            puzzleData = puzzleDataParsed!!,
                            adaptiveConfig = adaptiveConfig,
                            onStartGame = {
                                gameState = "playing"
                                gameStartTime = System.currentTimeMillis()
                            }
                        )
                    }

                    "playing" -> {
                        AdaptiveImagePuzzleGameScreen(
                            puzzleData = puzzleDataParsed!!,
                            puzzlePieces = puzzlePieces,
                            gridSlots = gridSlots,
                            selectedPieceId = selectedPieceId,
                            showPreview = showPreview,
                            removalMethod = removalMethod,
                            onPieceSelected = { pieceId ->
                                selectedPieceId = if (selectedPieceId == pieceId) -1 else pieceId
                            },
                            onPiecePlaced = { pieceId, targetRow, targetCol ->
                                puzzlePieces = handlePiecePlacement(pieceId, targetRow, targetCol, puzzlePieces)
                                selectedPieceId = -1
                            },
                            onPieceRemoved = { pieceId ->
                                // Handle piece removal
                                puzzlePieces = handlePieceRemoval(pieceId, puzzlePieces)
                                selectedPieceId = -1
                            },
                            onPieceRotated = { pieceId ->
                                if (adaptiveConfig.allowRotation) {
                                    puzzlePieces = puzzlePieces.map { piece ->
                                        if (piece.id == pieceId) {
                                            piece.copy(currentRotation = (piece.currentRotation + 90f) % 360f)
                                        } else piece
                                    }
                                }
                            },
                            onPreviewToggle = { showPreview = !showPreview }
                        )
                    }

                    "completed" -> {
                        AdaptiveImagePuzzleCompletionScreen(
                            puzzleData = puzzleDataParsed!!,
                            score = currentScore,
                            completedPieces = puzzlePieces.count { it.isCorrect },
                            timeUsed = puzzleDataParsed!!.timeLimit / 1000 - timeRemaining,
                            hintsUsed = hintsUsed,
                            adaptiveConfig = adaptiveConfig,
                            streakInfo = streakInfo,
                            onContinue = {
                                val completionTime = System.currentTimeMillis() - gameStartTime
                                val isSuccess = puzzlePieces.count { it.isCorrect } == puzzleDataParsed!!.totalPieces
                                recordAdaptivePerformance(isSuccess, completionTime)
                                onSubmitAnswer(isSuccess)
                                fetchNextPuzzle(currentScore)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveImagePuzzleHeader(
    level: UserLevel,
    streakInfo: StreakInfo,
    difficulty: String,
    timer: String,
    hearts: Int,
    adaptiveConfig: AdaptiveImagePuzzleConfig,
    currentScore: Int,
    completedPieces: Int,
    totalPieces: Int,
    onBack: () -> Unit,
    onPreview: () -> Unit
) {
    Column(modifier = Modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Level ${level.level}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = adaptiveConfig.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = timer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row {
                    repeat(hearts) {
                        Text(
                            text = "❤️",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (streakInfo.currentStreak > 0) {
                    Text(
                        text = "🔥 ${streakInfo.currentStreak}",
                        fontSize = 10.sp,
                        color = Color(0xFFFF6B35)
                    )
                }
            }
        }

        // Progress and controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Progress indicator
            Column {
                Text(
                    text = "${stringResource(R.string.progress)}: $completedPieces/$totalPieces",
                    fontSize = 12.sp,
                    color = Color.White
                )
                LinearProgressIndicator(
                    progress = completedPieces.toFloat() / totalPieces.toFloat(),
                    modifier = Modifier.width(120.dp),
                    color = Color(0xFF10B981)
                )
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                if (adaptiveConfig.showPreview) {
                    IconButton(
                        onClick = onPreview,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Preview,
                            "Preview",
                            tint = Color.Cyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Config info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (adaptiveConfig.allowRotation) {
                Text(
                    text = "🔄 Rotation",
                    fontSize = 10.sp,
                    color = Color(0xFF4CAF50)
                )
            }
            if (adaptiveConfig.adaptiveComplexity) {
                Text(
                    text = "⚡ Adaptive",
                    fontSize = 10.sp,
                    color = Color(0xFF2196F3)
                )
            }
            Text(
                text = "Score: $currentScore",
                fontSize = 10.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun AdaptationNotificationCard(
    adaptationInfo: DifficultyManager.AdaptiveConfig?,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4FC3F7).copy(alpha = 0.9f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.TrendingUp,
                contentDescription = "Difficulty adjusted",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Puzzle Adapted!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = adaptationInfo?.adjustmentReason ?: "",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AdaptiveImagePuzzleInstructions(
    puzzleData: AdaptiveImagePuzzleData,
    adaptiveConfig: AdaptiveImagePuzzleConfig,
    onStartGame: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Adaptive Image Puzzle",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = adaptiveConfig.description,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.how_to_play),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                val instructions = listOf(
                    "Drag puzzle pieces from the bottom row",
                    "Drop them into the correct grid positions",
                    if (adaptiveConfig.allowRotation) "Double-tap pieces to rotate them" else null,
                    "Long press placed pieces or click ❌ to remove them",
                    "Complete the image to win!",
                    if (adaptiveConfig.showPreview) "Use preview button to see the original" else null
                ).filterNotNull()

                instructions.forEach { instruction ->
                    Text(
                        text = "• $instruction",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Grid: ${puzzleData.gridSize}×${puzzleData.gridSize}",
                        fontSize = 12.sp,
                        color = Color.Cyan
                    )
                    Text(
                        text = "Pieces: ${puzzleData.totalPieces}",
                        fontSize = 12.sp,
                        color = Color.Yellow
                    )
                    Text(
                        text = "Theme: ${puzzleData.theme}",
                        fontSize = 12.sp,
                        color = Color.Green
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onStartGame,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Text(
                text = stringResource(R.string.start_puzzle),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// Enhanced grid cell with piece removal functionality
@Composable
private fun EnhancedGridCellWithRemoval(
    row: Int,
    col: Int,
    piece: EnhancedImagePuzzlePiece?,
    onPieceRemoved: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        piece?.isCorrect == true -> Color.Green
        piece != null -> Color.Cyan
        else -> Color.Gray
    }

    val backgroundColor = when {
        piece?.isCorrect == true -> Color.Green.copy(alpha = 0.3f)
        piece != null -> Color.Cyan.copy(alpha = 0.2f)
        else -> Color.Gray.copy(alpha = 0.1f)
    }

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        piece?.bitmap?.let { bitmap ->
            if (!piece.dragState.isDragging) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Puzzle piece",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .rotate(piece.currentRotation)
                            .pointerInput(Unit) {
                                // Long press to remove
                                detectTapGestures(
                                    onLongPress = {
                                        onPieceRemoved(piece.id)
                                    }
                                )
                            },
                        contentScale = ContentScale.Crop
                    )

                    // Remove button (X) that appears on pieces
                    IconButton(
                        onClick = { onPieceRemoved(piece.id) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .background(
                                Color.Red.copy(alpha = 0.8f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove piece",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Correctness indicator
                    if (piece.isCorrect) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.correct),
                            tint = Color.Green,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .background(Color.White, CircleShape)
                                .padding(2.dp)
                        )
                    }
                }
            }
        } ?: run {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Drop zone",
                    tint = Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.drop_here),
                    fontSize = 8.sp,
                    color = Color.Gray.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// Enhanced puzzle grid with removal functionality
@Composable
fun EnhancedPuzzleGridWithRemoval(
    gridSize: Int,
    puzzlePieces: List<EnhancedImagePuzzlePiece>,
    onPiecePlaced: (Int, Int, Int) -> Unit,
    onPieceRemoved: (Int) -> Unit,
    onGridBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    onPieceDragUpdate: (Int, DragState) -> Unit,
    modifier: Modifier = Modifier
) {
    var gridPosition by remember { mutableStateOf(Offset.Zero) }
    var gridSizeState by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(
                Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
            .onGloballyPositioned { coordinates ->
                gridPosition = coordinates.positionInWindow()
                gridSizeState = coordinates.size
                val bounds = androidx.compose.ui.geometry.Rect(
                    offset = gridPosition,
                    size = androidx.compose.ui.geometry.Size(
                        gridSizeState.width.toFloat(),
                        gridSizeState.height.toFloat()
                    )
                )
                onGridBoundsChanged(bounds)
            }
    ) {
        // Grid layout
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(gridSize) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(gridSize) { col ->
                        val piece = puzzlePieces.find { it.placedInRow == row && it.placedInCol == col }

                        EnhancedGridCellWithRemoval(
                            row = row,
                            col = col,
                            piece = piece,
                            onPieceRemoved = onPieceRemoved,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }

        // Render dragging pieces on top
        puzzlePieces.filter { it.dragState.isDragging }.forEach { piece ->
            piece.bitmap?.let { bitmap ->
                Card(
                    modifier = Modifier
                        .size(80.dp)
                        .offset {
                            IntOffset(
                                (piece.dragState.dragOffset.x - gridPosition.x).roundToInt(),
                                (piece.dragState.dragOffset.y - gridPosition.y).roundToInt()
                            )
                        }
                        .zIndex(10f)
                        .shadow(8.dp, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Dragging piece",
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(piece.currentRotation),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedPuzzlePiecesPanel(
    pieces: List<EnhancedImagePuzzlePiece>,
    selectedPieceId: Int,
    allowRotation: Boolean,
    gridBounds: androidx.compose.ui.geometry.Rect?,
    gridSize: Int,
    onPieceSelected: (Int) -> Unit,
    onPieceRotated: (Int) -> Unit,
    onPieceDragUpdate: (Int, DragState) -> Unit,
    onPiecePlaced: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (pieces.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "🎉 All pieces placed! Check if they're in the correct positions.",
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = "Drag pieces to the grid above (${pieces.size} remaining):",
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                items(pieces, key = { it.id }) { piece ->
                    EnhancedPuzzlePieceItem(
                        piece = piece,
                        isSelected = piece.id == selectedPieceId,
                        allowRotation = allowRotation,
                        gridBounds = gridBounds,
                        gridSize = gridSize,
                        onSelected = { onPieceSelected(piece.id) },
                        onRotated = { onPieceRotated(piece.id) },
                        onDragUpdate = { dragState -> onPieceDragUpdate(piece.id, dragState) },
                        onPiecePlaced = onPiecePlaced
                    )
                }
            }
        }
    }
}

@Composable
private fun EnhancedPuzzlePieceItem(
    piece: EnhancedImagePuzzlePiece,
    isSelected: Boolean,
    allowRotation: Boolean,
    gridBounds: androidx.compose.ui.geometry.Rect?,
    gridSize: Int,
    onSelected: () -> Unit,
    onRotated: () -> Unit,
    onDragUpdate: (DragState) -> Unit,
    onPiecePlaced: (Int, Int, Int) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var startPosition by remember { mutableStateOf(Offset.Zero) }

    val scale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.2f
            isSelected -> 1.1f
            else -> 1f
        },
        animationSpec = tween(200)
    )

    Card(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .onGloballyPositioned { coordinates ->
                startPosition = coordinates.positionInWindow()
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragOffset = offset
                        onDragUpdate(
                            DragState(
                                isDragging = true,
                                dragOffset = startPosition + offset,
                                startPosition = startPosition
                            )
                        )
                    },
                    onDragEnd = {
                        isDragging = false
                        val finalPosition = startPosition + dragOffset

                        gridBounds?.let { bounds ->
                            if (bounds.contains(finalPosition)) {
                                val gridCellWidth = bounds.width / gridSize
                                val gridCellHeight = bounds.height / gridSize
                                val col = ((finalPosition.x - bounds.left) / gridCellWidth).toInt()
                                val row = ((finalPosition.y - bounds.top) / gridCellHeight).toInt()

                                if (row >= 0 && row < gridSize && col >= 0 && col < gridSize) {
                                    onPiecePlaced(piece.id, row, col)
                                }
                            }
                        }

                        onDragUpdate(DragState())
                        dragOffset = Offset.Zero
                    },
                    onDrag = { _, delta ->
                        dragOffset += delta
                        onDragUpdate(
                            DragState(
                                isDragging = true,
                                dragOffset = startPosition + dragOffset,
                                startPosition = startPosition
                            )
                        )
                    }
                )
            }
            .clickable {
                if (!isDragging) {
                    onSelected()
                }
            }
            .let {
                if (allowRotation) {
                    it.pointerInput("rotation") {
                        detectTapGestures(
                            onDoubleTap = {
                                if (!isDragging) {
                                    onRotated()
                                }
                            }
                        )
                    }
                } else it
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragging -> Color.Yellow.copy(alpha = 0.3f)
                isSelected -> Color.Blue.copy(alpha = 0.3f)
                else -> Color.Transparent
            }
        ),
        border = BorderStroke(
            2.dp,
            when {
                isDragging -> Color.Yellow
                isSelected -> Color.Blue
                else -> Color.Gray
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            piece.bitmap?.let { bitmap ->
                if (!piece.dragState.isDragging) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Puzzle piece",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .rotate(piece.currentRotation),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            if (isSelected && !isDragging) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "DRAG TO GRID",
                        fontSize = 7.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    if (allowRotation) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.RotateRight,
                                contentDescription = "Double-tap to rotate",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "2X TAP",
                                fontSize = 6.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewOverlayDialog(
    imageUrl: String,
    originalBitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
        ) {
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .size(44.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close preview",
                    tint = Color.White
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111418))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Image Preview",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    if (originalBitmap != null) {
                        Image(
                            bitmap = originalBitmap.asImageBitmap(),
                            contentDescription = "Preview image",
                            modifier = Modifier
                                .sizeIn(maxWidth = 420.dp, maxHeight = 420.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Preview image",
                            modifier = Modifier
                                .sizeIn(maxWidth = 420.dp, maxHeight = 420.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Close", color = Color.White) }
                }
            }
        }
    }
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdaptiveImagePuzzleGameScreen(
    puzzleData: AdaptiveImagePuzzleData,
    puzzlePieces: List<ImagePuzzlePiece>,
    gridSlots: List<GridSlot>,
    selectedPieceId: Int,
    showPreview: Boolean,
    removalMethod: PieceRemovalMethod = PieceRemovalMethod.LONG_PRESS_WITH_BUTTON,
    onPieceSelected: (Int) -> Unit,
    onPiecePlaced: (Int, Int, Int) -> Unit,
    onPieceRemoved: (Int) -> Unit,
    onPieceRotated: (Int) -> Unit,
    onPreviewToggle: () -> Unit
) {
    // Convert to enhanced pieces for drag functionality
    var enhancedPieces by remember {
        mutableStateOf(
            puzzlePieces.map { piece ->
                EnhancedImagePuzzlePiece(
                    id = piece.id,
                    correctRow = piece.correctRow,
                    correctCol = piece.correctCol,
                    bitmap = piece.bitmap,
                    currentPosition = piece.currentPosition,
                    currentRotation = piece.currentRotation,
                    isPlaced = piece.isPlaced,
                    isCorrect = piece.isCorrect,
                    placedInRow = piece.placedInRow,
                    placedInCol = piece.placedInCol,
                    scale = piece.scale,
                    dragState = DragState()
                )
            }
        )
    }

    // Track grid bounds for drop zone detection
    var gridBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // Sync with original pieces when they change
    LaunchedEffect(puzzlePieces) {
        enhancedPieces = puzzlePieces.map { piece ->
            val existingEnhanced = enhancedPieces.find { it.id == piece.id }
            EnhancedImagePuzzlePiece(
                id = piece.id,
                correctRow = piece.correctRow,
                correctCol = piece.correctCol,
                bitmap = piece.bitmap,
                currentPosition = piece.currentPosition,
                currentRotation = piece.currentRotation,
                isPlaced = piece.isPlaced,
                isCorrect = piece.isCorrect,
                placedInRow = piece.placedInRow,
                placedInCol = piece.placedInCol,
                scale = piece.scale,
                dragState = existingEnhanced?.dragState ?: DragState()
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Preview overlay
        // Floating preview (modal)
        if (showPreview) {
            PreviewOverlayDialog(
                imageUrl = puzzleData.imageUrl,
                originalBitmap = puzzleData.originalBitmap,
                onDismiss = { onPreviewToggle() } // will set showPreview = false upstream
            )
        }


        // Instructions with removal method info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Blue.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "🎯 Drag puzzle pieces from the bottom panel and drop them on the grid above",
                    fontSize = 14.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "💡 Long press placed pieces or click ❌ to remove them",
                    fontSize = 12.sp,
                    color = Color.Yellow,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Main puzzle grid with enhanced removal functionality
        EnhancedPuzzleGridWithRemoval(
            gridSize = puzzleData.gridSize,
            puzzlePieces = enhancedPieces,
            onPiecePlaced = onPiecePlaced,
            onPieceRemoved = onPieceRemoved,
            onGridBoundsChanged = { bounds -> gridBounds = bounds },
            onPieceDragUpdate = { pieceId, dragState ->
                enhancedPieces = enhancedPieces.map { piece ->
                    if (piece.id == pieceId) {
                        piece.copy(dragState = dragState)
                    } else piece
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        )

        // Enhanced pieces panel
        EnhancedPuzzlePiecesPanel(
            pieces = enhancedPieces.filter { !it.isPlaced },
            selectedPieceId = selectedPieceId,
            allowRotation = puzzleData.config.allowRotation,
            gridBounds = gridBounds,
            gridSize = puzzleData.gridSize,
            onPieceSelected = onPieceSelected,
            onPieceRotated = onPieceRotated,
            onPieceDragUpdate = { pieceId, dragState ->
                enhancedPieces = enhancedPieces.map { piece ->
                    if (piece.id == pieceId) {
                        piece.copy(dragState = dragState)
                    } else piece
                }
            },
            onPiecePlaced = onPiecePlaced,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}


@Composable
private fun AdaptiveImagePuzzleCompletionScreen(
    puzzleData: AdaptiveImagePuzzleData,
    score: Int,
    completedPieces: Int,
    timeUsed: Long,
    hintsUsed: Int,
    adaptiveConfig: AdaptiveImagePuzzleConfig,
    streakInfo: StreakInfo,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var showFullScreenImage by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (completedPieces == puzzleData.totalPieces) "Puzzle Complete!" else "Time's Up!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (completedPieces == puzzleData.totalPieces) Color.Green else Color.Red,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Clickable completed puzzle image
        Box(
            modifier = Modifier
                .size(200.dp)
                .clickable { showFullScreenImage = true }
                .shadow(8.dp, RoundedCornerShape(8.dp))
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                puzzleData.originalBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Completed puzzle - tap to view full screen",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                } ?: run {
                    AsyncImage(
                        model = puzzleData.imageUrl,
                        contentDescription = "Completed puzzle - tap to view full screen",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Overlay with tap hint
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { showFullScreenImage = true },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.ZoomIn,
                        contentDescription = "Tap to enlarge",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Tap to view full screen",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Score and stats card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${stringResource(R.string.final_score)}: $score",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.completed), fontSize = 10.sp, color = Color.Gray)
                        Text("$completedPieces/${puzzleData.totalPieces}", fontSize = 14.sp, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.time_used), fontSize = 10.sp, color = Color.Gray)
                        Text(formatTime(timeUsed), fontSize = 14.sp, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hints", fontSize = 10.sp, color = Color.Gray)
                        Text("$hintsUsed", fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save status message
        saveStatus?.let { status ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.contains("✅")) Color.Green.copy(alpha = 0.2f)
                    else Color.Red.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    text = status,
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Action buttons row (Save and Share)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Save image button
            Button(
                onClick = {
                    saveImagePuzzle(
                        context = context,
                        puzzleData = puzzleData,
                        score = score,
                        completedPieces = completedPieces,
                        totalPieces = puzzleData.totalPieces,
                        timeUsed = timeUsed,
                        onStatusUpdate = { status -> saveStatus = status }
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), // Green
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = stringResource(R.string.save),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.save).uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Share button
            Button(
                onClick = {
                    shareImagePuzzle(
                        context = context,
                        puzzleData = puzzleData,
                        score = score,
                        completedPieces = completedPieces,
                        totalPieces = puzzleData.totalPieces,
                        timeUsed = timeUsed
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DA1F2)), // Twitter blue
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.share),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.share).uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Continue button
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Text(
                text = stringResource(R.string.continue_label_caps),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }

    // Full-screen image viewer
    if (showFullScreenImage) {
        Dialog(onDismissRequest = { showFullScreenImage = false }) {
            FullScreenImageViewer(
                puzzleData = puzzleData,
                onDismiss = { showFullScreenImage = false },
                score = score,
                completedPieces = completedPieces,
                totalPieces = puzzleData.totalPieces,
                timeUsed = timeUsed
            )
        }
    }
}

private fun saveImagePuzzle(
    context: Context,
    puzzleData: AdaptiveImagePuzzleData,
    score: Int,
    completedPieces: Int,
    totalPieces: Int,
    timeUsed: Long,
    onStatusUpdate: (String) -> Unit
) {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    scope.launch {
        try {
            Log.d("SavePuzzle", "💾 Starting save process...")
            onStatusUpdate("📸 Preparing image...")

            val originalBitmap = getBitmapForSharing(puzzleData)

            if (originalBitmap == null) {
                onStatusUpdate("❌ Failed to load image")
                delay(3000)
                onStatusUpdate("")
                return@launch
            }

            onStatusUpdate("🎨 Adding watermark...")

            // Add watermark to the bitmap
            val watermarkedBitmap = addWatermarkToBitmap(
                bitmap = originalBitmap,
                puzzleData = puzzleData,
                score = score,
                completedPieces = completedPieces,
                totalPieces = totalPieces,
                timeUsed = timeUsed
            )

            val result = saveImageToGallery(context, watermarkedBitmap, puzzleData, score, completedPieces, totalPieces, timeUsed)

            if (result) {
                onStatusUpdate("✅ Image saved to gallery!")
                delay(3000)
                onStatusUpdate("")
            } else {
                onStatusUpdate("❌ Failed to save image")
                delay(3000)
                onStatusUpdate("")
            }

        } catch (e: Exception) {
            Log.e("SavePuzzle", "❌ Error saving image: ${e.message}", e)
            onStatusUpdate("❌ Error: ${e.message}")
            delay(3000)
            onStatusUpdate("")
        }
    }
}

private suspend fun saveImageToGallery(
    context: Context,
    bitmap: Bitmap,
    puzzleData: AdaptiveImagePuzzleData,
    score: Int,
    completedPieces: Int,
    totalPieces: Int,
    timeUsed: Long
): Boolean = withContext(Dispatchers.IO) {
    try {
        Log.d("SavePuzzle", "💾 Saving to gallery...")

        val contentResolver = context.contentResolver
        val imageCollection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        // Create filename with puzzle info
        val fileName = "RiddleVerse_Puzzle_${System.currentTimeMillis()}.jpg"

        // Create description with puzzle stats
        val description = buildString {
            append("RiddleVerse Image Puzzle - ")
            append("Score: $score, ")
            append("Completed: $completedPieces/$totalPieces pieces, ")
            append("Time: ${formatTime(timeUsed)}, ")
            append("Difficulty: ${puzzleData.difficulty}")
        }

        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DESCRIPTION, description)
            put(MediaStore.Images.Media.TITLE, "RiddleVerse ${puzzleData.theme} Puzzle")

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RiddleVerse")
            }
        }

        val imageUri = contentResolver.insert(imageCollection, imageDetails)

        if (imageUri != null) {
            contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                imageDetails.clear()
                imageDetails.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(imageUri, imageDetails, null, null)
            }

            Log.d("SavePuzzle", "✅ Image saved to gallery: $imageUri")
            true
        } else {
            Log.e("SavePuzzle", "❌ Failed to create gallery entry")
            false
        }
    } catch (e: Exception) {
        Log.e("SavePuzzle", "❌ Gallery save failed: ${e.message}", e)
        false
    }
}

// Updated share function with watermark
private fun shareImagePuzzle(
    context: Context,
    puzzleData: AdaptiveImagePuzzleData,
    score: Int,
    completedPieces: Int,
    totalPieces: Int,
    timeUsed: Long
) {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    scope.launch {
        try {
            Log.d("SharePuzzle", "📤 Starting share process...")

            val shareText = createShareText(
                puzzleData = puzzleData,
                score = score,
                completedPieces = completedPieces,
                totalPieces = totalPieces,
                timeUsed = timeUsed
            )

            // Try to get the bitmap
            val originalBitmap = getBitmapForSharing(puzzleData)

            if (originalBitmap != null) {
                Log.d("SharePuzzle", "🎨 Adding watermark for sharing...")

                // Add watermark to the bitmap
                val watermarkedBitmap = addWatermarkToBitmap(
                    bitmap = originalBitmap,
                    puzzleData = puzzleData,
                    score = score,
                    completedPieces = completedPieces,
                    totalPieces = totalPieces,
                    timeUsed = timeUsed
                )

                Log.d("SharePuzzle", "🖼️ Watermarked bitmap available, using MediaStore method...")
                shareImageViaMediaStore(context, shareText, watermarkedBitmap)
            } else {
                Log.w("SharePuzzle", "⚠️ No bitmap available, sharing text only")
                shareTextOnly(context, shareText)
            }
        } catch (e: Exception) {
            Log.e("SharePuzzle", "❌ Error in share process: ${e.message}", e)
            val shareText = createShareText(puzzleData, score, completedPieces, totalPieces, timeUsed)
            shareTextOnly(context, shareText)
        }
    }
}

// New function to add watermark to bitmap
private fun addWatermarkToBitmap(
    bitmap: Bitmap,
    puzzleData: AdaptiveImagePuzzleData,
    score: Int,
    completedPieces: Int,
    totalPieces: Int,
    timeUsed: Long
): Bitmap {
    try {
        // Create a mutable copy of the bitmap
        val watermarkedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(watermarkedBitmap)

        val width = watermarkedBitmap.width
        val height = watermarkedBitmap.height

        // Calculate sizes based on image dimensions
        val baseSize = minOf(width, height)
        val logoTextSize = (baseSize * 0.04f).coerceAtLeast(20f) // 4% of image size, min 20px
        val statsTextSize = (baseSize * 0.025f).coerceAtLeast(14f) // 2.5% of image size, min 14px
        val padding = (baseSize * 0.02f).coerceAtLeast(10f) // 2% padding

        // Setup paints
        val logoPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = logoTextSize
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            setShadowLayer(3f, 2f, 2f, android.graphics.Color.BLACK)
        }

        val copyrightPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = logoTextSize * 0.7f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
            setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
        }

        val statsPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = statsTextSize
            typeface = Typeface.DEFAULT
            isAntiAlias = true
            setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
        }

        val backgroundPaint = Paint().apply {
            color = android.graphics.Color.argb(180, 0, 0, 0) // Semi-transparent black
            isAntiAlias = true
        }

        // Main branding text
        val brandingText = "RiddleVerse"
        val copyrightText = "© Created with RiddleVerse"

        // Stats text
        val statsLines = listOf(
            "Score: $score",
            "Completed: $completedPieces/$totalPieces",
            "Time: ${formatTime(timeUsed)}",
            "Difficulty: ${puzzleData.difficulty}"
        )

        // Calculate text dimensions
        val brandingBounds = Rect()
        logoPaint.getTextBounds(brandingText, 0, brandingText.length, brandingBounds)

        val copyrightBounds = Rect()
        copyrightPaint.getTextBounds(copyrightText, 0, copyrightText.length, copyrightBounds)

        // Calculate total height needed for bottom watermark
        val lineSpacing = statsTextSize * 0.3f
        val totalStatsHeight = statsLines.size * (statsTextSize + lineSpacing)
        val totalBottomHeight = brandingBounds.height() + copyrightBounds.height() + totalStatsHeight + padding * 4

        // Draw bottom watermark background
        val bottomRect = RectF(
            0f,
            height - totalBottomHeight,
            width.toFloat(),
            height.toFloat()
        )
        canvas.drawRect(bottomRect, backgroundPaint)

        // Position calculations for bottom watermark
        var currentY = height - totalBottomHeight + padding + brandingBounds.height()

        // Draw main branding
        val brandingX = padding
        canvas.drawText(brandingText, brandingX, currentY, logoPaint)

        // Draw copyright
        currentY += copyrightBounds.height() + padding * 0.5f
        canvas.drawText(copyrightText, brandingX, currentY, copyrightPaint)

        // Draw stats
        currentY += padding
        statsLines.forEach { statLine ->
            currentY += statsTextSize + lineSpacing
            canvas.drawText(statLine, brandingX, currentY, statsPaint)
        }

        // Add top-right corner watermark (smaller, less intrusive)
        val cornerLogoSize = logoTextSize * 0.6f
        val cornerLogoPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = cornerLogoSize
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            alpha = 180 // Semi-transparent
            setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
        }

        val cornerText = "RiddleVerse"
        val cornerBounds = Rect()
        cornerLogoPaint.getTextBounds(cornerText, 0, cornerText.length, cornerBounds)

        // Draw corner background
        val cornerBackgroundPaint = Paint().apply {
            color = android.graphics.Color.argb(120, 0, 0, 0)
            isAntiAlias = true
        }

        val cornerPadding = padding * 0.5f
        val cornerRect = RectF(
            width - cornerBounds.width() - cornerPadding * 2,
            cornerPadding,
            width.toFloat(),
            cornerBounds.height() + cornerPadding * 2
        )
        canvas.drawRoundRect(cornerRect, 8f, 8f, cornerBackgroundPaint)

        // Draw corner text
        canvas.drawText(
            cornerText,
            width - cornerBounds.width() - cornerPadding,
            cornerBounds.height() + cornerPadding,
            cornerLogoPaint
        )

        Log.d("Watermark", "✅ Watermark added successfully")
        return watermarkedBitmap

    } catch (e: Exception) {
        Log.e("Watermark", "❌ Failed to add watermark: ${e.message}", e)
        // Return original bitmap if watermarking fails
        return bitmap
    }
}

// New method using MediaStore (works on all Android versions)
private suspend fun shareImageViaMediaStore(
    context: Context,
    shareText: String,
    bitmap: Bitmap
) = withContext(Dispatchers.IO) {
    try {
        Log.d("SharePuzzle", "📱 Sharing via MediaStore...")

        val contentResolver = context.contentResolver
        val imageCollection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "riddleverse_puzzle_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val imageUri = contentResolver.insert(imageCollection, imageDetails)

        if (imageUri != null) {
            contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                imageDetails.clear()
                imageDetails.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(imageUri, imageDetails, null, null)
            }

            Log.d("SharePuzzle", "✅ Image saved to MediaStore: $imageUri")

            // Now share using the MediaStore URI
            withContext(Dispatchers.Main) {
                shareWithMediaStoreUri(context, shareText, imageUri)
            }
        } else {
            Log.e("SharePuzzle", "❌ Failed to create MediaStore entry")
            withContext(Dispatchers.Main) {
                shareTextOnly(context, shareText)
            }
        }
    } catch (e: Exception) {
        Log.e("SharePuzzle", "❌ MediaStore sharing failed: ${e.message}", e)
        withContext(Dispatchers.Main) {
            shareTextOnly(context, shareText)
        }
    }
}

// Share with MediaStore URI
private fun shareWithMediaStoreUri(context: Context, shareText: String, imageUri: Uri) {
    try {
        Log.d("SharePuzzle", "📤 Creating share intent with MediaStore URI...")

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "image/*"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_SUBJECT, "Check out this RiddleVerse puzzle!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share puzzle with image"))
        Log.d("SharePuzzle", "✅ MediaStore sharing intent launched")
    } catch (e: Exception) {
        Log.e("SharePuzzle", "❌ Failed to share with MediaStore URI: ${e.message}", e)
        shareTextOnly(context, shareText)
    }
}

// Helper to get bitmap for sharing
private suspend fun getBitmapForSharing(puzzleData: AdaptiveImagePuzzleData): Bitmap? {
    return try {
        when {
            puzzleData.originalBitmap != null -> {
                Log.d("SharePuzzle", "🎯 Using cached bitmap")
                puzzleData.originalBitmap
            }
            puzzleData.imageUrl.isNotEmpty() -> {
                Log.d("SharePuzzle", "📥 Downloading bitmap from URL: ${puzzleData.imageUrl}")
                downloadImageFromUrl(puzzleData.imageUrl)
            }
            else -> {
                Log.w("SharePuzzle", "⚠️ No image source available")
                null
            }
        }
    } catch (e: Exception) {
        Log.e("SharePuzzle", "❌ Error getting bitmap: ${e.message}", e)
        null
    }
}

// Enhanced text-only sharing
private fun shareTextOnly(context: Context, shareText: String) {
    try {
        Log.d("SharePuzzle", "📝 Sharing text only...")

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Check out this RiddleVerse puzzle!")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share puzzle"))
        Log.d("SharePuzzle", "✅ Text sharing intent launched")
    } catch (e: Exception) {
        Log.e("SharePuzzle", "❌ Failed to share text: ${e.message}", e)
    }
}

// Save image to cache for sharing
private suspend fun saveImageToCache(
    context: Context,
    puzzleData: AdaptiveImagePuzzleData
): Uri? = withContext(Dispatchers.IO) {
    try {
        // Use existing bitmap if available, otherwise download
        val bitmap = puzzleData.originalBitmap ?: downloadImageFromUrl(puzzleData.imageUrl)
        bitmap ?: return@withContext null

        // Create cache file
        val cachePath = File(context.cacheDir, "shared_images")
        cachePath.mkdirs()

        val fileName = "riddleverse_puzzle_${System.currentTimeMillis()}.jpg"
        val file = File(cachePath, fileName)

        // Save bitmap to file
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        // Get URI using FileProvider
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (e: Exception) {
        Log.e("SharePuzzle", "Failed to save image to cache: ${e.message}")
        null
    }
}

private fun createShareText(
    puzzleData: AdaptiveImagePuzzleData,
    score: Int,
    completedPieces: Int,
    totalPieces: Int,
    timeUsed: Long
): String {
    val completionStatus = if (completedPieces == totalPieces) "✅ COMPLETED" else "⏱️ ATTEMPTED"
    val completionEmoji = if (completedPieces == totalPieces) "🎉" else "🧩"

    val difficultyEmoji = when (puzzleData.difficulty.lowercase()) {
        "easy" -> "🟢"
        "medium" -> "🟡"
        "hard" -> "🔴"
        "expert" -> "🔥"
        else -> "🧩"
    }

    val timeEmoji = when {
        timeUsed < 60 -> "⚡" // Lightning fast
        timeUsed < 180 -> "🏃" // Quick
        timeUsed < 300 -> "🚶" // Steady
        else -> "🐌" // Slow and steady
    }

    return buildString {
        appendLine("$completionEmoji I just solved an image puzzle in RiddleVerse!")
        appendLine()
        appendLine("📊 Results:")
        appendLine("$completionStatus $completedPieces/$totalPieces pieces")
        appendLine("⭐ Score: $score points")
        appendLine("$timeEmoji Time: ${formatTime(timeUsed)}")
        appendLine("$difficultyEmoji Difficulty: ${puzzleData.difficulty}")
        appendLine("🎯 Theme: ${puzzleData.theme}")
        appendLine()
        appendLine("🎮 Can you beat my score? Download RiddleVerse and try this puzzle!")
        appendLine("#RiddleVerse #PuzzleGame #ImagePuzzle #BrainTraining")
        appendLine()
        appendLine("🖼️ Original image: ${puzzleData.imageUrl}")
    }
}

// Alternative share with image (if you want to implement image sharing later)
private fun shareImagePuzzleWithImage(
    context: Context,
    puzzleData: AdaptiveImagePuzzleData,
    score: Int,
    completedPieces: Int,
    totalPieces: Int,
    timeUsed: Long
) {
    val shareText = createShareText(puzzleData, score, completedPieces, totalPieces, timeUsed)

    // For now, just share text + URL
    // Later you could implement sharing the actual image bitmap
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        putExtra(Intent.EXTRA_SUBJECT, "Amazing RiddleVerse Image Puzzle!")
    }

    try {
        context.startActivity(Intent.createChooser(shareIntent, "Share your puzzle achievement"))
    } catch (e: Exception) {
        Log.e("SharePuzzle", "Failed to share puzzle: ${e.message}")
    }
}

// Helper function for better time formatting in share text
private fun formatTimeForShare(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

@Composable
private fun FullScreenImageViewer(
    puzzleData: AdaptiveImagePuzzleData,
    onDismiss: () -> Unit,
    score: Int = 0,
    completedPieces: Int = 0,
    totalPieces: Int = 0,
    timeUsed: Long = 0
) {
    var canDismiss by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var saveStatus by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Reset + guard against instant-close from the opening tap
    LaunchedEffect(Unit) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        kotlinx.coroutines.delay(200)
        canDismiss = true
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(0.5f, 5f)

        // Calculate bounds to prevent over-panning
        val scaledW = containerSize.width * newScale
        val scaledH = containerSize.height * newScale
        val maxX = ((scaledW - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledH - containerSize.height) / 2f).coerceAtLeast(0f)

        scale = newScale
        offsetX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
        offsetY = (offsetY + panChange.y).coerceIn(-maxY, maxY)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .onGloballyPositioned { containerSize = it.size }
            .pointerInput(canDismiss, scale) {
                // Tap background to close only when not zoomed in and after guard
                detectTapGestures {
                    if (canDismiss && scale <= 1.1f) onDismiss()
                }
            }
    ) {
        IconButton(
            onClick = {
                val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
                scope.launch {
                    saveStatus = "🎨 Adding watermark..."
                    val originalBitmap = getBitmapForSharing(puzzleData)
                    if (originalBitmap != null) {
                        val watermarkedBitmap = addWatermarkToBitmap(
                            bitmap = originalBitmap,
                            puzzleData = puzzleData,
                            score = score,
                            completedPieces = completedPieces,
                            totalPieces = totalPieces,
                            timeUsed = timeUsed
                        )
                        saveStatus = "💾 Saving..."
                        val result = saveImageToGallery(context, watermarkedBitmap, puzzleData, score, completedPieces, totalPieces, timeUsed)
                        saveStatus = if (result) "✅ Saved with watermark!" else "❌ Failed"
                        delay(3000)
                        saveStatus = null
                    } else {
                        saveStatus = "❌ Failed to load image"
                        delay(2000)
                        saveStatus = null
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Green.copy(alpha = 0.7f), CircleShape)
                .size(48.dp)
                .zIndex(10f)
        ) {
            Icon(
                Icons.Default.Save,
                contentDescription = "Save image",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                .size(48.dp)
                .zIndex(10f)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close full screen",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // Image title and info
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(12.dp)
                .zIndex(10f)
        ) {
            Text(
                text = puzzleData.theme,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (puzzleData.description.isNotEmpty()) {
                Text(
                    text = puzzleData.description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Text(
                text = "${puzzleData.gridSize}×${puzzleData.gridSize} puzzle",
                fontSize = 10.sp,
                color = Color.Cyan
            )
        }

        // Zoom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(8.dp)
                .zIndex(10f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { scale = (scale * 1.2f).coerceAtMost(5f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ZoomIn,
                    contentDescription = "Zoom in",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { scale = (scale / 1.2f).coerceAtLeast(0.5f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ZoomOut,
                    contentDescription = "Zoom out",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = "Reset zoom",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Instructions
        if (scale <= 1.2f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Pinch to zoom • Drag to pan • Tap background to close",
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Main image with zoom and pan
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
                .transformable(transformState),
            contentAlignment = Alignment.Center
        ) {
            puzzleData.originalBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Full screen puzzle image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } ?: run {
                AsyncImage(
                    model = puzzleData.imageUrl,
                    contentDescription = "Full screen puzzle image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}


// Helper classes for image loading
class ImagePuzzleImageLoader(private val context: Context) {
    suspend fun loadImageWithFallback(imageUrl: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("ImageLoader", "📄 Loading image from: $imageUrl")

                // Try direct URL loading
                val url = URL(imageUrl)
                val connection = url.openConnection().apply {
                    connectTimeout = 10000 // 10 seconds
                    readTimeout = 30000 // 30 seconds
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                }

                connection.connect()

                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                Log.d("ImageLoader", "✅ Image loaded successfully")
                bitmap
            } catch (e: Exception) {
                Log.e("ImageLoader", "❌ Failed to load image: ${e.message}")
                null
            }
        }
    }
}

// Enhanced ImagePuzzleManager with better fallback handling
class ImagePuzzleManager(private val context: Context) {
    private val mediaCache = MediaCacheManager.getInstance(context)

    suspend fun loadAndProcessImage(imageUrlOrPath: String, gridSize: Int): List<ImagePuzzlePiece>? {
        return withContext(Dispatchers.IO) {
            var originalBitmap: Bitmap? = null

            try {
                // Handle both cached paths and URLs using mediaCache
                originalBitmap = when {
                    imageUrlOrPath.startsWith("/") -> {
                        // Already a cached file path
                        Log.d("ImagePuzzleManager", "Loading from cached file: $imageUrlOrPath")
                        if (File(imageUrlOrPath).exists()) {
                            BitmapFactory.decodeFile(imageUrlOrPath)
                        } else {
                            Log.w("ImagePuzzleManager", "Cached file not found: $imageUrlOrPath")
                            null
                        }
                    }
                    imageUrlOrPath.startsWith("http") -> {
                        // URL - get cached version or download
                        Log.d("ImagePuzzleManager", "Getting cached version of: $imageUrlOrPath")
                        val cachedPath = mediaCache.getCachedMediaUrl(imageUrlOrPath)

                        if (cachedPath.startsWith("/")) {
                            // Cache hit - load from file
                            BitmapFactory.decodeFile(cachedPath)
                        } else {
                            // Cache miss - cachedPath is still the URL, download directly
                            downloadImageFromUrl(cachedPath)
                        }
                    }
                    else -> {
                        Log.w("ImagePuzzleManager", "Unknown image path format: $imageUrlOrPath")
                        null
                    }
                }

                if (originalBitmap == null) {
                    Log.d("ImagePuzzleManager", "Generating placeholder image")
                    originalBitmap = generatePlaceholderImage(1024, 1024, "Puzzle Image")
                }

                Log.d("ImagePuzzleManager", "Image loaded successfully")
            } catch (e: Exception) {
                Log.e("ImagePuzzleManager", "Failed to load image, generating placeholder: ${e.message}")
                originalBitmap = generatePlaceholderImage(1024, 1024, "Puzzle Image")
            }

            // Create puzzle pieces from the bitmap
            return@withContext originalBitmap?.let { bitmap ->
                try {
                    createPuzzlePiecesFromBitmap(bitmap, gridSize)
                } catch (e: Exception) {
                    Log.e("ImagePuzzleManager", "Failed to create puzzle pieces: ${e.message}")
                    null
                }
            }
        }
    }

    private fun createPuzzlePiecesFromBitmap(originalBitmap: Bitmap, gridSize: Int): List<ImagePuzzlePiece> {
        val pieces = mutableListOf<ImagePuzzlePiece>()
        val pieceWidth = originalBitmap.width / gridSize
        val pieceHeight = originalBitmap.height / gridSize

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val x = col * pieceWidth
                val y = row * pieceHeight

                try {
                    val pieceBitmap = Bitmap.createBitmap(
                        originalBitmap,
                        x,
                        y,
                        pieceWidth,
                        pieceHeight
                    )

                    pieces.add(
                        ImagePuzzlePiece(
                            id = row * gridSize + col,
                            correctRow = row,
                            correctCol = col,
                            bitmap = pieceBitmap,
                            currentRotation = 0f
                        )
                    )
                } catch (e: Exception) {
                    Log.e("PuzzlePieces", "Failed to create piece at [$row][$col]: ${e.message}")
                }
            }
        }

        Log.d("PuzzlePieces", "✅ Created ${pieces.size} puzzle pieces")
        return pieces
    }

    private fun generatePlaceholderImage(width: Int, height: Int, text: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fill with gradient background
        val paint = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    android.graphics.Color.parseColor("#4F46E5"),
                    android.graphics.Color.parseColor("#7C3AED")
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Add text
        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = width / 20f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val x = width / 2f
        val y = height / 2f + textPaint.textSize / 3f
        canvas.drawText(text, x, y, textPaint)

        return bitmap
    }
}

private fun createPuzzlePieces(originalBitmap: Bitmap, gridSize: Int, config: AdaptiveImagePuzzleConfig): List<ImagePuzzlePiece> {
    val pieces = mutableListOf<ImagePuzzlePiece>()
    val pieceWidth = originalBitmap.width / gridSize
    val pieceHeight = originalBitmap.height / gridSize

    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            val x = col * pieceWidth
            val y = row * pieceHeight

            val pieceBitmap = Bitmap.createBitmap(
                originalBitmap,
                x,
                y,
                pieceWidth,
                pieceHeight
            )

            pieces.add(
                ImagePuzzlePiece(
                    id = row * gridSize + col,
                    correctRow = row,
                    correctCol = col,
                    bitmap = pieceBitmap,
                    currentRotation = if (config.allowRotation && Random.nextBoolean())
                        (Random.nextInt(4) * 90f) else 0f
                )
            )
        }
    }

    return pieces
}

private fun createGridSlots(gridSize: Int): List<GridSlot> {
    val slots = mutableListOf<GridSlot>()
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            slots.add(GridSlot(row = row, col = col))
        }
    }
    return slots
}

private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun calculateTimeBonus(timeRemaining: Long, totalTime: Long): Int {
    val usedTime = totalTime - timeRemaining
    val efficiency = timeRemaining.toFloat() / totalTime.toFloat()
    return (efficiency * 50).toInt() // Max 50 point time bonus
}

// Helper function to handle piece removal logic
fun handlePieceRemoval(
    pieceId: Int,
    currentPieces: List<ImagePuzzlePiece>
): List<ImagePuzzlePiece> {
    return currentPieces.map { piece ->
        if (piece.id == pieceId && piece.isPlaced) {
            // Remove the piece from the grid and return it to available pieces
            piece.copy(
                isPlaced = false,
                placedInRow = -1,
                placedInCol = -1,
                isCorrect = false,
                currentRotation = 0f // Reset rotation when removed
            )
        } else piece
    }
}

fun generateAdaptiveImagePuzzleConfig(difficulty: DifficultyManager.DifficultyLevel): AdaptiveImagePuzzleConfig {
    return when (difficulty.index) {
        0 -> AdaptiveImagePuzzleConfig( // Beginner
            gridSize = 2, // 2x2 = 4 pieces
            allowRotation = false,
            snapTolerance = 40f,
            timeLimit = 300L, // 5 minutes
            showPreview = true,
            hintSystem = true,
            adaptiveComplexity = false,
            pieceShuffle = false,
            name = "Beginner Mode",
            description = "Simple 2x2 puzzle with hints and preview"
        )
        1 -> AdaptiveImagePuzzleConfig( // Easy
            gridSize = 2, // 2x2 = 4 pieces
            allowRotation = true,
            snapTolerance = 30f,
            timeLimit = 240L, // 4 minutes
            showPreview = true,
            hintSystem = true,
            adaptiveComplexity = true,
            pieceShuffle = true,
            name = "Easy Mode",
            description = "2x2 puzzle with rotation and shuffled pieces"
        )
        2 -> AdaptiveImagePuzzleConfig( // Medium
            gridSize = 3, // 3x3 = 9 pieces
            allowRotation = true,
            snapTolerance = 25f,
            timeLimit = 360L, // 6 minutes
            showPreview = true,
            hintSystem = true,
            adaptiveComplexity = true,
            pieceShuffle = true,
            name = "Medium Mode",
            description = "3x3 puzzle with full features"
        )
        3 -> AdaptiveImagePuzzleConfig( // Hard
            gridSize = 4, // 4x4 = 16 pieces
            allowRotation = true,
            snapTolerance = 20f,
            timeLimit = 480L, // 8 minutes
            showPreview = false,
            hintSystem = false,
            adaptiveComplexity = true,
            pieceShuffle = true,
            name = "Hard Mode",
            description = "4x4 puzzle without assistance"
        )
        4 -> AdaptiveImagePuzzleConfig( // Expert
            gridSize = 4, // 4x4 = 16 pieces
            allowRotation = true,
            snapTolerance = 15f,
            timeLimit = 360L, // 6 minutes (shorter time)
            showPreview = false,
            hintSystem = false,
            adaptiveComplexity = true,
            pieceShuffle = true,
            name = "Expert Mode",
            description = "4x4 puzzle with time pressure"
        )
        else -> AdaptiveImagePuzzleConfig(
            gridSize = 3,
            allowRotation = true,
            snapTolerance = 25f,
            timeLimit = 360L,
            showPreview = true,
            hintSystem = true,
            adaptiveComplexity = true,
            pieceShuffle = true,
            name = "Medium Mode",
            description = "3x3 puzzle with full features"
        )
    }
}

private suspend fun downloadImageFromUrl(imageUrl: String): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(imageUrl)
            val connection = url.openConnection()
            connection.doInput = true
            connection.connect()
            val inputStream = connection.getInputStream()
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e("ImageDownload", "Failed to download image: ${e.message}")
            null
        }
    }
}

// Helper function to handle piece placement logic
fun handlePiecePlacement(
    pieceId: Int,
    targetRow: Int,
    targetCol: Int,
    currentPieces: List<ImagePuzzlePiece>
): List<ImagePuzzlePiece> {
    return currentPieces.map { piece ->
        when {
            // If this is the piece being placed
            piece.id == pieceId -> {
                val isCorrectPosition = piece.correctRow == targetRow && piece.correctCol == targetCol
                piece.copy(
                    isPlaced = true,
                    placedInRow = targetRow,
                    placedInCol = targetCol,
                    isCorrect = isCorrectPosition
                )
            }
            // If another piece was already in this position, remove it
            piece.placedInRow == targetRow && piece.placedInCol == targetCol -> {
                piece.copy(
                    isPlaced = false,
                    placedInRow = -1,
                    placedInCol = -1,
                    isCorrect = false
                )
            }
            // If pieceId is -1 (remove piece), remove piece from this position
            pieceId == -1 && piece.placedInRow == targetRow && piece.placedInCol == targetCol -> {
                piece.copy(
                    isPlaced = false,
                    placedInRow = -1,
                    placedInCol = -1,
                    isCorrect = false
                )
            }
            else -> piece
        }
    }
}
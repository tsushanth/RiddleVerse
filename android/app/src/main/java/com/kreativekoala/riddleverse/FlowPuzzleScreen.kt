// FlowPuzzleScreen.kt
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

@Composable
fun FlowPuzzleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🧩 Showing Flow Puzzle Screen")

    // Parse the flow puzzle data - FIXED to handle API response format
    val parsedData = remember(currentPuzzle) {
        try {
            // The question field contains the puzzle configuration
            val questionData = currentPuzzle.question

            // Check if it's already a JSON object or needs parsing
            val puzzleConfig = if (questionData.startsWith("{")) {
                JSONObject(questionData)
            } else {
                // Generate default puzzle configuration if question is not JSON
                JSONObject("""
                {
                    "difficulty": "${currentPuzzle.difficulty ?: "easy"}",
                    "gridSize": ${when((currentPuzzle.difficulty ?: "easy").lowercase()) {
                    "easy" -> 6
                    "medium" -> 8
                    "hard" -> 10
                    else -> 6
                }},
                    "pairCount": ${when((currentPuzzle.difficulty ?: "easy").lowercase()) {
                    "easy" -> 4
                    "medium" -> 6
                    "hard" -> 8
                    else -> 4
                }},
                    "gameType": "flowPuzzle",
                    "timeLimit": ${when((currentPuzzle.difficulty ?: "easy").lowercase()) {
                    "easy" -> 120000
                    "medium" -> 180000
                    "hard" -> 240000
                    else -> 120000
                }}
                }
                """.trimIndent())
            }

            // Extract the pairs array and other game data
            val gridSize = puzzleConfig.optInt("gridSize", 6)
            val pairsArray = puzzleConfig.optJSONArray("pairs")
            val timeLimit = puzzleConfig.optInt("timeLimit", 120000)

            // Parse pairs into a format the screen can use
            val pairs = mutableListOf<Map<String, Any>>()

            if (pairsArray != null) {
                for (i in 0 until pairsArray.length()) {
                    val pairObj = pairsArray.getJSONObject(i)
                    val pair = mapOf(
                        "id" to pairObj.optString("id", "pair_$i"),
                        "color" to pairObj.optString("color", "#E53E3E"),
                        "label" to pairObj.optString("label", "A"),
                        "start" to mapOf(
                            "x" to pairObj.getJSONObject("start").optInt("x", 0),
                            "y" to pairObj.getJSONObject("start").optInt("y", 0)
                        ),
                        "end" to mapOf(
                            "x" to pairObj.getJSONObject("end").optInt("x", 1),
                            "y" to pairObj.getJSONObject("end").optInt("y", 1)
                        ),
                        "length" to pairObj.optInt("length", 5)
                    )
                    pairs.add(pair)
                }
            }

            mapOf(
                "gridSize" to gridSize,
                "pairs" to pairs,
                "timeLimit" to timeLimit,
                "difficulty" to (currentPuzzle.difficulty ?: "easy"),
                "correctAnswer" to (currentPuzzle.answer.ifEmpty { "completed" }),
                "success" to true
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "Failed to parse flow puzzle data", e)
            Log.e("PuzzleScreen", "Question data: ${currentPuzzle.question}")
            Log.e("PuzzleScreen", "Answer data: ${currentPuzzle.answer}")
            mapOf("success" to false, "error" to e.message)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val gridSize = parsedData["gridSize"] as Int
        val pairs = parsedData["pairs"] as List<Map<String, Any>>
        val timeLimit = parsedData["timeLimit"] as Int
        val difficulty = parsedData["difficulty"] as String
        val correctAnswer = parsedData["correctAnswer"] as String

        val timerText = formatTimeFromMillis(timeLimit)

        FlowPuzzleScreen(
            difficulty = difficulty,
            timer = timerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            gridSize = gridSize,
            flowPairs = pairs,
            correctAnswer = correctAnswer,
            timeLimit = timeLimit,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "Flow puzzle answer submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "Fetching next puzzle after flow puzzle completion")
                Log.d("PuzzleScreen", "Final score: $finalScore")

                // Flow puzzles are considered completed when all paths are connected
                // Score > 0 indicates successful completion
                val isCorrect = finalScore > 0
                handlePuzzleCompletion(isCorrect, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        // Show error state or move to next puzzle
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "Invalid flow puzzle data, moving to next puzzle")
            val errorMsg = parsedData["error"] as? String ?: "Unknown error"
            Log.e("PuzzleScreen", "Error details: $errorMsg")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.loading_next_puzzle))
            }
        }
    }
}

private fun formatTimeFromMillis(timeMillis: Int): String {
    val totalSeconds = timeMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

data class FlowPoint(val x: Int, val y: Int)
data class FlowPair(
    val id: String,
    val color: Color,
    val label: String,
    val start: FlowPoint,
    val end: FlowPoint
)
class FlowPath(
    val points: SnapshotStateList<FlowPoint> = mutableStateListOf(),
    var isComplete: Boolean = false
)

enum class MoveResult {
    VALID,
    INVALID_BOUNDS,
    NOT_ADJACENT,
    PATH_CONFLICT,
    ENDPOINT_CONFLICT,
    BACKTRACK
}

// --- Full-cover Flow puzzle generator ---
// Produces: (pairs, solutionPaths) where solutionPaths partition ALL cells.

// ==== DROP-IN: robust full-cover generator (no throws) ====

private data class HCfg(val pairCount: Int, val minSegLen: Int, val minBends: Int)

private fun generateFlowPuzzle(
    gridSize: Int,
    difficulty: String,
    rng: Random = Random(System.currentTimeMillis())
): Pair<List<FlowPair>, Map<String, List<FlowPoint>>> {

    val basePairs = when (difficulty.lowercase()) {
        "easy" -> 4
        "medium" -> 6
        "hard" -> 8
        else -> 5
    }.coerceAtMost((gridSize * gridSize) / 4)

    val cfg = when (difficulty.lowercase()) {
        "easy"   -> HCfg(pairCount = basePairs, minSegLen = maxOf(4, gridSize / 2), minBends = 1)
        "medium" -> HCfg(pairCount = basePairs, minSegLen = maxOf(5, gridSize - 3), minBends = 2)
        else     -> HCfg(pairCount = basePairs, minSegLen = maxOf(6, gridSize - 2), minBends = 3)
    }

    // 1) Try to build a bendy Hamiltonian path with restarts
    val path = buildHamiltonianPathWithRestarts(gridSize, rng, restarts = 120)
        ?: snakeHamiltonian(gridSize) // 2) Guaranteed fallback

    // 3) Cut into segments; prefer cut points at bends so segments aren't straight
    val segments = cutIntoSegmentsPreferBends(path, cfg, rng)
        ?: equalCut(path, cfg.pairCount) // last resort cut (still full cover)

    // 4) Trim/relax if grid is small
    val k = segments.size.coerceAtMost(cfg.pairCount)

    val colors = listOf(
        Color(0xFFE53E3E), Color(0xFF38A169), Color(0xFF3182CE), Color(0xFFD69E2E),
        Color(0xFF805AD5), Color(0xFFDD6B20), Color(0xFF319795), Color(0xFFEC4899),
        Color(0xFF4A5568), Color(0xFF2B6CB0), Color(0xFF9F7AEA)
    )
    val labels = listOf("A","B","C","D","E","F","G","H","I","J","K","L")

    val pairs = mutableListOf<FlowPair>()
    val solution = mutableMapOf<String, List<FlowPoint>>()

    for (i in 0 until k) {
        val seg = segments[i]
        val id = "pair_$i"
        pairs += FlowPair(
            id = id,
            color = colors[i % colors.size],
            label = labels[i % labels.size],
            start = seg.first(),
            end = seg.last()
        )
        solution[id] = seg
    }

    return pairs to solution
}

// ---------- helpers (version-safe, no throws) ----------

private fun neighbors(p: FlowPoint, n: Int): List<FlowPoint> = buildList(4) {
    if (p.x > 0) add(FlowPoint(p.x - 1, p.y))
    if (p.x < n - 1) add(FlowPoint(p.x + 1, p.y))
    if (p.y > 0) add(FlowPoint(p.x, p.y - 1))
    if (p.y < n - 1) add(FlowPoint(p.x, p.y + 1))
}

private fun dir(a: FlowPoint, b: FlowPoint): Int =
    when {
        b.x < a.x -> 0; b.x > a.x -> 1; b.y < a.y -> 2; else -> 3
    }

private fun bendsCount(seq: List<FlowPoint>): Int {
    if (seq.size < 3) return 0
    var bends = 0
    var prev = dir(seq[0], seq[1])
    for (i in 1 until seq.size - 1) {
        val d = dir(seq[i], seq[i + 1])
        if (d != prev) { bends++; prev = d }
    }
    return bends
}

private fun buildHamiltonianPathWithRestarts(
    n: Int,
    rng: Random,
    restarts: Int
): List<FlowPoint>? {
    repeat(restarts) {
        buildHamiltonianPathOnce(n, rng)?.let { path ->
            // prefer wigglier paths
            if (bendsCount(path) >= n - 1 || rng.nextFloat() < 0.25f) return path
        }
    }
    return null
}

private fun buildHamiltonianPathOnce(n: Int, rng: Random): List<FlowPoint>? {
    val total = n * n
    val visited = Array(n) { BooleanArray(n) }
    val start = FlowPoint(rng.nextInt(n), rng.nextInt(n))
    val path = ArrayList<FlowPoint>(total)

    fun onwardOptions(p: FlowPoint): Int =
        neighbors(p, n).count { !visited[it.y][it.x] }

    fun createsIsolatedPocket(nxt: FlowPoint): Boolean {
        visited[nxt.y][nxt.x] = true
        val iso = neighbors(nxt, n).any { q ->
            if (visited[q.y][q.x]) false
            else neighbors(q, n).all { visited[it.y][it.x] || it == nxt }
        }
        visited[nxt.y][nxt.x] = false
        return iso
    }

    fun step(p: FlowPoint): Boolean {
        path.add(p)
        visited[p.y][p.x] = true
        if (path.size == total) return true

        val nexts = neighbors(p, n)
            .filter { !visited[it.y][it.x] }
            .map { it to onwardOptions(it) }
            .sortedWith(compareBy({ it.second }, { rng.nextInt() })) // least-freedom first

        for ((nxt, _) in nexts) {
            if (!createsIsolatedPocket(nxt) && step(nxt)) return true
        }
        visited[p.y][p.x] = false
        path.removeAt(path.lastIndex) // compat for removeLast()
        return false
    }

    return if (step(start)) path else null
}

/** Always exists: row-wise zig-zag from (0,0) to (n-1,n-1). */
private fun snakeHamiltonian(n: Int): List<FlowPoint> = buildList(n * n) {
    for (y in 0 until n) {
        if (y % 2 == 0) for (x in 0 until n) add(FlowPoint(x, y))
        else for (x in n - 1 downTo 0) add(FlowPoint(x, y))
    }
}

/** Choose k-1 cut points at bends so each segment gets curvature; relax if needed. */
private fun cutIntoSegmentsPreferBends(
    path: List<FlowPoint>,
    cfg: HCfg,
    rng: Random
): List<List<FlowPoint>>? {
    val n = path.size
    val k = cfg.pairCount
    if (k <= 1) return listOf(path)

    // collect bend indices (i where dir(i-1->i) != dir(i->i+1))
    val bends = mutableListOf<Int>()
    for (i in 1 until n - 1) {
        if (dir(path[i - 1], path[i]) != dir(path[i], path[i + 1])) bends.add(i)
    }
    if (bends.isEmpty()) return null

    val cuts = IntArray(k + 1)
    cuts[0] = 0; cuts[k] = n

    // greedy place cuts near bends with spacing >= minSegLen
    var last = 0
    var placed = 1
    for (b in bends) {
        if (b - last >= cfg.minSegLen && n - b >= cfg.minSegLen && placed < k) {
            cuts[placed++] = b
            last = b
        }
        if (placed == k) break
    }
    if (placed < k) return null // not enough spaced bends; caller can fall back

    val segments = (0 until k).map { i -> path.subList(cuts[i], cuts[i + 1]).toList() }
    // Check bends per segment; if some fail, try small nudges
    var ok = segments.all { it.size >= cfg.minSegLen && bendsCount(it) >= cfg.minBends }
    if (!ok) {
        repeat(40) {
            val idx = rng.nextInt(1, k) // move a cut a bit
            val delta = if (rng.nextBoolean()) 1 else -1
            val newPos = (cuts[idx] + delta).coerceIn(cuts[idx - 1] + cfg.minSegLen, cuts[idx + 1] - cfg.minSegLen)
            if (newPos != cuts[idx]) {
                cuts[idx] = newPos
                val segs = (0 until k).map { i -> path.subList(cuts[i], cuts[i + 1]).toList() }
                if (segs.all { it.size >= cfg.minSegLen && bendsCount(it) >= cfg.minBends }) {
                    return segs
                }
            }
        }
        // give up; let caller fallback
        return null
    }
    return segments
}

/** Last resort equal split (still covers all cells). */
private fun equalCut(path: List<FlowPoint>, k: Int): List<List<FlowPoint>> {
    val n = path.size
    val segs = ArrayList<List<FlowPoint>>(k)
    var start = 0
    for (i in 0 until k) {
        val end = if (i == k - 1) n else ((i + 1) * n) / k
        segs.add(path.subList(start, end).toList())
        start = end
    }
    return segs
}




@Composable
fun FlowPuzzleScreen(
    difficulty: String = "easy",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/10",
    gridSize: Int = 6,
    flowPairs: List<Map<String, Any>> = emptyList(),
    correctAnswer: String = "",
    timeLimit: Int = 120000,
    onSubmitAnswer: (Boolean) -> Unit = {},
    fetchNextPuzzle: (Int) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var timeLeft by remember { mutableStateOf(timeLimit / 1000) } // Convert to seconds
    var score by remember { mutableStateOf(0) }
    var moves by remember { mutableStateOf(0) }
    var isCompleted by remember { mutableStateOf(false) }
    var showPathConflictDialog by remember { mutableStateOf(false) }
    var showGiveUpDialog by remember { mutableStateOf(false) }
    var conflictMessage by remember { mutableStateOf("") }
    var showingSolution by remember { mutableStateOf(false) }

    // Convert API pairs to FlowPair objects
    val convertedPairs = remember(flowPairs) {
        flowPairs.mapIndexed { index, pairMap ->
            val start = pairMap["start"] as Map<String, Int>
            val end = pairMap["end"] as Map<String, Int>

            FlowPair(
                id = pairMap["id"] as? String ?: "pair_$index",
                color = Color(android.graphics.Color.parseColor(pairMap["color"] as? String ?: "#E53E3E")),
                label = pairMap["label"] as? String ?: ('A' + index).toString(),
                start = FlowPoint(start["x"] ?: 0, start["y"] ?: 0),
                end = FlowPoint(end["x"] ?: 1, end["y"] ?: 1)
            )
        }
    }

    // Game state
    val paths = remember { mutableStateMapOf<String, FlowPath>() }
    var currentPath by remember { mutableStateOf<String?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf<FlowPoint?>(null) }

    val density = LocalDensity.current

    // Timer effect
    LaunchedEffect(timeLeft) {
        if (timeLeft > 0 && !isCompleted) {
            delay(1000)
            timeLeft--
        } else if (timeLeft <= 0 && !isCompleted) {
            // Time's up, show solution
            showingSolution = true
            showSolution(convertedPairs, paths)
        }
    }

    // Check completion
    LaunchedEffect(Unit) {
        snapshotFlow {
            val completed = paths.values.count { it.isComplete }
            val totalPairs = convertedPairs.size
            Pair(completed, totalPairs)
        }
            .distinctUntilChanged()
            .collect { (completed, totalPairs) ->
                if (!isCompleted && totalPairs > 0 && completed == totalPairs) {
                    isCompleted = true
                    val finalScore = calculateScore(timeLeft, moves, difficulty)
                    score = finalScore
                    delay(2000)
                    fetchNextPuzzle(finalScore)
                }
            }
    }

    LaunchedEffect(showingSolution) {
        if (showingSolution) {
            delay(3000)
            showingSolution = false
            fetchNextPuzzle(0)
        }
    }

    val hudRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = RvOnTone
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTime(timeLeft),
                    color = RvOnTone,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = difficulty.uppercase(),
                    color = RvOnTone.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }

            IconButton(onClick = {
                paths.clear()
                moves = 0
                isCompleted = false
                timeLeft = timeLimit / 1000
            }) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.reset),
                    tint = RvOnTone
                )
            }
        }
    }

    val infoRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Level: $level",
                color = RvOnTone,
                fontSize = 14.sp,
                maxLines = 1
            )
            Text(
                text = "Moves: $moves",
                color = RvOnTone,
                fontSize = 14.sp,
                maxLines = 1
            )
            Text(
                text = "Connected: ${paths.values.count { it.isComplete }}/${convertedPairs.size}",
                color = RvOnTone,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
    }

    val actionButtons: @Composable (Boolean) -> Unit = { stacked ->
        val clearBtn: @Composable (Modifier) -> Unit = { m ->
            Button(
                onClick = {
                    paths.clear()
                    moves = 0
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = m.heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.clear_all), color = RvInk, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
        val giveUpBtn: @Composable (Modifier) -> Unit = { m ->
            Button(
                onClick = {
                    showingSolution = true
                    showSolution(convertedPairs, paths)
                    score = 0
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A50)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = m.heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.give_up), color = RvInk, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
        val nextBtn: @Composable (Modifier) -> Unit = { m ->
            Button(
                onClick = {
                    // Skip to next puzzle with current score (or 0 if not completed)
                    val currentScore = if (isCompleted) score else 0
                    fetchNextPuzzle(currentScore)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF50C878)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = m.heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.next_puzzle), color = RvInk, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                clearBtn(Modifier.fillMaxWidth())
                giveUpBtn(Modifier.fillMaxWidth())
                nextBtn(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("flow_actions"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                clearBtn(Modifier.weight(1f))
                giveUpBtn(Modifier.weight(1f))
                nextBtn(Modifier.weight(1f))
            }
        }
    }

    // The board is a square sized from the space that is left (cell size derived from it), so
    // the drag surface always fits without scrolling. Result banners overlay the board.
    val boardSlot: @Composable (Modifier) -> Unit = { slotModifier ->
        BoxWithConstraints(modifier = slotModifier, contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            val cellSize = with(density) { (side / gridSize.coerceAtLeast(1)).toPx() }
            Box(
                modifier = Modifier
                    .size(side)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RvCanvas),
                contentAlignment = Alignment.Center
            ) {
                val textMeasurer = rememberTextMeasurer()

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("flow_board")
                    .pointerInput(cellSize) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val canvasWidth = size.width.toFloat()
                                val canvasHeight = size.height.toFloat()
                                val offsetX = (canvasWidth - gridSize * cellSize) / 2
                                val offsetY = (canvasHeight - gridSize * cellSize) / 2

                                val adjustedOffset = Offset(
                                    offset.x - offsetX,
                                    offset.y - offsetY
                                )

                                val gridPos = offsetToGrid(adjustedOffset, cellSize, gridSize)
                                Log.d("FlowPuzzle", "Drag start at grid: $gridPos")

                                val pair = convertedPairs.find {
                                    it.start == gridPos || it.end == gridPos
                                }

                                if (pair != null) {
                                    Log.d("FlowPuzzle", "Found pair: ${pair.label}")
                                    currentPath = pair.id
                                    isDragging = true
                                    dragStart = gridPos
                                    paths[pair.id] = FlowPath(mutableStateListOf(gridPos))
                                    moves++
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (isDragging && currentPath != null) {
                                    val canvasWidth = size.width.toFloat()
                                    val canvasHeight = size.height.toFloat()
                                    val offsetX = (canvasWidth - gridSize * cellSize) / 2
                                    val offsetY = (canvasHeight - gridSize * cellSize) / 2

                                    val adjustedOffset = Offset(
                                        change.position.x - offsetX,
                                        change.position.y - offsetY
                                    )

                                    val gridPos = offsetToGrid(adjustedOffset, cellSize, gridSize)
                                    val path = paths[currentPath!!] ?: return@detectDragGestures

                                    if (gridPos != path.points.lastOrNull()) {
                                        val moveResult = checkMove(gridPos, path, convertedPairs, paths, currentPath!!, gridSize)
                                        when (moveResult) {
                                            MoveResult.VALID -> {
                                                path.points.add(gridPos)
                                                Log.d("FlowPuzzle", "Added point: $gridPos to path ${currentPath}")
                                            }
                                            MoveResult.PATH_CONFLICT -> {
                                                // Clear current path and show dialog
                                                path.points.clear()
                                                path.points.add(dragStart!!)
                                                conflictMessage = "Path crossed another line! Starting over."
                                                showPathConflictDialog = true
                                            }
                                            MoveResult.ENDPOINT_CONFLICT -> {
                                                conflictMessage = "Cannot cross through another pair's endpoint!"
                                                showPathConflictDialog = true
                                            }
                                            else -> {
                                                // Invalid move, do nothing
                                            }
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                if (isDragging && currentPath != null) {
                                    val path = paths[currentPath!!] ?: return@detectDragGestures
                                    val pair = convertedPairs.find { it.id == currentPath!! }

                                    if (path != null && pair != null) {
                                        path.isComplete = path.points.contains(pair.start) &&
                                                path.points.contains(pair.end)
                                        Log.d("FlowPuzzle", "Path ${pair.label} complete: ${path.isComplete}")
                                    }
                                }
                                isDragging = false
                                currentPath = null
                                dragStart = null
                            }
                        )
                    }
            ) {
                drawFlowPuzzle(this, convertedPairs, paths, cellSize, gridSize, textMeasurer)
            }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showingSolution) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFB27D))
                    ) {
                        Text(
                            text = "Solution shown - Moving to next puzzle...",
                            modifier = Modifier.padding(12.dp),
                            color = RvInk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (isCompleted) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFA5D6A7))
                    ) {
                        Text(
                            text = "✓ Puzzle Completed! Score: $score",
                            modifier = Modifier.padding(12.dp),
                            color = RvInk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A3A))
            .statusBarsPadding()
    ) {
        val wide = maxWidth > maxHeight
        val pad = if (maxHeight < 600.dp) 8.dp else 16.dp
        if (wide) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 1200.dp)
                    .fillMaxSize()
                    .padding(pad),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                boardSlot(Modifier.weight(1f).fillMaxHeight())
                Column(
                    modifier = Modifier.weight(1f).widthIn(max = 420.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    hudRow()
                    infoRow()
                    Spacer(Modifier.weight(1f))
                    actionButtons(true)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .padding(pad),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                hudRow()
                infoRow()
                boardSlot(Modifier.weight(1f).fillMaxWidth())
                actionButtons(false)
            }
        }

        if (showPathConflictDialog) {
            AlertDialog(
                onDismissRequest = { showPathConflictDialog = false },
                title = { Text("Path Conflict!") },
                text = { Text(conflictMessage) },
                confirmButton = {
                    TextButton(onClick = { showPathConflictDialog = false }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
    }
}



private fun drawFlowPuzzle(
    drawScope: DrawScope,
    flowPairs: List<FlowPair>,
    paths: Map<String, FlowPath>,
    cellSize: Float,
    gridSize: Int,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val canvasWidth = drawScope.size.width
    val canvasHeight = drawScope.size.height
    val offsetX = (canvasWidth - gridSize * cellSize) / 2
    val offsetY = (canvasHeight - gridSize * cellSize) / 2

    // Draw grid background
    for (x in 0 until gridSize) {
        for (y in 0 until gridSize) {
            drawScope.drawRect(
                color = Color(0xFF3A3A5A),
                topLeft = Offset(
                    offsetX + x * cellSize + 1,
                    offsetY + y * cellSize + 1
                ),
                size = androidx.compose.ui.geometry.Size(cellSize - 2, cellSize - 2)
            )
        }
    }

    // Draw paths with thicker lines and better visibility
    for ((pairId, path) in paths) {
        val pair = flowPairs.find { it.id == pairId } ?: continue
        if (path.points.size > 1) {
            for (i in 0 until path.points.size - 1) {
                val start = path.points[i]
                val end = path.points[i + 1]

                // Draw path line
                drawScope.drawLine(
                    color = pair.color,
                    start = Offset(
                        offsetX + start.x * cellSize + cellSize / 2,
                        offsetY + start.y * cellSize + cellSize / 2
                    ),
                    end = Offset(
                        offsetX + end.x * cellSize + cellSize / 2,
                        offsetY + end.y * cellSize + cellSize / 2
                    ),
                    strokeWidth = cellSize * 0.3f
                )
            }
        }

        // Draw dots along the path for better visibility
        for (point in path.points) {
            if (point != pair.start && point != pair.end) {
                drawScope.drawCircle(
                    color = pair.color,
                    radius = cellSize * 0.15f,
                    center = Offset(
                        offsetX + point.x * cellSize + cellSize / 2,
                        offsetY + point.y * cellSize + cellSize / 2
                    )
                )
            }
        }
    }

    // Draw endpoints with letters
    for (pair in flowPairs) {
        val textStyle = TextStyle(
            color = RvInk,
            fontSize = (cellSize * 0.3f).sp,
            fontWeight = FontWeight.Bold
        )

        // Start point
        drawScope.drawCircle(
            color = pair.color,
            radius = cellSize * 0.4f,
            center = Offset(
                offsetX + pair.start.x * cellSize + cellSize / 2,
                offsetY + pair.start.y * cellSize + cellSize / 2
            )
        )

        // Start point border for better contrast
        drawScope.drawCircle(
            color = RvInk,
            radius = cellSize * 0.42f,
            center = Offset(
                offsetX + pair.start.x * cellSize + cellSize / 2,
                offsetY + pair.start.y * cellSize + cellSize / 2
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )

        // Draw letter on start point
        val textResult = textMeasurer.measure(pair.label, textStyle)
        drawScope.drawText(
            textMeasurer = textMeasurer,
            text = pair.label,
            style = textStyle,
            topLeft = Offset(
                offsetX + pair.start.x * cellSize + cellSize / 2 - textResult.size.width / 2,
                offsetY + pair.start.y * cellSize + cellSize / 2 - textResult.size.height / 2
            )
        )

        // End point
        drawScope.drawCircle(
            color = pair.color,
            radius = cellSize * 0.4f,
            center = Offset(
                offsetX + pair.end.x * cellSize + cellSize / 2,
                offsetY + pair.end.y * cellSize + cellSize / 2
            )
        )

        // End point border for better contrast
        drawScope.drawCircle(
            color = RvInk,
            radius = cellSize * 0.42f,
            center = Offset(
                offsetX + pair.end.x * cellSize + cellSize / 2,
                offsetY + pair.end.y * cellSize + cellSize / 2
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )

        // Draw letter on end point
        drawScope.drawText(
            textMeasurer = textMeasurer,
            text = pair.label,
            style = textStyle,
            topLeft = Offset(
                offsetX + pair.end.x * cellSize + cellSize / 2 - textResult.size.width / 2,
                offsetY + pair.end.y * cellSize + cellSize / 2 - textResult.size.height / 2
            )
        )
    }
}

private fun offsetToGrid(offset: Offset, cellSize: Float, gridSize: Int): FlowPoint {
    val x = (offset.x / cellSize).toInt().coerceIn(0, gridSize - 1)
    val y = (offset.y / cellSize).toInt().coerceIn(0, gridSize - 1)
    return FlowPoint(x, y)
}

private fun checkMove(
    newPoint: FlowPoint,
    currentPath: FlowPath,
    flowPairs: List<FlowPair>,
    allPaths: Map<String, FlowPath>,
    currentPairId: String,
    gridSize: Int
): MoveResult {
    // Check bounds
    if (newPoint.x < 0 || newPoint.x >= gridSize || newPoint.y < 0 || newPoint.y >= gridSize) {
        return MoveResult.INVALID_BOUNDS
    }

    if (currentPath.points.isEmpty()) return MoveResult.VALID

    val lastPoint = currentPath.points.last()
    val distance = abs(newPoint.x - lastPoint.x) + abs(newPoint.y - lastPoint.y)

    // Only allow adjacent moves (Manhattan distance = 1)
    if (distance != 1) return MoveResult.NOT_ADJACENT

    // Don't allow backtracking
    if (currentPath.points.size > 1 && currentPath.points[currentPath.points.size - 2] == newPoint) {
        return MoveResult.BACKTRACK
    }

    val currentPair = flowPairs.find { it.id == currentPairId }
    val isCurrentPairEndpoint = currentPair?.let {
        newPoint == it.start || newPoint == it.end
    } ?: false

    // Check if it's an endpoint of another pair
    for (pair in flowPairs) {
        if (pair.id != currentPairId && (pair.start == newPoint || pair.end == newPoint)) {
            return MoveResult.ENDPOINT_CONFLICT
        }
    }

    // Check if point is occupied by another path
    if (!isCurrentPairEndpoint) {
        for ((pairId, path) in allPaths) {
            if (pairId != currentPairId && path.points.contains(newPoint)) {
                return MoveResult.PATH_CONFLICT
            }
        }
    }

    return MoveResult.VALID
}

private fun showSolution(flowPairs: List<FlowPair>, paths: MutableMap<String, FlowPath>) {
    // Clear existing paths
    paths.clear()

    // Create simple solution paths (straight lines where possible)
    for (pair in flowPairs) {
        val solutionPath = findSimplePath(pair.start, pair.end)
        if (solutionPath.isNotEmpty()) {
            val flowPath = FlowPath(mutableStateListOf<FlowPoint>().apply { addAll(solutionPath) })
            flowPath.isComplete = true
            paths[pair.id] = flowPath
        }
    }
}

private fun findSimplePath(start: FlowPoint, end: FlowPoint): List<FlowPoint> {
    val path = mutableListOf<FlowPoint>()
    var current = start
    path.add(current)

    // Move horizontally first, then vertically
    while (current.x != end.x) {
        current = if (current.x < end.x) {
            FlowPoint(current.x + 1, current.y)
        } else {
            FlowPoint(current.x - 1, current.y)
        }
        path.add(current)
    }

    while (current.y != end.y) {
        current = if (current.y < end.y) {
            FlowPoint(current.x, current.y + 1)
        } else {
            FlowPoint(current.x, current.y - 1)
        }
        path.add(current)
    }

    return path
}

private fun calculateScore(timeLeft: Int, moves: Int, difficulty: String): Int {
    val baseScore = when(difficulty.lowercase()) {
        "easy" -> 100
        "medium" -> 200
        "hard" -> 300
        else -> 150
    }

    val timeBonus = timeLeft * 2
    val movesPenalty = moves * 1

    return maxOf(0, baseScore + timeBonus - movesPenalty)
}
package com.kreativekoala.riddleverse

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// --- spatial puzzles (feat/puzzles-spatial) ---
// Pixel Logic: nonogram with fill / X / move modes, drag painting, pinch-zoom + magnify toggle.

private const val MODE_FILL = 0
private const val MODE_X = 1
private const val MODE_MOVE = 2

@Composable
fun PixelLogicScreen(args: SpatialArgs) {
    val puzzle by produceState<NonogramPuzzle?>(null, args.seed, args.difficulty) {
        value = withContext(Dispatchers.Default) { NonogramEngine.generate(args.difficulty, args.seed) }
    }
    val p = puzzle
    if (p == null) { SpatialLoading(); return }
    PixelLogicBoard(args, p)
}

@Composable
private fun PixelLogicBoard(args: SpatialArgs, puzzle: NonogramPuzzle) {
    val n = puzzle.size
    val saved = rememberSaveable(args.seed) { mutableStateOf<String?>(null) }
    val game = remember(puzzle) { NonogramState(puzzle).also { g -> saved.value?.let { g.restore(it) } } }
    var tick by remember { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableIntStateOf(MODE_FILL) }
    var done by rememberSaveable(args.seed) { mutableStateOf(false) }
    var mistakes by remember { mutableStateOf<IntArray?>(null) }
    var zoom by rememberSaveable(args.seed) { mutableFloatStateOf(1f) }
    var panX by rememberSaveable(args.seed) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(args.seed) { mutableFloatStateOf(0f) }
    var toast by remember { mutableStateOf("") }

    fun commit() { tick++; saved.value = game.encode() }

    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val colFill = scheme.primary
    val colCell = scheme.surfaceVariant
    val colLine = scheme.outline
    val colText = scheme.onBackground
    val colDim = scheme.onBackground.copy(alpha = 0.35f)
    val colErr = scheme.error

    val rowStr = remember(puzzle) { puzzle.rowClues.map { c -> Array(c.size) { c[it].toString() } } }
    val colStr = remember(puzzle) { puzzle.colClues.map { c -> Array(c.size) { c[it].toString() } } }
    val maxRowClues = remember(puzzle) { puzzle.rowClues.maxOf { it.size } }
    val maxColClues = remember(puzzle) { puzzle.colClues.maxOf { it.size } }

    val paintTxt = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    }

    // satisfied rows/cols (recomputed only when state changes)
    val rowOk = remember(tick, puzzle) { BooleanArray(n) { game.isRowSatisfied(it) } }
    val colOk = remember(tick, puzzle) { BooleanArray(n) { game.isColSatisfied(it) } }
    val filled = remember(tick) { game.cells.count { it == 1 } }
    val target = remember(puzzle) { puzzle.solution.count { it } }

    LaunchedEffect(tick) {
        if (!done && game.filledMatches()) done = true
    }

    SpatialGameShell(
        title = "Pixel Logic",
        args = args,
        status = "$filled/$target",
        controls = {
            SpatialAction(Icons.Default.Undo, "Undo", enabled = game.canUndo()) {
                game.undo(); mistakes = null; commit()
            }
            SpatialAction(Icons.Default.Lightbulb, "Hint") {
                val i = game.hint()
                toast = if (i >= 0) "Revealed row ${i / n + 1}, column ${i % n + 1}" else "Nothing left to reveal"
                mistakes = null; commit()
            }
            SpatialAction(Icons.Default.FactCheck, "Check") {
                val m = game.mistakes()
                mistakes = m
                toast = if (m.isEmpty()) "No mistakes so far" else "${m.size} wrong cell(s) outlined"
            }
            SpatialAction(Icons.Default.Delete, "Clear", enabled = game.cells.any { it != 0 }) {
                game.clear(); mistakes = null; commit()
            }
            SpatialAction(Icons.Default.SkipNext, "Skip") { args.onFinished(false, 0) }
        }
    ) {
        // mode row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeChip("Fill", Icons.Default.CropSquare, mode == MODE_FILL, Modifier.weight(1f)) { mode = MODE_FILL }
            ModeChip("X mark", Icons.Default.Close, mode == MODE_X, Modifier.weight(1f)) { mode = MODE_X }
            ModeChip("Move", Icons.Default.OpenWith, mode == MODE_MOVE, Modifier.weight(1f)) { mode = MODE_MOVE }
        }
        if (toast.isNotEmpty()) {
            Text(
                toast, fontSize = 12.sp, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clipToBounds()
        ) {
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val clueBase = with(density) { 13.dp.toPx() }
            val bandW0 = maxRowClues * clueBase
            val bandH0 = maxColClues * clueBase
            val baseCell = min((wPx - bandW0) / n, (hPx - bandH0) / n)
            val minTouch = with(density) { 48.dp.toPx() }
            val maxZoom = max(1f, min(4f, minTouch / baseCell))

            fun clueUnit(z: Float) = clueBase * (1f + (z - 1f) * 0.4f)
            fun clampPan(z: Float) {
                val cell = baseCell * z
                val viewW = wPx - maxRowClues * clueUnit(z)
                val viewH = hPx - maxColClues * clueUnit(z)
                panX = panX.coerceIn(min(0f, viewW - n * cell), 0f)
                panY = panY.coerceIn(min(0f, viewH - n * cell), 0f)
            }
            fun zoomAbout(newZoom: Float, cx: Float, cy: Float) {
                val z = newZoom.coerceIn(1f, maxZoom)
                val oldCell = baseCell * zoom
                val oldX0 = maxRowClues * clueUnit(zoom) + panX
                val oldY0 = maxColClues * clueUnit(zoom) + panY
                val gx = (cx - oldX0) / oldCell
                val gy = (cy - oldY0) / oldCell
                zoom = z
                val newCell = baseCell * z
                panX = cx - gx * newCell - maxRowClues * clueUnit(z)
                panY = cy - gy * newCell - maxColClues * clueUnit(z)
                clampPan(z)
                tick++
            }

            // Re-clamp when the viewport changes (rotation / resize)
            LaunchedEffect(wPx, hPx) { clampPan(zoom); tick++ }

            fun cellAt(x: Float, y: Float): Int {
                val cell = baseCell * zoom
                val cu = clueUnit(zoom)
                val gx = x - (maxRowClues * cu + panX)
                val gy = y - (maxColClues * cu + panY)
                if (x < maxRowClues * cu || y < maxColClues * cu) return -1
                val c = floor(gx / cell).toInt(); val r = floor(gy / cell).toInt()
                return if (r in 0 until n && c in 0 until n) r * n + c else -1
            }

            val boardDesc = "Nonogram, $n by $n. $filled of $target cells filled."

            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = boardDesc; stateDescription = "Mode " + listOf("fill", "cross", "move")[mode] }
                    .pointerInput(puzzle, baseCell, maxZoom) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val startCell = cellAt(down.position.x, down.position.y)
                            var strokeActive = false
                            var paintVal = 0
                            var startOld = 0
                            var axis = 0 // 1 = along row, 2 = along column
                            var multi = false
                            var lastPos = down.position
                            val slop = viewConfiguration.touchSlop
                            if (mode != MODE_MOVE && startCell >= 0) {
                                val want = if (mode == MODE_FILL) 1 else 2
                                startOld = game.cells[startCell]
                                paintVal = if (startOld == want) 0 else want
                                game.beginStroke(); game.paint(startCell, paintVal)
                                strokeActive = true; mistakes = null; tick++
                            }
                            while (true) {
                                val ev = awaitPointerEvent()
                                val pressed = ev.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    if (strokeActive) { game.cancelStroke(); strokeActive = false; tick++ }
                                    multi = true
                                    val zc = ev.calculateZoom()
                                    val pc = ev.calculatePan()
                                    val ctr = ev.calculateCentroid()
                                    panX += pc.x; panY += pc.y
                                    if (zc != 1f) zoomAbout(zoom * zc, ctr.x, ctr.y) else { clampPan(zoom); tick++ }
                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                    continue
                                }
                                val ch = pressed[0]
                                if (multi) { ch.consume(); continue }
                                if (strokeActive) {
                                    val dx = ch.position.x - down.position.x
                                    val dy = ch.position.y - down.position.y
                                    if (axis == 0 && (abs(dx) > slop || abs(dy) > slop)) axis = if (abs(dx) >= abs(dy)) 1 else 2
                                    var x = ch.position.x; var y = ch.position.y
                                    val sc = startCell
                                    // lock to the row/column the stroke started on
                                    if (axis == 1) y = cellCenterY(sc, n, baseCell * zoom, maxColClues * clueUnit(zoom) + panY)
                                    if (axis == 2) x = cellCenterX(sc, n, baseCell * zoom, maxRowClues * clueUnit(zoom) + panX)
                                    val c = cellAt(x, y)
                                    if (c >= 0 && c != startCell) {
                                        val cur = game.cells[c]
                                        val ok = if (paintVal == 0) cur == startOld else cur == 0
                                        if (ok && cur != paintVal) { game.paint(c, paintVal); tick++ }
                                    }
                                } else {
                                    // one-finger pan (Move mode, or drag started on the clue bands)
                                    val d = ch.position - lastPos
                                    panX += d.x; panY += d.y; clampPan(zoom); tick++
                                }
                                lastPos = ch.position
                                ch.consume()
                            }
                            if (strokeActive) { game.endStroke(); commit() }
                        }
                    }
            ) {
                @Suppress("UNUSED_EXPRESSION") tick // subscribe to state changes
                val z = zoom
                val cell = baseCell * z
                val cu = clueUnit(z)
                val bandW = maxRowClues * cu
                val bandH = maxColClues * cu
                val gx0 = bandW + panX
                val gy0 = bandH + panY
                val c0 = max(0, floor(-panX / cell).toInt())
                val c1 = min(n - 1, floor((size.width - gx0) / cell).toInt())
                val r0 = max(0, floor(-panY / cell).toInt())
                val r1 = min(n - 1, floor((size.height - gy0) / cell).toInt())

                clipRect(left = bandW, top = bandH) {
                    val pad = cell * 0.06f
                    val rad = CornerRadius(cell * 0.12f)
                    for (r in r0..r1) for (c in c0..c1) {
                        val x = gx0 + c * cell; val y = gy0 + r * cell
                        val v = game.cells[r * n + c]
                        drawRoundRect(
                            if (v == 1) colFill else colCell,
                            Offset(x + pad, y + pad), Size(cell - 2 * pad, cell - 2 * pad), rad
                        )
                        if (v == 2) {
                            val m = cell * 0.3f
                            drawLine(colDim, Offset(x + m, y + m), Offset(x + cell - m, y + cell - m), strokeWidth = cell * 0.08f)
                            drawLine(colDim, Offset(x + cell - m, y + m), Offset(x + m, y + cell - m), strokeWidth = cell * 0.08f)
                        }
                    }
                    // block separators every 5 cells
                    for (i in 0..n step 5) {
                        val gxl = gx0 + i * cell; val gyl = gy0 + i * cell
                        drawLine(colLine, Offset(gxl, gy0), Offset(gxl, gy0 + n * cell), strokeWidth = 2f)
                        drawLine(colLine, Offset(gx0, gyl), Offset(gx0 + n * cell, gyl), strokeWidth = 2f)
                    }
                    mistakes?.let { m ->
                        for (i in m) {
                            val r = i / n; val c = i % n
                            if (r in r0..r1 && c in c0..c1) {
                                drawRoundRect(
                                    colErr, Offset(gx0 + c * cell + pad, gy0 + r * cell + pad),
                                    Size(cell - 2 * pad, cell - 2 * pad), rad,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = cell * 0.1f)
                                )
                            }
                        }
                    }
                }

                // sticky clue bands
                val fs = cu * 0.78f
                drawIntoCanvas { cv ->
                    val nc = cv.nativeCanvas
                    paintTxt.textSize = fs
                    nc.save()
                    nc.clipRect(0f, bandH, bandW, size.height)
                    for (r in r0..r1) {
                        val clues = rowStr[r]
                        paintTxt.color = (if (rowOk[r]) colDim else colText).toArgb()
                        val cy = gy0 + r * cell + cell / 2f + fs * 0.35f
                        for (k in clues.indices) {
                            val cx = bandW - (clues.size - k - 0.5f) * cu
                            nc.drawText(clues[k], cx, cy, paintTxt)
                        }
                    }
                    nc.restore()
                    nc.save()
                    nc.clipRect(bandW, 0f, size.width, bandH)
                    for (c in c0..c1) {
                        val clues = colStr[c]
                        paintTxt.color = (if (colOk[c]) colDim else colText).toArgb()
                        val cx = gx0 + c * cell + cell / 2f
                        for (k in clues.indices) {
                            val cy = bandH - (clues.size - k - 0.5f) * cu + fs * 0.35f
                            nc.drawText(clues[k], cx, cy, paintTxt)
                        }
                    }
                    nc.restore()
                }
            }

            // magnify toggle floats over the board corner (48dp target)
            FilledTonalIconButton(
                onClick = {
                    if (zoom < maxZoom - 0.01f) zoomAbout(maxZoom, wPx / 2f, hPx / 2f) else zoomAbout(1f, wPx / 2f, hPx / 2f)
                },
                modifier = Modifier.align(Alignment.BottomEnd).size(48.dp)
            ) {
                Icon(
                    if (zoom < maxZoom - 0.01f) Icons.Default.ZoomIn else Icons.Default.ZoomOut,
                    contentDescription = if (zoom < maxZoom - 0.01f) "Magnify board" else "Show whole board"
                )
            }
        }
    }

    if (done) {
        val s = game.score()
        SpatialResultDialog(
            success = true, title = "Picture complete!",
            detail = "Hints used: ${game.hintsUsed}.  Score $s",
            stars = when { game.hintsUsed == 0 -> 3; game.hintsUsed <= 2 -> 2; else -> 1 },
            primaryLabel = "Continue", onPrimary = { args.onFinished(true, s) }
        )
    }
}

private fun cellCenterX(cell: Int, n: Int, size: Float, x0: Float): Float = x0 + (cell % n) * size + size / 2f
private fun cellCenterY(cell: Int, n: Int, size: Float, y0: Float): Float = y0 + (cell / n) * size + size / 2f

@Composable
private fun ModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = modifier.heightIn(min = 48.dp)
    )
}

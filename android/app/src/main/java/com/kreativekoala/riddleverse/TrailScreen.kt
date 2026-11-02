package com.kreativekoala.riddleverse

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- trail (feat/game-trail) ---
// Trail: drag one line from dot 1 through the dots in order, covering every cell once. Drag back to undo.

/** Registry data for Trail; SpatialPuzzles.kt only forwards to this object. */
object TrailGame {
    const val TYPE = "trail"
    const val SCREEN_OPTION = "Trail Screen"
    const val HINT = "Start at dot 1 and join the dots in order, covering every square. Squares next to a wall have fewer ways through, so start there."

    val category = QuizCategory("Trail", "Draw one line through every square", Icons.Default.Route, TYPE, "Logic")

    val tutorial = SimpleTutorialContent(
        title = "Trail", emoji = "🧵",
        instruction = "Drag from dot 1 to dot 2, then 3, and so on, in order. Your one line must pass through every square exactly once, and it can never cross a wall. Drag back along the line to undo. With a screen reader, tap a square next to the end of the line to extend it.",
        tip = "Squares in a corner or beside walls have only one or two ways through. Start there. Hint shows the next few squares.",
        backgroundColor = listOf(Color(0xFF7F00FF), Color(0xFFE100FF))
    )
}

@Composable
fun TrailScreen(args: SpatialArgs) {
    val puzzle by produceState<TrailPuzzle?>(null, args.seed, args.difficulty) {
        value = withContext(Dispatchers.Default) { TrailEngine.generate(args.difficulty, args.seed) }
    }
    val p = puzzle
    if (p == null) { SpatialLoading(); return }
    TrailBoard(args, p)
}

@Composable
private fun TrailBoard(args: SpatialArgs, p: TrailPuzzle) {
    val saved = rememberSaveable(args.seed) { mutableStateOf<String?>(null) }
    val game = remember(p) { TrailState(p).also { g -> saved.value?.let { g.restore(it) } } }
    var tick by remember { mutableIntStateOf(0) }
    var done by rememberSaveable(args.seed) { mutableStateOf(false) }
    var showResult by rememberSaveable(args.seed) { mutableStateOf(false) }
    var hintCells by remember { mutableStateOf(IntArray(0)) }
    var message by remember { mutableStateOf("") }
    val win = remember { Animatable(if (done) 1f else 0f) }
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_EXPRESSION") tick

    val ctx = LocalContext.current
    val screenReader = remember {
        (ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true
    }

    fun changed() { tick++; saved.value = game.encode(); hintCells = IntArray(0); message = "" }
    LaunchedEffect(tick) { if (!done && game.solved()) done = true }
    LaunchedEffect(done) {
        if (done) { win.animateTo(1f, tween(1400)); showResult = true }
    }

    val scheme = MaterialTheme.colorScheme
    val colFloor = scheme.surfaceVariant
    val colWall = scheme.onSurface
    val colLine = scheme.primary
    val colHint = scheme.tertiary
    val colDotDone = Color(0xFF2E7D32)
    val colGold = Color(0xFFFFC107)
    val colDotOff = scheme.onSurface
    val colDotText = scheme.surface
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    SpatialGameShell(
        title = "Trail",
        args = args,
        status = "Cells ${game.length}/${p.cells}",
        controls = {
            SpatialAction(Icons.Default.Undo, "Undo", enabled = game.length > 1 && !done) {
                if (game.undo()) changed()
            }
            SpatialAction(Icons.Default.Refresh, "Reset", enabled = !done) { game.reset(); changed() }
            SpatialAction(Icons.Default.Lightbulb, "Hint", enabled = !done) {
                val h = game.hint(3)
                saved.value = game.encode()
                hintCells = h
                message = if (h.isEmpty()) "" else "The next squares are marked"
                tick++
            }
            SpatialAction(Icons.Default.SkipNext, "Skip") { args.onFinished(false, 0) }
        }
    ) {
        Text(
            "Dot ${minOf(game.dotsVisited + 1, game.totalDots)} of ${game.totalDots} next  |  $message",
            fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 2,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
        val desc = "Trail puzzle, ${p.n} by ${p.n}. ${game.length} of ${p.cells} squares covered. " +
            "Dot ${minOf(game.dotsVisited + 1, game.totalDots)} of ${game.totalDots} is next."

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val side = minOf(maxWidth, maxHeight)
            Box(Modifier.size(side)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = desc }
                        .pointerInput(p, done, screenReader) {
                            if (done || screenReader) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val cell = size.width.toFloat() / p.n
                                var last = down.position
                                var moved = false
                                val c0 = TrailGeometry.cellAt(down.position.x, down.position.y, cell, p.n)
                                // touching the line cuts back to that square; touching next to the end extends it
                                if (c0 >= 0 && game.advanceTo(c0)) moved = true
                                down.consume()
                                if (moved) changed()
                                drag(down.id) { change ->
                                    change.consume()
                                    val pos = change.position
                                    val cells = TrailGeometry.cellsAlong(last.x, last.y, pos.x, pos.y, cell, p.n)
                                    var any = false
                                    for (i in cells.indices) {
                                        val c = cells[i]
                                        if (c == game.head) continue
                                        // the final square only counts once the finger is clearly inside it
                                        if (i == cells.lastIndex && TrailGeometry.stickyHit(pos.x, pos.y, game.head, cell, p.n)) continue
                                        if (game.advanceTo(c)) any = true
                                    }
                                    last = pos
                                    if (any) changed()
                                }
                            }
                        }
                ) {
                    tick
                    val cell = size.width / p.n
                    val rad = CornerRadius(cell * 0.12f)
                    val lineW = cell * 0.36f
                    // squares
                    for (i in 0 until p.cells) {
                        val x = (i % p.n) * cell; val y = (i / p.n) * cell
                        val tint = if (game.onPath(i)) colLine.copy(alpha = 0.18f) else colFloor
                        drawRoundRect(tint, Offset(x + 1.5f, y + 1.5f), Size(cell - 3f, cell - 3f), rad)
                    }
                    // the line
                    val cells = game.pathCells()
                    fun center(i: Int) = Offset((i % p.n + 0.5f) * cell, (i / p.n + 0.5f) * cell)
                    val line = Path()
                    cells.forEachIndexed { k, c -> val o = center(c); if (k == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y) }
                    if (cells.size > 1) {
                        drawPath(line, colLine, style = Stroke(lineW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    drawCircle(colLine, cell * 0.27f, center(game.head))
                    drawCircle(Color.White.copy(alpha = 0.55f), cell * 0.1f, center(game.head))
                    // hint: faint trail from the end of the line over the next squares
                    if (hintCells.isNotEmpty()) {
                        val hp = Path()
                        val h0 = center(game.head); hp.moveTo(h0.x, h0.y)
                        hintCells.forEach { c -> val o = center(c); hp.lineTo(o.x, o.y) }
                        drawPath(hp, colHint.copy(alpha = 0.55f), style = Stroke(cell * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        hintCells.forEach { c -> drawCircle(colHint, cell * 0.13f, center(c)) }
                    }
                    // walls
                    val wallW = cell * 0.11f
                    for (i in 0 until p.cells) {
                        val x = (i % p.n) * cell; val y = (i / p.n) * cell
                        if (p.wallE[i]) drawLine(colWall, Offset(x + cell, y + 2f), Offset(x + cell, y + cell - 2f), wallW, cap = StrokeCap.Round)
                        if (p.wallS[i]) drawLine(colWall, Offset(x + 2f, y + cell), Offset(x + cell - 2f, y + cell), wallW, cap = StrokeCap.Round)
                    }
                    // win animation: a gold light runs along the finished line
                    if (win.value > 0f && cells.size > 1) {
                        val pm = PathMeasure(); pm.setPath(line, false)
                        val dist = pm.length * win.value
                        val seg = Path(); pm.getSegment(0f, dist, seg, true)
                        drawPath(seg, colGold, style = Stroke(lineW * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawCircle(Color.White, cell * (0.18f + 0.1f * (1f - win.value)), pm.getPosition(dist))
                    }
                    // dots
                    paint.textSize = cell * 0.34f
                    for (k in 0 until p.waypoints.size) {
                        val o = center(p.waypoints[k])
                        val visited = k < game.dotsVisited
                        val fill = if (win.value >= 1f) colGold else if (visited) colDotDone else colDotOff
                        if (k == game.dotsVisited && !done) {
                            drawCircle(colHint, cell * 0.4f, o, style = Stroke(cell * 0.06f))
                        }
                        drawCircle(fill, cell * 0.3f, o)
                        paint.color = if (win.value >= 1f) android.graphics.Color.BLACK else if (visited) android.graphics.Color.WHITE else colDotText.toArgb()
                        val fm = paint.fontMetrics
                        drawContext.canvas.nativeCanvas.drawText("${k + 1}", o.x, o.y - (fm.ascent + fm.descent) / 2f, paint)
                    }
                }

                if (screenReader) {
                    // TalkBack: every square is a button; tap a square next to the end of the line to extend it
                    Column(Modifier.fillMaxSize()) {
                        for (r in 0 until p.n) {
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                for (c in 0 until p.n) {
                                    val i = r * p.n + c
                                    Box(
                                        Modifier.weight(1f).fillMaxHeight()
                                            .semantics(mergeDescendants = true) {
                                                role = Role.Button
                                                contentDescription = describeSquare(p, game, i)
                                            }
                                            .clickable(onClickLabel = "Extend the line to this square") {
                                                if (!done) {
                                                    if (game.tap(i)) changed() else message = "Choose a square next to the end of the line"
                                                    tick++
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (done && showResult) {
        val s = game.score()
        SpatialResultDialog(
            success = true, title = "Trail complete!",
            detail = "Every square covered with one line. Score $s",
            stars = game.stars(), primaryLabel = "Continue", onPrimary = { args.onFinished(true, s) },
            secondaryLabel = "Play again",
            onSecondary = {
                scope.launch { win.snapTo(0f) }
                game.reset(); done = false; showResult = false; changed()
            }
        )
    }
}

private fun describeSquare(p: TrailPuzzle, g: TrailState, i: Int): String {
    val sb = StringBuilder("Row ${i / p.n + 1}, column ${i % p.n + 1}. ")
    val d = p.dotAt(i)
    if (d >= 0) sb.append("Dot ${d + 1}. ")
    sb.append(
        when {
            i == g.head -> "End of your line."
            g.onPath(i) -> "On your line. Tap to cut the line back to here."
            p.adjacent(g.head, i) && g.canStep(g.head, i) -> "Next to the end of your line. Tap to extend."
            else -> "Empty."
        }
    )
    for ((dr, dc, name) in listOf(Triple(0, 1, "right"), Triple(1, 0, "below"))) {
        val r = i / p.n + dr; val c = i % p.n + dc
        if (r < p.n && c < p.n && p.blocked(i, r * p.n + c)) sb.append(" Wall ").append(name).append('.')
    }
    return sb.toString()
}

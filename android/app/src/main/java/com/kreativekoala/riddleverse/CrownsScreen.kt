package com.kreativekoala.riddleverse

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

// --- crowns (feat/game-crowns) ---
// Crowns: place one crown per row, column and coloured region; no two crowns touch. Every region has a colour AND a
// pattern, so the board never relies on colour alone, and every cell reads out row, column, region, pattern and state.

/** Everything the shared spatial registry needs to know about Crowns, kept in one place. */
object CrownsGame {
    const val TYPE = "crowns"
    const val SCREEN_NAME = "Crowns Screen"
    const val HINT = "Each colour holds exactly one crown. A colour that only fits in one row or column rules out the rest of that line."

    val category = listOf(
        QuizCategory("Crowns", "One crown per row, column and colour", Icons.Default.EmojiEvents, TYPE, "Logic")
    )

    val tutorial = SimpleTutorialContent(
        title = "Crowns", emoji = "👑",
        instruction = "Place exactly one crown in every row, every column and every coloured region. No two crowns may touch, not even diagonally. Tap a square once for an X (a square that cannot hold a crown), twice for a crown, a third time to clear it.",
        tip = "A colour with only one square left must hold a crown. Crowns that break a rule are outlined in red with a warning mark.",
        backgroundColor = listOf(Color(0xFF7F5AF0), Color(0xFFE0A100))
    )
}

private class CrownRegionStyle(val color: Color, val colorName: String, val pattern: Int, val patternName: String)

// Orange/sky/green/yellow/blue/vermilion/pink are colour-blind-safe (Okabe-Ito family); grey and lavender complete nine.
// Patterns carry the identity when hues are hard to tell apart.
private val RegionStyles = listOf(
    CrownRegionStyle(Color(0xFFF2B05A), "orange", 0, "plain"),
    CrownRegionStyle(Color(0xFF8CCBEF), "sky blue", 1, "diagonal stripes"),
    CrownRegionStyle(Color(0xFF6FCBA5), "green", 2, "dots"),
    CrownRegionStyle(Color(0xFFF3EA7A), "yellow", 3, "horizontal lines"),
    CrownRegionStyle(Color(0xFF6F9FDB), "blue", 4, "vertical lines"),
    CrownRegionStyle(Color(0xFFE88B5C), "vermilion", 5, "crosshatch"),
    CrownRegionStyle(Color(0xFFE3A6C9), "pink", 6, "back stripes"),
    CrownRegionStyle(Color(0xFFC4C4C4), "grey", 7, "checks"),
    CrownRegionStyle(Color(0xFFC1B5F0), "lavender", 8, "rings")
)

private val Ink = Color(0xFF1B1B1F)
private val ErrRed = Color(0xFFD32F2F)

@Composable
fun CrownsScreen(args: SpatialArgs) {
    val puzzle by produceState<CrownsPuzzle?>(null, args.seed, args.difficulty) {
        value = withContext(Dispatchers.Default) { CrownsEngine.generate(args.difficulty, args.seed) }
    }
    val p = puzzle
    if (p == null) { SpatialLoading(); return }
    CrownsBoard(args, p)
}

@Composable
private fun CrownsBoard(args: SpatialArgs, puzzle: CrownsPuzzle) {
    val n = puzzle.n
    val saved = rememberSaveable(args.seed) { mutableStateOf<String?>(null) }
    val game = remember(puzzle) { CrownsState(puzzle).also { g -> saved.value?.let { g.restore(it) } } }
    var tick by remember { mutableIntStateOf(0) }
    var done by rememberSaveable(args.seed) { mutableStateOf(false) }
    var showResult by rememberSaveable(args.seed) { mutableStateOf(false) }
    var hintCell by remember { mutableIntStateOf(-1) }
    var toast by remember { mutableStateOf("") }
    val win = remember { Animatable(0f) }

    fun commit() { tick++; saved.value = game.encode() }

    @Suppress("UNUSED_EXPRESSION") tick
    val conflicts = game.conflicts()
    val crowns = game.crownCount()
    val anyConflict = conflicts.any { it }

    LaunchedEffect(tick) { if (!done && game.isSolved()) done = true }
    LaunchedEffect(done) {
        if (done) {
            if (showResult) { win.snapTo(1f) } else {
                win.animateTo(1f, tween(1100))
                showResult = true
            }
        }
    }

    SpatialGameShell(
        title = "Crowns",
        args = args,
        status = "$crowns/$n",
        controls = {
            SpatialAction(Icons.Default.Undo, "Undo", enabled = game.canUndo() && !done) {
                game.undo(); hintCell = -1; toast = ""; commit()
            }
            SpatialAction(Icons.Default.Lightbulb, "Hint", enabled = !done) {
                val h = game.hint()
                if (h != null) {
                    hintCell = h.cell
                    val where = "row ${h.cell / n + 1}, column ${h.cell % n + 1}"
                    toast = if (h.removed) "That crown at $where was wrong, so it was removed" else "A crown belongs at $where"
                }
                commit()
            }
            SpatialAction(Icons.Default.Delete, "Clear", enabled = !done && game.cells.any { it != 0 }) {
                game.clear(); hintCell = -1; toast = ""; commit()
            }
            SpatialAction(Icons.Default.SkipNext, "Skip") { args.onFinished(false, 0) }
        }
    ) {
        val message = when {
            toast.isNotEmpty() -> toast
            anyConflict -> "Crowns outlined in red share a row, column or colour, or touch"
            else -> "Tap: X, then crown, then clear"
        }
        Text(
            message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).semantics { stateDescription = message }
        )

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val side = min(maxWidth.value, maxHeight.value).dp
            val cell = side / n
            Column(
                Modifier.size(side).semantics { collectionInfo = CollectionInfo(rowCount = n, columnCount = n) }
            ) {
                for (r in 0 until n) {
                    Row {
                        for (c in 0 until n) {
                            val i = r * n + c
                            val value = game.cells[i]
                            val wave = if (value == 2) crownWave(win.value, r, c, n) else 1f
                            CrownCell(
                                r = r, c = c, n = n, regions = puzzle.regions, value = value,
                                conflict = conflicts[i], hinted = hintCell == i, waveScale = wave,
                                size = cell, enabled = !done,
                                onTap = {
                                    game.tap(i); hintCell = -1; toast = ""; commit()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResult) {
        val s = game.score()
        SpatialResultDialog(
            success = true, title = "Crowned!",
            detail = "Hints used: ${game.hintsUsed}.  Score $s",
            stars = game.stars(),
            primaryLabel = "Continue", onPrimary = { args.onFinished(true, s) }
        )
    }
}

/** A diagonal wave of bounces across the crowns as the puzzle is won. 1 = normal size. */
private fun crownWave(progress: Float, r: Int, c: Int, n: Int): Float {
    if (progress <= 0f || progress >= 1f) return 1f
    val phase = (r + c) / (2f * n)
    val t = ((progress * 1.5f) - phase).coerceIn(0f, 1f)
    return 1f + 0.35f * sin(PI.toFloat() * t)
}

@Composable
private fun CrownCell(
    r: Int, c: Int, n: Int, regions: IntArray, value: Int, conflict: Boolean, hinted: Boolean,
    waveScale: Float, size: Dp, enabled: Boolean, onTap: () -> Unit
) {
    val region = regions[r * n + c]
    val style = RegionStyles[region % RegionStyles.size]
    // crown pops in / out with a spring
    val appear by animateFloatAsState(
        if (value == 2) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "crown"
    )
    val markAlpha by animateFloatAsState(if (value == 1) 1f else 0f, tween(120), label = "mark")

    // thick borders where the neighbour belongs to another region (or the board ends)
    fun diff(rr: Int, cc: Int) = rr !in 0 until n || cc !in 0 until n || regions[rr * n + cc] != region
    val top = diff(r - 1, c); val bottom = diff(r + 1, c); val left = diff(r, c - 1); val right = diff(r, c + 1)

    val stateText = when (value) { 2 -> "crown"; 1 -> "marked empty"; else -> "empty" }
    val next = when (value) { 0 -> "Place mark"; 1 -> "Place crown"; else -> "Clear" }
    val desc = "Row ${r + 1}, column ${c + 1}, ${style.colorName} region" +
        (if (style.pattern != 0) ", ${style.patternName} pattern" else "") +
        ", $stateText" + (if (conflict) ", conflict" else "")

    Box(
        Modifier
            .size(size)
            .semantics {
                contentDescription = desc
                role = Role.Button
                collectionItemInfo = CollectionItemInfo(r, 1, c, 1)
            }
            .clickable(enabled = enabled, onClickLabel = next, onClick = onTap)
            .drawBehind {
                val s = this.size.minDimension
                drawRect(style.color)
                drawRegionPattern(style.pattern, s)
                val thick = s * 0.085f; val thin = s * 0.02f
                val edge = Ink.copy(alpha = 0.85f)
                val faint = Ink.copy(alpha = 0.22f)
                drawLine(if (top) edge else faint, Offset(0f, 0f), Offset(s, 0f), if (top) thick else thin)
                drawLine(if (bottom) edge else faint, Offset(0f, s), Offset(s, s), if (bottom) thick else thin)
                drawLine(if (left) edge else faint, Offset(0f, 0f), Offset(0f, s), if (left) thick else thin)
                drawLine(if (right) edge else faint, Offset(s, 0f), Offset(s, s), if (right) thick else thin)
                if (conflict) {
                    drawRect(ErrRed.copy(alpha = 0.28f))
                    drawRect(ErrRed, Offset(s * 0.06f, s * 0.06f), Size(s * 0.88f, s * 0.88f), style = Stroke(s * 0.07f))
                    // warning tick in the corner: shape, not just colour
                    drawLine(ErrRed, Offset(s * 0.82f, s * 0.12f), Offset(s * 0.82f, s * 0.30f), s * 0.07f)
                    drawCircle(ErrRed, s * 0.04f, Offset(s * 0.82f, s * 0.38f))
                }
                if (hinted) {
                    drawRect(Color.White, Offset(s * 0.1f, s * 0.1f), Size(s * 0.8f, s * 0.8f), style = Stroke(s * 0.06f))
                }
                if (markAlpha > 0f) {
                    val m = s * 0.3f
                    val col = Ink.copy(alpha = 0.6f * markAlpha)
                    drawLine(col, Offset(m, m), Offset(s - m, s - m), s * 0.07f)
                    drawLine(col, Offset(s - m, m), Offset(m, s - m), s * 0.07f)
                }
                if (appear > 0.01f) {
                    scale(appear * waveScale, Offset(s / 2f, s / 2f)) { drawCrown(s, if (conflict) ErrRed else Ink) }
                }
            }
    )
}

private fun DrawScope.drawCrown(s: Float, fill: Color) {
    val p = Path().apply {
        moveTo(s * 0.2f, s * 0.74f)
        lineTo(s * 0.2f, s * 0.32f)
        lineTo(s * 0.36f, s * 0.52f)
        lineTo(s * 0.5f, s * 0.24f)
        lineTo(s * 0.64f, s * 0.52f)
        lineTo(s * 0.8f, s * 0.32f)
        lineTo(s * 0.8f, s * 0.74f)
        close()
    }
    drawPath(p, fill)
    drawPath(p, Color.White, style = Stroke(s * 0.04f))
    drawCircle(Color.White, s * 0.035f, Offset(s * 0.5f, s * 0.64f))
}

private fun DrawScope.drawRegionPattern(pattern: Int, s: Float) {
    if (pattern == 0) return
    val ink = Ink.copy(alpha = 0.34f)
    val w = s * 0.045f
    clipRect(0f, 0f, s, s) {
        when (pattern) {
            1 -> diagonals(s, ink, w, forward = true)
            2 -> {
                for (i in 0 until 3) for (j in 0 until 3) {
                    val off = if (j % 2 == 0) 0f else s / 6f
                    drawCircle(ink, s * 0.065f, Offset(s * (i + 0.5f) / 3f + off - s / 12f, s * (j + 0.5f) / 3f))
                }
            }
            3 -> { var y = s * 0.2f; while (y < s) { drawLine(ink, Offset(0f, y), Offset(s, y), w); y += s * 0.2f } }
            4 -> { var x = s * 0.2f; while (x < s) { drawLine(ink, Offset(x, 0f), Offset(x, s), w); x += s * 0.2f } }
            5 -> { diagonals(s, ink, w, forward = true); diagonals(s, ink, w, forward = false) }
            6 -> diagonals(s, ink, w, forward = false)
            7 -> {
                val q = s / 4f
                for (i in 0 until 4) for (j in 0 until 4) if ((i + j) % 2 == 0) drawRect(ink, Offset(i * q, j * q), Size(q, q))
            }
            8 -> {
                drawCircle(ink, s * 0.17f, Offset(s * 0.28f, s * 0.28f), style = Stroke(w))
                drawCircle(ink, s * 0.17f, Offset(s * 0.72f, s * 0.72f), style = Stroke(w))
            }
        }
    }
}

private fun DrawScope.diagonals(s: Float, ink: Color, w: Float, forward: Boolean) {
    val gap = s * 0.24f
    var x = -s
    while (x <= s) {
        if (forward) drawLine(ink, Offset(x, s), Offset(x + s, 0f), w)
        else drawLine(ink, Offset(x, 0f), Offset(x + s, s), w)
        x += gap
    }
}

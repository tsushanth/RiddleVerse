package com.kreativekoala.riddleverse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// --- spatial puzzles (feat/puzzles-spatial) ---
// Piece Pack: drag pieces from the tray onto an 8x8 board, or tap a piece then tap the board.

@Composable
fun PiecePackScreen(args: SpatialArgs) {
    val saved = rememberSaveable(args.seed) { mutableStateOf<String?>(null) }
    val game = remember(args.seed, args.difficulty) {
        PiecePackState(args.seed, args.difficulty).also { g -> saved.value?.let { g.restore(it) } }
    }
    var tick by remember { mutableIntStateOf(0) }
    var done by rememberSaveable(args.seed) { mutableStateOf(false) }
    var selectedSlot by remember { mutableIntStateOf(-1) }
    var hintMove by remember { mutableStateOf<IntArray?>(null) }
    var dragSlot by remember { mutableIntStateOf(-1) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf("") }
    @Suppress("UNUSED_EXPRESSION") tick

    fun commit() { tick++; saved.value = game.encode() }

    val cfg = game.config
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val colEmpty = scheme.surfaceVariant
    val colGrid = scheme.outline
    val colGhostOk = scheme.primary.copy(alpha = 0.45f)
    val colGhostBad = scheme.error.copy(alpha = 0.35f)
    val colHint = scheme.tertiary

    val over = game.status != PiecePackStatus.PLAYING
    LaunchedEffect(tick) { if (game.status != PiecePackStatus.PLAYING) done = true }

    SpatialGameShell(
        title = "Piece Pack",
        args = args,
        status = "${game.linesCleared}/${cfg.targetLines} lines",
        controls = {
            SpatialAction(Icons.Default.Undo, "Undo", enabled = game.canUndo()) {
                game.undoMove(); done = false; selectedSlot = -1; hintMove = null; commit()
            }
            SpatialAction(Icons.Default.Refresh, "Restart") {
                game.restart(); done = false; selectedSlot = -1; hintMove = null; commit()
            }
            SpatialAction(Icons.Default.Lightbulb, "Hint", enabled = !over) {
                hintMove = game.hint(); selectedSlot = -1
                message = if (hintMove == null) "No piece fits" else "Suggested spot outlined"
                tick++
            }
            SpatialAction(Icons.Default.SkipNext, "Skip") { args.onFinished(false, 0) }
        }
    ) {
        Text(
            "Clear ${cfg.targetLines} lines in ${cfg.pieceBudget} pieces. Pieces left ${game.piecesLeft()}   Score ${game.score}",
            fontSize = 12.sp, color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
        if (message.isNotEmpty()) Text(message, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))

        val desc = "Block board, 8 by 8. ${game.linesCleared} of ${cfg.targetLines} lines cleared. " +
            "Pieces in hand: " + game.hand.filter { it >= 0 }.joinToString { PiecePackEngine.SHAPES[it].name } +
            ". Drag a piece onto the board, or tap a piece and then tap the board."

        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { contentDescription = desc }
                .pointerInputPieces(
                    game = game,
                    density = density.density,
                    getSelected = { selectedSlot },
                    setSelected = { selectedSlot = it },
                    onDrag = { slot, x, y -> dragSlot = slot; dragX = x; dragY = y; tick++ },
                    onPlaced = { hintMove = null; selectedSlot = -1; message = ""; commit() },
                    onRejected = { message = "That piece doesn't fit there"; tick++ },
                    key = game
                )
        ) {
            tick
            val g = Geo.of(size.width, size.height, density.density)
            val cell = g.cell
            val rad = CornerRadius(cell * 0.14f)
            val pad = cell * 0.05f
            // board
            for (r in 0 until 8) for (c in 0 until 8) {
                val v = game.board[r * 8 + c]
                drawRoundRect(
                    if (v == 0) colEmpty else SpatialPalette[(v - 1) % SpatialPalette.size],
                    Offset(g.left + c * cell + pad, g.top + r * cell + pad),
                    Size(cell - 2 * pad, cell - 2 * pad), rad
                )
            }
            for (i in 0..8) {
                if (i % 4 == 0) {
                    drawLine(colGrid, Offset(g.left + i * cell, g.top), Offset(g.left + i * cell, g.top + 8 * cell), 2f)
                    drawLine(colGrid, Offset(g.left, g.top + i * cell), Offset(g.left + 8 * cell, g.top + i * cell), 2f)
                }
            }
            // hint outline
            hintMove?.let { h ->
                val p = PiecePackEngine.SHAPES[game.hand[h[0]]]
                for (cc in p.cells) {
                    drawRoundRect(
                        colHint, Offset(g.left + (h[2] + cc % 10) * cell + pad, g.top + (h[1] + cc / 10) * cell + pad),
                        Size(cell - 2 * pad, cell - 2 * pad), rad, style = Stroke(width = cell * 0.12f)
                    )
                }
            }
            // tray
            for (slot in 0 until 3) {
                val id = game.hand[slot]
                if (id < 0) continue
                val p = PiecePackEngine.SHAPES[id]
                val sx = g.slotX(slot)
                val tcell = g.trayCell(p)
                val ox = sx + (g.slotW - p.cols * tcell) / 2f
                val oy = g.trayTop + (g.trayH - p.rows * tcell) / 2f
                if (selectedSlot == slot) {
                    drawRoundRect(
                        colGhostOk, Offset(sx + 4f, g.trayTop), Size(g.slotW - 8f, g.trayH), CornerRadius(16f),
                        style = Stroke(width = 5f)
                    )
                }
                if (dragSlot == slot) continue
                drawPiece(p, ox, oy, tcell, SpatialPalette[id % SpatialPalette.size], 1f)
            }
            // dragged piece + ghost
            if (dragSlot >= 0 && game.hand[dragSlot] >= 0) {
                val p = PiecePackEngine.SHAPES[game.hand[dragSlot]]
                val tx = dragX - p.cols * cell / 2f
                val ty = dragY - p.rows * cell - g.lift
                val o = PiecePackEngine.dropOrigin(dragX, dragY, p, g.left, g.top, cell, g.lift)
                val rr = o[0]; val cc = o[1]
                val ok = PiecePackEngine.fits(game.board, p, rr, cc)
                drawPiece(p, g.left + cc * cell, g.top + rr * cell, cell, if (ok) colGhostOk else colGhostBad, 1f)
                drawPiece(p, tx, ty, cell, SpatialPalette[p.id % SpatialPalette.size], 0.9f)
            }
        }
    }

    if (done) {
        when (game.status) {
            PiecePackStatus.WON -> {
                val s = game.finalScore()
                SpatialResultDialog(
                    success = true, title = "Target reached!",
                    detail = "Cleared ${game.linesCleared} lines with ${game.piecesPlaced} pieces.  Score $s",
                    stars = when { game.piecesLeft() >= cfg.pieceBudget / 3 -> 3; game.piecesLeft() >= cfg.pieceBudget / 6 -> 2; else -> 1 },
                    primaryLabel = "Continue", onPrimary = { args.onFinished(true, s) }
                )
            }
            PiecePackStatus.PLAYING -> {}
            else -> SpatialResultDialog(
                success = false,
                title = if (game.status == PiecePackStatus.STUCK) "No piece fits" else "Out of pieces",
                detail = "You cleared ${game.linesCleared} of ${cfg.targetLines} lines. Undo a move, or try again.",
                primaryLabel = "Try again", onPrimary = { game.restart(); done = false; commit() },
                secondaryLabel = "Undo last move", onSecondary = { game.undoMove(); done = false; commit() }
            )
        }
    }
}

private fun DrawScope.drawPiece(p: PieceShape, x: Float, y: Float, cell: Float, color: androidx.compose.ui.graphics.Color, alpha: Float) {
    val pad = cell * 0.05f
    val rad = CornerRadius(cell * 0.14f)
    for (cc in p.cells) {
        drawRoundRect(
            color, Offset(x + (cc % 10) * cell + pad, y + (cc / 10) * cell + pad),
            Size(cell - 2 * pad, cell - 2 * pad), rad, alpha = alpha
        )
    }
}

/** Layout geometry shared by drawing and hit-testing. Pure arithmetic; no allocation of note. */
private class Geo(
    val left: Float, val top: Float, val cell: Float,
    val trayTop: Float, val trayH: Float, val slotW: Float, val lift: Float
) {
    fun slotX(slot: Int) = slot * slotW
    fun trayCell(p: PieceShape) = min(min(slotW / 5.4f, trayH / 3.4f), cell * 0.8f).coerceAtLeast(6f).let {
        min(it, min(slotW * 0.9f / p.cols, trayH * 0.9f / p.rows))
    }
    fun slotAt(x: Float, y: Float): Int =
        if (y >= trayTop - 8f) (x / slotW).toInt().coerceIn(0, 2) else -1

    companion object {
        fun of(w: Float, h: Float, dens: Float): Geo {
            val trayH = max(84f * dens, min(w * 0.36f, 120f * dens))
            val side = min(w, h - trayH - 8f * dens)
            val cell = side / 8f
            return Geo((w - side) / 2f, 0f, cell, side + 8f * dens, trayH, w / 3f, 56f * dens)
        }
    }
}

private fun Modifier.pointerInputPieces(
    game: PiecePackState,
    density: Float,
    getSelected: () -> Int,
    setSelected: (Int) -> Unit,
    onDrag: (Int, Float, Float) -> Unit,
    onPlaced: () -> Unit,
    onRejected: () -> Unit,
    key: Any
): Modifier = this.pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val g = Geo.of(size.width.toFloat(), size.height.toFloat(), density)
            val slot = g.slotAt(down.position.x, down.position.y)
            val slop = viewConfiguration.touchSlop
            if (slot >= 0 && game.hand[slot] >= 0 && game.status == PiecePackStatus.PLAYING) {
                down.consume()
                var dragging = false
                var x = down.position.x; var y = down.position.y
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull() ?: break
                    if (!ch.pressed) break
                    x = ch.position.x; y = ch.position.y
                    if (!dragging && (kotlin.math.abs(x - down.position.x) > slop || kotlin.math.abs(y - down.position.y) > slop)) dragging = true
                    if (dragging) onDrag(slot, x, y)
                    if (ch.positionChanged()) ch.consume()
                }
                if (dragging) {
                    val p = PiecePackEngine.SHAPES[game.hand[slot]]
                    val o = PiecePackEngine.dropOrigin(x, y, p, g.left, g.top, g.cell, g.lift)
                    onDrag(-1, 0f, 0f)
                    if (game.tryPlace(slot, o[0], o[1])) onPlaced() else onRejected()
                } else {
                    setSelected(if (getSelected() == slot) -1 else slot)
                    onDrag(-1, 0f, 0f)
                }
            } else {
                // tap on the board with a piece selected
                val sel = getSelected()
                down.consume()
                val up = waitForUpOrCancellation()
                if (up != null && sel >= 0 && game.hand[sel] >= 0 && game.status == PiecePackStatus.PLAYING) {
                    val p = PiecePackEngine.SHAPES[game.hand[sel]]
                    val c = (((up.position.x - g.left) / g.cell).toInt() - p.cols / 2).coerceIn(0, PiecePackEngine.N - p.cols)
                    val r = (((up.position.y - g.top) / g.cell).toInt() - p.rows / 2).coerceIn(0, PiecePackEngine.N - p.rows)
                    if (game.tryPlace(sel, r, c)) onPlaced() else onRejected()
                }
            }
        }
}

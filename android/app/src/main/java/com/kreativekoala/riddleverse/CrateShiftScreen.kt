package com.kreativekoala.riddleverse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

// --- spatial puzzles (feat/puzzles-spatial) ---
// Crate Shift: push every crate onto a target. Swipe on the board or use the arrow pad.

@Composable
fun CrateShiftScreen(args: SpatialArgs) {
    val level by produceState<CrateLevel?>(null, args.seed, args.difficulty) {
        value = withContext(Dispatchers.Default) { CrateShiftEngine.generate(args.difficulty, args.seed) }
    }
    val l = level
    if (l == null) { SpatialLoading(); return }
    CrateShiftBoard(args, l)
}

@Composable
private fun CrateShiftBoard(args: SpatialArgs, level: CrateLevel) {
    val saved = rememberSaveable(args.seed) { mutableStateOf<String?>(null) }
    val game = remember(level) { CrateShiftState(level).also { g -> saved.value?.let { g.restore(it) } } }
    var tick by remember { mutableIntStateOf(0) }
    var done by rememberSaveable(args.seed) { mutableStateOf(false) }
    var hintDir by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf("") }
    @Suppress("UNUSED_EXPRESSION") tick

    fun commit() { tick++; saved.value = game.encode() }
    fun go(d: Int) {
        if (done) return
        hintDir = -1
        if (game.move(d)) {
            message = if (game.deadlocked()) "A crate is stuck in a corner. Undo or restart." else ""
            commit()
        }
    }
    LaunchedEffect(tick) { if (!done && game.solved()) done = true }

    val scheme = MaterialTheme.colorScheme
    val colFloor = scheme.surfaceVariant
    val colWall = scheme.onSurface.copy(alpha = 0.75f)
    val colGoal = scheme.tertiary
    val colCrate = Color(0xFFC98A3C)
    val colCrateDone = Color(0xFF43A047)
    val colPlayer = scheme.primary

    SpatialGameShell(
        title = "Crate Shift",
        args = args,
        status = "Moves ${game.moves}",
        controls = {
            SpatialAction(Icons.Default.Undo, "Undo", enabled = game.canUndo()) {
                game.undo(); hintDir = -1; message = ""; commit()
            }
            SpatialAction(Icons.Default.Refresh, "Restart") { game.restart(); hintDir = -1; message = ""; commit() }
            SpatialAction(Icons.Default.Lightbulb, "Hint") {
                val d = game.hint()
                hintDir = d
                message = if (d < 0) "No way forward from here. Undo or restart." else "Arrow shows the next move"
                tick++
            }
            SpatialAction(Icons.Default.SkipNext, "Skip") { args.onFinished(false, 0) }
        }
    ) {
        Text(
            "Pushes ${game.pushes}  |  Par ${level.par}  |  ${message}",
            fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 2,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
        val onGoal = level.goals.indices.count { level.goals[it] && game.boxes[it] }
        val total = level.goals.count { it }
        val desc = "Crate puzzle, ${level.w} by ${level.h}. $onGoal of $total crates on targets. Moves ${game.moves}. " +
            "Swipe on the board or use the arrow buttons to move."
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .semantics { contentDescription = desc }
                .pointerInput(level) {
                    var acc = Offset.Zero
                    detectDragGestures(
                        onDragStart = { acc = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            acc += amount
                            val step = min(size.width.toFloat() / level.w, size.height.toFloat() / level.h) * 0.6f
                            while (abs(acc.x) >= step || abs(acc.y) >= step) {
                                if (abs(acc.x) >= abs(acc.y)) {
                                    if (acc.x > 0) { go(1); acc = Offset(acc.x - step, acc.y) } else { go(3); acc = Offset(acc.x + step, acc.y) }
                                } else {
                                    if (acc.y > 0) { go(2); acc = Offset(acc.x, acc.y - step) } else { go(0); acc = Offset(acc.x, acc.y + step) }
                                }
                            }
                        }
                    )
                }
        ) {
            tick
            val cell = min(size.width / level.w, size.height / level.h)
            val x0 = (size.width - cell * level.w) / 2f
            val y0 = (size.height - cell * level.h) / 2f
            val rad = CornerRadius(cell * 0.12f)
            val pad = cell * 0.06f
            for (i in 0 until level.w * level.h) {
                val x = x0 + (i % level.w) * cell; val y = y0 + (i / level.w) * cell
                if (level.walls[i]) {
                    drawRoundRect(colWall, Offset(x, y), Size(cell, cell), rad)
                    continue
                }
                drawRoundRect(colFloor, Offset(x + 1f, y + 1f), Size(cell - 2f, cell - 2f), CornerRadius(4f))
                if (level.goals[i]) {
                    drawCircle(colGoal, cell * 0.2f, Offset(x + cell / 2, y + cell / 2), style = Stroke(width = cell * 0.08f))
                }
                if (game.boxes[i]) {
                    val c = if (level.goals[i]) colCrateDone else colCrate
                    drawRoundRect(c, Offset(x + pad, y + pad), Size(cell - 2 * pad, cell - 2 * pad), rad)
                    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(x + pad, y + pad), Size(cell - 2 * pad, cell - 2 * pad), rad, style = Stroke(width = cell * 0.05f))
                    drawLine(Color.Black.copy(alpha = 0.3f), Offset(x + pad * 2, y + pad * 2), Offset(x + cell - pad * 2, y + cell - pad * 2), cell * 0.05f)
                    drawLine(Color.Black.copy(alpha = 0.3f), Offset(x + cell - pad * 2, y + pad * 2), Offset(x + pad * 2, y + cell - pad * 2), cell * 0.05f)
                }
            }
            // player
            val px = x0 + (game.player % level.w) * cell + cell / 2f
            val py = y0 + (game.player / level.w) * cell + cell / 2f
            drawCircle(colPlayer, cell * 0.34f, Offset(px, py))
            drawCircle(Color.White, cell * 0.08f, Offset(px - cell * 0.12f, py - cell * 0.06f))
            drawCircle(Color.White, cell * 0.08f, Offset(px + cell * 0.12f, py - cell * 0.06f))
            if (hintDir >= 0) {
                val dx = CrateShiftEngine.DC[hintDir] * cell * 0.5f
                val dy = CrateShiftEngine.DR[hintDir] * cell * 0.5f
                drawLine(colGoal, Offset(px, py), Offset(px + dx, py + dy), cell * 0.12f)
                drawCircle(colGoal, cell * 0.1f, Offset(px + dx, py + dy))
            }
        }
        // arrow pad (accessible alternative to swiping)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            PadButton(Icons.Default.KeyboardArrowLeft, "Move left") { go(3) }
            PadButton(Icons.Default.KeyboardArrowUp, "Move up") { go(0) }
            PadButton(Icons.Default.KeyboardArrowDown, "Move down") { go(2) }
            PadButton(Icons.Default.KeyboardArrowRight, "Move right") { go(1) }
        }
    }

    if (done) {
        val s = game.score()
        SpatialResultDialog(
            success = true, title = "All crates in place!",
            detail = "${game.moves} moves (par ${level.par}), ${game.pushes} pushes.  Score $s",
            stars = game.stars(), primaryLabel = "Continue", onPrimary = { args.onFinished(true, s) },
            secondaryLabel = "Retry for more stars",
            onSecondary = { game.restart(); done = false; commit() }
        )
    }
}

@Composable
private fun PadButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label)
    }
}

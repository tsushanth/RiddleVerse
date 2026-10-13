package com.kreativekoala.riddleverse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- spatial puzzles (feat/puzzles-spatial) ---
// Conduit: tap tiles to rotate them clockwise until every pipe is powered by the source.

@Composable
fun ConduitScreen(args: SpatialArgs) {
    val puzzle by produceState<ConduitPuzzle?>(null, args.seed, args.difficulty) {
        value = withContext(Dispatchers.Default) { ConduitEngine.generate(args.difficulty, args.seed) }
    }
    val p = puzzle
    if (p == null) { SpatialLoading(); return }
    ConduitBoard(args, p)
}

private fun maskWords(m: Int): String {
    val parts = ArrayList<String>(4)
    if (m and ConduitEngine.N != 0) parts.add("up")
    if (m and ConduitEngine.E != 0) parts.add("right")
    if (m and ConduitEngine.S != 0) parts.add("down")
    if (m and ConduitEngine.W != 0) parts.add("left")
    return parts.joinToString(" and ")
}

@Composable
private fun ConduitBoard(args: SpatialArgs, puzzle: ConduitPuzzle) {
    val n = puzzle.n
    val saved = rememberSaveable(args.seed) { mutableStateOf<String?>(null) }
    val game = remember(puzzle) { ConduitState(puzzle).also { g -> saved.value?.let { g.restore(it) } } }
    var tick by remember { mutableIntStateOf(0) }
    var done by rememberSaveable(args.seed) { mutableStateOf(false) }
    var hintTile by remember { mutableIntStateOf(-1) }
    @Suppress("UNUSED_EXPRESSION") tick

    fun commit() { tick++; saved.value = game.encode() }
    val powered = ConduitEngine.powered(n, puzzle.source, game.masks)
    val poweredCount = powered.count { it }
    LaunchedEffect(tick) { if (!done && game.solved()) done = true }

    val scheme = MaterialTheme.colorScheme
    val colOff = scheme.onSurface.copy(alpha = 0.45f)
    val colOn = Color(0xFF1E88E5)
    val colBg = scheme.surfaceVariant
    val colHint = scheme.tertiary

    SpatialGameShell(
        title = "Conduit",
        args = args,
        status = "Powered $poweredCount/${n * n}",
        controls = {
            SpatialAction(Icons.Default.Undo, "Undo", enabled = game.canUndo()) { game.undo(); hintTile = -1; commit() }
            SpatialAction(Icons.Default.Refresh, "Restart") { game.restart(); hintTile = -1; commit() }
            SpatialAction(Icons.Default.Lightbulb, "Hint") { hintTile = game.hint(); tick++ }
            SpatialAction(Icons.Default.SkipNext, "Skip") { args.onFinished(false, 0) }
        }
    ) {
        Text(
            "Moves ${game.moves}  |  Par ${puzzle.par}. Tap a tile to rotate it.",
            fontSize = 12.sp, color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.TopCenter) {
            val side = minOf(maxWidth, maxHeight)
            Column(Modifier.size(side)) {
                for (r in 0 until n) {
                    Row(Modifier.weight(1f)) {
                        for (c in 0 until n) {
                            val i = r * n + c
                            val m = game.masks[i]
                            val on = powered[i]
                            val isSource = i == puzzle.source
                            val leaf = Integer.bitCount(m) == 1 && !isSource
                            val desc = "Row ${r + 1}, column ${c + 1}: " +
                                (if (isSource) "power source, " else if (leaf) "lamp, " else "") +
                                "pipe to ${maskWords(m)}" + (if (on) ", powered" else ", not powered")
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .semantics { contentDescription = desc; role = Role.Button }
                                    .clickable(onClickLabel = "Rotate tile") {
                                        if (!done) { hintTile = -1; game.rotate(i); commit() }
                                    }
                                    .padding(1.dp)
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val s = size.minDimension
                                    val ctr = Offset(size.width / 2f, size.height / 2f)
                                    drawRoundRect(colBg, Offset.Zero, size, CornerRadius(s * 0.12f))
                                    if (hintTile == i) {
                                        drawRoundRect(colHint, Offset(2f, 2f), Size(size.width - 4f, size.height - 4f), CornerRadius(s * 0.12f), style = Stroke(width = s * 0.08f))
                                    }
                                    val col = if (on) colOn else colOff
                                    val w = s * 0.2f
                                    if (m and ConduitEngine.N != 0) drawLine(col, ctr, Offset(ctr.x, 0f), w, StrokeCap.Butt)
                                    if (m and ConduitEngine.S != 0) drawLine(col, ctr, Offset(ctr.x, size.height), w, StrokeCap.Butt)
                                    if (m and ConduitEngine.W != 0) drawLine(col, ctr, Offset(0f, ctr.y), w, StrokeCap.Butt)
                                    if (m and ConduitEngine.E != 0) drawLine(col, ctr, Offset(size.width, ctr.y), w, StrokeCap.Butt)
                                    drawCircle(col, w / 2f, ctr)
                                    if (isSource) {
                                        drawCircle(Color(0xFFFFB020), s * 0.26f, ctr)
                                        drawCircle(Color.White, s * 0.1f, ctr)
                                    } else if (leaf) {
                                        drawCircle(if (on) Color(0xFFFFEB3B) else colOff.copy(alpha = 0.3f), s * 0.24f, ctr)
                                        drawCircle(col, s * 0.24f, ctr, style = Stroke(width = s * 0.05f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (done) {
        val s = game.score()
        SpatialResultDialog(
            success = true, title = "All lit up!",
            detail = "${game.moves} rotations (par ${puzzle.par}).  Score $s",
            stars = game.stars(), primaryLabel = "Continue", onPrimary = { args.onFinished(true, s) }
        )
    }
}

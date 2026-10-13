package com.kreativekoala.riddleverse

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- spatial puzzles (feat/puzzles-spatial) ---
// Tube Tint: liquid-sort. Each tube is its own focusable/clickable node (>= 48dp wide).

@Composable
fun TubeTintScreen(args: SpatialArgs) {
    val puzzle by produceState<TubeTintPuzzle?>(null, args.seed, args.difficulty) {
        value = withContext(Dispatchers.Default) { TubeTintEngine.generate(args.difficulty, args.seed) }
    }
    val p = puzzle
    if (p == null) { SpatialLoading(); return }
    TubeTintBoard(args, p)
}

@Composable
private fun TubeTintBoard(args: SpatialArgs, puzzle: TubeTintPuzzle) {
    val saved = rememberSaveable(args.seed) { mutableStateOf<String?>(null) }
    val game = remember(puzzle) { TubeTintState(puzzle).also { g -> saved.value?.let { g.restore(it) } } }
    var tick by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable(args.seed) { mutableIntStateOf(-1) }
    var done by rememberSaveable(args.seed) { mutableStateOf(false) }
    var hintPair by remember { mutableStateOf<IntArray?>(null) }
    var message by remember { mutableStateOf("") }

    fun commit() { tick++; saved.value = game.encode() }
    @Suppress("UNUSED_EXPRESSION") tick

    val tubes = game.tubes
    val cap = game.capacity

    LaunchedEffect(tick) { if (!done && game.solved()) done = true }

    fun tap(i: Int) {
        hintPair = null; message = ""
        if (done) return
        if (selected < 0) {
            if (tubes[i].isNotEmpty()) selected = i
        } else if (selected == i) {
            selected = -1
        } else {
            val from = selected
            if (game.pour(from, i)) { selected = -1; commit() }
            else {
                message = "Can't pour there: the top colours must match and the tube needs room."
                selected = if (tubes[i].isNotEmpty()) i else -1
            }
        }
    }

    SpatialGameShell(
        title = "Tube Tint",
        args = args,
        status = "Moves ${game.moves}",
        controls = {
            SpatialAction(Icons.Default.Undo, "Undo", enabled = game.canUndo()) {
                game.undo(); selected = -1; hintPair = null; commit()
            }
            SpatialAction(Icons.Default.Refresh, "Restart") {
                game.restart(); selected = -1; hintPair = null; commit()
            }
            SpatialAction(Icons.Default.Add, "Add tube", enabled = !game.extraTubeAdded) {
                game.addTube(); selected = -1; commit()
            }
            SpatialAction(Icons.Default.Lightbulb, "Hint") {
                val h = game.hint()
                if (h != null) { hintPair = h; selected = -1; message = "Pour tube ${h[0] + 1} into tube ${h[1] + 1}" }
                else message = "No path from here. Try Undo, Restart or Add tube."
                tick++
            }
            SpatialAction(Icons.Default.SkipNext, "Skip") { args.onFinished(false, 0) }
        }
    ) {
        if (message.isNotEmpty()) {
            Text(
                message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
        val perRow = if (tubes.size <= 7) tubes.size else (tubes.size + 1) / 2
        val rows = tubes.chunked(perRow)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            val slotW = maxWidth / maxOf(perRow, 1)
            val rowH = (maxHeight / rows.size)
            val tubeH: Dp = minOf(rowH - 20.dp, slotW * 2.9f).coerceAtLeast(96.dp)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                var index = 0
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().height(tubeH + 20.dp), verticalAlignment = Alignment.Bottom) {
                        row.forEach { _ ->
                            val i = index++
                            TubeView(
                                layers = tubes[i], capacity = cap, index = i,
                                selected = selected == i,
                                hinted = hintPair?.let { it[0] == i || it[1] == i } == true,
                                tubeH = tubeH,
                                modifier = Modifier.width(slotW)
                            ) { tap(i) }
                        }
                    }
                }
            }
        }
    }

    if (done) {
        val s = game.score()
        SpatialResultDialog(
            success = true, title = "Sorted!",
            detail = "${game.moves} moves.  Score $s",
            stars = when { game.hintsUsed == 0 && !game.extraTubeAdded -> 3; game.hintsUsed <= 1 -> 2; else -> 1 },
            primaryLabel = "Continue", onPrimary = { args.onFinished(true, s) }
        )
    }
}

@Composable
private fun TubeView(
    layers: List<Int>,
    capacity: Int,
    index: Int,
    selected: Boolean,
    hinted: Boolean,
    tubeH: Dp,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val lift by animateDpAsState(if (selected) 14.dp else 0.dp, label = "lift")
    val scheme = MaterialTheme.colorScheme
    val glass = scheme.onSurface.copy(alpha = 0.55f)
    val glow = if (hinted) scheme.tertiary else if (selected) scheme.primary else glass
    val desc = buildString {
        append("Tube ${index + 1}, ")
        if (layers.isEmpty()) append("empty")
        else append(layers.joinToString(", ") { SpatialColorNames[it % SpatialColorNames.size] } + " from bottom to top")
        if (layers.size == capacity && layers.all { it == layers[0] }) append(", complete")
        if (selected) append(", selected")
    }
    Box(
        modifier
            .fillMaxHeight()
            .semantics { contentDescription = desc; role = Role.Button }
            .clickable(onClickLabel = if (selected) "Deselect tube" else "Select or pour into tube", onClick = onClick),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(
            Modifier
                .padding(horizontal = 6.dp)
                .fillMaxWidth()
                .height(tubeH)
                .offset(y = -lift)
        ) {
            val w = size.width
            val layerH = (size.height - 8f) / capacity
            val r = CornerRadius(w * 0.35f)
            // liquid layers bottom -> top
            for (k in layers.indices) {
                val top = size.height - 4f - (k + 1) * layerH
                drawRoundRect(
                    SpatialPalette[layers[k] % SpatialPalette.size],
                    Offset(4f, top + 1f), Size(w - 8f, layerH - 1f),
                    if (k == 0) r else CornerRadius(2f)
                )
            }
            drawRoundRect(
                glow, Offset(1.5f, 1.5f), Size(w - 3f, size.height - 3f), r,
                style = Stroke(width = if (selected || hinted) 6f else 3f)
            )
        }
    }
}

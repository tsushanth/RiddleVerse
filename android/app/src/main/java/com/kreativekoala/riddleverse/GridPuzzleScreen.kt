package com.kreativekoala.riddleverse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.riddleverse.ui.theme.RvCanvas
import com.kreativekoala.riddleverse.ui.theme.RvCoral
import com.kreativekoala.riddleverse.ui.theme.RvDisabled
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvMint
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvSun
import com.kreativekoala.riddleverse.ui.theme.RvSurface
import com.kreativekoala.riddleverse.ui.theme.RvViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Cell tints
private val SelectedFill = Color(0xFFD9D0FF)
private val PeerFill = Color(0xFFEEEAFF)
private val SameDigitFill = Color(0xFFFFE9B8)
private val WrongFill = Color(0xFFFFE0E0)

/** Entry point used by the wrapper: generates the grid off the main thread, then shows the game. */
@Composable
fun GridPuzzleScreen(
    game: GridGame,
    difficulty: String,
    seed: Long,
    level: String,
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    val spec by produceState<GridSpec?>(null, game, difficulty, seed) {
        value = withContext(Dispatchers.Default) {
            try {
                GridPuzzleFactory.build(game, difficulty, seed)
            } catch (e: Exception) {
                PuzzleEvents.error(game.key, difficulty, "grid generation failed: ${e.javaClass.simpleName}")
                throw e
            }
        }
    }
    val s = spec
    if (s == null) {
        Box(Modifier.fillMaxSize().background(RvCanvas), contentAlignment = Alignment.Center) {
            Text("Building your puzzle...", color = RvInkSoft, fontSize = 16.sp)
        }
    } else {
        GridPuzzleContent(s, level, onGameComplete, onBack)
    }
}

@Composable
fun GridPuzzleContent(
    spec: GridSpec,
    level: String,
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    val state = rememberSaveable(
        spec.solution.contentHashCode(),
        saver = Saver<GridPlayState, IntArray>(save = { it.toSaved() }, restore = { GridPlayState(spec).apply { restore(it) } })
    ) { GridPlayState(spec) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.status) {
        while (state.status == GridStatus.PLAYING) {
            delay(1000)
            state.tick()
        }
    }

    val padRows = if (spec.game == GridGame.BINAIRO) 1 else if (spec.symbols > 5) 2 else 1

    BoxWithConstraints(Modifier.fillMaxSize().background(RvCanvas).statusBarsPadding().navigationBarsPadding()) {
        val headerH = 52.dp
        val controlsH = 56.dp
        // Landscape phone: board on the left, controls + number pad on the right, so nothing is cut off at the bottom.
        val sideBySide = maxHeight < 420.dp && maxWidth > maxHeight * 1.3f
        if (sideBySide) {
            val side = com.kreativekoala.riddleverse.ui.BoardFit.squareSide(
                maxWidth.value / 2f, maxHeight.value, reservedH = headerH.value + 16f, hPad = 12f, minSide = 120f
            ).dp
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                GridHeader(spec, state, level, onBack, headerH) { showRules = true }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { GridBoard(spec, state, side) }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GridControls(state, controlsH)
                        Spacer(Modifier.height(8.dp))
                        GridPad(spec, state)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        } else {
        val padH = 52.dp * padRows + 8.dp * (padRows - 1)
        val gaps = 8.dp * 4 + 12.dp
        val side = com.kreativekoala.riddleverse.ui.BoardFit.squareSide(
            maxWidth.value, maxHeight.value, (headerH + controlsH + padH + gaps).value, hPad = 24f, minSide = 200f
        ).dp

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GridHeader(spec, state, level, onBack, headerH) { showRules = true }
            Spacer(Modifier.height(8.dp))
            GridBoard(spec, state, side)
            Spacer(Modifier.height(8.dp))
            GridControls(state, controlsH)
            Spacer(Modifier.height(8.dp))
            GridPad(spec, state)
            Spacer(Modifier.height(4.dp))
        }
        }
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("How to play ${spec.game.title}") },
            text = { Text(GridPuzzleFactory.rulesFor(spec.game)) },
            confirmButton = { TextButton(onClick = { showRules = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Got it") } }
        )
    }

    if (state.status != GridStatus.PLAYING && !finished) {
        val won = state.status == GridStatus.WON
        AlertDialog(
            // Back = Continue; tapping outside is ignored.
            onDismissRequest = { finished = true; onGameComplete(won, if (won) state.score() else 0) },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
            title = { Text(if (won) "Solved!" else "Out of lives") },
            text = {
                Text(
                    if (won) "Time ${formatClock(state.elapsedSeconds)}  |  Mistakes ${state.mistakes}  |  Hints ${state.hintsUsed}\n+${state.score()} points"
                    else "Three mistakes ends the puzzle. Try the next one!"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { finished = true; onGameComplete(won, if (won) state.score() else 0) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Continue") }
            }
        )
    }
}

private fun formatClock(sec: Int) = String.format("%02d:%02d", sec / 60, sec % 60)

@Composable
private fun GridHeader(spec: GridSpec, state: GridPlayState, level: String, onBack: () -> Unit, height: Dp, onRules: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RvInk)
        }
        Column(Modifier.weight(1f)) {
            Text(spec.game.title, color = RvInk, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${spec.difficulty.replaceFirstChar { it.uppercase() }} | ${spec.n}x${spec.n} | $level",
                color = RvInkSoft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            formatClock(state.elapsedSeconds), color = RvInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
            modifier = Modifier.semantics { contentDescription = "Elapsed time ${state.elapsedSeconds / 60} minutes ${state.elapsedSeconds % 60} seconds" }
        )
        Row(
            Modifier.padding(horizontal = 6.dp).semantics { contentDescription = "${state.lives} lives left" },
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            repeat(GridPlayState.MAX_LIVES) { i ->
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = if (i < state.lives) RvCoral else RvDisabled, modifier = Modifier.size(16.dp))
            }
        }
        IconButton(onClick = onRules, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Info, contentDescription = "Rules", tint = RvViolet)
        }
    }
}

// ---------------------------------------------------------------- Board

@Composable
fun GridBoard(spec: GridSpec, state: GridPlayState, side: Dp) {
    val n = spec.n
    val cell = side / n
    val density = LocalDensity.current
    val digitSp = with(density) { (cell * 0.52f).toSp() }
    val noteSp = with(density) { (cell * 0.2f).toSp() }
    val labelSp = with(density) { (cell * 0.24f).toSp() }
    val sel = state.selected
    val selVal = if (sel >= 0) state.values[sel] else 0

    Box(
        Modifier.size(side).border(BorderStroke(2.dp, RvInk), RoundedCornerShape(4.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until n) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0 until n) {
                        val i = r * n + c
                        GridCell(
                            spec, state, i, r, c, selVal, digitSp, noteSp, labelSp,
                            Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
        if (spec.game == GridGame.SIGN_GRID) SignOverlay(spec, Modifier.fillMaxSize())
    }
}

@Composable
private fun GridCell(
    spec: GridSpec, state: GridPlayState, i: Int, r: Int, c: Int, selVal: Int,
    digitSp: androidx.compose.ui.unit.TextUnit, noteSp: androidx.compose.ui.unit.TextUnit, labelSp: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier
) {
    val n = spec.n
    val value = state.values[i]
    val given = state.isGiven(i)
    val wrong = state.isWrong(i)
    val sel = state.selected
    val isSel = sel == i
    val peer = sel >= 0 && !isSel && when (spec.game) {
        GridGame.SUDOKU -> sel / n == r || sel % n == c ||
            (sel / n / spec.boxRows == r / spec.boxRows && sel % n / spec.boxCols == c / spec.boxCols)
        GridGame.BINAIRO, GridGame.SIGN_GRID, GridGame.CAGE_MATH -> sel / n == r || sel % n == c
    }
    val sameDigit = selVal != 0 && value == selVal && !isSel
    val bg = when {
        isSel -> SelectedFill
        wrong -> WrongFill
        sameDigit -> SameDigitFill
        peer -> PeerFill
        else -> RvCanvas
    }
    val thinC = RvOutline
    val thickC = RvInk
    // which sides get a heavy line
    fun heavyRight(): Boolean = when (spec.game) {
        GridGame.SUDOKU -> c < n - 1 && (c + 1) % spec.boxCols == 0
        GridGame.CAGE_MATH -> c < n - 1 && spec.cageOf[i] != spec.cageOf[i + 1]
        else -> false
    }
    fun heavyBottom(): Boolean = when (spec.game) {
        GridGame.SUDOKU -> r < n - 1 && (r + 1) % spec.boxRows == 0
        GridGame.CAGE_MATH -> r < n - 1 && spec.cageOf[i] != spec.cageOf[i + n]
        else -> false
    }
    val hr = heavyRight()
    val hb = heavyBottom()
    val cageStart = spec.game == GridGame.CAGE_MATH && spec.cages[spec.cageOf[i]].cells.min() == i

    val desc = buildString {
        append("Row ${r + 1}, column ${c + 1}, ")
        append(
            when {
                value != 0 -> "${spec.symbolText(value)}${if (given) ", given" else ""}${if (wrong) ", incorrect" else ""}"
                state.notes[i] != 0 -> "empty, notes " + (1..spec.n).filter { state.notes[i] and (1 shl it) != 0 }.joinToString(" ")
                else -> "empty"
            }
        )
        if (spec.game == GridGame.CAGE_MATH) {
            val cg = spec.cages[spec.cageOf[i]]
            append(", cage ${cg.target} ${opWord(cg.op)} of ${cg.cells.size} cells")
        }
        if (spec.game == GridGame.SIGN_GRID) {
            if (c < n - 1) spec.hRel[r * (n - 1) + c].let { if (it == 1) append(", less than the cell to the right") else if (it == -1) append(", greater than the cell to the right") }
            if (c > 0) spec.hRel[r * (n - 1) + c - 1].let { if (it == 1) append(", greater than the cell to the left") else if (it == -1) append(", less than the cell to the left") }
            if (r < n - 1) spec.vRel[r * n + c].let { if (it == 1) append(", less than the cell below") else if (it == -1) append(", greater than the cell below") }
            if (r > 0) spec.vRel[(r - 1) * n + c].let { if (it == 1) append(", greater than the cell above") else if (it == -1) append(", less than the cell above") }
        }
        if (isSel) append(", selected")
    }

    Box(
        modifier
            .background(bg)
            .drawBehind {
                val thin = 0.5.dp.toPx()
                val thick = 2.dp.toPx()
                // right edge
                drawLine(if (hr) thickC else thinC, Offset(size.width, 0f), Offset(size.width, size.height), if (hr) thick else thin)
                // bottom edge
                drawLine(if (hb) thickC else thinC, Offset(0f, size.height), Offset(size.width, size.height), if (hb) thick else thin)
            }
            .semantics { contentDescription = desc }
            .clickable(role = Role.Button) { state.select(i) },
        contentAlignment = Alignment.Center
    ) {
        if (cageStart) {
            val cg = spec.cages[spec.cageOf[i]]
            Text(
                cg.label, color = RvInk, fontSize = labelSp, fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 1.dp)
            )
        }
        if (value != 0) {
            Text(
                spec.symbolText(value),
                color = when { wrong -> RvCoral; given -> RvInk; else -> RvViolet },
                fontSize = digitSp,
                fontWeight = if (given) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        } else if (state.notes[i] != 0) {
            NotesGrid(spec, state.notes[i], noteSp)
        }
    }
}

private fun opWord(op: Char) = when (op) { '+' -> "sum"; '-' -> "difference"; '*' -> "product"; '/' -> "quotient"; else -> "value" }

@Composable
private fun NotesGrid(spec: GridSpec, mask: Int, sp: androidx.compose.ui.unit.TextUnit) {
    val perRow = if (spec.n <= 4) 2 else 3
    val rows = (spec.n + perRow - 1) / perRow
    Column(Modifier.fillMaxSize().padding(1.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        for (rr in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (cc in 0 until perRow) {
                    val d = rr * perRow + cc + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (d <= spec.n && mask and (1 shl d) != 0) Text("$d", color = RvInkSoft, fontSize = sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignOverlay(spec: GridSpec, modifier: Modifier) {
    val n = spec.n
    val ink = RvInk
    Canvas(modifier) {
        val cs = size.width / n
        val s = cs * 0.13f
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        fun chip(x: Float, y: Float) = drawCircle(Color.White, radius = s * 1.5f, center = Offset(x, y))
        for (r in 0 until n) for (c in 0 until n - 1) {
            val rel = spec.hRel[r * (n - 1) + c]
            if (rel == 0) continue
            val x = (c + 1) * cs
            val y = (r + 0.5f) * cs
            chip(x, y)
            val p = Path()
            if (rel == 1) { // left smaller: "<"
                p.moveTo(x + s * 0.6f, y - s); p.lineTo(x - s * 0.6f, y); p.lineTo(x + s * 0.6f, y + s)
            } else { // left larger: ">"
                p.moveTo(x - s * 0.6f, y - s); p.lineTo(x + s * 0.6f, y); p.lineTo(x - s * 0.6f, y + s)
            }
            drawPath(p, ink, style = stroke)
        }
        for (r in 0 until n - 1) for (c in 0 until n) {
            val rel = spec.vRel[r * n + c]
            if (rel == 0) continue
            val x = (c + 0.5f) * cs
            val y = (r + 1) * cs
            chip(x, y)
            val p = Path()
            if (rel == 1) { // upper smaller: "^"
                p.moveTo(x - s, y + s * 0.6f); p.lineTo(x, y - s * 0.6f); p.lineTo(x + s, y + s * 0.6f)
            } else { // upper larger: "v"
                p.moveTo(x - s, y - s * 0.6f); p.lineTo(x, y + s * 0.6f); p.lineTo(x + s, y - s * 0.6f)
            }
            drawPath(p, ink, style = stroke)
        }
    }
}

// ---------------------------------------------------------------- Controls

@Composable
private fun GridControls(state: GridPlayState, height: Dp) {
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlButton("Undo", Icons.Filled.Undo, enabled = state.undoDepth > 0, modifier = Modifier.weight(1f)) { state.undo() }
        ControlButton("Erase", Icons.Filled.Clear, enabled = state.selected >= 0 && !state.isGiven(state.selected), modifier = Modifier.weight(1f)) { state.erase() }
        if (state.usesNotes) {
            ControlButton(if (state.notesMode) "Notes on" else "Notes", Icons.Filled.Edit, active = state.notesMode, modifier = Modifier.weight(1f)) {
                state.notesMode = !state.notesMode
            }
        }
        ControlButton("Hint ${state.hintsLeft}", Icons.Filled.Lightbulb, enabled = state.hintsLeft > 0, modifier = Modifier.weight(1f)) { state.hint() }
    }
}

@Composable
private fun ControlButton(label: String, icon: ImageVector, modifier: Modifier, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxHeight()
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(if (active) RvSun.copy(alpha = 0.35f) else RvSurface, shape)
            .border(1.dp, if (active) RvSun else RvOutline, shape)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = RvInk, modifier = Modifier.size(22.dp))
        Text(label, color = RvInk, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun GridPad(spec: GridSpec, state: GridPlayState) {
    val symbols = (1..spec.symbols).toList()
    val perRow = if (spec.game == GridGame.BINAIRO) 2 else if (symbols.size > 5) (symbols.size + 1) / 2 else symbols.size
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        symbols.chunked(perRow).forEach { rowSymbols ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowSymbols.forEach { d ->
                    val remaining = if (spec.game == GridGame.BINAIRO) 1 else spec.n - state.filledCount(d)
                    val noteMode = state.notesMode && state.usesNotes
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .alpha(if (remaining <= 0) 0.35f else 1f)
                            .background(if (noteMode) RvSun.copy(alpha = 0.3f) else RvViolet.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .border(1.dp, if (noteMode) RvSun else RvViolet.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .semantics { contentDescription = (if (noteMode) "Note " else "Enter ") + spec.symbolText(d) }
                            .clickable(role = Role.Button) { state.enter(d) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(spec.symbolText(d), color = RvInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // keep partially filled last rows aligned to the same cell width
                repeat(perRow - rowSymbols.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

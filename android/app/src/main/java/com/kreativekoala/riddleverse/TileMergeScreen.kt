package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// --- classic puzzles (feat/puzzles-classic) --- Tile Merge screen

private fun tileColor(v: Int): Pair<Color, Color> = when (v) {
    0 -> Color.Transparent to Color.Transparent
    2 -> Color(0xFFE6E1FF) to Color(0xFF231B4A)
    4 -> Color(0xFFCFC6FF) to Color(0xFF231B4A)
    8 -> Color(0xFF3AA0FF) to Color.White
    16 -> Color(0xFF1F7BD6) to Color.White
    32 -> Color(0xFF2DC98E) to Color(0xFF0B3B2B)
    64 -> Color(0xFF1AA070) to Color.White
    128 -> Color(0xFFFFB020) to Color(0xFF3B2A00)
    256 -> Color(0xFFFF8C00) to Color.White
    512 -> Color(0xFFFF6B6B) to Color.White
    1024 -> Color(0xFFD94848) to Color.White
    2048 -> Color(0xFF6C4CF1) to Color.White
    else -> Color(0xFF231B4A) to Color.White
}

@Composable
fun TileMergeScreen(tier: Int, level: String, onBack: () -> Unit, onFinish: (Boolean, Int) -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val cfg = remember(tier) { TileMergeRules.configFor(tier) }
    var game by rememberSavedGame({ TileMergeGame.newGame(System.nanoTime()) }, { it.encode() }, { TileMergeGame.decode(it) })
    var undosLeft by rememberSaveable { mutableIntStateOf(3) }
    var continuing by rememberSaveable { mutableStateOf(false) }
    var ended by rememberSaveable { mutableStateOf(false) }
    var newBest by rememberSaveable { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val best = remember { ClassicStats.best(ctx, ClassicPuzzles.TILE_MERGE, tier) }

    val won = game.reachedTarget
    val over = !game.canMove() || (!won && game.moves >= cfg.moveBudget)
    val showWin = won && !continuing && !ended && !over
    val showResult = ended || over

    LaunchedEffect(showResult) {
        if (showResult) newBest = ClassicStats.submit(ctx, ClassicPuzzles.TILE_MERGE, tier, game.score, lowerIsBetter = false)
    }

    fun doMove(dir: MergeDir) {
        if (showWin || showResult) return
        val next = game.move(dir, cfg.target)
        if (next !== game) {
            game = next
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    ClassicScaffold(
        title = "Tile Merge",
        level = level,
        subtitle = "Reach the ${cfg.target} tile within ${cfg.moveBudget} moves.",
        stats = listOf(
            "Score" to game.score.toString(),
            "Best" to (maxOf(best ?: 0, game.score)).toString(),
            "Moves" to "${game.moves}/${cfg.moveBudget}",
            "Top tile" to game.maxTile.toString(),
        ),
        onBack = onBack,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BoxWithConstraints {
                val side = minOf(maxWidth, maxHeight)
                TileMergeBoard(game, Modifier.size(side), enabled = !showWin && !showResult, onSwipe = ::doMove)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { if (undosLeft > 0 && game.canUndo && !showResult) { game = game.undo(); undosLeft-- } },
                enabled = undosLeft > 0 && game.canUndo && !showResult,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo last move, $undosLeft left")
                    Text(" $undosLeft", fontSize = 14.sp)
                }
            }
            DirButton(Icons.Default.ArrowBack, "Move tiles left", Modifier.weight(1f)) { doMove(MergeDir.LEFT) }
            DirButton(Icons.Default.ArrowUpward, "Move tiles up", Modifier.weight(1f)) { doMove(MergeDir.UP) }
            DirButton(Icons.Default.ArrowDownward, "Move tiles down", Modifier.weight(1f)) { doMove(MergeDir.DOWN) }
            DirButton(Icons.Default.ArrowForward, "Move tiles right", Modifier.weight(1f)) { doMove(MergeDir.RIGHT) }
        }
        if (won && continuing && !showResult) {
            TextButton(onClick = { ended = true }, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterHorizontally)) {
                Text("Finish with this score")
            }
        }
    }

    if (showWin) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("You made ${cfg.target}!", fontWeight = FontWeight.Bold) },
            text = { Text("Finish now, or keep going to push your score higher (the move limit no longer applies).") },
            confirmButton = { Button(onClick = { ended = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Finish") } },
            dismissButton = { OutlinedButton(onClick = { continuing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Keep going") } },
        )
    }
    if (showResult) {
        ClassicResultDialog(
            success = won,
            headline = if (won) "Goal reached!" else if (!game.canMove()) "No moves left" else "Out of moves",
            lines = buildList {
                add("Top tile: ${game.maxTile}")
                add("Score: ${game.score}")
                add("Moves: ${game.moves}")
                if (newBest) add("New best score!")
            },
            onContinue = { if (!reported) { reported = true; onFinish(won, game.score) } },
        )
    }
}

@Composable
private fun DirButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(icon, contentDescription = desc)
    }
}

@Composable
private fun TileMergeBoard(game: TileMergeGame, modifier: Modifier, enabled: Boolean, onSwipe: (MergeDir) -> Unit) {
    val density = LocalDensity.current
    val threshold = with(density) { 24.dp.toPx() }
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentEnabled by rememberUpdatedState(enabled)
    val n = game.size
    BoxWithConstraints(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(6.dp)
            .pointerInput(threshold) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDrag = { change, drag -> change.consume(); dx += drag.x; dy += drag.y },
                    onDragEnd = {
                        if (currentEnabled && maxOf(abs(dx), abs(dy)) >= threshold) {
                            currentOnSwipe(
                                if (abs(dx) > abs(dy)) (if (dx > 0) MergeDir.RIGHT else MergeDir.LEFT)
                                else (if (dy > 0) MergeDir.DOWN else MergeDir.UP)
                            )
                        }
                    },
                )
            }
            .semantics { contentDescription = "Tile board. Highest tile ${game.maxTile}. Swipe, or use the arrow buttons below." }
    ) {
        val cell = maxWidth / n
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until n) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0 until n) {
                        val v = game.cells[r * n + c]
                        val (bg, fg) = tileColor(v)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(3.dp)
                                .background(if (v == 0) MaterialTheme.colorScheme.background.copy(alpha = 0.5f) else bg, RoundedCornerShape(10.dp))
                                .semantics { contentDescription = if (v == 0) "Row ${r + 1} column ${c + 1} empty" else "Row ${r + 1} column ${c + 1} tile $v" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (v != 0) {
                                val digits = v.toString().length
                                val fs = cell.value * when { digits <= 2 -> 0.42f; digits == 3 -> 0.34f; else -> 0.27f }
                                Text(v.toString(), color = fg, fontSize = fs.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

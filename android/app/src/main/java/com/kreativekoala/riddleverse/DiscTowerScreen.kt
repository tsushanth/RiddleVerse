package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- classic puzzles (feat/puzzles-classic) --- Disc Tower screen

private val discColors = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFB020), Color(0xFF2DC98E), Color(0xFF3AA0FF),
    Color(0xFF6C4CF1), Color(0xFF9B5DE5), Color(0xFFFF8C00), Color(0xFF1AA070),
)

@Composable
fun DiscTowerScreen(tier: Int, level: String, onBack: () -> Unit, onFinish: (Boolean, Int) -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val discs = remember(tier) { DiscTowerRules.discsFor(tier) }
    var game by rememberSavedGame({ DiscTowerGame.newGame(discs) }, { it.encode() }, { DiscTowerGame.decode(it) })
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var hintsUsed by rememberSaveable { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf("") }
    var newBest by rememberSaveable { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val best = remember { ClassicStats.best(ctx, ClassicPuzzles.DISC_TOWER, tier) }
    val solved = game.isSolved

    LaunchedEffect(solved) {
        if (solved) newBest = ClassicStats.submit(ctx, ClassicPuzzles.DISC_TOWER, tier, game.moves, lowerIsBetter = true)
    }

    fun tapPeg(p: Int) {
        if (solved) return
        message = ""
        if (selected == -1) {
            if (game.pegs[p].isNotEmpty()) selected = p else haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else if (selected == p) {
            selected = -1
        } else if (game.canMove(selected, p)) {
            game = game.move(selected, p)
            selected = -1
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else {
            message = "A bigger disc can't go on a smaller one."
            selected = -1
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    ClassicScaffold(
        title = "Disc Tower",
        level = level,
        subtitle = "Move all $discs discs to the right-hand peg. Never put a bigger disc on a smaller one.",
        stats = listOf(
            "Moves" to game.moves.toString(),
            "Par" to game.par.toString(),
            "Best" to (best?.toString() ?: "-"),
        ),
        onBack = onBack,
    ) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in 0 until DiscTowerRules.PEGS) {
                val isSel = selected == p
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                        .let { if (isSel) it.border(3.dp, Color(0xFF6C4CF1), RoundedCornerShape(14.dp)) else it }
                        .clickable(role = Role.Button) { tapPeg(p) }
                        .semantics {
                            val stack = game.pegs[p]
                            contentDescription = "Peg ${p + 1}" + (if (p == DiscTowerRules.TARGET_PEG) " (goal)" else "") +
                                (if (isSel) ", selected" else "") +
                                if (stack.isEmpty()) ", empty" else ", discs from bottom: ${stack.joinToString()}"
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val discH = minOf(34.dp, (maxHeight - 16.dp) / (discs + 1))
                    Box(
                        Modifier.align(Alignment.BottomCenter).width(8.dp).fillMaxHeight(0.97f)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    )
                    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        game.pegs[p].asReversed().forEachIndexed { i, size ->
                            val lifted = isSel && i == 0
                            Box(
                                Modifier
                                    .padding(vertical = 1.dp)
                                    .fillMaxWidth(0.3f + 0.65f * size / discs)
                                    .height(discH)
                                    .background(discColors[(size - 1) % discColors.size], RoundedCornerShape(50))
                                    .let { if (lifted) it.border(2.dp, Color.White, RoundedCornerShape(50)) else it },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (discH >= 20.dp) Text(size.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Text(
            message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp).padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { game = game.undo(); selected = -1; message = "" },
                enabled = game.canUndo && !solved,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Icon(Icons.Default.Undo, contentDescription = null); Text("  Undo") }
            FilledTonalButton(
                onClick = {
                    game.hintMove()?.let { (f, t) ->
                        hintsUsed++
                        selected = f
                        message = "Hint: move the top disc from peg ${f + 1} to peg ${t + 1}."
                    }
                },
                enabled = !solved,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Icon(Icons.Default.Lightbulb, contentDescription = null); Text("  Hint ($hintsUsed)") }
        }
    }

    if (solved) {
        val score = ClassicPuzzles.efficiencyScore(game.par, game.moves, penalty = 5 * hintsUsed)
        ClassicResultDialog(
            success = true,
            headline = "Tower moved!",
            lines = buildList {
                add("Moves: ${game.moves} (best possible ${game.par})")
                if (hintsUsed > 0) add("Hints used: $hintsUsed")
                if (newBest) add("New best!")
            },
            onContinue = { if (!reported) { reported = true; onFinish(true, score) } },
        )
    }
}

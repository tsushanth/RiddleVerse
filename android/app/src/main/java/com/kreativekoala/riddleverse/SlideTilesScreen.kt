package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- classic puzzles (feat/puzzles-classic) --- Slide Tiles screen

@Composable
fun SlideTilesScreen(tier: Int, level: String, onBack: () -> Unit, onFinish: (Boolean, Int) -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var game by rememberSavedGame({ SlideTilesGame.newGame(tier, System.nanoTime()) }, { it.encode() }, { SlideTilesGame.decode(it) })
    var newBest by rememberSaveable { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val best = remember { ClassicStats.best(ctx, ClassicPuzzles.SLIDE_TILES, tier) }
    val solved = game.isSolved

    LaunchedEffect(solved) {
        if (solved) newBest = ClassicStats.submit(ctx, ClassicPuzzles.SLIDE_TILES, tier, game.moves, lowerIsBetter = true)
    }

    ClassicScaffold(
        title = "Slide Tiles",
        level = level,
        subtitle = "Put the numbers in order, empty space bottom right.",
        stats = listOf(
            "Moves" to game.moves.toString(),
            "Par" to game.par.toString(),
            "Best" to (best?.toString() ?: "-"),
        ),
        onBack = onBack,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BoxWithConstraints {
                val side = minOf(maxWidth, maxHeight)
                val n = game.n
                Column(
                    Modifier
                        .size(side)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(6.dp)
                ) {
                    for (r in 0 until n) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            for (c in 0 until n) {
                                val idx = r * n + c
                                val v = game.tiles[idx]
                                val home = v == idx + 1
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(3.dp)
                                        .background(
                                            when {
                                                v == 0 -> Color.Transparent
                                                home -> Color(0xFF2DC98E)
                                                else -> Color(0xFF6C4CF1)
                                            },
                                            RoundedCornerShape(10.dp),
                                        )
                                        .let {
                                            if (v == 0 || solved) it
                                            else it.clickable(role = Role.Button) {
                                                val next = game.tap(idx)
                                                if (next != null) {
                                                    game = next
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } else {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }
                                        }
                                        .semantics {
                                            contentDescription = if (v == 0) "Empty space, row ${r + 1} column ${c + 1}"
                                            else "Tile $v, row ${r + 1} column ${c + 1}${if (home) ", in place" else ""}"
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (v != 0) {
                                        val fs = (side / n).value * 0.38f
                                        Text(v.toString(), color = Color.White, fontSize = fs.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { game = game.undo() },
            enabled = game.canUndo && !solved,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Default.Undo, contentDescription = null)
            Text("  Undo")
        }
    }

    if (solved) {
        val score = ClassicPuzzles.efficiencyScore(game.par, game.moves)
        ClassicResultDialog(
            success = true,
            headline = "Solved!",
            lines = buildList {
                add("Moves: ${game.moves} (par ${game.par})")
                if (newBest) add("New best!")
            },
            onContinue = { if (!reported) { reported = true; onFinish(true, score) } },
        )
    }
}

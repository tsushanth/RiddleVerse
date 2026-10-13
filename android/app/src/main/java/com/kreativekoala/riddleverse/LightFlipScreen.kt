package com.kreativekoala.riddleverse

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp

// --- classic puzzles (feat/puzzles-classic) --- Light Flip screen

@Composable
fun LightFlipScreen(tier: Int, level: String, onBack: () -> Unit, onFinish: (Boolean, Int) -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var game by rememberSavedGame({ LightFlipGame.newGame(tier, System.nanoTime()) }, { it.encode() }, { LightFlipGame.decode(it) })
    var hint by rememberSaveable { mutableIntStateOf(-1) }
    var newBest by rememberSaveable { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val best = remember { ClassicStats.best(ctx, ClassicPuzzles.LIGHT_FLIP, tier) }
    val solved = game.isSolved

    LaunchedEffect(solved) {
        if (solved) newBest = ClassicStats.submit(ctx, ClassicPuzzles.LIGHT_FLIP, tier, game.moves, lowerIsBetter = true)
    }

    ClassicScaffold(
        title = "Light Flip",
        level = level,
        subtitle = "Tap a light to flip it and its neighbours. Turn them all off.",
        stats = listOf(
            "Taps" to game.moves.toString(),
            "Par" to game.par.toString(),
            "Lights on" to Integer.bitCount(game.lights).toString(),
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
                                val on = game.isOn(idx)
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(3.dp)
                                        .background(if (on) Color(0xFFFFB020) else Color(0xFF3A3463), RoundedCornerShape(12.dp))
                                        .let { if (hint == idx) it.border(BorderStroke(3.dp, Color(0xFF2DC98E)), RoundedCornerShape(12.dp)) else it }
                                        .let {
                                            if (solved) it else it.clickable(role = Role.Button) {
                                                game = game.press(idx)
                                                hint = -1
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                        .semantics {
                                            contentDescription = "Row ${r + 1} column ${c + 1}, light ${if (on) "on" else "off"}" +
                                                if (hint == idx) ", suggested tap" else ""
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { game = game.undo(); hint = -1 },
                enabled = game.canUndo && !solved,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Icon(Icons.Default.Undo, contentDescription = null); Text("  Undo") }
            FilledTonalButton(
                onClick = { hint = game.hintCell() ?: -1; game = game.withHintUsed() },
                enabled = !solved,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Icon(Icons.Default.Lightbulb, contentDescription = null); Text("  Hint (${game.hintsUsed})") }
        }
    }

    if (solved) {
        val score = ClassicPuzzles.efficiencyScore(game.par, game.moves, penalty = 10 * game.hintsUsed)
        ClassicResultDialog(
            success = true,
            headline = "All lights off!",
            lines = buildList {
                add("Taps: ${game.moves} (par ${game.par})")
                if (game.hintsUsed > 0) add("Hints used: ${game.hintsUsed}")
                if (newBest) add("New best!")
            },
            onContinue = { if (!reported) { reported = true; onFinish(true, score) } },
        )
    }
}

package com.kreativekoala.riddleverse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- classic puzzles (feat/puzzles-classic) --- Safe Sweep screen

/** Cell sizing: boards that can fit the width are shrunk to fit (no horizontal scroll); wider ones keep touch-sized cells and scroll. */
object SweepLayout {
    const val MIN_FIT_DP = 30f
    const val SCROLL_CELL_DP = 48f
    const val MAX_CELL_DP = 64f
    const val BOARD_PADDING_DP = 8f // 4dp each side

    fun fitsWidth(availWidthDp: Float, cols: Int): Boolean = (availWidthDp - BOARD_PADDING_DP) / cols >= MIN_FIT_DP

    fun cellDp(availWidthDp: Float, cols: Int): Float {
        val fit = (availWidthDp - BOARD_PADDING_DP) / cols
        return if (fit >= MIN_FIT_DP) minOf(fit, MAX_CELL_DP) else SCROLL_CELL_DP
    }
}

private val numberColors = listOf(
    Color.Transparent, Color(0xFF3AA0FF), Color(0xFF2DC98E), Color(0xFFFF6B6B), Color(0xFF6C4CF1),
    Color(0xFFFF8C00), Color(0xFF1AA070), Color(0xFF9B5DE5), Color(0xFF6B648F),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SafeSweepScreen(tier: Int, level: String, onBack: () -> Unit, onFinish: (Boolean, Int) -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val cfg = remember(tier) { SafeSweepRules.configFor(tier) }
    var game by rememberSavedGame({ SafeSweepGame.newGame(tier, System.nanoTime()) }, { it.encode() }, { SafeSweepGame.decode(it) })
    var flagMode by rememberSaveable { mutableStateOf(false) }
    var newBest by rememberSaveable { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val best = remember { ClassicStats.best(ctx, ClassicPuzzles.SAFE_SWEEP, tier) }
    val status = game.status
    val over = status != SweepStatus.PLAYING

    LaunchedEffect(status) {
        if (status == SweepStatus.WON) newBest = ClassicStats.submit(ctx, ClassicPuzzles.SAFE_SWEEP, tier, game.actions, lowerIsBetter = true)
    }

    ClassicScaffold(
        title = "Safe Sweep",
        level = level,
        subtitle = "${cfg.sizeLabel} board ${cfg.rows}x${cfg.cols}. Open every safe square. First tap is safe.",
        stats = listOf(
            "Mines left" to game.minesLeft.toString(),
            "Taps" to game.actions.toString(),
            "Best" to (best?.toString() ?: "-"),
        ),
        onBack = onBack,
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val cell: Dp = SweepLayout.cellDp(maxWidth.value, game.cols).dp
            Box(
                Modifier
                    .widthIn(max = maxWidth)
                    .heightIn(max = maxHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopStart,
            ) {
                Column(Modifier.padding(4.dp)) {
                    for (r in 0 until game.rows) {
                        Row {
                            for (c in 0 until game.cols) {
                                val idx = r * game.cols + c
                                SweepCell(
                                    game = game, idx = idx, size = cell, row = r, col = c,
                                    onTap = {
                                        if (!over) {
                                            val next = if (flagMode && !game.isRevealed(idx)) game.toggleFlag(idx) else game.tap(idx)
                                            if (next !== game) {
                                                game = next
                                                haptic.performHapticFeedback(
                                                    if (next.status == SweepStatus.LOST) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                                                )
                                            }
                                        }
                                    },
                                    onLongTap = {
                                        if (!over && !game.isRevealed(idx)) {
                                            game = game.toggleFlag(idx)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FilterChip(
            selected = flagMode,
            onClick = { flagMode = !flagMode },
            label = { Text(if (flagMode) "Flag mode: ON" else "Flag mode: OFF", fontSize = 15.sp) },
            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                contentDescription = "Flag mode toggle, currently ${if (flagMode) "on, taps place flags" else "off, taps open squares"}"
            },
        )
    }

    if (over) {
        val won = status == SweepStatus.WON
        val score = if (won) 50 + cfg.mines * 5 else (game.revealedCount - cfg.mines.coerceAtLeast(0)).coerceAtLeast(0)
        ClassicResultDialog(
            success = won,
            headline = if (won) "Board cleared!" else "Boom! You hit a mine",
            lines = buildList {
                add("Taps: ${game.actions}")
                add("Squares opened: ${game.revealedCount}")
                if (newBest) add("New best!")
            },
            onContinue = { if (!reported) { reported = true; onFinish(won, score) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SweepCell(
    game: SafeSweepGame, idx: Int, size: Dp, row: Int, col: Int, onTap: () -> Unit, onLongTap: () -> Unit,
) {
    val revealed = game.isRevealed(idx)
    val flagged = game.isFlagged(idx)
    val mine = revealed && game.isMine(idx)
    val exploded = idx == game.explodedIndex
    val count = if (revealed && !mine) game.adjacentMines(idx) else 0
    val desc = "Row ${row + 1} column ${col + 1}, " + when {
        exploded -> "mine, exploded"
        mine -> "mine"
        flagged -> "flagged"
        !revealed -> "hidden"
        count == 0 -> "empty"
        else -> "$count mines nearby"
    }
    val bg = when {
        exploded -> Color(0xFFD94848)
        revealed -> MaterialTheme.colorScheme.background
        else -> Color(0xFF6C4CF1)
    }
    Box(
        Modifier
            .size(size)
            .padding(1.5.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .combinedClickable(role = Role.Button, onClick = onTap, onLongClick = onLongTap)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        when {
            mine -> Canvas(Modifier.size(size * 0.5f)) {
                drawCircle(Color(0xFF231B4A), radius = this.size.minDimension / 2)
                drawCircle(Color.White.copy(alpha = 0.6f), radius = this.size.minDimension / 8, center = Offset(this.size.width * 0.38f, this.size.height * 0.38f))
            }
            flagged -> Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFFFB020), modifier = Modifier.size(size * 0.55f))
            revealed && count > 0 -> Text(
                count.toString(), color = numberColors[count.coerceIn(1, 8)],
                fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

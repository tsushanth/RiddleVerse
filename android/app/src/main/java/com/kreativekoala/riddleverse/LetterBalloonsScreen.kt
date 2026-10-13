package com.kreativekoala.riddleverse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

// --- word puzzles (feat/puzzles-word) --- "Letter Balloons" (hangman-style, balloons instead of a gallows)

private val balloonColors = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFB020), Color(0xFF3AA0FF), Color(0xFF9B5DE5),
    Color(0xFF2DC98E), Color(0xFFFF8C00), Color(0xFFE0569B), Color(0xFF5C6BC0)
)

@Composable
fun LetterBalloonsScreen(
    puzzleJson: String, levelLabel: String, onBack: () -> Unit, onFinished: (success: Boolean, score: Int) -> Unit
) {
    val context = LocalContext.current
    val stats = remember { WordPuzzleStats(PrefsKeyValueStore(context)) }
    val data = remember(puzzleJson) { JSONObject(puzzleJson) }
    val word = remember(data) { data.getString("word") }
    val category = remember(data) { data.getString("category") }
    val lives = remember(data) { data.getInt("lives") }

    var guessedEnc by rememberSaveable(puzzleJson) { mutableStateOf("") }
    var hints by rememberSaveable(puzzleJson) { mutableStateOf(0) }
    var recorded by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var reported by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    val game = LetterBalloonsGame(word, category, lives, guessedEnc.toSet(), hints)
    val score = game.score()

    LaunchedEffect(game.isOver) {
        if (game.isOver && !recorded) { recorded = true; stats.record("letterballoons", game.isWon) }
    }

    fun apply(g: LetterBalloonsGame) { guessedEnc = g.guessed.sorted().joinToString(""); hints = g.hintsUsed }

    WordPuzzleScaffold("Letter Balloons", levelLabel, onBack) {
        Text("Category: $category", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Guess the word before the balloons are all popped", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BalloonBunch(game.livesLeft, lives)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val groups = word.split(" ")
            val longest = groups.maxOf { it.length }
            val tile: Dp = minOf(40.dp, (maxWidth - 8.dp - 4.dp * (longest - 1)) / longest).coerceAtLeast(22.dp)
            val masked = game.maskedLetters
            var idx = 0
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { g ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (k in g.indices) {
                            val ch = masked[idx]; val pos = idx + 1; idx++
                            val shown = ch ?: if (game.isLost) g[k] else null
                            Box(
                                Modifier.size(width = tile, height = 48.dp).clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .wordTileBorder(if (ch == null && game.isLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                                    .clearAndSetSemantics { contentDescription = "Letter $pos, ${if (shown == null) "hidden" else shown.uppercaseChar().toString()}" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(shown?.uppercaseChar()?.toString() ?: "", fontSize = (tile.value * 0.55f).sp, fontWeight = FontWeight.Bold,
                                    color = if (ch == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    idx++ // skip the space
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Balloons left: ${game.livesLeft}/$lives" + if (game.wrongLetters.isNotEmpty()) "   Missed: ${game.wrongLetters.joinToString(" ") { it.uppercase() }}" else "",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 4.dp))
        if (!game.isOver) {
            WordActionButton("Hint (-20 pts)", "Reveal one letter for a score penalty") { apply(game.hint()) }
            Spacer(Modifier.height(6.dp))
            val states = HashMap<Char, LetterState>()
            game.guessed.forEach { states[it] = if (it in word) LetterState.CORRECT else LetterState.ABSENT }
            WordKeyboard(keyStates = states, enabled = true, onLetter = { apply(game.guess(it)) }, onDelete = {}, onEnter = {}, showActions = false)
        } else {
            WordPuzzleResultCard(
                success = game.isWon,
                headline = if (game.isWon) "You saved the balloons!" else "The word was ${word.uppercase()}",
                lines = emptyList(), score = score, stats = stats.snapshot("letterballoons"),
                shareText = null
            ) { if (!reported) { reported = true; onFinished(game.isWon, score) } }
        }
    }
}

@Composable
private fun BalloonBunch(left: Int, total: Int) {
    Canvas(
        Modifier.fillMaxWidth().height(130.dp).padding(vertical = 4.dp)
            .semantics { contentDescription = "$left of $total balloons left" }
    ) {
        val basket = Offset(size.width / 2f, size.height - 14.dp.toPx())
        val bw = 30.dp.toPx()
        drawRect(Color(0xFF8D6E63), topLeft = Offset(basket.x - bw / 2, basket.y), size = Size(bw, 14.dp.toPx()))
        val r = 17.dp.toPx()
        val gap = minOf(r * 2.3f, (size.width - 2 * r) / total.coerceAtLeast(1))
        val startX = size.width / 2f - gap * (total - 1) / 2f
        for (i in 0 until total) {
            val cx = startX + gap * i
            val cy = r + 8.dp.toPx() + (if (i % 2 == 0) 0f else 12.dp.toPx())
            if (i < left) {
                drawLine(Color(0xFF6B648F), Offset(cx, cy + r), basket, strokeWidth = 1.5f.dp.toPx())
                drawOval(balloonColors[i % balloonColors.size], Offset(cx - r * 0.85f, cy - r), Size(r * 1.7f, r * 2f))
            } else {
                // popped: small scrap on the string anchor
                drawCircle(Color(0x55888888), 3.dp.toPx(), Offset(cx, cy + r), style = Stroke(1.dp.toPx()))
            }
        }
    }
}

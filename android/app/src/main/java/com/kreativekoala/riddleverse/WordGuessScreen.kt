package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

// --- word puzzles (feat/puzzles-word) --- "Word Guess": five letters, six tries

private fun encodeGuess(g: WordGuessGame) = g.guesses.joinToString(",") + "|" + g.current + "|" + (if (g.hintUsed) "1" else "0")

private fun decodeGuess(answer: String, hard: Boolean, s: String): WordGuessGame {
    val p = s.split("|")
    if (p.size < 3) return WordGuessGame(answer, hard)
    return WordGuessGame(answer, hard, p[0].split(",").filter { it.isNotEmpty() }, p[1], p[2] == "1")
}

@Composable
fun WordGuessScreen(
    puzzleJson: String, levelLabel: String, onBack: () -> Unit, onFinished: (success: Boolean, score: Int) -> Unit
) {
    val context = LocalContext.current
    val lists = remember { WordPuzzleLists.get(context) }
    val stats = remember { WordPuzzleStats(PrefsKeyValueStore(context)) }
    val data = remember(puzzleJson) { JSONObject(puzzleJson) }
    val answer = remember(data) { data.getString("answer") }
    val hard = remember(data) { data.optBoolean("hard", false) }

    var enc by rememberSaveable(puzzleJson) { mutableStateOf("||0") }
    var hintPos by rememberSaveable(puzzleJson) { mutableStateOf(-1) }
    var recorded by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var reported by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val game = remember(enc) { decodeGuess(answer, hard, enc) }
    fun update(g: WordGuessGame) { enc = encodeGuess(g) }

    LaunchedEffect(message) {
        if (message.isNotEmpty()) { delay(1800); message = "" }
    }

    val status = game.status
    LaunchedEffect(status) {
        if (status != GuessStatus.PLAYING && !recorded) {
            recorded = true
            stats.record("wordguess", status == GuessStatus.WON, game.guesses.size, WordGuessRules.MAX_TRIES)
        }
    }
    val score = if (status == GuessStatus.WON) WordGuessRules.score(game.guesses.size, game.hintUsed, hard) else 0

    WordPuzzleScaffold("Word Guess" + if (hard) " (hard)" else "", levelLabel, onBack) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tile: Dp = minOf(58.dp, (maxWidth - 6.dp * 4 - 16.dp) / 5)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0 until WordGuessRules.MAX_TRIES) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val submitted = game.guesses.getOrNull(row)
                            val fb = submitted?.let { WordGuessRules.evaluate(it, answer) }
                            val typing = if (row == game.guesses.size && status == GuessStatus.PLAYING) game.current else ""
                            for (col in 0 until WordGuessRules.WORD_LENGTH) {
                                val letter = submitted?.get(col) ?: typing.getOrNull(col)
                                WordGuessTile(row, col, letter, fb?.get(col), tile)
                            }
                        }
                    }
                }
                WordPuzzleMessage(message)
                if (hintPos >= 0 && status == GuessStatus.PLAYING) {
                    Text("Hint: letter ${hintPos + 1} is ${answer[hintPos].uppercaseChar()}", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                if (status == GuessStatus.PLAYING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        WordActionButton("Hint (-30 pts)", "Reveal one letter for a score penalty", enabled = hintPos < 0) {
                            val p = game.hintPosition()
                            if (p >= 0) { hintPos = p; update(game.copy(hintUsed = true)) }
                        }
                        Text("${WordGuessRules.MAX_TRIES - game.guesses.size} tries left", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    WordKeyboard(
                        keyStates = WordGuessRules.keyboardStates(game.guesses, answer), enabled = true,
                        onLetter = { update(game.type(it)) },
                        onDelete = { update(game.backspace()) },
                        onEnter = {
                            val (next, outcome) = game.submit { lists.valid5.contains(it) }
                            when (outcome) {
                                SubmitOutcome.Accepted -> update(next)
                                SubmitOutcome.TooShort -> message = "Not enough letters"
                                SubmitOutcome.NotInList -> message = "Not in word list"
                                is SubmitOutcome.HardMode -> message = outcome.message
                                SubmitOutcome.GameOver -> {}
                            }
                        }
                    )
                } else {
                    WordPuzzleResultCard(
                        success = status == GuessStatus.WON,
                        headline = if (status == GuessStatus.WON) "Solved in ${game.guesses.size}!" else "The word was ${answer.uppercase()}",
                        lines = emptyList(), score = score,
                        stats = stats.snapshot("wordguess", WordGuessRules.MAX_TRIES),
                        shareText = WordGuessRules.shareText(game.guesses, answer, status == GuessStatus.WON, hard)
                    ) {
                        if (!reported) { reported = true; onFinished(status == GuessStatus.WON, score) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordGuessTile(row: Int, col: Int, letter: Char?, state: LetterState?, size: Dp) {
    val neutral = MaterialTheme.colorScheme.surface
    val bg = if (state != null) WordPuzzleColors.container(state, neutral) else neutral
    val fg = if (state != null) WordPuzzleColors.content(state, MaterialTheme.colorScheme.onSurface) else MaterialTheme.colorScheme.onSurface
    val border = if (state == null) {
        if (letter != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
    } else bg
    val desc = when {
        letter == null -> "Row ${row + 1}, column ${col + 1}, empty"
        state == null -> "Row ${row + 1}, column ${col + 1}, ${letter.uppercaseChar()}, not submitted"
        else -> "Row ${row + 1}, column ${col + 1}, ${letter.uppercaseChar()}, ${WordPuzzleColors.describe(state)}"
    }
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(bg).wordTileBorder(border)
            .clearAndSetSemantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        Text((letter?.uppercaseChar() ?: ' ').toString(), fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold, color = fg)
        if (state != null) {
            Text(WordPuzzleColors.glyph(state), fontSize = 10.sp, color = fg,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 1.dp))
        }
    }
}

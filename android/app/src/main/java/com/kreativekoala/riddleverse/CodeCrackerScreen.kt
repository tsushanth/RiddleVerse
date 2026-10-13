package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

// --- word puzzles (feat/puzzles-word) --- "Code Cracker": deduce a hidden colour code

private val pegColors = listOf(
    Color(0xFFFF6B6B), Color(0xFF3AA0FF), Color(0xFF2DC98E), Color(0xFFFFD34D), Color(0xFFB27AF0), Color(0xFFFF8C00)
)
private val pegSymbols = listOf("▲", "■", "●", "◆", "★", "✚") // triangle square circle diamond star plus
private val pegInk = Color(0xFF231B4A)

@Composable
fun CodeCrackerScreen(
    puzzleJson: String, levelLabel: String, onBack: () -> Unit, onFinished: (success: Boolean, score: Int) -> Unit
) {
    val context = LocalContext.current
    val stats = remember { WordPuzzleStats(PrefsKeyValueStore(context)) }
    val puzzle = remember(puzzleJson) { CodeCrackerPuzzle.fromJson(JSONObject(puzzleJson)) }
    val cfg = puzzle.config

    var guessesEnc by rememberSaveable(puzzleJson) { mutableStateOf("") }
    var currentEnc by rememberSaveable(puzzleJson) { mutableStateOf("-".repeat(cfg.positions)) }
    var selected by rememberSaveable(puzzleJson) { mutableStateOf(0) }
    var hints by rememberSaveable(puzzleJson) { mutableStateOf(0) }
    var hintPos by rememberSaveable(puzzleJson) { mutableStateOf(-1) }
    var recorded by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var reported by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(message) { if (message.isNotEmpty()) { delay(1800); message = "" } }

    val guesses = remember(guessesEnc) { guessesEnc.split(";").filter { it.isNotEmpty() }.map { g -> g.map { it - '0' } } }
    val feedbacks = remember(guesses, puzzle) { guesses.map { CodeCrackerRules.feedback(puzzle.secret, it) } }
    val won = guesses.lastOrNull() == puzzle.secret
    val lost = !won && guesses.size >= cfg.maxTries
    val over = won || lost
    val score = if (won) CodeCrackerRules.score(guesses.size, cfg.maxTries, hints) else 0

    LaunchedEffect(over) {
        if (over && !recorded) { recorded = true; stats.record("codecracker", won, guesses.size, cfg.maxTries) }
    }

    fun place(color: Int) {
        val i = if (selected in 0 until cfg.positions) selected else 0
        val chars = currentEnc.toCharArray()
        chars[i] = ('0' + color)
        currentEnc = String(chars)
        val next = (0 until cfg.positions).firstOrNull { it != i && chars[it] == '-' && it > i } ?: (0 until cfg.positions).firstOrNull { chars[it] == '-' }
        if (next != null) selected = next
    }

    WordPuzzleScaffold("Code Cracker", levelLabel, onBack) {
        Text(
            "Crack the ${cfg.positions}-peg code in ${cfg.maxTries} tries. Dark peg = right colour and place; light peg = right colour, wrong place." +
                if (cfg.allowRepeats) " Colours can repeat." else " No colour repeats.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp)
        )
        val slot: Dp = 48.dp
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            guesses.forEachIndexed { r, g ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.forEachIndexed { c, color -> Peg(color, 36.dp, "Try ${r + 1}, position ${c + 1}", false, null) }
                    FeedbackPegs(feedbacks[r], cfg.positions, "Try ${r + 1}")
                }
            }
            if (!over) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until cfg.positions) {
                        val ch = currentEnc[i]
                        Peg(if (ch == '-') -1 else ch - '0', slot, "Your try, position ${i + 1}", i == selected) { selected = i }
                    }
                }
                Text("Try ${guesses.size + 1} of ${cfg.maxTries}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hintPos >= 0 && !over) {
            Text("Hint: position ${hintPos + 1} is ${CodeCrackerRules.colorNames[puzzle.secret[hintPos]]} ${pegSymbols[puzzle.secret[hintPos]]}",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        WordPuzzleMessage(message)
        if (!over) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (color in 0 until cfg.colors) {
                    Peg(color, 48.dp, "Choose ${CodeCrackerRules.colorNames[color]}", false) { place(color) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WordActionButton("Clear", "Clear your try") { currentEnc = "-".repeat(cfg.positions); selected = 0 }
                WordActionButton("Hint (-20 pts)", "Reveal one position of the code", enabled = hintPos < 0) {
                    val unsolved = (0 until cfg.positions).filter { p -> guesses.none { it[p] == puzzle.secret[p] } }.ifEmpty { listOf(0) }
                    hintPos = unsolved.first(); hints++
                }
                Button(onClick = {
                    if (currentEnc.contains('-')) message = "Fill every peg"
                    else {
                        val g = currentEnc.map { it - '0' }
                        if (!cfg.allowRepeats && g.toSet().size != g.size) message = "No repeated colours on this level"
                        else {
                            guessesEnc = (guesses + listOf(g)).joinToString(";") { it.joinToString("") }
                            currentEnc = "-".repeat(cfg.positions); selected = 0
                        }
                    }
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Check") }
            }
        } else {
            if (lost) {
                Text("The code was:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    puzzle.secret.forEachIndexed { i, c -> Peg(c, slot, "Code position ${i + 1}", false, null) }
                }
            }
            WordPuzzleResultCard(
                success = won,
                headline = if (won) "Cracked in ${guesses.size}!" else "Out of tries",
                lines = emptyList(), score = score, stats = stats.snapshot("codecracker"),
                shareText = shareText(guesses, feedbacks, won, cfg)
            ) { if (!reported) { reported = true; onFinished(won, score) } }
        }
    }
}

private fun shareText(g: List<List<Int>>, f: List<CodeFeedback>, won: Boolean, cfg: CodeCrackerRules.Config): String {
    val sb = StringBuilder("RiddleVerse Code Cracker ${if (won) g.size.toString() else "X"}/${cfg.maxTries}\n")
    f.forEach { fb -> sb.append("⚫".repeat(fb.exact)).append("⚪".repeat(fb.misplaced)).append(if (fb.exact + fb.misplaced == 0) "➖" else "").append('\n') }
    return sb.toString().trimEnd()
}

@Composable
private fun Peg(color: Int, size: Dp, label: String, selected: Boolean, onClick: (() -> Unit)? = null) {
    val name = if (color < 0) "empty" else CodeCrackerRules.colorNames[color]
    val base = Modifier.size(size).clip(CircleShape)
        .background(if (color < 0) MaterialTheme.colorScheme.surface else pegColors[color])
        .wordTileBorder(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
    val mod = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Box(mod.clearAndSetSemantics { contentDescription = "$label, $name${if (selected) ", selected" else ""}" }, contentAlignment = Alignment.Center) {
        if (color >= 0) Text(pegSymbols[color], fontSize = (size.value * 0.4f).sp, color = pegInk, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeedbackPegs(fb: CodeFeedback, positions: Int, label: String) {
    val total = positions
    Row(
        Modifier.padding(start = 6.dp).clearAndSetSemantics { contentDescription = "$label: ${fb.exact} right colour and place, ${fb.misplaced} right colour wrong place" },
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until total) {
            val kind = when { i < fb.exact -> 2; i < fb.exact + fb.misplaced -> 1; else -> 0 }
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(when (kind) { 2 -> pegInk; 1 -> Color.White; else -> Color.Transparent })
                    .wordTileBorder(if (kind == 0) MaterialTheme.colorScheme.outline else pegInk, CircleShape)
            )
        }
    }
}

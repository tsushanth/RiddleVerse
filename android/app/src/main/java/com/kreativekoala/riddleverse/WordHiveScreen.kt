package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// --- word puzzles (feat/puzzles-word) --- "Word Hive": seven letters, centre letter required

private val HexShape = GenericShape { size, _ ->
    val cx = size.width / 2f; val cy = size.height / 2f; val r = minOf(cx, cy)
    for (i in 0 until 6) {
        val a = (PI / 180.0) * (60 * i - 30)
        val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Everything on the Hive page except the honeycomb: instructions, rank, progress, goal, entry, message and two button rows. */
private const val HIVE_FIXED_DP = 300f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordHiveScreen(
    puzzleJson: String, levelLabel: String, onBack: () -> Unit, onFinished: (success: Boolean, score: Int) -> Unit
) {
    val context = LocalContext.current
    val stats = remember { WordPuzzleStats(PrefsKeyValueStore(context)) }
    val data = remember(puzzleJson) { JSONObject(puzzleJson) }
    val puzzle = remember(data) { WordHivePuzzle.fromJson(data) }
    val difficulty = remember(data) { data.optString("difficulty", "Medium") }
    val solutions = remember(puzzle) { puzzle.words.toHashSet() }
    val targetIdx = WordHiveRules.targetRankIndex(difficulty)

    var foundEnc by rememberSaveable(puzzleJson) { mutableStateOf("") }
    var entry by rememberSaveable(puzzleJson) { mutableStateOf("") }
    var shuffleSeed by rememberSaveable(puzzleJson) { mutableStateOf(0) }
    var hints by rememberSaveable(puzzleJson) { mutableStateOf(0) }
    var finished by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var reported by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(message) { if (message.isNotEmpty()) { delay(1800); message = "" } }

    val found = remember(foundEnc) { foundEnc.split(",").filter { it.isNotEmpty() } }
    val score = found.sumOf { WordHiveRules.wordScore(it, puzzle.letterSet) }
    val rankIdx = WordHiveRules.rankIndex(score, puzzle.maxScore)
    val success = rankIdx >= targetIdx
    val finalScore = if (success) (40 + rankIdx * 10 - hints * 10).coerceAtLeast(10) else 0
    val outer = remember(puzzle, shuffleSeed) {
        if (shuffleSeed == 0) puzzle.letters else puzzle.letters.shuffled(Random(shuffleSeed.toLong()))
    }

    LaunchedEffect(finished) {
        if (finished) stats.record("wordhive", success)
    }

    fun submit() {
        val e = entry
        val why = WordHiveRules.rejection(e, puzzle.letterSet, puzzle.center, solutions, found)
        if (why != null) { message = why; return }
        foundEnc = (found + e).joinToString(",")
        val pts = WordHiveRules.wordScore(e, puzzle.letterSet)
        message = if (WordHiveRules.isPangram(e, puzzle.letterSet)) "Pangram! +$pts" else "+$pts"
        entry = ""
    }

    WordPuzzleScaffold(
        "Word Hive", levelLabel, onBack,
        // Pinned (measured): action rows are always on screen; the honeycomb scales into what is left.
        bottomBar = if (finished) null else ({
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WordActionButton("Delete", "Delete last letter", enabled = entry.isNotEmpty()) { entry = entry.dropLast(1) }
                WordActionButton("Shuffle", "Shuffle outer letters") { shuffleSeed += 1 }
                Button(onClick = { submit() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Enter") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WordActionButton("Hint (-10 pts)", "Show the start of a word you have not found") {
                    val missing = puzzle.words.filter { it !in found }
                    if (missing.isEmpty()) message = "You found them all!"
                    else {
                        val w = missing.first { it.length == missing.minOf { m -> m.length } }
                        hints++
                        message = "Try: ${w.take(2).uppercase()}... (${w.length} letters)"
                    }
                }
                WordActionButton(if (success) "Finish" else "Give up", if (success) "Finish with your rank" else "Give up and see results") { finished = true }
            }
            if (success) Text("Goal reached! Keep going for a higher rank or finish.", fontSize = 13.sp,
                color = WordPuzzleColors.correct, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        })
    ) {
        // On short screens (< 520dp under the header) drop the instruction line so the honeycomb and the button rows fit.
        val hiveCompact = LocalWordViewport.current < 520.dp
        if (!hiveCompact) {
            Text("Make words of 4+ letters that use the gold centre letter", fontSize = 13.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }
        Text("${WordHiveRules.ranks[rankIdx].first}  -  $score pts", fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        LinearProgressIndicator(
            progress = { if (puzzle.maxScore == 0) 0f else (score.toFloat() / puzzle.maxScore).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        Text("Goal: reach ${WordHiveRules.ranks[targetIdx].first}   Words ${found.size}/${puzzle.words.size}", fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!finished) {
            Spacer(Modifier.height(8.dp))
            // current entry
            Row(Modifier.heightIn(min = 44.dp).clearAndSetSemantics { contentDescription = "Current word ${entry.uppercase()}" }) {
                entry.forEach {
                    Text(it.uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = if (it == puzzle.center) WordPuzzleColors.present else if (it in puzzle.letterSet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                }
            }
            WordPuzzleMessage(message)
            HexBoard(puzzle.center, outer, WordFit.boardScale(LocalWordViewport.current.value - (if (LocalWordSideBySide.current) 160f else 260f) - (if (hiveCompact) 0f else 36f), 222f)) { entry += it }
        } else {
            val missedPangrams = puzzle.pangrams.filter { it !in found }
            WordPuzzleResultCard(
                success = success,
                headline = if (success) "${WordHiveRules.ranks[rankIdx].first}!" else "Final rank: ${WordHiveRules.ranks[rankIdx].first}",
                lines = listOf("Found ${found.size} of ${puzzle.words.size} words, $score of ${puzzle.maxScore} points") +
                    (if (missedPangrams.isNotEmpty()) listOf("Pangram${if (missedPangrams.size > 1) "s" else ""}: ${missedPangrams.joinToString(", ") { it.uppercase() }}") else emptyList()),
                score = finalScore, stats = stats.snapshot("wordhive"),
                shareText = "RiddleVerse Word Hive: ${WordHiveRules.ranks[rankIdx].first}, ${found.size}/${puzzle.words.size} words"
            ) { if (!reported) { reported = true; onFinished(success, finalScore) } }
        }
        Spacer(Modifier.height(8.dp))
        if (found.isNotEmpty()) {
            Text("Found words", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                found.sorted().forEach { w ->
                    val pan = WordHiveRules.isPangram(w, puzzle.letterSet)
                    Text(w, fontSize = 14.sp, fontWeight = if (pan) FontWeight.Bold else FontWeight.Normal,
                        color = if (pan) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (pan) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clearAndSetSemantics { contentDescription = if (pan) "$w, pangram" else w })
                }
            }
        }
    }
}

@Composable
private fun HexBoard(center: Char, outer: List<Char>, scale: Float = 1f, onLetter: (Char) -> Unit) {
    val hex = 70.dp * scale
    val radius = 76.dp * scale
    val density = LocalDensity.current
    Box(Modifier.size(radius * 2 + hex, radius * 2 + hex)) {
        @Composable
        fun cell(c: Char, dx: Float, dy: Float, isCenter: Boolean) {
            val ox = with(density) { ((radius * 2 + hex) / 2 - hex / 2).toPx() + dx }
            val oy = with(density) { ((radius * 2 + hex) / 2 - hex / 2).toPx() + dy }
            Box(
                Modifier.offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }.size(hex).clip(HexShape)
                    .background(if (isCenter) WordPuzzleColors.present else MaterialTheme.colorScheme.surface)
                    .clickable(role = Role.Button) { onLetter(c) }
                    .clearAndSetSemantics { contentDescription = "Letter ${c.uppercaseChar()}${if (isCenter) ", centre letter, required" else ""}" },
                contentAlignment = Alignment.Center
            ) {
                Text(c.uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = if (isCenter) WordPuzzleColors.onPresent else MaterialTheme.colorScheme.onSurface)
            }
        }
        cell(center, 0f, 0f, true)
        val rPx = with(density) { radius.toPx() }
        outer.forEachIndexed { i, c ->
            val a = (PI / 180.0) * (60 * i - 90)
            cell(c, (rPx * cos(a)).toFloat(), (rPx * sin(a)).toFloat(), false)
        }
    }
}

package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

// --- word puzzles (feat/puzzles-word) --- "Word Ladder"

/** Instruction (2 lines) + par line + start row + candidate row + goal + message + buttons: what sits above the keyboard on a fresh ladder. */
private const val WORD_LADDER_FIXED_DP = 330f

@Composable
fun WordLadderScreen(
    puzzleJson: String, levelLabel: String, onBack: () -> Unit, onFinished: (success: Boolean, score: Int) -> Unit
) {
    val context = LocalContext.current
    val lists = rememberWordLists() ?: run { WordPuzzleLoading(); return }
    val stats = remember { WordPuzzleStats(PrefsKeyValueStore(context)) }
    val puzzle = remember(puzzleJson) { WordLadderPuzzle.fromJson(JSONObject(puzzleJson)) }
    // The word graph is the expensive part of a ladder: build it off the main thread.
    val graph = produceState<WordLadderGraph?>(null, puzzle, lists) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            WordLadderGraph(WordLadderGenerator.poolFor(lists, puzzle.length) + puzzle.solution)
        }
    }.value ?: run { WordPuzzleLoading(); return }

    var chainEnc by rememberSaveable(puzzleJson) { mutableStateOf(puzzle.start) }
    var candidate by rememberSaveable(puzzleJson) { mutableStateOf(puzzle.start) }
    var selected by rememberSaveable(puzzleJson) { mutableStateOf(-1) }
    var hints by rememberSaveable(puzzleJson) { mutableStateOf(0) }
    var gaveUp by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var recorded by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var reported by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(message) { if (message.isNotEmpty()) { delay(2000); message = "" } }

    val chain = remember(chainEnc) { chainEnc.split(",") }
    val solved = chain.last() == puzzle.target
    val over = solved || gaveUp
    val steps = chain.size - 1
    val score = if (solved) WordLadderRules.score(steps, puzzle.par, hints) else 0

    LaunchedEffect(over) {
        if (over && !recorded) { recorded = true; stats.record("wordladder", solved) }
    }

    fun resetCandidate() { candidate = chain.last(); selected = -1 }

    WordPuzzleScaffold("Word Ladder", levelLabel, onBack) {
        Text("Change one letter at a time to turn ${puzzle.start.uppercase()} into ${puzzle.target.uppercase()}",
            fontSize = 14.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Par ${puzzle.par} steps   You: $steps", fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tile: Dp = minOf(54.dp, (maxWidth - 16.dp - 6.dp * (puzzle.length - 1)) / puzzle.length).coerceAtLeast(48.dp)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // The chain grows by a row per step: keep it in its own scroll area (newest row in view) so the
                // keyboard, ADD included, stays pinned on screen instead of being pushed below the fold.
                val keyH = WordFit.keyHeight(LocalWordViewport.current.value, WORD_LADDER_FIXED_DP).dp
                val chainScroll = rememberScrollState()
                LaunchedEffect(chain.size) { chainScroll.animateScrollTo(chainScroll.maxValue) }
                // Show a whole number of rows (never a clipped one at the top); newest row scrolls into view.
                val rowH = tile + 6.dp
                val chainAvail = LocalWordViewport.current - 224.dp - keyH * 3
                val visibleRows = ((chainAvail + 6.dp) / rowH).toInt().coerceAtLeast(2)
                val chainMax = rowH * visibleRows - 6.dp
                if (!over) {
                    Text("Goal: ${puzzle.target.uppercase()}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(
                    (if (!over) Modifier.heightIn(max = chainMax).verticalScroll(chainScroll) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                chain.forEachIndexed { i, w ->
                    val prev = chain.getOrNull(i - 1)
                    LadderRow(w, prev, tile, if (i == 0) "Start word" else "Step $i", tint = if (w == puzzle.target) WordPuzzleColors.correct else null)
                }
                if (!over) {
                    LadderCandidateRow(candidate, chain.last(), selected, tile) { selected = it }
                } else if (gaveUp && !solved) {
                    Text("One shortest path:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    puzzle.solution.forEachIndexed { i, w -> LadderRow(w, puzzle.solution.getOrNull(i - 1), tile, "Solution step $i", null) }
                }
                }
                WordPuzzleMessage(message)
                if (!over) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WordActionButton("Undo", "Remove the last word", enabled = chain.size > 1) {
                            chainEnc = chain.dropLast(1).joinToString(","); candidate = chain[chain.size - 2]; selected = -1
                        }
                        WordActionButton("Hint", "Show the next word on a shortest path") {
                            val path = graph.shortestPath(chain.last(), puzzle.target)
                            if (path != null && path.size > 1) {
                                val nxt = path[1]
                                val pos = nxt.indices.first { nxt[it] != chain.last()[it] }
                                hints++
                                message = "Change letter ${pos + 1} to ${nxt[pos].uppercaseChar()}"
                            } else message = "No path from here - undo a step"
                        }
                        WordActionButton("Give up", "Reveal the solution and end this puzzle") { gaveUp = true }
                    }
                    Spacer(Modifier.height(4.dp))
                    WordKeyboard(
                        keyHeight = keyH,
                        keyStates = emptyMap(), enabled = true, enterLabel = "ADD",
                        onLetter = { c ->
                            if (selected < 0) message = "Tap a tile to choose which letter to change"
                            else candidate = candidate.replaceRange(selected, selected + 1, c.toString())
                        },
                        onDelete = { resetCandidate() },
                        onEnter = {
                            val why = WordLadderRules.rejection(graph, chain, candidate)
                            if (why != null) message = why
                            else { chainEnc = (chain + candidate).joinToString(","); selected = -1 }
                        }
                    )
                } else {
                    WordPuzzleResultCard(
                        success = solved,
                        headline = if (solved) "Ladder complete in $steps! ${"★".repeat(WordLadderRules.stars(steps, puzzle.par))}" else "Better luck next time",
                        lines = listOf(if (solved) (if (steps <= puzzle.par) "You matched the shortest route." else "Shortest route: ${puzzle.par} steps.") else ""),
                        score = score, stats = stats.snapshot("wordladder"),
                        shareText = if (solved) "RiddleVerse Word Ladder ${puzzle.start.uppercase()} to ${puzzle.target.uppercase()}: $steps steps (par ${puzzle.par})" else null
                    ) { if (!reported) { reported = true; onFinished(solved, score) } }
                }
            }
        }
    }
}

@Composable
private fun LadderRow(word: String, prev: String?, size: Dp, label: String, tint: androidx.compose.ui.graphics.Color?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        word.forEachIndexed { i, c ->
            val changed = prev != null && prev[i] != c
            val bg = tint ?: if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            val fg = if (tint != null || changed) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
            Box(
                Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(bg)
                    .clearAndSetSemantics { contentDescription = "$label, letter ${i + 1}, ${c.uppercaseChar()}${if (changed) ", changed" else ""}" },
                contentAlignment = Alignment.Center
            ) { Text(c.uppercase(), fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold, color = fg) }
        }
    }
}

@Composable
private fun LadderCandidateRow(candidate: String, last: String, selected: Int, size: Dp, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        candidate.forEachIndexed { i, c ->
            val isSel = i == selected
            val changed = c != last[i]
            Box(
                Modifier.size(size).clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .wordTileBorder(if (isSel) MaterialTheme.colorScheme.primary else if (changed) WordPuzzleColors.present else MaterialTheme.colorScheme.outline)
                    .clickable(role = Role.Button, onClickLabel = "Select letter ${i + 1} to change") { onSelect(i) }
                    .clearAndSetSemantics { contentDescription = "Your word, letter ${i + 1}, ${c.uppercaseChar()}${if (isSel) ", selected" else ""}. Double tap to change this letter" },
                contentAlignment = Alignment.Center
            ) { Text(c.uppercase(), fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

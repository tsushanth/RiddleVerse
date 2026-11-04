package com.kreativekoala.riddleverse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.riddleverse.ui.CappedFontScale
import kotlinx.coroutines.delay
import org.json.JSONObject

// --- linkup (feat/game-linkup) --- "Linkup" screen. Text is English-only like the other word games.

/** Tier colours (fill, text). Each tier also has its own shape glyph and name, so colour is never the only cue. */
private fun tierColors(tier: Int): Pair<Color, Color> = when (tier) {
    1 -> Color(0xFFFFA94D) to Color(0xFF2B1600)
    2 -> Color(0xFF4DD0B0) to Color(0xFF04291F)
    3 -> Color(0xFFF06292) to Color(0xFF330012)
    else -> Color(0xFF5C6BC0) to Color.White
}

@Composable
fun LinkupScreen(
    puzzleJson: String, levelLabel: String, onBack: () -> Unit, onFinished: (success: Boolean, score: Int) -> Unit
) {
    val context = LocalContext.current
    val stats = remember { WordPuzzleStats(PrefsKeyValueStore(context)) }
    val data = remember(puzzleJson) { JSONObject(puzzleJson) }
    val puzzle = remember(data) { LinkupPuzzle.fromJson(data) }
    val profile = remember(data) { LinkupRules.profile(data.optString("difficulty", "Medium")) }

    var enc by rememberSaveable(puzzleJson) { mutableStateOf(LinkupState().encode()) }
    var reported by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(message) { if (message.isNotEmpty()) { delay(2200); message = "" } }

    val state = remember(enc) { LinkupState.decode(enc) }
    val won = LinkupRules.isWon(state)
    val lost = LinkupRules.isLost(state)
    val finished = won || lost
    val score = LinkupRules.score(state, profile)
    val tiles = remember(puzzle, state.solved, state.shuffle) { LinkupRules.available(puzzle, state) }

    LaunchedEffect(finished) { if (finished) stats.record("linkup", won) }

    fun update(s: LinkupState) { enc = s.encode() }

    fun submit() {
        val (next, result) = LinkupRules.submit(puzzle, state)
        update(next)
        message = when (result) {
            LinkupResult.SOLVED -> "Found it: ${puzzle.groups[next.solved.last()].title}"
            LinkupResult.CLOSE -> if (profile.closeFeedback) "Close! Three of those belong together" else "Not a group"
            LinkupResult.WRONG -> "Not a group"
            LinkupResult.ALREADY_TRIED -> "You already tried that set"
            LinkupResult.INCOMPLETE -> "Select four tiles first"
            LinkupResult.FINISHED -> ""
        }
    }

    WordPuzzleScaffold(
        "Linkup", levelLabel, onBack,
        bottomBar = if (finished) null else ({
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WordActionButton("Shuffle", "Shuffle the remaining tiles") { update(LinkupRules.shuffle(state)) }
                WordActionButton("Clear", "Deselect all tiles", enabled = state.selected.isNotEmpty()) { update(LinkupRules.deselectAll(state)) }
                Button(
                    onClick = { submit() }, enabled = state.selected.size == LinkupRules.GROUP_SIZE,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Submit the four selected tiles" }
                ) { Text("Submit") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val left = LinkupRules.hintsLeft(state, profile)
                WordActionButton(
                    "Hint ($left left)", "Hint: mark one tile of the easiest group. $left hints left", enabled = left > 0
                ) {
                    val (next, word) = LinkupRules.hint(puzzle, state, profile)
                    update(next)
                    message = if (word == null) "No hints left" else "Hint: ${word} belongs to the easiest group still hidden"
                }
                WordActionButton("Give up", "Give up and reveal the groups") { update(LinkupRules.giveUp(state)) }
            }
        })
    ) {
        Text(
            "Pick four tiles that share a link", fontSize = 13.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val left = LinkupRules.mistakesLeft(state)
        Text(
            "Mistakes left: " + "●".repeat(left) + "○".repeat(LinkupRules.MAX_MISTAKES - left),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
                .clearAndSetSemantics { contentDescription = "Mistakes left: $left of ${LinkupRules.MAX_MISTAKES}" }
        )
        WordPuzzleMessage(message)

        state.solved.forEach { LinkupBanner(puzzle.groups[it], missed = false) }
        if (lost) LinkupRules.unsolvedGroups(puzzle, state).forEach { LinkupBanner(it, missed = true) }

        if (!finished) {
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                tiles.chunked(LinkupRules.GROUP_SIZE).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { w ->
                            LinkupTile(
                                w, selected = w in state.selected, hinted = w in state.hinted,
                                modifier = Modifier.weight(1f)
                            ) { update(LinkupRules.toggle(puzzle, state, w)) }
                        }
                    }
                }
            }
        } else {
            WordPuzzleResultCard(
                success = won,
                headline = when { won -> "Linkup solved!"; state.gaveUp -> "You gave up"; else -> "Out of mistakes" },
                lines = listOf(
                    "${state.solved.size} of ${LinkupRules.GROUPS} groups found",
                    "${state.mistakes} mistake${if (state.mistakes == 1) "" else "s"}, ${state.hints} hint${if (state.hints == 1) "" else "s"}"
                ),
                score = score, stats = stats.snapshot("linkup"),
                shareText = "RiddleVerse Linkup: ${state.solved.size}/${LinkupRules.GROUPS} groups, ${state.mistakes} mistakes, ${state.hints} hints"
            ) { if (!reported) { reported = true; onFinished(won, score) } }
        }
    }
}

@Composable
private fun LinkupTile(word: String, selected: Boolean, hinted: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val cs = MaterialTheme.colorScheme
    val fill = if (selected) cs.primary else cs.surface
    val text = if (selected) cs.onPrimary else cs.onSurface
    val border = when {
        selected -> BorderStroke(3.dp, cs.onSurface)
        hinted -> BorderStroke(3.dp, WordPuzzleColors.present)
        else -> BorderStroke(1.dp, cs.outline)
    }
    val desc = "$word, ${if (selected) "selected" else "not selected"}" + if (hinted) ", hint: belongs to the easiest group" else ""
    Box(
        modifier
            .heightIn(min = 56.dp)
            .clip(shape).background(fill).border(border, shape)
            .toggleable(value = selected, role = Role.Checkbox) { onToggle() }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        CappedFontScale(1.0f) {
            Text(
                word, color = text, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                fontSize = when { word.length <= 6 -> 15.sp; word.length <= 8 -> 13.sp; else -> 11.sp },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            if (selected) Text("✓", color = text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 2.dp))
            if (hinted) Text("◆", color = if (selected) text else WordPuzzleColors.present, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 5.dp, top = 2.dp))
        }
    }
}

@Composable
private fun LinkupBanner(group: LinkupGroup, missed: Boolean) {
    val (fill, textColor) = tierColors(group.tier)
    val tier = "${LinkupRules.tierGlyph(group.tier)} ${LinkupRules.tierName(group.tier)}"
    val desc = "${LinkupRules.tierName(group.tier)} group${if (missed) ", not found" else ""}: ${group.title}. ${group.words.joinToString(", ")}"
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(fill)
            .padding(horizontal = 10.dp, vertical = 8.dp).clearAndSetSemantics { contentDescription = desc },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(tier + if (missed) "  (missed)" else "", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(group.title.uppercase(), color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(group.words.joinToString(", "), color = textColor, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
// --- end linkup ---

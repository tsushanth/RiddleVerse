package com.kreativekoala.riddleverse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.riddleverse.ui.CappedFontScale
import org.json.JSONObject

// --- riddle pack (feat/riddle-pack) --- the riddle card: typed answer, free hint ladder (costs stars, not points),
// streak, skip, reveal and share. Offline, no account needed. All text sizes are sp and the page scrolls, so large
// system font sizes never push the answer field off screen (the answer row is pinned, the rest scrolls).

private const val OUTCOME_PLAYING = "playing"
private const val OUTCOME_SOLVED = "solved"
private const val OUTCOME_REVEALED = "revealed"
private const val CONFIRM_SKIP = "skip"
private const val CONFIRM_REVEAL = "reveal"
private const val MAX_INPUT = 60

@Composable
fun RiddleScreen(
    puzzleJson: String, levelLabel: String, onBack: () -> Unit, onFinished: (success: Boolean, score: Int) -> Unit
) {
    val context = LocalContext.current
    val riddle = remember(puzzleJson) { Riddle.fromJson(JSONObject(puzzleJson)) }
    val stats = remember { WordPuzzleStats(PrefsKeyValueStore(context)) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    // State survives rotation / process death for the same puzzle.
    var input by rememberSaveable(puzzleJson) { mutableStateOf("") }
    var hintsShown by rememberSaveable(puzzleJson) { mutableStateOf(0) }
    var outcome by rememberSaveable(puzzleJson) { mutableStateOf(OUTCOME_PLAYING) }
    var recorded by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var reported by rememberSaveable(puzzleJson) { mutableStateOf(false) }
    var feedback by rememberSaveable(puzzleJson) { mutableStateOf(0) } // @StringRes of the message, 0 = none
    var confirm by remember { mutableStateOf<String?>(null) }

    val solved = outcome == OUTCOME_SOLVED
    val finished = outcome != OUTCOME_PLAYING
    val stars = RiddlePack.stars(hintsShown)
    val score = RiddlePack.points(riddle.tier, hintsShown, solved)
    val answerShown = riddle.displayAnswer

    LaunchedEffect(outcome) {
        if (finished && !recorded) {
            recorded = true
            stats.record(RiddlePack.STATS_KEY, solved)
        }
    }

    fun skip() {
        if (!recorded) { recorded = true; stats.record(RiddlePack.STATS_KEY, false) }
        if (!reported) { reported = true; onFinished(false, 0) }
    }

    fun check() {
        val guess = input.trim()
        when {
            guess.isEmpty() -> feedback = R.string.riddle_empty
            RiddleMatcher.isCorrect(guess, riddle) -> {
                feedback = 0
                outcome = OUTCOME_SOLVED
                keyboard?.hide()
                focus.clearFocus()
            }
            else -> feedback = R.string.riddle_wrong
        }
    }

    val streak = stats.snapshot(RiddlePack.STATS_KEY).streak
    val shareDesc = stringResource(R.string.riddle_share_desc)

    Box(Modifier.fillMaxSize().imePadding()) {
        WordPuzzleScaffold(
            title = stringResource(R.string.riddle_title), levelLabel = levelLabel, onBack = onBack,
            bottomBar = if (finished) null else ({
                RiddleActionRow(
                    hintsShown = hintsShown,
                    onHint = { if (hintsShown < riddle.hints.size) { hintsShown += 1 } },
                    onSkip = { if (streak > 0) confirm = CONFIRM_SKIP else skip() },
                    onReveal = { confirm = CONFIRM_REVEAL }
                )
                Spacer(Modifier.height(8.dp))
                RiddleAnswerRow(
                    value = input,
                    onValueChange = { input = it.take(MAX_INPUT); feedback = 0 },
                    onCheck = { check() }
                )
            })
        ) {
            Spacer(Modifier.height(4.dp))
            RiddleStatusRow(riddle.tier, stars, streak)
            Spacer(Modifier.height(8.dp))
            RiddleCard(riddle.question)
            riddle.hints.take(hintsShown).forEachIndexed { i, hint ->
                Spacer(Modifier.height(8.dp))
                RiddleHintCard(i + 1, hint)
            }
            val message = if (feedback != 0) stringResource(feedback) else ""
            Spacer(Modifier.height(4.dp))
            WordPuzzleMessage(message)
            if (!finished) {
                OutlinedButton(
                    onClick = { shareWordPuzzleText(context, RiddlePack.shareText(context, riddle)) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = shareDesc }
                ) { Text(stringResource(R.string.riddle_share), fontSize = 14.sp) }
            } else {
                val headline = stringResource(if (solved) R.string.riddle_correct else R.string.riddle_revealed, answerShown)
                val lines = buildList {
                    add(riddle.explanation)
                    if (solved) add(stringResource(R.string.riddle_stars_result, stars, RiddlePack.MAX_STARS, hintsShown))
                }
                WordPuzzleResultCard(
                    success = solved, headline = headline, lines = lines, score = score,
                    stats = stats.snapshot(RiddlePack.STATS_KEY),
                    shareText = RiddlePack.shareText(context, riddle),
                    continueLabel = stringResource(R.string.riddle_next)
                ) { if (!reported) { reported = true; onFinished(solved, score) } }
            }
        }
    }

    confirm?.let { pending ->
        val reveal = pending == CONFIRM_REVEAL
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(if (reveal) R.string.riddle_confirm_reveal_title else R.string.riddle_confirm_skip_title)) },
            text = { Text(stringResource(if (reveal) R.string.riddle_confirm_reveal_body else R.string.riddle_confirm_skip_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (reveal) { outcome = OUTCOME_REVEALED; keyboard?.hide(); focus.clearFocus() } else skip()
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.riddle_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.riddle_confirm_no)) }
            }
        )
    }
}

@Composable
private fun RiddleStatusRow(tier: RiddleTier, stars: Int, streak: Int) {
    val tierText = stringResource(when (tier) { RiddleTier.EASY -> R.string.riddle_tier_easy; RiddleTier.MEDIUM -> R.string.riddle_tier_medium; RiddleTier.HARD -> R.string.riddle_tier_hard })
    val tierDesc = stringResource(R.string.riddle_tier_desc, tierText)
    val starsDesc = stringResource(R.string.riddle_stars_desc, stars, RiddlePack.MAX_STARS)
    val streakDesc = stringResource(R.string.riddle_streak_desc, streak)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(tierText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { contentDescription = tierDesc })
        Text("★".repeat(stars) + "☆".repeat(RiddlePack.MAX_STARS - stars), fontSize = 20.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clearAndSetSemantics { contentDescription = starsDesc })
        Text(stringResource(R.string.riddle_streak, streak), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { contentDescription = streakDesc })
    }
}

@Composable
private fun RiddleCard(question: String) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(
            question, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(16.dp).fillMaxWidth().semantics { heading() }
        )
    }
}

@Composable
private fun RiddleHintCard(number: Int, hint: String) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Text(
            stringResource(R.string.riddle_hint_line, number, hint), fontSize = 16.sp, lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/** Hint / Skip / Reveal. Fixed-height buttons: the label font is capped at 1.3x, never the riddle text. */
@Composable
private fun RiddleActionRow(hintsShown: Int, onHint: () -> Unit, onSkip: () -> Unit, onReveal: () -> Unit) {
    val total = 3
    val hintsLeft = hintsShown < total
    val hintLabel = stringResource(if (hintsLeft) R.string.riddle_hint_button else R.string.riddle_hint_button_none, hintsShown + 1, total)
    val hintDesc = stringResource(if (hintsLeft) R.string.riddle_hint_desc else R.string.riddle_hint_none_desc, hintsShown + 1, total)
    val skipDesc = stringResource(R.string.riddle_skip_desc)
    val revealDesc = stringResource(R.string.riddle_reveal_desc)
    CappedFontScale(1.3f) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pad = PaddingValues(horizontal = 6.dp)
            OutlinedButton(onClick = onHint, enabled = hintsLeft, contentPadding = pad, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1.4f).heightIn(min = 48.dp).semantics { contentDescription = hintDesc }) {
                Text(hintLabel, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = onSkip, contentPadding = pad, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = skipDesc }) {
                Text(stringResource(R.string.riddle_skip), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = onReveal, contentPadding = pad, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1.2f).heightIn(min = 48.dp).semantics { contentDescription = revealDesc }) {
                Text(stringResource(R.string.riddle_reveal), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RiddleAnswerRow(value: String, onValueChange: (String) -> Unit, onCheck: () -> Unit) {
    val checkDesc = stringResource(R.string.riddle_check_desc)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            label = { Text(stringResource(R.string.riddle_your_answer)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCheck() }),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        CappedFontScale(1.3f) {
            Button(onClick = onCheck, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = checkDesc }) {
                Text(stringResource(R.string.riddle_check), fontSize = 16.sp, maxLines = 1)
            }
        }
    }
}

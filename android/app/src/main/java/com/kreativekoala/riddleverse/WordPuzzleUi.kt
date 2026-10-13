package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.res.stringResource

// --- word puzzles (feat/puzzles-word) --- shared UI for the word games

/** Feedback colours. Text colours are chosen for >= 4.5:1 contrast; state is also announced and marked with a glyph. */
object WordPuzzleColors {
    val correct = Color(0xFF1E8E5A)
    val present = Color(0xFFFFB020)
    val absent = Color(0xFF6B6B7B)
    val onCorrect = Color.White
    val onPresent = Color(0xFF231B4A)
    val onAbsent = Color.White

    fun container(s: LetterState, neutral: Color): Color = when (s) {
        LetterState.CORRECT -> correct
        LetterState.PRESENT -> present
        LetterState.ABSENT -> absent
        LetterState.UNKNOWN -> neutral
    }

    fun content(s: LetterState, neutral: Color): Color = when (s) {
        LetterState.CORRECT -> onCorrect
        LetterState.PRESENT -> onPresent
        LetterState.ABSENT -> onAbsent
        LetterState.UNKNOWN -> neutral
    }

    fun describe(s: LetterState) = when (s) {
        LetterState.CORRECT -> "correct position"
        LetterState.PRESENT -> "in the word, wrong position"
        LetterState.ABSENT -> "not in the word"
        LetterState.UNKNOWN -> "not guessed yet"
    }

    fun glyph(s: LetterState) = when (s) {
        LetterState.CORRECT -> "✓"
        LetterState.PRESENT -> "•"
        else -> ""
    }
}

@Composable
fun WordPuzzleScaffold(
    title: String,
    levelLabel: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                title, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(levelLabel, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        content()
        Spacer(Modifier.height(16.dp))
    }
}

/** One key. The whole cell is the touch target; the visible key is inset. */
@Composable
fun WordKey(
    label: String, description: String, weight: Float, enabled: Boolean,
    container: Color, contentColor: Color, onClick: () -> Unit, rowModifier: Modifier = Modifier,
    scope: RowScope
) {
    with(scope) {
        Box(
            modifier = rowModifier
                .weight(weight)
                .height(54.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 3.dp)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(container),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            }
        }
    }
}

/**
 * On-screen QWERTY keyboard (no system IME). 10 keys across cannot all be 48dp wide on a 360dp phone,
 * so keys are 54dp tall and as wide as the screen allows (>= ~31dp, whole cell is tappable).
 */
@Composable
fun WordKeyboard(
    keyStates: Map<Char, LetterState>, enabled: Boolean,
    onLetter: (Char) -> Unit, onDelete: () -> Unit, onEnter: () -> Unit, enterLabel: String = "ENTER", showActions: Boolean = true
) {
    val neutral = MaterialTheme.colorScheme.surface
    val onNeutral = MaterialTheme.colorScheme.onSurface
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEachIndexed { idx, letters ->
            Row(Modifier.fillMaxWidth().padding(horizontal = if (idx == 1) 14.dp else 0.dp)) {
                if (idx == 2 && showActions) WordKey(enterLabel, "Submit", 1.6f, enabled, MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary, onEnter, scope = this)
                letters.forEach { c ->
                    val st = keyStates[c] ?: LetterState.UNKNOWN
                    WordKey(
                        c.uppercase(), "${c.uppercase()}, ${WordPuzzleColors.describe(st)}", 1f, enabled,
                        WordPuzzleColors.container(st, neutral), WordPuzzleColors.content(st, onNeutral),
                        { onLetter(c) }, scope = this
                    )
                }
                if (idx == 2 && showActions) WordKey("⌫", "Delete", 1.6f, enabled, neutral, onNeutral, onDelete, scope = this)
            }
        }
    }
}

@Composable
fun WordPuzzleMessage(text: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
        if (text.isNotEmpty()) {
            Text(
                text, textAlign = TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { liveRegionPolite() }
            )
        }
    }
}

private fun androidx.compose.ui.semantics.SemanticsPropertyReceiver.liveRegionPolite() {
    liveRegion = LiveRegionMode.Polite
}

/** Shared end-of-puzzle card: outcome, detail lines, optional share, and the Continue button that reports completion once. */
@Composable
fun WordPuzzleResultCard(
    success: Boolean, headline: String, lines: List<String>, score: Int,
    stats: WordPuzzleStatsSnapshot?, shareText: String?, continueLabel: String = "Continue", onContinue: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, if (success) WordPuzzleColors.correct else MaterialTheme.colorScheme.error)
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(headline, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { liveRegionPolite() })
            lines.forEach {
                Text(it, fontSize = 15.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (success) Text("+$score points", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WordPuzzleColors.correct)
            if (stats != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Played ${stats.played}   Won ${stats.winPercent}%   Streak ${stats.streak}   Best ${stats.bestStreak}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (shareText != null) {
                    OutlinedButton(onClick = { shareWordPuzzleText(context, shareText) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Share")
                    }
                }
                Button(onClick = onContinue, modifier = Modifier.heightIn(min = 48.dp)) { Text(continueLabel) }
            }
        }
    }
}

fun shareWordPuzzleText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(chooser) } catch (_: Exception) { }
}

/** Plain bordered chip button with a >= 48dp touch target. */
@Composable
fun WordActionButton(label: String, description: String = label, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(12.dp)
    ) { Text(label, fontSize = 14.sp, maxLines = 1) }
}

internal fun Modifier.wordTileBorder(color: Color, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)) = this.border(2.dp, color, shape)

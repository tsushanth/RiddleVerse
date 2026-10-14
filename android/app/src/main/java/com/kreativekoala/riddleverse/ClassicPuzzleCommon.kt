package com.kreativekoala.riddleverse

import androidx.compose.foundation.verticalScroll
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- classic puzzles (feat/puzzles-classic) --- shared registry, wrapper, scaffold and stats

/** Registry of the classic casual puzzle types added by this feature. */
object ClassicPuzzles {
    const val TILE_MERGE = "tilemerge"
    const val SLIDE_TILES = "slidetiles"
    const val LIGHT_FLIP = "lightflip"
    const val SAFE_SWEEP = "safesweep"
    const val DISC_TOWER = "disctower"

    val types = setOf(TILE_MERGE, SLIDE_TILES, LIGHT_FLIP, SAFE_SWEEP, DISC_TOWER)

    fun isClassic(type: String?): Boolean = type != null && type.lowercase() in types

    /** Value of PuzzleScreenType.typeName for a puzzle type id. */
    fun screenName(type: String): String = when (type.lowercase()) {
        TILE_MERGE -> "Tile Merge Screen"
        SLIDE_TILES -> "Slide Tiles Screen"
        LIGHT_FLIP -> "Light Flip Screen"
        SAFE_SWEEP -> "Safe Sweep Screen"
        DISC_TOWER -> "Disc Tower Screen"
        else -> "Default"
    }

    /** Maps a difficulty label (Beginner/Easy/Medium/Hard/Expert, any case) to tier 0..4. */
    fun tierOf(difficulty: String?): Int = when (difficulty?.trim()?.lowercase()) {
        "beginner" -> 0
        "easy" -> 1
        "medium", "mixed" -> 2
        "hard" -> 3
        "expert" -> 4
        else -> 2
    }

    /** 10..100 score from how close the move count is to par. */
    fun efficiencyScore(par: Int, moves: Int, penalty: Int = 0): Int {
        if (moves <= 0) return 10
        val ratio = (100.0 * par / maxOf(moves, par, 1)).toInt()
        return (ratio - penalty).coerceIn(10, 100)
    }

    fun tutorialFor(type: String): Triple<String, String, String>? = when (type.lowercase()) {
        TILE_MERGE -> Triple(
            "Swipe to slide every tile. Equal tiles that touch merge into one bigger tile. Reach the goal tile before you run out of moves or space.",
            "Keep your biggest tile in a corner and build next to it.", "Tile Merge"
        )
        SLIDE_TILES -> Triple(
            "Tap a tile in the same row or column as the empty space to slide it. Put the numbers in order, with the empty space in the bottom right corner.",
            "Solve the top row first, then the next one, and leave the last two rows for the end.", "Slide Tiles"
        )
        LIGHT_FLIP -> Triple(
            "Tapping a light flips it and its four neighbours. Turn every light off in as few taps as you can.",
            "Tapping the same cell twice does nothing. Use Hint if you get stuck.", "Light Flip"
        )
        SAFE_SWEEP -> Triple(
            "Tap a square to open it. A number tells how many mines touch that square. Flag mines you are sure about, then open every safe square. Your first tap is always safe.",
            "Long press (or use Flag mode) to place a flag. Tap a number whose flags are all placed to open its other neighbours.", "Safe Sweep"
        )
        DISC_TOWER -> Triple(
            "Move the whole stack to the right-hand peg. Move one disc at a time, and never put a bigger disc on a smaller one. Tap a peg to pick up its top disc, then tap where to drop it.",
            "The best possible score needs 2 to the power of discs, minus 1 moves. Hint shows the next best move.", "Disc Tower"
        )
        else -> null
    }
}

/** Persistent best results, kept in a private SharedPreferences file (all failures are swallowed). */
object ClassicStats {
    private const val FILE = "classic_puzzle_stats"
    private fun key(game: String, tier: Int) = "${game}_t$tier"

    /** Returns the stored best (or null). */
    fun best(ctx: Context, game: String, tier: Int): Int? = try {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (p.contains(key(game, tier))) p.getInt(key(game, tier), 0) else null
    } catch (e: Exception) {
        null
    }

    /** Stores [value] if it beats the best; returns true when it is a new best. */
    fun submit(ctx: Context, game: String, tier: Int, value: Int, lowerIsBetter: Boolean): Boolean = try {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val old = if (p.contains(key(game, tier))) p.getInt(key(game, tier), 0) else null
        val better = old == null || (if (lowerIsBetter) value < old else value > old)
        if (better) p.edit().putInt(key(game, tier), value).apply()
        better && old != null
    } catch (e: Exception) {
        false
    }
}

/** Saveable (rotation + process death) holder for a game state that can encode itself to a string. */
@Composable
fun <T : Any> rememberSavedGame(init: () -> T, encode: (T) -> String, decode: (String) -> T?): MutableState<T> =
    rememberSaveable(
        saver = Saver<MutableState<T>, String>(
            save = { encode(it.value) },
            restore = { mutableStateOf(decode(it) ?: init()) },
        )
    ) { mutableStateOf(init()) }

/** Common page chrome: back button, title, level label, stat chips and a description line. */
@Composable
fun ClassicScaffold(
    title: String,
    level: String,
    subtitle: String,
    stats: List<Pair<String, String>>,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Short screens (landscape): the page scrolls as a whole at a minimum height, so the board and all controls stay reachable.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    val tooShort = maxHeight < com.kreativekoala.riddleverse.ui.RV_MIN_PAGE_HEIGHT
    Column(Modifier.fillMaxSize().then(if (tooShort) Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier)) {
    Column(
        Modifier
            .then(if (tooShort) Modifier.height(com.kreativekoala.riddleverse.ui.RV_MIN_PAGE_HEIGHT) else Modifier.fillMaxSize())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f),
            )
            Text(level, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, // keeps the board area from collapsing on short phones
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.forEach { (label, value) ->
                Column(
                    Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "$label $value" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(8.dp))
    }
    }
    }
}

/** Blocking end-of-game dialog. [onContinue] hands the result back to the puzzle flow. */
@Composable
fun ClassicResultDialog(
    success: Boolean,
    headline: String,
    lines: List<String>,
    onContinue: () -> Unit,
) {
    AlertDialog(
        // Back = the same as the button (Continue / Next puzzle). Tapping outside stays ignored so a stray touch cannot skip the result.
        onDismissRequest = onContinue,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        title = { Text(headline, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                lines.forEach { Text(it, fontSize = 16.sp, textAlign = TextAlign.Start) }
            }
        },
        confirmButton = {
            Button(onClick = onContinue, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (success) "Continue" else "Next puzzle")
            }
        },
    )
}

/** Routes the puzzle flow to the right classic game. Each game reports (success, score) exactly once. */
@Composable
fun ClassicPuzzleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit,
) {
    val tier = remember(currentPuzzle) { ClassicPuzzles.tierOf(currentPuzzle.difficulty) }
    val level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/" +
        "${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}"
    val finish: (Boolean, Int) -> Unit = { success, score -> handlePuzzleCompletion(success, success, score) }
    when (viewModel.screenType) {
        PuzzleScreenType.TILE_MERGE_SCREEN -> TileMergeScreen(tier, level, onBack, finish)
        PuzzleScreenType.SLIDE_TILES_SCREEN -> SlideTilesScreen(tier, level, onBack, finish)
        PuzzleScreenType.LIGHT_FLIP_SCREEN -> LightFlipScreen(tier, level, onBack, finish)
        PuzzleScreenType.SAFE_SWEEP_SCREEN -> SafeSweepScreen(tier, level, onBack, finish)
        PuzzleScreenType.DISC_TOWER_SCREEN -> DiscTowerScreen(tier, level, onBack, finish)
        else -> Unit
    }
}

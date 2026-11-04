package com.kreativekoala.riddleverse

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.kreativekoala.riddleverse.ui.RvActivityTheme
import com.kreativekoala.riddleverse.ui.theme.RvCanvas
import com.kreativekoala.riddleverse.ui.theme.RvInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Today's shared puzzle. Works for guests: no account, no network. It reuses the existing game screens through
 * PuzzleScreen with a one-puzzle PuzzleViewModel, and only decides what a result means for the daily:
 * solved -> save time/tries, extend the streak, pay coins; not solved -> count a try and offer a retry of the same puzzle.
 */
class DailyPuzzleActivity : AppCompatActivity() {
    private var day: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pin the day when the screen opens, so a solve that finishes after UTC midnight still counts for the puzzle played.
        day = savedInstanceState?.getLong(KEY_DAY) ?: DailyPuzzleStore.today()
        setContent {
            RvActivityTheme(maxFontScale = 1.3f) {
                DailyPuzzleRoot(day = day, onClose = { finish() })
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_DAY, day)
    }

    private companion object { const val KEY_DAY = "day" }
}

@Composable
private fun DailyPuzzleRoot(day: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val spec = remember(day) { DailyPuzzleSchedule.specFor(day) }

    var solved by remember {
        mutableStateOf(DailyPuzzleStore.resultFor(context, day)?.let { DailySolveRecord(it, firstSolve = false, reward = null) })
    }
    solved?.let { DailyResultScreen(record = it, onDone = onClose); return }

    val tutorialPrefs = remember { TutorialPreferences(context) }
    var showTutorial by rememberSaveable { mutableStateOf(!tutorialPrefs.hasSeenTutorial(spec.type)) }
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    var startedAt by rememberSaveable { mutableLongStateOf(-1L) }
    var failed by remember { mutableStateOf(false) }

    val built by produceState<DailyBuiltPuzzle?>(null, spec) {
        value = withContext(Dispatchers.Default) {
            try { DailyPuzzleFactory.build(context, spec) } catch (e: Exception) { null }
        }
    }

    if (showTutorial) {
        val done = { tutorialPrefs.markTutorialSeen(spec.type); showTutorial = false }
        when {
            GridGame.fromKey(spec.type) != null -> GridTutorialScreen(spec.type, onTutorialComplete = done, onTutorialSkipped = done)
            spec.type in SpatialPuzzles.TYPES -> SpatialTutorialScreen(spec.type, onTutorialComplete = done, onTutorialSkipped = done)
            else -> WordPuzzleTutorialScreen(spec.type, onTutorialComplete = done, onTutorialSkipped = done)
        }
        return
    }

    val puzzle = built
    if (puzzle == null) {
        Box(Modifier.fillMaxSize().background(RvCanvas), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Preparing today's puzzle", color = RvInk)
            }
        }
        return
    }

    // The timer starts when the board first appears and keeps running across retries within this visit.
    LaunchedEffect(Unit) {
        if (startedAt < 0) {
            startedAt = SystemClock.elapsedRealtime()
            AnalyticsManager.getInstance()?.track(AnalyticsEvent("daily_puzzle_started", mapOf("number" to spec.number, "type" to spec.type)))
        }
    }

    key(attempt) {
        val vm = remember {
            PuzzleViewModel().also {
                it.initializeFromIntent(
                    Intent()
                        .putExtra("puzzle", Gson().toJson(puzzle.puzzle))
                        .putExtra("screenType", puzzle.screenType)
                        .putExtra("targetPuzzleCount", 1)
                        .putExtra("currentPuzzleNumber", 1),
                    context,
                )
            }
        }
        PuzzleScreen(
            viewModel = vm,
            // With a one-puzzle flow PuzzleScreen reports every finished try here; the view model knows whether it was a solve.
            onComplete = {
                if (vm.getCorrectAnswerCount() > 0) {
                    val secs = ((SystemClock.elapsedRealtime() - (if (startedAt < 0) SystemClock.elapsedRealtime() else startedAt)) / 1000).toInt()
                    val record = DailyPuzzleStore.recordSolve(context, day, spec.type, secs)
                    AnalyticsManager.getInstance()?.track(
                        AnalyticsEvent("daily_puzzle_solved", mapOf("number" to spec.number, "type" to spec.type, "seconds" to secs, "attempts" to record.result.attempts))
                    )
                    solved = record
                } else {
                    DailyPuzzleStore.recordFailure(context, day)
                    failed = true
                }
            },
            onBack = onClose,
        )
    }

    if (failed) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Not solved this time") },
            text = { Text("It is the same puzzle for everyone today. Try again, your streak is safe until the day ends.") },
            confirmButton = { TextButton(onClick = { failed = false; attempt++ }) { Text("Try again") } },
            dismissButton = { TextButton(onClick = onClose) { Text("Back") } },
        )
    }
}

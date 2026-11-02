package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.kreativekoala.riddleverse.ui.theme.RvGrape
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvViolet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** First puzzle for a brand new guest: prefetched while the first-run screen is up, with an on-device fallback. */
class FirstPuzzleLoader(context: Context) {
    data class Loaded(val puzzle: Puzzle, val choice: FirstPuzzleChoice, val fellBack: Boolean)

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Deferred<Loaded?>? = null

    fun prefetch() {
        if (job == null) job = scope.async { load() }
    }

    suspend fun await(): Loaded? {
        prefetch()
        return job!!.await()
    }

    private suspend fun load(): Loaded? {
        // The blocking OkHttp call inside the queue manager ignores cancellation, so race it against a timer
        // instead of wrapping it in withTimeout: the timer wins and the stray request finishes in the background.
        val remote = scope.async(Dispatchers.IO) {
            val queue = PuzzleQueueManager.getInstance(app)
            for (c in FirstRunPlan.remoteCandidates) {
                val q = try { queue.getNextPuzzleForPlay(c.type, c.difficulty) } catch (e: Exception) { null }
                if (q != null) return@async Loaded(q.toPuzzle(), c, fellBack = false)
            }
            null
        }
        withTimeoutOrNull(FirstRunPlan.REMOTE_BUDGET_MS) { remote.await() }?.let { return it }

        val fb = FirstRunPlan.localFallback
        return try {
            Loaded(WordPuzzleRegistry.createPuzzle(fb.type, fb.difficulty, app), fb, fellBack = true)
        } catch (e: Exception) {
            Log.e("FirstRun", "On-device fallback puzzle failed", e)
            null
        }
    }
}

object FirstRun {
    /**
     * Starts Home (as the back-stack root, so Back from the puzzle lands on a real screen) and the puzzle on top of it.
     * Guest mode is switched on here: from now on a cold start goes straight to Home.
     */
    fun launchFirstPuzzle(context: Context, loaded: FirstPuzzleLoader.Loaded) {
        GuestPrefs.markStarted(context)
        val c = loaded.choice
        val puzzleIntent = Intent(context, PuzzleActivity::class.java).apply {
            putExtra("puzzle", Gson().toJson(loaded.puzzle))
            putExtra("screenType", c.screenType)
            putExtra("difficulty", c.difficulty.replaceFirstChar { it.uppercase() })
            putExtra("score", 0)
            putExtra("targetPuzzleCount", FirstRunPlan.TARGET_PUZZLE_COUNT)
            putExtra("startTime", System.currentTimeMillis())
            putExtra("puzzleSource", if (loaded.fellBack) "first_run_local" else "first_run")
            putExtra(EXTRA_IS_FIRST_RUN_PUZZLE, true)
        }
        val home = Intent(context, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        Funnel.log(FunnelStep.FIRST_PUZZLE_LAUNCHED, mapOf("type" to c.type, "source" to if (loaded.fellBack) "local_fallback" else "remote"))
        context.startActivities(arrayOf(home, puzzleIntent))
    }

    /** No puzzle could be built at all: still let the person in as a guest rather than strand them. */
    fun enterHomeAsGuest(context: Context) {
        GuestPrefs.markStarted(context)
        context.startActivity(Intent(context, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
    }

    const val EXTRA_IS_FIRST_RUN_PUZZLE = "is_first_run_puzzle"
}

/** Very first screen for a device with no account. One big Play button; signing in is a quiet link. */
@Composable
fun FirstRunScreen(onSignInClick: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = remember { FirstPuzzleLoader(context) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Funnel.log(FunnelStep.FIRST_RUN_SHOWN)
        loader.prefetch()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RvGrape, RvViolet))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🧩", fontSize = 72.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "RiddleVerse",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Quick brain puzzles. No sign-up needed.",
                fontSize = 17.sp,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    Funnel.log(FunnelStep.PLAY_TAPPED)
                    scope.launch {
                        val loaded = loader.await()
                        if (loaded != null) {
                            FirstRun.launchFirstPuzzle(context, loaded)
                        } else {
                            Toast.makeText(context, "Could not load a puzzle. Opening the puzzle list instead.", Toast.LENGTH_LONG).show()
                            FirstRun.enterHomeAsGuest(context)
                        }
                        onFinished()
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A65), contentColor = Color.White,
                    disabledContainerColor = Color(0xFFFF8A65).copy(alpha = 0.7f), disabledContentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics { contentDescription = "Play your first puzzle" }
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Play", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(20.dp))
            TextButton(
                onClick = {
                    Funnel.log(FunnelStep.SIGN_IN_LINK_TAPPED)
                    onSignInClick()
                },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Already have an account? Sign in", color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

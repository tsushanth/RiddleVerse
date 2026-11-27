package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.riddleverse.ui.theme.RvGrape
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvViolet

object FirstRun {
    /**
     * Starts Home (as the back-stack root, so Back from the puzzle lands on a real screen) and the starter puzzle on top
     * of it. Guest mode is switched on here: from now on a cold start goes straight to Home.
     */
    fun launchFirstPuzzle(context: Context) {
        GuestPrefs.markStarted(context)
        StarterOffer.markOffered(context) // Home underneath must not open a second one
        val home = Intent(context, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        Funnel.log(FunnelStep.FIRST_PUZZLE_LAUNCHED, mapOf("type" to StarterPuzzle.TELEMETRY_TYPE))
        context.startActivities(arrayOf(home, StarterOffer.intent(context)))
    }

    const val EXTRA_IS_FIRST_RUN_PUZZLE = "is_first_run_puzzle"
}

/** Very first screen for a device with no account. One big Play button; signing in is a quiet link. */
@Composable
fun FirstRunScreen(onSignInClick: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    val playFirstPuzzleCd = stringResource(R.string.onb_first_run_play_cd)

    LaunchedEffect(Unit) { Funnel.log(FunnelStep.FIRST_RUN_SHOWN) }

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
                text = stringResource(R.string.onb_first_run_tagline),
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
                    FirstRun.launchFirstPuzzle(context)
                    onFinished()
                },
                enabled = !busy,
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A65), contentColor = Color.White,
                    disabledContainerColor = Color(0xFFFF8A65).copy(alpha = 0.7f), disabledContentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics { contentDescription = playFirstPuzzleCd }
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.play), fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.auth_toggle_to_sign_in), color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

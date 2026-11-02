package com.kreativekoala.riddleverse

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.riddleverse.ui.theme.*
import kotlinx.coroutines.delay

// --- daily puzzle (feat/daily-puzzle-share) --- Home entry card and the result screen. Local state only.

private fun countdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** Ticks once a second. Rolls over to the next puzzle at UTC midnight without any restart. */
@Composable
private fun rememberNowMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}

/** "Today's puzzle" card for the Home (For You) tab: game, status, streak flame and a countdown to the next one. */
@Composable
fun DailyPuzzleHomeCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberNowMillis()
    val day = DailyPuzzleSchedule.dayOf(now)
    val spec = remember(day) { DailyPuzzleSchedule.specFor(day) }
    val result = DailyPuzzleStore.resultFor(context, day)
    val streak = DailyPuzzleStore.displayStreak(context, day)
    val gameName = PuzzleTypeNames.display(spec.type)
    val untilNext = DailyPuzzleSchedule.millisUntilNext(now)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(RvViolet, RvVioletEdge)))
            .clickable { context.startActivity(Intent(context, DailyPuzzleActivity::class.java)) }
            .semantics { contentDescription = "Today's puzzle number ${spec.number}, $gameName" }
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's puzzle", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = RvOnTone, modifier = Modifier.weight(1f))
                RvPill(text = "🔥 $streak", tone = if (streak > 0) RvToneSun else RvToneGrape)
            }
            Text(
                text = if (result != null) "#${spec.number}  $gameName  solved in ${DailyShareText.formatTime(result.seconds)}"
                else "#${spec.number}  $gameName  •  same puzzle for everyone",
                fontSize = 14.sp, color = RvOnTone, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Next puzzle in ${countdown(untilNext)}",
                    fontSize = 12.sp, color = RvOnTone.copy(alpha = 0.85f), modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.background(if (result != null) RvMint else RvSun, RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(if (result != null) "View result" else "Play", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = RvOnTone)
                }
            }
        }
    }
}

/** Shown after a solve (or when reopening a day that is already solved): stats, streak, coins and share buttons. */
@Composable
fun DailyResultScreen(record: DailySolveRecord, onDone: () -> Unit) {
    val context = LocalContext.current
    val result = record.result
    val share = remember(result) { DailyPuzzleStore.shareResultOf(context, result) }
    val card = remember(share) { DailyShareCard.render(share) }
    val now = rememberNowMillis()

    Column(
        Modifier.fillMaxSize().background(RvCanvas).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Daily #${share.number} solved ✅", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = RvInk, textAlign = TextAlign.Center)
        Text(
            "${share.gameName}  •  ${DailyShareText.formatTime(share.seconds)}  •  " +
                if (share.attempts == 1) "first try" else "${share.attempts} tries",
            fontSize = 16.sp, color = RvInkSoft, textAlign = TextAlign.Center,
        )
        if (share.streak > 0) {
            Text("🔥 ${share.streak} day streak", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RvFlame)
        }
        record.reward?.let { r ->
            Text("+${r.coins} coins", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RvSunEdge)
            r.specialReward?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RvGrape) }
        }

        Image(
            bitmap = card.asImageBitmap(),
            contentDescription = "Your shareable result card. It does not show the answer.",
            modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
        )

        RvChunkyButton(
            onClick = {
                DailyShareCard.shareImage(context, share)
                AnalyticsManager.getInstance()?.track(AnalyticsEvent("daily_puzzle_shared", mapOf("kind" to "image", "number" to share.number)))
            },
            modifier = Modifier.fillMaxWidth(), tone = RvToneViolet,
        ) { Text("Share image card", color = RvOnTone, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }

        RvChunkyButton(
            onClick = {
                DailyShareCard.shareText(context, share)
                AnalyticsManager.getInstance()?.track(AnalyticsEvent("daily_puzzle_shared", mapOf("kind" to "text", "number" to share.number)))
            },
            modifier = Modifier.fillMaxWidth(), tone = RvToneSun,
        ) { Text("Share as text", color = RvOnTone, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }

        Text("Next puzzle in ${countdown(DailyPuzzleSchedule.millisUntilNext(now))}", fontSize = 14.sp, color = RvInkSoft)
        Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDone).padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RvViolet)
        }
    }
}

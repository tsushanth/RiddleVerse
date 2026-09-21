package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.delay

// CelebrationShowcase.kt
// Compose 1.6+ (material3), no 3rd-party libs.

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.*
import kotlin.random.Random

private enum class CExtraFx { RibbonSweep, CometTrail, EmojiPop, BalloonRise, FireworksCorners }

// ────────────────────────────── PUBLIC ENTRY ──────────────────────────────
@Composable
fun RewardCelebrationPanel(
    modifier: Modifier = Modifier,
    // random placeholders; replace when you hook up real values
    targetScore: Int = remember { Random.nextInt(80, 280) },
    streakToday: Int = remember { Random.nextInt(1, 7) },
    streakGoal: Int = 7,
    rewardTitle: String = "Reward Unlocked",
    rewardSubtitle: String = "+${remember { Random.nextInt(50, 250) }} XP",
) {
    // pick ONE extra FX each time this panel is shown
    val cExtraFx = remember { CExtraFx.entries.random() } // Randomized among the 5

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Extra FX layer (behind)
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            when (cExtraFx) {
                CExtraFx.RibbonSweep      -> RibbonSweepBackground()
                CExtraFx.CometTrail       -> SparkleCometAroundCenter()
                CExtraFx.EmojiPop         -> EmojiStickerPop()
                CExtraFx.BalloonRise      -> BalloonRise()
                CExtraFx.FireworksCorners -> FireworksCorners()
            }
        }

        // Foreground: 11, 12, 13 — always on
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 11) Score Counter Up
            ScoreCounterUp(title = stringResource(R.string.score_label), target = targetScore)

            // 12) Streak Meter Fill
            StreakMeterFill(
                current = streakToday,
                goal = streakGoal,
                label = stringResource(R.string.daily_streak)
            )

            // 13) Card Flip: Reward Reveal
            CardFlipRewardReveal(
                title = rewardTitle,
                subtitle = rewardSubtitle
            )
        }
    }
}

// ────────────────────────────── 11) SCORE COUNTER ──────────────────────────────
@Composable
private fun ScoreCounterUp(
    title: String,
    target: Int,
    durationMs: Int = 1200
) {
    val value by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        label = "score"
    )
    // tiny pop on changes
    val scale by animateFloatAsState(
        targetValue = 1f + 0.06f * (if (value == target) 0f else 1f),
        animationSpec = tween(250, easing = LinearOutSlowInEasing),
        label = "scoreScale"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.7f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$title:",
                color = RvInkSoft.copy(alpha = 0.8f),
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                "$value",
                color = RvInk,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ────────────────────────────── 12) STREAK METER ──────────────────────────────
@Composable
private fun StreakMeterFill(
    current: Int,
    goal: Int,
    label: String
) {
    val fracTarget = (current.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val frac by animateFloatAsState(
        targetValue = fracTarget,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "streakFrac"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = RvInkSoft.copy(alpha = 0.9f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(16.dp)
                .background(RvCanvas, RoundedCornerShape(999.dp))
        ) {
            val brush = Brush.linearGradient(
                listOf(Color(0xFFFFA700), Color(0xFFFFDD55))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac)
                    .background(brush, RoundedCornerShape(999.dp))
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$current / $goal",
            color = Color(0xFFFFDD55),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ────────────────────────────── 13) CARD FLIP REVEAL ──────────────────────────────
@Composable
private fun CardFlipRewardReveal(
    title: String,
    subtitle: String,
    autoFlipDelayMs: Int = 250
) {
    val density = LocalDensity.current
    val camera = with(density) { 8 * 1_000f } // avoid skew

    val flip = remember { Animatable(0f) } // 0 → 180
    LaunchedEffect(Unit) {
        delay(autoFlipDelayMs.toLong())
        flip.animateTo(180f, tween(800, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(110.dp)
            .graphicsLayer {
                rotationY = flip.value
                cameraDistance = camera
            },
        contentAlignment = Alignment.Center
    ) {
        // Front
        if (flip.value <= 90f) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = RvInk,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Tap to Reveal",
                        color = RvInkSoft.copy(alpha = 0.7f),
                        fontSize = 18.sp
                    )
                }
            }
        } else {
            // Back (rotate inner content another 180 so text isn't mirrored)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0EA5E9),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
            ) {
                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(title, color = RvInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, color = RvInk, fontSize = 16.sp)
                }
            }
        }
    }
}

// ────────────────────────────── EXTRAS (14/15/8/9/10) ──────────────────────────────


// 14) Ribbon sweep behind content
@Composable
private fun RibbonSweepBackground() {
    val t by rememberInfiniteTransition(label = "ribbon")
        .animateFloat(
            initialValue = -0.4f, targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
            label = "ribbonT"
        )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val bandW = h * 0.35f
        val x = lerp(-bandW, w, t)
        withTransform({
            rotate(degrees = 18f, pivot = Offset(w / 2f, h / 2f))
        }) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), RvSuccess),
                    start = Offset.Zero, end = Offset(w, 0f)
                ),
                topLeft = Offset(x - bandW / 2, -bandW),
                size = androidx.compose.ui.geometry.Size(bandW, h * 2f),
                cornerRadius = CornerRadius(40f, 40f),
                alpha = 0.5f
            )
        }
    }
}

// 15) Sparkle comet orbiting center with fading trail
@Composable
private fun SparkleCometAroundCenter() {
    val t by rememberInfiniteTransition(label = "comet")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "t")
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.35f
        val angle = t * 2f * PI.toFloat()
        val head = Offset(c.x + r * cos(angle), c.y + r * sin(angle))
        // trail samples
        val trailCount = 16
        for (i in 0 until trailCount) {
            val frac = i / trailCount.toFloat()
            val a = angle - frac * 0.9f
            val p = Offset(c.x + r * cos(a), c.y + r * sin(a))
            drawCircle(
                color = Color(0xFF60A5FA).copy(alpha = (1f - frac) * 0.7f),
                radius = (6f * (1f - frac)).coerceAtLeast(1.5f),
                center = p
            )
        }
        drawCircle(Color.White, radius = 5f, center = head)
    }
}

// 8) Emoji / sticker pop (🎉✨🏆🌟)
@Composable
private fun EmojiStickerPop() {
    val emojis = listOf("🎉", "✨", "🏆", "🌟")
    val inf = rememberInfiniteTransition(label = "emojiPop")
    val waves = (0 until 8).map { idx ->
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200 + (idx % 3) * 200, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave$idx"
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

        waves.forEachIndexed { i, anim ->
            val t = anim.value
            val seeded = kotlin.random.Random(i)
            val startX = 0.5f + (seeded.nextFloat() - 0.5f) * 0.2f
            val x = startX + sin(t * 2 * Math.PI).toFloat() * 0.05f
            val y = 0.6f - t * 0.5f
            val alpha = 1f - t

            Text(
                text = emojis[i % emojis.size],
                fontSize = 20.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.TopStart)
                    .graphicsLayer {
                        translationX = x * widthPx
                        translationY = y * heightPx
                        this.alpha = alpha
                        rotationZ = (seeded.nextFloat() - 0.5f) * 20f * t
                    }
            )
        }
    }
}


// 9) Balloon rise (🎈)
@Composable
private fun BalloonRise() {
    val inf = rememberInfiniteTransition(label = "balloon")
    val anims = (0 until 6).map { idx ->
        inf.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(2600 + idx * 150, easing = LinearEasing),
                RepeatMode.Restart
            ),
            label = "b$idx"
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

        anims.forEachIndexed { i, a ->
            val t = a.value
            val x = (0.2f + (i / 6f) * 0.6f) + sin(t * 4).toFloat() * 0.02f
            val y = 1.1f - t * 1.2f
            val alpha = (1f - (t * 0.9f)).coerceAtLeast(0f)

            Text(
                "🎈",
                fontSize = 22.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.TopStart)
                    .graphicsLayer {
                        translationX = x * widthPx
                        translationY = y * heightPx
                        this.alpha = alpha
                    }
            )
        }
    }
}


// 10) Fireworks from corners
@Composable
private fun FireworksCorners() {
    val t by rememberInfiniteTransition(label = "fw")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "t")
    Canvas(Modifier.fillMaxSize()) {
        val rays = 12
        val colors = listOf(Color(0xFFF97316), Color(0xFF22C55E), Color(0xFF60A5FA), Color(0xFFF43F5E))
        fun burst(origin: Offset) {
            repeat(rays) { i ->
                val ang = (i / rays.toFloat()) * 2f * PI.toFloat()
                val len = size.minDimension * 0.18f * FastOutSlowInEasing.transform(t)
                val end = Offset(origin.x + cos(ang) * len, origin.y + sin(ang) * len)
                drawLine(
                    color = colors[i % colors.size].copy(alpha = 1f - t),
                    start = origin, end = end, strokeWidth = 3f
                )
                // little dot at the tip
                drawCircle(
                    color = RvInkSoft.copy(alpha = 1f - t),
                    radius = 2.5f,
                    center = end
                )
            }
        }
        burst(Offset(0f, 0f))
        burst(Offset(size.width, 0f))
        burst(Offset(0f, size.height))
        burst(Offset(size.width, size.height))
    }
}

// ────────────────────────────── SMALL HELPERS ──────────────────────────────
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

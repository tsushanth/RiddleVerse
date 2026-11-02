package com.kreativekoala.riddleverse

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.kreativekoala.riddleverse.QuizCategories.getQuizCategoryForType
import com.kreativekoala.riddleverse.ui.CappedFontScale
import com.kreativekoala.riddleverse.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Home discovery surfaces: "New games" row, "Picked for you" row, language suggestion + picker.
// Pure logic lives in HomeDiscoveryLogic.kt. Rows read the registry (QuizCategories.allCategories) at
// composition time, so a game another branch adds to the registry shows up here with no edit.

private fun trackDiscoveryTap(row: String, type: String) {
    AnalyticsManager.getInstance()?.track(
        AnalyticsEvent("home_discovery_tap", mapOf("row" to row, "puzzle_type" to type))
    )
}

private fun registryRefs(): List<GameRef> = QuizCategories.allCategories.map { it.toGameRef() }

// ---------------------------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------------------------

@Composable
private fun RowHeader(title: String, subtitle: String? = null, badge: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RvInk)
            if (badge != null) {
                Box(Modifier.background(RvCoral, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RvOnTone, maxLines = 1, softWrap = false)
                }
            }
        }
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = RvInkSoft)
    }
}

/** Horizontal row of the newest games, with generated card art and a one-line tagline each. */
@Composable
fun NewGamesRow(onPuzzleTypeSelected: (String) -> Unit) {
    val games = remember { DiscoveryCatalog.newGames(registryRefs()) }
    if (games.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RowHeader(stringResource(R.string.home_new_title), stringResource(R.string.home_new_subtitle), stringResource(R.string.home_new_badge))
        CappedFontScale(1.15f) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(games, key = { it.type }) { g ->
                    val tagline = DiscoveryCatalog.TAGLINES[g.type] ?: getQuizCategoryForType(g.type)?.subtitle.orEmpty()
                    DiscoveryCard(
                        type = g.type, title = g.title, caption = tagline, captionLines = 2,
                        width = 164.dp, artHeight = 104.dp, showNewPill = true
                    ) {
                        trackDiscoveryTap("new", g.type)
                        onPuzzleTypeSelected(g.type)
                    }
                }
            }
        }
    }
}

/** "Picked for you": last played, similar games, easy wins; quick-win starters on a fresh install. */
@Composable
fun PickedForYouRow(onPuzzleTypeSelected: (String) -> Unit) {
    val context = LocalContext.current
    // Re-rank every time Home comes back to the foreground (the player just finished a game).
    var resumes by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val picks by produceState(initialValue = emptyList<ForYouPick>(), resumes) {
        value = withContext(Dispatchers.IO) {
            val history = try {
                UserStatsManager.getInstance(context).getAllUserStats().map { (type, s) ->
                    PlayRecord(type, s.totalPlays, s.wins, s.lastPlayedTimestamp)
                }
            } catch (e: Exception) {
                emptyList()
            }
            ForYouRanking.rank(history, registryRefs(), { PuzzleRecommendations.getRecommendationsFor(it) })
        }
    }
    if (picks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RowHeader(stringResource(R.string.home_foryou_title))
        CappedFontScale(1.15f) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(picks, key = { it.game.type }) { p ->
                    val reason = when (p.reason) {
                        ForYouReason.CONTINUE -> stringResource(R.string.foryou_reason_continue)
                        ForYouReason.SIMILAR -> stringResource(R.string.foryou_reason_similar, p.basis ?: "")
                        ForYouReason.EASY_WIN -> stringResource(R.string.foryou_reason_winner)
                        ForYouReason.QUICK_START -> stringResource(R.string.foryou_reason_quick)
                    }
                    DiscoveryCard(
                        type = p.game.type, title = p.game.title, caption = reason, captionLines = 1,
                        width = 132.dp, artHeight = 84.dp, showNewPill = false
                    ) {
                        trackDiscoveryTap("for_you_" + p.reason.name.lowercase(), p.game.type)
                        onPuzzleTypeSelected(p.game.type)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryCard(
    type: String, title: String, caption: String, captionLines: Int,
    width: androidx.compose.ui.unit.Dp, artHeight: androidx.compose.ui.unit.Dp,
    showNewPill: Boolean, onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(width).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(artHeight)) {
                GameArt(type, Modifier.fillMaxSize())
                if (showNewPill) {
                    Box(
                        Modifier.padding(8.dp).align(Alignment.TopStart)
                            .background(RvCoral, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.home_new_badge), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = RvOnTone, maxLines = 1, softWrap = false)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RvInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // reserve the full caption height so cards in a row stay the same size
                Text(
                    caption, fontSize = 11.sp, color = RvInkSoft, maxLines = captionLines, overflow = TextOverflow.Ellipsis,
                    minLines = captionLines, lineHeight = 14.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Card art: drawn in code (Canvas), no bitmaps. Every motif is composed in a 100x100 box from
// rounded rects, circles and strokes over a two-stop gradient, white shapes plus 1-2 accent colours.
// ---------------------------------------------------------------------------------------------

private val ArtViolet = RvViolet to RvVioletEdge
private val ArtSky = RvSky to RvSkyEdge
private val ArtGrape = RvGrape to RvGrapeEdge
private val ArtNight = RvViolet to RvNight
private val ArtMint = RvMintEdge to Color(0xFF0F6B55)
private val ArtCoral = RvCoralEdge to Color(0xFF9E2F2F)
private val ArtFallbacks = listOf(ArtViolet, ArtSky, ArtGrape, ArtNight, ArtMint, ArtCoral)

private val artBackground: Map<String, Pair<Color, Color>> = mapOf(
    "wordhive" to ArtGrape, "sudoku" to ArtViolet, "tubetint" to ArtNight, "pixellogic" to ArtCoral,
    "wordguess" to ArtMint, "tilemerge" to ArtSky, "lightflip" to ArtNight, "slidetiles" to ArtViolet,
    "safesweep" to ArtMint, "wordladder" to ArtSky, "codecracker" to ArtNight, "letterballoons" to ArtViolet,
    "binarygrid" to ArtSky, "signgrid" to ArtGrape, "cagemath" to ArtCoral, "piecepack" to ArtViolet,
    "crateshift" to ArtGrape, "conduit" to ArtNight, "disctower" to ArtSky
)

/** Types with a hand-drawn motif. Others get the gradient plus the registry icon. */
val GAME_ART_TYPES: Set<String> = artBackground.keys

@Composable
fun GameArt(type: String, modifier: Modifier = Modifier) {
    val key = type.lowercase()
    val (c1, c2) = artBackground[key] ?: ArtFallbacks[(type.hashCode() and 0x7fffffff) % ArtFallbacks.size]
    Box(modifier.background(Brush.linearGradient(listOf(c1, c2))), contentAlignment = Alignment.Center) {
        if (key in artBackground) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) { drawMotif(key) }
        } else {
            Icon(
                imageVector = getQuizCategoryForType(type)?.icon ?: Icons.Default.Extension,
                contentDescription = null, tint = RvOnTone, modifier = Modifier.size(40.dp)
            )
        }
    }
}

private val W = Color.White
private val Soft = Color.White.copy(alpha = 0.28f)
private val Mid = Color.White.copy(alpha = 0.6f)

private fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r))

private fun DrawScope.circ(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(c, r, Offset(cx, cy))

private fun DrawScope.ln(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, c: Color) =
    drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = w, cap = StrokeCap.Round)

private fun DrawScope.ring(cx: Float, cy: Float, r: Float, w: Float, c: Color) =
    drawCircle(c, r, Offset(cx, cy), style = Stroke(width = w))

private fun DrawScope.hex(cx: Float, cy: Float, r: Float, c: Color) {
    val p = Path()
    for (i in 0 until 6) {
        val a = (PI / 180.0 * (90 + 60 * i)).toFloat()
        val x = cx + r * cos(a)
        val y = cy + r * sin(a)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, c)
}

private fun DrawScope.drawMotif(type: String) {
    val u = minOf(size.width, size.height) / 100f
    val ox = (size.width - 100f * u) / 2f
    val oy = (size.height - 100f * u) / 2f
    withTransform({ translate(ox, oy); scale(u, u, Offset.Zero) }) {
        when (type) {
            "sudoku" -> {
                rr(14f, 14f, 72f, 72f, 8f, Soft)
                for (i in 1..2) { ln(14f + 24f * i, 18f, 14f + 24f * i, 82f, 2.5f, Mid); ln(18f, 14f + 24f * i, 82f, 14f + 24f * i, 2.5f, Mid) }
                rr(18f, 18f, 16f, 16f, 4f, RvSun); rr(42f, 42f, 16f, 16f, 4f, W); rr(66f, 66f, 16f, 16f, 4f, RvSun)
                rr(66f, 18f, 16f, 16f, 4f, W); rr(18f, 66f, 16f, 16f, 4f, W)
            }
            "binarygrid" -> for (i in 0..2) for (j in 0..2) {
                val cx = 25f + 25f * i; val cy = 25f + 25f * j
                if ((i + j) % 2 == 0) circ(cx, cy, 10f, if (i == 1 && j == 1) RvSun else W) else ring(cx, cy, 8f, 4f, Mid)
            }
            "cagemath" -> {
                for (i in 0..2) for (j in 0..2) rr(17f + 25f * i, 17f + 25f * j, 20f, 20f, 5f, Soft)
                drawRoundRect(RvSun, Offset(13f, 13f), Size(53f, 28f), CornerRadius(8f, 8f),
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
                ln(27f, 22f, 27f, 32f, 4f, W); ln(22f, 27f, 32f, 27f, 4f, W)
                rr(67f, 67f, 20f, 20f, 5f, W)
            }
            "signgrid" -> {
                rr(12f, 34f, 30f, 30f, 8f, W); rr(58f, 34f, 30f, 30f, 8f, W)
                ln(46f, 39f, 54f, 49f, 5f, RvSun); ln(54f, 49f, 46f, 59f, 5f, RvSun)
                rr(12f, 72f, 30f, 12f, 6f, Soft); rr(58f, 72f, 30f, 12f, 6f, Soft)
                rr(12f, 16f, 30f, 12f, 6f, Soft); rr(58f, 16f, 30f, 12f, 6f, Soft)
            }
            "tilemerge" -> {
                val tiles = listOf(Triple(14f, 14f, 1) to W, Triple(52f, 14f, 2) to RvSun, Triple(14f, 52f, 3) to RvCoral, Triple(52f, 52f, 0) to Soft)
                for ((t, c) in tiles) {
                    rr(t.first, t.second, 34f, 34f, 8f, c)
                    for (k in 0 until t.third) circ(t.first + 17f + (k - (t.third - 1) / 2f) * 9f, t.second + 17f, 3.5f, RvNight)
                }
            }
            "slidetiles" -> for (i in 0..2) for (j in 0..2) {
                if (i == 2 && j == 2) drawRoundRect(Mid, Offset(14f + 26f * i, 14f + 26f * j), Size(22f, 22f), CornerRadius(6f, 6f),
                    style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
                else rr(14f + 26f * i, 14f + 26f * j, 22f, 22f, 6f, if (i == 0 && j == 0) RvSun else W)
            }
            "lightflip" -> for (i in 0..2) for (j in 0..2) {
                val cx = 25f + 25f * i; val cy = 25f + 25f * j
                val lit = (i == 1) || (j == 1)
                if (lit) { circ(cx, cy, 14f, RvSun.copy(alpha = 0.3f)); circ(cx, cy, 9f, RvSun) } else circ(cx, cy, 9f, Soft)
            }
            "safesweep" -> {
                for (i in 0..2) for (j in 0..2) rr(14f + 26f * i, 14f + 26f * j, 22f, 22f, 6f, if (i == 1 && j == 1) W else Soft)
                // flag in the top-right cell
                ln(76f, 18f, 76f, 32f, 2.5f, W)
                val flag = Path().apply { moveTo(76f, 18f); lineTo(86f, 22f); lineTo(76f, 27f); close() }
                drawPath(flag, RvCoral)
                // mine in the bottom-left cell
                circ(25f, 75f, 6f, RvNight)
                for (k in 0 until 4) { val a = (PI / 4 * k).toFloat(); ln(25f - 9f * cos(a), 75f - 9f * sin(a), 25f + 9f * cos(a), 75f + 9f * sin(a), 2.5f, RvNight) }
                circ(50f, 50f, 3.5f, RvMintEdge)
            }
            "disctower" -> {
                rr(10f, 82f, 80f, 6f, 3f, W)
                rr(18f, 40f, 4f, 42f, 2f, Mid); rr(48f, 30f, 4f, 52f, 2f, W); rr(78f, 40f, 4f, 42f, 2f, Mid)
                rr(25f, 70f, 50f, 11f, 5f, RvCoral); rr(31f, 58f, 38f, 11f, 5f, RvSun); rr(37f, 46f, 26f, 11f, 5f, RvMint)
            }
            "pixellogic" -> {
                val heart = listOf("01010", "11111", "11111", "01110", "00100")
                for (r in heart.indices) for (c in 0 until 5) {
                    val on = heart[r][c] == '1'
                    rr(15f + 14f * c, 15f + 14f * r, 12f, 12f, 3f, if (on) W else Soft.copy(alpha = 0.18f))
                }
            }
            "tubetint" -> {
                val layers = listOf(listOf(RvCoral, RvSky), listOf(RvMint, RvMint, RvSun), emptyList())
                for (t in 0..2) {
                    val x = 12f + 27f * t
                    drawRoundRect(W, Offset(x, 16f), Size(24f, 68f), CornerRadius(12f, 12f), style = Stroke(3f))
                    layers[t].forEachIndexed { k, c -> rr(x + 4f, 76f - 16f * (k + 1) + 2f, 16f, 15f, 5f, c) }
                }
            }
            "piecepack" -> {
                fun piece(cells: List<Pair<Int, Int>>, x: Float, y: Float, c: Color) = cells.forEach { rr(x + 15f * it.first, y + 15f * it.second, 13f, 13f, 3f, c) }
                piece(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2), 14f, 12f, RvCoral)
                piece(listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1), 46f, 12f, RvSun)
                piece(listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), 62f, 44f, RvMint)
                piece(listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0), 14f, 74f, W)
            }
            "crateshift" -> {
                rr(10f, 32f, 42f, 42f, 7f, RvSun)
                drawRoundRect(RvSunEdge, Offset(10f, 32f), Size(42f, 42f), CornerRadius(7f, 7f), style = Stroke(3f))
                ln(16f, 38f, 46f, 68f, 3.5f, RvSunEdge); ln(46f, 38f, 16f, 68f, 3.5f, RvSunEdge)
                ln(57f, 53f, 67f, 53f, 4f, W); ln(62f, 47f, 68f, 53f, 4f, W); ln(62f, 59f, 68f, 53f, 4f, W)
                ring(82f, 53f, 9f, 4f, W); circ(82f, 53f, 3f, W)
                circ(31f, 86f, 6f, W)
            }
            "conduit" -> {
                val pipe = Path().apply {
                    moveTo(12f, 72f); lineTo(12f, 32f); lineTo(50f, 32f); lineTo(50f, 72f); lineTo(86f, 72f)
                }
                drawPath(pipe, Soft, style = Stroke(12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(pipe, W, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                circ(12f, 72f, 8f, RvMint)
                circ(86f, 72f, 15f, RvSun.copy(alpha = 0.3f)); circ(86f, 72f, 9f, RvSun)
            }
            "wordguess" -> {
                val rows = listOf(listOf(Soft, RvSun, Soft, RvMint, Soft), listOf(RvMint, RvMint, RvSun, RvMint, Soft), List(5) { RvMint })
                rows.forEachIndexed { r, row -> row.forEachIndexed { c, col -> rr(9f + 17f * c, 18f + 22f * r, 14f, 17f, 4f, col) } }
            }
            "wordladder" -> {
                ln(30f, 10f, 30f, 90f, 5f, W); ln(70f, 10f, 70f, 90f, 5f, W)
                listOf(24f, 40f, 56f, 72f).forEachIndexed { i, y -> ln(30f, y, 70f, y, 4f, if (i == 0) RvSun else Mid) }
            }
            "letterballoons" -> {
                val b = listOf(Triple(30f, 38f, RvCoral), Triple(54f, 28f, RvSky), Triple(74f, 42f, RvSun))
                for ((x, y, c) in b) {
                    ln(x, y + 18f, 52f, 90f, 2f, Mid)
                    drawOval(c, Offset(x - 15f, y - 19f), Size(30f, 38f))
                    circ(x - 5f, y - 8f, 3.5f, W.copy(alpha = 0.55f))
                }
            }
            "wordhive" -> {
                for (k in 0 until 6) {
                    val a = (PI / 180.0 * (60 * k)).toFloat()
                    hex(50f + 28f * cos(a), 50f + 28f * sin(a), 13f, W.copy(alpha = 0.85f))
                }
                hex(50f, 50f, 14f, RvSun)
            }
            "codecracker" -> {
                listOf(RvCoral, RvSky, RvSun, RvMint).forEachIndexed { i, c -> circ(18f + 21f * i, 28f, 8f, c) }
                listOf(RvMint, RvCoral, RvSky).forEachIndexed { i, c -> circ(18f + 21f * i, 54f, 8f, c.copy(alpha = 0.55f)) }
                ring(81f, 54f, 7f, 3f, Mid)
                listOf(W, W, Mid, Soft).forEachIndexed { i, c -> circ(30f + 14f * i, 80f, 4.5f, c) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Language: one-time suggestion chip, picker dialog, settings entry
// ---------------------------------------------------------------------------------------------

private const val LANG_PREFS = "riddleverse_prefs"
private const val KEY_LANG_SUGGEST_DONE = "lang_suggest_done"

private fun tagOf(locale: Locale?): String? =
    locale?.let { AppLanguages.supportedTagFor(it.language, it.script, it.country) }

/** Supported tag of the device (system) language. */
fun deviceLanguageTag(): String? = tagOf(Resources.getSystem().configuration.locales.get(0))

/** Supported tag of the language the app is displaying now (per-app override first, else resources). */
fun currentLanguageTag(context: Context): String? {
    val override = AppCompatDelegate.getApplicationLocales()
    return if (!override.isEmpty) tagOf(override.get(0)) else tagOf(context.resources.configuration.locales.get(0))
}

fun applyAppLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}

/**
 * First-run chip "Switch to <language>?" shown once when the device language is a supported locale
 * that differs from the language the app is showing. Worded in the offered language.
 */
@Composable
fun LanguageSuggestionChip() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(LANG_PREFS, Context.MODE_PRIVATE) }
    // decided once per Home instance (survives rotation); persisted so it never shows twice
    val offeredTag: String? = rememberSaveable {
        LanguageSuggestion.decide(deviceLanguageTag(), currentLanguageTag(context), prefs.getBoolean(KEY_LANG_SUGGEST_DONE, false))?.tag ?: ""
    }.ifEmpty { null }
    var open by rememberSaveable { mutableStateOf(offeredTag != null) }
    LaunchedEffect(offeredTag) {
        if (offeredTag != null) prefs.edit().putBoolean(KEY_LANG_SUGGEST_DONE, true).apply()
    }
    val lang = AppLanguages.byTag(offeredTag)
    if (lang == null || !open) return

    // strings in the offered language, so the person can read the question
    val localized = remember(lang.tag) {
        val cfg = Configuration(context.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(lang.tag))
        context.createConfigurationContext(cfg)
    }
    val direction = if (lang.tag == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RvSurface),
            border = BorderStroke(1.dp, RvOutline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = RvViolet)
                Spacer(Modifier.width(10.dp))
                Text(
                    localized.getString(R.string.lang_switch_prompt, lang.nativeName),
                    modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = RvInk
                )
                TextButton(onClick = { open = false }) {
                    Text(localized.getString(R.string.lang_not_now), color = RvInkSoft, fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        open = false
                        prefs.edit().putBoolean(KEY_LANG_SUGGEST_DONE, true).apply()
                        applyAppLanguage(lang.tag)
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RvViolet, contentColor = RvOnTone)
                ) { Text(localized.getString(R.string.lang_switch_action), fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun LanguagePickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = currentLanguageTag(context)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_picker_title)) },
        text = {
            LazyColumn {
                items(AppLanguages.ALL, key = { it.tag }) { l ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onDismiss()
                            if (l.tag != current) applyAppLanguage(l.tag)
                        }.padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(l.nativeName, modifier = Modifier.weight(1f), fontSize = 16.sp,
                            fontWeight = if (l.tag == current) FontWeight.Bold else FontWeight.Normal, color = RvInk)
                        if (l.tag == current) Icon(Icons.Default.Check, contentDescription = null, tint = RvViolet)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

/** Settings entry: "Language  >  <current language>" opening the picker. */
@Composable
fun LanguageSettingsSection() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    SettingsSection(title = stringResource(R.string.language)) {
        SettingsRow(
            icon = Icons.Default.Language,
            title = stringResource(R.string.language_picker_title),
            subtitle = AppLanguages.byTag(currentLanguageTag(context))?.nativeName,
            onClick = { show = true }
        )
    }
    if (show) LanguagePickerDialog { show = false }
}

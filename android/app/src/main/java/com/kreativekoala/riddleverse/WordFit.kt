package com.kreativekoala.riddleverse

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height (below the page header) the word-puzzle screen really has, set by [WordPuzzleScaffold].
 * Games use it with [WordFit] to size tiles / keys / boards so the keyboard stays on screen without scrolling.
 * Infinity-safe default for tests and previews.
 */
val LocalWordViewport = compositionLocalOf { 640.dp }

/** Pure sizing rules (dp as Float) so the word screens can be swept in JVM tests. */
object WordFit {
    const val KEY_MAX = 54f
    const val KEY_MIN = 40f
    const val TILE_MAX = 58f
    const val TILE_COMFORT_MIN = 40f
    const val TILE_MIN = 32f

    class Sizes(val tile: Float, val key: Float, val needsScroll: Boolean)

    /**
     * Tile + key size for "tile grid on top, [keyRows] keyboard rows underneath" screens (Word Guess).
     * Order of giving up space: tiles down to 40, then keys down to 40, then tiles down to 32; below that the
     * caller's vertical scroll is the last resort ([Sizes.needsScroll]).
     * [fixedH] = everything else (message line, hint row, spacers, tile-grid gaps are NOT in it).
     */
    fun guessLayout(availW: Float, availH: Float, tileRows: Int, tileCols: Int, gap: Float, fixedH: Float, keyRows: Int = 3): Sizes {
        val widthLimit = (availW - 16f - gap * (tileCols - 1)) / tileCols
        val tile0 = minOf(TILE_MAX, widthLimit).coerceAtLeast(TILE_MIN)
        val gaps = gap * (tileRows - 1)
        fun need(tile: Float, key: Float) = tileRows * tile + gaps + keyRows * key + fixedH

        if (need(tile0, KEY_MAX) <= availH) return Sizes(tile0, KEY_MAX, false)
        val t1 = ((availH - fixedH - gaps - keyRows * KEY_MAX) / tileRows).coerceIn(minOf(TILE_COMFORT_MIN, tile0), tile0)
        if (need(t1, KEY_MAX) <= availH) return Sizes(t1, KEY_MAX, false)
        val tile2 = minOf(TILE_COMFORT_MIN, tile0)
        val k2 = ((availH - fixedH - gaps - tileRows * tile2) / keyRows).coerceIn(KEY_MIN, KEY_MAX)
        if (need(tile2, k2) <= availH) return Sizes(tile2, k2, false)
        val t3 = ((availH - fixedH - gaps - keyRows * KEY_MIN) / tileRows).coerceIn(minOf(TILE_MIN, tile0), tile2)
        return Sizes(t3, KEY_MIN, need(t3, KEY_MIN) > availH + 0.5f)
    }

    /** Key height for screens whose other content is [fixedH] tall. */
    fun keyHeight(availH: Float, fixedH: Float, keyRows: Int = 3): Float =
        ((availH - fixedH) / keyRows).coerceIn(KEY_MIN, KEY_MAX)

    /**
     * Balloon bunch height and key height that together fit: the bunch gives up space first (130 down to 70dp),
     * keys keep >= 40dp. [fixedH] excludes both.
     */
    fun bunchAndKey(availH: Float, fixedH: Float): Pair<Float, Float> {
        val key = ((availH - fixedH - 70f) / 3f).coerceIn(KEY_MIN, KEY_MAX)
        val bunch = (availH - fixedH - 3f * key).coerceIn(70f, 130f)
        return bunch to key
    }

    /** Size for each of [count] repeated items (rows of pegs, balloons...) sharing what is left after [fixedH]. */
    fun fitEach(availH: Float, fixedH: Float, count: Int, gap: Float, min: Float, max: Float): Float =
        ((availH - fixedH - gap * (count - 1).coerceAtLeast(0)) / count.coerceAtLeast(1)).coerceIn(min, max)

    /** Scale (0.7..1) for a fixed-size board of [boardH] when only [availH] is left for it. */
    fun boardScale(availH: Float, boardH: Float, minScale: Float = 0.7f): Float =
        (availH / boardH).coerceIn(minScale, 1f)
}

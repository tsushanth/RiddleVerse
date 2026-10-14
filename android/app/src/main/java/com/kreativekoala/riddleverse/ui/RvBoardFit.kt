package com.kreativekoala.riddleverse.ui

/**
 * Pure sizing for square game boards, so a board plus its controls fit the space a screen really has.
 * Boards sized from width alone push the controls off short phones; this takes the smaller of what the width
 * and the remaining height allow.
 */
object BoardFit {
    /**
     * Side (dp) of a square board given [availW] x [availH] of usable space, [hPad] of horizontal margin in total,
     * and [reservedH] of height taken by everything else on the screen. Never below [minSide] (the caller then scrolls).
     */
    fun squareSide(availW: Float, availH: Float, reservedH: Float, hPad: Float = 0f, minSide: Float = 0f): Float =
        minOf(availW - hPad, availH - reservedH).coerceAtLeast(minSide)
}

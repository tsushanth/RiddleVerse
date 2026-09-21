package com.kreativekoala.riddleverse.ui.theme

import androidx.compose.ui.graphics.Color

// Legacy template colors (kept: may be referenced elsewhere)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ---- RiddleVerse "playful flat" tokens (inspired by bright, flat brain-training apps) ----
// Neutrals: white canvas, cool lavender-tinted surfaces, deep indigo ink (never pure black).
val RvCanvas = Color(0xFFFFFFFF)
val RvSurface = Color(0xFFF4F2FF)
val RvOutline = Color(0xFFE2DEF5)
val RvInk = Color(0xFF231B4A)
val RvInkSoft = Color(0xFF6B648F)
val RvNight = Color(0xFF1E1B3A) // dark chrome (bottom bar)

// Brand + category "candy" colors. Each has a deeper edge used for the chunky bottom lip.
val RvViolet = Color(0xFF6C4CF1)
val RvVioletEdge = Color(0xFF4B31C4)
val RvSun = Color(0xFFFFB020)
val RvSunEdge = Color(0xFFD98A00)
val RvCoral = Color(0xFFFF6B6B)
val RvCoralEdge = Color(0xFFD94848)
val RvMint = Color(0xFF2DC98E)
val RvMintEdge = Color(0xFF1AA070)
val RvSky = Color(0xFF3AA0FF)
val RvSkyEdge = Color(0xFF1F7BD6)
val RvGrape = Color(0xFF9B5DE5)
val RvGrapeEdge = Color(0xFF7A3EC0)
val RvFlame = Color(0xFFFF8C00) // existing action orange, kept for continuity

/** Fill + edge pair for a tile / chunky button. */
data class RvTone(val fill: Color, val edge: Color)

val RvToneViolet = RvTone(RvViolet, RvVioletEdge)
val RvToneSun = RvTone(RvSun, RvSunEdge)
val RvToneCoral = RvTone(RvCoral, RvCoralEdge)
val RvToneMint = RvTone(RvMint, RvMintEdge)
val RvToneSky = RvTone(RvSky, RvSkyEdge)
val RvToneGrape = RvTone(RvGrape, RvGrapeEdge)

/** Maps a QuizCategory.filterCategory to a flat tone so each skill area has its own color. */
fun rvToneForCategory(filterCategory: String): RvTone = when (filterCategory) {
    "Math" -> RvToneSky
    "Memory" -> RvToneSun
    "English" -> RvToneGrape
    "Logic" -> RvToneMint
    "Focus" -> RvToneCoral
    "Reaction" -> RvToneCoral
    "Visual" -> RvToneViolet
    else -> RvToneViolet
}

// ---- Semantic tokens for screens (use these instead of raw Color(0x...) literals) ----
val RvSuccess = RvMint
val RvSuccessEdge = RvMintEdge
val RvError = RvCoral
val RvErrorEdge = RvCoralEdge
val RvWarning = RvSun
val RvWarningEdge = RvSunEdge
val RvInfo = RvSky
val RvOnTone = Color(0xFFFFFFFF)      // text/icons on a saturated tone fill
val RvSurfaceRaised = Color(0xFFFFFFFF) // cards on top of RvSurface
val RvDivider = RvOutline
val RvDisabled = Color(0xFFC9C5DD)
val RvScrim = Color(0x99231B4A)       // modal scrim, ink at 60%

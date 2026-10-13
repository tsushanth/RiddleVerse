package com.kreativekoala.riddleverse

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- classic puzzles (feat/puzzles-classic) --- short tutorial for every classic game

@Composable
fun ClassicTutorialScreen(type: String, onTutorialComplete: () -> Unit, onTutorialSkipped: () -> Unit) {
    val (instruction, tip, title) = ClassicPuzzles.tutorialFor(type) ?: return
    val (emoji, colors) = when (type.lowercase()) {
        ClassicPuzzles.TILE_MERGE -> "🔢" to listOf(Color(0xFFFF8C00), Color(0xFFFFB020))
        ClassicPuzzles.SLIDE_TILES -> "🧩" to listOf(Color(0xFF3AA0FF), Color(0xFF6C4CF1))
        ClassicPuzzles.LIGHT_FLIP -> "💡" to listOf(Color(0xFF231B4A), Color(0xFF6C4CF1))
        ClassicPuzzles.SAFE_SWEEP -> "🚩" to listOf(Color(0xFF2DC98E), Color(0xFF1F7BD6))
        else -> "🗼" to listOf(Color(0xFF9B5DE5), Color(0xFFFF6B6B))
    }
    SimpleTutorialScreen(
        content = SimpleTutorialContent(title = title, emoji = emoji, instruction = instruction, tip = tip, backgroundColor = colors),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped,
    )
}

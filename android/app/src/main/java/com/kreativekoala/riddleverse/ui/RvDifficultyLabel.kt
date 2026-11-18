package com.kreativekoala.riddleverse.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kreativekoala.riddleverse.R

/**
 * Localized display label for a difficulty tier name such as "Easy", "Medium+", "Expert-".
 * The raw English name stays the stored/compared value; use this only when showing it.
 * Unknown names are shown unchanged.
 */
@Composable
fun rvDifficultyLabel(name: String): String {
    val trimmed = name.trim()
    val suffix = trimmed.takeLastWhile { it == '+' || it == '-' }
    val base = trimmed.removeSuffix(suffix)
    val labelRes = when (base.lowercase()) {
        "tutorial" -> R.string.tutorial
        "beginner" -> R.string.xa_difficulty_beginner
        "easy" -> R.string.riddle_tier_easy
        "medium" -> R.string.riddle_tier_medium
        "hard" -> R.string.riddle_tier_hard
        "expert" -> R.string.common_difficulty_expert
        "advanced" -> R.string.common_difficulty_advanced
        "master" -> R.string.xa_difficulty_master
        "grandmaster" -> R.string.xa_difficulty_grandmaster
        else -> return name
    }
    return stringResource(labelRes) + suffix
}

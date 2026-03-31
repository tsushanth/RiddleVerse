package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tutorial Banner Component for StartPuzzleScreen
 * Shows a prominent banner encouraging first-time users to try the tutorial
 */
@Composable
fun TutorialBanner(
    puzzleType: String,
    onStartTutorial: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d("TutorialDebug", "🎯 Tutorial button clicked for puzzle type: $puzzleType")

    try {
        AnalyticsManager.getInstance()?.track(AnalyticsEvent("tutorial_started", mapOf(
            "puzzle_type" to puzzleType,
            "source" to "start_puzzle_banner"
        )))
        Log.d("TutorialDebug", "✅ Analytics tracked successfully")
    } catch (e: Exception) {
        Log.e("TutorialDebug", "❌ Analytics tracking failed", e)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3E5F5) // Light purple background
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = stringResource(R.string.tutorial),
                tint = Color(0xFF7B1FA2), // Purple icon
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "New to ${getPuzzleDisplayName(puzzleType)}?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A148C) // Dark purple
                )
                Text(
                    text = "Take a quick tutorial to learn how to play!",
                    fontSize = 14.sp,
                    color = Color(0xFF6A1B9A),
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Tutorial button
            Button(
                onClick = onStartTutorial,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7B1FA2)
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.tutorial),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Dismiss button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color(0xFF7B1FA2),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Utility function to get display names for different puzzle types
 */
fun getPuzzleDisplayName(puzzleType: String): String {
    return when (puzzleType.lowercase()) {
        "division" -> "Division Puzzles"
        "subtraction" -> "Subtraction Puzzles"
        "math" -> "Math Puzzles"
        "wordassociation" -> "Word Association"
        "synonyms" -> "Synonym Puzzles"
        "anagram" -> "Anagram Puzzles"
        "memorysquares" -> "Memory Squares"
        "memorystory" -> "Memory Story"
        "trivia" -> "Trivia Questions"
        "mathtipping" -> "Tip Calculator"
        "mathestimation" -> "Math Estimation"
        "conversion" -> "Unit Conversion"
        "average" -> "Average Calculator"
        "percentages" -> "Percentage Problems"
        "discounts" -> "Discount Calculator"
        "purchasing" -> "Smart Shopping"
        "jumbled words" -> "Word Jumble"
        "connotationwords" -> "Word Connotation"
        "storypuzzle" -> "Story Puzzles"
        "antonyms" -> "Antonym Puzzles"
        "find_object" -> "Find Objects"
        else -> "This Puzzle"
    }
}
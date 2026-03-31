package com.kreativekoala.riddleverse

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class DifficultySettingsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("difficulty_prefs", Context.MODE_PRIVATE)
    private val difficultyKey = "user_difficulty_preference"

    fun saveDifficulty(difficulty: String) {
        prefs.edit().putString(difficultyKey, difficulty).apply()
        android.util.Log.d("DifficultySettings", "💾 Saved difficulty preference: $difficulty")
    }

    fun loadDifficulty(): String {
        val difficulty = prefs.getString(difficultyKey, "Easy") ?: "Easy"
        android.util.Log.d("DifficultySettings", "📱 Loaded difficulty preference: $difficulty")
        return difficulty
    }

    fun resetToDefault() {
        saveDifficulty("Easy")
    }
}

// MARK: - Difficulty Settings Composable
@Composable
fun DifficultySettingsSection() {
    val context = LocalContext.current
    val difficultyManager = remember { DifficultySettingsManager(context) }
    var selectedDifficulty by remember { mutableStateOf(difficultyManager.loadDifficulty()) }

    val difficulties = listOf("Easy", "Medium", "Hard")

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Default Difficulty",
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                // Header row with icon and description
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Difficulty Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set your preferred difficulty level",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // Difficulty options
                difficulties.forEach { difficulty ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDifficulty == difficulty,
                            onClick = {
                                selectedDifficulty = difficulty
                                difficultyManager.saveDifficulty(difficulty)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = difficulty,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )

                        if (selectedDifficulty == difficulty) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (difficulty != difficulties.last()) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    }
                }

                // Info text
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡",
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "You can still change difficulty when starting any puzzle",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
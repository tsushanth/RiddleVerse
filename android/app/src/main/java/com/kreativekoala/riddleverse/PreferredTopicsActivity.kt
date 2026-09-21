package com.kreativekoala.riddleverse

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class PreferredTopicsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PreferredTopicsScreen {
                    finish() // Close activity and return to previous screen
                }
            }
        }
    }
}

// Color palette for topics (similar to HomeActivity)
val topicColors = listOf(
    RvSky.copy(alpha = 0.18f),
    RvGrape.copy(alpha = 0.18f),
    RvMint.copy(alpha = 0.18f),
    RvSun.copy(alpha = 0.22f),
    RvCoral.copy(alpha = 0.18f),
    RvViolet.copy(alpha = 0.16f),
    RvMint.copy(alpha = 0.25f),
    RvCoral.copy(alpha = 0.25f),
    RvSun.copy(alpha = 0.3f),
    RvGrape.copy(alpha = 0.25f)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferredTopicsScreen(onSaveComplete: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: "guest@example.com"

    var selectedTopics by remember { mutableStateOf(listOf<String>()) }
    var newTopic by remember { mutableStateOf(TextFieldValue()) }
    var isLoading by remember { mutableStateOf(false) }

    val sampleTopics = listOf(
        "Math", "Logic", "History", "Science", "Trivia", "Riddles",
        "Programming", "Literature", "Geography", "Art", "Music",
        "Sports", "Movies", "Technology", "Nature", "Psychology"
    )

    // Load topics on start
    LaunchedEffect(Unit) {
        isLoading = true
        db.collection("user_topics").document(userEmail).get().addOnSuccessListener { doc ->
            val topics = doc.get("topics") as? List<String> ?: emptyList()
            selectedTopics = topics.toMutableList()
            isLoading = false
        }.addOnFailureListener {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        // Top Navigation Bar
        TopNavigationBar(
            onBackClick = onSaveComplete,
            title = stringResource(R.string.preferred_topics)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Explanation Section
                item {
                    ExplanationCard()
                }

                // Popular Topics Section
                item {
                    PopularTopicsSection(
                        sampleTopics = sampleTopics,
                        selectedTopics = selectedTopics,
                        onTopicToggle = { topic ->
                            selectedTopics = if (selectedTopics.contains(topic)) {
                                selectedTopics.filter { it != topic }
                            } else {
                                selectedTopics + topic
                            }
                        }
                    )
                }

                // Add Custom Topic Section
                item {
                    AddCustomTopicSection(
                        newTopic = newTopic,
                        onNewTopicChange = { newTopic = it },
                        onAddTopic = {
                            val trimmed = newTopic.text.trim()
                            if (trimmed.isNotEmpty() && !selectedTopics.contains(trimmed)) {
                                selectedTopics = selectedTopics + trimmed
                                newTopic = TextFieldValue()
                            }
                        }
                    )
                }

                // Selected Topics Section
                item {
                    SelectedTopicsSection(
                        selectedTopics = selectedTopics,
                        onRemoveTopic = { topic ->
                            selectedTopics = selectedTopics.filter { it != topic }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Space for bottom button
                }
            }
        }

        // Bottom Action Button
        BottomActionButton(
            selectedTopics = selectedTopics,
            userEmail = userEmail,
            db = db,
            context = context,
            onComplete = onSaveComplete
        )
    }
}

@Composable
fun TopNavigationBar(
    onBackClick: () -> Unit,
    title: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk
            )
        }
    }
}

@Composable
fun ExplanationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = RvSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "🎯 Personalize Your Experience",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RvMintEdge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select topics you're interested in to receive personalized daily quizzes. We'll generate 5 questions each day based on your preferences!",
                fontSize = 14.sp,
                color = RvInk,
                lineHeight = 20.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PopularTopicsSection(
    sampleTopics: List<String>,
    selectedTopics: List<String>,
    onTopicToggle: (String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.popular_topics),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = RvInk
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sampleTopics.forEachIndexed { index, topic ->
                val isSelected = selectedTopics.contains(topic)
                val backgroundColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    topicColors[index % topicColors.size]
                }
                val textColor = if (isSelected) {
                    RvOnTone
                } else {
                    RvInk
                }

                Card(
                    modifier = Modifier.clickable { onTopicToggle(topic) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (isSelected) RvVioletEdge else RvOutline),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = backgroundColor
                    )
                ) {
                    Text(
                        text = topic,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun AddCustomTopicSection(
    newTopic: TextFieldValue,
    onNewTopicChange: (TextFieldValue) -> Unit,
    onAddTopic: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.add_custom_topic),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = RvInk
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
            border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = newTopic,
                    onValueChange = onNewTopicChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Enter your topic...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onAddTopic,
                    enabled = newTopic.text.trim().isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Add",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun SelectedTopicsSection(
    selectedTopics: List<String>,
    onRemoveTopic: (String) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.your_selected_topics),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = RvInk
            )

            Spacer(modifier = Modifier.width(8.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "${selectedTopics.size}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvOnTone
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTopics.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = RvSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎭",
                        fontSize = 40.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.no_topics_selected),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Select topics above to get started",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(selectedTopics) { topic ->
                    SelectedTopicChip(
                        topic = topic,
                        onRemove = { onRemoveTopic(topic) }
                    )
                }
            }
        }
    }
}

@Composable
fun SelectedTopicChip(
    topic: String,
    onRemove: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = topic,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove $topic",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onRemove() },
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun BottomActionButton(
    selectedTopics: List<String>,
    userEmail: String,
    db: FirebaseFirestore,
    context: android.content.Context,
    onComplete: () -> Unit
) {
    var isSaving by remember { mutableStateOf(false) }
    var isGeneratingPuzzles by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Status indicator for puzzle generation
            if (isGeneratingPuzzles) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = RvSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "🎯 Generating your daily puzzles...",
                            fontSize = 14.sp,
                            color = RvSkyEdge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving && !isGeneratingPuzzles
                ) {
                    Text(
                        stringResource(R.string.cancel),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        saveTopicsAndGeneratePuzzles(
                            selectedTopics = selectedTopics,
                            userEmail = userEmail,
                            db = db,
                            context = context,
                            onSavingStateChange = { isSaving = it },
                            onGeneratingStateChange = { isGeneratingPuzzles = it },
                            onComplete = onComplete
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving && selectedTopics.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RvOnTone,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.saving))
                    } else {
                        Text(
                            "Save & Generate Daily Puzzles",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (selectedTopics.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Select at least one topic to continue",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// New function to handle the two-step process
private fun saveTopicsAndGeneratePuzzles(
    selectedTopics: List<String>,
    userEmail: String,
    db: FirebaseFirestore,
    context: android.content.Context,
    onSavingStateChange: (Boolean) -> Unit,
    onGeneratingStateChange: (Boolean) -> Unit,
    onComplete: () -> Unit
) {
    onSavingStateChange(true)

    // Step 1: Save topics to Firestore
    db.collection("user_topics").document(userEmail)
        .set(mapOf("topics" to selectedTopics))
        .addOnSuccessListener {
            onSavingStateChange(false)

            // Show immediate success message and exit
            Toast.makeText(
                context,
                "🎉 Topics saved! Your daily puzzles are being generated.",
                Toast.LENGTH_LONG
            ).show()

            // Exit immediately
            onComplete()

            // Step 2: Generate puzzles in background (don't wait)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val encodedEmail = java.net.URLEncoder.encode(userEmail, "UTF-8")
                    val url = "https://puzzleverseai.com/generate-user-puzzles-with-notifications?email=$encodedEmail"

                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 15000

                    val responseCode = connection.responseCode

                    // Optional: Show another toast when generation completes
                    // (only if the user is still in the app)
                    withContext(Dispatchers.Main) {
                        if (responseCode == 200) {
                            Toast.makeText(
                                context,
                                "✨ Daily puzzles ready! Check your notifications.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    // Silently handle errors since user already left the screen
                    // The backend will still process the request
                }
            }
        }
        .addOnFailureListener { e ->
            onSavingStateChange(false)
            Toast.makeText(
                context,
                "Failed to save topics: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
}
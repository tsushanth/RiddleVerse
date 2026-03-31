package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data class for simple tutorial content
 */
data class SimpleTutorialContent(
    val title: String,
    val emoji: String,
    val instruction: String,
    val tip: String? = null,
    val backgroundColor: List<Color> = listOf(Color(0xFF667eea), Color(0xFF764ba2))
)

/**
 * Simple single-screen tutorial that shows key instructions
 * Used for puzzles that don't need complex interactive tutorials
 */
@Composable
fun SimpleTutorialScreen(
    content: SimpleTutorialContent,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(colors = content.backgroundColor)
            )
    ) {
        // Close button
        IconButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.skip),
                tint = Color.White.copy(alpha = 0.8f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Emoji
            Text(
                text = content.emoji,
                fontSize = 80.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = content.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Main instruction card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.how_to_play),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7B1FA2)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = content.instruction,
                        fontSize = 16.sp,
                        color = Color(0xFF333333),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    // Optional tip
                    if (content.tip != null) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFF3E0)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "💡", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = content.tip,
                                    fontSize = 14.sp,
                                    color = Color(0xFF795548)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Start button
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = stringResource(R.string.got_it_lets_play),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7B1FA2)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Skip text
            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(R.string.skip_tutorial),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ============ WAVE 1 TUTORIALS ============

@Composable
fun NumberSumTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Number Sum",
            emoji = "🔢",
            instruction = "Tap numbers that add up to the target sum. Select multiple numbers until their total matches the goal.",
            tip = "Start with larger numbers to get close, then use smaller ones to hit exact target.",
            backgroundColor = listOf(Color(0xFF11998e), Color(0xFF38ef7d))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun SymbolSwipeTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Symbol Swipe",
            emoji = "👆",
            instruction = "Swipe LEFT or RIGHT based on the symbol shown. Each symbol has a direction - learn the pattern and swipe fast!",
            tip = "Watch the symbol-direction mapping at the start of each round.",
            backgroundColor = listOf(Color(0xFF6a11cb), Color(0xFF2575fc))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun WordSearchTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Word Search",
            emoji = "🔍",
            instruction = "Find hidden words in the letter grid. Drag your finger across letters to select words horizontally, vertically, or diagonally.",
            tip = "Check the word list for hints about what to find.",
            backgroundColor = listOf(Color(0xFF00b4db), Color(0xFF0083b0))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun AveragesTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Averages",
            emoji = "📊",
            instruction = "Calculate the average of the given numbers. Add them all together, then divide by how many numbers there are.",
            tip = "Average = Sum of all numbers ÷ Count of numbers",
            backgroundColor = listOf(Color(0xFFf12711), Color(0xFFf5af19))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

// ============ WAVE 2 TUTORIALS ============

@Composable
fun SymmetryTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Symmetry",
            emoji = "🪞",
            instruction = "Create a mirror image of the pattern. Tap cells on your side to match the reflection of the original pattern.",
            tip = "Imagine folding the grid in half - matching cells should overlap.",
            backgroundColor = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun ColorTextMatchingTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Color Text Matching",
            emoji = "🎨",
            instruction = "Match the COLOR of the text, not what the word says! If 'BLUE' is written in red ink, the answer is RED.",
            tip = "Ignore the word meaning - focus only on the ink color.",
            backgroundColor = listOf(Color(0xFFee0979), Color(0xFFff6a00))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun NumberSequenceTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Number Sequence",
            emoji = "🔢",
            instruction = "Tap the numbers in ascending order, from smallest to largest. Find and tap each number as quickly as you can!",
            tip = "Scan the grid first to locate the next number before tapping.",
            backgroundColor = listOf(Color(0xFF56ab2f), Color(0xFFa8e063))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun PercentageTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Percentage",
            emoji = "💯",
            instruction = "Calculate the percentage of a number. For example, 25% of 80 = 80 × 0.25 = 20.",
            tip = "Move the decimal point: 25% = 0.25, 10% = 0.10",
            backgroundColor = listOf(Color(0xFFf953c6), Color(0xFFb91d73))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

// ============ WAVE 3 TUTORIALS ============

@Composable
fun SubtractionTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Subtraction",
            emoji = "➖",
            instruction = "Build the answer using value buttons. Tap + to add values and - to subtract until you reach the correct difference.",
            tip = "Start with the largest place value to get close quickly.",
            backgroundColor = listOf(Color(0xFF1e3c72), Color(0xFF2a5298))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun MathComparisonTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Math Comparison",
            emoji = "⚖️",
            instruction = "Compare two mathematical expressions. Tap the one with the GREATER value. Think fast - you're racing the clock!",
            tip = "Estimate quickly - you don't always need exact calculation.",
            backgroundColor = listOf(Color(0xFF3a1c71), Color(0xFFd76d77))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun MathExpressionTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Math Expression",
            emoji = "🧮",
            instruction = "Solve the math expression shown. Follow order of operations: parentheses, exponents, multiply/divide, then add/subtract.",
            tip = "Remember PEMDAS: Parentheses, Exponents, Multiplication, Division, Addition, Subtraction",
            backgroundColor = listOf(Color(0xFF0f0c29), Color(0xFF302b63))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun ImageVortexTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Image Vortex",
            emoji = "🌀",
            instruction = "Images spin in a vortex pattern. Identify and tap the correct image that matches the description before time runs out!",
            tip = "Focus on key distinguishing features of each image.",
            backgroundColor = listOf(Color(0xFF200122), Color(0xFF6f0000))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun UniqueObjectTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Unique Object",
            emoji = "🔎",
            instruction = "Find the ONE object that's different from all the others. Look for differences in shape, color, size, or pattern.",
            tip = "Scan systematically - don't just look randomly!",
            backgroundColor = listOf(Color(0xFF134e5e), Color(0xFF71b280))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun MemoryStoryTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Memory Story",
            emoji = "📖",
            instruction = "Read the story carefully, then answer questions about details. Pay attention to names, numbers, and specific facts.",
            tip = "Create mental images of the story to remember details.",
            backgroundColor = listOf(Color(0xFF1f4037), Color(0xFF99f2c8))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun MemorySequencingTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Memory Sequencing",
            emoji = "����",
            instruction = "Watch the sequence carefully, then repeat it in the correct order. The sequences get longer as you progress!",
            tip = "Try grouping items into chunks to remember longer sequences.",
            backgroundColor = listOf(Color(0xFF536976), Color(0xFF292E49))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun MemoryRetentionTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Memory Retention",
            emoji = "🧠",
            instruction = "Remember facts about different subjects, then match each fact to the correct subject. Focus on associations!",
            tip = "Create memorable connections between subjects and their facts.",
            backgroundColor = listOf(Color(0xFF4568DC), Color(0xFFB06AB3))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun MemoryPreviousSingleTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Memory Previous",
            emoji = "⏮️",
            instruction = "Remember what you saw in the PREVIOUS round. When asked, recall the item that appeared before the current one.",
            tip = "Always keep the previous item in mind as you see new ones.",
            backgroundColor = listOf(Color(0xFFfe8c00), Color(0xFFf83600))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun MemoryPreviousPairTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Memory Previous Pair",
            emoji = "👥",
            instruction = "Remember the PAIR from the previous round. You'll need to recall both items that appeared together before.",
            tip = "Link the two items together in your mind as a story.",
            backgroundColor = listOf(Color(0xFF00c6ff), Color(0xFF0072ff))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun TriangleDotMemoryTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Triangle Dot Memory",
            emoji = "🔺",
            instruction = "Memorize the dot pattern in the triangle, then recreate it from memory. Pay attention to exact positions!",
            tip = "Focus on the relative positions of dots to each other.",
            backgroundColor = listOf(Color(0xFF834d9b), Color(0xFFd04ed6))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun SwipeWordTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Swipe Word",
            emoji = "📝",
            instruction = "Swipe through letters to form the target word. Connect letters in order by dragging your finger across them.",
            tip = "Plan your path before you start swiping!",
            backgroundColor = listOf(Color(0xFF12c2e9), Color(0xFFc471ed))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun JumbleInputTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Jumble Input",
            emoji = "🔤",
            instruction = "Unscramble the jumbled letters to form a real word. Tap letters in the correct order to spell it out.",
            tip = "Look for common letter patterns like 'ing', 'tion', 'ed'.",
            backgroundColor = listOf(Color(0xFFf5af19), Color(0xFFf12711))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun WordPrefixTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Word Prefix",
            emoji = "🔠",
            instruction = "Find words that start with the given prefix. Type or select words that begin with those letters.",
            tip = "Think of common words first, then try less obvious ones.",
            backgroundColor = listOf(Color(0xFF1D976C), Color(0xFF93F9B9))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun LetterSetTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Letter Set",
            emoji = "🅰️",
            instruction = "Form valid words using only the letters provided. Each letter can typically be used once per word.",
            tip = "Start with short words, then try to find longer ones.",
            backgroundColor = listOf(Color(0xFF614385), Color(0xFF516395))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun SentenceTransitionsTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Sentence Transitions",
            emoji = "📝",
            instruction = "Choose the best transition word or phrase to connect the sentences. Think about the logical relationship between ideas.",
            tip = "Consider: Is it adding, contrasting, or showing cause/effect?",
            backgroundColor = listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun ColorShapeMatchingTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Color Shape Matching",
            emoji = "🔷",
            instruction = "Match shapes with their correct colors. Pay attention to BOTH the shape AND the color - both must match!",
            tip = "Process one attribute at a time: first shape, then color.",
            backgroundColor = listOf(Color(0xFFff6b6b), Color(0xFFfeca57))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun ImageMatchTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Image Match",
            emoji = "🖼️",
            instruction = "Find pairs of matching images. Tap two images that are identical or related based on the puzzle rules.",
            tip = "Memorize image positions as you flip them.",
            backgroundColor = listOf(Color(0xFF43cea2), Color(0xFF185a9d))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun ImageQuestionTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Image Question",
            emoji = "❓",
            instruction = "Study the image carefully, then answer questions about what you saw. Details matter!",
            tip = "Look at colors, numbers, positions, and small details.",
            backgroundColor = listOf(Color(0xFF0575E6), Color(0xFF021B79))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun FindDifferencesTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Find Differences",
            emoji = "🔍",
            instruction = "Compare two images side by side. Tap on the differences you spot between them!",
            tip = "Scan systematically from left to right, top to bottom.",
            backgroundColor = listOf(Color(0xFFad5389), Color(0xFF3c1053))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun FindObjectTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Find Object",
            emoji = "🎯",
            instruction = "Find and tap the hidden object in the scene. Read the description carefully to know what you're looking for!",
            tip = "Objects may be camouflaged or partially hidden.",
            backgroundColor = listOf(Color(0xFF654ea3), Color(0xFFeaafc8))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun WaldoPuzzleTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Find Character",
            emoji = "🕵️",
            instruction = "Find the specific character hidden in a busy scene. Look carefully - they could be anywhere!",
            tip = "Look for distinctive features like clothing colors or accessories.",
            backgroundColor = listOf(Color(0xFFe52d27), Color(0xFFb31217))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun ProgressiveRevealTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Progressive Reveal",
            emoji = "🎭",
            instruction = "An image slowly reveals itself. Guess what it is as early as possible - faster guesses earn more points!",
            tip = "Look for distinctive shapes and colors as they appear first.",
            backgroundColor = listOf(Color(0xFF373B44), Color(0xFF4286f4))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun RealOrAiTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Real or AI",
            emoji = "🤖",
            instruction = "Look at two images and decide which one is a real photo and which is AI-generated. Trust your instincts!",
            tip = "Check for unnatural textures, weird hands, or inconsistent lighting.",
            backgroundColor = listOf(Color(0xFF000428), Color(0xFF004e92))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun FlowPuzzleTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Flow Puzzle",
            emoji = "〰️",
            instruction = "Connect matching colored dots with pipes. Fill the entire board without crossing paths!",
            tip = "Start with dots that are far apart or in corners.",
            backgroundColor = listOf(Color(0xFF36D1DC), Color(0xFF5B86E5))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun ContextSwitchTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Context Switch",
            emoji = "🔀",
            instruction = "The rules change! Pay attention to the current context and respond accordingly. Stay flexible!",
            tip = "Watch for context indicators before each question.",
            backgroundColor = listOf(Color(0xFFf7971e), Color(0xFFffd200))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun DualCardTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Dual Card",
            emoji = "🃏",
            instruction = "Handle two tasks simultaneously! Each card has its own challenge - complete both correctly.",
            tip = "Focus on one card at a time, but keep track of both.",
            backgroundColor = listOf(Color(0xFF8360c3), Color(0xFF2ebf91))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun MultiMatchMusicTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Music Match",
            emoji = "🎵",
            instruction = "Match musical elements together. Listen carefully and connect related sounds, instruments, or patterns.",
            tip = "Use headphones for the best experience!",
            backgroundColor = listOf(Color(0xFFb92b27), Color(0xFF1565C0))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun GeographyCityTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "City Selection",
            emoji = "🏙️",
            instruction = "Identify the correct city based on clues, landmarks, or location. Test your world geography knowledge!",
            tip = "Famous landmarks and unique features help identify cities.",
            backgroundColor = listOf(Color(0xFF16222A), Color(0xFF3A6073))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun GeographyCountryTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Country Selection",
            emoji = "🌍",
            instruction = "Identify the country based on its shape, flag, or clues. Explore the world one country at a time!",
            tip = "Country shapes and neighboring countries are helpful clues.",
            backgroundColor = listOf(Color(0xFF1a2a6c), Color(0xFF16a085))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun TipBubbleTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Tip Calculator",
            emoji = "💵",
            instruction = "Calculate the correct tip amount. Multiply the bill by the tip percentage to find your answer.",
            tip = "For 15%: find 10% (move decimal), then add half of that.",
            backgroundColor = listOf(Color(0xFF1D976C), Color(0xFF93F9B9))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun MathCrosswordTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Math Crossword",
            emoji = "➕",
            instruction = "Fill in the crossword with numbers that make all equations correct. Each row and column must be valid!",
            tip = "Start with the equations that have the most numbers filled in.",
            backgroundColor = listOf(Color(0xFF3a7bd5), Color(0xFF3a6073))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun MatchScreenTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Matching",
            emoji = "🔗",
            instruction = "Match related items together. Draw lines or tap to connect pairs that belong together.",
            tip = "Look for logical connections between items.",
            backgroundColor = listOf(Color(0xFFee9ca7), Color(0xFFffdde1))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun QATutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Question & Answer",
            emoji = "❓",
            instruction = "Read the question carefully and select or type your answer. Some questions may have multiple valid answers!",
            tip = "Read all options before choosing your answer.",
            backgroundColor = listOf(Color(0xFF396afc), Color(0xFF2948ff))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

@Composable
fun MultipleChoiceTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    SimpleTutorialScreen(
        content = SimpleTutorialContent(
            title = "Multiple Choice",
            emoji = "📋",
            instruction = "Read the question and select the best answer from the choices provided. Only one answer is correct!",
            tip = "Eliminate obviously wrong answers first.",
            backgroundColor = listOf(Color(0xFF7F7FD5), Color(0xFF86A8E7))
        ),
        onComplete = onTutorialComplete,
        onSkip = onTutorialSkipped
    )
}

package com.kreativekoala.riddleverse

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.pager.*
import kotlinx.coroutines.launch

/**
 * Onboarding screen shown to first-time users
 * Explains the app's features and how to get started
 */
@OptIn(ExperimentalPagerApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState()
    val coroutineScope = rememberCoroutineScope()
    val onboardingPages = listOf(
        OnboardingPage(
            icon = Icons.Default.EmojiObjects,
            title = "Welcome to RiddleVerse!",
            description = "Challenge your mind with engaging puzzles, quizzes, and brain teasers. From math problems to word games, we have something for everyone.",
            color = Color(0xFFB388FF)
        ),
        OnboardingPage(
            icon = Icons.Default.Extension,
            title = "Explore Puzzle Categories",
            description = "Browse through different types of puzzles including Division, Subtraction, Word Association, Memory Games, and many more. Each category offers unique challenges.",
            color = Color(0xFF80D8FF)
        ),
        OnboardingPage(
            icon = Icons.Default.TrendingUp,
            title = "Track Your Progress",
            description = "Complete daily puzzles to build your streak, earn achievements, and see your improvement over time. Challenge yourself to reach new milestones!",
            color = Color(0xFF82B1FF)
        ),
        OnboardingPage(
            icon = Icons.Default.School,
            title = "Learn as You Play",
            description = "New to a puzzle type? Look for tutorial prompts that explain how each game works. You can always access help when you need it.",
            color = Color(0xFFA7FFEB)
        ),
        OnboardingPage(
            icon = Icons.Default.Rocket,
            title = "Ready to Begin?",
            description = "You're all set! Tap 'Get Started' to explore puzzles, create custom games, and start your journey to becoming a puzzle master.",
            color = Color(0xFFFF8A80)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar with skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < onboardingPages.size - 1) {
                    TextButton(onClick = onComplete) {
                        Text(
                            text = stringResource(R.string.skip),
                            color = Color(0xFF7B1FA2),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Pager content
            HorizontalPager(
                count = onboardingPages.size,
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                OnboardingPageContent(onboardingPages[page])
            }

            // Page indicators
            HorizontalPagerIndicator(
                pagerState = pagerState,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp),
                activeColor = Color(0xFF7B1FA2),
                inactiveColor = Color(0xFFE0E0E0),
                indicatorWidth = 8.dp,
                indicatorHeight = 8.dp,
                spacing = 8.dp
            )

            // Bottom button
            Button(
                onClick = {
                    if (pagerState.currentPage < onboardingPages.size - 1) {
                        // Go to next page
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        // Complete onboarding
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7B1FA2)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = if (pagerState.currentPage < onboardingPages.size - 1) stringResource(R.string.next) else stringResource(R.string.start),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon in colored circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(page.color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = page.color
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Title
        Text(
            text = page.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C2C54),
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Description
        Text(
            text = page.description,
            fontSize = 16.sp,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color
)

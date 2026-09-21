package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WelcomeOnboardingActivity : AppCompatActivity() {
    private var tutorialStartTime = System.currentTimeMillis()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsManager.getInstance()?.track(AnalyticsEvent.tutorialStart())
        trackScreenView("onboarding_welcome")
        setContent {
            RiddleVerseTheme {
                WelcomeOnboardingScreen(
                    onComplete = {
                        val duration = (System.currentTimeMillis() - tutorialStartTime) / 1000.0
                        AnalyticsManager.getInstance()?.track(
                            AnalyticsEvent.tutorialComplete(duration)
                        )
                        // Mark onboarding as completed
                        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("onboarding_completed", true)
                            .apply()

                        // Navigate to main app
                        startActivity(Intent(this@WelcomeOnboardingActivity, HomeActivity::class.java))
                        finish()
                    },
                    onSkip = {

                        // Same as complete but mark as skipped
                        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("onboarding_completed", true)
                            .putBoolean("onboarding_skipped", true)
                            .apply()

                        startActivity(Intent(this@WelcomeOnboardingActivity, HomeActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeOnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF667EEA),
                        Color(0xFF764BA2)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Skip button (top right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(top = 32.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < 3) {
                    TextButton(onClick = {
                        // Add skip tracking
                        AnalyticsManager.getInstance()?.track(
                            AnalyticsEvent.tutorialSkip(pagerState.currentPage + 1)
                        )
                        AnalyticsManager.getInstance()?.track(AnalyticsEvent("onboarding_skipped", mapOf(
                            "skipped_at_page" to pagerState.currentPage,
                            "total_pages" to 4,
                            "completion_percentage" to (pagerState.currentPage / 4.0 * 100)
                        )))
                        onSkip()
                    }) {
                        Text(
                            stringResource(R.string.skip),
                            color = RvInkSoft.copy(alpha = 0.8f),
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Content pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                LaunchedEffect(page) {
                    val stepNames = listOf("value_prop_1", "value_prop_2", "value_prop_3", "get_started")
                    AnalyticsManager.getInstance()?.track(
                        AnalyticsEvent.tutorialStep(page + 1, stepNames[page])
                    )
                }
                when (page) {
                    0 -> ValuePropPage1()
                    1 -> ValuePropPage2()
                    2 -> ValuePropPage3()
                    3 -> GetStartedPage(onComplete)
                }

            }

            // Bottom navigation
            OnboardingBottomBar(
                pagerState = pagerState,
                onNext = {
                    scope.launch {
                        if (pagerState.currentPage < 3) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        } else {
                            onComplete()
                        }
                    }
                },
                onComplete = onComplete
            )
        }
    }
}

@Composable
fun ValuePropPage1() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(800)) + slideInVertically(
            animationSpec = tween(800),
            initialOffsetY = { it / 3 }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated brain emoji
            val scale by animateFloatAsState(
                targetValue = if (visible) 1f else 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "brainScale"
            )

            Text(
                "🧠",
                fontSize = 100.sp,
                modifier = Modifier.scale(scale)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Train Your Brain Daily",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = RvInk
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Sharpen your mind with math, word puzzles, and brain teasers designed to boost cognitive skills",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = RvInkSoft.copy(alpha = 0.9f),
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Feature highlights
            FeatureRow(
                icon = "🔢",
                text = "Math & Logic Puzzles",
                color = RvInkSoft.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeatureRow(
                icon = "📝",
                text = "Word & Language Games",
                color = RvInkSoft.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeatureRow(
                icon = "⚡",
                text = "Quick Daily Challenges",
                color = RvInkSoft.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun ValuePropPage2() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(800)) + slideInVertically(
            animationSpec = tween(800),
            initialOffsetY = { it / 3 }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📈", fontSize = 100.sp)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Track Your Progress",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = RvInk
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Level up, earn badges, and see your improvement over time with detailed analytics",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = RvInkSoft.copy(alpha = 0.9f),
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Progress visualization
            ProgressVisualizationCard()
        }
    }
}

@Composable
fun ValuePropPage3() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(800)) + slideInVertically(
            animationSpec = tween(800),
            initialOffsetY = { it / 3 }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🏆", fontSize = 100.sp)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Compete & Achieve",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = RvInk
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Challenge friends, climb leaderboards, and unlock exclusive rewards as you master each puzzle type",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = RvInkSoft.copy(alpha = 0.9f),
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Achievement showcase
            AchievementShowcase()
        }
    }
}

@Composable
fun GetStartedPage(onComplete: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(800)) + slideInVertically(
            animationSpec = tween(800),
            initialOffsetY = { it / 3 }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🚀", fontSize = 100.sp)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Ready to Start?",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = RvInk
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Join thousands of puzzle enthusiasts and start your brain training journey today!",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = RvInkSoft.copy(alpha = 0.9f),
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RvSurfaceRaised,
                    contentColor = Color(0xFF667EEA)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    "Let's Get Started!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Free to play • No ads during puzzles",
                fontSize = 14.sp,
                color = RvInkSoft.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingBottomBar(
    pagerState: androidx.compose.foundation.pager.PagerState,
    onNext: () -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Page indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == index) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index)
                                Color.White
                            else
                                Color.White.copy(alpha = 0.4f)
                        )
                )
            }
        }

        // Next/Get Started button
        if (pagerState.currentPage < 3) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RvSurface,
                    contentColor = RvInk
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.next),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = stringResource(R.string.next),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureRow(
    icon: String,
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(0.8f)
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text,
            fontSize = 16.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ProgressVisualizationCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = RvSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Level 5",
                    color = RvInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "⭐ 1,250 XP",
                    color = RvInk,
                    fontSize = 14.sp
                )
            }

            LinearProgressIndicator(
                progress = 0.65f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = RvSun,
                trackColor = RvOutline
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("🔥 5 day streak", color = RvInk, fontSize = 12.sp)
                Text("65% to Level 6", color = RvInk, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AchievementShowcase() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(3) { index ->
            val achievements = listOf(
                Triple("🥇", "Math Master", "Solved 100 math puzzles"),
                Triple("⚡", "Speed Demon", "5 puzzles under 30 seconds"),
                Triple("🔥", "Streak King", "10 day solving streak")
            )

            Card(
                modifier = Modifier
                    .width(140.dp)
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = RvSurface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        achievements[index].first,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        achievements[index].second,
                        color = RvInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        achievements[index].third,
                        color = RvInkSoft.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
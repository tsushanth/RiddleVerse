
package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Data classes for daily system
data class DailyData(
    val lastOpenedDay: String,
    val currentStreak: Int,
    val coins: Int,
    val totalDaysPlayed: Int,
    val bestStreak: Int
)

data class StreakReward(
    val coins: Int,
    val streakDay: Int,
    val specialReward: String? = null
)

// Daily system manager
object DailyStreakManager {
    private const val PREFS_NAME = "daily_streak_data"
    private const val LAST_OPENED_KEY = "last_opened_day"
    private const val CURRENT_STREAK_KEY = "current_streak"
    private const val COINS_KEY = "total_coins"
    private const val TOTAL_DAYS_KEY = "total_days_played"
    private const val BEST_STREAK_KEY = "best_streak"
    private const val TAG = "DailyStreakManager"

    private fun getTodayString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getYesterdayString(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    fun getDailyData(context: Context): DailyData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DailyData(
            lastOpenedDay = prefs.getString(LAST_OPENED_KEY, "") ?: "",
            currentStreak = prefs.getInt(CURRENT_STREAK_KEY, 0),
            coins = prefs.getInt(COINS_KEY, 0),
            totalDaysPlayed = prefs.getInt(TOTAL_DAYS_KEY, 0),
            bestStreak = prefs.getInt(BEST_STREAK_KEY, 0)
        )
    }

    private fun saveDailyData(context: Context, data: DailyData) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(LAST_OPENED_KEY, data.lastOpenedDay)
            .putInt(CURRENT_STREAK_KEY, data.currentStreak)
            .putInt(COINS_KEY, data.coins)
            .putInt(TOTAL_DAYS_KEY, data.totalDaysPlayed)
            .putInt(BEST_STREAK_KEY, data.bestStreak)
            .apply()

        Log.d(TAG, "💾 Saved daily data: $data")
    }

    fun calculateStreakReward(streakDay: Int): StreakReward {
        val baseCoins = when {
            streakDay <= 3 -> 10
            streakDay <= 7 -> 15
            streakDay <= 14 -> 20
            streakDay <= 30 -> 25
            else -> 30
        }

        // Bonus coins for milestones
        val bonusCoins = when (streakDay) {
            7 -> 50      // Week bonus
            14 -> 100    // 2 week bonus
            30 -> 200    // Month bonus
            50 -> 300    // 50 day bonus
            100 -> 500   // 100 day bonus
            else -> 0
        }

        val specialReward = when (streakDay) {
            7 -> "🎯 Week Warrior!"
            14 -> "🔥 Two Week Champion!"
            30 -> "👑 Monthly Master!"
            50 -> "🏆 Dedication Hero!"
            100 -> "💎 Century Legend!"
            else -> null
        }

        return StreakReward(
            coins = baseCoins + bonusCoins,
            streakDay = streakDay,
            specialReward = specialReward
        )
    }

    /**
     * Main function called on app open
     * Returns: Pair<Boolean, StreakReward?> (shouldResetGroups, streakReward)
     */
    fun processAppOpen(context: Context): Pair<Boolean, StreakReward?> {
        val currentData = getDailyData(context)
        val today = getTodayString()
        val yesterday = getYesterdayString()

        Log.d(TAG, "📅 Processing app open:")
        Log.d(TAG, "  Today: $today")
        Log.d(TAG, "  Yesterday: $yesterday")
        Log.d(TAG, "  Last opened: ${currentData.lastOpenedDay}")
        Log.d(TAG, "  Current streak: ${currentData.currentStreak}")

        return when {
            // First time opening or same day - no reset needed
            currentData.lastOpenedDay == today -> {
                Log.d(TAG, "📱 Same day open - no changes needed")
                Pair(false, null)
            }

            // Opened yesterday - continue streak
            currentData.lastOpenedDay == yesterday -> {
                val newStreak = currentData.currentStreak + 1
                val newBestStreak = maxOf(currentData.bestStreak, newStreak)
                val streakReward = calculateStreakReward(newStreak)

                val updatedData = currentData.copy(
                    lastOpenedDay = today,
                    currentStreak = newStreak,
                    coins = currentData.coins + streakReward.coins,
                    totalDaysPlayed = currentData.totalDaysPlayed + 1,
                    bestStreak = newBestStreak
                )

                saveDailyData(context, updatedData)

                Log.d(TAG, "🔥 Streak continued!")
                Log.d(TAG, "  New streak: $newStreak")
                Log.d(TAG, "  Reward: ${streakReward.coins} coins")
                Log.d(TAG, "  Special: ${streakReward.specialReward}")

                // Reset groups for new day and give streak reward
                Pair(true, streakReward)
            }

            // Missed day(s) - reset streak but count as new day
            else -> {
                val streakReward = if (currentData.lastOpenedDay.isEmpty()) {
                    // First time user
                    calculateStreakReward(1)
                } else {
                    // Returning user who missed days - reset streak to 1
                    calculateStreakReward(1)
                }

                val updatedData = currentData.copy(
                    lastOpenedDay = today,
                    currentStreak = 1, // Reset to 1 (today counts)
                    coins = currentData.coins + streakReward.coins,
                    totalDaysPlayed = currentData.totalDaysPlayed + 1
                )

                saveDailyData(context, updatedData)

                if (currentData.lastOpenedDay.isEmpty()) {
                    Log.d(TAG, "🎉 Welcome new user!")
                } else {
                    Log.d(TAG, "💔 Streak broken - resetting to day 1")
                    Log.d(TAG, "  Previous streak was: ${currentData.currentStreak}")
                }
                Log.d(TAG, "  New day reward: ${streakReward.coins} coins")

                // Reset groups for new day and give day 1 reward
                Pair(true, streakReward)
            }
        }
    }

    fun addCoins(context: Context, amount: Int) {
        val currentData = getDailyData(context)
        val updatedData = currentData.copy(coins = currentData.coins + amount)
        saveDailyData(context, updatedData)
        Log.d(TAG, "💰 Added $amount coins. Total: ${updatedData.coins}")
    }

    fun spendCoins(context: Context, amount: Int): Boolean {
        val currentData = getDailyData(context)
        return if (currentData.coins >= amount) {
            val updatedData = currentData.copy(coins = currentData.coins - amount)
            saveDailyData(context, updatedData)
            Log.d(TAG, "💸 Spent $amount coins. Remaining: ${updatedData.coins}")
            true
        } else {
            Log.d(TAG, "❌ Not enough coins. Have: ${currentData.coins}, Need: $amount")
            false
        }
    }
}

// Composable for the gift opening animation with streak celebration
@Composable
fun DailyStreakGiftDialog(
    streakReward: StreakReward,
    onDismiss: () -> Unit
) {
    var giftOpened by remember { mutableStateOf(false) }
    var showReward by remember { mutableStateOf(false) }
    var showStreakCelebration by remember { mutableStateOf(false) }
    var showCloseButton by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // Calculate previous streak (current reward streak - 1, but minimum 0)
    val previousStreak = maxOf(0, streakReward.streakDay - 1)
    val newStreak = streakReward.streakDay

    // ✅ UPDATED: Extended timing and close button logic
    LaunchedEffect(showReward) {
        if (showReward) {
            // Show streak celebration first
            delay(500)
            showStreakCelebration = true
            delay(3000) // Show streak celebration for 3 seconds
            showCloseButton = true // Show close button after full animation
            // No auto-dismiss - user controls when to close
        }
    }

    // Gift opening animation
    val giftScale by animateFloatAsState(
        targetValue = if (giftOpened) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "gift_scale"
    )

    val giftRotation by animateFloatAsState(
        targetValue = if (giftOpened) 360f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "gift_rotation"
    )

    // Coin animation
    val coinScale by animateFloatAsState(
        targetValue = if (showReward) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "coin_scale"
    )

    // Streak number animation
    val streakScale by animateFloatAsState(
        targetValue = if (showStreakCelebration) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "streak_scale"
    )

    val streakAlpha by animateFloatAsState(
        targetValue = if (showStreakCelebration) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "streak_alpha"
    )

    // Close button animation
    val closeButtonScale by animateFloatAsState(
        targetValue = if (showCloseButton) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "close_button_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RvScrim)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // ✅ NEW: Close button in top-right corner
        if (showCloseButton) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier
                        .scale(closeButtonScale)
                        .size(48.dp)
                        .background(
                            RvSurfaceRaised,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = RvInkSoft,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = RvCanvas
            ),
            border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = if (!giftOpened) "Daily Reward!" else "Congratulations! 🎉",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = com.kreativekoala.riddleverse.ui.theme.RvInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ✅ NEW: Show current and new streak info
                if (!showReward) {
                    // Before opening gift - show current streak
                    if (previousStreak > 0) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = com.kreativekoala.riddleverse.ui.theme.RvSun.copy(alpha = 0.18f)
                            ),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🔥",
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Current streak: $previousStreak days",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = com.kreativekoala.riddleverse.ui.theme.RvSunEdge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = if (!giftOpened) "Tap the gift to open your reward!" else "Opening...",
                        fontSize = 16.sp,
                        color = RvInkSoft,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Here's your reward for day $newStreak!",
                        fontSize = 16.sp,
                        color = RvInkSoft,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Gift Box (before opening)
                if (!showReward) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(giftScale),
                        contentAlignment = Alignment.Center
                    ) {
                        // Gift box background
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(
                                    brush = Brush.verticalGradient(colors = listOf(RvCoral, RvCoral)),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 3.dp,
                                    color = RvSun,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        )

                        // Gift ribbon (horizontal)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .background(
                                    RvSun,
                                    RoundedCornerShape(10.dp)
                                )
                        )

                        // Gift ribbon (vertical)
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .fillMaxHeight()
                                .background(
                                    RvSun,
                                    RoundedCornerShape(10.dp)
                                )
                        )

                        // Bow
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = "Gift",
                            tint = RvSun,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Reward display (after opening)
                AnimatedVisibility(
                    visible = showReward,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ✅ NEW: Animated streak celebration
                        if (showStreakCelebration) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background celebration effect
                                repeat(8) { index ->
                                    val angle = (index * 45f)
                                    val distance = 40.dp

                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .offset(
                                                x = (distance.value * kotlin.math.cos(Math.toRadians(angle.toDouble()))).dp,
                                                y = (distance.value * kotlin.math.sin(Math.toRadians(angle.toDouble()))).dp
                                            )
                                            .scale(streakScale)
                                            .background(
                                                RvSun,
                                                CircleShape
                                            )
                                    )
                                }

                                // Main streak number
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = RvFlame
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .scale(streakScale)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "🔥",
                                                fontSize = 20.sp
                                            )
                                            Text(
                                                text = "$newStreak",
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = RvOnTone
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (newStreak == 1) {
                                    "Day 1 🎯"
                                } else {
                                    "$newStreak Day Streak! 🔥"
                                },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvFlame,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Coins animation
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = "Coins",
                                tint = RvSun,
                                modifier = Modifier
                                    .size(48.dp)
                                    .scale(coinScale)
                            )

                            Text(
                                text = "+${streakReward.coins}",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = RvSunEdge
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Special reward message
                        streakReward.specialReward?.let { specialReward ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = RvGrape
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = specialReward,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RvOnTone,
                                    modifier = Modifier.padding(16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Streak encouragement message
                        Text(
                            text = when (newStreak) {
                                1 -> "Welcome back! Start your streak journey! 🚀"
                                2 -> "Great job! You're building a habit! 💪"
                                3 -> "Three days strong! Keep it up! 🎯"
                                7 -> "One week complete! You're on fire! 🔥"
                                14 -> "Two weeks! Incredible dedication! 🏆"
                                30 -> "One month! You're a champion! 👑"
                                in 2..6 -> "Keep the momentum going! 🌟"
                                in 8..13 -> "You're building an amazing streak! ⭐"
                                in 15..29 -> "Outstanding consistency! 💎"
                                else -> "Legendary streak! You're unstoppable! 🚀"
                            },
                            fontSize = 14.sp,
                            color = RvInkSoft,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action button - only show when streak celebration is complete
                if (showCloseButton) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .scale(closeButtonScale),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RvSuccess
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = "Let's Play! 🎮",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvOnTone
                        )
                    }
                } else if (!giftOpened) {
                    Text(
                        text = "👆 Tap the gift above!",
                        fontSize = 14.sp,
                        color = RvGrape,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                RvGrape.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
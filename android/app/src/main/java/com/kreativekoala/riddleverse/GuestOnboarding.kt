package com.kreativekoala.riddleverse

import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.riddleverse.ui.theme.RvGrape
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvViolet

/**
 * The 4-page intro (WelcomeOnboardingScreen) is no longer a gate. It is offered as a dismissible card on Home and
 * opens full screen only if tapped. Anyone who already finished either old onboarding never sees the card.
 */
object OnboardingGate {
    private fun appPrefs(ctx: Context) = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun shouldOffer(ctx: Context): Boolean = GuestRules.shouldOfferOnboardingCard(
        appPrefsCompleted = appPrefs(ctx).getBoolean("onboarding_completed", false),
        tutorialPrefsCompleted = TutorialPreferences(ctx).hasCompletedOnboarding(),
        cardDismissed = GuestPrefs.isOnboardingCardDismissed(ctx)
    )

    /** Seen, skipped or dismissed: never offer again. */
    fun markDone(ctx: Context, skipped: Boolean) {
        appPrefs(ctx).edit().putBoolean("onboarding_completed", true).putBoolean("onboarding_skipped", skipped).apply()
        TutorialPreferences(ctx).markOnboardingCompleted()
        GuestPrefs.dismissOnboardingCard(ctx)
    }
}

/** Card + full-screen tour. Place it once under the Home header; it renders nothing when not needed. */
@Composable
fun OnboardingOffer() {
    val context = LocalContext.current
    var offered by remember { mutableStateOf(OnboardingGate.shouldOffer(context)) }
    var showTour by remember { mutableStateOf(false) }

    LaunchedEffect(offered) { if (offered) Funnel.log(FunnelStep.ONBOARDING_CARD_SHOWN) }

    if (offered && !showTour) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RvViolet.copy(alpha = 0.15f))
                .clickable {
                    Funnel.log(FunnelStep.ONBOARDING_OPENED)
                    showTour = true
                }
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(stringResource(R.string.guest_tour_title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RvGrape)
                Text(stringResource(R.string.guest_tour_subtitle), fontSize = 12.sp, color = RvInkSoft)
            }
            IconButton(
                onClick = {
                    Funnel.log(FunnelStep.ONBOARDING_CARD_DISMISSED)
                    OnboardingGate.markDone(context, skipped = true)
                    offered = false
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.guest_tour_dismiss), tint = RvInkSoft, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showTour) {
        BackHandler {
            Funnel.log(FunnelStep.ONBOARDING_SKIPPED)
            OnboardingGate.markDone(context, skipped = true)
            showTour = false; offered = false
        }
        Box(modifier = Modifier.fillMaxSize()) {
            WelcomeOnboardingScreen(
                onComplete = {
                    Funnel.log(FunnelStep.ONBOARDING_COMPLETED)
                    OnboardingGate.markDone(context, skipped = false)
                    showTour = false; offered = false
                },
                onSkip = {
                    Funnel.log(FunnelStep.ONBOARDING_SKIPPED)
                    OnboardingGate.markDone(context, skipped = true)
                    showTour = false; offered = false
                }
            )
        }
    }
}

/**
 * Gentle account nudge shown over the completion screen after a guest finishes a puzzle session.
 * "Not now" is the primary dismissal; "Don't ask again" stops all future nudges.
 */
@Composable
fun GuestSignupPrompt(onCreateAccount: () -> Unit, onNotNow: () -> Unit, onNeverAgain: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.guest_save_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.guest_save_body),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            Button(
                onClick = onCreateAccount,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RvViolet)
            ) { Text(stringResource(R.string.guest_save_yes)) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onNotNow, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.guest_save_not_now)) }
                TextButton(onClick = onNeverAgain, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.guest_save_never)) }
            }
        }
    }
}

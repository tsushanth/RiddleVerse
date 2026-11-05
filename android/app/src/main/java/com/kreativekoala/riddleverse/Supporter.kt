package com.kreativekoala.riddleverse

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kreativekoala.riddleverse.ui.theme.*
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import okhttp3.Request
import org.json.JSONObject

// --- optional "Supporter" extra --- ONE conservative paid option, OFF until the owner creates the product.
//
// What it is: a one-time purchase for people who like the daily puzzle. It takes nothing away from anyone and never
// blocks play. Extras: hold up to 5 streak freezes (instead of 2) and earn one every 5 streak days (instead of 7).
//
// Owner must create (Play Console + RevenueCat; this code never creates or changes billing products or prices):
//   Play product id (non-consumable, one-time):  com.kreativekoala.riddleverse.supporter
//   RevenueCat entitlement identifier:           supporter
//   RevenueCat offering identifier:              supporter   (one package attached to the product above)
// Then flip the remote flag: web/frontend/public/app-flags.json  {"supporter_offer": true}  (served from puzzleverseai.com).
//
// Never shown: while the flag is off or unreachable, while the offering has no loaded package (store/billing not ready),
// to guests, to existing supporters, or more than [MAX_SHOWS] times. It is a small dismissible card on the daily result
// screen, after the win, and nowhere else. Purchases do NOT go through the Premium backend-credit path.

/** Pure gating rules, JVM-testable. */
object SupporterRules {
    const val MAX_SHOWS = 3
    const val MIN_DAYS_BETWEEN_SHOWS = 14
    const val FLAG_TTL_MS = 12 * 3600 * 1000L

    fun shouldShow(
        flagOn: Boolean,
        packageLoaded: Boolean,
        hasAccount: Boolean,
        isSupporter: Boolean,
        solvedToday: Boolean,
        shows: Int,
        lastShownDay: Long?,
        today: Long,
        dismissedForever: Boolean,
    ): Boolean =
        flagOn && packageLoaded && hasAccount && !isSupporter && solvedToday && !dismissedForever &&
            shows < MAX_SHOWS && (lastShownDay == null || today - lastShownDay >= MIN_DAYS_BETWEEN_SHOWS)

    /** Parses the flag file; anything unexpected means OFF. */
    fun parseFlag(json: String?): Boolean = try {
        json != null && JSONObject(json).optBoolean("supporter_offer", false)
    } catch (_: Throwable) { false }
}

object Supporter {
    const val PRODUCT_ID = "com.kreativekoala.riddleverse.supporter"
    const val ENTITLEMENT = "supporter"
    const val OFFERING = "supporter"
    private const val FLAG_URL = "https://puzzleverseai.com/app-flags.json"
    private const val PREFS = "supporter_v1"
    private const val K_IS = "is_supporter"
    private const val K_FLAG = "flag_on"
    private const val K_FLAG_AT = "flag_at"
    private const val K_SHOWS = "shows"
    private const val K_LAST_SHOWN = "last_shown_day"
    private const val K_DISMISSED = "dismissed"

    private fun prefs(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Cached entitlement (works offline); refreshed from RevenueCat by [refresh]. */
    fun isSupporter(c: Context): Boolean = prefs(c).getBoolean(K_IS, false)

    /** Last known remote flag value (default OFF). */
    fun flagOn(c: Context): Boolean = prefs(c).getBoolean(K_FLAG, false)

    /** Refreshes the cached flag (at most every 12h) and the entitlement. Background thread only. Never throws. */
    fun refresh(c: Context) {
        try {
            val p = prefs(c)
            if (System.currentTimeMillis() - p.getLong(K_FLAG_AT, 0L) > SupporterRules.FLAG_TTL_MS) {
                val body = try {
                    HttpClientProvider.client.newCall(Request.Builder().url(FLAG_URL).build()).execute().use {
                        if (it.isSuccessful) it.body?.string() else null
                    }
                } catch (_: Throwable) { null }
                // Failure keeps the last known value; the timestamp moves so a dead endpoint is not hammered.
                if (body != null) p.edit().putBoolean(K_FLAG, SupporterRules.parseFlag(body)).apply()
                p.edit().putLong(K_FLAG_AT, System.currentTimeMillis()).apply()
            }
            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) { apply(c, customerInfo) }
                override fun onError(error: PurchasesError) {}
            })
        } catch (_: Throwable) { }
    }

    private fun apply(c: Context, info: CustomerInfo) {
        prefs(c).edit().putBoolean(K_IS, info.entitlements[ENTITLEMENT]?.isActive == true).apply()
    }

    fun hasAccount(): Boolean = !isGuestProfile(FirebaseAuth.getInstance().currentUser)

    fun shouldShow(c: Context, packageLoaded: Boolean): Boolean {
        val p = prefs(c)
        val last = p.getLong(K_LAST_SHOWN, -1L).takeIf { it >= 0 }
        val today = DailyPuzzleStore.today()
        return SupporterRules.shouldShow(
            flagOn = flagOn(c), packageLoaded = packageLoaded, hasAccount = hasAccount(), isSupporter = isSupporter(c),
            solvedToday = DailyPuzzleStore.resultFor(c, today) != null,
            shows = p.getInt(K_SHOWS, 0), lastShownDay = last, today = today, dismissedForever = p.getBoolean(K_DISMISSED, false),
        )
    }

    fun recordShown(c: Context) {
        val p = prefs(c)
        p.edit().putInt(K_SHOWS, p.getInt(K_SHOWS, 0) + 1).putLong(K_LAST_SHOWN, DailyPuzzleStore.today()).apply()
    }

    fun dismissForever(c: Context) { prefs(c).edit().putBoolean(K_DISMISSED, true).apply() }

    /** Loads the supporter package; `null` when the offering/product does not exist (card then stays hidden). */
    fun loadPackage(onResult: (Package?) -> Unit) {
        try {
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: Offerings) { onResult(offerings.getOffering(OFFERING)?.availablePackages?.firstOrNull()) }
                override fun onError(error: PurchasesError) { onResult(null) }
            })
        } catch (_: Throwable) { onResult(null) }
    }

    fun purchase(activity: Activity, pkg: Package, onDone: (Boolean) -> Unit) {
        try {
            Purchases.sharedInstance.purchase(PurchaseParams.Builder(activity, pkg).build(), object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    apply(activity, customerInfo); onDone(isSupporter(activity))
                }
                override fun onError(error: PurchasesError, userCancelled: Boolean) { onDone(false) }
            })
        } catch (_: Throwable) { onDone(false) }
    }
}

/** Small, dismissible card under the daily result. Renders nothing unless every rule in [SupporterRules.shouldShow] passes. */
@Composable
fun SupporterOfferCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pkg by remember { mutableStateOf<Package?>(null) }
    var gone by remember { mutableStateOf(false) }
    var thanks by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Only ask the store when the flag is already on, so nothing happens while the feature is off.
        if (Supporter.flagOn(context) && !Supporter.isSupporter(context)) Supporter.loadPackage { pkg = it }
    }
    if (thanks) {
        Text("Thank you for supporting RiddleVerse.", fontSize = 13.sp, color = RvInkSoft, modifier = modifier.fillMaxWidth())
        return
    }
    val offered = pkg
    if (gone || offered == null || !Supporter.shouldShow(context, packageLoaded = true)) return
    LaunchedEffect(offered) { Supporter.recordShown(context); Funnel.log("supporter_offer_shown") }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
        border = BorderStroke(2.dp, RvOutline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Like the daily puzzle?", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = RvInk)
            Text(
                "Become a supporter with a one-time purchase (${offered.product.price.formatted}). You keep playing everything for free either way. " +
                    "As a thank-you, you can hold up to ${DailyStreakRules.SUPPORTER_MAX_FREEZES} streak freezes and earn one every ${DailyStreakRules.SUPPORTER_FREEZE_EVERY} days.",
                fontSize = 13.sp, color = RvInkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(RvSun).clickable {
                        (context as? Activity)?.let { a -> Supporter.purchase(a, offered) { ok -> if (ok) { Funnel.log("supporter_purchased"); thanks = true } } }
                    }.padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("Become a supporter", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = RvOnTone) }
                Box(Modifier.clip(RoundedCornerShape(50)).clickable { Supporter.dismissForever(context); gone = true }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("No thanks", fontSize = 14.sp, color = RvInkSoft)
                }
            }
        }
    }
}

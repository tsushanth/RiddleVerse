package com.kreativekoala.riddleverse

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * RatingKit is the only automatic rating prompt. These tests guard the pieces that replaced the old
 * custom star dialog: the plain Play listing link and the absence of incentivised-rating copy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RatingSingleSystemTest {

    private val removedStringNames = listOf("rate_incentive", "rate_for_early_access")

    @Test
    fun listingUrls_pointAtThisPackage() {
        assertEquals("market://details?id=com.example.app", PlayListing.marketUrl("com.example.app"))
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.example.app",
            PlayListing.webUrl("com.example.app")
        )
    }

    @Test
    fun open_usesMarketFirst() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val started = mutableListOf<Intent>()
        val ctx = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { started += intent }
        }

        assertTrue(PlayListing.open(ctx))
        assertEquals(1, started.size)
        assertEquals("market://details?id=${app.packageName}", started[0].dataString)
        assertEquals(Intent.ACTION_VIEW, started[0].action)
    }

    @Test
    fun open_fallsBackToWebWhenPlayStoreAppIsMissing() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val started = mutableListOf<String?>()
        val ctx = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) {
                if (intent.dataString!!.startsWith("market://")) throw ActivityNotFoundException()
                started += intent.dataString
            }
        }

        assertTrue(PlayListing.open(ctx))
        assertEquals(listOf("https://play.google.com/store/apps/details?id=${app.packageName}"), started)
    }

    @Test
    fun open_returnsFalseWhenNothingCanHandleIt() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val ctx: Context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        assertFalse(PlayListing.open(ctx))
    }

    @Test
    fun noLocaleShipsIncentivisedRatingStrings() {
        val resDir = File("src/main/res")
        val files = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
            .map { File(it, "strings.xml") }.filter { it.exists() }
        assertTrue("expected the default and translated locales", files.size >= 20)

        val incentive = Regex("coins?|reward|early access", RegexOption.IGNORE_CASE)
        val stringTag = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        for (f in files) {
            for (m in stringTag.findAll(f.readText())) {
                val (name, value) = m.destructured
                assertFalse("${f.parentFile.name} still defines $name", name in removedStringNames)
                val ratingRelated = Regex("rat(e|ing)|review", RegexOption.IGNORE_CASE).containsMatchIn(name)
                if (ratingRelated) {
                    assertFalse("${f.parentFile.name}/$name mentions an incentive: $value", incentive.containsMatchIn(value))
                }
            }
        }
    }

    @Test
    fun sourceHasNoCustomRatingPromptOrIncentiveCopy() {
        val src = File("src/main/java")
        val kt = src.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(kt.isNotEmpty())
        val banned = listOf("AppRatingManager", "RatingFlowDialog", "R.string.rate_incentive", "rate_for_early_access")
        for (f in kt) {
            val text = f.readText()
            for (b in banned) assertFalse("${f.name} references $b", text.contains(b))
            assertFalse("${f.name} offers rewards for rating", text.contains("Rate us and get", ignoreCase = true))
        }
    }
}

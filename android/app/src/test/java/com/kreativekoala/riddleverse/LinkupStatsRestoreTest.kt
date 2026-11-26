package com.kreativekoala.riddleverse

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

/** Recreating the screen on the result card (rotation, process restore) must not count the same game again. */
class LinkupStatsRestoreTest : LayoutMatrixBase() {

    @Test @Config(qualifiers = Devices.TYPICAL_PHONE)
    fun restoredResultCardIsCountedOnce() {
        val puzzle = LinkupPuzzle("test", (1..4).map { tier -> LinkupGroup(tier, "Group $tier", (1..4).map { "W$tier$it" }) })
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LinkupScreen(puzzle.toJson(1L, "Medium").toString(), "1/1", {}, { _, _ -> }) }
        val prefs = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("word_puzzle_stats", Context.MODE_PRIVATE)

        compose.onNodeWithContentDescription("Give up and reveal the groups").performClick()
        compose.waitForIdle()
        assertEquals(1, prefs.getInt("linkup_played", 0))
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(1, prefs.getInt("linkup_played", 0))
    }
}

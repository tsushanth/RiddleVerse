package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinksTest {
    @Test fun builtInPuzzle_linksToItsPlayablePage_withShareTags() {
        assertEquals(
            "https://puzzleverseai.com/play/sudoku?src=share&utm_source=telegram&utm_medium=app_share&utm_campaign=sudoku",
            ShareLinks.playUrl("sudoku", ShareLinks.CHANNEL_TELEGRAM),
        )
    }

    @Test fun customGame_usesItsIdAndTheCustomCampaign() {
        val id = "3f2b1c9e-7a44-4d1e-9a31-0c5d2b8e6f10"
        assertEquals("https://puzzleverseai.com/play/$id?src=share&utm_source=whatsapp&utm_medium=app_share&utm_campaign=custom", ShareLinks.playUrl(id, ShareLinks.CHANNEL_WHATSAPP))
    }

    @Test fun unknownOrUnsafeTargets_fallBackToTheGameList_neverIntoThePath() {
        for (bad in listOf(null, "", "  ", "../admin", "sudoku/../../x", "a b", "<script>", "x".repeat(40), "not-a-uuid-123")) {
            val url = ShareLinks.playUrl(bad, ShareLinks.CHANNEL_OTHER)
            assertTrue(bad.toString(), url.startsWith("https://puzzleverseai.com/play?src=share&"))
            assertTrue(url.endsWith("utm_campaign=index"))
        }
    }

    @Test fun badChannelOrMedium_isReplacedByOther() {
        val url = ShareLinks.playUrl("sudoku", "Tele gram&x=1", "A B")
        assertTrue(url, url.contains("utm_source=other&utm_medium=app_share"))
        assertTrue(!url.contains(" ") && !url.contains("x=1"))
    }

    @Test fun botLink_acceptsSafeStartPayloadsOnly() {
        assertEquals("https://t.me/Riddleverse_bot", ShareLinks.botUrl())
        assertEquals("https://t.me/Riddleverse_bot?start=g_sudoku", ShareLinks.botUrl("g_sudoku"))
        assertEquals("https://t.me/Riddleverse_bot?start=link", ShareLinks.botUrl("link"))
        assertEquals("https://t.me/Riddleverse_bot", ShareLinks.botUrl("a b&c=d"))
        assertEquals("https://t.me/Riddleverse_bot", ShareLinks.botUrl("x".repeat(65)))
    }

    @Test fun eventTarget_reportsOnlyBuiltInPuzzleTypes() {
        assertEquals("sudoku", ShareLinks.eventTarget("sudoku"))
        assertEquals(null, ShareLinks.eventTarget("3f2b1c9e-7a44-4d1e-9a31-0c5d2b8e6f10"))
        assertEquals(null, ShareLinks.eventTarget(null))
    }
}

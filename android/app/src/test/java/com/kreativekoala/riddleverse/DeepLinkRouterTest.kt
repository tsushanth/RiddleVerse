package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkRouterTest {
    private fun parse(url: String): DeepLink? {
        // A tiny URL splitter so the test needs no Android Uri.
        val m = Regex("^([a-z]+)://([^/?#]*)([^?#]*)(?:\\?([^#]*))?").find(url) ?: return null
        val q = (m.groupValues[4]).split("&").filter { it.contains("=") }.associate { it.substringBefore("=") to it.substringAfter("=") }
        val segs = m.groupValues[3].split("/").filter { it.isNotEmpty() }
        return DeepLinkRouter.parse(m.groupValues[1], m.groupValues[2], segs) { q[it] }
    }

    private val code = "A".repeat(16) + "b".repeat(15) + "-"
    private val gameId = "3f2b1c9e-7a44-4d1e-9a31-0c5d2b8e6f10"

    @Test fun sharedPuzzlePage_opensThatPuzzle_andKeepsTheSource() {
        assertEquals(DeepLink.PlaySlug("sudoku", "share"), parse("https://puzzleverseai.com/play/sudoku?src=share&utm_source=telegram"))
        assertEquals(DeepLink.PlaySlug("daily", null), parse("https://puzzleverseai.com/play/daily"))
        assertEquals(DeepLink.PlayIndex("telegram"), parse("https://puzzleverseai.com/play?src=telegram"))
    }

    @Test fun sharedCustomGame_opensTheGame() {
        assertEquals(DeepLink.Game(gameId, "share"), parse("https://puzzleverseai.com/play/$gameId?src=share"))
        assertEquals(DeepLink.Game(gameId, null), parse("riddleverse://game/$gameId"))
        assertEquals(DeepLink.Game("abc", null), parse("riddleverse://game?gameId=abc"))
        assertNull(parse("riddleverse://game"))
    }

    @Test fun telegramCode_onlyWhenItLooksLikeOne_viaBothSchemes() {
        assertEquals(DeepLink.TelegramLink(code), parse("riddleverse://auth?code=$code"))
        assertEquals(DeepLink.TelegramLink(code), parse("https://puzzleverseai.com/app/link?code=$code&src=telegram"))
        assertNull(parse("riddleverse://auth?code=short"))
        assertNull(parse("riddleverse://auth?token=eyJhbGciOi"))   // the old raw-token form is gone
        assertNull(parse("https://puzzleverseai.com/app/link"))
        assertNull(parse("https://puzzleverseai.com/app/other?code=$code"))
    }

    @Test fun legacyRiddleLink_stillRecognised() {
        assertEquals(DeepLink.Riddle("p1"), parse("https://puzzleverseai.com/open-riddle?puzzleId=p1"))
        assertNull(parse("https://puzzleverseai.com/open-riddle"))
    }

    @Test fun otherHostsPathsAndWebSessionPages_areNotAppLinks() {
        assertNull(parse("https://evil.example/play/sudoku"))
        assertNull(parse("https://puzzleverseai.com/privacy"))
        assertNull(parse("https://puzzleverseai.com/play?session_id=abc"))
        assertNull(parse("https://puzzleverseai.com/play/NOT-A-SLUG-123"))
        assertNull(parse("http://puzzleverseai.com/play/sudoku"))
        assertNull(parse("riddleverse://unknown/x"))
    }

    @Test fun srcLabelsAreWhitelisted_andEventsUseThem() {
        assertEquals(DeepLink.PlaySlug("sudoku", null), parse("https://puzzleverseai.com/play/sudoku?src=Bad%20Label"))
        assertEquals("share", DeepLinkRouter.channelOf(DeepLink.PlaySlug("sudoku", "share")))
        assertEquals("link", DeepLinkRouter.channelOf(DeepLink.PlaySlug("sudoku", null)))
        assertEquals("telegram_link", DeepLinkRouter.channelOf(DeepLink.TelegramLink(code)))
        assertEquals("sudoku", DeepLinkRouter.targetOf(DeepLink.PlaySlug("sudoku", null)))
        assertEquals("app", DeepLinkRouter.targetOf(DeepLink.Game(gameId, null)))
    }
}

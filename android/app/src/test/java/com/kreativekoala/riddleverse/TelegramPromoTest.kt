package com.kreativekoala.riddleverse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPromoTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    @Test fun banner_showsForNewUsers() = assertTrue(TelegramPromo.bannerVisible(now, 0, 0))

    @Test fun banner_hiddenAfterDismissal_returnsAfterTheReshowWindow() {
        assertFalse(TelegramPromo.bannerVisible(now, now - 1 * day, 0))
        assertFalse(TelegramPromo.bannerVisible(now, now - (TelegramPromo.BANNER_RESHOW_DAYS - 1) * day, 0))
        assertTrue(TelegramPromo.bannerVisible(now, now - TelegramPromo.BANNER_RESHOW_DAYS * day, 0))
    }

    @Test fun opening_theBot_silencesPromosForAWhile_thenTheyReturn() {
        assertFalse(TelegramPromo.bannerVisible(now, 0, now - 10 * day))
        assertFalse(TelegramPromo.cardVisible(now, now - 10 * day))
        assertTrue(TelegramPromo.cardVisible(now, now - TelegramPromo.QUIET_AFTER_OPEN_DAYS * day))
        assertTrue(TelegramPromo.cardVisible(now, 0))
        assertTrue(TelegramPromo.bannerVisible(now, 0, now - TelegramPromo.QUIET_AFTER_OPEN_DAYS * day))
    }

    @Test fun botHandle_isTheLiveOne() = org.junit.Assert.assertEquals("Riddleverse_bot", TelegramPromo.BOT_USERNAME)
}

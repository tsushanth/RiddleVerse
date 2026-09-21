package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoinCostTest {

    @Test fun difficultyCost_perLevel() {
        assertEquals(10, CoinManager.difficultyCost(1))
        assertEquals(15, CoinManager.difficultyCost(2))
        assertEquals(20, CoinManager.difficultyCost(3))
        assertEquals(25, CoinManager.difficultyCost(4))
        assertEquals(30, CoinManager.difficultyCost(5))
    }

    @Test fun difficultyCost_outOfRangeFallsBackToBase() {
        assertEquals(10, CoinManager.difficultyCost(0))
        assertEquals(10, CoinManager.difficultyCost(-1))
        assertEquals(10, CoinManager.difficultyCost(CoinManager.MAX_DIFFICULTY_LEVEL + 1))
    }

    @Test fun difficultyCost_increasesUpToMax() {
        for (level in 1 until CoinManager.MAX_DIFFICULTY_LEVEL) {
            assertTrue(CoinManager.difficultyCost(level + 1) > CoinManager.difficultyCost(level))
        }
    }

    @Test fun coinPacks_areWellFormed() {
        val packs = CoinManager.COIN_PACKS
        assertEquals(packs.size, packs.map { it.productId }.toSet().size)
        packs.forEach { assertTrue(it.coins > 0) }
        // Product id embeds the coin amount; catches a pack whose id and grant drift apart.
        packs.forEach { assertTrue(it.productId.endsWith(".${it.coins}")) }
    }
}

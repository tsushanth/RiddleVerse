package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlayPurchaseSyncTest {
    @Test fun onlyAFinalAnswerStopsTheRetries() {
        assertEquals(PurchaseReport.DONE, PurchaseReportRules.outcome(200, true, null))
        assertEquals(PurchaseReport.REJECTED, PurchaseReportRules.outcome(400, false, false))
        assertEquals(PurchaseReport.REJECTED, PurchaseReportRules.outcome(409, false, false))
        // pending payment, signed out, verification unavailable, rate limit, server errors, an old server
        for ((code, retryable) in listOf(202 to true, 401 to null, 403 to null, 404 to null, 409 to true, 429 to true, 500 to true, 503 to true, 400 to null)) {
            assertEquals("$code", PurchaseReport.RETRY, PurchaseReportRules.outcome(code, false, retryable))
        }
    }

    @Test fun aWaitingPurchaseSurvivesStorageAndSendsNoAmounts() {
        val coins = PendingPurchase("token-1", "com.kreativekoala.riddleverse.coins.500", PendingPurchase.COINS, "uid-1", "GPA.1")
        assertEquals(coins, PendingPurchase.fromJson("token-1", coins.toJson()))
        assertEquals("https://puzzleverseai.com/api/coins/purchase", coins.url)
        val body = coins.requestBody()
        assertEquals("token-1", body.getString("purchaseToken"))
        assertEquals("GPA.1", body.getString("transactionId"))
        assertEquals("android", body.getString("platform"))
        val sub = PendingPurchase("token-2", "com.kreativekoala.riddleverse.premium", PendingPurchase.SUBSCRIPTION, "uid-1", null)
        assertEquals(sub, PendingPurchase.fromJson("token-2", sub.toJson()))
        assertEquals("https://puzzleverseai.com/api/subscription-purchase", sub.url)
        assertEquals(setOf("userId", "purchaseToken"), sub.requestBody().keys().asSequence().toSet())
        assertFalse(body.has("coins") || body.has("amount") || body.has("monthlyCoins"))
        assertNull(PendingPurchase.fromJson("token-3", "not json"))
    }
}

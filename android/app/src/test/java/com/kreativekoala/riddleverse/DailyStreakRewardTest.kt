package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyStreakRewardTest {

    @Test fun baseCoins_byStreakBand() {
        assertEquals(10, DailyStreakManager.calculateStreakReward(1).coins)
        assertEquals(10, DailyStreakManager.calculateStreakReward(3).coins)
        assertEquals(15, DailyStreakManager.calculateStreakReward(4).coins)
        assertEquals(20, DailyStreakManager.calculateStreakReward(8).coins)
        assertEquals(25, DailyStreakManager.calculateStreakReward(15).coins)
        assertEquals(30, DailyStreakManager.calculateStreakReward(31).coins)
    }

    @Test fun milestones_addBonusAndMessage() {
        val week = DailyStreakManager.calculateStreakReward(7)
        assertEquals(15 + 50, week.coins)
        assertEquals(7, week.streakDay)
        assertTrue(week.specialReward != null)

        assertEquals(20 + 100, DailyStreakManager.calculateStreakReward(14).coins)
        assertEquals(25 + 200, DailyStreakManager.calculateStreakReward(30).coins)
        assertEquals(30 + 300, DailyStreakManager.calculateStreakReward(50).coins)
        assertEquals(30 + 500, DailyStreakManager.calculateStreakReward(100).coins)
    }

    @Test fun nonMilestoneDays_haveNoSpecialReward() {
        for (day in listOf(1, 2, 6, 8, 13, 29, 31, 99, 101)) {
            assertNull("day $day", DailyStreakManager.calculateStreakReward(day).specialReward)
        }
    }

    @Test fun rewardsNeverDecreaseAcrossNonMilestoneDays() {
        val nonMilestone = (1..120).filter { it !in setOf(7, 14, 30, 50, 100) }
        var previous = 0
        for (day in nonMilestone) {
            val coins = DailyStreakManager.calculateStreakReward(day).coins
            assertTrue("reward dropped at day $day", coins >= previous)
            previous = coins
        }
    }
}

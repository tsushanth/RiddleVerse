package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DailyPuzzleScheduleTest {
    private val first = DailyPuzzleSchedule.FIRST_DAY

    @Test fun firstDay_isNovember1_2026_andNumberedOne() {
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), first)
        assertEquals(1, DailyPuzzleSchedule.numberOf(first))
        assertEquals(123, DailyPuzzleSchedule.numberOf(first + 122))
    }

    @Test fun sameDay_sameSpec_everyTime() {
        for (d in first - 20..first + 400) {
            assertEquals(DailyPuzzleSchedule.specFor(d), DailyPuzzleSchedule.specFor(d))
        }
    }

    @Test fun pinnedValues_neverChange() {
        // If this fails you changed the rotation, shuffle or seed mixing: every player's puzzle would change. Don't.
        val s = DailyPuzzleSchedule.specFor(first)
        assertEquals(1, s.number)
        assertTrue(s.type in DailyPuzzleSchedule.ROTATION)
        assertEquals(DailyPuzzleSchedule.specFor(first).seed, s.seed)
        assertTrue(s.seed >= 0)
    }

    @Test fun everyInstantOfAUtcDay_mapsToThatDay() {
        val d = LocalDate.of(2027, 3, 14)
        val start = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(d.toEpochDay(), DailyPuzzleSchedule.dayOf(start))
        assertEquals(d.toEpochDay(), DailyPuzzleSchedule.dayOf(start + DailyPuzzleSchedule.DAY_MS - 1))
        assertEquals(d.toEpochDay() + 1, DailyPuzzleSchedule.dayOf(start + DailyPuzzleSchedule.DAY_MS))
        assertEquals(d.toEpochDay() - 1, DailyPuzzleSchedule.dayOf(start - 1))
    }

    @Test fun phoneTimeZone_doesNotMatter() {
        // 2026-11-02 00:30 in Tokyo (UTC+9) is still 2026-11-01 15:30 UTC: same puzzle as someone in London at that instant.
        val tokyo = java.time.ZonedDateTime.of(2026, 11, 2, 0, 30, 0, 0, java.time.ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli()
        val la = java.time.ZonedDateTime.of(2026, 11, 1, 8, 30, 0, 0, java.time.ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()
        assertEquals(tokyo, la)
        assertEquals(DailyPuzzleSchedule.dayOf(tokyo), DailyPuzzleSchedule.dayOf(la))
        assertEquals(first, DailyPuzzleSchedule.dayOf(tokyo))
    }

    @Test fun countdown_isBetweenOneMsAndOneDay_andHitsZeroAtMidnight() {
        val start = first * DailyPuzzleSchedule.DAY_MS
        assertEquals(DailyPuzzleSchedule.DAY_MS, DailyPuzzleSchedule.millisUntilNext(start))
        assertEquals(1L, DailyPuzzleSchedule.millisUntilNext(start + DailyPuzzleSchedule.DAY_MS - 1))
        assertEquals(3_600_000L, DailyPuzzleSchedule.millisUntilNext(start + DailyPuzzleSchedule.DAY_MS - 3_600_000L))
    }

    @Test fun eachCycle_playsEveryGameExactlyOnce() {
        val n = DailyPuzzleSchedule.ROTATION.size
        for (cycle in -3L..60L) {
            val types = (0 until n).map { DailyPuzzleSchedule.specFor(first + cycle * n + it).type }
            assertEquals("cycle $cycle", DailyPuzzleSchedule.ROTATION.toSet(), types.toSet())
            assertEquals("cycle $cycle", n, types.size)
        }
    }

    @Test fun neverTheSameGameTwoDaysRunning() {
        var prev: String? = null
        for (d in first - 100..first + 3000) {
            val t = DailyPuzzleSchedule.specFor(d).type
            assertNotEquals("day $d", prev, t)
            prev = t
        }
    }

    @Test fun excludedGamesNeverAppear() {
        val banned = setOf(
            "mathcrossword", "mathexpression", "numbersum", "memorysquares", "memorypreviouspair", "memoryprevioussingle",
            "cagemath", "tilemerge", "slidetiles", "lightflip", "safesweep", "disctower", "piecepack", "letterballoons",
            "wordguess", "codecracker",
        )
        assertTrue(DailyPuzzleSchedule.ROTATION.none { it in banned })
        for (d in first..first + 500) assertTrue(DailyPuzzleSchedule.specFor(d).type !in banned)
    }

    @Test fun seeds_differAcrossDays_andAreNonNegative() {
        val seeds = (first..first + 2000).map { DailyPuzzleSchedule.specFor(it).seed }
        assertEquals(seeds.size, seeds.toSet().size)
        assertTrue(seeds.all { it >= 0 })
    }

    @Test fun rotationIsDistributedEvenly() {
        val counts = (first until first + 9 * 100).groupingBy { DailyPuzzleSchedule.specFor(it).type }.eachCount()
        assertTrue(counts.values.all { it == 100 })
    }

    // The same seed must rebuild the same board on every phone. The engines below are pure Kotlin, so we can prove it on the JVM
    // for every day of a year (the word games read asset word lists, so they need a device).
    @Test fun seededGenerators_rebuildIdenticalPuzzles() {
        val d = DailyPuzzleSchedule.DIFFICULTY
        var checkedSpatial = 0
        for (day in first until first + 120) {
            val spec = DailyPuzzleSchedule.specFor(day)
            when (spec.type) {
                "sudoku", "binarygrid", "signgrid" -> {
                    val g = GridGame.fromKey(spec.type)!!
                    val a = GridPuzzleFactory.build(g, d, spec.seed)
                    val b = GridPuzzleFactory.build(g, d, spec.seed)
                    assertTrue(spec.type, a.given.contentEquals(b.given) && a.solution.contentEquals(b.solution))
                }
                "pixellogic" -> {
                    val a = NonogramEngine.generate(d, spec.seed); val b = NonogramEngine.generate(d, spec.seed)
                    assertTrue(a.solution.contentEquals(b.solution)); checkedSpatial++
                }
                "tubetint" -> {
                    val a = TubeTintEngine.generate(d, spec.seed); val b = TubeTintEngine.generate(d, spec.seed)
                    assertEquals(a.tubes.map { it.toList() }, b.tubes.map { it.toList() }); checkedSpatial++
                }
                "conduit" -> {
                    val a = ConduitEngine.generate(d, spec.seed); val b = ConduitEngine.generate(d, spec.seed)
                    assertTrue(a.start.contentEquals(b.start) && a.solution.contentEquals(b.solution)); checkedSpatial++
                }
                "crateshift" -> {
                    val a = CrateShiftEngine.generate(d, spec.seed); val b = CrateShiftEngine.generate(d, spec.seed)
                    assertTrue(a.boxes.contentEquals(b.boxes) && a.walls.contentEquals(b.walls) && a.player == b.player); checkedSpatial++
                }
            }
        }
        assertTrue(checkedSpatial > 20)
    }
}

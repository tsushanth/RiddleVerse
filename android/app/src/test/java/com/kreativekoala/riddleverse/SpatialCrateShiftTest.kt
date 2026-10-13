package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class SpatialCrateShiftTest {

    private fun replay(l: CrateLevel, moves: IntArray): CrateShiftState {
        val s = CrateShiftState(l)
        for (d in moves) assertTrue("illegal solver move", s.move(d))
        return s
    }

    @Test fun generatedLevelsAreSolvedBySolverReplay() {
        for (d in listOf("Easy", "Medium", "Hard")) {
            for (seed in 1L..6L) {
                val l = CrateShiftEngine.generate(d, seed)
                val sol = CrateShiftEngine.solve(l, 600_000)
                assertNotNull("$d $seed", sol)
                assertEquals(sol!!.size, l.par)
                val s = replay(l, sol)
                assertTrue("$d $seed not solved after replay", s.solved())
                assertEquals(l.minPushes, s.pushes)
                assertTrue("$d $seed pushes=${l.minPushes}", l.minPushes >= 3)
            }
        }
    }

    @Test fun deterministic() {
        val a = CrateShiftEngine.generate("Medium", 11); val b = CrateShiftEngine.generate("Medium", 11)
        assertArrayEquals(a.boxes, b.boxes); assertEquals(a.player, b.player); assertArrayEquals(a.walls, b.walls)
    }

    @Test fun moveRules() {
        // 5x3 corridor: # P B . G #   (boxes pushable only forward, not through walls or other boxes)
        val w = 6; val h = 3
        val walls = BooleanArray(w * h) { val r = it / w; val c = it % w; r != 1 || c == 0 || c == 5 }
        val goals = BooleanArray(w * h); goals[w + 4] = true
        val boxes = BooleanArray(w * h); boxes[w + 2] = true
        val level = CrateLevel(w, h, walls, goals, boxes, w + 1, 3, 2)
        val s = CrateShiftState(level)
        assertFalse(s.move(3))            // wall at left
        assertTrue(s.move(1))             // push box right
        assertEquals(1, s.pushes)
        assertTrue(s.move(1))             // push to goal
        assertTrue(s.solved())
        assertFalse(s.move(1))            // box would hit wall
        assertEquals(2, s.moves)
        assertTrue(s.undo()); assertFalse(s.solved()); assertEquals(1, s.moves)
        assertTrue(s.undo()); assertEquals(0, s.moves); assertEquals(0, s.pushes)
        assertFalse(s.undo())
    }

    @Test fun cannotPushTwoBoxes() {
        val w = 6; val h = 3
        val walls = BooleanArray(w * h) { val r = it / w; val c = it % w; r != 1 || c == 0 || c == 5 }
        val goals = BooleanArray(w * h)
        val boxes = BooleanArray(w * h); boxes[w + 2] = true; boxes[w + 3] = true
        val s = CrateShiftState(CrateLevel(w, h, walls, goals, boxes, w + 1, 0, 0))
        assertFalse(s.move(1))
        assertTrue(s.deadlocked().not())
    }

    @Test fun unsolvableLevelDetected() {
        // box in a dead corner with goal elsewhere
        val w = 5; val h = 5
        val walls = BooleanArray(w * h) { val r = it / w; val c = it % w; r == 0 || c == 0 || r == h - 1 || c == w - 1 }
        val goals = BooleanArray(w * h); goals[2 * w + 3] = true
        val boxes = BooleanArray(w * h); boxes[1 * w + 1] = true
        assertNull(CrateShiftEngine.solveFrom(w, h, walls, goals, boxes, 2 * w + 2))
    }

    @Test fun encodeRestoreAndHint() {
        val l = CrateShiftEngine.generate("Easy", 3)
        val s = CrateShiftState(l)
        val sol = CrateShiftEngine.solve(l)!!
        assertEquals(sol[0], s.hint())
        s.move(sol[0]); s.move(sol[1])
        val t = CrateShiftState(l); t.restore(s.encode())
        assertEquals(s.player, t.player); assertArrayEquals(s.boxes, t.boxes); assertEquals(s.moves, t.moves)
    }
}

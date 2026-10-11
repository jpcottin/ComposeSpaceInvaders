package com.jpcottin.composespaceinvaders.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {
    @Test fun rowsGrowWithTheLevelAndCapAtFive() {
        assertEquals(3, Levels.rows(1)); assertEquals(3, Levels.rows(2))
        assertEquals(4, Levels.rows(3)); assertEquals(4, Levels.rows(4))
        assertEquals(5, Levels.rows(5)); assertEquals(5, Levels.rows(9))
    }

    @Test fun everyTuningGetsHarderWithTheLevel() {
        for (l in 2..Levels.COUNT) {
            assertTrue(Levels.marchSpeed(l) > Levels.marchSpeed(l - 1))
            assertTrue(Levels.bombInterval(l) < Levels.bombInterval(l - 1))
            assertTrue(Levels.bombSpeed(l) > Levels.bombSpeed(l - 1))
            assertTrue(Levels.maxBombs(l) >= Levels.maxBombs(l - 1))
            assertTrue(Levels.startY(l) >= Levels.startY(l - 1))
        }
        assertEquals(5, Levels.maxBombs(Levels.COUNT))
    }

    @Test fun theLevelClampsToTheTen() {
        assertEquals(1, Levels.clamp(0)); assertEquals(10, Levels.clamp(11))
        assertEquals(Levels.rows(10), Levels.rows(99))
    }
}

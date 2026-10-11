package com.jpcottin.composespaceinvaders.engine

import kotlin.math.min

/** The ten levels, easy to hard: how many rows, how fast they march, how
 *  often they bomb, where they start. Level 10 is the mothership. */
object Levels {
    const val COUNT = 10
    /** The boss level: a mothership with a two-row escort. */
    const val BOSS_LEVEL = 10

    fun clamp(level: Int): Int = level.coerceIn(1, COUNT)

    /** Level 1 fields 3 rows of 8; a row is added every other level, capped at 5. */
    fun rows(level: Int): Int = min(3 + (clamp(level) - 1) / 2, World.MAX_ROWS)

    /** Sideways speed of a full wave, in world units per second. */
    fun marchSpeed(level: Int): Float = 0.09f + 0.020f * (clamp(level) - 1)

    /** Seconds between bombs: 1.15 at level 1 down to 0.52 at level 10. */
    fun bombInterval(level: Int): Float = 1.15f - 0.07f * (clamp(level) - 1)

    /** Bombs in the air at once. */
    fun maxBombs(level: Int): Int = min(2 + (clamp(level) - 1) / 3, 5)

    fun bombSpeed(level: Int): Float = 0.50f + 0.05f * (clamp(level) - 1)

    /** Higher levels start the wave a little lower: less room before the invasion. */
    fun startY(level: Int): Float = -0.66f + 0.03f * min(clamp(level) - 1, 6)
}

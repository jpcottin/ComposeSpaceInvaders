package com.jpcottin.composespaceinvaders.engine

/**
 * The battlefield's fixed geometry, in world units.
 *
 * World coordinates: x in [-aspect, aspect], y in [-1, 1], y pointing DOWN
 * (the invaders descend toward +y, the ship sits near the bottom). aspect =
 * width / height of the view, so a world unit is the same size on both axes
 * and shapes stay square on screen. The renderer maps a world point to pixels
 * with one scale (half the view height) around the view centre.
 */
object World {
    // --- the invader formation ---
    /** Invaders per row. */
    const val COLS = 8
    /** Formation height cap (level 1 has three rows). */
    const val MAX_ROWS = 5
    const val COL_STEP = 0.088f     // horizontal slot pitch
    const val ROW_STEP = 0.085f     // vertical slot pitch
    const val ALIEN_HW = 0.038f     // invader half-width
    const val ALIEN_HH = 0.030f     // invader half-height
    const val DESCEND = 0.040f      // drop per edge bounce
    const val SIDE_MARGIN = 0.015f  // the wave reverses when its outermost invader gets this close to the side
    /** The last survivor of a wave marches this many times faster than a full one. */
    const val THIN_WAVE_SPEEDUP = 3.5f
    /** The invasion succeeds (instant game over) once an invader's bottom edge
     *  reaches the control strip. Invaders at the ship's altitude collide
     *  first and cost a life each, so a wave has to fight its way down past
     *  the player. */
    const val INVADE_Y = 0.70f

    // --- the ship (fixed altitude, steers horizontally) ---
    const val SHIP_Y = 0.58f
    const val SHIP_SCALE = 0.065f   // half-extent of the sprite
    const val SHIP_R = 0.050f       // collision radius
    const val SHIP_SPEED = 1.6f

    /** Control strip: the band below the ship where a finger steers and fires. */
    const val STRIP_TOP_Y = 0.70f

    // --- lasers and bombs ---
    const val LASER_SPEED = 2.40f   // player laser, flies up (-y)
    const val FIRE_COOLDOWN = 0.42f
    const val RAPID_COOLDOWN = 0.20f
    const val MAX_LASERS = 3        // player lasers in flight at once
    /** Triple-shot side lasers leave at ±8° off vertical. */
    const val TRIPLE_ANGLE = 8f * 3.14159265f / 180f

    // --- the saucer and the mothership ---
    const val SAUCER_Y = -0.80f
    const val SAUCER_SPEED = 0.35f
    const val SAUCER_HW = 0.055f    // hit box
    const val SAUCER_HH = 0.035f
    const val BOSS_Y = -0.80f
    const val BOSS_HW = 0.150f
    const val BOSS_HH = 0.072f
    const val BOSS_HP = 16
    const val BOSS_BOMB_INTERVAL = 1.15f

    // --- bonuses ---
    const val POWER_UP_R = 0.045f
    const val POWER_UP_SPEED = 0.30f
    const val POWER_UP_DURATION = 8f

    /** The starfield scrolls down at this speed (the near layer 3.2 times faster). */
    const val STAR_SPEED = 0.18f
}

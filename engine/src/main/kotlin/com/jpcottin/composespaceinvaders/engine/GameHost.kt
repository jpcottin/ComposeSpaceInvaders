package com.jpcottin.composespaceinvaders.engine

/** A sound the game asks its host to play. */
enum class SoundEvent { LASER, EXPLOSION, SHIP_HIT, LEVEL_CLEAR, POWER_UP }

/** One row of the high-score table. */
data class HighScore(val score: Long, val level: Int)

/**
 * Everything the game needs from the outside world. The engine never touches
 * Android: the app implements this with AudioTrack, the vibrator and
 * DataStore, the tests and the bench with nothing at all.
 */
interface GameHost {
    /** Play a one-shot effect. */
    fun sound(event: SoundEvent) {}
    /** The wave stepped: play note [step] (0..3) of the four-note march bass. */
    fun march(step: Int) {}
    /** Whether the saucer's siren should sound right now. */
    fun saucer(on: Boolean) {}
    /** Whether the ambient track should play right now. */
    fun music(on: Boolean) {}
    /** A short pulse: the ship was hit. */
    fun haptic() {}
    /** The high-score table changed and should be persisted. */
    fun highScoresChanged(table: List<HighScore>) {}
    /** A level never seen before was reached; persist it so the title can
     *  offer it as a starting point. */
    fun furthestLevelChanged(level: Int) {}

    /** The host that does nothing: tests, the bench, previews. */
    object None : GameHost
}

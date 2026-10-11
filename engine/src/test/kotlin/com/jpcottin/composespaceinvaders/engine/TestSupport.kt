package com.jpcottin.composespaceinvaders.engine

/** One 60 Hz frame (two simulation ticks). */
const val FRAME = 1f / 60f

/** Advance the game by [seconds] of 60 Hz frames. */
fun Game.run(seconds: Float) {
    var t = 0f
    while (t < seconds) { update(FRAME); t += FRAME }
}

/** Advance frame by frame until [done] or [seconds] have passed; returns whether it happened. */
fun Game.runUntil(seconds: Float, done: Game.() -> Boolean): Boolean {
    var t = 0f
    while (t < seconds) {
        if (done()) return true
        update(FRAME); t += FRAME
    }
    return done()
}

/** A finger on the control strip at [x]. */
fun Game.touchStrip(x: Float, id: Int = 0) = onPointerDown(id, x, 0.90f)

/** A game on the title screen with the default seed. */
fun newGame(host: GameHost = GameHost.None, aspect: Float = 0.45f): Game = Game(host).apply { setAspect(aspect) }

/** A game in play at level [level]. */
fun playing(level: Int = 1, host: GameHost = GameHost.None, aspect: Float = 0.45f): Game =
    newGame(host, aspect).apply { debug.startGame(); if (level > 1) debug.startLevel(level) }

/** A host that records what the game asks of it. */
class RecordingHost : GameHost {
    val sounds = ArrayList<SoundEvent>()
    val marches = ArrayList<Int>()
    var siren = false
    var music: Boolean? = null
    var haptics = 0
    var tables = ArrayList<List<HighScore>>()
    var furthest = 0
    override fun sound(event: SoundEvent) { sounds.add(event) }
    override fun march(step: Int) { marches.add(step) }
    override fun saucer(on: Boolean) { siren = on }
    override fun music(on: Boolean) { music = on }
    override fun haptic() { haptics++ }
    override fun highScoresChanged(table: List<HighScore>) { tables.add(table) }
    override fun furthestLevelChanged(level: Int) { furthest = level }
}

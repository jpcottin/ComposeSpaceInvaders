package com.jpcottin.composespaceinvaders.engine

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A Space Invaders-like game: a ship at the bottom, a marching wave of
 * invaders that bombs it and speeds up as it thins, a mystery saucer, three
 * bonuses, ten levels and a mothership at the end. Pure Kotlin: the app draws
 * what this class holds and feeds it input in world coordinates (see
 * [World]); the host gets sounds, haptics and the high-score table through
 * [GameHost].
 *
 * Controls: the bottom strip of the screen (below the ship) is the control
 * zone — while a finger presses there, the ship steers toward the finger's x
 * and fires continuously. On the glasses the whole touchbar is the zone
 * ([ControlMode.TOUCHBAR]). A key or a D-pad steers with [setSteer] and
 * fires with [setFire].
 */
class Game(private val host: GameHost = GameHost.None) {

    enum class State { TITLE, PLAYING, LEVEL_CLEAR, GAME_OVER, WIN }

    /** Row tiers, top to bottom, with their kill points (times the level). */
    enum class AlienType(val points: Int) { SQUID(30), CRAB(20), OCTOPUS(10) }

    /** Bonuses dropped by killed invaders. */
    enum class PowerUpType { SHIELD, RAPID, TRIPLE }

    /** How touch input drives the ship. */
    enum class ControlMode {
        /** Phone: the strip below the ship steers and fires. */
        STRIP,
        /** AI glasses: the whole touch surface is the bar, finger x steers, contact fires. */
        TOUCHBAR,
    }

    /** How the last round ended (bench statistics). */
    enum class DeathCause { NONE, BOMB, COLLISION, INVASION }

    /** One invader. Its position derives from the formation origin plus its
     *  (row, col) slot, so the whole wave marches as a unit. */
    class Alien internal constructor(val row: Int, val col: Int, val type: AlienType) {
        var alive = true
            internal set
    }

    /** A player laser, flying up (vy < 0); the triple shot's side lasers have vx != 0. */
    class Laser internal constructor(var x: Float, var y: Float, val vx: Float, val vy: Float) {
        var alive = true
            internal set
        /** Tilt along the flight path, for the renderer. */
        val rot: Float get() = if (vx != 0f) atan2(vx, -vy) else 0f
    }

    /** An invader's bomb, falling (vy > 0); the mothership's are aimed (vx != 0). */
    class Bomb internal constructor(var x: Float, var y: Float, val vx: Float, val vy: Float, var wobble: Float) {
        var alive = true
            internal set
    }

    class Saucer internal constructor(var x: Float, val y: Float, val vx: Float)

    /** The level-10 mothership: drifts sinusoidally across the top. */
    class Boss internal constructor(val maxHp: Int) {
        var t = 0f
            internal set
        var x = 0f
            internal set
        var hp = maxHp
            internal set
        var alive = true
            internal set
    }

    class PowerUp internal constructor(var x: Float, var y: Float, val vy: Float, var rot: Float, val spin: Float, val type: PowerUpType) {
        var alive = true
            internal set
    }

    /** A debris fragment: colour as (r, g, b) in 0..1, the renderer tints it. */
    class Particle internal constructor(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var rot: Float, val spin: Float, val maxLife: Float, val size: Float,
        val r: Float, val g: Float, val b: Float,
    ) {
        var t = 0f
        /** 1 at birth, 0 at death. */
        val life: Float get() = 1f - t / maxLife
    }

    /** An expanding, fading flash ring. */
    class Explosion internal constructor(
        val x: Float, val y: Float, val radius: Float, val maxLife: Float,
        val r: Float, val g: Float, val b: Float,
    ) {
        var t = 0f
        val progress: Float get() = t / maxLife
    }

    /** A background star; x is in -1..1 across the view (the renderer scales it by the aspect). */
    class Star internal constructor(var x: Float, var y: Float, val size: Float, val phase: Float)

    private class Pointer(val id: Int, var x: Float, var y: Float)

    // --- viewport ---
    /** Width / height of the view; the world spans x in [-aspect, aspect]. */
    var aspect: Float = 0.45f
        private set

    // --- state ---
    var state: State = State.TITLE
        private set
    /** Seconds since start; drives pulses and blinks in the renderer. */
    var animTime: Float = 0f
        private set
    /** Seconds in the current LEVEL_CLEAR / GAME_OVER / WIN screen. */
    var stateTimer: Float = 0f
        private set
    /** A round frozen mid-flight (the screen went away, Settings opened).
     *  A tap resumes it; the autopilot resumes by itself. */
    var paused: Boolean = false
        private set

    // --- the formation ---
    private val aliensList = ArrayList<Alien>()
    val aliens: List<Alien> get() = aliensList
    var rows: Int = 0
        private set
    /** Centre offset of the wave. */
    var formationX: Float = 0f
        private set
    /** y of the top row. */
    var formationY: Float = Levels.startY(1)
        private set
    /** +1 marching right, -1 left. */
    var marchDir: Int = 1
        private set
    /** 0 or 1, flips on each march beat: the sprite animation frame. */
    var marchFrame: Int = 0
        private set
    private var beatTimer = 0f
    private var beatStep = 0

    // --- the ship ---
    var shipX: Float = 0f
        private set
    /** Lean into the direction of travel, radians. */
    var shipTilt: Float = 0f
        private set
    private var fireCooldown = 0f
    /** Seconds of invulnerability left after a hit (the ship blinks). */
    var invuln: Float = 0f
        private set

    // --- lasers, bombs, the saucer, the mothership ---
    private val lasersList = ArrayList<Laser>()
    val lasers: List<Laser> get() = lasersList
    private val bombsList = ArrayList<Bomb>()
    val bombs: List<Bomb> get() = bombsList
    private var bombTimer = 1.2f
    var saucer: Saucer? = null
        private set
    private var saucerTimer = 14f
    private var saucerSiren = false
    /** The mothership, on the boss level only. */
    var boss: Boss? = null
        private set
    private var bossBombTimer = 0f

    // --- bonuses ---
    private val powerUpsList = ArrayList<PowerUp>()
    val powerUps: List<PowerUp> get() = powerUpsList
    /** The shield absorbs one hit and lasts until then. */
    var shieldActive: Boolean = false
        private set
    var rapidTimer: Float = 0f
        private set
    var tripleTimer: Float = 0f
        private set
    val rapidActive: Boolean get() = rapidTimer > 0f
    val tripleActive: Boolean get() = tripleTimer > 0f

    // --- effects ---
    private val particlesList = ArrayList<Particle>()
    val particles: List<Particle> get() = particlesList
    private val explosionsList = ArrayList<Explosion>()
    val explosions: List<Explosion> get() = explosionsList
    private val starsList = ArrayList<Star>()
    /** The far, slow layer of the starfield. */
    val stars: List<Star> get() = starsList
    private val starsNearList = ArrayList<Star>()
    /** The near, fast layer. */
    val starsNear: List<Star> get() = starsNearList
    private var shakeAmt = 0f
    var shakeX: Float = 0f
        private set
    var shakeY: Float = 0f
        private set
    /** The points just earned by a bonus, shown briefly where they were earned. */
    var bonusFlash: Long = 0
        private set
    var bonusFlashTimer: Float = 0f
        private set
    var bonusFlashX: Float = 0f
        private set
    var bonusFlashY: Float = 0f
        private set

    // --- progression / scoring ---
    var level: Int = 1
        private set
    var lives: Int = START_LIVES
        private set
    var score: Long = 0
        private set
    /** Bench statistics. */
    var shotsFired: Int = 0
        private set
    /** Straight lasers that left the screen without a hit. */
    var lasersLost: Int = 0
        private set
    /** Lasers spent on bombs. */
    var bombsShot: Int = 0
        private set
    var deathCause: DeathCause = DeathCause.NONE
        private set

    // --- settings (owned by the app, mirrored here) ---
    var soundEnabled: Boolean = true
        set(value) { field = value; syncMusic(); syncSiren() }
    var autoPlayActive: Boolean = false
    var controlMode: ControlMode = ControlMode.STRIP
    /** True on the phone while the round runs on the glasses: gameplay freezes. */
    var glassesActive: Boolean = false
        set(value) { if (field != value) { field = value; syncMusic(); syncSiren() } }

    // --- high scores ---
    private val table = ArrayList<HighScore>()
    val highScores: List<HighScore> get() = table
    var newHighScore: Boolean = false
        private set
    /** 0-based rank of the score just set, or -1. */
    var newHighScoreRank: Int = -1
        private set

    // --- auto-play (drives the same control path as a finger) ---
    private var aiMove = false
    private var aiTargetX = 0f
    private var aiFire = false
    /** The autopilot is steering the ship somewhere this frame. */
    val aiHasTarget: Boolean get() = aiMove
    /** Where the autopilot is taking the ship. */
    val aiTarget: Float get() = aiTargetX
    /** The autopilot holds the trigger this frame. */
    val aiFiring: Boolean get() = aiFire

    // --- input ---
    private val pointers = ArrayList<Pointer>()
    private var tapPending = false
    private var steer = 0
    private var fireKey = false

    private var rng: Int = 0x1234567

    init {
        repeat(60) { starsList.add(Star(frange(-1f, 1f), frange(-1f, 1f), frange(0.004f, 0.011f), frange(0f, 6.2832f))) }
        repeat(20) { starsNearList.add(Star(frange(-1f, 1f), frange(-1f, 1f), frange(0.007f, 0.016f), frange(0f, 6.2832f))) }
        buildFormation()   // the title screen shows a demo wave
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    /** The view's aspect ratio (width / height). The wave marches the full
     *  width, so a fold or unfold mid-level just gives it more or less room;
     *  the ship is kept on screen. */
    fun setAspect(aspect: Float) {
        if (aspect <= 0f || aspect == this.aspect) return
        this.aspect = aspect
        clampShip()
    }

    /** Randomize the xorshift state (production only — tests rely on the fixed
     *  default seed for deterministic choreography). */
    fun seed(seed: Int) {
        rng = if (seed == 0) 0x1234567 else seed
    }

    /** The highest level ever reached on this device; the host persists it.
     *  The title screen lets a round start at any level up to this one. */
    var furthestLevel: Int = 1
        set(value) {
            field = value.coerceIn(1, Levels.COUNT)
            if (startLevel > field) startLevel = field
        }

    /** The level the next round starts at, chosen on the title screen. */
    var startLevel: Int = 1
        private set

    /** Move the title screen's level pick by [delta] levels, within 1 and
     *  [furthestLevel]. Does nothing once a round is under way. */
    fun selectLevel(delta: Int) {
        if (state == State.TITLE) startLevel = (startLevel + delta).coerceIn(1, furthestLevel)
    }

    /** Adopt a persisted high-score table. */
    fun restoreHighScores(scores: List<HighScore>) {
        table.clear()
        for (s in scores) mergeHighScore(s.score, s.level)
    }

    /** Resume a run after process death: same level, score and lives, but a
     *  fresh wave. Bounds-checked because the saved values come back from the
     *  OS, not from us. */
    fun restoreSession(level: Int, score: Long, lives: Int) {
        if (level !in 1..Levels.COUNT || lives !in 1..START_LIVES || score < 0) return
        startGame()
        if (level > 1) startLevel(level)
        this.score = score
        this.lives = lives
    }

    /** A round is in progress (worth saving across process death). */
    val isMidGame: Boolean get() = state == State.PLAYING || state == State.LEVEL_CLEAR

    /** The screen is going away or something covers it: freeze a round in
     *  flight. Nothing to freeze on the title or an end screen. */
    fun pause() {
        if (state == State.PLAYING) { paused = true; syncSiren() }
    }

    /** The screen is back. A human gets a "tap to resume" moment; the
     *  autopilot, which nobody is watching anyway, carries on at once. */
    fun resume() {
        if (paused && autoPlayActive) { paused = false; syncSiren() }
    }

    // ── Input (world coordinates) ─────────────────────────────────────────────

    fun onPointerDown(id: Int, wx: Float, wy: Float) {
        val p = pointers.firstOrNull { it.id == id }
        if (p != null) { p.x = wx; p.y = wy } else pointers.add(Pointer(id, wx, wy))
        tapPending = true
    }

    fun onPointerMove(id: Int, wx: Float, wy: Float) {
        pointers.firstOrNull { it.id == id }?.apply { x = wx; y = wy }
    }

    fun onPointerUp(id: Int) {
        pointers.removeAll { it.id == id }
    }

    fun onPointersCancel() {
        pointers.clear()
        steer = 0
        fireKey = false
    }

    /** A tap without a position (a D-pad select, a glasses touchbar press). */
    fun tap() {
        tapPending = true
    }

    /** Steer with a key or a D-pad: -1 left, +1 right, 0 released. The ship
     *  moves at its usual speed for as long as the key is held. */
    fun setSteer(direction: Int) {
        steer = direction.coerceIn(-1, 1)
    }

    /** Hold or release the trigger from a key or a D-pad centre. */
    fun setFire(held: Boolean) {
        fireKey = held
    }

    // In TOUCHBAR mode (AI glasses) the whole surface is the control bar; in
    // STRIP mode only the strip below the ship counts.
    private fun inControlZone(p: Pointer): Boolean =
        controlMode == ControlMode.TOUCHBAR || p.y > World.STRIP_TOP_Y

    /** A finger, a key or the autopilot is steering the ship right now. */
    val moveHeld: Boolean
        get() = steer != 0 || pointers.any { inControlZone(it) } || (autoPlayActive && aiMove)

    /** A finger, a key or the autopilot holds the trigger right now. */
    val fireHeld: Boolean
        get() = fireKey || pointers.any { inControlZone(it) } || (autoPlayActive && aiFire)

    private fun controlTargetX(): Float {
        if (steer != 0) return steer * (aspect - World.SHIP_SCALE)   // the screen's edge, that way
        return pointers.firstOrNull { inControlZone(it) }?.x ?: aiTargetX
    }

    // ── The formation ─────────────────────────────────────────────────────────

    fun alienX(a: Alien): Float = formationX + (a.col - (World.COLS - 1) * 0.5f) * World.COL_STEP
    fun alienY(a: Alien): Float = formationY + a.row * World.ROW_STEP

    /** Invaders still alive. */
    val aliveAliens: Int get() = aliensList.count { it.alive }

    /** The row tier: the top row is the squid, the bottom row(s) the octopus, crabs between. */
    private fun rowType(row: Int, rows: Int): AlienType {
        val octoRows = if (rows >= 5) 2 else 1
        return when {
            row == 0 -> AlienType.SQUID
            row >= rows - octoRows -> AlienType.OCTOPUS
            else -> AlienType.CRAB
        }
    }

    private fun buildFormation() {
        // The boss level: the mothership only brings a two-row escort.
        rows = if (level == Levels.BOSS_LEVEL) 2 else Levels.rows(level)
        aliensList.clear()
        for (r in 0 until rows) for (c in 0 until World.COLS) aliensList.add(Alien(r, c, rowType(r, rows)))
        formationX = 0f
        formationY = Levels.startY(level)
        marchDir = 1
        marchFrame = 0
        beatTimer = 0f
        beatStep = 0
    }

    /** The fewer invaders remain, the faster the survivors march (classic). */
    val marchSpeed: Float
        get() {
            val total = aliensList.size
            if (total == 0) return Levels.marchSpeed(level)
            val aliveFrac = aliveAliens.toFloat() / total
            return Levels.marchSpeed(level) * (1f + (World.THIN_WAVE_SPEEDUP - 1f) * (1f - aliveFrac))
        }

    // ── Rounds ────────────────────────────────────────────────────────────────

    private fun startGame() {
        score = 0
        lives = START_LIVES
        shotsFired = 0; lasersLost = 0; bombsShot = 0
        deathCause = DeathCause.NONE
        newHighScore = false
        newHighScoreRank = -1
        shieldActive = false
        rapidTimer = 0f
        tripleTimer = 0f
        bonusFlashTimer = 0f
        level = startLevel
        startLevel(startLevel)
    }

    /** Clear every battlefield entity; shared by [startLevel] and the return
     *  to the title so nothing renders frozen there. */
    private fun resetBattlefield() {
        lasersList.clear()
        bombsList.clear()
        particlesList.clear()
        explosionsList.clear()
        powerUpsList.clear()
        saucer = null
        boss = null
        invuln = 0f
        syncSiren()
    }

    private fun startLevel(level: Int) {
        paused = false
        this.level = Levels.clamp(level)
        if (this.level > furthestLevel) {
            furthestLevel = this.level
            host.furthestLevelChanged(furthestLevel)
        }
        buildFormation()
        resetBattlefield()
        saucerTimer = frange(10f, 16f)
        bombTimer = 1.5f
        if (this.level == Levels.BOSS_LEVEL) boss = Boss(World.BOSS_HP)
        bossBombTimer = 2f
        shipX = 0f
        shipTilt = 0f
        fireCooldown = 0f
        invuln = 1.2f
        shakeAmt = 0f; shakeX = 0f; shakeY = 0f
        state = State.PLAYING
        syncMusic()
    }

    private fun spawnPowerUp(x: Float, y: Float) {
        val roll = frand()
        val type = when {
            roll < 0.34f -> PowerUpType.SHIELD
            roll < 0.67f -> PowerUpType.RAPID
            else -> PowerUpType.TRIPLE
        }
        powerUpsList.add(PowerUp(x, y, World.POWER_UP_SPEED, 0f, frange(-2.5f, 2.5f), type))
    }

    private fun spawnSaucer() {
        val fromLeft = frand() < 0.5f
        saucer = Saucer(if (fromLeft) -aspect - 0.08f else aspect + 0.08f, World.SAUCER_Y,
            (if (fromLeft) 1f else -1f) * World.SAUCER_SPEED)
        syncSiren()
    }

    /** Grant a bonus's effect. Shared by the pickup path and the test hook so
     *  tests exercise the production timers, not a copy of them. */
    private fun applyPowerUp(t: PowerUpType) {
        when (t) {
            PowerUpType.SHIELD -> shieldActive = true
            PowerUpType.RAPID -> rapidTimer = World.POWER_UP_DURATION
            PowerUpType.TRIPLE -> tripleTimer = World.POWER_UP_DURATION
        }
    }

    private fun spawnDebris(ax: Float, ay: Float, ar: Float, cr: Float, cg: Float, cb: Float) {
        explosionsList.add(Explosion(ax, ay, ar, 0.22f, cr, cg, cb))
        repeat(10) {
            val angle = frange(0f, 6.2832f)
            val speed = frange(0.5f, 1.6f)
            particlesList.add(Particle(
                x = ax + cos(angle) * ar * 0.4f, y = ay + sin(angle) * ar * 0.4f,
                vx = cos(angle) * speed, vy = sin(angle) * speed,
                rot = frange(0f, 6.2832f), spin = frange(-5f, 5f),
                maxLife = frange(0.25f, 0.55f), size = ar * frange(0.20f, 0.45f),
                r = cr + frange(-0.08f, 0.08f), g = cg + frange(-0.08f, 0.08f), b = cb + frange(-0.08f, 0.08f),
            ))
        }
    }

    /** Kill an invader: score by row tier, debris, a possible bonus drop. */
    private fun killAlien(a: Alien) {
        a.alive = false
        val ax = alienX(a); val ay = alienY(a)
        val c = TIER_COLORS[a.type.ordinal]
        spawnDebris(ax, ay, World.ALIEN_HW, c[0], c[1], c[2])
        score += a.type.points.toLong() * level
        sound(SoundEvent.EXPLOSION)
        if (frand() < DROP_CHANCE) spawnPowerUp(ax, ay)
    }

    /** The shield absorbs the hit; otherwise a life, a flash, a shake, and
     *  possibly the end. Shared by bomb hits and invader collisions. */
    private fun applyShipHit(cause: DeathCause) {
        if (shieldActive) {
            shieldActive = false
            shakeAmt = 0.5f
            // Brief grace so two overlapping hits in one frame (adjacent
            // invaders, invader + bomb) can't consume the shield and a life.
            invuln = 1f
            return
        }
        lives--
        invuln = 1.5f
        shakeAmt = 1f
        sound(SoundEvent.SHIP_HIT)
        host.haptic()
        explosionsList.add(Explosion(shipX, World.SHIP_Y, World.SHIP_SCALE, 0.18f, 0.45f, 0.9f, 1f))
        if (lives <= 0) endGame(cause)
    }

    /** The wave reached the ship's line: the invasion succeeded, game over
     *  regardless of remaining lives (classic rule). */
    private fun triggerInvasion() {
        lives = 0
        shakeAmt = 1f
        spawnDebris(shipX, World.SHIP_Y, World.SHIP_SCALE * 1.5f, 0.28f, 0.72f, 0.92f)
        sound(SoundEvent.SHIP_HIT)
        host.haptic()
        endGame(DeathCause.INVASION)
    }

    private fun endGame(cause: DeathCause) {
        deathCause = cause
        endRound(State.GAME_OVER)
        checkHighScore()
    }

    /** The round is over: nothing in flight is updated any more, so the
     *  shots and the bonuses are cleared rather than left frozen on the end
     *  screen; the debris and the flash rings play out. */
    private fun endRound(end: State) {
        paused = false
        lasersList.clear()
        bombsList.clear()
        powerUpsList.clear()
        saucer = null
        state = end; stateTimer = 0f
        syncSiren()
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Advance the game by [dtIn] seconds of wall-clock time. The simulation
     * runs in fixed ticks of [TICK] seconds: a frame's time is accumulated
     * and as many whole ticks as it holds are taken, the remainder carried
     * to the next frame. A 60, 90 or 120 Hz display therefore plays the
     * very same game, and a hitch is clamped to [MAX_FRAME] so nothing warps.
     */
    fun update(dtIn: Float) {
        if (dtIn <= 0f) return
        accumulator += if (dtIn > MAX_FRAME) MAX_FRAME else dtIn
        while (accumulator >= TICK - 1e-6f) {
            accumulator -= TICK
            tick(TICK)
        }
    }

    private var accumulator = 0f

    /** One fixed step of the simulation. */
    private fun tick(dt: Float) {
        animTime += dt

        // Particles and explosions animate in all states. Drag is exponential
        // decay, normalized to a 60 Hz reference so debris behaves the same
        // at any refresh rate.
        val drag = 0.88f.pow(dt * 60f)
        for (p in particlesList) {
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= drag; p.vy *= drag
            p.rot += p.spin * dt
            p.t += dt
        }
        particlesList.removeAll { it.t >= it.maxLife }
        for (e in explosionsList) e.t += dt
        explosionsList.removeAll { it.t >= it.maxLife }

        // Stars scroll in every state for a sense of motion.
        for (s in starsList) {
            s.y += World.STAR_SPEED * dt
            if (s.y > 1.05f) { s.y = -1.05f; s.x = frange(-1f, 1f) }
        }
        for (s in starsNearList) {
            s.y += World.STAR_SPEED * 3.2f * dt
            if (s.y > 1.05f) { s.y = -1.05f; s.x = frange(-1f, 1f) }
        }

        // Screen shake decay, 60 Hz-normalized like the particle drag above.
        if (shakeAmt > 0f) {
            shakeX = frange(-1f, 1f) * shakeAmt * 0.040f
            shakeY = frange(-1f, 1f) * shakeAmt * 0.040f
            shakeAmt *= 0.78f.pow(dt * 60f)
            if (shakeAmt < 0.01f) { shakeAmt = 0f; shakeX = 0f; shakeY = 0f }
        }
        if (bonusFlashTimer > 0f) bonusFlashTimer -= dt

        val tapped = tapPending
        tapPending = false

        // A paused round waits for a tap; the clock above keeps the overlay
        // breathing. A key does not resume it; the autopilot does.
        if (paused) {
            if (state != State.PLAYING) paused = false
            else if (tapped || (autoPlayActive && aiMove)) { paused = false; syncSiren() }
            else return
        }

        when (state) {
            State.TITLE -> {
                if (tapped) { startGame(); return }
                updateDemo(dt)
            }
            State.PLAYING -> {
                // While the game runs on the glasses, the phone instance freezes.
                if (glassesActive && controlMode == ControlMode.STRIP) return
                // Reset the AI intents; updateAutoPlay sets them when the autopilot is on.
                aiMove = false; aiFire = false
                if (autoPlayActive) updateAutoPlay()
                // One frame of gameplay, in order. State may flip to GAME_OVER
                // or WIN mid-sequence (fatal hit, invasion, boss kill); once it
                // does, the remaining steps are skipped — the high score has
                // already been saved, so nothing may fire, score or collect
                // after the end.
                updateShip(dt)
                if (state == State.PLAYING) updateFormation(dt)
                if (state == State.PLAYING) updateBoss(dt)
                if (state == State.PLAYING) updateBombs(dt)
                if (state == State.PLAYING) updateLasers(dt)
                if (state == State.PLAYING) updateSaucer(dt)
                if (state == State.PLAYING) updatePowerUps(dt)
                lasersList.removeAll { !it.alive }
                bombsList.removeAll { !it.alive }
                checkLevelClear()
            }
            State.LEVEL_CLEAR -> {
                // The boss level never enters LEVEL_CLEAR: it ends only by
                // killing the mothership, which jumps straight to WIN.
                stateTimer += dt
                if (stateTimer >= LEVEL_CLEAR_PAUSE) startLevel(level + 1)
            }
            State.GAME_OVER, State.WIN -> {
                stateTimer += dt
                if (tapped && stateTimer > END_SCREEN_GRACE) {
                    state = State.TITLE
                    level = 1
                    buildFormation()       // rebuild the title demo wave
                    // Clear every leftover, or it renders frozen on the title
                    // screen forever (nothing updates it there).
                    resetBattlefield()
                    shieldActive = false
                    rapidTimer = 0f
                    tripleTimer = 0f
                    shakeAmt = 0f; shakeX = 0f; shakeY = 0f
                    syncMusic()
                }
            }
        }
    }

    /** The demo wave slowly marching under the title. */
    private fun updateDemo(dt: Float) {
        formationX += marchDir * 0.06f * dt
        if (formationX > 0.09f) marchDir = -1
        if (formationX < -0.09f) marchDir = 1
        beatTimer -= dt
        if (beatTimer <= 0f) {
            beatTimer = 0.60f
            marchFrame = marchFrame xor 1
        }
    }

    private fun clampShip() {
        val lim = aspect - World.SHIP_SCALE
        shipX = shipX.coerceIn(-lim, lim)
    }

    private fun updateShip(dt: Float) {
        var dir = 0
        if (moveHeld) {
            val target = controlTargetX()
            val dx = target - shipX
            if (abs(dx) > SHIP_DEADBAND) {
                dir = if (dx > 0f) 1 else -1
                var step = World.SHIP_SPEED * dt
                if (step > abs(dx)) step = abs(dx)   // land exactly on the finger
                shipX += dir * step
            }
        }
        clampShip()
        // Smoothly lean into the direction of travel (±20°).
        val tiltTarget = dir * 0.35f
        shipTilt += (tiltTarget - shipTilt) * 9f * dt
        if (invuln > 0f) invuln -= dt
    }

    private fun updateFormation(dt: Float) {
        if (aliveAliens == 0) return

        // March sideways; the speed rises as the wave thins out.
        formationX += marchDir * marchSpeed * dt

        // Edge bounce: reverse and step down when the outermost alive invader
        // touches a side margin.
        var minX = 1e9f; var maxX = -1e9f
        for (a in aliensList) {
            if (!a.alive) continue
            val x = alienX(a)
            if (x < minX) minX = x
            if (x > maxX) maxX = x
        }
        if (marchDir > 0 && maxX + World.ALIEN_HW >= aspect - World.SIDE_MARGIN) {
            marchDir = -1
            formationY += World.DESCEND
        } else if (marchDir < 0 && minX - World.ALIEN_HW <= -aspect + World.SIDE_MARGIN) {
            marchDir = 1
            formationY += World.DESCEND
        }

        // The march beat: sprite animation plus the four-note bass loop,
        // faster as the wave thins (the interval tracks the fraction alive).
        val aliveFrac = aliveAliens.toFloat() / aliensList.size
        beatTimer -= dt
        if (beatTimer <= 0f) {
            beatTimer = 0.14f + 0.75f * aliveFrac
            marchFrame = marchFrame xor 1
            beatStep = (beatStep + 1) and 3
            if (soundEnabled) host.march(beatStep)
        }

        // Invader vs ship collision, and the invasion line.
        for (a in aliensList) {
            if (!a.alive) continue
            val ax = alienX(a); val ay = alienY(a)
            if (ay + World.ALIEN_HH >= World.INVADE_Y) {
                triggerInvasion()
                return
            }
            if (invuln <= 0f &&
                abs(ax - shipX) < World.ALIEN_HW + World.SHIP_R * 0.8f &&
                abs(ay - World.SHIP_Y) < World.ALIEN_HH + World.SHIP_R * 0.8f) {
                killAlien(a)
                applyShipHit(DeathCause.COLLISION)
                if (state != State.PLAYING) return
            }
        }
    }

    private fun updateBoss(dt: Float) {
        val b = boss ?: return
        if (!b.alive) return
        b.t += dt
        b.x = sin(b.t * 0.60f) * (aspect - World.BOSS_HW - 0.05f)

        // Aimed bombs: lobbed toward where the player currently is.
        bossBombTimer -= dt
        if (bossBombTimer <= 0f) {
            bossBombTimer = World.BOSS_BOMB_INTERVAL * frange(0.8f, 1.2f)
            val y = World.BOSS_Y + World.BOSS_HH + 0.02f
            val vy = Levels.bombSpeed(level) * 1.25f
            val dx = shipX - b.x
            val tReach = (World.SHIP_Y - y) / vy
            // Cap the lateral speed so the bombs stay dodgeable.
            val vx = (dx / (if (tReach > 0.3f) tReach else 0.3f)).coerceIn(-0.45f, 0.45f)
            bombsList.add(Bomb(b.x, y, vx, vy, frange(0f, 6.28f)))
        }
    }

    /** The bottom-most alive invader of a random occupied column fires (classic). */
    private fun dropBomb() {
        val cols = (0 until World.COLS).filter { c -> aliensList.any { it.alive && it.col == c } }
        if (cols.isEmpty()) return
        val col = cols[(frand() * cols.size).toInt() % cols.size]
        var shooter: Alien? = null
        for (a in aliensList) if (a.alive && a.col == col && (shooter == null || a.row > shooter.row)) shooter = a
        val s = shooter ?: return
        bombsList.add(Bomb(alienX(s), alienY(s) + World.ALIEN_HH + 0.01f, 0f,
            Levels.bombSpeed(level) * frange(0.9f, 1.1f), frange(0f, 6.28f)))
    }

    private fun updateBombs(dt: Float) {
        bombTimer -= dt
        if (bombTimer <= 0f) {
            if (bombsList.count { it.alive } < Levels.maxBombs(level) && aliveAliens > 0) dropBomb()
            bombTimer = Levels.bombInterval(level) * frange(0.6f, 1.4f)
        }
        for (b in bombsList) {
            if (!b.alive) continue
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.wobble += 6f * dt
            if (b.y > 1.05f || b.x > aspect + 0.1f || b.x < -aspect - 0.1f) { b.alive = false; continue }
            if (invuln <= 0f && state == State.PLAYING) {
                val dx = b.x - shipX; val dy = b.y - World.SHIP_Y
                val rad = World.SHIP_R + 0.012f
                if (dx * dx + dy * dy < rad * rad) {
                    b.alive = false
                    applyShipHit(DeathCause.BOMB)
                    if (state != State.PLAYING) return   // the round ended: the bombs are gone
                }
            }
        }
    }

    private fun fireLaser(angle: Float) {
        lasersList.add(Laser(shipX, World.SHIP_Y - World.SHIP_SCALE * 1.1f,
            World.LASER_SPEED * sin(angle), -World.LASER_SPEED * cos(angle)))
    }

    private fun updateLasers(dt: Float) {
        // Fire while the trigger is held; the rapid bonus shortens the cooldown
        // and the triple bonus adds two side lasers at ±8°.
        if (fireCooldown > 0f) fireCooldown -= dt
        if (fireHeld && fireCooldown <= 0f && lasersList.count { it.alive } < World.MAX_LASERS) {
            fireLaser(0f)
            if (tripleActive) { fireLaser(-World.TRIPLE_ANGLE); fireLaser(World.TRIPLE_ANGLE) }
            fireCooldown = if (rapidActive) World.RAPID_COOLDOWN else World.FIRE_COOLDOWN
            shotsFired++
            sound(SoundEvent.LASER)
        }

        for (b in lasersList) {
            if (!b.alive) continue
            // Substep the move so a fast laser can't tunnel: a full step at
            // the clamped frame time is taller than an invader's whole hit
            // box. Collisions are checked at every substep position.
            val steps = ((abs(b.vx) + abs(b.vy)) * dt / LASER_STEP).toInt() + 1
            val sdt = dt / steps
            var s = 0
            while (s < steps && b.alive) {
                s++
                b.x += b.vx * sdt
                b.y += b.vy * sdt
                if (b.y < -1.05f || b.x > aspect + 0.1f || b.x < -aspect - 0.1f) {
                    b.alive = false
                    if (b.vx == 0f) lasersLost++
                    continue
                }

                // Laser vs bomb: shooting down an incoming bomb cancels both.
                for (bomb in bombsList) {
                    if (!bomb.alive) continue
                    val dx = b.x - bomb.x; val dy = b.y - bomb.y
                    if (dx * dx + dy * dy < 0.030f * 0.030f) {
                        b.alive = false
                        bomb.alive = false
                        bombsShot++
                        explosionsList.add(Explosion(bomb.x, bomb.y, 0.020f, 0.12f, 1f, 0.6f, 0.2f))
                        break
                    }
                }
                if (!b.alive) continue

                // Laser vs the mothership.
                val boss = this.boss
                if (boss != null && boss.alive &&
                    abs(b.x - boss.x) < World.BOSS_HW + 0.010f &&
                    abs(b.y - World.BOSS_Y) < World.BOSS_HH + 0.016f) {
                    b.alive = false
                    boss.hp--
                    explosionsList.add(Explosion(b.x, World.BOSS_Y + World.BOSS_HH, 0.035f, 0.12f, 1f, 0.6f, 0.1f))
                    if (boss.hp <= 0) {
                        boss.alive = false
                        val bonus = BOSS_SCORE.toLong() * level
                        score += bonus
                        flashBonus(bonus, boss.x, World.BOSS_Y)
                        spawnDebris(boss.x, World.BOSS_Y, World.BOSS_HW, 1f, 0.35f, 0.55f)
                        spawnDebris(boss.x, World.BOSS_Y, World.BOSS_HW * 0.6f, 0.85f, 0.25f, 0.75f)
                        sound(SoundEvent.EXPLOSION)
                        sound(SoundEvent.LEVEL_CLEAR)
                        endRound(State.WIN)
                        checkHighScore()
                        return   // the table is written: the rest of the volley scores nothing
                    }
                    continue
                }

                // Laser vs the saucer.
                val sc = saucer
                if (sc != null && abs(b.x - sc.x) < World.SAUCER_HW && abs(b.y - sc.y) < World.SAUCER_HH) {
                    b.alive = false
                    saucer = null
                    val bonus = SAUCER_SCORE.toLong() * level
                    score += bonus
                    flashBonus(bonus, sc.x, sc.y)
                    spawnDebris(sc.x, sc.y, 0.05f, 1f, 0.35f, 0.30f)
                    syncSiren()
                    sound(SoundEvent.EXPLOSION)
                    continue
                }

                // Laser vs invaders (box test — the sprites are wide and flat).
                for (a in aliensList) {
                    if (!a.alive) continue
                    val dx = b.x - alienX(a); val dy = b.y - alienY(a)
                    if (abs(dx) < World.ALIEN_HW + 0.010f && abs(dy) < World.ALIEN_HH + 0.016f) {
                        b.alive = false
                        killAlien(a)
                        break
                    }
                }
            }
        }
    }

    private fun updateSaucer(dt: Float) {
        val sc = saucer
        if (sc == null) {
            // Only tease the saucer while a wave is up — and never during the
            // boss fight, whose mothership owns the top of the screen.
            if (aliveAliens > 0 && boss == null) {
                saucerTimer -= dt
                if (saucerTimer <= 0f) {
                    spawnSaucer()
                    saucerTimer = frange(14f, 22f)
                }
            }
        } else {
            sc.x += sc.vx * dt
            if (sc.x > aspect + 0.1f || sc.x < -aspect - 0.1f) { saucer = null; syncSiren() }
        }
    }

    private fun updatePowerUps(dt: Float) {
        if (rapidTimer > 0f) { rapidTimer -= dt; if (rapidTimer <= 0f) rapidTimer = 0f }
        if (tripleTimer > 0f) { tripleTimer -= dt; if (tripleTimer <= 0f) tripleTimer = 0f }
        val reach = World.POWER_UP_R + World.SHIP_R
        for (pu in powerUpsList) {
            if (!pu.alive) continue
            pu.y += pu.vy * dt
            pu.rot += pu.spin * dt
            if (pu.y > 1.1f) { pu.alive = false; continue }
            val dx = pu.x - shipX; val dy = pu.y - World.SHIP_Y
            if (dx * dx + dy * dy < reach * reach) {
                pu.alive = false
                val bonus = POWER_UP_SCORE.toLong() * level
                score += bonus
                flashBonus(bonus, pu.x, pu.y - 0.06f)
                applyPowerUp(pu.type)
                sound(SoundEvent.POWER_UP)
            }
        }
        powerUpsList.removeAll { !it.alive }
    }

    private fun flashBonus(bonus: Long, x: Float, y: Float) {
        bonusFlash = bonus
        bonusFlashTimer = BONUS_FLASH_TIME
        bonusFlashX = x; bonusFlashY = y
    }

    private fun checkLevelClear() {
        if (state != State.PLAYING || aliveAliens > 0) return
        // The boss level only ends when the mothership dies (in updateLasers,
        // which jumps straight to WIN).
        val b = boss
        if (b != null && b.alive) return
        score += LEVEL_CLEAR_SCORE.toLong() * level
        sound(SoundEvent.LEVEL_CLEAR)
        state = State.LEVEL_CLEAR
        stateTimer = 0f
        bombsList.clear()
        lasersList.clear()
        saucer = null
        syncSiren()
    }

    // ── Auto Play ─────────────────────────────────────────────────────────────

    /** The autopilot's pick among the invaders (see [pickAlienTarget]). */
    private class AlienTarget {
        var valid = false          // an invader is alive
        var aimX = 0f              // lead-aimed x under the chosen column
        var floorUrgent = false    // the lowest row is about to reach the ship
        var linedUp = false        // some open column is in the line of fire now
        var shootable = false      // the pick has an invader no laser claims yet
        // Per column: lead-aimed x under its lowest unclaimed invader, and
        // whether it has one (a laser is already on the way to each claimed
        // invader).
        val aim = FloatArray(World.COLS)
        val open = BooleanArray(World.COLS)
        /** True at x when some open column can be shot from there. */
        fun firingSpot(x: Float): Boolean {
            for (c in 0 until World.COLS) if (open[c] && abs(aim[c] - x) < 0.030f) return true
            return false
        }
    }

    // Which invader to shoot next. A laser only ever reaches the bottom-most
    // invader of a column, so the choice is really a choice of column:
    //  - Nearest column, while the wave is no threat: the fastest clear.
    //  - Column targeting, under descent pressure: the wave steps down every
    //    time its outermost alive column touches a side margin, so the
    //    narrower the wave, the longer each crossing and the slower the
    //    descent. Clear an edge column first (the one with fewer invaders
    //    left; nearer on a tie). On a portrait phone a full wave has almost
    //    no room to march and comes down faster than it can be shot.
    //  - Bottom-row targeting, as a last resort: an invader level with the
    //    ship can no longer be shot, only collided with. When the lowest row
    //    is about to get there, its invaders come first — each row cleared
    //    buys two more edge bounces.
    // Lasers already in the air are counted against the columns they will
    // hit: a column with as many lasers on the way as invaders left is
    // finished, and the next volley goes to the next column. In
    // nearest-column mode a column whose firing spot is under a bomb, or
    // behind a bomb lane the ship would have to cross as the bomb comes down,
    // is passed over for one that can be reached now. `hazard(x)` is the
    // cost of going to stand at x.
    private fun pickAlienTarget(hazard: (Float) -> Float): AlienTarget {
        val out = AlienTarget()
        // Alive rows per column, bottom-most first: the order lasers reach them.
        val rowsUp = Array(World.COLS) { IntArray(World.MAX_ROWS) }
        val count = IntArray(World.COLS)
        for (r in rows - 1 downTo 0) for (a in aliensList) {
            if (!a.alive || a.row != r) continue
            if (count[a.col] < World.MAX_ROWS) rowsUp[a.col][count[a.col]++] = r
        }
        var colMin = -1; var colMax = -1; var floorRow = -1; var alive = 0; var floorCount = 0
        for (c in 0 until World.COLS) {
            if (count[c] == 0) continue
            if (colMin < 0) colMin = c
            colMax = c
            alive += count[c]
            if (rowsUp[c][0] > floorRow) { floorRow = rowsUp[c][0]; floorCount = 0 }
            if (rowsUp[c][0] == floorRow) floorCount++
        }
        if (colMin < 0) return out
        out.valid = true

        fun colX(c: Int) = formationX + (c - (World.COLS - 1) * 0.5f) * World.COL_STEP
        // The wave reverses when its outermost alive invader reaches a side
        // margin, so a lead can't run past the bounce point — it folds back.
        val edgeX = aspect - World.SIDE_MARGIN - World.ALIEN_HW
        val distToBounce = (if (marchDir > 0) edgeX - colX(colMax) else colX(colMin) + edgeX).coerceAtLeast(0f)
        val v = marchSpeed
        // Where column c will be `t` seconds from now.
        fun colXAt(c: Int, t: Float): Float {
            val travel = v * t
            val off = if (travel <= distToBounce) travel else distToBounce - (travel - distToBounce)
            return colX(c) + marchDir * off
        }
        fun rowY(r: Int) = formationY + r * World.ROW_STEP

        // Claim the invaders that lasers already in the air will kill. Only
        // the straight laser is counted: the side lasers are a bonus. Higher
        // lasers land first, so they claim first.
        val claimed = IntArray(World.COLS)
        val straight = lasersList.filter { it.alive && it.vx == 0f && it.vy < 0f }.sortedBy { it.y }
        for (b in straight) {
            for (c in 0 until World.COLS) {
                if (claimed[c] >= count[c]) continue
                val y = rowY(rowsUp[c][claimed[c]])
                if (b.y < y) continue                     // already past that row
                val t = (b.y - y) / World.LASER_SPEED
                if (abs(colXAt(c, t) - b.x) < World.ALIEN_HW + 0.010f) { claimed[c]++; break }
            }
        }
        // The leftmost / rightmost column that still has an unclaimed invader.
        var openMin = -1; var openMax = -1; var openFloor = 0
        for (c in 0 until World.COLS) {
            if (claimed[c] >= count[c]) continue
            if (openMin < 0) openMin = c
            openMax = c
            if (rowsUp[c][claimed[c]] == floorRow) openFloor++
        }

        // Aim point per column: under its lowest unclaimed invader, lead-aimed.
        val aim = out.aim
        for (c in 0 until World.COLS) {
            if (claimed[c] >= count[c]) continue
            val y = rowY(rowsUp[c][claimed[c]])
            aim[c] = colXAt(c, (World.SHIP_Y - y) / World.LASER_SPEED)
            out.open[c] = true
            if (abs(aim[c] - shipX) < 0.030f) out.linedUp = true
        }

        // Descent model: the lowest row is `drops` edge bounces away from the
        // ship's line; the first bounce is distToBounce away, every later one
        // a full crossing of the room the wave has left to march in.
        val floorLine = World.SHIP_Y - (World.ALIEN_HH + World.SHIP_R * 0.8f)
        val floorY = rowY(floorRow)
        val room = (2f * edgeX - (colMax - colMin) * World.COL_STEP).coerceAtLeast(0.01f)
        val drops = ceil((floorLine - floorY) / World.DESCEND).toInt()
        val path = if (drops <= 0) 0f else distToBounce + (drops - 1) * room
        val cooldown = if (rapidActive) World.RAPID_COOLDOWN else World.FIRE_COOLDOWN

        // The bottom row: urgent once it would arrive before its invaders can
        // be shot one cooldown apart, plus a beat to line up.
        out.floorUrgent = path / v < floorCount * cooldown + 0.8f
        // Pressure: the wave, kept this wide and marching at its end-of-wave
        // speed, would reach the ship before a volley per invader clears it.
        val pressure = path / (Levels.marchSpeed(level) * World.THIN_WAVE_SPEEDUP) < alive * cooldown

        if (openMin < 0) {
            // Every invader left has a laser on the way: nothing to shoot yet.
            // Wait under the lowest one so the next target is close.
            out.aimX = colXAt(colMin, 0f)
            for (c in colMin..colMax) if (count[c] > 0 && rowsUp[c][0] == floorRow) { out.aimX = colXAt(c, 0f); break }
            return out
        }
        out.shootable = true
        // Cost of going to shoot column c: the walk, plus any hazard on the
        // way or at the spot (an order of magnitude more than any walk, so a
        // blocked column only wins when every other one is blocked too).
        fun cost(c: Int) = abs(aim[c] - shipX) + hazard(aim[c])
        fun nearest(floorOnly: Boolean): Int {
            var pick = -1
            var best = 1e9f
            for (c in 0 until World.COLS) {
                if (claimed[c] >= count[c]) continue
                if (floorOnly && rowsUp[c][claimed[c]] != floorRow) continue
                val d = cost(c)
                if (d < best) { best = d; pick = c }
            }
            return pick
        }
        // Under pressure the edge pick ignores hazards on purpose: swapping
        // edges whenever a bomb came down near one broke the narrowing plan.
        // The steer waits the bomb out.
        val left = count[openMin] - claimed[openMin]
        val right = count[openMax] - claimed[openMax]
        val pick = when {
            out.floorUrgent && openFloor > 0 -> nearest(true)
            !pressure -> nearest(false)
            left != right -> if (left < right) openMin else openMax
            else -> if (abs(aim[openMin] - shipX) <= abs(aim[openMax] - shipX)) openMin else openMax
        }
        out.aimX = aim[pick]
        return out
    }

    // Drives the same control path as a finger in the control strip: it sets
    // a steering target (aiTargetX / aiMove) and holds the trigger (aiFire).
    // Priorities: dodge incoming bombs > collect a bonus that is about to
    // land > hunt the saucer > the invader column pickAlienTarget() chose —
    // which outranks the saucer when the wave is about to reach the ship,
    // and the mothership for as long as any of her escort is alive.
    private fun updateAutoPlay() {
        // The triple shot covers a wider cone, so alignment can be looser.
        val alignThresh = if (tripleActive) 0.10f else 0.030f
        val horizon = 0.9f   // seconds of bomb look-ahead
        val radius = 0.11f   // an impact this close to x counts as a hit
        val lim = aspect - World.SHIP_SCALE

        // 1) Threat model: how much incoming fire lands near x within the
        //    horizon, urgency-weighted (boss bombs drift sideways — predict
        //    the impact x). Scoring candidate positions against every bomb
        //    keeps a dodge from sidestepping into the next bomb over.
        fun dangerAt(x: Float): Float {
            var d = 0f
            for (b in bombsList) {
                if (!b.alive || b.vy <= 0f) continue
                var tHit = (World.SHIP_Y - b.y) / b.vy
                // A slightly negative tHit: the bomb just crossed our altitude
                // but still overlaps the ship's collision band.
                if (tHit < -0.15f || tHit > horizon) continue
                if (tHit < 0f) tHit = 0f
                val dx = abs(b.x + b.vx * tHit - x)
                if (dx < radius) d += (radius - dx) * (horizon - tHit)
            }
            return d
        }
        // Lethality of walking from the ship to x: a bomb lane on the way
        // only matters if the ship would cross it at about the moment the
        // bomb reaches our altitude.
        fun crossDanger(x: Float): Float {
            var d = 0f
            val dir = if (x >= shipX) 1f else -1f
            val span = (x - shipX) * dir
            for (b in bombsList) {
                if (!b.alive || b.vy <= 0f) continue
                var tHit = (World.SHIP_Y - b.y) / b.vy
                if (tHit < -0.15f || tHit > horizon) continue
                if (tHit < 0f) tHit = 0f
                val along = (b.x + b.vx * tHit - shipX) * dir
                if (along < 0f || along > span) continue   // the lane is not on the way
                val gap = abs(tHit - along / World.SHIP_SPEED)
                val crossWin = 0.25f   // arrive within this of the bomb: lethal
                if (gap < crossWin) d += crossWin - gap
            }
            return d
        }
        // Cost of going to stand at x: zero with clear skies.
        fun hazard(x: Float) = dangerAt(x) * 100f + crossDanger(x) * 50f
        var soonestT = 1e9f; var soonestX = 0f
        for (b in bombsList) {
            if (!b.alive || b.vy <= 0f) continue
            val tHit = (World.SHIP_Y - b.y) / b.vy
            if (tHit < 0f || tHit > horizon || tHit >= soonestT) continue
            val impactX = b.x + b.vx * tHit
            if (abs(impactX - shipX) < radius) { soonestT = tHit; soonestX = impactX }
        }
        val threatened = soonestT < 1e9f

        // 2) The best shoot target. The boss level is won by killing the
        //    mothership, but her escort still marches down on the ship while
        //    she soaks up her hits — so the escort goes first, then the boss
        //    (lead-aimed on her sine drift). The saucer outranks the wave
        //    unless the wave is about to reach the ship.
        val wave = pickAlienTarget(::hazard)
        val boss = this.boss
        val bossUp = boss != null && boss.alive

        // Escape plan: sample positions outward from the ship and take the
        // cheapest — safe beats close, close beats far, positions we can't
        // reach before the soonest impact are penalised, a spot the chosen
        // column can be shot from beats one where the ship would stand idle,
        // and exact ties break away from that impact.
        var dodgeX = shipX
        if (threatened) {
            val reach = World.SHIP_SPEED * soonestT
            var best = 1e9f
            for (k in -8..8) {
                val x = (shipX + k * 0.08f).coerceIn(-lim, lim)
                val dist = abs(x - shipX)
                var cost = hazard(x) + dist * 0.01f
                if (dist > reach) cost += 1f
                if (wave.shootable && abs(x - wave.aimX) > 0.03f) cost += 0.005f
                if ((x - shipX) * (soonestX - shipX) > 0f) cost += 0.0001f
                if (cost < best) { best = cost; dodgeX = x }
            }
        }

        var hasTarget = false
        var saucerLinedUp = false
        var targetX = 0f
        val waveFirst = wave.valid && (wave.floorUrgent || bossUp)
        if (waveFirst) { targetX = wave.aimX; hasTarget = true }
        if (!hasTarget && boss != null && bossUp) {
            val tFly = (World.SHIP_Y - World.BOSS_Y) / World.LASER_SPEED
            targetX = sin((boss.t + tFly) * 0.60f) * (aspect - World.BOSS_HW - 0.05f)
            hasTarget = true
        }
        val sc = saucer
        if (!hasTarget && sc != null) {
            // One laser is enough: once a straight laser is on course to hit
            // the saucer, the hunt is over and the volley goes back to the wave.
            var claimed = false
            for (b in lasersList) {
                if (!b.alive || b.vx != 0f || b.vy >= 0f || b.y < sc.y) continue
                val t = (b.y - sc.y) / World.LASER_SPEED
                if (abs(sc.x + sc.vx * t - b.x) < World.SAUCER_HW) { claimed = true; break }
            }
            // And it is only worth a short detour: it crosses the whole
            // screen, so a saucer far off will come by on its own while the
            // wave is being shot, and it is taken as a shot of opportunity.
            val saucerChase = 0.80f
            val tFly = (World.SHIP_Y - sc.y) / World.LASER_SPEED
            val aim = sc.x + sc.vx * tFly
            if (!claimed && abs(aim) < lim && abs(aim - shipX) < saucerChase) { targetX = aim; hasTarget = true }
            if (!claimed && abs(aim - shipX) < alignThresh) saucerLinedUp = true
        }
        var waveShot = false   // the chosen target is the wave: fire only when it has an open invader
        if (!hasTarget && wave.valid) { targetX = wave.aimX; hasTarget = true; waveShot = true }

        // 3) Bonus interception, only when nothing is shooting at us — and
        //    only once the pickup is about to land: a bonus falls for two to
        //    three seconds, and parking under it that long costs five volleys.
        //    A second shield while one is up does nothing, so it is not worth
        //    the walk.
        var collect: PowerUp? = null
        if (!threatened) {
            var bestT = 1e9f
            for (pu in powerUpsList) {
                if (!pu.alive) continue
                if (pu.type == PowerUpType.SHIELD && shieldActive) continue
                var tFall = (World.SHIP_Y - pu.y) / pu.vy
                if (tFall < -0.3f) continue            // already dropped past the ship
                if (tFall < 0f) tFall = 0f
                val tSteer = abs(pu.x - shipX) / World.SHIP_SPEED
                if (tSteer > tFall + 0.8f) continue    // can't reach it before it escapes
                if (tFall > tSteer + 0.6f) continue    // not yet: keep shooting
                val t = maxOf(tSteer, tFall)
                if (t < bestT) { bestT = t; collect = pu }
            }
        }

        // Steer toward `desired` without volunteering into fire: stopping
        // points (the exact target included) are scored by whether a bomb
        // lands there and whether the walk crosses a bomb lane just as its
        // bomb arrives, so a hunt or collect route stops at the edge of a hot
        // lane instead of marching under a falling bomb — and, when it has
        // to stop short, under a column it can shoot meanwhile. With clear
        // skies every cost is zero and `desired` wins unchanged.
        fun steerTo(desiredIn: Float): Float {
            val desired = desiredIn.coerceIn(-lim, lim)
            fun costAt(x: Float) = hazard(x) + abs(x - desired) + (if (wave.firingSpot(x)) 0f else 0.02f)
            var bestX = desired
            var best = costAt(desired)
            for (k in -8..8) {
                val x = (shipX + k * 0.08f).coerceIn(-lim, lim)
                val c = costAt(x)
                if (c < best) { best = c; bestX = x }
            }
            return bestX
        }

        // Steering priority: dodge > collect > align.
        val pu = collect
        when {
            threatened -> { aiTargetX = dodgeX; aiMove = true }
            pu != null -> { aiTargetX = steerTo(pu.x); aiMove = true }
            hasTarget -> { aiTargetX = steerTo(targetX); aiMove = true }
        }

        // Fire whenever we're lined up on something worth hitting — the
        // target, or any column that happens to be overhead on the way to it
        // or during a dodge (every invader has to go eventually)…
        val onWave = waveFirst || waveShot
        if (hasTarget && (!onWave || wave.shootable) && abs(targetX - shipX) < alignThresh) aiFire = true
        if (wave.linedUp || saucerLinedUp) aiFire = true
        // …and at any bomb above us in our firing column: the laser-vs-bomb
        // check cancels both, clearing the lane the ship is dodging through
        // (or standing in, when the dodge can't outrun it).
        if (!aiFire) {
            for (b in bombsList) {
                if (!b.alive || b.vy <= 0f || b.y >= World.SHIP_Y) continue
                if (abs(b.x - shipX) < 0.025f) { aiFire = true; break }
            }
        }
    }

    // ── High scores ───────────────────────────────────────────────────────────

    /** Insert a score at its rank unless the identical entry is already
     *  present. Returns the rank, or -1 when not ranked. */
    private fun mergeHighScore(score: Long, level: Int): Int {
        if (score <= 0) return -1
        if (table.any { it.score == score && it.level == level }) return -1
        var pos = table.indexOfFirst { score > it.score }
        if (pos < 0) pos = table.size
        if (pos >= MAX_SCORES) return -1
        table.add(pos, HighScore(score, level))
        while (table.size > MAX_SCORES) table.removeAt(table.size - 1)
        return pos
    }

    private fun checkHighScore() {
        if (score <= 0) return
        val pos = mergeHighScore(score, level)
        if (pos < 0) return
        newHighScore = true
        newHighScoreRank = pos
        host.highScoresChanged(table.toList())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sound(event: SoundEvent) {
        if (soundEnabled) host.sound(event)
    }

    /** The single rule for the music flag: it plays from the start of a round
     *  through the end screen, when sound is on, and not on the phone while
     *  the round runs on the glasses. */
    private fun syncMusic() {
        val handedOff = glassesActive && controlMode == ControlMode.STRIP
        host.music(soundEnabled && state != State.TITLE && !handedOff)
    }

    /** The siren sounds while a saucer crosses, the round runs and sound is on. */
    private fun syncSiren() {
        val handedOff = glassesActive && controlMode == ControlMode.STRIP
        val on = soundEnabled && saucer != null && state == State.PLAYING && !paused && !handedOff
        if (on != saucerSiren) { saucerSiren = on; host.saucer(on) }
    }

    private fun frand(): Float {
        var x = rng
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        rng = x
        return (x and 0xFFFFFF).toFloat() / 0x1000000.toFloat()
    }

    private fun frange(a: Float, b: Float): Float = a + (b - a) * frand()

    // ── Debug hooks: tests, previews and the bench ────────────────────────────

    /** Ways into the middle of a game for tests, previews and the bench. The
     *  rules are never bypassed: an invader killed here still counts as
     *  cleared on the next update, a bomb placed here falls by the same physics. */
    inner class Debug internal constructor() {
        fun startGame() = this@Game.startGame()
        fun startLevel(level: Int) = this@Game.startLevel(level)
        fun killAlien(i: Int) { aliensList[i].alive = false }
        /** Kill every invader but the one at [keep]. */
        fun keepOnlyAlien(keep: Int) { for (i in aliensList.indices) aliensList[i].alive = i == keep }
        fun setFormation(x: Float = formationX, y: Float = formationY) { formationX = x; formationY = y }
        fun setShipX(x: Float) { shipX = x; clampShip() }
        fun clearInvuln() { invuln = 0f }
        fun spawnBomb(x: Float, y: Float, vy: Float, vx: Float = 0f) { bombsList.add(Bomb(x, y, vx, vy, 0f)) }
        fun spawnPowerUp(x: Float, y: Float, type: PowerUpType) { powerUpsList.add(PowerUp(x, y, World.POWER_UP_SPEED, 0f, 1.5f, type)) }
        fun activatePowerUp(type: PowerUpType) = applyPowerUp(type)
        fun spawnSaucer() = this@Game.spawnSaucer()
        fun setSaucer(x: Float, vx: Float) { saucer = Saucer(x, World.SAUCER_Y, vx); syncSiren() }
        fun setBossTime(t: Float) { boss?.t = t }
        fun setScore(s: Long) { score = s }
        fun setLives(n: Int) { lives = n }
        /** The trigger is ready: the cooldown is over and fewer than [World.MAX_LASERS] fly. */
        val fireReady: Boolean get() = fireCooldown <= 0f && lasersList.count { it.alive } < World.MAX_LASERS
    }
    val debug: Debug = Debug()

    companion object {
        const val MAX_SCORES = 5
        const val START_LIVES = 3
        /** The simulation's fixed step: 120 ticks a second. */
        const val TICK = 1f / 120f
        /** A frame longer than this (a hitch, a return from the background) counts as this. */
        const val MAX_FRAME = 0.05f
        const val LEVEL_CLEAR_PAUSE = 1.8f
        const val END_SCREEN_GRACE = 0.6f       // an end screen ignores taps this long
        const val DROP_CHANCE = 0.08f           // per kill
        const val SAUCER_SCORE = 100
        const val POWER_UP_SCORE = 25
        const val LEVEL_CLEAR_SCORE = 100
        const val BOSS_SCORE = 250              // times the level (2500 at level 10)
        const val BONUS_FLASH_TIME = 0.9f
        private const val SHIP_DEADBAND = 0.012f
        private const val LASER_STEP = 0.02f    // laser collision substep length

        /** Per-tier tint as (r, g, b), shared by the sprites and their debris. */
        val TIER_COLORS: List<FloatArray> = listOf(
            floatArrayOf(0.78f, 0.45f, 1.00f),   // squid: purple
            floatArrayOf(0.30f, 0.95f, 0.55f),   // crab: green
            floatArrayOf(1.00f, 0.70f, 0.20f),   // octopus: amber
        )
    }
}

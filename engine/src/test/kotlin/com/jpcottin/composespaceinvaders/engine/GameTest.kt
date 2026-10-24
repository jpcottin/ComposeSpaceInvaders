package com.jpcottin.composespaceinvaders.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class InitialStateTest {
    @Test fun startsOnTheTitleWithADemoWave() {
        val g = newGame()
        assertEquals(Game.State.TITLE, g.state)
        assertEquals(24, g.aliens.size)
        g.run(1f)
        assertEquals(Game.State.TITLE, g.state)
    }

    @Test fun aTapStartsAGame() {
        val g = newGame()
        g.tap(); g.update(FRAME)
        assertEquals(Game.State.PLAYING, g.state)
        assertEquals(1, g.level); assertEquals(3, g.lives); assertEquals(0L, g.score)
        assertEquals(0f, g.shipX, 1e-6f)
        assertTrue(g.lasers.isEmpty())
    }

    @Test fun levelOneFieldsThreeRowsOfEight() {
        val g = playing()
        assertEquals(3, g.rows)
        assertEquals(24, g.aliveAliens)
        assertEquals(Game.AlienType.SQUID, g.aliens.first { it.row == 0 }.type)
        assertEquals(Game.AlienType.CRAB, g.aliens.first { it.row == 1 }.type)
        assertEquals(Game.AlienType.OCTOPUS, g.aliens.first { it.row == 2 }.type)
    }

    @Test fun aFiveRowWaveHasTwoOctopusRows() {
        val g = playing(5)
        assertEquals(5, g.rows)
        assertEquals(Game.AlienType.OCTOPUS, g.aliens.first { it.row == 3 }.type)
        assertEquals(Game.AlienType.OCTOPUS, g.aliens.first { it.row == 4 }.type)
        assertEquals(Game.AlienType.CRAB, g.aliens.first { it.row == 2 }.type)
    }
}

class MarchTest {
    @Test fun theWaveStartsMovingRight() {
        val g = playing()
        val x0 = g.formationX
        g.run(0.5f)
        assertEquals(1, g.marchDir)
        assertTrue(g.formationX > x0)
    }

    @Test fun theWaveReversesAndDescendsAtTheRightEdge() {
        val g = playing()
        val y0 = g.formationY
        assertTrue(g.runUntil(30f) { marchDir < 0 })
        assertEquals(y0 + World.DESCEND, g.formationY, 1e-5f)
    }

    @Test fun theWaveStaysWithinTheSideMargins() {
        val g = playing()
        repeat(60 * 20) {
            g.update(FRAME)
            for (a in g.aliens) if (a.alive) {
                assertTrue(g.alienX(a) + World.ALIEN_HW <= g.aspect - World.SIDE_MARGIN + 0.01f)
                assertTrue(g.alienX(a) - World.ALIEN_HW >= -g.aspect + World.SIDE_MARGIN - 0.01f)
            }
        }
    }

    @Test fun theWaveMarchesFasterAsItThins() {
        val g = playing()
        val full = g.marchSpeed
        for (i in 0 until 20) g.debug.killAlien(i)
        assertTrue(g.marchSpeed > full * 2f)
        g.debug.keepOnlyAlien(23)
        assertEquals(Levels.marchSpeed(1) * World.THIN_WAVE_SPEEDUP, g.marchSpeed, 0.01f)
    }

    @Test fun theMarchBeatPlaysTheFourNoteBass() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.run(4f)
        assertTrue(host.marches.size >= 3)
        assertEquals(listOf(1, 2, 3), host.marches.take(3))
    }

    @Test fun anInvasionEndsTheGameWhateverTheLives() {
        val g = playing()
        g.debug.setLives(3)
        g.debug.setFormation(y = World.INVADE_Y - World.ALIEN_HH - 2 * World.ROW_STEP)   // the bottom row's edge on the line
        g.update(FRAME)
        assertEquals(Game.State.GAME_OVER, g.state)
        assertEquals(0, g.lives)
        assertEquals(Game.DeathCause.INVASION, g.deathCause)
    }

    @Test fun anInvasionEndsTheGameEvenThroughAShield() {
        val g = playing()
        g.debug.activatePowerUp(Game.PowerUpType.SHIELD)
        g.debug.setFormation(y = World.INVADE_Y - World.ALIEN_HH - 2 * World.ROW_STEP)
        g.update(FRAME)
        assertEquals(Game.State.GAME_OVER, g.state)
    }

    @Test fun anInvaderCollidingWithTheShipCostsALifeAndDies() {
        val g = playing()
        g.debug.clearInvuln()
        // The bottom row level with the ship, column 3 right over it.
        val col3 = g.aliens.first { it.row == 2 && it.col == 3 }
        g.debug.setFormation(x = -g.alienX(col3), y = World.SHIP_Y - 2 * World.ROW_STEP)
        g.debug.setShipX(0f)
        g.update(FRAME)
        assertEquals(2, g.lives)
        assertFalse(col3.alive)
        assertEquals(Game.State.PLAYING, g.state)
    }
}

class ShipControlTest {
    @Test fun aFingerOnTheStripSteersTheShipTowardIt() {
        val g = playing()
        g.touchStrip(0.30f)
        g.run(0.1f)
        assertTrue(g.shipX > 0.05f)
        assertTrue(g.shipX < 0.30f)
    }

    @Test fun aFingerAboveTheStripIsIgnored() {
        val g = playing()
        g.onPointerDown(0, 0.30f, 0.0f)
        g.run(0.5f)
        assertEquals(0f, g.shipX, 1e-6f)
        assertTrue(g.lasers.isEmpty())
    }

    @Test fun theShipStopsOnTheFingerX() {
        val g = playing()
        g.touchStrip(0.25f)
        g.run(1f)
        assertEquals(0.25f, g.shipX, World.SHIP_SPEED * FRAME)
    }

    @Test fun theShipIsClampedToTheScreenEdge() {
        val g = playing()
        g.touchStrip(5f)
        g.run(2f)
        assertEquals(g.aspect - World.SHIP_SCALE, g.shipX, 1e-5f)
    }

    @Test fun aNarrowerViewKeepsTheShipOnScreen() {
        val g = playing(aspect = 0.8f)
        g.touchStrip(5f); g.run(2f)
        g.setAspect(0.45f)
        assertEquals(0.45f - World.SHIP_SCALE, g.shipX, 1e-5f)
    }

    @Test fun aDragSteersTheShip() {
        val g = playing()
        g.touchStrip(0.2f); g.run(0.5f)
        g.onPointerMove(0, -0.2f, 0.9f); g.run(0.5f)
        assertEquals(-0.2f, g.shipX, World.SHIP_SPEED * FRAME)
    }

    @Test fun liftingOrCancellingTheFingerStopsTheShip() {
        val g = playing()
        g.touchStrip(0.4f); g.run(0.1f)
        val x = g.shipX
        g.onPointerUp(0); g.run(0.3f)
        assertEquals(x, g.shipX, 1e-6f)
        g.touchStrip(0.4f); g.onPointersCancel(); g.run(0.3f)
        assertEquals(x, g.shipX, 1e-6f)
    }

    @Test fun aKeySteersTheShipUntilReleased() {
        val g = playing()
        g.setSteer(-1); g.run(0.2f)
        assertTrue(g.shipX < -0.2f)
        val x = g.shipX
        g.setSteer(0); g.run(0.2f)
        assertEquals(x, g.shipX, 1e-6f)
    }

    @Test fun theShipLeansIntoItsTravel() {
        val g = playing()
        g.setSteer(1); g.run(0.15f)
        assertTrue(g.shipTilt > 0.15f)
        g.setSteer(0); g.run(1f)
        assertEquals(0f, g.shipTilt, 0.01f)
    }
}

class FiringTest {
    @Test fun aFingerOnTheStripFires() {
        val g = playing()
        g.touchStrip(0f); g.update(FRAME)
        assertEquals(1, g.lasers.size)
        assertTrue(g.lasers[0].vy < 0f)
    }

    @Test fun aFireKeyFires() {
        val g = playing()
        g.setFire(true); g.update(FRAME)
        assertEquals(1, g.lasers.size)
        g.setFire(false); g.run(1f)
        assertEquals(1, g.shotsFired)
    }

    @Test fun theCooldownLimitsTheRate() {
        val g = playing()
        g.setFire(true); g.run(0.3f)
        assertEquals(1, g.shotsFired)
        g.run(0.2f)
        assertEquals(2, g.shotsFired)
    }

    @Test fun atMostThreeLasersFlyAtOnce() {
        val g = playing()
        g.debug.setFormation(y = -5f)   // nothing to hit
        g.setFire(true)
        repeat(120) { g.update(FRAME); assertTrue(g.lasers.size <= World.MAX_LASERS) }
    }

    @Test fun lasersDespawnAtTheTop() {
        val g = playing()
        g.debug.setFormation(y = -5f)
        g.setFire(true); g.update(FRAME); g.setFire(false)
        g.run(1.5f)
        assertTrue(g.lasers.isEmpty())
        assertEquals(1, g.lasersLost)
    }
}

/** A lone invader straight above the ship, close enough that the lead does not matter. */
private fun Game.loneAlienOverhead(index: Int): Game.Alien {
    debug.keepOnlyAlien(index)
    val a = aliens[index]
    debug.setFormation(x = -(a.col - (World.COLS - 1) * 0.5f) * World.COL_STEP, y = 0.40f - a.row * World.ROW_STEP)
    debug.setShipX(0f)
    return a
}

class KillsTest {
    @Test fun aLaserKillsAnInvaderAndScores() {
        val g = playing()
        g.touchStrip(0f)
        assertTrue(g.runUntil(3f) { aliveAliens < 24 })
        assertTrue(g.score > 0)
    }

    private fun scoreFor(row: Int, level: Int): Long {
        val g = playing(level)
        val a = g.loneAlienOverhead(row * World.COLS + 3)
        g.setFire(true)
        assertTrue(g.runUntil(1f) { !a.alive })
        return g.score - Game.LEVEL_CLEAR_SCORE * level   // the lone kill also clears the wave
    }

    @Test fun theBottomRowOctopusIsWorthTen() = assertEquals(10L, scoreFor(2, 1))
    @Test fun theMiddleRowCrabIsWorthTwenty() = assertEquals(20L, scoreFor(1, 1))
    @Test fun theTopRowSquidIsWorthThirty() = assertEquals(30L, scoreFor(0, 1))
    @Test fun killPointsScaleWithTheLevel() = assertEquals(60L, scoreFor(0, 2))

    @Test fun aLaserStillKillsAtTheClampedWorstCaseFrame() {
        val g = playing()
        val a = g.loneAlienOverhead(2 * World.COLS + 3)
        g.setFire(true)
        var t = 0f
        while (t < 2f && a.alive) { g.update(1f); t += Game.MAX_FRAME }   // a huge hitch, clamped
        assertFalse(a.alive)
    }

    @Test fun aKillDropsABonusNowAndThen() {
        val g = playing()
        var drops = 0
        for (seed in 1..40) {
            val h = playing()
            h.seed(seed)
            h.loneAlienOverhead(2 * World.COLS + 3)
            h.setFire(true); h.runUntil(1f) { aliveAliens == 0 }
            if (h.powerUps.isNotEmpty()) drops++
        }
        assertTrue("$drops drops in 40 kills", drops in 1..12)
        assertEquals(Game.State.PLAYING, g.state)
    }
}

class BombsTest {
    @Test fun invadersEventuallyDropBombs() {
        val g = playing()
        assertTrue(g.runUntil(5f) { bombs.isNotEmpty() })
        assertTrue(g.bombs[0].vy > 0f)
    }

    @Test fun concurrentBombsAreCapped() {
        val g = playing(10)
        g.debug.setFormation(y = -5f)   // the escort fires but never arrives
        repeat(60 * 10) { g.update(FRAME); assertTrue(g.bombs.count { it.vx == 0f } <= Levels.maxBombs(10)) }
    }

    @Test fun aBombHitCostsALife() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.debug.clearInvuln()
        g.debug.spawnBomb(0f, 0.50f, 1f)
        g.run(0.2f)
        assertEquals(2, g.lives)
        assertEquals(1, host.haptics)
        assertTrue(host.sounds.contains(SoundEvent.SHIP_HIT))
    }

    @Test fun invulnerabilityBlocksBackToBackHits() {
        val g = playing()
        g.debug.clearInvuln()
        g.debug.spawnBomb(0f, 0.50f, 1f); g.run(0.2f)
        g.debug.spawnBomb(0f, 0.50f, 1f); g.run(0.2f)
        assertEquals(2, g.lives)
    }

    @Test fun missedBombsDespawnOffScreen() {
        val g = playing()
        g.debug.spawnBomb(0.3f, 0.9f, 1f)
        g.run(0.5f)
        assertTrue(g.bombs.isEmpty())
        assertEquals(3, g.lives)
    }

    @Test fun aLaserShootsDownABomb() {
        val g = playing()
        g.debug.setFormation(y = -5f)
        g.debug.spawnBomb(0f, 0.30f, 0.1f)
        g.setFire(true)
        assertTrue(g.runUntil(1f) { bombsShot == 1 })
        assertTrue(g.bombs.isEmpty())
        assertEquals(3, g.lives)
    }

    @Test fun aLaserStillInterceptsABombAtTheClampedWorstCaseFrame() {
        val g = playing()
        g.debug.setFormation(y = -5f)
        g.debug.spawnBomb(0f, 0.30f, 0.1f)
        g.setFire(true)
        var t = 0f
        while (t < 2f && g.bombsShot == 0) { g.update(1f); t += Game.MAX_FRAME }
        assertEquals(1, g.bombsShot)
    }

    @Test fun threeHitsEndTheGame() {
        val g = playing()
        repeat(3) {
            g.debug.clearInvuln()
            g.debug.spawnBomb(g.shipX, 0.50f, 1f); g.run(0.2f)
        }
        assertEquals(0, g.lives)
        assertEquals(Game.State.GAME_OVER, g.state)
        assertEquals(Game.DeathCause.BOMB, g.deathCause)
    }

    @Test fun theDeathFrameDoesNotScore() {
        val g = playing()
        g.debug.setLives(1); g.debug.clearInvuln()
        g.loneAlienOverhead(2 * World.COLS + 3)
        g.setFire(true); g.update(FRAME)   // a laser is on its way
        g.debug.spawnBomb(0f, World.SHIP_Y - 0.03f, 1f)
        g.update(FRAME)
        assertEquals(Game.State.GAME_OVER, g.state)
        val score = g.score
        g.run(1f)
        assertEquals(score, g.score)
    }
}

class SaucerTest {
    @Test fun theSaucerCrossesAndDespawns() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.debug.setFormation(y = -5f)
        g.debug.spawnSaucer()
        assertNotNull(g.saucer)
        assertTrue(host.siren)
        assertTrue(g.runUntil(6f) { saucer == null })
        assertFalse(host.siren)
    }

    @Test fun shootingTheSaucerPaysTheBonus() {
        val g = playing(2)
        g.debug.setFormation(y = -5f)
        g.debug.setSaucer(0f, 0f)
        g.setFire(true)
        assertTrue(g.runUntil(2f) { saucer == null })
        assertEquals(200L, g.score)
        assertEquals(200L, g.bonusFlash)
    }

    @Test fun theSaucerNeverShowsDuringTheBossFight() {
        val g = playing(10)
        g.debug.setFormation(y = -5f)
        g.run(40f)
        assertNull(g.saucer)
    }
}

class PowerUpsTest {
    @Test fun collectingAShieldActivatesIt() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.debug.spawnPowerUp(0f, World.SHIP_Y - 0.01f, Game.PowerUpType.SHIELD)
        g.run(0.1f)
        assertTrue(g.shieldActive)
        assertEquals(25L, g.score)
        assertEquals(1, host.ticks)                            // a tick under the finger
        assertEquals(0, host.haptics)                          // not the hit's click
    }

    @Test fun theShieldAbsorbsOneHitThenGrantsAGrace() {
        val g = playing()
        g.debug.activatePowerUp(Game.PowerUpType.SHIELD)
        g.debug.clearInvuln()
        g.debug.spawnBomb(0f, 0.50f, 1f); g.run(0.2f)
        assertEquals(3, g.lives)
        assertFalse(g.shieldActive)
        assertTrue(g.invuln > 0f)
    }

    @Test fun theShieldPersistsUntilHit() {
        val g = playing()
        g.debug.activatePowerUp(Game.PowerUpType.SHIELD)
        g.debug.setFormation(y = -5f)   // the wave's bombs take over ten seconds to arrive
        g.run(8f)
        assertTrue(g.shieldActive)
    }

    @Test fun rapidFireShortensTheCooldownAndExpires() {
        val g = playing()
        g.debug.setFormation(y = -5f)
        g.debug.activatePowerUp(Game.PowerUpType.RAPID)
        g.setFire(true); g.run(0.5f)
        assertEquals(3, g.shotsFired)   // 0.0, 0.2, 0.4 s
        g.run(World.POWER_UP_DURATION)
        assertFalse(g.rapidActive)
    }

    @Test fun tripleShotFiresThreeAngledLasers() {
        val g = playing()
        g.debug.activatePowerUp(Game.PowerUpType.TRIPLE)
        g.setFire(true); g.update(FRAME)
        assertEquals(3, g.lasers.size)
        val side = World.LASER_SPEED * sin(World.TRIPLE_ANGLE)
        assertEquals(0f, g.lasers[0].vx, 1e-6f)
        assertEquals(-side, g.lasers[1].vx, 1e-5f)
        assertEquals(side, g.lasers[2].vx, 1e-5f)
        g.run(World.POWER_UP_DURATION + 0.1f)
        assertFalse(g.tripleActive)
    }

    @Test fun anUncollectedBonusFallsPastWithoutEffect() {
        val g = playing()
        g.debug.spawnPowerUp(0.35f, 0.70f, Game.PowerUpType.RAPID)
        g.run(2f)
        assertTrue(g.powerUps.isEmpty())
        assertFalse(g.rapidActive)
        assertEquals(0L, g.score)
    }
}

class ProgressionTest {
    @Test fun clearingTheWavePaysABonusAndAdvances() {
        val host = RecordingHost()
        val g = playing(host = host)
        for (i in g.aliens.indices) g.debug.killAlien(i)
        g.update(FRAME)
        assertEquals(Game.State.LEVEL_CLEAR, g.state)
        assertEquals(100L, g.score)
        assertTrue(host.sounds.contains(SoundEvent.LEVEL_CLEAR))
        g.run(Game.LEVEL_CLEAR_PAUSE + 0.1f)
        assertEquals(Game.State.PLAYING, g.state)
        assertEquals(2, g.level)
        assertEquals(2, host.furthest)
    }

    @Test fun theLevelClearBonusScalesWithTheLevel() {
        val g = playing(4)
        for (i in g.aliens.indices) g.debug.killAlien(i)
        g.update(FRAME)
        assertEquals(400L, g.score)
    }

    @Test fun aLevelClearWipesTheBombsInFlight() {
        val g = playing()
        g.debug.spawnBomb(0.2f, 0f, 0.5f)
        for (i in g.aliens.indices) g.debug.killAlien(i)
        g.update(FRAME)
        assertTrue(g.bombs.isEmpty())
    }

    @Test fun clearingLevelNineBringsTheMothership() {
        val g = playing(9)
        for (i in g.aliens.indices) g.debug.killAlien(i)
        g.update(FRAME); g.run(Game.LEVEL_CLEAR_PAUSE + 0.1f)
        assertEquals(10, g.level)
        assertNotNull(g.boss)
        assertEquals(2, g.rows)
    }

    @Test fun theGameOverScreenIgnoresAnEarlyTapThenReturnsToTheTitle() {
        val g = playing()
        g.debug.setFormation(y = World.INVADE_Y)
        g.update(FRAME)
        assertEquals(Game.State.GAME_OVER, g.state)
        g.tap(); g.update(FRAME)
        assertEquals(Game.State.GAME_OVER, g.state)
        g.run(Game.END_SCREEN_GRACE)
        g.tap(); g.update(FRAME)
        assertEquals(Game.State.TITLE, g.state)
        assertEquals(1, g.level)
        assertTrue(g.bombs.isEmpty() && g.lasers.isEmpty() && g.powerUps.isEmpty())
        assertEquals(24, g.aliveAliens)
    }

    @Test fun theTitleReturnResetsTheBonusesAndTheBoss() {
        val g = playing(10)
        g.debug.activatePowerUp(Game.PowerUpType.SHIELD)
        g.debug.activatePowerUp(Game.PowerUpType.RAPID)
        g.debug.setFormation(y = World.INVADE_Y)
        g.update(FRAME); g.run(1f); g.tap(); g.update(FRAME)
        assertEquals(Game.State.TITLE, g.state)
        assertNull(g.boss)
        assertFalse(g.shieldActive); assertFalse(g.rapidActive)
        assertEquals(0f, g.invuln, 1e-6f)
    }

    @Test fun anEndScreenShowsNothingInFlight() {
        val g = playing()
        g.debug.setLives(1); g.debug.clearInvuln()
        g.debug.spawnPowerUp(0.3f, 0f, Game.PowerUpType.RAPID)
        g.debug.setSaucer(0.2f, 0.35f)
        g.setFire(true); g.update(FRAME)                      // a laser up
        g.debug.spawnBomb(0f, World.SHIP_Y - 0.03f, 1f)       // and the fatal bomb
        g.update(FRAME)
        assertEquals(Game.State.GAME_OVER, g.state)
        assertTrue(g.lasers.isEmpty() && g.bombs.isEmpty() && g.powerUps.isEmpty())
        assertNull(g.saucer)
        assertFalse(g.explosions.isEmpty())                   // the hit's flash still plays out
    }

    @Test fun theWinScreenShowsNothingInFlight() {
        val g = playing(10)
        for (i in g.aliens.indices) g.debug.killAlien(i)
        g.debug.activatePowerUp(Game.PowerUpType.TRIPLE)
        g.setFire(true)
        val b = g.boss!!
        var t = 0f
        while (t < 40f && b.alive) { g.debug.setShipX(b.x); g.update(FRAME); t += FRAME }
        assertEquals(Game.State.WIN, g.state)
        assertTrue(g.lasers.isEmpty() && g.bombs.isEmpty())
    }

    @Test fun aHugeFrameIsClampedToTheMaximum() {
        val g = playing()
        g.update(5f)
        assertEquals(Game.MAX_FRAME, g.animTime, 1e-4f)
    }

    @Test fun refreshRateDoesNotChangeTheGame() {
        fun play(frameDt: Float): Triple<Float, Long, Int> {
            val g = playing()
            g.touchStrip(0.30f)
            var t = 0f
            while (t < 4f - 1e-4f) { g.update(frameDt); t += frameDt }
            return Triple(g.shipX, g.score, g.lives)
        }
        val at60 = play(1f / 60f)
        val at90 = play(1f / 90f)
        val at120 = play(1f / 120f)
        assertEquals(at60.first, at90.first, 1e-4f)
        assertEquals(at60.first, at120.first, 1e-4f)
        assertEquals(at60.second, at90.second)
        assertEquals(at60.second, at120.second)
        assertEquals(at60.third, at90.third)
        assertEquals(at60.third, at120.third)
    }

    @Test fun theRemainderOfAFrameCarriesOver() {
        val g = newGame()
        repeat(3) { g.update(1f / 90f) }          // 1, 1 and 2 ticks: four in all
        assertEquals(4 * Game.TICK, g.animTime, 1e-5f)
        g.update(Game.TICK * 0.4f)                 // not a whole tick yet
        assertEquals(4 * Game.TICK, g.animTime, 1e-5f)
        g.update(Game.TICK * 0.7f)                 // now it is
        assertEquals(5 * Game.TICK, g.animTime, 1e-5f)
    }
}

class BossTest {
    @Test fun theMothershipSpawnsAtLevelTenWithFullHealth() {
        val g = playing(10)
        val b = g.boss!!
        assertEquals(World.BOSS_HP, b.hp); assertTrue(b.alive)
        assertEquals(16, g.aliveAliens)
    }

    @Test fun theMothershipDriftsAcrossTheTop() {
        val g = playing(10)
        g.run(2f)
        assertTrue(abs(g.boss!!.x) > 0.1f)
        assertTrue(abs(g.boss!!.x) <= g.aspect - World.BOSS_HW)
    }

    @Test fun clearingTheEscortDoesNotClearTheLevel() {
        val g = playing(10)
        for (i in g.aliens.indices) g.debug.killAlien(i)
        g.run(1f)
        assertEquals(Game.State.PLAYING, g.state)
    }

    @Test fun theMothershipFiresAimedBombs() {
        val g = playing(10)
        for (i in g.aliens.indices) g.debug.killAlien(i)
        g.debug.setShipX(0.3f)
        assertTrue(g.runUntil(5f) { bombs.any { it.vx != 0f } })
        assertTrue(g.bombs.first { it.vx != 0f }.vx > 0f)
    }

    @Test fun theMothershipTakesHitsAndDiesForTheWin() {
        val host = RecordingHost()
        val g = playing(10, host)
        for (i in g.aliens.indices) g.debug.killAlien(i)
        g.setFire(true)
        val b = g.boss!!
        var t = 0f
        while (t < 40f && b.alive) { g.debug.setShipX(b.x); g.update(FRAME); t += FRAME }
        assertFalse(b.alive)
        assertEquals(Game.State.WIN, g.state)
        assertEquals(2500L, g.score)
        assertEquals(1, host.tables.size)
    }
}

class SessionTest {
    @Test fun aSessionResumesAtItsLevelScoreAndLives() {
        val g = newGame()
        g.restoreSession(5, 1234L, 2)
        assertEquals(Game.State.PLAYING, g.state)
        assertEquals(5, g.level); assertEquals(1234L, g.score); assertEquals(2, g.lives)
    }

    @Test fun outOfRangeSessionsAreRejected() {
        val g = newGame()
        g.restoreSession(11, 10L, 2); assertEquals(Game.State.TITLE, g.state)
        g.restoreSession(3, -1L, 2); assertEquals(Game.State.TITLE, g.state)
        g.restoreSession(3, 10L, 0); assertEquals(Game.State.TITLE, g.state)
    }
}

class HighScoresTest {
    @Test fun theTableOrdersDisplacesAndCaps() {
        val g = newGame()
        g.restoreHighScores(listOf(HighScore(100, 1), HighScore(300, 2), HighScore(200, 1), HighScore(50, 1), HighScore(400, 3), HighScore(10, 1)))
        assertEquals(listOf(400L, 300L, 200L, 100L, 50L), g.highScores.map { it.score })
    }

    @Test fun restoredTableIsSortedAndDeduplicated() {
        val g = newGame()
        g.restoreHighScores(listOf(HighScore(100, 1), HighScore(300, 2), HighScore(100, 1), HighScore(200, 1)))
        assertEquals(listOf(300L, 200L, 100L), g.highScores.map { it.score })
    }

    @Test fun aRecordIsSavedOnGameOver() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.debug.setScore(777)
        g.debug.setFormation(y = World.INVADE_Y)
        g.update(FRAME)
        assertTrue(g.newHighScore); assertEquals(0, g.newHighScoreRank)
        assertEquals(listOf(HighScore(777, 1)), host.tables.single())
    }

    @Test fun aTieWithAnExistingRowIsNotARecord() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.restoreHighScores(listOf(HighScore(500, 1)))
        g.debug.setScore(500)
        g.debug.setFormation(y = World.INVADE_Y); g.update(FRAME)
        assertFalse(g.newHighScore)
        assertEquals(1, g.highScores.size)
        assertTrue(host.tables.isEmpty())
    }

    @Test fun aZeroScoreIsNotRecorded() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.debug.setFormation(y = World.INVADE_Y); g.update(FRAME)
        assertTrue(g.highScores.isEmpty()); assertTrue(host.tables.isEmpty())
    }
}

class PauseTest {
    @Test fun aPausedRoundFreezesUntilATap() {
        val g = playing()
        g.pause()
        assertTrue(g.paused)
        val x = g.formationX
        g.setSteer(1); g.run(1f)
        assertEquals(x, g.formationX, 1e-6f)
        assertEquals(0f, g.shipX, 1e-6f)
        g.tap(); g.update(FRAME)
        assertFalse(g.paused)
        g.run(0.5f)
        assertTrue(g.formationX != x)
    }

    @Test fun theAutopilotResumesByItself() {
        val g = playing()
        g.autoPlayActive = true
        g.run(0.5f)
        g.pause(); g.resume()
        assertFalse(g.paused)
    }

    @Test fun aPauseThePlayerAskedForHoldsTheAutopilotUntilATap() {
        // Back (or P) during an Auto Play round: the autopilot must not
        // unfreeze the round on the next tick, or Back could never leave.
        val g = playing()
        g.autoPlayActive = true
        g.run(0.5f)
        g.pause()
        val x = g.formationX
        g.run(0.5f)
        assertTrue(g.paused)
        assertEquals(x, g.formationX, 1e-6f)
        g.tap(); g.update(FRAME)
        assertFalse(g.paused)
    }

    @Test fun thereIsNothingToPauseOnTheTitleOrAnEndScreen() {
        val g = newGame()
        g.pause(); assertFalse(g.paused)
        val h = playing()
        h.debug.setFormation(y = World.INVADE_Y); h.update(FRAME)
        h.pause(); assertFalse(h.paused)
    }

    @Test fun theSirenStopsWhilePaused() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.debug.setSaucer(0f, 0.35f)
        assertTrue(host.siren)
        g.pause(); assertFalse(host.siren)
        g.tap(); g.update(FRAME); assertTrue(host.siren)
    }
}

class LevelSelectTest {
    @Test fun theTitleOffersLevelsUpToTheFurthestReached() {
        val g = newGame()
        g.selectLevel(1); assertEquals(1, g.startLevel)
        g.furthestLevel = 4
        g.selectLevel(1); g.selectLevel(1); assertEquals(3, g.startLevel)
        g.selectLevel(5); assertEquals(4, g.startLevel)
        g.selectLevel(-9); assertEquals(1, g.startLevel)
    }

    @Test fun aRoundStartsAtThePickedLevel() {
        val g = newGame()
        g.furthestLevel = 6
        g.selectLevel(2)
        g.tap(); g.update(FRAME)
        assertEquals(3, g.level)
        assertEquals(Levels.rows(3), g.rows)
    }

    @Test fun thePickIsFrozenOnceARoundIsUnderWay() {
        val g = playing()
        g.furthestLevel = 6
        g.selectLevel(2)
        assertEquals(1, g.startLevel)
    }

    @Test fun loweringTheFurthestLevelLowersThePick() {
        val g = newGame()
        g.furthestLevel = 6; g.selectLevel(5)
        g.furthestLevel = 2
        assertEquals(2, g.startLevel)
    }

    @Test fun reachingANewLevelRaisesTheFurthest() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.debug.startLevel(7)
        assertEquals(7, g.furthestLevel)
        assertEquals(7, host.furthest)
    }
}

class SettingsTest {
    @Test fun soundOffSilencesTheHost() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.soundEnabled = false
        g.setFire(true); g.run(1f)
        assertTrue(host.sounds.isEmpty())
        assertTrue(host.marches.isEmpty())
        assertEquals(false, host.music)
    }

    @Test fun theMusicFollowsTheRound() {
        val host = RecordingHost()
        val g = newGame(host)
        assertNull(host.music)
        g.tap(); g.update(FRAME)
        assertEquals(true, host.music)
    }

    @Test fun thePhoneFreezesWhileTheRoundRunsOnTheGlasses() {
        val host = RecordingHost()
        val g = playing(host = host)
        g.glassesActive = true
        assertEquals(false, host.music)
        val x = g.formationX
        g.run(1f)
        assertEquals(x, g.formationX, 1e-6f)
    }

    @Test fun theTouchbarSteersAndFiresFromAnywhere() {
        val g = playing()
        g.controlMode = Game.ControlMode.TOUCHBAR
        g.onPointerDown(0, 0.3f, -0.5f)
        g.run(0.3f)
        assertTrue(g.shipX > 0.1f)
        assertTrue(g.shotsFired > 0)
    }
}

class ShipHitTest {
    /** A wave parked far above: no bomb of its own reaches the ship. */
    private fun quiet(level: Int = 1) = playing(level).apply { debug.setFormation(y = -5f) }

    @Test fun theRoundOpensWithAGracePeriod() {
        val g = quiet()
        assertTrue(g.invuln > 0f)
        g.debug.spawnBomb(0f, 0.50f, 1f); g.run(0.2f)
        assertEquals(3, g.lives)
        g.run(1.2f)
        assertTrue(g.invuln <= 0f)
        g.debug.spawnBomb(0f, 0.50f, 1f); g.run(0.2f)
        assertEquals(2, g.lives)
    }

    @Test fun invulnerabilityWearsOff() {
        val g = quiet()
        g.debug.clearInvuln()
        g.debug.spawnBomb(0f, 0.50f, 1f); g.run(0.2f)
        assertEquals(2, g.lives)
        g.debug.spawnBomb(0f, 0.50f, 1f); g.run(0.2f)
        assertEquals(2, g.lives)                       // still inside the window
        g.run(1.3f)
        g.debug.spawnBomb(0f, 0.50f, 1f); g.run(0.2f)
        assertEquals(1, g.lives)                       // the window has closed
    }

    @Test fun aFatalCollisionIsRecordedAsTheCause() {
        val g = playing()
        g.debug.clearInvuln()
        g.debug.setLives(1)
        val col3 = g.aliens.first { it.row == 2 && it.col == 3 }
        g.debug.setFormation(x = -g.alienX(col3), y = World.SHIP_Y - 2 * World.ROW_STEP)
        g.debug.setShipX(0f)
        g.update(FRAME)
        assertEquals(Game.State.GAME_OVER, g.state)
        assertEquals(0, g.lives)
        assertEquals(Game.DeathCause.COLLISION, g.deathCause)
    }

    @Test fun aHitShakesTheScreenThenItSettles() {
        val g = quiet()
        g.debug.clearInvuln()
        g.run(0.2f)
        assertEquals(0f, g.shakeX, 1e-6f); assertEquals(0f, g.shakeY, 1e-6f)
        g.debug.spawnBomb(0f, 0.50f, 1f)
        assertTrue(g.runUntil(0.3f) { lives == 2 })
        g.update(FRAME)
        assertTrue(abs(g.shakeX) + abs(g.shakeY) > 0f)
        g.run(2f)
        assertEquals(0f, g.shakeX, 1e-6f); assertEquals(0f, g.shakeY, 1e-6f)
    }

    @Test fun aNewRoundResetsTheTally() {
        val g = quiet()
        g.setFire(true); g.run(0.5f); g.setFire(false)
        assertTrue(g.shotsFired > 0)
        g.debug.clearInvuln()
        g.debug.setLives(1)
        g.debug.activatePowerUp(Game.PowerUpType.RAPID)
        g.debug.spawnBomb(0f, 0.50f, 1f); g.run(0.2f)
        assertEquals(Game.State.GAME_OVER, g.state)
        assertEquals(Game.DeathCause.BOMB, g.deathCause)
        g.debug.startGame()
        assertEquals(Game.State.PLAYING, g.state)
        assertEquals(3, g.lives); assertEquals(0L, g.score)
        assertEquals(0, g.shotsFired); assertEquals(0, g.lasersLost); assertEquals(0, g.bombsShot)
        assertEquals(Game.DeathCause.NONE, g.deathCause)
        assertEquals(-1, g.newHighScoreRank)
        assertFalse(g.rapidActive)
        assertTrue(g.lasers.isEmpty()); assertTrue(g.bombs.isEmpty())
    }
}

class TimersTest {
    private fun quiet(level: Int = 1) = playing(level).apply { debug.setFormation(y = -5f) }

    @Test fun collectingTheSamePowerUpAgainRestartsItsClock() {
        val g = quiet()
        g.debug.activatePowerUp(Game.PowerUpType.RAPID)
        g.run(World.POWER_UP_DURATION - 1f)
        assertTrue(g.rapidActive)
        g.debug.activatePowerUp(Game.PowerUpType.RAPID)
        g.run(2f)
        assertTrue(g.rapidActive)                      // would have expired a second in
        g.run(World.POWER_UP_DURATION)
        assertFalse(g.rapidActive)
    }

    @Test fun tripleShotWearsOff() {
        val g = quiet()
        g.debug.activatePowerUp(Game.PowerUpType.TRIPLE)
        assertTrue(g.tripleActive)
        g.run(World.POWER_UP_DURATION + FRAME)
        assertFalse(g.tripleActive)
        g.setFire(true); g.update(FRAME)
        assertEquals(1, g.lasers.size)
    }

    @Test fun aCaughtBonusFlashesItsValueWhereItWasCaughtThenFades() {
        val g = quiet(2)
        g.run(0.2f)
        assertEquals(0f, g.bonusFlashTimer, 1e-6f)
        g.debug.spawnPowerUp(0f, 0.50f, Game.PowerUpType.SHIELD)
        assertTrue(g.runUntil(1f) { powerUps.isEmpty() })
        assertEquals(50L, g.score)                     // 25 times the level
        assertEquals(50L, g.bonusFlash)
        assertTrue(g.bonusFlashTimer > 0f)
        assertEquals(0f, g.bonusFlashX, 1e-6f)
        assertTrue(g.bonusFlashY > 0.3f && g.bonusFlashY < World.SHIP_Y)
        g.run(Game.BONUS_FLASH_TIME + 2 * FRAME)
        assertTrue(g.bonusFlashTimer <= 0f)
    }
}

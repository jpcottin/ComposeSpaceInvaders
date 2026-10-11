package com.jpcottin.composespaceinvaders.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private fun auto(level: Int = 1, aspect: Float = 0.45f): Game = playing(level, aspect = aspect).apply { autoPlayActive = true }

class AutoPlayTest {
    @Test fun firesOnItsOwn() {
        val g = auto()
        g.run(2f)
        assertTrue(g.shotsFired > 0)
    }

    @Test fun steersAwayFromAnIncomingBomb() {
        val g = auto()
        g.debug.setFormation(y = -5f)
        g.debug.spawnBomb(0f, 0.30f, 0.5f)
        g.run(0.3f)
        assertTrue(abs(g.shipX) > 0.05f)
        g.run(1f)
        assertEquals(3, g.lives)
    }

    @Test fun dodgeConsidersEveryBomb() {
        val g = auto()
        g.debug.setFormation(y = -5f)
        g.debug.spawnBomb(0f, 0.30f, 0.5f)
        g.debug.spawnBomb(0.08f, 0.30f, 0.5f)
        g.debug.spawnBomb(0.16f, 0.30f, 0.5f)
        g.run(0.6f)
        assertTrue(g.shipX < -0.05f)
        assertEquals(3, g.lives)
    }

    @Test fun collectsABonusThatIsAboutToLand() {
        val g = auto()
        g.debug.setFormation(y = -5f)
        g.debug.spawnPowerUp(0.3f, World.SHIP_Y - 0.2f, Game.PowerUpType.SHIELD)
        g.run(1.2f)
        assertTrue(g.shieldActive)
    }

    @Test fun keepsShootingUntilABonusIsAboutToLand() {
        val g = auto()
        g.debug.spawnPowerUp(0.3f, -0.8f, Game.PowerUpType.RAPID)   // 4.6 s from the ship
        g.update(FRAME)
        assertTrue(abs(g.aiTarget - 0.3f) > 0.05f)
    }

    @Test fun doesNotFireAtAnInvaderALaserIsAlreadyOnItsWayTo() {
        val g = auto()
        g.debug.keepOnlyAlien(3)   // top row, column 3: a long flight for the laser
        g.debug.setFormation(y = -0.66f)
        var t = 0f
        while (t < 3f && g.aliveAliens > 0) {
            g.update(FRAME); t += FRAME
            assertTrue(g.lasers.count { it.vx == 0f } <= 1)
        }
    }

    @Test fun shootsDownABombInItsColumn() {
        val g = auto()
        g.debug.setFormation(y = -5f)
        g.debug.spawnBomb(0f, 0.30f, 0.1f)   // too slow to threaten, right overhead
        assertTrue(g.runUntil(0.5f) { bombsShot == 1 })
    }

    @Test fun leadsTheSaucer() {
        val g = auto()
        g.debug.setFormation(y = -5f)
        g.debug.setSaucer(-0.2f, World.SAUCER_SPEED)
        g.debug.setShipX(0.3f)
        g.update(FRAME)
        assertTrue(g.aiHasTarget)
        assertTrue(g.aiTarget > -0.2f)
    }

    @Test fun leavesAFarSaucerToComeBy() {
        val g = auto(aspect = 2.2f)
        g.debug.setFormation(y = -5f)
        g.debug.setSaucer(-2.0f, World.SAUCER_SPEED)
        g.debug.setShipX(1.5f)
        g.update(FRAME)
        assertTrue(g.aiTarget > 0.5f)
    }

    @Test fun shootsTheEscortBeforeTheMothership() {
        val g = auto(10)
        g.run(0.5f)
        val b = g.boss!!
        assertTrue(abs(g.aiTarget - b.x) > 0.001f || g.aliveAliens < 16)
        assertTrue(g.runUntil(15f) { aliveAliens < 16 })
        assertEquals(Game.State.PLAYING, g.state)
    }

    @Test fun targetsTheMothershipOnceTheEscortIsGone() {
        val g = auto(10)
        for (i in g.aliens.indices) g.debug.killAlien(i)
        assertTrue(g.runUntil(60f) { state == Game.State.WIN })
    }

    @Test fun clearsAWaveEventually() {
        val g = auto()
        assertTrue(g.runUntil(120f) { state == Game.State.LEVEL_CLEAR })
    }

    @Test fun holdsThePortraitLineAtLevelSix() {
        val g = auto(6)
        assertTrue(g.runUntil(200f) { state != Game.State.PLAYING })
        assertEquals(Game.State.LEVEL_CLEAR, g.state)
    }

    @Test fun winsACampaignOnAPortraitPhone() {
        val g = auto()
        var t = 0f
        while (t < 600f && g.state != Game.State.WIN && g.state != Game.State.GAME_OVER) { g.update(FRAME); t += FRAME }
        assertEquals(Game.State.WIN, g.state)
    }

    @Test fun drivesTheSameControlPathAsAFinger() {
        val g = auto()
        g.run(0.5f)
        assertTrue(g.moveHeld)
        assertFalse(g.paused)
    }
}

/** The bench's guard rests on seeded runs giving the same numbers every
 *  time: the same seed must play the very same campaign twice. */
class DeterminismTest {
    private fun campaign(seed: Int, aspect: Float): Triple<Long, Int, Game.State> {
        val g = Game().apply { setAspect(aspect); seed(seed); debug.startGame(); autoPlayActive = true }
        var t = 0f
        while (t < 300f && g.state != Game.State.WIN && g.state != Game.State.GAME_OVER) {
            g.update(FRAME); t += FRAME
            while (g.state == Game.State.LEVEL_CLEAR) g.update(FRAME)
        }
        return Triple(g.score, g.shotsFired, g.state)
    }

    @Test fun theSameSeedPlaysTheSameCampaign() {
        assertEquals(campaign(7, 0.45f), campaign(7, 0.45f))
        assertEquals(campaign(7, 2.2f), campaign(7, 2.2f))
    }

    @Test fun differentSeedsPlayDifferentCampaigns() {
        assertTrue(campaign(7, 0.45f) != campaign(8, 0.45f))
    }

    @Test fun theFrameSizeDoesNotChangeTheGame() {
        // 60 Hz frames and 120 Hz frames land on the same ticks.
        fun play(frame: Float): Pair<Long, Int> {
            val g = Game().apply { setAspect(0.45f); seed(3); debug.startGame(); autoPlayActive = true }
            var t = 0f
            while (t < 30f) { g.update(frame); t += frame }
            return g.score to g.shotsFired
        }
        assertEquals(play(1f / 60f), play(1f / 120f))
    }
}

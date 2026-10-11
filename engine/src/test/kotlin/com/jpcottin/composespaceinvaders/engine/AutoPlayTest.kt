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

    // ── Column choices ───────────────────────────────────────────────────────
    // The wave at level 1 starts high: on a portrait phone it is no threat
    // yet, and the pick is the nearest column. Pushed down to where it would
    // reach the ship before a volley per invader clears it, the pick turns
    // to the edge columns; with the bottom row about to land, to that row.

    /** Column [c]'s x when the formation sits at [x]. */
    private fun colX(x: Float, c: Int) = x + (c - (World.COLS - 1) * 0.5f) * World.COL_STEP

    @Test fun takesTheNearestColumnWhenTheWaveIsNoThreat() {
        val g = auto()
        g.debug.setShipX(0.30f)                               // under column 7, at the right edge
        g.update(FRAME)
        assertTrue("aim ${g.aiTarget}", g.aiTarget in 0.25f..0.40f)
    }

    @Test fun narrowsAWaveThatOutrunsItsLasers() {
        val g = auto()
        g.debug.setFormation(y = 0.10f)                       // six step-downs from the ship's line
        g.debug.setShipX(0f)                                  // columns 3 and 4 are the nearest
        g.update(FRAME)
        assertTrue("aim ${g.aiTarget}", g.aiTarget < -0.20f)  // the left edge, nearer on a tie
        g.debug.killAlien(2 * World.COLS + 7)                 // the right edge is one invader thinner
        g.update(FRAME)
        assertTrue("aim ${g.aiTarget}", g.aiTarget > 0.20f)
    }

    @Test fun defendsTheBottomRowWhenItIsAboutToLand() {
        val g = auto()
        for (c in 0 until World.COLS) if (c != 5) g.debug.killAlien(2 * World.COLS + c)   // one invader left on the bottom row
        g.debug.setFormation(y = 0.30f)                       // one step-down from the ship's line
        g.debug.setShipX(-0.30f)                              // column 0 is the nearest
        g.update(FRAME)
        assertTrue("aim ${g.aiTarget}", g.aiTarget > 0.05f)   // column 5 first
    }

    @Test fun ignoresTheSaucerWhenTheWaveIsAboutToLand() {
        val g = auto()
        for (c in 0 until World.COLS) if (c != 5) g.debug.killAlien(2 * World.COLS + c)
        g.debug.setFormation(y = 0.30f)
        g.debug.setShipX(-0.30f)
        g.debug.setSaucer(-0.30f, 0f)                         // a sitting duck right overhead
        g.update(FRAME)
        assertTrue("aim ${g.aiTarget}", g.aiTarget > 0.05f)
    }

    @Test fun theAimNeverLeadsPastTheEdgeBounce() {
        val g = auto()
        g.debug.keepOnlyAlien(2 * World.COLS + 7)             // a lone survivor, marching fast
        g.debug.setFormation(x = 0.08f)                       // about to touch the right margin
        g.update(FRAME)
        val x = g.alienX(g.aliens[2 * World.COLS + 7])
        assertTrue("aim ${g.aiTarget} vs $x", g.aiTarget < x && g.aiTarget > 0f)   // folds back, left of it
    }

    @Test fun firesAtAColumnOverheadOnTheWayToItsTarget() {
        val g = auto()
        g.debug.setFormation(y = 0.10f)                       // under pressure: an edge is the target
        g.debug.setShipX(colX(0f, 4) + 0.012f)                // standing under column 4 meanwhile (lead included)
        g.update(FRAME)
        assertTrue("aim ${g.aiTarget}", abs(g.aiTarget) > 0.20f)
        assertTrue("not firing, ship at ${g.shipX}", g.aiFiring)
    }

    @Test fun waitsOutABombLaneInItsPath() {
        val g = auto()
        g.debug.keepOnlyAlien(2 * World.COLS + 7)             // the target is far right
        g.debug.setShipX(-0.30f)
        g.debug.spawnBomb(0f, 0.40f, 0.5f)                    // a bomb lands on the way just as the ship would cross
        g.update(FRAME)
        assertTrue(g.aiHasTarget)
        assertTrue("aim ${g.aiTarget}", g.aiTarget < 0f)      // stops short of the lane
    }

    @Test fun dodgesOntoASpotItCanShootFrom() {
        val g = auto()
        g.debug.setShipX(0f)
        g.debug.spawnBomb(0f, 0.30f, 0.5f)                    // right overhead: dodge
        g.update(FRAME)
        assertTrue(abs(g.aiTarget) > 0.05f)
        assertTrue("aim ${g.aiTarget}", g.aiTarget > 0f)      // the side with a column to shoot
    }

    @Test fun skipsAShieldWhileShielded() {
        val g = auto()
        g.debug.keepOnlyAlien(2 * World.COLS)                 // the wave is far left
        g.debug.activatePowerUp(Game.PowerUpType.SHIELD)
        g.debug.spawnPowerUp(0.35f, World.SHIP_Y - 0.15f, Game.PowerUpType.SHIELD)
        g.debug.setShipX(0f)
        g.update(FRAME)
        assertTrue("aim ${g.aiTarget}", g.aiTarget < 0f)
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

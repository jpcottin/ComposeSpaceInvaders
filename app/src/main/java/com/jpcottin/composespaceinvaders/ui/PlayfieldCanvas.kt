package com.jpcottin.composespaceinvaders.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.jpcottin.composespaceinvaders.engine.Game
import com.jpcottin.composespaceinvaders.engine.World
import com.jpcottin.composespaceinvaders.theme.BombCore
import com.jpcottin.composespaceinvaders.theme.BombGlow
import com.jpcottin.composespaceinvaders.theme.BossMagenta
import com.jpcottin.composespaceinvaders.theme.LaneLine
import com.jpcottin.composespaceinvaders.theme.LaserCore
import com.jpcottin.composespaceinvaders.theme.LaserGlow
import com.jpcottin.composespaceinvaders.theme.PowerUpColors
import com.jpcottin.composespaceinvaders.theme.SaucerRed
import com.jpcottin.composespaceinvaders.theme.ShipCyan
import com.jpcottin.composespaceinvaders.theme.ShipCyanLight
import com.jpcottin.composespaceinvaders.theme.ShipHull
import com.jpcottin.composespaceinvaders.theme.StarFar
import com.jpcottin.composespaceinvaders.theme.StarNear
import com.jpcottin.composespaceinvaders.theme.TierColors
import kotlin.math.sin

/**
 * Draws the battlefield from the engine's state: the starfield, the wave,
 * the saucer and the mothership, the lasers and the bombs, the bonuses, the
 * debris and the ship, plus the control strip and the bonus bars. The world
 * is mapped to pixels with one scale (half the view height) around the view
 * centre, so a world unit is the same size on both axes and the engine never
 * sees pixels.
 *
 * [frame] is read inside the draw lambda, so the canvas redraws (without
 * recomposing) once per engine step; the engine itself is a plain mutable
 * object, not Compose state.
 */
@Composable
fun PlayfieldCanvas(game: Game, frame: LongState, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION") frame.longValue
        drawPlayfield(game)
    }
}

/** World -> pixel mapping for a [DrawScope]. */
class WorldToPixels(size: Size) {
    val scale = size.height / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    fun x(wx: Float) = cx + wx * scale
    fun y(wy: Float) = cy + wy * scale
    fun len(w: Float) = w * scale
}

/**
 * The invader and saucer sprites: the classic pixel-art bitmaps, two march
 * frames each, kept as paths in cell units and scaled at draw time, so an
 * invader is one draw call whatever its size.
 */
object Sprites {
    private fun path(rows: List<String>): Path = Path().apply {
        for ((r, row) in rows.withIndex()) for ((c, ch) in row.withIndex())
            if (ch == '#') addRect(androidx.compose.ui.geometry.Rect(c.toFloat(), r.toFloat(), c + 1f, r + 1f))
    }

    class Sprite(frameA: List<String>, frameB: List<String>) {
        val cols = frameA[0].length
        val rows = frameA.size
        val frames = listOf(path(frameA), path(frameB))
    }

    val squid = Sprite(
        listOf("...##...", "..####..", ".######.", "##.##.##", "########", "..#..#..", ".#.##.#.", "#.#..#.#"),
        listOf("...##...", "..####..", ".######.", "##.##.##", "########", ".#.##.#.", "#......#", ".#....#."),
    )
    val crab = Sprite(
        listOf("..#.....#..", "...#...#...", "..#######..", ".##.###.##.", "###########", "#.#######.#", "#.#.....#.#", "...##.##..."),
        listOf("..#.....#..", "#..#...#..#", "#.#######.#", "###.###.###", "###########", ".#########.", "..#.....#..", ".#.......#."),
    )
    val octopus = Sprite(
        listOf("....####....", ".##########.", "############", "###..##..###", "############", "...##..##...", "..##.##.##..", "##........##"),
        listOf("....####....", ".##########.", "############", "###..##..###", "############", "..###..###..", ".##..##..##.", "..##....##.."),
    )
    val saucer = Sprite(
        listOf(".....######.....", "...##########...", "..############..", ".##.##.##.##.##.", "################", "...###....###...", "....#......#...."),
        listOf(".....######.....", "...##########...", "..############..", ".##.##.##.##.##.", "################", "...###....###...", "....#......#...."),
    )

    fun of(type: Game.AlienType): Sprite = when (type) {
        Game.AlienType.SQUID -> squid
        Game.AlienType.CRAB -> crab
        Game.AlienType.OCTOPUS -> octopus
    }
}

/** A sprite centred on (px, py) with half-extents (hw, hh) in pixels. */
fun DrawScope.drawSprite(sprite: Sprites.Sprite, frame: Int, px: Float, py: Float, hw: Float, hh: Float, color: Color) {
    val sx = 2f * hw / sprite.cols
    val sy = 2f * hh / sprite.rows
    translate(px - hw, py - hh) {
        scale(sx, sy, pivot = Offset.Zero) {
            drawPath(sprite.frames[frame and 1], color)
        }
    }
}

fun DrawScope.drawPlayfield(game: Game) {
    val m = WorldToPixels(size)
    val title = game.state == Game.State.TITLE
    val t = game.animTime

    // Stars are soft twinkling dots so they read as scenery, never as
    // something to shoot.
    for (s in game.stars) {
        val tw = 0.35f + 0.15f * sin(t * (0.8f + s.phase * 0.2f) + s.phase)
        drawCircle(StarFar.copy(alpha = tw), m.len(s.size), Offset(m.x(s.x * game.aspect), m.y(s.y)))
    }
    for (s in game.starsNear) {
        val tw = 0.50f + 0.20f * sin(t * (1.2f + s.phase * 0.3f) + s.phase)
        drawCircle(StarNear.copy(alpha = tw), m.len(s.size), Offset(m.x(s.x * game.aspect), m.y(s.y)))
    }

    // Everything in play shakes together when the ship is hit.
    translate(m.len(game.shakeX), m.len(game.shakeY)) {
        // The wave. On the title it doubles as the demo formation, drawn
        // dimmer under the title text.
        val waveAlpha = if (title) 0.85f else 1f
        val waveShift = if (title) 0.30f else 0f
        for (a in game.aliens) {
            if (!a.alive) continue
            drawSprite(
                Sprites.of(a.type), game.marchFrame, m.x(game.alienX(a)), m.y(game.alienY(a) + waveShift),
                m.len(World.ALIEN_HW), m.len(World.ALIEN_HH), TierColors[a.type.ordinal].copy(alpha = waveAlpha),
            )
        }

        // The mothership: a giant saucer with an angry pulsing aura, redder as it weakens.
        val boss = game.boss
        if (boss != null && boss.alive && !title) {
            val pulse = 0.25f + 0.15f * sin(t * 5f)
            val hurt = 1f - boss.hp.toFloat() / boss.maxHp
            val c = Offset(m.x(boss.x), m.y(World.BOSS_Y))
            val r = m.len(World.BOSS_HW * 1.5f)
            drawCircle(Brush.radialGradient(listOf(BossMagenta.copy(alpha = pulse), BossMagenta.copy(alpha = 0f)), c, r), r, c)
            val body = Color(0.80f + 0.20f * hurt, 0.30f - 0.10f * hurt, 0.70f - 0.30f * hurt)
            drawSprite(Sprites.saucer, 0, c.x, c.y, m.len(World.BOSS_HW), m.len(World.BOSS_HH), body)
        }

        // The saucer: the sprite over a pulsing glow, the bonus target.
        val sc = game.saucer
        if (sc != null) {
            val pulse = 0.30f + 0.15f * sin(t * 9f)
            val c = Offset(m.x(sc.x), m.y(sc.y))
            val r = m.len(0.075f)
            drawCircle(Brush.radialGradient(listOf(SaucerRed.copy(alpha = pulse), SaucerRed.copy(alpha = 0f)), c, r), r, c)
            drawSprite(Sprites.saucer, 0, c.x, c.y, m.len(0.055f), m.len(0.026f), SaucerRed)
        }

        // Bonuses: a spinning diamond with a glow halo.
        for (pu in game.powerUps) if (pu.alive) drawPowerUp(m, pu, t)

        // Player lasers: a glow and a bright core, tilted along their flight.
        for (b in game.lasers) {
            if (!b.alive) continue
            val c = Offset(m.x(b.x), m.y(b.y))
            rotate(b.rot * 57.2958f, c) {
                drawRoundRect(LaserGlow.copy(alpha = 0.45f), Offset(c.x - m.len(0.018f), c.y - m.len(0.034f)), Size(m.len(0.036f), m.len(0.068f)), CornerRadius(m.len(0.018f)))
                drawRoundRect(LaserCore, Offset(c.x - m.len(0.008f), c.y - m.len(0.025f)), Size(m.len(0.016f), m.len(0.050f)), CornerRadius(m.len(0.008f)))
            }
        }

        // Bombs: an amber bolt wobbling like the classic zig-zag shot.
        for (b in game.bombs) {
            if (!b.alive) continue
            val c = Offset(m.x(b.x), m.y(b.y))
            rotate(sin(b.wobble) * 0.45f * 57.2958f, c) {
                drawRoundRect(BombGlow.copy(alpha = 0.45f), Offset(c.x - m.len(0.016f), c.y - m.len(0.032f)), Size(m.len(0.032f), m.len(0.064f)), CornerRadius(m.len(0.016f)))
                drawRoundRect(BombCore, Offset(c.x - m.len(0.007f), c.y - m.len(0.024f)), Size(m.len(0.014f), m.len(0.048f)), CornerRadius(m.len(0.007f)))
            }
        }

        // Flash rings: expanding, fading.
        for (e in game.explosions) {
            val p = e.progress
            val r = m.len(e.radius * (1f + p * 3.5f))
            val c = Color(1f, (0.65f + e.r * 0.35f).coerceIn(0f, 1f), 0.10f)
            val center = Offset(m.x(e.x), m.y(e.y))
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = (1f - p) * 0.85f), c.copy(alpha = 0f)), center, r), r, center)
        }
        // Debris fragments: spinning squares that shrink and fade.
        for (p in game.particles) {
            val life = p.life.coerceIn(0f, 1f)
            val s = m.len(p.size * life)
            val c = Color(p.r.coerceIn(0f, 1f), p.g.coerceIn(0f, 1f), p.b.coerceIn(0f, 1f), life)
            rotate(p.rot * 57.2958f, Offset(m.x(p.x), m.y(p.y))) {
                drawRect(c, Offset(m.x(p.x) - s, m.y(p.y) - s), Size(2f * s, 2f * s))
            }
        }

        // The ship, blinking while invulnerable; bobbing on the title.
        val showShip = game.state == Game.State.PLAYING || game.state == Game.State.LEVEL_CLEAR || title
        val blinkOn = game.invuln <= 0f || (t * 12f) % 1f < 0.6f
        if (showShip && blinkOn) {
            val bob = if (title) 0.02f * sin(t * 2f) else 0f
            val tilt = if (title) 0f else game.shipTilt
            drawShip(m, game.shipX, World.SHIP_Y + bob, World.SHIP_SCALE, tilt, 1f)
            if (game.shieldActive) {
                val pulse = 0.60f + 0.40f * sin(t * 9f)
                val c = Offset(m.x(game.shipX), m.y(World.SHIP_Y + bob))
                val r = m.len(World.SHIP_SCALE * 1.85f)
                val sc0 = PowerUpColors[0]
                drawCircle(Brush.radialGradient(listOf(sc0.copy(alpha = 0.10f), sc0.copy(alpha = pulse * 0.55f)), c, r), r, c)
            }
        }
    }

    // The control strip and the bonus bars: shake-free, phone mode, in play.
    if (game.state == Game.State.PLAYING && game.controlMode == Game.ControlMode.STRIP) drawControlStrip(m, game)
    if (game.state == Game.State.PLAYING || game.state == Game.State.LEVEL_CLEAR) {
        drawBonusBars(m, game)
        drawBossHealth(m, game)
    }
}

/** The ship: a delta-wing fighter, dark swept wings under a cyan fuselage
 *  and a white nose, banked by [tilt] radians. [scale] is the half-extent. */
fun DrawScope.drawShip(m: WorldToPixels, wx: Float, wy: Float, scale: Float, tilt: Float, alpha: Float) {
    val c = Offset(m.x(wx), m.y(wy))
    val s = m.len(scale)
    rotate(tilt * 57.2958f, c) {
        val wings = Path().apply {
            moveTo(c.x, c.y - s * 0.55f)
            lineTo(c.x + s, c.y + s * 0.75f)
            lineTo(c.x + s * 0.55f, c.y + s * 0.95f)
            lineTo(c.x, c.y + s * 0.55f)
            lineTo(c.x - s * 0.55f, c.y + s * 0.95f)
            lineTo(c.x - s, c.y + s * 0.75f)
            close()
        }
        drawPath(wings, ShipHull.copy(alpha = alpha))
        val body = Path().apply {
            moveTo(c.x, c.y - s)
            lineTo(c.x + s * 0.28f, c.y + s * 0.85f)
            lineTo(c.x - s * 0.28f, c.y + s * 0.85f)
            close()
        }
        drawPath(body, ShipCyan.copy(alpha = alpha))
        val nose = Path().apply {
            moveTo(c.x, c.y - s)
            lineTo(c.x + s * 0.10f, c.y - s * 0.30f)
            lineTo(c.x - s * 0.10f, c.y - s * 0.30f)
            close()
        }
        drawPath(nose, ShipCyanLight.copy(alpha = alpha))
    }
}

/** A bonus: a pulsing glow, a spinning diamond in its colour, a white heart. */
fun DrawScope.drawPowerUp(m: WorldToPixels, pu: Game.PowerUp, animTime: Float) {
    val c = PowerUpColors[pu.type.ordinal]
    val center = Offset(m.x(pu.x), m.y(pu.y))
    val sz = m.len(0.035f)
    val glow = sz * (1.6f + 0.25f * sin(animTime * 5f))
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f), c.copy(alpha = 0f)), center, glow), glow, center)
    rotate(pu.rot * 57.2958f, center) {
        drawRect(c, Offset(center.x - sz, center.y - sz), Size(2f * sz, 2f * sz))
    }
    rotate(pu.rot * 57.2958f + 45f, center) {
        val k = sz * 0.38f
        drawRect(Color.White.copy(alpha = 0.85f), Offset(center.x - k, center.y - k), Size(2f * k, 2f * k))
    }
}

/** The touch strip below the ship: a faint backdrop, a divider line, and a
 *  steering marker under the ship so the player knows where their finger acts. */
private fun DrawScope.drawControlStrip(m: WorldToPixels, game: Game) {
    val held = game.moveHeld
    val top = m.y(World.STRIP_TOP_Y)
    drawRect(Color(0xFF4D99FF).copy(alpha = if (held) 0.10f else 0.05f), Offset(0f, top), Size(size.width, size.height - top))
    drawLine(LaneLine.copy(alpha = 0.30f), Offset(0f, top), Offset(size.width, top), strokeWidth = m.len(0.005f))
    val cy = m.y((World.STRIP_TOP_Y + 1f) * 0.5f)
    drawRoundRect(
        Color(0xFF66BFFF).copy(alpha = if (held) 0.90f else 0.40f),
        Offset(m.x(game.shipX) - m.len(0.030f), cy - m.len(0.004f)), Size(m.len(0.060f), m.len(0.008f)),
        CornerRadius(m.len(0.004f)),
    )
}

/** The bonuses in force, at the left of the control strip: the shield as a
 *  steady dot, the timed ones with their remaining time as a bar. */
private fun DrawScope.drawBonusBars(m: WorldToPixels, game: Game) {
    val iconX = -game.aspect + 0.055f
    val barX0 = -game.aspect + 0.095f
    val barMaxW = 0.16f
    var row = 0
    fun timedRow(timer: Float, c: Color) {
        val y = 0.76f + row * 0.06f
        val progress = timer / World.POWER_UP_DURATION
        val pulse = if (timer < 2f) 0.5f + 0.5f * sin(game.animTime * 18f) else 1f
        drawCircle(c.copy(alpha = pulse), m.len(0.020f), Offset(m.x(iconX), m.y(y)))
        drawRect(c.copy(alpha = 0.25f), Offset(m.x(barX0), m.y(y) - m.len(0.006f)), Size(m.len(barMaxW), m.len(0.012f)))
        if (progress > 0.01f)
            drawRect(c.copy(alpha = 0.85f), Offset(m.x(barX0), m.y(y) - m.len(0.006f)), Size(m.len(barMaxW * progress), m.len(0.012f)))
        row++
    }
    if (game.shieldActive) {
        val y = 0.76f + row * 0.06f
        drawCircle(PowerUpColors[0], m.len(0.020f), Offset(m.x(iconX), m.y(y)))
        row++
    }
    if (game.rapidActive) timedRow(game.rapidTimer, PowerUpColors[1])
    if (game.tripleActive) timedRow(game.tripleTimer, PowerUpColors[2])
}

/** The mothership's health, a bar under her flight path. */
private fun DrawScope.drawBossHealth(m: WorldToPixels, game: Game) {
    val boss = game.boss ?: return
    if (!boss.alive) return
    val hw = 0.24f
    val y = -0.675f
    drawRoundRect(BossMagenta.copy(alpha = 0.25f), Offset(m.x(-hw), m.y(y) - m.len(0.008f)), Size(m.len(2f * hw), m.len(0.016f)), CornerRadius(m.len(0.008f)))
    val frac = boss.hp.toFloat() / boss.maxHp
    drawRoundRect(BossMagenta, Offset(m.x(-hw), m.y(y) - m.len(0.008f)), Size(m.len(2f * hw * frac), m.len(0.016f)), CornerRadius(m.len(0.008f)))
}

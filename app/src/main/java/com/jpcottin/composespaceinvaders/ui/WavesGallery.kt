package com.jpcottin.composespaceinvaders.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.jpcottin.composespaceinvaders.engine.Game
import com.jpcottin.composespaceinvaders.engine.Levels
import com.jpcottin.composespaceinvaders.engine.World
import com.jpcottin.composespaceinvaders.theme.SpaceBlack
import com.jpcottin.composespaceinvaders.theme.TierColors
import com.jpcottin.composespaceinvaders.theme.TitleGold

/**
 * The ten waves side by side: every level's formation as the engine builds
 * it, drawn by the same sprites that draw the game. A preview and a
 * screenshot test, so a change to a level's wave is seen, not guessed.
 */
@Composable
fun WavesGallery(modifier: Modifier = Modifier, columns: Int = 2) {
    val waves = remember { (1..Levels.COUNT).map { level -> level to waveOf(level) } }
    Column(modifier.background(MaterialTheme.colorScheme.background).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in waves.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((level, game) in row) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "LEVEL $level",
                            style = MaterialTheme.typography.labelLarge,
                            color = TitleGold,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(WAVE_ASPECT)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpaceBlack),
                        ) {
                            // The wave's band only: the cell is the top of a
                            // taller virtual canvas, and the world is shifted
                            // so the top row sits just under the cell's edge.
                            val m = WorldToPixels(Size(size.width, 2f * size.width / SPAN))
                            for (a in game.aliens) if (a.alive) drawSprite(
                                Sprites.of(a.type), 0, m.x(game.alienX(a)), m.y(game.alienY(a) - Levels.startY(level) + TOP),
                                m.len(World.ALIEN_HW), m.len(World.ALIEN_HH), TierColors[a.type.ordinal],
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A game on [level] with its wave built and nothing in flight. */
private fun waveOf(level: Int): Game = Game().apply {
    setAspect(SPAN / 2f)
    debug.startLevel(level)
}

/** The cell's width for its height: five rows and a margin. */
private const val WAVE_ASPECT = 1.8f
/** World units across the virtual canvas: the wave's width plus margins. */
private const val SPAN = World.COLS * World.COL_STEP + 0.10f
/** The top row's y in the cell. */
private const val TOP = -1f + 0.06f

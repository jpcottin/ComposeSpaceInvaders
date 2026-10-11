package com.jpcottin.composespaceinvaders

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.jpcottin.composespaceinvaders.engine.Game
import com.jpcottin.composespaceinvaders.engine.HighScore
import com.jpcottin.composespaceinvaders.engine.World
import com.jpcottin.composespaceinvaders.theme.SpaceInvadersTheme
import com.jpcottin.composespaceinvaders.ui.GameScreen
import com.jpcottin.composespaceinvaders.ui.GlassesState
import com.jpcottin.composespaceinvaders.ui.Settings
import com.jpcottin.composespaceinvaders.ui.SettingsCard
import com.jpcottin.composespaceinvaders.ui.SettingsContent
import com.jpcottin.composespaceinvaders.ui.WavesGallery

/*
 * Every screen of the game as a Compose preview, rendered by the screenshot
 * tests and compared with the committed references. The engine is seeded
 * and stepped deterministically, so each preview is the same picture every
 * time. The frame loop is paused: the engine is stepped here, not by vsync.
 */

private const val PHONE = 411f / 891f
private const val FOLDABLE = 673f / 841f
private const val TABLET = 1280f / 800f
private const val TV = 1920f / 1080f

private fun step(g: Game, seconds: Float) { repeat((seconds * 60f).toInt()) { g.update(1f / 60f) } }

/** The title screen, one second in, with a podium. */
private fun titleGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreHighScores(listOf(HighScore(24500, 7), HighScore(13100, 5), HighScore(6200, 3)))
    step(this, 1f)
}

/** The title with a level pick: level 4 reached before, level 3 chosen. */
private fun titlePickGame(aspect: Float): Game = titleGame(aspect).apply {
    furthestLevel = 4
    selectLevel(2)
}

/** Level 3, a few invaders down, a laser up, a bomb falling, a bonus
 *  dropping, the shield on and rapid fire running. */
private fun playingGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreSession(3, 1480, 2)
    repeat(3) { debug.killAlien(3 * World.COLS + 2 + it) }
    debug.killAlien(2 * World.COLS + 5)
    debug.activatePowerUp(Game.PowerUpType.SHIELD)
    debug.activatePowerUp(Game.PowerUpType.RAPID)
    debug.spawnPowerUp(-0.20f, 0.20f, Game.PowerUpType.TRIPLE)
    debug.spawnBomb(0.15f, 0.10f, 0.6f)
    debug.setShipX(-0.10f)
    setFire(true)
    step(this, 0.3f)
    setFire(false)
}

private fun levelClearGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreSession(2, 2730, 3)
    for (i in aliens.indices) debug.killAlien(i)
    step(this, 0.5f)
}

/** The boss fight: the mothership at half health, the escort thinned. */
private fun bossGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreSession(10, 41200, 3)
    for (i in 0 until 6) debug.killAlien(World.COLS + i)
    repeat(8) { boss?.let { b -> debug.setBossTime(2.5f) } }
    setFire(true)
    step(this, 1.5f)
    setFire(false)
}

private fun gameOverGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreHighScores(listOf(HighScore(24500, 7), HighScore(6200, 3)))
    restoreSession(5, 9880, 1)
    debug.clearInvuln()
    debug.spawnBomb(0f, World.SHIP_Y - 0.05f, 1f)
    step(this, 1.5f)
}

private fun winGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    restoreSession(10, 49640, 3)
    for (i in aliens.indices) debug.killAlien(i)
    setFire(true)
    var t = 0f
    while (t < 40f && boss?.alive == true) { debug.setShipX(boss!!.x); update(1f / 60f); t += 1f / 60f }
    setFire(false)
    step(this, 1f)
}

@Composable
private fun Screen(game: Game, autoPlay: Boolean = false, tabletop: Boolean = false, glasses: Boolean = false) {
    SpaceInvadersTheme {
        GameScreen(
            game = game, settings = Settings(autoPlay = autoPlay), onOpenSettings = {},
            paused = true, tabletop = tabletop, glasses = glasses,
        )
    }
}

/** The phone's game, frozen while the glasses play. */
private fun handedOffGame(aspect: Float): Game = playingGame(aspect).apply { glassesActive = true }

/** The glasses' own game: touchbar mode, an invader or two down. */
private fun glassesGame(aspect: Float): Game = Game().apply {
    setAspect(aspect)
    controlMode = Game.ControlMode.TOUCHBAR
    restoreSession(2, 310, 3)
    debug.killAlien(World.COLS + 3)
    setFire(true)
    step(this, 0.3f)
    setFire(false)
}

// ── Title ────────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
fun TitlePhoneScreenshot() = Screen(titleGame(PHONE))

@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
fun TitleLevelPickPhoneScreenshot() = Screen(titlePickGame(PHONE))

@PreviewTest
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Composable
fun TitleFoldableScreenshot() = Screen(titleGame(FOLDABLE))

@PreviewTest
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
fun TitleTabletScreenshot() = Screen(titleGame(TABLET))

@PreviewTest
@Preview(name = "TV", device = Devices.TV_1080p, showBackground = true, uiMode = Configuration.UI_MODE_TYPE_TELEVISION)
@Composable
fun TitleTvScreenshot() = Screen(titleGame(TV))

// ── In play ──────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Playing phone", device = Devices.PHONE, showBackground = true)
@Composable
fun PlayingPhoneScreenshot() = Screen(playingGame(PHONE), autoPlay = true)

@PreviewTest
@Preview(name = "Playing foldable", device = Devices.FOLDABLE, showBackground = true)
@Composable
fun PlayingFoldableScreenshot() = Screen(playingGame(FOLDABLE))

@PreviewTest
@Preview(name = "Playing TV", device = Devices.TV_1080p, showBackground = true, uiMode = Configuration.UI_MODE_TYPE_TELEVISION)
@Composable
fun PlayingTvScreenshot() = Screen(playingGame(TV))

/** A foldable half-opened: the battlefield above the hinge, the control surface below. */
@PreviewTest
@Preview(name = "Tabletop foldable", device = Devices.FOLDABLE, showBackground = true)
@Composable
fun PlayingTabletopScreenshot() = Screen(playingGame(673f / 420f), tabletop = true)

/** A round frozen mid-flight, waiting for the tap. */
private fun pausedGame(aspect: Float): Game = playingGame(aspect).apply { pause() }

@PreviewTest
@Preview(name = "Paused", device = Devices.PHONE, showBackground = true)
@Composable
fun PausedScreenshot() = Screen(pausedGame(PHONE))

@PreviewTest
@Preview(name = "Level clear", device = Devices.PHONE, showBackground = true)
@Composable
fun LevelClearScreenshot() = Screen(levelClearGame(PHONE))

@PreviewTest
@Preview(name = "Mothership", device = Devices.PHONE, showBackground = true)
@Composable
fun BossPhoneScreenshot() = Screen(bossGame(PHONE))

// ── End screens ──────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Game over", device = Devices.PHONE, showBackground = true)
@Composable
fun GameOverScreenshot() = Screen(gameOverGame(PHONE))

@PreviewTest
@Preview(name = "Win tablet", device = Devices.TABLET, showBackground = true)
@Composable
fun WinTabletScreenshot() = Screen(winGame(TABLET))

// ── The ten waves ────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Waves", widthDp = 720, heightDp = 1280, showBackground = true)
@Composable
fun WavesGalleryScreenshot() {
    SpaceInvadersTheme { WavesGallery() }
}

// ── AI Glasses ───────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Glasses", widthDp = 640, heightDp = 360, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun GlassesScreenshot() = Screen(glassesGame(640f / 360f), glasses = true)

@PreviewTest
@Preview(name = "Glasses title", widthDp = 640, heightDp = 360, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun GlassesTitleScreenshot() = Screen(titlePickGame(640f / 360f).apply { controlMode = Game.ControlMode.TOUCHBAR }, glasses = true)

@PreviewTest
@Preview(name = "On glasses banner", device = Devices.PHONE, showBackground = true)
@Composable
fun OnGlassesScreenshot() = Screen(handedOffGame(PHONE))

@PreviewTest
@Preview(name = "Settings glasses paired", widthDp = 400, heightDp = 520, showBackground = true)
@Composable
fun SettingsGlassesPairedScreenshot() {
    SpaceInvadersTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SettingsContent(
                settings = Settings(), onSoundChange = {}, onAutoPlayChange = {}, onDone = {},
                glasses = GlassesState.CONNECTED,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Settings glasses active", widthDp = 400, heightDp = 520, showBackground = true)
@Composable
fun SettingsGlassesActiveScreenshot() {
    SpaceInvadersTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SettingsContent(
                settings = Settings(), onSoundChange = {}, onAutoPlayChange = {}, onDone = {},
                glasses = GlassesState.ACTIVE,
            )
        }
    }
}

// ── Settings ─────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Settings card", widthDp = 640, heightDp = 560, showBackground = true, backgroundColor = 0xFF080A17)
@Composable
fun SettingsCardScreenshot() {
    SpaceInvadersTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SettingsCard(
                settings = Settings(soundEnabled = true, autoPlay = false),
                onSoundChange = {}, onAutoPlayChange = {}, onDone = {},
                glasses = GlassesState.CONNECTED,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Settings", widthDp = 400, heightDp = 520, showBackground = true)
@Composable
fun SettingsScreenshot() {
    SpaceInvadersTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SettingsContent(
                settings = Settings(soundEnabled = true, autoPlay = true),
                onSoundChange = {},
                onAutoPlayChange = {},
                onDone = {},
            )
        }
    }
}

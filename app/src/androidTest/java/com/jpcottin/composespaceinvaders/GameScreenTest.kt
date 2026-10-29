package com.jpcottin.composespaceinvaders

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jpcottin.composespaceinvaders.engine.Game
import com.jpcottin.composespaceinvaders.engine.HighScore
import com.jpcottin.composespaceinvaders.engine.World
import com.jpcottin.composespaceinvaders.ui.GameViewModel
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches the real activity: the title must be up, a tap must start a
 * round with the HUD, and the settings sheet must open from the gear.
 *
 * The game steps once per frame for as long as it is on screen, which
 * Compose's test harness would otherwise read as "never idle". The test
 * clock is paused and advanced by hand, so every frame is explicit.
 */
@RunWith(AndroidJUnit4::class)
class GameScreenTest {

    @get:Rule val composeTestRule = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchFromACleanStore() {
        // Settings and scores persist in DataStore: a switch left on by an
        // earlier run (Auto Play steering the ship) must not reach a test.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "datastore").deleteRecursively()
        composeTestRule.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeTheActivity() {
        scenario.close()
    }

    private fun frames(count: Int) = composeTestRule.mainClock.advanceTimeBy(16L * count)

    /** Focus the battlefield and prove it has focus before a key goes in:
     *  a key sent to an unfocused node is silently lost, and the failure
     *  that follows would point at the game instead of at the focus. */
    private fun focusThePlayfield(): SemanticsNodeInteraction {
        val playfield = composeTestRule.onNodeWithContentDescription("Playfield")
        for (attempt in 1..5) {
            playfield.performSemanticsAction(SemanticsActions.RequestFocus)
            frames(2)
            if (runCatching { playfield.assertIsFocused() }.isSuccess) break
        }
        playfield.assertIsFocused()
        return playfield
    }

    /** Step frames until [condition] holds, up to [maxFrames]. */
    private fun stepUntil(maxFrames: Int, condition: () -> Boolean) {
        var left = maxFrames
        while (left > 0 && !condition()) { frames(1); left-- }
    }

    /** The engine behind the screen, from the activity's ViewModel store. */
    private fun game(): Game {
        lateinit var game: Game
        scenario.onActivity { game = ViewModelProvider(it)[GameViewModel::class.java].game }
        return game
    }

    @Test
    fun titleIsShownOnLaunch() {
        frames(5)
        composeTestRule.onNodeWithText("INVADERS").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Playfield").assertIsDisplayed()
    }

    @Test
    fun tapStartsARoundWithTheHud() {
        frames(5)
        composeTestRule.onNodeWithContentDescription("Playfield").performClick()
        frames(5)
        composeTestRule.onNode(hasContentDescription("HUD")).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Score 0").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Lives 3").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Level 1").assertIsDisplayed()
    }

    @Test
    fun gearOpensSettings() {
        frames(5)
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        frames(2)
        // The game pauses under the sheet, so the composition settles on its
        // own from here: let the clock run and the sheet slide in.
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sound").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto Play").assertIsDisplayed()
        composeTestRule.onNodeWithText("Glasses").assertIsDisplayed()
        composeTestRule.onNodeWithText("No AI Glasses paired").assertIsDisplayed()   // none on an emulator
    }

    @Test
    fun dPadStartsTheRoundAndSteers() {
        frames(5)
        val playfield = focusThePlayfield()
        playfield.performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }   // start the round
        val game = game()
        stepUntil(10) { game.state == Game.State.PLAYING }
        assertEquals(Game.State.PLAYING, game.state)
        playfield.performKeyInput { keyDown(Key.DirectionRight) }
        frames(20)
        playfield.performKeyInput { keyUp(Key.DirectionRight) }
        frames(2)
        assertTrue("ship at ${game.shipX}", game.shipX > 0.05f)
        val held = game.shipX
        frames(20)
        assertEquals(held, game.shipX, 1e-6f)                      // released: it stays
    }

    @Test
    fun theDpadCentreStartsTheRoundThenFires() {
        // On a TV the same button starts the round and, held during it,
        // holds the trigger: OK is a tap on the title and the fire key in play.
        frames(5)
        val playfield = focusThePlayfield()
        playfield.performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        val game = game()
        stepUntil(10) { game.state == Game.State.PLAYING }
        assertEquals(Game.State.PLAYING, game.state)
        assertEquals(0, game.shotsFired)                             // the tap that started it fired nothing
        playfield.performKeyInput { keyDown(Key.DirectionCenter) }
        stepUntil(10) { game.shotsFired > 0 }
        assertTrue("OK held should fire", game.shotsFired > 0)
        frames(60)                                                   // still held: the volleys keep coming
        assertTrue("OK held should keep firing", game.shotsFired >= 2)
        playfield.performKeyInput { keyUp(Key.DirectionCenter) }
        frames(2)
        val fired = game.shotsFired
        frames(60)
        assertEquals(fired, game.shotsFired)                         // released: the trigger is up
    }

    @Test
    fun theFireKeyHoldsTheTrigger() {
        val game = startARound()
        val playfield = focusThePlayfield()
        playfield.performKeyInput { keyDown(Key.Spacebar) }
        stepUntil(10) { game.shotsFired > 0 }
        assertTrue("a laser should have left", game.shotsFired > 0)
        playfield.performKeyInput { keyUp(Key.Spacebar) }
        frames(2)
        val fired = game.shotsFired
        frames(60)
        assertEquals(fired, game.shotsFired)                         // released: the trigger is up
        assertEquals(Game.State.PLAYING, game.state)
    }

    /** A round in flight: the HUD up, the wave marching. */
    private fun startARound(): Game {
        frames(5)
        composeTestRule.onNodeWithContentDescription("Playfield").performClick()
        frames(10)
        val game = game()
        assertEquals(Game.State.PLAYING, game.state)
        return game
    }

    @Test
    fun leavingTheScreenPausesTheRoundAndATapResumesIt() {
        // A free-form window (an XR panel, a desktop window) stays resumed
        // behind the scenario's cover activity: nothing to pause it with.
        var multiWindow = false
        scenario.onActivity { multiWindow = it.isInMultiWindowMode }
        assumeFalse("free-form windows stay resumed", multiWindow)
        val game = startARound()
        scenario.moveToState(Lifecycle.State.STARTED)                 // the screen goes away
        frames(2)
        assertTrue(game.paused)
        scenario.moveToState(Lifecycle.State.RESUMED)                 // and comes back
        frames(5)
        assertTrue(game.paused)                                       // still waiting for the player
        composeTestRule.onNodeWithContentDescription("Paused").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Playfield").performClick()
        frames(5)
        assertFalse(game.paused)
    }

    @Test
    fun aRecreatedActivityKeepsTheRound() {
        // A rotation, a fold, a theme change: the activity is recreated and
        // the engine, held by the ViewModel, carries on where it was.
        val game = startARound()
        frames(30)
        val score = game.score
        val level = game.level
        scenario.recreate()
        frames(5)
        val again = game()
        assertTrue("the engine should survive the recreation", again === game)
        assertEquals(Game.State.PLAYING, again.state)
        assertEquals(level, again.level)
        assertTrue(again.score >= score)
        composeTestRule.onNodeWithContentDescription("Level $level").assertIsDisplayed()
    }

    @Test
    fun theGearPausesARoundInFlight() {
        val game = startARound()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        frames(2)
        assertTrue(game.paused)
    }

    @Test
    fun theGearLetsGoOfASteeringKey() {
        val game = startARound()
        val playfield = focusThePlayfield()
        playfield.performKeyInput { keyDown(Key.DirectionRight) }
        frames(2)
        assertTrue("the key should steer", game.moveHeld)
        // The sheet takes the focus with it: the key's release lands there,
        // never on the playfield.
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        frames(2)
        assertTrue(game.paused)
        assertFalse("a key held when the sheet opened must not steer on", game.moveHeld)
        runCatching { playfield.performKeyInput { keyUp(Key.DirectionRight) } }
    }

    @Test
    fun leavingTheScreenLetsGoOfTheFireKey() {
        var multiWindow = false
        scenario.onActivity { multiWindow = it.isInMultiWindowMode }
        assumeFalse("free-form windows stay resumed", multiWindow)
        val game = startARound()
        val playfield = focusThePlayfield()
        playfield.performKeyInput { keyDown(Key.Spacebar) }
        frames(2)
        assertTrue("the key should hold the trigger", game.fireHeld)
        // Another app comes to the front: the key is released over there.
        scenario.moveToState(Lifecycle.State.STARTED)
        frames(2)
        assertTrue(game.paused)
        assertFalse("a key held when the screen went away must not fire on", game.fireHeld)
        scenario.moveToState(Lifecycle.State.RESUMED)
        frames(2)
        runCatching { playfield.performKeyInput { keyUp(Key.Spacebar) } }
    }

    @Test
    fun escapeFreezesTheRoundAndFreesItAgain() {
        val game = startARound()
        val playfield = focusThePlayfield()
        playfield.performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        stepUntil(10) { game.paused }
        assertTrue("Escape should have paused the round", game.paused)
        frames(2)                                                    // the overlay follows the flag by a frame
        composeTestRule.onNodeWithContentDescription("Paused").assertIsDisplayed()
        playfield.performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        stepUntil(10) { !game.paused }
        assertFalse("Escape again should have resumed it", game.paused)
        assertEquals(Game.State.PLAYING, game.state)
    }

    @Test
    fun backFreezesTheRoundInsteadOfLeaving() {
        val game = startARound()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        frames(2)
        assertTrue(game.paused)
        assertEquals(Lifecycle.State.RESUMED, scenario.state)       // still here
        composeTestRule.onNodeWithContentDescription("Paused").assertIsDisplayed()
    }

    @Test
    fun theTitleOffersAnyLevelReachedBefore() {
        frames(5)
        composeTestRule.onNodeWithContentDescription("Next level").assertDoesNotExist()   // nothing reached yet
        val game = game()
        scenario.onActivity { game.furthestLevel = 3 }
        frames(2)
        composeTestRule.onNodeWithContentDescription("Start level 1").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next level").performClick()
        frames(2)
        composeTestRule.onNodeWithContentDescription("Start level 2").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Playfield").performClick()         // start the round
        frames(5)
        assertEquals(Game.State.PLAYING, game.state)
        assertEquals(2, game.level)
        composeTestRule.onNodeWithContentDescription("Level 2").assertIsDisplayed()
    }

    @Test
    fun resetProgressForgetsTheScoresAndTheLevelRecord() {
        frames(5)
        val game = game()
        scenario.onActivity {
            game.furthestLevel = 4
            game.restoreHighScores(listOf(HighScore(900, 2)))
        }
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.onNodeWithText("Reset progress").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()                 // thought better of it
        composeTestRule.waitForIdle()
        assertEquals(4, game.furthestLevel)
        composeTestRule.onNodeWithText("Reset progress").performClick()
        composeTestRule.onNodeWithText("Reset").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, game.furthestLevel)
        assertTrue(game.highScores.isEmpty())
    }

    @Test
    fun leftAndRightOnTheTitlePickTheLevel() {
        frames(5)
        val game = game()
        scenario.onActivity { game.furthestLevel = 3 }
        val playfield = focusThePlayfield()
        playfield.performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        stepUntil(10) { game.startLevel == 2 }
        assertEquals(2, game.startLevel)
        playfield.performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        playfield.performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }   // past the record: stays
        stepUntil(10) { game.startLevel == 3 }
        assertEquals(3, game.startLevel)
        playfield.performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
        stepUntil(10) { game.startLevel == 2 }
        assertEquals(2, game.startLevel)
        frames(2)
        composeTestRule.onNodeWithContentDescription("Start level 2").assertIsDisplayed()
        playfield.performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }  // and OK starts there
        stepUntil(10) { game.state == Game.State.PLAYING }
        assertEquals(2, game.level)
        assertEquals(0f, game.shipX, 1e-6f)                                                     // the keys picked, not steered
    }

    @Test
    fun aScreenReaderHearsTheMothership() {
        frames(5)
        val game = game()
        scenario.onActivity { game.furthestLevel = 10; game.selectLevel(9) }   // straight to the boss level
        composeTestRule.onNodeWithContentDescription("Playfield").performClick()
        frames(5)
        assertEquals(10, game.level)
        composeTestRule.onNodeWithContentDescription("Playfield").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Level 10, 16 invaders left, 3 lives, the mothership has 16 hits left"),
        )
        composeTestRule.onNodeWithContentDescription("Mothership 16").assertExists()
    }

    @Test
    fun aScreenReaderHearsTheRoundStart() {
        frames(5)
        composeTestRule.onNodeWithContentDescription("Playfield").performClick()
        frames(5)
        val liveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        composeTestRule.onNode(liveRegion and hasContentDescription("Level 1 begins")).assertExists()
        composeTestRule.onNodeWithContentDescription("Playfield")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Level 1, 24 invaders left, 3 lives"))
    }

    @Test
    fun aSteerKeyReleasedOnTheTitleDoesNotSteerTheNextRound() {
        val game = startARound()
        val playfield = focusThePlayfield()
        scenario.onActivity {
            game.debug.setLives(1); game.debug.clearInvuln()
            game.debug.spawnBomb(0f, World.SHIP_Y - 0.05f, 1f)
        }
        stepUntil(60) { game.state == Game.State.GAME_OVER }
        assertEquals(Game.State.GAME_OVER, game.state)
        playfield.performKeyInput { keyDown(Key.DirectionRight) }     // held through the end screen
        frames(60)                                                    // past END_SCREEN_GRACE
        playfield.performKeyInput { keyDown(Key.Spacebar); keyUp(Key.Spacebar) }   // back to the title
        stepUntil(10) { game.state == Game.State.TITLE }
        assertEquals(Game.State.TITLE, game.state)
        playfield.performKeyInput { keyUp(Key.DirectionRight) }       // released on the title
        playfield.performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        stepUntil(10) { game.state == Game.State.PLAYING }
        assertEquals(Game.State.PLAYING, game.state)
        frames(20)
        assertEquals(0f, game.shipX, 1e-6f)                           // nothing is steering
    }

}

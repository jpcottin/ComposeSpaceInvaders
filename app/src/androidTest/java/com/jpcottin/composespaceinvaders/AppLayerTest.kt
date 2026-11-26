package com.jpcottin.composespaceinvaders

import android.os.Bundle
import java.util.Locale
import com.jpcottin.composespaceinvaders.ui.GameScreen
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import android.os.LocaleList
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.jpcottin.composespaceinvaders.glasses.GlassesSession
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jpcottin.composespaceinvaders.engine.Game
import com.jpcottin.composespaceinvaders.ui.GameViewModel
import com.jpcottin.composespaceinvaders.ui.Settings
import com.jpcottin.composespaceinvaders.ui.SettingsContent
import com.jpcottin.composespaceinvaders.theme.SpaceInvadersTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The app layer around the engine: the settings sheet's semantics and the view model's restore. */
@RunWith(AndroidJUnit4::class)
class AppLayerTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun aScreenReaderHearsWhetherEachSwitchIsOn() {
        composeTestRule.setContent {
            SpaceInvadersTheme {
                SettingsContent(
                    settings = Settings(soundEnabled = true, haptics = false, autoPlay = true),
                    onSoundChange = {}, onAutoPlayChange = {}, onDone = {},
                )
            }
        }
        composeTestRule.onNode(hasText("Sound") and isToggleable()).assertIsOn()
        composeTestRule.onNode(hasText("Haptics") and isToggleable()).assertIsOff()
        composeTestRule.onNode(hasText("Auto Play") and isToggleable()).assertIsOn()
    }

    @Test
    fun doneIsReachableOnAShortWindow() {
        // A phone in landscape, or a large font, leaves less room than the rows need.
        composeTestRule.setContent {
            SpaceInvadersTheme {
                Box(Modifier.height(320.dp)) { SettingsContent(Settings(), onSoundChange = {}, onAutoPlayChange = {}, onDone = {}) }
            }
        }
        composeTestRule.onNodeWithText("Done").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aRoundRestoredAfterProcessDeathWaitsForTheTap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "restore-${System.nanoTime()}.preferences_pb")
        }
        val session = Bundle().apply {
            putBoolean(GameViewModel.SESSION_MID_GAME, true)
            putInt(GameViewModel.SESSION_LEVEL, 3)
            putLong(GameViewModel.SESSION_SCORE, 1200L)
            putInt(GameViewModel.SESSION_LIVES, 2)
        }
        val vm = GameViewModel(store, SavedStateHandle(mapOf(GameViewModel.KEY_SESSION to session)), vibrator = null)
        try {
            assertEquals(Game.State.PLAYING, vm.game.state)
            assertEquals(3, vm.game.level)
            assertTrue("a restored round should wait for the player", vm.game.paused)
        } finally {
            closeViewModel(vm, scope)
        }
    }

    @Test
    fun theLevelsReachedOnTheGlassesComeHomeWithTheGame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "glasses-${System.nanoTime()}.preferences_pb")
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var vm: GameViewModel
        instrumentation.runOnMainSync { vm = GameViewModel(store, SavedStateHandle(), vibrator = null) }
        try {
            instrumentation.runOnMainSync { GlassesSession.setActive(true) }
            // The glasses instance reaches level 6 and records it in the shared store.
            runBlocking { store.edit { it[GameViewModel.KEY_FURTHEST_LEVEL] = 6 } }
            instrumentation.runOnMainSync { GlassesSession.bringBack() }
            var furthest = 0
            for (i in 0 until 100) {
                instrumentation.runOnMainSync { furthest = vm.game.furthestLevel }
                if (furthest == 6) break
                Thread.sleep(20)
            }
            assertEquals(6, furthest)
        } finally {
            instrumentation.runOnMainSync { GlassesSession.bringBack() }
            closeViewModel(vm, scope)
        }
    }

    /**
     * Clear [vm] (onCleared releases the audio), wait for the reads it still
     * has in flight to wind down, and only then close the store's [scope]: a
     * store closed under a pending read completes it empty, and the read
     * throws on the main thread and fails the test.
     */
    private fun closeViewModel(vm: GameViewModel, scope: CoroutineScope) {
        ViewModelStore().also { it.put("vm", vm) }.clear()
        runBlocking { vm.viewModelScope.coroutineContext.job.join() }
        scope.cancel()
    }

    @Test
    fun aFrenchScreenReaderHearsFrenchLabels() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(Locale.FRENCH)) }
        val french = base.createConfigurationContext(config)
        val game = Game().apply { setAspect(411f / 891f) }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides french, LocalConfiguration provides config, LocalResources provides french.resources) {
                SpaceInvadersTheme { GameScreen(game, Settings(), onOpenSettings = {}, paused = true) }
            }
        }
        composeTestRule.onNodeWithContentDescription(french.getString(R.string.a11y_playfield)).assertExists()
        composeTestRule.onNodeWithContentDescription("Playfield").assertDoesNotExist()
    }

}

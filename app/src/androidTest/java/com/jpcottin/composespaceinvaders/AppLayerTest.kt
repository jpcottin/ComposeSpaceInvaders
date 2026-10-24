package com.jpcottin.composespaceinvaders

import android.os.Bundle
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
            ViewModelStore().also { it.put("vm", vm) }.clear()   // onCleared: audio released
            scope.cancel()
        }
    }
}

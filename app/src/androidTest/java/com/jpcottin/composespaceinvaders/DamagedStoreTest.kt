package com.jpcottin.composespaceinvaders

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jpcottin.composespaceinvaders.ui.GameViewModel
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A settings file that cannot be parsed must not take the process down on the first write. */
@RunWith(AndroidJUnit4::class)
class DamagedStoreTest {
    @get:Rule val composeTestRule = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @After
    fun closeTheActivity() {
        if (::scenario.isInitialized) scenario.close()
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "datastore").deleteRecursively()
    }

    @Test
    fun aDamagedSettingsFileSurvivesTheFirstWrite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "datastore").apply { deleteRecursively(); mkdirs() }
        File(dir, "settings.preferences_pb").writeBytes(ByteArray(64) { 0x7F })   // not a Preferences proto
        composeTestRule.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeTestRule.mainClock.advanceTimeBy(16L * 5)
        scenario.onActivity { ViewModelProvider(it)[GameViewModel::class.java].setHaptics(false) }
        // The title screen animates every frame, so the clock stays manual;
        // the write runs on the store's own thread meanwhile.
        for (i in 0 until 30) { composeTestRule.mainClock.advanceTimeBy(16L); Thread.sleep(20) }
        assertEquals(Lifecycle.State.RESUMED, scenario.state)        // the process is still here
    }
}

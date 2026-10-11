package com.jpcottin.composespaceinvaders.engine

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RATE = 48000

private fun render(s: Synth, seconds: Float): FloatArray {
    val n = (seconds * RATE).toInt()
    val out = FloatArray(n)
    var i = 0
    while (i < n) {
        val block = minOf(256, n - i)
        val tmp = FloatArray(block)
        s.render(tmp, block)
        tmp.copyInto(out, i)
        i += block
    }
    return out
}

private fun peak(a: FloatArray, from: Int = 0, to: Int = a.size): Float {
    var p = 0f
    for (i in from until to) p = maxOf(p, abs(a[i]))
    return p
}

/** Magnitude of the DFT at [hz] over the samples from [from] on. */
private fun energyAt(a: FloatArray, from: Int, hz: Float): Float {
    var re = 0.0; var im = 0.0
    for (i in from until a.size) {
        val ph = 2.0 * Math.PI * hz * i / RATE
        re += a[i] * Math.cos(ph); im -= a[i] * Math.sin(ph)
    }
    return Math.hypot(re, im).toFloat()
}

class SynthTest {
    @Test fun silentUntilTriggered() {
        val s = Synth(RATE)
        assertEquals(0f, peak(render(s, 0.2f)), 0f)
        assertFalse(s.anyVoiceActive)
    }

    @Test fun everyEffectSoundsAndEnds() {
        for (e in SoundEvent.entries) {
            val s = Synth(RATE)
            s.trigger(e)
            val a = render(s, 1.0f)
            assertTrue("$e is silent", peak(a, 0, RATE / 10) > 0.05f)
            assertFalse("$e never ends", s.anyVoiceActive)
            assertEquals("$e leaves a tail", 0f, peak(a, a.size - RATE / 10, a.size), 0f)
        }
    }

    @Test fun theMarchPlaysTheStepsNote() {
        // Each step carries its own bass note: its energy at that pitch
        // dwarfs the energy at the next step's pitch.
        for (step in 0 until 4) {
            val s = Synth(RATE)
            s.march(step)
            val a = render(s, 0.09f)
            val hz = Synth.MARCH_FREQS[step]
            val other = Synth.MARCH_FREQS[(step + 2) % 4]
            assertTrue("step $step", energyAt(a, 0, hz) > 2f * energyAt(a, 0, other))
        }
    }

    @Test fun theSirenFadesInAndOut() {
        val s = Synth(RATE)
        s.saucer = true
        val a = render(s, 0.5f)
        assertTrue(peak(a, 0, RATE / 100) < peak(a, a.size - RATE / 100, a.size))   // easing in
        assertTrue(s.sirenAudible)
        s.saucer = false
        render(s, 1.5f)
        assertFalse(s.sirenAudible)
        assertEquals(0f, peak(render(s, 0.1f)), 0f)
    }

    @Test fun outputStaysInRangeWhenEverythingStacks() {
        val s = Synth(RATE)
        for (e in SoundEvent.entries) s.trigger(e)
        s.march(0)
        s.saucer = true
        s.musicEnabled = true
        val a = render(s, 1.0f)
        assertTrue(peak(a) <= 1f)
        assertTrue(peak(a) > 0.3f)
    }

    @Test fun musicPlaysOnlyWhenEnabled() {
        val s = Synth(RATE)
        assertEquals(0f, peak(render(s, 0.5f)), 0f)
        s.musicEnabled = true
        assertTrue(peak(render(s, 0.5f)) > 0.02f)
        s.musicEnabled = false
        assertEquals(0f, peak(render(s, 0.5f)), 0f)
    }

    @Test fun musicKeepsItsLevelOverMinutes() {
        // Wrapping phases, not a growing clock: after ten minutes the track
        // is as loud as in its first bar.
        val s = Synth(RATE)
        s.musicEnabled = true
        val first = peak(render(s, 2f))
        repeat(10 * 30) { render(s, 2f) }   // ten minutes
        val later = peak(render(s, 2f))
        assertEquals(first, later, first * 0.25f)
    }

    @Test fun triggersCoalesceWithinABlock() {
        // Ten lasers before the next block: one voice, not ten stacked.
        val s = Synth(RATE)
        repeat(10) { s.trigger(SoundEvent.LASER) }
        val a = render(s, 0.05f)
        val single = Synth(RATE).also { it.trigger(SoundEvent.LASER) }.let { render(it, 0.05f) }
        assertEquals(peak(single), peak(a), 1e-5f)
    }

    @Test fun voicesAreCappedAndTheOldestIsStolen() {
        val s = Synth(RATE)
        repeat(Synth.VOICES + 3) { s.march(it % 4); render(s, 0.001f) }
        assertTrue(s.anyVoiceActive)
        render(s, 1f)
        assertFalse(s.anyVoiceActive)
    }
}

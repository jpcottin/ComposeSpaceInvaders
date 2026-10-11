package com.jpcottin.composespaceinvaders.engine

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Every sound in the game, synthesised in real time: there are no audio
 * files. Pure Kotlin, so it is tested on the JVM; the app feeds its output
 * to an AudioTrack.
 *
 * Two threads touch it: the game thread triggers effects ([trigger],
 * [march], [saucer], [musicEnabled]) and the audio thread renders
 * ([render]). Triggers are a bit mask and a march step the audio thread
 * drains at the start of every block, so the game never waits on audio.
 */
class Synth(sampleRate: Int) {

    private val dt = 1f / sampleRate

    private enum class Kind { LASER, EXPLOSION, HIT, CLEAR, POWERUP, MARCH }

    private class Voice {
        var kind = Kind.LASER
        var t = 0f
        var phase = 0f
        var freq = 0f      // MARCH: the note chosen at trigger time
        var active = false
    }

    private val voices = Array(VOICES) { Voice() }
    private val pending = AtomicInteger(0)
    private val marchPending = AtomicInteger(-1)
    /** Whether the saucer's siren sounds. Set from the game thread. */
    @Volatile var saucer = false
    /** Whether the ambient track plays. Set from the game thread. */
    @Volatile var musicEnabled = false
    private var rng = 0xDEADBEEF.toInt()

    // ── Siren state (audio thread only) ──────────────────────────────────────
    private var sirenPhase = 0f
    private var sirenWobble = 0f
    private var sirenEnv = 0f

    // ── Background music state (audio thread only) ───────────────────────────
    // 80 BPM ambient space track in A minor: pad + bass + arpeggio. Wrapping
    // phases instead of an ever-growing time accumulator: a float that keeps
    // counting seconds loses the per-sample step after ~8 minutes.
    private var tremPhase = 0f             // [0,1) at 0.28 Hz
    private var barPhase = 0f              // [0,4) beats
    private val padPhase = FloatArray(3)
    private var bassPhase = 0f
    private var bassEnv = 0f
    private var arpPhase = 0f
    private var arpEnv = 0.55f             // start audible on step 0 immediately
    private var arpTimer = 0f
    private var arpStep = 0
    private var lastBeat = -1

    // ── Game thread ───────────────────────────────────────────────────────────

    fun trigger(event: SoundEvent) {
        val bit = when (event) {
            SoundEvent.LASER -> 1
            SoundEvent.EXPLOSION -> 2
            SoundEvent.SHIP_HIT -> 4
            SoundEvent.LEVEL_CLEAR -> 8
            SoundEvent.POWER_UP -> 16
        }
        pending.getAndUpdate { it or bit }
    }

    /** One step of the invader march: note [step] (0..3) of the four-note bass. */
    fun march(step: Int) {
        marchPending.set(step and 3)
    }

    // ── Audio thread ──────────────────────────────────────────────────────────

    /** Fills [out] with [frames] mono samples in -1..1. */
    fun render(out: FloatArray, frames: Int) {
        val mask = pending.getAndSet(0)
        if (mask and 1 != 0) activate(Kind.LASER)
        if (mask and 2 != 0) activate(Kind.EXPLOSION)
        if (mask and 4 != 0) activate(Kind.HIT)
        if (mask and 8 != 0) activate(Kind.CLEAR)
        if (mask and 16 != 0) activate(Kind.POWERUP)
        val step = marchPending.getAndSet(-1)
        if (step >= 0) activate(Kind.MARCH, MARCH_FREQS[step])
        val siren = saucer
        val music = musicEnabled
        for (i in 0 until frames) {
            var s = 0f
            for (v in voices) if (v.active) s += genVoice(v)
            // The saucer's siren: the pitch warbles between about 550 and
            // 900 Hz. The envelope eases in and out so toggling never clicks.
            sirenEnv += ((if (siren) 1f else 0f) - sirenEnv) * 0.0006f
            if (sirenEnv > 0.001f) {
                sirenWobble += dt * 6f
                if (sirenWobble >= 1f) sirenWobble -= 1f
                val freq = 725f + 175f * sin(sirenWobble * TAU)
                sirenPhase += dt * freq
                if (sirenPhase >= 1f) sirenPhase -= 1f
                s += sin(sirenPhase * TAU) * sirenEnv * 0.10f
            }
            if (music) s += genMusic()
            // Soft clip: stacked explosions, the march and the siren
            // compress instead of turning into a square wave.
            out[i] = tanh(s)
        }
    }

    /** True while any effect is sounding (tests, and the output can idle). */
    val anyVoiceActive: Boolean get() = voices.any { it.active }

    /** True while the siren is audible, including its fade. */
    val sirenAudible: Boolean get() = sirenEnv > 0.001f

    private fun activate(kind: Kind, freq: Float = 0f) {
        val v = voices.firstOrNull { !it.active } ?: voices[0]
        v.kind = kind; v.t = 0f; v.phase = 0f; v.freq = freq; v.active = true
    }

    private fun white(): Float {
        var x = rng
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        rng = x
        return (x and 0xFFFF).toFloat() / 32768f - 1f
    }

    /** Advance a wrapping oscillator phase and return the sine at it. */
    private fun osc(v: Voice, freq: Float): Float {
        v.phase += dt * freq
        if (v.phase >= 1f) v.phase -= 1f
        return sin(v.phase * TAU)
    }

    private fun genVoice(v: Voice): Float {
        var s = 0f
        when (v.kind) {
            Kind.LASER -> {
                // A pitch sweep down from 1100 Hz to 350 Hz.
                val dur = 0.12f
                if (v.t > dur) { v.active = false } else {
                    val prog = v.t / dur
                    s = osc(v, 1100f - 750f * prog) * (1f - prog) * 0.42f
                }
            }
            Kind.EXPLOSION -> {
                // A crack of noise, a filtered rumble and a 55 Hz boom. The
                // gains sum to about 0.9 so one explosion never hits the clipper.
                val dur = 0.50f
                if (v.t > dur) { v.active = false } else {
                    val crack = white() * exp(-v.t / 0.010f) * 0.32f
                    v.phase = v.phase * 0.48f + white() * 0.52f   // the phase doubles as the filter state
                    val rumble = v.phase * exp(-v.t / 0.09f) * 0.36f
                    val boom = sin(v.t * TAU * 55f) * exp(-v.t / 0.045f) * 0.22f
                    s = crack + rumble + boom
                }
            }
            Kind.HIT -> {
                // The ship taking a hit: a 90 Hz thud under a burst of noise.
                val dur = 0.20f
                if (v.t > dur) { v.active = false } else {
                    val thud = sin(v.t * TAU * 90f) * exp(-v.t / 0.05f)
                    val noise = white() * exp(-v.t / 0.025f)
                    s = (thud * 0.6f + noise * 0.4f) * 0.50f
                }
            }
            Kind.CLEAR -> {
                // Rising three-note arpeggio: C5 E5 G5.
                val dur = 0.55f
                val segDur = dur / 3f
                if (v.t > dur) { v.active = false } else {
                    val ni = (v.t / segDur).toInt().coerceAtMost(2)
                    val nt = v.t - ni * segDur
                    val env = sin(nt / segDur * PI_F)
                    s = osc(v, CLEAR_FREQS[ni]) * env * 0.45f
                }
            }
            Kind.POWERUP -> {
                // Rising sparkle: four quick notes over 0.28 s.
                val dur = 0.28f
                val noteDur = dur / 4f
                if (v.t > dur) { v.active = false } else {
                    val ni = (v.t / noteDur).toInt().coerceAtMost(3)
                    val nt = v.t - ni * noteDur
                    val env = sin(nt / noteDur * PI_F)
                    s = osc(v, POWERUP_FREQS[ni]) * env * 0.38f
                }
            }
            Kind.MARCH -> {
                // A short square-wave bass thump: one step of the invader march.
                val dur = 0.09f
                if (v.t > dur) { v.active = false } else {
                    v.phase += dt * v.freq
                    if (v.phase >= 1f) v.phase -= 1f
                    val sq = if (v.phase < 0.5f) 1f else -1f
                    s = sq * exp(-v.t / 0.035f) * 0.30f
                }
            }
        }
        v.t += dt
        return s
    }

    private fun wrapOsc(phase: Float, freq: Float): Float {
        var p = phase + dt * freq
        if (p >= 1f) p -= 1f
        return p
    }

    private fun genMusic(): Float {
        var s = 0f
        // Slow tremolo LFO at 0.28 Hz
        tremPhase += dt * 0.28f
        if (tremPhase >= 1f) tremPhase -= 1f
        val tremolo = 0.72f + 0.28f * sin(tremPhase * TAU)
        // Pad chord: A2 + C3 + E3 sustained
        for (i in 0 until 3) {
            padPhase[i] = wrapOsc(padPhase[i], PAD_FREQS[i])
            s += sin(padPhase[i] * TAU) * 0.022f * tremolo
        }
        // Bass: A1 (55 Hz) on beats 1 and 3 of a 4-beat bar
        barPhase += dt * TEMPO
        if (barPhase >= 4f) barPhase -= 4f
        val beat = barPhase.toInt()
        if (beat != lastBeat) {
            lastBeat = beat
            if (beat == 0 || beat == 2) bassEnv = 1f
        }
        bassEnv *= 0.9989f
        bassPhase = wrapOsc(bassPhase, 55f)
        s += sin(bassPhase * TAU) * bassEnv * 0.065f
        // Melody arpeggio: A3 C4 E4 A4 cycling on 8th notes
        arpTimer += dt
        if (arpTimer >= ARP_INTERVAL) {
            arpTimer -= ARP_INTERVAL
            arpStep = (arpStep + 1) and 3
            arpEnv = 0.55f
        }
        arpEnv *= 0.9988f
        arpPhase = wrapOsc(arpPhase, ARP_FREQS[arpStep])
        s += sin(arpPhase * TAU) * arpEnv * 0.018f
        return s
    }

    companion object {
        const val VOICES = 8
        private const val TAU = 6.28318530f
        private const val PI_F = 3.14159265f
        private const val TEMPO = 80f / 60f                  // beats/s
        private const val ARP_INTERVAL = 60f / 80f / 2f      // 8th note
        /** The classic descending four-note bass loop the formation marches to: A2 G2 F2 E2. */
        val MARCH_FREQS = floatArrayOf(110.0f, 98.0f, 87.3f, 82.4f)
        private val PAD_FREQS = floatArrayOf(110.0f, 130.8f, 164.8f)          // A2 C3 E3
        private val ARP_FREQS = floatArrayOf(220.0f, 261.6f, 329.6f, 440.0f)  // A3 C4 E4 A4
        private val CLEAR_FREQS = floatArrayOf(523.25f, 659.25f, 783.99f)
        private val POWERUP_FREQS = floatArrayOf(440.0f, 554.4f, 659.3f, 880.0f)
    }
}

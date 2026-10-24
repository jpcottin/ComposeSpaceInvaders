# Compose Space Invaders

[![CI](https://github.com/jpcottin/ComposeSpaceInvaders/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jpcottin/ComposeSpaceInvaders/actions/workflows/ci.yml)

A **Space Invaders-like** game written entirely in **Kotlin** and **Jetpack
Compose**, adaptive from a phone to a foldable, a tablet, Android XR and a TV
with a D-pad — and playable on Display AI Glasses when a pair is connected.

The game is drawn on a Compose `Canvas`; everything around it (the HUD, the
title, the settings sheet, the end screens) is Material 3. The game itself is
a pure-Kotlin module with no Android in it, so its tests and the Auto Play
bench run on any JVM in seconds. Sibling of
[VulkanSpaceInvaders](https://github.com/jpcottin/VulkanSpaceInvaders), the
same game with a hand-written Vulkan renderer, and of
[ComposeBreakout](https://github.com/jpcottin/ComposeBreakout), built the
same way.

> **Status:** complete — ten levels, the mothership, the three bonuses, the
> saucer, the high-score table, the end screens, sounds and haptics, Auto
> Play, and it plays on a phone, a foldable (open, closed or half-opened), a
> tablet, an XR panel, a TV with a D-pad and a pair of AI Glasses.

## Screenshots

Captured on an Android phone emulator with Auto Play driving.

| Title, after a won campaign | Level 1 in play | The mothership |
|---|---|---|
| <img src="docs/screenshots/phone-title.png" width="270" alt="Title screen: COMPOSE SPACE INVADERS over the demo wave, the podium with one score, the level pick between two arrows, Tap to play and the ship"> | <img src="docs/screenshots/phone-play.png" width="270" alt="Level 1 with Auto Play on: the wave thinned, a laser up, a bomb falling, a triple-shot bonus dropping, the rapid-fire bar in the strip"> | <img src="docs/screenshots/phone-mothership.png" width="270" alt="Level 10: the mothership with her health bar, the escort thinned, three lasers of a triple-shot volley, the shield bubble on the ship"> |

| You win, a new high score | Settings |
|---|---|
| <img src="docs/screenshots/phone-win.png" width="270" alt="Win screen: rank #1, NEW HIGH SCORE, the score 44,195 in gold, YOU WIN"> | <img src="docs/screenshots/phone-settings.png" width="270" alt="Settings sheet over the title: Sound, Haptics and Auto Play on, Glasses reading NONE, Reset progress"> |

The other screens, as the screenshot tests render them:

| Foldable half-opened: the control surface under the hinge | TV: overscan-safe HUD, the wave in the middle |
|---|---|
| <img src="docs/screenshots/foldable-tabletop.png" width="420" alt="Tabletop posture: the battlefield in the upper half, SLIDE HERE TO STEER AND FIRE and the ship marker in the lower half"> | <img src="docs/screenshots/tv-playing.png" width="480" alt="TV: the wave centred on a 16:9 screen, the score and the level in large text at the corners"> |

| AI Glasses: black background, no gear | The ten waves |
|---|---|
| <img src="docs/screenshots/glasses-playing.png" width="420" alt="The glasses' projected display: the wave on pure black, the HUD at the corners, no gear"> | <img src="docs/screenshots/waves.png" width="300" alt="The ten levels' formations side by side: three rows at level 1 up to five, the two-row escort at level 10"> |

## The game

- **Goal:** destroy every invader in the wave before it reaches your line.
- **Controls:** hold the strip below the ship: it steers toward your finger
  and fires while pressed. On a TV or a keyboard, left and right steer, OK,
  Enter or Space fire. The gear opens Settings from any screen; a tap on the
  title starts a round, a tap on an end screen returns to the title.
- **Progression:** ten levels. Level 1 fields 3 rows of 8 invaders; a row is
  added every other level (capped at 5 rows of 8). Each level marches faster,
  bombs more often and starts lower.
- **Marching:** the wave sweeps side to side, drops down at each edge, and,
  as in the 1978 original, speeds up as it thins; the last survivor is 3.5
  times faster than a full wave. The four-note march bass follows it.
- **Row tiers:** squid (top) 30 pts, crab (middle) 20 pts, octopus (bottom)
  10 pts, all times the level. Classic two-frame sprite animation synced to
  the march beat.
- **Bombs:** the bottom-most invader of a random column fires; bombs wobble
  as they fall and can be shot down in mid-air. Concurrent bombs and bomb
  speed scale with the level.
- **Mystery saucer:** crosses the top of the screen with a warbling siren,
  100 pts times the level, with the bonus value flashed where it died.
- **Bonuses:** about one kill in twelve drops one: shield (absorbs one hit, lasts
  until hit), rapid fire (the 0.42 s cooldown cut to 0.20 s, 8 s with a timer bar), triple shot
  (a three-laser volley, centre plus ±8°, 8 s). +25 pts times the level on
  pickup.
- **Lives:** 3. Lose one to a bomb or an invader collision (the invader is
  destroyed too); the ship blinks invulnerable after each hit.
- **Invasion:** if the wave reaches the control strip, the invasion succeeds:
  instant game over, whatever the lives left.
- **Mothership at level 10:** a giant 16-hit saucer drifts across the top
  with a health bar, lobbing bombs aimed at you, guarded by a two-row escort.
  Only her death wins the game (+250 pts times the level).
- **Level pick:** the title screen offers any level reached before, with
  arrows to tap or left and right on a D-pad. A Reset progress row in
  Settings forgets the record along with the high scores.
- **Pause:** a round freezes when the screen goes away, when Settings opens,
  on Back, and on P, Escape or a remote's play/pause button. A tap, OK or the
  same key resumes. When the screen itself went away and comes back, the
  autopilot carries on by itself; a pause the player asked for holds until the tap.
- **High scores:** a table of five, persisted; gold, silver and bronze on the
  title, the rank on the end screen when a record falls.
- **Sound:** every effect synthesised in real time: the laser sweep, the
  explosions, the hit, the level-clear arpeggio, the pickup sparkle, the
  saucer siren, the four-note march bass and an ambient A-minor bed. No
  audio files.
- **Haptics:** a light tick for a bonus picked up, a click when the ship
  is hit, with a switch in Settings.
- **Auto Play:** the autopilot dodges incoming bombs (the mothership's angled
  ones too, by predicted impact point), collects a bonus just before it
  lands, lead-aims the mothership, the saucer and the marching columns, and
  fires at whatever is lined up. It picks its column like a seasoned player:
  when the wave would reach the ship before it can be cleared, it clears the
  edge columns first (a narrower wave crosses more screen per step down) and
  switches to the bottom row when that row is about to land. It counts its
  lasers, so a column with a laser already on its way is left for the next
  one. It drives the exact same control path as a finger. Settings turns it
  on; the gear turns green and the HUD shows AUTO while it plays. Every
  change to it is measured with the [Auto Play bench](#the-auto-play-bench).
- **Play on AI Glasses:** with Display Glasses paired, the Glasses row in
  Settings hands the whole game to the glasses' projected display, where the
  touchbar drives it: finger position steers, contact fires. The phone shows
  an "on glasses" banner and waits; the same row brings the game home.

## Modules

| Module | What it is |
|---|---|
| `:engine` | The game: the wave, the ship, the bombs, the saucer, the mothership, the bonuses, the scoring, the autopilot and the synth. Pure Kotlin/JVM, no Android. Its tests and the Auto Play bench run on the host. |
| `:app` | The Compose shell: the canvas that draws the engine, the Material 3 chrome, settings and high scores in DataStore, the audio output, the glasses session. |

## Tech

- **Engine first.** `Game` is a plain mutable object the app steps once per
  frame with the real elapsed time; inside, the simulation runs in fixed
  ticks of 1/120 s, so a 60, 90 or 120 Hz display plays the very same game
  and the bench numbers are the same on every machine. The app reads its
  state to draw and feeds it input in world coordinates (x in
  [-aspect, aspect], y in [-1, 1]); it never sees pixels. Sounds, haptics
  and persistence go through a `GameHost` interface the ViewModel
  implements and the tests and the bench leave empty.
- **One frame counter.** The engine is not Compose state. A `LongState`
  counter is bumped once per step, and everything that shows engine state
  reads it: the canvas redraws without recomposing, and the overlays
  recompose once per step. The canvas places shapes and the overlays place
  text with the same world-to-pixel mapping, so they line up on every
  screen whatever the insets.
- **Sprites as paths.** The three invaders and the saucer are the classic
  pixel-art bitmaps, kept as `Path`s in cell units and scaled at draw time:
  one draw call per invader at any size. The ship is a delta-wing fighter
  that banks into its travel; lasers and bombs are two-layer bolts;
  explosions are expanding rings over spinning debris; the starfield
  scrolls in two parallax layers.
- **Adaptive.** The wave marches the full width of whatever screen it is
  on. A foldable half-opened (tabletop posture, read through Compose's
  MediaQuery API) puts the battlefield above the hinge and a control
  surface below it. A TV gets overscan-safe margins, larger text, and the
  D-pad: left and right steer, OK fires, Up reaches the gear, and the
  focus ring (Compose Styles API) shows where the cursor is. A wide window
  gets Settings as a centred dialog; a phone gets a bottom sheet. A short
  window (the glasses, a phone on its side) gets smaller text.
- **Keys everywhere.** A keyboard or a D-pad steers with the arrows or A
  and D, fires with OK, Enter, Space or the gamepad's A, pauses with P,
  Escape or play/pause, and picks the level on the title with left and
  right.
- **AI Glasses (Android XR projected).** `GlassesSession` observes pairing
  through `androidx.xr.projected` and launches `GlassesActivity` onto the
  projected display, where a second engine runs in touchbar mode with a
  pure black background (transparent on additive lenses) and no gear. The
  phone's engine freezes under a banner meanwhile, and the two share the
  high-score store.
- **Synth in the engine.** Every sound is generated by `Synth`, pure Kotlin
  and tested on the JVM (each effect sounds and ends, the march plays its
  note, the siren fades in and out, nothing clips when it all stacks). The
  app feeds it to a low-latency float `AudioTrack` from its own thread.
- **Persistence.** Settings, high scores and the level record live in
  DataStore; a round in progress survives process death through the saved
  state (level, score and lives come back with a fresh wave).
- **Accessibility.** A polite live region announces the round's events; the
  battlefield's state description says level, invaders left, lives and,
  on the last level, the hits the mothership has left; the
  HUD, the gear, the level pick and the end screen carry descriptions.
- **Languages.** English, French and Spanish, with a generated locale
  config so the app appears in the system's per-app language picker.
- `minSdk 26`, `targetSdk 37`, Jetpack Compose with the Compose BOM, Kotlin
  2.4, AGP 9.

## The Auto Play bench

`./gradlew :engine:run` plays seeded, headless rounds with the autopilot and
reports, per level and per campaign, how often it clears the wave, how it
dies (bomb, collision, invasion), how long a clear takes, lasers per kill
and lasers lost off-screen:

```bash
./gradlew :engine:run --args="--runs 100 --levels --campaigns"              # portrait phone
./gradlew :engine:run --args="--width 2400 --height 1080 --campaigns"       # landscape
./gradlew :engine:run --args="--runs 100 --levels --trace"                  # dump every lost life
./gradlew :engine:run --args="--seed 25 --level 9 --levels --trace"         # replay one run
```

Runs are seeded, so two builds of the same code give the same numbers and a
change to the autopilot is a before/after comparison. CI plays 100 portrait
campaigns, 50 on a landscape screen and 50 on a foldable's inner screen, and
fails when fewer than 95% are won. The current autopilot wins 100% of
campaigns on all three, in 98 s on a portrait phone, 132 s on a landscape
screen and 110 s on the foldable on average.

## Build & run

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.jpcottin.composespaceinvaders/.MainActivity

# An unattended round: Auto Play on, the round started
adb shell am start -n com.jpcottin.composespaceinvaders/.MainActivity --ez autoplay true
```

## Testing

```bash
./gradlew :engine:test                        # the game and the synth, on the JVM
./gradlew :app:testDebugUnitTest              # the app's unit tests
./gradlew :app:validateDebugScreenshotTest    # every screen against its reference image
./gradlew lintDebug
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest   # on one device
```

The engine tests cover the formation (rows per level, the march, edge
reversal and descent, the speed-up as the wave thins, the side margins),
the invasion (even through a shield), collisions, the ship (strip, drag,
keys, clamping, the lean), firing (strip, key, cooldown, the three-laser
cap, despawn), kills and per-tier scoring (at a clamped worst-case frame
too), bombs (cadence, cap, hits, the opening grace, invulnerability and its
end, the hit shake, shoot-down, the death frame, a fatal collision as the
recorded cause, the tally reset by a new round), the saucer, the bonuses
(clock restart on a repeat pickup, expiry, the bonus flash where it was
caught), progression (the clear bonus, level 9 into
10, the end screens, the title return), the mothership (drift, aimed bombs,
the escort rule, the win), session restore, high scores, pause, the level
pick, settings, the glasses touchbar, and the autopilot (firing, dodging,
collecting, laser accounting, lead-aiming, the escort first, a wave cleared,
a campaign won). The screenshot tests render the title, a round in play, the
mothership, pause, level clear, game over and win on a phone, a foldable
(open and half-opened), a tablet, a TV and the glasses, plus the ten waves
and the settings screens. The instrumented tests launch the real activity
and drive it by tap, D-pad and keyboard, pause it from every path, pick a
level, reset the progress and listen as a screen reader.

## CI/CD

GitHub Actions runs on every push and pull request to `main`. The first
three jobs gate the merge; the rest explore newer emulators and tooling and
are marked `continue-on-error`, so a preview package that moves underneath
us reports its findings without blocking a PR. The emulator-runner legs run
with full diagnostics (`-verbose -show-kernel -debug-metrics -metrics-collection`)
on the lavapipe software Vulkan GPU or the emulator's auto choice (plus one
swiftshader comparison leg), and the build and instrumented jobs post a
per-test summary to the run. The Android CLI legs boot through
`android emulator start`, which takes no emulator flags.

| Job | What it does | Gates |
|-----|-------------|-------|
| **Build, lint, tests, screenshots** | Engine JUnit tests, Android lint, app unit tests, Compose preview screenshot validation, the debug and the minified release APK (`apks` artifact) | yes |
| **Auto Play bench** | Seeded campaigns with the autopilot on a portrait phone (100), a landscape screen (50) and a foldable's inner screen (50); fails under a 95% campaign win rate | yes |
| **Instrumented** | The instrumented tests (title, a round by tap, D-pad and keyboard, pause from every path, the level pick, the settings sheet, a screen reader) on emulators: phones at API 33, 34, 35 and 36, a foldable and a TV at API 36, with `-delay-adb` so a device going offline mid-boot cannot fail a leg. The TV leg boots its emulator itself (`.github/scripts/boot-emulator.sh`): that image restarts adbd right after boot and the runner action's first key press used to meet it | yes |
| **Instrumented, experimental** | The same tests on an XR headset (API 34, `google-xr`), on a swiftshader comparison leg, and on the API 37.0, 37.1 and 37.2 page-size-16k images with the stable and canary emulators, lavapipe and auto GPU | no |
| **Android CLI experiment** | The whole emulator flow through the Android CLI (`android sdk install`, `android emulator create/start/stop`) on the canary emulator, API 37.0 and 37.2 | no |
| **Emulator Preview experiment** | The `emulators;latest` preview package launched by its own binary, API 37.0, 37.1 and 37.2 | no |
| **Emulator Preview multi-run** / **Android CLI multi-run** | Four boot cycles with snapshots: the game is launched once with Auto Play (`--ez autoplay true`), each cycle shuts the emulator down gracefully, and the next one checks the app is still running, drawing, and that the score read from the HUD did not go back. The Android CLI run plays the shrunk release APK, the only job that does | no |

The multi-runs use the launch extra `adb shell am start -n
com.jpcottin.composespaceinvaders/.MainActivity --ez autoplay true`, which
turns Auto Play on and starts a round: a way to watch the game play itself
from any script. Changes are listed in the [changelog](CHANGELOG.md).

## License

[Apache License 2.0](LICENSE).

# Changelog

## 1.0

The first release: the game, the autopilot, and the screens it plays on.

### The game
- Ten levels of marching invaders: three rows of eight at level 1, a row
  more every other level up to five, faster and lower each level, and a
  wave that speeds up as it thins. The last survivor marches three and a
  half times faster than a full wave.
- Three tiers of invader: squid (30 points), crab (20) and octopus (10),
  all times the level, with the classic two-frame march animation.
- Bombs from the bottom of a random column, wobbling as they fall, and a
  laser can shoot them down. The mystery saucer crosses the top with its
  siren, 100 points times the level.
- Three bonuses, dropped by one kill in twelve: a shield that absorbs one
  hit, rapid fire and the triple shot, eight seconds each with a bar.
- Three lives; a bomb or a collision costs one, and the wave reaching the
  ship's line is the invasion: game over whatever the lives left.
- Level 10 is the mothership: sixteen hits, aimed bombs, a two-row escort.
  Only her death wins the game.
- A high-score table of five, the podium on the title, the rank on the
  end screen when a record falls.
- Start a round at any level reached before, from the title screen.
- Pause: a round freezes when the screen goes away, when Settings opens,
  on Back, and on P, Escape or a remote's play/pause button.
- Every sound synthesised in real time: the laser sweep, the explosions,
  the hit, the sparkle, the four-note march bass that follows the wave,
  the saucer's warbling siren and an ambient A-minor bed.
- Haptics: a click when the ship is hit, with a switch in Settings.

### Auto Play
- The autopilot from the Vulkan version, line for line: it dodges bombs,
  collects a bonus just before it lands, lead-aims the saucer, the
  mothership and the marching columns, counts the lasers already in the
  air, narrows a wave that would outrun its lasers and defends the bottom
  row when it is about to land. The bench wins 100% of campaigns on a
  portrait phone and on a landscape screen.
- A fixed 120 Hz timestep: the same game at any refresh rate, and the
  same bench numbers on every machine.
- A launcher shortcut and a launch extra start an unattended round.

### Everywhere
- Phones, foldables (open, closed or half-opened, with a control surface
  under the hinge), tablets, an XR panel, a TV with a D-pad, and a pair of
  AI Glasses with the touchbar steering and firing.
- Keys and D-pads: left and right steer, OK, Enter or Space fire, and on
  the title they pick the level.
- English, French and Spanish, listed in the system's per-app language
  picker.
- A screen reader hears the round: level begins and cleared, the ship
  hit with the lives left, game over and win with the score, pause; the
  battlefield describes level, invaders left and lives.
- Settings is a centred dialog on a wide window and a bottom sheet on a
  phone.
- Predictive back, and a portrait default size for free-form windows.
- Target API 37.

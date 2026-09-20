# Milestone 19B combat asset contract

All artwork in `combat.png` is ORIGINAL TEMPORARY PROCEDURAL ART, not final production artwork.
Generated offline by `tools/generate-combat-assets.ps1` using Windows System.Drawing. No downloaded
characters, runtime downloads, new dependencies, audio assets or gameplay changes.

## Atlas and replacement

`combat.atlas` is a standard libGDX TextureAtlas descriptor. Its only page, `combat.png`, is
2048 x 2048 RGBA (16 MiB decoded, before GPU/driver overhead). Linear filtering, no mipmaps.
AssetManager loads the atlas once in renderer create; the manager alone disposes its texture.
SpriteBatch is disposed separately, with idempotent cleanup including initialization failure.
Nothing is loaded in render. The Android backend owns context restoration as before.

| Region | Count | Dimensions per region |
| --- | ---: | --- |
| blue/idle, red/idle | 4 each | 252 x 252 |
| blue/melee, red/melee | 4 each | 252 x 252 |
| blue/projectile, red/projectile | 4 each | 252 x 252 |
| blue/hit, red/hit | 4 each | 252 x 252 |
| blue/ko, red/ko | 4 each | 252 x 252 |
| blue/victory, red/victory | 4 each | 252 x 252 |
| arena/background | 1 | 1000 x 500 |
| arena/platform | 1 | 1000 x 120 |
| fx/shadow | 1 | 128 x 48 |
| fx/glow | 1 | 128 x 128 |
| fx/projectile | 1 | 64 x 64 |
| fx/impact | 1 | 64 x 64 |
| fx/paused | 1 | 64 x 64 |

Keep region names and ascending zero-based atlas `index` values. Any number of animation frames
is supported; timing comes from FighterAction, not image count. Use untrimmed, unrotated square
fighter regions with consistent transparent margins; foot anchor near (126, 240) in a 252-square
image, measured from the top left. Blue faces right; red faces left (do not flip again at runtime).
KO collapse is authored inside its frames, not a runtime whole-sprite rotation. Final KO and
victory frames hold; idle loops. Missing animation strips fall back to that side's required idle
frame. Optional FX regions can be omitted and are safely skipped. Background, platform and both
idle frames are required; invalid required assets give a safe cleared arena while lifecycle and
exit command processing continue. Validate replacement atlases visually before shipping.

Final art can replace the atlas/page without ViewModel, backend or gameplay edits. Production
artists should replace ALL regions, improve in-between poses and tune the original temporary
hit strip (currently a held pose with runtime knockback/tint). No asset is claimed final-quality.

## Presentation and state

A fixed 1000 x 500 FitViewport letterboxes without stretching/cropping. Bases are (260,82) and
(740,82). Local identity mapping remains in the existing Compose adapter. Camera-only shake
never moves Compose names, HP, question or controls.

FighterVisualState has IDLE, ATTACK_MELEE, ATTACK_PROJECTILE, HIT, KO, VICTORY and a PAUSED overlay
that retains the underlying pose/time. Terminal states reject further attacks/hits. All temporary
transforms are derived from the base each frame. Melee lasts 650 ms, projectile 800 ms. Question-ID
parity selects the attack; only damage events authorize hit reactions. Hit presentation waits for
contact when an attack is active. This does not delay authoritative HP, questions or results.
ANSWERING no longer cancels ongoing presentation. Final impact completes before terminal poses.
Fresh result snapshots restore the held result pose without replaying attacks. Resumed snapshots
that advanced beyond a question discard its unfinished effects. Existing ViewModel consumption
continues to suppress replay across rotation/reconnect; renderer high-water marks reject duplicate
or older event IDs within a battle.

Two reusable strike slots, 48 preallocated particles and four flash slots bound the effects.
Projectile position/trail are derived from attack time and disappear at contact; there is no
per-frame projectile allocation. Restart clears fighters, slots, particles, flash slots, event
marks, elapsed time and camera shake. Pause freezes progression (including shake) while GL
continuous rendering and Fragment synchronization remain enabled. Resume deltas cap at 50 ms.

ActivityArenaHost, BattleArenaFragment, BattleExitCoordinator, MainActivity, result composition,
ViewModel, gameplay rules, backend and Gradle configuration are unchanged. No manual draw-frame
calls, sleeps or lifecycle workarounds were introduced.

## Sound and vibration

CombatAudio remains the sole sound owner; libGDX audio remains disabled. Existing HIT feedback
plays punch.wav for both attack variants; existing KO feedback plays ko.wav for the defeat/victory
transition. Melee anticipation, projectile charge/travel and victory pulse intentionally have no
additional sound. Production charge, launch, energy impact and victory recordings remain needed.
Do not add duplicate renderer playback when supplying them: extend the single feedback owner.
Existing setting checks, immediate audio.stop() on Sound Off, and hit-only vibration are unchanged.
Sound remains timed to authoritative feedback; visual contact may follow it by approximately
120 ms for melee or 380 ms for a projectile with the current offline WINDUP timing.

## Manual acceptance checklist (not executed in this environment)

- Offline win and loss; observe both attack variants from both sides; wrong answers/timeouts do not attack.
- Two phones: simultaneous correct answers produce only the authoritative attack and damage.
- Final melee and projectile damage: impact then persistent KO and one victory pulse; Result remains usable.
- Restart resets all effects/poses/camera; complete three consecutive matches.
- Home, toolbar Back, system Back and back gesture during battle/result: no crash, ANR or exit deadlock.
- Rotate during Idle, each Attack and Result: no replay, duplicate Fragment/renderer or cropping.
- Background/foreground during combat: rendering restores and animation delta is bounded.
- Disconnect/reconnect during charge, projectile and result: no stale attacks or duplicate feedback.
- Toggle Sound and Vibration independently: immediate settings respected, no duplicate playback.
- On both target phones verify narrow/tall layouts, smooth continuous idle, impact performance,
  clear silhouettes, and logcat absence of ANR, SIG 9 or renderer synchronization failures.

No automated tests were added or run. Device performance and lifecycle stability require the
above hands-on checks; a successful APK build does not establish them.

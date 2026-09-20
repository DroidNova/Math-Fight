# Milestone 19C artwork, audio and presentation timing

## Quality status

The artwork is original and was created for this repository with the built-in OpenAI image
generation tool, then normalized by `tools/generate-combat-assets.ps1`. It contains no downloaded
characters, samples, logos, text or runtime downloads.

The arena background and platform are suitable for a production art review. The fighter sheets
and VFX are **pre-production**, not final production art. Character identity, colour, facing,
baseline and held KO poses are consistent, but several generated attack/victory energy accents
reached their original 256-pixel source-cell edges. Packaging feathers the outer eight source
pixels and adds an eight-pixel atlas inset so linear filtering does not create hard seams. An
illustrator should redraw those edge accents, clean a few pose-to-pose proportion changes and
provide a final art-direction pass before the project labels the fighter pack production-ready.

The seven audio cues are original deterministic synthesis created by
`tools/generate-combat-audio.ps1`. They are normalized, child-friendly and commercially usable,
but remain pre-production because they have not received a professional sound-design/mastering
pass or been auditioned on the two target phones.

## Atlas contract

`android/app/src/main/assets/arena/combat.png` remains one 2048 x 2048 RGBA atlas. The descriptor
uses the existing logical names and zero-based frame indexes. Each fighter has 24 transparent
252 x 252 regions: four frames each in this order:

1. `idle`
2. `melee`
3. `projectile`
4. `hit`
5. `ko`
6. `victory`

Blue faces right and red faces left. The last KO and victory frames hold indefinitely. Runtime
timings are unchanged: idle 1.2 s, melee 650 ms, projectile 800 ms, hit 300 ms, KO 550 ms and
victory 700 ms. Melee contact remains 300 ms; projectile contact remains 560 ms. The renderer
continues to choose attacks from question-ID parity and derives every transform from a fixed base.

| Atlas region | Count | Dimensions |
| --- | ---: | --- |
| `blue/*`, `red/*` | 24 each | 252 x 252 |
| `arena/background` | 1 | 1000 x 500 |
| `arena/platform` | 1 | 1000 x 120 |
| `fx/projectile`, `fx/glow`, `fx/impact` | 1 each | 128 x 128 |
| `fx/burst`, `fx/spark`, `fx/victory` | 1 each | 128 x 128 |
| `fx/shadow` | 1 | 128 x 48 |
| `fx/paused` | 1 | 64 x 64 |

The 256-pixel packing grid gives every 252-pixel fighter region two pixels of outer placement
margin and four transparent pixels between neighbouring regions. Optional VFX can still be
omitted. Missing animation strips still fall back to that fighter's required idle strip. If the
atlas or required regions fail, the renderer logs a debug message and uses its bounded procedural
arena/robot fallback; gameplay and presentation callbacks remain active.

Source files retained outside the APK:

| Source | Dimensions |
| --- | ---: |
| `docs/art-source/blue-robot-sheet-source.png` | 1024 x 1536 |
| `docs/art-source/red-robot-sheet-source.png` | 1024 x 1536 |
| `docs/art-source/arena-background-source.png` | 1774 x 887 |
| `docs/art-source/arena-platform-source.png` | 1986 x 792 |
| `docs/art-source/combat-effects-source.png` | 1536 x 1024 |

Only `combat.atlas` and `combat.png` are packaged in Android assets; source sheets are not copied
into the APK.

## Presentation-only audio timing

`ArenaCommandBridge` now accepts renderer cues and posts each unique `(session, visual-event ID,
cue)` once to Android's main thread. The callback cannot mutate battle state. `CombatAudio`
remains the only sound owner and libGDX audio stays disabled.

- Melee swing plays when its animation starts.
- Energy charge plays when projectile anticipation starts.
- Projectile launch plays at 200 ms.
- Impact and hit-reaction cues play at the attack's existing contact time.
- Impact vibration occurs in the same main-thread callback.
- KO power-down and victory cues play only when their terminal poses begin.
- If no renderer is attached, existing authoritative HIT/KO feedback immediately plays equivalent
  sounds and the existing impact haptic.

Sound and vibration settings are read when the callback arrives. Sound Off stops all active streams
immediately. Activity pause, online pause, Restart and Home continue to stop active audio. Renderer
event high-water marks, bridge event deduplication and the ViewModel's rotation/reconnect guards
remain in place.

## Audio files

All files are 22,050 Hz, mono, 16-bit PCM WAV and peak-normalized to 76 percent full scale.

| File | Duration | Purpose |
| --- | ---: | --- |
| `melee_swing.wav` | 220 ms | melee anticipation/swing |
| `energy_charge.wav` | 340 ms | projectile charge |
| `projectile_launch.wav` | 240 ms | projectile release |
| `impact.wav` | 190 ms | visual contact |
| `hit_reaction.wav` | 170 ms | robot reaction layer |
| `ko_power_down.wav` | 580 ms | nonviolent power-down |
| `victory_stinger.wav` | 720 ms | short victory phrase |

## Built-in image-generation prompt set

The blue and red character prompts requested exactly 24 full-body frames in a 4-column by 6-row
transparent sheet. Rows were idle, melee, projectile, hit, KO and victory. Both prompts required
stable scale, proportions, baseline and pivot, full-body padding, thick dark outlines, cel shading,
no weapons/gore/text/logos/watermarks, and inward facing. Blue specified round blue/cyan armour,
cyan circular core and friendly digital eyes. Red specified sharper red/orange armour, orange
hexagonal core and competitive amber digital eyes.

The arena prompt requested a wide orthographic dark-blue robot arena with cyan left lighting,
orange right lighting, central violet energy structure, layered audience/machinery depth and clear
fighter contrast. A targeted edit removed generated banners, screen imagery and robot silhouettes,
leaving no symbols or recognizable characters. The platform prompt requested an isolated
transparent 25:3 dark-navy deck with cyan/orange edge accents. The VFX prompt requested a precise
3-column by 2-row transparent sheet containing projectile, charge ring, impact, burst, electrical
spark and victory pulse with white cores suitable for runtime cyan/orange tinting.

## Manual acceptance checklist

Not executed in this environment:

- Inspect idle, melee, projectile, hit, held KO and held victory frames for both sides on-device.
- Confirm swing/charge/launch timing and impact sound plus haptic exactly at visual contact.
- Confirm final contact completes before KO/victory and does not duplicate audio.
- Verify Sound Off and Vibration Off immediately, including while an attack is in flight.
- Complete offline win/loss and online win/loss on both phones, including simultaneous answers.
- Complete three consecutive matches and verify Restart clears poses, VFX, callbacks and audio.
- Exercise Home, toolbar/system Back, gesture Back, rotation during each state, background/foreground,
  disconnect/reconnect and result navigation without replay, duplicate renderer, ANR or deadlock.
- Check stable frame rate, memory use, atlas edges and audio balance on both target phones.

No automated tests are added or run for this milestone.

# Development status

## Milestone 3

Combat presentation now includes mirrored leaning strikes, defender recoil, a 100 ms flash, a 140 ms contact burst, canvas-only shake (160 ms, at most 3 dp), and a fallen KO pose. The existing 180/320/600 ms gameplay phases, damage, questions, and bot timing are unchanged. Compose animations follow system animation scaling.

The Activity owns a small SoundPool controller using USAGE_GAME. Feedback is emitted only at authoritative IMPACT/KO transitions, collected only while RESUMED, and validated against battle/question/phase tokens. ViewModel consumption tokens prevent repeated audio/haptics and repeated impact effects across recreation or resume. Unready sounds and missed events are skipped. Pause, Back, Restart, and disabling sound stop audio; destruction releases it. Hit haptics use the View's CONTEXT_CLICK without overriding settings or requesting permissions.

Sound and vibration default on. Their separate in-memory ViewModel preferences survive restart, Home, and Activity recreation; process death may reset them.

`punch.wav` (100 ms) and `ko.wav` (280 ms) are original sounds generated locally for this project with standard-library math and binary WAV writing. Both are 22,050 Hz mono 16-bit PCM with short fade envelopes and amplitudes below clipping. The punch is a damped low-frequency tone; KO is a descending tone. Only final assets are bundled in `res/raw`; no runtime generator, downloads, or dependencies were added.

Device verification of this milestone is pending, including animation quality, phone-size layout, audio, haptic support/settings, lifecycle transitions, and airplane-mode play. No automated tests were added or run.

## Milestone 4

The debug build adds a local Connection check with an editable server URL, connect/disconnect controls, bounded reconnects, stale-callback protection, and a three-second acknowledgement timeout. The socket is owned by the activity-scoped ViewModel and is disconnected on backgrounding; it reconnects on foreground only when the user left the connection requested. Offline battle behavior is unchanged.

Run the server from `server/` with `npm run start:dev`. Use `ipconfig` to find the laptop Wi-Fi IPv4 and enter `http://<laptop-ip>:3000` on a phone using the same Wi-Fi; use `http://10.0.2.2:3000` from an emulator. A private-network Windows Firewall rule may be needed for Node. The server also exposes `GET /health`.

Build verification: `android/` -> `.\gradlew.bat :app:assembleDebug` succeeded on Windows for Milestone 3. The initial sandbox cache-access blocker and initial misplaced-resource compilation error were resolved; package/build configuration was preserved.

Manual checklist for Milestone 3:

1. Player and bot attacks animate in the correct direction.
2. Each hit still deals exactly 20 damage once.
3. Flash, recoil, burst, and subtle shake align with impact.
4. Equation, keypad, and HP labels remain steady.
5. Punch feedback happens once per hit; KO sound happens once.
6. Sound/vibration controls work and survive match restart.
7. Disabling sound stops current audio; re-enabling causes no old playback.
8. Pause/resume and Activity recreation do not replay consumed feedback.
9. Back/restart leaves no old audio, vibration, shake, or fighter transforms.
10. Victory, defeat, bot timing, and airplane-mode play still work.

## Milestone 2

The offline bot battle is implemented. Both fighters compete for each generated question: a correct player answer or the bot's scheduled correct answer claims one attack. Each hit deals 20 damage, and the match ends with a player victory or defeat after either fighter reaches zero HP.

The activity-scoped ViewModel owns combat timing and one bot job. Each new question samples one 4–7 second bot delay. Wrong answers and input edits do not restart it; pause saves its remaining monotonic-clock duration synchronously, and resume continues that duration. Phase and question keys reject stale attacks after Back or Restart.

Build from `android/` with:

```bash
./gradlew :app:assembleDebug
```

Build result: blocked before compilation because the environment proxy returned HTTP 403 while the Gradle wrapper attempted to download Gradle 9.5.0. Run the command above in a network-enabled environment to complete compilation verification.

## Manual verification

- [ ] Player-first and bot-first answers damage only the correct defender.
- [ ] Wrong answers do not restart the bot delay.
- [ ] Near-simultaneous answers produce exactly one attack.
- [ ] Five won questions produce victory; five bot wins produce defeat.
- [ ] Mixed attacks never produce negative HP.
- [ ] Restart and Back cancel obsolete bot actions.
- [ ] Background/resume preserves bot time and combat state without duplicate damage.
- [ ] The complete loop works in airplane mode.

Phone verification is pending.

## Milestone 5

The local server keeps ephemeral two-player rooms with six-character uppercase codes. The debug Connection check can create, join, leave, and display host/guest lobby state; room membership is cleared on disconnect, Back, URL changes, and backgrounding. Start Battle remains the offline bot mode.

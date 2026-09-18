# Development status

## Milestone 1

The first playable offline battle is implemented. The launch screen starts a match with generated addition, subtraction, and multiplication questions. Correct answers drive a timed attack, 20 damage, the next question, opponent KO after five hits, victory, and restart. The opponent remains passive and player HP remains 100.

Battle state and phase timing are owned by one activity-scoped ViewModel. Combat phases pause while the activity is backgrounded, and stale phase callbacks cannot affect a restarted or abandoned match. Fighters and combat feedback use Compose Canvas and built-in animation only.

Build from `android/` with:

```bash
./gradlew :app:assembleDebug
```

Build result: blocked before compilation because the environment proxy returned HTTP 403 while the Gradle wrapper attempted to download Gradle 9.5.0. Run the command above in a network-enabled environment to complete compilation verification.

## Manual verification

- [ ] Launch → Start Battle shows two fighters and both HP values at 100.
- [ ] Addition, subtraction, and multiplication appear with valid ranges.
- [ ] Keypad editing, submission, and wrong-answer feedback work.
- [ ] Every correct answer produces one attack and exactly 20 damage.
- [ ] The fifth correct answer leads through KO to victory.
- [ ] Restart and system Back reset or abandon the match safely.
- [ ] Background/resume does not duplicate damage or leave input stuck.
- [ ] The complete loop works in airplane mode.

Phone verification is pending.

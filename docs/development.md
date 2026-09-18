# Development status

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

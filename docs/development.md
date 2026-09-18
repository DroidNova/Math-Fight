# Development status

## Milestone 0

The Android foundation is implemented as a single application module. The app launches directly to an inset-safe, portrait Math Fight title screen with the subtitle “Solve. Strike. Win.” No gameplay or network functionality is included.

Build from `android/` with:

```bash
./gradlew :app:assembleDebug
```

Build result: blocked before compilation because the environment's proxy returned HTTP 403 while the Gradle wrapper tried to download Gradle 9.5.0. This is an environment limitation, not a reported source-code error. Run the command above in a network-enabled development environment to complete verification.

## Manual verification

- [ ] Gradle sync succeeds.
- [ ] App installs and launches.
- [ ] “Math Fight” and “Solve. Strike. Win.” appear correctly.
- [ ] Content does not overlap system bars.
- [ ] Portrait behavior works on the test phone.
- [ ] Background/resume and close/reopen do not crash.
- [ ] The installed app opens in airplane mode.

Device verification has not yet been performed.

## Next milestone

Milestone 1 is not started: offline answer → attack → damage → next question → KO → restart.

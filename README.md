# Math Fight

Math Fight is an offline Android game prototype in which solving math questions triggers fighter attacks. The current **Milestone 3** build adds combat animations, offline punch/KO audio, hit haptics, and sound/vibration controls to the complete offline bot battle.

## Local setup

Open the `android/` directory in Android Studio. The project currently requires:

- Android SDK 37 (with the project supporting Android 8.0/API 26 and newer)
- JDK 25 for the Gradle daemon
- The checked-in Gradle wrapper; no global Gradle installation is needed

Set the Android SDK location through Android Studio or an untracked `android/local.properties` file. Configure Android Studio's Gradle JDK as JDK 25; the project also includes daemon toolchain metadata that can provision it when supported.

## Build a debug APK

Run from `android/`:

```bash
# Unix
./gradlew :app:assembleDebug
```

```powershell
# Windows
.\gradlew.bat :app:assembleDebug
```

## Install and run

In Android Studio, allow Gradle sync to complete, select an emulator or connected Android phone, and choose **Run > Run 'app'**. The playable battle does not require a network connection at runtime.

## Milestone 4 local server

From `server/`, run `npm run start:dev`. The server listens on port 3000 by default (`PORT` may override it). For a phone on the same Wi-Fi, run `ipconfig` on the laptop, find its Wi-Fi IPv4 address, and enter `http://<laptop-ip>:3000` in the debug-only Connection check on the launch screen. An emulator uses `http://10.0.2.2:3000`. If needed, allow Node through Windows Firewall on private networks; do not change firewall settings automatically.

The debug Connection check also supports creating or joining one two-player local room. It only provides lobby presence in this milestone; Start Battle remains the offline bot mode.

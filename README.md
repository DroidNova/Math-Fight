# Math Fight

Math Fight is an offline Android game prototype in which solving math questions triggers fighter attacks. The current **Milestone 2** build supports a complete battle against an offline bot through KO and restart.

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

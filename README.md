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

## Milestone 12 local database

Start PostgreSQL and the server with the existing data volume:

```powershell
docker compose up -d postgres
cd server
$env:DB_PORT = "5433"
npm run migration:run
npm run start:dev
```

Copy `server/.env.example` to `server/.env` only when overriding the local defaults. The server stores anonymous account tokens as hashes and keeps player statistics in PostgreSQL; offline battles continue without the backend.

The debug Connection check also supports creating or joining one two-player local room. It only provides lobby presence in this milestone; Start Battle remains the offline bot mode.

When both room members tap Ready, the server starts the first online question. Answers, HP, attacks, and results are server-authoritative; Start Battle remains the offline bot mode.

## Milestone 8 reconnection

An active online match pauses when the server detects a disconnected phone (heartbeat: 3-second interval, 5-second timeout). The disconnected player has 15 seconds from detection to return. Temporary private session credentials stay only in the Android ViewModel and server memory; process death and server restart recovery are not supported. Both phones must receive a fresh question before answering resumes, with committed HP preserved. Expiry awards the connected opponent a forfeit victory; if neither returns, the room is removed. Back, Leave Room, and Disconnect intentionally end participation immediately when connected.

Manual checks: background/return within 15 seconds; old answers and delayed replies are ignored; disconnect during an attack preserves damage exactly once; expiry forfeits; both phones disconnect; Back/Leave skips grace; repeated retries keep one connection; rematches and offline Start Battle still work. Rebuild/install the APK on both phones and restart the server together for this protocol update.

## Milestone 9 player profile

First launch asks for a player name; Home → Profile changes it later. A local UUID and name are saved in Preferences DataStore (`androidx.datastore:datastore-preferences:1.2.1`). Names use 3–16 Unicode letter/number characters, ordinary spaces, or underscore; outside spaces are trimmed and repeated spaces collapsed. Both Android and the server validate them. Duplicate names are allowed. The UUID is metadata, never authentication: live reconnection still requires the private resume token.

The existing socket synchronizes the saved profile after connection/resume. Lobby names update on a rename; battle/result names are fixed for that match. Verify fresh setup, invalid names, persistence after app restart, renaming in a connected lobby, duplicate names on two phones, correct names after reconnect/rematch, and the saved name versus Bot offline. Install the updated APK on both phones and restart the server.

## Milestone 14 XP and player levels

Ranked normal wins/losses award 100/40 XP; forfeit wins/losses award 70/0. Private, offline, and abandoned matches award none. Level progress is derived on the server from total XP (200 XP times the current level to advance). Profile, history, and results display server values; result progress/level-up animation is consumed once per match in the ViewModel. XP, stats, and Elo commit together, and reconnect snapshots retain each player's own progression.

Run the data-preserving migration before restarting the server and installing the updated APK on both phones:

```powershell
cd server
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "5433"
npm run migration:run
npm run start:dev
```

Verify 100/40 and 70/0 rewards, zero XP in private/offline play, Level 2 at 200 XP, persistence across restarts, matching Profile/history/results, and no duplicate award or animation after reconnect/recreation. XP columns are non-negative PostgreSQL integers; all writes roll back on overflow or database failure, and unavailable XP is never invented by Android.

## Milestone 11 difficulty modes

Easy, Standard, and Expert are shared by offline questions, private rooms, and random matchmaking. Standard is the default and the last selection is saved with the profile preferences. Expert adds exact whole-number division; the server remains authoritative for online answers. Private-room hosts choose the mode and guests inherit it; matchmaking queues are separated by mode. Install the updated APK on both phones and restart the server when changing this protocol.
## Milestone 12 local database

Start PostgreSQL and the server with the existing data volume:

```powershell
docker compose up -d postgres
cd server
npm run migration:run
npm run start:dev
```

Copy `server/.env.example` to `server/.env` only when overriding the local defaults. The server stores anonymous account tokens as hashes and keeps player statistics in PostgreSQL; offline battles continue without the backend.

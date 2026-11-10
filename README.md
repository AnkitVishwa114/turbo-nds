# Turbo NDS – NDS Emulator for DS

Android app for playing Nintendo DS games you own. Package `com.appkraft.turbonds` · by AppKraft Studio ·
website [turbonds.site](https://turbonds.site)

Turbo NDS is a pure emulator. **No games, BIOS or firmware are included.** You add your own `.nds`, `.dsi`,
`.srl`, `.ids`, `.zip` or `.7z` files with Android's file picker.

## Features

- Library with each game's real icon and title, search, favourites, recent games and play time
- Portrait and landscape layouts, full-screen landscape, swap screens, one-screen and big + small modes
- Multi-touch buttons, gamepad support, turbo A/B, fast-forward, screen filters, vibration
- Auto-save every 30 seconds and on pause, 6 save slots, quick save/load, older in-game saves kept for restore
- Save backup and restore as a .zip, Action Replay cheats
- Light and dark themes

## Built with

- [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) 0.13.2 (GPL-3.0-or-later), in `app/libs/`
- [melonDS DS](https://github.com/JesseTG/melonds-ds) libretro core (GPL-3.0-or-later), arm64 build in
  `app/src/main/jniLibs/`
- Kotlin, Jetpack Compose, Apache Commons Compress, XZ for Java, Google Play Core libraries

## Build

Requirements: JDK 17–20 and the Android SDK (compile SDK 36).

```
./gradlew :app:assembleDebug
```

Release builds are signed with the publisher's private upload key, which is not part of this repository.
To make your own release build, create your own key and sign `app-release-unsigned.apk`.

## Licence

Copyright AppKraft Studio. Turbo NDS is free software under the GNU General Public License version 3 or later;
see [LICENSE](LICENSE). Third-party notices are in `app/src/main/assets/licenses/`.

"Nintendo DS" is a trademark of Nintendo. Turbo NDS is not affiliated with or endorsed by Nintendo.

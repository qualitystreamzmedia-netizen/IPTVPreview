# IPTV Preview

Android IPTV application built with Kotlin, Jetpack Compose, and LibVLC.

## Features

- Home with playlists, recently played channels, favorites, and source management.
- M3U and Xtream live sources, search, favorites, category visibility, original source order, and per-playlist custom category ordering.
- Four-pane browser with independently resizable browser/player and category/channel splits.
- VLC playback, buffering/error states, retry, volume, audio/subtitle selection, fullscreen, and manual Picture-in-Picture on supported devices.
- XMLTV guide parsing, including gzip and timezone offsets, with current/upcoming programme information.
- Persistent settings, 20-channel playback history, parental PIN and category locks, and text scaling from 70% to 130%.
- Labeled navigation rail, pane focus tracking, D-pad navigation, and media keys.

## Latest navigation changes

`ChannelListPanel` receives a shared `LazyListState` and `FocusRequester`; focus is requested from `LaunchedEffect`, never during composition. The browser selects the focused channel through the PIN gate.

`ObserveRemoteKeys` supports bounded Up/Down scrolling, held-key repeats, and empty-list guards. It is available for exclusive event-stream callers. The Browser uses its existing native/pane-specific handling; the observational key stream is not also fed into that helper, which would process keys twice.

## Build

Requires JDK 17 or newer and Android SDK platform 34. Set `ANDROID_HOME` or create a local, untracked `local.properties` with `sdk.dir`.

Windows:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

macOS/Linux:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. Minimum Android version: 6.0 (API 23). Gradle wrapper: 8.7. Application ID: `com.example.iptvpreview`.

## Downloads and verification

See GitHub Releases for the current debug APK and source archive. Build, JVM tests, and lint passed. The APK was installed and launched in MEmu.

Live streaming, PiP, and the latest remote-focus interactions have not been comprehensively verified interactively. MEmu UI inspection returned no accessible root. The APK is a debug build, not a store-signed production release.

Coil 2.6.0 is included but channel logos still use placeholders. The guide shows current/upcoming programmes rather than a complete schedule. Movies/series libraries and automatic Home-to-PiP are not implemented. Room annotations are present, while persistence uses DataStore.

Supply your own source in Settings; the requested public M3U example is https://iptv-org.github.io/iptv/index.m3u. Source availability depends on the provider.

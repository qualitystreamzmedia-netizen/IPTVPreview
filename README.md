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

Coil 2.6.0 is included but channel logos still use placeholders. The guide shows current/upcoming programmes rather than a complete schedule. Movies/series libraries and automatic Home-to-PiP are not implemented. Room provides a channel entity, playlist-ordered queries, name/group search capped at 500 results, replacement inserts, and an exported version 1 schema. Room is the active channel cache; DataStore retains playlist configuration and user settings.

Supply your own source in Settings; the requested public M3U example is https://iptv-org.github.io/iptv/index.m3u. Source availability depends on the provider.


The repository exposes getFilteredChannels(playlistId, query) for reactive Room reads. Search respects the playlist scope before the 500-result cap; blank queries return the selected playlist or all rows. Room stores channel logos and EPG IDs; hidden flags remain in the existing repository flow. The importer persists channel rows in Room and the browser observes database-backed flows.

## Background M3U import

Call `ImportPlaylistWorker.enqueue(context, url, playlistId)` to schedule a network-constrained unique import and observe the returned WorkRequest UUID with WorkManager. Supply the existing playlist ID when updating a source. Progress data contains `progress` (-1 for unknown download size) and `rows`. Download progresses to 70%; parsing reports batch counts; 100% means committed.

The worker streams a maximum 100 MB download to a temporary file, then parses line by line and inserts batches of 1,000 rows in one outer Room transaction. Invalid/empty input and cancellation roll back the replacement. Room favorites survive refresh; other playlists remain untouched. Transient HTTP/network failures retry at most three attempts with exponential backoff. Temporary files are removed on completion. A cancelable foreground notification and dataSync service declaration are included.

This entry point imports M3U into the basic Room channel table. Playlist Settings imports write to Room directly; the separate WorkManager enqueue API remains available but is not the Settings scheduler. Xtream imports and notification permission UX are not migrated here. Android 13+ users may need to allow notifications in system settings to see the foreground notification in the drawer.

Channel navigation now uses ViewModel-owned observable focus state. Category focus reports the actual focused row (zero is All Channels), and indices are clamped as lists shrink. Left/Right retain native pane navigation; the event stream is not processed twice.


Room schema v2 adds epg_programs and idx_epg_channel_time(channel_id, start_time), with a non-destructive v1-to-v2 migration. EPG timestamps are 64-bit milliseconds. The current XMLTV repository still uses its existing in-memory guide; writing guide data to Room is not yet connected.

EpgDao.getCurrentProgram(channelId, now) returns EpgProgramEntity with its database ID. It uses an exclusive end time, prefers the latest-starting overlap, breaks ties by ID, and returns null when no valid current programme exists.

VLC now retries playback errors up to three times with 1/2/4-second delays. Playing resets the counter; pause, stop, release and channel changes cancel pending retries. URL loading is owned by the controller, and generation tokens discard queued events from older attempts. Retry policy unit tests pass; live-network recovery is not yet verified end-to-end.

The dashboard toolbar now uses IPTV Pro branding, a rounded playlist selector, a collapsible search field and a Dashboard action. Closing search clears the repository query. Search bypasses the category filter while retaining playlist and Favorites/Recent scope. Existing playback, guide, parental controls and resizable panes remain connected; placeholder VLC and Recent implementations from the mockup are not used.

Room compilation uses KSP 1.9.22-1.0.17 with Kotlin 1.9.22. Runtime dependencies remain Room 2.6.1, WorkManager 2.9.0 and coroutines-android 1.8.0. Schema export remains enabled.

Schema v3 adds channel logoUrl/epgId and the playlist/group index, retaining the source-order index. Worker imports and domain mapping preserve these fields. EPG entities expose dbId and non-null required values while retaining existing SQL column names. Migration preserves legacy EPG IDs and rows, replacing null required values with empty strings/zero; empty titles are excluded from current-program lookup. data.local entity aliases are available for the supplied imports.


Repository imports atomically replace one playlist in 1,000-row batches using existing M3U/Xtream parsing and stable channel IDs. SQL filtering combines playlist, search and favorites; category summaries retain source order. Disabled sources keep their cache, deletion removes it, and failed refreshes retain cached channels. getFilteredChannels returns all matching rows (the legacy searchChannels helper still has its 500-result cap).

PlayerViewModel exposes Pane/currentPane, pane-aware Up/Down, and clamped switchPane. The activity shares its navigation controller. Database channel queries switch reactively with playlist/search using flatMapLatest, while existing visibility/custom order and category counts remain applied.

Category/channel remote Up/Down and OK now invoke ViewModel focus actions and selection events. Bounds use the actual visible rows, with All Channels at category index zero. Selections are collected only while STARTED and respect dialogs/PIN checks. Native pane-level dispatch remains the single key consumer; the global observational key flow is not subscribed a second time. Rail/player controls and Back retain native/existing behavior.

Schema v4 adds separate channel playlistId and group indexes, preserving existing composite indexes and all entity fields. The migration creates indexes without changing stored rows.


DAO updates: getAllChannels sorts by group COLLATE NOCASE then source index; getFavorites sorts by name. deleteByPlaylist is available. EpgDao supports replacement inserts and cleanup of rows ending strictly before the supplied cutoff; current lookup retains exclusive end times and deterministic overlap handling. data.local DAO aliases are provided.

# Zaghlol Player for Android TV

Zaghlol Player v1.0.0 for Android TV — the same UI and functions as the
Windows player v2.0.3, rebuilt natively for the television: Kotlin +
Jetpack Compose for TV, D-pad remote navigation, 10-foot UI, HLS playback
via ExoPlayer (Media3).

## Features (parity with Windows v2.0.3)

- **Login**: M3U link, M3U file (Storage Access Framework), Xtream Codes
  (server + username + password), or free demo channels. Saved logins stay
  on the device with auto-login; logout / wipe data in Settings.
- **Live TV**: top bar (Z logo, tabs, search), categories sidebar with real
  counts, channels list with red LIVE badges, video info bar with the real
  now/next programme from the EPG, bottom now-playing bar with progress
  and time, focus mode (hides the side panels).
- **Movies / Series**: header with count badge, category chips, poster
  grids with gradient title tiles as fallback when a cover is missing,
  resume support, season/episode picker for series.
- **EPG**: 7-day programme guide; Xtream catch-up (play a programme from
  the start); XMLTV URL in Settings for M3U playlists.
- **Favorites, recents**, connection test on the login forms with real
  Arabic error messages (no silent hangs, no generic errors).
- **Playback**: HLS via ExoPlayer. M3U VOD entries (.mp4/.mkv/.avi/.mov/
  .m4v/.webm/.flv) are classified into Movies; S01E02 / Season N /
  Episode N / الموسم N / الحلقة N markers (or a series group) into Series.
  Xtream live .ts URLs are rewritten to .m3u8.

The player contains no channels — use your own legal IPTV
playlist/subscription. Demo mode plays 5 free public streams.

## Build

GitHub Actions builds the debug APK on every push to `main` and publishes
a release (`v1.0.0`) with the APK attached.

Local build (needs Android SDK 34 + JDK 17):

```sh
gradle assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

## Install on a Sharp Android TV (sideload)

1. On the TV: **Settings → Device Preferences → Security & restrictions →
   Unknown sources** → allow the app you will install from (e.g. the
   Downloader app, or your file manager).
2. Install **Downloader** (by AFTVnews) from the Play Store on the TV.
3. In Downloader, enter the release APK link
   (`https://github.com/Ghidan1/zaghlol-player-android-tv/releases/download/v1.0.0/app-debug.apk`)
   and install it. (Alternative: copy the APK to a USB stick, or send it
   with **Send Files to TV** from your phone, then open it with a file
   manager on the TV.)
4. The app appears on the TV home screen as **Zaghlol Player**. Open it,
   log in with your M3U link or Xtream Codes (or try the demo channels),
   and navigate with the remote: arrows move, **OK** selects, **Back**
   goes back.

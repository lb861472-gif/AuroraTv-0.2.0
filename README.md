# AuroraTV 0.2.0

A cinematic Android TV / Google TV launcher: real-time mountain scenery (your three photos, cross-fading morning / day /
night, or drawn mountains), a big clock the foreground ridge overlaps, a "Most used" shelf of 8 apps, a scrolling
All-apps grid, weather, custom Liquid Glass settings and a Liquid Glass volume panel. Built for a TCL 65Q51K (Google TV).

**Status:** 0.1.0 was installed and photographed on the TV. 0.2.0 has NOT been built or tested yet: the build must go
green on GitHub Actions first, and everything marked "needs the TV" below is unverified until you try it.

## What your 0.1.0 photos showed (and what changed because of it)
- Home screen, clock overlap, dock and date all worked. The ridge covered a lot of the digits, so the clock now defaults to "Higher".
- Pressing **Settings** on the remote opened Google TV's own quick-settings panel over AuroraTV. The firmware handles that key before apps,
  so AuroraTV cannot take it over (no accessibility tricks are used). Replacement: a **Settings chip** (top right), the Menu button if your
  remote has one, and a **Remote key test** screen (Settings > Audio & volume or Navigation) to see exactly which keys reach apps.
- Volume keys showed the TV's own volume bar. 0.2.0 adds AuroraTV's glass volume panel that changes the real STREAM_MUSIC volume; if the TV
  won't let apps change it (fixed volume / firmware handling), AuroraTV steps aside and the TV's own bar is used. Settings says which happened.

## Making AuroraTV your Home app (one time)
Pressing Home never showed a chooser because since Android 10 the default Home app is a "role" that the current holder (TCL launcher) keeps.
On first start AuroraTV shows a glass prompt: **Set as Home** asks Android for the Home role. Also at Settings > Navigation & Home app.
If the TV offers no screen for it, AuroraTV shows this optional one-time PC step (untested on your TV; keeps the TCL launcher installed):
```
adb connect TV_IP:5555
adb shell cmd package set-home-activity com.auroratv.launcher/.HomeAlias
```
The GitHub build prints and checks the built APK's manifest (HOME + LEANBACK_LAUNCHER + HomeAlias) in the **Inspect the built APK's manifest** step.

## Features
- **Home:** clock, date, weather (Bellbrook, Ohio by default), scenery + Settings chip; shelf with **Most used** (8 tiles) and **All apps** (grid).
  Most used = pinned apps first (hold OK on a tile, or Settings > Home screen), then apps you open most from AuroraTV (counted on the TV only),
  then alphabetical. Android needs a special "usage access" permission to read other apps' usage; AuroraTV deliberately doesn't ask.
- **Wide banner tiles** (or square icons), focus ring + glow, smooth row scrolling, headers that can't trap focus.
- **Real-time theme:** sunrise / day / sunset / twilight palettes blend with the clock; photos cross-fade morning > day > night.
- **Liquid Glass:** the scenery is captured once into a tiny bitmap and stretched behind each glass panel (cheap frosted look), plus tint,
  sheen and bright edge. Strength and "Simple tint" fallback in Settings > Appearance. Animation intensity: Full / Reduced / Off.
- **Custom settings** (Appearance, Home screen, Clock & scenery, Ambient, Audio & volume, Navigation & Home app, About, Recovery) with custom
  glass rows, switches and sliders. Back returns to the previous page and focus.
- **Ambient mode:** apps fade away after your chosen time; first key press only wakes it.
- **Locked-in feel (no lock-out):** Back does nothing on the home screen when AuroraTV is Home; Home while on the home screen jumps to the first
  tile; returning from Settings/apps restores the same tile. No kiosk mode, no accessibility service, no root.
- **Permissions:** INTERNET only (weather). Weather Off = no network use.

## Build with GitHub Actions
1. Unzip, upload the **contents** to your repo (Add file > Upload files). Keep your existing `.github/workflows/build-apk.yml`
   or replace it with the one in this zip (it now also checks the manifest and names the APK).
2. Actions tab > run **Build AuroraTV debug APK** (starts by itself on push). Green tick = built.
3. Download artifact **AuroraTV-0.2.0-debug-apk** (zip) > `AuroraTV-0.2.0-debug.apk` + `manifest-dump.txt`.
Builds are signed with the public debug key in the repo (`app/aurora-debug.keystore`), so installing over 0.1.0 works.

## Install
USB stick + a file manager app on the TV, or `adb install -r AuroraTV-0.2.0-debug.apk` from your PC. Open AuroraTV, accept the Home prompt.

## Needs the TV to verify (not verified yet)
Home-role request screen on Google TV; whether AuroraTV receives volume keys and can change volume; photo layer look and clock overlap at
your clock height; remote focus feel in the grid; weather fetch; performance of the frosted glass; app banners for each app.

## Recovery
See `RECOVERY.md` (also in Settings > Recovery).

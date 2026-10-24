# LRT Greitas (`lt.lrt.lean`) — Ultra-Lean Native Android TV App for LRT.lt

A blazing-fast, **69 KB**, zero-dependency native Android TV application for **LRT.lt** (`Lietuvos nacionalinis radijas ir televizija`), built directly against the Android SDK without WebViews, React Native, or bloated frameworks.

---

## Features

- **100% Native Android TV UI (< 15 MB RAM, 69 KB APK):**
  - Instant startup (`< 150 ms`) and 60 fps D-pad navigation even on budget MediaTek Android TVs.
  - Parallel background feed prefetching and in-memory LRU thumbnail cache.
- **📅 TV programa (Program Guide):**
  - What's on now, what's next and what already aired on every LRT TV and radio channel — 4 days back, 6 days ahead (`/api/json/tvprog`).
  - The current show is marked **DABAR** with a live progress bar; the list opens on it with the previous show just above.
  - **OK** on the current show plays the channel live; **OK** on an aired show (or a rerun) plays its LRT mediateka/radioteka recording (`▶ ĮRAŠAS`), with resume memory shared with the rest of the app.
  - Channel and day tabs on top. In the list: **ATGAL** jumps to the tabs, **CH+/CH−** switch channel, **⏪/⏩** switch day, **◀** back to the menu.
  - Long-press **OK** (or **MENU**) on a live channel opens its program; the remote's **GUIDE** key opens it from anywhere. The live hero also shows what's next (`Toliau`).
- **❤️ Mėgstamos laidos (Favourite Shows):**
  - Hold **OK** (or **MENU**) on a show in the TV programa guide, or inside a show's episode list, to star it (★). The section appears in the menu once something is starred.
  - Lists each favourite once, at its next airing, grouped by day with time and channel; the hero adds the later airings. Shows not in the guide's next 6 days go under "Kita serija dar neskelbiama".
  - **OK** plays it live if it's on now, otherwise the latest recorded episode; holding **OK** removes it.
  - Matching ignores suffixes like "(subtitruota)", and starring from an episode list also picks up the on-air name when it differs (mediateka "Dviračio žinios" airs as "Dviračio savaitė").
- **Dedicated Sections:**
  - 🔴 **Tiesiogiai (Live):** Live HLS streams for `LRT TELEVIZIJA`, `LRT PLIUS`, `LRT LITUANICA`, `LRT RADIJAS`, `LRT KLASIKA`, and `LRT OPUS` with real-time on-air program schedules.
  - 🏆 **Auksinis protas** (`category_id=114`)
  - 🎓 **Tūkstantmečio vaikai** (`category_id=304`)
  - 🔥 **Populiariausi** & 🆕 **Naujausi**
  - 📺 **Populiarios laidos:** *Panorama*, *Dienos tema*, *Laba diena, Lietuva*, *Svarbi valanda*, *Stilius*, *Beatos virtuvė*, *Keliai. Mašinos. Žmonės*, * Nacionalinė ekspedicija*, *Daiktų istorijos*, * Duokim garo!*, and more.
  - 🎬 **Visos rubrikos:** *Filmai*, *Serialai*, *Dokumentika*, *Kultūra*, *Sportas*, *Vaikams*, *Koncertai*, *Archyvai*.
- **🔍 Native Lithuanian D-Pad Search (`Paieška`):**
  - Built-in on-screen Lithuanian keyboard (`Ą Č Ę Ė Į Š Ų Ū Ž`) optimized for TV remote directional navigation.
- **Native HLS Video Player (`PlayerActivity`):**
  - Resume playback position memory (`pos_<id>`), D-pad scrubbing (`±15s` / `±60s`), auto-hiding OSD controls, and full show descriptions.
- **Built-in Bluetooth A2DP Sniff-Mode Guard (`BtKeepAliveService`):**
  - Prevents Bluetooth A2DP speakers (such as Edifier R1700BT on Philips/MediaTek TVs) from entering `500 ms` Sniff Mode while the TV screen is on.

---

## Project Structure

```text
├── AndroidManifest.xml
├── build.sh
├── res/
│   └── drawable/
│       ├── ic_launcher.xml
│       └── tv_banner.xml
└── src/lt/lrt/lean/
    ├── MainActivity.java        # Native Leanback-style TV UI, D-pad navigation & search
    ├── PlayerActivity.java      # Native HLS VideoView player with OSD & resume memory
    ├── LrtApi.java              # LRT.lt JSON API client, HLS resolver & LRU image cache
    ├── VideoItem.java           # Data models for VOD items, Live channels & sections
    ├── BtKeepAliveService.java  # Bluetooth A2DP Active Mode keep-alive service
    └── BtKeepAliveReceiver.java # Boot & Bluetooth connection broadcast receiver
```

---

## Build & Deploy via ADB

Requires the Android SDK (the newest installed `build-tools` and `platforms` are used) and JDK 11+ (Android Studio's bundled JBR by default):

```bash
bash build.sh
```

- With more than one device attached, pick the TV: `ANDROID_SERIAL=<tv-ip>:5555 bash build.sh`
- Build only, no install: `NO_INSTALL=1 bash build.sh`
- The signing key lives at `~/.android/lrt-greitas.keystore` (override with `LRT_KEYSTORE`), outside the repo, so a fresh clone can still update the installed app. A build signed with a different key can only be installed after `adb uninstall lt.lrt.lean`, which wipes the app's resume positions.

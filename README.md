# LRT Greitas (`lt.lrt.lean`) — Ultra-Lean Native Android TV App for LRT.lt

A blazing-fast, **53 KB**, zero-dependency native Android TV application for **LRT.lt** (`Lietuvos nacionalinis radijas ir televizija`), built directly against the Android SDK without WebViews, React Native, or bloated frameworks.

---

## Features

- **100% Native Android TV UI (< 15 MB RAM, 53 KB APK):**
  - Instant startup (`< 150 ms`) and 60 fps D-pad navigation even on budget MediaTek Android TVs.
  - Parallel background feed prefetching and in-memory LRU thumbnail cache.
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

Requires Android SDK (`build-tools/34.0.0`, `platforms/android-34`) and JDK 11+:

```bash
bash build.sh
```

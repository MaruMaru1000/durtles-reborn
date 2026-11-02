# Durtles Reborn

An unofficial Android client for [WaniKani](https://www.wanikani.com), written from scratch in Kotlin and Jetpack Compose. It follows the feature set of the long-standing third-party clients (Smouldering Durtles in particular) but with its own UI, colours and artwork.

> **Not affiliated with WaniKani or Tofugu.** "WaniKani" is a trademark of Tofugu LLC. This app is a third-party client that talks to the public WaniKani API v2 with a token you provide. Do not report problems with it to WaniKani.

## Features

- **Dashboard** — Lessons and Reviews cards, kanji-passed progress with the 90% level-up mark, Radicals and Kanji tiles with SRS stage bars (or A1–A4 / G1–G2 / Master / Enlightened / Burned text), tap-and-hold peek, an eye toggle that reveals meanings and readings, a 7-day hourly review forecast and a six-month progress heatmap.
- **Lessons and reviews** — the same fast quiz screen for both: meaning and reading questions, kana input, typo leniency, "close enough" handling, undo, skip, "Don't know", "I was wrong", wrap-up, and leave-and-resume (the session is saved after every answer and appears on Home).
- **Answer feedback** — a rounded panel after every answer, green for correct and red for wrong, both derived from the active theme. Wrong answers hide the accepted answer unless you turn that on; swipe up, tap the reveal button, or tap the panel for the full details.
- **Self-study** — quiz whatever a search matches, without touching your WaniKani progress.
- **Item pages** — meaning, reading, mnemonics, example sentences, related subjects, stroke order for radicals and single kanji, and pitch accent where it is known.
- **Graded Reading** (optional, off by default) — pull articles from NHK News Web Easy, Tadoku and Aozora Bunko, or add your own RSS/Atom feed. Articles are tokenized on the device and WaniKani words are tinted by SRS stage, with known-word and known-kanji coverage, a "ready level", a native reader and a WebView fallback. Item pages gain "seen in your reading" examples. The cache is capped at 30 days / 50 MB and is never uploaded.
- **Audio** — vocabulary pronunciation clips, downloaded by level and playable offline.
- **Ten themes** — Dynamic (wallpaper colours), Light, Dark, Black (OLED), Matcha, Sandstone, Nord, Forest, Crabcroc and Sakura, plus a stage-name display toggle.
- **Notifications and a home-screen widget** — an hourly "reviews are waiting" check, and a 2×2 widget with Lessons and Reviews cards that follows the theme.
- **Settings backup** — export and import your settings as a file that also reads the original app's backups. Your token is never saved or restored.
- **Dry run** — practice mode for reviews and lessons where nothing is saved on the phone or sent to WaniKani.

## Requirements

- Android 7.0 (API 24) or newer.
- A WaniKani API v2 token: <https://www.wanikani.com/settings/personal_access_tokens>. The token is stored encrypted on the device (Android Keystore) and is only sent to `api.wanikani.com`.

## Install

Download the signed APK from [Releases](../../releases) and open it on your phone (you may need to allow installing from unknown sources).

## Build

Requires a JDK 17+ and the Android SDK. The Gradle wrapper handles the rest.

```bash
./gradlew testDebugUnitTest     # unit tests
./gradlew assembleDebug         # debug APK
./gradlew assembleRelease       # release APK (minified with R8)
./gradlew bundleRelease         # release App Bundle
```

Release signing reads `keystore.properties` in the project root:

```properties
storeFile=keystore/my-release.p12
storeType=PKCS12
storePassword=…
keyAlias=…
keyPassword=…
```

That file and the keystore are git-ignored and must never be committed. Without it, `assembleRelease` still works and produces an **unsigned** APK, which is what a store that signs from source (such as F-Droid) expects.

## Project layout

```
app/src/main/java/com/example/durtlesreborn/
  answer/     answer checking (typos, readings, on'yomi/kun'yomi, near misses)
  data/       Room database, sync, dashboard and search repositories
  notify/     notifications, the hourly alarm and the home-screen widget
  session/    session engine, planner, quiz controller
  settings/   DataStore settings and backup/restore
  strokes/    stroke-order data
  ui/         Compose screens, theme and icons
reading-core/     pure Kotlin tokenizing, matching and article scoring
reading-sources/  NHK Easy / RSS / index fetching and text extraction
reading-store/    Room cache for sources, articles and example sentences
reading-ui/       library, reader and WebView screens
reading-bridge/   the only reading module the app compiles against
app/src/main/assets/   stroke order (KanjiVG) and pitch accents (Kanjium)
docs/                  parity checklists and release notes
```

## Third-party data

Two data files are bundled and are the only third-party content in the app:

| File | Source | Licence |
| --- | --- | --- |
| `app/src/main/assets/strokes.txt` | [KanjiVG](https://kanjivg.tagaini.net/) r20260714 | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| `app/src/main/assets/pitch_accents.txt` | [Kanjium](https://github.com/mifunetoshiro/kanjium) by Uros O. | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |

Attribution and licence notices for both sit next to the files in `app/src/main/assets/` (the full CC BY-SA 4.0 legal text for Kanjium, and the KanjiVG attribution with the CC BY-SA 3.0 link). This project's own code is licensed separately; the data files are redistributed unmodified in substance, only repacked.

Graded Reading uses Kuromoji 0.9.0 with the IPADIC dictionary (Apache 2.0, with the NAIST/ICOT dictionary notice). The APK strips dependency `META-INF` notices, so that notice is kept in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Licence

The application code is released under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

It is an independent rewrite, but it follows the behaviour of Smouldering Durtles and Flaming Durtles, which are GPLv3, so the same licence is the safe and fair choice.

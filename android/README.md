# Thirteen for Android

Play against three computer players (always c = 0) and analyze hands, on the phone, in English or Traditional Chinese (the system's per-app language setting). The engine is the repository's
C++ (`cpp/`, through `cpp/command/guiengine.h` and JNI) with ONNX Runtime for Android; the UI is Jetpack Compose.

- **Play** (always c = 0): tap cards to select them (or swipe across them: a swipe that starts on an unselected card selects every card it passes, one that starts on a selected card unselects them), then tap a row to place them; or tap a row first, then cards
  until it is full. Tap a placed card to take it back, or long-press a row to take back all of it. When two rows are
  full, the last one fills itself. There are no hints during a game. Confirm shows one line per player with the
  scoops as arrows and bonus rows framed; "Analyze my hand" (beside "Next hand") lists the candidates with the
  player's arrangement on top and its notable differences from the best one colored.
- **Analyze**: pick 13 cards (or Random, or the last hand played, or a hand from Records), then the candidates: the
  undominated arrangements with the network's Q, and the event probabilities when one is tapped. Above them, short remarks
  (`Commentary.kt`): what the player's arrangement could do better, only when it loses at least half the red-mark gap
  (the network is not exact); what kind of choice the best one is against the best of other row categories (better
  on both scoops and scooped, attacking or defensive, or under row scoring, rows or scoops first); and how strong the
  hand is. Common arrangements are named in the list (三輪車, 五輪車, 雙同花, ...), as a classification only.
- **Records**: every confirmed hand is saved in the JSONL format of `gui/collect.html` (`label` is `android`), with
  `rules`, the full rules spec as `thirteen record` writes it, and `result`, the player's score of the hand, which
  the fitting ignores. A record can be
  sent to Analyze. Export writes the file where you choose; `thirteen humanfeatures -i FILE` reads it.
- **Rules**: a preset of `docs/rules.md` section 11 (family, family_mid2, taiwan, pagat), then any value changed.
  Strict rows and the ranking of A-2-3-4-5 change the play (which arrangements foul, how hands rank) and are kept
  apart from the points. A game takes the rules when it is dealt; the running scores start over when they change.
  The built-in network, run2's `net_10`, takes every rule on the page; a loaded network from before the rules input
  plays every rules as family, which the app warns about.
- **Settings**: each opponent seat is a network (the built-in `run2_net10` or one loaded from a `.onnx` file), Monte Carlo
  against `indep`, or `indep`.

## Building

`android/build-apk.sh` builds the release APK locally (after the unit tests) and copies it to
`android/build/apk/thirteen-<version>.apk` (or `$THIRTEEN_APK_DIR`). It needs JDK 17, Gradle, and the Android SDK
with platform 35, build-tools 35, NDK 27.0.12077973 and CMake 3.22.1. Their paths come from the environment or from
`android/local.env` (shell assignments such as `ANDROID_HOME=...`, `GRADLE=...`, kept out of the repository); see the
script for the variables. The built-in network is not in the repository: put it at
`android/app/src/main/assets/run2_net10.onnx` (any network exported by `python/train.py`, see docs/training.md)
before building; the app and its device tests expect it. The version code is the minutes from 2026-01-01 to the last commit, so a newer
build always installs over an older one. The version name is the release's `vX.Y.Z` tag on the tagged commit (`1.0.0`);
any other build is a beta, named after the latest tag (`1.0.0+beta.3` three commits after it).

`android/device-tests.sh` runs `ScreenshotTest` on a local headless Android 14 emulator (made on the first run): every
screen under several screen, display and font sizes, saved in `android/build/screenshots`, failing when a card is cut
off at the side. `ALL=1` runs every device test instead. It needs the SDK's emulator and
`system-images;android-34;google_apis;x86_64`, and access to `/dev/kvm`. On a slow or flaky connection sdkmanager can't
resume the 1.5 GB image; download `x86_64-34_r14.zip` from `https://dl.google.com/android/repository/sys-img/google_apis/`
with `curl -C -`, check its SHA-1 against `sys-img2-3.xml` there, unzip it into
`$ANDROID_HOME/system-images/android-34/google_apis/` and add a `package.xml` (sdkmanager writes one for every package it installs).

The layout follows the room it has rather than fixed sizes: cards are sized from the width and height left (their
rank and suit scale with the card, not with the font size), room for text is in `sp` (`textRoom`) so it grows with
the font, and a page that still does not fit scrolls.

## Signing

Updates install over the old version (keeping the records) only when every APK is signed with the same key: a
keystore kept outside the repository, described by `~/.android/thirteen-release.txt` (or `$THIRTEEN_SIGNING`), with
lines `keystore PATH`, `alias NAME` and `password PASS`, which `build-apk.sh` reads. Without it the APK is signed with
the debug key.

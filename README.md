# HearText

Android app that turns text into sound. Import local books, browse a cloud store, read with a customizable reader, and listen with system TTS or offline voice packs.

**Package:** `com.yishenghuang.heartext`  
**Min SDK:** 26 · **Target SDK:** 36  
**Privacy:** [heartext-privacy.netlify.app](https://heartext-privacy.netlify.app)

## Features

- **Library** — import EPUB, TXT, and PDF; filter by reading / store / local
- **Store** — browse, search, and download catalog books (signed in)
- **Reader** — typography, themes, page-turn styles, bookmarks, search
- **Listen** — system TTS or offline neural voice packs; lock-screen / headset controls
- **Sync** — cloud reading progress when signed in (Clerk)
- **i18n** — English, 中文, Français, Español (or follow system)

## Stack

- Kotlin · Jetpack Compose · Navigation · Room
- [Readium](https://github.com/readium/kotlin-toolkit) (EPUB / PDF)
- Media3 media session for playback
- Clerk for auth
- Firebase Crashlytics (optional)
- sherpa-onnx AAR for offline TTS (downloaded on first build)

## Setup

### Requirements

- Android Studio (recent stable) with JDK 17
- Android SDK matching `compileSdk` 37

### Local config

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

| Key | Required | Notes |
|-----|----------|--------|
| `CLERK_PUBLISHABLE_KEY` | For login / sync / store | From your Clerk dashboard |
| `HEARTEXT_API_BASE_URL` | No | Defaults to `https://heartext.677000.xyz` |
| `HEARTEXT_STORE_FILE` / passwords / alias | For signed release | Paths relative to project root; see example file |

Do not commit `local.properties`, keystores, or `app/google-services.json`.

### Firebase (optional)

Place `app/google-services.json` from the Firebase Console (Android app id `com.yishenghuang.heartext`). Without it, the project still builds; Crashlytics plugins are skipped.

### Build & run

```bash
./gradlew :app:assembleDebug
# or open the project in Android Studio and Run
```

First build downloads `sherpa-onnx` into `app/libs/` via the `downloadSherpaAar` task.

### Isolated local verification (no production writes)

On Windows, run `powershell -File tools/verify-local.ps1`. It builds the debug-only
`com.yishenghuang.heartext.validation` APK, runs unit tests and Android Lint, and uses
Android Studio's bundled JDK if `JAVA_HOME` is absent. This opt-in build has no Clerk
key, skips the Firebase plugins, and points to `http://10.0.2.2:18080` (the emulator's
host loopback). Existing `local.properties` and release signing are not changed.
Use **Continue offline** for local reading. HTTP is allowed only in this validation
debug build. Normal builds require HTTPS.

With an emulator running, add `-Connected -Serial emulator-5556` (using its actual
ADB serial) to run device tests on that device only. Tests of HTTP
retry and cancellation use an in-process MockWebServer, never the configured API.
Reports are under `build/validation-app/reports/`; the isolated APK is at
`build/validation-app/outputs/apk/debug/app-debug.apk`. The verification script uses
a single-use Gradle daemon to avoid retained Windows test JAR locks. Current scope and evidence are in
[`docs/LOCAL_HARDENING.md`](docs/LOCAL_HARDENING.md).

### Release signing

Set the `HEARTEXT_*` signing properties in `local.properties`, keep the keystore under `keystore/` (gitignored), then:

```bash
./gradlew :app:assembleRelease
```

## Play assets

Store listing icons and screenshots live under `play/`. Regenerators:

```bash
python tools/process_brand_assets.py
python tools/make_play_promos.py
python tools/make_feature_graphic.py
```

## License

Private / all rights reserved unless otherwise stated.

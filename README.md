# Mini JARVIS

A modular, fully offline, privacy-first personal assistant for Android. Every
feature runs on-device; there is no backend server, no cloud storage, and no
`android.permission.INTERNET` declared anywhere in the app.

## Why this is actually offline

- **No `INTERNET` permission.** It is not declared in `AndroidManifest.xml`.
  Android enforces this at the OS/network layer — without the permission,
  the app process cannot open a socket at all, regardless of what any
  library tries to do.
- **Encrypted local database.** All structured data (expenses, food, meds,
  weight, habits, tasks, chat, and cached system data) lives in a single
  [SQLCipher](https://www.zetetic.net/sqlcipher/)-encrypted Room database
  (`data/AppDatabase.kt`). The passphrase is generated once with
  `SecureRandom` and stored via `EncryptedSharedPreferences`, whose key
  material is sealed in the device's hardware-backed Android Keystore
  (`security/PassphraseStore.kt`) — it never leaves the device and is never
  visible in plaintext.
- **Backup disabled.** `android:allowBackup="false"` plus explicit
  `data_extraction_rules.xml` exclusions block cloud backup and
  device-to-device transfer of the database, prefs, and files.
- **On-device ML only.** Image labeling uses ML Kit's *bundled*
  `image-labeling` model artifact, which ships inside the APK — not the
  network-downloaded "unbundled" variant. (On-device text recognition/OCR
  was evaluated and deliberately left out for now: ML Kit's bundled
  `text-recognition` artifact pulls in an OCR pipeline whose native
  libraries alone add ~22MB to the APK. See `vision/ImageAnalyzer.kt` for
  notes on re-adding it.)
- **Speech recognition** uses Android's `SpeechRecognizer` with
  `EXTRA_PREFER_OFFLINE`. Mini JARVIS never calls a speech API itself; if a
  given device's platform recognizer has no offline model installed, voice
  input will fail cleanly rather than the app silently going online — see
  *Known limitations* below.

## Modules

| Module | Screen | Notes |
|---|---|---|
| Text & voice chat | `Chat` | Rule-based local NLU (`assistant/IntentParser.kt`) routes commands to every other module; on-device TTS/STT. |
| Image capture & analysis | `Vision` | CameraX capture → ML Kit on-device image labeling, saved locally (no text/OCR — see below). |
| Expense tracker | `Expenses` | Amount/category/note, running totals. |
| Food tracker | `Food` | Meal type, calories, notes. |
| Medicine tracker | `Medicine` | Medicines + taken/missed dose logs. |
| Weight & health tracker | `Weight & Health` | Weight history plus free-form health metrics (BP, heart rate, etc.). |
| Habit tracker | `Habits` | Daily toggle, weekly target progress. |
| Tasks & reminders | `Tasks & Reminders` | Local notifications scheduled with WorkManager. |
| Calls, location & app usage | `Calls, Location & Usage` | Each sub-module is opt-in and reads only device-local providers/APIs. |
| Music history | same screen | `NotificationListenerService` reads *only* media-session title/artist metadata, never notification text. |
| Reports | `Reports` | Daily/weekly/monthly aggregates computed from local data. |
| Smart search | `Smart Search` | Natural-language-ish search (date phrases + keywords) across every module. |
| Settings & privacy | `Settings & Privacy` | Permission status at a glance, and an "erase all local data" action. |

## Permissions — minimal and opt-in

No permission is requested at launch. Each optional module requests its own
permission only when the user opens that screen, and every module remains
fully usable (just empty) without it:

- `RECORD_AUDIO` — voice chat only.
- `CAMERA` — image capture only.
- `READ_CALL_LOG` — call history only.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — on-demand "log my
  current location" only; there is no background location tracking.
- `PACKAGE_USAGE_STATS` — a special app-op granted from system Settings, for
  the app usage stats module.
- Notification Listener access (no manifest permission — granted per-app in
  system Settings) — for the music "now playing" history module.
- `POST_NOTIFICATIONS` — local task/medicine reminder notifications.

Two more permissions show up in the *built APK* that aren't in the app's own
source manifest, both injected by libraries and both unrelated to
networking: `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, and `FOREGROUND_SERVICE`
come from WorkManager's own manifest (used internally to run and
reschedule local reminder jobs reliably) — this is standard for any app
using WorkManager and cannot be removed without breaking reminders.
ML Kit's transitive `vision-internal-vkp` library separately injects
`INTERNET` and `ACCESS_NETWORK_STATE` (for Google-side telemetry on the
inference pipeline, not for the on-device inference itself); those two
*are* explicitly stripped in `AndroidManifest.xml` via `tools:node="remove"`
so the shipped APK has zero network-capable permissions — verified with
`aapt dump badging` against the built debug APK.

## Architecture

Plain, dependency-injection-framework-free Kotlin + Jetpack Compose:

```
core/        Application class + AppContainer (manual composition root)
data/        Room entities, DAOs, AppDatabase (SQLCipher-backed), repositories
security/    Keystore-backed passphrase generation/storage
assistant/   Local intent parser, assistant engine, voice input/output
vision/      ML Kit image analysis
system/      Permission checks, call log / location / usage-stats / music
             listener helpers, local reminder scheduling
reports/     Daily/weekly/monthly aggregation
search/      Cross-module smart search
ui/          Compose navigation + one screen per module
```

## Building

Requires Android Studio (or the CLI) with the Android SDK installed —
this repository does not bundle the SDK itself:

```
./gradlew assembleDebug     # fast, unminified, for local testing
./gradlew assembleRelease   # R8-minified + shrunk resources, ~19MB
```

Minimum SDK 26 (Android 8.0), target/compile SDK 34. This repo has no
release signing config, so `assembleRelease` produces an unsigned APK —
that's correct for a release build; sign it with your own keystore before
distributing it. (A version of this release APK sent for testing purposes
was temporarily signed with the debug key so it could be installed
directly — never ship a real release that way.)

## Known limitations (read before relying on this in production)

- **Voice recognition offline coverage is device-dependent.** Android's
  `SpeechRecognizer` decides whether an offline model is actually used;
  Mini JARVIS requests it via `EXTRA_PREFER_OFFLINE` but cannot force it on
  every device/OEM build. Text chat and all trackers work with zero network
  dependency regardless.
- **The assistant is rule-based, not a general LLM.** `IntentParser.kt` uses
  pattern matching over English phrases (e.g. "spent 200 on lunch", "I weigh
  68 kg"). It is intentionally simple so it can run with no model download
  and no network call; conversational nuance is limited.
- **Reminder timing uses WorkManager**, not exact alarms, to avoid
  requesting the `SCHEDULE_EXACT_ALARM` permission. Reminders may fire a
  little later under aggressive battery optimization/Doze — trading
  precision for a smaller permission footprint.
- **No text recognition (OCR) in Vision.** Image labeling ("what's in this
  photo") works; reading text out of a photo does not, purely because
  bundling ML Kit's on-device OCR pipeline adds ~22MB of native libraries.
  This was a deliberate size trade-off, not a technical limitation — adding
  `com.google.mlkit:text-recognition` back and wiring up a
  `TextRecognition.getClient(...)` call in `vision/ImageAnalyzer.kt` restores
  it in a few lines if APK size isn't a constraint for your build.
- **This app also builds an arm64-v8a-only debug APK by default**
  (`defaultConfig.ndk.abiFilters`) to keep test builds small; a release meant
  for older 32-bit devices should widen this back to include
  `armeabi-v7a`/`x86`/`x86_64`.
- **UI strings are in English** in this pass; the architecture (Compose +
  `strings.xml`) supports adding a `values-ta/` resource set for Tamil
  localization as a follow-up.
- **Compiles and packages cleanly, but has not been run on a device or
  emulator.** A real Android SDK was installed in this environment and
  `./gradlew assembleDebug` produces a working, installable
  `app-debug.apk` with zero compile errors (the manifest was even caught
  and fixed for a real issue this way — see above). There was no
  emulator/device available here to actually launch it and click through
  each screen, so treat the debug APK as "builds clean, UI/runtime
  behavior unverified" rather than fully tested — install it on a device
  and exercise each module before relying on it.

## Privacy summary

No account, no sign-in, no analytics SDK, no crash reporter, no ad SDK, no
`INTERNET` permission. Every byte Mini JARVIS collects is written to a
SQLCipher-encrypted database on the device it runs on, and the in-app
"Erase all local data" action (Settings & Privacy) permanently deletes it.

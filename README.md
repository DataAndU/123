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
- **On-device ML only.** Image labeling and text recognition use ML Kit's
  *bundled* model artifacts (`image-labeling`, `text-recognition`), which
  ship inside the APK — not the network-downloaded "unbundled" variants.
- **Speech recognition** uses Android's `SpeechRecognizer` with
  `EXTRA_PREFER_OFFLINE`. Mini JARVIS never calls a speech API itself; if a
  given device's platform recognizer has no offline model installed, voice
  input will fail cleanly rather than the app silently going online — see
  *Known limitations* below.

## Modules

| Module | Screen | Notes |
|---|---|---|
| Text & voice chat | `Chat` | Rule-based local NLU (`assistant/IntentParser.kt`) routes commands to every other module; on-device TTS/STT. |
| Image capture & analysis | `Vision` | CameraX capture → ML Kit image labeling + text recognition, saved locally. |
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
./gradlew assembleDebug
```

Minimum SDK 26 (Android 8.0), target/compile SDK 34.

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
- **UI strings are in English** in this pass; the architecture (Compose +
  `strings.xml`) supports adding a `values-ta/` resource set for Tamil
  localization as a follow-up.
- **Not yet run on a device/emulator in this environment** — there was no
  Android SDK/emulator available to build and manually test against. The
  code was written and reviewed carefully against the real Android/Jetpack
  APIs it calls, and `./gradlew wrapper` was used to generate a real Gradle
  wrapper, but treat this as an unverified-by-execution first cut: build it
  in Android Studio and exercise each screen before shipping.

## Privacy summary

No account, no sign-in, no analytics SDK, no crash reporter, no ad SDK, no
`INTERNET` permission. Every byte Mini JARVIS collects is written to a
SQLCipher-encrypted database on the device it runs on, and the in-app
"Erase all local data" action (Settings & Privacy) permanently deletes it.

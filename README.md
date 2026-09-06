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

## Mini JARVIS is a voice assistant first

The Chat screen is the app's start destination — talk or type to it and it
routes your request to whichever module actually handles it. Every other
screen (Expenses, Food, Habits, Reports, ...) still exists and works exactly
as before for manual entry/browsing, reachable from the drawer, but they are
no longer the primary way in.

What makes this a real "access to my whole phone" assistant rather than a
chatbot bolted onto some trackers:

- **Open any app**: "open camera", "launch spotify".
- **Calls & texts**: "call mom", "text mom saying I'm on my way" — resolves
  contact names via `ContactsContract`, dials directly if `CALL_PHONE`/
  `SEND_SMS` are granted, otherwise opens the dialer/SMS pre-filled so it
  still works with fewer permissions granted.
- **System controls**: volume, screen brightness, flashlight, Do Not
  Disturb — direct API calls, no Accessibility Service needed.
- **WiFi / Bluetooth**: "turn on wifi" — Android 10+ no longer lets a
  regular app silently flip these radios, so this opens the relevant
  settings panel and (if the Accessibility Service below is enabled) tries
  to tap the toggle for you; reliability varies by phone/OEM, and it always
  falls back to "you tap it" if accessibility isn't on.
- **Whole-screen control** (needs the Accessibility Service, see below):
  "what's on my screen" (reads it aloud/back to you), "tap settings", "go
  home", "go back", "take a screenshot", "lock my phone".
- **Always-listening wake word** ("Jarvis, ..."): optional, off by default,
  toggled in Settings — see *Wake word: how it stays offline* below.

## The Accessibility Service — the actual "whole phone" permission

`control/JarvisAccessibilityService.kt` is what lets the assistant read the
screen and tap things in *other* apps, not just its own. This is the most
powerful permission on Android, which is exactly why the platform requires
a human to turn it on manually from system Settings (Settings → Accessibility
→ Mini JARVIS Phone Control) — no app, this one included, can enable it for
itself. Every capability that needs it fails with a clear "enable
Accessibility in Settings" message rather than silently doing nothing until
it's granted, and it can be revoked at any time from the same place it was
granted.

## Wake word: how it stays offline

An "always listening" assistant normally means a dedicated wake-word engine
(e.g. Picovoice Porcupine) — but every one of those needs an `INTERNET`
permission for periodic license checks against the vendor's servers, even
though the audio itself never leaves the device. That's a real compromise
against this app's zero-network identity, so it wasn't used here. Instead,
`assistant/WakeWordListener.kt` repeatedly restarts Android's own on-device
`SpeechRecognizer` (the same one used for on-demand voice chat) and checks
each short utterance for the word "Jarvis" — zero new dependencies, zero new
permission. The honest trade-offs: a brief gap between listening cycles
(restart isn't instantaneous), more battery cost than a lightweight
wake-word classifier since each cycle does full speech-to-text, and the same
per-device offline-recognizer caveat that already applies to voice chat.
`assistant/WakeWordService.kt` runs this as a foreground service with a
persistent, low-priority notification and a one-tap "Stop listening" — a
background microphone should never be invisible.

## Modules

| Module | Screen | Notes |
|---|---|---|
| Text & voice chat | `Chat` | Rule-based local NLU (`assistant/IntentParser.kt`) routes commands to every other module, plus every whole-phone control command below; on-device TTS/STT; **the app's start screen**. |
| Phone & app control | `Settings → Phone & App Control` | Open apps, call/text, system toggles, and the Accessibility Service grant — see above. |
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
| Settings & privacy | `Settings & Privacy` | Permission status at a glance, wake word toggle, Accessibility grant, and an "erase all local data" action. |

## Permissions — minimal and opt-in

No permission is requested at launch. Each optional module requests its own
permission only when the user opens that screen or turns on that feature,
and every module remains fully usable (just doing less) without it:

- `RECORD_AUDIO` — voice chat and the wake word listener.
- `CAMERA` — image capture, and the flashlight control (torch access).
- `READ_CALL_LOG` — call history only.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — on-demand "log my
  current location" only; there is no background location tracking.
- `PACKAGE_USAGE_STATS` — a special app-op granted from system Settings, for
  the app usage stats module.
- Notification Listener access (no manifest permission — granted per-app in
  system Settings) — for the music "now playing" history module.
- `POST_NOTIFICATIONS` — local task/medicine reminders, and the wake-word
  listening notification.
- `CALL_PHONE` — dial directly by voice; without it, "call X" opens the
  dialer pre-filled instead.
- `SEND_SMS` / `READ_SMS` — send texts by voice, and "read my last message";
  without `SEND_SMS`, texting falls back to a clear error rather than
  silently failing.
- `READ_CONTACTS` — resolve a spoken name ("mom") to a phone number for
  calls/texts; without it, only raw numbers work.
- `WRITE_SETTINGS` — a special permission (granted via Settings, like
  `PACKAGE_USAGE_STATS`) for voice-controlled screen brightness.
- Do Not Disturb access (no manifest permission — granted via Settings) —
  for ringer-volume changes and toggling DND.
- The Accessibility Service (see above) — screen reading/tapping,
  home/back/recents/lock/screenshot, and the WiFi/Bluetooth toggle
  workaround. Manually enabled in system Settings; Android does not allow
  an app to grant this to itself.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` / `RECEIVE_BOOT_COMPLETED`
  — the wake-word listening service and its restart after a reboot, only
  if wake word was left on.

Two more permissions show up in the *built APK* that aren't requested for
any user-facing feature, both injected by libraries and both unrelated to
networking: `WAKE_LOCK` comes from WorkManager's own manifest (used
internally to run and reschedule local reminder jobs reliably) — standard
for any app using WorkManager and not removable without breaking reminders.
ML Kit's transitive `vision-internal-vkp` library separately injects
`INTERNET` and `ACCESS_NETWORK_STATE` (for Google-side telemetry on the
inference pipeline, not for the on-device inference itself); those two
*are* explicitly stripped in `AndroidManifest.xml` via `tools:node="remove"`
so the shipped APK still has zero network-capable permissions — verified
by inspecting the merged manifest Gradle actually produced.

## Architecture

Plain, dependency-injection-framework-free Kotlin + Jetpack Compose:

```
core/        Application class + AppContainer (manual composition root)
data/        Room entities, DAOs, AppDatabase (SQLCipher-backed), repositories
security/    Keystore-backed passphrase generation/storage
assistant/   Local intent parser, assistant engine, voice input/output,
             wake-word listener + foreground service
control/     AppLauncher, SystemControlManager, PhoneActionsManager,
             ContactsHelper, JarvisAccessibilityService — the "whole phone" layer
vision/      ML Kit image analysis
system/      Permission checks, call log / location / usage-stats / music
             listener helpers, local reminder scheduling, wake-word settings,
             boot receiver
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
- **WiFi/Bluetooth voice toggling is best-effort.** Android 10+ removed the
  API for a regular app to flip these radios silently; the Accessibility
  Service tries to find and tap the toggle in the settings panel Android
  opens, but the exact screen layout varies by OEM/Android version, so this
  is the one control command that isn't guaranteed to actually work —
  it always at least gets you to the right screen to tap it yourself.
- **The Accessibility Service's screen-reading/tapping is generic**, not
  tuned per-app: it walks the visible accessibility tree for matching text.
  This works well for straightforward UIs and less well for apps that draw
  custom views without proper accessibility labels.
- **Wake word detection is not instantaneous or perfectly reliable** — see
  *Wake word: how it stays offline* above for the specific trade-offs
  versus a dedicated wake-word engine.
- **Compiles and packages cleanly, but has not been run on a device or
  emulator.** A real Android SDK was installed in this environment and
  `./gradlew assembleDebug` produces a working, installable
  `app-debug.apk` with zero compile errors (the manifest was even caught
  and fixed for a real issue this way — see above). There was no
  emulator/device available here to actually launch it and click through
  each screen, so treat the debug APK as "builds clean, UI/runtime
  behavior unverified" rather than fully tested — install it on a device
  and exercise each module before relying on it. This applies doubly to
  the whole-phone control features added afterward: the Accessibility
  Service, wake word, and call/SMS/contacts permissions all need a real
  device with real permission grants and were verified only by confirming
  the merged manifest carries the right permissions/services/receivers and
  that the code compiles and packages — not by watching them run.

## Privacy summary

No account, no sign-in, no analytics SDK, no crash reporter, no ad SDK, no
`INTERNET` permission. Every byte Mini JARVIS collects is written to a
SQLCipher-encrypted database on the device it runs on, and the in-app
"Erase all local data" action (Settings & Privacy) permanently deletes it.

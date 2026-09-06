# Gemma Assistant

A standalone, offline-first Android app whose entire purpose is being your personal AI
assistant, powered by an on-device Gemma model (any MediaPipe-compatible `.task` file,
e.g. Gemma 3n). It is a separate app from Mini JARVIS — a fresh package
(`com.gemmaassistant.app`), a fresh identity, and a much smaller surface: no habit/expense/
medicine trackers, no camera vision, no location, no usage-stats dashboards. Just chat,
voice, and full device control.

## Running as your default assistant, always in the background

Two independent features make Gemma Assistant behave like a system-level assistant
(ChatGPT/Claude/Gemini's own apps) rather than just another app you open by hand:

- **Default digital assistant app.** Gemma Assistant registers a `VoiceInteractionService`,
  which makes it selectable under Settings → Apps → Default apps → Digital assistant app —
  the exact slot Google Assistant/Gemini occupies on stock Android. Once picked there, the
  system's assist gesture (long-press the home button, or a bottom-corner swipe on gesture
  navigation) opens Gemma Assistant's chat instantly from anywhere, in any app. Settings has
  a one-tap link to that system screen, plus a live status line. Some OEMs (Samsung, Xiaomi,
  and others that ship their own built-in assistant) hide or restrict this system picker —
  if Gemma Assistant doesn't appear as a choice, that's a manufacturer restriction on their
  build of Android, not something an app can work around.
- **Actually running in the background.** The "Wake word" toggle in Settings starts a
  foreground service with a persistent (but dismissible) notification, which is what
  legitimately keeps the process alive between uses — Android does not allow an ordinary
  app to run indefinitely in the background without one. Settings also has a "Run in
  background" section with a one-tap battery-optimization exemption request, since Android
  will otherwise eventually suspend even a foreground service's app process to save power,
  especially with the screen off for a long time.

Together, these two are the realistic Android equivalent of "always available, like the
default assistant": there is no way for a non-system, sideloaded app to run with zero
footprint the way a pre-installed system assistant can, but this gets as close as the
platform allows a regular app to get, with every step visible and under your control
(a persistent notification while listening, and an explicit system picker for the assist
gesture) rather than anything silent.

## What it can do

**Without any model loaded**, a small local rule-based parser already understands plain
commands, fully hands-free through voice or typed chat:

- Calls and texts: "call mom", "text mom saying I'm on my way", "read my last message"
- Volume / brightness / flashlight / WiFi / Bluetooth / Do Not Disturb: "turn up the volume",
  "turn on the flashlight", "turn off wifi", "enable do not disturb"
- Navigation and screen control (via Accessibility Service): "go home", "go back", "show
  recent apps", "lock my phone", "take a screenshot", "what's on my screen", "tap settings",
  "type hello there", "scroll down"
- Opening any installed app by name: "open camera", "launch spotify"

**Once you import and load a local Gemma `.task` model** (Settings → Local AI model), a
tool-calling agent takes over: it understands free-form phrasing, chains multi-step
requests ("open messages, then read my last text"), and additionally reaches:

- **Your files** — list, search, read, write, and delete within any folder you explicitly
  grant it (Settings → Agent file access), via the standard Android folder picker. It never
  gets silent access to your whole device; you choose which folders to share, one at a time,
  and can revoke any of them later.
- **The internet** — a single, plain HTTP(S) fetch tool, off by default and gated behind a
  one-time explicit toggle with a warning dialog (Settings → Internet access). Once
  on, GET requests run immediately; nothing else changes silently.

Every part of this — calls, texts, radios, screen taps/typing/scrolling, file writes/deletes,
and any non-GET network request — is a direct, real API call. There is no macro engine,
scripting layer, or third-party automation SDK involved.

## The one rule that never bends

**The agent never alters anything without your explicit permission.** Reads (listing,
searching, reading a file, a GET request) happen immediately once you've granted the
underlying access. But every file write, every file delete, and every non-GET network
request (POST/PUT/DELETE) always stops and asks first — a confirmation dialog if you're
in the app, or an actionable Allow/Deny notification if you're not — with a 2-minute
timeout that defaults to **denied**. This is enforced in exactly one place
(`llm/ConfirmationGate.kt`), used by every mutating tool call, so there's a single
chokepoint to audit rather than scattered checks that could be missed.

Every agent action — file reads/writes/deletes, web fetches, taps, toggles — is logged
locally (Settings → Recent agent activity) so you can see exactly what it did and when.

## How voice and chat control the whole phone

Both the rule-based parser and the LLM agent execute through the exact same code path
(`AssistantEngine.executeIntent`), so anything you can say, you can also type, and vice
versa. Beyond the fixed command list above, two general-purpose tools make control open-ended
rather than limited to a fixed list of apps/actions:

- **`read_screen` / `tap <text>` / `type <text>` / `scroll`** — the Accessibility Service can
  read whatever's currently on screen, tap anything by its visible label, type into whatever
  text field is focused (or the first one found), and scroll the active list — in *any* app,
  not just ones Gemma Assistant has special-cased. This is what makes "fully control my
  phone" possible on stock, non-rooted Android: apps aren't required to expose an API for the
  assistant to drive them.
- The Accessibility Service is entirely optional and manually enabled by you in system
  Settings (Android does not allow an app to silently turn this on) — every command that
  needs it fails gracefully with a message pointing you to enable it if it's off.

## Privacy & security model

- **Encrypted local database.** Chat history and the agent activity log live in a
  SQLCipher-encrypted Room database. The encryption passphrase is generated once with
  `SecureRandom`, then stored via `EncryptedSharedPreferences` backed by a hardware Keystore
  key — it never leaves the device and is never derived from anything guessable.
- **No telemetry, no analytics, no ads, no accounts.** The only network traffic this app
  ever generates is the fetch tool you explicitly enabled and explicitly invoked.
  Everything else — the model, your chats, your files — stays on-device.
- **Folder access is opt-in, one folder at a time**, via Android's Storage Access
  Framework (not the broad "manage all files" permission). You can revoke any granted
  folder at any time from Settings.
- **"Erase all local data"** in Settings deletes the encrypted database file outright and
  rotates the encryption passphrase — irreversible, and does not touch files outside the
  app's own database (your granted folders/files are untouched by this action).

## Proactive suggestions (optional, deterministic — never runs the LLM)

A lightweight WorkManager job runs about once an hour, doing two purely rule-based, no-LLM
checks (LLM inference is battery-expensive; background checks intentionally never invoke
it):

- Flashlight left on while battery is low
- Device storage running low (under 5% free)

Both can be turned off in Settings, along with an "auto-apply safe actions" toggle that
lets *only* the flashlight-off suggestion apply itself automatically (never anything that
touches your files, network, or messages).

## What's genuinely verified vs. not

**Verified in this session:** the project compiles (`:app:compileDebugKotlin`), a debug
APK builds, and a release APK builds and passes R8 minification with the proguard rules in
this repo. The signed release APK in this delivery was built from that same successful
`assembleRelease` output, aligned and signed for direct install.

**Not verified:** this has not been run on a physical device or emulator in this
environment (no device/emulator available here). The wake-word listener, MediaPipe LLM
inference correctness, Accessibility Service tap/type/scroll reliability across different
OEM UIs, voice recognition, and — especially — whether your specific phone's Settings app
actually lists Gemma Assistant under Digital assistant app (this varies by OEM/Android
version and cannot be confirmed without your hardware) all depend on hardware this
environment doesn't have — please test the golden paths (a plain command, loading a model,
granting a folder, enabling internet access, enabling Accessibility, setting it as your
default assistant, exempting it from battery optimization) yourself after installing.

## Getting a Gemma model onto the device

This app does not bundle a model — you bring your own `.task` file (e.g. a Gemma 3n
variant packaged for MediaPipe LLM Inference). In Settings → Local AI model, use Import to
copy a `.task` file from your device's storage into the app's private storage, then Load
it. Without a loaded model, the app still works as a fully local, rule-based device-control
assistant — the model only adds free-form understanding and the file/internet tools.

## Installing

This is a sideloaded APK, not distributed through the Play Store — Android will warn about
installing from an unknown source, and Google Play Protect may show an extra warning given
the accessibility/SMS/call-permission combination on a non-Play-Store app. That's Play
Protect's standard heuristic for this permission combination, not a defect; it does not
mean the APK is unsafe. Enable "install unknown apps" for whichever app you use to open the
file, and if Play Protect blocks the install outright, you can temporarily disable "Scan
apps with Play Protect" in Play Store settings to proceed, then re-enable it afterward.

## Permissions this app requests, and why

| Permission | Why |
|---|---|
| RECORD_AUDIO | Voice input and wake-word listening |
| POST_NOTIFICATIONS | Wake-word/proactive-suggestion notifications, background confirmation prompts |
| CALL_PHONE | "Call mom" style voice/chat commands |
| SEND_SMS / READ_SMS | Sending texts and reading your last message on request |
| READ_CONTACTS | Resolving "mom" / a name to a phone number |
| WRITE_SETTINGS | Adjusting screen brightness on request |
| Accessibility Service (granted separately in system Settings) | Reading the screen, tapping, typing, scrolling, and global navigation (home/back/recents/lock/screenshot) |
| INTERNET / ACCESS_NETWORK_STATE | The optional, off-by-default web-fetch tool |
| FOREGROUND_SERVICE (+ microphone type) | Keeping wake-word listening alive |
| RECEIVE_BOOT_COMPLETED | Restarting wake-word listening after a reboot, if you had it enabled |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | The "Run in background" button in Settings — a direct system dialog, only shown when you tap it |
| BIND_VOICE_INTERACTION (declared on Gemma Assistant's own services, not requested by the app) | Lets the system exclusively bind these services once you pick Gemma Assistant as your Digital assistant app |

No CAMERA, no location, no call-log, no usage-access, and no notification-listener
permissions exist in this app at all — those were Mini JARVIS features deliberately left
out of this build.

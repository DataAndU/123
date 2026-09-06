# Prosperity Online — Android Client

The Android client for **Prosperity Online**, a real-time, real-people
economics simulator. This app is online-only: there is no local game engine
or save file anymore — every simulation (economy, markets, businesses,
personal finance) runs on the server in
[`prosperity-server/`](../prosperity-server), and this client is a thin,
fully-functional UI + networking layer over it.

See `prosperity-server/README.md` for how the simulation itself works
(business cycle, market pricing, business P&L, personal finance tick) and
`prosperity-server/DEPLOY.md` for how to actually stand up a server.

## What this client does

- **Accounts**: register/login against the server's JWT auth, with the
  server address itself editable on the login screen (there's no fixed
  production URL — you deploy your own server, see DEPLOY.md).
- **Real-time**: a Socket.IO connection carries the world clock's tick
  broadcasts, live trade prints from other players, chat, presence, and
  marketplace order updates. Everything else is plain REST (Retrofit).
- **Dashboard**: net worth, cash, wallet coins, happiness/health/reputation,
  the shared economy's current phase/GDP/inflation/rates, lifestyle tier.
- **Career**: job ladder (apply/quit, gated by education + skills), skill
  training, education enrollment.
- **Markets**: live stocks/bonds/commodities with buy/sell dialogs; trades
  from other players show up as they happen.
- **Business**: start/manage businesses (hire/fire, pricing, advertising,
  upgrades, loans, withdraw/inject capital, close).
- **Bank**: savings, personal loans, real estate (buy/sell), foreign currency
  exchange.
- **Social**: global chat, friend requests, user search.
- **Marketplace**: browse and buy other players' listings; sellers fulfill
  orders through placed → accepted → producing → delivered; manage your own
  listings and both sides of your orders.
- **Wallet**: real-money coin purchases via Google Play Billing, spent only
  in the marketplace — coins never convert back to real money (see the
  server's README for why that boundary is deliberate).

## Architecture

```
network/          Retrofit ApiService + DTOs matching the server's JSON
                   exactly, ApiClient (auth interceptor, rebuildable base
                   URL), SocketManager (Socket.IO client + typed event flows)
data/             TokenStore (encrypted JWT + server URL), GameRepository
                   (every network call wrapped as a Result<T>)
billing/          BillingManager — Google Play Billing Library integration
ui/               OnlineViewModel (single source of UI state, merges REST
                   refreshes with live socket events), Compose screens,
                   navigation drawer + bottom nav
ui/components/    Reusable chart/stat/chip composables (no dependency on
                   any game-specific model — safe to reuse as-is)
```

There is no `engine/` or `save/` package anymore — deleted along with the
offline single-player mode when this app moved online. The server is the
only source of truth for game state; the client never computes financial
outcomes itself, only displays what the server returns and sends the
player's intents (apply for a job, buy a stock, hire an employee, ...).

## Running against a server

1. Get a Prosperity server running (locally for dev, or deployed — see
   `prosperity-server/DEPLOY.md`).
2. On the login screen, set "Server address" to that server's URL
   (defaults to `http://10.0.2.2:4000/`, the Android emulator's alias for
   your host machine's `localhost:4000` — convenient if you're running the
   server locally with `npm run dev` while testing in the emulator).
3. Register an account and play. A debug build allows plain `http://`; a
   release build requires `https://` (see `src/debug/AndroidManifest.xml`
   vs. the main manifest) since a real deployment should be behind TLS.

## Building

```
./gradlew assembleDebug     # unminified, allows cleartext http:// for local dev
./gradlew assembleRelease   # R8-minified; unsigned — sign with your own key to distribute
```

Minimum SDK 26, target/compile SDK 34. No Android SDK is bundled in this
repo — install one via Android Studio or `sdkmanager` first.

## What's verified vs. what needs your own setup

**Verified in this environment:**
- `./gradlew assembleDebug` succeeds end-to-end with the real Android SDK
  and every dependency (Retrofit, OkHttp, socket.io-client, security-crypto,
  Play Billing Library) resolving and compiling cleanly.
- Every network DTO's field names were cross-checked against the actual
  server route/repository code (not guessed), and the server side of every
  endpoint this client calls was exercised directly (curl + a live
  two-client WebSocket session) during backend development — see
  `prosperity-server/README.md`'s "What's verified" section.

**Needs your own setup to fully verify:**
- **No emulator or device was available in this environment** to click
  through the UI against a live server. Install the APK on a device/emulator
  pointed at a running server and play through registration, a trade, a
  business, and a marketplace order before treating the UI itself as fully
  proven — the wiring is real, but no one has watched it render yet.
- **Google Play Billing** requires real in-app products configured in the
  Google Play Console under this app's package name, and a signed release
  build uploaded there — this client can launch the purchase flow and
  verify tokens against the server, but can't create Play Console products
  for you.
- **Socket auth** uses a `token` query parameter (not the `auth` handshake
  object) because that's the more reliably-typed option across
  socket.io-client-java versions; the server accepts either, so this only
  matters if you write another client.

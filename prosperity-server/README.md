# Prosperity Online — Backend

The real-time multiplayer server behind Prosperity: a shared economy, live
trading, businesses, a player-to-player marketplace, friends/chat, and
real-money virtual currency. Node.js + TypeScript + Express + Socket.IO +
PostgreSQL.

Everything in this document was actually run in the environment that built
it: a local Postgres instance, the dev server, and a compiled production
build were all exercised against real HTTP/WebSocket requests before this
was handed off — see the "What's verified" section.

## Architecture

```
economy/     Shared macro-economic simulation (business cycle, Okun's law,
             Taylor-rule interest rates) — same model as the offline game,
             now server-authoritative and shared by every player.
market/      Stocks/bonds/commodities/housing/currency; real-time price
             impact from player trades (trading.ts); optional live data
             feed from AlphaVantage/FRED (liveProvider.ts).
business/    The 7 business types' monthly P&L simulation, server-side.
marketplace/ Player-to-player listings + orders + a coin wallet ledger.
billing/     Google Play purchase verification for buying coins.
tick/        The scheduler that advances economy → markets → businesses
             once per tick and broadcasts the result to everyone.
realtime/    Socket.IO auth + presence + chat.
routes/      REST endpoints, one file per domain.
db/          Postgres pool + a small hand-rolled migration runner.
```

## Running it locally

```bash
cp .env.example .env    # then fill in DATABASE_URL etc.
npm install
npm run migrate         # applies migrations/*.sql, idempotent
npm run dev             # ts-node-dev, auto-restarts on file changes
```

`npm run build && npm start` runs the compiled production build the same
way the Docker image does. See `DEPLOY.md` for actually putting this on
the internet (DigitalOcean App Platform).

## What's verified (not just written)

Every one of these was exercised against a real Postgres database and a
running server in this environment, not just read for plausibility:

- Register → login → JWT-protected `/player/me`.
- Friend request → accept → appears in both users' friend lists.
- Global chat over a real Socket.IO connection, persisted and replayed.
- The economy tick genuinely advancing (business-cycle phase changes,
  inflation/rate/unemployment drifting together) over 150+ simulated
  months without crashing.
- A real trade (`POST /trade/stocks`) moving the shared price by exactly
  the modeled amount, broadcast instantly to a second, purely-WebSocket
  client.
- A business hired up, advertised, and simulated monthly with real
  revenue/expense/profit numbers; a second business going genuinely
  bankrupt from bad economics and soft-closing with its history intact.
- A full marketplace order lifecycle: listing → order → coins held →
  seller accepts → produces → delivers → coins released to the seller →
  listing quantity decremented.
- A coin purchase verified (in dev-mode) end-to-end, including rejecting
  a replayed purchase token.
- The compiled `dist/` build (what the Docker image actually ships)
  booting and serving traffic identically to the dev server.

What is **not** verified: an actual DigitalOcean deployment (needs your
account/credentials), a real Google Play purchase (needs your Play Console
app), and real AlphaVantage/FRED data (needs your API keys) — the code
paths for all three are real and complete, just untriggered without those
credentials. See `DEPLOY.md`.

## API surface

All endpoints except `/health`, `/auth/register`, `/auth/login` require
`Authorization: Bearer <token>`.

| Method & path | What it does |
|---|---|
| `POST /auth/register` / `/auth/login` | Returns `{ token, player }` |
| `GET /player/me` | Full player state (cash, holdings, businesses, loans...) |
| `POST /player/lifestyle` | Change lifestyle tier |
| `GET /social/users/search?q=` | Find players by username |
| `POST /social/friends/request` / `/respond` | Friend requests |
| `GET /social/friends` | Your friends + pending requests |
| `GET /social/chat/:channel/history` | Last 50 messages (live chat is via socket `chat:send`/`chat:message`) |
| `GET /market/snapshot` | Current shared economy + all instruments |
| `GET /market/economy/history` | Last 60 months, for charts |
| `POST /trade/stocks` \| `/bonds` \| `/commodities` | `{ id, side, quantity }` — executes at current price with real-time impact |
| `POST /business` | Start a business: `{ type, name }` |
| `POST /business/:id/hire` \| `/fire` \| `/price` \| `/advertise` \| `/upgrade` \| `/loan` \| `/repay-loan` \| `/withdraw` \| `/inject` | Business actions |
| `DELETE /business/:id` | Liquidate and close |
| `GET /business/:id/history` | Monthly P&L |
| `GET /marketplace/listings` \| `/listings/mine` | Browse / manage listings |
| `POST /marketplace/listings` | Create a listing |
| `POST /marketplace/orders` | Buy from a listing (coins held) |
| `POST /marketplace/orders/:id/advance` | Seller moves an order forward |
| `POST /marketplace/orders/:id/cancel` | Refund before acceptance |
| `GET /wallet` | Coin balance + transaction history |
| `GET /billing/products` | Coin pack catalog |
| `POST /billing/verify-purchase` | Redeem a Play purchase token for coins |

### Socket.IO events (connect with `auth: { token }`)

- Server → client: `presence:update`, `chat:message`, `trade:executed`,
  `economy:update`, `market:update`, `business:bankrupted`,
  `marketplace:order_placed`, `marketplace:order_updated`.
- Client → server: `chat:send { channel, body }`, `chat:join channel`.

## Two currencies, on purpose

`cash`/`bankSavings` (in `player_state`) is the simulated in-game economy's
money — earned from jobs, investments, businesses. `walletCoins` is the
real-money-purchased marketplace currency. They never convert into each
other anywhere in this codebase, and coins never convert back into real
money — see `billing/products.ts` and the marketplace order flow.

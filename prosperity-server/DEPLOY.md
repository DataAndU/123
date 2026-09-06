# Deploying Prosperity Online to DigitalOcean App Platform

This backend needs to actually be reachable from your phone for real-time
multiplayer to work, which means it needs to run somewhere with a public
URL — DigitalOcean App Platform, which you chose, does exactly that. I
can't create the DigitalOcean account, Play Console app, or API keys for
you (those require your own credentials/billing), but everything below is
real, tested config — I built it, ran the compiled production build
locally, and ran the migration against a real Postgres database in this
environment before handing it to you.

## What you need before you start

1. A DigitalOcean account with billing set up.
2. This repository pushed to GitHub (it already is, on the
   `claude/life-economy-simulator` branch of `dataandu/123`) — App
   Platform deploys from a GitHub repo.
3. (Optional, can add later) An [AlphaVantage](https://www.alphavantage.co/support/#api-key)
   key for real stock quotes, and/or a [FRED](https://fred.stlouisfed.org/docs/api/api_key.html)
   key for real macro data.
4. (Optional, can add later) A Google Play Console app + service account,
   for real-money coin purchases — see "Enabling real purchases" below.

## Option A — the DigitalOcean web console (easiest)

1. Go to <https://cloud.digitalocean.com/apps> → **Create App**.
2. Choose **GitHub** as the source, pick the `dataandu/123` repo and the
   `claude/life-economy-simulator` branch.
3. When it asks for the source directory, set it to `prosperity-server`.
4. It should detect the `Dockerfile` automatically. If it offers to add a
   database, add a **Dev Database (PostgreSQL)** — this becomes the `db`
   component referenced in `.do/app.yaml`.
5. Instead of clicking through every field by hand, use **"Edit Your App
   Spec"** in the console and paste the contents of `.do/app.yaml` from
   this folder — it already has the right build settings, health check,
   and a pre-deploy job that runs database migrations automatically.
6. Fill in the real values for the two required secrets it will prompt for:
   - `JWT_SECRET` — generate one with `openssl rand -hex 32`.
   - Leave `ALPHAVANTAGE_API_KEY` / `FRED_API_KEY` / `GOOGLE_SERVICE_ACCOUNT_JSON`
     blank for now — the app runs fully simulated without them.
7. Click **Create Resources**. First deploy takes a few minutes (it builds
   the Docker image, provisions Postgres, runs the migration job, then
   starts the service).
8. Once it's live, note the app's URL (something like
   `https://prosperity-server-xxxxx.ondigitalocean.app`) and check
   `https://<that-url>/health` returns `{"ok":true,...}`.

## Option B — the `doctl` CLI

```bash
# One-time setup
brew install doctl   # or see https://docs.digitalocean.com/reference/doctl/how-to/install/
doctl auth init      # paste a DigitalOcean API token from the console

# From the prosperity-server/ directory:
doctl apps create --spec .do/app.yaml

# Later, after editing .do/app.yaml or pushing new commits:
doctl apps update <app-id> --spec .do/app.yaml

# Watch the deploy:
doctl apps list
doctl apps get <app-id>
```

`doctl apps create` will prompt you to fill in the `SECRET`-typed env vars
(`JWT_SECRET`, etc.) interactively, or you can set them with
`doctl apps update` afterward.

## After it's deployed

Point the Android app at your live URL: in `prosperity/local.properties`-adjacent
build config (see `app/build.gradle.kts` → `BuildConfig.API_BASE_URL` in the
Android project), replace the local `http://10.0.2.2:4000` default with your
App Platform URL, using `https://` and `wss://` (Socket.IO negotiates this
automatically over the same host).

## Turning on real-world data (optional)

Set these as **Secrets** on the `api` service in the app spec (web console:
Settings → App-Level Environment Variables, or edit `.do/app.yaml` and
redeploy):

- `MARKET_DATA_PROVIDER=alphavantage` + `ALPHAVANTAGE_API_KEY=<your key>` —
  the 6 stocks start mirroring their real tickers' prices (see
  `src/market/model.ts` for the ticker mapping).
- `FRED_API_KEY=<your key>` — the shared economy's unemployment, inflation,
  and interest rate get overwritten each tick with the real current US
  figures instead of the pure simulation.

Both are additive — turn one on without the other, or both, or neither.

## Enabling real-money purchases (optional, more involved)

The coin-purchase endpoint (`POST /billing/verify-purchase`) is fully
implemented against the real Google Play Developer API, but three things
only you can do, since they require your own Google account and app:

1. Create the app in the [Google Play Console](https://play.google.com/console)
   with package name `com.prosperity.game` (or update
   `GOOGLE_PLAY_PACKAGE_NAME` to match whatever you use), and define the
   in-app products with IDs matching `src/billing/products.ts`
   (`coins_small`, `coins_medium`, `coins_large`, `coins_mega`).
2. Create a Google Cloud service account, grant it **Viewer** access to the
   Play Console app (Play Console → Users and permissions), and download
   its JSON key.
3. Set `GOOGLE_SERVICE_ACCOUNT_JSON` (the full JSON key, as a single-line
   string secret) on the `api` service, and make sure `BILLING_DEV_MODE`
   is `false` (it defaults to false — never turn it on in production).

Until you do this, `/billing/verify-purchase` returns a clear "not
configured" error instead of silently faking a purchase — it will never
pretend to succeed without real verification.

## Scaling the world clock

`TICK_INTERVAL_MS` controls how often the shared economy advances one
in-game month — the default (`300000` = 5 minutes) was picked for a
build/demo pace. For a longer-running live game you'll likely want this
much larger (e.g. `86400000` for one month per real day). Changing it only
requires updating the env var and redeploying — no code or migration
needed.

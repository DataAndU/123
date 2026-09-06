# Prosperity — Life & Economy Simulator

A fully offline Android economics/business/investing simulation game. No
backend, no cloud save, no `INTERNET` permission — everything runs and
persists locally on the device.

## What's actually simulated (not just flavor text)

- **Macro economy** (`engine/economy`): a four-phase business cycle
  (expansion → peak → recession → trough) drives GDP growth; unemployment
  responds to growth surprises (a simplified Okun's Law); inflation responds
  to the output gap and money supply with mean reversion to a target; the
  central bank sets interest rates with a Taylor-rule-like reaction function
  to inflation and unemployment gaps. A cost-of-living index compounds with
  inflation and feeds directly into the player's lifestyle expenses.
- **Markets** (`engine/market`): six stocks across sectors with different
  betas/cyclicality, three bonds (priced with a duration-based model that
  pulls to par as they approach maturity, with real coupon income and
  maturity payouts), gold and oil (safe-haven vs. cyclical behavior), a
  housing price index driven mainly by interest rates, and a currency pair
  with a simple interest-rate-differential drift.
- **Businesses** (`engine/business`): 7 types (restaurant, grocery store,
  tech startup, manufacturing, transport, construction, online business),
  each with real revenue = f(employees, productivity/level, economy phase,
  consumer confidence, reputation, price point, competition, advertising),
  real expenses (salaries that track inflation, rent, inventory cost, loan
  interest), real corporate tax, reputation that drifts based on your
  pricing and investment, and competitive pressure that grows over time
  unless you defend against it.
- **Personal finance** (`engine/player`): a job ladder gated by education
  and skills, real loan amortization (with interest/principal split every
  month), savings interest, dividend/coupon/rental income, personal income
  tax, and happiness/health that respond to your actual income-to-expense
  ratio and lifestyle choices — not just flat numbers.
- **28 events** (`engine/event`), each with 2–3 real choices and a plain-English
  educational note — recessions, rate hikes/cuts, inflation spikes, stimulus,
  tax policy, stock crashes/rallies, housing booms/crashes, oil shocks,
  supply shortages, new competitors, tech breakthroughs, job offers,
  medical/car expenses, hot stock tips, windfalls, natural disasters, a
  pandemic-style shock, and more. Every option produces a mechanically real
  outcome — nothing is cosmetic.

## Game modes & progression

Career, Free Economy (sandbox, no bankruptcy), Business Tycoon (starts with
seed debt), Investor (more capital, weaker jobs), Crisis Survival (starts
mid-recession, survive N months), and Challenge (hit a net-worth target
before a deadline) — all built on the same engine via `Scenarios.kt`, plus
4 difficulty levels that scale starting cash, expenses, event severity, and
bankruptcy leniency. Progression runs Beginner → Skilled → Entrepreneur →
Seasoned Investor → Business Tycoon → Economic Master by net worth, with 12
achievements and a local leaderboard of your own past runs.

## Architecture

```
engine/economy/   Macro simulation (business cycle, inflation, rates, tax)
engine/market/    Stocks, bonds, commodities, real estate, currency pricing
engine/business/  7 business types + their monthly P&L simulation
engine/player/    Jobs, education, skills, loans, savings, net worth, actions
engine/event/     28 event definitions + weighted selection + educational notes
engine/game/      Orchestrates one monthly tick across every engine above,
                  scenarios, achievements, progression, leaderboard model
save/             Local JSON save/load (slots, autosave, export/import)
ui/               Compose screens, a ViewModel, and two from-scratch
                  Canvas chart composables (no charting library dependency)
```

Every engine package depends only on the ones below it in that list (event
depends on economy/market/player; game depends on all of them) — nothing
depends on `ui`, so the simulation is fully testable without Android.

## Time model

Progression is by **month**, advanced with an explicit "Advance to Next
Month" button rather than a real-time clock — a deliberate choice for a
turn-based economic strategy game (in the spirit of tycoon/management sims):
it keeps every tick deterministic and reviewable, and avoids any
energy-system or forced-wait mechanic.

## Save system

All saves are plain JSON in the app's private storage
(`context.filesDir/saves/`): 5 manual slots, one autosave (written after
every month and every event resolution), and export/import through the
Storage Access Framework document picker — no storage permission needed on
API 26+, and the exported file is a normal `.json` you can back up anywhere.

## Building

```
./gradlew assembleDebug     # ~16MB, unminified
./gradlew assembleRelease   # R8-minified; unsigned — sign with your own key to distribute
```

Minimum SDK 26, target/compile SDK 34. No Android SDK is bundled in this
repo — install one via Android Studio or `sdkmanager` first.

## Known limitations

- **Compiles and runs the build cleanly** (verified with a real Android SDK
  in this environment — `assembleDebug` succeeds with no errors), but there
  was no emulator/device available to click through the UI. Install the
  APK and play a few months before treating this as fully verified.
- **One instrument universe, not a full exchange.** 6 stocks, 3 bonds, gold
  and oil — enough to teach diversification and sector behavior, not a
  simulation of thousands of tickers.
- **A single foreign currency pair**, not a multi-currency forex market.
- **Housing is one national index**, not per-region pricing.
- **Real-time/daily granularity was intentionally left out** in favor of a
  monthly tick — see *Time model* above.
- **The event catalog is static** (28 hand-written events); it doesn't
  generate novel events procedurally.

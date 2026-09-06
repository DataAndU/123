import { Server } from 'socket.io';
import { pool } from '../db/pool';
import { getEconomyState, saveEconomyState } from '../economy/model';
import { advanceEconomy } from '../economy/simulator';
import { advanceMarkets } from '../market/simulator';
import { getMarketSnapshot } from '../market/model';
import { fetchLiveMacroIndicators, fetchLiveStockQuotes, isLiveDataConfigured, isLiveMacroConfigured } from '../market/liveProvider';
import { settleMaturedBonds } from './bondSettlement';
import { advanceBusinessMonth } from '../business/simulator';
import { closeBusiness, getAllBusinesses, saveBusinessAfterTick } from '../business/repository';
import { applyFinanceTickForAllPlayers, refreshAllNetWorths } from '../player/financeTick';

const DEFAULT_TICK_MS = 5 * 60 * 1000; // one in-game month per real 5 minutes by default

let tickHandle: NodeJS.Timeout | null = null;

export function startTickScheduler(io: Server) {
  const intervalMs = Number(process.env.TICK_INTERVAL_MS) || DEFAULT_TICK_MS;
  console.log(`Tick scheduler running every ${intervalMs}ms (one in-game month per tick)`);
  tickHandle = setInterval(() => {
    runTick(io).catch((err) => console.error('Tick failed', err));
  }, intervalMs);
}

export function stopTickScheduler() {
  if (tickHandle) clearInterval(tickHandle);
}

export async function runTick(io: Server) {
  const before = await getEconomyState();
  const previousRate = before.interestRate;

  let after = advanceEconomy(before);

  if (isLiveMacroConfigured()) {
    const live = await fetchLiveMacroIndicators(process.env.FRED_API_KEY!);
    if (live.unemploymentRate !== undefined) after.unemploymentRate = live.unemploymentRate;
    if (live.fedFundsRatePercent !== undefined) after.interestRate = live.fedFundsRatePercent;
    if (live.inflationYoYPercent !== undefined) after.inflationRate = live.inflationYoYPercent;
    after = { ...after, dataSource: 'live' };
  }

  await saveEconomyState(after);

  const maturedBonds = await advanceMarkets(after, previousRate);
  await settleMaturedBonds(maturedBonds);

  if (isLiveDataConfigured()) {
    const snapshot = await getMarketSnapshot();
    const liveQuotes = await fetchLiveStockQuotes(
      snapshot.stocks.map((s) => ({ instrumentId: s.id, realTicker: s.realTicker })),
      process.env.ALPHAVANTAGE_API_KEY!
    );
    for (const [stockId, price] of Object.entries(liveQuotes)) {
      await pool.query('UPDATE stocks SET price = $2 WHERE id = $1', [stockId, price]);
    }
  }

  const closedBusinessIds: string[] = [];
  const businesses = await getAllBusinesses();
  for (const business of businesses) {
    const result = advanceBusinessMonth(business, after);
    // Record the final month's numbers even on bankruptcy, so the player can see what killed it.
    await saveBusinessAfterTick(result.business, after.month, result.revenue, result.expenses, result.profit);
    if (result.isBankrupt) {
      closedBusinessIds.push(business.id);
      await closeBusiness(business.id, 'closed_bankrupt');
    }
  }

  const snapshot = await getMarketSnapshot();
  await applyFinanceTickForAllPlayers(after, snapshot);
  await refreshAllNetWorths(snapshot);

  io.emit('economy:update', after);
  io.emit('market:update', snapshot);
  if (closedBusinessIds.length > 0) io.emit('business:bankrupted', { businessIds: closedBusinessIds });
  io.emit('tick:complete', { month: after.month });
  console.log(`Tick complete: month ${after.month}, phase ${after.phase}, source=${after.dataSource}, businesses advanced=${businesses.length}, closed=${closedBusinessIds.length}`);
}

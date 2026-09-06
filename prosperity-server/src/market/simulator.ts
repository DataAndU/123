import { pool } from '../db/pool';
import { EconomyState } from '../economy/model';
import { getMarketSnapshot, Sector } from './model';

function gaussian(mean: number, stdDev: number): number {
  let u1 = 0;
  let u2 = 0;
  do {
    u1 = Math.random();
    u2 = Math.random();
  } while (u1 <= Number.EPSILON);
  const z0 = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
  return mean + z0 * stdDev;
}

const SECTOR_CYCLICALITY: Record<Sector, number> = {
  TECH: 1.3,
  INDUSTRIAL: 1.1,
  CONSUMER: 0.6,
  FINANCE: 1.0,
  ENERGY: 0.4,
  HEALTHCARE: 0.2
};

function phaseDrift(phase: EconomyState['phase']): number {
  switch (phase) {
    case 'EXPANSION':
      return 0.012;
    case 'PEAK':
      return 0.002;
    case 'RECESSION':
      return -0.02;
    case 'TROUGH':
      return -0.004;
  }
}

function sectorTilt(sector: Sector, phase: EconomyState['phase']): number {
  const phaseSign: Record<EconomyState['phase'], number> = { EXPANSION: 0.006, PEAK: 0, RECESSION: -0.012, TROUGH: -0.002 };
  return phaseSign[phase] * SECTOR_CYCLICALITY[sector];
}

export interface MaturedBond {
  bondId: string;
  faceValuePerUnit: number;
}

/**
 * Advances every shared instrument by one month using the same model as
 * the offline single-player build, PLUS real-time price impact already
 * baked into `stock.price`/`bond.price`/`commodity.price` from player
 * trades executed since the last tick (see trading.ts) — so the monthly
 * drift compounds on top of whatever real order flow already moved.
 */
export async function advanceMarkets(economy: EconomyState, previousRate: number): Promise<MaturedBond[]> {
  const snapshot = await getMarketSnapshot();

  for (const stock of snapshot.stocks) {
    const drift = phaseDrift(economy.phase) * stock.beta + sectorTilt(stock.sector, economy.phase);
    const rateChange = economy.interestRate - previousRate;
    const monthlyReturn = drift - rateChange * 0.02 * stock.beta + gaussian(0, stock.volatility);
    const newPrice = Math.max(0.05, stock.price * (1 + monthlyReturn));
    await pool.query('UPDATE stocks SET price = $2 WHERE id = $1', [stock.id, newPrice]);
    await pool.query('INSERT INTO stock_price_history (stock_id, month, price) VALUES ($1,$2,$3) ON CONFLICT DO NOTHING', [stock.id, economy.month, newPrice]);
  }

  const maturedBonds: MaturedBond[] = [];
  for (const bond of snapshot.bonds) {
    if (bond.monthsRemaining <= 1) {
      maturedBonds.push({ bondId: bond.id, faceValuePerUnit: bond.faceValue });
      const newCoupon = economy.interestRate + bond.riskPremium;
      await pool.query(
        'UPDATE bonds SET coupon_rate = $2, months_remaining = $3, price = face_value WHERE id = $1',
        [bond.id, newCoupon, bond.originalTermMonths]
      );
    } else {
      const monthsLeft = bond.monthsRemaining - 1;
      const years = monthsLeft / 12;
      const rateGap = (bond.couponRate - economy.interestRate) / 100;
      const priceFactor = Math.min(1.6, Math.max(0.5, 1 + rateGap * years * 0.55));
      const newPrice = bond.faceValue * priceFactor;
      await pool.query('UPDATE bonds SET months_remaining = $2, price = $3 WHERE id = $1', [bond.id, monthsLeft, newPrice]);
    }
  }

  for (const commodity of snapshot.commodities) {
    const recessionBoost = economy.phase === 'RECESSION' || economy.phase === 'TROUGH' ? 1.0 : -0.3;
    const inflationBoost = (economy.inflationRate - 2.0) * 0.01;
    const monthlyReturn = commodity.safeHavenFactor * (recessionBoost * 0.01 + inflationBoost) + gaussian(0, commodity.volatility);
    const newPrice = Math.max(0.05, commodity.price * (1 + monthlyReturn));
    await pool.query('UPDATE commodities SET price = $2 WHERE id = $1', [commodity.id, newPrice]);
    await pool.query('INSERT INTO commodity_price_history (commodity_id, month, price) VALUES ($1,$2,$3) ON CONFLICT DO NOTHING', [commodity.id, economy.month, newPrice]);
  }

  const rateChange = economy.interestRate - previousRate;
  const housingReturn = -0.03 * rateChange + (economy.gdpGrowthRate - 2.2) * 0.003 + gaussian(0, 0.01);
  const newHousing = Math.max(10, snapshot.housingPriceIndex * (1 + housingReturn));
  await pool.query('UPDATE housing_market SET price_index = $1 WHERE id = 1', [newHousing]);
  await pool.query('INSERT INTO housing_history (month, price_index) VALUES ($1,$2) ON CONFLICT DO NOTHING', [economy.month, newHousing]);

  const fxReturn = (economy.interestRate - 3.0) * 0.004 + gaussian(0, 0.012);
  const newFx = Math.min(5, Math.max(0.2, snapshot.exchangeRate * (1 + fxReturn)));
  await pool.query('UPDATE currency_market SET exchange_rate = $1 WHERE id = 1', [newFx]);
  await pool.query('INSERT INTO currency_history (month, exchange_rate) VALUES ($1,$2) ON CONFLICT DO NOTHING', [economy.month, newFx]);

  return maturedBonds;
}

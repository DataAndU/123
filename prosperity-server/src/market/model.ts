import { pool } from '../db/pool';

export type Sector = 'TECH' | 'ENERGY' | 'CONSUMER' | 'FINANCE' | 'INDUSTRIAL' | 'HEALTHCARE';

export interface Stock {
  id: string;
  name: string;
  sector: Sector;
  realTicker: string | null;
  price: number;
  beta: number;
  volatility: number;
  dividendYieldAnnual: number;
}

export interface Bond {
  id: string;
  name: string;
  issuer: 'GOVERNMENT' | 'CORPORATE';
  faceValue: number;
  couponRate: number;
  originalTermMonths: number;
  monthsRemaining: number;
  price: number;
  riskPremium: number;
}

export interface Commodity {
  id: string;
  name: string;
  realSymbol: string | null;
  price: number;
  safeHavenFactor: number;
  volatility: number;
}

export interface MarketSnapshot {
  stocks: Stock[];
  bonds: Bond[];
  commodities: Commodity[];
  housingPriceIndex: number;
  exchangeRate: number;
}

const SEED_STOCKS: Omit<Stock, 'price'>[] & { price: number }[] = [
  { id: 'STK_TCH', name: 'Nimbus Cloud Systems', sector: 'TECH', realTicker: 'MSFT', price: 42, beta: 1.5, volatility: 0.09, dividendYieldAnnual: 0 },
  { id: 'STK_NRG', name: 'Continental Energy Co.', sector: 'ENERGY', realTicker: 'XOM', price: 65, beta: 0.8, volatility: 0.07, dividendYieldAnnual: 4.5 },
  { id: 'STK_CNS', name: 'Harbor Retail Group', sector: 'CONSUMER', realTicker: 'WMT', price: 28, beta: 0.7, volatility: 0.05, dividendYieldAnnual: 2.0 },
  { id: 'STK_FIN', name: 'Meridian Bank Holdings', sector: 'FINANCE', realTicker: 'JPM', price: 55, beta: 1.2, volatility: 0.08, dividendYieldAnnual: 3.2 },
  { id: 'STK_IND', name: 'Ironclad Industrial', sector: 'INDUSTRIAL', realTicker: 'CAT', price: 37, beta: 1.1, volatility: 0.07, dividendYieldAnnual: 2.5 },
  { id: 'STK_HLT', name: 'Willowbrook Health', sector: 'HEALTHCARE', realTicker: 'JNJ', price: 80, beta: 0.5, volatility: 0.04, dividendYieldAnnual: 1.8 }
];

const SEED_BONDS = [
  { id: 'BND_GOV2', name: '2-Year Treasury Note', issuer: 'GOVERNMENT' as const, faceValue: 1000, couponRate: 3.0, originalTermMonths: 24, monthsRemaining: 24, price: 1000, riskPremium: 0 },
  { id: 'BND_GOV10', name: '10-Year Treasury Bond', issuer: 'GOVERNMENT' as const, faceValue: 1000, couponRate: 3.6, originalTermMonths: 120, monthsRemaining: 120, price: 1000, riskPremium: 0.6 },
  { id: 'BND_CORP', name: 'Meridian Corporate Bond', issuer: 'CORPORATE' as const, faceValue: 1000, couponRate: 5.5, originalTermMonths: 60, monthsRemaining: 60, price: 1000, riskPremium: 2.5 }
];

const SEED_COMMODITIES = [
  { id: 'COM_GOLD', name: 'Gold (oz)', realSymbol: 'XAUUSD', price: 1900, safeHavenFactor: 1.0, volatility: 0.03 },
  { id: 'COM_OIL', name: 'Crude Oil (barrel)', realSymbol: 'WTI', price: 75, safeHavenFactor: -0.4, volatility: 0.08 }
];

export async function seedMarketsIfEmpty(): Promise<void> {
  const { rows } = await pool.query('SELECT COUNT(*)::int AS n FROM stocks');
  if (rows[0].n > 0) return;

  for (const s of SEED_STOCKS) {
    await pool.query(
      'INSERT INTO stocks (id, name, sector, real_ticker, price, beta, volatility, dividend_yield_annual) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)',
      [s.id, s.name, s.sector, s.realTicker, s.price, s.beta, s.volatility, s.dividendYieldAnnual]
    );
  }
  for (const b of SEED_BONDS) {
    await pool.query(
      'INSERT INTO bonds (id, name, issuer, face_value, coupon_rate, original_term_months, months_remaining, price, risk_premium) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)',
      [b.id, b.name, b.issuer, b.faceValue, b.couponRate, b.originalTermMonths, b.monthsRemaining, b.price, b.riskPremium]
    );
  }
  for (const c of SEED_COMMODITIES) {
    await pool.query(
      'INSERT INTO commodities (id, name, real_symbol, price, safe_haven_factor, volatility) VALUES ($1,$2,$3,$4,$5,$6)',
      [c.id, c.name, c.realSymbol, c.price, c.safeHavenFactor, c.volatility]
    );
  }
}

export async function getMarketSnapshot(): Promise<MarketSnapshot> {
  const [stocksRes, bondsRes, commoditiesRes, housingRes, currencyRes] = await Promise.all([
    pool.query('SELECT * FROM stocks ORDER BY id'),
    pool.query('SELECT * FROM bonds ORDER BY id'),
    pool.query('SELECT * FROM commodities ORDER BY id'),
    pool.query('SELECT price_index FROM housing_market WHERE id = 1'),
    pool.query('SELECT exchange_rate FROM currency_market WHERE id = 1')
  ]);
  return {
    stocks: stocksRes.rows.map(rowToStock),
    bonds: bondsRes.rows.map(rowToBond),
    commodities: commoditiesRes.rows.map(rowToCommodity),
    housingPriceIndex: Number(housingRes.rows[0].price_index),
    exchangeRate: Number(currencyRes.rows[0].exchange_rate)
  };
}

function rowToStock(r: any): Stock {
  return { id: r.id, name: r.name, sector: r.sector, realTicker: r.real_ticker, price: Number(r.price), beta: Number(r.beta), volatility: Number(r.volatility), dividendYieldAnnual: Number(r.dividend_yield_annual) };
}
function rowToBond(r: any): Bond {
  return { id: r.id, name: r.name, issuer: r.issuer, faceValue: Number(r.face_value), couponRate: Number(r.coupon_rate), originalTermMonths: r.original_term_months, monthsRemaining: r.months_remaining, price: Number(r.price), riskPremium: Number(r.risk_premium) };
}
function rowToCommodity(r: any): Commodity {
  return { id: r.id, name: r.name, realSymbol: r.real_symbol, price: Number(r.price), safeHavenFactor: Number(r.safe_haven_factor), volatility: Number(r.volatility) };
}

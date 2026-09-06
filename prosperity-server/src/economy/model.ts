import { pool } from '../db/pool';

export type BusinessCyclePhase = 'EXPANSION' | 'PEAK' | 'RECESSION' | 'TROUGH';

export interface EconomyState {
  month: number;
  phase: BusinessCyclePhase;
  phaseMonthsElapsed: number;
  gdpGrowthRate: number;
  inflationRate: number;
  unemploymentRate: number;
  interestRate: number;
  consumerConfidence: number;
  moneySupplyGrowth: number;
  priceLevelIndex: number;
  incomeTaxRate: number;
  corporateTaxSurcharge: number;
  dataSource: 'simulated' | 'live';
}

export const INFLATION_TARGET = 2.0;
export const NATURAL_UNEMPLOYMENT = 4.5;
export const NATURAL_GROWTH = 2.2;

export async function getEconomyState(): Promise<EconomyState> {
  const { rows } = await pool.query('SELECT * FROM economy_state WHERE id = 1');
  const r = rows[0];
  return {
    month: r.month,
    phase: r.phase,
    phaseMonthsElapsed: r.phase_months_elapsed,
    gdpGrowthRate: Number(r.gdp_growth_rate),
    inflationRate: Number(r.inflation_rate),
    unemploymentRate: Number(r.unemployment_rate),
    interestRate: Number(r.interest_rate),
    consumerConfidence: Number(r.consumer_confidence),
    moneySupplyGrowth: Number(r.money_supply_growth),
    priceLevelIndex: Number(r.price_level_index),
    incomeTaxRate: Number(r.income_tax_rate),
    corporateTaxSurcharge: Number(r.corporate_tax_surcharge),
    dataSource: r.data_source
  };
}

export async function saveEconomyState(state: EconomyState): Promise<void> {
  await pool.query(
    `UPDATE economy_state SET
      month = $1, phase = $2, phase_months_elapsed = $3, gdp_growth_rate = $4,
      inflation_rate = $5, unemployment_rate = $6, interest_rate = $7,
      consumer_confidence = $8, money_supply_growth = $9, price_level_index = $10,
      income_tax_rate = $11, corporate_tax_surcharge = $12, data_source = $13, updated_at = now()
     WHERE id = 1`,
    [
      state.month,
      state.phase,
      state.phaseMonthsElapsed,
      state.gdpGrowthRate,
      state.inflationRate,
      state.unemploymentRate,
      state.interestRate,
      state.consumerConfidence,
      state.moneySupplyGrowth,
      state.priceLevelIndex,
      state.incomeTaxRate,
      state.corporateTaxSurcharge,
      state.dataSource
    ]
  );
  await pool.query(
    `INSERT INTO economy_history (month, gdp_growth_rate, inflation_rate, unemployment_rate, interest_rate, consumer_confidence)
     VALUES ($1, $2, $3, $4, $5, $6) ON CONFLICT (month) DO NOTHING`,
    [state.month, state.gdpGrowthRate, state.inflationRate, state.unemploymentRate, state.interestRate, state.consumerConfidence]
  );
}

export async function getEconomyHistory(limit = 60) {
  const { rows } = await pool.query(
    'SELECT month, gdp_growth_rate, inflation_rate, unemployment_rate, interest_rate, consumer_confidence FROM economy_history ORDER BY month DESC LIMIT $1',
    [limit]
  );
  return rows.reverse().map((r) => ({
    month: r.month,
    gdpGrowthRate: Number(r.gdp_growth_rate),
    inflationRate: Number(r.inflation_rate),
    unemploymentRate: Number(r.unemployment_rate),
    interestRate: Number(r.interest_rate),
    consumerConfidence: Number(r.consumer_confidence)
  }));
}

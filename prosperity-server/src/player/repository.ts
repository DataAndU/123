import { pool } from '../db/pool';

export interface Loan {
  id: string;
  type: string;
  principalRemaining: number;
  annualRate: number;
  monthlyPayment: number;
  originalPrincipal: number;
  termMonthsRemaining: number;
}

export interface PropertyHolding {
  id: string;
  purchasePrice: number;
  purchaseHousingIndex: number;
  mortgageBalance: number;
  mortgageRate: number;
  monthlyRentIncome: number;
}

export interface BusinessRow {
  id: string;
  type: string;
  name: string;
  level: number;
  employees: number;
  pricePointMultiplier: number;
  reputation: number;
  advertisingBudgetMonthly: number;
  competitionPressure: number;
  businessCash: number;
  loanBalance: number;
  loanInterestRate: number;
}

export interface PlayerView {
  userId: string;
  username: string;
  displayName: string;
  cash: number;
  bankSavings: number;
  walletCoins: number;
  currentJobId: string | null;
  jobMonthsHeld: number;
  educationLevel: string;
  educationInProgressId: string | null;
  educationMonthsRemaining: number;
  skills: Record<string, number>;
  lifestyleTier: string;
  happiness: number;
  health: number;
  reputation: number;
  foreignCurrencyHoldings: number;
  achievementsUnlocked: string[];
  netWorth: number;
  loans: Loan[];
  properties: PropertyHolding[];
  businesses: BusinessRow[];
  stockHoldings: Record<string, number>;
  bondHoldings: Record<string, number>;
  commodityHoldings: Record<string, number>;
}

export async function createPlayerState(userId: string): Promise<void> {
  await pool.query('INSERT INTO player_state (user_id) VALUES ($1) ON CONFLICT DO NOTHING', [userId]);
}

export async function getFullPlayerView(userId: string): Promise<PlayerView | null> {
  const userRes = await pool.query('SELECT username, display_name FROM users WHERE id = $1', [userId]);
  if (userRes.rows.length === 0) return null;

  const stateRes = await pool.query('SELECT * FROM player_state WHERE user_id = $1', [userId]);
  if (stateRes.rows.length === 0) return null;
  const s = stateRes.rows[0];

  const [loansRes, propsRes, bizRes, stockRes, bondRes, commodityRes] = await Promise.all([
    pool.query('SELECT * FROM loans WHERE user_id = $1 ORDER BY created_at', [userId]),
    pool.query('SELECT * FROM properties WHERE user_id = $1 ORDER BY created_at', [userId]),
    pool.query("SELECT * FROM businesses WHERE user_id = $1 AND status = 'active' ORDER BY created_at", [userId]),
    pool.query('SELECT stock_id, shares FROM stock_holdings WHERE user_id = $1', [userId]),
    pool.query('SELECT bond_id, units FROM bond_holdings WHERE user_id = $1', [userId]),
    pool.query('SELECT commodity_id, units FROM commodity_holdings WHERE user_id = $1', [userId])
  ]);

  return {
    userId,
    username: userRes.rows[0].username,
    displayName: userRes.rows[0].display_name,
    cash: Number(s.cash),
    bankSavings: Number(s.bank_savings),
    walletCoins: Number(s.wallet_coins),
    currentJobId: s.current_job_id,
    jobMonthsHeld: s.job_months_held,
    educationLevel: s.education_level,
    educationInProgressId: s.education_in_progress_id,
    educationMonthsRemaining: s.education_months_remaining,
    skills: s.skills,
    lifestyleTier: s.lifestyle_tier,
    happiness: Number(s.happiness),
    health: Number(s.health),
    reputation: Number(s.reputation),
    foreignCurrencyHoldings: Number(s.foreign_currency_holdings),
    achievementsUnlocked: s.achievements_unlocked,
    netWorth: Number(s.net_worth),
    loans: loansRes.rows.map(rowToLoan),
    properties: propsRes.rows.map(rowToProperty),
    businesses: bizRes.rows.map(rowToBusiness),
    stockHoldings: Object.fromEntries(stockRes.rows.map((r) => [r.stock_id, r.shares])),
    bondHoldings: Object.fromEntries(bondRes.rows.map((r) => [r.bond_id, r.units])),
    commodityHoldings: Object.fromEntries(commodityRes.rows.map((r) => [r.commodity_id, Number(r.units)]))
  };
}

function rowToLoan(r: any): Loan {
  return {
    id: r.id,
    type: r.type,
    principalRemaining: Number(r.principal_remaining),
    annualRate: Number(r.annual_rate),
    monthlyPayment: Number(r.monthly_payment),
    originalPrincipal: Number(r.original_principal),
    termMonthsRemaining: r.term_months_remaining
  };
}

function rowToProperty(r: any): PropertyHolding {
  return {
    id: r.id,
    purchasePrice: Number(r.purchase_price),
    purchaseHousingIndex: Number(r.purchase_housing_index),
    mortgageBalance: Number(r.mortgage_balance),
    mortgageRate: Number(r.mortgage_rate),
    monthlyRentIncome: Number(r.monthly_rent_income)
  };
}

export function rowToBusiness(r: any): BusinessRow {
  return {
    id: r.id,
    type: r.type,
    name: r.name,
    level: r.level,
    employees: r.employees,
    pricePointMultiplier: Number(r.price_point_multiplier),
    reputation: Number(r.reputation),
    advertisingBudgetMonthly: Number(r.advertising_budget_monthly),
    competitionPressure: Number(r.competition_pressure),
    businessCash: Number(r.business_cash),
    loanBalance: Number(r.loan_balance),
    loanInterestRate: Number(r.loan_interest_rate)
  };
}

export async function updatePlayerCash(userId: string, delta: number): Promise<void> {
  await pool.query('UPDATE player_state SET cash = cash + $2, updated_at = now() WHERE user_id = $1', [userId, delta]);
}

export async function setPlayerFields(userId: string, fields: Record<string, unknown>): Promise<void> {
  const keys = Object.keys(fields);
  if (keys.length === 0) return;
  const setClauses = keys.map((k, i) => `${k} = $${i + 2}`).join(', ');
  await pool.query(`UPDATE player_state SET ${setClauses}, updated_at = now() WHERE user_id = $1`, [userId, ...keys.map((k) => fields[k])]);
}

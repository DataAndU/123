import { pool } from '../db/pool';
import { EconomyState } from '../economy/model';
import { MarketSnapshot } from '../market/model';
import { findEducationProgram, findJob, LIFESTYLE_COSTS, LifestyleTier } from './catalog';

function clamp(v: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, v));
}

const LIFESTYLE_ORDER: LifestyleTier[] = ['SPARTAN', 'MODEST', 'COMFORTABLE', 'LUXURY', 'ELITE'];

/**
 * The monthly personal-finance tick every player gets, regardless of
 * whether they own a business: salary (net of income tax), investment
 * income (dividends/coupons/rent), savings interest, lifestyle expenses
 * (scaled by the shared cost-of-living index), loan/mortgage amortization,
 * education progress, and happiness/health drift. Same model as the
 * offline game's FinanceEngine.kt, now applied server-side to everyone
 * each tick.
 */
export async function applyFinanceTickForAllPlayers(economy: EconomyState, markets: MarketSnapshot): Promise<void> {
  const { rows: users } = await pool.query('SELECT user_id FROM player_state');
  for (const { user_id: userId } of users) {
    await applyFinanceTickForPlayer(userId, economy, markets);
  }
}

async function applyFinanceTickForPlayer(userId: string, economy: EconomyState, markets: MarketSnapshot): Promise<void> {
  const stateRes = await pool.query('SELECT * FROM player_state WHERE user_id = $1', [userId]);
  if (stateRes.rows.length === 0) return;
  const s = stateRes.rows[0];

  const job = findJob(s.current_job_id);
  const tenureBonus = job ? 1 + Math.min(0.5, s.job_months_held * 0.004) : 1;
  const grossSalary = (job?.baseSalary ?? 0) * tenureBonus;
  const salary = grossSalary * (1 - economy.incomeTaxRate / 100);

  const [stockHoldings, bondHoldings, properties, loans] = await Promise.all([
    pool.query('SELECT stock_id, shares FROM stock_holdings WHERE user_id = $1', [userId]),
    pool.query('SELECT bond_id, units FROM bond_holdings WHERE user_id = $1', [userId]),
    pool.query('SELECT * FROM properties WHERE user_id = $1', [userId]),
    pool.query('SELECT * FROM loans WHERE user_id = $1', [userId])
  ]);

  let dividends = 0;
  for (const h of stockHoldings.rows) {
    const stock = markets.stocks.find((x) => x.id === h.stock_id);
    if (stock) dividends += (stock.price * h.shares * stock.dividendYieldAnnual) / 100 / 12;
  }
  let coupons = 0;
  for (const h of bondHoldings.rows) {
    const bond = markets.bonds.find((x) => x.id === h.bond_id);
    if (bond) coupons += (bond.faceValue * h.units * bond.couponRate) / 100 / 12;
  }
  const rent = properties.rows.reduce((sum, p) => sum + Number(p.monthly_rent_income), 0);
  const savingsInterest = (Number(s.bank_savings) * (economy.interestRate * 0.65)) / 100 / 12;
  const income = salary + dividends + coupons + rent + savingsInterest;

  const lifestyleCost = LIFESTYLE_COSTS[s.lifestyle_tier as LifestyleTier].monthlyCost * (economy.priceLevelIndex / 100);
  const eduProgram = findEducationProgram(s.education_in_progress_id);
  const tuition = eduProgram ? eduProgram.tuitionCost / eduProgram.durationMonths : 0;

  let loanPayments = 0;
  for (const loan of loans.rows) {
    const monthlyRate = Number(loan.annual_rate) / 100 / 12;
    const interestPortion = Number(loan.principal_remaining) * monthlyRate;
    const principalPortion = Math.min(Number(loan.monthly_payment) - interestPortion, Number(loan.principal_remaining));
    loanPayments += Number(loan.monthly_payment);
    const remaining = Number(loan.principal_remaining) - principalPortion;
    if (remaining <= 0.01 || loan.term_months_remaining <= 1) {
      await pool.query('DELETE FROM loans WHERE id = $1', [loan.id]);
    } else {
      await pool.query('UPDATE loans SET principal_remaining = $2, term_months_remaining = $3 WHERE id = $1', [
        loan.id,
        remaining,
        loan.term_months_remaining - 1
      ]);
    }
  }

  let mortgagePayments = 0;
  for (const p of properties.rows) {
    const monthlyRate = Number(p.mortgage_rate) / 100 / 12;
    const balance = Number(p.mortgage_balance);
    const interestPortion = balance * monthlyRate;
    const principalPortion = Math.min(balance * 0.005, balance - interestPortion);
    mortgagePayments += interestPortion + balance * 0.005;
    await pool.query('UPDATE properties SET mortgage_balance = $2 WHERE id = $1', [p.id, Math.max(0, balance - principalPortion)]);
  }

  const expenses = lifestyleCost + tuition + loanPayments + mortgagePayments;
  const newCash = Number(s.cash) + income - expenses;
  const wentNegative = newCash < 0;
  const monthsNegative = wentNegative ? s.months_since_negative_cash + 1 : 0;

  const skills: Record<string, number> = { ...s.skills };
  if (job) {
    for (const [type, amount] of Object.entries(job.skillGainPerMonth)) {
      skills[type] = clamp((skills[type] ?? 0) + (amount ?? 0), 0, 100);
    }
  }

  let newEducationLevel = s.education_level;
  let newEduInProgress = s.education_in_progress_id;
  let newEduMonthsRemaining = s.education_months_remaining;
  if (s.education_in_progress_id) {
    const remaining = s.education_months_remaining - 1;
    if (remaining <= 0) {
      newEducationLevel = eduProgram?.grantsLevel ?? s.education_level;
      newEduInProgress = null;
      newEduMonthsRemaining = 0;
    } else {
      newEduMonthsRemaining = remaining;
    }
  }

  const incomeToExpenseRatio = expenses > 0 ? income / expenses : 1.5;
  const happinessTarget = clamp(
    50 +
      LIFESTYLE_COSTS[s.lifestyle_tier as LifestyleTier].happinessBonus +
      (job?.happinessImpact ?? -1) +
      (incomeToExpenseRatio - 1) * 15 +
      (eduProgram?.happinessImpactPerMonth ?? 0),
    0,
    100
  );
  const newHappiness = clamp(Number(s.happiness) + (happinessTarget - Number(s.happiness)) * 0.2, 0, 100);
  const lifestyleOrdinal = LIFESTYLE_ORDER.indexOf(s.lifestyle_tier as LifestyleTier);
  const healthTarget = clamp(60 + (newHappiness - 50) * 0.4 + (lifestyleOrdinal - 1) * 3, 0, 100);
  const newHealth = clamp(Number(s.health) + (healthTarget - Number(s.health)) * 0.15, 0, 100);
  const newReputation = clamp(Number(s.reputation) + (wentNegative ? -3 : 0.3), 0, 100);

  await pool.query(
    `UPDATE player_state SET
      cash = $2, job_months_held = $3, education_level = $4, education_in_progress_id = $5,
      education_months_remaining = $6, skills = $7, happiness = $8, health = $9,
      reputation = $10, months_since_negative_cash = $11, updated_at = now()
     WHERE user_id = $1`,
    [userId, newCash, job ? s.job_months_held + 1 : 0, newEducationLevel, newEduInProgress, newEduMonthsRemaining, JSON.stringify(skills), newHappiness, newHealth, newReputation, monthsNegative]
  );
}

/** Recomputes and caches net worth for every player — used by listings, leaderboards, etc. */
export async function refreshAllNetWorths(markets: MarketSnapshot): Promise<void> {
  const { rows: users } = await pool.query('SELECT user_id FROM player_state');
  for (const { user_id: userId } of users) {
    const netWorth = await computeNetWorth(userId, markets);
    await pool.query('UPDATE player_state SET net_worth = $2 WHERE user_id = $1', [userId, netWorth]);
  }
}

export async function computeNetWorth(userId: string, markets: MarketSnapshot): Promise<number> {
  const [stateRes, stockHoldings, bondHoldings, commodityHoldings, properties, businesses, loans] = await Promise.all([
    pool.query('SELECT cash, bank_savings, foreign_currency_holdings FROM player_state WHERE user_id = $1', [userId]),
    pool.query('SELECT stock_id, shares FROM stock_holdings WHERE user_id = $1', [userId]),
    pool.query('SELECT bond_id, units FROM bond_holdings WHERE user_id = $1', [userId]),
    pool.query('SELECT commodity_id, units FROM commodity_holdings WHERE user_id = $1', [userId]),
    pool.query('SELECT * FROM properties WHERE user_id = $1', [userId]),
    pool.query("SELECT business_cash, loan_balance FROM businesses WHERE user_id = $1 AND status = 'active'", [userId]),
    pool.query('SELECT principal_remaining FROM loans WHERE user_id = $1', [userId])
  ]);
  if (stateRes.rows.length === 0) return 0;
  const s = stateRes.rows[0];

  const stockValue = stockHoldings.rows.reduce((sum, h) => sum + (markets.stocks.find((x) => x.id === h.stock_id)?.price ?? 0) * h.shares, 0);
  const bondValue = bondHoldings.rows.reduce((sum, h) => sum + (markets.bonds.find((x) => x.id === h.bond_id)?.price ?? 0) * h.units, 0);
  const commodityValue = commodityHoldings.rows.reduce((sum, h) => sum + (markets.commodities.find((x) => x.id === h.commodity_id)?.price ?? 0) * Number(h.units), 0);
  const propertyValue = properties.rows.reduce((sum, p) => sum + Number(p.purchase_price) * (markets.housingPriceIndex / Number(p.purchase_housing_index)), 0);
  const propertyDebt = properties.rows.reduce((sum, p) => sum + Number(p.mortgage_balance), 0);
  const currencyValue = Number(s.foreign_currency_holdings) / markets.exchangeRate;
  const businessValue = businesses.rows.reduce((sum, b) => sum + Number(b.business_cash) - Number(b.loan_balance), 0);
  const loanDebt = loans.rows.reduce((sum, l) => sum + Number(l.principal_remaining), 0);

  return Number(s.cash) + Number(s.bank_savings) + stockValue + bondValue + commodityValue + propertyValue + currencyValue + businessValue - loanDebt - propertyDebt;
}

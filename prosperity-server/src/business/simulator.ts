import { EconomyState, NATURAL_GROWTH } from '../economy/model';
import { BUSINESS_CATALOG, BusinessRecord } from './model';

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

export interface BusinessMonthResult {
  business: BusinessRecord;
  revenue: number;
  expenses: number;
  profit: number;
  isBankrupt: boolean;
}

/** Same monthly business simulation as the offline Kotlin build — see BusinessSimulator.kt. */
export function advanceBusinessMonth(business: BusinessRecord, economy: EconomyState): BusinessMonthResult {
  const spec = BUSINESS_CATALOG[business.type];
  const productivity = 1 + (business.level - 1) * 0.25;

  const economyFactor = clamp(1 + ((economy.gdpGrowthRate - NATURAL_GROWTH) / 10) * spec.cyclicality, 0.35, 1.9);
  const confidenceFactor = clamp(0.5 + economy.consumerConfidence / 100, 0.5, 1.5);
  const reputationFactor = clamp(0.5 + business.reputation / 100, 0.5, 1.5);
  const priceFactor = clamp(1.5 - 0.5 * business.pricePointMultiplier, 0.3, 1.3);
  const competitionFactor = clamp(1 - business.competitionPressure / 150, 0.25, 1.0);
  const advertisingFactor = 1 + clamp(business.advertisingBudgetMonthly / 5000, 0, 0.5);
  const noise = gaussian(1.0, 0.06);

  const baseRevenue = business.employees * spec.baseRevenuePerEmployee * productivity;
  const revenue = Math.max(
    0,
    baseRevenue * economyFactor * confidenceFactor * reputationFactor * priceFactor * business.pricePointMultiplier * competitionFactor * advertisingFactor * noise
  );

  const inflationAdjustedSalary = spec.baseSalaryPerEmployee * (1 + (economy.inflationRate / 100) * 0.5);
  const salaries = business.employees * inflationAdjustedSalary;
  const rent = spec.baseRent * (1 + (business.level - 1) * 0.3);
  const inventoryCost = spec.hasInventory ? revenue * 0.35 : 0;
  const loanInterest = (business.loanBalance * business.loanInterestRate) / 12;
  const expenses = salaries + rent + inventoryCost + business.advertisingBudgetMonthly + loanInterest;

  const profitBeforeTax = revenue - expenses;
  const effectiveTaxRate = spec.corporateTaxRate + economy.corporateTaxSurcharge / 100;
  const tax = Math.max(0, profitBeforeTax) * effectiveTaxRate;
  const profit = profitBeforeTax - tax;

  const reputationTarget = clamp(
    50 + (business.level - 1) * 5 - Math.max(0, business.pricePointMultiplier - 1.3) * 20 + Math.min(10, business.advertisingBudgetMonthly / 2000),
    0,
    100
  );
  const newReputation = clamp(business.reputation + (reputationTarget - business.reputation) * 0.15 + gaussian(0, 1.2), 0, 100);

  const competitionDelta = 0.5 + Math.max(0, profit / 2000) * 0.3 - business.advertisingBudgetMonthly / 3000 - (business.level - 1) * 0.2;
  const newCompetition = clamp(business.competitionPressure + competitionDelta, 0, 100);

  const newCash = business.businessCash + profit;
  const isBankrupt = newCash < -spec.startupCost * 0.4;

  return {
    business: { ...business, businessCash: newCash, reputation: newReputation, competitionPressure: newCompetition },
    revenue,
    expenses,
    profit,
    isBankrupt
  };
}

function clamp(v: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, v));
}

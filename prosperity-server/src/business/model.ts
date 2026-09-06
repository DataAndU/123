export type BusinessType = 'RESTAURANT' | 'GROCERY_STORE' | 'TECH_COMPANY' | 'MANUFACTURING' | 'TRANSPORT' | 'CONSTRUCTION' | 'ONLINE_BUSINESS';

export interface BusinessTypeSpec {
  displayName: string;
  startupCost: number;
  baseRevenuePerEmployee: number;
  baseSalaryPerEmployee: number;
  baseRent: number;
  hasInventory: boolean;
  cyclicality: number;
  corporateTaxRate: number;
}

export const BUSINESS_CATALOG: Record<BusinessType, BusinessTypeSpec> = {
  RESTAURANT: { displayName: 'Restaurant', startupCost: 15_000, baseRevenuePerEmployee: 2800, baseSalaryPerEmployee: 2200, baseRent: 1800, hasInventory: true, cyclicality: 1.1, corporateTaxRate: 0.2 },
  GROCERY_STORE: { displayName: 'Grocery Store', startupCost: 25_000, baseRevenuePerEmployee: 3200, baseSalaryPerEmployee: 2000, baseRent: 2200, hasInventory: true, cyclicality: 0.4, corporateTaxRate: 0.2 },
  TECH_COMPANY: { displayName: 'Technology Startup', startupCost: 40_000, baseRevenuePerEmployee: 6500, baseSalaryPerEmployee: 4800, baseRent: 2500, hasInventory: false, cyclicality: 1.6, corporateTaxRate: 0.2 },
  MANUFACTURING: { displayName: 'Manufacturing Plant', startupCost: 80_000, baseRevenuePerEmployee: 5200, baseSalaryPerEmployee: 3200, baseRent: 4000, hasInventory: true, cyclicality: 1.4, corporateTaxRate: 0.2 },
  TRANSPORT: { displayName: 'Transport & Logistics', startupCost: 50_000, baseRevenuePerEmployee: 4200, baseSalaryPerEmployee: 2800, baseRent: 1500, hasInventory: false, cyclicality: 1.2, corporateTaxRate: 0.2 },
  CONSTRUCTION: { displayName: 'Construction Firm', startupCost: 60_000, baseRevenuePerEmployee: 5800, baseSalaryPerEmployee: 3400, baseRent: 1200, hasInventory: true, cyclicality: 1.8, corporateTaxRate: 0.2 },
  ONLINE_BUSINESS: { displayName: 'Online Business', startupCost: 8_000, baseRevenuePerEmployee: 3400, baseSalaryPerEmployee: 2600, baseRent: 400, hasInventory: false, cyclicality: 0.9, corporateTaxRate: 0.2 }
};

export interface BusinessRecord {
  id: string;
  userId: string;
  type: BusinessType;
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
  status: 'active' | 'closed_bankrupt' | 'closed_voluntary';
}

export function upgradeCost(business: BusinessRecord): number {
  return BUSINESS_CATALOG[business.type].startupCost * 0.6 * business.level;
}

export function liquidationValue(business: BusinessRecord): number {
  const spec = BUSINESS_CATALOG[business.type];
  const assetValue = spec.startupCost * 0.4 * business.level;
  return Math.max(0, assetValue + business.businessCash - business.loanBalance);
}

import { BusinessCyclePhase, EconomyState, INFLATION_TARGET, NATURAL_GROWTH, NATURAL_UNEMPLOYMENT } from './model';

export interface EconomyShock {
  gdpGrowthDelta?: number;
  inflationDelta?: number;
  unemploymentDelta?: number;
  interestRateDelta?: number;
  confidenceDelta?: number;
  forceRecession?: boolean;
  forceBoom?: boolean;
}

/** Box-Muller Gaussian sample. */
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

function clamp(v: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, v));
}

function advancePhase(state: EconomyState, shock: EconomyShock): { phase: BusinessCyclePhase; phaseMonthsElapsed: number } {
  if (shock.forceRecession) return { phase: 'RECESSION', phaseMonthsElapsed: 0 };
  if (shock.forceBoom) return { phase: 'EXPANSION', phaseMonthsElapsed: 0 };

  const months = state.phaseMonthsElapsed;
  let transitionChance: number;
  switch (state.phase) {
    case 'EXPANSION':
      transitionChance = Math.min(0.02 + Math.max(0, months - 12) * 0.01, 0.35);
      break;
    case 'PEAK':
      transitionChance = Math.min(0.25 + months * 0.15, 0.9);
      break;
    case 'RECESSION':
      transitionChance = Math.min(0.05 + Math.max(0, months - 4) * 0.03, 0.5);
      break;
    case 'TROUGH':
      transitionChance = Math.min(0.2 + months * 0.1, 0.85);
      break;
  }

  if (Math.random() < transitionChance) {
    const next: Record<BusinessCyclePhase, BusinessCyclePhase> = {
      EXPANSION: 'PEAK',
      PEAK: 'RECESSION',
      RECESSION: 'TROUGH',
      TROUGH: 'EXPANSION'
    };
    return { phase: next[state.phase], phaseMonthsElapsed: 0 };
  }
  return { phase: state.phase, phaseMonthsElapsed: months + 1 };
}

/**
 * Advances the shared, server-authoritative macro economy by one month.
 * Same business-cycle/Okun's-law/Taylor-rule model as the offline game's
 * Kotlin simulator, so the two stay conceptually consistent even though
 * this copy now drives a real multiplayer world instead of a solo save.
 */
export function advanceEconomy(state: EconomyState, shock: EconomyShock = {}): EconomyState {
  const { phase, phaseMonthsElapsed } = advancePhase(state, shock);

  const phaseBaseGrowth: Record<BusinessCyclePhase, number> = {
    EXPANSION: 3.2,
    PEAK: 1.0,
    RECESSION: -2.0,
    TROUGH: -0.3
  };

  const rateDrag = (state.interestRate - INFLATION_TARGET - 1.0) * 0.18;
  const growthNoise = gaussian(0, 0.9);
  let gdpGrowth = phaseBaseGrowth[phase] - rateDrag + growthNoise + (shock.gdpGrowthDelta || 0);
  gdpGrowth = clamp(gdpGrowth, -8, 8);

  const growthSurprise = gdpGrowth - NATURAL_GROWTH;
  let unemployment = state.unemploymentRate - 0.22 * growthSurprise + gaussian(0, 0.12) + (shock.unemploymentDelta || 0);
  unemployment = clamp(unemployment, 2, 25);

  const outputGap = gdpGrowth - NATURAL_GROWTH;
  let inflation =
    state.inflationRate +
    0.1 * outputGap +
    0.05 * (state.moneySupplyGrowth - 4.0) -
    0.15 * (state.inflationRate - INFLATION_TARGET) +
    gaussian(0, 0.25) +
    (shock.inflationDelta || 0);
  inflation = clamp(inflation, -5, 40);

  const targetRate = 2.0 + inflation + 1.5 * (inflation - INFLATION_TARGET) - 1.0 * (unemployment - NATURAL_UNEMPLOYMENT);
  let interestRate = state.interestRate + (targetRate - state.interestRate) * 0.25 + (shock.interestRateDelta || 0);
  interestRate = clamp(interestRate, 0, 30);

  let confidence =
    state.consumerConfidence +
    0.6 * growthSurprise -
    0.4 * (unemployment - NATURAL_UNEMPLOYMENT) -
    0.3 * (inflation - INFLATION_TARGET) +
    gaussian(0, 1.5) +
    (shock.confidenceDelta || 0);
  confidence = clamp(confidence, 0, 100);

  const moneySupplyGrowth = clamp(state.moneySupplyGrowth + gaussian(0, 0.3), -5, 15);
  const priceLevelIndex = state.priceLevelIndex * (1 + inflation / 100 / 12);

  return {
    ...state,
    month: state.month + 1,
    phase,
    phaseMonthsElapsed,
    gdpGrowthRate: gdpGrowth,
    inflationRate: inflation,
    unemploymentRate: unemployment,
    interestRate,
    consumerConfidence: confidence,
    moneySupplyGrowth,
    priceLevelIndex
  };
}

/** Standard amortization formula: fixed monthly payment for a fixed-rate, fixed-term loan. */
export function loanMonthlyPayment(principal: number, annualRatePercent: number, termMonths: number): number {
  const r = annualRatePercent / 100 / 12;
  if (r === 0) return principal / termMonths;
  const factor = Math.pow(1 + r, termMonths);
  return (principal * r * factor) / (factor - 1);
}

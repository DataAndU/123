import { Router } from 'express';
import { z } from 'zod';
import { pool } from '../db/pool';
import { requireAuth } from '../middleware/auth';
import { asyncHandler, HttpError } from '../util/asyncHandler';
import { BUSINESS_CATALOG, liquidationValue, upgradeCost } from '../business/model';
import { closeBusiness, createBusiness, getBusiness, getBusinessHistory, updateBusinessFields } from '../business/repository';

export const businessRouter = Router();
businessRouter.use(requireAuth);

async function requireOwnedBusiness(id: string, userId: string) {
  const business = await getBusiness(id);
  if (!business) throw new HttpError(404, 'Business not found');
  if (business.userId !== userId) throw new HttpError(403, 'Not your business');
  return business;
}

async function requireActiveOwnedBusiness(id: string, userId: string) {
  const business = await requireOwnedBusiness(id, userId);
  if (business.status !== 'active') throw new HttpError(400, 'This business is closed');
  return business;
}

async function adjustPlayerCash(userId: string, delta: number) {
  await pool.query('UPDATE player_state SET cash = cash + $2, updated_at = now() WHERE user_id = $1', [userId, delta]);
}

async function getPlayerCash(userId: string): Promise<number> {
  const { rows } = await pool.query('SELECT cash FROM player_state WHERE user_id = $1', [userId]);
  return Number(rows[0]?.cash ?? 0);
}

const startSchema = z.object({
  type: z.enum(Object.keys(BUSINESS_CATALOG) as [string, ...string[]]),
  name: z.string().min(1).max(60)
});

businessRouter.post(
  '/',
  asyncHandler(async (req, res) => {
    const parsed = startSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid business type/name');
    const type = parsed.data.type as keyof typeof BUSINESS_CATALOG;
    const spec = BUSINESS_CATALOG[type];
    const cash = await getPlayerCash(req.userId!);
    if (cash < spec.startupCost) throw new HttpError(400, `Not enough cash — need $${spec.startupCost}`);
    await adjustPlayerCash(req.userId!, -spec.startupCost);
    const business = await createBusiness(req.userId!, type, parsed.data.name);
    res.status(201).json(business);
  })
);

businessRouter.get(
  '/:id/history',
  asyncHandler(async (req, res) => {
    await requireOwnedBusiness(req.params.id, req.userId!);
    res.json(await getBusinessHistory(req.params.id));
  })
);

businessRouter.post(
  '/:id/hire',
  asyncHandler(async (req, res) => {
    const business = await requireActiveOwnedBusiness(req.params.id, req.userId!);
    await updateBusinessFields(business.id, { employees: business.employees + 1 });
    res.json({ ok: true });
  })
);

businessRouter.post(
  '/:id/fire',
  asyncHandler(async (req, res) => {
    const business = await requireActiveOwnedBusiness(req.params.id, req.userId!);
    await updateBusinessFields(business.id, { employees: Math.max(0, business.employees - 1) });
    res.json({ ok: true });
  })
);

const priceSchema = z.object({ multiplier: z.number().min(0.5).max(2.0) });
businessRouter.post(
  '/:id/price',
  asyncHandler(async (req, res) => {
    const business = await requireActiveOwnedBusiness(req.params.id, req.userId!);
    const parsed = priceSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'multiplier must be 0.5-2.0');
    await updateBusinessFields(business.id, { price_point_multiplier: parsed.data.multiplier });
    res.json({ ok: true });
  })
);

const budgetSchema = z.object({ monthlyBudget: z.number().min(0) });
businessRouter.post(
  '/:id/advertise',
  asyncHandler(async (req, res) => {
    const business = await requireActiveOwnedBusiness(req.params.id, req.userId!);
    const parsed = budgetSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'monthlyBudget must be >= 0');
    await updateBusinessFields(business.id, { advertising_budget_monthly: parsed.data.monthlyBudget });
    res.json({ ok: true });
  })
);

businessRouter.post(
  '/:id/upgrade',
  asyncHandler(async (req, res) => {
    const business = await requireActiveOwnedBusiness(req.params.id, req.userId!);
    if (business.level >= 5) throw new HttpError(400, 'Already at max level');
    const cost = upgradeCost(business);
    const cash = await getPlayerCash(req.userId!);
    if (cash < cost) throw new HttpError(400, `Not enough cash — need $${cost.toFixed(2)}`);
    await adjustPlayerCash(req.userId!, -cost);
    await updateBusinessFields(business.id, { level: business.level + 1 });
    res.json({ ok: true });
  })
);

const loanSchema = z.object({ amount: z.number().positive(), annualRate: z.number().positive() });
businessRouter.post(
  '/:id/loan',
  asyncHandler(async (req, res) => {
    const business = await requireActiveOwnedBusiness(req.params.id, req.userId!);
    const parsed = loanSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid loan request');
    await updateBusinessFields(business.id, {
      business_cash: business.businessCash + parsed.data.amount,
      loan_balance: business.loanBalance + parsed.data.amount,
      loan_interest_rate: parsed.data.annualRate
    });
    res.json({ ok: true });
  })
);

const amountSchema = z.object({ amount: z.number().positive() });
businessRouter.post(
  '/:id/repay-loan',
  asyncHandler(async (req, res) => {
    const business = await requireActiveOwnedBusiness(req.params.id, req.userId!);
    const parsed = amountSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid amount');
    const payment = Math.min(parsed.data.amount, business.loanBalance);
    await updateBusinessFields(business.id, { business_cash: business.businessCash - payment, loan_balance: business.loanBalance - payment });
    res.json({ ok: true });
  })
);

businessRouter.post(
  '/:id/withdraw',
  asyncHandler(async (req, res) => {
    const business = await requireActiveOwnedBusiness(req.params.id, req.userId!);
    const parsed = amountSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid amount');
    const take = Math.min(parsed.data.amount, business.businessCash);
    await updateBusinessFields(business.id, { business_cash: business.businessCash - take });
    await adjustPlayerCash(req.userId!, take);
    res.json({ ok: true, withdrawn: take });
  })
);

businessRouter.post(
  '/:id/inject',
  asyncHandler(async (req, res) => {
    const business = await requireActiveOwnedBusiness(req.params.id, req.userId!);
    const parsed = amountSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid amount');
    const cash = await getPlayerCash(req.userId!);
    if (cash < parsed.data.amount) throw new HttpError(400, 'Not enough personal cash');
    await adjustPlayerCash(req.userId!, -parsed.data.amount);
    await updateBusinessFields(business.id, { business_cash: business.businessCash + parsed.data.amount });
    res.json({ ok: true });
  })
);

businessRouter.delete(
  '/:id',
  asyncHandler(async (req, res) => {
    const business = await requireActiveOwnedBusiness(req.params.id, req.userId!);
    const value = liquidationValue(business);
    await adjustPlayerCash(req.userId!, value);
    await closeBusiness(business.id, 'closed_voluntary');
    res.json({ ok: true, recovered: value });
  })
);

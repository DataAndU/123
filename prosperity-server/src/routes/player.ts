import { Router } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { requireAuth } from '../middleware/auth';
import { asyncHandler, HttpError } from '../util/asyncHandler';
import { getFullPlayerView, setPlayerFields } from '../player/repository';
import { z } from 'zod';
import { pool } from '../db/pool';
import { EDUCATION_CATALOG, EDUCATION_ORDER, findEducationProgram, findJob, isEligibleForJob, JOB_CATALOG, SKILL_TYPES } from '../player/catalog';
import { getMarketSnapshot } from '../market/model';
import { BASE_PROPERTY_PRICE } from '../player/propertyConstants';
import { loanMonthlyPayment } from '../player/loanMath';

export const playerRouter = Router();
playerRouter.use(requireAuth);

playerRouter.get(
  '/me',
  asyncHandler(async (req, res) => {
    const player = await getFullPlayerView(req.userId!);
    if (!player) throw new HttpError(404, 'Player not found');
    res.json(player);
  })
);

const lifestyleSchema = z.object({ tier: z.enum(['SPARTAN', 'MODEST', 'COMFORTABLE', 'LUXURY', 'ELITE']) });

playerRouter.post(
  '/lifestyle',
  asyncHandler(async (req, res) => {
    const parsed = lifestyleSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid lifestyle tier');
    await setPlayerFields(req.userId!, { lifestyle_tier: parsed.data.tier });
    res.json({ ok: true });
  })
);

// ---------------- Career: jobs, skills, education ----------------

playerRouter.get('/jobs', asyncHandler(async (_req, res) => res.json(JOB_CATALOG)));
playerRouter.get('/education/programs', asyncHandler(async (_req, res) => res.json(EDUCATION_CATALOG)));

const applyJobSchema = z.object({ jobId: z.string() });
playerRouter.post(
  '/jobs/apply',
  asyncHandler(async (req, res) => {
    const parsed = applyJobSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'jobId required');
    const job = findJob(parsed.data.jobId);
    if (!job) throw new HttpError(404, 'Unknown job');

    const { rows } = await pool.query('SELECT education_level, skills FROM player_state WHERE user_id = $1', [req.userId]);
    if (rows.length === 0) throw new HttpError(404, 'Player not found');
    if (!isEligibleForJob(job, rows[0].education_level, rows[0].skills)) {
      throw new HttpError(400, `You don't meet the requirements for ${job.title} yet`);
    }
    await setPlayerFields(req.userId!, { current_job_id: job.id, job_months_held: 0 });
    res.json({ ok: true });
  })
);

playerRouter.post(
  '/jobs/quit',
  asyncHandler(async (req, res) => {
    await setPlayerFields(req.userId!, { current_job_id: null, job_months_held: 0 });
    res.json({ ok: true });
  })
);

const trainSkillSchema = z.object({ skill: z.enum(SKILL_TYPES as [string, ...string[]]), cost: z.number().positive() });
playerRouter.post(
  '/skills/train',
  asyncHandler(async (req, res) => {
    const parsed = trainSkillSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid training request');
    const { rows } = await pool.query('SELECT cash, skills FROM player_state WHERE user_id = $1', [req.userId]);
    if (rows.length === 0) throw new HttpError(404, 'Player not found');
    const cash = Number(rows[0].cash);
    if (cash < parsed.data.cost) throw new HttpError(400, 'Not enough cash for training');
    const skills = { ...rows[0].skills };
    const current = skills[parsed.data.skill] ?? 0;
    const gain = Math.max(1, 8 - Math.floor(current / 15));
    skills[parsed.data.skill] = Math.min(100, current + gain);
    await setPlayerFields(req.userId!, { cash: cash - parsed.data.cost, skills: JSON.stringify(skills) });
    res.json({ ok: true, gain });
  })
);

const enrollSchema = z.object({ programId: z.string() });
playerRouter.post(
  '/education/enroll',
  asyncHandler(async (req, res) => {
    const parsed = enrollSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'programId required');
    const program = findEducationProgram(parsed.data.programId);
    if (!program) throw new HttpError(404, 'Unknown program');

    const { rows } = await pool.query('SELECT cash, education_level, education_in_progress_id FROM player_state WHERE user_id = $1', [req.userId]);
    if (rows.length === 0) throw new HttpError(404, 'Player not found');
    if (rows[0].education_in_progress_id) throw new HttpError(400, "You're already studying");
    if (EDUCATION_ORDER.indexOf(program.grantsLevel) <= EDUCATION_ORDER.indexOf(rows[0].education_level)) {
      throw new HttpError(400, 'You already hold this level or higher');
    }
    const upfrontFee = program.tuitionCost * 0.2;
    const cash = Number(rows[0].cash);
    if (cash < upfrontFee) throw new HttpError(400, `Not enough cash for the enrollment fee of $${upfrontFee.toFixed(2)}`);

    await setPlayerFields(req.userId!, {
      cash: cash - upfrontFee,
      education_in_progress_id: program.id,
      education_months_remaining: program.durationMonths
    });
    res.json({ ok: true });
  })
);

// ---------------- Personal loans ----------------

const takeLoanSchema = z.object({ amount: z.number().positive(), annualRate: z.number().positive(), termMonths: z.number().int().positive() });
playerRouter.post(
  '/loans',
  asyncHandler(async (req, res) => {
    const parsed = takeLoanSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid loan request');
    const { amount, annualRate, termMonths } = parsed.data;
    const countRes = await pool.query('SELECT COUNT(*)::int AS n FROM loans WHERE user_id = $1', [req.userId]);
    if (countRes.rows[0].n >= 4) throw new HttpError(400, 'Too many active loans already');

    const payment = loanMonthlyPayment(amount, annualRate, termMonths);
    await pool.query(
      'INSERT INTO loans (id, user_id, type, principal_remaining, annual_rate, monthly_payment, original_principal, term_months_remaining) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)',
      [uuidv4(), req.userId, 'PERSONAL', amount, annualRate, payment, amount, termMonths]
    );
    await pool.query('UPDATE player_state SET cash = cash + $2 WHERE user_id = $1', [req.userId, amount]);
    res.status(201).json({ ok: true, monthlyPayment: payment });
  })
);

const repaySchema = z.object({ amount: z.number().positive() });
playerRouter.post(
  '/loans/:id/repay',
  asyncHandler(async (req, res) => {
    const parsed = repaySchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid amount');
    const loanRes = await pool.query('SELECT * FROM loans WHERE id = $1 AND user_id = $2', [req.params.id, req.userId]);
    if (loanRes.rows.length === 0) throw new HttpError(404, 'Loan not found');
    const loan = loanRes.rows[0];
    const cashRes = await pool.query('SELECT cash FROM player_state WHERE user_id = $1', [req.userId]);
    const cash = Number(cashRes.rows[0].cash);
    if (cash < parsed.data.amount) throw new HttpError(400, 'Not enough cash');

    const payoff = Math.min(parsed.data.amount, Number(loan.principal_remaining));
    const remaining = Number(loan.principal_remaining) - payoff;
    if (remaining <= 0.01) {
      await pool.query('DELETE FROM loans WHERE id = $1', [loan.id]);
    } else {
      await pool.query('UPDATE loans SET principal_remaining = $2 WHERE id = $1', [loan.id, remaining]);
    }
    await pool.query('UPDATE player_state SET cash = cash - $2 WHERE user_id = $1', [req.userId, payoff]);
    res.json({ ok: true, paid: payoff });
  })
);

// ---------------- Savings ----------------

const amountSchema = z.object({ amount: z.number().positive() });
playerRouter.post(
  '/savings/deposit',
  asyncHandler(async (req, res) => {
    const parsed = amountSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid amount');
    const { rows } = await pool.query('SELECT cash FROM player_state WHERE user_id = $1', [req.userId]);
    if (Number(rows[0].cash) < parsed.data.amount) throw new HttpError(400, 'Not enough cash');
    await pool.query('UPDATE player_state SET cash = cash - $2, bank_savings = bank_savings + $2 WHERE user_id = $1', [req.userId, parsed.data.amount]);
    res.json({ ok: true });
  })
);

playerRouter.post(
  '/savings/withdraw',
  asyncHandler(async (req, res) => {
    const parsed = amountSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid amount');
    const { rows } = await pool.query('SELECT bank_savings FROM player_state WHERE user_id = $1', [req.userId]);
    if (Number(rows[0].bank_savings) < parsed.data.amount) throw new HttpError(400, 'Not enough savings');
    await pool.query('UPDATE player_state SET cash = cash + $2, bank_savings = bank_savings - $2 WHERE user_id = $1', [req.userId, parsed.data.amount]);
    res.json({ ok: true });
  })
);

// ---------------- Real estate ----------------

playerRouter.post(
  '/properties',
  asyncHandler(async (req, res) => {
    const schema = z.object({ downPaymentFraction: z.number().min(0.1).max(1), mortgageRate: z.number().positive() });
    const parsed = schema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid property purchase request');

    const markets = await getMarketSnapshot();
    const price = BASE_PROPERTY_PRICE * (markets.housingPriceIndex / 100);
    const downPayment = price * parsed.data.downPaymentFraction;
    const { rows } = await pool.query('SELECT cash FROM player_state WHERE user_id = $1', [req.userId]);
    if (Number(rows[0].cash) < downPayment) throw new HttpError(400, `Not enough cash for the down payment of $${downPayment.toFixed(2)}`);

    const mortgage = price - downPayment;
    await pool.query('UPDATE player_state SET cash = cash - $2 WHERE user_id = $1', [req.userId, downPayment]);
    await pool.query(
      'INSERT INTO properties (id, user_id, purchase_price, purchase_housing_index, mortgage_balance, mortgage_rate, monthly_rent_income) VALUES ($1,$2,$3,$4,$5,$6,$7)',
      [uuidv4(), req.userId, price, markets.housingPriceIndex, mortgage, parsed.data.mortgageRate, price * 0.0045]
    );
    res.status(201).json({ ok: true, price });
  })
);

playerRouter.delete(
  '/properties/:id',
  asyncHandler(async (req, res) => {
    const propRes = await pool.query('SELECT * FROM properties WHERE id = $1 AND user_id = $2', [req.params.id, req.userId]);
    if (propRes.rows.length === 0) throw new HttpError(404, 'Property not found');
    const property = propRes.rows[0];
    const markets = await getMarketSnapshot();
    const currentValue = Number(property.purchase_price) * (markets.housingPriceIndex / Number(property.purchase_housing_index));
    const proceeds = Math.max(0, currentValue - Number(property.mortgage_balance));
    await pool.query('UPDATE player_state SET cash = cash + $2 WHERE user_id = $1', [req.userId, proceeds]);
    await pool.query('DELETE FROM properties WHERE id = $1', [property.id]);
    res.json({ ok: true, proceeds });
  })
);

// ---------------- Foreign currency ----------------

playerRouter.post(
  '/currency/buy',
  asyncHandler(async (req, res) => {
    const parsed = amountSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid amount');
    const markets = await getMarketSnapshot();
    const { rows } = await pool.query('SELECT cash FROM player_state WHERE user_id = $1', [req.userId]);
    if (Number(rows[0].cash) < parsed.data.amount) throw new HttpError(400, 'Not enough cash');
    const foreignAmount = parsed.data.amount * markets.exchangeRate;
    await pool.query('UPDATE player_state SET cash = cash - $2, foreign_currency_holdings = foreign_currency_holdings + $3 WHERE user_id = $1', [
      req.userId,
      parsed.data.amount,
      foreignAmount
    ]);
    res.json({ ok: true, foreignAmount });
  })
);

playerRouter.post(
  '/currency/sell',
  asyncHandler(async (req, res) => {
    const parsed = amountSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid amount');
    const markets = await getMarketSnapshot();
    const { rows } = await pool.query('SELECT foreign_currency_holdings FROM player_state WHERE user_id = $1', [req.userId]);
    if (Number(rows[0].foreign_currency_holdings) < parsed.data.amount) throw new HttpError(400, 'Not enough foreign currency');
    const localAmount = parsed.data.amount / markets.exchangeRate;
    await pool.query('UPDATE player_state SET cash = cash + $2, foreign_currency_holdings = foreign_currency_holdings - $3 WHERE user_id = $1', [
      req.userId,
      localAmount,
      parsed.data.amount
    ]);
    res.json({ ok: true, localAmount });
  })
);

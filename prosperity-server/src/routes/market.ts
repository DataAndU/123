import { Router } from 'express';
import { getEconomyHistory, getEconomyState } from '../economy/model';
import { getMarketSnapshot } from '../market/model';
import { asyncHandler } from '../util/asyncHandler';
import { requireAuth } from '../middleware/auth';

export const marketRouter = Router();
marketRouter.use(requireAuth);

marketRouter.get(
  '/snapshot',
  asyncHandler(async (_req, res) => {
    const [economy, markets] = await Promise.all([getEconomyState(), getMarketSnapshot()]);
    res.json({ economy, markets });
  })
);

marketRouter.get(
  '/economy/history',
  asyncHandler(async (_req, res) => {
    res.json(await getEconomyHistory());
  })
);

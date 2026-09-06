import { Router } from 'express';
import { Server } from 'socket.io';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth';
import { asyncHandler, HttpError } from '../util/asyncHandler';
import { executeBondTrade, executeCommodityTrade, executeStockTrade } from '../market/trading';

const stockTradeSchema = z.object({ stockId: z.string(), side: z.enum(['buy', 'sell']), shares: z.number().int().positive() });
const bondTradeSchema = z.object({ bondId: z.string(), side: z.enum(['buy', 'sell']), units: z.number().int().positive() });
const commodityTradeSchema = z.object({ commodityId: z.string(), side: z.enum(['buy', 'sell']), units: z.number().positive() });

export function createTradingRouter(io: Server): Router {
  const router = Router();
  router.use(requireAuth);

  router.post(
    '/stocks',
    asyncHandler(async (req, res) => {
      const parsed = stockTradeSchema.safeParse(req.body);
      if (!parsed.success) throw new HttpError(400, 'Invalid trade request');
      const { stockId, side, shares } = parsed.data;
      const result = await executeStockTrade(req.userId!, stockId, side, shares);
      io.emit('trade:executed', { username: req.username, ...result });
      res.json(result);
    })
  );

  router.post(
    '/bonds',
    asyncHandler(async (req, res) => {
      const parsed = bondTradeSchema.safeParse(req.body);
      if (!parsed.success) throw new HttpError(400, 'Invalid trade request');
      const { bondId, side, units } = parsed.data;
      const result = await executeBondTrade(req.userId!, bondId, side, units);
      io.emit('trade:executed', { username: req.username, ...result });
      res.json(result);
    })
  );

  router.post(
    '/commodities',
    asyncHandler(async (req, res) => {
      const parsed = commodityTradeSchema.safeParse(req.body);
      if (!parsed.success) throw new HttpError(400, 'Invalid trade request');
      const { commodityId, side, units } = parsed.data;
      const result = await executeCommodityTrade(req.userId!, commodityId, side, units);
      io.emit('trade:executed', { username: req.username, ...result });
      res.json(result);
    })
  );

  return router;
}

import { Router } from 'express';
import { requireAuth } from '../middleware/auth';
import { asyncHandler } from '../util/asyncHandler';
import { getWalletBalance, getWalletHistory } from '../marketplace/repository';

export const walletRouter = Router();
walletRouter.use(requireAuth);

walletRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const [balance, history] = await Promise.all([getWalletBalance(req.userId!), getWalletHistory(req.userId!)]);
    res.json({ balanceCoins: balance, transactions: history });
  })
);

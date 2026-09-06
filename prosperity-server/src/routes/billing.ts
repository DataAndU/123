import { Router } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { z } from 'zod';
import { pool } from '../db/pool';
import { requireAuth } from '../middleware/auth';
import { asyncHandler, HttpError } from '../util/asyncHandler';
import { COIN_PRODUCTS, coinsForProduct } from '../billing/products';
import { isBillingDevMode, isPlayBillingConfigured, verifyPlayPurchase } from '../billing/playVerification';

export const billingRouter = Router();
billingRouter.use(requireAuth);

billingRouter.get(
  '/products',
  asyncHandler(async (_req, res) => {
    res.json(COIN_PRODUCTS);
  })
);

const verifySchema = z.object({ productId: z.string().min(1), purchaseToken: z.string().min(1) });

/**
 * Coins are one-way: real money buys them via Google Play, they're spent
 * or earned in-game, and there is no endpoint anywhere in this server that
 * converts coins back into real money or a payout to a person.
 */
billingRouter.post(
  '/verify-purchase',
  asyncHandler(async (req, res) => {
    const parsed = verifySchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'productId and purchaseToken are required');
    const { productId, purchaseToken } = parsed.data;

    const coins = coinsForProduct(productId);
    if (coins === null) throw new HttpError(400, `Unknown product: ${productId}`);

    const existing = await pool.query('SELECT 1 FROM iap_purchases WHERE purchase_token = $1', [purchaseToken]);
    if (existing.rows.length > 0) throw new HttpError(409, 'This purchase was already redeemed');

    if (isBillingDevMode()) {
      console.warn(`BILLING_DEV_MODE is on — trusting client-reported purchase for ${productId} without real verification.`);
    } else if (isPlayBillingConfigured()) {
      const result = await verifyPlayPurchase(productId, purchaseToken);
      if (!result.valid) throw new HttpError(402, `Purchase could not be verified: ${result.reason}`);
    } else {
      throw new HttpError(501, 'Play Billing verification is not configured on this server yet (see README)');
    }

    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      await client.query(
        'INSERT INTO iap_purchases (id, user_id, product_id, purchase_token, coins_credited, verified) VALUES ($1,$2,$3,$4,$5,true)',
        [uuidv4(), req.userId, productId, purchaseToken, coins]
      );
      await client.query('UPDATE player_state SET wallet_coins = wallet_coins + $2 WHERE user_id = $1', [req.userId, coins]);
      await client.query('INSERT INTO wallet_transactions (user_id, amount_coins, reason, reference_id) VALUES ($1,$2,$3,$4)', [
        req.userId,
        coins,
        'iap_purchase',
        productId
      ]);
      await client.query('COMMIT');
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }

    res.json({ ok: true, coinsCredited: coins });
  })
);

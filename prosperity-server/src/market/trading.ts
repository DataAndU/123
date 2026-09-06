import { pool } from '../db/pool';
import { HttpError } from '../util/asyncHandler';

export type InstrumentType = 'stock' | 'bond' | 'commodity';

export interface TradeResult {
  instrumentType: InstrumentType;
  instrumentId: string;
  side: 'buy' | 'sell';
  quantity: number;
  executedPrice: number;
  newPrice: number;
  totalCost: number;
  cashAfter: number;
}

const STOCK_DEPTH_SHARES = 8000; // assumed resting liquidity; trading against it moves price
const STOCK_IMPACT_SENSITIVITY = 0.5;
const COMMODITY_DEPTH_NOTIONAL = 150_000;
const COMMODITY_IMPACT_SENSITIVITY = 0.4;
const MAX_IMPACT_PER_TRADE = 0.08; // no single trade moves price more than 8%

function clampImpact(x: number): number {
  return Math.max(-MAX_IMPACT_PER_TRADE, Math.min(MAX_IMPACT_PER_TRADE, x));
}

/**
 * Executes a buy/sell of a stock or commodity inside one DB transaction:
 * row-locks the instrument and the player's cash, fills at the current
 * price, then nudges the shared price by the trade's share of assumed
 * market depth — real order flow from real players is what moves prices
 * between monthly economy ticks, not just background noise.
 */
export async function executeStockTrade(userId: string, stockId: string, side: 'buy' | 'sell', shares: number): Promise<TradeResult> {
  if (!Number.isFinite(shares) || shares <= 0 || !Number.isInteger(shares)) throw new HttpError(400, 'Shares must be a positive whole number');

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const stockRes = await client.query('SELECT price FROM stocks WHERE id = $1 FOR UPDATE', [stockId]);
    if (stockRes.rows.length === 0) throw new HttpError(404, 'Unknown stock');
    const price = Number(stockRes.rows[0].price);

    const playerRes = await client.query('SELECT cash FROM player_state WHERE user_id = $1 FOR UPDATE', [userId]);
    if (playerRes.rows.length === 0) throw new HttpError(404, 'Player not found');
    const cash = Number(playerRes.rows[0].cash);

    const holdingRes = await client.query('SELECT shares FROM stock_holdings WHERE user_id = $1 AND stock_id = $2 FOR UPDATE', [userId, stockId]);
    const ownedShares = holdingRes.rows[0]?.shares ?? 0;

    const totalCost = price * shares;
    if (side === 'buy') {
      if (totalCost > cash) throw new HttpError(400, `Not enough cash — need $${totalCost.toFixed(2)}`);
    } else {
      if (shares > ownedShares) throw new HttpError(400, `You only own ${ownedShares} shares`);
    }

    const newCash = side === 'buy' ? cash - totalCost : cash + totalCost;
    await client.query('UPDATE player_state SET cash = $2, updated_at = now() WHERE user_id = $1', [userId, newCash]);

    const newOwned = side === 'buy' ? ownedShares + shares : ownedShares - shares;
    if (newOwned > 0) {
      await client.query(
        `INSERT INTO stock_holdings (user_id, stock_id, shares) VALUES ($1, $2, $3)
         ON CONFLICT (user_id, stock_id) DO UPDATE SET shares = $3`,
        [userId, stockId, newOwned]
      );
    } else {
      await client.query('DELETE FROM stock_holdings WHERE user_id = $1 AND stock_id = $2', [userId, stockId]);
    }

    const rawImpact = (shares / STOCK_DEPTH_SHARES) * STOCK_IMPACT_SENSITIVITY;
    const impact = clampImpact(side === 'buy' ? rawImpact : -rawImpact);
    const newPrice = Math.max(0.05, price * (1 + impact));
    await client.query('UPDATE stocks SET price = $2 WHERE id = $1', [stockId, newPrice]);

    await client.query(
      'INSERT INTO trades (user_id, instrument_type, instrument_id, side, quantity, price) VALUES ($1, $2, $3, $4, $5, $6)',
      [userId, 'stock', stockId, side, shares, price]
    );

    await client.query('COMMIT');
    return { instrumentType: 'stock', instrumentId: stockId, side, quantity: shares, executedPrice: price, newPrice, totalCost, cashAfter: newCash };
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

export async function executeCommodityTrade(userId: string, commodityId: string, side: 'buy' | 'sell', units: number): Promise<TradeResult> {
  if (!Number.isFinite(units) || units <= 0) throw new HttpError(400, 'Units must be a positive number');

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const commodityRes = await client.query('SELECT price FROM commodities WHERE id = $1 FOR UPDATE', [commodityId]);
    if (commodityRes.rows.length === 0) throw new HttpError(404, 'Unknown commodity');
    const price = Number(commodityRes.rows[0].price);

    const playerRes = await client.query('SELECT cash FROM player_state WHERE user_id = $1 FOR UPDATE', [userId]);
    if (playerRes.rows.length === 0) throw new HttpError(404, 'Player not found');
    const cash = Number(playerRes.rows[0].cash);

    const holdingRes = await client.query('SELECT units FROM commodity_holdings WHERE user_id = $1 AND commodity_id = $2 FOR UPDATE', [userId, commodityId]);
    const ownedUnits = Number(holdingRes.rows[0]?.units ?? 0);

    const totalCost = price * units;
    if (side === 'buy') {
      if (totalCost > cash) throw new HttpError(400, `Not enough cash — need $${totalCost.toFixed(2)}`);
    } else if (units > ownedUnits + 1e-9) {
      throw new HttpError(400, `You only own ${ownedUnits.toFixed(2)} units`);
    }

    const newCash = side === 'buy' ? cash - totalCost : cash + totalCost;
    await client.query('UPDATE player_state SET cash = $2, updated_at = now() WHERE user_id = $1', [userId, newCash]);

    const newOwned = side === 'buy' ? ownedUnits + units : ownedUnits - units;
    if (newOwned > 1e-9) {
      await client.query(
        `INSERT INTO commodity_holdings (user_id, commodity_id, units) VALUES ($1, $2, $3)
         ON CONFLICT (user_id, commodity_id) DO UPDATE SET units = $3`,
        [userId, commodityId, newOwned]
      );
    } else {
      await client.query('DELETE FROM commodity_holdings WHERE user_id = $1 AND commodity_id = $2', [userId, commodityId]);
    }

    const rawImpact = (totalCost / COMMODITY_DEPTH_NOTIONAL) * COMMODITY_IMPACT_SENSITIVITY;
    const impact = clampImpact(side === 'buy' ? rawImpact : -rawImpact);
    const newPrice = Math.max(0.05, price * (1 + impact));
    await client.query('UPDATE commodities SET price = $2 WHERE id = $1', [commodityId, newPrice]);

    await client.query(
      'INSERT INTO trades (user_id, instrument_type, instrument_id, side, quantity, price) VALUES ($1, $2, $3, $4, $5, $6)',
      [userId, 'commodity', commodityId, side, units, price]
    );

    await client.query('COMMIT');
    return { instrumentType: 'commodity', instrumentId: commodityId, side, quantity: units, executedPrice: price, newPrice, totalCost, cashAfter: newCash };
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

/** Bonds are priced by the duration model (see market/simulator.ts), not order flow — no price impact here. */
export async function executeBondTrade(userId: string, bondId: string, side: 'buy' | 'sell', units: number): Promise<TradeResult> {
  if (!Number.isFinite(units) || units <= 0 || !Number.isInteger(units)) throw new HttpError(400, 'Units must be a positive whole number');

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const bondRes = await client.query('SELECT price FROM bonds WHERE id = $1 FOR UPDATE', [bondId]);
    if (bondRes.rows.length === 0) throw new HttpError(404, 'Unknown bond');
    const price = Number(bondRes.rows[0].price);

    const playerRes = await client.query('SELECT cash FROM player_state WHERE user_id = $1 FOR UPDATE', [userId]);
    if (playerRes.rows.length === 0) throw new HttpError(404, 'Player not found');
    const cash = Number(playerRes.rows[0].cash);

    const holdingRes = await client.query('SELECT units FROM bond_holdings WHERE user_id = $1 AND bond_id = $2 FOR UPDATE', [userId, bondId]);
    const ownedUnits = holdingRes.rows[0]?.units ?? 0;

    const totalCost = price * units;
    if (side === 'buy') {
      if (totalCost > cash) throw new HttpError(400, `Not enough cash — need $${totalCost.toFixed(2)}`);
    } else if (units > ownedUnits) {
      throw new HttpError(400, `You only own ${ownedUnits} units`);
    }

    const newCash = side === 'buy' ? cash - totalCost : cash + totalCost;
    await client.query('UPDATE player_state SET cash = $2, updated_at = now() WHERE user_id = $1', [userId, newCash]);

    const newOwned = side === 'buy' ? ownedUnits + units : ownedUnits - units;
    if (newOwned > 0) {
      await client.query(
        `INSERT INTO bond_holdings (user_id, bond_id, units) VALUES ($1, $2, $3)
         ON CONFLICT (user_id, bond_id) DO UPDATE SET units = $3`,
        [userId, bondId, newOwned]
      );
    } else {
      await client.query('DELETE FROM bond_holdings WHERE user_id = $1 AND bond_id = $2', [userId, bondId]);
    }

    await client.query(
      'INSERT INTO trades (user_id, instrument_type, instrument_id, side, quantity, price) VALUES ($1, $2, $3, $4, $5, $6)',
      [userId, 'bond', bondId, side, units, price]
    );

    await client.query('COMMIT');
    return { instrumentType: 'bond', instrumentId: bondId, side, quantity: units, executedPrice: price, newPrice: price, totalCost, cashAfter: newCash };
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

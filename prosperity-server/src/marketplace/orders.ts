import { v4 as uuidv4 } from 'uuid';
import { pool } from '../db/pool';
import { HttpError } from '../util/asyncHandler';
import { getOrder, Order } from './repository';

const STATUS_ORDER = ['placed', 'accepted', 'producing', 'delivered'] as const;

/**
 * Places an order: locks the listing and the buyer's wallet in one
 * transaction, debits coins immediately (held, not yet earned by the
 * seller), and decrements available quantity. The seller "hires" their own
 * business to fulfill it — see advanceOrderStatus.
 */
export async function placeOrder(buyerId: string, listingId: string, quantity: number): Promise<Order> {
  if (!Number.isInteger(quantity) || quantity <= 0) throw new HttpError(400, 'Quantity must be a positive whole number');

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const listingRes = await client.query('SELECT * FROM marketplace_listings WHERE id = $1 FOR UPDATE', [listingId]);
    if (listingRes.rows.length === 0) throw new HttpError(404, 'Listing not found');
    const listing = listingRes.rows[0];
    if (listing.status !== 'active') throw new HttpError(400, 'Listing is not active');
    if (listing.seller_id === buyerId) throw new HttpError(400, "You can't buy your own listing");
    if (listing.quantity_available < quantity) throw new HttpError(400, `Only ${listing.quantity_available} available`);

    const totalPriceCoins = Number(listing.price_coins) * quantity;

    const buyerRes = await client.query('SELECT wallet_coins FROM player_state WHERE user_id = $1 FOR UPDATE', [buyerId]);
    if (buyerRes.rows.length === 0) throw new HttpError(404, 'Buyer not found');
    const walletCoins = Number(buyerRes.rows[0].wallet_coins);
    if (walletCoins < totalPriceCoins) throw new HttpError(400, `Not enough coins — need ${totalPriceCoins}`);

    await client.query('UPDATE player_state SET wallet_coins = wallet_coins - $2 WHERE user_id = $1', [buyerId, totalPriceCoins]);

    const remaining = listing.quantity_available - quantity;
    await client.query('UPDATE marketplace_listings SET quantity_available = $2, status = $3 WHERE id = $1', [
      listingId,
      remaining,
      remaining > 0 ? listing.status : 'closed'
    ]);

    const orderId = uuidv4();
    const statusHistory = [{ status: 'placed', at: new Date().toISOString() }];
    await client.query(
      `INSERT INTO marketplace_orders (id, listing_id, buyer_id, seller_id, quantity, total_price_coins, status, status_history)
       VALUES ($1,$2,$3,$4,$5,$6,'placed',$7)`,
      [orderId, listingId, buyerId, listing.seller_id, quantity, totalPriceCoins, JSON.stringify(statusHistory)]
    );

    await client.query(
      'INSERT INTO wallet_transactions (user_id, amount_coins, reason, reference_id) VALUES ($1, $2, $3, $4)',
      [buyerId, -totalPriceCoins, 'order_payment', orderId]
    );

    await client.query('COMMIT');
    const order = await getOrder(orderId);
    if (!order) throw new HttpError(500, 'Order vanished after creation');
    return order;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

/** Seller-driven fulfillment: placed -> accepted -> producing -> delivered, one step at a time. */
export async function advanceOrderStatus(sellerId: string, orderId: string, nextStatus: string): Promise<Order> {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const orderRes = await client.query('SELECT * FROM marketplace_orders WHERE id = $1 FOR UPDATE', [orderId]);
    if (orderRes.rows.length === 0) throw new HttpError(404, 'Order not found');
    const order = orderRes.rows[0];
    if (order.seller_id !== sellerId) throw new HttpError(403, 'Not your order to fulfill');

    const currentIndex = STATUS_ORDER.indexOf(order.status);
    const nextIndex = STATUS_ORDER.indexOf(nextStatus as any);
    if (currentIndex === -1 || nextIndex !== currentIndex + 1) {
      throw new HttpError(400, `Cannot move from ${order.status} to ${nextStatus}`);
    }

    const history = [...order.status_history, { status: nextStatus, at: new Date().toISOString() }];
    await client.query('UPDATE marketplace_orders SET status = $2, status_history = $3, updated_at = now() WHERE id = $1', [
      orderId,
      nextStatus,
      JSON.stringify(history)
    ]);

    if (nextStatus === 'delivered') {
      const totalPriceCoins = Number(order.total_price_coins);
      await client.query('UPDATE player_state SET wallet_coins = wallet_coins + $2 WHERE user_id = $1', [sellerId, totalPriceCoins]);
      await client.query(
        'INSERT INTO wallet_transactions (user_id, amount_coins, reason, reference_id) VALUES ($1, $2, $3, $4)',
        [sellerId, totalPriceCoins, 'order_earning', orderId]
      );
    }

    await client.query('COMMIT');
    const updated = await getOrder(orderId);
    if (!updated) throw new HttpError(500, 'Order vanished after update');
    return updated;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

/** Only cancellable before the seller has accepted it; refunds the buyer in full. */
export async function cancelOrder(requesterId: string, orderId: string): Promise<Order> {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const orderRes = await client.query('SELECT * FROM marketplace_orders WHERE id = $1 FOR UPDATE', [orderId]);
    if (orderRes.rows.length === 0) throw new HttpError(404, 'Order not found');
    const order = orderRes.rows[0];
    if (order.buyer_id !== requesterId && order.seller_id !== requesterId) throw new HttpError(403, 'Not your order');
    if (order.status !== 'placed') throw new HttpError(400, 'Order already accepted; ask the seller to fulfill or contact them');

    await client.query('UPDATE player_state SET wallet_coins = wallet_coins + $2 WHERE user_id = $1', [order.buyer_id, order.total_price_coins]);
    await client.query(
      'INSERT INTO wallet_transactions (user_id, amount_coins, reason, reference_id) VALUES ($1, $2, $3, $4)',
      [order.buyer_id, order.total_price_coins, 'order_refund', orderId]
    );
    await client.query('UPDATE marketplace_listings SET quantity_available = quantity_available + $2, status = $3 WHERE id = $1', [
      order.listing_id,
      order.quantity,
      'active'
    ]);

    const history = [...order.status_history, { status: 'cancelled', at: new Date().toISOString() }];
    await client.query('UPDATE marketplace_orders SET status = $2, status_history = $3, updated_at = now() WHERE id = $1', [
      orderId,
      'cancelled',
      JSON.stringify(history)
    ]);

    await client.query('COMMIT');
    const updated = await getOrder(orderId);
    if (!updated) throw new HttpError(500, 'Order vanished after update');
    return updated;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

import { v4 as uuidv4 } from 'uuid';
import { pool } from '../db/pool';

export interface Listing {
  id: string;
  sellerId: string;
  sellerUsername: string;
  businessId: string | null;
  title: string;
  description: string;
  priceCoins: number;
  quantityAvailable: number;
  status: 'active' | 'paused' | 'closed';
}

export interface Order {
  id: string;
  listingId: string;
  listingTitle: string;
  buyerId: string;
  buyerUsername: string;
  sellerId: string;
  sellerUsername: string;
  quantity: number;
  totalPriceCoins: number;
  status: 'placed' | 'accepted' | 'producing' | 'delivered' | 'cancelled';
  statusHistory: { status: string; at: string }[];
}

function rowToListing(r: any): Listing {
  return {
    id: r.id,
    sellerId: r.seller_id,
    sellerUsername: r.seller_username,
    businessId: r.business_id,
    title: r.title,
    description: r.description,
    priceCoins: Number(r.price_coins),
    quantityAvailable: r.quantity_available,
    status: r.status
  };
}

function rowToOrder(r: any): Order {
  return {
    id: r.id,
    listingId: r.listing_id,
    listingTitle: r.listing_title,
    buyerId: r.buyer_id,
    buyerUsername: r.buyer_username,
    sellerId: r.seller_id,
    sellerUsername: r.seller_username,
    quantity: r.quantity,
    totalPriceCoins: Number(r.total_price_coins),
    status: r.status,
    statusHistory: r.status_history
  };
}

export async function createListing(sellerId: string, businessId: string | null, title: string, description: string, priceCoins: number, quantity: number): Promise<Listing> {
  const id = uuidv4();
  await pool.query(
    'INSERT INTO marketplace_listings (id, seller_id, business_id, title, description, price_coins, quantity_available) VALUES ($1,$2,$3,$4,$5,$6,$7)',
    [id, sellerId, businessId, title, description, priceCoins, quantity]
  );
  return getListing(id) as Promise<Listing>;
}

export async function getListing(id: string): Promise<Listing | null> {
  const { rows } = await pool.query(
    `SELECT l.*, u.username AS seller_username FROM marketplace_listings l JOIN users u ON u.id = l.seller_id WHERE l.id = $1`,
    [id]
  );
  return rows.length ? rowToListing(rows[0]) : null;
}

export async function listActiveListings(excludeUserId?: string): Promise<Listing[]> {
  const { rows } = excludeUserId
    ? await pool.query(
        `SELECT l.*, u.username AS seller_username FROM marketplace_listings l JOIN users u ON u.id = l.seller_id
         WHERE l.status = 'active' AND l.quantity_available > 0 AND l.seller_id != $1 ORDER BY l.created_at DESC LIMIT 100`,
        [excludeUserId]
      )
    : await pool.query(
        `SELECT l.*, u.username AS seller_username FROM marketplace_listings l JOIN users u ON u.id = l.seller_id
         WHERE l.status = 'active' AND l.quantity_available > 0 ORDER BY l.created_at DESC LIMIT 100`
      );
  return rows.map(rowToListing);
}

export async function listMyListings(sellerId: string): Promise<Listing[]> {
  const { rows } = await pool.query(
    `SELECT l.*, u.username AS seller_username FROM marketplace_listings l JOIN users u ON u.id = l.seller_id
     WHERE l.seller_id = $1 ORDER BY l.created_at DESC`,
    [sellerId]
  );
  return rows.map(rowToListing);
}

export async function updateListing(id: string, fields: Record<string, unknown>): Promise<void> {
  const keys = Object.keys(fields);
  if (keys.length === 0) return;
  const setClauses = keys.map((k, i) => `${k} = $${i + 2}`).join(', ');
  await pool.query(`UPDATE marketplace_listings SET ${setClauses} WHERE id = $1`, [id, ...keys.map((k) => fields[k])]);
}

export async function getOrder(id: string): Promise<Order | null> {
  const { rows } = await pool.query(
    `SELECT o.*, l.title AS listing_title, bu.username AS buyer_username, su.username AS seller_username
     FROM marketplace_orders o
     JOIN marketplace_listings l ON l.id = o.listing_id
     JOIN users bu ON bu.id = o.buyer_id
     JOIN users su ON su.id = o.seller_id
     WHERE o.id = $1`,
    [id]
  );
  return rows.length ? rowToOrder(rows[0]) : null;
}

export async function listOrdersForBuyer(buyerId: string): Promise<Order[]> {
  const { rows } = await pool.query(
    `SELECT o.*, l.title AS listing_title, bu.username AS buyer_username, su.username AS seller_username
     FROM marketplace_orders o
     JOIN marketplace_listings l ON l.id = o.listing_id
     JOIN users bu ON bu.id = o.buyer_id
     JOIN users su ON su.id = o.seller_id
     WHERE o.buyer_id = $1 ORDER BY o.created_at DESC`,
    [buyerId]
  );
  return rows.map(rowToOrder);
}

export async function listOrdersForSeller(sellerId: string): Promise<Order[]> {
  const { rows } = await pool.query(
    `SELECT o.*, l.title AS listing_title, bu.username AS buyer_username, su.username AS seller_username
     FROM marketplace_orders o
     JOIN marketplace_listings l ON l.id = o.listing_id
     JOIN users bu ON bu.id = o.buyer_id
     JOIN users su ON su.id = o.seller_id
     WHERE o.seller_id = $1 ORDER BY o.created_at DESC`,
    [sellerId]
  );
  return rows.map(rowToOrder);
}

export async function getWalletBalance(userId: string): Promise<number> {
  const { rows } = await pool.query('SELECT wallet_coins FROM player_state WHERE user_id = $1', [userId]);
  return Number(rows[0]?.wallet_coins ?? 0);
}

export async function getWalletHistory(userId: string, limit = 50) {
  const { rows } = await pool.query(
    'SELECT id, amount_coins, reason, reference_id, created_at FROM wallet_transactions WHERE user_id = $1 ORDER BY created_at DESC LIMIT $2',
    [userId, limit]
  );
  return rows.map((r) => ({ id: r.id, amountCoins: Number(r.amount_coins), reason: r.reason, referenceId: r.reference_id, createdAt: r.created_at }));
}

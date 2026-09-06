import { v4 as uuidv4 } from 'uuid';
import { pool } from '../db/pool';
import { BusinessRecord, BusinessType } from './model';

function rowToRecord(r: any): BusinessRecord {
  return {
    id: r.id,
    userId: r.user_id,
    type: r.type,
    name: r.name,
    level: r.level,
    employees: r.employees,
    pricePointMultiplier: Number(r.price_point_multiplier),
    reputation: Number(r.reputation),
    advertisingBudgetMonthly: Number(r.advertising_budget_monthly),
    competitionPressure: Number(r.competition_pressure),
    businessCash: Number(r.business_cash),
    loanBalance: Number(r.loan_balance),
    loanInterestRate: Number(r.loan_interest_rate),
    status: r.status
  };
}

/** Only active businesses are simulated each tick — closed ones are frozen in place. */
export async function getAllBusinesses(): Promise<BusinessRecord[]> {
  const { rows } = await pool.query("SELECT * FROM businesses WHERE status = 'active'");
  return rows.map(rowToRecord);
}

export async function getBusiness(id: string): Promise<BusinessRecord | null> {
  const { rows } = await pool.query('SELECT * FROM businesses WHERE id = $1', [id]);
  return rows.length ? rowToRecord(rows[0]) : null;
}

export async function getBusinessesForUser(userId: string, includeClosed = false) {
  const { rows } = includeClosed
    ? await pool.query('SELECT * FROM businesses WHERE user_id = $1 ORDER BY created_at', [userId])
    : await pool.query("SELECT * FROM businesses WHERE user_id = $1 AND status = 'active' ORDER BY created_at", [userId]);
  return rows.map(rowToRecord);
}

export async function createBusiness(userId: string, type: BusinessType, name: string): Promise<BusinessRecord> {
  const id = uuidv4();
  const { rows } = await pool.query(
    'INSERT INTO businesses (id, user_id, type, name) VALUES ($1, $2, $3, $4) RETURNING *',
    [id, userId, type, name]
  );
  return rowToRecord(rows[0]);
}

export async function saveBusinessAfterTick(business: BusinessRecord, month: number, revenue: number, expenses: number, profit: number): Promise<void> {
  await pool.query(
    `UPDATE businesses SET business_cash = $2, reputation = $3, competition_pressure = $4 WHERE id = $1`,
    [business.id, business.businessCash, business.reputation, business.competitionPressure]
  );
  await pool.query(
    'INSERT INTO business_history (business_id, month, revenue, expenses, profit) VALUES ($1, $2, $3, $4, $5)',
    [business.id, month, revenue, expenses, profit]
  );
}

/** Soft-close so business_history (and the record itself) survives for the player to review. */
export async function closeBusiness(id: string, reason: 'closed_bankrupt' | 'closed_voluntary'): Promise<void> {
  await pool.query("UPDATE businesses SET status = $2, closed_at = now() WHERE id = $1", [id, reason]);
}

export async function getBusinessHistory(businessId: string, limit = 24) {
  const { rows } = await pool.query(
    'SELECT month, revenue, expenses, profit FROM business_history WHERE business_id = $1 ORDER BY month DESC LIMIT $2',
    [businessId, limit]
  );
  return rows.reverse().map((r) => ({ month: r.month, revenue: Number(r.revenue), expenses: Number(r.expenses), profit: Number(r.profit) }));
}

export async function updateBusinessFields(id: string, fields: Record<string, unknown>): Promise<void> {
  const keys = Object.keys(fields);
  if (keys.length === 0) return;
  const setClauses = keys.map((k, i) => `${k} = $${i + 2}`).join(', ');
  await pool.query(`UPDATE businesses SET ${setClauses} WHERE id = $1`, [id, ...keys.map((k) => fields[k])]);
}

import { pool } from '../db/pool';
import { MaturedBond } from '../market/simulator';

/** Pays every holder of a matured bond its face value and clears the holding. */
export async function settleMaturedBonds(matured: MaturedBond[]): Promise<void> {
  for (const bond of matured) {
    const holders = await pool.query('SELECT user_id, units FROM bond_holdings WHERE bond_id = $1', [bond.bondId]);
    for (const row of holders.rows) {
      // Bond payouts settle in in-game cash, a different ledger from the
      // real-money-purchased "coins" used by the marketplace — see README.
      const payout = row.units * bond.faceValuePerUnit;
      await pool.query('UPDATE player_state SET cash = cash + $2 WHERE user_id = $1', [row.user_id, payout]);
    }
    await pool.query('DELETE FROM bond_holdings WHERE bond_id = $1', [bond.bondId]);
  }
}

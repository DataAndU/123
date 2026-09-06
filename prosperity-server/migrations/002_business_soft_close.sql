-- Businesses are soft-closed (bankruptcy or voluntary) instead of hard-deleted,
-- so business_history survives for the player to review what happened.
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'active';
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_businesses_status ON businesses(status);

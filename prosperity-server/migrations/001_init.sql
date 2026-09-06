-- Prosperity Online: initial schema

CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY,
  username TEXT UNIQUE NOT NULL,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  display_name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS friendships (
  id UUID PRIMARY KEY,
  requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  addressee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL CHECK (status IN ('pending','accepted','declined','blocked')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  responded_at TIMESTAMPTZ,
  UNIQUE(requester_id, addressee_id)
);

CREATE TABLE IF NOT EXISTS chat_messages (
  id UUID PRIMARY KEY,
  channel TEXT NOT NULL,
  sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  sender_username TEXT NOT NULL,
  body TEXT NOT NULL,
  sent_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_channel_time ON chat_messages(channel, sent_at);

CREATE TABLE IF NOT EXISTS player_state (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  cash DOUBLE PRECISION NOT NULL DEFAULT 2500,
  bank_savings DOUBLE PRECISION NOT NULL DEFAULT 0,
  wallet_coins BIGINT NOT NULL DEFAULT 500,
  current_job_id TEXT,
  job_months_held INT NOT NULL DEFAULT 0,
  education_level TEXT NOT NULL DEFAULT 'HIGH_SCHOOL',
  education_in_progress_id TEXT,
  education_months_remaining INT NOT NULL DEFAULT 0,
  skills JSONB NOT NULL DEFAULT '{"BUSINESS":5,"FINANCE":5,"TECH":5,"MARKETING":5,"LABOR":5}',
  lifestyle_tier TEXT NOT NULL DEFAULT 'SPARTAN',
  happiness DOUBLE PRECISION NOT NULL DEFAULT 65,
  health DOUBLE PRECISION NOT NULL DEFAULT 80,
  reputation DOUBLE PRECISION NOT NULL DEFAULT 50,
  foreign_currency_holdings DOUBLE PRECISION NOT NULL DEFAULT 0,
  months_since_negative_cash INT NOT NULL DEFAULT 0,
  achievements_unlocked JSONB NOT NULL DEFAULT '[]',
  net_worth DOUBLE PRECISION NOT NULL DEFAULT 2500,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS loans (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type TEXT NOT NULL,
  principal_remaining DOUBLE PRECISION NOT NULL,
  annual_rate DOUBLE PRECISION NOT NULL,
  monthly_payment DOUBLE PRECISION NOT NULL,
  original_principal DOUBLE PRECISION NOT NULL,
  term_months_remaining INT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS properties (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  purchase_price DOUBLE PRECISION NOT NULL,
  purchase_housing_index DOUBLE PRECISION NOT NULL,
  mortgage_balance DOUBLE PRECISION NOT NULL,
  mortgage_rate DOUBLE PRECISION NOT NULL,
  monthly_rent_income DOUBLE PRECISION NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS businesses (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type TEXT NOT NULL,
  name TEXT NOT NULL,
  level INT NOT NULL DEFAULT 1,
  employees INT NOT NULL DEFAULT 1,
  price_point_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.0,
  reputation DOUBLE PRECISION NOT NULL DEFAULT 55,
  advertising_budget_monthly DOUBLE PRECISION NOT NULL DEFAULT 0,
  competition_pressure DOUBLE PRECISION NOT NULL DEFAULT 10,
  business_cash DOUBLE PRECISION NOT NULL DEFAULT 0,
  loan_balance DOUBLE PRECISION NOT NULL DEFAULT 0,
  loan_interest_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS business_history (
  id BIGSERIAL PRIMARY KEY,
  business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  month INT NOT NULL,
  revenue DOUBLE PRECISION NOT NULL,
  expenses DOUBLE PRECISION NOT NULL,
  profit DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_holdings (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  stock_id TEXT NOT NULL,
  shares INT NOT NULL,
  PRIMARY KEY (user_id, stock_id)
);
CREATE TABLE IF NOT EXISTS bond_holdings (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  bond_id TEXT NOT NULL,
  units INT NOT NULL,
  PRIMARY KEY (user_id, bond_id)
);
CREATE TABLE IF NOT EXISTS commodity_holdings (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  commodity_id TEXT NOT NULL,
  units DOUBLE PRECISION NOT NULL,
  PRIMARY KEY (user_id, commodity_id)
);

CREATE TABLE IF NOT EXISTS economy_state (
  id INT PRIMARY KEY DEFAULT 1,
  month INT NOT NULL DEFAULT 0,
  phase TEXT NOT NULL DEFAULT 'EXPANSION',
  phase_months_elapsed INT NOT NULL DEFAULT 0,
  gdp_growth_rate DOUBLE PRECISION NOT NULL DEFAULT 2.5,
  inflation_rate DOUBLE PRECISION NOT NULL DEFAULT 2.0,
  unemployment_rate DOUBLE PRECISION NOT NULL DEFAULT 5.0,
  interest_rate DOUBLE PRECISION NOT NULL DEFAULT 3.0,
  consumer_confidence DOUBLE PRECISION NOT NULL DEFAULT 60.0,
  money_supply_growth DOUBLE PRECISION NOT NULL DEFAULT 4.0,
  price_level_index DOUBLE PRECISION NOT NULL DEFAULT 100.0,
  income_tax_rate DOUBLE PRECISION NOT NULL DEFAULT 18.0,
  corporate_tax_surcharge DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  data_source TEXT NOT NULL DEFAULT 'simulated',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS economy_history (
  month INT PRIMARY KEY,
  gdp_growth_rate DOUBLE PRECISION NOT NULL,
  inflation_rate DOUBLE PRECISION NOT NULL,
  unemployment_rate DOUBLE PRECISION NOT NULL,
  interest_rate DOUBLE PRECISION NOT NULL,
  consumer_confidence DOUBLE PRECISION NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS stocks (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  sector TEXT NOT NULL,
  real_ticker TEXT,
  price DOUBLE PRECISION NOT NULL,
  beta DOUBLE PRECISION NOT NULL,
  volatility DOUBLE PRECISION NOT NULL,
  dividend_yield_annual DOUBLE PRECISION NOT NULL
);
CREATE TABLE IF NOT EXISTS stock_price_history (
  stock_id TEXT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
  month INT NOT NULL,
  price DOUBLE PRECISION NOT NULL,
  PRIMARY KEY (stock_id, month)
);

CREATE TABLE IF NOT EXISTS bonds (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  issuer TEXT NOT NULL,
  face_value DOUBLE PRECISION NOT NULL,
  coupon_rate DOUBLE PRECISION NOT NULL,
  original_term_months INT NOT NULL,
  months_remaining INT NOT NULL,
  price DOUBLE PRECISION NOT NULL,
  risk_premium DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS commodities (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  real_symbol TEXT,
  price DOUBLE PRECISION NOT NULL,
  safe_haven_factor DOUBLE PRECISION NOT NULL,
  volatility DOUBLE PRECISION NOT NULL
);
CREATE TABLE IF NOT EXISTS commodity_price_history (
  commodity_id TEXT NOT NULL REFERENCES commodities(id) ON DELETE CASCADE,
  month INT NOT NULL,
  price DOUBLE PRECISION NOT NULL,
  PRIMARY KEY (commodity_id, month)
);

CREATE TABLE IF NOT EXISTS housing_market (
  id INT PRIMARY KEY DEFAULT 1,
  price_index DOUBLE PRECISION NOT NULL DEFAULT 100.0,
  CHECK (id = 1)
);
CREATE TABLE IF NOT EXISTS housing_history (
  month INT PRIMARY KEY,
  price_index DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS currency_market (
  id INT PRIMARY KEY DEFAULT 1,
  exchange_rate DOUBLE PRECISION NOT NULL DEFAULT 1.0,
  CHECK (id = 1)
);
CREATE TABLE IF NOT EXISTS currency_history (
  month INT PRIMARY KEY,
  exchange_rate DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS event_log (
  id BIGSERIAL PRIMARY KEY,
  month INT NOT NULL,
  event_id TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  educational_note TEXT NOT NULL,
  triggered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS trades (
  id BIGSERIAL PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  instrument_type TEXT NOT NULL,
  instrument_id TEXT NOT NULL,
  side TEXT NOT NULL,
  quantity DOUBLE PRECISION NOT NULL,
  price DOUBLE PRECISION NOT NULL,
  executed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_trades_instrument_time ON trades(instrument_id, executed_at);

CREATE TABLE IF NOT EXISTS marketplace_listings (
  id UUID PRIMARY KEY,
  seller_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  business_id UUID REFERENCES businesses(id) ON DELETE SET NULL,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  price_coins BIGINT NOT NULL,
  quantity_available INT NOT NULL,
  status TEXT NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_listings_status ON marketplace_listings(status);

CREATE TABLE IF NOT EXISTS marketplace_orders (
  id UUID PRIMARY KEY,
  listing_id UUID NOT NULL REFERENCES marketplace_listings(id) ON DELETE CASCADE,
  buyer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  seller_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  quantity INT NOT NULL,
  total_price_coins BIGINT NOT NULL,
  status TEXT NOT NULL DEFAULT 'placed',
  status_history JSONB NOT NULL DEFAULT '[]',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_orders_buyer ON marketplace_orders(buyer_id);
CREATE INDEX IF NOT EXISTS idx_orders_seller ON marketplace_orders(seller_id);

CREATE TABLE IF NOT EXISTS wallet_transactions (
  id BIGSERIAL PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  amount_coins BIGINT NOT NULL,
  reason TEXT NOT NULL,
  reference_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS iap_purchases (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL,
  purchase_token TEXT NOT NULL UNIQUE,
  coins_credited BIGINT NOT NULL,
  verified BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO economy_state (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
INSERT INTO housing_market (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
INSERT INTO currency_market (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

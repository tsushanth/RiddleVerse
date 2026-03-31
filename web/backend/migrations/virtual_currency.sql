-- Virtual Currency Tables Migration
-- Run this in Supabase SQL Editor

-- Table: user_coins
-- Stores each user's current coin balance
CREATE TABLE IF NOT EXISTS user_coins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT UNIQUE NOT NULL,
    balance INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
    total_purchased INTEGER NOT NULL DEFAULT 0,
    total_spent INTEGER NOT NULL DEFAULT 0,
    total_earned INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Table: coin_transactions
-- Immutable ledger of all coin movements
CREATE TABLE IF NOT EXISTS coin_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    amount INTEGER NOT NULL, -- positive = credit, negative = debit
    type TEXT NOT NULL CHECK (type IN ('purchase', 'spend', 'earn', 'refund')),
    reason TEXT, -- e.g. 'continue_play', 'coin_pack_500', 'creator_earnings'
    game_id UUID, -- which game the transaction relates to (nullable)
    creator_id TEXT, -- the game creator who earns from this spend (nullable)
    platform TEXT, -- 'ios', 'android'
    iap_transaction_id TEXT, -- App Store / Play Store transaction ID (for purchases)
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Table: creator_earnings
-- Aggregated earnings for game creators (updated on each spend)
CREATE TABLE IF NOT EXISTS creator_earnings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id TEXT NOT NULL,
    game_id UUID NOT NULL,
    total_coins_earned INTEGER NOT NULL DEFAULT 0,
    total_plays_monetized INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(creator_id, game_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_user_coins_user ON user_coins(user_id);
CREATE INDEX IF NOT EXISTS idx_coin_transactions_user ON coin_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_coin_transactions_type ON coin_transactions(type);
CREATE INDEX IF NOT EXISTS idx_coin_transactions_game ON coin_transactions(game_id);
CREATE INDEX IF NOT EXISTS idx_coin_transactions_creator ON coin_transactions(creator_id);
CREATE INDEX IF NOT EXISTS idx_coin_transactions_iap ON coin_transactions(iap_transaction_id);
CREATE INDEX IF NOT EXISTS idx_creator_earnings_creator ON creator_earnings(creator_id);
CREATE INDEX IF NOT EXISTS idx_creator_earnings_game ON creator_earnings(game_id);

-- Enable RLS
ALTER TABLE user_coins ENABLE ROW LEVEL SECURITY;
ALTER TABLE coin_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE creator_earnings ENABLE ROW LEVEL SECURITY;

-- Policies (backend uses service key, so allow all via anon for now)
CREATE POLICY "Allow all on user_coins" ON user_coins
    FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow all on coin_transactions" ON coin_transactions
    FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow all on creator_earnings" ON creator_earnings
    FOR ALL USING (true) WITH CHECK (true);

-- Trigger for auto-updating updated_at on user_coins
DROP TRIGGER IF EXISTS update_user_coins_updated_at ON user_coins;
CREATE TRIGGER update_user_coins_updated_at
    BEFORE UPDATE ON user_coins
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger for auto-updating updated_at on creator_earnings
DROP TRIGGER IF EXISTS update_creator_earnings_updated_at ON creator_earnings;
CREATE TRIGGER update_creator_earnings_updated_at
    BEFORE UPDATE ON creator_earnings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- RPC function: Atomically spend coins (prevents race conditions)
CREATE OR REPLACE FUNCTION spend_coins(
    p_user_id TEXT,
    p_amount INTEGER,
    p_reason TEXT,
    p_game_id UUID DEFAULT NULL,
    p_creator_id TEXT DEFAULT NULL,
    p_platform TEXT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
    v_balance INTEGER;
    v_tx_id UUID;
    v_creator_share INTEGER;
BEGIN
    -- Lock the row for update
    SELECT balance INTO v_balance
    FROM user_coins
    WHERE user_id = p_user_id
    FOR UPDATE;

    IF v_balance IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'User not found');
    END IF;

    IF v_balance < p_amount THEN
        RETURN jsonb_build_object('success', false, 'error', 'Insufficient coins', 'balance', v_balance);
    END IF;

    -- Deduct coins
    UPDATE user_coins
    SET balance = balance - p_amount,
        total_spent = total_spent + p_amount
    WHERE user_id = p_user_id;

    -- Record transaction
    INSERT INTO coin_transactions (user_id, amount, type, reason, game_id, creator_id, platform)
    VALUES (p_user_id, -p_amount, 'spend', p_reason, p_game_id, p_creator_id, p_platform)
    RETURNING id INTO v_tx_id;

    -- Credit creator (70% share)
    IF p_creator_id IS NOT NULL AND p_game_id IS NOT NULL THEN
        v_creator_share := GREATEST(1, (p_amount * 70) / 100);

        -- Add to creator's coin balance
        INSERT INTO user_coins (user_id, balance, total_earned)
        VALUES (p_creator_id, v_creator_share, v_creator_share)
        ON CONFLICT (user_id) DO UPDATE
        SET balance = user_coins.balance + v_creator_share,
            total_earned = user_coins.total_earned + v_creator_share;

        -- Record creator earning transaction
        INSERT INTO coin_transactions (user_id, amount, type, reason, game_id, creator_id, platform)
        VALUES (p_creator_id, v_creator_share, 'earn', 'creator_earnings', p_game_id, p_creator_id, p_platform);

        -- Update aggregated creator earnings
        INSERT INTO creator_earnings (creator_id, game_id, total_coins_earned, total_plays_monetized)
        VALUES (p_creator_id, p_game_id, v_creator_share, 1)
        ON CONFLICT (creator_id, game_id) DO UPDATE
        SET total_coins_earned = creator_earnings.total_coins_earned + v_creator_share,
            total_plays_monetized = creator_earnings.total_plays_monetized + 1;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'transaction_id', v_tx_id,
        'new_balance', v_balance - p_amount,
        'creator_share', COALESCE(v_creator_share, 0)
    );
END;
$$ LANGUAGE plpgsql;

-- RPC function: Add coins after IAP purchase
CREATE OR REPLACE FUNCTION add_purchased_coins(
    p_user_id TEXT,
    p_amount INTEGER,
    p_reason TEXT,
    p_platform TEXT,
    p_iap_transaction_id TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_existing_tx UUID;
    v_new_balance INTEGER;
BEGIN
    -- Check for duplicate transaction
    SELECT id INTO v_existing_tx
    FROM coin_transactions
    WHERE iap_transaction_id = p_iap_transaction_id;

    IF v_existing_tx IS NOT NULL THEN
        RETURN jsonb_build_object('success', true, 'message', 'Transaction already processed', 'duplicate', true);
    END IF;

    -- Add coins (upsert user_coins row)
    INSERT INTO user_coins (user_id, balance, total_purchased)
    VALUES (p_user_id, p_amount, p_amount)
    ON CONFLICT (user_id) DO UPDATE
    SET balance = user_coins.balance + p_amount,
        total_purchased = user_coins.total_purchased + p_amount
    RETURNING balance INTO v_new_balance;

    -- Record transaction
    INSERT INTO coin_transactions (user_id, amount, type, reason, platform, iap_transaction_id)
    VALUES (p_user_id, p_amount, 'purchase', p_reason, p_platform, p_iap_transaction_id);

    RETURN jsonb_build_object('success', true, 'new_balance', v_new_balance);
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- Creator Payouts Migration
-- Adds Stripe Connect integration for creator cashouts
-- Min threshold: 1000 coins, Rate: 1 coin = $0.007 USD
-- Date: 2026-02-14
-- ============================================================

-- Step 1: Alter coin_transactions CHECK constraint to allow 'payout' type
ALTER TABLE coin_transactions DROP CONSTRAINT IF EXISTS coin_transactions_type_check;
ALTER TABLE coin_transactions ADD CONSTRAINT coin_transactions_type_check
    CHECK (type IN ('purchase', 'spend', 'earn', 'refund', 'payout'));

-- Step 2: creator_stripe_accounts table
CREATE TABLE IF NOT EXISTS creator_stripe_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id TEXT UNIQUE NOT NULL,
    stripe_account_id TEXT UNIQUE NOT NULL,
    onboarding_complete BOOLEAN NOT NULL DEFAULT false,
    charges_enabled BOOLEAN NOT NULL DEFAULT false,
    payouts_enabled BOOLEAN NOT NULL DEFAULT false,
    details_submitted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_creator_stripe_creator ON creator_stripe_accounts(creator_id);
CREATE INDEX IF NOT EXISTS idx_creator_stripe_account ON creator_stripe_accounts(stripe_account_id);

ALTER TABLE creator_stripe_accounts ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all on creator_stripe_accounts" ON creator_stripe_accounts
    FOR ALL USING (true) WITH CHECK (true);

DROP TRIGGER IF EXISTS update_creator_stripe_accounts_updated_at ON creator_stripe_accounts;
CREATE TRIGGER update_creator_stripe_accounts_updated_at
    BEFORE UPDATE ON creator_stripe_accounts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Step 3: payout_requests table
CREATE TABLE IF NOT EXISTS payout_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id TEXT NOT NULL,
    coins_amount INTEGER NOT NULL CHECK (coins_amount > 0),
    usd_amount NUMERIC(10, 2) NOT NULL CHECK (usd_amount > 0),
    stripe_transfer_id TEXT,
    status TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'processing', 'completed', 'failed')),
    failure_reason TEXT,
    idempotency_key TEXT UNIQUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_payout_requests_creator ON payout_requests(creator_id);
CREATE INDEX IF NOT EXISTS idx_payout_requests_status ON payout_requests(status);
CREATE INDEX IF NOT EXISTS idx_payout_requests_stripe ON payout_requests(stripe_transfer_id);

ALTER TABLE payout_requests ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all on payout_requests" ON payout_requests
    FOR ALL USING (true) WITH CHECK (true);

-- Step 4: RPC function - create_payout_request
-- Atomically deducts coins from creator balance and creates pending payout
CREATE OR REPLACE FUNCTION create_payout_request(
    p_creator_id TEXT,
    p_coins_amount INTEGER,
    p_usd_amount NUMERIC,
    p_idempotency_key TEXT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
    v_balance INTEGER;
    v_request_id UUID;
    v_existing_request UUID;
BEGIN
    -- Check for duplicate request via idempotency key
    IF p_idempotency_key IS NOT NULL THEN
        SELECT id INTO v_existing_request
        FROM payout_requests
        WHERE idempotency_key = p_idempotency_key;

        IF v_existing_request IS NOT NULL THEN
            RETURN jsonb_build_object(
                'success', true,
                'duplicate', true,
                'request_id', v_existing_request
            );
        END IF;
    END IF;

    -- Lock the creator's coin balance row
    SELECT balance INTO v_balance
    FROM user_coins
    WHERE user_id = p_creator_id
    FOR UPDATE;

    IF v_balance IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Creator not found');
    END IF;

    IF v_balance < p_coins_amount THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Insufficient balance',
            'balance', v_balance,
            'requested', p_coins_amount
        );
    END IF;

    -- Deduct coins from balance
    UPDATE user_coins
    SET balance = balance - p_coins_amount
    WHERE user_id = p_creator_id;

    -- Create payout request record
    INSERT INTO payout_requests (creator_id, coins_amount, usd_amount, status, idempotency_key)
    VALUES (p_creator_id, p_coins_amount, p_usd_amount, 'pending', p_idempotency_key)
    RETURNING id INTO v_request_id;

    -- Record the debit transaction in the ledger
    INSERT INTO coin_transactions (user_id, amount, type, reason)
    VALUES (p_creator_id, -p_coins_amount, 'payout', 'payout_request');

    RETURN jsonb_build_object(
        'success', true,
        'request_id', v_request_id,
        'new_balance', v_balance - p_coins_amount
    );
END;
$$ LANGUAGE plpgsql;

-- Step 5: RPC function - complete_payout_request
CREATE OR REPLACE FUNCTION complete_payout_request(
    p_request_id UUID,
    p_stripe_transfer_id TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_status TEXT;
BEGIN
    SELECT status INTO v_status FROM payout_requests WHERE id = p_request_id FOR UPDATE;

    IF v_status IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Request not found');
    END IF;

    IF v_status NOT IN ('pending', 'processing') THEN
        RETURN jsonb_build_object('success', false, 'error', 'Request not in completable state', 'current_status', v_status);
    END IF;

    UPDATE payout_requests
    SET status = 'completed',
        stripe_transfer_id = p_stripe_transfer_id,
        completed_at = NOW()
    WHERE id = p_request_id;

    RETURN jsonb_build_object('success', true);
END;
$$ LANGUAGE plpgsql;

-- Step 6: RPC function - fail_payout_request
-- On failure, refunds the coins back to the creator
CREATE OR REPLACE FUNCTION fail_payout_request(
    p_request_id UUID,
    p_reason TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_creator_id TEXT;
    v_coins INTEGER;
    v_status TEXT;
BEGIN
    SELECT creator_id, coins_amount, status
    INTO v_creator_id, v_coins, v_status
    FROM payout_requests
    WHERE id = p_request_id
    FOR UPDATE;

    IF v_creator_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Request not found');
    END IF;

    IF v_status NOT IN ('pending', 'processing') THEN
        RETURN jsonb_build_object('success', false, 'error', 'Request not in failable state', 'current_status', v_status);
    END IF;

    -- Mark request as failed
    UPDATE payout_requests
    SET status = 'failed',
        failure_reason = p_reason,
        completed_at = NOW()
    WHERE id = p_request_id;

    -- Refund coins back to creator balance
    UPDATE user_coins
    SET balance = balance + v_coins
    WHERE user_id = v_creator_id;

    -- Record refund transaction in ledger
    INSERT INTO coin_transactions (user_id, amount, type, reason)
    VALUES (v_creator_id, v_coins, 'refund', 'payout_failed: ' || COALESCE(p_reason, 'unknown'));

    RETURN jsonb_build_object('success', true, 'refunded_coins', v_coins);
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- Verification queries (run after migration)
-- ============================================================
-- SELECT conname, consrc FROM pg_constraint WHERE conname = 'coin_transactions_type_check';
-- SELECT * FROM creator_stripe_accounts LIMIT 1;
-- SELECT * FROM payout_requests LIMIT 1;

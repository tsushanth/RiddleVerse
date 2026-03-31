-- Fix: Creator share percentage 70% -> 55%
-- The JS constant CREATOR_SHARE_PCT was changed to 55 but the SQL function
-- still used 70%. This migration aligns the SQL function with the intended split.
-- Date: 2026-02-14

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

    -- Credit creator (55% share)
    IF p_creator_id IS NOT NULL AND p_game_id IS NOT NULL THEN
        v_creator_share := GREATEST(1, (p_amount * 55) / 100);

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

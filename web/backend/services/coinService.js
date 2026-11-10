// coinService.js - Virtual currency service
import { supabase } from '../config/database.js';
import { getDifficultyCost, MAX_DIFFICULTY_LEVEL } from './difficultyService.js';
import { getPlayPurchaseVerifier, PLAY_ORDER_ID_RE } from './playPurchaseVerifier.js';
import { readPolicy, createCounters, recentPurchases, logAnomaly } from './coinPurchasePolicy.js';
import { verifiedCaller } from '../middleware/callerIdentity.js';

const COIN_PACKS = {
    'com.kreativekoala.riddleverse.coins.100': { coins: 100, price: '$0.99' },
    'com.kreativekoala.riddleverse.coins.500': { coins: 500, price: '$3.99' },
    'com.kreativekoala.riddleverse.coins.1200': { coins: 1200, price: '$7.99' },
};

const CONTINUE_COST = 10; // coins to continue playing after game over
const CUSTOMIZE_COST = 10; // coins per game customization
const CREATOR_SHARE_PCT = 55; // creator gets 55% of spent coins
const AWARD_REASONS = { app_rating_reward: 100 }; // reason -> the only amount POST /api/coins/award will grant

/**
 * Get user's coin balance
 */
export async function getCoinBalance(req, res) {
    try {
        const { userId } = req.query;
        if (!userId) {
            return res.status(400).json({ error: 'userId is required' });
        }

        const { data, error } = await supabase
            .from('user_coins')
            .select('balance, total_purchased, total_spent, total_earned')
            .eq('user_id', userId)
            .single();

        if (error && error.code === 'PGRST116') {
            // No row yet — user has 0 coins
            return res.json({
                success: true,
                balance: 0,
                totalPurchased: 0,
                totalSpent: 0,
                totalEarned: 0
            });
        }

        if (error) {
            console.error('Error fetching coin balance:', error);
            return res.status(500).json({ error: 'Failed to fetch balance' });
        }

        res.json({
            success: true,
            balance: data.balance,
            totalPurchased: data.total_purchased,
            totalSpent: data.total_spent,
            totalEarned: data.total_earned
        });
    } catch (error) {
        console.error('Unexpected error in getCoinBalance:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
}

/**
 * Record a coin purchase.
 *
 * Fails closed: in the default `enforce` mode coins are credited only when the Google Play order is verified server side
 * (see services/playPurchaseVerifier.js and services/coinPurchasePolicy.js). The coin amount always comes from COIN_PACKS,
 * never from the request. Crediting is idempotent on the transaction id (add_purchased_coins).
 *
 * Shipped Android clients send no credential, so an Authorization header is optional: when present it must verify and match
 * userId; COIN_PURCHASE_REQUIRE_AUTH=true makes it mandatory once clients send it.
 */
export function createPurchaseHandler(deps = {}) {
    const d = {
        env: process.env,
        supabase,
        now: Date.now,
        verifier: null,
        counters: null,
        verifyCaller: null,
        ...deps,
    };
    const counters = d.counters || (d.counters = createCounters(d.now));
    const inflight = new Set();

    return async function purchaseCoinsHandler(req, res) {
        let lockKey = null;
        try {
            const policy = readPolicy(d.env);
            const body = req.body && typeof req.body === 'object' ? req.body : {};
            const { userId, productId, transactionId, platform } = body;
            const ip = String(req.ip || req.socket?.remoteAddress || 'unknown');

            if (!userId || !productId || !transactionId || !platform) {
                return res.status(400).json({
                    error: 'userId, productId, transactionId, and platform are required'
                });
            }
            if (typeof userId !== 'string' || userId.length > 128 ||
                typeof productId !== 'string' || typeof transactionId !== 'string' || transactionId.length > 128 ||
                typeof platform !== 'string') {
                return res.status(400).json({ error: 'Invalid purchase fields' });
            }

            const pack = COIN_PACKS[productId];
            if (!pack) {
                return res.status(400).json({
                    error: 'Invalid product ID',
                    validProducts: Object.keys(COIN_PACKS)
                });
            }

            if (platform !== 'android') {
                logAnomaly('unsupported_platform', { platform: String(platform).slice(0, 16), userId });
                return res.status(400).json({ error: 'purchase_not_verified', message: 'This platform cannot be verified yet.' });
            }

            // Optional identity: if a token is sent it must be valid and belong to userId.
            const hasAuth = typeof req.headers?.authorization === 'string' && req.headers.authorization.startsWith('Bearer ');
            if (hasAuth) {
                const caller = await (d.verifyCaller || verifiedCaller)(req);
                if (!caller) return res.status(401).json({ error: 'Invalid or expired credentials' });
                if (caller.uid !== userId) {
                    logAnomaly('uid_mismatch', { userId });
                    return res.status(403).json({ error: 'Credentials do not match userId' });
                }
            } else if (String(d.env.COIN_PURCHASE_REQUIRE_AUTH || '').toLowerCase() === 'true') {
                return res.status(401).json({ error: 'Authentication required' });
            }

            if (counters.ipOverLimit(ip, policy.ipClaimsPerHour)) {
                logAnomaly('ip_rate_limit', { ip, userId });
                res.setHeader('Retry-After', '3600');
                return res.status(429).json({ error: 'Too many purchase requests. Please try again later.' });
            }
            counters.recordIp(ip);

            if (inflight.has(transactionId)) {
                return res.status(409).json({ error: 'This purchase is already being processed' });
            }
            inflight.add(transactionId);
            lockKey = transactionId;

            if (policy.mode === 'enforce') {
                const verifier = d.verifier || getPlayPurchaseVerifier();
                if (!verifier.isConfigured()) {
                    logAnomaly('verifier_not_configured', { productId, userId, authed: hasAuth });
                    return res.status(503).json({
                        error: 'purchase_verification_unavailable',
                        retryable: true,
                        message: 'We could not confirm your purchase yet. Your coins will not be lost: please try again later.'
                    });
                }
                const v = await verifier.verifyOrder({ orderId: transactionId, productId }, { maxAgeMs: policy.maxOrderAgeMs });
                if (!v.ok && v.code === 'unavailable') {
                    logAnomaly('verifier_unavailable', { detail: v.detail, productId, userId, authed: hasAuth });
                    return res.status(503).json({
                        error: 'purchase_verification_unavailable',
                        retryable: true,
                        message: 'We could not confirm your purchase yet. Your coins will not be lost: please try again later.'
                    });
                }
                if (!v.ok) {
                    logAnomaly('purchase_not_verified', { code: v.code, productId, userId, authed: hasAuth, ip });
                    return res.status(400).json({ error: 'purchase_not_verified' });
                }
            } else {
                // interim: no receipt check; shape check, per-account and global limits, logging.
                if (!PLAY_ORDER_ID_RE.test(transactionId)) {
                    logAnomaly('bad_order_id_shape', { productId, userId, authed: hasAuth, ip });
                    return res.status(400).json({ error: 'purchase_not_verified' });
                }
                const recent = await recentPurchases(d.supabase, userId, d.now);
                if (recent === null) {
                    logAnomaly('ledger_unreadable', { userId });
                } else if (recent.count >= policy.accountPurchasesPerDay || recent.coins + pack.coins > policy.accountCoinsPerDay) {
                    logAnomaly('account_daily_limit', { userId, count: recent.count, coins: recent.coins, productId });
                    return res.status(429).json({ error: 'Daily purchase limit reached for this account. Please contact support if this is a mistake.' });
                }
                if (counters.globalWouldExceed(pack.coins, policy.globalCoinsPerDay)) {
                    logAnomaly('global_daily_ceiling', { userId, productId });
                    return res.status(503).json({ error: 'purchase_verification_unavailable', retryable: true });
                }
                logAnomaly('unverified_credit', { productId, userId, authed: hasAuth, ip, orderIdPrefix: transactionId.slice(0, 8) });
            }

            console.log(`[Coins] Purchase: ${userId.slice(0, 6)}… buying ${pack.coins} coins (${productId}) via ${platform} [${policy.mode}]`);

            const { data, error } = await d.supabase.rpc('add_purchased_coins', {
                p_user_id: userId,
                p_amount: pack.coins,
                p_reason: productId,
                p_platform: platform,
                p_iap_transaction_id: transactionId
            });

            if (error) {
                console.error('Error recording coin purchase:', error);
                return res.status(500).json({ error: 'Failed to record purchase' });
            }

            if (data.duplicate) {
                console.log(`[Coins] Duplicate transaction ${transactionId}, returning success`);
                return res.json({ success: true, message: 'Transaction already processed', balance: data.new_balance });
            }

            if (policy.mode !== 'enforce') counters.recordGlobal(pack.coins);
            console.log(`[Coins] Purchase complete: now ${data.new_balance} coins`);

            res.json({
                success: true,
                coinsAdded: pack.coins,
                newBalance: data.new_balance
            });
        } catch (error) {
            console.error('Unexpected error in purchaseCoins:', error);
            res.status(500).json({ error: 'Internal server error' });
        } finally {
            if (lockKey) inflight.delete(lockKey);
        }
    };
}

export const purchaseCoins = createPurchaseHandler();

/**
 * Spend coins (e.g. continue playing)
 */
export async function spendCoins(req, res) {
    try {
        const { userId, gameId, creatorId, reason, platform, difficultyLevel } = req.body;

        if (!userId || !reason) {
            return res.status(400).json({ error: 'userId and reason are required' });
        }

        // Determine cost based on reason
        let amount;
        // Creator gets their share on replay, customize, and harder_challenge
        // Owner customizing their own game should not send creatorId from the client
        let effectiveCreatorId = creatorId || null;
        // A creator's own spend never earns them a share (stops mint-and-recycle through their own game).
        if (effectiveCreatorId && effectiveCreatorId === userId) effectiveCreatorId = null;

        if (reason === 'harder_challenge') {
            if (!difficultyLevel || difficultyLevel < 2 || difficultyLevel > MAX_DIFFICULTY_LEVEL) {
                return res.status(400).json({ error: 'Invalid difficulty level (must be 2-5)' });
            }
            amount = getDifficultyCost(difficultyLevel);
            // Creator gets 55% — their game inspired the harder play
        } else if (reason === 'customize') {
            amount = CUSTOMIZE_COST;
            // Creator gets 55% when non-owner customizes (client omits creatorId for owner)
        } else {
            // continue_play / replay
            amount = CONTINUE_COST;
        }

        console.log(`[Coins] Spend: ${userId} spending ${amount} coins on ${reason} (game: ${gameId || 'n/a'}, level: ${difficultyLevel || 1})`);

        const { data, error } = await supabase.rpc('spend_coins', {
            p_user_id: userId,
            p_amount: amount,
            p_reason: reason,
            p_game_id: gameId || null,
            p_creator_id: effectiveCreatorId,
            p_platform: platform || null
        });

        if (error) {
            console.error('Error spending coins:', error);
            return res.status(500).json({ error: 'Failed to spend coins' });
        }

        if (!data.success) {
            return res.status(400).json({
                success: false,
                error: data.error,
                balance: data.balance || 0
            });
        }

        console.log(`[Coins] Spend complete: ${userId} balance=${data.new_balance}, creator_share=${data.creator_share}`);

        res.json({
            success: true,
            coinsSpent: amount,
            newBalance: data.new_balance,
            creatorShare: data.creator_share
        });
    } catch (error) {
        console.error('Unexpected error in spendCoins:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
}

/**
 * Get creator earnings for a specific user
 */
export async function getCreatorEarnings(req, res) {
    try {
        const { creatorId } = req.query;
        if (!creatorId) {
            return res.status(400).json({ error: 'creatorId is required' });
        }

        const { data: earnings, error } = await supabase
            .from('creator_earnings')
            .select('game_id, total_coins_earned, total_plays_monetized, updated_at')
            .eq('creator_id', creatorId)
            .order('total_coins_earned', { ascending: false });

        if (error) {
            console.error('Error fetching creator earnings:', error);
            return res.status(500).json({ error: 'Failed to fetch earnings' });
        }

        const totalEarned = (earnings || []).reduce((sum, e) => sum + e.total_coins_earned, 0);
        const totalPlays = (earnings || []).reduce((sum, e) => sum + e.total_plays_monetized, 0);

        // Fetch game titles for display
        const gameIds = (earnings || []).map(e => e.game_id);
        let gameTitleMap = {};
        if (gameIds.length > 0) {
            const { data: games } = await supabase
                .from('custom_games')
                .select('id, title')
                .in('id', gameIds);
            if (games) {
                gameTitleMap = Object.fromEntries(games.map(g => [g.id, g.title]));
            }
        }

        res.json({
            success: true,
            totalCoinsEarned: totalEarned,
            totalPlaysMonetized: totalPlays,
            gameBreakdown: (earnings || []).map(e => ({
                ...e,
                game_title: gameTitleMap[e.game_id] || 'Untitled Game'
            }))
        });
    } catch (error) {
        console.error('Unexpected error in getCreatorEarnings:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
}

/**
 * Get transaction history for a user
 */
export async function getTransactionHistory(req, res) {
    try {
        const { userId, limit = 50, offset = 0 } = req.query;
        if (!userId) {
            return res.status(400).json({ error: 'userId is required' });
        }

        const { data, error } = await supabase
            .from('coin_transactions')
            .select('id, amount, type, reason, game_id, created_at')
            .eq('user_id', userId)
            .order('created_at', { ascending: false })
            .range(parseInt(offset), parseInt(offset) + parseInt(limit) - 1);

        if (error) {
            console.error('Error fetching transactions:', error);
            return res.status(500).json({ error: 'Failed to fetch transactions' });
        }

        res.json({ success: true, transactions: data || [] });
    } catch (error) {
        console.error('Unexpected error in getTransactionHistory:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
}

/**
 * Award coins (e.g. rating reward, referral bonus)
 */
export async function awardCoins(req, res) {
    try {
        const { userId, amount, reason, platform } = req.body;

        if (!userId || !amount || !reason) {
            return res.status(400).json({ error: 'userId, amount, and reason are required' });
        }

        // This route is called without credentials (the iOS rating reward). Keep it to that one reward, at its fixed size,
        // once per account: the transaction id below is per user and reason, so the RPC's duplicate check enforces "once".
        const allowed = AWARD_REASONS[reason];
        if (typeof userId !== 'string' || userId.length > 128 || !allowed || amount !== allowed) {
            return res.status(400).json({ error: 'Unsupported award' });
        }

        const { data, error } = await supabase.rpc('add_purchased_coins', {
            p_user_id: userId,
            p_amount: amount,
            p_reason: reason,
            p_platform: platform || null,
            p_iap_transaction_id: `award_${reason}_${userId}`
        });

        if (error) {
            console.error('Error awarding coins:', error);
            return res.status(500).json({ error: 'Failed to award coins' });
        }

        if (data && data.duplicate) {
            return res.json({ success: true, coinsAdded: 0, message: 'Already awarded' });
        }

        console.log(`[Coins] Award: ${userId} +${amount} coins for ${reason}`);

        res.json({
            success: true,
            coinsAdded: amount,
            newBalance: data.new_balance
        });
    } catch (error) {
        console.error('Unexpected error in awardCoins:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
}

/**
 * Get coin store configuration (packs + prices)
 */
export async function getCoinStore(req, res) {
    const packs = Object.entries(COIN_PACKS).map(([productId, info]) => ({
        productId,
        coins: info.coins,
        price: info.price
    }));

    res.json({
        success: true,
        continueCost: CONTINUE_COST,
        customizeCost: CUSTOMIZE_COST,
        creatorSharePercent: CREATOR_SHARE_PCT,
        packs
    });
}

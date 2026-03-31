// coinService.js - Virtual currency service
import { supabase } from '../config/database.js';
import { getDifficultyCost, MAX_DIFFICULTY_LEVEL } from './difficultyService.js';

const COIN_PACKS = {
    'com.kreativekoala.riddleverse.coins.100': { coins: 100, price: '$0.99' },
    'com.kreativekoala.riddleverse.coins.500': { coins: 500, price: '$3.99' },
    'com.kreativekoala.riddleverse.coins.1200': { coins: 1200, price: '$7.99' },
};

const CONTINUE_COST = 10; // coins to continue playing after game over
const CUSTOMIZE_COST = 10; // coins per game customization
const CREATOR_SHARE_PCT = 55; // creator gets 55% of spent coins

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
 * Record a coin purchase after IAP verification
 */
export async function purchaseCoins(req, res) {
    try {
        const { userId, productId, transactionId, platform } = req.body;

        if (!userId || !productId || !transactionId || !platform) {
            return res.status(400).json({
                error: 'userId, productId, transactionId, and platform are required'
            });
        }

        const pack = COIN_PACKS[productId];
        if (!pack) {
            return res.status(400).json({
                error: 'Invalid product ID',
                validProducts: Object.keys(COIN_PACKS)
            });
        }

        console.log(`[Coins] Purchase: ${userId} buying ${pack.coins} coins (${productId}) via ${platform}`);

        const { data, error } = await supabase.rpc('add_purchased_coins', {
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

        console.log(`[Coins] Purchase complete: ${userId} now has ${data.new_balance} coins`);

        res.json({
            success: true,
            coinsAdded: pack.coins,
            newBalance: data.new_balance
        });
    } catch (error) {
        console.error('Unexpected error in purchaseCoins:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
}

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

        const { data, error } = await supabase.rpc('add_purchased_coins', {
            p_user_id: userId,
            p_amount: amount,
            p_reason: reason,
            p_platform: platform || null,
            p_iap_transaction_id: `award_${reason}_${Date.now()}`
        });

        if (error) {
            console.error('Error awarding coins:', error);
            return res.status(500).json({ error: 'Failed to award coins' });
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

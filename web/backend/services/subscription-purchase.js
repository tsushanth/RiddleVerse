// subscription-purchase.js
// Node.js endpoint for handling subscription purchases from iOS app

import { supabase } from "../config/database.js";

const TIER_MONTHLY_COINS = {
    premium: 100,
    unlimited: 500
};

/**
 * Handle subscription purchase notification from iOS app
 * POST /api/subscription-purchase
 */
export async function handleSubscriptionPurchase(req, res) {
    try {
        // Validate request method
        if (req.method !== 'POST') {
            return res.status(405).json({ 
                error: 'Method not allowed',
                message: 'Only POST requests are supported'
            });
        }

        // Extract purchase data from request body
        const {
            userId,
            email,
            productId,
            transactionId,
            purchaseDate,
            tier
        } = req.body;

        // Validate required fields
        if (!userId || !email || !productId || !transactionId || !tier) {
            return res.status(400).json({
                error: 'Missing required fields',
                message: 'userId, email, productId, transactionId, and tier are required',
                received: { userId: !!userId, email: !!email, productId: !!productId, transactionId: !!transactionId, tier: !!tier }
            });
        }

        // Validate tier value
        const validTiers = ['free', 'premium', 'unlimited'];
        if (!validTiers.includes(tier)) {
            return res.status(400).json({
                error: 'Invalid tier',
                message: `Tier must be one of: ${validTiers.join(', ')}`,
                received: tier
            });
        }

        // Validate product ID format
        const validProductIds = [
            'com.puzzleforge.premium.monthly',
            'com.puzzleforge.unlimited.monthly',
            "com.kreativekoala.riddleverse.premium.mo",
            "com.kreativekoala.riddleverse.premium.yr"
        ];
        if (!validProductIds.includes(productId)) {
            return res.status(400).json({
                error: 'Invalid product ID',
                message: `Product ID must be one of: ${validProductIds.join(', ')}`,
                received: productId
            });
        }

        console.log(`Processing subscription purchase for user ${email} (${userId})`);
        console.log(`Product: ${productId}, Tier: ${tier}, Transaction: ${transactionId}`);

        // Check if transaction already exists (prevent duplicates)
        const { data: existingTransaction, error: checkError } = await supabase
            .from('user_subscriptions')
            .select('id, transaction_id')
            .eq('transaction_id', transactionId)
            .single();

        if (checkError && checkError.code !== 'PGRST116') { // PGRST116 = no rows found
            console.error('Error checking existing transaction:', checkError);
            return res.status(500).json({
                error: 'Database error',
                message: 'Failed to check existing transaction'
            });
        }

        if (existingTransaction) {
            console.log(`Transaction ${transactionId} already exists, skipping duplicate`);
            return res.status(200).json({
                success: true,
                message: 'Transaction already processed',
                subscriptionId: existingTransaction.id
            });
        }

        // Convert purchase date (assuming it's a timestamp)
        const purchaseDateObj = purchaseDate ? new Date(purchaseDate * 1000) : new Date();

        // Calculate subscription end date (monthly subscription)
        const subscriptionEndDate = new Date(purchaseDateObj);
        subscriptionEndDate.setMonth(subscriptionEndDate.getMonth() + 1);

        // Insert subscription record
        const { data: subscription, error: insertError } = await supabase
            .from('user_subscriptions')
            .insert({
                user_id: userId,
                email: email,
                product_id: productId,
                transaction_id: transactionId,
                subscription_tier: tier,
                purchase_date: purchaseDateObj.toISOString(),
                subscription_start_date: purchaseDateObj.toISOString(),
                subscription_end_date: subscriptionEndDate.toISOString(),
                status: 'active',
                platform: 'ios',
                created_at: new Date().toISOString(),
                updated_at: new Date().toISOString()
            })
            .select()
            .single();

        if (insertError) {
            console.error('Error inserting subscription:', insertError);
            return res.status(500).json({
                error: 'Database error',
                message: 'Failed to save subscription',
                details: insertError.message
            });
        }

        console.log(`Successfully saved subscription ${subscription.id} for user ${email}`);

        // Update user's current tier in users table (if you have one)
        const { error: updateUserError } = await supabase
            .from('users')
            .update({
                subscription_tier: tier,
                updated_at: new Date().toISOString()
            })
            .eq('user_id', userId);

        if (updateUserError) {
            console.warn('Warning: Failed to update user tier in users table:', updateUserError);
            // Don't fail the request since subscription was saved successfully
        }

        // Credit monthly coins based on subscription tier
        const monthlyCoins = req.body.monthlyCoins || TIER_MONTHLY_COINS[tier] || 0;
        let newBalance = null;
        if (monthlyCoins > 0) {
            const { data: coinData, error: coinError } = await supabase.rpc('add_purchased_coins', {
                p_user_id: userId,
                p_amount: monthlyCoins,
                p_reason: `subscription_${tier}_monthly_coins`,
                p_platform: 'ios',
                p_iap_transaction_id: `sub_coins_${transactionId}`
            });

            if (coinError) {
                console.warn('Warning: Failed to credit subscription coins:', coinError);
            } else {
                newBalance = coinData?.new_balance ?? null;
                console.log(`Credited ${monthlyCoins} subscription coins to user ${email}, new balance: ${newBalance}`);
            }
        }

        // Clear any existing regeneration limits for this user
        await clearUserRegenerationLimits(userId, email);

        // Log analytics event
        await logSubscriptionAnalytics(userId, email, tier, productId, purchaseDateObj);

        // Return success response
        res.status(200).json({
            success: true,
            message: 'Subscription processed successfully',
            subscription: {
                id: subscription.id,
                tier: tier,
                startDate: subscription.subscription_start_date,
                endDate: subscription.subscription_end_date,
                status: subscription.status
            },
            coinsGranted: monthlyCoins,
            newBalance: newBalance
        });

    } catch (error) {
        console.error('Unexpected error in subscription purchase handler:', error);
        res.status(500).json({
            error: 'Internal server error',
            message: 'An unexpected error occurred while processing the subscription'
        });
    }
}

/**
 * Clear regeneration limits for user when they upgrade
 */
async function clearUserRegenerationLimits(userId, email) {
    try {
        console.log(`Clearing regeneration limits for user ${email}`);

        // Clear from regeneration_limits table (if you have one)
        const { error: clearLimitsError } = await supabase
            .from('regeneration_limits')
            .delete()
            .eq('user_id', userId);

        if (clearLimitsError && clearLimitsError.code !== 'PGRST116') {
            console.warn('Warning: Failed to clear regeneration limits:', clearLimitsError);
        }

        // Clear from user_daily_usage table (if you have one)
        const { error: clearUsageError } = await supabase
            .from('user_daily_usage')
            .delete()
            .eq('user_id', userId);

        if (clearUsageError && clearUsageError.code !== 'PGRST116') {
            console.warn('Warning: Failed to clear daily usage:', clearUsageError);
        }

        console.log(`Successfully cleared limits for user ${email}`);

    } catch (error) {
        console.error('Error clearing user regeneration limits:', error);
        // Don't throw - this is cleanup, not critical
    }
}

/**
 * Log subscription analytics events
 */
async function logSubscriptionAnalytics(userId, email, tier, productId, purchaseDate) {
    try {
        const analyticsData = {
            user_id: userId,
            email: email,
            event_type: 'subscription_purchased',
            event_data: {
                tier: tier,
                product_id: productId,
                purchase_date: purchaseDate.toISOString(),
                platform: 'ios'
            },
            created_at: new Date().toISOString()
        };

        const { error: analyticsError } = await supabase
            .from('analytics_events')
            .insert(analyticsData);

        if (analyticsError) {
            console.warn('Failed to log subscription analytics:', analyticsError);
        } else {
            console.log(`Logged subscription analytics for ${email}`);
        }

    } catch (error) {
        console.error('Error logging subscription analytics:', error);
        // Don't throw - analytics failure shouldn't fail the purchase
    }
}

/**
 * Get user's current subscription status
 * GET /api/subscription-status
 */
export async function getSubscriptionStatus(req, res) {
    try {
        const { userId, email } = req.query;

        if (!userId && !email) {
            return res.status(400).json({
                error: 'Missing identifier',
                message: 'Either userId or email is required'
            });
        }

        // Build query
        let query = supabase
            .from('user_subscriptions')
            .select('*')
            .eq('status', 'active')
            .order('created_at', { ascending: false });

        if (userId) {
            query = query.eq('user_id', userId);
        } else {
            query = query.eq('email', email);
        }

        const { data: subscriptions, error } = await query;

        if (error) {
            console.error('Error fetching subscription status:', error);
            return res.status(500).json({
                error: 'Database error',
                message: 'Failed to fetch subscription status'
            });
        }

        // Find the most recent active subscription
        const activeSubscription = subscriptions && subscriptions.length > 0 ? subscriptions[0] : null;

        if (!activeSubscription) {
            return res.status(200).json({
                success: true,
                subscription: {
                    tier: 'free',
                    status: 'none',
                    endDate: null
                }
            });
        }

        // Check if subscription is still valid (not expired)
        const now = new Date();
        const endDate = new Date(activeSubscription.subscription_end_date);
        const isExpired = now > endDate;

        if (isExpired) {
            // Mark as expired in database
            await supabase
                .from('user_subscriptions')
                .update({ 
                    status: 'expired',
                    updated_at: new Date().toISOString()
                })
                .eq('id', activeSubscription.id);

            return res.status(200).json({
                success: true,
                subscription: {
                    tier: 'free',
                    status: 'expired',
                    endDate: activeSubscription.subscription_end_date
                }
            });
        }

        res.status(200).json({
            success: true,
            subscription: {
                id: activeSubscription.id,
                tier: activeSubscription.subscription_tier,
                status: activeSubscription.status,
                startDate: activeSubscription.subscription_start_date,
                endDate: activeSubscription.subscription_end_date,
                productId: activeSubscription.product_id
            }
        });

    } catch (error) {
        console.error('Unexpected error in subscription status handler:', error);
        res.status(500).json({
            error: 'Internal server error',
            message: 'An unexpected error occurred while fetching subscription status'
        });
    }
}

/**
 * Sync subscription status from iOS app
 * POST /api/sync-subscription-status
 */
export async function syncSubscriptionStatus(req, res) {
    try {
        if (req.method !== 'POST') {
            return res.status(405).json({ 
                error: 'Method not allowed',
                message: 'Only POST requests are supported'
            });
        }

        const { userId, email, subscriptionTier, platform = 'ios' } = req.body;

        if (!userId || !email || !subscriptionTier) {
            return res.status(400).json({
                error: 'Missing required fields',
                message: 'userId, email, and subscriptionTier are required'
            });
        }

        console.log(`Syncing subscription status for ${email}: ${subscriptionTier}`);

        // Update user's current tier
        const { error: updateError } = await supabase
            .from('users')
            .upsert({
                user_id: userId,
                email: email,
                subscription_tier: subscriptionTier,
                platform: platform,
                updated_at: new Date().toISOString()
            }, {
                onConflict: 'user_id'
            });

        if (updateError) {
            console.error('Error updating user subscription tier:', updateError);
            return res.status(500).json({
                error: 'Database error',
                message: 'Failed to update subscription status'
            });
        }

        // If user downgraded to free, don't clear limits immediately
        // Let them finish their current billing period
        if (subscriptionTier !== 'free') {
            await clearUserRegenerationLimits(userId, email);
        }

        res.status(200).json({
            success: true,
            message: 'Subscription status synced successfully',
            tier: subscriptionTier
        });

    } catch (error) {
        console.error('Unexpected error in sync subscription status:', error);
        res.status(500).json({
            error: 'Internal server error',
            message: 'An unexpected error occurred while syncing subscription status'
        });
    }
}

/**
 * Cancel or update subscription
 * POST /api/subscription-cancel
 */
export async function cancelSubscription(req, res) {
    try {
        if (req.method !== 'POST') {
            return res.status(405).json({ 
                error: 'Method not allowed' 
            });
        }

        const { userId, email, reason = 'user_request' } = req.body;

        if (!userId && !email) {
            return res.status(400).json({
                error: 'Missing identifier',
                message: 'Either userId or email is required'
            });
        }

        // Find active subscription
        let query = supabase
            .from('user_subscriptions')
            .select('*')
            .eq('status', 'active');

        if (userId) {
            query = query.eq('user_id', userId);
        } else {
            query = query.eq('email', email);
        }

        const { data: subscriptions, error: fetchError } = await query;

        if (fetchError) {
            console.error('Error fetching subscription for cancellation:', fetchError);
            return res.status(500).json({
                error: 'Database error',
                message: 'Failed to fetch subscription'
            });
        }

        if (!subscriptions || subscriptions.length === 0) {
            return res.status(404).json({
                error: 'No active subscription found',
                message: 'User does not have an active subscription to cancel'
            });
        }

        const subscription = subscriptions[0];

        // Update subscription status to cancelled
        const { error: cancelError } = await supabase
            .from('user_subscriptions')
            .update({
                status: 'cancelled',
                cancellation_date: new Date().toISOString(),
                cancellation_reason: reason,
                updated_at: new Date().toISOString()
            })
            .eq('id', subscription.id);

        if (cancelError) {
            console.error('Error cancelling subscription:', cancelError);
            return res.status(500).json({
                error: 'Database error',
                message: 'Failed to cancel subscription'
            });
        }

        // Update user tier back to free
        const { error: updateUserError } = await supabase
            .from('users')
            .update({
                subscription_tier: 'free',
                updated_at: new Date().toISOString()
            })
            .eq('user_id', subscription.user_id);

        if (updateUserError) {
            console.warn('Warning: Failed to update user tier after cancellation:', updateUserError);
        }

        // Log cancellation analytics
        await supabase
            .from('analytics_events')
            .insert({
                user_id: subscription.user_id,
                email: subscription.email,
                event_type: 'subscription_cancelled',
                event_data: {
                    tier: subscription.subscription_tier,
                    reason: reason,
                    days_active: Math.floor(
                        (new Date() - new Date(subscription.subscription_start_date)) / (1000 * 60 * 60 * 24)
                    )
                },
                created_at: new Date().toISOString()
            });

        console.log(`Successfully cancelled subscription for ${subscription.email}`);

        res.status(200).json({
            success: true,
            message: 'Subscription cancelled successfully'
        });

    } catch (error) {
        console.error('Unexpected error in subscription cancellation:', error);
        res.status(500).json({
            error: 'Internal server error',
            message: 'An unexpected error occurred while cancelling subscription'
        });
    }
}

/**
 * Get subscription analytics and metrics
 * GET /api/subscription-metrics
 */
export async function getSubscriptionMetrics(req, res) {
    try {
        // This endpoint could be used for admin dashboard or analytics

        const { data: metrics, error } = await supabase
            .from('user_subscriptions')
            .select('subscription_tier, status, created_at, platform')
            .order('created_at', { ascending: false });

        if (error) {
            console.error('Error fetching subscription metrics:', error);
            return res.status(500).json({
                error: 'Database error',
                message: 'Failed to fetch subscription metrics'
            });
        }

        // Calculate metrics
        const totalSubscriptions = metrics.length;
        const activeSubscriptions = metrics.filter(sub => sub.status === 'active').length;
        const tierBreakdown = metrics.reduce((acc, sub) => {
            acc[sub.subscription_tier] = (acc[sub.subscription_tier] || 0) + 1;
            return acc;
        }, {});

        const platformBreakdown = metrics.reduce((acc, sub) => {
            acc[sub.platform] = (acc[sub.platform] || 0) + 1;
            return acc;
        }, {});

        res.status(200).json({
            success: true,
            metrics: {
                total: totalSubscriptions,
                active: activeSubscriptions,
                byTier: tierBreakdown,
                byPlatform: platformBreakdown
            }
        });

    } catch (error) {
        console.error('Error in subscription metrics handler:', error);
        res.status(500).json({
            error: 'Internal server error',
            message: 'Failed to generate subscription metrics'
        });
    }
}

// Export all handlers
export default {
    handleSubscriptionPurchase,
    getSubscriptionStatus,
    syncSubscriptionStatus,
    cancelSubscription,
    getSubscriptionMetrics
};
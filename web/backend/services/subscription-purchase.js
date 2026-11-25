// subscription-purchase.js
// Subscription endpoints. A purchase counts only after Google Play confirms its purchase token (services/playPurchases.js).

import { supabase } from "../config/database.js";
import { verifiedCaller } from "../middleware/callerIdentity.js";
import { createCounters, logAnomaly } from "./coinPurchasePolicy.js";
import { applySubscription, isPurchaseToken, tokenHash } from "./playPurchases.js";

const IP_REQUESTS_PER_HOUR = 60;

/**
 * POST /api/subscription-purchase  { purchaseToken, userId? }  with Authorization: Bearer <the signed-in user's ID token>
 *
 * Sent after a purchase, and again by the app to restore or to pick up a renewal. The account is the one in the bearer
 * token; the product, the period and the coins all come from Play and the server's own table, never from the request.
 * 503 (verification unavailable) and 5xx change nothing and are safe to retry.
 */
export function createSubscriptionPurchaseHandler(deps = {}) {
    const d = { supabase, now: Date.now, verifier: undefined, verifyCaller: verifiedCaller, ...deps };
    const counters = createCounters(d.now);
    const inflight = new Set();

    return async function subscriptionPurchaseHandler(req, res) {
        let lockKey = null;
        try {
            const body = req.body && typeof req.body === 'object' ? req.body : {};
            const ip = String(req.ip || req.socket?.remoteAddress || 'unknown');
            if (counters.ipOverLimit(ip, IP_REQUESTS_PER_HOUR)) {
                res.setHeader('Retry-After', '3600');
                return res.status(429).json({ success: false, error: 'Too many purchase requests. Please try again later.', retryable: true });
            }
            counters.recordIp(ip);

            const caller = await d.verifyCaller(req);
            if (!caller) {
                return res.status(401).json({ success: false, error: 'Authentication required', message: 'Update the app and sign in.' });
            }
            if (body.userId !== undefined && body.userId !== caller.uid) {
                logAnomaly('subscription_uid_mismatch', { userId: caller.uid });
                return res.status(403).json({ success: false, error: 'Credentials do not match userId' });
            }
            if (!isPurchaseToken(body.purchaseToken)) {
                return res.status(400).json({ success: false, error: 'purchaseToken is required', retryable: false });
            }

            const key = tokenHash(body.purchaseToken);
            if (inflight.has(key)) {
                return res.status(409).json({ success: false, error: 'This purchase is already being processed', retryable: true });
            }
            inflight.add(key);
            lockKey = key;

            const result = await applySubscription({
                db: d.supabase, verifier: d.verifier, userId: caller.uid, purchaseToken: body.purchaseToken, now: d.now,
            });
            if (result.status >= 400) {
                logAnomaly('subscription_not_recorded', { status: result.status, error: result.body.error, userId: caller.uid });
            }
            return res.status(result.status).json(result.body);
        } catch (error) {
            console.error('Unexpected error in subscription purchase handler:', error);
            return res.status(500).json({ success: false, error: 'Internal server error', retryable: true });
        } finally {
            if (lockKey) inflight.delete(lockKey);
        }
    };
}

export const handleSubscriptionPurchase = createSubscriptionPurchaseHandler();

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
            // Not written here: the renewal sweep looks this subscription up at Play and records what happened.
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
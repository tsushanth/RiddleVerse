// payoutService.js - Creator payout service via Stripe Connect
import Stripe from 'stripe';
import { supabase } from '../config/database.js';

const MIN_PAYOUT_COINS = 1000;
const COIN_TO_USD = 0.007;
const PLATFORM_URL = 'https://puzzleverseai.com';

let stripe = null;
function getStripe() {
    if (!stripe) {
        if (!process.env.STRIPE_SECRET_KEY) {
            throw new Error('STRIPE_SECRET_KEY not configured');
        }
        stripe = new Stripe(process.env.STRIPE_SECRET_KEY);
    }
    return stripe;
}

/**
 * Check if a creator is eligible for payout
 */
export async function getPayoutEligibility(req, res) {
    try {
        const { creatorId } = req.query;
        if (!creatorId) {
            return res.status(400).json({ error: 'creatorId is required' });
        }

        // Get creator's coin balance
        const { data: coinData } = await supabase
            .from('user_coins')
            .select('balance, total_earned')
            .eq('user_id', creatorId)
            .single();

        const earnedBalance = coinData?.balance || 0;

        // Check Stripe connection
        const { data: stripeData } = await supabase
            .from('creator_stripe_accounts')
            .select('stripe_account_id, payouts_enabled, details_submitted, onboarding_complete')
            .eq('creator_id', creatorId)
            .single();

        const stripeConnected = !!stripeData?.stripe_account_id;
        const stripePayoutsEnabled = stripeData?.payouts_enabled || false;

        const eligible = earnedBalance >= MIN_PAYOUT_COINS && stripePayoutsEnabled;
        const usdAmount = parseFloat((earnedBalance * COIN_TO_USD).toFixed(2));

        res.json({
            success: true,
            eligible,
            earnedBalance,
            minThreshold: MIN_PAYOUT_COINS,
            conversionRate: COIN_TO_USD,
            stripeConnected,
            stripePayoutsEnabled,
            detailsSubmitted: stripeData?.details_submitted || false,
            onboardingComplete: stripeData?.onboarding_complete || false,
            usdAmount
        });
    } catch (error) {
        console.error('Error in getPayoutEligibility:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
}

/**
 * Create or retrieve Stripe Connect Express account and return onboarding URL
 */
export async function connectStripe(req, res) {
    try {
        const { creatorId } = req.body;
        if (!creatorId) {
            return res.status(400).json({ error: 'creatorId is required' });
        }

        const s = getStripe();

        // Check for existing Stripe account
        const { data: existing } = await supabase
            .from('creator_stripe_accounts')
            .select('stripe_account_id, payouts_enabled')
            .eq('creator_id', creatorId)
            .single();

        let stripeAccountId;

        if (existing?.stripe_account_id) {
            stripeAccountId = existing.stripe_account_id;
        } else {
            // Create new Stripe Connect Express account
            const account = await s.accounts.create({
                type: 'express',
                metadata: { creator_id: creatorId }
            });
            stripeAccountId = account.id;

            // Store in database
            await supabase
                .from('creator_stripe_accounts')
                .upsert({
                    creator_id: creatorId,
                    stripe_account_id: stripeAccountId
                }, { onConflict: 'creator_id' });

            console.log(`[Payouts] Created Stripe account ${stripeAccountId} for creator ${creatorId}`);
        }

        // Generate onboarding link
        const accountLink = await s.accountLinks.create({
            account: stripeAccountId,
            refresh_url: `${PLATFORM_URL}/api/payouts/connect-refresh?creatorId=${creatorId}`,
            return_url: `${PLATFORM_URL}/api/payouts/connect-return?creatorId=${creatorId}`,
            type: 'account_onboarding'
        });

        res.json({
            success: true,
            url: accountLink.url,
            stripeAccountId
        });
    } catch (error) {
        console.error('Error in connectStripe:', error);
        const userMessage = error.type === 'StripeInvalidRequestError'
            ? 'Stripe setup is being finalized by our team. Please try again shortly.'
            : 'Failed to create Stripe connection. Please try again later.';
        res.status(500).json({ error: userMessage });
    }
}

/**
 * Check live Stripe account status and update local record
 */
export async function getStripeStatus(req, res) {
    try {
        const { creatorId } = req.query;
        if (!creatorId) {
            return res.status(400).json({ error: 'creatorId is required' });
        }

        const { data: row } = await supabase
            .from('creator_stripe_accounts')
            .select('stripe_account_id')
            .eq('creator_id', creatorId)
            .single();

        if (!row?.stripe_account_id) {
            return res.json({
                success: true,
                connected: false,
                onboardingComplete: false,
                chargesEnabled: false,
                payoutsEnabled: false,
                detailsSubmitted: false
            });
        }

        // Retrieve live status from Stripe
        const s = getStripe();
        const account = await s.accounts.retrieve(row.stripe_account_id);

        // Update local record with live status
        await supabase
            .from('creator_stripe_accounts')
            .update({
                onboarding_complete: account.details_submitted && account.charges_enabled,
                charges_enabled: account.charges_enabled,
                payouts_enabled: account.payouts_enabled,
                details_submitted: account.details_submitted
            })
            .eq('creator_id', creatorId);

        res.json({
            success: true,
            connected: true,
            onboardingComplete: account.details_submitted && account.charges_enabled,
            chargesEnabled: account.charges_enabled,
            payoutsEnabled: account.payouts_enabled,
            detailsSubmitted: account.details_submitted
        });
    } catch (error) {
        console.error('Error in getStripeStatus:', error);
        res.status(500).json({ error: 'Failed to check Stripe status' });
    }
}

/**
 * Request a payout - atomically deducts coins and initiates Stripe Transfer
 */
export async function requestPayout(req, res) {
    try {
        const { creatorId, coinsAmount } = req.body;

        if (!creatorId || !coinsAmount) {
            return res.status(400).json({ error: 'creatorId and coinsAmount are required' });
        }

        if (coinsAmount < MIN_PAYOUT_COINS) {
            return res.status(400).json({
                error: `Minimum payout is ${MIN_PAYOUT_COINS} coins`,
                minThreshold: MIN_PAYOUT_COINS
            });
        }

        // Verify Stripe account is connected and enabled
        const { data: stripeRow } = await supabase
            .from('creator_stripe_accounts')
            .select('stripe_account_id, payouts_enabled')
            .eq('creator_id', creatorId)
            .single();

        if (!stripeRow?.stripe_account_id) {
            return res.status(400).json({ error: 'Stripe account not connected' });
        }

        if (!stripeRow.payouts_enabled) {
            return res.status(400).json({ error: 'Stripe payouts not yet enabled. Complete onboarding first.' });
        }

        // Calculate USD
        const usdAmount = parseFloat((coinsAmount * COIN_TO_USD).toFixed(2));
        const usdCents = Math.round(usdAmount * 100);

        if (usdCents < 1) {
            return res.status(400).json({ error: 'Payout amount too small' });
        }

        const idempotencyKey = `payout_${creatorId}_${Date.now()}`;

        console.log(`[Payouts] Requesting payout: creator=${creatorId}, coins=${coinsAmount}, usd=$${usdAmount}`);

        // Step 1: Atomically deduct coins and create payout request
        const { data: rpcResult, error: rpcError } = await supabase.rpc('create_payout_request', {
            p_creator_id: creatorId,
            p_coins_amount: coinsAmount,
            p_usd_amount: usdAmount,
            p_idempotency_key: idempotencyKey
        });

        if (rpcError) {
            console.error('[Payouts] RPC error:', rpcError);
            return res.status(500).json({ error: 'Failed to create payout request' });
        }

        if (!rpcResult.success) {
            return res.status(400).json({
                success: false,
                error: rpcResult.error,
                balance: rpcResult.balance
            });
        }

        if (rpcResult.duplicate) {
            return res.json({ success: true, message: 'Duplicate request', requestId: rpcResult.request_id });
        }

        const requestId = rpcResult.request_id;

        // Step 2: Update status to processing
        await supabase
            .from('payout_requests')
            .update({ status: 'processing' })
            .eq('id', requestId);

        // Step 3: Initiate Stripe Transfer
        try {
            const s = getStripe();
            const transfer = await s.transfers.create({
                amount: usdCents,
                currency: 'usd',
                destination: stripeRow.stripe_account_id,
                transfer_group: requestId,
                metadata: {
                    payout_request_id: requestId,
                    creator_id: creatorId,
                    coins_amount: String(coinsAmount)
                }
            });

            // Step 4: Mark as completed
            await supabase.rpc('complete_payout_request', {
                p_request_id: requestId,
                p_stripe_transfer_id: transfer.id
            });

            console.log(`[Payouts] Payout completed: request=${requestId}, transfer=${transfer.id}, $${usdAmount}`);

            res.json({
                success: true,
                requestId,
                coinsAmount,
                usdAmount,
                newBalance: rpcResult.new_balance,
                stripeTransferId: transfer.id
            });
        } catch (stripeError) {
            // Stripe failed — refund coins
            console.error(`[Payouts] Stripe transfer failed for request ${requestId}:`, stripeError.message);

            await supabase.rpc('fail_payout_request', {
                p_request_id: requestId,
                p_reason: stripeError.message
            });

            return res.status(500).json({
                success: false,
                error: 'Payment transfer failed. Your coins have been refunded.',
                refunded: true
            });
        }
    } catch (error) {
        console.error('Unexpected error in requestPayout:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
}

/**
 * Get payout history for a creator
 */
export async function getPayoutHistory(req, res) {
    try {
        const { creatorId, limit = 20, offset = 0 } = req.query;
        if (!creatorId) {
            return res.status(400).json({ error: 'creatorId is required' });
        }

        const { data, error } = await supabase
            .from('payout_requests')
            .select('id, coins_amount, usd_amount, status, failure_reason, created_at, completed_at')
            .eq('creator_id', creatorId)
            .order('created_at', { ascending: false })
            .range(parseInt(offset), parseInt(offset) + parseInt(limit) - 1);

        if (error) {
            console.error('Error fetching payout history:', error);
            return res.status(500).json({ error: 'Failed to fetch payout history' });
        }

        res.json({ success: true, payouts: data || [] });
    } catch (error) {
        console.error('Unexpected error in getPayoutHistory:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
}

/**
 * Handle Stripe webhook events
 */
export async function handleStripeWebhook(req, res) {
    const sig = req.headers['stripe-signature'];

    if (!sig || !process.env.STRIPE_WEBHOOK_SECRET) {
        return res.status(400).json({ error: 'Missing webhook configuration' });
    }

    let event;
    try {
        const s = getStripe();
        event = s.webhooks.constructEvent(req.body, sig, process.env.STRIPE_WEBHOOK_SECRET);
    } catch (err) {
        console.error('[Webhook] Signature verification failed:', err.message);
        return res.status(400).json({ error: 'Invalid signature' });
    }

    try {
        switch (event.type) {
            case 'account.updated': {
                const account = event.data.object;
                const stripeAccountId = account.id;

                await supabase
                    .from('creator_stripe_accounts')
                    .update({
                        onboarding_complete: account.details_submitted && account.charges_enabled,
                        charges_enabled: account.charges_enabled,
                        payouts_enabled: account.payouts_enabled,
                        details_submitted: account.details_submitted
                    })
                    .eq('stripe_account_id', stripeAccountId);

                console.log(`[Webhook] Account updated: ${stripeAccountId}, payouts=${account.payouts_enabled}`);
                break;
            }

            case 'transfer.failed':
            case 'transfer.reversed': {
                const transfer = event.data.object;
                const requestId = transfer.metadata?.payout_request_id;

                if (requestId) {
                    const reason = event.type === 'transfer.failed'
                        ? 'Stripe transfer failed'
                        : 'Stripe transfer reversed';

                    await supabase.rpc('fail_payout_request', {
                        p_request_id: requestId,
                        p_reason: reason
                    });

                    console.log(`[Webhook] ${event.type}: request=${requestId}, coins refunded`);
                }
                break;
            }

            default:
                break;
        }
    } catch (error) {
        console.error(`[Webhook] Error processing ${event.type}:`, error);
    }

    // Always return 200 to prevent Stripe retries
    res.json({ received: true });
}

/**
 * Stripe redirect: return page (onboarding complete)
 */
export function connectReturn(req, res) {
    res.send(`<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Stripe Connected</title>
<style>body{font-family:-apple-system,system-ui,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;background:#1a1a2e;color:#fff;text-align:center}
.card{padding:40px;max-width:400px}.icon{font-size:48px;margin-bottom:16px}.title{font-size:24px;font-weight:bold;margin-bottom:8px}.sub{color:#aaa;font-size:14px}</style>
</head><body><div class="card">
<div class="icon">&#10003;</div>
<div class="title">Stripe Connected!</div>
<div class="sub">You can close this page and return to the app.</div>
</div></body></html>`);
}

/**
 * Stripe redirect: refresh page (link expired)
 */
export function connectRefresh(req, res) {
    res.send(`<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Link Expired</title>
<style>body{font-family:-apple-system,system-ui,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;background:#1a1a2e;color:#fff;text-align:center}
.card{padding:40px;max-width:400px}.icon{font-size:48px;margin-bottom:16px}.title{font-size:24px;font-weight:bold;margin-bottom:8px}.sub{color:#aaa;font-size:14px}</style>
</head><body><div class="card">
<div class="icon">&#8635;</div>
<div class="title">Link Expired</div>
<div class="sub">Please return to the app and try connecting again.</div>
</div></body></html>`);
}

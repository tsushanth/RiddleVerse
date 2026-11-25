// Google Play purchases verified from the purchase token: which account a token belongs to, what a subscription grants
// and crediting it. Amounts come only from the tables here. Nothing is written unless Play confirmed the purchase.
import crypto from 'crypto';
import { getPlayTokenVerifier } from './playPurchaseVerifier.js';

const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');
export const tokenHash = sha256;

const PREMIUM = 'com.kreativekoala.riddleverse.premium';
// Coins per paid month, and once per account on its first paid period. Same figures the app shows. A monthly plan's
// billing period is one month; a yearly plan pays `coins` in each month of its paid year (`coinsEveryMonth`).
const MONTHLY = { tier: 'premium', coins: 150, signupBonus: 150 };
const YEARLY = { tier: 'premium_yearly', coins: 700, signupBonus: 500, coinsEveryMonth: true };

export function planFor(productId, basePlanId) {
    if (productId === `${PREMIUM}.mo`) return MONTHLY;
    if (productId === `${PREMIUM}.yr`) return YEARLY;
    if (productId === PREMIUM && basePlanId === 'mo') return MONTHLY;
    if (productId === PREMIUM && basePlanId === 'yr') return YEARLY;
    return null;
}

/** A Play purchase token is a long opaque string; app versions up to 188 put the order id (GPA....) in this field. */
export function isPurchaseToken(value) {
    return typeof value === 'string' && value.length >= 20 && value.length <= 2048 && /^[\x21-\x7e]+$/.test(value) && !value.startsWith('GPA.');
}

export const UNAVAILABLE = {
    success: false,
    error: 'purchase_verification_unavailable',
    retryable: true,
    message: 'We could not confirm your purchase yet. It is not lost: it will be tried again later.',
};
const RETRY_LATER = { success: false, error: 'Failed to record purchase', retryable: true };

/** Binds a purchase token to the first account that presents it. 'ok' | 'other_account' | 'error' */
export async function claimToken(db, { token, userId, kind, productId }) {
    const hash = tokenHash(token);
    const { error } = await db.from('play_purchases').insert({ token_hash: hash, user_id: userId, kind, product_id: productId });
    if (!error) return 'ok';
    if (error.code !== '23505') {
        console.error('[Play] could not record a purchase token:', error.message);
        return 'error';
    }
    const { data, error: readError } = await db.from('play_purchases').select('user_id').eq('token_hash', hash).maybeSingle();
    if (readError || !data) return 'error';
    return data.user_id === userId ? 'ok' : 'other_account';
}

/** add_purchased_coins once per `id` (coin_transactions has a unique index on it). */
async function creditOnce(db, { userId, amount, reason, id }) {
    const { data, error } = await db.rpc('add_purchased_coins', {
        p_user_id: userId, p_amount: amount, p_reason: reason, p_platform: 'android', p_iap_transaction_id: id,
    });
    if (error?.code === '23505') return { ok: true, added: 0 };
    if (error || !data) {
        console.error('[Play] could not credit subscription coins:', error?.message);
        return { ok: false, added: 0 };
    }
    if (data.duplicate) return { ok: true, added: 0 };
    return { ok: true, added: amount, balance: data.new_balance };
}

/** `ms` moved back `n` calendar months (UTC); a day the target month does not have becomes its last day. */
function monthsBefore(ms, n) {
    const from = new Date(ms);
    const to = new Date(ms);
    to.setUTCDate(1);
    to.setUTCMonth(to.getUTCMonth() - n);
    const lastDay = new Date(Date.UTC(to.getUTCFullYear(), to.getUTCMonth() + 1, 0)).getUTCDate();
    to.setUTCDate(Math.min(from.getUTCDate(), lastDay));
    return to.getTime();
}

/** Which month (1 to 12) of the paid year ending at `expiry` contains the time `at`. */
export function monthOfPaidYear(expiry, at) {
    for (let month = 12; month > 1; month -= 1) {
        if (at >= monthsBefore(expiry, 13 - month)) return month;
    }
    return 1;
}

/**
 * Ledger id of the coins due now for a paid period. A yearly order has one id for each of its months, so a month is paid
 * at most once and a year at most twelve times. Only the month containing `at` is ever asked for: months that passed
 * without a check are not made up.
 */
function periodCreditId(plan, orderId, expiry, at) {
    const id = `sub_coins_${orderId}`;
    return plan.coinsEveryMonth ? `${id}_m${monthOfPaidYear(expiry, at)}` : id;
}

const STATUS = {
    SUBSCRIPTION_STATE_ACTIVE: 'active',
    SUBSCRIPTION_STATE_CANCELED: 'active', // auto-renew off; paid up to the expiry time
    SUBSCRIPTION_STATE_IN_GRACE_PERIOD: 'active', // renewal payment failing; access continues, no coins
    SUBSCRIPTION_STATE_ON_HOLD: 'on_hold',
    SUBSCRIPTION_STATE_PAUSED: 'paused',
    SUBSCRIPTION_STATE_EXPIRED: 'expired',
};

/**
 * Looks a subscription token up at Play, records it for `userId` and credits the coins of the current paid period (for a
 * yearly plan, of the current month of the paid year) if they have not been credited. Returns { status, body } for the
 * HTTP answer; safe to call any number of times. Periods and months that already ended are never credited, and neither
 * is a free trial.
 */
export async function applySubscription({ db, verifier = getPlayTokenVerifier(), userId, purchaseToken, now = Date.now }) {
    if (!verifier.isConfigured()) return { status: 503, body: UNAVAILABLE };
    const looked = await verifier.getSubscription(purchaseToken);
    if (!looked.ok) {
        if (looked.code === 'unavailable') {
            console.warn(`[Play] subscription lookup unavailable: ${looked.detail}`);
            return { status: 503, body: UNAVAILABLE };
        }
        return { status: 400, body: { success: false, error: 'purchase_not_verified', retryable: false } };
    }
    const sub = looked.data;
    if (sub.subscriptionState === 'SUBSCRIPTION_STATE_PENDING') {
        return { status: 202, body: { success: false, pending: true, retryable: true } };
    }
    const item = (sub.lineItems || []).find((l) => planFor(l?.productId, l?.offerDetails?.basePlanId));
    if (!item) return { status: 400, body: { success: false, error: 'purchase_not_verified', retryable: false } };

    const plan = planFor(item.productId, item.offerDetails?.basePlanId);
    const expiry = Date.parse(item.expiryTime);
    const current = Number.isFinite(expiry) && expiry > now();
    // A state that can still renew is kept even if the expiry just passed, so the renewal sweep keeps looking.
    const status = STATUS[sub.subscriptionState] || 'expired';
    const entitled = status === 'active' && current;
    const orderId = item.latestSuccessfulOrderId || sub.latestOrderId || null;
    // offerPhase says whether this period is free. Without it: the signup order (no ..N suffix) of a trial offer.
    const trial = item.offerPhase
        ? Boolean(item.offerPhase.freeTrial)
        : /trial/i.test(item.offerDetails?.offerId || '') && !/\.\.\d+$/.test(orderId || '');

    const claim = await claimToken(db, { token: purchaseToken, userId, kind: 'subscription', productId: item.productId });
    if (claim === 'other_account') return { status: 409, body: { success: false, error: 'purchase_belongs_to_another_account', retryable: false } };
    if (claim !== 'ok') return { status: 500, body: RETRY_LATER };

    const stamp = new Date(now()).toISOString();
    const { error: saveError } = await db.from('user_subscriptions').upsert({
        user_id: userId,
        product_id: item.productId,
        base_plan_id: item.offerDetails?.basePlanId || null,
        subscription_tier: plan.tier,
        purchase_token_hash: tokenHash(purchaseToken),
        purchase_token: purchaseToken,
        latest_order_id: orderId,
        play_state: sub.subscriptionState || null,
        status,
        subscription_start_date: sub.startTime || null,
        subscription_end_date: Number.isFinite(expiry) ? new Date(expiry).toISOString() : null,
        platform: 'android',
        test_purchase: Boolean(sub.testPurchase),
        updated_at: stamp,
    }, { onConflict: 'purchase_token_hash' });
    if (saveError) {
        console.error('[Play] could not save a subscription:', saveError.message);
        return { status: 500, body: RETRY_LATER };
    }
    // An upgrade, downgrade or re-signup replaces the token it links to.
    if (sub.linkedPurchaseToken) {
        await db.from('user_subscriptions').update({ status: 'replaced', updated_at: stamp })
            .eq('purchase_token_hash', tokenHash(sub.linkedPurchaseToken));
    }

    const paid = sub.subscriptionState === 'SUBSCRIPTION_STATE_ACTIVE' || sub.subscriptionState === 'SUBSCRIPTION_STATE_CANCELED';
    let coinsGranted = 0;
    let newBalance = null;
    if (paid && current && !trial && orderId) {
        for (const grant of [
            { amount: plan.coins, reason: `subscription_${plan.tier}_coins`, id: periodCreditId(plan, orderId, expiry, now()) },
            { amount: plan.signupBonus, reason: 'subscription_signup_bonus', id: `sub_signup_${sha256(userId).slice(0, 32)}` },
        ]) {
            const c = await creditOnce(db, { userId, ...grant });
            if (!c.ok) return { status: 500, body: RETRY_LATER };
            coinsGranted += c.added;
            if (c.balance !== undefined) newBalance = c.balance;
        }
    }

    const { error: tierError } = await db.from('users')
        .update({ subscription_tier: entitled ? plan.tier : 'free', updated_at: stamp }).eq('user_id', userId);
    if (tierError) console.warn('[Play] could not update the user tier:', tierError.message);

    return {
        status: 200,
        body: {
            success: true,
            message: 'Subscription verified',
            subscription: {
                tier: entitled ? plan.tier : 'free',
                status: current ? status : 'expired',
                productId: item.productId,
                startDate: sub.startTime || null,
                endDate: Number.isFinite(expiry) ? new Date(expiry).toISOString() : null,
            },
            trial,
            coinsGranted,
            newBalance,
        },
    };
}

/** Runs the sweep shortly after start (the machine stops when idle) and then every six hours. */
export function startSubscriptionSweep({ db, intervalMs = 6 * 60 * 60 * 1000 }) {
    const run = () => refreshDueSubscriptions({ db }).catch((e) => console.error('[Play] subscription sweep failed:', e.message));
    setTimeout(run, 60_000).unref();
    setInterval(run, intervalMs).unref();
}

/**
 * Renewals happen at Play without the app, and a yearly plan earns coins every month although nothing changes at Play.
 * Looks up again every recorded subscription whose period has ended, and every running yearly one whose current month has
 * not been credited, so the coins arrive even if the app is not opened. Does nothing while the Play account is not
 * configured.
 */
export async function refreshDueSubscriptions({ db, verifier = getPlayTokenVerifier(), now = Date.now, limit = 200 }) {
    if (!db || !verifier.isConfigured()) return { checked: 0 };
    const { data, error } = await db.from('user_subscriptions')
        .select('user_id, purchase_token')
        .in('status', ['active', 'on_hold', 'paused'])
        .lt('subscription_end_date', new Date(now()).toISOString())
        .order('updated_at', { ascending: true })
        .limit(limit);
    if (error) {
        console.error('[Play] could not list due subscriptions:', error.message);
        return { checked: 0 };
    }
    let checked = 0;
    for (const row of [...(data || []), ...await yearlyMonthsDue({ db, now })]) {
        const r = await applySubscription({ db, verifier, userId: row.user_id, purchaseToken: row.purchase_token, now });
        if (r.status === 503) break; // Play cannot be asked at all: try again on the next pass
        checked += 1;
        // A token Play no longer knows (long expired) stops being re-checked.
        if (r.status === 400) {
            await db.from('user_subscriptions').update({ status: 'expired', updated_at: new Date(now()).toISOString() })
                .eq('purchase_token_hash', tokenHash(row.purchase_token));
        }
    }
    return { checked };
}

/**
 * Running yearly subscriptions whose current month has no coins in the ledger. Reading the ledger first keeps Play out of
 * it for everyone already paid this month.
 * ponytail: reads the first 1000 running yearly rows, one ledger query for each; page through them when there are more.
 */
async function yearlyMonthsDue({ db, now }) {
    const { data, error } = await db.from('user_subscriptions')
        .select('user_id, purchase_token, latest_order_id, subscription_end_date')
        .eq('subscription_tier', YEARLY.tier)
        .eq('status', 'active')
        .gte('subscription_end_date', new Date(now()).toISOString())
        .order('updated_at', { ascending: true })
        .limit(1000);
    if (error) {
        console.error('[Play] could not list yearly subscriptions:', error.message);
        return [];
    }
    const due = [];
    for (const row of data || []) {
        if (!row.latest_order_id) continue;
        const id = periodCreditId(YEARLY, row.latest_order_id, Date.parse(row.subscription_end_date), now());
        const { data: paid, error: ledgerError } = await db.from('coin_transactions')
            .select('id').eq('iap_transaction_id', id).maybeSingle();
        if (ledgerError) {
            console.error('[Play] could not read the coin ledger:', ledgerError.message);
            break;
        }
        if (!paid) due.push(row);
    }
    return due;
}

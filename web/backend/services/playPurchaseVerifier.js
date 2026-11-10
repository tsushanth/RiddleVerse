// Verifies a Google Play in-app purchase server side, from the Play order id.
//
// The shipped Android app buys coins through the RevenueCat SDK and then tells the backend only
// { userId, productId, transactionId = Play order id, platform: 'android' }. It does not send the purchase token, so the
// check goes through the Android Publisher "orders" API (orders.get), which is keyed by order id and returns the
// state and the purchased product ids. That needs a service account that has been granted Play Console access to this app
// with the "View financial data, orders and cancellation survey responses" permission (and the Android Publisher API enabled
// in the service account's Google Cloud project).
//
// Credential lookup (first one that parses as a service account JSON): PLAY_VERIFIER_SERVICE_ACCOUNT, then
// GCS_SERVICE_ACCOUNT_KEY, then FIREBASE_SERVICE_ACCOUNT_KEY. Nothing is read from disk and nothing is logged.
//
// Every outcome is one of:
//   { ok: true,  order }                                    verified: processed order containing the product
//   { ok: false, code: 'not_found' | 'not_processed' | 'product_mismatch' | 'quantity' | 'too_old' }   definitive "no"
//   { ok: false, code: 'unavailable', detail }              could not decide (no credential, network, 401/403/5xx): caller fails closed, retryable
import jwt from 'jsonwebtoken';

const PACKAGE_NAME = 'com.kreativekoala.riddleverse';
const SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const API = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications';

// A Play order id, optionally with the ..N suffix Play adds to recurring/refunded variants. Used by the interim (unverified) mode.
export const PLAY_ORDER_ID_RE = /^GPA\.\d{4}-\d{4}-\d{4}-\d{5}(\.\.\d+)?$/;

export function loadServiceAccount(env = process.env) {
    for (const name of ['PLAY_VERIFIER_SERVICE_ACCOUNT', 'GCS_SERVICE_ACCOUNT_KEY', 'FIREBASE_SERVICE_ACCOUNT_KEY']) {
        const raw = env[name];
        if (typeof raw !== 'string' || !raw.trim()) continue;
        try {
            const j = JSON.parse(raw);
            if (j && j.client_email && j.private_key) return { source: name, email: j.client_email, privateKey: j.private_key };
        } catch { /* try the next one */ }
    }
    return null;
}

export function createPlayPurchaseVerifier({
    env = process.env,
    fetchImpl = (...a) => globalThis.fetch(...a),
    now = Date.now,
    signJwt = (payload, key) => jwt.sign(payload, key, { algorithm: 'RS256' }),
} = {}) {
    let cachedToken = null; // { value, exp }
    let cachedSa = null;

    function serviceAccount() {
        if (!cachedSa) cachedSa = loadServiceAccount(env);
        return cachedSa;
    }

    async function accessToken() {
        const sa = serviceAccount();
        if (!sa) throw Object.assign(new Error('no service account configured'), { unavailable: true });
        if (cachedToken && cachedToken.exp - 60_000 > now()) return cachedToken.value;
        const iat = Math.floor(now() / 1000);
        const assertion = signJwt({ iss: sa.email, scope: SCOPE, aud: TOKEN_URL, iat, exp: iat + 3000 }, sa.privateKey);
        const res = await fetchImpl(TOKEN_URL, {
            method: 'POST',
            headers: { 'content-type': 'application/x-www-form-urlencoded' },
            body: `grant_type=${encodeURIComponent('urn:ietf:params:oauth:grant-type:jwt-bearer')}&assertion=${encodeURIComponent(assertion)}`,
        });
        const body = await res.json().catch(() => ({}));
        if (!res.ok || !body.access_token) throw Object.assign(new Error(`token exchange failed (${res.status})`), { unavailable: true });
        cachedToken = { value: body.access_token, exp: now() + (Number(body.expires_in) || 3000) * 1000 };
        return cachedToken.value;
    }

    return {
        isConfigured: () => Boolean(serviceAccount()),
        credentialSource: () => serviceAccount()?.source || null,

        /**
         * @param {{orderId: string, productId: string}} p
         * @param {{maxAgeMs?: number}} [opts] reject orders created longer ago than this (0/undefined = no limit)
         */
        async verifyOrder({ orderId, productId }, { maxAgeMs = 0 } = {}) {
            if (typeof orderId !== 'string' || !orderId || typeof productId !== 'string' || !productId) {
                return { ok: false, code: 'not_found' };
            }
            let token;
            try {
                token = await accessToken();
            } catch (e) {
                return { ok: false, code: 'unavailable', detail: String(e.message || e).slice(0, 120) };
            }
            let res;
            try {
                res = await fetchImpl(`${API}/${PACKAGE_NAME}/orders/${encodeURIComponent(orderId)}`, {
                    headers: { authorization: `Bearer ${token}` },
                });
            } catch (e) {
                return { ok: false, code: 'unavailable', detail: `network: ${String(e.message || e).slice(0, 100)}` };
            }
            if (res.status === 404 || res.status === 400) return { ok: false, code: 'not_found' };
            if (!res.ok) {
                if (res.status === 401) cachedToken = null;
                return { ok: false, code: 'unavailable', detail: `play api ${res.status}` };
            }
            const order = await res.json().catch(() => null);
            if (!order || typeof order !== 'object') return { ok: false, code: 'unavailable', detail: 'unreadable response' };

            if (order.state !== 'PROCESSED') return { ok: false, code: 'not_processed', state: order.state };
            const items = Array.isArray(order.lineItems) ? order.lineItems : [];
            const item = items.find((l) => l && l.productId === productId);
            if (!item) return { ok: false, code: 'product_mismatch' };
            const qty = item.oneTimePurchaseDetails?.quantity;
            if (qty !== undefined && Number(qty) !== 1) return { ok: false, code: 'quantity' };
            if (items.length !== 1) return { ok: false, code: 'product_mismatch' };
            if (maxAgeMs > 0) {
                const created = Date.parse(order.createTime);
                if (!Number.isFinite(created) || now() - created > maxAgeMs) return { ok: false, code: 'too_old' };
            }
            return { ok: true, order: { orderId: order.orderId, createTime: order.createTime, state: order.state } };
        },
    };
}

let shared = null;
export function getPlayPurchaseVerifier() {
    if (!shared) shared = createPlayPurchaseVerifier();
    return shared;
}

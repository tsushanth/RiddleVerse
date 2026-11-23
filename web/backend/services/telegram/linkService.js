// Bot-issued, one-time login/link codes -> Firebase custom token (or a link to the signed-in app account).
//
// Flow: /link in the private chat -> issue() stores sha256(code) with a 10 minute expiry and the bot sends
// https://puzzleverseai.com/app/link?code=... -> the app (riddleverse://auth?code=...) POSTs /api/telegram/link/redeem.
//   - app signed in (Bearer Firebase ID token): the Telegram identity is attached to that account ("linked").
//   - app not signed in: the account bound to the Telegram identity is created/used and a Firebase custom token for it
//     is returned ("signin"). The uid is `tg_<telegram id>` unless the Telegram user was linked to another uid earlier.
// A code is consumed by one atomic conditional update, so a replay or a race can never succeed twice. Entitlements
// (premium, no ads) are NOT touched here: they come from RevenueCat / the subscription tables, never from a login method.
import { hashLinkCode, newLinkCode, LINK_CODE_RE } from './security.js';
import { StoreConflict } from './store.js';

export const LINK_TTL_MS = 10 * 60 * 1000;

export function createLinkService({ store, getFirebaseAuth, now = () => Date.now(), ttlMs = LINK_TTL_MS }) {
    return {
        async issue(tgId) {
            const code = newLinkCode();
            const expiresAt = new Date(now() + ttlMs).toISOString();
            await store.createLinkCode({ hash: hashLinkCode(code), tgId, expiresAt });
            return { code, expiresAt, ttlMinutes: Math.round(ttlMs / 60000) };
        },

        /** @returns {Promise<{ok:true, mode:'signin'|'linked', uid:string, customToken?:string}|{ok:false,status:number,error:string}>} */
        async redeem({ code, idToken }) {
            if (typeof code !== 'string' || !LINK_CODE_RE.test(code)) return { ok: false, status: 400, error: 'invalid_code' };

            let auth;
            try { auth = await getFirebaseAuth(); } catch { auth = null; }
            if (!auth) return { ok: false, status: 503, error: 'auth_unavailable' };

            // Verify the caller's own session BEFORE spending the one-time code.
            let callerUid = null;
            if (idToken) {
                try { callerUid = (await auth.verifyIdToken(idToken)).uid; } catch { return { ok: false, status: 401, error: 'invalid_token' }; }
            }

            const consumed = await store.consumeLinkCode(hashLinkCode(code), new Date(now()).toISOString());
            if (!consumed) return { ok: false, status: 410, error: 'expired_or_used' };

            const user = await store.getUser(consumed.tgId);
            if (!user) return { ok: false, status: 410, error: 'expired_or_used' };

            try {
                if (callerUid) {
                    if (user.firebase_uid && user.firebase_uid !== callerUid) return { ok: false, status: 409, error: 'telegram_already_linked' };
                    if (user.firebase_uid !== callerUid) await store.setFirebaseUid(consumed.tgId, callerUid);
                    return { ok: true, mode: 'linked', uid: callerUid, tgId: consumed.tgId };
                }
                const uid = user.firebase_uid || `tg_${consumed.tgId}`;
                if (!user.firebase_uid) await store.setFirebaseUid(consumed.tgId, uid);
                if (typeof auth.createCustomToken !== 'function') return { ok: false, status: 503, error: 'auth_unavailable' };
                const customToken = await auth.createCustomToken(uid, { provider: 'telegram' });
                return { ok: true, mode: 'signin', uid, customToken, tgId: consumed.tgId };
            } catch (e) {
                if (e instanceof StoreConflict) return { ok: false, status: 409, error: e.code };
                return { ok: false, status: 500, error: 'link_failed' };
            }
        },
    };
}

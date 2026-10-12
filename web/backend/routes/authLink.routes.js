// POST /api/auth/link-supabase
// Lazy-migration hook: a client signed in with BOTH Firebase and Supabase proves
// possession of both tokens; we record supabase_user_id -> canonical (Firebase) uid.
//   Authorization: Bearer <FIREBASE ID token>
//   Body: { supabaseAccessToken: "<Supabase access token>" }
import express from 'express';
import rateLimit from 'express-rate-limit';
import {
    IdentityError, serviceClient, verifyFirebaseToken, verifySupabaseToken, forgetMapping, __getDeps,
} from '../auth/verifyIdentity.js';

const router = express.Router();
const limiter = rateLimit({ windowMs: 60_000, max: 20, standardHeaders: true, legacyHeaders: false });

function fail(res, status, error) {
    return res.status(status).json({ success: false, error });
}

router.post('/link-supabase', limiter, async (req, res) => {
    const header = req.headers.authorization || '';
    const supabaseToken = req.body?.supabaseAccessToken;
    if (!header.startsWith('Bearer ') || !header.slice(7).trim()) return fail(res, 401, 'Missing authorization token');
    if (typeof supabaseToken !== 'string' || !supabaseToken) return fail(res, 400, 'supabaseAccessToken is required');

    let firebaseIdentity;
    try {
        firebaseIdentity = await verifyFirebaseToken(header.slice(7).trim());
    } catch {
        return fail(res, 401, 'Invalid or expired token');
    }
    let sbUser;
    try {
        sbUser = await verifySupabaseToken(supabaseToken);
    } catch (e) {
        if (e instanceof IdentityError && e.code === 'not_configured') return fail(res, 503, e.message);
        return fail(res, 401, 'Invalid or expired Supabase token');
    }

    const canonicalUid = firebaseIdentity.uid;
    const provider = sbUser.app_metadata?.provider || null;
    let db;
    try {
        db = __getDeps().getServiceClient();
    } catch (e) {
        return fail(res, e.status || 503, e.message);
    }

    try {
        const byUser = await db.from('auth_identity_map').select('supabase_user_id, canonical_uid').eq('supabase_user_id', sbUser.id).maybeSingle();
        if (byUser.error) throw byUser.error;
        if (byUser.data) {
            if (byUser.data.canonical_uid === canonicalUid) return res.json({ success: true, canonicalUid, alreadyLinked: true });
            return fail(res, 409, 'Supabase account is already linked to a different user');
        }
        const byUid = await db.from('auth_identity_map').select('supabase_user_id').eq('canonical_uid', canonicalUid).maybeSingle();
        if (byUid.error) throw byUid.error;
        if (byUid.data) return fail(res, 409, 'User is already linked to a different Supabase account');

        const ins = await db.from('auth_identity_map').insert({
            supabase_user_id: sbUser.id, canonical_uid: canonicalUid, firebase_uid: canonicalUid, provider,
        });
        if (ins.error) {
            if (ins.error.code === '23505') {
                // Lost a race: re-read and treat as idempotent only if it is the same pair.
                const again = await db.from('auth_identity_map').select('canonical_uid').eq('supabase_user_id', sbUser.id).maybeSingle();
                if (again.data?.canonical_uid === canonicalUid) return res.json({ success: true, canonicalUid, alreadyLinked: true });
                return fail(res, 409, 'Link conflict');
            }
            throw ins.error;
        }
        forgetMapping(sbUser.id);
        return res.status(201).json({ success: true, canonicalUid, alreadyLinked: false });
    } catch (e) {
        console.error('[link-supabase] failed:', e?.message);
        return fail(res, 500, 'Link failed');
    }
});

// POST /api/auth/supabase-exchange
//   Authorization: Bearer <Supabase access token>   (body ignored)
// Returns a Firebase custom token for the canonical uid so clients keep using
// FirebaseAuth.currentUser.uid unchanged. Existing mappings are honoured as-is
// (including legacy Firebase uids); unmapped users get a NEW uid `sb_<uuid>`.
// Never touches user data, never re-keys, never merges by email.
const exchangeLimiter = rateLimit({ windowMs: 60_000, max: 30, standardHeaders: true, legacyHeaders: false });
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

router.post('/supabase-exchange', exchangeLimiter, async (req, res) => {
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
    if (!token) return res.status(401).json({ error: 'invalid_token' });

    let sbUser;
    try {
        sbUser = await verifySupabaseToken(token);
    } catch (e) {
        if (e instanceof IdentityError && e.code === 'not_configured') return res.status(503).json({ error: 'auth_bridge_unavailable' });
        return res.status(401).json({ error: 'invalid_token' });
    }
    if (typeof sbUser?.id !== 'string' || !UUID_RE.test(sbUser.id)) return res.status(401).json({ error: 'invalid_token' });
    const sbId = sbUser.id.toLowerCase();

    let db;
    let fbAuth;
    try {
        db = __getDeps().getServiceClient();
        fbAuth = await __getDeps().getFirebaseAuth();
        if (!fbAuth || typeof fbAuth.createCustomToken !== 'function') throw new Error('firebase admin unavailable');
    } catch {
        return res.status(503).json({ error: 'auth_bridge_unavailable' });
    }

    try {
        let canonicalUid;
        let isNew = false;
        const found = await db.from('auth_identity_map').select('supabase_user_id, canonical_uid').eq('supabase_user_id', sbId).maybeSingle();
        if (found.error) throw found.error;
        if (found.data?.canonical_uid) {
            canonicalUid = found.data.canonical_uid;
        } else {
            canonicalUid = `sb_${sbId}`; // 3 + 36 chars, [a-z0-9_-] only: valid Firebase uid
            const ins = await db.from('auth_identity_map').insert({
                supabase_user_id: sbId, canonical_uid: canonicalUid, firebase_uid: null, provider: 'supabase',
            });
            if (ins.error) {
                if (ins.error.code !== '23505') throw ins.error;
                // Lost a race (or uid collision): use whatever row now exists for this user.
                const again = await db.from('auth_identity_map').select('canonical_uid').eq('supabase_user_id', sbId).maybeSingle();
                if (again.error || !again.data?.canonical_uid) throw ins.error;
                canonicalUid = again.data.canonical_uid;
            } else {
                isNew = true;
            }
            forgetMapping(sbId);
        }
        const firebaseCustomToken = await fbAuth.createCustomToken(canonicalUid, { provider: 'supabase' });
        return res.json({ firebaseCustomToken, canonicalUid, isNew });
    } catch (e) {
        console.error('[supabase-exchange] failed:', e?.message);
        return res.status(500).json({ error: 'exchange_failed' });
    }
});

export default router;

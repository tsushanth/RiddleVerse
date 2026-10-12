// verifyIdentity.js - single entry point for verifying a bearer token.
//
// Firebase is tried FIRST and behaves exactly as before (same decoded token,
// same thrown error). Only when Firebase rejects the token AND the token's
// unverified `iss` claim points at this project's Supabase Auth do we try
// Supabase, then map supabase_user_id -> canonical_uid via auth_identity_map.
// The canonical uid is always the legacy Firebase uid. We never mint uids.
//
// Env (Supabase path only; the Firebase path needs nothing new):
//   SUPABASE_URL                 already set
//   SUPABASE_ANON_KEY            already set (used for auth.getUser)
//   SUPABASE_SERVICE_ROLE_KEY    NEW, required to read/write auth_identity_map
import { createClient } from '@supabase/supabase-js';

export class IdentityError extends Error {
    constructor(code, message, status = 401) {
        super(message);
        this.name = 'IdentityError';
        this.code = code; // missing_token | invalid_token | unlinked | not_configured
        this.status = status;
    }
}

const MAP_TTL_MS = 60_000;
const MAP_MAX = 5000;
const mapCache = new Map(); // supabase_user_id -> { uid, exp } (positive hits only)

const defaultDeps = {
    async getFirebaseAuth() {
        const { admin } = await import('../config/firebaseAdmin.js');
        return admin.auth();
    },
    getSupabaseUrl: () => process.env.SUPABASE_URL,
    // Validates the JWT server-side against Supabase Auth (honours revocation).
    async getSupabaseUser(jwt) {
        const url = process.env.SUPABASE_URL;
        const key = process.env.SUPABASE_ANON_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;
        if (!url || !key) throw new IdentityError('not_configured', 'Supabase auth not configured', 503);
        const c = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } });
        const { data, error } = await c.auth.getUser(jwt);
        if (error || !data?.user) throw new IdentityError('invalid_token', 'Invalid or expired token');
        return data.user;
    },
    getServiceClient: serviceClient,
};

let deps = defaultDeps;
/** Test hook: override any dependency. Call with no args to reset. */
export function __setDeps(overrides) {
    deps = overrides ? { ...defaultDeps, ...overrides } : defaultDeps;
    mapCache.clear();
}

let _svc = null;
export function serviceClient() {
    const url = process.env.SUPABASE_URL;
    const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
    if (!url || !key) throw new IdentityError('not_configured', 'Identity bridge not configured', 503);
    if (!_svc) _svc = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } });
    return _svc;
}

function tokenFromHeader(header) {
    if (typeof header !== 'string' || !header.startsWith('Bearer ')) {
        throw new IdentityError('missing_token', 'Missing authorization token');
    }
    const t = header.slice(7).trim();
    if (!t) throw new IdentityError('missing_token', 'Invalid authorization header');
    return t;
}

/** Unverified `iss` claim, used ONLY to route to the right verifier. */
function unverifiedIssuer(token) {
    try {
        const part = token.split('.')[1];
        return JSON.parse(Buffer.from(part, 'base64url').toString('utf8')).iss;
    } catch {
        return undefined;
    }
}

function looksLikeSupabase(token) {
    const base = deps.getSupabaseUrl();
    const iss = unverifiedIssuer(token);
    return Boolean(base && typeof iss === 'string' && iss.replace(/\/+$/, '') === `${base.replace(/\/+$/, '')}/auth/v1`);
}

export async function resolveCanonicalUid(supabaseUserId) {
    const hit = mapCache.get(supabaseUserId);
    if (hit && hit.exp > Date.now()) return hit.uid;
    const { data, error } = await deps.getServiceClient().rpc('resolve_canonical_uid', { p_supabase_user_id: supabaseUserId });
    if (error) throw new IdentityError('not_configured', 'Identity lookup failed', 503);
    if (!data) return null; // do not cache misses: a fresh link must take effect at once
    if (mapCache.size >= MAP_MAX) mapCache.clear();
    mapCache.set(supabaseUserId, { uid: data, exp: Date.now() + MAP_TTL_MS });
    return data;
}

export function forgetMapping(supabaseUserId) {
    mapCache.delete(supabaseUserId);
}

/** Firebase-only verification. Throws the ORIGINAL firebase-admin error. */
export async function verifyFirebaseToken(token) {
    const auth = await deps.getFirebaseAuth();
    const decoded = await auth.verifyIdToken(token);
    return { uid: decoded.uid, provider: 'firebase', raw: decoded };
}

/** Supabase-only verification. Returns the Supabase user (no mapping). */
export async function verifySupabaseToken(token) {
    return deps.getSupabaseUser(token);
}

export async function verifyToken(token) {
    try {
        return await verifyFirebaseToken(token);
    } catch (firebaseError) {
        if (!looksLikeSupabase(token)) throw firebaseError; // identical to legacy behaviour
        const user = await verifySupabaseToken(token);
        const uid = await resolveCanonicalUid(user.id);
        if (!uid) {
            throw new IdentityError('unlinked', 'Supabase account is not linked to a RiddleVerse user', 403);
        }
        return { uid, provider: 'supabase', raw: user };
    }
}

export async function verifyBearerToken(authorizationHeader) {
    return verifyToken(tokenFromHeader(authorizationHeader));
}


export function __getDeps() {
    return deps;
}

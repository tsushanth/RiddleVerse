// Shared fakes for auth tests. No network, no real Firebase/Supabase.
export const SB_URL = 'https://fake-project.supabase.co';
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
export const jwt = (payload) => `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(payload)}.sig`;
export const sbJwt = (sub) => jwt({ iss: `${SB_URL}/auth/v1`, sub, aud: 'authenticated' });
export const fbJwt = () => jwt({ iss: 'https://securetoken.google.com/proj', sub: 'x' });

export class FirebaseError extends Error {
    constructor(code, message) { super(message); this.code = code; }
}

/** firebase auth fake: tokens map -> decoded; anything else rejects like firebase-admin. */
export function fakeFirebase(valid = {}) {
    return async () => ({
        verifyIdToken: async (t) => {
            if (valid[t]) return valid[t];
            throw new FirebaseError('auth/argument-error', 'Decoding Firebase ID token failed');
        },
    });
}

/** supabase user fake: tokens map -> user. */
export function fakeSupabaseUsers(valid = {}) {
    return async (t) => {
        if (valid[t]) return valid[t];
        const e = new Error('Invalid or expired token'); e.code = 'invalid_token'; throw e;
    };
}

/** In-memory auth_identity_map with the subset of the supabase-js API we use. */
export function fakeDb(initial = []) {
    const rows = [...initial];
    const calls = { rpc: 0, insert: 0 };
    return {
        rows, calls,
        rpc: async (fn, args) => {
            calls.rpc++;
            if (fn !== 'resolve_canonical_uid') return { data: null, error: { message: 'no fn' } };
            const r = rows.find((x) => x.supabase_user_id === args.p_supabase_user_id);
            return { data: r ? r.canonical_uid : null, error: null };
        },
        from: () => ({
            select: () => ({
                eq: (col, val) => ({
                    maybeSingle: async () => ({ data: rows.find((r) => r[col] === val) || null, error: null }),
                }),
            }),
            insert: async (row) => {
                calls.insert++;
                if (rows.some((r) => r.supabase_user_id === row.supabase_user_id || r.canonical_uid === row.canonical_uid)) {
                    return { error: { code: '23505', message: 'duplicate' } };
                }
                rows.push(row);
                return { error: null };
            },
        }),
    };
}

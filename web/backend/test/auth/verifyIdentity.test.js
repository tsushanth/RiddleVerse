import test from 'node:test';
import assert from 'node:assert/strict';
import { verifyBearerToken, verifyToken, __setDeps, IdentityError } from '../../auth/verifyIdentity.js';
import { SB_URL, jwt, sbJwt, fbJwt, fakeFirebase, fakeSupabaseUsers, fakeDb } from './helpers.js';

const SB_UID = '11111111-1111-1111-1111-111111111111';

function setup({ rows = [], fbValid = {}, sbValid = {} } = {}) {
    const db = fakeDb(rows);
    __setDeps({
        getFirebaseAuth: fakeFirebase(fbValid),
        getSupabaseUrl: () => SB_URL,
        getSupabaseUser: fakeSupabaseUsers(sbValid),
        getServiceClient: () => db,
    });
    return db;
}

test('firebase token: identical decoded result, no supabase involvement', async () => {
    const t = fbJwt();
    const decoded = { uid: 'fbUser1', email: 'a@b.c', firebase: { sign_in_provider: 'google.com' } };
    const db = setup({ fbValid: { [t]: decoded } });
    const r = await verifyBearerToken(`Bearer ${t}`);
    assert.equal(r.uid, 'fbUser1');
    assert.equal(r.provider, 'firebase');
    assert.strictEqual(r.raw, decoded);
    assert.equal(db.calls.rpc, 0);
});

test('invalid firebase token (non-supabase issuer): original firebase error is rethrown untouched', async () => {
    setup();
    await assert.rejects(() => verifyToken(fbJwt()), (e) => e.code === 'auth/argument-error' && !(e instanceof IdentityError));
});

test('garbage token: original firebase error', async () => {
    setup();
    await assert.rejects(() => verifyToken('not-a-jwt'), (e) => e.code === 'auth/argument-error');
});

test('supabase token mapped to canonical uid', async () => {
    const t = sbJwt(SB_UID);
    setup({ rows: [{ supabase_user_id: SB_UID, canonical_uid: 'legacyFbUid' }], sbValid: { [t]: { id: SB_UID, email: 'a@b.c' } } });
    const r = await verifyBearerToken(`Bearer ${t}`);
    assert.equal(r.uid, 'legacyFbUid');
    assert.equal(r.provider, 'supabase');
});

test('mapping lookups are cached briefly', async () => {
    const t = sbJwt(SB_UID);
    const db = setup({ rows: [{ supabase_user_id: SB_UID, canonical_uid: 'legacyFbUid' }], sbValid: { [t]: { id: SB_UID } } });
    await verifyToken(t); await verifyToken(t);
    assert.equal(db.calls.rpc, 1);
});

test('unlinked supabase user: clear error, nothing minted, misses not cached', async () => {
    const t = sbJwt(SB_UID);
    const db = setup({ sbValid: { [t]: { id: SB_UID } } });
    await assert.rejects(() => verifyToken(t), (e) => e.code === 'unlinked' && e.status === 403);
    assert.equal(db.rows.length, 0);
    db.rows.push({ supabase_user_id: SB_UID, canonical_uid: 'late' });
    assert.equal((await verifyToken(t)).uid, 'late'); // link takes effect immediately
});

test('forged / expired supabase token rejected', async () => {
    setup({ rows: [{ supabase_user_id: SB_UID, canonical_uid: 'x' }] });
    await assert.rejects(() => verifyToken(sbJwt(SB_UID)), (e) => e.code === 'invalid_token');
});

test('token claiming another supabase project issuer is not sent to supabase', async () => {
    setup();
    const evil = jwt({ iss: 'https://other-project.supabase.co/auth/v1', sub: SB_UID });
    await assert.rejects(() => verifyToken(evil), (e) => e.code === 'auth/argument-error');
});

test('missing / malformed Authorization header', async () => {
    setup();
    for (const h of [undefined, '', 'Basic abc', 'Bearer ', 'Bearer    ']) {
        await assert.rejects(() => verifyBearerToken(h), (e) => e.code === 'missing_token');
    }
});

test('supabase path without service key configured surfaces not_configured, firebase unaffected', async () => {
    const t = sbJwt(SB_UID);
    __setDeps({
        getFirebaseAuth: fakeFirebase({ ok: { uid: 'u' } }),
        getSupabaseUrl: () => SB_URL,
        getSupabaseUser: fakeSupabaseUsers({ [t]: { id: SB_UID } }),
        getServiceClient: () => { throw new IdentityError('not_configured', 'Identity bridge not configured', 503); },
    });
    await assert.rejects(() => verifyToken(t), (e) => e.code === 'not_configured' && e.status === 503);
    assert.equal((await verifyToken('ok')).uid, 'u');
});

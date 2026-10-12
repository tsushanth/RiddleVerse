import test from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import authLink from '../../routes/authLink.routes.js';
import { __setDeps } from '../../auth/verifyIdentity.js';
import { SB_URL, sbJwt, fakeFirebase, fakeSupabaseUsers, fakeDb } from './helpers.js';

const U1 = '11111111-1111-1111-1111-111111111111';
const U2 = '22222222-2222-2222-2222-222222222222';

async function boot(rows = []) {
    const db = fakeDb(rows);
    __setDeps({
        getFirebaseAuth: fakeFirebase({ fbA: { uid: 'A' }, fbB: { uid: 'B' } }),
        getSupabaseUrl: () => SB_URL,
        getSupabaseUser: fakeSupabaseUsers({ [sbJwt(U1)]: { id: U1, app_metadata: { provider: 'google' } }, [sbJwt(U2)]: { id: U2 } }),
        getServiceClient: () => db,
    });
    const app = express();
    app.use(express.json());
    app.use('/api/auth', authLink);
    const server = await new Promise((r) => { const s = app.listen(0, () => r(s)); });
    const base = `http://127.0.0.1:${server.address().port}/api/auth/link-supabase`;
    const post = async (fb, sb) => {
        const res = await fetch(base, { method: 'POST', headers: { 'content-type': 'application/json', ...(fb ? { authorization: `Bearer ${fb}` } : {}) }, body: JSON.stringify(sb ? { supabaseAccessToken: sb } : {}) });
        return { status: res.status, body: await res.json() };
    };
    return { db, post, close: () => server.close() };
}

test('link creates mapping, then is idempotent', async () => {
    const { db, post, close } = await boot();
    const a = await post('fbA', sbJwt(U1));
    assert.equal(a.status, 201); assert.equal(a.body.canonicalUid, 'A');
    assert.deepEqual(db.rows.map((r) => [r.supabase_user_id, r.canonical_uid, r.firebase_uid]), [[U1, 'A', 'A']]);
    const b = await post('fbA', sbJwt(U1));
    assert.equal(b.status, 200); assert.equal(b.body.alreadyLinked, true);
    assert.equal(db.rows.length, 1);
    close();
});

test('refuses relink: supabase user mapped to a different canonical uid', async () => {
    const { db, post, close } = await boot([{ supabase_user_id: U1, canonical_uid: 'A' }]);
    const r = await post('fbB', sbJwt(U1));
    assert.equal(r.status, 409); assert.equal(db.rows.length, 1); assert.equal(db.rows[0].canonical_uid, 'A');
    close();
});

test('refuses relink: canonical uid already mapped to a different supabase user', async () => {
    const { db, post, close } = await boot([{ supabase_user_id: U1, canonical_uid: 'A' }]);
    const r = await post('fbA', sbJwt(U2));
    assert.equal(r.status, 409); assert.equal(db.rows.length, 1);
    close();
});

test('rejects bad / missing tokens', async () => {
    const { db, post, close } = await boot();
    assert.equal((await post(null, sbJwt(U1))).status, 401);
    assert.equal((await post('forged', sbJwt(U1))).status, 401);
    assert.equal((await post('fbA', 'forged-supabase')).status, 401);
    assert.equal((await post('fbA', null)).status, 400);
    // a supabase token in the Authorization slot is NOT accepted as the firebase proof
    assert.equal((await post(sbJwt(U1), sbJwt(U1))).status, 401);
    assert.equal(db.rows.length, 0);
    close();
});

function runPayout(authz, body) {
    return new Promise((resolve) => {
        const req = { headers: authz ? { authorization: authz } : {}, body, query: {} };
        const res = { status(c) { this.code = c; return this; }, json(o) { resolve({ code: this.code, body: o }); } };
        import('../../middleware/payoutAuth.js').then(({ verifyCreator }) => verifyCreator(req, res, () => resolve({ code: 200, uid: req.verifiedUserId })));
    });
}

test('payoutAuth: firebase behaviour and messages unchanged; supabase maps to canonical uid', async () => {
    await boot([{ supabase_user_id: U1, canonical_uid: 'A' }]).then(async ({ close }) => {
        assert.deepEqual(await runPayout(undefined, {}), { code: 401, body: { error: 'Missing authorization token' } });
        assert.deepEqual(await runPayout('Bearer fbA', { creatorId: 'A' }), { code: 200, uid: 'A' });
        assert.deepEqual(await runPayout('Bearer fbA', { creatorId: 'B' }), { code: 403, body: { error: 'Creator ID does not match authenticated user' } });
        assert.deepEqual(await runPayout('Bearer bogus', { creatorId: 'A' }), { code: 401, body: { error: 'Invalid or expired token' } });
        assert.deepEqual(await runPayout(`Bearer ${sbJwt(U1)}`, { creatorId: 'A' }), { code: 200, uid: 'A' });
        assert.deepEqual(await runPayout(`Bearer ${sbJwt(U2)}`, { creatorId: 'A' }), { code: 403, body: { error: 'Account not linked' } });
        close();
    });
});

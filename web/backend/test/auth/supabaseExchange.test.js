import test from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import authLink from '../../routes/authLink.routes.js';
import { __setDeps, IdentityError, serviceClient } from '../../auth/verifyIdentity.js';
import { SB_URL, sbJwt, fakeSupabaseUsers, fakeDb } from './helpers.js';

const U1 = '11111111-1111-1111-1111-111111111111';
const U2 = '22222222-2222-2222-2222-222222222222';

async function boot({ rows = [], noDb = false, noFirebase = false, noSbCfg = false } = {}) {
    const db = fakeDb(rows);
    const minted = [];
    const users = fakeSupabaseUsers({ [sbJwt(U1)]: { id: U1, email: 'a@x.com' }, [sbJwt(U2)]: { id: U2, email: 'a@x.com' } });
    __setDeps({
        getFirebaseAuth: async () => {
            if (noFirebase) throw new Error('Missing FIREBASE_SERVICE_ACCOUNT_KEY environment variable');
            return { createCustomToken: async (uid, claims) => { minted.push({ uid, claims }); return `custom:${uid}`; } };
        },
        getSupabaseUrl: () => SB_URL,
        getSupabaseUser: async (t) => {
            if (noSbCfg) throw new IdentityError('not_configured', 'x', 503);
            return users(t);
        },
        getServiceClient: () => { if (noDb) throw new IdentityError('not_configured', 'x', 503); return db; },
    });
    const app = express();
    app.use(express.json());
    app.use('/api/auth', authLink);
    const server = await new Promise((r) => { const s = app.listen(0, () => r(s)); });
    const url = `http://127.0.0.1:${server.address().port}/api/auth/supabase-exchange`;
    const post = async (token) => {
        const res = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) }, body: '{}' });
        return { status: res.status, body: await res.json() };
    };
    return { db, minted, post, close: () => server.close() };
}

test('new user: creates sb_ mapping and returns custom token for it', async () => {
    const { db, minted, post, close } = await boot();
    const r = await post(sbJwt(U1));
    assert.equal(r.status, 200);
    assert.deepEqual(r.body, { firebaseCustomToken: `custom:sb_${U1}`, canonicalUid: `sb_${U1}`, isNew: true });
    assert.deepEqual(db.rows, [{ supabase_user_id: U1, canonical_uid: `sb_${U1}`, firebase_uid: null, provider: 'supabase' }]);
    assert.deepEqual(minted, [{ uid: `sb_${U1}`, claims: { provider: 'supabase' } }]);
    assert.ok(r.body.canonicalUid.length <= 128 && /^[A-Za-z0-9_-]+$/.test(r.body.canonicalUid));
    close();
});

test('repeat call is idempotent: same uid, one row, isNew false', async () => {
    const { db, post, close } = await boot();
    const a = await post(sbJwt(U1));
    const b = await post(sbJwt(U1));
    assert.equal(b.status, 200);
    assert.equal(b.body.canonicalUid, a.body.canonicalUid);
    assert.equal(b.body.isNew, false);
    assert.equal(db.rows.length, 1);
    close();
});

test('existing mapped legacy Firebase uid is used, nothing inserted', async () => {
    const { db, minted, post, close } = await boot({ rows: [{ supabase_user_id: U1, canonical_uid: 'LegacyFbUid123', firebase_uid: 'LegacyFbUid123' }] });
    const r = await post(sbJwt(U1));
    assert.deepEqual(r.body, { firebaseCustomToken: 'custom:LegacyFbUid123', canonicalUid: 'LegacyFbUid123', isNew: false });
    assert.equal(db.calls.insert, 0);
    assert.equal(minted[0].uid, 'LegacyFbUid123');
    close();
});

test('same email under another mapped uid is NOT merged', async () => {
    const { db, post, close } = await boot({ rows: [{ supabase_user_id: U1, canonical_uid: 'Legacy' }] });
    const r = await post(sbJwt(U2));
    assert.equal(r.body.canonicalUid, `sb_${U2}`);
    assert.equal(db.rows.find((x) => x.supabase_user_id === U1).canonical_uid, 'Legacy');
    assert.equal(db.rows.length, 2);
    close();
});

test('missing / forged / non-bearer tokens: 401 invalid_token, no writes', async () => {
    const { db, minted, post, close } = await boot();
    for (const t of [null, 'forged', sbJwt('33333333-3333-3333-3333-333333333333')]) {
        const r = await post(t);
        assert.equal(r.status, 401); assert.deepEqual(r.body, { error: 'invalid_token' });
    }
    assert.equal(db.rows.length, 0); assert.equal(minted.length, 0);
    close();
});

test('supabase user id that is not a uuid is rejected', async () => {
    const { db, post, close } = await boot();
    __setDeps({ ...(await import('../../auth/verifyIdentity.js')).__getDeps(), getSupabaseUser: async () => ({ id: 'abc/../x' }) });
    const r = await post('anything');
    assert.equal(r.status, 401); assert.equal(db.rows.length, 0);
    close();
});

test('missing service config, firebase admin, or supabase config: 503 auth_bridge_unavailable', async () => {
    for (const opt of [{ noDb: true }, { noFirebase: true }, { noSbCfg: true }]) {
        const { post, close } = await boot(opt);
        const r = await post(sbJwt(U1));
        assert.equal(r.status, 503, JSON.stringify(opt)); assert.deepEqual(r.body, { error: 'auth_bridge_unavailable' });
        close();
    }
});

test('service client accepts SUPABASE_SERVICE_KEY or SUPABASE_SERVICE_ROLE_KEY', () => {
    const saved = { ...process.env };
    try {
        process.env.SUPABASE_URL = SB_URL;
        delete process.env.SUPABASE_SERVICE_ROLE_KEY; delete process.env.SUPABASE_SERVICE_KEY;
        assert.throws(() => serviceClient(), (e) => e.status === 503);
        process.env.SUPABASE_SERVICE_KEY = 'k1';
        assert.ok(serviceClient());
    } finally {
        for (const k of Object.keys(process.env)) if (!(k in saved)) delete process.env[k];
        Object.assign(process.env, saved);
    }
});

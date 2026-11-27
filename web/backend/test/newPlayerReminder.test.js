// The new-player daily reminder: off by default, small, never twice, opt-out respected. Everything is faked: no
// database, no FCM, no message is ever sent.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {
    reminderConfig, planReminder, runReminder, startNewPlayerReminder, runLogId, sendLogId,
    LOG_TYPE, RUN_LOG_TYPE, PUSH_TYPE, REMINDER_TEXT, HARD_DAILY_CAP,
} from '../services/newPlayerReminder.js';
import { trafficLabel, clientAddress } from '../services/trafficClass.js';

const NOW = new Date('2026-11-27T15:05:00Z');
const daysAgo = (n, base = NOW) => new Date(base.getTime() - n * 86400e3).toISOString();

/** Just enough of the supabase-js query builder for the service, recording every call. */
function fakeDb(tables = {}, { failOn = {} } = {}) {
    const store = { user_notification_tokens: [], user_notification_preferences: [], notification_logs: [], ...tables };
    const calls = [];
    return {
        store,
        calls,
        from(table) {
            const q = { filters: [], op: 'select', limit: Infinity, count: false };
            const matches = (row) => q.filters.every((f) => f(row));
            const run = () => {
                calls.push(`${q.op} ${table}`);
                const fail = failOn[`${q.op} ${table}`];
                if (fail) return { data: null, error: fail };
                const rows = store[table];
                if (q.op === 'insert') {
                    if (rows.some((r) => r.id === q.row.id)) return { data: null, error: { code: '23505', message: 'duplicate key' } };
                    rows.push({ ...q.row });
                    return { data: null, error: null };
                }
                if (q.op === 'update') {
                    rows.filter(matches).forEach((r) => Object.assign(r, q.row));
                    return { data: null, error: null };
                }
                let found = rows.filter(matches);
                const count = found.length;
                if (q.order) found = [...found].sort((a, b) => String(b[q.order]).localeCompare(String(a[q.order])));
                return { data: found.slice(0, q.limit), error: null, count: q.count ? count : null };
            };
            const api = {
                select(_cols, opts) { q.count = opts?.count === 'exact'; return api; },
                insert(row) { q.op = 'insert'; q.row = row; return api; },
                update(row) { q.op = 'update'; q.row = row; return api; },
                eq(col, v) { q.filters.push((r) => r[col] === v); return api; },
                gte(col, v) { q.filters.push((r) => String(r[col]) >= v); return api; },
                in(col, list) { q.filters.push((r) => list.includes(r[col])); return api; },
                order(col) { q.order = col; return api; },
                limit(n) { q.limit = n; return api; },
                then(resolve, reject) { return Promise.resolve(run()).then(resolve, reject); },
            };
            return api;
        },
    };
}

/** n players: account created `age` days ago, last app open `opened` days ago. */
function world(specs) {
    const tokens = [];
    const users = new Map();
    specs.forEach((s, i) => {
        const email = s.email || `player${i}@mail.test`;
        const uid = s.uid || `uid${i}`;
        tokens.push({
            user_email: email, user_id: uid, fcm_token: s.token || `tok${i}`, platform: s.platform || 'android',
            is_active: s.active ?? true, language: s.language ?? null, created_at: daysAgo(s.opened ?? 1),
        });
        users.set(uid, { uid, email: s.authEmail || email, disabled: Boolean(s.disabled), metadata: { creationTime: new Date(daysAgo(s.age ?? 2)).toUTCString() } });
    });
    const batches = [];
    const auth = { getUsers: async (ids) => { batches.push(ids.length); return { users: ids.map((x) => users.get(x.uid)).filter(Boolean) }; } };
    return { tokens, auth, batches };
}

function fakeMessaging(fail = () => null) {
    const sent = [];
    return { sent, messaging: () => ({ send: async (m) => { const code = fail(m); if (code) throw Object.assign(new Error('no'), { code }); sent.push(m); return 'id'; } }) };
}

const ON = reminderConfig({ NEW_PLAYER_REMINDER_ENABLED: 'true' });
const quiet = { log: () => {} };

test('off by default, bounded settings', () => {
    assert.deepEqual(reminderConfig({}), { enabled: false, dailyCap: 20, newDays: 7, hourUtc: 15, maxPerPlayer: 3 });
    for (const v of ['false', '0', 'yes', '']) assert.equal(reminderConfig({ NEW_PLAYER_REMINDER_ENABLED: v }).enabled, false);
    const wild = reminderConfig({ NEW_PLAYER_REMINDER_DAILY_CAP: '999999', NEW_PLAYER_REMINDER_NEW_DAYS: '900', NEW_PLAYER_REMINDER_HOUR_UTC: '23', NEW_PLAYER_REMINDER_MAX_PER_PLAYER: '50' });
    assert.deepEqual([wild.dailyCap, wild.newDays, wild.hourUtc, wild.maxPerPlayer], [HARD_DAILY_CAP, 30, 21, 7]);
});

test('switched off: no timer, no query, no send', async () => {
    const lines = [];
    let timers = 0;
    const handle = startNewPlayerReminder({ env: {}, deps: {}, timers: { setInterval: () => { timers++; }, setTimeout: () => { timers++; } }, log: (l) => lines.push(l) });
    assert.equal(handle, null);
    assert.equal(timers, 0);
    assert.match(lines[0], /OFF/);
    const db = fakeDb();
    const m = fakeMessaging();
    assert.deepEqual(await runReminder({ db, messaging: m.messaging, auth: null, config: reminderConfig({}), now: NOW, ...quiet }), { skipped: 'disabled' });
    assert.equal(db.calls.length, 0);
    assert.equal(m.sent.length, 0);
});

test('only new, not-seen-today, reachable, signed-up-before-today players are chosen', async () => {
    const w = world([
        { email: 'good@mail.test' },
        { email: 'old-account@mail.test', age: 40 },
        { email: 'signed-up-today@mail.test', age: 0 },
        { email: 'opened-today@mail.test', opened: 0 },
        { email: 'ios@mail.test', platform: 'ios' },
        { email: 'inactive@mail.test', active: false },
        { email: 'french@mail.test', language: 'fr' },
        { email: 'someone.12345@gmail.com' },
        { email: 'robot@cloudtestlabaccounts.com' },
        { email: 'spoofed@mail.test', authEmail: 'other@mail.test' },
        { email: 'disabled@mail.test', disabled: true },
        { email: 'stale@mail.test', opened: 20, age: 3 },
    ]);
    const db = fakeDb({ user_notification_tokens: w.tokens });
    const { recipients, counts } = await planReminder({ db, auth: w.auth, config: ON, now: NOW });
    assert.deepEqual(recipients.map((r) => r.email), ['good@mail.test']);
    assert.equal(counts.selected, 1);
    assert.ok(db.calls.every((c) => c.startsWith('select ')), 'planning only reads');
});

test('opening the app today removes a player even with an older token row, and one token is used once', async () => {
    const w = world([
        { email: 'two@mail.test', uid: 'u-two', token: 'old', opened: 2 },
        { email: 'two@mail.test', uid: 'u-two', token: 'new', opened: 0 },
        { email: 'a@mail.test', token: 'shared' },
        { email: 'b@mail.test', token: 'shared' },
    ]);
    const { recipients } = await planReminder({ db: fakeDb({ user_notification_tokens: w.tokens }), auth: w.auth, config: ON, now: NOW });
    assert.equal(recipients.length, 1);
    assert.equal(recipients[0].token, 'shared');
});

test('opt-out and the per-player limit are respected', async () => {
    const w = world([{ email: 'No-Daily@mail.test' }, { email: 'noremind@mail.test' }, { email: 'had3@mail.test' }, { email: 'had2@mail.test' }]);
    // The app writes the opt-out under the lower-cased address.
    const prefs = [
        { user_email: 'no-daily@mail.test', daily_notifications: false, puzzle_reminders: true },
        { user_email: 'noremind@mail.test', daily_notifications: true, puzzle_reminders: false },
    ];
    const history = (email, n) => Array.from({ length: n }, (_, i) => ({ id: `${email}${i}`, user_email: email, notification_type: LOG_TYPE, sent_at: daysAgo(i + 1) }));
    const db = fakeDb({ user_notification_tokens: w.tokens, user_notification_preferences: prefs, notification_logs: [...history('had3@mail.test', 3), ...history('had2@mail.test', 2)] });
    const { recipients, counts } = await planReminder({ db, auth: w.auth, config: ON, now: NOW });
    assert.deepEqual(recipients.map((r) => r.email), ['had2@mail.test']);
    assert.equal(counts.optedOut, 2);
    assert.equal(counts.atLimit, 1);
});

test('a run sends the draft once per person, records it, and a second run the same day sends nothing', async () => {
    const w = world([{}, {}, {}]);
    const db = fakeDb({ user_notification_tokens: w.tokens });
    const m = fakeMessaging();
    const args = { db, messaging: m.messaging, auth: w.auth, config: ON, now: NOW, ...quiet };
    const first = await runReminder(args);
    assert.equal(first.sent, 3);
    assert.equal(m.sent[0].notification.title, REMINDER_TEXT.en.title);
    assert.deepEqual(m.sent[0].data, { type: PUSH_TYPE });
    assert.deepEqual(await runReminder({ ...args, now: new Date(NOW.getTime() + 15 * 60e3) }), { skipped: 'already_ran' });
    assert.equal(m.sent.length, 3);
    const run = db.store.notification_logs.find((r) => r.id === runLogId(NOW));
    assert.equal(run.notification_type, RUN_LOG_TYPE);
    assert.equal(run.data.sent, 3);
    assert.equal(db.store.notification_logs.filter((r) => r.notification_type === LOG_TYPE && r.success).length, 3);
});

test('two machines at once: one claims the day', async () => {
    const w = world([{}, {}]);
    const db = fakeDb({ user_notification_tokens: w.tokens });
    const m = fakeMessaging();
    const args = { db, messaging: m.messaging, auth: w.auth, config: ON, now: NOW, ...quiet };
    const results = await Promise.all([runReminder(args), runReminder(args)]);
    assert.equal(results.filter((r) => r.skipped === 'already_ran').length, 1);
    assert.equal(m.sent.length, 2);
});

test('the daily cap holds, counts rows already written today, and auth lookups are batched', async () => {
    const w = world(Array.from({ length: 250 }, () => ({})));
    const config = reminderConfig({ NEW_PLAYER_REMINDER_ENABLED: 'true', NEW_PLAYER_REMINDER_DAILY_CAP: '5' });
    const earlier = [1, 2].map((i) => ({ id: `e${i}`, user_email: `x${i}@mail.test`, notification_type: LOG_TYPE, sent_at: NOW.toISOString() }));
    const db = fakeDb({ user_notification_tokens: w.tokens, notification_logs: earlier });
    const m = fakeMessaging();
    const result = await runReminder({ db, messaging: m.messaging, auth: w.auth, config, now: NOW, ...quiet });
    assert.equal(result.sent, 3);
    assert.equal(m.sent.length, 3);
    assert.deepEqual(w.batches, [100, 100, 50]);
    // The most one run can read: 250 candidates are cut to the hard cap before preferences and history are read.
    const reads = (table) => db.calls.filter((c) => c === `select ${table}`).length;
    assert.deepEqual([reads('user_notification_tokens'), reads('user_notification_preferences'), reads('notification_logs')], [1, 2, 3]);
});

test('a person already recorded today is not sent again', async () => {
    const w = world([{ email: 'again@mail.test' }]);
    const db = fakeDb({ user_notification_tokens: w.tokens, notification_logs: [{ id: sendLogId(NOW, 'again@mail.test'), notification_type: 'other', sent_at: NOW.toISOString() }] });
    const m = fakeMessaging();
    const result = await runReminder({ db, messaging: m.messaging, auth: w.auth, config: ON, now: NOW, ...quiet });
    assert.equal(result.sent, 0);
    assert.equal(m.sent.length, 0);
});

test('fails closed: a failed read or record sends nothing and is reported', async () => {
    for (const failOn of ['select user_notification_tokens', 'select user_notification_preferences', 'select notification_logs']) {
        const w = world([{}]);
        const db = fakeDb({ user_notification_tokens: w.tokens }, { failOn: { [failOn]: { code: '500', message: 'down' } } });
        const m = fakeMessaging();
        const reports = [];
        await runReminder({ db, messaging: m.messaging, auth: w.auth, config: ON, now: NOW, report: (f) => reports.push(f), ...quiet });
        assert.equal(m.sent.length, 0, failOn);
        assert.equal(reports.length, 1, failOn);
    }
    const w = world([{}]);
    const db = fakeDb({ user_notification_tokens: w.tokens }, { failOn: { 'insert notification_logs': { code: '500', message: 'down' } } });
    const m = fakeMessaging();
    assert.deepEqual(await runReminder({ db, messaging: m.messaging, auth: w.auth, config: ON, now: NOW, ...quiet }), { skipped: 'claim_failed' });
    assert.equal(m.sent.length, 0);
});

test('delivery to no device is recorded and reported', async () => {
    const w = world([{}, {}]);
    const db = fakeDb({ user_notification_tokens: w.tokens });
    const reports = [];
    const m = fakeMessaging(() => 'messaging/registration-token-not-registered');
    const result = await runReminder({ db, messaging: m.messaging, auth: w.auth, config: ON, now: NOW, report: (f) => reports.push(f), ...quiet });
    assert.equal(result.failed, 2);
    assert.deepEqual(result.errorCodes, { 'messaging/registration-token-not-registered': 2 });
    assert.equal(reports.length, 1);
});

test('the timer runs only inside the send window, once a day', async () => {
    let at = new Date('2026-11-27T14:59:00Z');
    let runs = 0;
    const handle = startNewPlayerReminder({
        env: { NEW_PLAYER_REMINDER_ENABLED: 'true' }, deps: {}, now: () => at, log: () => {},
        run: async () => { runs++; return {}; }, timers: { setInterval: () => null, setTimeout: () => null },
    });
    for (const [time, expected] of [['14:59', 0], ['15:00', 1], ['15:15', 1], ['18:00', 1]]) {
        at = new Date(`2026-11-27T${time}:00Z`);
        await handle.tick();
        assert.equal(runs, expected, time);
    }
    at = new Date('2026-11-28T17:59:00Z');
    await handle.tick();
    assert.equal(runs, 2);
});

test('the reminder code cannot read Firestore', () => {
    const source = fs.readFileSync(new URL('../services/newPlayerReminder.js', import.meta.url), 'utf8');
    assert.doesNotMatch(source, /firebaseAdmin|firestore\(|\.collection\(/);
});

test('the Android app opens the same push type', () => {
    const kt = fs.readFileSync(new URL('../../../android/app/src/main/java/com/kreativekoala/riddleverse/DailyReminder.kt', import.meta.url), 'utf8');
    assert.ok(kt.includes(`const val PUSH_TYPE = "${PUSH_TYPE}"`));
});

test('traffic labels: Test Lab ranges and the app flag, otherwise other', () => {
    assert.equal(trafficLabel('70.32.128.5'), 'testlab');
    assert.equal(trafficLabel('2001:4860:1008::1'), 'testlab');
    assert.equal(trafficLabel('203.0.113.9'), 'other');
    assert.equal(trafficLabel('203.0.113.9', true), 'testlab');
    assert.equal(trafficLabel(''), null);
    assert.equal(trafficLabel('garbage'), null);
    assert.equal(clientAddress({ headers: { 'cf-connecting-ip': '70.32.128.5', 'fly-client-ip': '1.1.1.1' }, ip: '2.2.2.2' }), '70.32.128.5');
    assert.equal(clientAddress({ headers: {}, ip: '::ffff:70.32.128.5' }), '70.32.128.5');
});

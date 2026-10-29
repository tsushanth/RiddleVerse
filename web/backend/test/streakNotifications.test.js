// Tests for the streak notification job: off-by-default flag, per-run caps,
// quota circuit breaker, no emails in logs, fail-closed dedup, run gap.
// Uses fakes only: no network, no Firestore, no Supabase.
import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';

// Allow the config modules to load without credentials (same switch the Docker build uses).
process.env.DOCKER_BUILD = 'true';
const {
  FirebaseStreakNotificationService,
  isStreakNotificationsEnabled,
  getStreakLimits,
  hashUser
} = await import('../services/firebaseStreakNotificationService.js');

const ENV_KEYS = [
  'STREAK_NOTIFICATIONS_ENABLED',
  'STREAK_MAX_USERS_PER_RUN',
  'STREAK_TOKEN_MAX_AGE_DAYS',
  'STREAK_MIN_RUN_GAP_MINUTES'
];
let savedEnv;
let logged;
let origLog, origErr;

beforeEach(() => {
  savedEnv = {};
  for (const k of ENV_KEYS) { savedEnv[k] = process.env[k]; delete process.env[k]; }
  logged = [];
  origLog = console.log; origErr = console.error;
  console.log = (...a) => logged.push(a.map(String).join(' '));
  console.error = (...a) => logged.push(a.map(String).join(' '));
});

afterEach(() => {
  console.log = origLog; console.error = origErr;
  for (const k of ENV_KEYS) {
    if (savedEnv[k] === undefined) delete process.env[k]; else process.env[k] = savedEnv[k];
  }
});

/**
 * Chainable fake of the supabase-js query builder.
 * handler(table, op, calls) returns { data, error }.
 */
function fakeSupabase(handler) {
  const calls = [];
  return {
    calls,
    from(table) {
      const rec = { table, op: 'select', filters: [], payload: null };
      calls.push(rec);
      const b = {
        select() { return b; },
        insert(p) { rec.op = 'insert'; rec.payload = p; return b; },
        update(p) { rec.op = 'update'; rec.payload = p; return b; },
        eq(...a) { rec.filters.push(['eq', ...a]); return b; },
        neq(...a) { rec.filters.push(['neq', ...a]); return b; },
        not(...a) { rec.filters.push(['not', ...a]); return b; },
        gte(...a) { rec.filters.push(['gte', ...a]); return b; },
        order() { return b; },
        limit(n) { rec.limit = n; return b; },
        then(res, rej) { return Promise.resolve(handler(table, rec.op, rec)).then(res, rej); }
      };
      return b;
    }
  };
}

function fakeDb({ docs = [], error = null } = {}) {
  const state = { gets: 0 };
  return {
    state,
    collection() {
      const q = {
        where() { return q; }, orderBy() { return q; }, limit() { return q; },
        async get() {
          state.gets++;
          if (error) throw error;
          return {
            empty: docs.length === 0,
            forEach(fn) { docs.forEach((d) => fn({ data: () => d })); }
          };
        }
      };
      return q;
    }
  };
}

function makeUsers(n) {
  return Array.from({ length: n }, (_, i) => ({
    user_email: `player${i}@example.test`,
    firebase_uid: `uid${i}`,
    fcm_token: `tok${i}`
  }));
}

const NO_ADMIN = { auth() { throw new Error('unused'); }, messaging() { throw new Error('unused'); } };

test('flag defaults to OFF and only "true"/"1" turn it on', () => {
  assert.equal(isStreakNotificationsEnabled({}), false);
  assert.equal(isStreakNotificationsEnabled({ STREAK_NOTIFICATIONS_ENABLED: '' }), false);
  assert.equal(isStreakNotificationsEnabled({ STREAK_NOTIFICATIONS_ENABLED: 'false' }), false);
  assert.equal(isStreakNotificationsEnabled({ STREAK_NOTIFICATIONS_ENABLED: '0' }), false);
  assert.equal(isStreakNotificationsEnabled({ STREAK_NOTIFICATIONS_ENABLED: 'yes' }), false);
  assert.equal(isStreakNotificationsEnabled({ STREAK_NOTIFICATIONS_ENABLED: 'true' }), true);
  assert.equal(isStreakNotificationsEnabled({ STREAK_NOTIFICATIONS_ENABLED: 'TRUE' }), true);
  assert.equal(isStreakNotificationsEnabled({ STREAK_NOTIFICATIONS_ENABLED: '1' }), true);
});

test('limits have safe defaults and ignore garbage values', () => {
  assert.deepEqual(getStreakLimits({}), { maxUsersPerRun: 25, tokenMaxAgeDays: 90, minRunGapMinutes: 60 });
  assert.equal(getStreakLimits({ STREAK_MAX_USERS_PER_RUN: 'abc' }).maxUsersPerRun, 25);
  assert.equal(getStreakLimits({ STREAK_MAX_USERS_PER_RUN: '-3' }).maxUsersPerRun, 25);
  assert.equal(getStreakLimits({ STREAK_MAX_USERS_PER_RUN: '40' }).maxUsersPerRun, 40);
});

test('when disabled: no timer, no Supabase call, no Firestore call', async () => {
  const sb = fakeSupabase(() => ({ data: [], error: null }));
  const db = fakeDb();
  const svc = new FirebaseStreakNotificationService({ supabase: sb, db, admin: NO_ADMIN });

  svc.startPeriodicService(60);
  await svc.runPeriodicCheck();

  assert.equal(svc.intervalId, null);
  assert.equal(sb.calls.length, 0);
  assert.equal(db.state.gets, 0);
  assert.ok(logged.some((l) => l.includes('Streak notification job is OFF')));
});

test('hashUser is short, stable and does not contain the address', () => {
  const h = hashUser('someone@example.test');
  assert.match(h, /^[0-9a-f]{8}$/);
  assert.equal(h, hashUser('someone@example.test'));
  assert.notEqual(h, hashUser('other@example.test'));
});

test('enabled run: token query is filtered and capped, Firestore reads at most the cap', async () => {
  process.env.STREAK_NOTIFICATIONS_ENABLED = 'true';
  process.env.STREAK_MAX_USERS_PER_RUN = '25';
  const sb = fakeSupabase((table, op) => {
    if (table === 'streak_notification_jobs' && op === 'select') return { data: [], error: null }; // no recent run
    if (table === 'user_notification_tokens') return { data: makeUsers(100), error: null };
    return { data: [], error: null };
  });
  const db = fakeDb({ docs: [] }); // every user has no recent app_open
  const svc = new FirebaseStreakNotificationService({ supabase: sb, db, admin: NO_ADMIN });

  await svc.runPeriodicCheck();

  assert.equal(db.state.gets, 25, 'one Firestore query per capped user, no more');
  const tokenQuery = sb.calls.find((c) => c.table === 'user_notification_tokens');
  assert.equal(tokenQuery.limit, 75);
  const kinds = tokenQuery.filters.map((f) => f.join(':'));
  assert.ok(kinds.includes('eq:is_active:true'));
  assert.ok(kinds.some((k) => k.startsWith('not:fcm_token:is')), 'null tokens excluded');
  assert.ok(kinds.includes('neq:fcm_token:'), 'empty tokens excluded');
  assert.ok(kinds.some((k) => k.startsWith('gte:updated_at:')), 'stale tokens excluded');
  const closed = sb.calls.find((c) => c.table === 'streak_notification_jobs' && c.op === 'update');
  assert.equal(closed.payload.status, 'completed');
});

test('quota circuit breaker: stops after the first batch and closes the job row as failed', async () => {
  process.env.STREAK_NOTIFICATIONS_ENABLED = 'true';
  const quota = Object.assign(new Error('8 RESOURCE_EXHAUSTED: Quota exceeded.'), { code: 8 });
  const sb = fakeSupabase((table, op) => {
    if (table === 'streak_notification_jobs' && op === 'select') return { data: [], error: null };
    if (table === 'user_notification_tokens') return { data: makeUsers(40), error: null };
    return { data: [], error: null };
  });
  const db = fakeDb({ error: quota });
  const svc = new FirebaseStreakNotificationService({ supabase: sb, db, admin: NO_ADMIN });

  await svc.runPeriodicCheck();

  assert.equal(db.state.gets, 5, 'only the first batch of 5 is attempted');
  const quotaLines = logged.filter((l) => l.includes('RESOURCE_EXHAUSTED'));
  assert.equal(quotaLines.length, 1, 'quota error is logged once per run, not once per user');
  const closed = sb.calls.find((c) => c.table === 'streak_notification_jobs' && c.op === 'update');
  assert.equal(closed.payload.status, 'failed');
  assert.equal(svc.isRunning, false);
  assert.equal(svc.processedUsers.size, 0);
});

test('no log line and no stored error detail contains a user email', async () => {
  process.env.STREAK_NOTIFICATIONS_ENABLED = 'true';
  const sb = fakeSupabase((table, op) => {
    if (table === 'streak_notification_jobs' && op === 'select') return { data: [], error: null };
    if (table === 'user_notification_tokens') return { data: makeUsers(6), error: null };
    return { data: [], error: null };
  });
  const db = fakeDb({ error: new Error('boom for player') });
  const svc = new FirebaseStreakNotificationService({ supabase: sb, db, admin: NO_ADMIN });

  await svc.runPeriodicCheck();

  assert.ok(logged.length > 0);
  for (const line of logged) assert.ok(!/@example\.test/.test(line), `email leaked in log: ${line}`);
  const closed = sb.calls.find((c) => c.table === 'streak_notification_jobs' && c.op === 'update');
  assert.ok(!/@example\.test/.test(JSON.stringify(closed.payload)), 'email leaked in job row');
  assert.ok(logged.some((l) => l.includes(hashUser('player0@example.test'))));
});

test('run gap: a recent job row skips the run; an unreadable gap check also skips (fails closed)', async () => {
  process.env.STREAK_NOTIFICATIONS_ENABLED = 'true';
  for (const answer of [{ data: [{ job_id: 'x' }], error: null }, { data: null, error: { message: 'down' } }]) {
    const sb = fakeSupabase(() => answer);
    const db = fakeDb();
    const svc = new FirebaseStreakNotificationService({ supabase: sb, db, admin: NO_ADMIN });
    await svc.runPeriodicCheck();
    assert.equal(db.state.gets, 0);
    assert.equal(sb.calls.filter((c) => c.op === 'insert').length, 0, 'no job row created');
  }
});

function recentOpenDocs() {
  const H = 3600 * 1000;
  return [22, 46, 70, 94].map((h) => ({ timestamp: Date.now() - h * H }));
}

test('dedup table unreadable: nothing is sent (fail closed)', async () => {
  process.env.STREAK_NOTIFICATIONS_ENABLED = 'true';
  let sent = 0;
  const admin = { auth() { throw new Error('unused'); }, messaging: () => ({ sendMulticast: async () => { sent++; return { successCount: 1 }; } }) };
  const sb = fakeSupabase((table, op) => {
    if (table === 'streak_notification_jobs' && op === 'select') return { data: [], error: null };
    if (table === 'user_notification_tokens') return { data: makeUsers(1), error: null };
    if (table === 'streak_notification_logs') return { data: null, error: { message: 'relation does not exist' } };
    return { data: [], error: null };
  });
  const svc = new FirebaseStreakNotificationService({ supabase: sb, db: fakeDb({ docs: recentOpenDocs() }), admin });

  await svc.runPeriodicCheck();

  assert.equal(sent, 0);
  const closed = sb.calls.find((c) => c.table === 'streak_notification_jobs' && c.op === 'update');
  assert.equal(closed.payload.notifications_sent, 0);
  assert.equal(closed.payload.errors_count, 1);
});

test('dedup readable and empty: one at-risk notification is sent and logged', async () => {
  process.env.STREAK_NOTIFICATIONS_ENABLED = 'true';
  let sent = 0;
  const admin = { auth() { throw new Error('unused'); }, messaging: () => ({ sendMulticast: async () => { sent++; return { successCount: 1 }; } }) };
  const sb = fakeSupabase((table, op) => {
    if (table === 'streak_notification_jobs' && op === 'select') return { data: [], error: null };
    if (table === 'user_notification_tokens') {
      // first call = user list, second = per-user fcm token lookup
      return { data: makeUsers(1), error: null };
    }
    return { data: [], error: null };
  });
  const svc = new FirebaseStreakNotificationService({ supabase: sb, db: fakeDb({ docs: recentOpenDocs() }), admin });

  await svc.runPeriodicCheck();

  assert.equal(sent, 1);
  assert.equal(sb.calls.filter((c) => c.table === 'streak_notification_logs' && c.op === 'insert').length, 1);
});

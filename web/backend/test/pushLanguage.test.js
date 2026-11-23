import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

process.env.DOCKER_BUILD = 'true';
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_KEY = 'test-service-key';

// Stand-in for PostgREST (supabase-js uses the global fetch captured when the client is built, so install it first).
const realFetch = globalThis.fetch;
const bodies = [];
let rejectLanguage = true;
globalThis.fetch = async (url, init) => {
    if (String(url).startsWith('http://supabase.test')) {
        const body = init?.body ? JSON.parse(init.body) : null;
        bodies.push(body);
        const hasLang = body && (Array.isArray(body) ? body[0] : body).language;
        if (hasLang && rejectLanguage) {
            return new Response(JSON.stringify({ message: "Could not find the 'language' column of 'user_notification_tokens' in the schema cache" }), { status: 400, headers: { 'content-type': 'application/json' } });
        }
        return new Response('[]', { status: 201, headers: { 'content-type': 'application/json' } });
    }
    return realFetch(url, init);
};
const { pushText, pushLanguage, localizedStreakPush, localizedPuzzlesReadyPush } = await import('../services/pushText.js');
const { FirebaseStreakNotificationService } = await import('../services/firebaseStreakNotificationService.js');
const { storeFCMToken } = await import('../services/dailyPuzzleService.js');
const { loadLocales } = await import('../services/telegram/i18n.js');

const ph = (s) => (String(s).match(/\{\w+\}/g) || []).sort().join(',');

test('every language has the push strings with the English placeholders, no em dashes', () => {
    const locales = loadLocales();
    const en = locales.get('en').push;
    assert.equal(Object.keys(en).length, 9);
    for (const [code, l] of locales) {
        assert.deepEqual(Object.keys(l.push), Object.keys(en), code);
        for (const k of Object.keys(en)) {
            assert.ok(l.push[k].trim().length > 0, `${code}.${k}`);
            assert.equal(ph(l.push[k]), ph(en[k]), `${code}.${k} placeholders`);
            assert.ok(!l.push[k].includes('—'), `${code}.${k} em dash`);
        }
    }
});

test('pushLanguage / pushText: stored language resolves, unknown falls back to English', () => {
    assert.equal(pushLanguage('de'), 'de');
    assert.equal(pushLanguage('pt'), 'pt');
    assert.equal(pushLanguage(null), 'en');
    assert.equal(pushLanguage('xx'), 'en');
    assert.equal(pushText('de', 'celebrate_title', { days: 7 }), '7-Tage-Serie!');
    assert.equal(pushText('xx', 'celebrate_title', { days: 7 }), '7-day streak!');
});

test('localized pushes: English keeps its original wording (null), other languages are localized', () => {
    assert.equal(localizedStreakPush('streak_at_risk', 'en', 5), null);
    assert.equal(localizedStreakPush('streak_at_risk', null, 5), null);
    const de = localizedStreakPush('streak_at_risk', 'de', 5);
    assert.match(de.title, /^🔥 /);
    assert.match(de.message, /5-Tage-Serie/);
    assert.match(localizedStreakPush('comeback', 'fr', 12).message, /12 jours/);
    assert.ok(!/\d/.test(localizedStreakPush('comeback', 'fr', 3).message), 'no-record comeback text has no number');
    assert.match(localizedStreakPush('celebration', 'ja', 30).title, /30/);
    assert.equal(localizedStreakPush('nope', 'de', 1), null);
    const ready = localizedPuzzlesReadyPush('es', 'Space', 5);
    assert.match(ready.body, /Space/);
    assert.match(ready.body, /5/);
    assert.equal(localizedPuzzlesReadyPush('en', 'Space', 5), null);
});

function fakeSupabase(tokenRows, { languageColumn = true } = {}) {
    const calls = [];
    return {
        calls,
        from(table) {
            const rec = { table, columns: null };
            calls.push(rec);
            const b = {
                select(c) { rec.columns = c; return b; },
                insert() { return b; }, update() { return b; },
                eq() { return b; }, neq() { return b; }, not() { return b; }, order() { return b; }, limit() { return b; },
                then(res, rej) {
                    let out;
                    if (table === 'user_notification_tokens' && /language/.test(rec.columns || '') && !languageColumn) {
                        out = { data: null, error: { message: 'column user_notification_tokens.language does not exist' } };
                    } else if (table === 'user_notification_tokens') {
                        out = { data: tokenRows.map(r => languageColumn ? r : { fcm_token: r.fcm_token }), error: null };
                    } else out = { data: [], error: null };
                    return Promise.resolve(out).then(res, rej);
                },
            };
            return b;
        },
    };
}
function fakeAdmin(sent) {
    return { messaging: () => ({ sendMulticast: async (m) => { sent.push(m); return { successCount: m.tokens.length, failureCount: 0 }; } }) };
}
const quiet = async (fn) => { const l = console.log, e = console.error; console.log = console.error = () => {}; try { return await fn(); } finally { console.log = l; console.error = e; } };

test('streak push: each device gets the text in the language it registered with', async () => {
    const sent = [];
    const svc = new FirebaseStreakNotificationService({
        supabase: fakeSupabase([{ fcm_token: 't-de', language: 'de' }, { fcm_token: 't-en', language: 'en' }, { fcm_token: 't-old', language: null }, { fcm_token: 't-ja', language: 'ja' }]),
        admin: fakeAdmin(sent), db: {},
    });
    const ok = await quiet(() => svc.sendStreakAtRiskNotification('a@b.c', 5));
    assert.equal(ok, true);
    const byToken = Object.fromEntries(sent.flatMap(m => m.tokens.map(t => [t, m.notification])));
    assert.equal(byToken['t-en'].title, "🔥 Don't Break Your Streak!");
    assert.equal(byToken['t-en'].body, "Your 5-day streak is amazing! Don't stop now! 🚀", 'English wording unchanged');
    assert.equal(byToken['t-old'].title, byToken['t-en'].title);
    assert.match(byToken['t-de'].body, /5-Tage-Serie/);
    assert.match(byToken['t-ja'].body, /5日/);
    assert.equal(sent.length, 3, 'one multicast per language');
});

test('streak push still works before the language column exists (all English)', async () => {
    const sent = [];
    const svc = new FirebaseStreakNotificationService({
        supabase: fakeSupabase([{ fcm_token: 't1' }, { fcm_token: 't2' }], { languageColumn: false }),
        admin: fakeAdmin(sent), db: {},
    });
    assert.equal(await quiet(() => svc.sendComebackNotification('a@b.c', 3)), true);
    assert.equal(sent.length, 1);
    assert.equal(sent[0].notification.title, '💪 Ready for a Comeback?');
    assert.deepEqual(sent[0].tokens, ['t1', 't2']);
});

test('storeFCMToken stores the language, and retries without it when the column is missing', async () => {
    const dailyPath = new URL('../services/dailyPuzzleService.js', import.meta.url);
    const src = fs.readFileSync(dailyPath, 'utf8');
    assert.match(src, /storeFCMToken\(userEmail, userId, fcmToken, platform, language = null\)/);
    assert.match(src, /language \? \{ \.\.\.row, language \} : row/);
    const routes = fs.readFileSync(new URL('../routes/dailyPuzzles.routes.js', import.meta.url), 'utf8');
    assert.match(routes, /storeFCMToken\(userEmail, userId, fcmToken, platform, language\)/);
    try {
        const r1 = await quiet(() => storeFCMToken('a@b.c', 'u', 'tok', 'android', 'de'));
        assert.equal(r1.success, true);
        assert.equal(bodies.length, 2, 'first with the language, then without');
        assert.ok(!('language' in (Array.isArray(bodies[1]) ? bodies[1][0] : bodies[1])));
        bodies.length = 0; rejectLanguage = false;
        const r2 = await quiet(() => storeFCMToken('a@b.c', 'u', 'tok', 'android', 'fr'));
        assert.equal(r2.success, true);
        assert.equal((Array.isArray(bodies[0]) ? bodies[0][0] : bodies[0]).language, 'fr');
        bodies.length = 0;
        await quiet(() => storeFCMToken('a@b.c', 'u', 'tok', 'android'));
        assert.ok(!('language' in (Array.isArray(bodies[0]) ? bodies[0][0] : bodies[0])), 'old apps leave the stored language untouched');
    } finally {
        rejectLanguage = true;
    }
});

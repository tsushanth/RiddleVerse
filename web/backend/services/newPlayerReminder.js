// One daily-puzzle push for a small cohort of recent new players. OFF unless NEW_PLAYER_REMINDER_ENABLED=true.
//
// Who can get it: signed-in Android players (guests have no push token) whose account is at most NEW_DAYS old, who did
// not sign up today, did not open the app today (the app re-registers its token on every launch, which rewrites
// created_at), have not opted out, and have had fewer than MAX_PER_PLAYER of these.
//
// Cost per run: NO Firestore reads or writes. Supabase: at most 6 reads (1 token query of <= 1000 rows, 1 count of
// today's sends, 2 preference and 2 history queries of <= 100 people), then 1 insert and 1 update for the run and for
// each push. Firebase Auth: at most 10 batch lookups of 100.
//
// No duplicates and a hard cap: one run per UTC day (a fixed notification_logs id only one machine can insert), one
// row per person per day inserted BEFORE the send (same technique), and the cap counts the rows already written today.
// Every failure before a send means nothing is sent.
import crypto from 'node:crypto';

/** `data.type` of the push; the Android app opens the daily puzzle on it (DailyReminder.PUSH_TYPE). */
export const PUSH_TYPE = 'daily_reminder';
export const LOG_TYPE = 'new_player_reminder';
export const RUN_LOG_TYPE = 'new_player_reminder_run';

/** DRAFT TEXT, awaiting the owner's approval. Players whose app language has no text here get nothing. */
export const REMINDER_TEXT = {
    en: { title: "Today's puzzle is ready", body: "One new puzzle a day. Tap to play today's." },
};

export const HARD_DAILY_CAP = 200;
export const SEND_WINDOW_HOURS = 3;
const MAX_TOKEN_ROWS = 1000;
const AUTH_BATCH = 100;
/** People per preference / history query: the addresses travel in the request URL. */
const QUERY_BATCH = 100;
const DAY_MS = 24 * 3600 * 1000;
// Accounts that Google's pre-launch testers sign in with.
const TEST_ACCOUNT_RE = /@cloudtestlabaccounts\.com$|\.\d{5}@gmail\.com$/;

function boundedInt(value, fallback, min, max) {
    const n = Number.parseInt(value, 10);
    return Number.isFinite(n) ? Math.min(Math.max(n, min), max) : fallback;
}

export function reminderConfig(env = process.env) {
    const on = String(env.NEW_PLAYER_REMINDER_ENABLED || '').trim().toLowerCase();
    return {
        enabled: on === 'true' || on === '1',
        dailyCap: boundedInt(env.NEW_PLAYER_REMINDER_DAILY_CAP, 20, 0, HARD_DAILY_CAP),
        newDays: boundedInt(env.NEW_PLAYER_REMINDER_NEW_DAYS, 7, 1, 30),
        hourUtc: boundedInt(env.NEW_PLAYER_REMINDER_HOUR_UTC, 15, 0, 24 - SEND_WINDOW_HOURS),
        maxPerPlayer: boundedInt(env.NEW_PLAYER_REMINDER_MAX_PER_PLAYER, 3, 1, 7),
    };
}

/** A stable uuid for a key, so the primary key of notification_logs refuses a second insert. */
function fixedId(key) {
    const h = crypto.createHash('sha256').update(key).digest('hex');
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`;
}
const utcDay = (date) => date.toISOString().slice(0, 10);
export const runLogId = (date) => fixedId(`${RUN_LOG_TYPE}:${utcDay(date)}`);
export const sendLogId = (date, email) => fixedId(`${LOG_TYPE}:${utcDay(date)}:${String(email).trim().toLowerCase()}`);

const lower = (v) => String(v || '').trim().toLowerCase();
/** Stored addresses are not normalised: ask for both spellings. */
const spellings = (rows) => [...new Set(rows.flatMap((r) => [r.email, r.storedEmail]))];
const batches = (list, size) => Array.from({ length: Math.ceil(list.length / size) }, (_, i) => list.slice(i * size, (i + 1) * size));

function must({ data, error, count }, what) {
    if (error) throw Object.assign(new Error(`${what} query failed`), { code: error.code || null });
    return count === undefined || count === null ? data || [] : { data: data || [], count };
}

/** Reads only. Returns { recipients: [{ email, token, language }], counts } (counts carry no personal data). */
export async function planReminder({ db, auth, config, now = new Date() }) {
    const todayStart = Date.parse(`${utcDay(now)}T00:00:00Z`);
    const cutoff = now.getTime() - config.newDays * DAY_MS;
    const counts = { tokenRows: 0, candidates: 0, newAccounts: 0, optedOut: 0, atLimit: 0, sentToday: 0, selected: 0 };

    const rows = must(await db.from('user_notification_tokens')
        .select('user_email,user_id,fcm_token,language,created_at')
        .eq('platform', 'android').eq('is_active', true)
        .gte('created_at', new Date(cutoff).toISOString())
        .order('created_at', { ascending: false })
        .limit(MAX_TOKEN_ROWS), 'token');
    counts.tokenRows = rows.length;

    // Newest registration per person (rows arrive newest first). Opening the app today removes them.
    const byEmail = new Map();
    for (const r of rows) {
        const email = lower(r.user_email);
        if (!email || byEmail.has(email)) continue;
        byEmail.set(email, { email, storedEmail: r.user_email, uid: r.user_id, token: r.fcm_token, language: r.language || 'en', registeredAt: Date.parse(r.created_at) });
    }
    const seenTokens = new Set();
    let people = [...byEmail.values()].filter((p) => {
        if (!p.uid || !p.token || !(p.registeredAt < todayStart) || !REMINDER_TEXT[p.language]) return false;
        if (TEST_ACCOUNT_RE.test(p.email) || seenTokens.has(p.token)) return false;
        seenTokens.add(p.token);
        return true;
    });
    counts.candidates = people.length;

    // "New" is the account's age. The uid must belong to the address on the token row (registration is not signed in).
    const created = new Map();
    for (let i = 0; i < people.length; i += AUTH_BATCH) {
        const { users } = await auth.getUsers(people.slice(i, i + AUTH_BATCH).map((p) => ({ uid: p.uid })));
        for (const u of users) created.set(u.uid, u);
    }
    people = people.filter((p) => {
        const u = created.get(p.uid);
        if (!u || u.disabled || lower(u.email) !== p.email) return false;
        const at = Date.parse(u.metadata?.creationTime);
        return at >= cutoff && at < todayStart;
    });
    counts.newAccounts = people.length;

    const sentToday = must(await db.from('notification_logs')
        .select('id', { count: 'exact' })
        .eq('notification_type', LOG_TYPE).gte('sent_at', new Date(todayStart).toISOString())
        .limit(1), 'daily count');
    counts.sentToday = sentToday.count ?? sentToday.length;
    const room = Math.max(0, config.dailyCap - counts.sentToday);
    if (people.length === 0 || room === 0) return { recipients: [], counts };

    // Nobody beyond the hard cap can be sent today, so nobody beyond it is looked up.
    people = people.slice(0, HARD_DAILY_CAP);

    const optedOut = new Set();
    for (const group of batches(people, QUERY_BATCH)) {
        const prefs = must(await db.from('user_notification_preferences')
            .select('user_email,daily_notifications,puzzle_reminders')
            .in('user_email', spellings(group)), 'preference');
        prefs.filter((p) => p.daily_notifications === false || p.puzzle_reminders === false).forEach((p) => optedOut.add(lower(p.user_email)));
    }
    counts.optedOut = people.filter((p) => optedOut.has(p.email)).length;
    people = people.filter((p) => !optedOut.has(p.email));

    const had = new Map();
    for (const group of batches(people, QUERY_BATCH)) {
        const history = must(await db.from('notification_logs')
            .select('user_email')
            .eq('notification_type', LOG_TYPE).in('user_email', spellings(group))
            .limit(MAX_TOKEN_ROWS), 'history');
        for (const h of history) had.set(lower(h.user_email), (had.get(lower(h.user_email)) || 0) + 1);
    }
    const before = people.length;
    people = people.filter((p) => (had.get(p.email) || 0) < config.maxPerPlayer);
    counts.atLimit = before - people.length;

    const recipients = people.slice(0, room).map(({ email, token, language }) => ({ email, token, language }));
    counts.selected = recipients.length;
    return { recipients, counts };
}

/** One run. Does nothing when the switch is off. At most one run per UTC day sends. */
export async function runReminder({ db, messaging, auth, config, now = new Date(), report = () => {}, log = console.log }) {
    if (!config.enabled) return { skipped: 'disabled' };
    if (config.dailyCap === 0) return { skipped: 'cap_zero' };

    const id = runLogId(now);
    const claim = await db.from('notification_logs').insert({
        id, notification_type: RUN_LOG_TYPE, success: false, sent_at: now.toISOString(), data: { state: 'claimed', config },
    });
    if (claim.error) {
        if (claim.error.code === '23505') return { skipped: 'already_ran' };
        report('new_player_reminder', new Error('run claim failed; nothing sent'), { code: claim.error.code || null });
        return { skipped: 'claim_failed' };
    }

    let counts = {};
    let sent = 0;
    let failed = 0;
    let stopped = null;
    const errorCodes = {};
    try {
        const plan = await planReminder({ db, auth, config, now });
        counts = plan.counts;
        for (const r of plan.recipients) {
            const text = REMINDER_TEXT[r.language];
            const rowId = sendLogId(now, r.email);
            const row = await db.from('notification_logs').insert({
                id: rowId, user_email: r.email, notification_type: LOG_TYPE, title: text.title, body: text.body,
                success: false, sent_at: now.toISOString(), data: { state: 'sending' },
            });
            if (row.error) {
                if (row.error.code === '23505') continue; // already sent to this person today
                stopped = 'log_insert_failed';
                break;
            }
            let outcome;
            try {
                await messaging().send({
                    token: r.token,
                    notification: { title: text.title, body: text.body },
                    data: { type: PUSH_TYPE },
                    android: { ttl: 4 * 3600 * 1000, collapseKey: PUSH_TYPE, notification: { tag: PUSH_TYPE } },
                });
                sent++;
                outcome = { success: true, data: { state: 'done' } };
            } catch (err) {
                failed++;
                const code = err?.code || 'send_threw';
                errorCodes[code] = (errorCodes[code] || 0) + 1;
                outcome = { success: false, error_message: code, data: { state: 'failed' } };
            }
            await db.from('notification_logs').update(outcome).eq('id', rowId);
        }
    } catch (err) {
        stopped = 'plan_failed';
        report('new_player_reminder', err, { code: err?.code || null });
    }

    const summary = { ...counts, sent, failed, errorCodes, stopped };
    const done = await db.from('notification_logs').update({
        success: !stopped && (failed === 0 || sent > 0), error_message: stopped, data: { state: 'done', config, ...summary },
    }).eq('id', id);
    if (done.error) report('new_player_reminder', new Error('run result not recorded'), { code: done.error.code || null });
    if (stopped === 'log_insert_failed') report('new_player_reminder', new Error('stopped: cannot record a send'), {});
    if (failed > 0 && sent === 0) report('new_player_reminder', new Error('reminder delivered to no device'), { failed });
    log(`New player reminder: ${sent} sent, ${failed} failed, ${counts.newAccounts ?? 0} new accounts eligible by age${stopped ? `, stopped: ${stopped}` : ''}`);
    return summary;
}

/**
 * Checks every 15 minutes while a machine is up (machines stop when idle, so a fixed cron minute could be missed) and
 * runs inside [hourUtc, hourUtc + SEND_WINDOW_HOURS). No timer at all while the switch is off.
 */
export function startNewPlayerReminder({ env = process.env, deps, run = runReminder, now = () => new Date(), timers = { setInterval, setTimeout }, log = console.log }) {
    const config = reminderConfig(env);
    if (!config.enabled) {
        log('New player reminder is OFF (set NEW_PLAYER_REMINDER_ENABLED=true to enable)');
        return null;
    }
    let ranDay = null;
    const tick = async () => {
        const at = now();
        const hour = at.getUTCHours();
        if (hour < config.hourUtc || hour >= config.hourUtc + SEND_WINDOW_HOURS || ranDay === utcDay(at)) return;
        try {
            const result = await run({ ...deps, config, now: at });
            if (result?.skipped !== 'claim_failed') ranDay = utcDay(at);
        } catch (err) {
            deps?.report?.('new_player_reminder', err, {});
        }
    };
    timers.setTimeout(tick, 30_000)?.unref?.();
    timers.setInterval(tick, 15 * 60_000)?.unref?.();
    log(`New player reminder is ON: up to ${config.dailyCap} a day, accounts up to ${config.newDays} days old, from ${config.hourUtc}:00 UTC`);
    return { tick, config };
}

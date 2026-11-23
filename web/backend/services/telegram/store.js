// Persistence for the Telegram bot. Two implementations of one interface: Supabase (production) and in-memory (tests).
// Tables (see supabase/migrations/20261122120000_telegram_bot.sql): rv_telegram_users, rv_telegram_link_codes,
// rv_telegram_updates, plus the existing chat_scores and game_sessions (source = 'telegram'). The Supabase project is
// shared with other apps: every table here carries the rv_ prefix or is already RiddleVerse-only.

const PLATFORM = 'telegram';

export class StoreConflict extends Error {
    constructor(code) { super(code); this.name = 'StoreConflict'; this.code = code; }
}

// ---------------------------------------------------------------- Supabase
export function createSupabaseStore(sb) {
    const must = (r) => { if (r.error) throw r.error; return r.data; };
    return {
        async claimUpdate(updateId) {
            const { error } = await sb.from('rv_telegram_updates').insert({ update_id: updateId });
            if (!error) return true;
            if (error.code === '23505') return false; // already seen
            throw error;
        },
        async pruneUpdates(olderThanIso) {
            must(await sb.from('rv_telegram_updates').delete().lt('received_at', olderThanIso));
        },
        async getUser(tgId) {
            return must(await sb.from('rv_telegram_users').select('*').eq('tg_user_id', tgId).maybeSingle());
        },
        async upsertUser({ tgId, chatId, firstName, username, language }) {
            const existing = await this.getUser(tgId);
            const patch = { first_name: firstName ?? null, username: username ?? null, updated_at: new Date().toISOString() };
            if (chatId != null) patch.chat_id = chatId;
            if (existing) {
                must(await sb.from('rv_telegram_users').update({ ...patch, blocked: false }).eq('tg_user_id', tgId));
                return { ...existing, ...patch, blocked: false };
            }
            const row = { tg_user_id: tgId, language, ...patch };
            const { error } = await sb.from('rv_telegram_users').insert(row);
            if (error && error.code !== '23505') throw error;
            return { remind_daily: false, firebase_uid: null, blocked: false, ...row };
        },
        async setLanguage(tgId, language) {
            must(await sb.from('rv_telegram_users').update({ language, updated_at: new Date().toISOString() }).eq('tg_user_id', tgId));
        },
        async setReminder(tgId, on) {
            must(await sb.from('rv_telegram_users').update({ remind_daily: !!on, updated_at: new Date().toISOString() }).eq('tg_user_id', tgId));
        },
        async markBlocked(tgId, blocked) {
            must(await sb.from('rv_telegram_users').update({ blocked: !!blocked, ...(blocked ? { remind_daily: false } : {}) }).eq('tg_user_id', tgId));
        },
        async listReminderUsers(todayIso, limit) {
            return must(await sb.from('rv_telegram_users').select('tg_user_id, chat_id, language')
                .eq('remind_daily', true).eq('blocked', false).not('chat_id', 'is', null)
                .or(`last_daily_sent.is.null,last_daily_sent.lt.${todayIso}`).limit(limit)) || [];
        },
        async markDailySent(tgId, todayIso) {
            must(await sb.from('rv_telegram_users').update({ last_daily_sent: todayIso }).eq('tg_user_id', tgId));
        },
        async createLinkCode({ hash, tgId, expiresAt }) {
            // One live code per user: a new /link invalidates the previous unused one.
            must(await sb.from('rv_telegram_link_codes').delete().eq('tg_user_id', tgId).is('used_at', null));
            must(await sb.from('rv_telegram_link_codes').insert({ code_hash: hash, tg_user_id: tgId, expires_at: expiresAt }));
        },
        /** Atomic: exactly one caller can consume a code, and only before it expires. Returns { tgId } or null. */
        async consumeLinkCode(hash, nowIso) {
            const row = must(await sb.from('rv_telegram_link_codes').update({ used_at: nowIso })
                .eq('code_hash', hash).is('used_at', null).gt('expires_at', nowIso).select('tg_user_id').maybeSingle());
            return row ? { tgId: Number(row.tg_user_id) } : null;
        },
        async setFirebaseUid(tgId, uid) {
            const taken = must(await sb.from('rv_telegram_users').select('tg_user_id').eq('firebase_uid', uid).maybeSingle());
            if (taken && Number(taken.tg_user_id) !== Number(tgId)) throw new StoreConflict('account_already_linked');
            const { error } = await sb.from('rv_telegram_users').update({ firebase_uid: uid, linked_at: new Date().toISOString() }).eq('tg_user_id', tgId);
            if (error) { if (error.code === '23505') throw new StoreConflict('account_already_linked'); throw error; }
        },
        async upsertScore({ gameId, chatId, userId, username, score }) {
            const find = async () => must(await sb.from('chat_scores').select('id, score')
                .eq('game_id', gameId).eq('chat_id', chatId).eq('platform', PLATFORM).eq('user_id', userId).maybeSingle());
            let existing = await find();
            if (!existing) {
                const { error } = await sb.from('chat_scores').insert({ game_id: gameId, chat_id: chatId, platform: PLATFORM, user_id: userId, username: username || null, score });
                if (!error) return { improved: true, best: score };
                if (error.code !== '23505') throw error;
                existing = await find(); // lost a race with a parallel submit
            }
            if (existing && score > existing.score) {
                must(await sb.from('chat_scores').update({ score, username: username || null, updated_at: new Date().toISOString() }).eq('id', existing.id));
                return { improved: true, best: score };
            }
            return { improved: false, best: existing ? existing.score : score };
        },
        async topScores({ gameId, chatId, limit = 10 }) {
            const rows = must(await sb.from('chat_scores').select('user_id, username, score')
                .eq('game_id', gameId).eq('chat_id', chatId).eq('platform', PLATFORM)
                .order('score', { ascending: false }).limit(limit)) || [];
            return rows.map((r) => ({ userId: r.user_id, username: r.username, score: r.score }));
        },
        async insertSession(row) {
            must(await sb.from('game_sessions').insert(row));
        },
    };
}

// ---------------------------------------------------------------- Memory (tests, local dry runs)
export function createMemoryStore({ now = () => Date.now() } = {}) {
    const updates = new Set();
    const users = new Map();
    const codes = new Map();
    const scores = new Map(); // `${gameId}|${chatId}|${userId}` -> row
    const sessions = [];
    return {
        _users: users, _codes: codes, _scores: scores, _sessions: sessions, _updates: updates,
        async claimUpdate(id) { if (updates.has(id)) return false; updates.add(id); return true; },
        async pruneUpdates() {},
        async getUser(id) { return users.get(Number(id)) || null; },
        async upsertUser({ tgId, chatId, firstName, username, language }) {
            const cur = users.get(Number(tgId));
            const next = cur
                ? { ...cur, first_name: firstName ?? null, username: username ?? null, ...(chatId != null ? { chat_id: chatId } : {}), blocked: false }
                : { tg_user_id: Number(tgId), chat_id: chatId ?? null, first_name: firstName ?? null, username: username ?? null, language, remind_daily: false, firebase_uid: null, blocked: false, last_daily_sent: null };
            users.set(Number(tgId), next);
            return next;
        },
        async setLanguage(id, language) { const u = users.get(Number(id)); if (u) u.language = language; },
        async setReminder(id, on) { const u = users.get(Number(id)); if (u) u.remind_daily = !!on; },
        async markBlocked(id, blocked) { const u = users.get(Number(id)); if (u) { u.blocked = !!blocked; if (blocked) u.remind_daily = false; } },
        async listReminderUsers(today, limit) {
            return [...users.values()].filter((u) => u.remind_daily && !u.blocked && u.chat_id != null && (!u.last_daily_sent || u.last_daily_sent < today))
                .slice(0, limit).map((u) => ({ tg_user_id: u.tg_user_id, chat_id: u.chat_id, language: u.language }));
        },
        async markDailySent(id, today) { const u = users.get(Number(id)); if (u) u.last_daily_sent = today; },
        async createLinkCode({ hash, tgId, expiresAt }) {
            for (const [h, c] of codes) if (c.tgId === Number(tgId) && !c.usedAt) codes.delete(h);
            codes.set(hash, { tgId: Number(tgId), expiresAt: Date.parse(expiresAt), usedAt: null });
        },
        async consumeLinkCode(hash, nowIso) {
            const c = codes.get(hash);
            if (!c || c.usedAt || c.expiresAt <= Date.parse(nowIso)) return null;
            c.usedAt = nowIso;
            return { tgId: c.tgId };
        },
        async setFirebaseUid(tgId, uid) {
            for (const u of users.values()) if (u.firebase_uid === uid && u.tg_user_id !== Number(tgId)) throw new StoreConflict('account_already_linked');
            const u = users.get(Number(tgId));
            if (u) u.firebase_uid = uid;
        },
        async upsertScore({ gameId, chatId, userId, username, score }) {
            const k = `${gameId}|${chatId}|${userId}`;
            const cur = scores.get(k);
            if (!cur) { scores.set(k, { gameId, chatId, userId, username, score }); return { improved: true, best: score }; }
            if (score > cur.score) { cur.score = score; cur.username = username; return { improved: true, best: score }; }
            return { improved: false, best: cur.score };
        },
        async topScores({ gameId, chatId, limit = 10 }) {
            return [...scores.values()].filter((s) => s.gameId === gameId && s.chatId === chatId)
                .sort((a, b) => b.score - a.score).slice(0, limit).map((s) => ({ userId: s.userId, username: s.username, score: s.score }));
        },
        async insertSession(row) { sessions.push(row); },
    };
}

// HTTP handlers for the Telegram integration. Framework-free ((req, res) with res.status().json() / res.type().send()), so
// they are unit tested with plain objects; routes/telegram.routes.js only wires them to Express.
import { safeEqual, verifyInitData, verifyContext, LINK_CODE_RE } from './security.js';
import { computeScore, gameUuid, boardKey } from './scoring.js';
import { todayFor } from './dailySchedule.js';
import { escapeHtml } from './telegramApi.js';
import { noopChannelEvents } from './channelEvents.js';

const DIFFICULTIES = new Set(['easy', 'medium', 'hard']);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export function createTelegramHandlers({ bot, api, store, i18n, linkService, catalog, config, now = () => Date.now(), limiter, log = console, sleepFn = sleep, events = noopChannelEvents }) {
    const slugs = new Set(catalog.games.map((g) => g.slug));
    const ipOf = (req) => req.ip || req.headers?.['x-forwarded-for'] || 'unknown';

    // ------------------------------------------------------------ webhook
    /** Returns { done } (a promise for the background work) so tests can await it; Express ignores it. */
    async function webhook(req, res) {
        if (!config.webhookSecret) { res.status(503).json({ ok: false }); return { done: Promise.resolve() }; }
        const got = req.headers?.['x-telegram-bot-api-secret-token'];
        if (!safeEqual(String(got || ''), config.webhookSecret)) { res.status(401).json({ ok: false }); return { done: Promise.resolve() }; }
        const update = req.body;
        if (!update || typeof update !== 'object' || !Number.isSafeInteger(update.update_id)) { res.status(400).json({ ok: false }); return { done: Promise.resolve() }; }

        // Ack first: Telegram retries anything slower than its timeout, and a retry would double-post.
        res.status(200).json({ ok: true });
        const done = (async () => {
            let first = true;
            try { first = await store.claimUpdate(update.update_id); } catch (e) { log.warn?.('[telegram] dedupe unavailable:', e?.message); }
            if (!first) return;
            await bot.handleUpdate(update);
        })().catch((e) => log.error?.('[telegram] webhook work failed:', e?.message));
        return { done };
    }

    // ------------------------------------------------------------ score from the Mini App / play page
    async function score(req, res) {
        if (!limiter.take(`score-ip:${ipOf(req)}`, 1, 60, 60e3)) return res.status(429).json({ error: 'rate_limited' });
        const b = req.body || {};
        const auth = verifyInitData(b.initData, config.botToken, { now });
        if (!auth) return res.status(401).json({ error: 'invalid_init_data' });
        const slug = String(b.slug || '');
        const kind = b.kind === 'd' ? 'd' : 'p';
        if (!slugs.has(slug)) return res.status(400).json({ error: 'unknown_game' });
        if (b.difficulty != null && !DIFFICULTIES.has(b.difficulty)) return res.status(400).json({ error: 'bad_difficulty' });
        const points = computeScore({ difficulty: b.difficulty, durationMs: b.durationMs });
        if (points === null) return res.status(400).json({ error: 'bad_duration' });
        if (!limiter.take(`score-u:${auth.user.id}:${slug}`, 1, 1, 5e3)) return res.status(429).json({ error: 'too_fast' });

        // Where the score belongs: the group the signed context names (if valid for THIS game), else the player's private chat.
        let chatId = String(auth.user.id);
        const ctx = verifyContext(b.ctx || auth.startParam, config.botToken);
        if (ctx && ctx.slug === slug && ctx.kind === kind) chatId = ctx.chatId;

        const spec = todayFor(now());
        const key = boardKey(kind, slug, spec.number);
        const userId = `tg-${auth.user.id}`;
        const username = auth.user.username ? `@${auth.user.username}` : (auth.user.first_name || 'Player');
        const durationSec = Math.min(3600, Math.max(0, Math.round(Number(b.durationMs) / 1000)));
        try {
            const result = await store.upsertScore({ gameId: gameUuid(key), chatId, userId, username, score: points });
            const endedAt = new Date(now());
            await store.insertSession({
                user_id: userId, game_id: kind === 'd' ? `daily:${spec.number}:${slug}` : slug, source: 'telegram', chat_id: chatId,
                started_at: new Date(endedAt.getTime() - durationSec * 1000).toISOString(), ended_at: endedAt.toISOString(),
                duration_seconds: durationSec, is_billable: durationSec > 15, score: points, score_source: 'reported', forced_by: 'user_button',
            }).catch((e) => log.warn?.('[telegram] session insert failed:', e?.message));
            if (events.enabled) { // a verified finished round: the one bot event that comes from the Mini App, not from a chat update
                const stored = await store.getUser(auth.user.id).catch(() => null);
                events.emit('game_finished', {
                    chatId, userId: auth.user.id, language: i18n.resolve(stored?.language || auth.user.language_code),
                    props: { game: slug, kind, difficulty: b.difficulty, score: points, improved: result.improved, duration_s: durationSec,
                        chat_type: String(chatId) === String(auth.user.id) ? 'private' : 'group' },
                });
            }
            const leaderboard = await store.topScores({ gameId: gameUuid(key), chatId, limit: 10 });
            if (result.improved) announce({ auth, chatId, slug, kind, points, best: result.best, leaderboard, username }).catch((e) => log.warn?.('[telegram] announce failed:', e?.message));
            return res.json({ ok: true, score: points, best: result.best, improved: result.improved,
                leaderboard: leaderboard.map((r) => ({ name: r.username, score: r.score })) });
        } catch (e) {
            log.error?.('[telegram] score failed:', e?.message);
            return res.status(500).json({ error: 'score_failed' });
        }
    }

    async function announce({ auth, chatId, slug, kind, points, best, leaderboard, username }) {
        const user = await store.getUser(auth.user.id).catch(() => null);
        const lang = i18n.resolve(user?.language || auth.user.language_code);
        const label = i18n.game(lang, slug);
        const isPrivate = String(chatId) === String(auth.user.id);
        const lines = [`<b>${escapeHtml(i18n.t(lang, 'score_line', { name: username, score: points, game: label }))}</b>`];
        if (best > points) lines.push(escapeHtml(i18n.t(lang, 'score_best', { score: best })));
        if (!isPrivate) {
            const medals = ['🥇', '🥈', '🥉'];
            lines.push('');
            leaderboard.slice(0, 5).forEach((r, i) => lines.push(`${medals[i] || `${i + 1}.`} ${escapeHtml(r.username || 'Player')} - <b>${Number(r.score) | 0}</b>`));
        }
        const chatType = isPrivate ? 'private' : 'group';
        const kb = [[bot.helpers.playButton(lang, { kind, slug, chatId, chatType })],
            [bot.helpers.challengeButton(lang, slug, label)]];
        await api.sendMessage(chatId, lines.join('\n'), { reply_markup: { inline_keyboard: kb } });
    }

    // ------------------------------------------------------------ app link redeem
    async function redeem(req, res) {
        if (!limiter.take(`redeem-ip:${ipOf(req)}`, 1, 20, 60e3)) return res.status(429).json({ error: 'rate_limited' });
        const header = req.headers?.authorization || '';
        const idToken = header.startsWith('Bearer ') ? header.slice(7).trim() : null;
        const out = await linkService.redeem({ code: req.body?.code, idToken });
        if (!out.ok) {
            events.emit('link_redeem_failed', { props: { error: out.error, status: out.status } });
            return res.status(out.status).json({ error: out.error });
        }
        events.emit('link_redeemed', { userId: out.tgId, props: { mode: out.mode } });
        res.set?.('Cache-Control', 'no-store');
        return res.json({ mode: out.mode, uid: out.uid, ...(out.customToken ? { customToken: out.customToken } : {}) });
    }

    // ------------------------------------------------------------ daily reminders (called by an external scheduler)
    async function dailyPush(req, res) {
        if (!config.cronSecret) return res.status(503).json({ error: 'not_configured' });
        if (!safeEqual(String(req.headers?.['x-cron-secret'] || ''), config.cronSecret)) return res.status(401).json({ error: 'unauthorized' });
        const spec = todayFor(now());
        const today = new Date(now()).toISOString().slice(0, 10);
        const limit = Math.min(Math.max(parseInt(req.query?.limit, 10) || 300, 1), 1000);
        let sent = 0, gone = 0, failed = 0;
        const users = await store.listReminderUsers(today, limit);
        for (const u of users) {
            const lang = i18n.resolve(u.language);
            const label = i18n.game(lang, spec.type);
            try {
                await api.sendMessage(u.chat_id, escapeHtml(i18n.t(lang, 'reminder_text', { game: label })), {
                    reply_markup: { inline_keyboard: [[bot.helpers.playButton(lang, { kind: 'd', slug: spec.type, chatId: u.chat_id, chatType: 'private' })]] },
                });
                await store.markDailySent(u.tg_user_id, today);
                sent++;
            } catch (e) {
                if (e?.isGone) {
                    events.emit('bot_blocked', { chatId: u.chat_id, userId: u.tg_user_id, language: u.language, props: { via: 'daily_push' } });
                    await store.markBlocked(u.tg_user_id, true).catch(() => {}); gone++;
                }
                else { failed++; if (e?.retryAfter) await sleepFn(Math.min(e.retryAfter, 5) * 1000); }
            }
            await sleepFn(45); // stay well under Telegram's 30 messages per second
        }
        await store.pruneUpdates(new Date(now() - 3 * 86400e3).toISOString()).catch(() => {});
        events.emit('daily_push', { props: { candidates: users.length, sent, gone, failed } });
        return res.json({ ok: true, candidates: users.length, sent, gone, failed });
    }

    // ------------------------------------------------------------ usage numbers (docs/channels-metrics.md), same secret as the daily push
    async function stats(req, res) {
        if (!config.cronSecret) return res.status(503).json({ error: 'not_configured' });
        if (!safeEqual(String(req.headers?.['x-cron-secret'] || ''), config.cronSecret)) return res.status(401).json({ error: 'unauthorized' });
        const days = Math.min(Math.max(parseInt(req.query?.days, 10) || 7, 1), 90);
        try {
            const data = await store.channelStats(days);
            res.set?.('Cache-Control', 'no-store');
            return res.json({ generatedAt: new Date(now()).toISOString(), days, ...(data && typeof data === 'object' ? data : {}) });
        } catch (e) {
            const missing = e?.code === 'PGRST202' || e?.code === '42883' || /rv_channel_stats|schema cache/i.test(String(e?.message || ''));
            log.error?.('[telegram] stats failed:', e?.message);
            return res.status(missing ? 503 : 500).json({ error: missing ? 'channel_events_not_migrated' : 'stats_failed' });
        }
    }

    // ------------------------------------------------------------ https link page (opens the app, else Google Play)
    function linkPage(req, res) {
        const code = String(req.query?.code || '');
        const lang = i18n.resolve((req.headers?.['accept-language'] || '').split(',')[0]);
        res.set('Cache-Control', 'no-store');
        res.set('Referrer-Policy', 'no-referrer');
        res.set('X-Robots-Tag', 'noindex');
        if (!LINK_CODE_RE.test(code)) return res.status(400).type('text/plain').send('Invalid link');
        const play = `${config.playStoreBase}&utm_source=telegram&utm_medium=bot&utm_campaign=link`;
        const scheme = `riddleverse://auth?code=${encodeURIComponent(code)}`;
        const intent = `intent://auth?code=${encodeURIComponent(code)}#Intent;scheme=riddleverse;package=com.kreativekoala.riddleverse;S.browser_fallback_url=${encodeURIComponent(play)};end`;
        const ua = String(req.headers?.['user-agent'] || '');
        const t = (k) => escapeHtml(i18n.web(lang, k));
        const html = `<!doctype html><html lang="${escapeHtml(lang)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex"><title>RiddleVerse</title>`
            + `<style>body{font-family:system-ui,sans-serif;background:#4c1d95;color:#fff;display:flex;min-height:100vh;align-items:center;justify-content:center;margin:0}main{max-width:22rem;padding:1.5rem;text-align:center}a{display:block;margin:.75rem 0;padding:.9rem 1rem;border-radius:.8rem;background:#fff;color:#4c1d95;font-weight:700;text-decoration:none}a.s{background:transparent;color:#fff;border:1px solid #fff8}</style></head><body><main>`
            + `<h1>RiddleVerse</h1><a href="${escapeHtml(/Android/i.test(ua) ? intent : scheme)}">${t('cta_open_app')}</a><a class="s" href="${escapeHtml(play)}">${t('cta_play_store')}</a></main>`
            + (/Android/i.test(ua) ? `<script>setTimeout(function(){location.href=${JSON.stringify(intent)}},50)</script>` : '')
            + `</body></html>`;
        return res.type('html').send(html);
    }

    return { webhook, score, redeem, dailyPush, stats, linkPage };
}

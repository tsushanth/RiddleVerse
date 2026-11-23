// RiddleVerse Telegram bot: Play-in-Chat. Pure logic over injected dependencies (Telegram API client, store, link service,
// clock), so it is fully testable without a network. The webhook route (routes/telegram.routes.js) acks Telegram first and
// then calls handleUpdate in the background.
//
// Replies use parse_mode HTML and every dynamic value goes through escapeHtml (names, game titles, translations).
import { escapeHtml } from './telegramApi.js';
import { signContext } from './security.js';
import { boardKey, gameUuid } from './scoring.js';
import { todayFor } from './dailySchedule.js';

const MENU_COLUMNS = 2;

export function createBot({ api, store, i18n, linkService, catalog, config, now = () => Date.now(), limiter, log = console }) {
    const slugs = new Set(catalog.games.map((g) => g.slug));
    const origin = config.origin;
    const bold = (s) => `<b>${escapeHtml(s)}</b>`;

    // ------------------------------------------------------------ URLs
    const playStoreUrl = (campaign) => `${config.playStoreBase}&utm_source=telegram&utm_medium=bot&utm_campaign=${encodeURIComponent(campaign)}`;
    const shareLink = (slug, medium) => `${origin}/play/${slug}?src=share&utm_source=telegram&utm_medium=${medium}&utm_campaign=${slug}`;
    const shareUrl = (url, text) => `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(text)}`;
    const botLink = (start) => `https://t.me/${config.username}${start ? `?start=${start}` : ''}`;

    /** Button that starts the game: a Mini App button in private chats, a link elsewhere (web_app buttons are private-only). */
    function playButton(lang, { kind, slug, chatId, chatType }) {
        const label = `▶️ ${i18n.t(lang, 'btn_play_now')}`;
        const ctx = signContext({ kind, slug, chatId }, config.botToken);
        if (chatType === 'private') {
            return { text: label, web_app: { url: `${origin}/play/${slug}?src=telegram&tgctx=${ctx}` } };
        }
        if (config.miniAppShortName) {
            return { text: label, url: `https://t.me/${config.username}/${config.miniAppShortName}?startapp=${ctx}` };
        }
        return { text: label, url: `${origin}/play/${slug}?src=telegram&utm_source=telegram&utm_medium=group` };
    }

    function challengeButton(lang, slug, gameLabel) {
        return { text: `📤 ${i18n.t(lang, 'btn_challenge')}`, url: shareUrl(shareLink(slug, 'bot_challenge'), i18n.t(lang, 'challenge_text', { game: gameLabel })) };
    }

    function mainMenu(lang, c) {
        const rows = [
            [{ text: `📅 ${i18n.t(lang, 'btn_daily')}`, callback_data: 'd' }, { text: `🎮 ${i18n.t(lang, 'btn_play')}`, callback_data: 'play' }],
            [{ text: `🏆 ${i18n.t(lang, 'btn_leaderboard')}`, callback_data: 'lbs' }, { text: `🌐 ${i18n.t(lang, 'btn_language')}`, callback_data: 'langs' }],
        ];
        if (c.chatType === 'private') rows.push([{ text: `🔗 ${i18n.t(lang, 'btn_link')}`, callback_data: 'link' }]);
        rows.push([{ text: `📱 ${i18n.t(lang, 'btn_install')}`, url: playStoreUrl('menu') }]);
        return { inline_keyboard: rows };
    }

    function gameGrid(lang, prefix, extraFirstRow) {
        const rows = extraFirstRow ? [extraFirstRow] : [];
        let row = [];
        for (const g of catalog.games) {
            row.push({ text: i18n.game(lang, g.slug), callback_data: `${prefix}:${g.slug}` });
            if (row.length === MENU_COLUMNS) { rows.push(row); row = []; }
        }
        if (row.length) rows.push(row);
        return { inline_keyboard: rows };
    }

    // ------------------------------------------------------------ context
    async function contextFor(update) {
        const msg = update.message || update.callback_query?.message;
        const from = update.message?.from || update.callback_query?.from;
        if (!msg || !from || from.is_bot) return null;
        const chat = msg.chat;
        const stored = await store.getUser(from.id).catch(() => null);
        const lang = i18n.resolve(stored?.language || from.language_code);
        return { chatId: chat.id, chatType: chat.type, from, lang, user: stored, messageId: update.callback_query ? msg.message_id : null };
    }

    const reply = (c, text, extra) => api.sendMessage(c.chatId, text, extra);

    async function remember(c) {
        try {
            return await store.upsertUser({
                tgId: c.from.id, chatId: c.chatType === 'private' ? c.chatId : undefined,
                firstName: c.from.first_name, username: c.from.username, language: i18n.resolve(c.from.language_code),
            });
        } catch (e) { log.warn?.('[telegram] user upsert failed:', e?.message); return null; }
    }

    // ------------------------------------------------------------ commands
    async function cmdStart(c, args) {
        const payload = (args || '').trim();
        let m;
        if ((m = /^g_([a-z]{2,24})$/.exec(payload)) && slugs.has(m[1])) return sendGame(c, m[1], 'p');
        if (payload === 'daily') return cmdDaily(c);
        if (payload === 'link') return cmdLink(c);
        const name = c.from.first_name || '';
        const text = c.chatType === 'private' ? i18n.t(c.lang, 'welcome', { name }) : i18n.t(c.lang, 'welcome_group');
        await reply(c, escapeHtml(text), { reply_markup: mainMenu(c.lang, c) });
    }

    async function cmdHelp(c) {
        await reply(c, escapeHtml(i18n.t(c.lang, 'help')), { reply_markup: mainMenu(c.lang, c) });
    }

    async function sendGame(c, slug, kind, { dailyNumber } = {}) {
        const label = i18n.game(c.lang, slug);
        const lines = [bold(label)];
        if (kind === 'd') lines.unshift(bold(i18n.t(c.lang, 'daily_title', { date: new Date(now()).toISOString().slice(0, 10) }) + ` #${dailyNumber}`));
        if (kind === 'd') lines.push(escapeHtml(i18n.t(c.lang, 'daily_body', { game: label })));
        const kb = [
            [playButton(c.lang, { kind, slug, chatId: c.chatId, chatType: c.chatType })],
            [{ text: `🏆 ${i18n.t(c.lang, 'btn_leaderboard')}`, callback_data: `lb:${kind === 'd' ? 'daily' : slug}` }, challengeButton(c.lang, slug, label)],
        ];
        await reply(c, lines.join('\n\n'), { reply_markup: { inline_keyboard: kb } });
    }

    async function cmdDaily(c) {
        const spec = todayFor(now());
        await sendGame(c, spec.type, 'd', { dailyNumber: spec.number });
    }

    function matchGame(arg) {
        const q = String(arg || '').trim().toLowerCase().replace(/[\s_-]+/g, '');
        if (!q) return null;
        if (slugs.has(q)) return q;
        for (const g of catalog.games) {
            if (g.name.toLowerCase().replace(/[\s_-]+/g, '') === q) return g.slug;
            for (const code of i18n.locales.keys()) {
                if ((i18n.locales.get(code).games?.[g.slug] || '').toLowerCase().replace(/[\s_-]+/g, '') === q) return g.slug;
            }
        }
        return null;
    }

    async function cmdPlay(c, args) {
        const slug = matchGame(args);
        if (slug) return sendGame(c, slug, 'p');
        const text = args && args.trim() ? i18n.t(c.lang, 'game_unknown') : i18n.t(c.lang, 'games_title');
        await reply(c, escapeHtml(text), { reply_markup: gameGrid(c.lang, 'g') });
    }

    async function showLeaderboard(c, which) {
        const isDaily = which === 'daily';
        const spec = todayFor(now());
        const slug = isDaily ? spec.type : which;
        const key = isDaily ? boardKey('d', slug, spec.number) : slug;
        const label = isDaily ? `${i18n.t(c.lang, 'btn_daily')} #${spec.number} (${i18n.game(c.lang, slug)})` : i18n.game(c.lang, slug);
        const rows = await store.topScores({ gameId: gameUuid(key), chatId: String(c.chatId), limit: 10 });
        const medals = ['🥇', '🥈', '🥉'];
        const lines = [bold(i18n.t(c.lang, 'leaderboard_title', { game: label })), ''];
        if (!rows.length) lines.push(escapeHtml(i18n.t(c.lang, 'leaderboard_empty')));
        rows.forEach((r, i) => lines.push(`${medals[i] || `${i + 1}.`} ${escapeHtml(r.username || 'Player')} - <b>${Number(r.score) | 0}</b>`));
        await reply(c, lines.join('\n'), {
            reply_markup: { inline_keyboard: [[playButton(c.lang, { kind: isDaily ? 'd' : 'p', slug, chatId: c.chatId, chatType: c.chatType })]] },
        });
    }

    async function cmdLeaderboard(c, args) {
        const slug = matchGame(args);
        if (slug) return showLeaderboard(c, slug);
        if (/^daily$/i.test((args || '').trim())) return showLeaderboard(c, 'daily');
        const first = [{ text: `📅 ${i18n.t(c.lang, 'btn_daily')}`, callback_data: 'lb:daily' }];
        await reply(c, escapeHtml(i18n.t(c.lang, 'leaderboard_pick')), { reply_markup: gameGrid(c.lang, 'lb', first) });
    }

    async function cmdLink(c) {
        if (c.chatType !== 'private') return reply(c, escapeHtml(i18n.t(c.lang, 'link_private_only')));
        if (!limiter.take(`link:${c.from.id}`, 1, 5, 3600e3)) return reply(c, escapeHtml(i18n.t(c.lang, 'rate_limited')));
        try {
            const { code, ttlMinutes } = await linkService.issue(c.from.id);
            const url = `${origin}/app/link?code=${encodeURIComponent(code)}&src=telegram`;
            await reply(c, escapeHtml(i18n.t(c.lang, 'link_intro', { minutes: ttlMinutes })), {
                reply_markup: { inline_keyboard: [[{ text: `📱 ${i18n.t(c.lang, 'btn_open_riddleverse')}`, url }]] },
                protect_content: true,
            });
        } catch (e) {
            log.error?.('[telegram] link issue failed:', e?.message);
            await reply(c, escapeHtml(i18n.t(c.lang, 'link_error')));
        }
    }

    async function cmdRemind(c) {
        if (c.chatType !== 'private') return reply(c, escapeHtml(i18n.t(c.lang, 'remind_private_only')));
        const on = !c.user?.remind_daily;
        await store.setReminder(c.from.id, on);
        await reply(c, escapeHtml(i18n.t(c.lang, on ? 'remind_on' : 'remind_off')));
    }

    async function cmdLanguage(c) {
        const rows = [];
        let row = [];
        for (const l of i18n.languages()) {
            row.push({ text: l.name, callback_data: `lang:${l.code}` });
            if (row.length === 3) { rows.push(row); row = []; }
        }
        if (row.length) rows.push(row);
        await reply(c, escapeHtml(i18n.t(c.lang, 'lang_prompt')), { reply_markup: { inline_keyboard: rows } });
    }

    async function setLanguage(c, code) {
        if (!i18n.has(code)) return;
        await store.setLanguage(c.from.id, code);
        const name = i18n.locales.get(code)._meta?.name || code;
        await reply(c, escapeHtml(i18n.t(code, 'lang_set', { language: name })), { reply_markup: mainMenu(code, c) });
    }

    async function cmdApp(c) {
        await reply(c, escapeHtml(i18n.t(c.lang, 'app_text')), {
            reply_markup: { inline_keyboard: [[{ text: `📱 ${i18n.t(c.lang, 'btn_install')}`, url: playStoreUrl('app_cmd') }]] },
        });
    }

    async function cmdCreate(c) {
        await reply(c, escapeHtml(i18n.t(c.lang, 'create_pointer')), {
            reply_markup: { inline_keyboard: [[{ text: `📱 ${i18n.t(c.lang, 'btn_install')}`, url: playStoreUrl('create') }]] },
        });
    }

    const COMMANDS = { start: cmdStart, help: cmdHelp, daily: cmdDaily, play: cmdPlay, games: cmdPlay, leaderboard: cmdLeaderboard, top: cmdLeaderboard,
        link: cmdLink, login: cmdLink, remind: cmdRemind, language: cmdLanguage, lang: cmdLanguage, app: cmdApp, create: cmdCreate };

    /** "/play@Riddleverse_bot sudoku" -> { name: 'play', args: 'sudoku' }; a command aimed at another bot returns null. */
    function parseCommand(text) {
        const m = /^\/([A-Za-z_]{1,32})(?:@([A-Za-z0-9_]{3,32}))?(?:\s+([\s\S]*))?$/.exec(text || '');
        if (!m) return null;
        if (m[2] && m[2].toLowerCase() !== config.username.toLowerCase()) return null;
        return { name: m[1].toLowerCase(), args: (m[3] || '').trim().slice(0, 200) };
    }

    async function onMessage(update) {
        const c = await contextFor(update);
        if (!c) return;
        const cmd = parseCommand(update.message.text);
        if (!cmd) {
            if (c.chatType === 'private' && update.message.text) { // plain text to the bot: show the menu, never loop in groups
                await remember(c);
                return reply(c, escapeHtml(i18n.t(c.lang, 'welcome', { name: c.from.first_name || '' })), { reply_markup: mainMenu(c.lang, c) });
            }
            return;
        }
        if (!limiter.take(`u:${c.from.id}`, 1, 20, 60e3)) {
            if (limiter.take(`warn:${c.from.id}`, 1, 1, 60e3)) await reply(c, escapeHtml(i18n.t(c.lang, 'rate_limited')));
            return;
        }
        const user = await remember(c);
        if (user) { c.user = user; c.lang = i18n.resolve(user.language || c.from.language_code); }
        const fn = COMMANDS[cmd.name];
        if (!fn) { if (c.chatType === 'private') await reply(c, escapeHtml(i18n.t(c.lang, 'unknown_command'))); return; }
        await fn(c, cmd.args);
    }

    async function onCallback(update) {
        const q = update.callback_query;
        const c = await contextFor(update);
        const ack = () => api.answerCallbackQuery(q.id).catch(() => {});
        if (!c) return ack();
        if (!limiter.take(`u:${c.from.id}`, 1, 20, 60e3)) return ack();
        await ack();
        const user = await remember(c);
        if (user) { c.user = user; c.lang = i18n.resolve(user.language || c.from.language_code); }
        const data = String(q.data || '');
        let m;
        if (data === 'd') return cmdDaily(c);
        if (data === 'play') return cmdPlay(c, '');
        if (data === 'lbs') return cmdLeaderboard(c, '');
        if (data === 'langs') return cmdLanguage(c);
        if (data === 'link') return cmdLink(c);
        if ((m = /^g:([a-z]{2,24})$/.exec(data)) && slugs.has(m[1])) return sendGame(c, m[1], 'p');
        if ((m = /^lb:([a-z]{2,24})$/.exec(data)) && (m[1] === 'daily' || slugs.has(m[1]))) return showLeaderboard(c, m[1]);
        if ((m = /^lang:([A-Za-z-]{2,10})$/.exec(data))) return setLanguage(c, m[1]);
    }

    /** Bot added to / removed from a chat, or blocked by a user. */
    async function onMyChatMember(update) {
        const m = update.my_chat_member;
        if (!m || !m.chat) return;
        const status = m.new_chat_member?.status;
        if (m.chat.type === 'private') {
            if (status === 'kicked' || status === 'left') await store.markBlocked(m.from.id, true);
            else if (status === 'member') await store.markBlocked(m.from.id, false);
            return;
        }
        if (status === 'member' || status === 'administrator') {
            const lang = i18n.resolve(m.from?.language_code);
            await api.sendMessage(m.chat.id, escapeHtml(i18n.t(lang, 'welcome_group')), {
                reply_markup: mainMenu(lang, { chatType: m.chat.type }),
            });
        }
    }

    return {
        async handleUpdate(update) {
            try {
                if (update.message) return await onMessage(update);
                if (update.callback_query) return await onCallback(update);
                if (update.my_chat_member) return await onMyChatMember(update);
            } catch (e) {
                if (e?.isGone) {
                    const id = update.message?.from?.id || update.callback_query?.from?.id;
                    if (id) await store.markBlocked(id, true).catch(() => {});
                    return;
                }
                log.error?.('[telegram] update failed:', e?.message || e);
            }
        },
        parseCommand,
        matchGame,
        // exposed for the daily push and the score announcements
        helpers: { playButton, challengeButton, shareLink, playStoreUrl, botLink },
    };
}

#!/usr/bin/env node
// One-time (and re-runnable) setup of @Riddleverse_bot. SAFE BY DEFAULT: without --apply it only prints what it would do.
//
//   node scripts/telegramSetup.mjs                    dry run: print the webhook call and the per-language command lists
//   node scripts/telegramSetup.mjs --write-docs       (re)generate docs/botfather/<lang>.txt from web/shared/i18n (no network)
//   RIDDLEVERSE_BOT_TOKEN=... TELEGRAM_WEBHOOK_SECRET=... node scripts/telegramSetup.mjs --apply
//        setWebhook (secret_token, allowed_updates), setMyCommands + setMyShortDescription + setMyDescription for every language
//
// Never prints the bot token or the webhook secret. Needs Node 18+. Does not send any chat message.
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { loadLocales } from '../services/telegram/i18n.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const DOCS = path.resolve(here, '..', '..', '..', 'docs', 'botfather');
const WEBHOOK_URL = process.env.TELEGRAM_WEBHOOK_URL || 'https://puzzleverseai.com/api/telegram/webhook';
const ALLOWED = ['message', 'callback_query', 'my_chat_member'];
const COMMANDS = ['daily', 'play', 'leaderboard', 'link', 'remind', 'language', 'app', 'help'];

const locales = loadLocales();
const plan = [...locales.entries()].map(([code, l]) => ({
  code,
  // The default (no language_code) list is English; every other list is shown to clients in that language.
  languageCode: code === 'en' ? undefined : code,
  // Telegram only takes two-letter ISO 639-1 language_codes: zh-TW / zh-HK have no list of their own (docs only), the
  // 'zh' list is what Telegram clients show for every Chinese variant.
  register: code === 'en' || /^[a-z]{2}$/.test(code),
  commands: COMMANDS.map((c) => ({ command: c, description: l.bot[`cmd_${c}`] })),
  shortDescription: l.bot.bot_short_description,
  description: l.bot.bot_description,
}));

function docFor(p) {
  const name = locales.get(p.code)._meta?.name || p.code;
  return [
    `Language: ${name} (${p.code})${p.register ? '' : '  [BotFather/API: not registrable, language_code must be 2 letters; the zh list covers it]'}${locales.get(p.code)._meta?.review === 'machine' ? '  [machine translation: needs native review]' : ''}`,
    '',
    'BotFather > /setcommands (default list) or API setMyCommands with language_code=' + (p.languageCode || '(none)') + ':',
    ...p.commands.map((c) => `${c.command} - ${c.description}`),
    '',
    'BotFather > /setabouttext (short description, max 120):',
    p.shortDescription,
    '',
    'BotFather > /setdescription (shown in an empty chat, max 512):',
    p.description,
    '',
  ].join('\n');
}

const args = new Set(process.argv.slice(2));
if (args.has('--write-docs')) {
  fs.mkdirSync(DOCS, { recursive: true });
  for (const p of plan) fs.writeFileSync(path.join(DOCS, `${p.code}.txt`), docFor(p));
  console.log(`wrote ${plan.length} files to ${DOCS}`);
  process.exit(0);
}

const token = process.env.RIDDLEVERSE_BOT_TOKEN || process.env.TELEGRAM_BOT_TOKEN;
const secret = process.env.TELEGRAM_WEBHOOK_SECRET;

async function call(method, payload) {
  const res = await fetch(`https://api.telegram.org/bot${token}/${method}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
  });
  const body = await res.json().catch(() => ({}));
  if (!body.ok) throw new Error(`${method} failed: ${body.description || res.status}`);
  return body.result;
}

if (!args.has('--apply')) {
  console.log('DRY RUN (add --apply to execute)\n');
  console.log(`setWebhook url=${WEBHOOK_URL} secret_token=<TELEGRAM_WEBHOOK_SECRET> allowed_updates=${JSON.stringify(ALLOWED)} max_connections=20`);
  for (const p of plan) console.log(`\n--- ${p.code}\n${docFor(p)}`);
  process.exit(0);
}

if (!token || !secret) { console.error('Set RIDDLEVERSE_BOT_TOKEN and TELEGRAM_WEBHOOK_SECRET.'); process.exit(1); }
if (!/^[A-Za-z0-9_-]{16,256}$/.test(secret)) { console.error('TELEGRAM_WEBHOOK_SECRET must be 16-256 chars of A-Z a-z 0-9 _ - (Telegram rule).'); process.exit(1); }

const me = await call('getMe', {});
console.log(`bot: @${me.username}`);
for (const p of plan.filter((x) => x.register)) {
  const lc = p.languageCode ? { language_code: p.languageCode } : {};
  await call('setMyCommands', { commands: p.commands, ...lc });
  await call('setMyShortDescription', { short_description: p.shortDescription, ...lc });
  await call('setMyDescription', { description: p.description, ...lc });
  console.log(`commands + descriptions set: ${p.code}`);
}
await call('setWebhook', { url: WEBHOOK_URL, secret_token: secret, allowed_updates: ALLOWED, max_connections: 20 });
const info = await call('getWebhookInfo', {});
console.log(`webhook set: ${info.url} pending=${info.pending_update_count} last_error=${info.last_error_message || 'none'}`);

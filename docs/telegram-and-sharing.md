# Telegram bot, account linking, sharing and web /play: how it works and how to go live

Status when this was written (2026-11-22): everything below is built and unit tested, NOT deployed, NO webhook set. Nothing here has been run against the real Telegram API.

## What exists

| Piece | Where |
|---|---|
| Bot webhook, Mini App score endpoint, link-code redeem, daily push, https link page | `web/backend/routes/telegram.routes.js`, logic in `web/backend/services/telegram/` |
| Bot strings (30 languages, machine translated), game names reused from the app | `web/shared/i18n/<code>.json` (same files the `/play` pages use) |
| Tables | `web/backend/supabase/migrations/20261122120000_telegram_bot.sql` (+ rollback) |
| One-time setup (webhook, commands, descriptions) | `web/backend/scripts/telegramSetup.mjs` |
| BotFather text per language | `docs/botfather/<code>.txt` |
| Live smoke test | `web/backend/scripts/telegram-live-test.sh` |
| Android: banner, result card, daily card, Settings rows, share links, deep links, link redeem | `TelegramPromo.kt`, `TelegramPlayCard.kt`, `TelegramBotBanner.kt`, `ShareLinks.kt`, `DeepLinkRouter.kt`, `TelegramLinkManager.kt`, `MainActivity.kt` |
| Web: Play in Telegram buttons, browser-language chrome, Mini App score submit, share | `web/frontend/src/pages/Play*.jsx`, `components/play/PlayShell.jsx`, `utils/telegramPlay.js`, `utils/playI18n*.js` |

## Hosting decision and cost

The bot is a **webhook route inside the existing backend** (`quiz-web-frontend` on Fly, served at `puzzleverseai.com`). No new app, no always-on process, no polling loop (the old `web/telegram-bot/` long-polling server was never deployed and is left untouched). The bot token is the existing Fly secret `RIDDLEVERSE_BOT_TOKEN`.

- Incremental cost: **$0** (existing Fly app, existing Supabase project, Cloudflare in front already).
- The Fly machines auto-stop (`min_machines_running = 0`). A message to a sleeping bot wakes the machine: expect the first reply after idle to take roughly 10 to 30 seconds. Telegram retries a slow webhook; the handler acks first and dedupes by `update_id`, so a retry never double-posts. If that delay is unacceptable, pin one machine: `min_machines_running = 1` in `web/fly.toml`, about **$11 a month** (shared-cpu-2x, 2 GB). Not done in this change.
- Daily reminders need something to call the backend once a day (the machines are asleep, so the in-process cron cannot do it). A Cloudflare Worker cron or any scheduler: free.

## Owner steps to go live (in this order)

1. **Review and merge the PR** (it supersedes #63), then deploy from main as usual: `cd web && flyctl deploy`.
2. **Apply the SQL** in the RiddleVerse Supabase project (SQL editor): `web/backend/supabase/migrations/20261122120000_telegram_bot.sql`. It only adds `rv_telegram_*` tables and a nullable `puzzle_events.channel` column. Check the two existing tables the bot writes to:
   ```sql
   select to_regclass('public.chat_scores'), to_regclass('public.game_sessions');  -- both must be non-null
   ```
   If either is null, run `web/backend/migrations/chat_scores.sql` / `create_game_sessions.sql` first. Until the migration is applied the bot still answers but cannot remember languages or issue link codes, and share/telegram events are stored without their channel (the server retries without the column).
3. **Set two NEW Fly secrets** (adding new ones is fine; do not touch existing ones):
   ```bash
   flyctl secrets set -a quiz-web-frontend \
     TELEGRAM_WEBHOOK_SECRET="$(openssl rand -hex 32)" \
     TELEGRAM_CRON_SECRET="$(openssl rand -hex 32)"
   ```
   Keep both values somewhere you can read them back (Keychain). The bot token is already there as `RIDDLEVERSE_BOT_TOKEN`.
4. **Set the webhook** (this is the moment the bot goes live). Either the script:
   ```bash
   cd web/backend
   RIDDLEVERSE_BOT_TOKEN=... TELEGRAM_WEBHOOK_SECRET=... node scripts/telegramSetup.mjs --apply
   ```
   (also sets the command list, short description and description in all 30 languages), or just the webhook:
   ```bash
   curl -sS "https://api.telegram.org/bot${RIDDLEVERSE_BOT_TOKEN}/setWebhook" \
     -d "url=https://puzzleverseai.com/api/telegram/webhook" \
     -d "secret_token=${TELEGRAM_WEBHOOK_SECRET}" \
     -d 'allowed_updates=["message","callback_query","my_chat_member"]' \
     -d "max_connections=20"
   ```
   Dry run (prints, calls nothing): `node scripts/telegramSetup.mjs`. Rollback: `curl .../deleteWebhook`.
5. **Group scores (optional but recommended).** In BotFather: `/newapp` for @Riddleverse_bot, short name `play`, Web App URL `https://puzzleverseai.com/play`. Then `flyctl secrets set TELEGRAM_MINIAPP_SHORTNAME=play -a quiz-web-frontend`. Without it, groups get a plain "play" link (games work, scores are not recorded) because Telegram only passes a signed user identity to Mini Apps, and `web_app` buttons work only in private chats.
6. **Daily reminders.** Call once a day (for example 14:00 UTC):
   ```bash
   curl -sS -X POST "https://puzzleverseai.com/api/telegram/daily-push" -H "x-cron-secret: ${TELEGRAM_CRON_SECRET}"
   ```
   It sends only to people who sent `/remind`, at most once per user per UTC day, 300 per call (`?limit=` up to 1000), and marks users who blocked the bot. It also prunes old dedupe rows.
7. **Run the live test**: `web/backend/scripts/telegram-live-test.sh` (checks webhook info, 401s, link page, then lists 8 manual checks). Check `getWebhookInfo` for `last_error_message`: if Cloudflare Bot Fight Mode challenges Telegram, add a skip rule for Telegram's ranges (149.154.160.0/20, 91.108.4.0/22).
8. **Android release.** The app changes (App Links for `/play` and `/app/link`, the `riddleverse://auth?code=` handler, banner, cards, share links) need a new versionCode. App Links verification needs a device check after install: `adb shell pm get-app-links com.kreativekoala.riddleverse` must say `puzzleverseai.com: verified`, and `https://puzzleverseai.com/.well-known/assetlinks.json` must list the **Play app signing** certificate SHA-256 (the file lists `57:AE:00...`; the upload key in docs/DEPLOYED.md is a different one, so confirm in Play Console > App integrity which certificate the Play-signed build carries).

## How the pieces work

**Play-in-Chat.** `/daily` and `/play` send an inline button. In a private chat it is a Mini App (`web_app`) button to `https://puzzleverseai.com/play/<game>?src=telegram&tgctx=<signed context>`. When the player solves the puzzle, the page sends `{game, difficulty, time}` plus Telegram's signed `initData` to `POST /api/telegram/score`. The server verifies the HMAC (bot token), computes the score itself (100/200/300 by difficulty plus up to 100 for speed, max 400), writes `chat_scores` (platform `telegram`, one best score per player per chat per game) and `game_sessions` (`source = 'telegram'`), and posts "<name> scored N" back into the chat with the top 5 and a "Beat that score" button. The daily puzzle is the same game as the Android daily (port of `DailyPuzzleSchedule`, pinned to its golden vectors in `test/telegramDaily.test.js`), with its own leaderboard per day.

**Webhook safety.** Fails closed (503) when `TELEGRAM_WEBHOOK_SECRET` is unset, 401 on a wrong `X-Telegram-Bot-Api-Secret-Token` (constant-time compare), acks 200 before doing any work, dedupes `update_id` (table `rv_telegram_updates`), replies in HTML parse mode with every dynamic value escaped, 20 commands per minute per user, `/link` 5 per hour, per-IP limits on score and redeem.

**Language.** From Telegram's `language_code`, English fallback, `/language` override saved per user. 30 languages (English plus the app's 29). Machine translations, marked `"review": "machine"` in every file; the words for the 23 games reuse the app's own translations. Each language also gets its command list and descriptions through `setMyCommands` / `setMyDescription`.

**Login / account link (magic link, done properly).** `/link` (private chat only) creates a 192-bit one-time code, stores only its SHA-256, valid 10 minutes, and sends `https://puzzleverseai.com/app/link?code=...`. On Android that opens the app (verified App Link) or a small page that fires `intent://` with a Google Play fallback. The app asks "Connect Telegram?" and posts the code to `POST /api/telegram/link/redeem`:
- already signed in (guests included): the Telegram identity is attached to that account, nothing else changes;
- signed out: the server returns a Firebase **custom token** for the account bound to that Telegram user (`tg_<id>` the first time) and the app signs in.
The code is consumed by one atomic conditional update (replay and parallel requests lose), an invalid ID token fails before the code is spent, conflicts are 409. Tests: expiry at 10 minutes, replay, races, forged tokens, conflicts (`test/telegramHandlers.test.js`).

**Entitlements.** Logging in this way grants **nothing**. The old client-side "magic link session" flag that made the app treat bot-login users as premium and ad-free is removed: premium and no-ads come only from the store purchase (RevenueCat), never from how someone logged in. See the PR for the product question this leaves open.

**Sharing.** Every share (daily result, finished puzzle, custom game, Telegram button) carries `https://puzzleverseai.com/play/<puzzle type or game id>?src=share&utm_source=<telegram|whatsapp|other>&utm_medium=...`. A friend without the app plays on the web page; with the app installed the verified App Link opens the app on that puzzle (`DeepLinkRouter`). Events: `share_sent` (app, with channel), `deeplink_open` (app, with the link's `src`), `share_open` (server, when a `/play` page is opened with `src=share`), `telegram_cta` (app, which promo was tapped). The chooser does not tell us which app was picked, so a generic share is channel `other`; only the explicit Telegram button is `telegram`.

**Why `/play` showed 0 events.** The deployed bundle does contain the telemetry and the backend (release v54, Nov 11) accepts the event types, so it is not a deploy gap. Nothing in the code path is broken: a 200 from the ingest needs a real browser to send it, and `/play` has had no organic visitors. To tell "no visitors" from "telemetry broken", the server now counts page opens itself (`web_play_hit`, and `share_open` for shared links): no JavaScript needed, Global Privacy Control and Do Not Track request headers honoured, crawlers and link-preview bots skipped, no IP / user agent / referrer stored. After deploy: `select event_type, puzzle_type, count(*) from puzzle_events where event_type in ('web_play_hit','share_open') group by 1,2;`. Hits without `web_play_view` rows would point at the client; no hits would mean no traffic.

## Forge hardening (small)

- Worker (`game-worker/server.js`): logs the Claude CLI's `result` figures (cost, input / output / cache tokens, duration, turns) per generation and keeps per-UTC-day totals at `GET /usage` (needs `x-worker-secret`). Takes effect when the worker is redeployed on the Hetzner box (`pm2 restart vibecoder-worker`); the CLI broker account may not report a real dollar cost, so treat `costUsd` as indicative.
- Backend: one alert when 3 forge sessions fail in a row (`forge.consecutive_failures`, once per streak, re-armed by a success), alongside the existing 3 hour dead-man and 24 hour rate rules.
- Transport: the backend calls the worker on a Hetzner IP over plain `http://`. HTTPS needs a hostname and certificate on that box (for example a Cloudflare tunnel or Caddy), which is infrastructure this change does not create. The backend now logs a warning at boot while `GAME_WORKER_URL` is plain http. Once the worker has an https URL: `flyctl secrets set GAME_WORKER_URL=https://... -a quiz-web-frontend`.
- Found, not changed: both the backend (`GAME_WORKER_SECRET`) and the worker (`WORKER_SECRET`) fall back to the string `game-worker-secret-2024`, which is public in this repo. If either side runs on that default, rotate it (set a new random value on both and restart).

## Not verified here (needs a device or the live bot)

Real Telegram delivery, the Mini App opening inside Telegram, Telegram's `initData` HMAC against a real client (the algorithm follows Telegram's documentation and is tested against a reference implementation in this repo), App Links verification on a phone, the deep link `riddleverse://game` and `/open-riddle` on a device, the Android UI (compiles, strings and guards pass; not looked at on a screen), and cold-start latency of the Fly machine.

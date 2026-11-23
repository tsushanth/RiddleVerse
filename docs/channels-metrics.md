# Channel metrics: how people use RiddleVerse outside the app

Covers the Telegram bot, account linking, sharing and deep links, and the public web `/play` pages. The app's own events
(`docs/metrics.md`) are a different table on purpose.

## Where the data lives

| What | Table | Written by |
|---|---|---|
| Bot interactions (this document) | `public.rv_channel_events` (new, `rv_` prefix, service role only) | the bot, `web/backend/services/telegram/channelEvents.js` |
| Bot users, language, linked account, reminders, blocked | `public.rv_telegram_users` | the bot |
| Link codes: issued time, redeemed time | `public.rv_telegram_link_codes` | the bot (a used code stays; an unused one is deleted when a new one is issued) |
| Finished bot games | `chat_scores` (platform `telegram`), `game_sessions` (source `telegram`) | `POST /api/telegram/score` |
| Share, deep link, Telegram promo (app) and page-open (server) events | `public.puzzle_events` (`channel` column) | Android, and `services/pageHits.js` |
| Web `/play` events, "Play in Telegram" clicks | `public.puzzle_events` (`web_play_*`, platform `web`) | the web pages, `services/pageHits.js` |
| AI game generations | `public.forge_sessions` | the forge routes |

Why bot events are not in `puzzle_events`: the app's DAU, funnel and retention queries read that table, so bot rows would be counted as
app activity. Check any time that the two stay apart (should return 0): `select count(*) from public.puzzle_events where event_type in ('daily_open','game_finished','link_issued','bot_blocked');`

Privacy: no message text, names, usernames or raw Telegram ids are stored. `chat_ref` / `tg_user_ref` are HMAC-SHA256 hashes (24 hex
characters) keyed by `RV_CHANNEL_REF_KEY` (falls back to a key derived from the bot token; set `RV_CHANNEL_REF_KEY` before launch if you
may ever rotate the token, otherwise every ref changes and retention restarts). They are pseudonymous, not anonymous: service role only,
purge with `select public.rv_purge_channel_events(180);` (not scheduled). In a private chat `chat_ref = tg_user_ref`; in a group `chat_ref`
is the group and `tg_user_ref` the person. "A user" below is `coalesce(tg_user_ref, chat_ref)`.

## Events in `rv_channel_events` (channel = 'telegram')

| event | When | props |
|---|---|---|
| `start` | `/start` (any payload) | `src`, `action` (menu, game, daily, link), `game`, `new_user` (true only for a first contact), `chat_type` |
| `daily_open` | the daily puzzle card was shown | `game`, `via` (command, button, start) |
| `game_open` | a game card was shown | `game`, `via` (command, button, start) |
| `game_menu` | the game picker was shown | `via` |
| `leaderboard_view` | a leaderboard was shown | `game` (slug or `daily`) |
| `language_change` | `/language` choice | `from`, `to` (the event's `language` is the new one) |
| `remind_on`, `remind_off` | `/remind` toggled | |
| `link_issued` | `/link` made a code | |
| `link_redeemed` | the app redeemed it | `mode` (`signin` or `linked`); no chat |
| `link_redeem_failed` | redeem refused | `error`, `status`; no refs |
| `app_cmd`, `create_cmd` | `/app`, `/create` (install pointers) | |
| `text_message` | plain text to the bot in a private chat (text not stored) | |
| `unknown_command` | an unrecognised command | |
| `game_finished` | a verified Mini App score | `game`, `kind` (d daily, p play), `difficulty`, `score`, `improved`, `duration_s`, `chat_type` |
| `bot_blocked`, `bot_unblocked` | `my_chat_member`, or a send that failed because the user left | `via` |
| `group_added`, `group_removed` | the bot joined or left a group | `status` |
| `daily_push` | one per reminder run, no refs | `candidates`, `sent`, `gone`, `failed` |
| `rate_limited`, `error` | limits hit, update failures | `where`, `code` |

Every event also has `language` (the bot language of that person) and, in `props`, `chat_type` (private, group, supergroup).

Failure behaviour: emitting is background and silent. If the table is missing, events pause for 10 minutes (other errors: 30 seconds)
and one warning is logged; a bot reply is never delayed or failed by it. It also drops events beyond 1200 a minute or 100 in flight.

## Attributing the bot start (promotion by surface)

The bot records the `/start` payload as `props.src`. Grammar (`parseStartPayload` in `bot.js`): `<action>-<src>` or just `<src>`; action is
`g_<game>`, `daily` or `link`; `src` is `[a-z0-9_]{1,16}` (anything else is stored as `other`, no payload as `none`).

| Sender | Payload | `src` |
|---|---|---|
| `/play/<game>` Play in Telegram button | `g_<game>-web_game` | `web_game` |
| same button on a page opened from a shared link (`src=share`) | `g_<game>-web_share` | `web_share` |
| `/play` index button | `web_index` | `web_index` |
| Welcome page button | `web_home` | `web_home` |
| Android (to do in the Android release, nothing changed here) | e.g. `g_sudoku-app_result`, `daily-app_card`, `app_banner`, `link-app_settings` | `app_*` |
| older links (`g_<game>`, `daily`, `link`) | | `none` |

Android's `ShareLinks.botUrl` already accepts `[A-Za-z0-9_-]{1,64}`, so the app only needs to append `-<surface>` (the same surface
strings it already sends in `telegram_cta`). Until then app-originated starts show `src = none`; `telegram_cta.channel` in `puzzle_events`
counts the app taps.

## What cannot be seen (be honest about the funnel)

- A tap on a URL or Mini App button (Play now, Install, Challenge) is not sent to the bot. "Play tap" is therefore measured from both ends:
  the card was shown (`daily_open`, `game_open`) and the game was finished (`game_finished`). In between, Mini App page opens are counted
  anonymously by the server (`web_play_hit`, channel `telegram`); they cannot be tied to a person.
- Installs. Play Console shows installs per `utm_campaign` (`menu`, `app_cmd`, `create`, `link`); this database does not.
- Shares done through the Android chooser report channel `other`; only the explicit Telegram button reports `telegram`.
- Everyone who blocks the bot or never taps anything is invisible after that; do not read silence as retention.

## Queries

Run in the RiddleVerse Supabase SQL editor (project `uujjodxicvifmiwlimob`) after migration `20261123120000_rv_channel_events.sql`.
Change `7` in `interval '7 days'` for another window. All of these are what `GET /api/telegram/stats` computes (via `rv_channel_stats`).

```sql
-- 1. Overview and events by name.
select count(*) as events,
       count(distinct coalesce(tg_user_ref, chat_ref)) filter (where event in ('start','help','daily_open','game_open','game_menu','leaderboard_view','language_change','remind_on','remind_off','link_issued','app_cmd','create_cmd','text_message','unknown_command','game_finished')) as active_users,
       count(distinct coalesce(tg_user_ref, chat_ref)) filter (where props->>'new_user' = 'true') as new_users,
       count(distinct chat_ref) filter (where props->>'chat_type' in ('group','supergroup')) as group_chats_active
from public.rv_channel_events where channel = 'telegram' and ts >= now() - interval '7 days';

select event, count(*) as events, count(distinct coalesce(tg_user_ref, chat_ref)) as users
from public.rv_channel_events where channel = 'telegram' and ts >= now() - interval '7 days'
group by event order by events desc;
```

```sql
-- 2. Bot funnel: start -> daily open -> play card shown -> finished game -> link issued -> linked.
-- People whose first /start falls in the window; a later step counts them when it happened at or after that start.
-- Not strictly sequential (someone can finish a game without opening the daily).
with ev as materialized (
  select ts, event, props, coalesce(tg_user_ref, chat_ref) as user_ref
  from public.rv_channel_events where channel = 'telegram' and ts >= now() - interval '7 days'),
starters as (
  select distinct on (user_ref) user_ref, ts as t0 from ev where event = 'start' and user_ref is not null order by user_ref, ts),
pu as (
  select s.user_ref,
         coalesce(bool_or(e.event = 'daily_open'), false) as saw_daily,
         coalesce(bool_or(e.event in ('daily_open','game_open')), false) as saw_play_card,
         coalesce(bool_or(e.event = 'game_finished'), false) as finished_game,
         coalesce(bool_or(e.event = 'link_issued'), false) as link_issued,
         coalesce(bool_or(e.event = 'link_redeemed'), false) as linked
  from starters s left join ev e on e.user_ref = s.user_ref and e.ts >= s.t0 group by s.user_ref)
select count(*) as started,
       count(*) filter (where saw_daily) as saw_daily,
       count(*) filter (where saw_play_card) as saw_play_card,
       count(*) filter (where finished_game) as finished_game,
       count(*) filter (where link_issued) as link_issued,
       count(*) filter (where linked) as linked
from pu;
```

```sql
-- 3. Where bot users come from (first touch in the window, by /start src), and what they did afterwards.
with ev as materialized (
  select ts, event, props, coalesce(tg_user_ref, chat_ref) as user_ref
  from public.rv_channel_events where channel = 'telegram' and ts >= now() - interval '7 days'),
starters as (
  select distinct on (user_ref) user_ref, ts as t0, coalesce(props->>'src', 'none') as src, coalesce(props->>'new_user' = 'true', false) as new_user
  from ev where event = 'start' and user_ref is not null order by user_ref, ts),
pu as (
  select s.user_ref, s.src, s.new_user,
         coalesce(bool_or(e.event in ('daily_open','game_open')), false) as saw_play_card,
         coalesce(bool_or(e.event = 'game_finished'), false) as finished_game,
         coalesce(bool_or(e.event = 'link_redeemed'), false) as linked
  from starters s left join ev e on e.user_ref = s.user_ref and e.ts >= s.t0 group by s.user_ref, s.src, s.new_user)
select src, count(*) as users, count(*) filter (where new_user) as new_users,
       count(*) filter (where saw_play_card) as saw_play_card, count(*) filter (where finished_game) as finished_game, count(*) filter (where linked) as linked
from pu group by src order by users desc;
```

```sql
-- 4. D1 / D7 return. Cohort = UTC day of a person's first action; D1 = an action on exactly the next UTC day, D7 on day 7 (same
-- definition as the app's retention_by_cohort). Rate is null until the cohort is old enough. Block events do not count as a return.
with fs as (
  select coalesce(tg_user_ref, chat_ref) as user_ref, (min(ts) at time zone 'UTC')::date as cohort
  from public.rv_channel_events
  where channel = 'telegram' and coalesce(tg_user_ref, chat_ref) is not null
    and event in ('start','help','daily_open','game_open','game_menu','leaderboard_view','language_change','remind_on','remind_off','link_issued','app_cmd','create_cmd','text_message','unknown_command','game_finished')
  group by 1 having min(ts) >= now() - interval '30 days'),
act as (
  select distinct coalesce(tg_user_ref, chat_ref) as user_ref, (ts at time zone 'UTC')::date as day
  from public.rv_channel_events
  where channel = 'telegram' and ts >= now() - interval '30 days'
    and event in ('start','help','daily_open','game_open','game_menu','leaderboard_view','language_change','remind_on','remind_off','link_issued','app_cmd','create_cmd','text_message','unknown_command','game_finished')),
c as (
  select fs.cohort, fs.user_ref,
         exists (select 1 from act a where a.user_ref = fs.user_ref and a.day = fs.cohort + 1) as d1,
         exists (select 1 from act a where a.user_ref = fs.user_ref and a.day = fs.cohort + 7) as d7
  from fs)
select cohort, count(*) as users, count(*) filter (where d1) as d1_returned, count(*) filter (where d7) as d7_returned,
       case when cohort + 1 < (now() at time zone 'UTC')::date then round(100.0 * count(*) filter (where d1) / count(*), 1) end as d1_pct,
       case when cohort + 7 < (now() at time zone 'UTC')::date then round(100.0 * count(*) filter (where d7) / count(*), 1) end as d7_pct
from c group by cohort order by cohort desc;
```

```sql
-- 5. Per language: people, events, starts, finished games, linked accounts, reminder opt-ins. ("unknown" = events without a person, e.g. reminder runs.)
select coalesce(language, 'unknown') as language,
       count(distinct coalesce(tg_user_ref, chat_ref)) filter (where event in ('start','help','daily_open','game_open','game_menu','leaderboard_view','language_change','remind_on','remind_off','link_issued','app_cmd','create_cmd','text_message','unknown_command','game_finished')) as users,
       count(*) as events,
       count(*) filter (where event = 'start') as starts,
       count(*) filter (where event = 'game_finished') as games_finished,
       count(distinct coalesce(tg_user_ref, chat_ref)) filter (where event = 'link_redeemed') as linked,
       count(*) filter (where event = 'remind_on') as reminders_on
from public.rv_channel_events where channel = 'telegram' and ts >= now() - interval '7 days'
group by 1 order by users desc, events desc;

-- Bot audience by language right now (all time), from the user table.
select language, count(*) as users, count(*) filter (where firebase_uid is not null) as linked, count(*) filter (where remind_daily and not blocked) as reminders, count(*) filter (where blocked) as blocked
from public.rv_telegram_users group by 1 order by users desc;
```

```sql
-- 6. Games: cards shown vs finished rounds (daily cards carry the daily game).
select props->>'game' as game,
       count(*) filter (where event in ('daily_open','game_open')) as cards,
       count(*) filter (where event = 'game_finished') as finished,
       count(distinct coalesce(tg_user_ref, chat_ref)) filter (where event = 'game_finished') as finishers
from public.rv_channel_events
where channel = 'telegram' and ts >= now() - interval '7 days' and event in ('daily_open','game_open','game_finished') and props->>'game' is not null
group by 1 order by finished desc, cards desc;
```

```sql
-- 7. Account linking: issued vs redeemed, how it was redeemed, why it failed, how long it took.
-- Issued comes from events (unused codes are deleted when a new one is issued); redeemed from events and from the codes table.
select (select count(*) from public.rv_channel_events where channel = 'telegram' and event = 'link_issued' and ts >= now() - interval '7 days') as issued,
       (select count(*) from public.rv_channel_events where channel = 'telegram' and event = 'link_redeemed' and ts >= now() - interval '7 days') as redeemed_events,
       (select count(*) from public.rv_telegram_link_codes where used_at >= now() - interval '7 days') as codes_used,
       (select round((percentile_cont(0.5) within group (order by extract(epoch from (used_at - created_at))))::numeric, 1)
          from public.rv_telegram_link_codes where used_at >= now() - interval '7 days') as median_seconds_issue_to_redeem,
       (select count(*) from public.rv_telegram_users where linked_at is not null) as linked_total;

select props->>'mode' as mode, count(*) from public.rv_channel_events
where channel = 'telegram' and event = 'link_redeemed' and ts >= now() - interval '7 days' group by 1;           -- signin = new device, linked = already signed in
select props->>'error' as error, count(*) from public.rv_channel_events
where channel = 'telegram' and event = 'link_redeem_failed' and ts >= now() - interval '7 days' group by 1 order by 2 desc;  -- expired_or_used, invalid_code, ...
```

```sql
-- 8. Groups, blocks, reminders, errors.
select count(*) filter (where event = 'group_added') as groups_added,
       count(*) filter (where event = 'group_removed') as groups_removed,
       count(*) filter (where event = 'game_finished' and props->>'chat_type' <> 'private') as games_finished_in_groups,
       count(*) filter (where event = 'bot_blocked') as blocked,
       count(*) filter (where event = 'bot_unblocked') as unblocked,
       count(*) filter (where event = 'daily_push') as reminder_runs,
       coalesce(sum((props->>'sent')::int) filter (where event = 'daily_push'), 0) as reminders_sent
from public.rv_channel_events where channel = 'telegram' and ts >= now() - interval '7 days';

select event, coalesce(props->>'where', '') as location, count(*) as n
from public.rv_channel_events where channel = 'telegram' and ts >= now() - interval '7 days' and event in ('error','rate_limited')
group by 1, 2 order by n desc;
```

```sql
-- 9. Share funnel (puzzle_events). share_sent.channel = what the app chose (telegram, whatsapp, other); share_open.channel = the link's
-- utm_source (written by the server when a /play page opens with src=share); deeplink_open.channel = the link's src (app opened from a link).
-- Compare by channel, not as a per-person funnel: nothing ties one share to one open.
select coalesce(channel, 'unknown') as channel,
       count(*) filter (where event_type = 'share_sent') as share_sent,
       count(*) filter (where event_type = 'share_open') as share_open,
       count(*) filter (where event_type = 'deeplink_open') as deeplink_open,
       count(*) filter (where event_type = 'telegram_cta') as telegram_cta
from public.puzzle_events
where event_type in ('share_sent','share_open','deeplink_open','telegram_cta') and ts >= now() - interval '7 days'
group by 1 order by share_sent desc, share_open desc;

-- What gets shared (app), by puzzle type.
select puzzle_type, channel, count(*) as shares, count(distinct install_id) as installs
from public.puzzle_events where event_type = 'share_sent' and ts >= now() - interval '7 days' group by 1, 2 order by shares desc;
-- Shared-link installs: Play Console, acquisition by utm_source / utm_campaign (the page buttons tag them); not visible here.
```

```sql
-- 10. Web /play by source. web_play_hit = a page opened (server-counted, GPC/DNT and crawlers excluded, so a lower bound);
-- share_open = opened through a shared link; channel is utm_source, else src (telegram = the Mini App or a group link). Null = direct.
select coalesce(channel, 'direct') as source,
       count(*) filter (where event_type = 'web_play_hit') as hits,
       count(*) filter (where event_type = 'share_open') as share_opens
from public.puzzle_events where event_type in ('web_play_hit','share_open') and ts >= now() - interval '7 days'
group by 1 order by count(*) desc;

select puzzle_type as page, count(*) as hits from public.puzzle_events
where event_type in ('web_play_hit','share_open') and ts >= now() - interval '7 days' group by 1 order by count(*) desc limit 30;

-- "Play in Telegram" clicks (client event, silent under GPC/DNT): page = game slug, index (the /play index) or welcome; surface = the /start src.
select puzzle_type as page, coalesce(channel, 'unknown') as surface, count(*) as clicks
from public.puzzle_events where event_type = 'web_play_telegram' and ts >= now() - interval '7 days' group by 1, 2 order by clicks desc;

-- Play in Telegram click -> bot start from that surface (aggregate, same window). Clicks undercount (GPC/DNT), starts include people who typed the link.
select s.src, s.starts, coalesce(c.clicks, 0) as clicks
from (select props->>'src' as src, count(*) as starts from public.rv_channel_events
      where channel = 'telegram' and event = 'start' and ts >= now() - interval '7 days' and props->>'src' like 'web\_%' group by 1) s
left join (select channel as src, count(*) as clicks from public.puzzle_events
           where event_type = 'web_play_telegram' and ts >= now() - interval '7 days' group by 1) c on c.src = s.src
order by s.starts desc;
```

```sql
-- 11. Forge (AI game generation) per user. Distribution first; the second query names the heaviest users (service role only).
with g as (select user_id, count(*) as n, count(*) filter (where status = 'completed') as ok
           from public.forge_sessions where created_at >= now() - interval '7 days' group by user_id)
select coalesce(sum(n), 0) as sessions, coalesce(sum(ok), 0) as completed,
       count(*) filter (where user_id is not null) as users,
       round(avg(n) filter (where user_id is not null), 2) as avg_per_user,
       max(n) filter (where user_id is not null) as max_per_user,
       count(*) filter (where user_id is not null and n = 1) as users_with_1,
       count(*) filter (where user_id is not null and n = 2) as users_with_2,
       count(*) filter (where user_id is not null and n between 3 and 5) as users_with_3_to_5,
       count(*) filter (where user_id is not null and n >= 6) as users_with_6_plus
from g;

select left(user_id, 8) as user_prefix, count(*) as generations, count(*) filter (where status = 'completed') as completed, count(*) filter (where status = 'failed') as failed
from public.forge_sessions where created_at >= now() - interval '7 days' and user_id is not null
group by user_id order by generations desc limit 20;
```

## The same numbers as JSON

`GET https://puzzleverseai.com/api/telegram/stats?days=7` (1 to 90, default 7). Header `x-cron-secret: <TELEGRAM_CRON_SECRET>`, the same
secret as the daily push (503 when it is not set, 401 when wrong; the worker secret of the forge is NOT accepted because its default is public
in this repo). Never put the secret in a URL.

```bash
curl -sS "https://puzzleverseai.com/api/telegram/stats?days=7" -H "x-cron-secret: ${TELEGRAM_CRON_SECRET}" | jq .
```

Keys map to the queries above: `overview` and `events_by_name` (1), `bot_funnel` (2), `start_sources` (3), `retention` (4), `by_language` (5),
`by_game` (6), `linking` (7), `audience` (users snapshot, blocks, groups, reminders: 5 and 8), `errors` (8), `by_day`, `share_funnel` (9),
`web_play` (10), `forge` (11; counts only, no user ids). A section that fails shows `{"error": ...}` without hiding the others. 503
`channel_events_not_migrated` means the migration is not applied.

## Deploy order

1. Apply `web/backend/supabase/migrations/20261123120000_rv_channel_events.sql` (idempotent; rollback file next to it). Optional: set the Fly secret `RV_CHANNEL_REF_KEY` to a new random value (adding it is fine; do not change it later or all refs change).
2. Deploy the backend. Order does not matter for safety (a missing table only pauses the writer), but events before the migration are lost.
3. Deploy the web frontend (the button click event and the `/start` labels). The ingest accepts `web_play_telegram` only after step 2.
4. Android 186 (separately): append `-<surface>` to the bot link payload.

Caveats: small numbers are noisy; the funnel is per person only where a ref exists (Mini App scores are verified, so `game_finished` always has one);
`first_event_at` in the stats is the first event in the window, not the launch; a person who uses the bot only in groups appears without a `start`.

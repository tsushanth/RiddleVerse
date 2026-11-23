-- Usage events for the NON-APP channels (today: the Telegram bot) and the numbers built on them (ADDITIVE ONLY, idempotent).
-- NOT applied by anyone yet. Apply BEFORE deploying the backend that writes these events: until then the bot works exactly as
-- before, the event writer backs off quietly for 10 minutes at a time, and GET /api/telegram/stats answers 503
-- "channel_events_not_migrated". Rollback: 20261123120000_rv_channel_events.rollback.sql
--
-- Why a separate table: public.puzzle_events is the APP analytics table. Its DAU / funnel / retention queries read every row of
-- the event types they name, so bot interactions are kept out of it. This migration only creates rv_ objects; the Supabase
-- project is shared with other apps and nothing else is touched. It READS puzzle_events, forge_sessions, rv_telegram_users and
-- rv_telegram_link_codes (inside rv_channel_stats) and writes only rv_channel_events.
--
-- Row model: one row per interaction. No message text, no names, no usernames, no raw Telegram ids:
--   chat_ref     keyed hash (HMAC-SHA256, 24 hex chars) of the Telegram chat id (a group, or the private chat = the user)
--   tg_user_ref  keyed hash of the Telegram user id (same key); equal to chat_ref in a private chat
--   props        small json object of labels/counters (src, game, mode, ...), written through a whitelist in the backend
-- The refs are pseudonymous, not anonymous: treat them as personal data (service role only, purge old rows).

create table if not exists public.rv_channel_events (
  id           uuid primary key default gen_random_uuid(),
  ts           timestamptz not null default now(),
  channel      text not null,
  event        text not null,
  language     text,
  chat_ref     text,
  tg_user_ref  text,
  props        jsonb not null default '{}'::jsonb,
  constraint rv_channel_events_channel_chk check (channel ~ '^[a-z0-9_]{1,24}$'),
  constraint rv_channel_events_event_chk   check (event ~ '^[a-z0-9_]{1,40}$'),
  constraint rv_channel_events_props_object check (jsonb_typeof(props) = 'object')
);

create index if not exists rv_channel_events_channel_event_ts_idx on public.rv_channel_events (channel, event, ts);
create index if not exists rv_channel_events_chat_ref_ts_idx      on public.rv_channel_events (chat_ref, ts);
create index if not exists rv_channel_events_user_ref_ts_idx      on public.rv_channel_events (tg_user_ref, ts) where tg_user_ref is not null;

-- Service role only: RLS on, no policies.
alter table public.rv_channel_events enable row level security;
revoke all on table public.rv_channel_events from public, anon, authenticated;
grant select, insert, delete on table public.rv_channel_events to service_role;

-- Retention helper. NOT scheduled. Run manually, e.g.: select public.rv_purge_channel_events(180);
create or replace function public.rv_purge_channel_events(p_keep_days int default 180)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare n bigint;
begin
  if p_keep_days is null or p_keep_days < 30 then
    raise exception 'p_keep_days must be >= 30';
  end if;
  delete from public.rv_channel_events where ts < now() - make_interval(days => p_keep_days);
  get diagnostics n = row_count;
  return n;
end;
$$;
revoke all on function public.rv_purge_channel_events(int) from public, anon, authenticated;
grant execute on function public.rv_purge_channel_events(int) to service_role;

-- All the numbers of docs/channels-metrics.md in one document (GET /api/telegram/stats?days=N returns it). Every section is
-- computed on its own: a section that fails (for example a table that does not exist in a copy of the database) reports
-- {"error": "..."} instead of breaking the rest. `active` below is the list of events that mean "a person did something" and
-- must stay equal to USER_ACTION_EVENTS in web/backend/services/telegram/channelEvents.js.
create or replace function public.rv_channel_stats(p_days int default 7)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $fn$
declare
  d      int         := greatest(1, least(coalesce(p_days, 7), 90));
  since  timestamptz := now() - make_interval(days => greatest(1, least(coalesce(p_days, 7), 90)));
  today  date        := (now() at time zone 'UTC')::date;
  active text[]      := array['start','help','daily_open','game_open','game_menu','leaderboard_view','language_change','remind_on','remind_off',
                              'link_issued','app_cmd','create_cmd','text_message','unknown_command','game_finished'];
  r      jsonb       := jsonb_build_object('window_days', greatest(1, least(coalesce(p_days, 7), 90)));
  sec    jsonb;
begin
  -- overview
  begin
    select jsonb_build_object(
      'events', count(*),
      'active_users', count(distinct coalesce(tg_user_ref, chat_ref)) filter (where event = any(active)),
      'new_users', count(distinct coalesce(tg_user_ref, chat_ref)) filter (where props->>'new_user' = 'true'),
      'group_chats_active', count(distinct chat_ref) filter (where props->>'chat_type' in ('group', 'supergroup')),
      'first_event_at', min(ts), 'last_event_at', max(ts))
    into sec from public.rv_channel_events where channel = 'telegram' and ts >= since;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('overview', sec);

  -- events by name
  begin
    select coalesce(jsonb_agg(to_jsonb(t) order by t.events desc), '[]'::jsonb) into sec from (
      select event, count(*) as events, count(distinct coalesce(tg_user_ref, chat_ref)) as users
      from public.rv_channel_events where channel = 'telegram' and ts >= since group by event) t;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('events_by_name', sec);

  -- bot funnel (people whose FIRST start in the window is counted; each later step counts them if it happened at or after that start)
  -- and per-source attribution (first touch). saw_play_card = the daily card or a game card with the Play button was shown.
  begin
    with ev as materialized (
      select ts, event, props, coalesce(tg_user_ref, chat_ref) as user_ref
      from public.rv_channel_events where channel = 'telegram' and ts >= since),
    starters as (
      select distinct on (user_ref) user_ref, ts as t0, coalesce(props->>'src', 'none') as src, coalesce(props->>'new_user' = 'true', false) as new_user
      from ev where event = 'start' and user_ref is not null order by user_ref, ts),
    pu as materialized (
      select s.user_ref, s.src, s.new_user,
             coalesce(bool_or(e.event = 'daily_open'), false)                  as saw_daily,
             coalesce(bool_or(e.event in ('daily_open', 'game_open')), false)  as saw_play_card,
             coalesce(bool_or(e.event = 'game_finished'), false)               as finished_game,
             coalesce(bool_or(e.event = 'link_issued'), false)                 as link_issued,
             coalesce(bool_or(e.event = 'link_redeemed'), false)               as linked
      from starters s left join ev e on e.user_ref = s.user_ref and e.ts >= s.t0
      group by s.user_ref, s.src, s.new_user)
    select jsonb_build_object(
      'funnel', (select jsonb_build_object('started', count(*), 'saw_daily', count(*) filter (where saw_daily),
                   'saw_play_card', count(*) filter (where saw_play_card), 'finished_game', count(*) filter (where finished_game),
                   'link_issued', count(*) filter (where link_issued), 'linked', count(*) filter (where linked)) from pu),
      'by_source', (select coalesce(jsonb_agg(to_jsonb(t) order by t.users desc), '[]'::jsonb) from (
                   select src, count(*) as users, count(*) filter (where new_user) as new_users,
                          count(*) filter (where saw_play_card) as saw_play_card, count(*) filter (where finished_game) as finished_game,
                          count(*) filter (where linked) as linked from pu group by src) t))
    into sec;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('bot_funnel', sec->'funnel', 'start_sources', sec->'by_source');
  if sec ? 'error' then r := r || jsonb_build_object('bot_funnel', sec, 'start_sources', sec); end if;

  -- D1 / D7 return by first-seen UTC day (exact day N, like the app's retention view). Rates are null until the cohort is old enough.
  begin
    with fs as (
      select coalesce(tg_user_ref, chat_ref) as user_ref, (min(ts) at time zone 'UTC')::date as cohort
      from public.rv_channel_events
      where channel = 'telegram' and event = any(active) and coalesce(tg_user_ref, chat_ref) is not null
      group by 1 having min(ts) >= since),
    act as (
      select distinct coalesce(tg_user_ref, chat_ref) as user_ref, (ts at time zone 'UTC')::date as day
      from public.rv_channel_events
      where channel = 'telegram' and event = any(active) and ts >= since),
    c as (
      select fs.cohort, fs.user_ref,
             exists (select 1 from act a where a.user_ref = fs.user_ref and a.day = fs.cohort + 1) as d1,
             exists (select 1 from act a where a.user_ref = fs.user_ref and a.day = fs.cohort + 7) as d7
      from fs),
    per as (
      select cohort, count(*) as users, count(*) filter (where d1) as d1_returned, count(*) filter (where d7) as d7_returned,
             case when cohort + 1 < today then round(100.0 * count(*) filter (where d1) / count(*), 1) end as d1_pct,
             case when cohort + 7 < today then round(100.0 * count(*) filter (where d7) / count(*), 1) end as d7_pct
      from c group by cohort)
    select jsonb_build_object(
      'cohorts', coalesce((select jsonb_agg(to_jsonb(p) order by p.cohort desc) from per p), '[]'::jsonb),
      'd1_pct', (select round(100.0 * sum(d1_returned) / nullif(sum(users), 0), 1) from per where cohort + 1 < today),
      'd7_pct', (select round(100.0 * sum(d7_returned) / nullif(sum(users), 0), 1) from per where cohort + 7 < today))
    into sec;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('retention', sec);

  -- per language
  begin
    select coalesce(jsonb_agg(to_jsonb(t) order by t.users desc, t.events desc), '[]'::jsonb) into sec from (
      select coalesce(language, 'unknown') as language,
             count(distinct coalesce(tg_user_ref, chat_ref)) filter (where event = any(active)) as users,
             count(*) as events,
             count(*) filter (where event = 'start') as starts,
             count(*) filter (where event = 'game_finished') as games_finished,
             count(distinct coalesce(tg_user_ref, chat_ref)) filter (where event = 'link_redeemed') as linked,
             count(*) filter (where event = 'remind_on') as reminders_on
      from public.rv_channel_events where channel = 'telegram' and ts >= since group by 1) t;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('by_language', sec);

  -- games
  begin
    select coalesce(jsonb_agg(to_jsonb(t) order by t.finished desc, t.cards desc), '[]'::jsonb) into sec from (
      select props->>'game' as game,
             count(*) filter (where event in ('daily_open', 'game_open')) as cards,
             count(*) filter (where event = 'game_finished') as finished,
             count(distinct coalesce(tg_user_ref, chat_ref)) filter (where event = 'game_finished') as finishers
      from public.rv_channel_events
      where channel = 'telegram' and ts >= since and event in ('daily_open', 'game_open', 'game_finished') and props->>'game' is not null
      group by 1) t;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('by_game', sec);

  -- account linking: events plus the authoritative rv_telegram_link_codes / rv_telegram_users tables.
  -- link_codes_used / median: unused codes are deleted when a new one is issued, so "issued" comes from events, "redeemed" from both.
  begin
    select jsonb_build_object(
      'issued_events', (select count(*) from public.rv_channel_events where channel = 'telegram' and event = 'link_issued' and ts >= since),
      'issued_users', (select count(distinct coalesce(tg_user_ref, chat_ref)) from public.rv_channel_events where channel = 'telegram' and event = 'link_issued' and ts >= since),
      'redeemed_events', (select coalesce(jsonb_object_agg(mode, n), '{}'::jsonb) from (
          select coalesce(props->>'mode', 'unknown') as mode, count(*) as n from public.rv_channel_events
          where channel = 'telegram' and event = 'link_redeemed' and ts >= since group by 1) m),
      'redeem_failed', (select coalesce(jsonb_object_agg(err, n), '{}'::jsonb) from (
          select coalesce(props->>'error', 'unknown') as err, count(*) as n from public.rv_channel_events
          where channel = 'telegram' and event = 'link_redeem_failed' and ts >= since group by 1) m),
      'link_codes_used', (select count(*) from public.rv_telegram_link_codes where used_at >= since),
      'median_seconds_issue_to_redeem', (select round((percentile_cont(0.5) within group (order by extract(epoch from (used_at - created_at))))::numeric, 1)
          from public.rv_telegram_link_codes where used_at >= since),
      'linked_total', (select count(*) from public.rv_telegram_users where linked_at is not null),
      'linked_in_window', (select count(*) from public.rv_telegram_users where linked_at >= since))
    into sec;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('linking', sec);

  -- audience snapshot (rv_telegram_users), reminders, blocks, groups
  begin
    select jsonb_build_object(
      'users_total', (select count(*) from public.rv_telegram_users),
      'users_new_in_window', (select count(*) from public.rv_telegram_users where created_at >= since),
      'reminders_on', (select count(*) from public.rv_telegram_users where remind_daily and not blocked),
      'blocked_now', (select count(*) from public.rv_telegram_users where blocked),
      'blocked_events', (select count(*) from public.rv_channel_events where channel = 'telegram' and event = 'bot_blocked' and ts >= since),
      'unblocked_events', (select count(*) from public.rv_channel_events where channel = 'telegram' and event = 'bot_unblocked' and ts >= since),
      'groups_added', (select count(*) from public.rv_channel_events where channel = 'telegram' and event = 'group_added' and ts >= since),
      'groups_removed', (select count(*) from public.rv_channel_events where channel = 'telegram' and event = 'group_removed' and ts >= since),
      'games_finished_in_groups', (select count(*) from public.rv_channel_events where channel = 'telegram' and event = 'game_finished' and ts >= since and props->>'chat_type' <> 'private'),
      'reminder_runs', (select count(*) from public.rv_channel_events where channel = 'telegram' and event = 'daily_push' and ts >= since),
      'reminders_sent', (select coalesce(sum((props->>'sent')::int), 0) from public.rv_channel_events where channel = 'telegram' and event = 'daily_push' and ts >= since))
    into sec;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('audience', sec);

  -- errors and rate limits
  begin
    select coalesce(jsonb_agg(to_jsonb(t) order by t.n desc), '[]'::jsonb) into sec from (
      select event, coalesce(props->>'where', '') as location, count(*) as n
      from public.rv_channel_events where channel = 'telegram' and ts >= since and event in ('error', 'rate_limited') group by 1, 2) t;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('errors', sec);

  -- per UTC day
  begin
    select coalesce(jsonb_agg(to_jsonb(t) order by t.day), '[]'::jsonb) into sec from (
      select (ts at time zone 'UTC')::date as day, count(*) as events,
             count(distinct coalesce(tg_user_ref, chat_ref)) filter (where event = any(active)) as users,
             count(*) filter (where event = 'start') as starts, count(*) filter (where event = 'game_finished') as games_finished
      from public.rv_channel_events where channel = 'telegram' and ts >= since group by 1) t;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('by_day', sec);

  -- share funnel (app + server events in puzzle_events). Labels differ per event: share_sent.channel = the app's choice
  -- (telegram|whatsapp|other), share_open.channel = the link's utm_source, deeplink_open.channel = the link's src.
  begin
    select coalesce(jsonb_agg(to_jsonb(t) order by t.share_sent desc, t.share_open desc), '[]'::jsonb) into sec from (
      select coalesce(channel, 'unknown') as channel,
             count(*) filter (where event_type = 'share_sent') as share_sent,
             count(*) filter (where event_type = 'share_open') as share_open,
             count(*) filter (where event_type = 'deeplink_open') as deeplink_open,
             count(*) filter (where event_type = 'telegram_cta') as telegram_cta
      from public.puzzle_events
      where event_type in ('share_sent', 'share_open', 'deeplink_open', 'telegram_cta') and ts >= since group by 1) t;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('share_funnel', sec);

  -- web /play: server-counted page opens by source, Play in Telegram clicks, Play Store clicks
  begin
    select jsonb_build_object(
      'hits_by_source', (select coalesce(jsonb_agg(to_jsonb(t) order by t.hits + t.share_opens desc), '[]'::jsonb) from (
          select coalesce(channel, 'direct') as source,
                 count(*) filter (where event_type = 'web_play_hit') as hits, count(*) filter (where event_type = 'share_open') as share_opens
          from public.puzzle_events where event_type in ('web_play_hit', 'share_open') and ts >= since group by 1) t),
      'hits_by_page', (select coalesce(jsonb_agg(to_jsonb(t) order by t.hits desc), '[]'::jsonb) from (
          select puzzle_type as page, count(*) as hits from public.puzzle_events
          where event_type in ('web_play_hit', 'share_open') and ts >= since group by 1 order by 2 desc limit 30) t),
      'telegram_clicks', (select coalesce(jsonb_agg(to_jsonb(t) order by t.clicks desc), '[]'::jsonb) from (
          select puzzle_type as page, coalesce(channel, 'unknown') as surface, count(*) as clicks from public.puzzle_events
          where event_type = 'web_play_telegram' and ts >= since group by 1, 2) t),
      'play_store_clicks', (select count(*) from public.puzzle_events where event_type = 'web_play_cta' and ts >= since))
    into sec;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('web_play', sec);

  -- forge (AI game generation) per user. Counts only: no user ids leave the database through this function.
  begin
    with g as (select user_id, count(*) as n, count(*) filter (where status = 'completed') as ok
               from public.forge_sessions where created_at >= since group by user_id)
    select jsonb_build_object(
      'sessions', coalesce(sum(n), 0), 'completed', coalesce(sum(ok), 0),
      'users', count(*) filter (where user_id is not null),
      'avg_per_user', round(avg(n) filter (where user_id is not null), 2),
      'max_per_user', max(n) filter (where user_id is not null),
      'users_with_1', count(*) filter (where user_id is not null and n = 1),
      'users_with_2', count(*) filter (where user_id is not null and n = 2),
      'users_with_3_to_5', count(*) filter (where user_id is not null and n between 3 and 5),
      'users_with_6_plus', count(*) filter (where user_id is not null and n >= 6))
    into sec from g;
  exception when others then sec := jsonb_build_object('error', sqlerrm); end;
  r := r || jsonb_build_object('forge', sec);

  return r;
end;
$fn$;
revoke all on function public.rv_channel_stats(int) from public, anon, authenticated;
grant execute on function public.rv_channel_stats(int) to service_role;

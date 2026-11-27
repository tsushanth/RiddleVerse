-- Share of new players who come back the next day. Read-only. Run in the RiddleVerse Supabase project
-- (uujjodxicvifmiwlimob). Events, columns and today's baseline: docs/metrics.md, "Next-day return".

-- 1) Android, new installs of the last 30 days, split by how likely they are to be people.
--    next day = an app_open on the calendar day (the phone's own) after the first one.
--    Eligible = first opened at least two UTC dates ago, so the whole next day has passed everywhere.
with e as (
  select install_id, app_version, event_type, funnel_step, days_since_install, traffic, puzzle_type,
         coalesce(event_ts, ts) as at
  from public.puzzle_events
  where platform = 'android' and length(install_id) = 36 and coalesce(app_version, '') not ilike '%qa%'
    and ts >= now() - interval '32 days'
), installs as (
  select install_id,
    min(app_version) as version,
    min(at) filter (where funnel_step = 'first_open') as first_open,
    min(at) filter (where funnel_step in ('guest_started', 'auth_started')) as first_tap,
    min(days_since_install) filter (where event_type = 'app_open') as day0,
    coalesce(bool_or(traffic = 'testlab'), false) as testlab,
    bool_or(event_type = 'start') as started,
    bool_or(event_type = 'complete') as completed
  from e group by install_id
  having bool_or(funnel_step = 'first_open')
), marked as (
  -- Heuristics for automated installs that carry no testlab label (all rows before 2026-11-26 carry none):
  -- three or more first opens of one version within an hour, or a first tap 35 to 65 seconds after the first open
  -- (the fixed wait of the release-time crawlers).
  select i.*,
    count(*) over (partition by version order by first_open
                   range between interval '1 hour' preceding and interval '1 hour' following) >= 3 as burst,
    coalesce(extract(epoch from first_tap - first_open) between 35 and 65, false) as scripted
  from installs i
), r as (
  select m.*,
    exists (select 1 from e where e.install_id = m.install_id and e.event_type = 'app_open'
            and e.days_since_install = m.day0 + 1) as back_next_day,
    exists (select 1 from e where e.install_id = m.install_id and e.event_type = 'app_open'
            and e.days_since_install > m.day0) as back_later
  from marked m
  where first_open >= now() - interval '30 days'
    and (first_open at time zone 'utc')::date <= (now() at time zone 'utc')::date - 2
)
select case when grouping(grp) = 1 then '0 all new installs' else grp end as cohort,
  count(*) as eligible,
  count(*) filter (where started) as started_a_puzzle,
  count(*) filter (where completed) as completed_one,
  count(*) filter (where back_next_day) as back_next_day,
  count(*) filter (where back_later) as back_any_later_day,
  round(100.0 * count(*) filter (where back_next_day) / nullif(count(*), 0), 1) as next_day_pct
from (
  select *, case when testlab then '3 test lab (labelled)'
                 when burst or scripted then '2 looks automated (heuristic)'
                 else '1 likely people' end as grp
  from r
) x
group by grouping sets ((grp), ())
order by 1;

-- 2) The starter puzzle (builds after 188, and the web card): who saw it, tried it and finished it.
select platform,
  count(distinct install_id) filter (where event_type in ('start', 'web_play_view')) as shown,
  count(distinct install_id) filter (where event_type = 'web_play_start') as web_tried,
  count(distinct install_id) filter (where event_type in ('complete', 'web_play_complete')) as finished,
  count(distinct install_id) filter (where event_type = 'abandon') as left_unfinished
from public.puzzle_events
where puzzle_type = 'starter' and coalesce(traffic, '') <> 'testlab' and ts >= now() - interval '30 days'
group by 1 order by 1;

-- 3) Web visitors (one random id per browser; Do Not Track / GPC visitors send nothing): back on the next UTC day.
with w as (
  select install_id, (coalesce(event_ts, ts) at time zone 'utc')::date as d
  from public.puzzle_events
  where platform = 'web' and event_type in ('web_play_view', 'web_play_start', 'web_play_complete')
    and coalesce(traffic, '') <> 'testlab' and ts >= now() - interval '30 days'
), f as (select install_id, min(d) as d0 from w group by 1)
select count(*) as eligible,
  count(*) filter (where exists (select 1 from w where w.install_id = f.install_id and w.d = f.d0 + 1)) as back_next_day
from f where d0 <= (now() at time zone 'utc')::date - 2;

-- 4) The reminder push, when it is switched on: runs and sends per day (no addresses selected).
select (sent_at at time zone 'utc')::date as day, notification_type, count(*) as rows,
  count(*) filter (where success) as delivered
from public.notification_logs
where notification_type in ('new_player_reminder', 'new_player_reminder_run') and sent_at >= now() - interval '30 days'
group by 1, 2 order by 1 desc, 2;

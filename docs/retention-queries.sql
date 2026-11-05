-- RiddleVerse retention + daily puzzle metrics (READ-ONLY: select statements only, no DDL, nothing is created).
-- Run in the RiddleVerse Supabase SQL editor / with the service role. Needs, in this order:
--   1. migration 20261101120000_funnel_events.sql applied (adds funnel_step / days_since_install)
--   2. backend deployed with the retention allow-list (web/backend/services/puzzleMonitoring.js)
--   3. an Android build with the retention release live (Funnel.dailySolved, reminder, welcome-back)
-- See docs/metrics.md for the event list. All data is anonymous (install_id is a random per-install id).

-- 1) Daily puzzle completion rate: of installs that opened the app on a UTC day, how many solved the daily that day.
with opens as (
  select (ts at time zone 'UTC')::date as day, install_id
  from public.puzzle_events where event_type = 'app_open' and ts >= now() - interval '28 days' group by 1, 2
), solves as (
  select (ts at time zone 'UTC')::date as day, install_id
  from public.puzzle_events where event_type = 'daily_solved' and ts >= now() - interval '28 days' group by 1, 2
)
select o.day,
       count(*)                                             as opened,
       count(s.install_id)                                  as solved_daily,
       round(100.0 * count(s.install_id) / nullif(count(*), 0), 1) as daily_completion_pct
from opens o left join solves s on s.day = o.day and s.install_id = o.install_id
group by o.day order by o.day desc;

-- 2) Streak length distribution of solves (score = streak after the solve), last 28 days.
select case when score >= 14 then '14+' when score >= 7 then '7-13' when score >= 3 then '3-6' else '1-2' end as streak_bucket,
       count(*) as solves, count(distinct install_id) as installs
from public.puzzle_events where event_type = 'daily_solved' and ts >= now() - interval '28 days'
group by 1 order by 1;

-- 3) Return after first daily solve: share of installs that solved a daily and opened again 1+ / 7+ days later.
with first_solve as (
  select install_id, min(days_since_install) as d0 from public.puzzle_events where event_type = 'daily_solved' group by 1
), later as (
  select f.install_id, max(o.days_since_install) - f.d0 as span
  from first_solve f join public.puzzle_events o on o.install_id = f.install_id and o.event_type = 'app_open'
  group by f.install_id, f.d0
)
select count(*) as solvers,
       count(*) filter (where span >= 1) as came_back_1d_plus,
       count(*) filter (where span >= 7) as came_back_7d_plus,
       round(100.0 * count(*) filter (where span >= 1) / nullif(count(*), 0), 1) as pct_1d_plus,
       round(100.0 * count(*) filter (where span >= 7) / nullif(count(*), 0), 1) as pct_7d_plus
from later;

-- 4) Reminder opt-in funnel (each step counted once per install).
select funnel_step, count(distinct install_id) as installs
from public.puzzle_events
where event_type = 'funnel' and funnel_step in
  ('daily_completed', 'reminder_enabled', 'notif_permission_granted', 'notif_permission_denied', 'welcome_back_shown', 'streak_3', 'streak_7')
group by 1 order by installs desc;

-- 5) Do reminders bring people back? Opens per active day, installs with reminder_enabled vs without (last 28 days).
with r as (select distinct install_id from public.puzzle_events where event_type = 'funnel' and funnel_step = 'reminder_enabled'),
d as (
  select install_id, count(distinct (ts at time zone 'UTC')::date) as active_days
  from public.puzzle_events where event_type = 'app_open' and ts >= now() - interval '28 days' group by 1
)
select (r.install_id is not null) as reminder_on, count(*) as installs, round(avg(d.active_days), 2) as avg_active_days_28d
from d left join r using (install_id) group by 1;

-- 6) Win-back: installs that opened after 3+ idle days (welcome_back_shown) and solved the daily within the same week.
select count(distinct w.install_id) as welcomed,
       count(distinct s.install_id) as solved_daily_after
from public.puzzle_events w
left join public.puzzle_events s on s.install_id = w.install_id and s.event_type = 'daily_solved' and s.ts >= w.ts and s.ts < w.ts + interval '7 days'
where w.event_type = 'funnel' and w.funnel_step = 'welcome_back_shown';

-- 7) Supporter offer (only once the flag is on): shown vs purchased.
select funnel_step, count(distinct install_id) as installs
from public.puzzle_events where event_type = 'funnel' and funnel_step in ('supporter_offer_shown', 'supporter_purchased')
group by 1;

-- D1 / D7 retention by install cohort, funnel steps and DAU: docs/funnel-queries.sql (queries 1, 2, 6).

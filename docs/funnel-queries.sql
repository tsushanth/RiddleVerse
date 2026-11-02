-- RiddleVerse install funnel + retention queries (read-only). Run in the RiddleVerse Supabase project
-- (uujjodxicvifmiwlimob) SQL editor / service role, AFTER migration 20261101120000_funnel_events.sql is applied,
-- the backend is deployed, and an Android build containing Funnel.kt is live.
--
-- Scope: only NEW installs made after the instrumented build (they report first_open). Installs that updated into
-- the build report app_open (days_since_install) but are excluded from the funnel and the cohort views.
-- Data is anonymous: install_id is the random per-install id; no emails or device ids. Debug builds do not report.
-- Caveat: the public ingest endpoint is unauthenticated (rate-limited), so treat small absolute numbers with care.

-- 1) D1 / D7 retention by install-date cohort. Rates are null until the cohort is old enough.
select cohort_date, installs, d1_returned, d1_rate_pct, d7_returned, d7_rate_pct
from public.retention_by_cohort
order by cohort_date desc
limit 30;

-- 2) Funnel conversion (all new installs in the last 30 days), each step as count and % of first_open.
with f as (select * from public.funnel_by_install where first_open_at >= now() - interval '30 days')
select step, installs, round(100.0 * installs / nullif((select count(*) from f), 0), 1) as pct_of_first_open
from (
  select 1 as o, 'first_open' as step,             count(*) as installs from f
  union all select 2, 'welcome_shown',            count(welcome_shown_at) from f
  union all select 3, 'auth_started',             count(auth_started_at) from f
  union all select 4, 'auth_done',                count(auth_done_at) from f
  union all select 5, 'guest_started',            count(guest_started_at) from f
  union all select 6, 'onboarding_done',          count(onboarding_done_at) from f
  union all select 7, 'home_shown',               count(home_shown_at) from f
  union all select 8, 'first_puzzle_started',     count(first_puzzle_started_at) from f
  union all select 9, 'first_puzzle_completed',   count(first_puzzle_completed_at) from f
) s order by o;

-- 3) Funnel by app version (spot a build that broke a step).
select app_version, count(*) as installs,
       count(auth_done_at) as auth_done, count(onboarding_done_at) as onboarding_done,
       count(home_shown_at) as home_shown, count(first_puzzle_started_at) as first_puzzle_started,
       count(first_puzzle_completed_at) as first_puzzle_completed
from public.funnel_by_install
group by app_version order by installs desc;

-- 4) Median time from first_open to first completed puzzle (activation speed), new installs only.
select percentile_cont(0.5) within group (order by extract(epoch from (first_puzzle_completed_at - first_open_at))) / 60.0 as median_minutes
from public.funnel_by_install where first_puzzle_completed_at is not null;

-- 5) Where do installs stop? Last step reached, per install (last 30 days).
select case
         when first_puzzle_completed_at is not null then '9 first_puzzle_completed'
         when first_puzzle_started_at   is not null then '8 first_puzzle_started'
         when home_shown_at             is not null then '7 home_shown'
         when onboarding_done_at        is not null then '6 onboarding_done'
         when guest_started_at          is not null then '5 guest_started'
         when auth_done_at              is not null then '4 auth_done'
         when auth_started_at           is not null then '3 auth_started'
         when welcome_shown_at          is not null then '2 welcome_shown'
         else '1 first_open only' end as last_step_reached,
       count(*) as installs
from public.funnel_by_install where first_open_at >= now() - interval '30 days'
group by 1 order by 1;

-- 6) Daily opens (DAU from app_open) and returning share, last 14 days (UTC by server time).
select (ts at time zone 'UTC')::date as day,
       count(distinct install_id) as dau,
       count(distinct install_id) filter (where days_since_install >= 1) as returning_installs
from public.puzzle_events
where event_type = 'app_open' and ts >= now() - interval '14 days'
group by 1 order by 1 desc;

-- 7) Retention WITHOUT the views (raw-table equivalent of query 1, handy if the views were not created).
with installs as (
  select install_id, (min(ts) at time zone 'UTC')::date as cohort_date
  from public.puzzle_events where event_type = 'funnel' and funnel_step = 'first_open' group by install_id
), opens as (
  select distinct install_id, days_since_install as d from public.puzzle_events where event_type = 'app_open'
)
select i.cohort_date, count(distinct i.install_id) as installs,
       count(distinct i.install_id) filter (where o.d = 1) as d1_returned,
       count(distinct i.install_id) filter (where o.d = 7) as d7_returned
from installs i left join opens o using (install_id)
group by 1 order by 1 desc;

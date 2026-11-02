-- Install funnel + day-N return measurement on top of public.puzzle_events (ADDITIVE ONLY, idempotent).
-- NOT applied by anyone yet. Apply BEFORE deploying the backend that accepts event_type 'funnel' / 'app_open',
-- otherwise inserts of a batch that contains such events fail (plain puzzle events are unaffected either way,
-- the backend only references the new columns on funnel rows).
--
-- Event model (all anonymous, keyed by the existing client-generated install_id; no email/device id/free text):
--   event_type = 'funnel'   -> funnel_step in (first_open, welcome_shown, auth_started, auth_done, guest_started,
--                              onboarding_done, home_shown, first_puzzle_started, first_puzzle_completed); first occurrence per install
--   event_type = 'app_open' -> days_since_install (whole local days), at most once per local day per install
--   puzzle_type = 'app' for both (column is NOT NULL; the admin puzzle summary hides this placeholder).
-- Rollback: 20261101120000_funnel_events.rollback.sql

alter table public.puzzle_events add column if not exists funnel_step text;
alter table public.puzzle_events add column if not exists days_since_install int;

-- Partial index: only the (small) funnel/app_open slice, for per-install lookups.
create index if not exists puzzle_events_funnel_install_idx
  on public.puzzle_events (install_id, event_type, ts)
  where event_type in ('funnel', 'app_open');

-- One row per install that reported first_open (i.e. a NEW install made after this instrumentation shipped;
-- installs that updated into the instrumented build never report first_open and are excluded by design).
-- *_at columns are the earliest SERVER timestamp (ts) of that step; null = step not reached.
create or replace view public.funnel_by_install with (security_invoker = true) as
select
  e.install_id,
  (array_agg(e.app_version order by e.ts) filter (where e.funnel_step = 'first_open'))[1] as app_version,
  min(e.ts) filter (where e.funnel_step = 'first_open')            as first_open_at,
  min(e.ts) filter (where e.funnel_step = 'welcome_shown')         as welcome_shown_at,
  min(e.ts) filter (where e.funnel_step = 'auth_started')          as auth_started_at,
  min(e.ts) filter (where e.funnel_step = 'auth_done')             as auth_done_at,
  min(e.ts) filter (where e.funnel_step = 'guest_started')         as guest_started_at,
  min(e.ts) filter (where e.funnel_step = 'onboarding_done')       as onboarding_done_at,
  min(e.ts) filter (where e.funnel_step = 'home_shown')            as home_shown_at,
  min(e.ts) filter (where e.funnel_step = 'first_puzzle_started')  as first_puzzle_started_at,
  min(e.ts) filter (where e.funnel_step = 'first_puzzle_completed') as first_puzzle_completed_at
from public.puzzle_events e
where e.event_type = 'funnel'
group by e.install_id
having bool_or(e.funnel_step = 'first_open');

-- D1 / D7 (and D3, D14, D30) classic retention per install-date cohort (UTC date of first_open).
-- "Returned on day N" = the install reported an app_open with days_since_install = N (device-local day count).
-- *_rate is null until the cohort is old enough (cohort_date + N < current_date), so immature cohorts never read as 0%.
create or replace view public.retention_by_cohort with (security_invoker = true) as
with installs as (
  select install_id, (first_open_at at time zone 'UTC')::date as cohort_date from public.funnel_by_install
), opens as (
  select distinct install_id, days_since_install as d
  from public.puzzle_events
  where event_type = 'app_open' and days_since_install is not null
)
select
  i.cohort_date,
  count(distinct i.install_id)                                           as installs,
  count(distinct i.install_id) filter (where o.d = 1)                    as d1_returned,
  count(distinct i.install_id) filter (where o.d = 3)                    as d3_returned,
  count(distinct i.install_id) filter (where o.d = 7)                    as d7_returned,
  count(distinct i.install_id) filter (where o.d = 14)                   as d14_returned,
  count(distinct i.install_id) filter (where o.d = 30)                   as d30_returned,
  case when i.cohort_date + 1  < current_date then round(100.0 * count(distinct i.install_id) filter (where o.d = 1)  / count(distinct i.install_id), 1) end as d1_rate_pct,
  case when i.cohort_date + 3  < current_date then round(100.0 * count(distinct i.install_id) filter (where o.d = 3)  / count(distinct i.install_id), 1) end as d3_rate_pct,
  case when i.cohort_date + 7  < current_date then round(100.0 * count(distinct i.install_id) filter (where o.d = 7)  / count(distinct i.install_id), 1) end as d7_rate_pct,
  case when i.cohort_date + 14 < current_date then round(100.0 * count(distinct i.install_id) filter (where o.d = 14) / count(distinct i.install_id), 1) end as d14_rate_pct,
  case when i.cohort_date + 30 < current_date then round(100.0 * count(distinct i.install_id) filter (where o.d = 30) / count(distinct i.install_id), 1) end as d30_rate_pct
from installs i
left join opens o on o.install_id = i.install_id
group by i.cohort_date;

-- Read-only, service role only (same posture as puzzle_events).
revoke all on public.funnel_by_install   from public, anon, authenticated;
revoke all on public.retention_by_cohort from public, anon, authenticated;
grant select on public.funnel_by_install   to service_role;
grant select on public.retention_by_cohort to service_role;

-- Rollback for 20261101120000_funnel_events.sql (drops ONLY objects it created; deletes funnel/app_open rows' extra columns).
drop view if exists public.retention_by_cohort;
drop view if exists public.funnel_by_install;
drop index if exists public.puzzle_events_funnel_install_idx;
alter table public.puzzle_events drop column if exists days_since_install;
alter table public.puzzle_events drop column if exists funnel_step;
-- Optional: delete the placeholder rows too.
-- delete from public.puzzle_events where event_type in ('funnel', 'app_open');

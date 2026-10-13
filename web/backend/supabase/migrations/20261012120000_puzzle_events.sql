-- Puzzle monitoring (ADDITIVE ONLY). Event log written by POST /api/puzzle-events,
-- read by GET /api/dash/puzzles. Service role only: RLS enabled, NO policies.
-- user_id is text (Firebase canonical uid); install_id is the client-generated id.
-- Rollback: 20261012120000_puzzle_events.rollback.sql

create table if not exists public.puzzle_events (
  id            bigint generated always as identity primary key,
  ts            timestamptz not null default now(),
  event_ts      timestamptz,
  user_id       text,
  install_id    text not null,
  platform      text not null,
  app_version   text,
  event_type    text not null,
  puzzle_type   text not null,
  difficulty    text,
  session_id    text,
  duration_ms   int,
  score         int,
  moves         int,
  hints_used    int,
  mistakes      int,
  error_message text
);

create index if not exists puzzle_events_puzzle_ts_idx  on public.puzzle_events (puzzle_type, ts desc);
create index if not exists puzzle_events_type_ts_idx    on public.puzzle_events (event_type, ts desc);
create index if not exists puzzle_events_install_ts_idx on public.puzzle_events (install_id, ts desc);

alter table public.puzzle_events enable row level security;
-- Intentionally NO policies: anon/authenticated get no access; service_role bypasses RLS.
revoke all on table public.puzzle_events from anon, authenticated;

-- Aggregation for the admin dashboard. One scan of the window, grouped four ways.
create or replace function public.puzzle_monitoring_summary(p_days int default 7, p_platform text default 'all')
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  with ev as materialized (
    select e.ts, e.install_id, e.platform, e.event_type, e.puzzle_type, e.duration_ms, e.error_message
    from public.puzzle_events e
    where e.ts >= now() - make_interval(days => greatest(1, least(coalesce(p_days, 7), 90)))
      and (coalesce(p_platform, 'all') = 'all' or e.platform = p_platform)
  )
  select jsonb_build_object(
    'activeInstalls', (select count(distinct install_id) from ev),
    'puzzles', coalesce((
      select jsonb_agg(x) from (select jsonb_build_object(
        'puzzleType', puzzle_type,
        'starts',     count(*) filter (where event_type = 'start'),
        'completes',  count(*) filter (where event_type = 'complete'),
        'fails',      count(*) filter (where event_type = 'fail'),
        'abandons',   count(*) filter (where event_type = 'abandon'),
        'hints',      count(*) filter (where event_type = 'hint'),
        'errors',     count(*) filter (where event_type = 'error'),
        'medianDurationMs', (percentile_cont(0.5) within group (order by duration_ms)
                              filter (where event_type = 'complete' and duration_ms is not null)),
        'android',    count(*) filter (where platform = 'android'),
        'web',        count(*) filter (where platform = 'web'),
        'lastSeen',   max(ts)) x
      from ev group by puzzle_type) g), '[]'::jsonb),
    'daily', coalesce((
      select jsonb_agg(x order by x->>'date') from (
        select jsonb_build_object(
          'date', to_char((ts at time zone 'UTC')::date, 'YYYY-MM-DD'),
          'starts',    count(*) filter (where event_type = 'start'),
          'completes', count(*) filter (where event_type = 'complete'),
          'errors',    count(*) filter (where event_type = 'error')) x
        from ev group by (ts at time zone 'UTC')::date) d), '[]'::jsonb),
    'topErrors', coalesce((
      select jsonb_agg(x order by (x->>'count')::int desc) from (
        select jsonb_build_object('puzzleType', puzzle_type, 'message', coalesce(error_message, ''),
                                  'count', count(*), 'lastSeen', max(ts)) x
        from ev where event_type = 'error'
        group by puzzle_type, coalesce(error_message, '')
        order by count(*) desc limit 50) t), '[]'::jsonb)
  );
$$;

revoke all on function public.puzzle_monitoring_summary(int, text) from public, anon, authenticated;
grant execute on function public.puzzle_monitoring_summary(int, text) to service_role;

-- Retention helper. NOT scheduled. Run manually, e.g.:
--   select public.purge_puzzle_events(90);
create or replace function public.purge_puzzle_events(p_keep_days int default 90)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare n bigint;
begin
  if p_keep_days is null or p_keep_days < 7 then
    raise exception 'p_keep_days must be >= 7';
  end if;
  delete from public.puzzle_events where ts < now() - make_interval(days => p_keep_days);
  get diagnostics n = row_count;
  return n;
end;
$$;

revoke all on function public.purge_puzzle_events(int) from public, anon, authenticated;
grant execute on function public.purge_puzzle_events(int) to service_role;

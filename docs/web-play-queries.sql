-- RiddleVerse public /play page measurement (read-only). Run in the RiddleVerse Supabase project
-- (uujjodxicvifmiwlimob) SQL editor / service role, AFTER the backend that accepts the web_play_* event types is deployed
-- (no migration needed: it reuses public.puzzle_events) and the web build that sends them is live.
--
-- Rows: event_type in (web_play_view, web_play_start, web_play_complete, web_play_cta), platform = 'web',
-- puzzle_type = the game slug (the Play button link carries utm_campaign = the same slug, see web/frontend/src/utils/playLinks.js),
-- install_id = random per-browser id (localStorage key rv_play_vid, NOT the signed-in app's id), session_id = random per page load,
-- user_id is always null. Visitors with Global Privacy Control / Do Not Track send nothing, so these are lower bounds.
-- Caveats: (a) a CTA click is not an install; join to Play Console (campaign = slug) for installs. (b) start = first
-- pointer/key interaction inside the game box. (c) the unauthenticated ingest endpoint is rate-limited, not bot-proof.
-- (d) puzzle_monitoring_summary().activeInstalls also counts these visitors when platform is web or all.

-- 1) Per slug per day: views, starts, completes, CTA clicks, plus distinct browsers (last 30 days, UTC).
select (ts at time zone 'UTC')::date                                   as day,
       puzzle_type                                                      as slug,
       count(*) filter (where event_type = 'web_play_view')            as views,
       count(*) filter (where event_type = 'web_play_start')           as starts,
       count(*) filter (where event_type = 'web_play_complete')        as completes,
       count(*) filter (where event_type = 'web_play_cta')             as cta_clicks,
       count(distinct install_id)                                       as browsers
from public.puzzle_events
where event_type in ('web_play_view', 'web_play_start', 'web_play_complete', 'web_play_cta')
  and ts >= now() - interval '30 days'
group by 1, 2
order by 1 desc, cta_clicks desc, views desc;

-- 2) Which pages bring Play clicks: per slug over 30 days, with rates (distinct page loads = session_id).
select puzzle_type as slug,
       count(distinct session_id) filter (where event_type = 'web_play_view')     as page_loads,
       count(distinct session_id) filter (where event_type = 'web_play_start')    as played,
       count(distinct session_id) filter (where event_type = 'web_play_complete') as solved,
       count(distinct session_id) filter (where event_type = 'web_play_cta')      as clicked_play,
       round(100.0 * count(distinct session_id) filter (where event_type = 'web_play_cta')
             / nullif(count(distinct session_id) filter (where event_type = 'web_play_view'), 0), 1) as cta_pct_of_loads
from public.puzzle_events
where event_type in ('web_play_view', 'web_play_start', 'web_play_complete', 'web_play_cta')
  and ts >= now() - interval '30 days'
group by 1
order by clicked_play desc, page_loads desc;

-- 3) Median seconds from first interaction to a solve, per slug (complete rows only).
select puzzle_type as slug, count(*) as solves,
       round((percentile_cont(0.5) within group (order by duration_ms) / 1000.0)::numeric, 1) as median_seconds
from public.puzzle_events
where event_type = 'web_play_complete' and duration_ms is not null and ts >= now() - interval '30 days'
group by 1 order by solves desc;

-- 4) Do players click more than lookers? CTA rate for page loads with a start vs without (30 days).
with s as (
  select session_id,
         bool_or(event_type = 'web_play_start') as played,
         bool_or(event_type = 'web_play_cta')   as clicked
  from public.puzzle_events
  where event_type in ('web_play_view', 'web_play_start', 'web_play_cta') and session_id is not null
    and ts >= now() - interval '30 days'
  group by session_id
)
select played, count(*) as page_loads, count(*) filter (where clicked) as clicked_play,
       round(100.0 * count(*) filter (where clicked) / count(*), 1) as cta_pct
from s group by played order by played;

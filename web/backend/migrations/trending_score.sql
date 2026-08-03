-- Feature C — trending-score ranking for custom_games.
--
-- Apply order: run after game_series.sql (which creates the
-- game_play_events table and the custom_games.trending_score column).
--
-- Score formula:
--   trending_score = LEAST(completed_plays / unique_completers, 10)  -- replays per UU
--                  * completion_rate                                 -- (completed / started)
--                  * recency_floor(created_at)                       -- 1.0 at 0d → 0.3 at 90d
--
-- All over a 7-day window so a game that pops, then dies, decays naturally.
-- completion_rate falls back to 1.0 when no /play-events/start events exist
-- (cold start before iOS deploys the start hook).

CREATE OR REPLACE FUNCTION refresh_trending_scores() RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    -- 1. Compute scores for games with plays in the last 7 days
    WITH stats AS (
        SELECT
            game_id,
            COUNT(*) FILTER (WHERE completed)::float                                       AS completed_plays,
            GREATEST(COUNT(DISTINCT user_id) FILTER (WHERE completed), 0)::float           AS unique_completers,
            COUNT(*)::float                                                                AS total_events,
            COUNT(*) FILTER (WHERE NOT completed)::float                                   AS starts_only
        FROM game_play_events
        WHERE started_at > NOW() - INTERVAL '7 days'
          AND user_id IS NOT NULL
        GROUP BY game_id
    )
    UPDATE custom_games cg SET trending_score =
        -- replays_per_uu, capped at 10
        LEAST(s.completed_plays / NULLIF(s.unique_completers, 0), 10.0)
        -- completion_rate: completed / (completed + starts-only). When no /start events
        -- have been recorded (starts_only = 0), this collapses to 1.0 — safe v1 default.
        * (s.completed_plays / NULLIF(s.completed_plays + s.starts_only, 0))
        -- recency floor (1.0 for new games, 0.3 minimum at 90+ days old)
        * GREATEST(
            0.3,
            1.0 - EXTRACT(EPOCH FROM (NOW() - cg.created_at)) / (90.0 * 86400.0)
          )
    FROM stats s
    WHERE cg.id = s.game_id;

    -- 2. Reset score on games that have aged out of the 7-day window
    UPDATE custom_games SET trending_score = 0
    WHERE trending_score > 0
      AND id NOT IN (
        SELECT DISTINCT game_id FROM game_play_events
        WHERE started_at > NOW() - INTERVAL '7 days'
      );
END;
$$;

-- Hourly cron via pg_cron (Supabase enables this extension by default).
-- Wrapped in a check so this file is safe to run on local Postgres without pg_cron.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        PERFORM cron.unschedule(jobid)
        FROM cron.job
        WHERE jobname = 'refresh-trending-hourly';

        PERFORM cron.schedule(
            'refresh-trending-hourly',
            '7 * * * *',           -- :07 every hour, off-the-hour to avoid contention with other jobs
            $cron$SELECT refresh_trending_scores();$cron$
        );
    END IF;
END $$;

-- One-time initial run so /browse?sort=trending isn't empty until the first cron tick.
SELECT refresh_trending_scores();

-- Game Series + sequential level progression + play-event analytics
-- Adds the "Generate Next Level" feature scaffolding (Feature A)
-- and the play-event table for trending-score ranking (Feature C).
--
-- Schema decisions locked in this migration:
--   - Series is owned by the original creator of level 1.
--   - Anyone who beats level N may append level N+1 (no fork concept).
--   - Race resolved by UNIQUE(series_id, level_index).
--   - Each level keeps its own creator_id in custom_games; series.creator_id
--     records only the originator.
--   - game_difficulty_variants table is untouched and remains orthogonal
--     (level X can still have harder variants 2-5).

-- ────────────────────────────────────────────────────────────────────────
-- 1. game_series — groups a chain of levels
-- ────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS game_series (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id  TEXT NOT NULL,
    title       TEXT NOT NULL,
    description TEXT,
    level_count INTEGER NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_game_series_creator  ON game_series(creator_id);
CREATE INDEX IF NOT EXISTS idx_game_series_created  ON game_series(created_at DESC);

ALTER TABLE game_series ENABLE ROW LEVEL SECURITY;
CREATE POLICY "read game_series"   ON game_series FOR SELECT USING (true);
CREATE POLICY "insert game_series" ON game_series FOR INSERT WITH CHECK (true);
CREATE POLICY "update game_series" ON game_series FOR UPDATE USING (true) WITH CHECK (true);

CREATE TRIGGER update_game_series_updated_at
    BEFORE UPDATE ON game_series
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ────────────────────────────────────────────────────────────────────────
-- 2. custom_games — link each game to a series + its position in the chain
-- ────────────────────────────────────────────────────────────────────────
ALTER TABLE custom_games
    ADD COLUMN IF NOT EXISTS series_id        UUID REFERENCES game_series(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS level_index      INTEGER,
    ADD COLUMN IF NOT EXISTS parent_level_id  UUID REFERENCES custom_games(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS trending_score   DOUBLE PRECISION NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_custom_games_series        ON custom_games(series_id, level_index);
CREATE INDEX IF NOT EXISTS idx_custom_games_trending      ON custom_games(trending_score DESC) WHERE series_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_custom_games_series_level
    ON custom_games(series_id, level_index)
    WHERE series_id IS NOT NULL;

-- ────────────────────────────────────────────────────────────────────────
-- 3. game_play_events — one row per play session, feeds the rollup job
-- ────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS game_play_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id     UUID NOT NULL REFERENCES custom_games(id) ON DELETE CASCADE,
    series_id   UUID REFERENCES game_series(id) ON DELETE SET NULL,
    user_id     TEXT,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at    TIMESTAMPTZ,
    completed   BOOLEAN NOT NULL DEFAULT FALSE,
    score       INTEGER NOT NULL DEFAULT 0,
    ms_played   INTEGER
);

CREATE INDEX IF NOT EXISTS idx_play_events_game        ON game_play_events(game_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_play_events_user        ON game_play_events(user_id, game_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_play_events_recent      ON game_play_events(started_at DESC);

ALTER TABLE game_play_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY "read play_events"   ON game_play_events FOR SELECT USING (true);
CREATE POLICY "insert play_events" ON game_play_events FOR INSERT WITH CHECK (true);
CREATE POLICY "update play_events" ON game_play_events FOR UPDATE USING (true) WITH CHECK (true);

-- ────────────────────────────────────────────────────────────────────────
-- 4. Backfill — wrap every existing custom_games row in its own series-of-one
-- ────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
    g RECORD;
    new_series_id UUID;
BEGIN
    FOR g IN
        SELECT id, creator_id, title, description, created_at
        FROM custom_games
        WHERE series_id IS NULL
    LOOP
        INSERT INTO game_series (creator_id, title, description, level_count, created_at, updated_at)
        VALUES (g.creator_id, g.title, g.description, 1, g.created_at, g.created_at)
        RETURNING id INTO new_series_id;

        UPDATE custom_games
        SET series_id = new_series_id,
            level_index = 1
        WHERE id = g.id;
    END LOOP;
END $$;

-- After backfill, level_index for series-linked rows is never null.
-- (Standalone rows where series_id ends up NULL again via ON DELETE SET NULL
--  can have level_index = NULL — that's fine, the unique index ignores them.)

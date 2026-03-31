-- Difficulty Variants for Progressive Challenge
-- Stores harder versions of community games (levels 2-5)
-- Level 1 = original game in custom_games table

CREATE TABLE IF NOT EXISTS game_difficulty_variants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_game_id UUID NOT NULL REFERENCES custom_games(id) ON DELETE CASCADE,
    difficulty_level INTEGER NOT NULL CHECK (difficulty_level BETWEEN 2 AND 5),
    html_content TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'generating' CHECK (status IN ('generating', 'ready', 'failed')),
    generated_by TEXT,
    play_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(parent_game_id, difficulty_level)
);

CREATE INDEX IF NOT EXISTS idx_difficulty_parent ON game_difficulty_variants(parent_game_id);
CREATE INDEX IF NOT EXISTS idx_difficulty_status ON game_difficulty_variants(status);

-- RLS
ALTER TABLE game_difficulty_variants ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow read difficulty_variants" ON game_difficulty_variants
    FOR SELECT USING (true);

CREATE POLICY "Allow insert difficulty_variants" ON game_difficulty_variants
    FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow update difficulty_variants" ON game_difficulty_variants
    FOR UPDATE USING (true) WITH CHECK (true);

-- Auto-update updated_at (reuses trigger function from virtual_currency migration)
CREATE TRIGGER update_difficulty_variants_updated_at
    BEFORE UPDATE ON game_difficulty_variants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

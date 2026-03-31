-- Migration: Game Tweak Editor Support
-- Adds git repo tracking and tweak management columns to custom_games

-- Add GitHub repo reference (repo name under Kreative-Koala-LLC org)
ALTER TABLE custom_games
    ADD COLUMN IF NOT EXISTS github_repo TEXT;

-- Add published commit SHA (which git commit is currently live)
ALTER TABLE custom_games
    ADD COLUMN IF NOT EXISTS published_commit TEXT;

-- Add free tweaks remaining (default 5 per game)
ALTER TABLE custom_games
    ADD COLUMN IF NOT EXISTS free_tweaks_remaining INTEGER DEFAULT 5;

-- Index on github_repo for lookups
CREATE INDEX IF NOT EXISTS idx_custom_games_github_repo
    ON custom_games(github_repo)
    WHERE github_repo IS NOT NULL;

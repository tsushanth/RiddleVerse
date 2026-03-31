-- Migration: Track validation quality from game-worker
-- Stores the number of critical issues remaining after generation validation

ALTER TABLE custom_games
    ADD COLUMN IF NOT EXISTS critical_issues INTEGER DEFAULT 0;

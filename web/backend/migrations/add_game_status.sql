-- Add status column for staged publishing
-- Games with critical issues start as 'draft' and don't appear in public browse
-- Games that pass validation are 'published' and visible to everyone

ALTER TABLE custom_games
    ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'published';

-- Mark existing games with critical issues as drafts
UPDATE custom_games SET status = 'draft' WHERE critical_issues > 0;

-- Index for efficient filtering
CREATE INDEX IF NOT EXISTS idx_custom_games_status ON custom_games(status);

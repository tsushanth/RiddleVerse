-- Language of each create-game idea chip. Existing rows are English. The backend works without this column (every
-- row is then treated as English and the app shows its localized chips for other languages), so apply it before or
-- after deploying; non-English chips are only generated once the column exists.
ALTER TABLE game_suggestions ADD COLUMN IF NOT EXISTS language TEXT NOT NULL DEFAULT 'en';
CREATE INDEX IF NOT EXISTS idx_game_suggestions_language_status ON game_suggestions(language, status);

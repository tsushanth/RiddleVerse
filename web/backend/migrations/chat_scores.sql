-- Chat Scores table for Play-in-Chat feature
-- Stores per-chat, per-game leaderboards (e.g., Telegram group chat leaderboards)

CREATE TABLE IF NOT EXISTS chat_scores (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  game_id UUID NOT NULL,
  chat_id TEXT NOT NULL,
  platform TEXT NOT NULL DEFAULT 'telegram',
  user_id TEXT NOT NULL,
  username TEXT,
  score INTEGER NOT NULL DEFAULT 0,
  avatar_url TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(game_id, chat_id, platform, user_id)
);

-- Index for fast lookups by game+chat+platform
CREATE INDEX IF NOT EXISTS idx_chat_scores_lookup ON chat_scores(game_id, chat_id, platform);

-- Enable Row Level Security
ALTER TABLE chat_scores ENABLE ROW LEVEL SECURITY;

-- Allow all operations (scores are public within chat context)
CREATE POLICY chat_scores_all ON chat_scores FOR ALL USING (true) WITH CHECK (true);

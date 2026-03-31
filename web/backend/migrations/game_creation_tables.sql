-- Game Creation Tables Migration
-- Run this in Supabase SQL Editor

-- Table: game_creation_sessions
-- Stores active game creation sessions for both template and freeform flows
CREATE TABLE IF NOT EXISTS game_creation_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT,
    mode TEXT CHECK (mode IN ('template', 'freeform', 'remix')),
    status TEXT DEFAULT 'created',
    session_data JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Table: game_build_jobs
-- Tracks asynchronous game build jobs
CREATE TABLE IF NOT EXISTS game_build_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES game_creation_sessions(id),
    user_id TEXT,
    status TEXT DEFAULT 'queued' CHECK (status IN ('queued', 'running', 'ready', 'failed')),
    mode TEXT,
    template_id TEXT,
    prompt_text TEXT,
    final_layout JSONB,
    additional_instructions TEXT,
    original_job_id UUID,
    game_id UUID,
    html_content TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_game_sessions_user ON game_creation_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_game_sessions_status ON game_creation_sessions(status);
CREATE INDEX IF NOT EXISTS idx_game_jobs_session ON game_build_jobs(session_id);
CREATE INDEX IF NOT EXISTS idx_game_jobs_user ON game_build_jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_game_jobs_status ON game_build_jobs(status);

-- Enable RLS
ALTER TABLE game_creation_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE game_build_jobs ENABLE ROW LEVEL SECURITY;

-- Policies for anonymous access (adjust based on your auth needs)
CREATE POLICY "Allow all operations on sessions" ON game_creation_sessions
    FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow all operations on jobs" ON game_build_jobs
    FOR ALL USING (true) WITH CHECK (true);

-- Function to auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger for sessions
DROP TRIGGER IF EXISTS update_game_sessions_updated_at ON game_creation_sessions;
CREATE TRIGGER update_game_sessions_updated_at
    BEFORE UPDATE ON game_creation_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Cleanup old sessions (optional - run periodically)
-- DELETE FROM game_creation_sessions WHERE created_at < NOW() - INTERVAL '7 days';
-- DELETE FROM game_build_jobs WHERE created_at < NOW() - INTERVAL '30 days';

-- Table: game_template_prompt_chips
-- Stores prompt chips/quick options for each template
-- These can be default chips or AI-generated ones
CREATE TABLE IF NOT EXISTS game_template_prompt_chips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id TEXT NOT NULL,
    chip_text TEXT NOT NULL,
    category TEXT DEFAULT 'default', -- 'default', 'ai_generated', 'user_suggested'
    usage_count INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for fast lookup by template
CREATE INDEX IF NOT EXISTS idx_template_chips_template ON game_template_prompt_chips(template_id);
CREATE INDEX IF NOT EXISTS idx_template_chips_active ON game_template_prompt_chips(is_active);

-- Unique constraint to prevent duplicate chips for same template
CREATE UNIQUE INDEX IF NOT EXISTS idx_template_chips_unique
    ON game_template_prompt_chips(template_id, chip_text);

-- Enable RLS
ALTER TABLE game_template_prompt_chips ENABLE ROW LEVEL SECURITY;

-- Policy for public read, admin write
CREATE POLICY "Allow read on template chips" ON game_template_prompt_chips
    FOR SELECT USING (true);

CREATE POLICY "Allow insert on template chips" ON game_template_prompt_chips
    FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow update on template chips" ON game_template_prompt_chips
    FOR UPDATE USING (true);

-- Trigger for auto-updating updated_at
DROP TRIGGER IF EXISTS update_template_chips_updated_at ON game_template_prompt_chips;
CREATE TRIGGER update_template_chips_updated_at
    BEFORE UPDATE ON game_template_prompt_chips
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Insert default prompt chips for each template
INSERT INTO game_template_prompt_chips (template_id, chip_text, category) VALUES
    -- Memory Match
    ('memory_match', 'Easy (6 pairs)', 'default'),
    ('memory_match', 'Medium (8 pairs)', 'default'),
    ('memory_match', 'Hard (12 pairs)', 'default'),
    ('memory_match', 'Animals', 'default'),
    ('memory_match', 'Emojis', 'default'),
    ('memory_match', 'Numbers', 'default'),
    -- Math Quiz
    ('math_quiz', 'Addition Only', 'default'),
    ('math_quiz', 'Subtraction', 'default'),
    ('math_quiz', 'Multiplication', 'default'),
    ('math_quiz', 'Mixed', 'default'),
    ('math_quiz', '30 seconds', 'default'),
    ('math_quiz', '60 seconds', 'default'),
    -- Click Challenge
    ('click_challenge', '15 seconds', 'default'),
    ('click_challenge', '30 seconds', 'default'),
    ('click_challenge', '60 seconds', 'default'),
    ('click_challenge', 'Moving targets', 'default'),
    ('click_challenge', 'Static targets', 'default'),
    -- Trivia Quiz
    ('trivia_quiz', 'Science', 'default'),
    ('trivia_quiz', 'Geography', 'default'),
    ('trivia_quiz', 'History', 'default'),
    ('trivia_quiz', 'Animals', 'default'),
    ('trivia_quiz', 'Sports', 'default'),
    ('trivia_quiz', 'Movies', 'default'),
    -- Block Puzzle
    ('puzzle_blocks', '3x3 Grid', 'default'),
    ('puzzle_blocks', '4x4 Grid', 'default'),
    ('puzzle_blocks', '5x5 Grid', 'default'),
    ('puzzle_blocks', 'Timed', 'default'),
    ('puzzle_blocks', 'Untimed', 'default')
ON CONFLICT (template_id, chip_text) DO NOTHING;

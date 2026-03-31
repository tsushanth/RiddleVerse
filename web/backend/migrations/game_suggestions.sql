-- Game Suggestions Table
-- Stores game idea suggestions that are served to users.
-- Suggestions are marked 'used' after a game is generated from them,
-- and new AI-generated suggestions are added to maintain variety.

CREATE TABLE IF NOT EXISTS game_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    label TEXT NOT NULL,
    prompt TEXT NOT NULL,
    status TEXT DEFAULT 'active' CHECK (status IN ('active', 'used')),
    source TEXT DEFAULT 'seed' CHECK (source IN ('seed', 'ai_generated')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    used_at TIMESTAMPTZ
);

-- Fast lookup of active suggestions
CREATE INDEX IF NOT EXISTS idx_game_suggestions_status ON game_suggestions(status);

-- Case-insensitive unique label to prevent duplicates
CREATE UNIQUE INDEX IF NOT EXISTS idx_game_suggestions_label_unique
    ON game_suggestions(LOWER(label));

-- RLS
ALTER TABLE game_suggestions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow read on game_suggestions" ON game_suggestions
    FOR SELECT USING (true);

CREATE POLICY "Allow insert on game_suggestions" ON game_suggestions
    FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow update on game_suggestions" ON game_suggestions
    FOR UPDATE USING (true);

-- Seed with existing 20 suggestions
INSERT INTO game_suggestions (label, prompt, source) VALUES
    ('Snake Neon', 'A classic snake game with neon glow effects. The snake moves on a dark grid, eating glowing orbs that make it grow longer. Swipe to change direction. Speed increases every 5 points. Walls kill you, and biting your own tail ends the game. Show a trail effect behind the snake.', 'seed'),
    ('Brick Breaker', 'A brick breaker / breakout game with colorful rows of bricks at the top. The player controls a paddle at the bottom by dragging left and right. A ball bounces off the paddle and breaks bricks on contact. Some bricks take multiple hits. Include power-ups: wider paddle, multi-ball, and slow ball. 3 lives.', 'seed'),
    ('Memory Cards', 'A memory card matching game with 6 pairs (12 cards total) in a 3x4 grid. Cards have colorful emoji symbols. Tap to flip, find matches. Matched pairs stay face-up with celebration effect. Track moves and time. Win screen when all pairs found.', 'seed'),
    ('Whack-a-Mole', 'A whack-a-mole game with emoji characters popping from a 3x3 grid of holes. Moles pop up randomly, tap them to score. Golden moles worth bonus points. 30 second timer, moles appear faster as time progresses. Hit animation and miss penalty.', 'seed'),
    ('Space Invaders', 'A space invaders game where you control a spaceship by dragging. Rows of aliens move side to side and descend. Tap to shoot. Aliens shoot back randomly. Include shields that degrade when hit. Different alien types worth different points. Wave system that gets harder.', 'seed'),
    ('Trivia Quiz', 'A trivia quiz about science with 10 multiple-choice questions. 4 tappable answer buttons per question. 15-second timer. Correct = green flash, wrong = red + show correct answer. Faster answers earn more points. Progress bar and final results screen with grade.', 'seed'),
    ('Fruit Ninja', 'A fruit slicing game where fruits fly up from the bottom of the screen. Swipe across them to slice for points. Bombs also appear — slicing a bomb ends the game. Combo bonus for slicing multiple fruits in one swipe. Juice splash effects. 60 second time limit.', 'seed'),
    ('Flappy Bird', 'A flappy bird style game with a cute pixel bird. Tap anywhere to flap and gain altitude. Gravity pulls the bird down. Navigate through gaps in pipes scrolling from right to left. Pipes get closer together as score increases. Show current and best score.', 'seed'),
    ('Color Match', E'A fast-paced color matching game. A colored word appears on screen (e.g., \'RED\' written in blue). Two buttons show colors — tap the button matching the TEXT color, not the word meaning. Gets faster over time. 3 strikes and you\'re out. Streak bonuses.', 'seed'),
    ('Tower Builder', 'A tower stacking game where blocks swing back and forth overhead. Tap to drop each block. If it lands perfectly aligned, you get a bonus. Parts that overhang get cut off, making the next block narrower. Game ends when a block misses entirely. How tall can you build?', 'seed'),
    ('Asteroid Dodge', 'A space survival game. Your ship is in the center, asteroids fly in from all directions. Drag to move your ship and dodge them. Collect power-up stars for temporary shields. Asteroids get faster and more frequent over time. Survive as long as possible.', 'seed'),
    ('Word Scramble', 'A word unscrambling game. Scrambled letters appear on screen. Tap letters in the correct order to spell the word. Hints available (costs points). 10 rounds with increasingly difficult words. Time bonus for speed. Categories: animals, foods, countries.', 'seed'),
    ('Rhythm Tap', 'A rhythm game where colored circles fall from the top of the screen toward a hit zone at the bottom. Tap circles as they reach the zone for points. Perfect timing = more points. Miss 3 notes and game over. Circles come in patterns that create a visual rhythm. Increasing speed.', 'seed'),
    ('Maze Runner', 'A maze navigation game. A randomly generated maze fills the screen. Drag to move your character from start (top-left) to finish (bottom-right). Timer counts up. Collect gems scattered throughout for bonus points. Fog of war — only see nearby areas. New maze each round.', 'seed'),
    ('Bubble Pop', 'A bubble shooter game. Colored bubbles fill the top of the screen. Aim and shoot bubbles from the bottom to match 3+ of the same color, which pop and score points. Chain reactions give bonus points. Ceiling drops down periodically. Game over when bubbles reach the bottom.', 'seed'),
    ('Reflex Test', E'A reaction time tester disguised as a game. Screen shows \'Wait...\' in red, then changes to \'TAP!\' in green at random intervals. Tap as fast as possible. Shows reaction time in milliseconds. Best of 5 rounds. Track average and personal best. Fun animations between rounds.', 'seed'),
    ('Number Puzzle', 'A sliding number puzzle (15 puzzle). A 4x4 grid with tiles numbered 1-15 and one empty space. Tap adjacent tiles to slide them into the empty space. Goal: arrange numbers in order. Move counter and timer. Shuffle animation at start. Victory celebration when solved.', 'seed'),
    ('Pong Classic', 'A modern take on Pong. Player controls the left paddle by dragging up and down. AI controls the right paddle. Ball speeds up with each hit. Score to 7 to win. Particle trail on the ball. Neon glow aesthetic. Between-point animations.', 'seed'),
    ('Emoji Catcher', 'A catching game where happy emojis fall from the sky and you move a basket left and right to catch them. Different emojis worth different points. Sad/angry emojis are bombs that lose points if caught. Speed and frequency increase over time. 45 second game.', 'seed'),
    ('Platform Jump', 'An endless vertical jumper. Your character auto-bounces on platforms. Tilt or drag to move left and right. Platforms get smaller and more spaced out as you go higher. Some platforms break on contact. Spring platforms launch you extra high. Camera follows upward. Score = height reached.', 'seed')
ON CONFLICT (LOWER(label)) DO NOTHING;

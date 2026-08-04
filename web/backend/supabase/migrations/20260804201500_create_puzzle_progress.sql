-- Per-user sequential puzzle-position tracking, migrated off Firestore.
-- fetchNextPuzzle() in puzzleService.js used Firestore's users/{userId}
-- doc (.lastPuzzleProgress.{type}_{difficulty}) for this single field —
-- under Firestore's ongoing quota exhaustion, reads degrade to "no
-- progress", which made the sequence always restart from the same first
-- puzzle (repeat-puzzle bug, 2026-08-04) instead of advancing.
create table if not exists puzzle_progress (
  user_id text not null,
  puzzle_type text not null,
  difficulty text not null,
  current_puzzle_id text,
  updated_at timestamptz not null default now(),
  primary key (user_id, puzzle_type, difficulty)
);

create index if not exists puzzle_progress_lookup_idx
  on puzzle_progress(user_id, puzzle_type, difficulty);

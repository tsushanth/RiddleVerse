-- Simple global leaderboard, migrated off Firestore (quota exhaustion —
-- this collection was read/written on every live scoring.routes.js /
-- leaderboard.routes.js request, unlike the per-puzzle leaderboard which
-- already lives in custom_leaderboard).
create table if not exists leaderboard (
  user_id text primary key,
  name text not null,
  score integer not null default 0,
  last_updated timestamptz not null default now()
);

create index if not exists leaderboard_score_idx on leaderboard(score desc);
create index if not exists leaderboard_name_idx on leaderboard(name);
